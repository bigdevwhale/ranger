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

/** The span the summary's first card covers, with the buckets its trends are drawn in. */
private enum class Span { DAYS7, DAYS30, MONTHS12, ALL }

/** Totals and averages over a chosen span, the span before it for comparison, and pace and pulse trends. */
@Composable
private fun PeriodStats(w: World) {
    val ru = LocalRu.current
    var span by rememberSaveable { mutableStateOf(Span.DAYS30) }
    val today = w.today
    val first = w.patrols.minOfOrNull { it.date } ?: today
    val from = when (span) {
        Span.DAYS7 -> today.minusDays(6)
        Span.DAYS30 -> today.minusDays(29)
        Span.MONTHS12 -> today.minusMonths(12).plusDays(1)
        Span.ALL -> first
    }
    val ps = w.patrols.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
    // the same length just before, for the "compared with" line
    val len = java.time.temporal.ChronoUnit.DAYS.between(from, today) + 1
    val before = if (span == Span.ALL) emptyList() else w.patrols.filter { !it.date.isBefore(from.minusDays(len)) && it.date.isBefore(from) }

    fun moving(p: Patrol) = (if (p.run.movingS > 0) p.run.movingS else p.run.durationS).toLong()
    fun pace(l: List<Patrol>): Double? = l.filter { it.run.distanceM > 500 }.let { r -> val km = r.sumOf { it.run.km }; if (km < .5) null else r.sumOf { moving(it) } / km }
    fun pulse(l: List<Patrol>): Double? = l.filter { (it.run.avgHr ?: 0) > 0 }.let { r -> val t = r.sumOf { moving(it) }; if (t == 0L) null else r.sumOf { it.run.avgHr!! * moving(it).toDouble() } / t }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((k, l) in listOf(Span.DAYS7 to t("7 дней", "7 days"), Span.DAYS30 to t("30 дней", "30 days"), Span.MONTHS12 to t("год", "a year"), Span.ALL to t("всё время", "all time")))
            Chip(l, span == k, { span = k })
    }
    Card {
        Text(if (ru) "${Fmt.date(from, true)} — ${Fmt.date(today, true)}" else "${Fmt.date(from, false)} — ${Fmt.date(today, false)}", style = T.Eyebrow)
        val km = ps.sumOf { it.run.km }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat(Fmt.km(km * 1000, ru), Fmt.patrols(ps.size, ru))
            Stat(Fmt.dur(ps.sumOf { moving(it) }, ru), t("в пути", "on the trail"))
            Stat("${Fmt.int(ps.sumOf { it.run.ascentM }.toInt(), ru)} ${t("м", "m")}", t("набор", "climbed"))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat(pace(ps)?.let { Fmt.pace(it, ru, false) } ?: "—", t("средний темп", "average pace"))
            Stat(pulse(ps)?.let { "${it.toInt()}" } ?: "—", t("средний пульс", "average pulse"))
            Stat(ps.maxOfOrNull { it.run.km }?.let { Fmt.km(it * 1000, ru) } ?: "—", t("самый длинный", "longest"))
        }
        if (span != Span.ALL) {
            val kb = before.sumOf { it.run.km }
            val parts = buildList {
                add(if (ru) "за прошлые $len дн. было ${Fmt.km(kb * 1000, true)}" else "the $len days before: ${Fmt.km(kb * 1000, false)}")
                val a = pace(ps); val b = pace(before)
                if (a != null && b != null) {
                    val d = (b - a).toInt(); val mmss = "${kotlin.math.abs(d) / 60}:${"%02d".format(kotlin.math.abs(d) % 60)}"
                    add(if (ru) (if (d >= 0) "темп быстрее на $mmss" else "темп медленнее на $mmss") else (if (d >= 0) "pace $mmss faster" else "pace $mmss slower"))
                }
                val h1 = pulse(ps); val h0 = pulse(before)
                if (h1 != null && h0 != null) { val d = (h1 - h0).toInt(); add(if (ru) "пульс ${if (d >= 0) "+$d" else "$d"}" else "pulse ${if (d >= 0) "+$d" else "$d"}") }
            }
            Text(parts.joinToString(", ").replaceFirstChar { it.uppercase() } + ".", style = T.Small, modifier = Modifier.padding(top = 8.dp))
        }
    }

    // buckets: days for a week, weeks for a month, months for longer
    val buckets: List<Pair<LocalDate, LocalDate>> = when (span) {
        Span.DAYS7 -> (0L..6L).map { from.plusDays(it).let { d -> d to d } }
        Span.DAYS30 -> generateSequence(from.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(today) }.map { it to it.plusDays(6) }.toList()
        else -> generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(today) }.map { it.atDay(1) to it.atEndOfMonth() }.toList()
    }
    val groups = buckets.map { (a, b) -> ps.filter { !it.date.isBefore(a) && !it.date.isAfter(b) } }
    val unit = when (span) { Span.DAYS7 -> t("по дням", "by day"); Span.DAYS30 -> t("по неделям", "by week"); else -> t("по месяцам", "by month") }
    Section(t("Километры", "Kilometres"), unit)
    Bars(groups.map { g -> g.sumOf { it.run.km } }, Modifier.fillMaxWidth().height(80.dp))
    val paces = groups.map { pace(it) }
    if (paces.count { it != null } >= 2) {
        Section(t("Темп", "Pace"), unit)
        Trend(paces, C.seal, lowerIsBetter = true) { Fmt.pace(it, ru, false) }
    }
    val pulses = groups.map { pulse(it) }
    if (pulses.count { it != null } >= 2) {
        Section(t("Средний пульс", "Average pulse"), unit)
        Trend(pulses, C.river, lowerIsBetter = false) { "${it.toInt()}" }
    }
}

