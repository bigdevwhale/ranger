package app.ranger.data

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * OpenStreetMap in z14 vector tiles from OpenFreeMap (a CDN for apps, no key). A tile is fetched once and kept:
 * it tells the terrain of a patrol (water, parks, woods) and is drawn as the parchment map.
 */
object Tiles {
    const val Z = 14
    const val AGENT = "Ranger/1.0 (github.com/bigdevwhale/ranger)"
    private const val TILEJSON = "https://tiles.openfreemap.org/planet"
    @Volatile private var template: String? = null

    fun tileX(lon: Double) = floor((lon + 180) / 360 * (1 shl Z)).toInt()
    fun tileY(lat: Double): Int { val r = Math.toRadians(lat); return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * (1 shl Z)).toInt() }
    /** Fractional tile coordinates, for placing a point inside a tile. */
    fun fx(lon: Double) = (lon + 180) / 360 * (1 shl Z)
    fun fy(lat: Double): Double { val r = Math.toRadians(lat); return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * (1 shl Z) }
    fun lon(tx: Double) = tx / (1 shl Z) * 360 - 180
    fun lat(ty: Double) = Math.toDegrees(atan(sinh(PI * (1 - 2 * ty / (1 shl Z)))))

    fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", AGENT)
        c.setRequestProperty("Accept-Encoding", "gzip")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode} at ${URL(url).host}")
            val raw = c.inputStream.use { it.readBytes() }
            return if (c.contentEncoding == "gzip" || (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte())) GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() } else raw
        } finally { c.disconnect() }
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "tiles").apply { mkdirs() }
    fun cached(ctx: Context, x: Int, y: Int): ByteArray? = File(dir(ctx), "${Z}_${x}_$y.mvt").takeIf { it.exists() }?.readBytes()
    fun has(ctx: Context, x: Int, y: Int) = File(dir(ctx), "${Z}_${x}_$y.mvt").exists()
    fun store(ctx: Context, x: Int, y: Int, b: ByteArray) { File(dir(ctx), "${Z}_${x}_$y.mvt").writeBytes(b) }

    /** From the cache or the network; null when offline. */
    fun tile(ctx: Context, x: Int, y: Int): ByteArray? {
        cached(ctx, x, y)?.let { return it }
        return runCatching {
            val t = template ?: JSONObject(String(get(TILEJSON))).getJSONArray("tiles").getString(0).also { template = it }
            val b = get(t.replace("{z}", "$Z").replace("{x}", "$x").replace("{y}", "$y"))
            store(ctx, x, y, b); b
        }.getOrNull()
    }
}

/** A minimal Mapbox Vector Tile reader: layers, features, tags and geometry commands. */
object Mvt {
    class Feature(val type: Int, val tags: Map<String, Any>, val parts: List<IntArray>)
    class Layer(val name: String, val extent: Int, val features: List<Feature>)

    private class Pb(val b: ByteArray, var p: Int = 0, val end: Int = b.size) {
        fun more() = p < end
        fun varint(): Long { var r = 0L; var s = 0; while (true) { val x = b[p++].toInt(); r = r or ((x and 0x7f).toLong() shl s); if (x and 0x80 == 0) return r; s += 7 } }
        fun sub(): Pb { val n = varint().toInt(); val q = Pb(b, p, p + n); p += n; return q }
        fun skip(wire: Int) { when (wire) { 0 -> varint(); 1 -> p += 8; 2 -> p += varint().toInt(); 5 -> p += 4; else -> error("wire $wire") } }
        fun string(): String { val n = varint().toInt(); val s = String(b, p, n, Charsets.UTF_8); p += n; return s }
        fun packed(): IntArray { val q = sub(); val out = ArrayList<Int>(); while (q.more()) out += q.varint().toInt(); return out.toIntArray() }
        fun fixed64(): Long { var r = 0L; for (i in 0 until 8) r = r or ((b[p + i].toLong() and 0xff) shl (8 * i)); p += 8; return r }
        fun fixed32(): Int { var r = 0; for (i in 0 until 4) r = r or ((b[p + i].toInt() and 0xff) shl (8 * i)); p += 4; return r }
    }

    fun layers(bytes: ByteArray): List<Layer> {
        val out = ArrayList<Layer>()
        val t = Pb(bytes)
        while (t.more()) {
            val key = t.varint().toInt()
            if (key shr 3 == 3 && key and 7 == 2) out += layer(t.sub()) else t.skip(key and 7)
        }
        return out
    }

    private fun layer(l: Pb): Layer {
        var name = ""; var extent = 4096
        val keys = ArrayList<String>(); val values = ArrayList<Any>()
        val raw = ArrayList<Triple<Int, IntArray, IntArray>>()
        while (l.more()) {
            val key = l.varint().toInt()
            when (key shr 3) {
                1 -> name = l.string()
                2 -> {
                    val f = l.sub(); var type = 0; var tags = IntArray(0); var geom = IntArray(0)
                    while (f.more()) {
                        val k = f.varint().toInt()
                        when (k shr 3) { 2 -> tags = f.packed(); 3 -> type = f.varint().toInt(); 4 -> geom = f.packed(); else -> f.skip(k and 7) }
                    }
                    raw += Triple(type, tags, geom)
                }
                3 -> keys += l.string()
                4 -> {
                    val v = l.sub(); var value: Any = ""
                    while (v.more()) {
                        val k = v.varint().toInt()
                        value = when (k shr 3) {
                            1 -> v.string(); 2 -> java.lang.Float.intBitsToFloat(v.fixed32()); 3 -> java.lang.Double.longBitsToDouble(v.fixed64())
                            4, 5 -> v.varint(); 6 -> v.varint().let { (it ushr 1) xor -(it and 1) }; 7 -> v.varint() != 0L
                            else -> { v.skip(k and 7); value }
                        }
                    }
                    values += value
                }
                5 -> extent = l.varint().toInt()
                else -> l.skip(key and 7)
            }
        }
        val features = raw.map { (type, tags, geom) ->
            val m = HashMap<String, Any>()
            var i = 0
            while (i + 1 < tags.size) { keys.getOrNull(tags[i])?.let { k -> values.getOrNull(tags[i + 1])?.let { m[k] = it } }; i += 2 }
            Feature(type, m, geometry(geom))
        }
        return Layer(name, extent, features)
    }

    /** Decodes MoveTo / LineTo / ClosePath into parts (each part a flat x, y array). */
    private fun geometry(g: IntArray): List<IntArray> {
        val parts = ArrayList<IntArray>()
        var cur = ArrayList<Int>()
        var x = 0; var y = 0; var i = 0
        fun zz(v: Int) = (v ushr 1) xor -(v and 1)
        while (i < g.size) {
            val cmd = g[i] and 7; val count = g[i] ushr 3; i++
            when (cmd) {
                1 -> repeat(count) { if (cur.size >= 2) parts += cur.toIntArray(); cur = ArrayList(); x += zz(g[i]); y += zz(g[i + 1]); i += 2; cur += x; cur += y }
                2 -> repeat(count) { x += zz(g[i]); y += zz(g[i + 1]); i += 2; cur += x; cur += y }
                7 -> {}
            }
        }
        if (cur.size >= 2) parts += cur.toIntArray()
        return parts
    }
}
