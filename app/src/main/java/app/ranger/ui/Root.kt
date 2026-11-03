package app.ranger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ranger.container
import app.ranger.data.Settings
import app.ranger.domain.Ranks
import app.ranger.domain.World
import app.ranger.ru
import app.ranger.ui.components.t
import app.ranger.ui.art.parchment
import app.ranger.ui.sheets.AddSheet
import app.ranger.ui.sheets.BandSheet
import app.ranger.ui.sheets.BootsSheet
import app.ranger.ui.sheets.BoardSheet
import app.ranger.ui.sheets.CreatureSheet
import app.ranger.ui.sheets.ManualSheet
import app.ranger.ui.sheets.PatrolSheet
import app.ranger.ui.sheets.RecordSheet
import app.ranger.ui.sheets.RevealSheet
import app.ranger.ui.sheets.SkillsSheet
import app.ranger.ui.theme.C
import app.ranger.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val LocalRu = staticCompositionLocalOf { true }

enum class Tab { OUTPOST, MAP, BESTIARY, CHRONICLE }

sealed class Overlay {
    data class Patrol(val id: Long) : Overlay()
    data class Creature(val key: String) : Overlay()
    data class Feat(val key: String) : Overlay()
    data object Add : Overlay()
    data object Manual : Overlay()
    data object Record : Overlay()
    data object Reveal : Overlay()
    data object Board : Overlay()
    data object Boots : Overlay()
    data object Band : Overlay()
    data object Skills : Overlay()
    data object Steps : Overlay()
    data object Settings : Overlay()
}

@Stable
class Nav(val go: (Overlay?) -> Unit, val tab: (Tab) -> Unit) {
    fun close() = go(null)
    var toast by mutableStateOf("" to 0L)
        private set
    fun toast(t: String) { toast = t to System.nanoTime() }
    /** The bestiary tab shows the hall of feats instead of the creatures. */
    var trophies by mutableStateOf(false)
    fun trophies() { trophies = true; tab(Tab.BESTIARY) }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("no nav") }

