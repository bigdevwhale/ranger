package app.ranger.ui.sheets

import app.ranger.ui.art.leaf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ranger.container
import app.ranger.data.Friend
import app.ranger.data.Palette
import app.ranger.data.Quest
import app.ranger.data.Shoe
import app.ranger.domain.Campaigns
import app.ranger.domain.Hunt
import app.ranger.domain.Ranks
import app.ranger.domain.Skill
import app.ranger.domain.World
import app.ranger.ui.Fmt
import app.ranger.ui.LocalRu
import app.ranger.ui.Nav
import app.ranger.ui.Overlay
import app.ranger.ui.Pips
import app.ranger.ui.art.Seal
import app.ranger.ui.components.Bar
import app.ranger.ui.components.Btn
import app.ranger.ui.components.Card
import app.ranger.ui.components.Chip
import app.ranger.ui.components.Field
import app.ranger.ui.components.Flow
import app.ranger.ui.components.Look
import app.ranger.ui.components.Section
import app.ranger.ui.components.Sheet
import app.ranger.ui.components.t
import app.ranger.ui.patrolTitle
import app.ranger.ui.theme.C
import app.ranger.ui.theme.Sh
import app.ranger.ui.theme.T

@Composable
fun CreatureSheet(w: World, h: Hunt, nav: Nav) {
    val ru = LocalRu.current
    val c = h.c
    Sheet(nav::close) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Seal(c, h.stage, Modifier.size(140.dp)) }
        Text(c.name.of(ru), style = T.Title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(when (c.tier) { 3 -> t("легенда", "legend"); 2 -> t("редкий гость", "rare visitor"); else -> t("обитатель Пограничья", "dweller of the Borderlands") }.uppercase(),
            style = T.Eyebrow.copy(color = C.seal), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (h.stage == 3) Text(c.lore.of(ru), style = T.Lore, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Card {
            Text(t("Где искать", "Where to look"), style = T.Eyebrow)
            Text(c.hint.of(ru), style = T.Body)
            if (c.need > 1) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Bar(h.n.coerceAtMost(c.need).toFloat() / c.need, Modifier.weight(1f))
                    Text("  ${h.n.coerceAtMost(c.need)} / ${c.need}", style = T.Small)
                }
                Text(when (h.stage) {
                    0 -> t("Нужно ${Fmt.patrols(c.need, true)} в таких условиях: сначала следы, потом логово, потом встреча.", "Takes ${Fmt.patrols(c.need, false)} like that: first tracks, then the lair, then the meeting.")
                    1 -> t("Свежие следы. Ещё немного — и найдётся логово.", "Fresh tracks. A little more and you'll find the lair.")
                    2 -> t("Логово рядом. Ещё один такой дозор — и встретитесь.", "The lair is near. One more patrol like that and you'll meet.")
                    else -> if (ru) "Встреч всего: ${h.n}" else "Meetings in all: ${h.n}"
                }, style = T.Small)
            } else Text(t("Хватит одного такого дозора.", "One patrol like that is enough."), style = T.Small)
        }
        h.metAt?.let { p ->
            Section(t("Первая встреча", "First meeting"))
            app.ranger.ui.PatrolRow(p) { nav.go(Overlay.Patrol(p.run.id)) }
        }
        if (h.last != null && h.last != h.metAt) {
            Section(t("Последний раз", "Last time"))
            app.ranger.ui.PatrolRow(h.last) { nav.go(Overlay.Patrol(h.last.run.id)) }
        }
    }
}

