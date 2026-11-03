package app.ranger.ui.art

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.ranger.ui.theme.C
import kotlin.random.Random

/** Parchment: a tile of fibres and speckles laid over the base colour, made once. */
object Paper {
    private var tile: ImageBitmap? = null

    fun tile(): ImageBitmap = tile ?: run {
        val n = 384
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val r = Random(5)
        // speckles, wrapped at the edges so the tile repeats without seams
        repeat(2400) {
            val x = r.nextFloat() * n; val y = r.nextFloat() * n
            p.color = if (r.nextFloat() < .55f) 0xFF7A5A30.toInt() else 0xFFFFFFFF.toInt()
            p.alpha = 6 + r.nextInt(16)
            val s = .5f + r.nextFloat() * 1.6f
            for (dx in listOf(-n, 0, n)) for (dy in listOf(-n, 0, n)) c.drawCircle(x + dx, y + dy, s, p)
        }
        // fibres
        p.style = android.graphics.Paint.Style.STROKE
        repeat(260) {
            val x = r.nextFloat() * n; val y = r.nextFloat() * n
            val a = r.nextFloat() * 6.28f; val len = 4 + r.nextFloat() * 14
            p.color = 0xFF6B4E2A.toInt(); p.alpha = 8 + r.nextInt(12); p.strokeWidth = .5f + r.nextFloat() * .6f
            for (dx in listOf(-n, 0, n)) for (dy in listOf(-n, 0, n))
                c.drawLine(x + dx, y + dy, x + dx + kotlin.math.cos(a) * len, y + dy + kotlin.math.sin(a) * len, p)
        }
        b.asImageBitmap().also { tile = it }
    }
}

/** Fills the area with parchment: the base colour, the fibre tile, and a darker edge. */
fun DrawScope.parchment(base: Color = C.bg, edge: Float = .1f) {
    drawRect(base)
    val t = Paper.tile()
    var y = 0
    while (y < size.height) {
        var x = 0
        while (x < size.width) { drawImage(t, dstOffset = IntOffset(x, y), dstSize = IntSize(t.width, t.height)); x += t.width }
        y += t.height
    }
    if (edge > 0) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFF6B4A22).copy(alpha = edge)), Offset(size.width / 2, size.height * .4f), maxOf(size.width, size.height) * .8f))
}

fun Modifier.parchment(base: Color = C.bg, edge: Float = .1f) = drawBehind { parchment(base, edge) }

/**
 * A leaf of the journal: lighter paper, a double ink rule with small corner diamonds, a soft shadow below.
 */
fun Modifier.leaf(fill: Color = C.page, ink: Color = C.ink, radius: Float = 10f) = drawBehind {
    val r = radius * density
    drawRoundRect(Color(0xFF5A3E1C).copy(alpha = .10f), Offset(0f, 2 * density), size, CornerRadius(r))
    drawRoundRect(Brush.verticalGradient(listOf(fill, androidx.compose.ui.graphics.lerp(fill, C.deep, .35f))), size = size, cornerRadius = CornerRadius(r))
    drawRoundRect(ink.copy(alpha = .38f), size = size, cornerRadius = CornerRadius(r), style = Stroke(1.1f * density))
    val m = 4 * density
    drawRoundRect(ink.copy(alpha = .16f), Offset(m, m), Size(size.width - 2 * m, size.height - 2 * m), CornerRadius((r - m).coerceAtLeast(2f)), style = Stroke(.7f * density))
    val d = 3.2f * density
    for ((cx, cy) in listOf(m to m, size.width - m to m, m to size.height - m, size.width - m to size.height - m)) {
        drawPath(Path().apply { moveTo(cx, cy - d); lineTo(cx + d, cy); lineTo(cx, cy + d); lineTo(cx - d, cy); close() }, ink.copy(alpha = .4f))
    }
}

/** A flourish: a thin rule that thickens to a diamond in the middle, for section titles. */
fun DrawScope.flourish(x0: Float, x1: Float, y: Float, col: Color, toRight: Boolean) {
    val w = .9f * density
    drawLine(col.copy(alpha = .5f), Offset(x0, y), Offset(x1, y), w, StrokeCap.Round)
    val end = if (toRight) x1 else x0
    val d = 2.6f * density
    drawPath(Path().apply { moveTo(end, y - d); lineTo(end + d, y); lineTo(end, y + d); lineTo(end - d, y); close() }, col.copy(alpha = .7f))
    val near = if (toRight) x0 else x1
    drawCircle(col.copy(alpha = .6f), 1.3f * density, Offset(near, y))
}

