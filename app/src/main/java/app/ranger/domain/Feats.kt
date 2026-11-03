package app.ranger.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Shelves of the trophy hall, in the order they're shown. */
enum class FeatGroup(val title: L) {
    DISTANCE(L("Дистанции", "Distances")),
    ROAD(L("Пройденный путь", "The road behind")),
    RHYTHM(L("Ритм", "Rhythm")),
    SKY(L("Небо и время", "Sky and hour")),
    WORLD(L("Люди и места", "People and places")),
    LORE(L("Бестиарий", "Bestiary")),
}

/**
 * Where a feat stands: earned on [date] by [by]; until then [now] of [goal] (both 0 for a feat that's a yes or a no).
 */
data class FeatState(val f: Feat, val date: LocalDate?, val by: Patrol?, val now: Double, val goal: Double) {
    val done get() = date != null
    /** 0..1, how close it is; 1 once earned. */
    val share get() = if (done) 1.0 else if (goal <= 0) 0.0 else (now / goal).coerceIn(0.0, .99)
}

/** What the patrols so far add up to, kept while walking through them in order. */
class Tally {
    var n = 0; var km = 0.0; var climb = 0.0
    var longest = 0.0
    var best5 = 0; var best10 = 0
    val conds = HashSet<Cond>()
    val weekKm = HashMap<LocalDate, Double>()
    val monthKm = HashMap<String, Double>()
    var bestWeek = 0.0; var bestMonth = 0.0
    val seasons = HashSet<Int>()
    var met = 0
    var gap = 0L // days since the patrol before this one
    var dayRow = 0 // days in a row with a patrol, ending with this one
    var bestRow = 0
}

private val FOUL = listOf(Cond.RAIN, Cond.SNOW, Cond.FOG, Cond.FROST, Cond.HEAT, Cond.WIND)

/**
 * Feats: one-off deeds, each earned once, by a single patrol or by everything up to it.
 * The rest of the journal measures (ranks, abilities, campaigns); feats remember the moments.
 * [progress] says how far along an unearned one is: (now, goal).
 */
