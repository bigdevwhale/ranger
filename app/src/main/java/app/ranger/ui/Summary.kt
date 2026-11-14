package app.ranger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ranger.data.Digest
import app.ranger.domain.Patrol
import app.ranger.domain.World
import app.ranger.ui.art.InkIcon
import app.ranger.ui.art.leaf
import app.ranger.ui.components.Card
import app.ranger.ui.components.Chip
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.Sh
import app.ranger.ui.theme.T
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/** The summary's sections, in order, with the icon and name each shows in the jump bar. */
enum class Part(val icon: String, val ru: String, val en: String) {
    TOTALS("stat_distance", "Итоги", "Totals"),
    TRENDS("stat_pace", "Темп и пульс", "Pace & pulse"),
    ZONES("stat_zones", "Зоны", "Zones"),
    FORM("stat_form", "Форма", "Form"),
    STEPS("stat_steps", "Шаги", "Steps"),
    REST("stat_rest", "Покой", "Rest"),
    RECORDS("stat_records", "Рекорды", "Records"),
    YEARS("stat_years", "Годы", "Years"),
}

/** Where each summary section sits in the scrolled page, so the jump bar can take you there. */
@Stable
class Jumps(val scroll: ScrollState) {
    var content: LayoutCoordinates? = null
    val tops = mutableStateMapOf<Part, Float>()
    fun mark(p: Part, c: LayoutCoordinates) { content?.takeIf { it.isAttached && c.isAttached }?.let { tops[p] = it.localPositionOf(c, Offset.Zero).y } }
}

/** A row of the sections that are there; tapping one scrolls to it. */
@Composable
fun JumpBar(j: Jumps, modifier: Modifier = Modifier) {
    val ru = LocalRu.current
    val scope = rememberCoroutineScope()
    val here = j.tops.entries.filter { it.value <= j.scroll.value + 80 }.maxByOrNull { it.value }?.key
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in Part.entries) {
            val top = j.tops[p] ?: continue
            val on = p == here
            Row(Modifier.clip(CircleShape).background(if (on) C.ink else C.deep).border(1.dp, if (on) Color.Transparent else C.ink.copy(alpha = .15f), CircleShape)
                .clickable { scope.launch { j.scroll.animateScrollTo((top - 8).roundToInt().coerceAtLeast(0)) } }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                InkIcon(p.icon, if (on) C.page else C.soft, Modifier.size(15.dp)) {}
                Text(if (ru) p.ru else p.en, style = T.Small.copy(color = if (on) C.page else C.soft, fontSize = 13.sp), maxLines = 1)
            }
        }
    }
}

