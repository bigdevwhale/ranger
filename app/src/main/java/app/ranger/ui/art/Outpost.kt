package app.ranger.ui.art

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import app.ranger.BuildConfig
import app.ranger.ui.theme.C
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** What stands at the outpost, from the ranger's record. */
data class Camp(
    val rank: Int,
    /** 0 night … 1 full day, with dawn and dusk in between. */
    val daylight: Float,
    /** Warm colour of the horizon: true around sunrise and sunset. */
    val glow: Boolean,
    /** 0..1, moon light, shown at night. */
    val moon: Double,
    val tents: Int,
    val dog: Boolean,
    val pennants: Int,
    val boots: List<Long>,
    val forge: Boolean,
    /** 0 embers … 1 a roaring fire. */
    val fire: Float,
    /** 0 winter, 1 spring, 2 summer, 3 autumn. */
    val season: Int = 2,
)

object Art { var still = BuildConfig.SHOTS }

/** Milliseconds since start, for flicker; frozen in screenshots. */
@Composable
fun rememberClock(): Long {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { if (!Art.still) while (true) withInfiniteAnimationFrameMillis { t = it } }
    return t
}

@Composable
fun OutpostScene(camp: Camp, modifier: Modifier) {
    val t = rememberClock()
    Canvas(modifier) { outpost(camp, if (Art.still) 1200L else t) }
}

/** A palette for the hour: everything in the scene is a mix of night and day colours. */
private class Tint(private val d: Float, glow: Boolean, winter: Boolean) {
    private fun m(night: Long, day: Long) = lerp(Color(night), Color(day), d.coerceIn(0f, 1f))
    val skyTop = m(0xFF0B1022, 0xFF7FA9C9)
    val skyMid = m(0xFF1A2342, 0xFFB7CDD6)
    val skyLow = if (glow) lerp(m(0xFF3A3050, 0xFFE9D3B0), Color(0xFFE48A52), .55f) else m(0xFF2B3557, 0xFFE4DCC4)
    val far = m(0xFF1F2A47, 0xFF9AAFB5)
    val mid = m(0xFF141C33, 0xFF6F8778)
    val near = m(0xFF0A0F1D, 0xFF3F5240)
    val ground = if (winter) m(0xFF1E2638, 0xFFDDE4E6) else m(0xFF0C111C, 0xFF5D6B45)
    val ground2 = if (winter) m(0xFF141A2A, 0xFFC4CFD3) else m(0xFF080B14, 0xFF4A5737)
    val wood = m(0xFF120E10, 0xFF5A4030)
    val woodHi = m(0xFF2A2026, 0xFF8A6648)
    val ink = m(0xFF06080F, 0xFF2A2620)
    val canvas = m(0xFF2A2830, 0xFFD8C9A6)
    val snow = m(0xFF9AA6BE, 0xFFF4F7F8)
    val night = 1 - d
}

