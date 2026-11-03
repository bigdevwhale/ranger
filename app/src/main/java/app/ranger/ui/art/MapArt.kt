package app.ranger.ui.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import app.ranger.data.Geo
import app.ranger.data.Hidden
import app.ranger.data.Terrain
import app.ranger.data.TileShapes
import app.ranger.data.Tiles
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** A track to draw: its points with hidden places cut out, already split where the cut was. */
class Trail(val id: Long, val parts: List<List<Pair<Double, Double>>>, val fresh: Boolean)

/** A seal on the map: where a creature was met. */
class Pin(val lat: Double, val lon: Double, val key: String)

class Chart(val bitmap: Bitmap, val x0: Double, val y0: Double, val scale: Double) {
    /** Bitmap pixel of a coordinate. */
    fun px(lat: Double, lon: Double) = ((Tiles.fx(lon) - x0) * scale).toFloat() to ((Tiles.fy(lat) - y0) * scale).toFloat()
}

object MapArt {
    private const val PAPER = 0xFFEFE3C8.toInt()
    private const val PAPER_D = 0xFFE2D1AC.toInt()
    private const val INK = 0xFF2B2118.toInt()
    private const val WATER = 0xFFB7C8C2.toInt()
    private const val WATER_INK = 0xFF2F5D7C.toInt()
    private const val GREEN = 0xFFC9D2A6.toInt()
    private const val WOOD_INK = 0xFF3D5E34.toInt()
    private const val ROAD = 0xFF8C7A5D.toInt()
    private const val SEAL = 0xFF9E3B26.toInt()

    /** Cuts the parts of a line inside any hidden circle. */
    fun trail(id: Long, line: String, hidden: List<Hidden>, fresh: Boolean = false): Trail {
        val pts = Geo.decode(line)
        val parts = ArrayList<List<Pair<Double, Double>>>(); var cur = ArrayList<Pair<Double, Double>>()
        for (p in pts) {
            val inside = hidden.any { Geo.dist(it.lat, it.lon, p.first, p.second) < it.radiusM }
            if (inside) { if (cur.size > 1) parts += cur; cur = ArrayList() } else cur += p
        }
        if (cur.size > 1) parts += cur
        return Trail(id, parts, fresh)
    }

    /**
     * Draws the Borderlands: parchment, the land the trails passed through (water, woods, parks, roads), the trails
     * in red ink and seals where creatures were met. Land far from any trail stays blank, as on an explorer's map.
     * [maxPx] bounds the longer side. Only cached tiles are used unless [online].
     */
    fun draw(ctx: Context, trails: List<Trail>, pins: List<Pin>, maxPx: Int, online: Boolean, focus: Pair<Double, Double>? = null, radiusKm: Double = 12.0, corridorM: Double = 260.0, home: Pair<Double, Double>? = null, aspect: Double = 0.0): Chart? {
        val all = trails.flatMap { t -> t.parts.flatten() }
        if (all.isEmpty()) return null
        // the region: everything within [radiusKm] of the focus (or of the middle), so a trip abroad doesn't shrink home to a dot
        val mid = focus ?: (all.map { it.first }.sorted()[all.size / 2] to all.map { it.second }.sorted()[all.size / 2])
        val near = all.filter { Geo.dist(mid.first, mid.second, it.first, it.second) < radiusKm * 1000 }.ifEmpty { all }
        val minX = near.minOf { Tiles.fx(it.second) }; val maxX = near.maxOf { Tiles.fx(it.second) }
        val minY = near.minOf { Tiles.fy(it.first) }; val maxY = near.maxOf { Tiles.fy(it.first) }
        val pad = max(maxX - minX, maxY - minY) * 0.12 + 0.15
        var x0 = minX - pad; var x1 = maxX + pad; var y0 = minY - pad; var y1 = maxY + pad
        // stretch the box to the screen's shape (height / width), so the map fills it instead of floating in it
        if (aspect > 0) {
            val cur = (y1 - y0) / (x1 - x0)
            if (cur < aspect) { val add = ((x1 - x0) * aspect - (y1 - y0)) / 2; y0 -= add; y1 += add }
            else { val add = ((y1 - y0) / aspect - (x1 - x0)) / 2; x0 -= add; x1 += add }
        }
        val span = max(x1 - x0, y1 - y0)
        val scale = min(maxPx / span, 1400.0)
        val wPx = ((x1 - x0) * scale).roundToInt().coerceAtLeast(64); val hPx = ((y1 - y0) * scale).roundToInt().coerceAtLeast(64)
        val chart = Chart(Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888), x0, y0, scale)
        val c = Canvas(chart.bitmap)