/** The new meetings since last read, one seal after another; reading marks them seen. */
@Composable
fun RevealSheet(w: World, nav: Nav) {
    val ru = LocalRu.current
    val c = LocalContext.current.container
    val fresh = remember { w.met.filter { "met:${it.c.key}" !in w.s.dismissed } }
    LaunchedEffect(Unit) { c.settings.update { s -> s.copy(dismissed = s.dismissed + fresh.map { "met:${it.c.key}" }) } }
    Sheet(nav::close) {
        Text(t("Вести с Рубежа", "News from the Border"), style = T.Title)
        Text(if (ru) "Пока вас не было, Пограничье кое-что рассказало о ваших дозорах." else "While you were away, the Borderlands told a few things about your patrols.", style = T.Muted)
        for (h in fresh) {
            Row(Modifier.fillMaxWidth().leaf().clip(Sh.Md).clickable { nav.go(Overlay.Creature(h.c.key)) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Seal(h.c, 3, Modifier.size(64.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.c.name.of(ru), style = T.Name)
                    Text(h.c.lore.of(ru), style = T.Small)
                    h.metAt?.let { Text(patrolTitle(it, ru), style = T.Small.copy(color = C.seal)) }
                }
            }
        }
    }
}

/** The board: campaigns to take on; the current one with its progress, and the ones done. */
@Composable
fun BoardSheet(w: World, nav: Nav) {
    val ru = LocalRu.current
    val c = LocalContext.current.container
    val active = w.quests.lastOrNull { it.active }
    Sheet(nav::close) {
        Text(t("Доска заданий", "The board"), style = T.Title)
        if (active != null) Card {
            Text(t("Сейчас в походе", "Under way"), style = T.Eyebrow)
            Text(active.c.name.of(ru), style = T.Name)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Bar(active.progress.toFloat() / active.c.target, Modifier.weight(1f))
                Text("  ${active.progress.coerceAtMost(active.c.target)} / ${active.c.target}", style = T.Small)
            }
            Btn(t("Сойти с пути", "Give it up"), { c.write { dao.update(active.q.copy(abandonedAt = System.currentTimeMillis())) } }, Modifier.padding(top = 8.dp), kind = Look.GHOST, small = true)
        } else Text(t("Выберите поход. Он засчитывает дозоры с сегодняшнего дня.", "Choose a campaign. It counts patrols from today."), style = T.Muted)
        val done = w.quests.filter { it.doneAt != null }.groupingBy { it.c.key }.eachCount()
        for (cp in Campaigns.all) Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cp.name.of(ru), style = T.Name)
                    Text(cp.about.of(ru), style = T.Muted)
                    Text((if (ru) "${Fmt.days(cp.days, true)}" else Fmt.days(cp.days, false)) + (done[cp.key]?.let { if (ru) " · пройден ×$it" else " · done ×$it" } ?: ""), style = T.Small)
                }
            }
            if (active == null) Btn(t("Выступить", "Set out"), {
                val now = System.currentTimeMillis()
                val end = java.time.LocalDate.now().plusDays(cp.days.toLong() - 1).atTime(23, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                c.write { dao.insert(Quest(key = cp.key, startedAt = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli().coerceAtMost(now), endsAt = end)) }
                nav.close()
            }, Modifier.padding(top = 8.dp), small = true)
        }
    }
}

/** Running shoes: their kilometres, and hanging them on the wall when they're done. */
@Composable
fun BootsSheet(w: World, nav: Nav) {
    val ru = LocalRu.current
    val c = LocalContext.current.container
    var name by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0) }
    Sheet(nav::close) {
        Text(t("Сапоги", "Boots"), style = T.Title)
        Text(t("Пара служит обычно 600–800 км. Когда она отслужит, её вешают на частокол заставы.", "A pair usually lasts 600–800 km. When it's done, it's hung on the outpost's wall."), style = T.Muted)
        for (st in w.shoeStats.sortedBy { it.shoe.retiredAt != null }) Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Color(Palette.boots[st.shoe.color.mod(Palette.BOOTS)])))
                Column(Modifier.weight(1f)) {
                    Text(st.shoe.name, style = T.Strong)
                    Text("${Fmt.km(st.km * 1000, ru)} · ${Fmt.patrols(st.runs, ru)}" + (st.shoe.retiredAt?.let { if (ru) " · на стене с ${Fmt.date(it, true)}" else " · on the wall since ${Fmt.date(it, false)}" } ?: ""), style = T.Small)
                }
                if (st.shoe.retiredAt == null) Chip(if (w.s.defaultShoe == st.shoe.id) t("основные", "main") else t("сделать основными", "make main"), w.s.defaultShoe == st.shoe.id,
                    { c.write { settings.update { s -> s.copy(defaultShoe = if (s.defaultShoe == st.shoe.id) null else st.shoe.id) } } })
            }
            if (st.shoe.retiredAt == null) {
                Bar(st.wear.toFloat(), Modifier.fillMaxWidth().padding(top = 8.dp), color = if (st.wear > .9) C.seal else C.soft)
                Btn(t("Повесить на частокол", "Hang on the wall"), { c.write { dao.update(st.shoe.copy(retiredAt = System.currentTimeMillis())); if (settings.now().defaultShoe == st.shoe.id) settings.update { it.copy(defaultShoe = null) } } },
                    Modifier.padding(top = 8.dp), kind = Look.GHOST, small = true)
            }
        }
        Section(t("Новая пара", "A new pair"))
        Field(name, { name = it }, t("Как называются", "What they're called"))
        Field(base, { base = it }, t("Уже набегано, км (если не новые)", "Kilometres they already have"), number = true)
        Flow { Palette.boots.forEachIndexed { i, col -> Box(Modifier.size(30.dp).clip(CircleShape).background(Color(col)).border(if (color == i) 3.dp else 0.dp, C.ink, CircleShape).clickable { color = i }) } }
        Btn(t("Поставить на полку", "Put on the shelf"), {
            val n = name.trim(); if (n.isEmpty()) return@Btn
            c.write {
                val id = dao.insert(Shoe(name = n, color = color, baseKm = base.replace(',', '.').toDoubleOrNull() ?: 0.0))
                if (settings.now().defaultShoe == null) settings.update { it.copy(defaultShoe = id) }
            }
            name = ""; base = ""
        }, Modifier.fillMaxWidth())
    }
}