fun DrawScope.outpost(c: Camp, t: Long) {
    val w = size.width; val h = size.height
    val u = h / 100f
    val winter = c.season == 0
    val k = Tint(c.daylight, c.glow, winter)
    val night = k.night
    val fireOn = c.fire.coerceIn(.15f, 1f)
    val fx = w * .5f; val ground = h * .78f

    // ——— sky ———
    drawRect(Brush.verticalGradient(0f to k.skyTop, .55f to k.skyMid, .8f to k.skyLow, startY = 0f, endY = ground))
    val rnd = Random(7)
    if (night > .3f) {
        val a = ((night - .3f) / .7f).coerceIn(0f, 1f)
        // a faint band of the Milky Way
        drawPath(Path().apply { moveTo(-w * .1f, h * .55f); cubicTo(w * .3f, h * .1f, w * .6f, h * .05f, w * 1.1f, -h * .05f); lineTo(w * 1.1f, h * .1f); cubicTo(w * .6f, h * .2f, w * .3f, h * .25f, -w * .1f, h * .7f); close() },
            Brush.linearGradient(listOf(Color.Transparent, Color(0xFFB8C4E8).copy(alpha = .08f * a), Color.Transparent), Offset(0f, h * .6f), Offset(w, 0f)))
        repeat(130) { i ->
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * ground * .85f
            val s = rnd.nextFloat()
            val tw = if (Art.still) .8f else (.55f + .45f * sin(t / (500.0 + i * 13) + i).toFloat())
            val col = if (s > .93f) Color(0xFFFFE9C4) else Color.White
            drawCircle(col.copy(alpha = a * tw * (.25f + s * .7f)), (.25f + s * s * 1.1f) * u * .45f, Offset(x, y))
            if (s > .975f) {
                val r = u * 1.6f
                drawLine(col.copy(alpha = a * .5f * tw), Offset(x - r, y), Offset(x + r, y), u * .12f)
                drawLine(col.copy(alpha = a * .5f * tw), Offset(x, y - r), Offset(x, y + r), u * .12f)
            }
        }
        moon(Offset(w * .8f, h * .2f), h * .065f, c.moon, a)
    } else {
        val sy = h * (.62f - (c.daylight - .3f) * .55f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3D0).copy(alpha = .55f), Color.Transparent), Offset(w * .82f, sy), h * .22f), h * .22f, Offset(w * .82f, sy))
        drawCircle(Color(0xFFFFF6DE), h * .05f, Offset(w * .82f, sy))
        for (i in 0 until 4) {
            val cx = (w * (.15f + i * .28f) + t / 400f * (1 + i * .3f)) % (w * 1.3f) - w * .15f
            val cy = h * (.12f + (i % 2) * .1f)
            for (j in 0 until 4) drawOval(Color.White.copy(alpha = .35f * c.daylight), Offset(cx + j * 6 * u - 10 * u, cy - (if (j % 2 == 0) 2 else 4) * u), Size(14 * u, 6 * u))
        }
    }

    // ——— far mountains, then hills with pines along the ridge ———
    drawPath(ridge(w, h * .5f, h * .08f, 3, 11), k.far)
    if (winter || c.season == 3) drawPath(ridgeCaps(w, h * .5f, h * .08f, 3, 11), k.snow.copy(alpha = if (winter) .8f else .25f))
    drawPath(ridge(w, h * .6f, h * .05f, 2, 23), k.mid)
    forestLine(w, h * .6f, h * .05f, 23, 6.5f * u, k.mid, 61, winter, k.snow)
    if (c.rank >= 8) {
        val bx = w * .28f; val by = h * .545f
        drawLine(k.ink, Offset(bx, by), Offset(bx, by - 6 * u), .6f * u)
        drawCircle(Brush.radialGradient(listOf(C.fire.copy(alpha = .5f), Color.Transparent), Offset(bx, by - 6.5f * u), 6 * u), 6 * u, Offset(bx, by - 6.5f * u))
        drawCircle(Color(0xFFFFD27A), .9f * u, Offset(bx, by - 6.5f * u))
    }

    // ——— the ground and the path to the gate ———
    drawRect(Brush.verticalGradient(listOf(k.ground, k.ground2), ground, h), Offset(0f, ground), Size(w, h - ground))
    drawPath(Path().apply { moveTo(fx - 4 * u, ground); lineTo(fx + 4 * u, ground); lineTo(fx + 26 * u, h); lineTo(fx - 26 * u, h); close() },
        Brush.verticalGradient(listOf(lerp(k.ground, Color(0xFF7A6448), .25f + .3f * c.daylight), lerp(k.ground2, Color(0xFF8A7050), .3f + .3f * c.daylight)), ground, h))

    if (c.rank >= 7) keep(fx, ground, u, k, c.rank >= 9)

    // ——— the wall: stakes, or stone from rank 6 ———
    if (c.rank >= 1) {
        val stone = c.rank >= 6
        val top = ground - (if (stone) 19 else 17) * u
        val gate0 = w * .4f; val gate1 = w * .6f
        var x = -u
        var i = 0
        while (x < w) {
            if (x > gate0 - 1 && x < gate1) { x = gate1; continue }
            val lit = ((1 - abs(x - fx) / (w * .5f)).coerceIn(0f, 1f) * fireOn * night)
            if (stone) {
                val bw = 6 * u
                val st = lerp(k.wood, Color(0xFF4A4A52), .5f)
                drawRect(st, Offset(x, top), Size(bw, ground - top))
                for (r in 0 until 4) drawLine(k.ink.copy(alpha = .5f), Offset(x, top + r * 5 * u), Offset(x + bw, top + r * 5 * u), .25f * u)
                if (i % 2 == 0) drawRect(st, Offset(x, top - 3 * u), Size(bw, 3 * u))
                drawRect(C.fire.copy(alpha = lit * .25f), Offset(x, top), Size(bw * .35f, ground - top))
                x += bw
            } else {
                val sw = 3.6f * u
                val tip = top - Random(i).nextFloat() * 2 * u
                drawPath(Path().apply { moveTo(x, ground); lineTo(x, tip + 2.2f * u); lineTo(x + sw / 2, tip); lineTo(x + sw, tip + 2.2f * u); lineTo(x + sw, ground); close() }, k.wood)
                // the side facing the fire catches its light
                val side = if (x < fx) Offset(x + sw * .62f, tip + 2 * u) else Offset(x, tip + 2 * u)
                drawRect(lerp(k.woodHi, C.fire, lit * .6f).copy(alpha = .35f + lit * .5f), side, Size(sw * .38f, ground - tip - 2 * u))
                if (winter) drawPath(Path().apply { moveTo(x + sw * .1f, tip + 1.9f * u); lineTo(x + sw / 2, tip + .2f * u); lineTo(x + sw * .9f, tip + 1.9f * u); close() }, k.snow)
                x += sw + .3f * u
            }
            i++
        }
        if (!stone) for (y in listOf(top + 5 * u, ground - 5 * u)) {
            drawLine(k.ink.copy(alpha = .8f), Offset(0f, y), Offset(gate0, y), .9f * u); drawLine(k.ink.copy(alpha = .8f), Offset(gate1, y), Offset(w, y), .9f * u)
        }
        c.boots.take(6).forEachIndexed { j, col -> boot(Offset(w * (.07f + j * .05f), top + 6 * u), 3.2f * u, Color(col), k.ink) }
        for (tx in listOf(gate0 - 2 * u, gate1 + 2 * u)) torch(Offset(tx, top - 1 * u), u, t, night)
    }
    if (c.rank >= 3) {
        val g0 = w * .4f; val g1 = w * .6f
        drawRect(k.wood, Offset(g0 - 1.5f * u, ground - 32 * u), Size(3.2f * u, 32 * u)); drawRect(k.wood, Offset(g1 - 1.7f * u, ground - 32 * u), Size(3.2f * u, 32 * u))
        drawRect(k.wood, Offset(g0 - 3 * u, ground - 34 * u), Size(g1 - g0 + 6 * u, 3 * u))
        drawRect(k.woodHi.copy(alpha = .5f), Offset(g0 - 3 * u, ground - 34 * u), Size(g1 - g0 + 6 * u, .7f * u))
        val bw = 5 * u
        drawPath(Path().apply { moveTo(fx - bw, ground - 31 * u); lineTo(fx + bw, ground - 31 * u); lineTo(fx + bw, ground - 16 * u); lineTo(fx, ground - 19.5f * u); lineTo(fx - bw, ground - 16 * u); close() },
            Brush.verticalGradient(listOf(lerp(C.seal, Color.Black, night * .4f), lerp(Color(0xFF6E2418), Color.Black, night * .4f)), ground - 31 * u, ground - 16 * u))
        pineSign(fx, ground - 25 * u, 5 * u, C.gold.copy(alpha = .9f))
    }
    if (c.rank >= 2) tower(w * .13f, ground, u, k, night, t)
    if (c.forge || c.rank >= 4) forge(w * .82f, ground, u, k, night, t)
    if (c.rank >= 5) longhouse(w * .68f, ground, u, k, night)

    // ——— big pines framing the scene ———
    for ((px, s) in listOf(-w * .02f to 1.25f, w * .05f to .9f, w * .97f to 1.15f, w * 1.03f to 1.4f)) pine(Offset(px, ground + 2 * u), 30 * u * s, k.near, winter, k.snow)

    drawOval(Brush.radialGradient(listOf(C.fire.copy(alpha = (.28f + .25f * night) * fireOn), Color.Transparent), Offset(fx, ground + 6 * u), 34 * u), Offset(fx - 40 * u, ground - 6 * u), Size(80 * u, 26 * u))

    val tentXs = listOf(.31f, .24f, .69f, .76f, .19f)
    for (i in 0 until (1 + c.tents).coerceAtMost(5)) tent(w * tentXs[i], ground + 2 * u, if (i == 0) 1f else .82f, u, k, fireOn, night)

    if (c.pennants > 0) {
        val x0 = w * .36f; val x1 = w * .64f; val y0 = ground - 37 * u
        drawPath(Path().apply { moveTo(x0, y0); quadraticTo(fx, y0 + 6 * u, x1, y0) }, k.ink, style = Stroke(.35f * u))
        for (i in 0 until c.pennants.coerceAtMost(9)) {
            val f = (i + .5f) / 9; val px = x0 + (x1 - x0) * f; val py = y0 + 6 * u * 2 * f * (1 - f)
            val col = listOf(C.seal, C.gold, Color(0xFF3D5E34))[i % 3]
            drawPath(Path().apply { moveTo(px - 1.3f * u, py); lineTo(px + 1.3f * u, py); lineTo(px, py + 3.8f * u); close() }, lerp(col, Color.Black, night * .35f))
        }
    }

    campfire(fx, ground + 4 * u, u, fireOn, t)
    if (c.dog) dog(fx + 12 * u, ground + 5 * u, u, k, fireOn * night)
    ranger(fx - 12.5f * u, ground + 5 * u, u, k, fireOn * night)

    // ——— the season in the air ———
    if (winter) repeat(70) { i ->
        val r = Random(i * 7 + 3)
        val x = ((r.nextFloat() * w + sin(t / 900.0 + i).toFloat() * 3 * u) % w + w) % w
        val y = (r.nextFloat() * h + t / 30f * (.6f + r.nextFloat())) % h
        drawCircle(Color.White.copy(alpha = .7f), (.25f + r.nextFloat() * .4f) * u, Offset(x, y))
    } else if (c.season == 3) repeat(14) { i ->
        val r = Random(i * 11 + 1)
        val x = (r.nextFloat() * w + t / 50f * (.5f + r.nextFloat())) % w
        val y = (r.nextFloat() * h * .8f + t / 70f) % (h * .9f)
        drawOval(listOf(Color(0xFFC0602A), Color(0xFFD9A03A), Color(0xFF8A3A22))[i % 3].copy(alpha = .8f), Offset(x, y), Size(1.2f * u, .7f * u))
    } else if (night > .5f) repeat(if (c.season == 2) 16 else 6) { i ->
        val r = Random(i * 5 + 2)
        val x = w * r.nextFloat() + sin(t / (700.0 + i * 40) + i).toFloat() * 6 * u
        val y = ground - 3 * u - r.nextFloat() * 22 * u + cos(t / (900.0 + i * 30)).toFloat() * 3 * u
        val on = if (Art.still) .8f else (.5f + .5f * sin(t / 300.0 + i * 2).toFloat()).coerceAtLeast(0f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE6F59A).copy(alpha = .35f * on), Color.Transparent), Offset(x, y), 1.4f * u), 1.4f * u, Offset(x, y))
        drawCircle(Color(0xFFF2FFC0).copy(alpha = .9f * on), .35f * u, Offset(x, y))
    }

    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .18f + .25f * night)), Offset(w / 2, h * .55f), w * .75f))
}

