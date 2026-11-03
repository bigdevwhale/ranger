package app.ranger.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Numbers and dates the way a person writes them, in either language. */
object Fmt {
    private val monthsRu = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    private val monthsRuNom = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
    private val monthsEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val daysRu = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
    private val daysEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val shortDaysRu = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    val shortDaysEn = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    fun km(m: Double, ru: Boolean, unit: Boolean = true): String {
        val k = m / 1000
        val s = if (k >= 100) "${k.roundToInt()}" else String.format(Locale.US, "%.1f", k).let { if (ru) it.replace('.', ',') else it }
        return if (unit) "$s ${if (ru) "км" else "km"}" else s
    }

    fun int(n: Number, ru: Boolean): String = String.format(Locale.US, "%,d", n.toLong()).replace(",", if (ru) " " else ",")

    /** 5:12 per km. */
    fun pace(sPerKm: Double, ru: Boolean, unit: Boolean = true): String {
        if (sPerKm <= 0 || sPerKm > 3600) return "—"
        val s = sPerKm.roundToInt()
        return "${s / 60}:${"%02d".format(s % 60)}" + if (unit) (if (ru) " /км" else " /km") else ""
    }

    /** 1:02:15 or 48:10. */
    fun clock(s: Int): String = if (s >= 3600) "${s / 3600}:${"%02d".format(s % 3600 / 60)}:${"%02d".format(s % 60)}" else "${s / 60}:${"%02d".format(s % 60)}"

    /** "1 ч 20 мин", "48 мин". */
    fun dur(s: Long, ru: Boolean): String {
        val h = s / 3600; val m = (s % 3600) / 60
        return when {
            h >= 100 -> "$h ${if (ru) "ч" else "h"}"
            h > 0 -> "$h ${if (ru) "ч" else "h"} $m ${if (ru) "мин" else "min"}"
            else -> "$m ${if (ru) "мин" else "min"}"
        }
    }

    fun date(d: LocalDate, ru: Boolean, year: Boolean = d.year != LocalDate.now().year): String =
        if (ru) "${d.dayOfMonth} ${monthsRu[d.monthValue - 1]}" + (if (year) " ${d.year}" else "")
        else "${d.dayOfMonth} ${monthsEn[d.monthValue - 1]}" + (if (year) " ${d.year}" else "")

    fun date(ms: Long, ru: Boolean, zone: ZoneId = ZoneId.systemDefault()) = date(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(), ru)

    fun month(d: LocalDate, ru: Boolean, year: Boolean = true) = (if (ru) monthsRuNom[d.monthValue - 1] else monthsEn[d.monthValue - 1]) + if (year) " ${d.year}" else ""

    private val monthsRuIn = listOf("январе", "феврале", "марте", "апреле", "мае", "июне", "июле", "августе", "сентябре", "октябре", "ноябре", "декабре")

    /** "в марте 2026". */
    fun monthIn(d: LocalDate) = "в ${monthsRuIn[d.monthValue - 1]}" + if (d.year != LocalDate.now().year) " ${d.year}" else ""

    fun weekday(d: LocalDate, ru: Boolean) = if (ru) daysRu[d.dayOfWeek.value - 1] else daysEn[d.dayOfWeek.value - 1]

    fun time(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(ms).atZone(zone).let { "%d:%02d".format(it.hour, it.minute) }

    /** Morning, afternoon, evening, night: the part of the day a run began in. */
    fun partOfDay(ms: Long, ru: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val h = Instant.ofEpochMilli(ms).atZone(zone).hour
        return when (h) {
            in 5..11 -> if (ru) "утро" else "morning"
            in 12..16 -> if (ru) "день" else "afternoon"
            in 17..22 -> if (ru) "вечер" else "evening"
            else -> if (ru) "ночь" else "night"
        }
    }

    /** Russian plural: 1 дозор, 2 дозора, 5 дозоров. */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val a = abs(n) % 100; val b = a % 10
        return when { a in 11..14 -> many; b == 1 -> one; b in 2..4 -> few; else -> many }
    }

    fun patrols(n: Int, ru: Boolean) = if (ru) "$n ${plural(n, "дозор", "дозора", "дозоров")}" else "$n patrol${if (n == 1) "" else "s"}"
    fun weeks(n: Int, ru: Boolean) = if (ru) "$n ${plural(n, "неделя", "недели", "недель")}" else "$n week${if (n == 1) "" else "s"}"
    fun days(n: Int, ru: Boolean) = if (ru) "$n ${plural(n, "день", "дня", "дней")}" else "$n day${if (n == 1) "" else "s"}"

    /** The time for a distance best: 4:58 for a kilometre, 24:10 for 5 km, 1:52:03 for a half. */
    fun best(s: Int) = clock(s)

    val distanceNames = listOf("1 км" to "1 km", "5 км" to "5 km", "10 км" to "10 km", "Полумарафон" to "Half marathon", "Марафон" to "Marathon")
}