/** A section of the summary: a card with its icon and title, marked for the jump bar. */
@Composable
private fun Block(j: Jumps, part: Part, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val ru = LocalRu.current
    Card(Modifier.onGloballyPositioned { j.mark(part, it) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(C.sealSoft), contentAlignment = Alignment.Center) {
                InkIcon(part.icon, C.seal, Modifier.size(20.dp)) {}
            }
            Text(if (ru) part.ru else part.en, style = T.Name, modifier = Modifier.weight(1f), maxLines = 1)
            if (note != null) Text(note, style = T.Small, maxLines = 1)
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** The span the summary's first card covers, with the buckets its charts are drawn in. */
private enum class Span { DAYS7, DAYS30, MONTHS12, ALL }

private fun moving(p: Patrol) = (if (p.run.movingS > 0) p.run.movingS else p.run.durationS).toLong()
private fun pace(l: List<Patrol>): Double? = l.filter { it.run.distanceM > 500 }.let { r -> val km = r.sumOf { it.run.km }; if (km < .5) null else r.sumOf { moving(it) } / km }
private fun pulse(l: List<Patrol>): Double? = l.filter { (it.run.avgHr ?: 0) > 0 }.let { r -> val t = r.sumOf { moving(it) }; if (t == 0L) null else r.sumOf { it.run.avgHr!! * moving(it).toDouble() } / t }

/** A change against the span before: an arrow, the amount, green when it's for the better. */
private data class Delta(val text: String, val up: Boolean?, val good: Boolean?)

/** One number in the totals grid: its icon, value, label and how it moved. */
@Composable
private fun Tile(icon: String, value: String, label: String, delta: Delta?, modifier: Modifier) {
    Column(modifier.leaf(C.page).clip(Sh.Md).padding(horizontal = 10.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            InkIcon(icon, C.faint, Modifier.size(14.dp)) {}
            Text(label, style = T.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = T.NumberSm, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        if (delta != null) {
            val col = when (delta.good) { true -> C.forest; false -> C.seal; null -> C.faint }
            Text(when (delta.up) { true -> "↑ "; false -> "↓ "; null -> "= " } + delta.text, style = T.Small.copy(color = col, fontSize = 12.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1)
        } else Text(" ", style = T.Small.copy(fontSize = 11.5.sp))
    }
}

@Composable
fun Summary(w: World, nav: Nav, j: Jumps) {
    Totals(w, nav, j)
    Zones(w, j)
    Form(w, j)
    Steps(w, j)
    Rest(w, j)
    Records(w, nav, j)
    Years(w, j)
    Box(Modifier.size(1.dp))
}

@Composable
private fun Totals(w: World, nav: Nav, j: Jumps) {
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
    val len = java.time.temporal.ChronoUnit.DAYS.between(from, today) + 1
    val before = if (span == Span.ALL) null else w.patrols.filter { !it.date.isBefore(from.minusDays(len)) && it.date.isBefore(from) }

    /** Up or down by a share of the old value, when there's an old value to compare with. */
    fun pct(now: Double, was: Double, moreIsBetter: Boolean = true): Delta? {
        if (before == null || was <= 0) return null
        val d = (now - was) / was * 100
        if (abs(d) < 1) return Delta(t2(ru, "как раньше", "same as before"), null, null)
        return Delta("${abs(d).roundToInt()}%", d > 0, (d > 0) == moreIsBetter)
    }

    Block(j, Part.TOTALS, if (ru) "${Fmt.date(from, true)} — ${Fmt.date(today, true)}" else "${Fmt.date(from, false)} — ${Fmt.date(today, false)}") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((k, l) in listOf(Span.DAYS7 to t("7 дней", "7 days"), Span.DAYS30 to t("30 дней", "30 days"), Span.MONTHS12 to t("год", "a year"), Span.ALL to t("всё время", "all time")))
                Chip(l, span == k, { span = k })
        }
        val km = ps.sumOf { it.run.km }; val kmB = before?.sumOf { it.run.km } ?: 0.0
        val time = ps.sumOf { moving(it) }.toDouble(); val timeB = before?.sumOf { moving(it) }?.toDouble() ?: 0.0
        val up = ps.sumOf { it.run.ascentM }; val upB = before?.sumOf { it.run.ascentM } ?: 0.0
        val pa = pace(ps); val paB = before?.let { pace(it) }
        val hr = pulse(ps); val hrB = before?.let { pulse(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("stat_distance", Fmt.km(km * 1000, ru), t("путь", "distance"), pct(km, kmB), Modifier.weight(1f))
            Tile("stat_patrols", "${ps.size}", Fmt.patrols(ps.size, ru).substringAfter(" "), pct(ps.size.toDouble(), before?.size?.toDouble() ?: 0.0), Modifier.weight(1f))
            Tile("stat_time", Fmt.dur(time.toLong(), ru), t("в пути", "on the trail"), pct(time, timeB), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("stat_pace", pa?.let { Fmt.pace(it, ru, false) } ?: "—", t("темп, мин/км", "pace, min/km"),
                if (pa != null && paB != null && abs(pa - paB) >= 1) Delta("${abs(pa - paB).roundToInt() / 60}:${"%02d".format(abs(pa - paB).roundToInt() % 60)}", pa > paB, pa < paB) else null, Modifier.weight(1f))
            Tile("stat_pulse", hr?.let { "${it.roundToInt()}" } ?: "—", t("ср. пульс", "avg pulse"),
                if (hr != null && hrB != null && abs(hr - hrB) >= 1) Delta("${abs(hr - hrB).roundToInt()}", hr > hrB, null) else null, Modifier.weight(1f))
            Tile("stat_climb", "${Fmt.int(up.toInt(), ru)} ${t("м", "m")}", t("набор", "climbed"), pct(up, upB), Modifier.weight(1f))
        }
        if (before != null) Text(if (ru) "Стрелки — сравнение с предыдущими ${Fmt.days(len.toInt(), true)}." else "Arrows compare with the ${Fmt.days(len.toInt(), false)} before.", style = T.Small)
        ps.maxByOrNull { it.run.km }?.let { p ->
            Row(Modifier.fillMaxWidth().clip(Sh.Md).background(C.goldSoft.copy(alpha = .5f)).clickable { nav.go(Overlay.Patrol(p.run.id)) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkIcon("stat_longest", C.gold, Modifier.size(18.dp)) {}
                Text(t("Самый длинный", "Longest"), style = T.Small, modifier = Modifier.weight(1f))
                Text("${Fmt.km(p.run.distanceM, ru)} · ${Fmt.date(p.date, ru)}", style = T.Strong)
            }
        }
        if (ps.isNotEmpty()) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { ShareButton({ nav.share(Share.Period(from.toString(), today.toString())) }) }
    }

    // days for a week, weeks for a month, months for longer
    val buckets: List<Pair<LocalDate, LocalDate>> = when (span) {
        Span.DAYS7 -> (0L..6L).map { from.plusDays(it).let { d -> d to d } }
        Span.DAYS30 -> generateSequence(from.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(today) }.map { it to it.plusDays(6) }.toList()
        else -> generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(today) }.map { it.atDay(1) to it.atEndOfMonth() }.toList()
    }
    val groups = buckets.map { (a, b) -> ps.filter { !it.date.isBefore(a) && !it.date.isAfter(b) } }
    val unit = when (span) { Span.DAYS7 -> t("по дням", "by day"); Span.DAYS30 -> t("по неделям", "by week"); else -> t("по месяцам", "by month") }
    fun label(d: LocalDate) = when (span) { Span.DAYS7 -> Fmt.weekday(d, ru).take(2); Span.DAYS30 -> "${d.dayOfMonth}.${"%02d".format(d.monthValue)}"; else -> Fmt.month(d, ru, false).take(3) }
    val kms = groups.map { g -> g.sumOf { it.run.km } }
    val paces = groups.map { pace(it) }
    val pulses = groups.map { pulse(it) }
    Block(j, Part.TRENDS, unit) {
        Chart(t("Километры", "Kilometres"), kms.maxOrNull()?.let { Fmt.km(it * 1000, ru) }?.let { t("макс. ", "max ") + it }) {
            Bars(kms, Modifier.fillMaxWidth().height(80.dp), color = C.forest, last = C.seal)
            Axis(label(buckets.first().first), label(buckets.last().first))
        }
        if (paces.count { it != null } >= 2) Chart(t("Темп, мин/км", "Pace, min/km"), t("выше — быстрее", "higher is faster")) {
            Trend(paces, C.seal, lowerIsBetter = true) { Fmt.pace(it, ru, false) }
            Axis(label(buckets.first().first), label(buckets.last().first))
        }
        if (pulses.count { it != null } >= 2) Chart(t("Средний пульс", "Average pulse"), null) {
            Trend(pulses, C.river, lowerIsBetter = false) { "${it.roundToInt()}" }
            Axis(label(buckets.first().first), label(buckets.last().first))
        }
    }
}

/** A small chart with its title and a note to the right. */
@Composable
private fun Chart(title: String, note: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = T.Strong, modifier = Modifier.weight(1f))
            if (note != null) Text(note, style = T.Small)
        }
        Column(Modifier.padding(top = 4.dp), content = content)
    }
}

/** The first and last labels under a chart. */
@Composable
private fun Axis(a: String, b: String) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(a, style = T.Small.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
        Text(b, style = T.Small.copy(fontSize = 11.sp))
    }
}

/** A line through the buckets that have a value, its last value and range beside it. */
@Composable
private fun Trend(values: List<Double?>, color: Color, lowerIsBetter: Boolean, fmt: (Double) -> String) {
    val known = values.filterNotNull()
    val lo = known.min(); val hi = known.max()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height(64.dp).padding(vertical = 6.dp)) {
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
        Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
            Text(fmt(known.last()), style = T.NumberSm.copy(color = color))
            Text("${fmt(if (lowerIsBetter) hi else lo)}–${fmt(if (lowerIsBetter) lo else hi)}", style = T.Small.copy(fontSize = 11.sp))
        }
    }
}