// ——— pieces ———

private fun ridgeY(x: Float, w: Float, ph: List<Float>): Float {
    var y = 0f
    for (o in ph.indices) y += sin(x / w * PI * (1.3 + o * 2.1) + ph[o]).toFloat() / (o + 1)
    return y
}

private fun ridge(w: Float, base: Float, amp: Float, octaves: Int, seed: Int): Path {
    val r = Random(seed)
    val ph = List(octaves) { r.nextFloat() * 6f }
    return Path().apply {
        moveTo(0f, base)
        var x = 0f
        while (x <= w) { lineTo(x, base - amp * (ridgeY(x, w, ph) * .6f + .4f)); x += w / 80 }
        lineTo(w, base + amp * 6); lineTo(0f, base + amp * 6); close()
    }
}

private fun ridgeCaps(w: Float, base: Float, amp: Float, octaves: Int, seed: Int): Path {
    val full = ridge(w, base, amp, octaves, seed)
    val lower = Path().apply { addRect(Rect(0f, base - amp * .25f, w, base + amp * 8)) }
    return Path().apply { op(full, lower, PathOperation.Difference) }
}

private fun DrawScope.forestLine(w: Float, base: Float, amp: Float, seed: Int, size: Float, col: Color, n: Int, winter: Boolean, snow: Color) {
    val r = Random(seed)
    val ph = List(2) { r.nextFloat() * 6f }
    val rr = Random(seed * 3)
    for (i in 0 until n) {
        val x = w * i / n + rr.nextFloat() * w / n
        pine(Offset(x, base - amp * (ridgeY(x, w, ph) * .6f + .4f) + size * .2f), size * (.6f + rr.nextFloat() * .7f), col, winter, snow, simple = true)
    }
}

