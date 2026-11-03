package app.ranger

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
    }
}

/** Shown by Health Connect when someone asks why the app wants their data. */
class PrivacyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RangerTheme {
                app.ranger.ui.components.Page(if (ru) "Что читает Следопыт" else "What Ranger reads", ::finish) {
                    androidx.compose.material3.Text(
                        if (ru) "Следопыт только читает из Health Connect пробежки (время, маршрут, дистанцию, набор высоты, пульс), а также утренний пульс покоя и вариабельность пульса. Из этого складываются дозоры, нагрузка и состояние. Данные остаются на телефоне и никуда не отправляются. В сеть уходят только запросы карты (OpenFreeMap) и погоды (Open-Meteo) по координатам пробежек. Приложение ничего не записывает в Health Connect."
                        else "Ranger only reads runs from Health Connect (time, route, distance, climb, heart rate), plus morning resting heart rate and heart-rate variability. They become patrols, load and condition. The data stays on the phone and is never sent anywhere. Only map (OpenFreeMap) and weather (Open-Meteo) requests for run coordinates go out. The app writes nothing to Health Connect.",
                        style = app.ranger.ui.theme.T.Body,
                    )
                }
            }
        }
    }
}
