package app.ranger.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.zip.ZipInputStream
import kotlin.math.abs

/**
 * Brings runs in from anywhere and keeps one copy of each: the same morning run may arrive from the watch's FIT
 * file, from Strava and from Health Connect. A run within three minutes of another with a similar distance is
 * the same run; the copy with a track or a heart rate wins, while shoes, company and notes stay as they were.
 */
class Importer(private val ctx: Context, private val dao: RangerDao, private val settings: SettingsStore) {
    data class Result(val added: Int, val merged: Int, val skipped: Int, val failed: Int, val walks: Int = 0) {
        operator fun plus(o: Result) = Result(added + o.added, merged + o.merged, skipped + o.skipped, failed + o.failed, walks + o.walks)
    }

    enum class Outcome { ADDED, MERGED, SKIPPED, WALK }

    private var shoes: MutableList<Shoe>? = null

    suspend fun put(p: Parsed): Pair<Outcome, Long> {
        val d = Digest.of(p)
        var run = d.run
        if (run.distanceM < 300 && run.durationS < 180) return Outcome.SKIPPED to 0
        if (run.isWalk) return Outcome.WALK to 0
        val near = dao.runsBetween(run.start - 180_000, run.start + 180_000).firstOrNull { o ->
            o.distanceM < 1 || run.distanceM < 1 || abs(o.distanceM - run.distanceM) / maxOf(o.distanceM, run.distanceM) < 0.15
        }
        if (near != null) {
            val better = (run.hasTrack && !near.hasTrack) || (run.hrHist.isNotEmpty() && near.hrHist.isEmpty())
            if (!better) return Outcome.SKIPPED to near.id
            val merged = run.copy(
                id = near.id, source = near.source, sourceId = near.sourceId, title = near.title.ifBlank { run.title },
                shoeId = near.shoeId, feel = near.feel, note = near.note, listened = near.listened, race = near.race,
                ascentM = if (near.ascentM > 0 && !run.hasTrack) near.ascentM else run.ascentM,
                weatherDone = near.weatherDone && near.lat != null, wCode = near.wCode, tempC = near.tempC, precipMm = near.precipMm,
                snowCm = near.snowCm, windKmh = near.windKmh, importedAt = near.importedAt,
            )
            dao.update(merged)
            if (d.points.isNotEmpty()) TrackFile.write(ctx, near.id, merged.start, d.points)
            return Outcome.MERGED to near.id
        }
        val shoe = shoeFor(p.gear, run.start)
        run = run.copy(shoeId = shoe)
        val id = dao.insert(run)
        if (id <= 0) return Outcome.SKIPPED to 0
        if (d.points.isNotEmpty()) TrackFile.write(ctx, id, run.start, d.points)
        return Outcome.ADDED to id
    }

    /** Strava gear names become pairs on the shelf; otherwise the pair in use since before this run, if any. */
    private suspend fun shoeFor(gear: String, at: Long): Long? {
        val list = shoes ?: dao.shoeList().toMutableList().also { shoes = it }
        if (gear.isNotBlank()) {
            list.firstOrNull { it.gear == gear }?.let { return it.id }
            val s = Shoe(name = gear, color = (list.size * 3) % Palette.BOOTS, gear = gear, addedAt = at)
            val id = dao.insert(s); list += s.copy(id = id)
            return id
        }
        val def = settings.now().defaultShoe ?: return null
        return list.firstOrNull { it.id == def && it.addedAt <= at + 86_400_000 && (it.retiredAt == null || it.retiredAt > at) }?.id
    }

    fun Result.add(o: Outcome) = when (o) { Outcome.ADDED -> copy(added = added + 1); Outcome.MERGED -> copy(merged = merged + 1); Outcome.SKIPPED -> copy(skipped = skipped + 1); Outcome.WALK -> copy(walks = walks + 1) }

    /** Files picked by the ranger: single workouts, gzipped ones, zips of them, or a whole Strava export. */
    suspend fun files(uris: List<Uri>, progress: (String) -> Unit = {}): Result {
        var res = Result(0, 0, 0, 0)
        shoes = null
        for (u in uris) {
            val name = name(u)
            progress(name)
            if (name.lowercase().endsWith(".zip")) {
                res += zip(u, progress)
                continue
            }
            val bytes = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: run { res = res.copy(failed = res.failed + 1); null } ?: continue
            val p = runCatching { Formats.parse(name, bytes) }.getOrNull()
            res = if (p == null) res.copy(failed = res.failed + 1) else res.add(put(p).first)
        }
        return res
    }

    private suspend fun zip(u: Uri, progress: (String) -> Unit): Result {
        var res = Result(0, 0, 0, 0)
        val strava = ArrayList<Parsed>()
        val n = runCatching { Strava.read({ ctx.contentResolver.openInputStream(u)!! }, { strava += it }) { i, all -> if (i % 25 == 0) progress("$i / $all") } }.getOrDefault(-1)
        if (n >= 0) {
            for (p in strava) res = res.add(put(p).first)
            return res
        }
        // a plain zip of workout files
        val found = ArrayList<Parsed>()
        ctx.contentResolver.openInputStream(u)?.use { s ->
            ZipInputStream(s.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val nm = e.name.lowercase()
                    if (listOf(".gpx", ".tcx", ".fit", ".gpx.gz", ".tcx.gz", ".fit.gz").none { nm.endsWith(it) }) continue
                    val p = runCatching { Formats.parse(e.name, z.readBytes()) }.getOrNull()
                    if (p == null) res = res.copy(failed = res.failed + 1) else found += p
                }
            }
        }
        for (p in found) res = res.add(put(p).first)
        return res
    }

    private fun name(u: Uri): String = runCatching {
        ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: u.lastPathSegment.orEmpty()
}
