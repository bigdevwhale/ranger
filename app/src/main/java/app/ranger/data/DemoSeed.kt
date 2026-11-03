package app.ranger.data

import android.content.Context
import app.ranger.AppContainer
import app.ranger.ru
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp

import kotlin.math.min
import kotlin.math.roundToInt

import kotlin.random.Random

/**
 * A year and a bit of patrols around Letná in Prague for the demo build and the screenshots: real loops along real
 * paths (tools/demo_routes.py), heart rate from a simple model, weather by season, a band of two and a dog,
 * three pairs of boots, a few races and the music that played. Everything goes through the real importer.
 */
object DemoSeed {
    private val HOME = 50.1003 to 14.4262

    private class Grid(val lat0: Double, val lon0: Double, val dlat: Double, val dlon: Double, val n: Int, val m: Int, val z: DoubleArray) {
        fun at(la: Double, lo: Double): Double {
            val fi = ((la - lat0) / dlat).coerceIn(0.0, n - 1.001); val fj = ((lo - lon0) / dlon).coerceIn(0.0, m - 1.001)
            val i = fi.toInt(); val j = fj.toInt(); val a = fi - i; val b = fj - j
            fun z(ii: Int, jj: Int) = z[ii * m + jj]
            return z(i, j) * (1 - a) * (1 - b) + z(i + 1, j) * a * (1 - b) + z(i, j + 1) * (1 - a) * b + z(i + 1, j + 1) * a * b
        }
    }

    private enum class K { EASY, LONG, TEMPO, INTERVALS, RECOVERY, RACE, MILL }

