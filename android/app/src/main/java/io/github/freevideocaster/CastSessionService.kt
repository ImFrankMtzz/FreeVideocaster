package io.github.freevideocaster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.freevideocaster.roku.LiveState
import io.github.freevideocaster.roku.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Mientras algo se reproduce en el Roku, muestra una notificación con controles (también en la
 * pantalla de bloqueo) y guarda el progreso para poder retomar. Se detiene sola al terminar.
 */
class CastSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private lateinit var manager: NotificationManager
    private var monitor: Job? = null
    private var rokuName = ""
    private var shown: Shown? = null
    private var stopped = false
    @Volatile private var latest: LiveState? = null

    private val caster get() = (application as FreeVideocasterApp).caster

    /** Lo único que cambia el aspecto de la notificación; evita actualizarla cada segundo. */
    private data class Shown(val title: String, val playback: Playback, val queueSize: Int)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(NotificationManager::class.java)
        // Los canales de notificación existen desde Android 8; antes basta la prioridad baja del constructor.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reproducción en el Roku", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Controles del video que se reproduce en tu Roku"
                    setShowBadge(false)
                }
            )
        }
        session = MediaSession(this, "FreeVideocaster").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() = send("resume")
                    override fun onPause() = send("pause")
                    override fun onStop() = send("stop")
                    override fun onSkipToNext() = send("next")
                    override fun onRewind() = send("skip", mapOf("delta" to "-$SKIP_SECONDS"))
                    override fun onFastForward() = send("skip", mapOf("delta" to "$SKIP_SECONDS"))
                    override fun onSeekTo(pos: Long) = send("seek", mapOf("pos" to (pos / 1000).toString()))
                }
            )
            isActive = true
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(null),
            FGS_TYPE_CONNECTED_DEVICE,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> send("toggle")
            ACTION_BACK -> send("skip", mapOf("delta" to "-$SKIP_SECONDS"))
            ACTION_FORWARD -> send("skip", mapOf("delta" to "$SKIP_SECONDS"))
            ACTION_NEXT -> send("next")
            ACTION_STOP -> scope.launch {
                latest?.let { runCatching { caster.saveProgress(it) } }
                runCatching { caster.command("stop") }
                shutdown()
            }
        }
        if (monitor == null) monitor = scope.launch { monitorRoku() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopped = true
        runCatching { session.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun send(action: String, extra: Map<String, String> = emptyMap()) {
        scope.launch { runCatching { caster.command(action, extra) } }
    }

    private suspend fun monitorRoku() {
        rokuName = caster.roku.first()?.name.orEmpty()
        val startedAt = SystemClock.elapsedRealtime()

        // El estado solo se publica cuando cambia (en reposo no cambia nunca), así que el
        // vigilante que decide cuándo apagar el servicio necesita su propio reloj.
        scope.launch {
            var seenActive = false
            var idleSince = 0L
            var offlineSince = 0L
            while (!stopped) {
                delay(1000)
                val live = latest ?: continue
                val now = SystemClock.elapsedRealtime()
                if (!live.reachable) {
                    if (offlineSince == 0L) offlineSince = now
                    if (now - offlineSince > OFFLINE_LIMIT_MS) shutdown()
                    continue
                }
                offlineSince = 0L
                if (live.ours && live.isActive) {
                    seenActive = true
                    idleSince = 0L
                } else {
                    if (idleSince == 0L) idleSince = now
                    // Al arrancar se da más margen: el canal tarda unos segundos en abrirse.
                    val limit = if (seenActive || now - startedAt > START_GRACE_MS) IDLE_LIMIT_MS else START_GRACE_MS
                    if (now - idleSince > limit) shutdown()
                }
            }
        }

        var lastSave = 0L
        var previous: LiveState? = null
        caster.live.collect { live ->
            if (live == null || stopped) return@collect
            latest = live
            val now = SystemClock.elapsedRealtime()
            render(live)

            // Guardar el progreso cada 10 s, y al pausar, cambiar de video o terminar.
            val changedVideo = previous?.let { it.ours && it.isActive && (live.key != it.key || !live.isActive) } == true
            if (changedVideo) previous?.let { caster.saveProgress(it) }
            if (live.ours && live.isActive && live.key.isNotEmpty() &&
                (now - lastSave >= SAVE_EVERY_MS || (previous?.playback == Playback.Playing && live.playback == Playback.Paused))
            ) {
                lastSave = now
                caster.saveProgress(live)
            }
            previous = live
        }
    }
    private fun render(live: LiveState) {
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, live.title.ifBlank { "FreeVideocaster" })
            .putString(MediaMetadata.METADATA_KEY_ARTIST, rokuName)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, live.durationMs)
            .build()
        session.setMetadata(metadata)

        val state = when (live.playback) {
            Playback.Playing -> PlaybackState.STATE_PLAYING
            Playback.Paused -> PlaybackState.STATE_PAUSED
            Playback.Buffering -> PlaybackState.STATE_BUFFERING
            Playback.Idle -> PlaybackState.STATE_STOPPED
        }
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_REWIND or
            PlaybackState.ACTION_FAST_FORWARD
        if (live.queue.isNotEmpty()) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, live.positionMs, if (live.playback == Playback.Playing) 1f else 0f, SystemClock.elapsedRealtime())
                .build()
        )

        val now = Shown(live.title, live.playback, live.queue.size)
        if (now != shown) {
            shown = now
            manager.notify(NOTIFICATION_ID, buildNotification(live))
        }
    }

    private fun buildNotification(live: LiveState?): Notification {
        val playing = live?.playback == Playback.Playing
        val hasQueue = (live?.queue?.size ?: 0) > 0

        val builder = newBuilder()
            .setSmallIcon(R.drawable.ic_stat_cast)
            .setContentTitle(live?.title?.takeIf { it.isNotBlank() } ?: "FreeVideocaster")
            .setContentText(statusText(live))
            .setSubText(rokuName.ifBlank { null })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .addAction(action(android.R.drawable.ic_media_rew, "−$SKIP_SECONDS s", ACTION_BACK))
            .addAction(
                action(
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (playing) "Pausa" else "Reproducir",
                    ACTION_TOGGLE,
                )
            )
            .addAction(action(android.R.drawable.ic_media_ff, "+$SKIP_SECONDS s", ACTION_FORWARD))
        if (hasQueue) builder.addAction(action(android.R.drawable.ic_media_next, "Siguiente", ACTION_NEXT))
        builder.addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Detener", ACTION_STOP))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        builder.setStyle(
            Notification.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        return builder.build()
    }

    private fun newBuilder(): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }

    private fun statusText(live: LiveState?): String {
        val queue = live?.queue?.size?.takeIf { it > 0 }?.let { " · $it en cola" }.orEmpty()
        return when (live?.playback) {
            Playback.Playing -> "Reproduciendo$queue"
            Playback.Paused -> "En pausa$queue"
            Playback.Buffering -> "Cargando…$queue"
            Playback.Idle -> if (live.error.isNotBlank()) "No se pudo reproducir" else "Sin reproducción$queue"
            null -> "Enviando al Roku…"
        }
    }

    private fun action(icon: Int, label: String, action: String): Notification.Action {
        val intent = Intent(this, CastSessionService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, action.hashCode(), intent, flags)
        } else {
            PendingIntent.getService(this, action.hashCode(), intent, flags)
        }
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pending).build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_REMOTE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun shutdown() {
        if (stopped) return
        stopped = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "cast"
        private const val NOTIFICATION_ID = 1
        // ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE (Android 10+); antes se ignora.
        private const val FGS_TYPE_CONNECTED_DEVICE = 0x10
        private const val SKIP_SECONDS = 10
        private const val SAVE_EVERY_MS = 10_000L
        private const val IDLE_LIMIT_MS = 15_000L
        private const val START_GRACE_MS = 40_000L
        private const val OFFLINE_LIMIT_MS = 30_000L

        private const val ACTION_TOGGLE = "io.github.freevideocaster.action.TOGGLE"
        private const val ACTION_BACK = "io.github.freevideocaster.action.BACK"
        private const val ACTION_FORWARD = "io.github.freevideocaster.action.FORWARD"
        private const val ACTION_NEXT = "io.github.freevideocaster.action.NEXT"
        private const val ACTION_STOP = "io.github.freevideocaster.action.STOP"

        /** Debe llamarse con la app a la vista (justo después de que el usuario envía un video). */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, CastSessionService::class.java))
            } catch (e: IllegalStateException) {
                // Android puede negar el inicio en segundo plano; la app sigue funcionando sin notificación.
            } catch (e: SecurityException) {
                // Falta algún permiso de servicios en primer plano; igual, solo se pierde la notificación.
            }
        }
    }
}