/** A line through the buckets that have a value; for pace the faster end is drawn on top. */
@Composable
private fun Trend(values: List<Double?>, color: androidx.compose.ui.graphics.Color, lowerIsBetter: Boolean, fmt: (Double) -> String) {
    val known = values.filterNotNull()
    val lo = known.min(); val hi = known.max()
    Canvas(Modifier.fillMaxWidth().height(80.dp).padding(vertical = 6.dp)) {
        val range = (hi - lo).coerceAtLeast(1e-6)
        val p = Path(); var started = false
        values.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val x = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
            val f = ((v - lo) / range).toFloat()
            val y = size.height * (if (lowerIsBetter) f else 1 - f)
            if (!started) { p.moveTo(x, y); started = true } else p.lineTo(x, y)
            drawCircle(color, 3.dp.toPx(), Offset(x, y))
        }
        drawPath(p, color, style = Stroke(2.dp.toPx()))
    }
    val ru = LocalRu.current
    val best = if (lowerIsBetter) lo else hi
    Text(if (ru) "Сейчас ${fmt(known.last())}, разброс ${fmt(lo)}–${fmt(hi)}" + (if (lowerIsBetter) ", лучший ${fmt(best)}" else "") + "."
        else "Now ${fmt(known.last())}, range ${fmt(lo)}–${fmt(hi)}" + (if (lowerIsBetter) ", best ${fmt(best)}" else "") + ".", style = T.Small)
}

@Composable
private fun Summary(w: World, nav: Nav) {
    val ru = LocalRu.current
    PeriodStats(w)

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

    val steps = w.steps(28)
    if (steps.any { it.second > 0 }) {
        val known = steps.filter { it.second > 0 }
        Section(t("Шаги за 4 недели", "Steps, 4 weeks"), if (ru) "в среднем ${Fmt.int(known.map { it.second }.average().toInt(), true)}" else "avg ${Fmt.int(known.map { it.second }.average().toInt(), false)}")
        Bars(steps.map { it.second.toDouble() }, Modifier.fillMaxWidth().height(70.dp), color = C.forest, last = C.seal)
        Text(if (ru) "Дней с нормой ${Fmt.int(w.s.stepGoal, true)}: ${steps.count { it.second >= w.s.stepGoal }} из 28." else "Days at ${Fmt.int(w.s.stepGoal, false)}: ${steps.count { it.second >= w.s.stepGoal }} of 28.", style = T.Small)
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
