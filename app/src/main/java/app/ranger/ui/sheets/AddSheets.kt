package app.ranger.ui.sheets

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ranger.container
import app.ranger.data.Health
import app.ranger.data.Importer
import app.ranger.data.Parsed
import app.ranger.domain.World
import app.ranger.ui.Fmt
import app.ranger.ui.LocalRu
import app.ranger.ui.Nav
import app.ranger.ui.Overlay
import app.ranger.ui.Stat
import app.ranger.ui.components.Btn
import app.ranger.ui.components.Card
import app.ranger.ui.components.Chip
import app.ranger.ui.components.Field
import app.ranger.ui.components.Flow
import app.ranger.ui.components.Look
import app.ranger.ui.components.Sheet
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.T
import app.ranger.work.PatrolService
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

fun Importer.Result.say(ru: Boolean): String = if (ru) "Новых дозоров: $added" + (if (merged > 0) ", дополнено $merged" else "") + (if (skipped > 0) ", уже были $skipped" else "") + (if (failed > 0) ", не прочитано $failed" else "") + (if (walks > 0) ", прогулок пропущено $walks" else "")
    else "New patrols: $added" + (if (merged > 0) ", completed $merged" else "") + (if (skipped > 0) ", already here $skipped" else "") + (if (failed > 0) ", unreadable $failed" else "") + (if (walks > 0) ", walks left out $walks" else "")

/**
 * Health Connect: read [what] since the last read, a little overlapping in case a watch synced late.
 * [kinds]: what to read, the saved choice when null; [fromMs] reads again from that moment (say, to fetch routes allowed later). The very first read starts at today's midnight: history isn't pulled, the journal begins now.
 */
suspend fun syncHealth(ctx: android.content.Context, ru: Boolean, kinds: Set<Health.Kind>? = null, fromMs: Long? = null): String {
    val c = ctx.container
    val s = c.settings.now()
    val what = kinds ?: Health.Kind.entries.filter { it.name in s.hcKinds }.toSet()
    if (what.isEmpty()) return if (ru) "Ничего не выбрано." else "Nothing chosen."
    val from = if (fromMs != null) fromMs else if (s.hcSince > 0) s.hcSince - 2 * 86_400_000L
        else LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val got = runCatching { Health.read(ctx, from, what) }.getOrElse { return (if (ru) "Health Connect не ответил: " else "Health Connect didn't answer: ") + it.message }
    var res = Importer.Result(0, 0, 0, 0)
    for (p in got.runs) res = with(c.importer) { res.add(put(p).first) }
    for (d in got.days) {
        val old = c.dao.day(d.date)
        // a day typed in by hand keeps its steps
        c.dao.upsert(d.copy(restHr = d.restHr ?: old?.restHr, hrv = d.hrv ?: old?.hrv,
            steps = if (old?.stepsByHand == true) old.steps else d.steps ?: old?.steps, stepsByHand = old?.stepsByHand == true))
    }
    c.settings.update { it.copy(healthConnect = true, hcSince = got.until) }
    c.tend()
    val body = got.days.count { it.restHr != null || it.hrv != null }
    val steps = got.days.count { it.steps != null }
    return buildList {
        if (Health.Kind.RUNS in what) add(res.say(ru))
        if (Health.Kind.BODY in what) add(if (ru) "дней с пульсом покоя и HRV: $body" else "days with resting pulse and HRV: $body")
        if (Health.Kind.STEPS in what) add(if (ru) "дней с шагами: $steps" else "days with steps: $steps")
    }.joinToString("; ").replaceFirstChar { it.uppercase() }
}

