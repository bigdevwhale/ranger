package app.ranger

import android.app.Application
import android.content.Context
import app.ranger.data.DemoSeed
import app.ranger.data.Importer
import app.ranger.data.RangerDb
import app.ranger.data.SettingsStore
import app.ranger.data.Terrain
import app.ranger.data.Weather
import app.ranger.domain.World
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val db = RangerDb.create(app)
    val dao = db.dao()
    val settings = SettingsStore(app)
    val importer = Importer(app, dao, settings)
    /** On the main thread: Room and DataStore are main-safe, and screens' state is written from here. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** True while the demo build walks its year of patrols, so the first screen doesn't jump. */
    val seeding = MutableStateFlow(BuildConfig.DEMO)
    /** A line about background work for the outpost ("Looking up the weather…"), empty when idle. */
    val busy = MutableStateFlow("")

    suspend fun world(now: Long = System.currentTimeMillis()): World = World(
        dao.runList(), dao.shoeList(), dao.companions().first(), dao.links().first(), dao.plays().first(), dao.wounds().first(),
        dao.days().first(), dao.quests().first(), settings.now(), now,
    )

    /** Fire-and-forget so a write survives the screen being closed right after tapping. */
    fun write(block: suspend AppContainer.() -> Unit) {
        scope.launch { block() }
    }

    private var steward: Job? = null

    /**
     * Looks up what imported runs are missing: the weather they ran in, and the land they crossed. Quietly, a batch
     * at a time; whatever fails (no network) is tried again next time the app opens.
     */
    fun tend() {
        if (BuildConfig.DEMO && seeding.value) return
        if (steward?.isActive == true) return
        steward = scope.launch {
            val ru = app.ru
            try {
                while (true) {
                    val need = dao.needWeather(400)
                    if (need.isEmpty()) break
                    busy.value = if (ru) "Узнаём погоду дозоров…" else "Looking up the weather…"
                    var progress = false
                    for (g in Weather.groups(need)) {
                        val done = withContext(Dispatchers.IO) { runCatching { Weather.fill(g) }.getOrNull() } ?: continue
                        for (r in done) if (r.weatherDone) { dao.update(r); progress = true }
                    }
                    if (!progress) break
                }
                while (true) {
                    val need = dao.needTerrain(20)
                    if (need.isEmpty()) break
                    busy.value = if (ru) "Наносим тропы на карту…" else "Charting the trails…"
                    var progress = false
                    for (r in need) {
                        val t = withContext(Dispatchers.IO) { runCatching { Terrain.of(app, r.line) }.getOrNull() } ?: continue
                        dao.update(r.copy(terrainDone = true, nearWater = t.first, inGreen = t.second)); progress = true
                    }
                    if (!progress) break
                }
            } finally { busy.value = "" }
        }
    }
}

class RangerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (BuildConfig.DEMO) container.scope.launch { try { DemoSeed.seedIfEmpty(this@RangerApp, container) } finally { container.seeding.value = false } }
    }
}

val Context.container: AppContainer get() = (applicationContext as RangerApp).container

val Context.ru: Boolean get() = resources.configuration.locales[0].language == "ru"
