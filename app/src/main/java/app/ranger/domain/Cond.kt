package app.ranger.domain

/** A phrase in both languages. */
data class L(val ru: String, val en: String) {
    fun of(ru: Boolean) = if (ru) this.ru else en
}

/** What a patrol was like: the facts the bestiary and the campaigns are written against. */
enum class Cond(val ru: String, val en: String) {
    RAIN("дождь", "rain"), SNOW("снег", "snow"), FOG("туман", "fog"), STORM("гроза", "thunder"),
    FROST("мороз", "frost"), HEAT("жара", "heat"), WIND("ветер", "wind"),
    NIGHT("ночь", "night"), DAWN("рассвет", "dawn"), DUSK("закат", "dusk"), MIDNIGHT("полночь", "midnight"), EARLY("до петухов", "before cockcrow"),
    FULL_MOON("полнолуние", "full moon"),
    WATER("у воды", "by water"), GREEN("лесом", "through woods"), HILLS("в гору", "uphill"),
    LONG("долгий", "long"), VERY_LONG("очень долгий", "very long"),
    INTERVALS("рывки", "surges"), EASY("тихий шаг", "quiet pace"), TEMPO("гон", "steady push"),
    TREADMILL("дорожка", "treadmill"), UNDERGROUND("без карты", "no map"), RACE("турнир", "tournament"),
    TOGETHER("вдвоём", "in company"), PACK("отрядом", "as a band"), DOG("с собакой", "with a dog"),
    FAR("вдали от дома", "far from home"), NEW_GROUND("новые тропы", "new ground"),
    FIRST_SNOW("первый снег", "first snow"), NEW_YEAR("первое января", "New Year's Day"), MIDSUMMER("Купальская ночь", "Midsummer"), SAMHAIN("канун Дня всех святых", "Hallowe'en");

    fun of(ru: Boolean) = if (ru) this.ru else en

    val bad get() = this in BAD

    companion object {
        val BAD = setOf(RAIN, SNOW, FOG, STORM, FROST, HEAT, WIND)
        /** Shown on a patrol's card in this order. */
        val SHOWN = listOf(RACE, FULL_MOON, NIGHT, MIDNIGHT, DAWN, DUSK, EARLY, STORM, RAIN, SNOW, FIRST_SNOW, FOG, FROST, HEAT, WIND,
            WATER, GREEN, HILLS, VERY_LONG, LONG, INTERVALS, TEMPO, EASY, TREADMILL, UNDERGROUND, PACK, TOGETHER, DOG, FAR, NEW_GROUND,
            NEW_YEAR, MIDSUMMER, SAMHAIN)
    }
}

/** The main sort of a patrol, as a coach would name it. */
enum class Kind(val ru: String, val en: String) {
    EASY("лёгкий", "easy"), LONG("длинный", "long"), TEMPO("темповый", "tempo"), INTERVALS("интервалы", "intervals"),
    HILLS("горный", "hills"), RACE("старт", "race"), RECOVERY("восстановительный", "recovery"), TREADMILL("дорожка", "treadmill"), RUN("пробежка", "run");

    fun of(ru: Boolean) = if (ru) this.ru else en
}
