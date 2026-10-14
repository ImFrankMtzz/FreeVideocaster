package io.github.freevideocaster.roku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class EcpClientTest {

    @Test
    fun privateIpsAreAccepted() {
        listOf("192.168.1.50", "10.0.0.2", "172.16.0.1", "172.31.255.254").forEach {
            assertTrue(it, EcpClient.isPrivateIp(it))
        }
    }

    @Test
    fun publicOrInvalidIpsAreRejected() {
        listOf("8.8.8.8", "172.32.0.1", "192.169.1.1", "192.168.1.256", "roku.local", "", "192.168.1").forEach {
            assertFalse(it, EcpClient.isPrivateIp(it))
        }
    }

    @Test
    fun shortRequestIsSentInOnePiece() {
        val params = mapOf("action" to "play", "url" to "https://example.com/a.mp4", "title" to "Prueba")
        val paths = EcpClient.inputPaths(params)
        assertEquals(1, paths.size)
        assertEquals(params, rokuReceives(paths))
    }

    @Test
    fun longRequestIsChunkedAndRebuiltExactly() {
        val url = "https://store-031.weur.tb-cdn.st/dl/abc123/Serie.S01E03.1080p.WEB-DL.DDP5.1.H.264-GRP%20[x].mkv" +
            "?token=" + "aZ9+/=&?".repeat(40) + "&expires=1760000000&name=Episodio ñ 3"
        val params = mapOf(
            "action" to "play",
            "url" to url,
            "title" to "Episodio 3: «Prueba» & más",
            "headers" to "Referer: https://sitio.example/ver?id=1\nCookie: a=1; b=2",
        )
        val paths = EcpClient.inputPaths(params, id = "test1")
        assertTrue("Debería trocearse", paths.size > 1)
        assertTrue("Cada trozo debe ser corto", paths.all { decodedValues(it).values.all { v -> v.length <= 255 } })
        assertEquals(params, rokuReceives(paths))
    }

    /**
     * Simula el camino completo: Roku decodifica cada parámetro de la petición ECP,
     * y si hay trozos, el canal los une y vuelve a decodificar la consulta (main.brs: AddChunk + ParseQuery).
     */
    private fun rokuReceives(paths: List<String>): Map<String, String> {
        val messages = paths.map { decodedValues(it) }
        if (messages.size == 1 && "chunk" !in messages[0]) return messages[0]

        val total = messages.first().getValue("n").toInt()
        val query = messages.sortedBy { it.getValue("i").toInt() }.joinToString("") { it.getValue("chunk") }
        assertEquals(total, messages.size)
        return parseQuery(query)
    }

    private fun decodedValues(path: String): Map<String, String> = parseQuery(path.substringAfter('?'))

    private fun parseQuery(query: String): Map<String, String> = query.split("&").associate { pair ->
        val key = pair.substringBefore("=")
        val value = pair.substringAfter("=", "")
        URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
    }
}