@Composable
private fun Zones(w: World, j: Jumps) {
    val ru = LocalRu.current
    val z = w.zonesSince(28)
    if (z.sum() <= 0) return
    val easy = (z[0] + z[1]) * 100 / z.sum().coerceAtLeast(1)
    Block(j, Part.ZONES, t("4 недели", "4 weeks")) {
        ZoneStrip(z, height = 14.dp)
        ZoneLegend(z)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(if (easy >= 70) C.forestSoft else C.goldSoft), contentAlignment = Alignment.Center) {
                Text("$easy%", style = T.Strong.copy(color = if (easy >= 70) C.forest else C.gold))
            }
            Column(Modifier.weight(1f)) {
                Text(t("Лёгкий бег", "Easy running"), style = T.Strong)
                Text(t("Советуют около 80%: лёгкие дозоры строят базу для быстрых.", "About 80% is the usual advice: easy patrols build the base for fast ones."), style = T.Small)
            }
        }
        Text(if (ru) "От макс. пульса ${w.maxHr}: ${w.zoneEdges.joinToString(" · ")}" else "From max pulse ${w.maxHr}: ${w.zoneEdges.joinToString(" · ")}", style = T.Small.copy(fontSize = 11.sp))
    }
}

@Composable
private fun Form(w: World, j: Jumps) {
    val ru = LocalRu.current
    val curve = remember(w) { w.curve(90) }
    val easy = w.easyPaceByMonth()
    if (curve.size <= 10 && easy.size < 2) return
    Block(j, Part.FORM, t("90 дней", "90 days")) {
        if (curve.size > 10) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Legend(C.seal, "${curve.last().second.roundToInt()}", t("закалка, 6 нед.", "fitness, 6 wk"))
                Legend(C.gold, "${curve.last().third.roundToInt()}", t("усталость, 7 дн.", "fatigue, 7 d"))
            }
            Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                val mx = curve.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1.0)
                fun line(sel: (Triple<LocalDate, Double, Double>) -> Double, col: Color, wd: Float) {
                    val p = Path()
                    curve.forEachIndexed { i, c -> val x = size.width * i / (curve.size - 1); val y = (size.height * (1 - sel(c) / mx)).toFloat(); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                    drawPath(p, col, style = Stroke(wd))
                }
                line({ it.third }, C.gold, 1.5.dp.toPx())
                line({ it.second }, C.seal, 2.5.dp.toPx())
            }
            Axis(Fmt.date(curve.first().first, ru), Fmt.date(curve.last().first, ru))
        }
        if (easy.size >= 2) Row(Modifier.fillMaxWidth().clip(Sh.Md).background(C.forestSoft.copy(alpha = .6f)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkIcon("stat_easy", C.forest, Modifier.size(20.dp)) {}
            Text(if (ru) "На лёгком пульсе: ${Fmt.pace(easy.first().second, true)} ${Fmt.monthIn(easy.first().first)} → ${Fmt.pace(easy.last().second, true)} сейчас"
                else "At an easy pulse: ${Fmt.pace(easy.first().second, false)} in ${Fmt.month(easy.first().first, false)} → ${Fmt.pace(easy.last().second, false)} now", style = T.Body)
        }
    }
}

