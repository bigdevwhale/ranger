package app.ranger.data

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The weather a patrol ran in, from Open-Meteo (free, no key): the archive for older runs, the forecast service's
 * recent past for the last days. Runs close to each other share one request per place and year.
 */
object Weather {
    class Hour(val code: Int?, val temp: Double?, val precip: Double?, val snow: Double?, val wind: Double?)

    private const val VARS = "temperature_2m,precipitation,snowfall,weather_code,wind_speed_10m"

    /** Fills weather into [runs] (all near one place); returns the updated copies. */
    fun fill(runs: List<Run>, now: Long = System.currentTimeMillis()): List<Run> {
        if (runs.isEmpty()) return emptyList()
        val lat = runs.first().lat ?: return runs.map { it.copy(weatherDone = true) }
        val lon = runs.first().lon!!
        val recent = runs.filter { now - it.start < 6 * 86_400_000L }
        val old = runs - recent.toSet()
        val hours = HashMap<Long, Hour>()
        if (old.isNotEmpty()) {
            val from = day(old.minOf { it.start }); val to = day(old.maxOf { it.end })
            hours += fetch("https://archive-api.open-meteo.com/v1/archive?latitude=$lat&longitude=$lon&start_date=$from&end_date=$to&hourly=$VARS&timezone=GMT&timeformat=unixtime")
        }
        if (recent.isNotEmpty()) hours += fetch("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&past_days=7&forecast_days=1&hourly=$VARS&timezone=GMT&timeformat=unixtime")
        return runs.map { r ->
            val mid = (r.start + r.durationS * 500L) / 1000
            val key = hours.keys.minByOrNull { abs(it - mid) }?.takeIf { abs(it - mid) <= 5400 }
            val h = key?.let { hours[it] }
            if (h == null) r.copy(weatherDone = hours.isNotEmpty())
            else r.copy(weatherDone = true, wCode = h.code, tempC = h.temp, precipMm = h.precip, snowCm = h.snow, windKmh = h.wind)
        }
    }

    private fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()

    private fun fetch(url: String): Map<Long, Hour> {
        val o = JSONObject(String(Tiles.get(url))).getJSONObject("hourly")
        val t = o.getJSONArray("time")
        fun arr(n: String) = o.optJSONArray(n)
        val code = arr("weather_code"); val temp = arr("temperature_2m"); val pr = arr("precipitation"); val sn = arr("snowfall"); val wi = arr("wind_speed_10m")
        fun d(a: org.json.JSONArray?, i: Int): Double? = if (a == null || a.isNull(i)) null else a.getDouble(i)
        return (0 until t.length()).associate { i ->
            t.getLong(i) to Hour(d(code, i)?.roundToInt(), d(temp, i), d(pr, i), d(sn, i), d(wi, i))
        }
    }

    /** Groups runs by place (about 30 km) and calendar year, the unit of one request. */
    fun groups(runs: List<Run>): List<List<Run>> = runs.filter { it.lat != null }.groupBy {
        Triple((it.lat!! * 3).roundToInt(), (it.lon!! * 3).roundToInt(), Instant.ofEpochMilli(it.start).atZone(ZoneOffset.UTC).year)
    }.values.toList()
}
