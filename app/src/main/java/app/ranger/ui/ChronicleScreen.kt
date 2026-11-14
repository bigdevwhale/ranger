package app.ranger.ui

import app.ranger.ui.art.leaf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ranger.data.Digest
import app.ranger.domain.Patrol
import app.ranger.domain.World
import app.ranger.ui.components.Card
import app.ranger.ui.components.Chip
import app.ranger.ui.components.Section
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.Sh
import app.ranger.ui.theme.T
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ChronicleScreen(w: World, nav: Nav) {
    var summary by rememberSaveable { mutableStateOf(nav.summary) }
    val scroll = rememberScrollState()
    val jumps = remember(scroll) { Jumps(scroll) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        // pinned: which book is open, and in the summary, where to jump
        Column(Modifier.fillMaxWidth().background(C.bg).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(listOf(t("Летопись", "Chronicle") to "tab_chronicle", t("Сводка", "Summary") to "stat_years"), if (summary) 1 else 0) {
                summary = it == 1; nav.summary = summary; scope.launch { scroll.scrollTo(0) }
            }
            if (summary) JumpBar(jumps)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        Column(Modifier.fillMaxSize().verticalScroll(scroll).onGloballyPositioned { jumps.content = it }) {
            app.ranger.ui.art.Banner(if (summary) "summary" else "chronicle", Modifier.fillMaxWidth().aspectRatio(2.8f))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (summary) Summary(w, nav, jumps) else Months(w, nav)
            }
        }
    }
}

/** Two or more choices in one rounded bar, each with its icon; the chosen one is inked. */
@Composable
fun Segmented(items: List<Pair<String, String>>, chosen: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(C.deep).border(1.dp, C.ink.copy(alpha = .12f), CircleShape).padding(3.dp)) {
        items.forEachIndexed { i, (label, icon) ->
            val on = i == chosen
            Row(Modifier.weight(1f).clip(CircleShape).background(if (on) C.ink else androidx.compose.ui.graphics.Color.Transparent).clickable { onPick(i) }.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                app.ranger.ui.art.InkIcon(icon, if (on) C.page else C.soft, Modifier.size(17.dp)) {}
                Text(label, style = T.Strong.copy(color = if (on) C.page else C.soft), modifier = Modifier.padding(start = 7.dp))
            }
        }
    }
}

@Composable
private fun Months(w: World, nav: Nav) {
    val ru = LocalRu.current
    var ym by rememberSaveable { mutableStateOf(YearMonth.from(w.today).toString()) }
    val month = YearMonth.parse(ym)
    val byDay = remember(w) { w.patrols.groupBy { it.date } }
    val now = YearMonth.from(w.today)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Arrow(left = true, enabled = true, label = t("Прошлый месяц", "Previous month")) { ym = month.minusMonths(1).toString() }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Fmt.month(month.atDay(1), ru), style = T.Title, textAlign = TextAlign.Center)
            if (month != now) Text(t("к текущему месяцу", "back to this month"), style = T.Small.copy(color = C.seal),
                modifier = Modifier.clip(CircleShape).clickable { ym = now.toString() }.padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Arrow(left = false, enabled = month < now, label = t("Следующий месяц", "Next month")) { ym = month.plusMonths(1).toString() }
    }
    val inMonth = w.patrols.filter { YearMonth.from(it.date) == month }
    Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Stat("${inMonth.size}", Fmt.patrols(inMonth.size, ru).substringAfter(" "), Modifier.weight(1f))
            Stat(Fmt.km(inMonth.sumOf { it.run.distanceM }, ru), t("путь", "distance"), Modifier.weight(1f))
            Stat(Fmt.dur(inMonth.sumOf { it.run.movingS.toLong() }, ru), t("в пути", "on the trail"), Modifier.weight(1f))
        }
    }
    if (inMonth.isNotEmpty()) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { ShareButton({ nav.share(Share.Month(month.toString())) }) }
    Calendar(month, byDay, w.today) { d -> byDay[d]?.lastOrNull()?.let { nav.go(Overlay.Patrol(it.run.id)) } }
    if (inMonth.isNotEmpty()) Section(t("Дозоры месяца", "This month's patrols"), "${inMonth.size}")
    for (p in inMonth.reversed()) PatrolRow(p) { nav.go(Overlay.Patrol(p.run.id)) }
    if (inMonth.isEmpty()) Text(if (month == now) t("В этом месяце дозоров пока не было. Тропа ждёт.", "No patrols yet this month. The trail is waiting.")
        else t("В этом месяце дозоров не было.", "No patrols this month."), style = T.Lore, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
}

/** A round button with a chevron, for paging months. */
@Composable
private fun Arrow(left: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val col = if (enabled) C.ink else C.line
    Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, col.copy(alpha = .35f), CircleShape).clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
            val sw = 2.dp.toPx(); val m = size.height / 2
            val a = if (left) size.width * .7f else size.width * .3f; val b = if (left) size.width * .3f else size.width * .7f
            drawLine(col, androidx.compose.ui.geometry.Offset(a, 0f), androidx.compose.ui.geometry.Offset(b, m), sw, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(col, androidx.compose.ui.geometry.Offset(b, m), androidx.compose.ui.geometry.Offset(a, size.height), sw, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** A month of pages: each day with a patrol carries a footprint, sized by distance and inked by the kind of run. */
@Composable
private fun Calendar(month: YearMonth, byDay: Map<LocalDate, List<Patrol>>, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val ru = LocalRu.current
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val maxKm = byDay.filterKeys { YearMonth.from(it) == month }.values.maxOfOrNull { l -> l.sumOf { it.run.km } }?.coerceAtLeast(5.0) ?: 10.0
    Column(Modifier.fillMaxWidth().leaf().clip(Sh.Lg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row { for (d in (if (ru) Fmt.shortDaysRu else Fmt.shortDaysEn)) Text(d, style = T.Small, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        val cells = lead + days
        for (week in 0 until (cells + 6) / 7) Row {
            for (dow in 0 until 7) {
                val i = week * 7 + dow - lead + 1
                Box(Modifier.weight(1f).aspectRatio(1.08f), contentAlignment = Alignment.Center) {
                    if (i in 1..days) {
                        val d = month.atDay(i)
                        val ps = byDay[d].orEmpty()
                        val km = ps.sumOf { it.run.km }
                        Box(Modifier.fillMaxSize().clip(CircleShape).clickable(enabled = ps.isNotEmpty()) { onDay(d) }, contentAlignment = Alignment.Center) {
                            if (ps.isNotEmpty()) {
                                val k = (0.45 + 0.55 * (km / maxKm)).toFloat().coerceIn(.45f, 1f)
                                val col = if (ps.any { it.run.race }) C.gold else if (ps.any { it.events.any { e -> e is app.ranger.domain.Event.Met } }) C.seal else C.ink
                                // an ink seal on the day, bigger for a longer patrol
                                Box(Modifier.fillMaxSize(k * .86f).clip(CircleShape).background(col.copy(alpha = .9f)))
                            } else if (d == today) Box(Modifier.fillMaxSize(.62f).clip(CircleShape).border(1.5.dp, C.seal, CircleShape))
                            Text("$i", style = T.Small.copy(color = when { ps.isNotEmpty() -> C.page; d == today -> C.seal; d.isAfter(today) -> C.line; else -> C.soft }), modifier = Modifier)
                        }
                    }
                }
            }
        }
    }
}
