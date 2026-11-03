package app.ranger.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import app.ranger.domain.Creature
import app.ranger.domain.Glyph
import app.ranger.ui.theme.C
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A creature's seal: once met, a wax disc in its ink with its marks pressed in; while tracking, a pencilled ring
 * with the marks half-drawn; unknown, an empty dotted ring with a question.
 */
@Composable
fun Seal(c: Creature, stage: Int, modifier: Modifier) {
    Canvas(modifier) { seal(c, stage) }
}

fun DrawScope.seal(c: Creature, stage: Int) {
    val r = size.minDimension / 2
    val o = center
    val ink = Color(c.ink)
    when (stage) {
        3 -> {
            // wax with a wobbly rim
            val wax = Path()
            val n = 22
            for (i in 0..n * 4) {
                val a = 2 * PI * i / (n * 4)
                val rr = r * (0.93f + 0.05f * sin(a * n).toFloat() + 0.02f * sin(a * 3 + c.key.hashCode()).toFloat())
                val p = Offset(o.x + rr * cos(a).toFloat(), o.y + rr * sin(a).toFloat())
                if (i == 0) wax.moveTo(p.x, p.y) else wax.lineTo(p.x, p.y)
            }
            wax.close()
            drawPath(wax, ink)
            drawCircle(Color.Black.copy(alpha = .18f), r * .74f, o, style = Stroke(r * .05f))
            drawCircle(Color.White.copy(alpha = .14f), r * .70f, o.copy(y = o.y - r * .02f), style = Stroke(r * .025f))
            if (c.tier >= 2) for (i in 0 until (if (c.tier == 3) 12 else 8)) {
                val a = 2 * PI * i / (if (c.tier == 3) 12 else 8)
                drawCircle(Color.White.copy(alpha = .35f), r * .035f, Offset(o.x + r * .82f * cos(a).toFloat(), o.y + r * .82f * sin(a).toFloat()))
            }
            marks(c, o, r * .62f, Color(0xFFF6EBD3), 1f)
        }
        0 -> {
            drawCircle(C.line, r * .9f, o, style = Stroke(r * .05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * .06f, r * .12f)), cap = StrokeCap.Round))
            question(o, r * .38f, C.line)
        }
        else -> {
            drawCircle(ink.copy(alpha = .08f), r * .9f, o)
            drawCircle(ink.copy(alpha = .55f), r * .9f, o, style = Stroke(r * .045f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * .22f, r * .1f))))
            if (stage == 2) drawCircle(ink.copy(alpha = .4f), r * .76f, o, style = Stroke(r * .03f))
            marks(c, o, r * .6f, ink.copy(alpha = if (stage == 2) .75f else .45f), if (stage == 2) .8f else .55f)
        }
    }
}

private fun DrawScope.marks(c: Creature, o: Offset, r: Float, col: Color, weight: Float) {
    val g = c.glyphs
    if (g.size == 1) glyph(g[0], o, r * 1.7f, col, weight)
    else {
        glyph(g[0], Offset(o.x, o.y - r * .18f), r * 1.35f, col, weight)
        glyph(g[1], Offset(o.x, o.y + r * .68f), r * .62f, col, weight)
    }
}

private fun DrawScope.question(o: Offset, s: Float, col: Color) {
    val p = Path().apply {
        moveTo(o.x - s * .5f, o.y - s * .45f)
        cubicTo(o.x - s * .5f, o.y - s * 1.05f, o.x + s * .55f, o.y - s * 1.05f, o.x + s * .5f, o.y - s * .4f)
        cubicTo(o.x + s * .45f, o.y - s * .05f, o.x, o.y, o.x, o.y + s * .35f)
    }
    drawPath(p, col, style = Stroke(s * .22f, cap = StrokeCap.Round))
    drawCircle(col, s * .14f, Offset(o.x, o.y + s * .8f))
}

