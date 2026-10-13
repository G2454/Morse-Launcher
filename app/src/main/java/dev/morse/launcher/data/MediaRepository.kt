package dev.morse.launcher.data

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import dev.morse.launcher.service.MorseNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val positionMs: Long,
    /** [SystemClock.elapsedRealtime] when [positionMs] was measured. */
    val positionAt: Long,
    val speed: Float,
    val playing: Boolean,
    val packageName: String,
) {
    fun positionNow(elapsedRealtime: Long = SystemClock.elapsedRealtime()): Long {
        if (!playing) return positionMs
        val p = positionMs + ((elapsedRealtime - positionAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * The active media session, for the Music widget. Android only exposes sessions
 * to apps with notification access, so this stays empty until the person
 * enables "Morse notification dots & music" in system settings.
 */
class MediaRepository(private val context: Context) {

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _access = MutableStateFlow(hasAccess())
    val access: StateFlow<Boolean> = _access.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val listenerComponent = ComponentName(context, MorseNotificationListener::class.java)

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        pick(controllers.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            publish()
        }
    }

    /** Safe to call repeatedly; does nothing without notification access. */
    fun start() {
        handler.post {
            _access.value = hasAccess()
            if (!_access.value || manager != null) return@post
            val m = context.getSystemService(MediaSessionManager::class.java) ?: return@post
            try {
                m.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
                manager = m
                pick(m.getActiveSessions(listenerComponent))
            } catch (e: SecurityException) {
                manager = null
            }
        }
    }

    fun stop() {
        handler.post {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
            manager = null
            controller?.unregisterCallback(controllerCallback)
            controller = null
            _nowPlaying.value = null
        }
    }

    private fun pick(controllers: List<MediaController>) {
        val best = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val state = c?.playbackState
        if (c == null || meta == null) {
            _nowPlaying.value = null
            return
        }
        _nowPlaying.value = NowPlaying(
            title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Unknown",
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            positionMs = state?.position ?: 0L,
            positionAt = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed ?: 1f,
            playing = state?.state == PlaybackState.STATE_PLAYING,
            packageName = c.packageName,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }
}
