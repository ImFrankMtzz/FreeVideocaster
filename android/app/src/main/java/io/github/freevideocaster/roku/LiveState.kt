package io.github.freevideocaster.roku

enum class Playback { Idle, Buffering, Playing, Paused }

/** Estado actual del Roku visto desde el teléfono; lo comparten la pantalla de control y la notificación. */
data class LiveState(
    /** El Roku respondió en esta consulta. */
    val reachable: Boolean,
    /** Lo que se reproduce viene del canal FreeVideocaster (y no de otra app del Roku). */
    val ours: Boolean,
    val playback: Playback,
    val key: String = "",
    val title: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<QueueItem> = emptyList(),
    val error: String = "",
    val audioCodec: String? = null,
    val videoCodec: String? = null,
    val audio: List<Track> = emptyList(),
    val audioCurrent: String = "",
    val subtitles: List<Track> = emptyList(),
    val subtitleCurrent: String = "",
) {
    val isActive get() = playback != Playback.Idle
    val audioWarning: String? get() = PlayerStatus.audioWarningFor(audioCodec)

    /** Solo vale la pena un selector de audio si hay donde elegir. */
    val canChooseAudio get() = ours && isActive && audio.size > 1

    /** Con una sola pista de subtítulos también hay algo que activar o desactivar. */
    val canChooseSubtitles get() = ours && isActive && subtitles.isNotEmpty()

    companion object {
        val Offline = LiveState(reachable = false, ours = false, playback = Playback.Idle)
    }
}
