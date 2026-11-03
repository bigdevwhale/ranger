package app.ranger.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Runs from Health Connect: Strava, Garmin, Samsung Health, Mi Fitness, Zepp, Polar and Coros can all write there.
 * Only reading; the app never writes anything back.
 */
object Health {
    const val ROUTES = "android.permission.health.READ_EXERCISE_ROUTES"

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        ROUTES,
    )

    fun available(ctx: Context) = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    fun client(ctx: Context) = HealthConnectClient.getOrCreate(ctx)

    suspend fun granted(ctx: Context): Set<String> = runCatching { client(ctx).permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    private suspend fun <T : Record> all(c: HealthConnectClient, type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        do {
            val r = c.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageSize = 1000, pageToken = token))
            out += r.records; token = r.pageToken
        } while (token != null)
        return out
    }

    /** Steps per local day, summed by Health Connect itself so the phone and the watch aren't counted twice. */
    private suspend fun steps(c: HealthConnectClient, from: Instant, to: Instant, zone: ZoneId): Map<String, Int> {
        val out = HashMap<String, Int>()
        var a = from.atZone(zone).toLocalDate().atStartOfDay()
        val end = to.atZone(zone).toLocalDateTime()
        while (a.isBefore(end)) {
            val b = minOf(a.plusDays(365), end)
            val groups = c.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(a, b), java.time.Period.ofDays(1)))
            for (g in groups) g.result[StepsRecord.COUNT_TOTAL]?.let { if (it > 0) out[g.startTime.toLocalDate().toString()] = it.toInt() }
            a = b
        }
        return out
    }

    class Synced(val runs: List<Parsed>, val days: List<Day>, val until: Long)

    /** Everything since [since] (0 = as far back as allowed). */
    suspend fun read(ctx: Context, since: Long): Synced {
        val c = client(ctx)
        val have = granted(ctx)
        val now = Instant.now()
        val from = Instant.ofEpochMilli(if (since > 0) since - 2 * 86_400_000L else now.toEpochMilli() - 10L * 365 * 86_400_000L)
        val runTypes = setOf(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL)
        val sessions = all(c, ExerciseSessionRecord::class, from, now).filter { it.exerciseType in runTypes }
        val runs = sessions.map { s ->
            val range = TimeRangeFilter.between(s.startTime, s.endTime)
            val hr = if (HealthPermission.getReadPermission(HeartRateRecord::class) in have)
                runCatching { c.readRecords(ReadRecordsRequest(HeartRateRecord::class, range)).records.flatMap { it.samples } }.getOrDefault(emptyList()) else emptyList()
            val dist = runCatching { c.readRecords(ReadRecordsRequest(DistanceRecord::class, range)).records.sumOf { it.distance.inMeters } }.getOrDefault(0.0)
            val climb = runCatching { c.readRecords(ReadRecordsRequest(ElevationGainedRecord::class, range)).records.sumOf { it.elevation.inMeters } }.getOrDefault(0.0)
            val route = (s.exerciseRouteResult as? ExerciseRouteResult.Data)?.exerciseRoute?.route.orEmpty()
            // one stream: route points with the nearest heart rate, or heart rate alone
            val pts = if (route.isNotEmpty()) {
                val h = hr.sortedBy { it.time }
                var j = 0
                route.map { l ->
                    while (j + 1 < h.size && h[j + 1].time <= l.time) j++
                    val bpm = h.getOrNull(j)?.takeIf { kotlin.math.abs(it.time.toEpochMilli() - l.time.toEpochMilli()) < 15_000 }?.beatsPerMinute?.toInt()
                    Pt(l.time.toEpochMilli(), l.latitude, l.longitude, l.altitude?.inMeters, bpm)
                }
            } else hr.map { Pt(it.time.toEpochMilli(), hr = it.beatsPerMinute.toInt()) }
            Parsed(
                start = s.startTime.toEpochMilli(), points = pts, source = "hc", sourceId = "hc:${s.metadata.id}", title = s.title.orEmpty(),
                treadmill = s.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
                durationS = ((s.endTime.toEpochMilli() - s.startTime.toEpochMilli()) / 1000).toInt(),
                distanceM = dist.takeIf { it > 0 && route.isEmpty() } ?: dist.takeIf { it > 0 },
                ascentM = climb.takeIf { it > 0 },
            )
        }
        val zone = ZoneId.systemDefault()
        val rest = runCatching { all(c, RestingHeartRateRecord::class, from, now) }.getOrDefault(emptyList())
            .groupBy { it.time.atZone(zone).toLocalDate().toString() }.mapValues { (_, v) -> v.minOf { it.beatsPerMinute }.toInt() }
        val hrv = runCatching { all(c, HeartRateVariabilityRmssdRecord::class, from, now) }.getOrDefault(emptyList())
            .groupBy { it.time.atZone(zone).toLocalDate().toString() }.mapValues { (_, v) -> v.map { it.heartRateVariabilityMillis }.average() }
        val steps = runCatching { steps(c, from, now, zone) }.getOrDefault(emptyMap())
        val days = (rest.keys + hrv.keys + steps.keys).map { Day(it, rest[it], hrv[it], steps[it]) }
        return Synced(runs, days, now.toEpochMilli())
    }
}
