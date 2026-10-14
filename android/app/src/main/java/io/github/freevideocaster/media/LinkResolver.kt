package io.github.freevideocaster.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLDecoder

/**
 * Antes de enviar un enlace, lo "abre" pidiendo solo el primer byte:
 * sigue las redirecciones (los enlaces de Stremio/TorBox suelen tenerlas) y lee
 * el tipo de archivo y su nombre, porque la URL muchas veces no tiene extensión.
 */
class LinkResolver(private val http: OkHttpClient) {

    data class Resolved(
        val finalUrl: String,
        val httpCode: Int?,
        val format: String?,
        val fileName: String?,
        val contentType: String? = null,
    ) {
        /** true si el enlace lleva a una página web y no a un video. */
        val isWebPage get() = contentType?.startsWith("text/html", ignoreCase = true) == true
    }

    suspend fun resolve(request: MediaRequest): Resolved = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(request.url).header("Range", "bytes=0-0")
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        try {
            http.newCall(builder.build()).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type")?.substringBefore(';')?.trim()
                val fileName = fileNameFrom(response.header("Content-Disposition"))
                Resolved(
                    finalUrl = finalUrl,
                    httpCode = response.code,
                    format = MediaFormats.fromContentType(contentType)
                        ?: MediaFormats.fromFileName(fileName)
                        ?: MediaFormats.fromUrl(finalUrl),
                    fileName = fileName,
                    contentType = contentType,
                )
            }
        } catch (e: IOException) {
            // Si no se puede comprobar (red lenta, servidor raro), se envía tal cual y que decida el Roku.
            Resolved(request.url, null, MediaFormats.fromUrl(request.url), null)
        } catch (e: IllegalArgumentException) {
            Resolved(request.url, null, MediaFormats.fromUrl(request.url), null)
        }
    }

    private fun fileNameFrom(disposition: String?): String? {
        if (disposition == null) return null
        EXTENDED_NAME.find(disposition)?.let { match ->
            return runCatching { URLDecoder.decode(match.groupValues[1], "UTF-8") }.getOrNull()
        }
        return PLAIN_NAME.find(disposition)?.groupValues?.get(1)?.trim()
    }

    private companion object {
        val EXTENDED_NAME = Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
        val PLAIN_NAME = Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
    }
}
