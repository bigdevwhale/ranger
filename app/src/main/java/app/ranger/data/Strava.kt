package app.ranger.data

import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * A Strava bulk export (Settings → My account → Download or delete your account): a zip with activities.csv and
 * the original files under activities/. Rows without a file still become runs from the numbers in the table.
 */
object Strava {
    class Row(
        val id: String, val start: Long, val name: String, val type: String, val elapsed: Int?, val moving: Int?,
        val distanceM: Double?, val ascent: Double?, val avgHr: Int?, val maxHr: Int?, val file: String, val gear: String,
    )

    private val DATE = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm:ss a", Locale.ENGLISH)

    /** CSV with quoted fields that may hold commas and line breaks. */
    fun csv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>(); val f = StringBuilder()
        var q = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            if (q) {
                if (c == '"') { if (i + 1 < text.length && text[i + 1] == '"') { f.append('"'); i++ } else q = false } else f.append(c)
            } else when (c) {
                '"' -> q = true
                ',' -> { row += f.toString(); f.clear() }
                '\n' -> { row += f.toString(); f.clear(); rows += row; row = ArrayList() }
                '\r' -> {}
                else -> f.append(c)
            }
            i++
        }
        if (f.isNotEmpty() || row.isNotEmpty()) { row += f.toString(); rows += row }
        return rows
    }

    fun rows(text: String): List<Row> {
        val all = csv(text.removePrefix(0xFEFF.toChar().toString()))
        if (all.isEmpty()) return emptyList()
        val head = all[0]
        // Several names appear twice (the second "Distance" is in metres, the first in km): take the first.
        fun col(vararg names: String): Int { for (n in names) { val i = head.indexOf(n); if (i >= 0) return i }; return -1 }
        val cId = col("Activity ID"); val cDate = col("Activity Date"); val cName = col("Activity Name"); val cType = col("Activity Type")
        val cElapsed = col("Elapsed Time"); val cMoving = col("Moving Time"); val cDist = col("Distance"); val cAsc = col("Elevation Gain")
        val cAvg = col("Average Heart Rate"); val cMax = col("Max Heart Rate"); val cFile = col("Filename"); val cGear = col("Activity Gear")
        val distM = head.indices.filter { head[it] == "Distance" }.getOrNull(1)
        return all.drop(1).mapNotNull { r ->
            fun g(i: Int) = r.getOrNull(i)?.trim().orEmpty()
            if (!Formats.isRun(g(cType))) return@mapNotNull null
            val start = runCatching { LocalDateTime.parse(g(cDate), DATE).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            val km = g(cDist).replace(",", "").toDoubleOrNull()
            val m = distM?.let { g(it).toDoubleOrNull() } ?: km?.times(1000)
            Row(g(cId), start, g(cName), g(cType), g(cElapsed).toDoubleOrNull()?.toInt(), g(cMoving).toDoubleOrNull()?.toInt(), m,
                g(cAsc).toDoubleOrNull(), g(cAvg).toDoubleOrNull()?.toInt(), g(cMax).toDoubleOrNull()?.toInt(), g(cFile), g(cGear))
        }
    }

    /**
     * Two passes over the zip, since it can be gigabytes of photos: first the table, then only the files it names.
     * [open] must give a fresh stream each time.
     */
    fun read(open: () -> InputStream, onRun: (Parsed) -> Unit, progress: (Int, Int) -> Unit = { _, _ -> }): Int {
        var table: String? = null
        ZipInputStream(open().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name.endsWith("activities.csv")) { table = z.readBytes().toString(Charsets.UTF_8); break }
            }
        }
        val rows = rows(table ?: return -1)
        val byFile = rows.filter { it.file.isNotBlank() }.associateBy { it.file.substringAfterLast('/') }
        val done = HashSet<String>()
        var n = 0
        ZipInputStream(open().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val row = byFile[e.name.substringAfterLast('/')] ?: continue
                val p = runCatching { Formats.parse(e.name, z.readBytes(), "strava") }.getOrNull()
                if (p != null) {
                    onRun(p.copy(sourceId = "strava:${row.id}", title = row.name.ifBlank { p.title }, gear = row.gear,
                        treadmill = p.treadmill || row.type.contains("Virtual", true),
                        ascentM = p.ascentM ?: row.ascent, durationS = p.durationS ?: row.elapsed, movingS = p.movingS ?: row.moving))
                    done += row.id
                }
                progress(++n, rows.size)
            }
        }
        for (r in rows) if (r.id !in done) {
            onRun(Parsed(start = r.start, points = emptyList(), source = "strava", sourceId = "strava:${r.id}", title = r.name,
                treadmill = r.type.contains("Virtual", true), durationS = r.elapsed, movingS = r.moving, distanceM = r.distanceM,
                ascentM = r.ascent, avgHr = r.avgHr, maxHr = r.maxHr, gear = r.gear))
        }
        return rows.size
    }
}
