package app.ranger.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.ranger.BuildConfig
import app.ranger.data.Geo
import app.ranger.domain.World
import app.ranger.ui.art.Chart
import app.ranger.ui.art.MapArt
import app.ranger.ui.art.Pin
import app.ranger.ui.components.Chip
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A place the ranger ran: home, or somewhere visited. */
private data class Area(val lat: Double, val lon: Double, val runs: Int)

/** Groups run starts into places about 30 km across, the busiest first. */
private fun areas(w: World): List<Area> {
    val out = ArrayList<Triple<Double, Double, Int>>()
    for (p in w.patrols) {
        val la = p.run.lat ?: continue; val lo = p.run.lon ?: continue
        val i = out.indexOfFirst { Geo.dist(it.first, it.second, la, lo) < 30_000 }
        if (i < 0) out += Triple(la, lo, 1) else out[i] = out[i].copy(third = out[i].third + 1)
    }
    return out.sortedByDescending { it.third }.map { Area(it.first, it.second, it.third) }
}

@Composable
fun MapScreen(w: World, nav: Nav) {
    val ru = LocalRu.current
    val ctx = LocalContext.current
    val places = remember(w.patrols.size) { areas(w) }
    if (places.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(t("Карта пустая: на ней появятся пробежки с GPS. Дозоры на дорожке уходят в подземелья, их видно в летописи.",
                "The map is empty: runs with GPS appear on it. Treadmill patrols go to the deeps; they're in the chronicle."), style = T.Lore)
        }
        return
    }
    var area by remember { mutableIntStateOf(0) }
    val a = places[area.coerceIn(0, places.size - 1)]
    val terrainDone = w.patrols.count { it.run.terrainDone }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val chart by produceState<Chart?>(null, area, w.patrols.size, terrainDone, box.width > 0) {
        if (box.width == 0) return@produceState
        value = withContext(Dispatchers.Default) {
            val last = w.patrols.lastOrNull { it.run.hasTrack }?.run?.id
            val trails = w.patrols.filter { it.run.line.isNotEmpty() && it.run.lat != null && Geo.dist(a.lat, a.lon, it.run.lat, it.run.lon!!) < 40_000 }
                .map { MapArt.trail(it.run.id, it.run.line, w.s.hidden, fresh = it.run.id == last) }
            val pins = w.met.mapNotNull { h -> h.metAt?.run?.line?.takeIf { it.isNotEmpty() }?.let { l -> val pts = Geo.decode(l); pts[pts.size / 2] }?.let { Pin(it.first, it.second, h.c.key) } }
                .filter { Geo.dist(a.lat, a.lon, it.lat, it.lon) < 40_000 }
                .filterNot { pin -> w.s.hidden.any { Geo.dist(it.lat, it.lon, pin.lat, pin.lon) < it.radiusM } }
            val home = w.s.hidden.firstOrNull()?.let { it.lat to it.lon }?.takeIf { Geo.dist(a.lat, a.lon, it.first, it.second) < 40_000 }
            runCatching { MapArt.draw(ctx, trails, pins, 2200, online = !BuildConfig.DEMO, focus = a.lat to a.lon, home = home, aspect = if (box.width > 0) box.height.toDouble() / box.width else 1.6) }.getOrNull()
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        val maxX = box.width * (scale - 1) / 2; val maxY = box.height * (scale - 1) / 2
        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
    }
    Column(Modifier.fillMaxSize().background(C.deep)) {
        if (places.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            places.take(8).forEachIndexed { i, p ->
                Chip(if (i == 0) t("Дом", "Home") else t("Поход ${i}", "Trip $i") + " · ${p.runs}", area == i, { area = i; scale = 1f; offset = Offset.Zero })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }.transformable(state)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1.2f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }, contentAlignment = Alignment.Center) {
            val c = chart
            if (c == null) Text(t("Картограф чертит карту…", "The cartographer is drawing…"), style = T.Lore)
            else Image(c.bitmap.asImageBitmap(), t("Карта Пограничья", "Map of the Borderlands"),
                Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit)
            Text(if (ru) "${w.cells.size} участков разведано" else "${w.cells.size} squares scouted", style = T.Small.copy(color = C.soft),
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).background(C.bg.copy(alpha = .8f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}
