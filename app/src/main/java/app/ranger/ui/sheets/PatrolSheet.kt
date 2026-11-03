package app.ranger.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ranger.container
import app.ranger.data.Friend
import app.ranger.data.RunCompanion
import app.ranger.data.TrackFile
import app.ranger.data.Wound
import app.ranger.domain.Body
import app.ranger.domain.Cond
import app.ranger.domain.Event
import app.ranger.domain.Patrol
import app.ranger.domain.Ranks
import app.ranger.domain.World
import app.ranger.ui.Fmt
import app.ranger.ui.LocalRu
import app.ranger.ui.Nav
import app.ranger.ui.Overlay
import app.ranger.ui.Stat
import app.ranger.ui.TrailMap
import app.ranger.ui.ZoneLegend
import app.ranger.ui.ZoneStrip
import app.ranger.ui.art.Seal
import app.ranger.ui.components.Btn
import app.ranger.ui.components.Card
import app.ranger.ui.components.Chip
import app.ranger.ui.components.Field
import app.ranger.ui.components.Flow
import app.ranger.ui.components.Look
import app.ranger.ui.components.Section
import app.ranger.ui.components.Sheet
import app.ranger.ui.components.Tag
import app.ranger.ui.components.t
import app.ranger.ui.patrolTitle
import app.ranger.ui.theme.C
import app.ranger.ui.theme.T
import kotlin.math.roundToInt

