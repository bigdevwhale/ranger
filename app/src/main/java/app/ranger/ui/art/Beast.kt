package app.ranger.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.ranger.domain.Creature
import app.ranger.ui.theme.C
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A creature's portrait in its round frame. Met: the painting in colour, ringed in the creature's ink (gold for legends).
 * On its trail: a faded sepia sketch with a dashed ring and an arc for how many of the patrols it needs are done.
 * Unknown: barely a pencil ghost behind a question mark.
 */
@Composable
fun Beast(c: Creature, stage: Int, modifier: Modifier, n: Int = 0) {
    BoxWithConstraints(modifier) {
        val px = with(LocalDensity.current) { maxWidth.toPx().roundToInt().coerceAtLeast(1) }
        val img = rememberPainting("beasts/${c.key}.webp", px)
        Canvas(Modifier.matchParentSize()) { beast(c, stage, n, img) }
    }
}

private fun DrawScope.beast(c: Creature, stage: Int, n: Int, img: ImageBitmap?) {
    val r = size.minDimension / 2
    val o = center
    val ink = Color(c.ink)
    val inner = r * .9f
    val legend = c.tier == 3
    val gold = Color(0xFFC9A04A)

    // the paper under the painting, so a portrait still loading reads as a blank page, not a hole
    drawCircle(C.deep.copy(alpha = if (stage == 3) 1f else .55f), inner, o)
    if (img != null) {
        // the painting's own vignette fills nearly all of it; scale it up a touch so its soft edge falls outside our circle
        val side = inner * 2 / .93f
        val clip = Path().apply { addOval(Rect(o, inner)) }
        clipPath(clip) {
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height),
                IntOffset((o.x - side / 2).roundToInt(), (o.y - side / 2).roundToInt()), IntSize(side.roundToInt(), side.roundToInt()),
                alpha = when (stage) { 3 -> 1f; 2 -> .62f; 1 -> .42f; else -> .13f },
                colorFilter = if (stage == 3) null else Sepia)
        }
    }

    when (stage) {
        3 -> {
            // a soft inner shade so the painting sits in the frame, not on it
            drawCircle(Color.Black.copy(alpha = .16f), inner - r * .015f, o, style = Stroke(r * .03f))
            drawCircle(if (legend) gold else ink, r * .955f, o, style = Stroke(r * .075f))
            drawCircle(Color.White.copy(alpha = .28f), r * .93f, o, style = Stroke(r * .012f))
            if (c.tier >= 2) {
                val k = if (legend) 12 else 8
                for (i in 0 until k) {
                    val a = 2 * PI * i / k - PI / 2
                    drawCircle(if (legend) Color(0xFFFFF1C9) else gold, r * .028f, Offset(o.x + r * .955f * cos(a).toFloat(), o.y + r * .955f * sin(a).toFloat()))
                }
            }
        }
        0 -> {
            drawCircle(C.line, r * .94f, o, style = Stroke(r * .04f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * .07f, r * .1f)), cap = StrokeCap.Round))
            question(o, r * .36f, C.faint.copy(alpha = .8f))
        }
        else -> {
            drawCircle(ink.copy(alpha = .5f), r * .945f, o, style = Stroke(r * .035f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * .16f, r * .09f))))
            // how far along the trail is: an arc of gold from the top
            val p = (n.toFloat() / c.need.coerceAtLeast(1)).coerceIn(0f, 1f)
            if (p > 0f) drawArc(C.gold, -90f, 360f * p, false, Offset(o.x - r * .945f, o.y - r * .945f), Size(r * 1.89f, r * 1.89f),
                style = Stroke(r * .075f, cap = StrokeCap.Round))
        }
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