@Composable
fun AddSheet(w: World, nav: Nav) {
    val ru = LocalRu.current
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        working = true
        c.write {
            val r = importer.files(uris) { status = it }
            working = false; status = r.say(ru)
            tend()
            nav.toast(status)
        }
    }
    val locAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) { PatrolService.start(ctx); nav.go(Overlay.Record) }
        else status = t2(ru, "Без доступа к GPS записать дозор не получится.", "Recording needs GPS access.")
    }
    Sheet(nav::close) {
        Text(t("Новые дозоры", "New patrols"), style = T.Title)
        Text(t("Пробежки можно принести откуда угодно. Если одна и та же придёт из двух мест, останется одна копия, с треком и пульсом, если они есть хоть в одной.",
            "Bring runs from anywhere. If the same run arrives twice, one copy stays, with the track and pulse if either had them."), style = T.Muted)

        var kinds by remember { mutableStateOf(Health.Kind.entries.filter { it.name in w.s.hcKinds }.toSet()) }
        fun fetch() {
            working = true; status = t2(ru, "Читаем Health Connect…", "Reading Health Connect…")
            scope.launch { status = syncHealth(ctx, ru, kinds); working = false; nav.toast(status) }
        }
        val hcAsk = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.isEmpty()) { status = t2(ru, "Доступ не дан.", "No access given."); return@rememberLauncherForActivityResult }
            fetch()
        }
        Card {
            Text("Health Connect", style = T.Name)
            Text(t("Сюда пишут Strava, Garmin, Samsung Health, Mi Fitness, Zepp, Polar и Coros.",
                "Strava, Garmin, Samsung Health, Mi Fitness, Zepp, Polar and Coros write here."), style = T.Muted)
            val ok = remember { Health.available(ctx) }
            if (!ok) Text(t("На этом телефоне Health Connect не установлен.", "Health Connect isn't installed on this phone."), style = T.Small.copy(color = C.seal))
            else {
                Text(t("Что читать", "What to read"), style = T.Small, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                Flow {
                    for ((k, l) in listOf(
                        Health.Kind.RUNS to t("пробежки", "runs"),
                        Health.Kind.BODY to t("пульс покоя и HRV", "resting pulse and HRV"),
                        Health.Kind.STEPS to t("шаги", "steps"),
                    )) Chip(l, k in kinds, {
                        kinds = if (k in kinds) kinds - k else kinds + k
                        val names = kinds.map { it.name }.toSet()
                        c.write { settings.update { it.copy(hcKinds = names) } }
                    })
                }
                Text(if (w.s.hcSince > 0) (if (ru) "Читается то, что появилось с ${Fmt.date(w.s.hcSince, true)}, ${Fmt.time(w.s.hcSince)}." else "Reads what appeared since ${Fmt.date(w.s.hcSince, false)}, ${Fmt.time(w.s.hcSince)}.")
                    else t("Старая история не тянется: летопись начнётся с сегодняшнего дня. Старые пробежки можно принести файлами ниже.",
                        "Old history isn't pulled: the journal starts today. Older runs can come in as files below."),
                    style = T.Small, modifier = Modifier.padding(top = 10.dp))
            }
            Btn(if (w.s.healthConnect) t("Подтянуть", "Fetch") else t("Подключить и подтянуть", "Connect and fetch"), {
                scope.launch {
                    val have = Health.granted(ctx)
                    if (have.containsAll(Health.permissions - Health.ROUTES - androidx.health.connect.client.permission.HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY) && have.isNotEmpty()) fetch()
                    else hcAsk.launch(Health.permissions)
                }
            }, Modifier.padding(top = 8.dp), enabled = ok && !working && kinds.isNotEmpty())
        }
        Card {
            Text(t("Файлы и архивы", "Files and archives"), style = T.Name)
            Text(t("GPX, TCX и FIT с часов (Garmin, Coros, Suunto, Polar), можно сжатые и в zip. Архив Strava тоже подойдёт: «Настройки → Мой аккаунт → Скачать или удалить аккаунт», дальше zip целиком.",
                "GPX, TCX and FIT from watches (Garmin, Coros, Suunto, Polar), gzipped or zipped too. A Strava archive works as well: Settings → My account → Download or delete your account, then the whole zip."), style = T.Muted)
            Btn(t("Выбрать файлы", "Choose files"), { files.launch(arrayOf("*/*")) }, Modifier.padding(top = 8.dp), enabled = !working)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn(t("Записать дозор", "Record a patrol"), {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { PatrolService.start(ctx); nav.go(Overlay.Record) }
                else locAsk.launch(buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }.toTypedArray())
            }, Modifier.weight(1f), kind = Look.INK, enabled = !working)
            Btn(t("Вручную", "By hand"), { nav.go(Overlay.Manual) }, Modifier.weight(1f), kind = Look.SOFT)
        }
        if (status.isNotEmpty()) Text(status, style = T.Body.copy(color = if (working) C.soft else C.forest))
    }
}

private fun t2(ru: Boolean, a: String, b: String) = if (ru) a else b