private fun DrawScope.pine(at: Offset, hgt: Float, col: Color, winter: Boolean, snow: Color, simple: Boolean = false) {
    val tiers = if (simple) 3 else 5
    val wd = hgt * .42f
    drawRect(col, Offset(at.x - hgt * .02f, at.y - hgt * .18f), Size(hgt * .04f, hgt * .2f))
    for (i in 0 until tiers) {
        val f = i / tiers.toFloat()
        val tierW = wd * (1 - f * .75f)
        val bottom = at.y - hgt * (.12f + f * .78f)
        val top = bottom - hgt * (.36f - f * .1f)
        drawPath(Path().apply { moveTo(at.x - tierW / 2, bottom); lineTo(at.x, top); lineTo(at.x + tierW / 2, bottom); quadraticTo(at.x, bottom - hgt * .05f, at.x - tierW / 2, bottom); close() }, col)
        if (winter && !simple) drawPath(Path().apply { moveTo(at.x - tierW * .22f, top + (bottom - top) * .45f); lineTo(at.x, top); lineTo(at.x + tierW * .22f, top + (bottom - top) * .45f); close() }, snow.copy(alpha = .85f))
    }
    if (winter && simple) drawPath(Path().apply { moveTo(at.x - wd * .08f, at.y - hgt * .82f); lineTo(at.x, at.y - hgt * .99f); lineTo(at.x + wd * .08f, at.y - hgt * .82f); close() }, snow.copy(alpha = .7f))
}

