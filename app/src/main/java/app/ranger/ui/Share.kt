package app.ranger.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.ranger.domain.Bestiary
import app.ranger.domain.Cond
import app.ranger.domain.Event
import app.ranger.domain.Feat
import app.ranger.domain.Ranks
import app.ranger.domain.World
import app.ranger.ui.art.Beast
import app.ranger.ui.art.Crest
import app.ranger.ui.art.InkIcon
import app.ranger.ui.art.Medal
import app.ranger.ui.art.OutpostScene
import app.ranger.ui.art.Painted
import app.ranger.ui.art.flourish
import app.ranger.ui.art.leaf
import app.ranger.ui.art.parchment
import app.ranger.ui.components.Btn
import app.ranger.ui.components.Chip
import app.ranger.ui.components.Sheet
import app.ranger.ui.components.Tag
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.Sh
import app.ranger.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** What a share card shows. */
sealed class Share {
    data class Patrol(val id: Long) : Share()
    data class Creature(val key: String) : Share()
    data class Feat(val key: String) : Share()
    data class Month(val ym: String) : Share()
    /** The days from [from] to [to], inclusive, as ISO dates. */
    data class Period(val from: String, val to: String) : Share()
    /** The ranger: rank, outpost, totals. */
    data object Ranger : Share()
}

/** A small "Share" button with its icon, for the corner of a sheet or a card. */
@Composable
fun ShareButton(onClick: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    val ink = if (dark) C.onDark else C.ink
    val label = t("Поделиться", "Share")
    Row(modifier.clip(CircleShape).background(if (dark) Color.Black.copy(alpha = .35f) else C.deep)
        .border(1.dp, ink.copy(alpha = .25f), CircleShape).clickable(onClick = onClick).semantics { contentDescription = label }
        .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        InkIcon("share", ink, Modifier.size(16.dp)) { ShareGlyph(ink, Modifier.fillMaxSize()) }
        Text(label, style = T.Small.copy(color = ink, fontSize = 13.sp))
    }
}

/** Three linked dots: the share mark, drawn in code. */
@Composable
private fun ShareGlyph(color: Color, modifier: Modifier) = Canvas(modifier) {
    val r = size.minDimension * .14f
    val a = Offset(size.width * .78f, size.height * .2f); val b = Offset(size.width * .22f, size.height * .5f); val c = Offset(size.width * .78f, size.height * .8f)
    drawLine(color, a, b, r * .7f, StrokeCap.Round); drawLine(color, b, c, r * .7f, StrokeCap.Round)
    for (o in listOf(a, b, c)) drawCircle(color, r, o)
}

/** Post (4:5) or story (9:16). */
private enum class Format(val ratio: Float) { POST(.8f), STORY(.5625f) }

/** The card up close, a choice of shape, and the button that sends it as a picture to any app. */
@Composable
fun ShareSheet(w: World, what: Share, nav: Nav) {
    val ctx = LocalContext.current
    val ru = LocalRu.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var format by rememberSaveable { mutableStateOf(Format.POST) }
    var busy by remember { mutableStateOf(false) }
    Sheet(nav::close) {
        Text(t("Поделиться", "Share"), style = T.Title)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(t("Пост", "Post"), format == Format.POST, { format = Format.POST })
            Chip(t("История", "Story"), format == Format.STORY, { format = Format.STORY })
        }
        Box(Modifier.fillMaxWidth(if (format == Format.STORY) .78f else 1f).align(Alignment.CenterHorizontally).aspectRatio(format.ratio)
            .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
            CardFrame(format) { CardBody(w, what, format) }
        }
        Btn(if (busy) t("Готовим открытку…", "Preparing the card…") else t("Отправить открытку", "Send the card"), {
            if (busy) return@Btn
            busy = true
            scope.launch {
                runCatching {
                    val bmp = layer.toImageBitmap().asAndroidBitmap()
                    val file = withContext(Dispatchers.IO) { save(ctx, bmp) }
                    send(ctx, file, if (ru) "Из летописи «Следопыта»" else "From the Ranger's chronicle")
                }.onFailure { nav.toast(if (ru) "Не получилось сохранить открытку." else "Couldn't make the card.") }
                busy = false
            }
        }, Modifier.fillMaxWidth(), enabled = !busy)
    }
}

