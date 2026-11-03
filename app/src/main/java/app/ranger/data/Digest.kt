package app.ranger.data

import kotlin.math.max
import kotlin.math.min

/**
 * Turns samples into the numbers a [Run] keeps: distance, moving time, climb, heart-rate histogram, best efforts
 * and surges. Done once at import, so the rest of the app never needs to read a track file to draw a screen.
 */
object Digest {
    val DISTANCES = intArrayOf(1000, 5000, 10000, 21097, 42195)

    class Out(val run: Run, val points: List<Pt>)

    fun of(p: Parsed): Out {
        val pts = clean(p.points)
        val dist = cumulative(pts)
        val t = LongArray(pts.size) { pts[it].at }
        val duration = p.durationS ?: if (pts.size > 1) ((t.last() - t.first()) / 1000).toInt() else 0
        val total = p.distanceM ?: dist.lastOrNull() ?: 0.0
        val moving = p.movingS ?: if (pts.size > 1) moving(t, dist) else duration
        val ascent = p.ascentM ?: ascent(pts)
        val hist = hrHist(pts)
        val hrs = pts.mapNotNull { it.hr }
        val avg = p.avgHr ?: hist.takeIf { it.isNotEmpty() }?.let { h -> (h.entries.sumOf { it.key.toLong() * it.value } / h.values.sum().coerceAtLeast(1)).toInt() }
        val maxHr = p.maxHr ?: hrs.maxOrNull()
        val geo = pts.filter { it.lat != null && it.lon != null }.map { it.lat!! to it.lon!! }
        val line = if (geo.size > 1) Geo.encode(Geo.simplify(geo, 8.0)) else ""
        val best = if (dist.isNotEmpty()) DISTANCES.joinToString(",") { bestOver(t, dist, it.toDouble()).toString() }
            else DISTANCES.joinToString(",") { if (total >= it * 0.995 && total < it * 1.06 && duration > 0) (duration * it / total).toInt().toString() else "0" }
        val run = Run(
            start = pts.firstOrNull()?.at ?: p.start,
            durationS = max(duration, moving),
            movingS = moving,
            distanceM = total,
            ascentM = ascent,
            avgHr = avg,
            maxHr = maxHr,
            source = p.source,
            sourceId = p.sourceId.ifBlank { "${p.source}:${(pts.firstOrNull()?.at ?: p.start) / 60_000}" },
            title = p.title,
            treadmill = p.treadmill,
            hasTrack = geo.size > 1,
            lat = geo.firstOrNull()?.first,
            lon = geo.firstOrNull()?.second,
            line = line,
            hrHist = hist.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" },
            best = best,
            surges = if (dist.size > 30) surges(t, dist) else 0,
        )
        return Out(run, pts)
    }

    /** Sorted, one sample per second at most, no GPS teleports (faster than 12 m/s between samples). */
    private fun clean(raw: List<Pt>): List<Pt> {
        val s = raw.sortedBy { it.at }
        val out = ArrayList<Pt>(s.size)
        var lastGeo: Pt? = null
        for (p in s) {
            val prev = out.lastOrNull()
            if (prev != null && p.at - prev.at < 1000) continue
            var q = p
            if (p.lat != null && p.lon != null) {
                val g = lastGeo
                if (g != null) {
                    val dt = (p.at - g.at) / 1000.0
                    if (dt > 0 && Geo.dist(g.lat!!, g.lon!!, p.lat, p.lon) / dt > 12) q = p.copy(lat = null, lon = null)
                }
                if (q.lat != null) lastGeo = q
            }
            if (q.lat == null && q.hr == null && q.dist == null && q.ele == null) continue
            out += q
        }
        return out
    }

