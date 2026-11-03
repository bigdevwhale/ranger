package app.ranger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ranger.domain.Feat
import app.ranger.domain.FeatGroup
import app.ranger.domain.FeatState
import app.ranger.domain.World
import app.ranger.ui.art.Medal
import app.ranger.ui.art.ink
import app.ranger.ui.components.Bar
import app.ranger.ui.components.Btn
import app.ranger.ui.components.Card
import app.ranger.ui.components.Look
import app.ranger.ui.components.Section
import app.ranger.ui.components.Sheet
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.Sh
import app.ranger.ui.theme.T
import kotlin.math.roundToInt

private val KM = setOf(Feat.FIVE, Feat.TEN, Feat.FIFTEEN, Feat.HALF, Feat.THIRTY, Feat.MARATHON, Feat.KM100, Feat.KM500, Feat.KM1000, Feat.KM2500,
    Feat.WEEK30, Feat.WEEK50, Feat.MONTH100, Feat.MONTH200)

/** "3,2 из 5 км", "7 из 10 дозоров": how far along, in the feat's own units. */
fun FeatState.progressText(ru: Boolean): String? {
    if (done || goal <= 0) return null
    // whole goals read whole ("30", not "30,0"); kilometres run so far keep a decimal
    fun n(v: Double) = if (f in KM && v < 100 && v != kotlin.math.floor(v)) Fmt.km(v * 1000, ru, unit = false) else Fmt.int(v.roundToInt(), ru)
    val unit = when {
        f in KM -> if (ru) "км" else "km"
        f.name.startsWith("PATROLS") -> if (ru) "дозоров" else "patrols"
        f == Feat.CLIMB1000 || f == Feat.EVEREST -> if (ru) "м" else "m"
        f == Feat.ROW3 || f == Feat.ROW7 -> if (ru) "дней" else "days"
        f == Feat.SEASONS -> if (ru) "сезонов" else "seasons"
        f == Feat.ELEMENTS -> if (ru) "стихий" else "elements"
        f.group == FeatGroup.LORE -> if (ru) "существ" else "creatures"
        else -> ""
    }
    return (if (ru) "${n(now)} из ${n(goal)}" else "${n(now)} of ${n(goal)}") + if (unit.isEmpty()) "" else " $unit"
}

/** The feats not yet earned that are furthest along, the ones worth aiming at next. */
fun World.nearFeats(n: Int): List<FeatState> = feats.values.filter { !it.done && it.share > 0 }.sortedByDescending { it.share }.take(n)

/** The trophy hall: every feat by shelf, earned ones struck in metal, the rest pencilled with their progress. */
@Composable
fun ColumnScope.Trophies(w: World, nav: Nav) {
    val ru = LocalRu.current
    val all = w.feats.values
    val done = all.count { it.done }
    Card {
        Text(t("Зал подвигов", "Hall of feats"), style = T.Eyebrow)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text("$done", style = T.Number)
            Text(if (ru) " из ${all.size}" else " of ${all.size}", style = T.Muted, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        }
        Bar(done.toFloat() / all.size, Modifier.fillMaxWidth().padding(top = 8.dp), color = C.gold)
        Text(t("Подвиг даётся один раз — за дистанцию, погоду, час, компанию или упорство. Нажмите на медаль, чтобы узнать подробности.",
            "Each feat is earned once — for a distance, the weather, the hour, company or persistence. Tap a medal for details."),
            style = T.Small, modifier = Modifier.padding(top = 8.dp))
    }
    val near = w.nearFeats(3)
    if (near.isNotEmpty()) {
        Section(t("Ближе всего", "Almost there"))
        MedalRow(near, nav)
    }
    for (g in FeatGroup.entries) {
        val list = all.filter { it.f.group == g }
        Section(g.title.of(ru), "${list.count { it.done }} / ${list.size}")
        // earned first, so each shelf reads as what's won, then what's left
        MedalGrid(list.sortedByDescending { it.done }, nav)
    }
}

@Composable
fun MedalRow(list: List<FeatState>, nav: Nav) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (st in list) MedalCell(st, nav, Modifier.weight(1f))
        repeat(3 - list.size) { Box(Modifier.weight(1f)) }
    }
}

@Composable
private fun MedalGrid(list: List<FeatState>, nav: Nav) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        list.chunked(3).forEach { MedalRow(it, nav) }
    }
}

@Composable
private fun MedalCell(st: FeatState, nav: Nav, modifier: Modifier) {
    val ru = LocalRu.current
    Column(modifier.clip(Sh.Md).clickable { nav.go(Overlay.Feat(st.f.name)) }.padding(vertical = 6.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Medal(st, Modifier.fillMaxWidth(.62f).aspectRatio(1f))
        NameSlot(st.f.title.of(ru), T.Strong.copy(fontSize = T.Small.fontSize * 1.08f, lineHeight = T.Small.lineHeight * 1.05f, color = if (st.done) C.ink else C.soft), Modifier.padding(top = 4.dp))
        Text(st.date?.let { Fmt.date(it, ru) } ?: st.progressText(ru) ?: "—", style = T.Small.copy(color = if (st.done) st.f.group.ink() else C.faint),
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One feat up close: the medal, what it asks, when it came or how far is left. */
@Composable
fun FeatSheet(w: World, f: Feat, nav: Nav) {
    val ru = LocalRu.current
    val st = w.feats[f] ?: return
    Sheet(nav::close) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Medal(st, Modifier.size(150.dp))
            Text(f.group.title.of(ru).uppercase(), style = T.Eyebrow.copy(color = f.group.ink()), modifier = Modifier.padding(top = 6.dp))
            Text(f.title.of(ru), style = T.Title, textAlign = TextAlign.Center)
            Text(f.about.of(ru), style = T.Body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        if (st.done) {
            Text(if (ru) "Получен ${Fmt.date(st.date!!, true, true)}." else "Earned on ${Fmt.date(st.date!!, false, true)}.", style = T.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            st.by?.let { p -> Btn(t("Открыть тот дозор", "Open that patrol"), { nav.go(Overlay.Patrol(p.run.id)) }, Modifier.fillMaxWidth(), kind = Look.SOFT) }
        } else {
            st.progressText(ru)?.let { txt ->
                Bar(st.share.toFloat(), Modifier.fillMaxWidth(), color = f.group.ink())
                Text(txt, style = T.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } ?: Text(t("Пока не получен. Он придёт сам, как только дозор подойдёт под условие.", "Not yet. It comes by itself once a patrol fits."),
                style = T.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
