package app.ranger.work

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import app.ranger.container
import app.ranger.data.Play
import kotlinx.coroutines.launch

/**
 * The bard's ears: with notification access the app sees which track the music player is on and keeps a log of
 * artist and title with times. Only that; notifications themselves are never read or stored. A patrol later
 * picks the tracks that played during it.
 */
class Ears : NotificationListenerService() {
    private var msm: MediaSessionManager? = null
    private val watched = HashMap<android.media.session.MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list.orEmpty()) }

    override fun onListenerConnected() {
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        msm = m
        val me = ComponentName(this, Ears::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessions, me)
            watch(m.getActiveSessions(me))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessions) }
        for ((c, cb) in watched.values) c.unregisterCallback(cb)
        watched.clear()
    }

    private fun watch(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        for (t in watched.keys.toList()) if (t !in tokens) watched.remove(t)?.let { (c, cb) -> c.unregisterCallback(cb) }
        for (c in list) if (c.sessionToken !in watched) {
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = note(c)
                override fun onPlaybackStateChanged(state: PlaybackState?) = note(c)
            }
            c.registerCallback(cb)
            watched[c.sessionToken] = c to cb
            note(c)
        }
    }

    private fun note(c: MediaController) {
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val md = c.metadata
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val app = c.packageName
        val box = applicationContext.container
        box.write {
            if (!settings.now().music) return@write
            val now = System.currentTimeMillis()
            val last = dao.lastPlay()
            val same = last != null && last.artist == artist && last.title == title && last.app == app && now - last.until < 10 * 60_000
            if (!playing) {
                if (same) dao.update(last!!.copy(until = minOf(last.until, now).coerceAtLeast(last.at)))
                return@write
            }
            if (title.isBlank()) return@write
            val until = now + if (dur in 30_000..1_800_000) dur else 4 * 60_000L
            if (same) dao.update(last!!.copy(until = maxOf(last.until, until)))
            else {
                if (last != null && last.until > now) dao.update(last.copy(until = now))
                dao.insert(Play(at = now, until = until, artist = artist.ifBlank { "—" }, title = title, app = app))
            }
        }
    }

    companion object {
        fun allowed(ctx: Context): Boolean =
            Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ComponentName(ctx, Ears::class.java).flattenToString()) == true
    }
}

