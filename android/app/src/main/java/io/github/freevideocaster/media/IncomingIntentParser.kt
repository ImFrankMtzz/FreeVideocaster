package io.github.freevideocaster.media

import android.content.Intent
import android.os.Bundle

/**
 * Extrae el video de lo que envía otra app.
 * - "Reproductor externo" (Stremio, navegadores…): ACTION_VIEW con la URL en data.
 * - Menú Compartir: ACTION_SEND con la URL dentro del texto.
 * Además lista todo el contenido del intent para diagnóstico (p. ej., ver si llegan subtítulos).
 */
object IncomingIntentParser {

    data class Parsed(
        val request: MediaRequest?,
        val details: List<Pair<String, String>>,
    )

    fun parse(intent: Intent): Parsed {
        val extras = intent.extras
        // Es lo que se muestra y se copia: los enlaces llevan claves (la de TorBox), así que se ocultan aquí.
        val details = buildList {
            add("action" to (intent.action ?: "-"))
            add("data" to (intent.dataString ?: "-"))
            add("type" to (intent.type ?: "-"))
            extras?.keySet()?.sorted()?.forEach { key -> add("extra: $key" to describe(rawExtra(extras, key))) }
        }.map { (key, value) -> key to UrlRedactor.redactUrls(value) }

        val url = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { it.startsWith("http", ignoreCase = true) }
            Intent.ACTION_SEND -> extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            else -> null
        }
        val request = url?.let {
            MediaRequest(
                url = it,
                title = firstString(extras, "title", Intent.EXTRA_TITLE, "android.intent.extra.SUBJECT").orEmpty(),
                subtitleUrl = subtitleFrom(extras),
                format = MediaFormats.fromContentType(intent.type),
            )
        }
        return Parsed(request, details)
    }

    fun extractUrl(text: String?): String? =
        URL_IN_TEXT.find(text.orEmpty())?.value?.trimEnd(')', ']', '.', ',', '"', '\'')

    /**
     * Subtítulos en los formatos que usan los reproductores más comunes:
     * MX Player ("subs", "subs.enable": arrays de Uri) y VLC ("subtitles_location").
     */
    private fun subtitleFrom(extras: Bundle?): String? {
        if (extras == null) return null
        val candidates = urls(rawExtra(extras, "subs.enable")) +
            urls(rawExtra(extras, "subs")) +
            urls(rawExtra(extras, "subtitles_location"))
        return candidates.firstOrNull()
    }

    private fun urls(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is Array<*> -> value.mapNotNull { it?.toString() }
        is Collection<*> -> value.mapNotNull { it?.toString() }
        else -> listOf(value.toString())
    }.filter { it.startsWith("http", ignoreCase = true) }

    private fun firstString(extras: Bundle?, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> (rawExtra(extras, key) as? CharSequence)?.toString()?.takeIf { it.isNotBlank() } }

    @Suppress("DEPRECATION") // Se necesita leer extras de tipo desconocido.
    private fun rawExtra(extras: Bundle?, key: String): Any? = runCatching { extras?.get(key) }.getOrNull()

    private fun describe(value: Any?): String = when (value) {
        null -> "null"
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]")
        is IntArray -> value.joinToString(prefix = "[", postfix = "]")
        is LongArray -> value.joinToString(prefix = "[", postfix = "]")
        else -> "$value (${value.javaClass.simpleName})"
    }

    private val URL_IN_TEXT = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
}