enum class Feat(
    val group: FeatGroup, val glyph: Glyph, val title: L, val about: L,
    val test: (Patrol, Tally) -> Boolean,
    val progress: ((Tally) -> Pair<Double, Double>)? = null,
) {
    FIRST(FeatGroup.DISTANCE, Glyph.STAR, L("Первый дозор", "First patrol"), L("Выйти в первый дозор.", "Go on your first patrol."), { _, _ -> true }),
    FIVE(FeatGroup.DISTANCE, Glyph.ROAD, L("Пятёрка", "Five"), L("Пробежать 5 км за раз.", "Run 5 km in one go."), { p, _ -> p.run.km >= 5 }, { it.longest to 5.0 }),
    TEN(FeatGroup.DISTANCE, Glyph.ROAD, L("Десятка", "Ten"), L("Пробежать 10 км за раз.", "Run 10 km in one go."), { p, _ -> p.run.km >= 10 }, { it.longest to 10.0 }),
    FIFTEEN(FeatGroup.DISTANCE, Glyph.ROAD, L("Пятнашка", "Fifteen"), L("Пробежать 15 км за раз.", "Run 15 km in one go."), { p, _ -> p.run.km >= 15 }, { it.longest to 15.0 }),
    HALF(FeatGroup.DISTANCE, Glyph.BANNER, L("Полумарафон", "Half marathon"), L("Пробежать 21,1 км за раз.", "Run 21.1 km in one go."), { p, _ -> p.run.km >= 21.0 }, { it.longest to 21.1 }),
    THIRTY(FeatGroup.DISTANCE, Glyph.PEAK, L("Тридцатка", "Thirty"), L("Пробежать 30 км за раз.", "Run 30 km in one go."), { p, _ -> p.run.km >= 30 }, { it.longest to 30.0 }),
    MARATHON(FeatGroup.DISTANCE, Glyph.CROWN, L("Марафон", "Marathon"), L("Пробежать 42,2 км за раз.", "Run 42.2 km in one go."), { p, _ -> p.run.km >= 42.0 }, { it.longest to 42.2 }),
    FAST5(FeatGroup.DISTANCE, Glyph.BOLT, L("Быстрая пятёрка", "Quick five"), L("5 км быстрее 30 минут.", "5 km under 30 minutes."), { p, _ -> p.run.bestOf(1) in 1 until 30 * 60 }),
    FASTER5(FeatGroup.DISTANCE, Glyph.BOLT, L("Стремительная пятёрка", "Swift five"), L("5 км быстрее 25 минут.", "5 km under 25 minutes."), { p, _ -> p.run.bestOf(1) in 1 until 25 * 60 }),
    FAST10(FeatGroup.DISTANCE, Glyph.ARROW, L("Быстрая десятка", "Quick ten"), L("10 км быстрее часа.", "10 km under an hour."), { p, _ -> p.run.bestOf(2) in 1 until 60 * 60 }),
    FASTER10(FeatGroup.DISTANCE, Glyph.ARROW, L("Стремительная десятка", "Swift ten"), L("10 км быстрее 50 минут.", "10 km under 50 minutes."), { p, _ -> p.run.bestOf(2) in 1 until 50 * 60 }),

    PATROLS10(FeatGroup.ROAD, Glyph.FEATHER, L("Десять дозоров", "Ten patrols"), L("10 дозоров всего.", "10 patrols in all."), { _, t -> t.n >= 10 }, { it.n.toDouble() to 10.0 }),
    PATROLS50(FeatGroup.ROAD, Glyph.FEATHER, L("Полсотни", "Half a hundred"), L("50 дозоров всего.", "50 patrols in all."), { _, t -> t.n >= 50 }, { it.n.toDouble() to 50.0 }),
    PATROLS100(FeatGroup.ROAD, Glyph.FEATHER, L("Сотня дозоров", "A hundred patrols"), L("100 дозоров всего.", "100 patrols in all."), { _, t -> t.n >= 100 }, { it.n.toDouble() to 100.0 }),
    PATROLS250(FeatGroup.ROAD, Glyph.CROWN, L("Ветеран", "Veteran"), L("250 дозоров всего.", "250 patrols in all."), { _, t -> t.n >= 250 }, { it.n.toDouble() to 250.0 }),
    KM100(FeatGroup.ROAD, Glyph.COMPASS, L("Сто вёрст", "A hundred kilometres"), L("100 км всего.", "100 km in all."), { _, t -> t.km >= 100 }, { it.km to 100.0 }),
    KM500(FeatGroup.ROAD, Glyph.COMPASS, L("Пятьсот вёрст", "Five hundred kilometres"), L("500 км всего.", "500 km in all."), { _, t -> t.km >= 500 }, { it.km to 500.0 }),
    KM1000(FeatGroup.ROAD, Glyph.COMPASS, L("Тысяча вёрст", "A thousand kilometres"), L("1000 км всего.", "1000 km in all."), { _, t -> t.km >= 1000 }, { it.km to 1000.0 }),
    KM2500(FeatGroup.ROAD, Glyph.CROWN, L("Через всё королевство", "Across the realm"), L("2500 км всего.", "2500 km in all."), { _, t -> t.km >= 2500 }, { it.km to 2500.0 }),
    CLIMB1000(FeatGroup.ROAD, Glyph.PEAK, L("Тысяча ступеней", "A thousand steps up"), L("Набрать 1000 м высоты в сумме.", "Climb 1,000 m in all."), { _, t -> t.climb >= 1000 }, { it.climb to 1000.0 }),
    EVEREST(FeatGroup.ROAD, Glyph.PEAK, L("Эверест", "Everest"), L("Набрать 8848 м высоты в сумме.", "Climb 8,848 m in all."), { _, t -> t.climb >= 8848 }, { it.climb to 8848.0 }),

    WEEK30(FeatGroup.RHYTHM, Glyph.SUNRISE, L("Добрая неделя", "A good week"), L("30 км за одну неделю.", "30 km in one week."), { p, t -> (t.weekKm[week(p.date)] ?: 0.0) >= 30 }, { it.bestWeek to 30.0 }),
    WEEK50(FeatGroup.RHYTHM, Glyph.SUNRISE, L("Большая неделя", "A big week"), L("50 км за одну неделю.", "50 km in one week."), { p, t -> (t.weekKm[week(p.date)] ?: 0.0) >= 50 }, { it.bestWeek to 50.0 }),
    MONTH100(FeatGroup.RHYTHM, Glyph.FULL, L("Сотня за месяц", "A hundred in a month"), L("100 км за календарный месяц.", "100 km in a calendar month."), { p, t -> (t.monthKm[p.date.toString().take(7)] ?: 0.0) >= 100 }, { it.bestMonth to 100.0 }),
    MONTH200(FeatGroup.RHYTHM, Glyph.FULL, L("Великий месяц", "A great month"), L("200 км за календарный месяц.", "200 km in a calendar month."), { p, t -> (t.monthKm[p.date.toString().take(7)] ?: 0.0) >= 200 }, { it.bestMonth to 200.0 }),
    ROW3(FeatGroup.RHYTHM, Glyph.PAIR, L("Три дня подряд", "Three days running"), L("Дозоры три дня подряд.", "Patrols three days in a row."), { _, t -> t.dayRow >= 3 }, { it.bestRow.toDouble() to 3.0 }),
    ROW7(FeatGroup.RHYTHM, Glyph.BAND, L("Семь дней подряд", "Seven days running"), L("Дозоры семь дней подряд.", "Patrols seven days in a row."), { _, t -> t.dayRow >= 7 }, { it.bestRow.toDouble() to 7.0 }),
    COMEBACK(FeatGroup.RHYTHM, Glyph.HORN, L("Возвращение", "The return"), L("Выйти в дозор после месяца без них.", "Patrol again after a month without."), { _, t -> t.gap >= 30 }),
    SEASONS(FeatGroup.RHYTHM, Glyph.FERN, L("Четыре сезона", "Four seasons"), L("Дозоры зимой, весной, летом и осенью.", "Patrols in winter, spring, summer and autumn."), { _, t -> t.seasons.size == 4 }, { it.seasons.size.toDouble() to 4.0 }),

    EARLY(FeatGroup.SKY, Glyph.STAR, L("Ранняя пташка", "Early bird"), L("Выйти до петухов.", "Head out before cockcrow."), { p, _ -> Cond.EARLY in p.conds }),
    DAWN(FeatGroup.SKY, Glyph.SUNRISE, L("Встречающий рассвет", "Dawn greeter"), L("Бежать на рассвете.", "Run at sunrise."), { p, _ -> Cond.DAWN in p.conds }),
    DUSK(FeatGroup.SKY, Glyph.SUNSET, L("Провожающий закат", "Sunset chaser"), L("Бежать на закате.", "Run at sunset."), { p, _ -> Cond.DUSK in p.conds }),
    MIDNIGHT(FeatGroup.SKY, Glyph.CRESCENT, L("Полуночник", "Night owl"), L("Бежать в полночь.", "Run at midnight."), { p, _ -> Cond.MIDNIGHT in p.conds }),
    MOON(FeatGroup.SKY, Glyph.FULL, L("Под полной луной", "Under the full moon"), L("Дозор в полнолуние.", "Patrol under a full moon."), { p, _ -> Cond.FULL_MOON in p.conds }),
    RAIN(FeatGroup.SKY, Glyph.DROPS, L("Промокший", "Soaked"), L("Бежать под дождём.", "Run in the rain."), { p, _ -> Cond.RAIN in p.conds }),
    STORM(FeatGroup.SKY, Glyph.BOLT, L("Сквозь грозу", "Through the storm"), L("Бежать в грозу.", "Run in a thunderstorm."), { p, _ -> Cond.STORM in p.conds }),
    FROST(FeatGroup.SKY, Glyph.FLAKE, L("Морозоустойчивый", "Frost-proof"), L("Бежать в мороз.", "Run in a frost."), { p, _ -> Cond.FROST in p.conds }),
    HEAT(FeatGroup.SKY, Glyph.FLAME, L("Пекло", "The furnace"), L("Бежать в жару.", "Run in the heat."), { p, _ -> Cond.HEAT in p.conds }),
    ELEMENTS(FeatGroup.SKY, Glyph.GUST, L("Все стихии", "All the elements"), L("Дождь, снег, туман, мороз, жара и ветер — хотя бы по разу.", "Rain, snow, fog, frost, heat and wind, each at least once."),
        { _, t -> t.conds.containsAll(FOUL) }, { t -> FOUL.count { it in t.conds }.toDouble() to FOUL.size.toDouble() }),
    FIRST_SNOW(FeatGroup.SKY, Glyph.FLAKE, L("Первый снег", "First snow"), L("Бежать по первому снегу.", "Run on the first snow."), { p, _ -> Cond.FIRST_SNOW in p.conds }),
    NEW_YEAR(FeatGroup.SKY, Glyph.CROWN, L("С Новым годом", "Happy New Year"), L("Дозор первого января.", "Patrol on the first of January."), { p, _ -> Cond.NEW_YEAR in p.conds }),
    MIDSUMMER(FeatGroup.SKY, Glyph.FERN, L("Купальская ночь", "Midsummer night"), L("Дозор в Купальскую ночь.", "Patrol on Midsummer."), { p, _ -> Cond.MIDSUMMER in p.conds }),
    SAMHAIN(FeatGroup.SKY, Glyph.HORN, L("Ночь духов", "Night of spirits"), L("Дозор в канун Дня всех святых.", "Patrol on Hallowe'en."), { p, _ -> Cond.SAMHAIN in p.conds }),

    PAIR(FeatGroup.WORLD, Glyph.PAIR, L("Плечом к плечу", "Shoulder to shoulder"), L("Бежать вдвоём.", "Run with someone."), { p, _ -> Cond.TOGETHER in p.conds }),
    BAND(FeatGroup.WORLD, Glyph.BAND, L("Отряд", "The band"), L("Бежать отрядом.", "Run as a band."), { p, _ -> Cond.PACK in p.conds }),
    DOG(FeatGroup.WORLD, Glyph.PAW, L("Верный пёс", "A faithful hound"), L("Бежать с собакой.", "Run with a dog."), { p, _ -> Cond.DOG in p.conds }),
    WATER(FeatGroup.WORLD, Glyph.WAVE, L("У воды", "By the water"), L("Бежать вдоль реки или озера.", "Run along a river or a lake."), { p, _ -> Cond.WATER in p.conds }),
    WOODS(FeatGroup.WORLD, Glyph.TREE, L("Лесной путь", "Woodland way"), L("Бежать парком или лесом.", "Run through a park or a wood."), { p, _ -> Cond.GREEN in p.conds }),
    NEW_GROUND(FeatGroup.WORLD, Glyph.COMPASS, L("Первопроходец", "Pathfinder"), L("Пробежать по новым улицам.", "Run new streets."), { p, _ -> Cond.NEW_GROUND in p.conds }),
    FAR(FeatGroup.WORLD, Glyph.COMPASS, L("Дальние земли", "Far lands"), L("Дозор вдали от дома.", "A patrol far from home."), { p, _ -> Cond.FAR in p.conds }),
    DEEP(FeatGroup.WORLD, Glyph.ANVIL, L("Подземелья", "The deeps"), L("Бежать на дорожке.", "Run on a treadmill."), { p, _ -> Cond.TREADMILL in p.conds || Cond.UNDERGROUND in p.conds }),
    RACE(FeatGroup.WORLD, Glyph.BANNER, L("Турнир", "The tournament"), L("Выйти на старт забега.", "Run a race."), { p, _ -> p.run.race }),

    MET1(FeatGroup.LORE, Glyph.EYE, L("Первая встреча", "First meeting"), L("Встретить существо.", "Meet a creature."), { _, t -> t.met >= 1 }, { it.met.toDouble() to 1.0 }),
    HUNTER(FeatGroup.LORE, Glyph.PAW, L("Охотник за следами", "Tracker"), L("Встретить 10 существ.", "Meet 10 creatures."), { _, t -> t.met >= 10 }, { it.met.toDouble() to 10.0 }),
    HALF_LORE(FeatGroup.LORE, Glyph.EYE, L("Полкниги", "Half the book"), L("Встретить половину бестиария.", "Meet half the bestiary."), { _, t -> t.met * 2 >= Bestiary.all.size }, { it.met.toDouble() to Bestiary.all.size / 2.0 }),
    LOREMASTER(FeatGroup.LORE, Glyph.CROWN, L("Знаток Пограничья", "Loremaster"), L("Встретить всех существ бестиария.", "Meet every creature in the bestiary."), { _, t -> t.met >= Bestiary.all.size }, { it.met.toDouble() to Bestiary.all.size.toDouble() }),
    ;

    companion object {
        private fun week(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** Walk the patrols in order, mark each feat on the patrol that earned it. */
        fun walk(patrols: List<Patrol>): Map<Feat, FeatState> {
            val t = Tally()
            val got = HashMap<Feat, Pair<LocalDate, Patrol>>()
            var last: LocalDate? = null
            for (p in patrols) {
                t.n++; t.km += p.run.km; t.climb += p.run.ascentM
                t.longest = maxOf(t.longest, p.run.km)
                t.conds += p.conds
                val wk = t.weekKm.merge(week(p.date), p.run.km, Double::plus)!!
                val mo = t.monthKm.merge(p.date.toString().take(7), p.run.km, Double::plus)!!
                t.bestWeek = maxOf(t.bestWeek, wk); t.bestMonth = maxOf(t.bestMonth, mo)
                t.seasons += (p.date.monthValue % 12) / 3
                t.met += p.events.count { it is Event.Met }
                t.gap = last?.let { ChronoUnit.DAYS.between(it, p.date) } ?: 0
                t.dayRow = when {
                    last == null -> 1
                    p.date == last -> t.dayRow
                    p.date == last.plusDays(1) -> t.dayRow + 1
                    else -> 1
                }
                t.bestRow = maxOf(t.bestRow, t.dayRow)
                last = p.date
                for (f in entries) if (f !in got && f.test(p, t)) {
                    got[f] = p.date to p
                    p.events += Event.FeatDone(f)
                }
            }
            return entries.associateWith { f ->
                val (now, goal) = f.progress?.invoke(t) ?: (0.0 to 0.0)
                got[f]?.let { (d, p) -> FeatState(f, d, p, goal, goal) } ?: FeatState(f, null, null, now, goal)
            }
        }
    }
}
