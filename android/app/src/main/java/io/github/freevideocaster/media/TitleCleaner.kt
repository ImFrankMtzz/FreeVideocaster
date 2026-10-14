package io.github.freevideocaster.media

import java.net.URLDecoder

/**
 * Convierte nombres de archivo de release («Serie.S01E03.1080p.WEB-DL.DDP5.1.H.264-GRP.mkv»)
 * en un título legible («Serie S01E03») y una línea de detalle («1080p · WEB-DL · H.264 · DDP5.1»).
 */
object TitleCleaner {

    /**
     * [searchTitle] es el título sin la marca de episodio; [year], [season] y [episode] salen del nombre
     * cuando están (sirven para buscar subtítulos).
     */
    data class Parsed(
        val title: String,
        val detail: String,
        val searchTitle: String = title,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
    )

    private val IGNORE = RegexOption.IGNORE_CASE

    private val EXTENSION = Regex("""\.(mkv|mp4|m4v|mov|avi|webm|ts|m2ts|wmv|flv|mpe?g|m3u8?|mpd)$""", IGNORE)
    private val EPISODE = Regex("""(?<![A-Za-z0-9])[Ss](\d{1,2})[ ._-]?[Ee](\d{1,3})(?!\d)""")
    private val EPISODE_X = Regex("""(?<![A-Za-z0-9])(\d{1,2})x(\d{2,3})(?![A-Za-z0-9])""")
    private val RESOLUTION = Regex(
        """(?<!\d)(2160|1440|1080|720|576|480)[pi](?![A-Za-z])|(?<![A-Za-z0-9])(4K|UHD)(?![A-Za-z0-9])""",
        IGNORE,
    )
    private val SOURCE = Regex(
        """(?<![A-Za-z])(WEB[ ._-]?DL|WEB[ ._-]?Rip|Blu[ ._-]?Ray|BDRip|BRRip|BDMux|HDRip|HDTV|DVDRip|REMUX|HDCAM)(?![A-Za-z])""",
        IGNORE,
    )
    private val CODEC = Regex("""(?<![A-Za-z0-9])([xh][ .]?26[45]|HEVC|AV1|XviD|DivX)(?![A-Za-z0-9])""", IGNORE)
    private val AUDIO = Regex(
        """(?<![A-Za-z0-9])(DDP?\d(?:\.\d)?|DD\+|E-?AC-?3|AC-?3|AAC\d?(?:\.\d)?|DTS(?:-HD)?(?:[ .]MA)?|TrueHD|Atmos|FLAC)(?![A-Za-z0-9])""",
        IGNORE,
    )
    private val HDR = Regex("""(?<![A-Za-z0-9])(HDR10\+?|HDR|DV|Dolby[ .]Vision)(?![A-Za-z0-9])""", IGNORE)
    private val LANGUAGE = Regex(
        """(?<![A-Za-z0-9])(Dual|Multi|Latino|Lat|Castellano|Spanish|Spa|English|Eng|Ita|Por|Fre|Ger)(?![A-Za-z0-9])""",
        IGNORE,
    )
    private val YEAR = Regex("""(?<![A-Za-z0-9])(19\d\d|20\d\d)(?![A-Za-z0-9])""")
    private val LEADING_GROUPS = Regex("""^\s*(\[[^\]]*]\s*)+""")
    private val HASH_LIKE = Regex("""^[0-9a-fA-F-]{16,}$""")

    private val USELESS = setOf(
        "playback", "video", "stream", "download", "play", "index", "master", "manifest",
        "null", "file", "movie", "watch", "videoplayback", "unknown",
    )

    /** true si el texto no sirve como título (vacío, una letra, «playback», un hash, una dirección web…). */
    fun isUseless(text: String?): Boolean {
        val clean = text?.trim().orEmpty()
        // El navegador usa la dirección como título de las páginas que son solo un video.
        val looksLikeAddress = "://" in clean || (clean.none { it.isWhitespace() } && '/' in clean)
        return clean.length < 2 ||
            looksLikeAddress ||
            clean.lowercase() in USELESS ||
            HASH_LIKE.matches(clean) ||
            clean.none { it.isLetter() }
    }

