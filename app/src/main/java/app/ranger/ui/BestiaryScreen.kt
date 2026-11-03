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
import androidx.compose.ui.unit.dp
import app.ranger.domain.Bestiary
import app.ranger.domain.World
import app.ranger.ui.art.Seal
import app.ranger.ui.components.Section
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.Sh
import app.ranger.ui.theme.T

@Composable
fun BestiaryScreen(w: World, nav: Nav) {
    val ru = LocalRu.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Бестиарий Пограничья", "Bestiary of the Borderlands"), style = T.Title)
        Text(if (ru) "Встречено ${w.met.size} из ${Bestiary.all.size}. Каждое существо живёт при своей погоде, в своё время суток или на своей тропе. Нажмите на печать, чтобы узнать, где его искать."
            else "Met ${w.met.size} of ${Bestiary.all.size}. Each creature lives in its own weather, hour or kind of trail. Tap a seal to learn where to look.", style = T.Muted)
        for ((tier, title) in listOf(1 to t("Обитатели", "Dwellers"), 2 to t("Редкие гости", "Rare visitors"), 3 to t("Легенды", "Legends"))) {
            val list = Bestiary.all.filter { it.tier == tier }
            Section(title, "${list.count { w.hunts[it.key]?.stage == 3 }} / ${list.size}")
            list.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in row) {
                        val h = w.hunts[c.key]!!
                        Column(Modifier.weight(1f).clip(Sh.Md).clickable { nav.go(Overlay.Creature(c.key)) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth(.72f).aspectRatio(1f)) { Seal(c, h.stage, Modifier.fillMaxSize()) }
                            Text(c.name.of(ru), style = T.Strong.copy(fontSize = T.Small.fontSize * 1.12f, color = if (h.stage == 3) C.ink else C.faint), textAlign = TextAlign.Center, maxLines = 2, minLines = 2,
                                modifier = Modifier.padding(top = 4.dp))
                            Text(when (h.stage) { 3 -> mastery(h.n, ru); 2 -> t("логово", "lair"); 1 -> t("следы", "tracks"); else -> " " }, style = T.Small.copy(color = if (h.stage == 3) C.seal else C.faint))
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Met once, then a star at 10, 30 and 100 meetings: the bestiary keeps something for later years. */
fun mastery(n: Int, ru: Boolean): String {
    val stars = listOf(10, 30, 100).count { n >= it }
    return if (stars == 0) (if (ru) "встреч: $n" else "met ×$n") else "★".repeat(stars) + " $n"
}