/** One patrol: where and how it went, what it brought, and the ranger's own notes on it. */
@Composable
fun PatrolSheet(w: World, p: Patrol, nav: Nav) {
    val ru = LocalRu.current
    val ctx = LocalContext.current
    val c = ctx.container
    val r = p.run
    Sheet(nav::close) {
        Text(p.kind.of(ru).uppercase() + if (r.title.isNotBlank()) " · ${r.title}" else "", style = T.Eyebrow.copy(color = C.seal), maxLines = 1)
        Text(patrolTitle(p, ru), style = T.Title)
        Text(Fmt.weekday(p.date, ru) + ", " + Fmt.time(r.start) + " · " + source(r.source, ru), style = T.Small)
        if (r.hasTrack) TrailMap(w, p)
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        // two even rows of three, so the numbers line up in columns
        Row(Modifier.fillMaxWidth()) {
            Stat(Fmt.km(r.distanceM, ru), t("путь", "distance"), Modifier.weight(1f))
            Stat(Fmt.clock(if (r.movingS > 0) r.movingS else r.durationS), t("в движении", "moving"), Modifier.weight(1f))
            Stat(Fmt.pace(r.pace, ru, false), t("мин/км", "min/km"), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Stat("${r.ascentM.roundToInt()} " + t("м", "m"), t("набор", "climbed"), Modifier.weight(1f))
            Stat(r.avgHr?.let { "$it" + (r.maxHr?.let { m -> " / $m" } ?: "") } ?: "—", t("пульс ср./макс.", "pulse avg/max"), Modifier.weight(1f))
            Stat("+${p.load}", t("доблести", "valour"), Modifier.weight(1f))
        }
        if (p.hasHr) {
            Spacer(Modifier.height(12.dp))
            ZoneStrip(p.zones)
            ZoneLegend(p.zones)
        } else Text(t("Без пульса доблесть считается по темпу и набору.", "Without a pulse, valour comes from pace and climb."), style = T.Small, modifier = Modifier.padding(top = 8.dp))
        }

        val conds = Cond.SHOWN.filter { it in p.conds }
        if (conds.isNotEmpty() || r.tempC != null) {
            Flow {
                for (cd in conds) Tag(cd.of(ru), when { cd.bad -> C.river; cd in setOf(Cond.NIGHT, Cond.MIDNIGHT, Cond.FULL_MOON, Cond.DUSK, Cond.DAWN, Cond.EARLY) -> C.night2; cd in setOf(Cond.WATER, Cond.GREEN, Cond.HILLS) -> C.forest; cd in setOf(Cond.RACE, Cond.NEW_YEAR, Cond.MIDSUMMER, Cond.SAMHAIN, Cond.FIRST_SNOW) -> C.gold; else -> C.seal })
            }
            val wx = listOfNotNull(r.tempC?.let { "${it.roundToInt()}°" }, r.windKmh?.let { if (ru) "ветер ${(it / 3.6).roundToInt()} м/с" else "wind ${it.roundToInt()} km/h" },
                r.precipMm?.takeIf { it > 0 }?.let { if (ru) "осадки ${"%.1f".format(it)} мм" else "rain ${"%.1f".format(it)} mm" })
            if (wx.isNotEmpty()) Text(wx.joinToString(" · "), style = T.Small)
            else if (!r.weatherDone && r.lat != null) Text(t("Погода ещё уточняется.", "Weather still being looked up."), style = T.Small)
        }

        Events(p, nav)

        Section(t("Как прошло", "How it felt"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((v, l) in listOf(1 to t("легко", "light"), 2 to t("нормально", "fine"), 3 to t("тяжело", "hard"))) Chip(l, r.feel == v, { c.write { dao.update(r.copy(feel = if (r.feel == v) 0 else v)) } })
            Chip(t("турнир", "tournament"), r.race, { c.write { dao.update(r.copy(race = !r.race)) } }, color = C.gold)
        }

        Section(t("Сапоги", "Boots"))
        val active = w.shoes.filter { it.retiredAt == null || it.id == r.shoeId }
        Flow {
            for (s in active) Chip(s.name, r.shoeId == s.id, { c.write { dao.update(r.copy(shoeId = if (r.shoeId == s.id) null else s.id)) } })
            Chip(t("+ пара", "+ pair"), false, { nav.go(Overlay.Boots) })
        }

        Section(t("С кем", "With whom"))
        var adding by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf("") }
        Flow {
            for (f in w.companions) {
                val on = p.company.any { it.id == f.id }
                Chip((if (f.dog) "🐾 " else "") + f.name, on, { c.write { if (on) dao.unlink(r.id, f.id) else dao.link(RunCompanion(r.id, f.id)) } })
            }
            Chip(t("+ спутник", "+ companion"), adding, { adding = !adding })
        }
        if (adding) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(name, { name = it }, t("Имя", "Name"), Modifier.weight(1f))
            Btn(t("Добавить", "Add"), {
                val n = name.trim(); if (n.isEmpty()) return@Btn
                c.write { val id = dao.insert(Friend(name = n)); dao.link(RunCompanion(r.id, id)) }
                name = ""; adding = false
            }, small = true)
        }

        Section(t("Что звучало", "What was playing"))
        if (p.plays.isNotEmpty()) Column {
            for (pl in p.plays.take(12)) Text("${pl.artist} — ${pl.title}", style = T.Body, maxLines = 1)
            if (p.plays.size > 12) Text(if (ru) "и ещё ${p.plays.size - 12}" else "and ${p.plays.size - 12} more", style = T.Small)
        } else if (!w.s.music) Text(t("Можно включить в настройках: приложение будет запоминать, что играло в наушниках.", "Turn it on in settings and the app remembers what played in your headphones."), style = T.Small)
        var listened by remember(r.id) { mutableStateOf(r.listened) }
        Field(listened, { listened = it; c.write { dao.update(dao.run(r.id)!!.copy(listened = it)) } }, t("Подкаст, радио, тишина…", "A podcast, radio, silence…"))

        Section(t("Что-то болит?", "Anything hurt?"))
        val mine = w.wounds.filter { it.runId == r.id }
        Flow {
            for ((k, l) in Body.parts) {
                val on = mine.any { it.part == k }
                Chip(l.of(ru), on, { c.write { if (on) mine.filter { it.part == k }.forEach { dao.delete(it) } else dao.insert(Wound(runId = r.id, at = r.end, part = k)) } }, color = C.seal)
            }
        }

        Section(t("Заметка", "Note"))
        var note by remember(r.id) { mutableStateOf(r.note) }
        Field(note, { note = it; c.write { dao.update(dao.run(r.id)!!.copy(note = it)) } }, t("Что запомнилось", "What you remember"), single = false)

        var sure by remember { mutableStateOf(false) }
        Btn(if (sure) t("Точно стереть дозор?", "Really erase this patrol?") else t("Стереть дозор", "Erase patrol"), {
            if (!sure) { sure = true; return@Btn }
            c.write { dao.forget(r); TrackFile.delete(app, r.id) }
            nav.close()
        }, Modifier.fillMaxWidth().padding(top = 12.dp), kind = Look.GHOST)
    }
}