/** Tab icons in ink: the tower, the map, the seal, the book. */
@Composable
fun TabIcon(kind: Int, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension; val o = Offset((size.width - s) / 2, (size.height - s) / 2)
        fun x(f: Float) = o.x + f * s
        fun y(f: Float) = o.y + f * s
        val st = Stroke(s * .085f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        when (kind) {
            0 -> { // watchtower
                drawPath(Path().apply { moveTo(x(.2f), y(.38f)); lineTo(x(.5f), y(.1f)); lineTo(x(.8f), y(.38f)); close() }, color, style = st)
                drawRect(color, Offset(x(.3f), y(.38f)), Size(s * .4f, s * .2f), style = st)
                drawLine(color, Offset(x(.33f), y(.58f)), Offset(x(.22f), y(.92f)), s * .085f, StrokeCap.Round)
                drawLine(color, Offset(x(.67f), y(.58f)), Offset(x(.78f), y(.92f)), s * .085f, StrokeCap.Round)
                drawLine(color, Offset(x(.3f), y(.72f)), Offset(x(.7f), y(.84f)), s * .06f)
            }
            1 -> { // folded map with a trail
                drawPath(Path().apply { moveTo(x(.1f), y(.22f)); lineTo(x(.37f), y(.12f)); lineTo(x(.63f), y(.22f)); lineTo(x(.9f), y(.12f)); lineTo(x(.9f), y(.78f)); lineTo(x(.63f), y(.88f)); lineTo(x(.37f), y(.78f)); lineTo(x(.1f), y(.88f)); close() }, color, style = st)
                drawLine(color, Offset(x(.37f), y(.12f)), Offset(x(.37f), y(.78f)), s * .05f); drawLine(color, Offset(x(.63f), y(.22f)), Offset(x(.63f), y(.88f)), s * .05f)
                drawCircle(color, s * .07f, Offset(x(.72f), y(.42f)))
            }
            2 -> { // a seal with a paw
                drawCircle(color, s * .38f, Offset(x(.5f), y(.5f)), style = st)
                drawOval(color, Offset(x(.38f), y(.5f)), Size(s * .24f, s * .2f))
                for ((cx, cy) in listOf(.33f to .44f, .44f to .34f, .56f to .34f, .67f to .44f)) drawCircle(color, s * .055f, Offset(x(cx), y(cy)))
            }
            else -> { // an open book
                drawPath(Path().apply { moveTo(x(.5f), y(.25f)); quadraticTo(x(.3f), y(.14f), x(.08f), y(.2f)); lineTo(x(.08f), y(.8f)); quadraticTo(x(.3f), y(.74f), x(.5f), y(.85f)); quadraticTo(x(.7f), y(.74f), x(.92f), y(.8f)); lineTo(x(.92f), y(.2f)); quadraticTo(x(.7f), y(.14f), x(.5f), y(.25f)); close() }, color, style = st)
                drawLine(color, Offset(x(.5f), y(.25f)), Offset(x(.5f), y(.85f)), s * .06f)
            }
        }
    }
}

/** The rank's crest: a shield with a chevron and roman numerals. */
@Composable
fun Crest(rank: Int, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val shield = Path().apply { moveTo(w * .08f, h * .06f); lineTo(w * .92f, h * .06f); lineTo(w * .92f, h * .5f); quadraticTo(w * .9f, h * .82f, w * .5f, h * .97f); quadraticTo(w * .1f, h * .82f, w * .08f, h * .5f); close() }
        drawPath(shield, Brush.verticalGradient(listOf(Color(0xFFB0452C), Color(0xFF7A2A1A))))
        drawPath(shield, C.gold, style = Stroke(w * .07f))
        drawPath(Path().apply { moveTo(w * .2f, h * .62f); lineTo(w * .5f, h * .38f); lineTo(w * .8f, h * .62f) }, C.gold.copy(alpha = .9f), style = Stroke(w * .09f, cap = StrokeCap.Round))
        val stars = (rank + 1).coerceAtMost(10)
        val per = if (stars > 5) 5 else stars
        for (i in 0 until stars) {
            val row = i / 5; val n = if (row == 0) per else stars - 5
            val cx = w * .5f + (i % 5 - (n - 1) / 2f) * w * .14f
            drawCircle(Color(0xFFF3E2B0), w * .04f, Offset(cx, h * (.2f + row * .09f)))
        }
    }
}