        // land, then cut to the corridor around the trails
        val land = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val lc = Canvas(land)
        val lat0 = Tiles.lat(y0 + (y1 - y0) / 2)
        val mPerTile = 40_075_016.0 * cos(Math.toRadians(lat0)) / (1 shl Tiles.Z)
        val pxPerM = (scale / mPerTile).toFloat()
        val tiles = HashSet<Pair<Int, Int>>()
        for (p in near) { val tx = floor(Tiles.fx(p.second)).toInt(); val ty = floor(Tiles.fy(p.first)).toInt(); for (dx in -1..1) for (dy in -1..1) tiles += (tx + dx) to (ty + dy) }
        val inView = tiles.filter { (x, y) -> x + 1 >= x0 && x <= x1 && y + 1 >= y0 && y <= y1 }
        if (inView.size <= 120) for ((tx, ty) in inView) {
            val s = Terrain.shapes(ctx, tx, ty, online) ?: continue
            tile(lc, s, ((tx - x0) * scale).toFloat(), ((ty - y0) * scale).toFloat(), (scale / s.extent).toFloat(), pxPerM)
        }
        // the unknown: a sheet of blank parchment over everything, with the trails' corridors cut out of it
        val fog = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val fc = Canvas(fog)
        paper(fc, wPx, hPx)
        val wide = (corridorM * pxPerM).toFloat().coerceAtLeast(10f)
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        // soft edges: several passes, wide and faint to narrow and solid
        for ((k, a) in listOf(1.9f to 50, 1.5f to 70, 1.2f to 110, 1f to 255)) {
            cut.strokeWidth = wide * k; cut.alpha = a
            for (t in trails) for (part in t.parts) fc.drawPath(path(chart, part), cut)
        }
        paper(c, wPx, hPx)
        c.drawBitmap(land, 0f, 0f, null)
        c.drawBitmap(fog, 0f, 0f, null)
        land.recycle(); fog.recycle()

