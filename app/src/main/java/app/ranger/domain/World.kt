package app.ranger.domain

import app.ranger.data.Friend
import app.ranger.data.Day
import app.ranger.data.Geo
import app.ranger.data.Play
import app.ranger.data.Quest
import app.ranger.data.Run
import app.ranger.data.RunCompanion
import app.ranger.data.Settings
import app.ranger.data.Shoe
import app.ranger.data.Wound
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Something a patrol brought: shown on its card and in the reveal right after import. */
sealed class Event {
    data class Tracks(val c: Creature, val stage: Int, val n: Int) : Event()
    data class Met(val c: Creature) : Event()
    /** A new best over [Digest.DISTANCES][app.ranger.data.Digest.DISTANCES] index [i]; [old] 0 if there was none. */
    data class Record(val i: Int, val s: Int, val old: Int) : Event()
    data class RankUp(val index: Int) : Event()
    data class SkillUp(val skill: Skill, val level: Int) : Event()
    data class CampaignDone(val c: Campaign) : Event()
}

/** A run as the Borderlands see it. */
class Patrol(
    val run: Run,
    /** Seconds in heart-rate zones 1–5; all zero without a heart rate. */
    val zones: IntArray,
    /** Training load (TRIMP), the patrol's valour. */
    val load: Int,
    val conds: Set<Cond>,
    val kind: Kind,
    val company: List<Friend>,
    val plays: List<Play>,
    /** Map squares first run through on this patrol. */
    val newCells: Int,
    val date: LocalDate,
    val events: MutableList<Event> = ArrayList(),
) {
    val hasHr get() = zones.sum() > 0
    val easyShare get() = zones.sum().let { if (it == 0) 0.0 else (zones[0] + zones[1]).toDouble() / it }
    val music get() = plays.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key
}

data class Hunt(val c: Creature, val n: Int, val stage: Int, val metAt: Patrol?, val last: Patrol?)
data class ShoeStat(val shoe: Shoe, val km: Double, val runs: Int, val last: Long?) { val wear get() = (km / shoe.limitKm).coerceIn(0.0, 1.5) }
data class Mate(val c: Friend, val runs: Int, val km: Double, val role: L, val last: Long?)
data class Best(val i: Int, val s: Int, val p: Patrol)
data class QuestState(val q: Quest, val c: Campaign, val progress: Int, val doneAt: Patrol?, val active: Boolean, val failed: Boolean)
data class Tune(val artist: String, val runs: Int, val faster: Int)

/** Freshness from the last weeks of load: fitness (42-day average) against fatigue (7-day). */
enum class Form(val title: L, val about: L) {
    FRESH(L("Полны сил", "Fresh"), L("Отдохнули. Хороший день для долгого или быстрого дозора.", "Rested. A good day for something long or fast.")),
    READY(L("В форме", "In shape"), L("Нагрузка и отдых в равновесии.", "Load and rest in balance.")),
    TIRED(L("Устали", "Tired"), L("Последние дни были тяжёлыми. Лучше лёгкий шаг или привал.", "The last days were heavy. Go easy or rest.")),
    SPENT(L("Выбились из сил", "Worn out"), L("Нагрузка выросла слишком быстро. Нужен привал, иначе захромаете.", "Load rose too fast. Rest, or you'll go lame.")),
    IDLE(L("Засиделись", "Idle"), L("Давно не было дозоров. Начните с короткого.", "No patrols for a while. Start with a short one.")),
}

