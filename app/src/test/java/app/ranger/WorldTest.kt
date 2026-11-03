package app.ranger

import app.ranger.data.Friend
import app.ranger.data.Quest
import app.ranger.data.Run
import app.ranger.data.RunCompanion
import app.ranger.data.Settings
import app.ranger.domain.Bestiary
import app.ranger.domain.Cond
import app.ranger.domain.Event
import app.ranger.domain.Form
import app.ranger.domain.Kind
import app.ranger.domain.Skill
import app.ranger.domain.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class WorldTest {
    private val zone: ZoneId = ZoneOffset.ofHours(1)
    private var nextId = 1L

    private fun run(at: String, km: Double, min: Int, hr: String = "", vararg mods: (Run) -> Run): Run {
        val start = LocalDateTime.parse(at).atZone(zone).toInstant().toEpochMilli()
        var r = Run(id = nextId++, start = start, durationS = min * 60, movingS = min * 60, distanceM = km * 1000, source = "t", sourceId = "t$nextId",
            lat = 50.1, lon = 14.42, hrHist = hr, weatherDone = true, tempC = 12.0, wCode = 1, precipMm = 0.0, windKmh = 5.0)
        for (m in mods) r = m(r)
        return r
    }

    private fun world(runs: List<Run>, now: String = "2026-06-30T12:00", friends: List<Friend> = emptyList(), links: List<RunCompanion> = emptyList(), quests: List<Quest> = emptyList(), s: Settings = Settings(maxHr = 190, restHr = 50)) =
        World(runs, emptyList(), friends, links, emptyList(), emptyList(), emptyList(), quests, s, LocalDateTime.parse(now).atZone(zone).toInstant().toEpochMilli(), zone)

    @Test fun nightRainMeetsTheWisp() {
        val runs = listOf(run("2026-06-10T23:30", 6.0, 36, "", { it.copy(wCode = 61, precipMm = 1.2) }))
        val w = world(runs)
        val p = w.patrols.single()
        assertTrue(Cond.NIGHT in p.conds)
        assertTrue(Cond.RAIN in p.conds)
        assertTrue(Cond.MIDNIGHT in p.conds)
        assertEquals(3, w.hunts["wisp_light"]!!.stage)
        assertEquals(1, w.hunts["lynx"]!!.stage)
        assertTrue(p.events.any { it is Event.Met && it.c.key == "wisp_light" })
    }

    @Test fun huntStages() {
        val lynx = Bestiary.byKey["lynx"]!!
        assertEquals(listOf(0, 1, 2, 3, 3), (0..4).map { Bestiary.stage(lynx, it) })
        val quiet = Bestiary.byKey["whisperer"]!!
        assertEquals(listOf(0, 1, 1, 2, 2, 3), (0..5).map { Bestiary.stage(quiet, it) })
    }

    @Test fun zonesAndEasy() {
        // 40 minutes at 120 bpm with max 190: zone 2 (114..133)
        val w = world(listOf(run("2026-06-10T08:00", 6.0, 40, "120:2400")))
        val p = w.patrols.single()
        assertEquals(2400, p.zones[1])
        assertTrue(Cond.EASY in p.conds)
        assertEquals(Kind.EASY, p.kind)
        assertTrue(p.load in 25..45)
    }

    @Test fun longRunAndRecord() {
        val runs = listOf(
            run("2026-06-01T08:00", 5.0, 30, "", { it.copy(best = "300,1500,0,0,0") }),
            run("2026-06-03T08:00", 5.0, 29, "", { it.copy(best = "290,1450,0,0,0") }),
            run("2026-06-07T08:00", 16.0, 100),
        )
        val w = world(runs)
        assertTrue(Cond.LONG in w.patrols[2].conds)
        assertEquals(1450, w.bests[1]!!.s)
        assertTrue(w.patrols[1].events.any { it is Event.Record && it.i == 1 && it.old == 1500 })
        assertEquals(16, w.skillValues[Skill.ENDURANCE])
    }

    @Test fun companyAndDog() {
        val f = listOf(Friend(1, "A"), Friend(2, "B"), Friend(3, "Dog", dog = true))
        val runs = listOf(run("2026-06-10T08:00", 6.0, 36))
        val w = world(runs, friends = f, links = listOf(RunCompanion(runs[0].id, 1), RunCompanion(runs[0].id, 2), RunCompanion(runs[0].id, 3)))
        val c = w.patrols.single().conds
        assertTrue(Cond.TOGETHER in c && Cond.PACK in c && Cond.DOG in c)
        assertEquals(3, w.hunts["centaurs"]!!.stage)
    }

    @Test fun treadmillGoesUnderground() {
        val w = world(listOf(run("2026-06-10T08:00", 6.0, 36, "", { it.copy(treadmill = true, lat = null, lon = null, wCode = 61, precipMm = 3.0) })))
        val c = w.patrols.single().conds
        assertTrue(Cond.UNDERGROUND in c)
        assertFalse(Cond.RAIN in c)
    }

    @Test fun campaignCountsOnlyItsWindow() {
        val start = LocalDateTime.parse("2026-06-01T00:00").atZone(zone).toInstant().toEpochMilli()
        val q = Quest(1, "tract", start, start + 30 * 86_400_000L)
        val runs = listOf(run("2026-05-30T08:00", 30.0, 180)) + (1..8).map { run("2026-06-%02dT08:00".format(it * 2), 10.0, 60) }
        val w = world(runs, quests = listOf(q))
        val st = w.quests.single()
        assertEquals(80, st.progress)
        assertTrue(st.doneAt != null)
    }

    @Test fun stepsGiveValourWithoutTheRun() {
        val runs = listOf(run("2026-06-10T08:00", 5.0, 30))
        val bare = World(runs, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), Settings(maxHr = 190, restHr = 50),
            LocalDateTime.parse("2026-06-11T12:00").atZone(zone).toInstant().toEpochMilli(), zone)
        val days = listOf(app.ranger.data.Day("2026-06-10", steps = 16_500), app.ranger.data.Day("2026-06-11", steps = 9_000, stepsByHand = true))
        val w = World(runs, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), days, emptyList(), Settings(maxHr = 190, restHr = 50),
            LocalDateTime.parse("2026-06-11T12:00").atZone(zone).toInstant().toEpochMilli(), zone)
        // 16 500 - 5 km x 1300 = 10 000 walked, plus 9 000: 19 thousand, three each
        assertEquals(bare.xp + 57, w.xp)
        assertEquals(9_000, w.todaySteps)
        assertEquals(2, w.skillValues[Skill.WALKER])
    }

    @Test fun idleAfterAPause() {
        val w = world(listOf(run("2026-05-01T08:00", 6.0, 36)))
        assertEquals(Form.IDLE, w.form)
    }

    @Test fun firstSnowOncePerWinter() {
        val runs = listOf(
            run("2025-11-20T08:00", 6.0, 36, "", { it.copy(wCode = 73, tempC = -1.0) }),
            run("2025-12-02T08:00", 6.0, 36, "", { it.copy(wCode = 73, tempC = -1.0) }),
        )
        val w = world(runs, now = "2025-12-10T12:00")
        assertTrue(Cond.FIRST_SNOW in w.patrols[0].conds)
        assertFalse(Cond.FIRST_SNOW in w.patrols[1].conds)
    }
}
