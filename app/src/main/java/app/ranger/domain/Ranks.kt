package app.ranger.domain

/** Ranks of the Border Watch, by valour (training load summed over all patrols). */
object Ranks {
    class Rank(val at: Int, val name: L)

    val all = listOf(
        Rank(0, L("Новобранец", "Recruit")),
        Rank(1_500, L("Дозорный", "Watchman")),
        Rank(4_000, L("Следопыт", "Ranger")),
        Rank(8_000, L("Ловчий", "Huntsman")),
        Rank(14_000, L("Егерь", "Warden")),
        Rank(22_000, L("Старший егерь", "Senior warden")),
        Rank(32_000, L("Хранитель тропы", "Keeper of the trail")),
        Rank(45_000, L("Страж Рубежа", "Guardian of the Border")),
        Rank(62_000, L("Воевода Рубежа", "Marshal of the Border")),
        Rank(85_000, L("Легенда Пограничья", "Legend of the Borderlands")),
    )

    fun index(xp: Int) = all.indexOfLast { xp >= it.at }.coerceAtLeast(0)
}

/** The ranger's abilities, each from one kind of real effort, levels 0–10. */
enum class Skill(val title: L, val unit: L, val steps: IntArray, val about: L) {
    ENDURANCE(L("Выносливость", "Endurance"), L("км за раз", "km in one go"), intArrayOf(3, 5, 8, 10, 12, 15, 18, 21, 26, 32),
        L("По самой длинной пробежке.", "From the longest run.")),
    SPEED(L("Быстрота", "Speed"), L("мин/км на 5 км", "min/km over 5 km"), intArrayOf(450, 420, 390, 360, 340, 320, 300, 285, 270, 250),
        L("По лучшим пяти километрам.", "From the best five kilometres.")),
    NIGHT_EYE(L("Ночное зрение", "Night eye"), L("ночных дозоров", "night patrols"), intArrayOf(1, 3, 6, 10, 15, 25, 40, 60, 85, 120),
        L("Сколько раз вы бегали в темноте.", "How many times you ran in the dark.")),
    MOUNTAIN(L("Горная тропа", "Mountain path"), L("м набора всего", "m climbed in all"), intArrayOf(100, 300, 600, 1000, 2000, 3500, 5000, 7500, 10000, 15000),
        L("Весь набор высоты.", "All the climbing.")),
    GRIT(L("Стойкость", "Grit"), L("дозоров в непогоду", "patrols in foul weather"), intArrayOf(1, 3, 6, 10, 15, 25, 40, 60, 85, 120),
        L("Дождь, снег, туман, мороз, жара и ветер.", "Rain, snow, fog, frost, heat and wind.")),
    QUIET(L("Тихий шаг", "Quiet step"), L("лёгких дозоров", "easy patrols"), intArrayOf(1, 3, 6, 10, 15, 25, 40, 60, 85, 120),
        L("Пробежки в лёгких зонах, когда можно разговаривать.", "Runs in the easy zones, slow enough to talk.")),
    SCOUTING(L("Чутьё следа", "Tracker's sense"), L("разведанных участков", "squares scouted"), intArrayOf(10, 25, 50, 100, 175, 275, 400, 550, 750, 1000),
        L("Сколько участков карты (250 × 250 м) вы пробежали.", "How many map squares (250 × 250 m) you've run through.")),
    CONSTANCY(L("Постоянство", "Constancy"), L("недель подряд", "weeks in a row"), intArrayOf(2, 3, 4, 6, 8, 12, 16, 24, 36, 52),
        L("Самая длинная серия недель хотя бы с одним дозором.", "The longest run of weeks with at least one patrol.")),
    ;

    /** Speed counts down (lower pace is better), the rest up. */
    fun level(v: Int): Int = if (this == SPEED) (if (v <= 0) 0 else steps.count { v <= it }) else steps.count { v >= it }
}
