package io.github.freevideocaster.roku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Lee el estado en vivo del canal FreeVideocaster (solo lectura, puerto 8061). */
class ChannelClient(baseClient: OkHttpClient) {

    private val http = baseClient.newBuilder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    /** null si el canal no está abierto, es una versión antigua o la red falla. */
    suspend fun status(ip: String): ChannelStatus? = withContext(Dispatchers.IO) {
        if (!EcpClient.isPrivateIp(ip)) return@withContext null
        val request = Request.Builder().url("http://$ip:$PORT/status").build()
        try {
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) ChannelStatus.parse(response.body.string()) else null
            }
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        const val PORT = 8061
    }
}
