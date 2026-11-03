package app.ranger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ranger.container
import app.ranger.domain.Bestiary
import app.ranger.ui.art.Camp
import app.ranger.ui.art.OutpostScene
import app.ranger.ui.art.Seal
import app.ranger.ui.components.Btn
import app.ranger.ui.components.Look
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.T

@Composable
fun Onboarding() {
    val c = LocalContext.current.container
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf(
        t("На краю королевства лежит Пограничье", "At the edge of the kingdom lie the Borderlands") to
            t("Леса, реки, холмы и улицы вашего города. Вы следопыт, и каждая пробежка — это дозор.", "Woods, rivers, hills and the streets of your town. You are a ranger, and every run is a patrol."),
        t("Кого встретишь, решает сама пробежка", "Who you meet depends on the run") to
            t("Дождь, туман, рассвет, полнолуние, вода вдоль пути, подъёмы, лёгкий пульс или рывки. У каждого из ${app.ranger.domain.Bestiary.all.size} существ свои условия: сначала следы, потом логово, потом встреча.",
                "Rain, fog, sunrise, a full moon, water along the way, climbs, an easy pulse or surges. Each of the ${app.ranger.domain.Bestiary.all.size} creatures has its own: first tracks, then the lair, then the meeting."),
        t("Пробежки — откуда угодно", "Runs from anywhere") to
            t("Health Connect, файлы GPX, TCX и FIT, архив Strava, своя запись или вручную. Старые пробежки сразу станут историей заставы. Всё остаётся на телефоне.",
                "Health Connect, GPX, TCX and FIT files, a Strava archive, recording here, or by hand. Old runs become the outpost's history at once. Everything stays on the phone."),
    )
    Column(Modifier.fillMaxSize().background(C.night)) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.05f)) {
            OutpostScene(Camp(rank = page * 2 + 1, daylight = 0f, glow = false, moon = .7, tents = page, dog = page == 2, pennants = page * 2, boots = emptyList(), forge = page == 2, fire = 1f, season = when (java.time.LocalDate.now().monthValue) { 12, 1, 2 -> 0; 3, 4, 5 -> 1; 6, 7, 8 -> 2; else -> 3 }), Modifier.fillMaxSize().statusBarsPadding())
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(pages[page].first, style = T.Display.copy(color = C.onDark))
            Text(pages[page].second, style = T.Body.copy(color = C.onDarkSoft))
            if (page == 1) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((k, st) in listOf("lynx" to 3, "stag" to 2, "toad" to 1, "dragon" to 0)) Seal(Bestiary.byKey[k]!!, st, Modifier.size(58.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                for (i in pages.indices) Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == page) C.fire else C.night2))
            }
        }
    }
        Btn(if (page < pages.size - 1) t("Дальше", "Next") else t("Разбить лагерь", "Make camp"), {
            if (page < pages.size - 1) page++ else c.write { settings.update { it.copy(onboarded = true) } }
        }, Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 18.dp), kind = Look.SEAL)
    }
}
