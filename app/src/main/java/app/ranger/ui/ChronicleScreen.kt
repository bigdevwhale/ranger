package app.ranger.ui

import app.ranger.ui.art.leaf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    var summary by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(t("Летопись", "Chronicle"), !summary, { summary = false })
            Chip(t("Сводка", "Summary"), summary, { summary = true })
        }
        if (summary) Summary(w, nav) else Months(w, nav)
    }
}

@Composable
private fun Months(w: World, nav: Nav) {
    val ru = LocalRu.current
    var ym by rememberSaveable { mutableStateOf(YearMonth.from(w.today).toString()) }
    val month = YearMonth.parse(ym)
    val byDay = remember(w) { w.patrols.groupBy { it.date } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹", style = T.Title, modifier = Modifier.clip(CircleShape).clickable { ym = month.minusMonths(1).toString() }.padding(horizontal = 14.dp))
        Text(Fmt.month(month.atDay(1), ru), style = T.Title, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text("›", style = T.Title.copy(color = if (month < YearMonth.from(w.today)) C.ink else C.line),
            modifier = Modifier.clip(CircleShape).clickable(enabled = month < YearMonth.from(w.today)) { ym = month.plusMonths(1).toString() }.padding(horizontal = 14.dp))
    }
    val inMonth = w.patrols.filter { YearMonth.from(it.date) == month }
    Text(if (ru) "${Fmt.patrols(inMonth.size, true)} · ${Fmt.km(inMonth.sumOf { it.run.distanceM }, true)} · ${Fmt.dur(inMonth.sumOf { it.run.movingS.toLong() }, true)}"
        else "${Fmt.patrols(inMonth.size, false)} · ${Fmt.km(inMonth.sumOf { it.run.distanceM }, false)} · ${Fmt.dur(inMonth.sumOf { it.run.movingS.toLong() }, false)}", style = T.Muted)
    Calendar(month, byDay, w.today) { d -> byDay[d]?.lastOrNull()?.let { nav.go(Overlay.Patrol(it.run.id)) } }
    for (p in inMonth.reversed()) PatrolRow(p) { nav.go(Overlay.Patrol(p.run.id)) }
    if (inMonth.isEmpty()) Text(t("В этом месяце дозоров не было.", "No patrols this month."), style = T.Lore)
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
                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
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
                            }
                            Text("$i", style = T.Small.copy(color = when { ps.isNotEmpty() -> C.page; d == today -> C.seal; d.isAfter(today) -> C.line; else -> C.soft }), modifier = Modifier)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(w: World, nav: Nav) {
    val ru = LocalRu.current
    Card {
        Text(t("За всё время", "All time"), style = T.Eyebrow)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat(Fmt.km(w.totalKm * 1000, ru), Fmt.patrols(w.patrols.size, ru))
            Stat(Fmt.dur(w.totalTime, ru), t("в пути", "on the trail"))
            Stat("${Fmt.int(w.totalAscent.toInt(), ru)} ${t("м", "m")}", t("набор", "climbed"))
        }
    }
    val weeks = w.weeks(16)
    Section(t("Километры по неделям", "Kilometres by week"), Fmt.km(weeks.last().second * 1000, ru))
    Bars(weeks.map { it.second }, Modifier.fillMaxWidth().height(90.dp))
    Text(t("Сейчас справа, шестнадцать недель назад слева.", "This week on the right, sixteen weeks ago on the left."), style = T.Small)

    val z = w.zonesSince(28)
    if (z.sum() > 0) {
        Section(t("Пульсовые зоны за 4 недели", "Heart-rate zones, 4 weeks"))
        ZoneStrip(z, height = 14.dp)
        ZoneLegend(z)
        val easy = (z[0] + z[1]) * 100 / z.sum().coerceAtLeast(1)
        Text(if (ru) "Лёгкий бег: $easy% времени. Обычно советуют держать около 80%: лёгкие дозоры строят базу, на которой потом держатся быстрые."
            else "Easy running: $easy% of the time. The usual advice is about 80%: easy patrols build the base the fast ones stand on.", style = T.Muted)
        Text(if (ru) "Зоны считаются от максимального пульса ${w.maxHr}: ${w.zoneEdges.joinToString(" · ")}." else "Zones from a max heart rate of ${w.maxHr}: ${w.zoneEdges.joinToString(" · ")}.", style = T.Small)
    }

    val curve = remember(w) { w.curve(90) }
    if (curve.size > 10) {
        Section(t("Закалка и усталость, 90 дней", "Fitness and fatigue, 90 days"))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val mx = curve.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1.0)
            fun line(sel: (Triple<LocalDate, Double, Double>) -> Double, col: androidx.compose.ui.graphics.Color, wd: Float) {
                val p = Path()
                curve.forEachIndexed { i, c -> val x = size.width * i / (curve.size - 1); val y = (size.height * (1 - sel(c) / mx)).toFloat(); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                drawPath(p, col, style = Stroke(wd))
            }
            line({ it.third }, C.gold, 1.5.dp.toPx())
            line({ it.second }, C.seal, 2.5.dp.toPx())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(t("— закалка (6 недель)", "— fitness (6 weeks)"), style = T.Small.copy(color = C.seal))
            Text(t("— усталость (неделя)", "— fatigue (week)"), style = T.Small.copy(color = C.gold))
        }
    }

    val easy = w.easyPaceByMonth()
    if (easy.size >= 2) {
        Section(t("Сапоги легчают", "Boots getting lighter"))
        val firstP = easy.first().second; val lastP = easy.last().second
        Text(if (ru) "На лёгком пульсе вы бежали ${Fmt.pace(firstP, true)} ${Fmt.monthIn(easy.first().first)}, а теперь ${Fmt.pace(lastP, true)}."
            else "At an easy pulse you ran ${Fmt.pace(firstP, false)} in ${Fmt.month(easy.first().first, false)}, and now ${Fmt.pace(lastP, false)}.", style = T.Lore)
    }

    val rest = w.days.mapNotNull { d -> d.restHr?.let { d.date to it } }.takeLast(60)
    if (rest.size >= 7) {
        Section(t("Пульс покоя", "Resting pulse"), "${rest.last().second}")
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val lo = rest.minOf { it.second } - 2; val hi = rest.maxOf { it.second } + 2
            val p = Path()
            rest.forEachIndexed { i, (_, v) -> val x = size.width * i / (rest.size - 1); val y = size.height * (1 - (v - lo).toFloat() / (hi - lo)); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, C.river, style = Stroke(2.dp.toPx()))
        }
    }

    Section(t("Королевские грамоты", "Royal charters"))
    Card {
        for (i in Digest.DISTANCES.indices) {
            val b = w.bests[i]
            Row(Modifier.fillMaxWidth().clickable(enabled = b != null) { b?.let { nav.go(Overlay.Patrol(it.p.run.id)) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (ru) Fmt.distanceNames[i].first else Fmt.distanceNames[i].second, style = T.Body, modifier = Modifier.weight(1f))
                if (b == null) Text("—", style = T.Muted)
                else Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.best(b.s), style = T.NumberSm)
                    Text(Fmt.date(b.p.date, ru, true), style = T.Small)
                }
            }
        }
    }

    val years = w.patrols.groupBy { it.date.year }.toSortedMap(compareByDescending { it })
    if (years.size > 1) {
        Section(t("По годам", "By year"))
        Card {
            for ((y, ps) in years) Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text("$y", style = T.Strong, modifier = Modifier.weight(1f))
                Text("${Fmt.patrols(ps.size, ru)} · ${Fmt.km(ps.sumOf { it.run.distanceM }, ru)}", style = T.Muted)
            }
        }
    }
    Box(Modifier.size(1.dp))
}
