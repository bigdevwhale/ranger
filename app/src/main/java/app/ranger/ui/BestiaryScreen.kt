package app.ranger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ranger.domain.Bestiary
import app.ranger.domain.Hunt
import app.ranger.domain.World
import app.ranger.ui.art.Seal
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(if (ru) "Существа ${w.met.size}/${Bestiary.all.size}" else "Creatures ${w.met.size}/${Bestiary.all.size}", !nav.trophies, { nav.trophies = false })
            val feats = w.feats.values
            Chip(if (ru) "Подвиги ${feats.count { it.done }}/${feats.size}" else "Feats ${feats.count { it.done }}/${feats.size}", nav.trophies, { nav.trophies = true })
        }
        if (nav.trophies) Trophies(w, nav) else Creatures(w, nav)
    }
}

@Composable
private fun Creatures(w: World, nav: Nav) {
    val ru = LocalRu.current
    Card {
        Text(t("Бестиарий Пограничья", "Bestiary of the Borderlands"), style = T.Eyebrow)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text("${w.met.size}", style = T.Number)
            Text(if (ru) " из ${Bestiary.all.size} встречено" else " of ${Bestiary.all.size} met", style = T.Muted, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        }
        Bar(w.met.size.toFloat() / Bestiary.all.size, Modifier.fillMaxWidth().padding(top = 8.dp))
        Text(t("Каждое существо живёт при своей погоде, в своё время суток или на своей тропе. Первый подходящий дозор находит следы, следующие ведут к логову, последний — встреча. Нажмите на печать, чтобы узнать, где искать.",
            "Each creature keeps to its own weather, hour or kind of trail. The first fitting patrol finds tracks, the next lead to the lair, the last is the meeting. Tap a seal to learn where to look."),
            style = T.Small, modifier = Modifier.padding(top = 8.dp))
    }
    // the hunts under way come first: they're what the next patrols can finish
    val going = w.hunts.values.filter { it.stage in 1..2 }.sortedByDescending { it.n.toFloat() / it.c.need }
    if (going.isNotEmpty()) {
        Section(t("Идёт охота", "On the hunt"), "${going.size}")
        Grid(going, nav)
    }
    for ((tier, title) in listOf(1 to t("Обитатели", "Dwellers"), 2 to t("Редкие гости", "Rare visitors"), 3 to t("Легенды", "Legends"))) {
        val list = Bestiary.all.filter { it.tier == tier }.map { w.hunts[it.key]!! }
        Section(title, "${list.count { it.stage == 3 }} / ${list.size}")
        Grid(list.sortedByDescending { it.stage == 3 }, nav)
    }
}

@Composable
private fun Grid(list: List<Hunt>, nav: Nav) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        list.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (h in row) SealCell(h, nav, Modifier.weight(1f))
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SealCell(h: Hunt, nav: Nav, modifier: Modifier) {
    val ru = LocalRu.current
    val c = h.c
    Column(modifier.clip(Sh.Md).clickable { nav.go(Overlay.Creature(c.key)) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(.8f).aspectRatio(1f)) { Seal(c, h.stage, Modifier.fillMaxSize()) }
        NameSlot(c.name.of(ru), T.Strong.copy(fontSize = T.Small.fontSize, lineHeight = T.Small.lineHeight, color = if (h.stage == 3) C.ink else C.soft), Modifier.padding(top = 4.dp))
        Text(when (h.stage) { 3 -> mastery(h.n, ru); 0 -> " "; else -> "${h.n}/${c.need}" },
            style = T.Small.copy(color = when (h.stage) { 3 -> C.seal; 0 -> C.faint; else -> C.gold }), maxLines = 1)
    }
}

/** Met once, then a star at 10, 30 and 100 meetings: the bestiary keeps something for later years. */
fun mastery(n: Int, ru: Boolean): String {
    val stars = listOf(10, 30, 100).count { n >= it }
    return if (stars == 0) (if (ru) "встреч: $n" else "met ×$n") else "★".repeat(stars) + " $n"
}