    fun parse(raw: String): Parsed {
        val name = raw.trim().replace(EXTENSION, "")
        if (name.isEmpty()) return Parsed("", "")

        val bracket = name.indexOf('[').takeIf { it >= 0 }
        val firstQuality = listOfNotNull(
            RESOLUTION.find(name)?.range?.first,
            SOURCE.find(name)?.range?.first,
            CODEC.find(name)?.range?.first,
            bracket,
        ).minOrNull()
        val episode = EPISODE.find(name)
        val episodeX = EPISODE_X.find(name)
        val markerEnd = listOfNotNull(firstQuality, episode?.range?.first, episodeX?.range?.first).minOrNull()

        // El año cuenta si va antes del primer marcador (o entre paréntesis), y nunca al inicio del nombre.
        val limit = markerEnd ?: name.length
        val year = YEAR.findAll(name)
            .filter { it.range.first > 0 && it.range.last < limit }
            .lastOrNull()
            ?.takeIf { markerEnd != null || isParenthesized(name, it) }

        var titleEnd = markerEnd ?: name.length
        if (year != null) {
            var start = year.range.first
            if (name[start - 1] == '(') start--
            titleEnd = minOf(titleEnd, start)
        }

        var title = normalize(name.substring(0, titleEnd))
        if (title.isEmpty()) title = normalize(name)

        val episodeMatch = episode ?: episodeX
        val episodeLabel = episodeMatch?.let { match ->
            "S%02dE%02d".format(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
        val searchTitle = title.trim()
        if (episodeLabel != null) title = "$title $episodeLabel".trim()

        val region = if (markerEnd != null) name.substring(markerEnd) else ""
        val badges = buildList {
            year?.let { add(it.value) }
            RESOLUTION.find(region)?.let { add(resolutionLabel(it)) }
            SOURCE.find(region)?.let { add(sourceLabel(it.value)) }
            CODEC.find(region)?.let { add(codecLabel(it.value)) }
            AUDIO.find(region)?.let { add(audioLabel(it.value)) }
            HDR.find(region)?.let { add(hdrLabel(it.value)) }
            addAll(
                LANGUAGE.findAll(region)
                    .map { it.value.lowercase().replaceFirstChar(Char::uppercase) }
                    .distinct()
                    .take(3),
            )
        }
        return Parsed(
            title = title,
            detail = badges.joinToString(" · "),
            searchTitle = searchTitle,
            year = year?.value?.toInt(),
            season = episodeMatch?.groupValues?.get(1)?.toInt(),
            episode = episodeMatch?.groupValues?.get(2)?.toInt(),
        )
    }

    /** Último tramo de una URL, decodificado («…/Mi%20Video.mkv?x=1» → «Mi Video.mkv»). */
    fun nameFromUrl(url: String): String? {
        // Se quita el esquema y el servidor: «https://host/» no tiene nombre de archivo.
        val path = url.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "")
        val segment = path.trimEnd('/').substringAfterLast('/')
        if (segment.isBlank()) return null
        return runCatching { URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)
    }

    private fun isParenthesized(name: String, match: MatchResult): Boolean {
        val before = name.getOrNull(match.range.first - 1)
        val after = name.getOrNull(match.range.last + 1)
        return before == '(' && after == ')'
    }

    private fun normalize(text: String): String = text
        .replace(LEADING_GROUPS, "")
        .replace('.', ' ')
        .replace('_', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '(', '[')

    private fun resolutionLabel(match: MatchResult): String {
        val pixels = match.groupValues[1]
        return when {
            pixels.isEmpty() || pixels == "2160" -> "4K"
            else -> "${pixels}p"
        }
    }

    private fun sourceLabel(text: String): String {
        val key = text.lowercase().filter { it.isLetterOrDigit() }
        return when (key) {
            "webdl" -> "WEB-DL"
            "webrip" -> "WEBRip"
            "bluray" -> "BluRay"
            "bdrip" -> "BDRip"
            "brrip" -> "BRRip"
            "bdmux" -> "BDMux"
            "hdrip" -> "HDRip"
            "hdtv" -> "HDTV"
            "dvdrip" -> "DVDRip"
            "remux" -> "REMUX"
            else -> "HDCAM"
        }
    }

    private fun codecLabel(text: String): String {
        val key = text.lowercase()
        return when {
            "265" in key || key == "hevc" -> "HEVC"
            "264" in key -> "H.264"
            key == "av1" -> "AV1"
            key == "xvid" -> "XviD"
            else -> "DivX"
        }
    }

    private fun audioLabel(text: String): String = when (text.lowercase()) {
        "truehd" -> "TrueHD"
        "atmos" -> "Atmos"
        else -> text.uppercase()
    }

    private fun hdrLabel(text: String): String =
        if (text.startsWith("dolby", ignoreCase = true) || text.equals("dv", ignoreCase = true)) "DV" else text.uppercase()
}
