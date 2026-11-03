package app.ranger

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.ranger.data.DemoSeed
import app.ranger.data.Settings
import app.ranger.domain.World
import app.ranger.ui.LocalRu
import app.ranger.ui.Onboarding
import app.ranger.ui.Overlay
import app.ranger.ui.RangerRoot
import app.ranger.ui.Tab
import app.ranger.ui.art.Art
import app.ranger.ui.theme.RangerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Calendar

/** Every main screen over the demo year, drawn to PNGs (run with `-Prender`), in both languages. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreensBase {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    abstract val lang: String
    private val out by lazy { File(System.getProperty("render.out") ?: "build/art", "screens-$lang").apply { mkdirs() } }

    /** Today at twenty to nine in the evening. */
    private val evening: Long = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 40) }.timeInMillis

    @Before fun still() { Art.still = true }

    private val app get() = ApplicationProvider.getApplicationContext<RangerApp>()
    private val ru get() = lang == "ru"

    private fun seed(): Settings = runBlocking {
        DemoSeed.seedIfEmpty(app, app.container)
        app.container.seeding.value = false
        app.container.settings.update { it.copy(onboarded = true) }
        app.container.settings.now()
    }

    private fun shot(name: String, wait: String? = null, desc: String? = null, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { RangerTheme { CompositionLocalProvider(LocalRu provides ru) { content() } } }
        if (wait != null) rule.waitUntil(60_000) { rule.mainClock.advanceTimeBy(200); rule.onAllNodesWithText(wait, substring = true).fetchSemanticsNodes().isNotEmpty() }
        if (desc != null) rule.waitUntil(240_000) { rule.mainClock.advanceTimeBy(200); Thread.sleep(50); rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        Thread.sleep(1500)
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            File(out, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun screen(name: String, tab: Tab, wait: String? = null, overlay: (World) -> Overlay? = { null }) {
        val s = seed()
        val w = runBlocking { app.container.world(evening) }
        shot(name, wait ?: if (ru) "Застава" else "Outpost") { RangerRoot(s, evening, tab, overlay(w)) }
    }

    @Test fun welcome() = shot("0-welcome") { Onboarding() }
    @Test fun outpost() = screen("1-outpost", Tab.OUTPOST)
    @Test fun map() {
        val s = seed()
        shot("2-map", desc = if (ru) "Карта Пограничья" else "Map of the Borderlands") { RangerRoot(s, evening, Tab.MAP) }
    }
    @Test fun bestiary() = screen("3-bestiary", Tab.BESTIARY)
    @Test fun chronicle() = screen("4-chronicle", Tab.CHRONICLE)
    @Test fun patrol() = screen("5-patrol", Tab.OUTPOST) { w -> Overlay.Patrol(w.patrols.last { it.run.hasTrack && it.events.isNotEmpty() }.run.id) }
    @Test fun creature() = screen("6-creature", Tab.BESTIARY) { w -> Overlay.Creature(w.met.last().c.key) }
    @Test fun skills() = screen("7-skills", Tab.OUTPOST) { Overlay.Skills }
    @Test fun board() = screen("8-board", Tab.OUTPOST) { Overlay.Board }
    @Test fun add() = screen("9-add", Tab.OUTPOST) { Overlay.Add }
}

@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-xxhdpi")
class RenderScreensEn : ScreensBase() { override val lang = "en" }

@Config(sdk = [34], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class RenderScreensRu : ScreensBase() { override val lang = "ru" }
