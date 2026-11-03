package app.ranger.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ranger.container
import app.ranger.data.Day
import app.ranger.domain.World
import app.ranger.ui.Bars
import app.ranger.ui.Fmt
import app.ranger.ui.LocalRu
import app.ranger.ui.Nav
import app.ranger.ui.Stat
import app.ranger.ui.components.Btn
import app.ranger.ui.components.Card
import app.ranger.ui.components.Chip
import app.ranger.ui.components.Field
import app.ranger.ui.components.Look
import app.ranger.ui.components.Section
import app.ranger.ui.components.Sheet
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.T

/** Steps: what Health Connect counted, a number typed in by hand for any of the last days, and the daily goal. */
@Composable
fun StepsSheet(w: World, nav: Nav) {
    val ru = LocalRu.current
    val c = LocalContext.current.container
    var back by remember { mutableIntStateOf(0) }
    val date = w.today.minusDays(back.toLong())
    val day = w.days.firstOrNull { it.date == date.toString() }
    var typed by remember(back, day?.steps) { mutableStateOf(day?.steps?.toString().orEmpty()) }
    var goal by remember { mutableStateOf(w.s.stepGoal.toString()) }
    Sheet(nav::close) {
        Text(t("Шаги", "Steps"), style = T.Title)
        Text(t("Ходьба тоже служба: каждая тысяча шагов вне пробежек даёт три доблести, а дни с нормой растят умение «Ходок».",
            "Walking is service too: every thousand steps outside runs gives three valour, and days at the goal grow the Wayfarer ability."), style = T.Muted)
        val week = w.steps(14)
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Stat(w.todaySteps?.let { Fmt.int(it, ru) } ?: "—", t("сегодня", "today"))
                Stat(Fmt.int(week.takeLast(7).map { it.second }.average().toInt(), ru), t("в среднем за неделю", "a day this week"))
                Stat("${w.goalDays}", t("дней с нормой", "days at goal"))
            }
            Bars(week.map { it.second.toDouble() }, Modifier.fillMaxWidth().height(70.dp).padding(top = 10.dp), color = C.forest, last = C.seal)
            Text(t("Две недели, сегодня справа.", "Two weeks, today on the right."), style = T.Small)
        }

        Section(t("Вписать вручную", "Write in by hand"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((d, l) in listOf(0 to t("сегодня", "today"), 1 to t("вчера", "yesterday"), 2 to t("позавчера", "day before"))) Chip(l, back == d, { back = d })
        }
        Text(Fmt.weekday(date, ru) + ", " + Fmt.date(date, ru) + when {
            day?.stepsByHand == true -> t(" · вписано вручную", " · typed in")
            day?.steps != null -> t(" · из Health Connect", " · from Health Connect")
            else -> ""
        }, style = T.Small)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(typed, { v -> typed = v.filter { it.isDigit() }.take(6) }, t("Сколько шагов", "How many steps"), Modifier.weight(1f), number = true)
            Btn(t("Записать", "Save"), {
                val n = typed.toIntOrNull() ?: return@Btn
                c.write { upsertSteps(date.toString(), n, true) }
                nav.toast(if (ru) "${Fmt.int(n, true)} шагов записано" else "${Fmt.int(n, false)} steps saved")
            }, small = true, enabled = typed.toIntOrNull() != null)
        }
        if (day?.stepsByHand == true) Btn(t("Снова брать из Health Connect", "Take it from Health Connect again"), {
            c.write { dao.upsert(day.copy(stepsByHand = false)) }
        }, kind = Look.GHOST, small = true)
        Text(t("Вписанное вручную Health Connect уже не перезапишет.", "What you type in, Health Connect won't overwrite."), style = T.Small)

        Section(t("Норма на день", "Daily goal"))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (g in listOf(6000, 8000, 10000, 12000)) Chip(Fmt.int(g, ru), w.s.stepGoal == g, { goal = g.toString(); c.write { settings.update { it.copy(stepGoal = g) } } })
        }
        Field(goal, { v -> goal = v.filter { it.isDigit() }.take(5); goal.toIntOrNull()?.takeIf { it in 1000..50000 }?.let { g -> c.write { settings.update { it.copy(stepGoal = g) } } } },
            t("Своя норма", "Your own goal"), number = true)
        Column {}
    }
}

/** Writes a day's steps, keeping its pulse numbers. */
suspend fun app.ranger.AppContainer.upsertSteps(date: String, steps: Int, byHand: Boolean) {
    val old = dao.day(date)
    dao.upsert((old ?: Day(date)).copy(steps = steps, stepsByHand = byHand))
}
