package io.github.freevideocaster.media

/**
 * Oculta las claves que llevan los enlaces antes de mostrarlos o copiarlos. La URL que manda Stremio trae
 * dentro (en base64, como un tramo de la ruta) la clave de TorBox, y los enlaces finales suelen llevar un
 * `token=` en la consulta. Solo sirve para mostrar: el enlace que se envía al Roku no se toca.
 *
 * Es deliberadamente agresivo: ante la duda se oculta (un tramo largo sin puntos ni espacios casi siempre
 * es una clave o un identificador, no un nombre de archivo).
 */
object UrlRedactor {

    const val HIDDEN = "«oculto»"

    /** Cambia cada enlace http(s) que haya dentro de [text] por su versión sin claves. */
    fun redactUrls(text: String): String = URL_IN_TEXT.replace(text) { match ->
        // La puntuación pegada al final («…, » o «…]») no es parte del enlace: se conserva tal cual.
        val link = match.value.trimEnd(*TRAILING)
        redactUrl(link) + match.value.substring(link.length)
    }

    fun redactUrl(url: String): String {
        val match = URL_PARTS.matchEntire(url) ?: return url
        val (scheme, authority, path, query, fragment) = match.destructured

        // «usuario:clave@servidor»: se conserva solo el servidor.
        val safeAuthority = if ('@' in authority) "$HIDDEN@${authority.substringAfterLast('@')}" else authority

        val safePath = path.split('/').joinToString("/") { if (looksSecret(it)) HIDDEN else it }

        val safeQuery = if (query.isEmpty()) "" else "?" + query.drop(1).split('&').joinToString("&") { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            when {
                !pair.contains('=') -> if (looksSecret(pair)) HIDDEN else pair
                SENSITIVE_NAME.containsMatchIn(name) || looksSecret(value) -> "$name=$HIDDEN"
                else -> pair
            }
        }

        return "$scheme$safeAuthority$safePath$safeQuery$fragment"
    }

    /** Tramo con aspecto de clave: largo y solo con caracteres de base64/hexadecimal, sin puntos ni espacios. */
    private fun looksSecret(segment: String): Boolean =
        segment != HIDDEN && BASE64_LIKE.matches(segment) &&
            (segment.length >= LONG_SEGMENT || (segment.startsWith("eyJ") && segment.length >= JSON_PREFIX_MIN))

    private const val LONG_SEGMENT = 32
    private const val JSON_PREFIX_MIN = 16

    private val TRAILING = charArrayOf(',', ';', '.', ')', ']', '}', '"', '\'')

    private val URL_IN_TEXT = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val URL_PARTS = Regex("""(https?://)([^/?#]*)([^?#]*)(\?[^#]*)?(#.*)?""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BASE64_LIKE = Regex("""[A-Za-z0-9_\-+=%]+""")
    private val SENSITIVE_NAME =
        Regex("""token|key|auth|secret|sig|pass|pwd|session|cookie|jwt|credential|bearer""", RegexOption.IGNORE_CASE)
}