/** A coloured mark, a number and what it is: a chart's legend that also reads as the latest value. */
@Composable
private fun Legend(color: Color, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(width = 14.dp, height = 4.dp).clip(CircleShape).background(color))
        Text(value, style = T.NumberSm.copy(color = color))
        Text(label, style = T.Small)
    }
}

@Composable
private fun Steps(w: World, j: Jumps) {
    val ru = LocalRu.current
    val steps = w.steps(28)
    val known = steps.filter { it.second > 0 }
    if (known.isEmpty()) return
    val atGoal = steps.count { it.second >= w.s.stepGoal }
    Block(j, Part.STEPS, t("4 недели", "4 weeks")) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat(Fmt.int(known.map { it.second }.average().toInt(), ru), t("в среднем за день", "a day on average"))
            Stat("$atGoal / 28", if (ru) "дней с нормой ${Fmt.int(w.s.stepGoal, true)}" else "days at ${Fmt.int(w.s.stepGoal, false)}")
        }
        Bars(steps.map { it.second.toDouble() }, Modifier.fillMaxWidth().height(64.dp), color = C.forest, last = C.seal)
        Axis(Fmt.date(steps.first().first, ru), t("сегодня", "today"))
    }
}

@Composable
private fun Rest(w: World, j: Jumps) {
    val ru = LocalRu.current
    val rest = w.days.mapNotNull { d -> d.restHr?.let { d.date to it } }.takeLast(60)
    if (rest.size < 7) return
    val lo = rest.minOf { it.second }; val hi = rest.maxOf { it.second }
    Block(j, Part.REST, t("пульс покоя", "resting pulse")) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat("${rest.last().second}", t("сегодня", "today"))
            Stat("$lo–$hi", if (ru) "за ${Fmt.days(rest.size, true)}" else "over ${Fmt.days(rest.size, false)}")
        }
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val a = lo - 2; val b = hi + 2
            val p = Path()
            rest.forEachIndexed { i, (_, v) -> val x = size.width * i / (rest.size - 1); val y = size.height * (1 - (v - a).toFloat() / (b - a)); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, C.river, style = Stroke(2.dp.toPx()))
        }
        Text(t("Если утром он заметно выше обычного, телу нужен отдых.", "When it's well above usual in the morning, the body needs rest."), style = T.Small)
    }
}

