package app.ranger.domain

/** A campaign: a goal over a few weeks, taken on from the outpost's board. */
class Campaign(
    val key: String,
    val name: L,
    val about: L,
    val days: Int,
    val target: Int,
    val unit: L,
    /** What one patrol adds towards the goal. */
    val gives: (Patrol) -> Int,
)

object Campaigns {
    val all = listOf(
        Campaign("tract", L("Старый тракт", "The Old Road"), L("Пройти старым трактом до соседней заставы: восемьдесят километров за месяц.", "Follow the old road to the next outpost: eighty kilometres in a month."),
            30, 80, L("км", "km")) { (it.run.distanceM / 1000).toInt() },
        Campaign("pass", L("Горный перевал", "The High Pass"), L("Подняться к перевалу: тысяча метров набора за месяц.", "Climb to the pass: a thousand metres up in a month."),
            30, 1000, L("м набора", "m climbed")) { it.run.ascentM.toInt() },
        Campaign("rounds", L("Обход владений", "Beating the Bounds"), L("Обойти все свои тропы: двенадцать дозоров за четыре недели.", "Walk all your trails: twelve patrols in four weeks."),
            28, 12, L("дозоров", "patrols")) { 1 },
        Campaign("quiet", L("Тихий шаг", "The Quiet Step"), L("Десять лёгких дозоров за четыре недели, без спешки и пыхтения.", "Ten easy patrols in four weeks, no rush, no panting."),
            28, 10, L("лёгких", "easy")) { if (Cond.EASY in it.conds) 1 else 0 },
        Campaign("night", L("Ночной дозор", "The Night Watch"), L("Шесть раз выйти в темноте за три недели.", "Go out in the dark six times in three weeks."),
            21, 6, L("ночных", "at night")) { if (Cond.NIGHT in it.conds) 1 else 0 },
        Campaign("dawn", L("Встреча зари", "Greeting the Dawn"), L("Пять раз встретить рассвет за три недели.", "Meet the sunrise five times in three weeks."),
            21, 5, L("рассветов", "sunrises")) { if (Cond.DAWN in it.conds || Cond.EARLY in it.conds) 1 else 0 },
        Campaign("weather", L("Непогода", "Foul Weather"), L("Пять дозоров в дождь, снег, туман, мороз, жару или ветер за месяц.", "Five patrols in rain, snow, fog, frost, heat or wind in a month."),
            30, 5, L("в непогоду", "in foul weather")) { if (it.conds.any { c -> c.bad }) 1 else 0 },
        Campaign("long", L("Дальняя дорога", "The Long Road"), L("Три длинных дозора за четыре недели.", "Three long patrols in four weeks."),
            28, 3, L("длинных", "long")) { if (Cond.LONG in it.conds) 1 else 0 },
        Campaign("surge", L("Погоня", "The Chase"), L("Четыре тренировки с рывками за четыре недели.", "Four sessions with surges in four weeks."),
            28, 4, L("с рывками", "with surges")) { if (Cond.INTERVALS in it.conds) 1 else 0 },
        Campaign("scout", L("Разведка", "Scouting"), L("Разведать тридцать новых участков карты за месяц.", "Scout thirty new map squares in a month."),
            30, 30, L("участков", "squares")) { it.newCells },
        Campaign("band", L("Отряд", "The Band"), L("Четыре дозора с кем-нибудь за месяц.", "Four patrols with someone in a month."),
            30, 4, L("вместе", "together")) { if (it.company.isNotEmpty()) 1 else 0 },
    )
    val byKey = all.associateBy { it.key }
}