private fun save(ctx: Context, bmp: android.graphics.Bitmap): File {
    val dir = File(ctx.cacheDir, "cards").apply { mkdirs() }
    // only the latest few are kept; other apps have copied them by the time a new one is made
    dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
    return File(dir, "ranger-${System.currentTimeMillis()}.png").also { f -> f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
}

private fun send(ctx: Context, file: File, text: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.cards", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The card itself: a journal leaf with a double rule, the body, and the app's mark at the foot. */
@Composable
private fun CardFrame(format: Format, body: @Composable ColumnScope.() -> Unit) {
    val ru = LocalRu.current
    Column(Modifier.fillMaxSize().clip(Sh.Lg).parchment(C.page, .12f).border(1.5.dp, C.ink.copy(alpha = .55f), Sh.Lg).padding(6.dp)
        .border(.8.dp, C.ink.copy(alpha = .3f), Sh.Md).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (format == Format.STORY) 12.dp else 8.dp), content = body)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.weight(1f).height(8.dp)) { flourish(0f, size.width, size.height / 2, C.seal, toRight = true) }
            Text(if (ru) "  СЛЕДОПЫТ  " else "  RANGER  ", style = T.Eyebrow.copy(color = C.seal, fontSize = 10.sp))
            Canvas(Modifier.weight(1f).height(8.dp)) { flourish(0f, size.width, size.height / 2, C.seal, toRight = false) }
        }
    }
}

@Composable
private fun ColumnScope.CardBody(w: World, what: Share, format: Format) {
    when (what) {
        is Share.Patrol -> w.patrol(what.id)?.let { PatrolCard(w, it, format) }
        is Share.Creature -> w.hunts[what.key]?.let { CreatureCard(it, format) }
        is Share.Feat -> runCatching { Feat.valueOf(what.key) }.getOrNull()?.let { f -> w.feats[f]?.let { FeatCard(it) } }
        is Share.Month -> MonthCard(w, YearMonth.parse(what.ym), format)
        is Share.Period -> PeriodCard(w, LocalDate.parse(what.from), LocalDate.parse(what.to), format)
        Share.Ranger -> RangerCard(w, format)
    }
}

/** A big number with its label, centred in its column. */
@Composable
private fun Big(value: String, label: String, modifier: Modifier = Modifier, size: Int = 26) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = T.Number.copy(fontSize = size.sp, lineHeight = (size + 2).sp), maxLines = 1)
        Text(label, style = T.Small, maxLines = 1)
    }
}

