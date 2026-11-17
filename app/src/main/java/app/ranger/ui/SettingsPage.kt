package app.ranger.ui

import android.content.Intent
import android.provider.Settings as Sys
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.health.connect.client.PermissionController
import app.ranger.BuildConfig
import app.ranger.container
import app.ranger.data.Geo
import app.ranger.data.Health
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
import app.ranger.work.HealthSync
import kotlinx.coroutines.launch

/** Where "Support the author" leads; the button stays hidden while this is empty. */
const val DONATE_URL = "https://bigdevwhale.github.io/stillcraft/support/"

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
        app.ranger.ui.art.SheetBanner("settings")
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
            if (w.s.healthConnect) {
                Text(t("Новое подтягивается само, когда открываете приложение (не чаще раза в 15 минут).",
                    "New data comes in by itself when you open the app (at most every 15 minutes)."), style = T.Small, modifier = Modifier.padding(top = 10.dp))
                val canBg by produceState<Boolean?>(null) { value = Health.backgroundSupported(ctx) }
                fun setBg(on: Boolean) = scope.launch { c.settings.update { it.copy(hcBackground = on) }; HealthSync.plan(ctx) }
                val bgAsk = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
                    if (Health.BACKGROUND in got) setBg(true) else status = t2(ru, "Доступ к чтению в фоне не дан.", "Background access wasn't given.")
                }
                when (canBg) {
                    null -> {}
                    false -> Text(t("Чтение в фоне этот Health Connect не поддерживает: обновите его или Android.",
                        "This Health Connect can't read in the background: update it or Android."), style = T.Small)
                    true -> {
                        Text(t("Читать и в фоне, примерно раз в час, пока приложение закрыто", "Also read in the background, about hourly, while the app is closed"),
                            style = T.Small, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(t("да", "yes"), w.s.hcBackground, {
                                scope.launch { if (Health.BACKGROUND in Health.granted(ctx)) setBg(true) else bgAsk.launch(setOf(Health.BACKGROUND)) }
                            })
                            Chip(t("нет", "no"), !w.s.hcBackground, { setBg(false) })
                        }
                    }
                }
            }
        }

        Section(t("Язык", "Language"))
        Flow {
            val cur = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            Chip(t("как в телефоне", "as the phone"), cur.isEmpty(), { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) })
            Chip("Русский", cur.startsWith("ru"), { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru")) })
            Chip("English", cur.startsWith("en"), { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) })
        }

        if (DONATE_URL.isNotBlank()) {
            Section(t("Поддержать", "Support"))
            Card(fill = C.goldSoft) {
                Text(t("Поддержать автора", "Support the author"), style = T.Name)
                Text(t("«Следопыт» бесплатный, без рекламы и аккаунтов. Если он вам по душе, можно угостить автора чашкой чая на привале.",
                    "Ranger is free, with no ads and no accounts. If you like it, you can buy the author a cup of tea at the next halt."), style = T.Muted)
                Btn(t("Поддержать", "Support"), {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(DONATE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, Modifier.fillMaxWidth().padding(top = 10.dp))
            }
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
