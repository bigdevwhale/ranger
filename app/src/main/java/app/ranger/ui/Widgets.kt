package app.ranger.ui

import app.ranger.ui.art.leaf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ranger.container
import app.ranger.domain.Cond
import app.ranger.domain.Patrol
import app.ranger.domain.World
import app.ranger.ui.art.MapArt
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.Sh
import app.ranger.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Time in each heart-rate zone as one bar of five colours. */
@Composable
fun ZoneStrip(z: IntArray, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val total = z.sum().coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth().height(height).clip(CircleShape)) {
        var x = 0f
        for (i in 0 until 5) {
            val w = size.width * z[i] / total
            drawRect(C.zones[i], Offset(x, 0f), Size(w, size.height)); x += w
        }
    }
}

@Composable
fun ZoneLegend(z: IntArray) {
    val total = z.sum().coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0 until 5) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(C.zones[i]))
            Text(" ${i + 1}: ${(z[i] * 100f / total).toInt()}%", style = T.Small)
        }
    }
}

/** A big number with a small label under it. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Column(modifier) {
        Text(value, style = T.NumberSm.copy(color = if (dark) C.onDark else C.ink), maxLines = 1)
        Text(label, style = T.Small.copy(color = if (dark) C.onDarkSoft else C.faint), maxLines = 1)
    }
}

/** Ten pips, [n] of them inked. */
@Composable
fun Pips(n: Int, of: Int = 10, color: Color = C.seal, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until of) Box(Modifier.size(width = 9.dp, height = 9.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(if (i < n) color else C.deep))
    }
}

fun patrolTitle(p: Patrol, ru: Boolean): String = "${Fmt.date(p.date, ru).replaceFirstChar { it.uppercase() }}, ${Fmt.partOfDay(p.run.start, ru)}"

/** A patrol in a list: when, how far, what it was like. */
@Composable
fun PatrolRow(p: Patrol, onClick: () -> Unit) {
    val ru = LocalRu.current
    Row(Modifier.fillMaxWidth().leaf().clip(Sh.Md).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(patrolTitle(p, ru), style = T.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val conds = Cond.SHOWN.filter { it in p.conds }.take(3).joinToString(" · ") { it.of(ru) }
            Text(listOf(p.kind.of(ru), conds).filter { it.isNotBlank() }.joinToString(" · "), style = T.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.km(p.run.distanceM, ru), style = T.NumberSm)
            Text(Fmt.pace(p.run.pace, ru), style = T.Small)
        }
    }
}

/** The map of one patrol, drawn in the background. */
@Composable
fun TrailMap(w: World, p: Patrol, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val chart by produceState<android.graphics.Bitmap?>(null, p.run.id, p.run.terrainDone) {
        value = withContext(Dispatchers.Default) {
            val trail = MapArt.trail(p.run.id, p.run.line, w.s.hidden, fresh = true)
            runCatching { MapArt.draw(ctx, listOf(trail), emptyList(), 1000, online = !app.ranger.BuildConfig.DEMO, radiusKm = 60.0, corridorM = 420.0) }.getOrNull()?.bitmap
        }
    }
    Box(modifier.fillMaxWidth().aspectRatio(1.35f).clip(Sh.Md).background(C.deep).border(1.dp, C.line, Sh.Md), contentAlignment = Alignment.Center) {
        val b = chart
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(1.35f), contentScale = ContentScale.Crop)
        else Text(t("Чертим…", "Drawing…"), style = T.Lore)
    }
}

/** A mini bar chart: values left to right, the last bar inked. */
@Composable
fun Bars(values: List<Double>, modifier: Modifier = Modifier, color: Color = C.soft, last: Color = C.seal) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Canvas(modifier) {
        val n = values.size.coerceAtLeast(1)
        val gap = size.width * .25f / n
        val bw = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val h = (size.height * (v / max)).toFloat().coerceAtLeast(if (v > 0) 2f else 1f)
            drawRoundRect(if (i == values.size - 1) last else color.copy(alpha = if (v > 0) .75f else .2f), Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(bw / 4))
        }
    }
}