    suspend fun seedIfEmpty(ctx: Context, c: AppContainer, today: LocalDate = LocalDate.now()) {
        if (c.dao.count() > 0) return
        val ru = ctx.ru
        val am = ctx.assets
        // tiles into the cache, so the map and the terrain work offline
        val dir = File(ctx.filesDir, "tiles").apply { mkdirs() }
        for (f in am.list("demo/tiles").orEmpty()) File(dir, f).takeIf { !it.exists() }?.let { out -> am.open("demo/tiles/$f").use { i -> out.outputStream().use { i.copyTo(it) } } }
        val routes = am.open("demo/routes.txt").bufferedReader().readLines().filter { it.isNotBlank() }.map { Geo.decode(it) }
        val g = JSONObject(am.open("demo/elev.json").bufferedReader().readText()).let { o ->
            val z = o.getJSONArray("z"); Grid(o.getDouble("lat0"), o.getDouble("lon0"), o.getDouble("dlat"), o.getDouble("dlon"), o.getInt("n"), o.getInt("m"), DoubleArray(z.length()) { z.getDouble(it) })
        }
        val zone = ZoneId.systemDefault()
        val rnd = Random(7)
        val dao = c.dao

        val anya = dao.insert(Friend(name = if (ru) "Аня" else "Anya", addedAt = 0))
        val misha = dao.insert(Friend(name = if (ru) "Миша" else "Misha", addedAt = 0))
        val dog = dao.insert(Friend(name = if (ru) "Бублик" else "Biscuit", dog = true, addedAt = 0))
        val start0 = today.minusDays(400)
        val oldPair = dao.insert(Shoe(name = "Novablast 3", color = 4, addedAt = start0.atStartOfDay(zone).toInstant().toEpochMilli(), baseKm = 120.0,
            retiredAt = today.minusDays(150).atStartOfDay(zone).toInstant().toEpochMilli()))
        val daily = dao.insert(Shoe(name = "Clifton 9", color = 1, addedAt = today.minusDays(152).atStartOfDay(zone).toInstant().toEpochMilli()))
        val fast = dao.insert(Shoe(name = "Endorphin Speed", color = 3, addedAt = today.minusDays(300).atStartOfDay(zone).toInstant().toEpochMilli()))

        val artists = listOf(
            "Kino" to listOf("Gruppa krovi", "Zvezda po imeni Solntse", "Peremen"), "Daft Punk" to listOf("Harder, Better, Faster, Stronger", "One More Time", "Around the World"),
            "The Prodigy" to listOf("Firestarter", "Breathe", "Omen"), "Florence + The Machine" to listOf("Dog Days Are Over", "Shake It Out", "Spectrum"),
            "Muse" to listOf("Uprising", "Knights of Cydonia", "Hysteria"), "Zemfira" to listOf("Iskala", "Khochesh?", "Pripevochka"),
        )
        var routeI = 0
        fun nextRoute(km: Double): List<Pair<Double, Double>> {
            val pick = routes.drop(routeI % routes.size).plus(routes).take(40).minByOrNull { r -> kotlin.math.abs(length(r) / 1000 - km) } ?: routes[0]
            routeI += 7
            return pick
        }

        var d = start0
        var week = 0
        val made = ArrayList<Triple<Long, K, LocalDate>>()
        while (!d.isAfter(today.minusDays(1))) {
            val dow = d.dayOfWeek.value
            val progress = (d.toEpochDay() - start0.toEpochDay()) / 400.0
            val winter = d.monthValue in listOf(12, 1, 2)
            val plan: K? = when {
                d.monthValue == 1 && d.dayOfMonth == 1 -> K.EASY
                d.monthValue == 10 && d.dayOfMonth == 31 -> K.EASY
                d.monthValue == 6 && d.dayOfMonth == 22 -> K.EASY
                d == today.minusDays(180) -> K.RACE
                d == today.minusDays(60) -> K.RACE
                d == today.minusDays(300) -> K.RACE
                dow == 2 -> if (rnd.nextFloat() < .85f) (if (winter && rnd.nextFloat() < .5f) K.MILL else K.INTERVALS) else null
                dow == 4 -> if (rnd.nextFloat() < .8f) (if (week % 2 == 0) K.TEMPO else K.EASY) else null
                dow == 6 -> if (rnd.nextFloat() < .9f) K.LONG else null
                dow == 1 || dow == 5 -> if (rnd.nextFloat() < .45f) (if (rnd.nextFloat() < .5f) K.RECOVERY else K.EASY) else null
                dow == 7 -> if (rnd.nextFloat() < .35f) K.EASY else null
                else -> null
            }
            if (dow == 7) week++
            if (plan == null || (rnd.nextFloat() < .06f && plan != K.RACE)) { d = d.plusDays(1); continue }
            // the hour: mornings and evenings, dawn now and then, night in winter comes by itself
            val hour = when {
                d.monthValue == 1 && d.dayOfMonth == 1 -> 11.0
                d.monthValue == 10 && d.dayOfMonth == 31 -> 19.5
                d.monthValue == 6 && d.dayOfMonth == 22 -> 21.4
                plan == K.RACE -> 9.0
                plan == K.LONG -> 8.0 + rnd.nextDouble() * 1.5
                rnd.nextFloat() < .08f -> 23.2
                rnd.nextFloat() < .4f -> 6.0 + rnd.nextDouble() * 1.4
                else -> 18.3 + rnd.nextDouble() * 2.4
            }
            val start = d.atStartOfDay(zone).toInstant().toEpochMilli() + (hour * 3_600_000).toLong()
            val easy = 372 - 38 * progress // s/km, getting lighter through the year
            val (km, pace) = when (plan) {
                K.EASY -> (5.5 + rnd.nextDouble() * 3) to easy
                K.RECOVERY -> (4.0 + rnd.nextDouble() * 1.2) to easy + 25
                K.LONG -> (if (progress > .6 && rnd.nextFloat() < .25f) 24.0 + rnd.nextDouble() * 7 else 13.0 + rnd.nextDouble() * 6) to easy + 8
                K.TEMPO -> (8.0 + rnd.nextDouble() * 2) to easy - 62
                K.INTERVALS -> (7.0 + rnd.nextDouble() * 2) to easy - 20
                K.RACE -> (if (d == today.minusDays(180)) 21.1 else if (d == today.minusDays(60)) 10.0 else 5.0) to easy - 80
                K.MILL -> 7.0 to easy - 15
            }
            val p = if (plan == K.MILL) mill(start, km, pace, rnd) else outdoor(nextRoute(km), start, pace, plan, g, rnd, progress)
            val (_, id) = c.importer.put(p)
            if (id > 0) made += Triple(id, plan, d)
            d = d.plusDays(1)
        }

        for ((i, m) in made.withIndex()) {
            val (id, plan, date) = m
            val r = dao.run(id) ?: continue
            val wr = Random(i * 31 + 5)
            val month = date.monthValue
            val temp = 9 - 11 * cos((date.dayOfYear - 20) / 365.0 * 2 * PI) + wr.nextDouble(-4.0, 4.0) - (if (Random(i).nextBoolean()) 0.0 else 2.0)
            val wet = wr.nextFloat() < .24f
            val code = when {
                wet && temp < 0.5 -> 73
                wet && wr.nextFloat() < .08f && month in 5..8 -> 95
                wet -> 61
                month in listOf(10, 11, 3) && wr.nextFloat() < .12f -> 45
                else -> if (wr.nextFloat() < .5f) 2 else 0
            }
            val wind = if (wr.nextFloat() < .1f) 32.0 + wr.nextDouble() * 15 else 6 + wr.nextDouble() * 16
            val (water, green) = Terrain.of(ctx, r.line) ?: (0f to 0f)
            dao.update(r.copy(
                weatherDone = true, wCode = if (r.treadmill) null else code, tempC = temp, precipMm = if (wet && code != 73) 0.6 + wr.nextDouble() * 2 else 0.0,
                snowCm = if (code == 73) 0.4 else 0.0, windKmh = wind, terrainDone = true, nearWater = water, inGreen = green,
                shoeId = when { plan == K.RACE || plan == K.TEMPO -> fast; r.start < today.minusDays(150).atStartOfDay(zone).toInstant().toEpochMilli() -> oldPair; else -> daily },
                race = plan == K.RACE, title = if (plan == K.RACE) (if (r.km > 20) (if (ru) "Пражский полумарафон" else "Prague Half") else if (r.km > 8) (if (ru) "Десятка у реки" else "River 10K") else (if (ru) "Паркран Стромовка" else "parkrun Stromovka")) else "",
                feel = if (plan == K.RECOVERY) 1 else if (plan == K.RACE || plan == K.TEMPO) 3 else 0,
            ))
            if (wr.nextFloat() < .26f || plan == K.RACE) dao.link(RunCompanion(id, anya))
            if (wr.nextFloat() < .1f) { dao.link(RunCompanion(id, misha)); dao.link(RunCompanion(id, anya)) }
            if ((plan == K.EASY || plan == K.RECOVERY) && wr.nextFloat() < .3f) dao.link(RunCompanion(id, dog))
            if (wr.nextFloat() < .6f && plan != K.RACE) {
                var t = r.start; var k = wr.nextInt(artists.size)
                while (t < r.end) {
                    val (a, songs) = artists[if (wr.nextFloat() < .7f) k else wr.nextInt(artists.size).also { k = it }]
                    val len = 180_000L + wr.nextLong(120_000)
                    dao.insert(Play(at = t, until = t + len, artist = a, title = songs[wr.nextInt(songs.size)], app = "demo"))
                    t += len
                }
            }
        }
        // the knee after the longest run
        made.mapNotNull { dao.run(it.first) }.maxByOrNull { it.distanceM }?.let { dao.insert(Wound(runId = it.id, at = it.end, part = "knee")) }
        // mornings from the watch
        for (k in 0 until 120) {
            val day = today.minusDays(k.toLong())
            dao.upsert(Day(day.toString(), restHr = 54 - (k < 60).let { if (it) 2 else 0 } + Random(k).nextInt(-2, 3), hrv = 48.0 + Random(k + 9).nextInt(-8, 9)))
        }
        val dayMs = 86_400_000L
        val now = today.atStartOfDay(zone).toInstant().toEpochMilli()
        dao.insert(Quest(key = "tract", startedAt = now - 130 * dayMs, endsAt = now - 101 * dayMs))
        dao.insert(Quest(key = "night", startedAt = now - 9 * dayMs, endsAt = now + 11 * dayMs))
        c.settings.update { s ->
            s.copy(hidden = listOf(Hidden(HOME.first, HOME.second, 300)), music = true, defaultShoe = daily, maxHr = 188, restHr = 0)
        }
        val w = c.world()
        val met = w.met
        c.settings.update { it.copy(dismissed = met.dropLast(2).map { h -> "met:${h.c.key}" }.toSet()) }
    }

