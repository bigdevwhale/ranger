package app.ranger.domain

import app.ranger.domain.Cond.*

/** Marks drawn on a creature's seal. */
enum class Glyph { WAVE, TREE, CRESCENT, SUNRISE, SUNSET, PEAK, DROPS, MIST, FLAKE, FLAME, GUST, BOLT, ROAD, FEATHER, ARROW, ANVIL, BANNER, PAIR, BAND, PAW, STAR, COMPASS, FULL, CROWN, FERN, HORN, TUSK, EYE }

/**
 * Someone who lives in the Borderlands. A creature shows itself after [need] patrols that meet its [test]:
 * the first leaves tracks, the next ones lead to the lair, the last one is the meeting.
 */
class Creature(
    val key: String,
    val name: L,
    /** How it is found: the condition in plain words. */
    val hint: L,
    /** What the ranger's journal says once met. */
    val lore: L,
    val need: Int,
    /** 1 common, 2 rare, 3 legendary. */
    val tier: Int,
    val glyphs: List<Glyph>,
    /** Ink of the seal, ARGB. */
    val ink: Long,
    val test: (Set<Cond>) -> Boolean,
)

object Bestiary {
    private fun c(key: String, ru: String, en: String, hintRu: String, hintEn: String, loreRu: String, loreEn: String, need: Int, tier: Int, ink: Long, vararg g: Glyph, test: (Set<Cond>) -> Boolean) =
        Creature(key, L(ru, en), L(hintRu, hintEn), L(loreRu, loreEn), need, tier, g.toList(), ink, test)

    private const val RIVER = 0xFF2F5D7C
    private const val WOOD = 0xFF3D5E34
    private const val NIGHTINK = 0xFF2E3157
    private const val SUN = 0xFFA8692A
    private const val STONE = 0xFF5D5247
    private const val BLOOD = 0xFF8A2F2A
    private const val ICE = 0xFF4F7A8C
    private const val GOLD = 0xFF9A7A22
    private const val PLUM = 0xFF5E3A5E