private fun DrawScope.moon(o: Offset, r: Float, lit: Double, a: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF3EBD0).copy(alpha = .28f * a), Color.Transparent), o, r * 3.2f), r * 3.2f, o)
    drawCircle(Color(0xFF39405A).copy(alpha = a * .6f), r, o)
    if (lit <= .03) return
    val disc = Path().apply { addOval(Rect(o, r)) }
    val shadowX = o.x + (r * 2 * lit).toFloat() * if (lit < .5) 1f else -1f
    val cut = Path().apply { addOval(Rect(Offset(shadowX, o.y - r * .04f), r * 1.02f)) }
    val shape = if (lit > .97) disc else Path().apply { op(disc, cut, PathOperation.Difference) }
    drawPath(shape, Brush.radialGradient(listOf(Color(0xFFFFF8E2), Color(0xFFE9DDB8)), Offset(o.x - r * .3f, o.y - r * .3f), r * 1.4f), alpha = a)
    for ((dx, dy, s) in listOf(Triple(-.3f, -.1f, .16f), Triple(.2f, .3f, .11f), Triple(.05f, -.4f, .08f))) {
        val p = Offset(o.x + r * dx, o.y + r * dy)
        val inLit = lit > .97 || kotlin.math.hypot(p.x - shadowX, p.y - o.y) > r * 1.12f
        if (inLit) drawCircle(Color(0xFFCFC3A0).copy(alpha = a * .5f), r * s, p)
    }
}

private fun DrawScope.torch(at: Offset, u: Float, t: Long, night: Float) {
    drawLine(Color(0xFF2A1C14), at, at.copy(y = at.y + 6 * u), .8f * u)
    val fl = if (Art.still) 1f else .85f + .15f * sin(t / 70.0 + at.x).toFloat()
    drawCircle(Brush.radialGradient(listOf(C.fire.copy(alpha = .45f * (.3f + night)), Color.Transparent), at, 9 * u), 9 * u, at)
    drawPath(Path().apply { moveTo(at.x - 1 * u, at.y); quadraticTo(at.x - 1 * u, at.y - 2.5f * u * fl, at.x, at.y - 3.4f * u * fl); quadraticTo(at.x + 1 * u, at.y - 2.2f * u, at.x + 1 * u, at.y); close() }, Color(0xFFFFB347))
}

