package io.github.freevideocaster.roku

data class RokuDevice(
    val ip: String,
    val serial: String,
    val name: String,
    val model: String,
    val isTv: Boolean,
    val supportsVolume: Boolean,
    // Solo se rellenan al consultar el equipo (no se guardan): los usa el diagnóstico.
    val ecpMode: String = "",
    val developerEnabled: Boolean = false,
    val softwareVersion: String = "",
    val uiResolution: String = "",
)

/** Estado del reproductor según /query/media-player. */
data class PlayerStatus(
    val appId: String?,
    val state: String,
    val error: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val audioCodec: String?,
    val videoCodec: String?,
) {
    val isPlaying get() = state == "play"
    val isActive get() = state == "play" || state == "pause" || state == "buffer"

    /** Avisa si el audio es de un tipo que muchos Roku no decodifican (típico en remux de torrents). */
    val audioWarning: String? get() = audioWarningFor(audioCodec)

    companion object {
        fun audioWarningFor(audioCodec: String?): String? {
            val codec = audioCodec?.lowercase() ?: return null
            // Por palabras: «aac_adts» contiene «dts» pero es AAC.
            val words = codec.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            return if (words.any { it.startsWith("dts") || it == "truehd" }) {
                "El audio es ${codec.uppercase()}: si no se oye, este Roku no lo soporta."
            } else {
                null
            }
        }
    }
}