    val all: List<Creature> = listOf(
        c("nixie", "Водяница", "Nixie", "Бегите вдоль воды: реки, пруда, набережной.", "Run along water: a river, a pond, an embankment.",
            "Сидит на мостках и пересчитывает тех, кто пробегает мимо. Вас она уже запомнила.", "Sits on the jetty counting the runners who pass. She knows you by now.",
            3, 1, RIVER, Glyph.WAVE) { WATER in it },
        c("woodwose", "Лесовик", "Woodwose", "Бегите парком или лесом, а не вдоль дороги.", "Run through a park or a wood, not along the road.",
            "Шуршит в кустах на полшага позади. Если обернуться, там только шишка на тропе.", "Rustles in the bushes half a step behind. Turn round and there's only a pine cone on the path.",
            3, 1, WOOD, Glyph.TREE) { GREEN in it },
        c("lynx", "Лунная рысь", "Moon lynx", "Бегите после заката, в темноте.", "Run after sunset, in the dark.",
            "Её глаза горят в темноте, пока вы бежите. Рысь идёт следом, но не нападает: ей просто интересно.", "Her eyes shine in the dark while you run. She follows but never pounces: she's curious, that's all.",
            3, 1, NIGHTINK, Glyph.CRESCENT, Glyph.EYE) { NIGHT in it },
        c("stag", "Златорогий олень", "Gold-horned stag", "Выйдите на рассвете, когда встаёт солнце.", "Go out at sunrise.",
            "Появляется на опушке с первым лучом и исчезает, когда солнце поднимется на ладонь.", "Stands at the edge of the wood with the first ray and is gone when the sun is a hand high.",
            3, 1, SUN, Glyph.SUNRISE) { DAWN in it },
        c("owl", "Сумеречная сова", "Dusk owl", "Бегите на закате.", "Run at sunset.",
            "Пролетает над тропой ровно тогда, когда гаснет последний свет. Ни разу не опоздала.", "Crosses the path just as the last light goes. She has never been late.",
            3, 1, PLUM, Glyph.SUNSET) { DUSK in it },
        c("troll", "Горный тролль", "Hill troll", "Наберите высоту: холмы, лестницы, мосты.", "Climb: hills, stairs, bridges.",
            "Живёт там, где тропа задирается вверх. Пропускает только тех, кто не перешёл на шаг.", "Lives where the path tilts up. Lets past only those who didn't drop to a walk.",
            3, 1, STONE, Glyph.PEAK) { HILLS in it },
        c("toad", "Ливневая жаба", "Rain toad", "Бегите под дождём.", "Run in the rain.",
            "Выходит, когда все разбежались по домам. Считает, что мокрый бегун — лучший собеседник.", "Comes out when everyone else has run indoors. Thinks a wet runner is the best company.",
            3, 1, RIVER, Glyph.DROPS) { RAIN in it },
        c("mistling", "Туманник", "Mistling", "Бегите в туман.", "Run in fog.",
            "Сплетён из тумана. Если остановиться, он растает, так что не останавливайтесь.", "Woven out of fog. Stop and he melts, so don't stop.",
            2, 1, 0xFF6B7178, Glyph.MIST) { FOG in it },
        c("wolf", "Инистый волк", "Rime wolf", "Бегите в мороз от минус пяти.", "Run at minus five or colder.",
            "Оставляет на тропе следы из инея. Утром их видно, к обеду они тают.", "Leaves pawprints of rime on the trail. You see them in the morning; by noon they're gone.",
            2, 1, ICE, Glyph.FLAKE, Glyph.PAW) { FROST in it },
        c("wisp", "Метелица", "Snow wisp", "Бегите, когда идёт снег.", "Run while it snows.",
            "Кружится вокруг и заметает следы. Ей нравится, когда никто не знает, где вы пробежали.", "Whirls round you and covers your tracks. She likes it when no one can tell where you ran.",
            2, 1, ICE, Glyph.FLAKE) { SNOW in it },
        c("salamander", "Саламандра", "Salamander", "Бегите в жару от двадцати пяти.", "Run when it's 25° or hotter.",
            "Греется на раскалённых камнях и смотрит на вас с уважением. И с лёгким сомнением.", "Basks on the hot stones and watches you with respect. And mild doubt.",
            2, 1, BLOOD, Glyph.FLAME) { HEAT in it },
        c("petrel", "Буревестник", "Storm petrel", "Бегите в сильный ветер.", "Run into a strong wind.",
            "Ловит ветер, от которого вы уворачиваетесь, и смеётся вам в спину.", "Rides the wind you are leaning into and laughs at your back.",
            2, 1, 0xFF46606E, Glyph.GUST) { WIND in it },
        c("hare", "Ветрогон", "Windchaser", "Сделайте рывки: несколько быстрых отрезков с отдыхом.", "Do surges: several fast stretches with rest between.",
            "Его не догнать ровным бегом. Только рывком, и ещё одним, и ещё.", "You'll never catch him at a steady pace. Only with a burst, and another, and another.",
            3, 1, SUN, Glyph.BOLT, Glyph.GUST) { INTERVALS in it },
        c("giant", "Странствующий великан", "Wandering giant", "Пробегите длинную дистанцию, дольше обычного.", "Go long, longer than usual.",
            "Его шаг — полверсты. Чтобы догнать, нужно идти по следу долго и не сворачивать.", "His stride is half a mile. To catch him you follow the trail for a long time and don't turn off.",
            3, 1, STONE, Glyph.ROAD) { LONG in it },
        c("whisperer", "Шептунья", "Whisperer", "Бегите тихо: так, чтобы можно было разговаривать.", "Run quietly: slow enough to talk.",
            "Показывается только тем, кто бежит не торопясь. Кто пыхтит, тот её даже не услышит.", "Shows herself only to the unhurried. If you're panting, you won't even hear her.",
            5, 1, WOOD, Glyph.FEATHER) { EASY in it },
        c("boar", "Железный вепрь", "Iron boar", "Пробегите темповую: ровно и тяжело, двадцать минут или больше.", "Run tempo: steady and hard, twenty minutes or more.",
            "Прёт напролом и уважает только тех, кто держит его темп.", "Charges straight through and respects only those who hold his pace.",
            3, 1, BLOOD, Glyph.TUSK, Glyph.ARROW) { TEMPO in it },
        c("smith", "Подземный кузнец", "Deep smith", "Бегите на дорожке или там, где нет GPS.", "Run on a treadmill or where there's no GPS.",
            "Работает в подземельях, где карты бесполезны. Под стук ваших шагов куёт подковы.", "Works in the deeps where maps are useless. Shoes horses in time with your steps.",
            3, 1, STONE, Glyph.ANVIL) { UNDERGROUND in it },
        c("griffin", "Грифон", "Griffin", "Выйдите на настоящий старт и отметьте его как турнир.", "Run a real race and mark it as a tournament.",
            "Прилетает на каждый турнир и садится на финишную арку. Говорят, он болеет за вас.", "Comes to every tournament and perches on the finish arch. They say he roots for you.",
            1, 2, GOLD, Glyph.BANNER) { RACE in it },
        c("raven", "Двуглавый ворон", "Two-headed raven", "Бегите с кем-нибудь вдвоём.", "Run with someone.",
            "Одна голова слушает вас, другая того, кто рядом. Обе запоминают всё.", "One head listens to you, the other to your companion. Both remember everything.",
            3, 1, NIGHTINK, Glyph.PAIR) { TOGETHER in it },
        c("centaurs", "Табун кентавров", "Centaur herd", "Бегите отрядом: втроём или больше.", "Run as a band: three or more of you.",
            "Скачут рядом, пока отряд держится вместе. Если кто-то отстанет, они ждут.", "Gallop alongside while the band keeps together. If someone drops back, they wait.",
            1, 2, SUN, Glyph.BAND) { PACK in it },
        c("hound", "Гончая Рубежа", "Border hound", "Бегите со своей собакой.", "Run with your dog.",
            "Ваша собака её видит, а вы нет. Поэтому собака иногда лает в пустоту.", "Your dog sees her; you don't. That's why your dog sometimes barks at nothing.",
            3, 1, STONE, Glyph.PAW) { DOG in it },
        c("lark", "Жаворонок-вестник", "Herald lark", "Выйдите до половины седьмого утра.", "Go out before half past six in the morning.",
            "Разносит вести по Пограничью раньше всех. Теперь в его вестях есть и вы.", "Carries the news round the Borderlands before anyone else is up. Now the news includes you.",
            3, 1, SUN, Glyph.STAR) { EARLY in it },
        c("wraith", "Полуночница", "Midnight wraith", "Выйдите около полуночи, с одиннадцати вечера до трёх ночи.", "Go out round midnight, between eleven and three.",
            "Бродит по пустым улицам и удивляется, что вы тоже не спите.", "Wanders the empty streets and wonders why you're not asleep either.",
            1, 2, PLUM, Glyph.CRESCENT, Glyph.STAR) { MIDNIGHT in it },
        c("wyrm", "Перелётный змей", "Wandering wyrm", "Пробегите в другом городе, дальше пятидесяти километров от дома.", "Run in another town, over fifty kilometres from home.",
            "Летит туда, куда летите вы. Спит в чужих городах на крышах вокзалов.", "Flies wherever you travel. Sleeps on station roofs in strange towns.",
            1, 2, BLOOD, Glyph.COMPASS) { FAR in it },
        c("magpie", "Сорока-картограф", "Map magpie", "Бегите там, где ещё не бывали: половина пути по новым улицам.", "Run where you've never been: half the way on new streets.",
            "Ворует блестящее и рисует карты. Ваши новые тропы она уже перенесла на пергамент.", "Steals shiny things and draws maps. She has already put your new paths on parchment.",
            3, 1, NIGHTINK, Glyph.COMPASS, Glyph.FEATHER) { NEW_GROUND in it },
        c("thunderbird", "Громовая птица", "Thunderbird", "Бегите в грозу (осторожно).", "Run in a thunderstorm (carefully).",
            "Бьёт крыльями, и гремит гром. Таких храбрецов, как вы, она встречает редко.", "Thunder is her wingbeat. She rarely meets anyone as bold as you.",
            1, 2, 0xFF4A4F7A, Glyph.BOLT) { STORM in it },
        c("werewolf", "Оборотень", "Werewolf", "Бегите ночью в полнолуние.", "Run at night under a full moon.",
            "Раз в месяц он бегает вместе с вами. Остальное время это обычный сосед с собакой.", "Once a month he runs with you. The rest of the time he's an ordinary neighbour with a dog.",
            1, 2, NIGHTINK, Glyph.FULL, Glyph.PAW) { FULL_MOON in it },
        c("wisp_light", "Болотный огонёк", "Will-o'-the-wisp", "Бегите ночью под дождём.", "Run at night in the rain.",
            "Светится впереди и зовёт за собой. Вы не свернули с тропы, и он остался доволен.", "Glows ahead and beckons. You kept to the path, and he was pleased.",
            1, 2, 0xFF5E7A3A, Glyph.DROPS, Glyph.CRESCENT) { RAIN in it && NIGHT in it },
        c("unicorn", "Ледяной единорог", "Frost unicorn", "Встретьте рассвет в мороз.", "Meet the sunrise in a frost.",
            "Показывается на рассвете в самый холод. Рог у него из инея, и он звенит.", "Appears at dawn in the deepest cold. His horn is made of rime, and it rings.",
            1, 3, ICE, Glyph.FLAKE, Glyph.SUNRISE) { FROST in it && DAWN in it },
        c("kelpie", "Келпи", "Kelpie", "Бегите вдоль воды в туман.", "Run along water in fog.",
            "Водяной конь выходит из реки в туманные утра. Если не садиться ему на спину, он безобиден.", "The water horse leaves the river on foggy mornings. Harmless, as long as you don't climb on.",
            1, 3, RIVER, Glyph.WAVE, Glyph.MIST) { FOG in it && WATER in it },
        c("phoenix", "Феникс", "Phoenix", "Пробегите длинную в жару.", "Go long in the heat.",
            "Сгорает и встаёт из пепла. Как и вы на тридцатом километре.", "Burns down and rises from the ashes. As you do at kilometre thirty.",
            1, 3, BLOOD, Glyph.FLAME, Glyph.ROAD) { HEAT in it && LONG in it },
        c("dragon", "Древний дракон", "Elder dragon", "Пробегите тридцать километров или больше за раз.", "Run thirty kilometres or more in one go.",
            "Спит на краю карты и просыпается только ради тех, кто дошёл до края.", "Sleeps at the edge of the map and wakes only for those who reach it.",
            1, 3, GOLD, Glyph.CROWN, Glyph.ROAD) { VERY_LONG in it },
        c("winterking", "Хозяин зимы", "Winter King", "Пробегите первого января.", "Run on the first of January.",
            "Обходит владения в первый день года. Кого встретит на тропе, тому весь год удача.", "Walks his lands on the first day of the year. Meet him on the trail and luck stays with you all year.",
            1, 3, ICE, Glyph.CROWN, Glyph.FLAKE) { NEW_YEAR in it },
        c("firebird", "Жар-птица", "Firebird", "Бегите в купальскую ночь, с 20 по 24 июня, на закате или в темноте.", "Run on Midsummer night, 20–24 June, at dusk or after.",
            "Одно перо упало прямо на тропу. Вы его подобрали, и теперь оно светится в летописи.", "One feather fell right on the path. You picked it up, and now it glows in your chronicle.",
            1, 3, SUN, Glyph.FERN, Glyph.FLAME) { MIDSUMMER in it },
        c("hunt", "Дикий гон", "Wild Hunt", "Бегите в канун Дня всех святых, после заката.", "Run on Hallowe'en, after dark.",
            "Призрачные всадники проносятся над крышами. Вы бежали так, что они приняли вас за своего.", "Ghostly riders sweep over the rooftops. You ran so well they took you for one of their own.",
            1, 3, PLUM, Glyph.HORN, Glyph.CRESCENT) { SAMHAIN in it },
        c("hind", "Белая лань", "White hind", "Пробегите по первому снегу зимы.", "Run in the season's first snow.",
            "Её видят один раз за зиму, в день первого снега. Следов она не оставляет.", "Seen once a winter, on the day of the first snow. She leaves no tracks.",
            1, 2, ICE, Glyph.FLAKE, Glyph.STAR) { FIRST_SNOW in it },
    )

    val byKey = all.associateBy { it.key }

    /** How far along the hunt is after [n] matching patrols: 0 nothing, 1 tracks, 2 lair, 3 met. */
    fun stage(c: Creature, n: Int): Int = when {
        n >= c.need -> 3
        n <= 0 -> 0
        c.need <= 2 -> 1
        n >= c.need - 1 || n * 2 >= c.need + 1 -> 2
        else -> 1
    }
}
