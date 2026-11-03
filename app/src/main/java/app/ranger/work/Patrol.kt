package app.ranger.work

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import app.ranger.MainActivity
import app.ranger.R
import app.ranger.container
import app.ranger.data.Geo
import app.ranger.data.Parsed
import app.ranger.data.Pt
import app.ranger.ru
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** A patrol being recorded right now, for ranger without a watch: GPS only, in a foreground service. */
class PatrolService : Service(), LocationListener {
    data class Live(val running: Boolean = false, val startedAt: Long = 0, val elapsedS: Int = 0, val meters: Double = 0.0, val fix: Boolean = false, val points: Int = 0) {
        val pace get() = if (meters > 50) elapsedS / (meters / 1000) else 0.0
    }

    private val pts = ArrayList<Pt>()
    private var startedAt = 0L
    private var startedClock = 0L
    private var meters = 0.0
    private var last: Location? = null
    private val ticker = android.os.Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { publish(); ticker.postDelayed(this, 1000) } }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> { finish(save = true); return START_NOT_STICKY }
            CANCEL -> { finish(save = false); return START_NOT_STICKY }
        }
        if (startedAt != 0L) return START_STICKY
        channel(this)
        val n = notification(getString(R.string.app_name), if (ru) "Дозор идёт" else "On patrol")
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(ID, n)
        startedAt = System.currentTimeMillis(); startedClock = SystemClock.elapsedRealtime()
        val lm = getSystemService(LocationManager::class.java)
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper()) }
        ticker.post(tick)
        return START_STICKY
    }

    override fun onLocationChanged(l: Location) {
        if (l.hasAccuracy() && l.accuracy > 30) return
        val prev = last
        if (prev != null) {
            val d = Geo.dist(prev.latitude, prev.longitude, l.latitude, l.longitude)
            val dt = (l.time - prev.time) / 1000.0
            if (dt > 0 && d / dt > 12) return
            meters += d
        }
        last = l
        pts += Pt(l.time, l.latitude, l.longitude, if (l.hasAltitude()) l.altitude else null, dist = meters)
        if (pts.size % 30 == 0) backup()
        publish()
    }

    private fun publish() {
        state.value = Live(true, startedAt, ((SystemClock.elapsedRealtime() - startedClock) / 1000).toInt(), meters, last != null && System.currentTimeMillis() - last!!.time < 10_000, pts.size)
    }

    /** Points so far go to a file now and then, so a killed service still leaves the patrol behind. */
    private fun backup() = runCatching {
        File(filesDir, "patrol.csv").writeText(pts.joinToString("\n") { "${it.at},${it.lat},${it.lon},${it.ele ?: ""},${it.dist}" })
    }

    private fun finish(save: Boolean) {
        ticker.removeCallbacks(tick)
        runCatching { getSystemService(LocationManager::class.java).removeUpdates(this) }
        val list = ArrayList(pts)
        val start = startedAt
        val dur = ((SystemClock.elapsedRealtime() - startedClock) / 1000).toInt()
        state.value = Live()
        File(filesDir, "patrol.csv").delete()
        if (save && list.size > 1) {
            val p = Parsed(start = start, points = list, source = "rec", sourceId = "rec:${start / 1000}", durationS = dur)
            applicationContext.container.write { val (_, id) = importer.put(p); lastSaved.value = id; tend() }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        startedAt = 0
    }

    override fun onDestroy() { ticker.removeCallbacks(tick); super.onDestroy() }

    private fun notification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome).setContentTitle(title).setContentText(text)
            .setOngoing(true).setContentIntent(open).build()
    }

    companion object {
        private const val CHANNEL = "patrol"
        private const val ID = 7
        const val STOP = "stop"
        const val CANCEL = "cancel"
        private val state = MutableStateFlow(Live())
        val live: StateFlow<Live> get() = state
        /** The id of the run saved by the last finished recording, for opening its card. */
        val lastSaved = MutableStateFlow(0L)

        fun channel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, if (ctx.ru) "Дозор" else "Patrol", NotificationManager.IMPORTANCE_LOW))
        }

        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, PatrolService::class.java))
        fun stop(ctx: Context, save: Boolean) = ctx.startService(Intent(ctx, PatrolService::class.java).setAction(if (save) STOP else CANCEL))
    }
}
