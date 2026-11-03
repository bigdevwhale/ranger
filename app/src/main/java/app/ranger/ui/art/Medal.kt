package app.ranger.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import app.ranger.domain.FeatGroup
import app.ranger.domain.FeatState
import app.ranger.ui.theme.C

/** The metal of each shelf, so a glance tells distances from weather from company. */
fun FeatGroup.ink(): Color = when (this) {
    FeatGroup.DISTANCE -> C.seal
    FeatGroup.ROAD -> Color(0xFF9A7A22)
    FeatGroup.RHYTHM -> C.forest
    FeatGroup.SKY -> Color(0xFF2E3157)
    FeatGroup.WORLD -> Color(0xFF2F5D7C)
    FeatGroup.LORE -> Color(0xFF5E3A5E)
}

/**
 * A feat's medal: earned, a struck disc on two ribbon tails with its mark; not yet, a pencilled ring with the mark
 * faint and an arc showing how far along it is.
 */
@Composable
fun Medal(st: FeatState, modifier: Modifier) {
    val ink = st.f.group.ink()
    Canvas(modifier) {
        val r = size.minDimension / 2 * .78f
        val o = Offset(size.width / 2, size.height / 2 - size.minDimension * .04f)
        if (st.done) {
            // ribbon tails behind the disc
            val tail = lerp(ink, Color.Black, .25f)
            for (side in listOf(-1f, 1f)) {
                val x0 = o.x + side * r * .35f
                val p = Path().apply {
                    moveTo(x0 - r * .2f, o.y + r * .4f); lineTo(x0 + r * .2f, o.y + r * .4f)
                    lineTo(x0 + r * .2f + side * r * .12f, o.y + r * 1.22f); lineTo(x0 + side * r * .12f, o.y + r * 1.05f)
                    lineTo(x0 - r * .2f + side * r * .12f, o.y + r * 1.22f); close()
                }
                drawPath(p, tail)
            }
            drawCircle(lerp(ink, Color.Black, .35f), r, o)
            drawCircle(ink, r * .9f, o)
            drawCircle(Color.White.copy(alpha = .18f), r * .78f, o, style = Stroke(r * .05f))
            // a little shine on the upper left
            drawArc(Color.White.copy(alpha = .22f), 200f, 70f, false, Offset(o.x - r * .7f, o.y - r * .7f), Size(r * 1.4f, r * 1.4f), style = Stroke(r * .07f, cap = StrokeCap.Round))
            glyph(st.f.glyph, o, r * 1.05f, Color(0xFFF6EBD3), 1f)
        } else {
            val faint = C.line
            drawCircle(C.deep.copy(alpha = .5f), r, o)
            drawCircle(faint, r, o, style = Stroke(r * .05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * .14f, r * .1f))))
            glyph(st.f.glyph, o, r * 1.05f, faint, .9f)
            if (st.share > 0) drawArc(ink.copy(alpha = .85f), -90f, (360 * st.share).toFloat(), false,
                Offset(o.x - r, o.y - r), Size(r * 2, r * 2), style = Stroke(r * .12f, cap = StrokeCap.Round))
        }
    }
}
