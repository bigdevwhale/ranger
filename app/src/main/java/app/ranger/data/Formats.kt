package app.ranger.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.util.zip.GZIPInputStream

/** Reads the usual workout files: GPX (any watch, Strava), TCX (Garmin, Polar, Strava), FIT (Garmin, Coros, Suunto, Wahoo). */
object Formats {
    /** By name and, failing that, by the first bytes. Gzip is unwrapped. Null when it isn't a run file at all. */
    fun parse(name: String, bytes: ByteArray, source: String? = null): Parsed? {
        var b = bytes; var n = name.lowercase()
        if (n.endsWith(".gz") || (b.size > 2 && b[0] == 0x1f.toByte() && b[1] == 0x8b.toByte())) {
            b = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }; n = n.removeSuffix(".gz")
        }
        return when {
            n.endsWith(".fit") || Fit.looksLike(b) -> Fit.parse(b, source ?: "fit")
            n.endsWith(".tcx") -> xml(b, source ?: "tcx")
            n.endsWith(".gpx") -> xml(b, source ?: "gpx")
            else -> runCatching { xml(b, source ?: "gpx") }.getOrNull()
        }
    }

    fun time(s: String): Long? = runCatching { OffsetDateTime.parse(s.trim()).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { Instant.parse(s.trim()).toEpochMilli() }.getOrNull()
        ?: runCatching { Instant.parse(s.trim() + "Z").toEpochMilli() }.getOrNull()

    /** GPX and TCX in one pass: both are a list of timed points with optional position, height, heart rate and distance. */
    private fun xml(bytes: ByteArray, source: String): Parsed? {
        // Strava's TCX files can start with spaces before the declaration, which the parser refuses.
        var off = 0; while (off < bytes.size && bytes[off].toInt().toChar().isWhitespace()) off++
        val xp = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        xp.setInput(ByteArrayInputStream(bytes, off, bytes.size - off) as InputStream, null)
        val pts = ArrayList<Pt>()
        var lat: Double? = null; var lon: Double? = null; var ele: Double? = null; var hr: Int? = null; var dist: Double? = null; var at: Long? = null
        var inPt = false; var text = StringBuilder(); var name = ""; var title = ""; var sport = ""
        val path = ArrayList<String>()
        var summaryDist = 0.0; var summaryTime = 0.0; var laps = 0
        while (true) {
            when (xp.next()) {
                XmlPullParser.START_TAG -> {
                    val tag = xp.name; path += tag; text = StringBuilder()
                    when (tag) {
                        "trkpt", "rtept" -> { inPt = true; lat = xp.getAttributeValue(null, "lat")?.toDoubleOrNull(); lon = xp.getAttributeValue(null, "lon")?.toDoubleOrNull(); ele = null; hr = null; dist = null; at = null }
                        "Trackpoint" -> { inPt = true; lat = null; lon = null; ele = null; hr = null; dist = null; at = null }
                        "Activity" -> sport = xp.getAttributeValue(null, "Sport").orEmpty()
                    }
                    name = tag
                }
                XmlPullParser.TEXT -> text.append(xp.text)
                XmlPullParser.END_TAG -> {
                    val tag = xp.name; val v = text.toString().trim()
                    val parent = path.getOrNull(path.size - 2)
                    if (inPt) when (tag) {
                        "ele", "AltitudeMeters" -> ele = v.toDoubleOrNull()
                        "time", "Time" -> at = time(v)
                        "hr" -> hr = v.toDoubleOrNull()?.toInt()
                        "Value" -> if (parent == "HeartRateBpm") hr = v.toDoubleOrNull()?.toInt()
                        "LatitudeDegrees" -> lat = v.toDoubleOrNull()
                        "LongitudeDegrees" -> lon = v.toDoubleOrNull()
                        "DistanceMeters" -> dist = v.toDoubleOrNull()
                        "trkpt", "rtept", "Trackpoint" -> { inPt = false; at?.let { pts += Pt(it, lat, lon, ele, hr?.takeIf { h -> h > 0 }, dist) } }
                    } else when (tag) {
                        "name" -> if (title.isEmpty() && parent in setOf("trk", "metadata")) title = v
                        "DistanceMeters" -> if (parent == "Lap") summaryDist += v.toDoubleOrNull() ?: 0.0
                        "TotalTimeSeconds" -> if (parent == "Lap") { summaryTime += v.toDoubleOrNull() ?: 0.0; laps++ }
                        "type" -> if (parent == "trk") sport = v
                    }
                    if (path.isNotEmpty()) path.removeAt(path.size - 1)
                    text = StringBuilder()
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        if (pts.isEmpty()) return null
        if (sport.isNotEmpty() && !isRun(sport)) return null
        val hasGeo = pts.any { it.lat != null }
        return Parsed(
            start = pts.first().at, points = pts, source = source, title = title,
            treadmill = !hasGeo && pts.any { it.dist != null },
            distanceM = if (!hasGeo && pts.none { it.dist != null } && summaryDist > 0) summaryDist else null,
            movingS = if (laps > 0 && summaryTime > 0) summaryTime.toInt() else null,
        )
    }

    /** Sports names that count as running in GPX `<type>`, TCX `Sport` and Strava's activity type. */
    fun isRun(s: String): Boolean {
        val x = s.lowercase().replace("_", " ")
        if (x.isBlank() || x.all { it.isDigit() }) return x.isBlank() || x == "9" // 9 is "running" in old Strava GPX
        return "run" in x || x == "jogging" || "бег" in x
    }
}

/** A Garmin FIT reader: enough of the format for record and session messages. */
object Fit {
    private const val EPOCH = 631_065_600L // 1989-12-31T00:00:00Z

    fun looksLike(b: ByteArray) = b.size > 12 && b[8] == '.'.code.toByte() && b[9] == 'F'.code.toByte() && b[10] == 'I'.code.toByte() && b[11] == 'T'.code.toByte()

    private class Field(val num: Int, val size: Int, val type: Int)
    private class Def(val global: Int, val big: Boolean, val fields: List<Field>, val devBytes: Int)

    fun parse(b: ByteArray, source: String = "fit"): Parsed? {
        if (!looksLike(b)) return null
        val headerSize = b[0].toInt() and 0xff
        val dataSize = u32(b, 4, false)
        val end = (headerSize + dataSize).coerceAtMost(b.size.toLong()).toInt()
        var p = headerSize
        val defs = arrayOfNulls<Def>(16)
        var lastTs = 0L
        val pts = ArrayList<Pt>()
        var sport = -1; var subSport = -1
        var sStart: Long? = null; var sElapsed: Int? = null; var sTimer: Int? = null; var sDist: Double? = null; var sAvg: Int? = null; var sMax: Int? = null; var sAsc: Double? = null
        while (p < end) {
            val h = b[p++].toInt() and 0xff
            if (h and 0x80 != 0) {
                val local = (h shr 5) and 3; val off = h and 0x1f
                var ts = (lastTs and 0x1f.toLong().inv()) or off.toLong()
                if (off < (lastTs and 0x1f)) ts += 0x20
                lastTs = ts
                val d = defs[local] ?: return null
                p = data(b, p, d, ts) { m -> if (d.global == 20) record(m, ts)?.let { pts += it } }
                continue
            }
            val local = h and 0x0f
            if (h and 0x40 != 0) {
                p++ // reserved
                val big = b[p++].toInt() == 1
                val global = u16(b, p, big); p += 2
                val n = b[p++].toInt() and 0xff
                val fields = List(n) { Field(b[p + it * 3].toInt() and 0xff, b[p + it * 3 + 1].toInt() and 0xff, b[p + it * 3 + 2].toInt() and 0xff) }
                p += n * 3
                var dev = 0
                if (h and 0x20 != 0) { val dn = b[p++].toInt() and 0xff; for (i in 0 until dn) dev += b[p + i * 3 + 1].toInt() and 0xff; p += dn * 3 }
                defs[local] = Def(global, big, fields, dev)
            } else {
                val d = defs[local] ?: return null
                p = data(b, p, d, null) { m ->
                    m[253]?.let { lastTs = it }
                    when (d.global) {
                        20 -> record(m, null)?.let { pts += it }
                        18 -> {
                            m[5]?.let { sport = it.toInt() }; m[6]?.let { subSport = it.toInt() }
                            sStart = m[2]?.let { (it + EPOCH) * 1000 } ?: sStart
                            m[7]?.let { sElapsed = (it / 1000).toInt() }; m[8]?.let { sTimer = (it / 1000).toInt() }
                            m[9]?.let { sDist = it / 100.0 }; m[16]?.let { sAvg = it.toInt() }; m[17]?.let { sMax = it.toInt() }; m[22]?.let { sAsc = it.toDouble() }
                        }
                        12 -> m[0]?.let { if (sport < 0) sport = it.toInt() } // sport message
                    }
                }
            }
        }
        if (sport >= 0 && sport != 1) return null // 1 = running
        val start = pts.firstOrNull()?.at ?: sStart ?: return null
        val hasGeo = pts.any { it.lat != null }
        return Parsed(
            start = start, points = pts, source = source,
            treadmill = subSport == 1 || subSport == 45 || (!hasGeo && subSport != 3),
            durationS = sElapsed, movingS = sTimer, distanceM = sDist, ascentM = sAsc, avgHr = sAvg, maxHr = sMax,
        )
    }

    /** Puts a record's fields into a map of field number → raw value (invalid values left out). */
    private inline fun data(b: ByteArray, start: Int, d: Def, ts: Long?, use: (HashMap<Int, Long>) -> Unit): Int {
        var p = start
        val m = HashMap<Int, Long>()
        for (f in d.fields) {
            val v = value(b, p, f, d.big)
            if (v != null) m[f.num] = v
            p += f.size
        }
        p += d.devBytes
        if (ts != null) m[253] = ts
        use(m)
        return p
    }

    private fun record(m: Map<Int, Long>, ts: Long?): Pt? {
        val t = m[253] ?: ts ?: return null
        val lat = m[0]?.let { it.toInt() * (180.0 / 2147483648.0) }
        val lon = m[1]?.let { it.toInt() * (180.0 / 2147483648.0) }
        val ele = (m[78] ?: m[2])?.let { it / 5.0 - 500 }
        return Pt((t + EPOCH) * 1000, lat, lon, ele, m[3]?.toInt()?.takeIf { it in 30..240 }, m[5]?.let { it / 100.0 })
    }

    private fun value(b: ByteArray, p: Int, f: Field, big: Boolean): Long? {
        val base = f.type and 0x1f
        val size = when (base) { 0, 1, 2, 7, 10, 13 -> 1; 3, 4, 11 -> 2; 5, 6, 8, 12 -> 4; 9, 14, 15, 16 -> 8; else -> f.size }
        if (f.size < size || base == 7 || base == 13) return null
        return when (base) {
            0, 2 -> (b[p].toLong() and 0xff).takeIf { it != 0xffL }
            1 -> b[p].toLong().takeIf { it != 0x7fL }
            10 -> (b[p].toLong() and 0xff).takeIf { it != 0L }
            3 -> u16(b, p, big).toShort().toLong().takeIf { it != 0x7fffL }
            4 -> u16(b, p, big).toLong().takeIf { it != 0xffffL }
            11 -> u16(b, p, big).toLong().takeIf { it != 0L }
            5 -> u32(b, p, big).toInt().toLong().takeIf { it != 0x7fffffffL }
            6 -> u32(b, p, big).takeIf { it != 0xffffffffL }
            12 -> u32(b, p, big).takeIf { it != 0L }
            else -> null
        }
    }

    private fun u16(b: ByteArray, p: Int, big: Boolean): Int =
        if (big) ((b[p].toInt() and 0xff) shl 8) or (b[p + 1].toInt() and 0xff) else (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8)

    private fun u32(b: ByteArray, p: Int, big: Boolean): Long {
        var r = 0L
        for (i in 0 until 4) r = r or ((b[p + i].toLong() and 0xff) shl (if (big) 8 * (3 - i) else 8 * i))
        return r
    }
}
