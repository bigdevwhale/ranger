package app.ranger.data

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One sample of a recording. Anything a device didn't record is null. */
data class Pt(val at: Long, val lat: Double? = null, val lon: Double? = null, val ele: Double? = null, val hr: Int? = null, val dist: Double? = null)

/**
 * A run as read from somewhere, before it is a [Run]. Summary numbers are used when the samples don't cover them
 * (a Strava row without a file, a Health Connect session without a route).
 */
data class Parsed(
    val start: Long,
    val points: List<Pt>,
    val source: String,
    val sourceId: String = "",
    val title: String = "",
    val treadmill: Boolean = false,
    val durationS: Int? = null,
    val movingS: Int? = null,
    val distanceM: Double? = null,
    val ascentM: Double? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val gear: String = "",
)

/** The full samples of a run, kept in a small binary file next to the database. */
object TrackFile {
    private const val MAGIC = 0x52545231 // "RTR1"

    private fun dir(ctx: Context) = File(ctx.filesDir, "tracks").apply { mkdirs() }
    fun file(ctx: Context, id: Long) = File(dir(ctx), "$id.trk")

    fun write(ctx: Context, id: Long, start: Long, pts: List<Pt>) {
        DataOutputStream(file(ctx, id).outputStream().buffered()).use { o ->
            o.writeInt(MAGIC); o.writeLong(start); o.writeInt(pts.size)
            for (p in pts) {
                o.writeInt(((p.at - start) / 1000).toInt())
                o.writeInt(p.lat?.let { (it * 1e7).roundToInt() } ?: Int.MIN_VALUE)
                o.writeInt(p.lon?.let { (it * 1e7).roundToInt() } ?: Int.MIN_VALUE)
                o.writeFloat(p.ele?.toFloat() ?: Float.NaN)
                o.writeShort(p.hr ?: 0)
                o.writeFloat(p.dist?.toFloat() ?: Float.NaN)
            }
        }
    }

    fun read(ctx: Context, id: Long): List<Pt> {
        val f = file(ctx, id)
        if (!f.exists()) return emptyList()
        return DataInputStream(f.inputStream().buffered()).use { inp ->
            if (inp.readInt() != MAGIC) return emptyList()
            val start = inp.readLong(); val n = inp.readInt()
            List(n) {
                val t = inp.readInt(); val la = inp.readInt(); val lo = inp.readInt(); val e = inp.readFloat(); val h = inp.readShort().toInt(); val d = inp.readFloat()
                Pt(start + t * 1000L, if (la == Int.MIN_VALUE) null else la / 1e7, if (lo == Int.MIN_VALUE) null else lo / 1e7,
                    if (e.isNaN()) null else e.toDouble(), if (h > 0) h else null, if (d.isNaN()) null else d.toDouble())
            }
        }
    }

    fun delete(ctx: Context, id: Long) { file(ctx, id).delete() }
}

object Geo {
    const val R = 6_371_000.0
    fun dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Google's encoded polyline. */
    fun encode(pts: List<Pair<Double, Double>>): String {
        val sb = StringBuilder(); var pLat = 0; var pLon = 0
        fun put(v: Int) { var x = if (v < 0) (v shl 1).inv() else v shl 1; while (x >= 0x20) { sb.append(((0x20 or (x and 0x1f)) + 63).toChar()); x = x shr 5 }; sb.append((x + 63).toChar()) }
        for ((la, lo) in pts) { val a = (la * 1e5).roundToInt(); val b = (lo * 1e5).roundToInt(); put(a - pLat); put(b - pLon); pLat = a; pLon = b }
        return sb.toString()
    }

    fun decode(s: String): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>(s.length / 4)
        var i = 0; var lat = 0; var lon = 0
        fun next(): Int { var r = 0; var sh = 0; var b: Int; do { b = s[i++].code - 63; r = r or ((b and 0x1f) shl sh); sh += 5 } while (b >= 0x20); return if (r and 1 != 0) (r shr 1).inv() else r shr 1 }
        while (i < s.length) { lat += next(); lon += next(); out += lat / 1e5 to lon / 1e5 }
        return out
    }

    /** Douglas–Peucker in metres (a local flat projection is plenty at run scale). */
    fun simplify(pts: List<Pair<Double, Double>>, tol: Double): List<Pair<Double, Double>> {
        if (pts.size < 3) return pts
        val lat0 = pts[0].first; val k = cos(Math.toRadians(lat0))
        val x = DoubleArray(pts.size) { Math.toRadians(pts[it].second) * R * k }
        val y = DoubleArray(pts.size) { Math.toRadians(pts[it].first) * R }
        val keep = BooleanArray(pts.size); keep[0] = true; keep[pts.size - 1] = true
        val stack = ArrayDeque<IntArray>(); stack.add(intArrayOf(0, pts.size - 1))
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var best = -1; var bd = tol
            val dx = x[b] - x[a]; val dy = y[b] - y[a]; val len = sqrt(dx * dx + dy * dy)
            for (i in a + 1 until b) {
                val d = if (len < 1e-6) sqrt((x[i] - x[a]).let { it * it } + (y[i] - y[a]).let { it * it }) else abs(dy * x[i] - dx * y[i] + x[b] * y[a] - y[b] * x[a]) / len
                if (d > bd) { bd = d; best = i }
            }
            if (best >= 0) { keep[best] = true; stack.add(intArrayOf(a, best)); stack.add(intArrayOf(best, b)) }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
    }
}
