package io.github.freevideocaster.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UrlRedactorTest {

    private val hidden = UrlRedactor.HIDDEN

    // «{"apiKey":"CLAVE-FALSA"}» en base64: así viaja la clave dentro de la ruta del enlace de Stremio.
    private val fakeBase64 = "eyJhcGlLZXkiOiJDTEFWRS1GQUxTQSIsInNlcnZpY2UiOiJ0b3Jib3gifQ"

    @Test
    fun hidesBase64SegmentOfStremioLink() {
        val url = "https://addon.example.com/$fakeBase64/stream/tt0000000:1:8/file.mkv?season=1&episode=8"
        assertEquals(
            "https://addon.example.com/$hidden/stream/tt0000000:1:8/file.mkv?season=1&episode=8",
            UrlRedactor.redactUrl(url),
        )
    }

    @Test
    fun hidesTokenInQueryButKeepsTheRest() {
        assertEquals(
            "https://store.example.com/v1/stream?token=$hidden&id=9&season=2",
            UrlRedactor.redactUrl("https://store.example.com/v1/stream?token=abc-123&id=9&season=2"),
        )
        assertEquals(
            "https://x.example.com/v?apiKey=$hidden&Signature=$hidden&part=1",
            UrlRedactor.redactUrl("https://x.example.com/v?apiKey=SECRETO&Signature=zzz&part=1"),
        )
    }

    @Test
    fun hidesLongOpaqueQueryValues() {
        val value = "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6"
        assertEquals("https://x.example.com/v?id=$hidden", UrlRedactor.redactUrl("https://x.example.com/v?id=$value"))
    }

    @Test
    fun hidesUserInfo() {
        assertEquals("http://$hidden@192.168.1.5:8080/v.mp4", UrlRedactor.redactUrl("http://usuario:clave@192.168.1.5:8080/v.mp4"))
    }

    @Test
    fun keepsOrdinaryLinksAndFileNames() {
        val url = "https://cdn.example.com/video/Serie.S01E03.1080p.WEB-DL.x265-GRUPO.mkv?season=1&episode=3#t=10"
        assertEquals(url, UrlRedactor.redactUrl(url))
        val short = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        assertEquals(short, UrlRedactor.redactUrl(short))
    }

    @Test
    fun redactsEveryLinkInsideText() {
        val text = "[https://a.example.com/$fakeBase64/x, https://b.example.com/y?token=zz] (String)"
        val result = UrlRedactor.redactUrls(text)
        assertFalse(result.contains(fakeBase64))
        assertFalse(result.contains("token=zz"))
        assertEquals("[https://a.example.com/$hidden/x, https://b.example.com/y?token=$hidden] (String)", result)
    }

    @Test
    fun leavesNonLinkTextAlone() {
        assertEquals("android.intent.action.VIEW", UrlRedactor.redactUrls("android.intent.action.VIEW"))
        assertEquals("video/* (String)", UrlRedactor.redactUrls("video/* (String)"))
    }

    @Test
    fun isIdempotent() {
        val once = UrlRedactor.redactUrl("https://a.example.com/$fakeBase64/s?token=zz")
        assertEquals(once, UrlRedactor.redactUrl(once))
    }
}
