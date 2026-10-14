package io.github.freevideocaster.media

import java.security.MessageDigest

/** Un video para enviar al Roku. */
data class MediaRequest(
    val url: String,
    val title: String = "",
    val subtitleUrl: String? = null,
    /** mp4, mkv, hls o dash. Si es null se averigua al resolver el enlace. */
    val format: String? = null,
    /** Cabeceras que el Roku debe enviar al pedir el video (Referer, Cookie…). */
    val headers: Map<String, String> = emptyMap(),
    /** Línea de detalle para mostrar («2025 · 1080p · WEB-DL»). */
    val detail: String = "",
) {
    /**
     * Identificador corto y estable del enlace original (antes de seguir redirecciones).
     * El canal lo devuelve en su estado sin exponer la URL, y así la app sabe de qué video habla.
     */
    val key: String get() = keyOf(url)

    companion object {
        fun keyOf(url: String): String =
            MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
                .take(6)
                .joinToString("") { "%02x".format(it) }
    }
}