private fun DrawScope.tower(x: Float, ground: Float, u: Float, k: Tint, night: Float, t: Long) {
    val legTop = ground - 44 * u
    for ((a, b) in listOf(-6f to -3.5f, 6f to 3.5f)) drawLine(k.wood, Offset(x + a * u, ground), Offset(x + b * u, legTop), 1.6f * u)
    for (i in 0 until 3) {
        val y0 = ground - (8 + i * 12) * u; val y1 = y0 - 10 * u
        val s0 = 6f - (i * 12 + 8) / 44f * 2.5f; val s1 = 6f - (i * 12 + 18) / 44f * 2.5f
        drawLine(k.wood, Offset(x - s0 * u, y0), Offset(x + s1 * u, y1), .7f * u)
        drawLine(k.wood, Offset(x + s0 * u, y0), Offset(x - s1 * u, y1), .7f * u)
        drawLine(k.wood, Offset(x - s0 * u, y0), Offset(x + s0 * u, y0), .7f * u)
    }
    val hx = x - 8 * u; val hy = legTop - 10 * u
    drawRect(k.wood, Offset(hx, hy), Size(16 * u, 10 * u))
    for (i in 1 until 5) drawLine(k.ink.copy(alpha = .4f), Offset(hx, hy + i * 2 * u), Offset(hx + 16 * u, hy + i * 2 * u), .2f * u)
    drawRect(k.wood, Offset(hx - 1.5f * u, legTop - .5f * u), Size(19 * u, 1.6f * u))
    drawRect(lerp(Color(0xFF6B5A3A), Color(0xFFFFC266), night), Offset(x - 2.5f * u, hy + 2.5f * u), Size(5 * u, 4.5f * u))
    drawLine(k.wood, Offset(x, hy + 2.5f * u), Offset(x, hy + 7 * u), .4f * u)
    if (night > .3f) drawCircle(Brush.radialGradient(listOf(C.fire.copy(alpha = .3f * night), Color.Transparent), Offset(x, hy + 5 * u), 14 * u), 14 * u, Offset(x, hy + 5 * u))
    drawPath(Path().apply { moveTo(hx - 3 * u, hy); lineTo(x, hy - 9 * u); lineTo(hx + 19 * u, hy); close() }, k.ink)
    for (i in 1 until 4) drawLine(k.woodHi.copy(alpha = .35f), Offset(hx - 3 * u + i * 2.2f * u, hy - i * 2.1f * u), Offset(hx + 19 * u - i * 2.2f * u, hy - i * 2.1f * u), .25f * u)
    drawLine(k.ink, Offset(x, hy - 9 * u), Offset(x, hy - 16 * u), .4f * u)
    val wave = if (Art.still) 0f else sin(t / 300.0).toFloat() * .6f * u
    drawPath(Path().apply { moveTo(x, hy - 16 * u); quadraticTo(x + 3 * u, hy - 16.5f * u + wave, x + 6 * u, hy - 15 * u); lineTo(x, hy - 12.5f * u); close() }, lerp(C.seal, Color.Black, night * .4f))
}

private fun DrawScope.keep(x: Float, ground: Float, u: Float, k: Tint, flag: Boolean) {
    val stone = lerp(k.mid, Color(0xFF55555C), .35f)
    drawRect(stone, Offset(x - 12 * u, ground - 52 * u), Size(24 * u, 52 * u))
    for (i in 0 until 5) drawRect(stone, Offset(x - 12 * u + i * 5.2f * u, ground - 56 * u), Size(3.2f * u, 4 * u))
    for (r in 0 until 10) drawLine(k.ink.copy(alpha = .25f), Offset(x - 12 * u, ground - 52 * u + r * 5 * u), Offset(x + 12 * u, ground - 52 * u + r * 5 * u), .2f * u)
    drawRect(Color(0xFFFFC266).copy(alpha = .85f), Offset(x - 1.5f * u, ground - 44 * u), Size(3 * u, 5 * u))
    if (flag) {
        drawLine(k.ink, Offset(x, ground - 56 * u), Offset(x, ground - 70 * u), .5f * u)
        drawPath(Path().apply { moveTo(x, ground - 70 * u); lineTo(x + 11 * u, ground - 67 * u); lineTo(x, ground - 63.5f * u); close() }, C.gold)
    }
}

private fun DrawScope.forge(x: Float, ground: Float, u: Float, k: Tint, night: Float, t: Long) {
    drawRect(k.wood, Offset(x - 9 * u, ground - 13 * u), Size(18 * u, 13 * u))
    for (i in 1 until 6) drawLine(k.ink.copy(alpha = .35f), Offset(x - 9 * u + i * 3 * u, ground - 13 * u), Offset(x - 9 * u + i * 3 * u, ground), .2f * u)
    drawPath(Path().apply { moveTo(x - 11.5f * u, ground - 13 * u); lineTo(x, ground - 21 * u); lineTo(x + 11.5f * u, ground - 13 * u); close() }, k.ink)
    drawRect(lerp(k.wood, Color(0xFF55555C), .4f), Offset(x + 4 * u, ground - 27 * u), Size(3.6f * u, 10 * u))
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFC266), C.ember), ground - 9 * u, ground), Offset(x - 4 * u, ground - 9 * u), Size(6 * u, 9 * u))
    drawCircle(Brush.radialGradient(listOf(C.ember.copy(alpha = .35f * (.3f + night)), Color.Transparent), Offset(x - 1 * u, ground - 4 * u), 12 * u), 12 * u, Offset(x - 1 * u, ground - 4 * u))
    for (i in 0 until 6) {
        val p = ((t / 40 + i * 17) % 100) / 100f
        drawCircle(lerp(Color(0xFF8C8C8C), Color(0xFF4A4E5E), night).copy(alpha = (1 - p) * .3f), (1.5f + p * 5) * u, Offset(x + 6 * u + p * 9 * u + sin(p * 6 + i) * u, ground - 28 * u - p * 26 * u))
    }
}

