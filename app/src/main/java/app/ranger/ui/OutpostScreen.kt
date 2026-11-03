package app.ranger.ui

import app.ranger.ui.art.leaf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ranger.container
import app.ranger.data.Palette
import app.ranger.domain.Bestiary
import app.ranger.domain.Form
import app.ranger.domain.Moon
import app.ranger.domain.Sun
import app.ranger.domain.World
import app.ranger.ui.art.Camp
import app.ranger.ui.art.OutpostScene
import app.ranger.ui.art.Seal
import app.ranger.ui.components.Bar
import app.ranger.ui.components.Btn
import app.ranger.ui.components.Card
import app.ranger.ui.components.Look
import app.ranger.ui.components.Section
import app.ranger.ui.components.t
import app.ranger.ui.theme.C
import app.ranger.ui.theme.Sh
import app.ranger.ui.theme.T
import java.time.Instant

/** The camp itself: how the ranger stands, what's being hunted, the campaign, the boots and the band. */
@Composable
fun OutpostScreen(w: World, nav: Nav) {
    val ru = LocalRu.current
    val busy by LocalContext.current.container.busy.collectAsStateWithLifecycle()
    val camp = remember(w) { camp(w) }
    val fresh = w.met.filter { "met:${it.c.key}" !in w.s.dismissed }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).background(C.night)) {
            OutpostScene(camp, Modifier.fillMaxSize())
            // the scene's lower edge burns into the parchment like a torn page
            androidx.compose.foundation.Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(14.dp)) {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height); lineTo(0f, size.height * .55f)
                    var x = 0f; val r = kotlin.random.Random(3)
                    while (x < size.width) { x += 6 * density + r.nextFloat() * 10 * density; lineTo(x, size.height * (.25f + r.nextFloat() * .55f)) }
                    lineTo(size.width, size.height); close()
                }
                drawPath(p, C.bg)
            }
            if (busy.isNotEmpty()) Text(busy, style = T.Small.copy(color = C.onDarkSoft), modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (w.patrols.isEmpty()) Empty(nav)
            if (fresh.isNotEmpty()) News(fresh, nav)
            if (w.patrols.isNotEmpty()) FormCard(w)
            w.lastPatrol?.let { p ->
                Section(t("Последний дозор", "Last patrol"), Fmt.date(p.date, ru))
                PatrolRow(p) { nav.go(Overlay.Patrol(p.run.id)) }
            }
            Hunting(w, nav)
            CampaignCard(w, nav)
            if (w.patrols.isNotEmpty()) {
                Section(t("Снаряжение и отряд", "Kit and band"))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(Modifier.weight(1f), onClick = { nav.go(Overlay.Boots) }) {
                        Text(t("Сапоги", "Boots"), style = T.Eyebrow)
                        val sh = w.shoeStats.filter { it.shoe.retiredAt == null }.maxByOrNull { it.last ?: 0 }
                        if (sh == null) Text(t("Добавьте пару", "Add a pair"), style = T.Muted)
                        else {
                            Text(sh.shoe.name, style = T.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.km(sh.km * 1000, ru) + " / ${sh.shoe.limitKm}", style = T.Small)
                            Bar(sh.wear.toFloat(), Modifier.fillMaxWidth().padding(top = 6.dp), color = if (sh.wear > .9) C.seal else C.soft)
                        }
                    }
                    Card(Modifier.weight(1f), onClick = { nav.go(Overlay.Band) }) {
                        Text(t("Отряд", "Band"), style = T.Eyebrow)
                        val m = w.mates.firstOrNull()
                        if (m == null) Text(t("Добавьте спутников", "Add companions"), style = T.Muted)
                        else {
                            Text(w.mates.take(3).joinToString(", ") { it.c.name }, style = T.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t("${m.c.name}: ${m.role.ru.lowercase()}", "${m.c.name}: ${m.role.en.lowercase()}"), style = T.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Bard(w)
            }
        }
    }
}

fun camp(w: World): Camp {
    val now = w.now
    val local = Instant.ofEpochMilli(now).atZone(w.zone)
    val min = local.hour * 60 + local.minute
    val home = w.home
    val sun = if (home != null) Sun.times(local.toLocalDate(), home.first, home.second, w.zone) else Sun.times(local.toLocalDate(), 52.0, w.zone.rules.getOffset(local.toInstant()).totalSeconds / 240.0, w.zone)
    val daylight = if (sun == null) (if (local.monthValue in 4..9) 1f else 0f) else when {
        min < sun.rise - 40 || min > sun.set + 40 -> 0f
        min < sun.rise + 40 -> (min - (sun.rise - 40)) / 80f
        min > sun.set - 40 -> 1 - (min - (sun.set - 40)) / 80f
        else -> 1f
    }
    val glow = sun != null && (kotlin.math.abs(min - sun.rise) < 50 || kotlin.math.abs(min - sun.set) < 50)
    val fire = when (w.form) { Form.SPENT -> .35f; Form.IDLE -> .2f; Form.TIRED -> .6f; else -> 1f }
    return Camp(
        rank = w.rank, daylight = daylight, glow = glow, moon = Moon.light(now),
        tents = w.companions.count { !it.dog }, dog = w.companions.any { it.dog },
        pennants = w.patrols.count { it.run.race },
        boots = w.shoes.filter { it.retiredAt != null }.map { Palette.boots[it.color.mod(Palette.BOOTS)] },
        forge = w.patrols.any { it.run.treadmill }, fire = fire,
        season = when (local.monthValue) { 12, 1, 2 -> 0; 3, 4, 5 -> 1; 6, 7, 8 -> 2; else -> 3 },
    )
}

@Composable
private fun Empty(nav: Nav) {
    Card {
        Text(t("Застава ждёт первого дозора", "The outpost awaits its first patrol"), style = T.Name)
        Text(t("Подтяните пробежки из Health Connect, файлов GPX, TCX, FIT или архива Strava. Можно записать дозор прямо здесь или внести вручную. По старым пробежкам Пограничье сразу оживёт.",
            "Bring runs in from Health Connect, GPX, TCX or FIT files, or a Strava archive. You can also record a patrol here or add one by hand. With your old runs the Borderlands come alive at once."), style = T.Muted)
        Btn(t("Добавить дозоры", "Add patrols"), { nav.go(Overlay.Add) }, Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun News(fresh: List<app.ranger.domain.Hunt>, nav: Nav) {
    val ru = LocalRu.current
    val n = fresh.size
    Row(Modifier.fillMaxWidth().clip(Sh.Lg).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(C.night2, C.night))).clickable { nav.go(Overlay.Reveal) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box {
            fresh.take(3).forEachIndexed { i, h -> Seal(h.c, 3, Modifier.padding(start = (i * 18).dp).size(52.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(t("Вести с Рубежа", "News from the Border"), style = T.Eyebrow.copy(color = C.fire))
            Text(if (ru) "Новые встречи: $n" else "New encounters: $n", style = T.Name.copy(color = C.onDark))
        }
        Text(t("Читать", "Read"), style = T.Button.copy(color = C.fire))
    }
}

@Composable
private fun FormCard(w: World) {
    val ru = LocalRu.current
    val f = w.form
    val up = w.pulseUp
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Состояние", "Condition"), style = T.Eyebrow)
                Text(f.title.of(ru), style = T.Title.copy(color = when (f) { Form.SPENT -> C.seal; Form.TIRED -> C.gold; Form.FRESH -> C.forest; else -> C.ink }))
            }
            Stat(Fmt.weeks(w.streak, ru), t("подряд с дозором", "in a row"))
        }
        Text(f.about.of(ru), style = T.Muted)
        if (up != null) {
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().clip(Sh.Md).background(C.sealSoft).padding(12.dp)) {
                Text(if (ru) "Знахарка: пульс покоя сегодня на $up выше обычного. Тело ещё восстанавливается, так что лучше привал или тихий шаг."
                    else "The healer: your resting pulse is $up above usual today. The body is still recovering, so rest or go very easy.", style = T.Body)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat("${w.fitness.toInt()}", t("закалка", "fitness"))
            Stat("${w.fatigue.toInt()}", t("усталость", "fatigue"))
            Stat(Fmt.km(w.lastWeekKm * 1000, ru), t("за 7 дней", "last 7 days"))
        }
    }
}

@Composable
private fun Hunting(w: World, nav: Nav) {
    val ru = LocalRu.current
    val list = w.tracking
    if (list.isEmpty()) return
    Section(t("Идёт охота", "On the trail"), "${w.met.size} / ${Bestiary.all.size}")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (h in list.take(10)) {
            Column(Modifier.width(112.dp).leaf().clip(Sh.Md).clickable { nav.go(Overlay.Creature(h.c.key)) }.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Seal(h.c, h.stage, Modifier.size(56.dp))
                Text(h.c.name.of(ru), style = T.Strong.copy(fontSize = T.Small.fontSize * 1.1f), maxLines = 2, minLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(if (h.stage == 2) t("логово рядом", "lair near") else t("свежие следы", "fresh tracks"), style = T.Small)
                Text("${h.n} / ${h.c.need}", style = T.Small.copy(color = C.seal))
            }
        }
    }
}

@Composable
private fun CampaignCard(w: World, nav: Nav) {
    val ru = LocalRu.current
    val q = w.quests.lastOrNull { it.active }
    Section(t("Поход", "Campaign"))
    if (q == null) {
        Card(onClick = { nav.go(Overlay.Board) }) {
            Text(t("Доска заданий", "The board"), style = T.Name)
            Text(t("Возьмите поход на несколько недель: тракт, перевал, ночной дозор, тихий шаг…", "Take on a campaign for a few weeks: the old road, the high pass, the night watch, the quiet step…"), style = T.Muted)
        }
        return
    }
    Card(onClick = { nav.go(Overlay.Board) }) {
        Text(q.c.name.of(ru), style = T.Name)
        Text(q.c.about.of(ru), style = T.Muted)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Bar(q.progress.toFloat() / q.c.target, Modifier.weight(1f))
            Text("  ${q.progress.coerceAtMost(q.c.target)} / ${q.c.target} ${q.c.unit.of(ru)}", style = T.Small)
        }
        val left = ((q.q.endsAt - w.now) / 86_400_000L).toInt() + 1
        Text(if (ru) "Осталось ${Fmt.days(left, true)}" else "${Fmt.days(left, false)} left", style = T.Small)
    }
}

/** What the bard noticed about the music. */
@Composable
private fun Bard(w: World) {
    val ru = LocalRu.current
    val top = w.tunes.filter { kotlin.math.abs(it.faster) >= 5 }.take(3)
    if (top.isEmpty()) return
    Section(t("Бард заметил", "The bard noticed"))
    Card {
        for (tn in top) {
            val s = kotlin.math.abs(tn.faster)
            Text(if (ru) (if (tn.faster > 0) "Под ${tn.artist} вы бежите на $s с/км быстрее обычного." else "Под ${tn.artist} вы бежите на $s с/км медленнее обычного.")
                else (if (tn.faster > 0) "To ${tn.artist} you run $s s/km faster than usual." else "To ${tn.artist} you run $s s/km slower than usual."), style = T.Lore)
        }
    }
}