/** One mark in a square [s] wide centred on [o]. Line art in [col]. */
fun DrawScope.glyph(g: Glyph, o: Offset, s: Float, col: Color, weight: Float = 1f) {
    val w = s * .085f * weight.coerceAtLeast(.6f)
    val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun P(block: Path.() -> Unit) = Path().apply(block)
    fun x(f: Float) = o.x + (f - .5f) * s
    fun y(f: Float) = o.y + (f - .5f) * s
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(col, Offset(x(x1), y(y1)), Offset(x(x2), y(y2)), w, StrokeCap.Round)
    fun wave(yy: Float, x0: Float, x1: Float, amp: Float, n: Int) = P {
        moveTo(x(x0), y(yy)); val step = (x1 - x0) / n
        for (i in 0 until n) quadraticTo(x(x0 + step * (i + .5f)), y(yy + if (i % 2 == 0) -amp else amp), x(x0 + step * (i + 1)), y(yy))
    }
    when (g) {
        Glyph.WAVE -> { drawPath(wave(.38f, .1f, .9f, .12f, 4), col, style = st); drawPath(wave(.62f, .1f, .9f, .12f, 4), col, style = st) }
        Glyph.TREE -> {
            drawPath(P { moveTo(x(.5f), y(.08f)); lineTo(x(.22f), y(.48f)); lineTo(x(.36f), y(.48f)); lineTo(x(.14f), y(.78f)); lineTo(x(.86f), y(.78f)); lineTo(x(.64f), y(.48f)); lineTo(x(.78f), y(.48f)); close() }, col, style = st)
            line(.5f, .78f, .5f, .94f)
        }
        Glyph.CRESCENT -> {
            val a = P { addOval(Rect(Offset(x(.5f), y(.5f)), s * .38f)) }
            val b = P { addOval(Rect(Offset(x(.64f), y(.4f)), s * .32f)) }
            drawPath(Path().apply { op(a, b, PathOperation.Difference) }, col)
        }
        Glyph.SUNRISE, Glyph.SUNSET -> {
            drawArc(col, 180f, 180f, false, Offset(x(.26f), y(.42f)), Size(s * .48f, s * .48f), style = st)
            line(.08f, .66f, .92f, .66f)
            if (g == Glyph.SUNRISE) for (i in 0..4) { val a = PI + PI * (i + .5) / 5; line(.5f + .34f * cos(a).toFloat(), .66f + .34f * sin(a).toFloat(), .5f + .46f * cos(a).toFloat(), .66f + .46f * sin(a).toFloat()) }
            else { line(.24f, .8f, .76f, .8f); line(.36f, .92f, .64f, .92f) }
        }
        Glyph.PEAK -> {
            drawPath(P { moveTo(x(.04f), y(.84f)); lineTo(x(.38f), y(.2f)); lineTo(x(.56f), y(.52f)); lineTo(x(.7f), y(.34f)); lineTo(x(.96f), y(.84f)); close() }, col, style = st)
            drawPath(P { moveTo(x(.28f), y(.4f)); lineTo(x(.38f), y(.48f)); lineTo(x(.46f), y(.36f)) }, col, style = st)
        }
        Glyph.DROPS -> for ((cx, cy, k) in listOf(Triple(.3f, .36f, 1f), Triple(.68f, .3f, .8f), Triple(.52f, .68f, .9f))) {
            val r = .13f * k
            drawPath(P { moveTo(x(cx), y(cy - r * 1.9f)); cubicTo(x(cx + r * 1.1f), y(cy - r * .4f), x(cx + r), y(cy + r), x(cx), y(cy + r)); cubicTo(x(cx - r), y(cy + r), x(cx - r * 1.1f), y(cy - r * .4f), x(cx), y(cy - r * 1.9f)) }, col)
        }
        Glyph.MIST -> { drawPath(wave(.3f, .1f, .74f, .06f, 3), col, style = st); drawPath(wave(.5f, .26f, .92f, .06f, 3), col, style = st); drawPath(wave(.7f, .08f, .7f, .06f, 3), col, style = st) }
        Glyph.FLAKE -> for (i in 0 until 6) {
            val a = PI / 3 * i - PI / 2
            val ex = .5f + .42f * cos(a).toFloat(); val ey = .5f + .42f * sin(a).toFloat()
            line(.5f, .5f, ex, ey)
            val mx = .5f + .27f * cos(a).toFloat(); val my = .5f + .27f * sin(a).toFloat()
            for (d in listOf(-1, 1)) { val b = a + d * PI / 4; line(mx, my, mx + .13f * cos(b).toFloat(), my + .13f * sin(b).toFloat()) }
        }
        Glyph.FLAME -> {
            drawPath(P { moveTo(x(.5f), y(.06f)); cubicTo(x(.62f), y(.3f), x(.86f), y(.42f), x(.8f), y(.68f)); cubicTo(x(.76f), y(.86f), x(.62f), y(.94f), x(.5f), y(.94f)); cubicTo(x(.32f), y(.94f), x(.2f), y(.82f), x(.2f), y(.64f)); cubicTo(x(.2f), y(.46f), x(.34f), y(.38f), x(.38f), y(.22f)); cubicTo(x(.44f), y(.32f), x(.46f), y(.4f), x(.44f), y(.48f)); cubicTo(x(.5f), y(.34f), x(.52f), y(.2f), x(.5f), y(.06f)) }, col, style = st)
            drawPath(P { moveTo(x(.5f), y(.56f)); cubicTo(x(.62f), y(.68f), x(.62f), y(.86f), x(.5f), y(.86f)); cubicTo(x(.4f), y(.86f), x(.38f), y(.72f), x(.5f), y(.56f)) }, col)
        }
        Glyph.GUST -> {
            drawPath(P { moveTo(x(.06f), y(.38f)); lineTo(x(.62f), y(.38f)); cubicTo(x(.82f), y(.38f), x(.82f), y(.12f), x(.64f), y(.14f)) }, col, style = st)
            drawPath(P { moveTo(x(.14f), y(.56f)); lineTo(x(.78f), y(.56f)); cubicTo(x(.98f), y(.56f), x(.98f), y(.84f), x(.78f), y(.82f)) }, col, style = st)
            line(.06f, .74f, .5f, .74f)
        }
        Glyph.BOLT -> drawPath(P { moveTo(x(.6f), y(.04f)); lineTo(x(.26f), y(.54f)); lineTo(x(.48f), y(.54f)); lineTo(x(.38f), y(.96f)); lineTo(x(.76f), y(.42f)); lineTo(x(.54f), y(.42f)); close() }, col)
        Glyph.ROAD -> {
            drawPath(P { moveTo(x(.18f), y(.96f)); cubicTo(x(.1f), y(.6f), x(.86f), y(.6f), x(.7f), y(.3f)); cubicTo(x(.62f), y(.14f), x(.44f), y(.12f), x(.44f), y(.04f)) }, col, style = Stroke(w, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 1.6f, w * 1.4f))))
            drawCircle(col, s * .05f, Offset(x(.44f), y(.04f)))
        }
        Glyph.FEATHER -> {
            drawPath(P { moveTo(x(.2f), y(.9f)); cubicTo(x(.36f), y(.6f), x(.56f), y(.3f), x(.82f), y(.08f)) }, col, style = st)
            drawPath(P { moveTo(x(.82f), y(.08f)); cubicTo(x(.9f), y(.4f), x(.66f), y(.66f), x(.36f), y(.7f)); cubicTo(x(.32f), y(.46f), x(.5f), y(.2f), x(.82f), y(.08f)) }, col, style = st)
        }
        Glyph.ARROW -> { line(.1f, .5f, .9f, .5f); line(.9f, .5f, .66f, .28f); line(.9f, .5f, .66f, .72f); line(.1f, .5f, .2f, .34f); line(.1f, .5f, .2f, .66f) }
        Glyph.ANVIL -> {
            drawPath(P { moveTo(x(.08f), y(.3f)); lineTo(x(.86f), y(.3f)); lineTo(x(.86f), y(.44f)); cubicTo(x(.7f), y(.46f), x(.64f), y(.54f), x(.62f), y(.62f)); lineTo(x(.72f), y(.8f)); lineTo(x(.28f), y(.8f)); lineTo(x(.38f), y(.62f)); cubicTo(x(.34f), y(.5f), x(.2f), y(.44f), x(.08f), y(.42f)); close() }, col)
            line(.22f, .9f, .78f, .9f)
        }
        Glyph.BANNER -> {
            line(.24f, .06f, .24f, .96f)
            drawPath(P { moveTo(x(.24f), y(.12f)); lineTo(x(.84f), y(.12f)); lineTo(x(.7f), y(.32f)); lineTo(x(.84f), y(.52f)); lineTo(x(.24f), y(.52f)); close() }, col)
        }
        Glyph.PAIR -> { drawCircle(col, s * .16f, Offset(x(.33f), y(.42f)), style = st); drawCircle(col, s * .16f, Offset(x(.67f), y(.42f)), style = st); drawArc(col, 200f, 140f, false, Offset(x(.1f), y(.62f)), Size(s * .8f, s * .5f), style = st) }
        Glyph.BAND -> for ((cx, cy) in listOf(.24f to .56f, .5f to .4f, .76f to .56f)) { drawCircle(col, s * .12f, Offset(x(cx), y(cy)), style = st); line(cx - .12f, cy + .3f, cx + .12f, cy + .3f) }
        Glyph.PAW -> {
            drawOval(col, Offset(x(.3f), y(.48f)), Size(s * .4f, s * .36f))
            for ((cx, cy) in listOf(.2f to .4f, .38f to .24f, .62f to .24f, .8f to .4f)) drawOval(col, Offset(x(cx - .08f), y(cy - .1f)), Size(s * .16f, s * .2f))
        }
        Glyph.STAR -> drawPath(P {
            for (i in 0 until 8) { val a = PI / 4 * i - PI / 2; val rr = if (i % 2 == 0) .46f else .12f; val px = x(.5f + rr * cos(a).toFloat()); val py = y(.5f + rr * sin(a).toFloat()); if (i == 0) moveTo(px, py) else lineTo(px, py) }
            close()
        }, col)
        Glyph.COMPASS -> {
            drawCircle(col, s * .4f, o, style = st)
            drawPath(P { moveTo(x(.5f), y(.12f)); lineTo(x(.6f), y(.5f)); lineTo(x(.5f), y(.88f)); lineTo(x(.4f), y(.5f)); close() }, col)
            line(.12f, .5f, .88f, .5f)
        }
        Glyph.FULL -> { drawCircle(col, s * .4f, o, style = st); drawCircle(col, s * .07f, Offset(x(.4f), y(.4f))); drawCircle(col, s * .05f, Offset(x(.62f), y(.58f))); drawCircle(col, s * .04f, Offset(x(.44f), y(.66f))) }
        Glyph.CROWN -> {
            drawPath(P { moveTo(x(.1f), y(.76f)); lineTo(x(.1f), y(.3f)); lineTo(x(.32f), y(.52f)); lineTo(x(.5f), y(.2f)); lineTo(x(.68f), y(.52f)); lineTo(x(.9f), y(.3f)); lineTo(x(.9f), y(.76f)); close() }, col, style = st)
            line(.1f, .88f, .9f, .88f)
        }
        Glyph.FERN -> {
            line(.5f, .96f, .5f, .3f)
            for (i in 0 until 4) { val yy = .78f - i * .14f; val len = .3f - i * .05f; line(.5f, yy, .5f - len, yy - .1f); line(.5f, yy, .5f + len, yy - .1f) }
            for (i in 0 until 5) { val a = 2 * PI * i / 5 - PI / 2; drawCircle(col, s * .07f, Offset(x(.5f + .13f * cos(a).toFloat()), y(.2f + .13f * sin(a).toFloat()))) }
        }
        Glyph.HORN -> {
            drawPath(P { moveTo(x(.08f), y(.36f)); cubicTo(x(.3f), y(.36f), x(.56f), y(.5f), x(.78f), y(.24f)); lineTo(x(.92f), y(.5f)); cubicTo(x(.7f), y(.56f), x(.62f), y(.8f), x(.3f), y(.62f)); cubicTo(x(.2f), y(.56f), x(.12f), y(.46f), x(.08f), y(.36f)) }, col, style = st)
            line(.36f, .68f, .3f, .9f); line(.3f, .9f, .62f, .9f)
        }
        Glyph.TUSK -> {
            drawPath(P { moveTo(x(.16f), y(.86f)); cubicTo(x(.1f), y(.4f), x(.5f), y(.12f), x(.86f), y(.14f)); cubicTo(x(.62f), y(.26f), x(.36f), y(.5f), x(.34f), y(.86f)); close() }, col)
        }
        Glyph.EYE -> {
            drawPath(P { moveTo(x(.06f), y(.5f)); quadraticTo(x(.5f), y(.12f), x(.94f), y(.5f)); quadraticTo(x(.5f), y(.88f), x(.06f), y(.5f)) }, col, style = st)
            drawOval(col, Offset(x(.43f), y(.32f)), Size(s * .14f, s * .36f))
        }
    }
}

/** Draws [block] offset so that its own origin sits at [at]. */
inline fun DrawScope.at(at: Offset, block: DrawScope.() -> Unit) = translate(at.x, at.y, block)