private fun DrawScope.longhouse(x: Float, ground: Float, u: Float, k: Tint, night: Float) {
    drawRect(k.wood, Offset(x - 13 * u, ground - 10 * u), Size(20 * u, 10 * u))
    for (i in 1 until 4) drawLine(k.ink.copy(alpha = .35f), Offset(x - 13 * u, ground - 10 * u + i * 2.5f * u), Offset(x + 7 * u, ground - 10 * u + i * 2.5f * u), .2f * u)
    drawPath(Path().apply { moveTo(x - 15 * u, ground - 10 * u); lineTo(x - 3 * u, ground - 18 * u); lineTo(x + 9 * u, ground - 10 * u); close() }, k.ink)
    drawRect(lerp(Color(0xFF6B5A3A), Color(0xFFFFC266), night), Offset(x - 7 * u, ground - 7.5f * u), Size(3 * u, 3 * u))
}

private fun DrawScope.tent(x: Float, base: Float, s: Float, u: Float, k: Tint, fire: Float, night: Float) {
    val hw = 11 * u * s; val ht = 15 * u * s
    drawPath(Path().apply { moveTo(x - hw, base); lineTo(x, base - ht); lineTo(x + hw, base); close() },
        Brush.horizontalGradient(listOf(lerp(k.canvas, Color.Black, .35f), k.canvas, lerp(k.canvas, Color.Black, .2f)), x - hw, x + hw))
    drawLine(k.ink.copy(alpha = .4f), Offset(x, base - ht), Offset(x, base), .3f * u)
    drawPath(Path().apply { moveTo(x - 2.6f * u * s, base); lineTo(x, base - 8 * u * s); lineTo(x + 2.6f * u * s, base); close() }, lerp(Color(0xFF2A1C14), Color(0xFFFFB45A), .3f + .6f * fire * night))
    drawLine(k.ink.copy(alpha = .7f), Offset(x, base - ht), Offset(x, base - ht - 2 * u * s), .5f * u)
    drawLine(k.ink.copy(alpha = .5f), Offset(x - hw, base), Offset(x - hw - 3 * u * s, base + .5f * u), .2f * u)
    drawLine(k.ink.copy(alpha = .5f), Offset(x + hw, base), Offset(x + hw + 3 * u * s, base + .5f * u), .2f * u)
}

private fun DrawScope.campfire(x: Float, y: Float, u: Float, f: Float, t: Long) {
    drawCircle(Brush.radialGradient(listOf(C.fire.copy(alpha = .45f * f), C.fire.copy(alpha = .12f * f), Color.Transparent), Offset(x, y - 5 * u), 30 * u), 30 * u, Offset(x, y - 5 * u))
    for (i in 0 until 9) {
        val a = PI * i / 8
        val sx = x + cos(a).toFloat() * 7 * u; val sy = y + sin(a).toFloat() * 1.2f * u
        drawOval(lerp(Color(0xFF55504A), C.fire, .3f * f), Offset(sx - 1.4f * u, sy - 1 * u), Size(2.8f * u, 2 * u))
    }
    drawLine(Color(0xFF3A2818), Offset(x - 6 * u, y), Offset(x + 5 * u, y - 2.2f * u), 1.6f * u, StrokeCap.Round)
    drawLine(Color(0xFF4A3220), Offset(x + 6 * u, y), Offset(x - 5 * u, y - 2.2f * u), 1.6f * u, StrokeCap.Round)
    for (i in 0 until 4) {
        val fl = if (Art.still) .85f - i * .05f else (.75f + .25f * sin(t / (80.0 + i * 31) + i * 1.7).toFloat())
        val hh = (12 - i * 2.6f) * u * f * fl
        val ww = (5f - i * .9f) * u
        val lean = if (Art.still) 0f else sin(t / 400.0 + i).toFloat() * .8f * u
        val col = listOf(Color(0xFFB8361E), C.ember, C.fire, Color(0xFFFFEBA8))[i]
        drawPath(Path().apply { moveTo(x - ww, y - 1 * u); cubicTo(x - ww, y - hh * .5f, x - ww * .2f + lean, y - hh * .7f, x + lean, y - hh); cubicTo(x + ww * .3f + lean, y - hh * .6f, x + ww, y - hh * .45f, x + ww, y - 1 * u); close() }, col)
    }
    if (!Art.still) for (i in 0 until 7) {
        val p = ((t / 28 + i * 37) % 100) / 100f
        drawCircle(Color(0xFFFFC266).copy(alpha = (1 - p) * f), .45f * u, Offset(x + sin(i * 2.1 + p * 7).toFloat() * 4 * u, y - 10 * u - p * 32 * u))
    }
}