/** A run typed in: date, start, distance, time, and optionally climb, pulse and treadmill. */
@Composable
fun ManualSheet(w: World, nav: Nav) {
    val ru = LocalRu.current
    val c = LocalContext.current.container
    var day by remember { mutableStateOf(0) }
    var at by remember { mutableStateOf("${LocalTime.now().hour}:00") }
    var km by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var up by remember { mutableStateOf("") }
    var hr by remember { mutableStateOf("") }
    var mill by remember { mutableStateOf(false) }
    Sheet(nav::close) {
        Text(t("Дозор вручную", "A patrol by hand"), style = T.Title)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((d, l) in listOf(0 to t("сегодня", "today"), 1 to t("вчера", "yesterday"), 2 to t("позавчера", "day before"))) Chip(l, day == d, { day = d })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { Text(t("Начало", "Start"), style = T.Small); Field(at, { at = it }, "7:30", number = false) }
            Column(Modifier.weight(1f)) { Text(t("Километры", "Kilometres"), style = T.Small); Field(km, { km = it }, "8,5", number = true) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { Text(t("Время (ч:мм:сс или мин)", "Time (h:mm:ss or min)"), style = T.Small); Field(time, { time = it }, "48:30") }
            Column(Modifier.weight(1f)) { Text(t("Набор, м", "Climb, m"), style = T.Small); Field(up, { up = it }, "0", number = true) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { Text(t("Средний пульс", "Average pulse"), style = T.Small); Field(hr, { hr = it }, "—", number = true) }
            Chip(t("на дорожке", "treadmill"), mill, { mill = !mill }, Modifier.weight(1f).padding(bottom = 6.dp))
        }
        val dist = km.replace(',', '.').toDoubleOrNull()
        val secs = time.split(':').mapNotNull { it.trim().toIntOrNull() }.let { p -> when (p.size) { 1 -> p[0] * 60; 2 -> p[0] * 60 + p[1]; 3 -> p[0] * 3600 + p[1] * 60 + p[2]; else -> null } }
        val start = runCatching {
            val (h, m) = at.split(':', '.').map { it.trim().toInt() }.let { it[0] to it.getOrElse(1) { 0 } }
            LocalDate.now().minusDays(day.toLong()).atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
        if (dist != null && secs != null && secs > 0) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat(Fmt.pace(secs / dist, ru), t("темп", "pace"))
        }
        Btn(t("Записать в летопись", "Write it down"), {
            if (dist == null || secs == null || start == null) return@Btn
            val p = Parsed(start = start, points = emptyList(), source = "manual", sourceId = "manual:${start / 60000}", treadmill = mill,
                durationS = secs, movingS = secs, distanceM = dist * 1000, ascentM = up.toDoubleOrNull(), avgHr = hr.toIntOrNull())
            c.write {
                val (_, id) = importer.put(p)
                // a hand-written run with an average pulse: a histogram of one bar, so zones and load still work
                hr.toIntOrNull()?.let { bpm -> dao.run(id)?.let { dao.update(it.copy(hrHist = "$bpm:$secs")) } }
                if (id > 0) nav.go(Overlay.Patrol(id)) else nav.close()
            }
        }, Modifier.fillMaxWidth(), enabled = dist != null && secs != null && start != null)
    }
}

/** The patrol being recorded: time, distance, pace and a big button to finish. */
@Composable
fun RecordSheet(nav: Nav) {
    val ru = LocalRu.current
    val ctx = LocalContext.current
    val live by PatrolService.live.collectAsStateWithLifecycle()
    val saved by PatrolService.lastSaved.collectAsStateWithLifecycle()
    var stopping by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(saved) { if (stopping && saved > 0) nav.go(Overlay.Patrol(saved)) }
    Sheet({ nav.close() }) {
        Text(t("Дозор идёт", "On patrol"), style = T.Title)
        Text(if (live.fix) t("GPS держит след.", "GPS has a fix.") else t("Ищем спутники… Выйдите на открытое место.", "Looking for satellites… Step into the open."), style = T.Muted.copy(color = if (live.fix) C.forest else C.seal))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(Fmt.clock(live.elapsedS), t("время", "time"))
            Stat(Fmt.km(live.meters, ru), t("путь", "distance"))
            Stat(Fmt.pace(live.pace, ru, false), t("мин/км", "min/km"))
        }
        Text(t("Экран можно выключить: запись идёт в фоне. На телефонах Xiaomi разрешите приложению автозапуск и работу без ограничений батареи, иначе система может остановить запись.",
            "You can turn the screen off: recording runs in the background. On Xiaomi phones allow autostart and no battery restrictions, or the system may stop it."), style = T.Small)
        Btn(t("Вернуться на заставу", "Back to the outpost"), { stopping = true; PatrolService.stop(ctx, save = true) }, Modifier.fillMaxWidth(), enabled = live.running)
        Btn(t("Отменить без записи", "Cancel, don't save"), { PatrolService.stop(ctx, save = false); nav.close() }, Modifier.fillMaxWidth(), kind = Look.GHOST)
    }
}
