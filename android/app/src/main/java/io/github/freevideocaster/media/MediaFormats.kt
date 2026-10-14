package io.github.freevideocaster.media

/** Traduce tipos MIME y extensiones al "streamFormat" que entiende el canal del Roku. */
object MediaFormats {

    fun fromContentType(contentType: String?): String? = when (contentType?.lowercase()) {
        "video/mp4", "video/quicktime", "video/x-m4v" -> "mp4"
        "video/x-matroska" -> "mkv"
        "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl" -> "hls"
        "application/dash+xml" -> "dash"
        else -> null
    }

    fun fromFileName(name: String?): String? {
        val extension = name.orEmpty().substringBefore('?').substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp4", "m4v", "mov" -> "mp4"
            "mkv" -> "mkv"
            "m3u8" -> "hls"
            "mpd" -> "dash"
            else -> null
        }
    }

    fun fromUrl(url: String): String? =
        fromFileName(url.substringBefore('#').substringBefore('?').substringAfterLast('/'))
}
