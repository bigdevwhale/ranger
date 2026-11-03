package app.ranger.data

import android.content.Context
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** The parts of a tile that matter here, in tile pixels (0..extent). */
class TileShapes(
    val extent: Int,
    val water: List<List<FloatArray>>,
    val waterways: List<FloatArray>,
    val green: List<List<FloatArray>>,
    val wood: List<List<FloatArray>>,
    val roads: List<Pair<Int, FloatArray>>,
    val buildings: List<FloatArray>,
) {
    companion object {
        fun of(bytes: ByteArray): TileShapes {
            var extent = 4096
            val water = ArrayList<List<FloatArray>>(); val ways = ArrayList<FloatArray>(); val green = ArrayList<List<FloatArray>>()
            val wood = ArrayList<List<FloatArray>>(); val roads = ArrayList<Pair<Int, FloatArray>>(); val buildings = ArrayList<FloatArray>()
            for (l in Mvt.layers(bytes)) {
                extent = l.extent
                for (f in l.features) {
                    val parts = f.parts.map { p -> FloatArray(p.size) { p[it].toFloat() } }
                    when (l.name) {
                        "water" -> if (f.type == 3) water += parts
                        "waterway" -> if (f.type == 2 && f.tags["class"] in setOf("river", "canal", "stream")) ways += parts
                        "park" -> if (f.type == 3) green += parts
                        "landcover" -> if (f.type == 3) when (f.tags["class"]) { "wood" -> wood += parts; "grass" -> green += parts }
                        "landuse" -> if (f.type == 3 && f.tags["class"] in setOf("cemetery", "allotments")) green += parts
                        "transportation" -> if (f.type == 2) {
                            val w = when (f.tags["class"]) { "motorway", "trunk", "primary" -> 3; "secondary", "tertiary" -> 2; "minor", "service" -> 1; "path", "track" -> 0; else -> -1 }
                            if (w >= 0 && f.tags["brunnel"] != "tunnel") parts.forEach { roads += w to it }
                        }
                        "building" -> if (f.type == 3) buildings += parts
                    }
                }
            }
            return TileShapes(extent, water, ways, green, wood, roads, buildings)
        }

        /** Even–odd point in a polygon made of rings (holes included). */
        fun inside(rings: List<FloatArray>, x: Float, y: Float): Boolean {
            var c = false
            for (r in rings) {
                val n = r.size / 2; var j = n - 1
                for (i in 0 until n) {
                    val xi = r[2 * i]; val yi = r[2 * i + 1]; val xj = r[2 * j]; val yj = r[2 * j + 1]
                    if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
                    j = i
                }
            }
            return c
        }

        fun near(line: FloatArray, x: Float, y: Float, d: Float): Boolean {
            for (i in 0 until line.size / 2 - 1) {
                val ax = line[2 * i]; val ay = line[2 * i + 1]; val bx = line[2 * i + 2]; val by = line[2 * i + 3]
                if (x < min(ax, bx) - d || x > max(ax, bx) + d || y < min(ay, by) - d || y > max(ay, by) + d) continue
                val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
                val t = if (l2 == 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
                if (hypot(x - (ax + t * dx), y - (ay + t * dy)) <= d) return true
            }
            return false
        }
    }
}

/** Where a patrol went: how much of it was by water and how much under trees or across parks. */
object Terrain {
    private val memo = object : LinkedHashMap<Long, TileShapes?>(32, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Long, TileShapes?>) = size > 48
    }

    fun shapes(ctx: Context, x: Int, y: Int, online: Boolean = true): TileShapes? {
        val k = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
        synchronized(memo) { if (memo.containsKey(k)) return memo[k] }
        val b = if (online) Tiles.tile(ctx, x, y) else Tiles.cached(ctx, x, y)
        val s = b?.let { runCatching { TileShapes.of(it) }.getOrNull() }
        if (b != null) synchronized(memo) { memo[k] = s }
        return s
    }

    /** Null when a needed tile couldn't be fetched (try again later). Tracks spread over more than 25 tiles are skipped. */
    fun of(ctx: Context, line: String): Pair<Float, Float>? {
        val pts = Geo.decode(line)
        if (pts.size < 2) return 0f to 0f
        // a sample every ~25 m
        val samples = ArrayList<Pair<Double, Double>>()
        for (i in 1 until pts.size) {
            val (a, b) = pts[i - 1]; val (c, d) = pts[i]
            val n = (Geo.dist(a, b, c, d) / 25).toInt().coerceAtLeast(1)
            for (k in 0 until n) samples += (a + (c - a) * k / n) to (b + (d - b) * k / n)
        }
        val tiles = samples.map { Tiles.tileX(it.second) to Tiles.tileY(it.first) }.toSet()
        if (tiles.size > 25) return 0f to 0f
        val shapes = HashMap<Pair<Int, Int>, TileShapes>()
        for (t in tiles) shapes[t] = shapes(ctx, t.first, t.second) ?: return null
        var water = 0; var green = 0
        for ((la, lo) in samples) {
            val tx = Tiles.tileX(lo); val ty = Tiles.tileY(la)
            val s = shapes[tx to ty] ?: continue
            val x = ((Tiles.fx(lo) - tx) * s.extent).toFloat(); val y = ((Tiles.fy(la) - ty) * s.extent).toFloat()
            val mPerPx = 40_075_016.0 * cos(Math.toRadians(la)) / (1 shl Tiles.Z) / s.extent
            val d = (60 / mPerPx).toFloat()
            if (s.waterways.any { TileShapes.near(it, x, y, d) } || s.water.any { p -> TileShapes.inside(p, x, y) || p.any { r -> TileShapes.near(r, x, y, d) } }) water++
            if (s.green.any { TileShapes.inside(it, x, y) } || s.wood.any { TileShapes.inside(it, x, y) }) green++
        }
        return water.toFloat() / samples.size to green.toFloat() / samples.size
    }
}