/** [clock], [tab0] and [overlay0] let renders open a fixed moment on a given screen; the app passes none. */
@Composable
fun RangerRoot(s: Settings, clock: Long? = null, tab0: Tab = Tab.OUTPOST, overlay0: Overlay? = null) {
    val ctx = LocalContext.current
    val c = ctx.container
    val ru = ctx.ru
    CompositionLocalProvider(LocalRu provides ru) {
        if (!s.onboarded) { Onboarding(); return@CompositionLocalProvider }
        val runs by c.dao.runs().collectAsStateWithLifecycle(initialValue = null)
        val shoes by c.dao.shoes().collectAsStateWithLifecycle(initialValue = null)
        val friends by c.dao.companions().collectAsStateWithLifecycle(initialValue = null)
        val links by c.dao.links().collectAsStateWithLifecycle(initialValue = null)
        val plays by c.dao.plays().collectAsStateWithLifecycle(initialValue = null)
        val wounds by c.dao.wounds().collectAsStateWithLifecycle(initialValue = null)
        val days by c.dao.days().collectAsStateWithLifecycle(initialValue = null)
        val quests by c.dao.quests().collectAsStateWithLifecycle(initialValue = null)
        val seeding by c.seeding.collectAsStateWithLifecycle()
        var now by remember { mutableLongStateOf(clock ?: System.currentTimeMillis()) }
        LaunchedEffect(Unit) { if (clock == null) while (true) { delay(60_000); now = System.currentTimeMillis() } }
        LaunchedEffect(seeding) { if (!seeding) c.tend() }
        val world by produceState<World?>(null, runs, shoes, friends, links, plays, wounds, days, quests, s, now, seeding) {
            if (seeding) return@produceState
            val r = runs ?: return@produceState; val sh = shoes ?: return@produceState; val f = friends ?: return@produceState
            val l = links ?: return@produceState; val p = plays ?: return@produceState; val w = wounds ?: return@produceState
            val d = days ?: return@produceState; val q = quests ?: return@produceState
            value = withContext(Dispatchers.Default) { World(r, sh, f, l, p, w, d, q, s, now) }
        }
        val wd = world ?: run { Loading(); return@CompositionLocalProvider }
        var tab by rememberSaveable { mutableStateOf(tab0) }
        var overlay by remember { mutableStateOf(overlay0) }
        val nav = remember { Nav({ overlay = it }, { tab = it; overlay = null }) }

        CompositionLocalProvider(LocalNav provides nav) {
            Box(Modifier.fillMaxSize().parchment()) {
                Column(Modifier.fillMaxSize()) {
                    Header(wd, onRank = { overlay = Overlay.Skills }, onAdd = { overlay = Overlay.Add }, onSettings = { overlay = Overlay.Settings })
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            Tab.OUTPOST -> OutpostScreen(wd, nav)
                            Tab.MAP -> MapScreen(wd, nav)
                            Tab.BESTIARY -> BestiaryScreen(wd, nav)
                            Tab.CHRONICLE -> ChronicleScreen(wd, nav)
                        }
                    }
                    TabBar(tab) { tab = it }
                }
                when (val o = overlay) {
                    is Overlay.Patrol -> wd.patrol(o.id)?.let { PatrolSheet(wd, it, nav) } ?: LaunchedEffect(o) { overlay = null }
                    is Overlay.Creature -> wd.hunts[o.key]?.let { CreatureSheet(wd, it, nav) }
                    is Overlay.Feat -> runCatching { app.ranger.domain.Feat.valueOf(o.key) }.getOrNull()?.let { FeatSheet(wd, it, nav) }
                    Overlay.Add -> AddSheet(wd, nav)
                    Overlay.Manual -> ManualSheet(wd, nav)
                    Overlay.Record -> RecordSheet(nav)
                    Overlay.Reveal -> RevealSheet(wd, nav)
                    Overlay.Board -> BoardSheet(wd, nav)
                    Overlay.Boots -> BootsSheet(wd, nav)
                    Overlay.Band -> BandSheet(wd, nav)
                    Overlay.Skills -> SkillsSheet(wd, nav)
                    Overlay.Steps -> app.ranger.ui.sheets.StepsSheet(wd, nav)
                    Overlay.Settings -> SettingsPage(wd, nav)
                    null -> Unit
                }
                Toast(nav, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
        Text(t("Разворачиваем карту…", "Unrolling the map…"), style = T.Lore)
    }
}

@Composable
private fun Header(w: World, onRank: () -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    val ru = LocalRu.current
    val ri = w.rank
    val r = Ranks.all[ri]; val next = Ranks.all.getOrNull(ri + 1)
    Row(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.night, C.night2))).statusBarsPadding().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        app.ranger.ui.art.Crest(ri, Modifier.size(width = 34.dp, height = 40.dp).clickable(onClick = onRank))
        Box(Modifier.size(10.dp))
        Column(Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).clickable(onClick = onRank).padding(vertical = 2.dp)) {
            Text(r.name.of(ru), style = T.Name.copy(color = C.onDark))
            val p = if (next == null) 1f else (w.xp - r.at).toFloat() / (next.at - r.at)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f, fill = false).size(width = 120.dp, height = 5.dp).clip(CircleShape).background(C.night2)) {
                    Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).size(height = 5.dp, width = 120.dp).clip(CircleShape).background(C.fire))
                }
                Text(if (next == null) Fmt.int(w.xp, ru) else "${Fmt.int(w.xp, ru)} / ${Fmt.int(next.at, ru)}", style = T.Small.copy(color = C.onDarkSoft))
            }
        }
        val addLabel = t("Добавить дозор", "Add a patrol")
        Box(Modifier.size(44.dp).clip(CircleShape).background(C.seal).clickable(onClick = onAdd).semantics { contentDescription = addLabel }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(16.dp)) {
                val sw = 2.4.dp.toPx()
                drawLine(C.page, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), sw, StrokeCap.Round)
                drawLine(C.page, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), sw, StrokeCap.Round)
            }
        }
        val label = t("Настройки", "Settings")
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onSettings).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(20.dp)) {
                val rr = size.width / 2; val sw = 2.dp.toPx()
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    drawLine(C.onDarkSoft, Offset(rr + (rr * .55f) * kotlin.math.cos(a).toFloat(), rr + (rr * .55f) * kotlin.math.sin(a).toFloat()),
                        Offset(rr + rr * kotlin.math.cos(a).toFloat(), rr + rr * kotlin.math.sin(a).toFloat()), sw * 1.4f)
                }
                drawCircle(C.onDarkSoft, rr * .62f, style = Stroke(sw))
                drawCircle(C.onDarkSoft, rr * .22f, style = Stroke(sw))
            }
        }
    }
}

@Composable
private fun TabBar(tab: Tab, onTab: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().parchment(C.page, 0f)) {
        Canvas(Modifier.fillMaxWidth().size(height = 4.dp, width = 1.dp)) {
            drawLine(C.ink.copy(alpha = .45f), Offset(0f, 1f), Offset(size.width, 1f), 1.2f * density)
            drawLine(C.ink.copy(alpha = .2f), Offset(0f, 3.5f * density), Offset(size.width, 3.5f * density), .7f * density)
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp)) {
            for ((i, e) in listOf(Tab.OUTPOST to ("Застава" to "Outpost"), Tab.MAP to ("Карта" to "Map"), Tab.BESTIARY to ("Бестиарий" to "Bestiary"), Tab.CHRONICLE to ("Летопись" to "Chronicle")).withIndex()) {
                val (tb, l) = e
                val on = tb == tab
                val col = if (on) C.seal else C.faint
                Column(Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable { onTab(tb) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    app.ranger.ui.art.TabIcon(i, col, Modifier.size(24.dp))
                    Text(t(l.first, l.second), style = T.Small.copy(color = if (on) C.ink else C.faint, fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1)
                    Box(Modifier.padding(top = 3.dp).size(width = if (on) 18.dp else 0.dp, height = 2.dp).background(C.seal))
                }
            }
        }
    }
}

@Composable
private fun Toast(nav: Nav, modifier: Modifier) {
    val (text, stamp) = nav.toast
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(stamp) { if (text.isNotEmpty()) { shown = true; delay(3400); shown = false } }
    if (!shown || text.isEmpty()) return
    Text(text, style = T.Body.copy(color = C.page), modifier = modifier.statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp).fillMaxWidth()
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(C.ink).padding(horizontal = 16.dp, vertical = 12.dp))
}