    private fun length(r: List<Pair<Double, Double>>) = r.zipWithNext().sumOf { (a, b) -> Geo.dist(a.first, a.second, b.first, b.second) }

    /** Walks [route] at [pace] s/km with a sample every ~4 s; intervals and tempo shape speed, heart rate follows. */
    private fun outdoor(route: List<Pair<Double, Double>>, start: Long, pace: Double, k: K, g: Grid, rnd: Random, progress: Double): Parsed {
        val pts = ArrayList<Pt>()
        val rest = 54.0; val max = 188.0
        var t = 0.0; var hr = 70.0; var dist = 0.0
        val segs = route.zipWithNext()
        val total = length(route)
        for ((a, b) in segs) {
            val len = Geo.dist(a.first, a.second, b.first, b.second)
            var s = 0.0
            while (s < len) {
                val frac = dist / total
                val climb = (g.at(b.first, b.second) - g.at(a.first, a.second)) / len.coerceAtLeast(1.0)
                val surge = k == K.INTERVALS && frac in .2..0.85 && ((t / 60).toInt() % 4 < 1.5)
                val speed = (1000 / pace) * (if (surge) 1.28 else if (k == K.INTERVALS) .88 else 1.0) * (1 - climb * 4).coerceIn(.7, 1.15) * (1 + rnd.nextDouble(-.03, .03))
                val dt = 4.0
                val step = min(speed * dt, len - s)
                s += step; dist += step; t += step / speed
                val la = a.first + (b.first - a.first) * (s / len); val lo = a.second + (b.second - a.second) * (s / len)
                // heart rate: a share of the reserve by effort, drifting up through long runs, lagging behind changes
                val effort = ((speed * 3.6 - 6.5) / 9.0).coerceIn(0.2, 1.05) + climb * 3 + frac * (if (k == K.LONG) .08 else .03) - progress * .04
                val target = rest + (max - rest) * (.33 + .56 * effort).coerceIn(.35, .99)
                hr += (target - hr) * (1 - exp(-dt / 25))
                pts += Pt(start + (t * 1000).toLong(), la + rnd.nextDouble(-.00002, .00002), lo + rnd.nextDouble(-.00002, .00002), g.at(la, lo) + rnd.nextDouble(-.4, .4), hr.roundToInt())
            }
        }
        return Parsed(start = start, points = pts, source = "demo", sourceId = "demo:${start / 1000}")
    }

    private fun mill(start: Long, km: Double, pace: Double, rnd: Random): Parsed {
        val pts = ArrayList<Pt>()
        var t = 0.0; var d = 0.0; var hr = 70.0
        while (d < km * 1000) {
            val surge = (t / 60).toInt() % 4 == 1 && d > 1500
            val speed = 1000 / pace * (if (surge) 1.25 else .9)
            t += 4; d += speed * 4
            hr += (54 + 134 * (if (surge) .9 else .7) - hr) * (1 - exp(-4.0 / 25))
            pts += Pt(start + (t * 1000).toLong(), hr = (hr + rnd.nextDouble(-1.0, 1.0)).roundToInt(), dist = d)
        }
        return Parsed(start = start, points = pts, source = "demo", sourceId = "demo:${start / 1000}", treadmill = true)
    }

}