private fun source(s: String, ru: Boolean) = when (s) {
    "hc" -> "Health Connect"; "strava" -> "Strava"; "gpx" -> "GPX"; "tcx" -> "TCX"; "fit" -> "FIT"
    "rec" -> if (ru) "записан здесь" else "recorded here"; "manual" -> if (ru) "вручную" else "by hand"; "demo" -> if (ru) "пример" else "sample"
    else -> s
}

/** What the patrol brought: meetings, tracks, charters, ranks, skills, campaigns. */
@Composable
fun Events(p: Patrol, nav: Nav) {
    val ru = LocalRu.current
    if (p.events.isEmpty()) return
    Section(t("Что принёс дозор", "What the patrol brought"))
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in p.events.sortedBy { order(it) }) when (e) {
                is Event.Met -> Row(Modifier.fillMaxWidth().clickable { nav.go(Overlay.Creature(e.c.key)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Seal(e.c, 3, Modifier.size(40.dp))
                    Column { Text(if (ru) "Встреча: ${e.c.name.ru}" else "Met: ${e.c.name.en}", style = T.Strong); Text(e.c.lore.of(ru), style = T.Small, maxLines = 2) }
                }
                is Event.Tracks -> Row(Modifier.fillMaxWidth().clickable { nav.go(Overlay.Creature(e.c.key)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Seal(e.c, e.stage, Modifier.size(32.dp))
                    Text(if (e.stage == 2) (if (ru) "Логово: ${e.c.name.ru} (${e.n}/${e.c.need})" else "Lair: ${e.c.name.en} (${e.n}/${e.c.need})")
                        else (if (ru) "Следы: ${e.c.name.ru} (${e.n}/${e.c.need})" else "Tracks: ${e.c.name.en} (${e.n}/${e.c.need})"), style = T.Body)
                }
                is Event.Record -> Text((if (ru) "Грамота: лучшее время на ${Fmt.distanceNames[e.i].first.lowercase().replace("полумарафон", "полумарафоне").replace("марафон", "марафоне")} — ${Fmt.best(e.s)}"
                    else "Charter: best ${Fmt.distanceNames[e.i].second.lowercase()} — ${Fmt.best(e.s)}") + if (e.old > 0) " (−${Fmt.clock(e.old - e.s)})" else "", style = T.Body.copy(color = C.gold))
                is Event.RankUp -> Text(if (ru) "Новое звание: ${Ranks.all[e.index].name.ru}" else "New rank: ${Ranks.all[e.index].name.en}", style = T.Strong.copy(color = C.seal))
                is Event.SkillUp -> Text(if (ru) "${e.skill.title.ru}: уровень ${e.level}" else "${e.skill.title.en}: level ${e.level}", style = T.Body)
                is Event.CampaignDone -> Text(if (ru) "Поход пройден: ${e.c.name.ru}" else "Campaign done: ${e.c.name.en}", style = T.Strong.copy(color = C.forest))
                is Event.FeatDone -> Text((if (ru) "Подвиг: " else "Feat: ") + e.f.title.of(ru) + " — " + e.f.about.of(ru).replaceFirstChar { it.lowercase() }, style = T.Body.copy(color = C.gold))
            }
        }
    }
}

private fun order(e: Event) = when (e) { is Event.Met -> 0; is Event.RankUp -> 1; is Event.CampaignDone -> 2; is Event.Record -> 3; is Event.Tracks -> 4; is Event.SkillUp -> 6; is Event.FeatDone -> 5 }
