package app.ranger.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore("settings")

/** A place where no track is ever drawn or shared: home, work. */
data class Hidden(val lat: Double, val lon: Double, val radiusM: Int = 300)

data class Settings(
    val onboarded: Boolean = false,
    /** What the outpost calls you; empty means "ranger". */
    val name: String = "",
    /** 0 = worked out from the runs. */
    val maxHr: Int = 0,
    val restHr: Int = 0,
    val hidden: List<Hidden> = emptyList(),
    val healthConnect: Boolean = false,
    /** Up to when Health Connect has been read. */
    val hcSince: Long = 0,
    /** What "fetch new ones" reads from Health Connect, names of [Health.Kind]. */
    val hcKinds: Set<String> = Health.Kind.entries.map { it.name }.toSet(),
    /** Catch what plays in the headphones (needs notification access). */
    val music: Boolean = false,
    /** The boots new runs get when nothing else says so. */
    val defaultShoe: Long? = null,
    /** Runs whose fresh encounters have been shown already, so the reveal plays once. */
    val seenUpTo: Long = 0,
    /** Things the ranger has read and dismissed (hints, the healer's warning for a day). */
    val dismissed: Set<String> = emptySet(),
    /** Steps a day the ranger aims for. */
    val stepGoal: Int = 8000,
) {
    fun json(): String = JSONObject().put("on", onboarded).put("n", name).put("mx", maxHr).put("rs", restHr)
        .put("h", hidden.joinToString(";") { "${it.lat},${it.lon},${it.radiusM}" }).put("hc", healthConnect).put("hcs", hcSince).put("hck", hcKinds.joinToString(","))
        .put("mu", music).put("ds", defaultShoe ?: -1).put("seen", seenUpTo).put("dis", dismissed.joinToString("\n")).put("sg", stepGoal).toString()

    companion object {
        fun of(s: String?): Settings = runCatching {
            val o = JSONObject(s ?: return Settings())
            Settings(
                onboarded = o.optBoolean("on"), name = o.optString("n"), maxHr = o.optInt("mx"), restHr = o.optInt("rs"),
                hidden = o.optString("h").split(';').mapNotNull { h -> h.split(',').takeIf { it.size == 3 }?.let { Hidden(it[0].toDouble(), it[1].toDouble(), it[2].toInt()) } },
                healthConnect = o.optBoolean("hc"), hcSince = o.optLong("hcs"),
                hcKinds = o.optString("hck", Health.Kind.entries.joinToString(",") { it.name }).split(',').filter { it.isNotBlank() }.toSet(), music = o.optBoolean("mu"),
                defaultShoe = o.optLong("ds", -1).takeIf { it >= 0 }, seenUpTo = o.optLong("seen"),
                dismissed = o.optString("dis").split('\n').filter { it.isNotBlank() }.toSet(),
                stepGoal = o.optInt("sg", 8000).coerceIn(1000, 50000),
            )
        }.getOrElse { Settings() }
    }
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore
    private val KEY = stringPreferencesKey("settings")

    val settings: Flow<Settings> = store.data.map { Settings.of(it[KEY]) }

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit { it[KEY] = change(Settings.of(it[KEY])).json() }
    }
}