/** The band: everyone who runs along, and the roles their patrols gave them. */
@Composable
fun BandSheet(w: World, nav: Nav) {
    val ru = LocalRu.current
    val c = LocalContext.current.container
    var name by remember { mutableStateOf("") }
    var dog by remember { mutableStateOf(false) }
    Sheet(nav::close) {
        Text(t("Отряд", "The band"), style = T.Title)
        Text(t("Роль каждому даёт то, как вы бегаете вместе: с кем-то быстро, с кем-то долго, с кем-то тихо.", "Each gets a role from how you run together: fast with one, long with another, quietly with a third."), style = T.Muted)
        for (m in w.mates) Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((if (m.c.dog) "🐾 " else "") + m.c.name, style = T.Strong)
                    Text(m.role.of(ru) + " · " + Fmt.patrols(m.runs, ru) + " · " + Fmt.km(m.km * 1000, ru), style = T.Small)
                }
                var sure by remember { mutableStateOf(false) }
                Chip(if (sure) t("точно?", "sure?") else t("убрать", "remove"), sure, { if (!sure) sure = true else c.write { dao.unlinkCompanion(m.c.id); dao.delete(m.c) } }, color = C.seal)
            }
        }
        Section(t("Новый спутник", "A new companion"))
        Field(name, { name = it }, t("Имя", "Name"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(t("человек", "person"), !dog, { dog = false }); Chip(t("собака", "dog"), dog, { dog = true })
        }
        Btn(t("Принять в отряд", "Welcome to the band"), {
            val n = name.trim(); if (n.isEmpty()) return@Btn
            c.write { dao.insert(Friend(name = n, dog = dog)) }; name = ""
        }, Modifier.fillMaxWidth())
    }
}

/** Rank and abilities, with what each is measured by. */
@Composable
fun SkillsSheet(w: World, nav: Nav) {
    val ru = LocalRu.current
    Sheet(nav::close) {
        Text(Ranks.all[w.rank].name.of(ru), style = T.Title)
        val next = Ranks.all.getOrNull(w.rank + 1)
        Text(if (next == null) t("Выше званий нет.", "There's no higher rank.") else if (ru) "До звания «${next.name.ru}»: ${Fmt.int(next.at - w.xp, true)} доблести." else "To ${next.name.en}: ${Fmt.int(next.at - w.xp, false)} valour.", style = T.Muted)
        Text(t("Доблесть — это тренировочная нагрузка дозора (TRIMP): время, умноженное на то, насколько высоко был пульс. Без пульса она считается по темпу и набору высоты. Так час лёгкого бега и полчаса тяжёлого стоят примерно одинаково.",
            "Valour is a patrol's training load (TRIMP): time weighted by how high the pulse was. Without a pulse it's worked out from pace and climb. An easy hour and a hard half-hour count about the same."), style = T.Small)
        Section(t("Умения", "Abilities"))
        for (sk in Skill.entries) {
            val lv = w.skills[sk] ?: 0; val v = w.skillValues[sk] ?: 0
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sk.title.of(ru), style = T.Strong, modifier = Modifier.weight(1f))
                    Pips(lv)
                }
                val shown = if (sk == Skill.SPEED) (if (v > 0) Fmt.pace(v.toDouble(), ru, false) else "—") else Fmt.int(v, ru)
                val nextStep = sk.steps.getOrNull(lv)?.let { s -> if (sk == Skill.SPEED) Fmt.pace(s.toDouble(), ru, false) else Fmt.int(s, ru) }
                Text("${sk.about.of(ru)} $shown ${sk.unit.of(ru)}" + (nextStep?.let { if (ru) ", следующий уровень: $it" else ", next level: $it" } ?: ""), style = T.Small)
            }
        }
    }
}
