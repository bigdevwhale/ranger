package app.ranger.ui

import android.content.Intent
import android.provider.Settings as Sys
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.ranger.BuildConfig
import app.ranger.container
import app.ranger.data.Geo
import app.ranger.data.Hidden
import app.ranger.domain.World
import app.ranger.ui.components.Btn
import app.ranger.ui.components.Card
import app.ranger.ui.components.Chip
import app.ranger.ui.components.Field
import app.ranger.ui.components.Flow
import app.ranger.ui.components.Look
import app.ranger.ui.components.Page
import app.ranger.ui.components.Section
import app.ranger.ui.components.t
import app.ranger.ui.sheets.syncHealth
import app.ranger.ui.theme.C
import app.ranger.ui.theme.T
import app.ranger.work.Ears
import kotlinx.coroutines.launch

/** Places where many runs start: candidates for hiding. */
private fun starts(w: World): List<Triple<Double, Double, Int>> {
    val out = ArrayList<Triple<Double, Double, Int>>()
    for (p in w.patrols) {
        val la = p.run.lat ?: continue; val lo = p.run.lon ?: continue
        val i = out.indexOfFirst { Geo.dist(it.first, it.second, la, lo) < 250 }
        if (i < 0) out += Triple(la, lo, 1) else out[i] = out[i].copy(third = out[i].third + 1)
    }
    return out.filter { it.third >= 3 }.sortedByDescending { it.third }.take(4)
}