private fun DrawScope.ranger(x: Float, y: Float, u: Float, k: Tint, rim: Float) {
    drawRoundRect(Color(0xFF3A2818), Offset(x - 6 * u, y - 2.6f * u), Size(11 * u, 2.6f * u), CornerRadius(1.3f * u))
    drawPath(Path().apply {
        moveTo(x - 4.5f * u, y - 2 * u); lineTo(x - 3.4f * u, y - 9 * u); quadraticTo(x - 2.8f * u, y - 13 * u, x, y - 13.5f * u)
        quadraticTo(x + 2.8f * u, y - 13 * u, x + 3.4f * u, y - 9 * u); lineTo(x + 5.5f * u, y - 2 * u); close()
    }, k.ink)
    drawPath(Path().apply { moveTo(x - 3 * u, y - 12.5f * u); quadraticTo(x - 3 * u, y - 17.5f * u, x + .5f * u, y - 18.5f * u); quadraticTo(x + 3.2f * u, y - 16 * u, x + 2.8f * u, y - 12.5f * u); close() }, k.ink)
    // the edge facing the fire catches the light
    drawPath(Path().apply { moveTo(x + 2.8f * u, y - 12.5f * u); quadraticTo(x + 3.2f * u, y - 16 * u, x + .5f * u, y - 18.5f * u) }, C.fire.copy(alpha = .8f * rim), style = Stroke(.45f * u))
    drawPath(Path().apply { moveTo(x + 3.4f * u, y - 9 * u); lineTo(x + 5.5f * u, y - 2 * u) }, C.fire.copy(alpha = .7f * rim), style = Stroke(.45f * u))
    drawLine(Color(0xFF2A1C14), Offset(x - 6.5f * u, y), Offset(x - 2 * u, y - 20 * u), .7f * u)
    drawCircle(Color(0xFF6FA7C9).copy(alpha = .9f), .7f * u, Offset(x - 2 * u, y - 20.3f * u))
}

private fun DrawScope.dog(x: Float, y: Float, u: Float, k: Tint, rim: Float) {
    drawOval(k.ink, Offset(x - 5 * u, y - 3.8f * u), Size(10 * u, 4.4f * u))
    drawCircle(k.ink, 2.3f * u, Offset(x - 4.6f * u, y - 2.6f * u))
    drawPath(Path().apply { moveTo(x - 6 * u, y - 4 * u); lineTo(x - 5.6f * u, y - 6.6f * u); lineTo(x - 4.2f * u, y - 4.3f * u); close() }, k.ink)
    drawPath(Path().apply { moveTo(x + 5 * u, y - 1.5f * u); quadraticTo(x + 7 * u, y - 1 * u, x + 6 * u, y + .3f * u) }, k.ink, style = Stroke(.8f * u, cap = StrokeCap.Round))
    drawArc(C.fire.copy(alpha = .6f * rim), 180f, 90f, false, Offset(x - 5 * u, y - 3.8f * u), Size(10 * u, 4.4f * u), style = Stroke(.35f * u))
}

/** A tiny boot hung by its laces. */
private fun DrawScope.boot(at: Offset, s: Float, col: Color, ink: Color) {
    drawLine(ink.copy(alpha = .8f), at.copy(y = at.y - s * 1.2f), at, s * .15f)
    drawPath(Path().apply {
        moveTo(at.x - s * .3f, at.y); lineTo(at.x + s * .3f, at.y); lineTo(at.x + s * .35f, at.y + s * 1.1f)
        quadraticTo(at.x + s * 1.1f, at.y + s * 1.2f, at.x + s * 1.1f, at.y + s * 1.6f); lineTo(at.x - s * .4f, at.y + s * 1.6f); close()
    }, col)
    drawLine(Color.White.copy(alpha = .5f), Offset(at.x - s * .4f, at.y + s * 1.45f), Offset(at.x + s * 1.1f, at.y + s * 1.45f), s * .12f)
}

/** The outpost's sign on the banner: a pine. */
private fun DrawScope.pineSign(x: Float, y: Float, s: Float, col: Color) {
    drawPath(Path().apply { moveTo(x, y - s * .5f); lineTo(x - s * .32f, y + s * .15f); lineTo(x + s * .32f, y + s * .15f); close() }, col)
    drawPath(Path().apply { moveTo(x, y - s * .2f); lineTo(x - s * .4f, y + s * .45f); lineTo(x + s * .4f, y + s * .45f); close() }, col)
}