        // trails: older ones thin and faded, fresh ones bold
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = SEAL }
        val base = (1.6f * maxPx / 1400f).coerceIn(1.2f, 4f)
        for (t in trails.filter { !it.fresh }) {
            line.strokeWidth = base; line.alpha = 120; line.pathEffect = null
            for (part in t.parts) c.drawPath(path(chart, part), line)
        }
        for (t in trails.filter { it.fresh }) {
            line.strokeWidth = base * 2.2f; line.alpha = 255; line.pathEffect = DashPathEffect(floatArrayOf(base * 5, base * 2.5f), 0f)
            for (part in t.parts) c.drawPath(path(chart, part), line)
        }
        home?.let { (la, lo) -> val (hx, hy) = chart.px(la, lo); fort(c, hx, hy, base * 7) }
        for (p in pins) {
            val (px, py) = chart.px(p.lat, p.lon)
            val r = base * 5.5f
            c.drawCircle(px, py, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SEAL })
            c.drawCircle(px, py, r * .62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * .14f; color = 0x66FFFFFF })
        }
        border(c, wPx, hPx)
        return chart
    }

    private fun path(chart: Chart, pts: List<Pair<Double, Double>>): Path {
        val p = Path()
        pts.forEachIndexed { i, (la, lo) -> val (x, y) = chart.px(la, lo); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
        return p
    }

    private fun paper(c: Canvas, w: Int, h: Int) {
        c.drawColor(PAPER)
        val r = Random(11); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(w * h / 900) {
            p.color = if (r.nextBoolean()) PAPER_D else 0xFFF5ECD6.toInt(); p.alpha = 40 + r.nextInt(60)
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, .6f + r.nextFloat() * 2.4f, p)
        }
        // a few large stains
        repeat(6) {
            p.color = PAPER_D; p.alpha = 22
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, (w + h) * (.05f + r.nextFloat() * .08f), p)
        }
    }

    private fun tile(c: Canvas, s: TileShapes, ox: Float, oy: Float, k: Float, pxPerM: Float) {
        fun ring(rings: List<FloatArray>): Path { val p = Path(); p.fillType = Path.FillType.EVEN_ODD; for (r in rings) { for (i in 0 until r.size / 2) { val x = ox + r[2 * i] * k; val y = oy + r[2 * i + 1] * k; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }; p.close() }; return p }
        fun line(l: FloatArray): Path { val p = Path(); for (i in 0 until l.size / 2) { val x = ox + l[2 * i] * k; val y = oy + l[2 * i + 1] * k; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }; return p }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = GREEN
        for (g in s.green) c.drawPath(ring(g), fill)
        fill.color = 0xFFBFC995.toInt()
        for (g in s.wood) c.drawPath(ring(g), fill)
        // little trees scattered in woods
        val tree = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WOOD_INK; alpha = 150; style = Paint.Style.STROKE; strokeWidth = max(1f, pxPerM * 3) }
        val step = (45 * pxPerM).coerceAtLeast(7f)
        for (g in s.wood) {
            val outer = g.firstOrNull() ?: continue
            var minx = Float.MAX_VALUE; var miny = Float.MAX_VALUE; var maxx = -Float.MAX_VALUE; var maxy = -Float.MAX_VALUE
            for (i in 0 until outer.size / 2) { minx = min(minx, outer[2 * i]); maxx = max(maxx, outer[2 * i]); miny = min(miny, outer[2 * i + 1]); maxy = max(maxy, outer[2 * i + 1]) }
            val rr = Random((minx * 31 + miny).toInt())
            var yy = miny
            var row = 0
            while (yy < maxy) {
                var xx = minx + (if (row % 2 == 0) 0f else step / k / 2)
                while (xx < maxx) {
                    val jx = xx + (rr.nextFloat() - .5f) * step / k * .5f; val jy = yy + (rr.nextFloat() - .5f) * step / k * .5f
                    if (TileShapes.inside(g, jx, jy)) {
                        val px = ox + jx * k; val py = oy + jy * k; val sz = step * .32f
                        c.drawLine(px - sz * .7f, py + sz * .4f, px, py - sz, tree); c.drawLine(px, py - sz, px + sz * .7f, py + sz * .4f, tree); c.drawLine(px, py + sz * .4f, px, py + sz * .9f, tree)
                    }
                    xx += step / k
                }
                yy += step / k * .8f; row++
            }
        }
        fill.color = WATER
        for (w in s.water) c.drawPath(ring(w), fill)
        val shore = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = WATER_INK; alpha = 150; strokeWidth = max(1f, pxPerM * 2.5f) }
        for (w in s.water) c.drawPath(ring(w), shore)
        val stream = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = WATER_INK; alpha = 170; strokeWidth = max(1.2f, pxPerM * 6); strokeCap = Paint.Cap.ROUND }
        for (l in s.waterways) c.drawPath(line(l), stream)
        val bld = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; alpha = 26 }
        if (pxPerM > .12f) for (b in s.buildings) c.drawPath(ring(listOf(b)), bld)
        val road = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = ROAD; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        for ((cls, l) in s.roads.sortedBy { it.first }) {
            road.strokeWidth = max(.7f, pxPerM * when (cls) { 3 -> 14f; 2 -> 10f; 1 -> 6f; else -> 3f })
            road.alpha = when (cls) { 0 -> 110; 1 -> 140; else -> 190 }
            road.pathEffect = if (cls == 0) DashPathEffect(floatArrayOf(road.strokeWidth * 3, road.strokeWidth * 2), 0f) else null
            c.drawPath(line(l), road)
        }
    }

    private fun fort(c: Canvas, x: Float, y: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
        c.drawRect(x - s * .5f, y - s * .4f, x + s * .5f, y + s * .4f, p)
        for (i in 0 until 3) c.drawRect(x - s * .5f + i * s * .4f, y - s * .62f, x - s * .5f + i * s * .4f + s * .2f, y - s * .4f, p)
        p.color = PAPER; c.drawRect(x - s * .12f, y, x + s * .12f, y + s * .4f, p)
    }

    private fun border(c: Canvas, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = INK; alpha = 200 }
        val m = min(w, h) * .011f
        p.strokeWidth = m * .5f; c.drawRect(m, m, w - m, h - m, p)
        p.strokeWidth = m * .18f; c.drawRect(m * 1.9f, m * 1.9f, w - m * 1.9f, h - m * 1.9f, p)
        // compass rose
        val cx = w - m * 9; val cy = h - m * 10; val r = m * 5
        val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; alpha = 210 }
        val star = Path().apply { moveTo(cx, cy - r); lineTo(cx + r * .2f, cy); lineTo(cx, cy + r); lineTo(cx - r * .2f, cy); close(); moveTo(cx - r, cy); lineTo(cx, cy - r * .2f); lineTo(cx + r, cy); lineTo(cx, cy + r * .2f); close() }
        c.drawPath(star, f)
        p.strokeWidth = m * .15f; c.drawCircle(cx, cy, r * .7f, p)
        f.textSize = r * .7f; f.textAlign = Paint.Align.CENTER; f.isFakeBoldText = true
        c.drawText("N", cx, cy - r * 1.15f, f)
    }
}
