package app.ranger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ranger.domain.Bestiary
import app.ranger.domain.Hunt
import app.ranger.domain.World
import app.ranger.ui.art.Beast
import app.ranger.ui.art.leaf
import app.ranger.ui.components.Bar
import app.ranger.ui.components.Card
import app.ranger.ui.components.Chip
import app.ranger.ui.components.Section
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.Sh
import app.ranger.ui.theme.T

@Composable
fun BestiaryScreen(w: World, nav: Nav) {
    val ru = LocalRu.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    app.ranger.ui.art.Banner(if (nav.trophies) "feats" else "bestiary", Modifier.fillMaxWidth().aspectRatio(2.6f))
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(if (ru) "Существа ${w.met.size}/${Bestiary.all.size}" else "Creatures ${w.met.size}/${Bestiary.all.size}", !nav.trophies, { nav.trophies = false })
            val feats = w.feats.values
            Chip(if (ru) "Подвиги ${feats.count { it.done }}/${feats.size}" else "Feats ${feats.count { it.done }}/${feats.size}", nav.trophies, { nav.trophies = true })
        }
        if (nav.trophies) Trophies(w, nav) else Creatures(w, nav)
    }
    }
}

/** Which creatures the grid shows. */
private enum class Show { ALL, MET, TRAIL, UNSEEN }

@Composable
private fun Creatures(w: World, nav: Nav) {
    val ru = LocalRu.current
    var show by rememberSaveable { mutableStateOf(Show.ALL) }
    val hunts = Bestiary.all.map { w.hunts[it.key]!! }
    val going = hunts.filter { it.stage in 1..2 }
    Card {
        Text(t("Бестиарий Пограничья", "Bestiary of the Borderlands"), style = T.Eyebrow)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text("${w.met.size}", style = T.Number)
            Text(if (ru) " из ${Bestiary.all.size} встречено" else " of ${Bestiary.all.size} met", style = T.Muted, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
            Box(Modifier.weight(1f))
            if (going.isNotEmpty()) Text(if (ru) "на следу: ${going.size}" else "on the trail: ${going.size}", style = T.Small.copy(color = C.gold), modifier = Modifier.padding(bottom = 4.dp))
        }
        Bar(w.met.size.toFloat() / Bestiary.all.size, Modifier.fillMaxWidth().padding(top = 8.dp))
        // one bar per tier, so it's clear where the rare ones are still hiding
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for ((tier, title) in listOf(1 to t("обитатели", "dwellers"), 2 to t("редкие", "rare"), 3 to t("легенды", "legends"))) {
                val l = hunts.filter { it.c.tier == tier }
                Column(Modifier.weight(1f)) {
                    Text("${l.count { it.stage == 3 }}/${l.size}", style = T.Strong)
                    Text(title, style = T.Small)
                    Bar(l.count { it.stage == 3 }.toFloat() / l.size, Modifier.fillMaxWidth().padding(top = 4.dp), color = if (tier == 3) C.gold else if (tier == 2) C.river else C.forest)
                }
            }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(t("Все", "All"), show == Show.ALL, { show = Show.ALL })
        Chip(t("Встречены", "Met") + " ${w.met.size}", show == Show.MET, { show = Show.MET })
        Chip(t("На следу", "On the trail") + " ${going.size}", show == Show.TRAIL, { show = Show.TRAIL }, color = C.gold)
        Chip(t("Не видели", "Unseen") + " ${hunts.count { it.stage == 0 }}", show == Show.UNSEEN, { show = Show.UNSEEN })
    }
    val fits: (Hunt) -> Boolean = when (show) {
        Show.ALL -> { _ -> true }
        Show.MET -> { h -> h.stage == 3 }
        Show.TRAIL -> { h -> h.stage in 1..2 }
        Show.UNSEEN -> { h -> h.stage == 0 }
    }
    // the hunts under way come first: they're what the next patrols can finish
    if (show == Show.ALL && going.isNotEmpty()) {
        Section(t("Идёт охота", "On the hunt"), "${going.size}")
        Grid(going.sortedByDescending { it.n.toFloat() / it.c.need }, nav)
    }
    var any = false
    for ((tier, title) in listOf(1 to t("Обитатели", "Dwellers"), 2 to t("Редкие гости", "Rare visitors"), 3 to t("Легенды", "Legends"))) {
        val all = hunts.filter { it.c.tier == tier }
        val list = all.filter(fits)
        if (list.isEmpty()) continue
        any = true
        Section(title, "${all.count { it.stage == 3 }} / ${all.size}")
        Grid(list.sortedByDescending { it.stage * 100 + it.n }, nav)
    }
    if (!any) Text(when (show) {
        Show.MET -> t("Пока никого. Первая встреча ждёт на тропе.", "No one yet. The first meeting is out on the trail.")
        Show.TRAIL -> t("Свежих следов нет. Попробуйте новую погоду, час или тропу.", "No fresh tracks. Try a new kind of weather, hour or trail.")
        else -> t("Вы встретили всех. Пограничье гордится вами.", "You've met them all. The Borderlands are proud of you.")
    }, style = T.Lore, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
}

@Composable
private fun Grid(list: List<Hunt>, nav: Nav) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (h in row) BeastCell(h, nav, Modifier.weight(1f))
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BeastCell(h: Hunt, nav: Nav, modifier: Modifier) {
    val ru = LocalRu.current
    val c = h.c
    Column(modifier.leaf(if (h.stage == 3) C.page else C.bg).clip(Sh.Md).clickable { nav.go(Overlay.Creature(c.key)) }.padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Beast(c, h.stage, Modifier.fillMaxWidth(.86f).aspectRatio(1f), h.n)
        NameSlot(c.name.of(ru),T.Strong.copy(fontSize = T.Small.fontSize * 1.08f, lineHeight = T.Small.lineHeight * 1.05f, color = if (h.stage == 3) C.ink else C.soft), Modifier.padding(top = 6.dp))
        Text(status(h, ru), style = T.Small.copy(color = when (h.stage) { 3 -> C.seal; 0 -> C.faint; else -> C.gold }), maxLines = 1)
    }
}

/** Under a portrait: how often met, how far along the trail, or nothing yet. */
fun status(h: Hunt, ru: Boolean): String = when (h.stage) {
    3 -> mastery(h.n, ru)
    2 -> (if (ru) "логово" else "lair") + " ${h.n}/${h.c.need}"
    1 -> (if (ru) "следы" else "tracks") + " ${h.n}/${h.c.need}"
    else -> if (ru) "не встречен" else "not yet seen"
}

/** Met once, then a star at 10, 30 and 100 meetings: the bestiary keeps something for later years. */
fun mastery(n: Int, ru: Boolean): String {
    val stars = listOf(10, 30, 100).count { n >= it }
    return if (stars == 0) (if (ru) "встреч: $n" else "met ×$n") else "★".repeat(stars) + " $n"
}
