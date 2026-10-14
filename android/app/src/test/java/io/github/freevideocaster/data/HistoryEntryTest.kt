package io.github.freevideocaster.data

import io.github.freevideocaster.media.MediaRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryEntryTest {

    private fun entry(position: Long, duration: Long, url: String = "https://example.com/v") = HistoryEntry(
        title = "Video", url = url, subtitleUrl = null, headers = emptyMap(), timestamp = 0,
        positionMs = position, durationMs = duration,
    )

    @Test
    fun resumeNeedsAMeaningfulPosition() {
        assertEquals("muy al inicio no se ofrece", 0L, entry(10_000, 600_000).resumeMs)
        assertEquals(120_000L, entry(120_000, 600_000).resumeMs)
    }

    @Test
    fun resumeIsNotOfferedWhenAlmostFinished() {
        assertEquals(0L, entry(580_000, 600_000).resumeMs)
        assertEquals(500_000L, entry(500_000, 600_000).resumeMs)
    }

    @Test
    fun resumeWorksWhenDurationIsUnknown() {
        assertEquals(90_000L, entry(90_000, 0).resumeMs)
    }

    @Test
    fun keyIsStableAndShort() {
        val a = MediaRequest.keyOf("https://example.com/v")
        assertEquals(a, MediaRequest.keyOf("https://example.com/v"))
        assertNotEquals(a, MediaRequest.keyOf("https://example.com/w"))
        assertEquals(12, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
        assertEquals(a, entry(0, 0).key)
        assertEquals(a, MediaRequest(url = "https://example.com/v").key)
    }

    @Test
    fun toRequestKeepsEverythingNeededToResend() {
        val saved = entry(0, 0).copy(title = "Exit 8", detail = "2025 · 1080p", subtitleUrl = "https://s/x.srt", headers = mapOf("Referer" to "r"))
        val request = saved.toRequest()
        assertEquals("Exit 8", request.title)
        assertEquals("2025 · 1080p", request.detail)
        assertEquals("https://s/x.srt", request.subtitleUrl)
        assertEquals(mapOf("Referer" to "r"), request.headers)
    }
}
