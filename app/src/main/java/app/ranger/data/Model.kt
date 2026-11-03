package app.ranger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One run: a patrol. The numbers come from wherever it was recorded (a file, Health Connect, the app itself);
 * shoe, company, feeling and note are the ranger's own. Weather and terrain are looked up afterwards and stay
 * null until then. The full track lives in a file (see [TrackFile]); [line] is a thinned copy for the map.
 */
@Entity(tableName = "runs", indices = [Index("start"), Index(value = ["source", "sourceId"], unique = true)])
data class Run(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long,
    val durationS: Int,
    val movingS: Int,
    val distanceM: Double,
    val ascentM: Double = 0.0,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    /** gpx, tcx, fit, strava, hc, manual, rec. */
    val source: String,
    val sourceId: String,
    val title: String = "",
    val treadmill: Boolean = false,
    val race: Boolean = false,
    val shoeId: Long? = null,
    /** 0 not said, 1 light, 2 fine, 3 hard. */
    val feel: Int = 0,
    val note: String = "",
    /** What was in the ears, typed by hand: a podcast, "silence", a radio. Tracks caught automatically are in [Play]. */
    val listened: String = "",
    val hasTrack: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Encoded polyline (Google's algorithm, 1e5) of the thinned track, home zone not cut yet. */
    val line: String = "",
    /** Seconds at each heart rate: "bpm:s,bpm:s,…" — enough to work out zones and load for any settings later. */
    val hrHist: String = "",
    /** Best times (s) over 1, 5, 10, 21.1 and 42.2 km within this run, 0 where it was shorter. */
    val best: String = "",
    /** Number of surges: stretches clearly faster than the run's own steady pace — how intervals show. */
    val surges: Int = 0,
    val weatherDone: Boolean = false,
    val wCode: Int? = null,
    val tempC: Double? = null,
    val precipMm: Double? = null,
    val snowCm: Double? = null,
    val windKmh: Double? = null,
    val terrainDone: Boolean = false,
    /** Share of the track within ~60 m of water, and inside parks or woods. */
    val nearWater: Float? = null,
    val inGreen: Float? = null,
    val importedAt: Long = System.currentTimeMillis(),
) {
    val km get() = distanceM / 1000.0
    val end get() = start + durationS * 1000L
    /** Seconds per km over moving time. */
    val pace: Double get() = if (distanceM > 50) (if (movingS > 0) movingS else durationS) / km else 0.0
    /**
     * A walk that Health Connect filed as a run (watches auto-detect them): slower than 12 min/km with a calm pulse,
     * or no pulse at all. It isn't a patrol; its steps still count as walking.
     */
    val isWalk: Boolean get() = source == "hc" && pace > 720 && (avgHr ?: 0) < 120
    fun bestOf(i: Int): Int = best.split(',').getOrNull(i)?.toIntOrNull() ?: 0
}

/** A pair of running shoes — the ranger's boots. */
@Entity(tableName = "shoes")
data class Shoe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** One of [Palette.boots]. */
    val color: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val retiredAt: Long? = null,
    /** Kilometres they already had before coming here. */
    val baseKm: Double = 0.0,
    /** The gear name in a Strava export, so imported runs find their shoes. */
    val gear: String = "",
    val limitKm: Int = 700,
)

/** Someone who runs alongside: a friend, or a dog. */
@Entity(tableName = "companions")
data class Friend(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dog: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "run_companions", primaryKeys = ["runId", "companionId"], indices = [Index("companionId")])
data class RunCompanion(val runId: Long, val companionId: Long)

/** A track that was playing on the phone, caught from the media notification. */
@Entity(tableName = "plays", indices = [Index("at")])
data class Play(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val until: Long,
    val artist: String,
    val title: String,
    val app: String = "",
)

/** Something that hurt: where, and after which run. */
@Entity(tableName = "wounds", indices = [Index("runId")])
data class Wound(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long?,
    val at: Long,
    /** A key of [Body.parts]. */
    val part: String,
    val note: String = "",
    val healedAt: Long? = null,
)

/** A day's numbers: the morning pulse from the watch, and the steps (from Health Connect, or typed in). */
@Entity(tableName = "days")
data class Day(
    @PrimaryKey val date: String,
    val restHr: Int? = null,
    val hrv: Double? = null,
    val steps: Int? = null,
    /** Typed in by the ranger: Health Connect then leaves the steps of that day alone. */
    val stepsByHand: Boolean = false,
)

/** A campaign taken on: a goal over a few weeks. */
@Entity(tableName = "quests")
data class Quest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val startedAt: Long,
    val endsAt: Long,
    /** Set when it was given up. A finished one is recognised from the runs. */
    val abandonedAt: Long? = null,
)