@Composable
fun SettingsPage(w: World, nav: Nav) {
    val ru = LocalRu.current
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    Page(t("Настройки", "Settings"), nav::close) {
        Section(t("Пульс", "Heart rate"))
        Card {
            var mx by remember { mutableStateOf(if (w.s.maxHr > 0) "${w.s.maxHr}" else "") }
            var rs by remember { mutableStateOf(if (w.s.restHr > 0) "${w.s.restHr}" else "") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(t("Максимальный", "Maximum"), style = T.Small)
                    Field(mx, { mx = it; c.write { settings.update { s -> s.copy(maxHr = it.toIntOrNull()?.takeIf { v -> v in 120..230 } ?: 0) } } }, "${w.maxHr}", number = true)
                }
                Column(Modifier.weight(1f)) {
                    Text(t("Покоя", "Resting"), style = T.Small)
                    Field(rs, { rs = it; c.write { settings.update { s -> s.copy(restHr = it.toIntOrNull()?.takeIf { v -> v in 30..110 } ?: 0) } } }, "${w.restHr}", number = true)
                }
            }
            Text(t("Пустое поле — значит, приложение само берёт максимум из ваших пробежек, а пульс покоя из Health Connect. Зоны: ${w.zoneEdges.joinToString(" · ")}.",
                "Leave empty and the app takes the maximum from your runs and the resting pulse from Health Connect. Zones: ${w.zoneEdges.joinToString(" · ")}."), style = T.Small)
        }

        Section(t("Скрытые места", "Hidden places"))
        Card {
            Text(t("Вокруг дома и работы треки обрезаются: на карте и в картинках там пусто. Ниже места, откуда вы часто стартуете.", "Tracks are cut around home and work: blank on the map and in pictures. Below are places you often start from."), style = T.Muted)
            for (h in w.s.hidden) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (ru) "Скрыто: круг ${h.radiusM} м" else "Hidden: ${h.radiusM} m circle", style = T.Body, modifier = Modifier.weight(1f))
                Chip("300", h.radiusM == 300, { c.write { settings.update { s -> s.copy(hidden = s.hidden.map { if (it == h) it.copy(radiusM = 300) else it }) } } })
                Chip("600", h.radiusM == 600, { c.write { settings.update { s -> s.copy(hidden = s.hidden.map { if (it == h) it.copy(radiusM = 600) else it }) } } })
                Chip(t("открыть", "show"), false, { c.write { settings.update { s -> s.copy(hidden = s.hidden - h) } } }, Modifier.padding(start = 4.dp))
            }
            for ((la, lo, n) in starts(w).filter { s -> w.s.hidden.none { Geo.dist(it.lat, it.lon, s.first, s.second) < it.radiusM } }) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (ru) "Отсюда ${Fmt.patrols(n, true)}" else "${Fmt.patrols(n, false)} from here", style = T.Body, modifier = Modifier.weight(1f))
                Btn(t("Скрыть", "Hide"), { c.write { settings.update { s -> s.copy(hidden = s.hidden + Hidden(la, lo, 300)) } } }, small = true, kind = Look.SOFT)
            }
        }

        Section(t("Что играло", "What was playing"))
        Card {
            val allowed = remember { Ears.allowed(ctx) }
            Text(t("Приложение может запоминать исполнителя и название трека, который играет в плеере, чтобы бард вписал музыку в дозор. Сами уведомления не читаются и не хранятся.",
                "The app can note the artist and title the player is on, so the bard can write the music into the patrol. Notifications themselves are never read or kept."), style = T.Muted)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(t("запоминать", "remember"), w.s.music, { c.write { settings.update { it.copy(music = true) } }; if (!allowed) ctx.startActivity(Intent(Sys.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
                Chip(t("нет", "no"), !w.s.music, { c.write { settings.update { it.copy(music = false) } } })
            }
            if (w.s.music && !allowed) Btn(t("Дать доступ к уведомлениям", "Allow notification access"), { ctx.startActivity(Intent(Sys.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, Modifier.padding(top = 8.dp), small = true)
        }

        Section("Health Connect")
        Card {
            Text(if (w.s.healthConnect) (if (ru) "Подключено. Последний раз: ${Fmt.date(w.s.hcSince, true)}, ${Fmt.time(w.s.hcSince)}." else "Connected. Last read: ${Fmt.date(w.s.hcSince, false)}, ${Fmt.time(w.s.hcSince)}.")
                else t("Не подключено. Подключить можно через «+» на заставе.", "Not connected. Connect it with “+” at the outpost."), style = T.Muted)
            var status by remember { mutableStateOf("") }
            if (w.s.healthConnect) Btn(t("Подтянуть сейчас", "Fetch now"), { scope.launch { status = t2(ru, "Читаем…", "Reading…"); status = syncHealth(ctx, ru) } }, Modifier.padding(top = 8.dp), small = true)
            if (status.isNotEmpty()) Text(status, style = T.Small.copy(color = C.forest))
        }

        Section(t("Язык", "Language"))
        Flow {
            val cur = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            Chip(t("как в телефоне", "as the phone"), cur.isEmpty(), { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) })
            Chip("Русский", cur.startsWith("ru"), { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru")) })
            Chip("English", cur.startsWith("en"), { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) })
        }

        Section(t("Сообщество", "Community"))
        Card {
            fun open(u: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            for ((label, url) in listOf(
                t("Чат StillCraft в Telegram", "StillCraft chat on Telegram") to "https://t.me/+Q4gFEDk8O842OGVi",
                t("Сообщить об ошибке", "Report a bug") to "https://t.me/ArcaneWhisperBot?start=bug_ranger",
                t("Предложить идею", "Suggest an idea") to "https://t.me/ArcaneWhisperBot?start=wish_ranger",
                t("Следить за обновлениями", "Follow updates") to "https://t.me/ArcaneWhisperBot?start=f_ranger",
                t("Исходный код", "Source code") to "https://github.com/bigdevwhale/ranger",
            )) Text(label, style = T.Body.copy(color = C.river), modifier = Modifier.fillMaxWidth().clickable { open(url) }.padding(vertical = 8.dp))
        }

        Section(t("Как это устроено", "How it works"))
        Card {
            Text(t("Каждая пробежка — это дозор. По времени и месту приложение узнаёт погоду (Open-Meteo), солнце и луну, по карте OpenStreetMap — воду, парки и леса вдоль пути, по пульсу — зоны и нагрузку. Из этого складываются встречи с существами, звания, умения и походы. Всё хранится только на телефоне; в сеть уходят лишь запросы карты и погоды по координатам.",
                "Each run is a patrol. From its time and place the app learns the weather (Open-Meteo), the sun and moon; from OpenStreetMap, the water, parks and woods along the way; from the pulse, zones and load. That makes the meetings, ranks, abilities and campaigns. Everything stays on the phone; only map and weather requests for coordinates go out."), style = T.Muted)
        }
        Text("${t("Следопыт", "Ranger")} ${BuildConfig.VERSION_NAME}", style = T.Small, modifier = Modifier.padding(bottom = 20.dp))
    }
}

private fun t2(ru: Boolean, a: String, b: String) = if (ru) a else b
