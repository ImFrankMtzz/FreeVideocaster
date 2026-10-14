package io.github.freevideocaster.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Busca subtítulos en línea con los mismos servicios públicos que usa Stremio, sin cuenta ni clave:
 * Cinemeta (para saber qué película o serie es) y el complemento OpenSubtitles v3 (para los archivos .srt).
 * Solo se envía el título (y año, temporada y episodio) de lo que se va a ver. Nunca lanza errores: si algo
 * falla devuelve una lista vacía y el video se envía igual.
 */
class OnlineSubtitles(baseClient: OkHttpClient) {

    // Cada petición tiene su propio límite: el bloqueo de OkHttp no se interrumpe al cancelar la corrutina.
    private val http = baseClient.newBuilder().callTimeout(5, java.util.concurrent.TimeUnit.SECONDS).build()

    /** Lo que se sabe del video. [releaseName] es el nombre del archivo, para elegir la versión que mejor encaja. */
    data class Query(
        val title: String,
        val year: Int?,
        val season: Int?,
        val episode: Int?,
        val releaseName: String,
    ) {
        val isSeries: Boolean get() = season != null && episode != null
    }

    /** Un subtítulo listo para enviar al Roku. [language] es el código de 3 letras (spa, eng). */
    data class Subtitle(val language: String, val label: String, val url: String)

    internal data class Candidate(val language: String, val url: String, val releaseName: String)

    private val cache = HashMap<Query, List<Subtitle>>()

    suspend fun find(query: Query): List<Subtitle> {
        if (query.title.isBlank()) return emptyList()
        synchronized(cache) { cache[query] }?.let { return it }
        val found = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching { search(query) }.getOrDefault(emptyList())
        }.orEmpty()
        // Solo se recuerdan las búsquedas con resultado: un fallo de red no debe repetirse para siempre.
        if (found.isNotEmpty()) synchronized(cache) { cache[query] = found }
        return found
    }

    private suspend fun search(query: Query): List<Subtitle> = withContext(Dispatchers.IO) {
        val type = if (query.isSeries) "series" else "movie"
        val encoded = URLEncoder.encode(query.title, "UTF-8").replace("+", "%20")
        val imdb = imdbIdFrom(get("$CINEMETA/catalog/$type/top/search=$encoded.json"), query.year)
            ?: return@withContext emptyList()
        val id = if (query.isSeries) "$imdb:${query.season}:${query.episode}" else imdb
        choose(candidatesFrom(get("$OPENSUBTITLES/subtitles/$type/$id.json")), query.releaseName)
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body.string()
        }
    }

    companion object {
        private const val CINEMETA = "https://v3-cinemeta.strem.io"
        private const val OPENSUBTITLES = "https://opensubtitles-v3.strem.io"
        private const val TIMEOUT_MS = 9000L

        /** Idiomas que se ofrecen, en este orden, y cuántas versiones de cada uno. */
        private val LANGUAGES = listOf("spa", "eng")
        private const val PER_LANGUAGE = 2

        /**
         * Identificador de IMDb del mejor resultado de Cinemeta. Si se sabe el año, gana el primero que
         * coincide; si ninguno coincide (o no hay año) se toma el primero, que es el más relevante.
         */
        internal fun imdbIdFrom(json: String, year: Int?): String? {
            val metas = JSONObject(json).optJSONArray("metas") ?: return null
            val items = (0 until metas.length()).map { metas.getJSONObject(it) }
                .filter { it.optString("id").startsWith("tt") }
            val byYear = year?.let { y -> items.firstOrNull { it.optString("releaseInfo").take(4) == y.toString() } }
            return (byYear ?: items.firstOrNull())?.optString("id")
        }

        internal fun candidatesFrom(json: String): List<Candidate> {
            val list = JSONObject(json).optJSONArray("subtitles") ?: return emptyList()
            return (0 until list.length()).mapNotNull { index ->
                val item = list.getJSONObject(index)
                val url = item.optString("url")
                if (!url.startsWith("https://")) return@mapNotNull null
                Candidate(
                    language = item.optString("lang"),
                    url = url,
                    releaseName = item.optString("movieReleaseName").ifBlank { item.optString("subtitleFileName") },
                )
            }
        }

        /**
         * Hasta [PER_LANGUAGE] subtítulos por idioma. Primero los que más se parecen al nombre del archivo
         * (misma resolución, origen, códec y grupo, que es lo que suele decidir si quedan sincronizados);
         * a igualdad se respeta el orden del servicio.
         */
        internal fun choose(candidates: List<Candidate>, releaseName: String): List<Subtitle> {
            val wanted = tokens(releaseName)
            return LANGUAGES.flatMap { language ->
                candidates.asSequence()
                    .filter { it.language == language }
                    .distinctBy { it.url }
                    .withIndex()
                    .sortedWith(compareByDescending<IndexedValue<Candidate>> { similarity(wanted, it.value.releaseName) }.thenBy { it.index })
                    .take(PER_LANGUAGE)
                    .mapIndexed { n, item -> Subtitle(language, "En línea ${n + 1}", item.value.url) }
                    .toList()
            }
        }

        private fun similarity(wanted: Set<String>, releaseName: String): Int =
            tokens(releaseName).count { it in wanted }

        private val GENERIC = setOf("srt", "sub", "mkv", "mp4", "the", "and", "de", "la", "el", "en", "es", "eng", "spa", "spanish", "english")

        private fun tokens(text: String): Set<String> =
            text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 && it !in GENERIC }.toSet()

        /** Formato que entiende el canal: «idioma|nombre|url», una pista por línea. */
        fun toChannelParam(subtitles: List<Subtitle>): String =
            subtitles.joinToString("\n") { "${it.language}|${it.label}|${it.url}" }
    }
}