class World(
    runs: List<Run>,
    val shoes: List<Shoe>,
    val companions: List<Friend>,
    links: List<RunCompanion>,
    plays: List<Play>,
    val wounds: List<Wound>,
    val days: List<Day>,
    quests: List<Quest>,
    val s: Settings,
    val now: Long,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    val maxHr: Int = s.maxHr.takeIf { it > 0 } ?: runs.mapNotNull { it.maxHr }.filter { it in 120..230 }.sortedDescending().take(3)
        // a run's peak is rarely the true maximum, so a few beats on top
        .takeIf { it.isNotEmpty() }?.average()?.let { it + 5 }?.roundToInt()?.coerceIn(170, 215) ?: 190
    val restHr: Int = s.restHr.takeIf { it > 0 } ?: days.mapNotNull { it.restHr }.takeLast(30).sorted().let { if (it.isEmpty()) 60 else it[it.size / 2] }

    /** Where the ranger lives: the first hidden place, or the middle of where runs start. */
    val home: Pair<Double, Double>? = s.hidden.firstOrNull()?.let { it.lat to it.lon } ?: runs.filter { it.lat != null }.let { r ->
        if (r.isEmpty()) null else r.map { it.lat!! }.sorted()[r.size / 2] to r.map { it.lon!! }.sorted()[r.size / 2]
    }

    private val cellLon = 0.00225 / cos(Math.toRadians(home?.first ?: 50.0)).coerceAtLeast(0.2)
    private fun cell(lat: Double, lon: Double): Long = (floor(lat / 0.00225).toLong() shl 32) or (floor(lon / cellLon).toLong() and 0xffffffffL)

    val zoneEdges: IntArray = intArrayOf(60, 70, 80, 90).map { (maxHr * it / 100.0).roundToInt() }.toIntArray()

    val patrols: List<Patrol>
    val hunts: Map<String, Hunt>
    val xp: Int
    val rank: Int get() = Ranks.index(xp)
    val skills: Map<Skill, Int>
    val skillValues: Map<Skill, Int>
    val bests: List<Best?>
    val cells: Set<Long>
    val bestStreak: Int
    val streak: Int
    val medianPace: Double
    val medianKm: Double

    init {
        val sorted = runs.sortedBy { it.start }
        val byRun = links.groupBy { it.runId }
        val people = companions.associateBy { it.id }
        val ps = plays.sortedBy { it.at }
        val paces = sorted.filter { it.distanceM > 1000 && it.pace > 120 }.map { it.pace }.sorted()
        medianPace = if (paces.isEmpty()) 390.0 else paces[paces.size / 2]
        val kms = sorted.filter { it.distanceM > 1000 }.map { it.km }.sorted()
        medianKm = if (kms.isEmpty()) 5.0 else kms[kms.size / 2]

        val seen = HashSet<Long>()
        val counts = HashMap<String, Int>(); val met = HashMap<String, Patrol>(); val last = HashMap<String, Patrol>()
        val best = arrayOfNulls<Best>(5)
        var total = 0; var rankNow = 0
        val sk = HashMap<Skill, Int>(); val lv = HashMap<Skill, Int>()
        var longest = 0.0; var climbed = 0.0; var nights = 0; var foul = 0; var easy = 0
        val weeks = sortedSetOf<Long>()
        var snowSeason = -1
        val recentKm = ArrayDeque<Double>()
        val out = ArrayList<Patrol>(sorted.size)

        for (r in sorted) {
            val local = Instant.ofEpochMilli(r.start).atZone(zone)
            val date = local.toLocalDate()
            val zones = zones(r.hrHist)
            val company = byRun[r.id].orEmpty().mapNotNull { people[it.companionId] }
            // squares of the map run through, new ones counted
            var fresh = 0; var allCells = 0
            if (r.line.isNotEmpty()) {
                val mine = HashSet<Long>()
                val pts = Geo.decode(r.line)
                for (i in pts.indices) {
                    val (la, lo) = pts[i]
                    if (i > 0) {
                        val (pa, po) = pts[i - 1]
                        val steps = (Geo.dist(pa, po, la, lo) / 120).toInt()
                        for (k in 1..steps) mine += cell(pa + (la - pa) * k / (steps + 1), po + (lo - po) * k / (steps + 1))
                    }
                    mine += cell(la, lo)
                }
                allCells = mine.size
                for (c in mine) if (seen.add(c)) fresh++
            }
            val conds = conds(r, local, zones, company, fresh, allCells, recentKm)
            // the season's first snow: a winter counts from July
            if (Cond.SNOW in conds) {
                val season = if (date.monthValue >= 7) date.year else date.year - 1
                if (season != snowSeason) { snowSeason = season; conds += Cond.FIRST_SNOW }
            }
            recentKm.addLast(r.km); if (recentKm.size > 20) recentKm.removeFirst()
            val load = load(r, zones)
            val runPlays = playsIn(ps, r.start, r.end)
            val p = Patrol(r, zones, load, conds, kind(r, conds, zones), company, runPlays, fresh, date)

            for (c in Bestiary.all) if (c.test(conds)) {
                val n = (counts[c.key] ?: 0) + 1; counts[c.key] = n; last[c.key] = p
                val before = Bestiary.stage(c, n - 1); val after = Bestiary.stage(c, n)
                if (after == 3 && before < 3) { met[c.key] = p; p.events += Event.Met(c) }
                else if (after < 3) p.events += Event.Tracks(c, after, n)
            }
            for (i in 0 until 5) {
                val t = r.bestOf(i)
                if (t > 0 && (best[i] == null || t < best[i]!!.s)) { p.events += Event.Record(i, t, best[i]?.s ?: 0); best[i] = Best(i, t, p) }
            }
            total += load
            val ri = Ranks.index(total)
            if (ri > rankNow) { rankNow = ri; p.events += Event.RankUp(ri) }

            longest = max(longest, r.km); climbed += r.ascentM
            if (Cond.NIGHT in conds) nights++
            if (conds.any { it.bad }) foul++
            if (Cond.EASY in conds) easy++
            weeks += date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
            sk[Skill.ENDURANCE] = longest.toInt(); sk[Skill.SPEED] = (best[1]?.s ?: 0) / 5
            sk[Skill.NIGHT_EYE] = nights; sk[Skill.MOUNTAIN] = climbed.toInt(); sk[Skill.GRIT] = foul; sk[Skill.QUIET] = easy
            sk[Skill.SCOUTING] = seen.size; sk[Skill.CONSTANCY] = longestRun(weeks)
            for (k in Skill.entries) {
                val l = k.level(sk[k] ?: 0)
                if (l > (lv[k] ?: 0)) { lv[k] = l; p.events += Event.SkillUp(k, l) }
            }
            out += p
        }
        patrols = out
        xp = total
        hunts = Bestiary.all.associate { c -> val n = counts[c.key] ?: 0; c.key to Hunt(c, n, Bestiary.stage(c, n), met[c.key], last[c.key]) }
        skillValues = Skill.entries.associateWith { sk[it] ?: 0 }
        skills = Skill.entries.associateWith { lv[it] ?: 0 }
        bests = best.toList()
        cells = seen
        bestStreak = longestRun(weeks)
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        streak = run {
            var w = if (thisWeek in weeks) thisWeek else thisWeek - 7
            var n = 0
            while (w in weeks) { n++; w -= 7 }
            n
        }
    }

    // ——— campaigns ———

    val quests: List<QuestState> = quests.mapNotNull { q ->
        val c = Campaigns.byKey[q.key] ?: return@mapNotNull null
        var acc = 0; var done: Patrol? = null
        for (p in patrols) if (p.run.start in q.startedAt..q.endsAt && (q.abandonedAt == null || p.run.start < q.abandonedAt)) {
            acc += c.gives(p)
            if (done == null && acc >= c.target) { done = p; p.events += Event.CampaignDone(c) }
        }
        val ended = now > q.endsAt || q.abandonedAt != null
        QuestState(q, c, acc, done, !ended && done == null, ended && done == null && q.abandonedAt == null)
    }

    // ——— load and form ———

    /** Load per day, oldest first, up to today. */
    private val daily: Map<LocalDate, Int> = patrols.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.load } }

    /** Fitness (CTL), fatigue (ATL) as of each day of the last [n] days, today last. */
    fun curve(n: Int): List<Triple<LocalDate, Double, Double>> {
        val first = patrols.firstOrNull()?.date ?: return emptyList()
        var ctl = 0.0; var atl = 0.0
        val out = ArrayList<Triple<LocalDate, Double, Double>>()
        var d = first
        val from = today.minusDays(n.toLong() - 1)
        while (!d.isAfter(today)) {
            val l = (daily[d] ?: 0).toDouble()
            ctl += (l - ctl) / 42; atl += (l - atl) / 7
            if (!d.isBefore(from)) out += Triple(d, ctl, atl)
            d = d.plusDays(1)
        }
        return out
    }

    val fitness: Double; val fatigue: Double
    init { val c = curve(1).lastOrNull(); fitness = c?.second ?: 0.0; fatigue = c?.third ?: 0.0 }

    val lastPatrol get() = patrols.lastOrNull()
    val daysSince: Int get() = lastPatrol?.let { ChronoUnit.DAYS.between(it.date, today).toInt() } ?: -1

    val form: Form get() {
        if (patrols.isEmpty()) return Form.IDLE
        if (daysSince > 10) return Form.IDLE
        val week = patrols.filter { !it.date.isBefore(today.minusDays(6)) }.sumOf { it.load }
        val month = patrols.filter { !it.date.isBefore(today.minusDays(27)) }.sumOf { it.load } / 4.0
        val ratio = if (month > 30) week / month else 1.0
        val tsb = fitness - fatigue
        return when {
            ratio > 1.5 && week > 250 || tsb < -30 -> Form.SPENT
            tsb < -12 -> Form.TIRED
            tsb > 8 -> Form.FRESH
            else -> Form.READY
        }
    }

    /** Resting pulse this morning against the month before: up by five or more means the body is busy recovering. */
    val pulseUp: Int? get() {
        val t = days.firstOrNull { it.date == today.toString() }?.restHr ?: return null
        val before = days.filter { it.date < today.toString() && it.date >= today.minusDays(30).toString() }.mapNotNull { it.restHr }.sorted()
        if (before.size < 5) return null
        val base = before[before.size / 2]
        return (t - base).takeIf { it >= 5 }
    }

    // ——— the rest of the outpost ———

    val shoeStats: List<ShoeStat> = shoes.map { sh ->
        val mine = patrols.filter { it.run.shoeId == sh.id }
        ShoeStat(sh, sh.baseKm + mine.sumOf { it.run.km }, mine.size, mine.lastOrNull()?.run?.start)
    }

    val mates: List<Mate> = companions.map { c ->
        val mine = patrols.filter { p -> p.company.any { it.id == c.id } }
        Mate(c, mine.size, mine.sumOf { it.run.km }, role(c, mine), mine.lastOrNull()?.run?.start)
    }.sortedByDescending { it.runs }

    /** Artists heard on three or more patrols, and how much faster (s/km) you ran to them than usual. */
    val tunes: List<Tune> = patrols.filter { it.plays.isNotEmpty() && it.run.pace > 0 }.flatMap { p -> p.plays.map { it.artist }.distinct().map { it to p } }
        .groupBy({ it.first }, { it.second }).filter { it.value.size >= 3 }
        .map { (a, ps) -> Tune(a, ps.size, (medianPace - ps.map { it.run.pace }.average()).roundToInt()) }
        .sortedByDescending { it.runs }

    val met: List<Hunt> get() = hunts.values.filter { it.stage == 3 }.sortedBy { it.metAt!!.run.start }
    val tracking: List<Hunt> get() = hunts.values.filter { it.stage in 1..2 }.sortedWith(compareByDescending<Hunt> { it.stage }.thenByDescending { it.last?.run?.start ?: 0 })

    fun patrol(id: Long) = patrols.firstOrNull { it.run.id == id }
    fun shoe(id: Long?) = shoes.firstOrNull { it.id == id }

    val totalKm get() = patrols.sumOf { it.run.km }
    val totalAscent get() = patrols.sumOf { it.run.ascentM }
    val totalTime get() = patrols.sumOf { it.run.movingS.toLong() }

    /** Kilometres over the last seven days, today included. */
    val lastWeekKm get() = patrols.filter { !it.date.isBefore(today.minusDays(6)) }.sumOf { it.run.km }

    /** Kilometres per week for the last [n] weeks, oldest first. */
    fun weeks(n: Int): List<Pair<LocalDate, Double>> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val byWeek = patrols.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        return (n - 1 downTo 0).map { k -> val w = monday.minusWeeks(k.toLong()); w to byWeek[w].orEmpty().sumOf { it.run.km } }
    }

    /** Seconds in each zone over the last [days] days. */
    fun zonesSince(days: Int): IntArray {
        val out = IntArray(5)
        for (p in patrols) if (!p.date.isBefore(today.minusDays(days.toLong() - 1))) for (i in 0 until 5) out[i] += p.zones[i]
        return out
    }

    /** Average pace of easy-zone runs month by month: if it drops at the same pulse, the boots got lighter. */
    fun easyPaceByMonth(): List<Pair<LocalDate, Double>> = patrols.filter { it.hasHr && it.run.avgHr != null && it.run.avgHr in zoneEdges[0]..zoneEdges[1] && it.run.distanceM > 2000 }
        .groupBy { it.date.withDayOfMonth(1) }.toSortedMap().map { (m, ps) -> m to ps.map { it.run.pace }.average() }

    val openWounds get() = wounds.filter { it.healedAt == null }

    /** A part that hurt twice or more in a pair of boots: the healer mentions it. */
    val chafing: List<Pair<String, Long>> get() = wounds.mapNotNull { w -> w.runId?.let { id -> patrol(id)?.run?.shoeId?.let { w.part to it } } }
        .groupingBy { it }.eachCount().filter { it.value >= 2 }.keys.toList()

    // ——— helpers ———

    fun zones(hist: String): IntArray {
        val z = IntArray(5)
        if (hist.isEmpty()) return z
        for (part in hist.split(',')) {
            val i = part.indexOf(':'); if (i < 0) continue
            val hr = part.substring(0, i).toIntOrNull() ?: continue; val s = part.substring(i + 1).toIntOrNull() ?: continue
            z[zoneEdges.count { hr >= it }] += s
        }
        return z
    }

    /** Banister TRIMP from the heart-rate histogram; without one, guessed from pace against your usual and the climb. */
    private fun load(r: Run, zones: IntArray): Int {
        val span = (maxHr - restHr).coerceAtLeast(40).toDouble()
        if (r.hrHist.isNotEmpty() && zones.sum() > 60) {
            var t = 0.0
            for (part in r.hrHist.split(',')) {
                val i = part.indexOf(':'); if (i < 0) continue
                val hr = part.substring(0, i).toIntOrNull() ?: continue; val s = part.substring(i + 1).toIntOrNull() ?: continue
                val x = ((hr - restHr) / span).coerceIn(0.0, 1.0)
                t += s / 60.0 * x * 0.64 * exp(1.92 * x)
            }
            return t.roundToInt()
        }
        val min = (if (r.movingS > 0) r.movingS else r.durationS) / 60.0
        val pace = r.pace
        var x = if (pace > 0) (0.62 + 0.55 * (medianPace / pace - 1)).coerceIn(0.45, 0.95) else 0.62
        if (r.feel == 3) x = (x + 0.06).coerceAtMost(0.95)
        if (r.feel == 1) x = (x - 0.04).coerceAtLeast(0.45)
        return (min * x * 0.64 * exp(1.92 * x) + r.ascentM / 12).roundToInt()
    }

    private fun conds(r: Run, local: java.time.ZonedDateTime, zones: IntArray, company: List<Friend>, fresh: Int, allCells: Int, recentKm: ArrayDeque<Double>): MutableSet<Cond> {
        val c = HashSet<Cond>()
        val date = local.toLocalDate()
        val lat = r.lat ?: home?.first; val lon = r.lon ?: home?.second
        val startMin = local.hour * 60 + local.minute
        val midMin = startMin + r.durationS / 120
        val sun = if (lat != null && lon != null) Sun.times(date, lat, lon, zone) else Sun.times(date, 52.0, zone.rules.getOffset(local.toInstant()).totalSeconds / 240.0, zone)
        if (sun != null) {
            if (midMin < sun.rise - 35 || midMin > sun.set + 35) c += Cond.NIGHT
            if (startMin in (sun.rise - 50)..(sun.rise + 40)) c += Cond.DAWN
            if (midMin in (sun.set - 40)..(sun.set + 50)) c += Cond.DUSK
        } else if (date.monthValue in listOf(11, 12, 1)) c += Cond.NIGHT
        if (startMin >= 23 * 60 || startMin < 3 * 60) c += Cond.MIDNIGHT
        if (startMin in 4 * 60 until 6 * 60 + 30) c += Cond.EARLY
        if (Cond.NIGHT in c && Moon.light(r.start + r.durationS * 500L) > 0.96) c += Cond.FULL_MOON

        val code = r.wCode
        if (code != null || r.precipMm != null) {
            if ((r.precipMm ?: 0.0) >= 0.3 || code in 51..67 || code in 80..82) c += Cond.RAIN
            if ((r.snowCm ?: 0.0) > 0.0 || code in 71..77 || code == 85 || code == 86) { c += Cond.SNOW; c -= Cond.RAIN }
            if (code == 45 || code == 48) c += Cond.FOG
            if (code != null && code >= 95) { c += Cond.STORM; c += Cond.RAIN }
        }
        r.tempC?.let { if (it <= -5) c += Cond.FROST; if (it >= 25) c += Cond.HEAT }
        r.windKmh?.let { if (it >= 30) c += Cond.WIND }
        if (r.treadmill) { c.removeAll(Cond.BAD); c += Cond.TREADMILL }
        if (r.treadmill || !r.hasTrack) c += Cond.UNDERGROUND

        if ((r.nearWater ?: 0f) >= 0.25f) c += Cond.WATER
        if ((r.inGreen ?: 0f) >= 0.4f) c += Cond.GREEN
        if (r.ascentM >= 300 || (r.ascentM >= 80 && r.ascentM / r.km.coerceAtLeast(0.5) >= 15)) c += Cond.HILLS

        val med = recentKm.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
        if (r.km >= 15 || (r.km >= 10 && med > 0 && r.km >= med * 1.6)) c += Cond.LONG
        if (r.km >= 30) { c += Cond.VERY_LONG; c += Cond.LONG }
        if (r.surges >= 4 || Regex("интервал|interval|фартлек|fartlek|\\d+ ?[x×х] ?\\d", RegexOption.IGNORE_CASE).containsMatchIn(r.title)) c += Cond.INTERVALS
        val zsum = zones.sum()
        if (zsum > 0) {
            if (zsum >= 15 * 60 && (zones[0] + zones[1]) >= zsum * 0.8) c += Cond.EASY
            if (zsum >= 20 * 60 && (zones[2] + zones[3]) >= zsum * 0.5 && Cond.INTERVALS !in c) c += Cond.TEMPO
        } else if (r.pace > 0) {
            if (r.feel == 1 || (r.feel != 3 && r.pace >= medianPace * 1.06)) c += Cond.EASY
            if (r.movingS >= 20 * 60 && r.pace <= medianPace * 0.93 && Cond.INTERVALS !in c) c += Cond.TEMPO
        }
        if (r.race) c += Cond.RACE

        val people = company.count { !it.dog }
        if (people >= 1) c += Cond.TOGETHER
        if (people >= 2) c += Cond.PACK
        if (company.any { it.dog }) c += Cond.DOG
        if (home != null && r.lat != null && r.lon != null && Geo.dist(home.first, home.second, r.lat, r.lon) > 50_000) c += Cond.FAR
        if (allCells >= 4 && fresh * 2 >= allCells) c += Cond.NEW_GROUND

        if (date.monthValue == 1 && date.dayOfMonth == 1) c += Cond.NEW_YEAR
        if (date.monthValue == 6 && date.dayOfMonth in 20..24 && (Cond.DUSK in c || Cond.NIGHT in c)) c += Cond.MIDSUMMER
        if (date.monthValue == 10 && date.dayOfMonth == 31 && (Cond.DUSK in c || Cond.NIGHT in c)) c += Cond.SAMHAIN
        return c
    }

    private fun kind(r: Run, c: Set<Cond>, zones: IntArray): Kind = when {
        Cond.RACE in c -> Kind.RACE
        Cond.TREADMILL in c && Cond.INTERVALS !in c -> Kind.TREADMILL
        Cond.INTERVALS in c -> Kind.INTERVALS
        Cond.TEMPO in c -> Kind.TEMPO
        Cond.LONG in c -> Kind.LONG
        Cond.HILLS in c -> Kind.HILLS
        Cond.EASY in c && r.movingS in 1 until 30 * 60 -> Kind.RECOVERY
        Cond.EASY in c -> Kind.EASY
        else -> Kind.RUN
    }

    private fun role(c: Friend, mine: List<Patrol>): L {
        if (c.dog) return L("Гончая", "Hound")
        if (mine.isEmpty()) return L("Спутник", "Friend")
        val n = mine.size.toDouble()
        fun share(k: Cond) = mine.count { k in it.conds } / n
        return when {
            share(Cond.RACE) >= 0.3 -> L("Знаменосец", "Standard-bearer")
            share(Cond.INTERVALS) + share(Cond.TEMPO) >= 0.4 -> L("Застрельщик", "Skirmisher")
            share(Cond.LONG) >= 0.4 -> L("Проводник", "Guide")
            share(Cond.HILLS) >= 0.4 -> L("Горец", "Highlander")
            share(Cond.NIGHT) >= 0.5 -> L("Ночной страж", "Night guard")
            share(Cond.EASY) >= 0.5 -> L("Лекарь", "Healer")
            share(Cond.DAWN) + share(Cond.EARLY) >= 0.5 -> L("Вестник", "Herald")
            else -> L("Спутник", "Friend")
        }
    }

    private fun playsIn(ps: List<Play>, from: Long, to: Long): List<Play> {
        if (ps.isEmpty()) return emptyList()
        var lo = 0; var hi = ps.size
        while (lo < hi) { val m = (lo + hi) / 2; if (ps[m].until < from) lo = m + 1 else hi = m }
        val out = ArrayList<Play>()
        var i = lo
        while (i < ps.size && ps[i].at <= to) { if (ps[i].until >= from) out += ps[i]; i++ }
        return out
    }

    private fun longestRun(weeks: Set<Long>): Int {
        var best = 0; var cur = 0; var prev = Long.MIN_VALUE
        for (w in weeks.sorted()) { cur = if (w == prev + 7) cur + 1 else 1; best = max(best, cur); prev = w }
        return best
    }
}