@Composable
private fun Heading(eyebrow: String, title: String, sub: String? = null) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(eyebrow.uppercase(), style = T.Eyebrow.copy(color = C.seal), maxLines = 1)
        Text(title, style = T.Title, textAlign = TextAlign.Center, maxLines = 2)
        if (sub != null) Text(sub, style = T.Small, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun ColumnScope.PatrolCard(w: World, p: app.ranger.domain.Patrol, format: Format) {
    val ru = LocalRu.current
    val r = p.run
    Heading(p.kind.of(ru), patrolTitle(p, ru), Fmt.weekday(p.date, ru) + ", " + Fmt.time(r.start))
    Box(Modifier.fillMaxWidth().weight(1f).clip(Sh.Md).border(1.dp, C.line, Sh.Md)) {
        if (r.hasTrack) TrailMap(w, p, Modifier.fillMaxSize())
        else OutpostScene(app.ranger.ui.camp(w), Modifier.fillMaxSize())
    }
    Row(Modifier.fillMaxWidth()) {
        Big(Fmt.km(r.distanceM, ru), t("путь", "distance"), Modifier.weight(1f))
        Big(Fmt.clock(if (r.movingS > 0) r.movingS else r.durationS), t("в движении", "moving"), Modifier.weight(1f))
        Big(Fmt.pace(r.pace, ru, false), t("мин/км", "min/km"), Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth()) {
        Big("${r.ascentM.toInt()} " + t("м", "m"), t("набор", "climbed"), Modifier.weight(1f), 18)
        Big(r.avgHr?.toString() ?: "—", t("ср. пульс", "avg pulse"), Modifier.weight(1f), 18)
        Big("+${p.load}", t("доблести", "valour"), Modifier.weight(1f), 18)
    }
    if (p.hasHr && format == Format.STORY) ZoneStrip(p.zones, height = 8.dp)
    val conds = Cond.SHOWN.filter { it in p.conds }.take(if (format == Format.STORY) 6 else 4)
    if (conds.isNotEmpty()) app.ranger.ui.components.Flow(Modifier.fillMaxWidth(), gap = 4.dp) { for (cd in conds) Tag(cd.of(ru), C.soft) }
    val met = p.events.filterIsInstance<Event.Met>().map { it.c }
    if (met.isNotEmpty()) Row(Modifier.fillMaxWidth().leaf().clip(Sh.Md).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in met.take(3)) Beast(c, 3, Modifier.size(40.dp))
        Text((if (ru) "Встреча: " else "Met: ") + met.joinToString(", ") { it.name.of(ru) }, style = T.Strong, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val rec = p.events.filterIsInstance<Event.Record>().firstOrNull()
    if (rec != null) Text((if (ru) "Лучшее время: ${Fmt.distanceNames[rec.i].first.lowercase()} — " else "Best ${Fmt.distanceNames[rec.i].second.lowercase()}: ") + Fmt.best(rec.s),
        style = T.Strong.copy(color = C.gold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.CreatureCard(h: app.ranger.domain.Hunt, format: Format) {
    val ru = LocalRu.current
    val c = h.c
    Spacer(Modifier.weight(.2f))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Beast(c, h.stage, Modifier.fillMaxWidth(if (format == Format.STORY) .9f else .62f).aspectRatio(1f), h.n) }
    Heading(when (c.tier) { 3 -> t("легенда", "legend"); 2 -> t("редкий гость", "rare visitor"); else -> t("обитатель Пограничья", "dweller of the Borderlands") }, c.name.of(ru))
    if (h.stage == 3) Text(c.lore.of(ru), style = T.Lore, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    h.metAt?.let { m ->
        Text(if (ru) "Впервые встречен ${Fmt.date(m.date, true, true)} · встреч: ${h.n}" else "First met ${Fmt.date(m.date, false, true)} · met ×${h.n}",
            style = T.Small.copy(color = C.seal), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.weight(.2f))
}

@Composable
private fun ColumnScope.FeatCard(st: app.ranger.domain.FeatState) {
    val ru = LocalRu.current
    Spacer(Modifier.weight(.3f))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Medal(st, Modifier.fillMaxWidth(.6f).aspectRatio(1f)) }
    Heading(st.f.group.title.of(ru), st.f.title.of(ru))
    Text(st.f.about.of(ru), style = T.Lore, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    st.date?.let { Text(if (ru) "Получен ${Fmt.date(it, true, true)}" else "Earned ${Fmt.date(it, false, true)}", style = T.Small.copy(color = C.seal), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    Spacer(Modifier.weight(.3f))
}

/** Totals over a list of patrols, as three big numbers and three small ones. */
@Composable
private fun Totals(ps: List<app.ranger.domain.Patrol>) {
    val ru = LocalRu.current
    val moving = ps.sumOf { (if (it.run.movingS > 0) it.run.movingS else it.run.durationS).toLong() }
    val km = ps.sumOf { it.run.km }
    Row(Modifier.fillMaxWidth()) {
        Big(Fmt.km(km * 1000, ru), t("путь", "distance"), Modifier.weight(1.2f))
        Big("${ps.size}", Fmt.patrols(ps.size, ru).substringAfter(" "), Modifier.weight(.8f))
        Big(Fmt.dur(moving, ru), t("в пути", "on the trail"), Modifier.weight(1f), 22)
    }
    Row(Modifier.fillMaxWidth()) {
        val pace = ps.filter { it.run.distanceM > 500 }.let { r -> val k = r.sumOf { it.run.km }; if (k < .5) null else r.sumOf { (if (it.run.movingS > 0) it.run.movingS else it.run.durationS).toDouble() } / k }
        Big(pace?.let { Fmt.pace(it, ru, false) } ?: "—", t("средний темп", "avg pace"), Modifier.weight(1f), 18)
        Big("${Fmt.int(ps.sumOf { it.run.ascentM }.toInt(), ru)} " + t("м", "m"), t("набор", "climbed"), Modifier.weight(1f), 18)
        Big(ps.maxOfOrNull { it.run.km }?.let { Fmt.km(it * 1000, ru) } ?: "—", t("самый длинный", "longest"), Modifier.weight(1f), 18)
    }
}

/** The creatures met for the first time among [ps], as a row of portraits. */
@Composable
private fun FirstMeetings(w: World, ps: List<app.ranger.domain.Patrol>) {
    val ru = LocalRu.current
    val ids = ps.map { it.run.id }.toSet()
    val firsts = w.met.filter { it.metAt?.run?.id in ids }
    if (firsts.isEmpty()) return
    Text((if (ru) "Новые встречи: " else "New meetings: ") + firsts.size, style = T.Eyebrow, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        for (h in firsts.take(6)) Beast(h.c, 3, Modifier.size(44.dp))
    }
}

@Composable
private fun ColumnScope.MonthCard(w: World, month: YearMonth, format: Format) {
    val ru = LocalRu.current
    val ps = w.patrols.filter { YearMonth.from(it.date) == month }
    Box(Modifier.fillMaxWidth().weight(.8f).clip(Sh.Md)) { Painted("banners/chronicle.webp", Modifier.fillMaxSize()) }
    Heading(t("Летопись", "Chronicle"), Fmt.month(month.atDay(1), ru))
    Totals(ps)
    // a strip of the month's days, each inked by how far it went
    val byDay = ps.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.run.km } }
    val max = byDay.values.maxOrNull()?.coerceAtLeast(5.0) ?: 10.0
    Bars((1..month.lengthOfMonth()).map { byDay[month.atDay(it)] ?: 0.0 }, Modifier.fillMaxWidth().height(if (format == Format.STORY) 70.dp else 44.dp), color = C.forest, last = C.forest)
    Text(t("километры по дням", "kilometres by day") + " · max ${Fmt.km(max * 1000, ru)}".takeIf { byDay.isNotEmpty() }.orEmpty(), style = T.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    FirstMeetings(w, ps)
}

@Composable
private fun ColumnScope.PeriodCard(w: World, from: LocalDate, to: LocalDate, format: Format) {
    val ru = LocalRu.current
    val ps = w.patrols.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
    Box(Modifier.fillMaxWidth().weight(.8f).clip(Sh.Md)) { Painted("banners/summary.webp", Modifier.fillMaxSize()) }
    Heading(t("Сводка", "Summary"), if (ru) "${Fmt.date(from, true)} — ${Fmt.date(to, true)}" else "${Fmt.date(from, false)} — ${Fmt.date(to, false)}")
    Totals(ps)
    val days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
    val buckets: List<Pair<LocalDate, LocalDate>> = when {
        days <= 14 -> (0 until days).map { from.plusDays(it).let { d -> d to d } }
        days <= 92 -> generateSequence(from) { it.plusWeeks(1) }.takeWhile { !it.isAfter(to) }.map { it to it.plusDays(6) }.toList()
        else -> generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(to) }.map { it.atDay(1) to it.atEndOfMonth() }.toList()
    }
    Bars(buckets.map { (a, b) -> ps.filter { !it.date.isBefore(a) && !it.date.isAfter(b) }.sumOf { it.run.km } },
        Modifier.fillMaxWidth().height(if (format == Format.STORY) 70.dp else 44.dp), color = C.forest, last = C.seal)
    FirstMeetings(w, ps)
}

@Composable
private fun ColumnScope.RangerCard(w: World, format: Format) {
    val ru = LocalRu.current
    Box(Modifier.fillMaxWidth().weight(1f).clip(Sh.Md)) {
        OutpostScene(app.ranger.ui.camp(w), Modifier.fillMaxSize())
        Crest(w.rank, Modifier.align(Alignment.BottomStart).padding(10.dp).size(width = 44.dp, height = 52.dp))
    }
    Heading(t("Пограничье", "The Borderlands"), Ranks.all[w.rank].name.of(ru),
        if (ru) "доблесть ${Fmt.int(w.xp, true)} · ${Fmt.weeks(w.streak, true)} подряд" else "valour ${Fmt.int(w.xp, false)} · ${Fmt.weeks(w.streak, false)} in a row")
    Totals(w.patrols)
    Row(Modifier.fillMaxWidth()) {
        Big("${w.met.size}/${Bestiary.all.size}", t("существ", "creatures"), Modifier.weight(1f), 20)
        Big("${w.feats.values.count { it.done }}/${w.feats.size}", t("подвигов", "feats"), Modifier.weight(1f), 20)
        Big("${w.patrols.count { it.run.race }}", t("турниров", "races"), Modifier.weight(1f), 20)
    }
    if (format == Format.STORY) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        for (h in w.met.sortedByDescending { it.c.tier * 1000 + it.n }.take(5)) Beast(h.c, 3, Modifier.size(48.dp))
    }
}