    /** Distance along the run at each sample: the device's own counter when it has one, else from the coordinates. */
    private fun cumulative(pts: List<Pt>): DoubleArray {
        if (pts.isEmpty()) return DoubleArray(0)
        val own = pts.count { it.dist != null }
        val geo = pts.count { it.lat != null }
        if (own < pts.size / 2 && geo < 2) return DoubleArray(0)
        val out = DoubleArray(pts.size)
        if (own >= pts.size / 2) {
            var last = 0.0
            for (i in pts.indices) { val d = pts[i].dist; if (d != null && d >= last) last = d; out[i] = last }
            val base = out.first(); for (i in out.indices) out[i] -= base
        } else {
            var acc = 0.0; var lat = Double.NaN; var lon = Double.NaN
            for (i in pts.indices) {
                val p = pts[i]
                if (p.lat != null && p.lon != null) { if (!lat.isNaN()) acc += Geo.dist(lat, lon, p.lat, p.lon); lat = p.lat; lon = p.lon }
                out[i] = acc
            }
        }
        return out
    }

    /** Time spent going faster than a slow walk, gaps of over 30 s left out (paused watch). */
    private fun moving(t: LongArray, d: DoubleArray): Int {
        if (d.isEmpty()) return ((t.last() - t.first()) / 1000).toInt()
        var s = 0.0
        for (i in 1 until t.size) {
            val dt = (t[i] - t[i - 1]) / 1000.0
            if (dt <= 0 || dt > 30) continue
            if ((d[i] - d[i - 1]) / dt > 0.8) s += dt
        }
        return s.toInt()
    }

    /** Climb from smoothed elevation, ignoring wobbles under 2 m. */
    private fun ascent(pts: List<Pt>): Double {
        val e = pts.mapNotNull { it.ele }
        if (e.size < 5) return 0.0
        val sm = DoubleArray(e.size) { i -> var s = 0.0; var n = 0; for (j in max(0, i - 3)..min(e.size - 1, i + 3)) { s += e[j]; n++ }; s / n }
        var up = 0.0; var ref = sm[0]
        for (v in sm) { if (v > ref + 2) { up += v - ref; ref = v } else if (v < ref - 2) ref = v }
        return up
    }

    private fun hrHist(pts: List<Pt>): Map<Int, Int> {
        val m = HashMap<Int, Double>()
        for (i in 1 until pts.size) {
            val h = pts[i].hr ?: continue
            if (h !in 35..230) continue
            val dt = ((pts[i].at - pts[i - 1].at) / 1000.0).coerceAtMost(10.0)
            m[h] = (m[h] ?: 0.0) + dt
        }
        return m.mapValues { it.value.toInt() }.filterValues { it > 0 }
    }

    /** Fastest stretch covering [target] metres, in seconds (0 if the run is shorter). Two pointers, interpolated. */
    fun bestOver(t: LongArray, d: DoubleArray, target: Double): Int {
        if (d.isEmpty() || d.last() < target) return 0
        var best = Double.MAX_VALUE; var i = 0
        for (j in d.indices) {
            while (i + 1 < j && d[j] - d[i + 1] >= target) i++
            if (d[j] - d[i] >= target) {
                val over = d[j] - d[i] - target
                val seg = d[j] - d[j - 1].coerceAtLeast(d[i])
                val frac = if (seg > 0) (over / seg).coerceIn(0.0, 1.0) else 0.0
                val dt = (t[j] - t[i]) / 1000.0 - frac * (t[j] - t[j - 1]) / 1000.0
                if (dt in 1.0..best) best = dt
            }
        }
        return if (best == Double.MAX_VALUE) 0 else best.toInt()
    }

    /** Counts surges: 20 s – 5 min stretches at least 18% faster than the run's median moving speed. */
    fun surges(t: LongArray, d: DoubleArray): Int {
        val n = t.size
        val speed = DoubleArray(n)
        var a = 0
        for (b in 0 until n) {
            while (a < b && t[b] - t[a] > 20_000) a++
            val dt = (t[b] - t[a]) / 1000.0
            speed[b] = if (dt > 0) (d[b] - d[a]) / dt else 0.0
        }
        val moving = speed.filter { it > 1.2 }.sorted()
        if (moving.size < 20) return 0
        val med = moving[moving.size / 2]
        var count = 0; var since = -1L
        for (i in 0 until n) {
            val fast = speed[i] > med * 1.18
            if (fast && since < 0) since = t[i]
            if (!fast && since >= 0) { val len = t[i] - since; if (len in 20_000..300_000) count++; since = -1 }
        }
        return count
    }
}