@Composable
private fun Records(w: World, nav: Nav, j: Jumps) {
    val ru = LocalRu.current
    Block(j, Part.RECORDS, "${w.bests.count { it != null }} / ${Digest.DISTANCES.size}") {
        Column {
            for (i in Digest.DISTANCES.indices) {
                val b = w.bests[i]
                Row(Modifier.fillMaxWidth().clip(Sh.Sm).clickable(enabled = b != null) { b?.let { nav.go(Overlay.Patrol(it.p.run.id)) } }.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (b != null) C.gold else C.line))
                    Text(if (ru) Fmt.distanceNames[i].first else Fmt.distanceNames[i].second, style = T.Body, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    if (b == null) Text("—", style = T.Muted)
                    else Column(horizontalAlignment = Alignment.End) {
                        Text(Fmt.best(b.s), style = T.NumberSm)
                        Text(Fmt.date(b.p.date, ru, true), style = T.Small)
                    }
                }
            }
        }
    }
}

@Composable
private fun Years(w: World, j: Jumps) {
    val ru = LocalRu.current
    val years = w.patrols.groupBy { it.date.year }.toSortedMap(compareByDescending { it })
    if (years.size < 2) return
    val max = years.values.maxOf { l -> l.sumOf { it.run.km } }.coerceAtLeast(1.0)
    Block(j, Part.YEARS) {
        for ((y, ps) in years) {
            val km = ps.sumOf { it.run.km }
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$y", style = T.Strong, modifier = Modifier.weight(1f))
                    Text("${Fmt.km(km * 1000, ru)} · ${Fmt.patrols(ps.size, ru)}", style = T.Small)
                }
                app.ranger.ui.components.Bar((km / max).toFloat(), Modifier.fillMaxWidth().padding(top = 4.dp), color = C.forest)
            }
        }
    }
}

private fun t2(ru: Boolean, a: String, b: String) = if (ru) a else b
