package app.ranger

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ranger.data.Settings
import app.ranger.ui.RangerRoot
import app.ranger.ui.theme.RangerTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = android.graphics.Color.TRANSPARENT
        // the header is the night sky, the tab bar parchment
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(t), navigationBarStyle = SystemBarStyle.light(t, t))
        setContent {
            val settings: Settings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            settings?.let { s -> RangerTheme { RangerRoot(s) } }
        }
    }

    override fun onResume() {
        super.onResume()
        container.tend()
        container.freshen()
    }
}

const val PRIVACY_URL = "https://github.com/bigdevwhale/ranger/blob/main/PRIVACY.md"

/** Shown by Health Connect when someone asks why the app wants their data. */
class PrivacyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RangerTheme {
                app.ranger.ui.components.Page(if (ru) "Что читает Следопыт" else "What Ranger reads", ::finish) {
                    androidx.compose.material3.Text(
                        if (ru) "Следопыт только читает из Health Connect пробежки (время, маршрут, дистанцию, набор высоты, пульс), а также утренний пульс покоя, вариабельность пульса и шаги за день. Из этого складываются дозоры, нагрузка и состояние. Данные остаются на телефоне и никуда не отправляются. В сеть уходят только запросы карты (OpenFreeMap) и погоды (Open-Meteo) по координатам пробежек. Новое читается при открытии приложения, а в фоне (примерно раз в час) только если включить это в настройках. Приложение ничего не записывает в Health Connect."
                        else "Ranger only reads runs from Health Connect (time, route, distance, climb, heart rate), plus morning resting heart rate, heart-rate variability and daily steps. They become patrols, load and condition. The data stays on the phone and is never sent anywhere. Only map (OpenFreeMap) and weather (Open-Meteo) requests for run coordinates go out. New data is read when the app opens, and in the background (about hourly) only if turned on in the settings. The app writes nothing to Health Connect.",
                        style = app.ranger.ui.theme.T.Body,
                    )
                    // The full policy, which Health Connect's review expects to be one tap away from here.
                    androidx.compose.material3.Text(
                        if (ru) "Политика конфиденциальности" else "Privacy policy",
                        style = app.ranger.ui.theme.T.Body.copy(color = app.ranger.ui.theme.C.seal),
                        modifier = androidx.compose.ui.Modifier.padding(top = 16.dp).clickable {
                            runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_URL))) }
                        },
                    )
                }
            }
        }
    }
}
