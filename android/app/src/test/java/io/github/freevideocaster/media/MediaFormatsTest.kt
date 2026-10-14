package io.github.freevideocaster.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaFormatsTest {

    @Test
    fun formatFromContentType() {
        assertEquals("mkv", MediaFormats.fromContentType("video/x-matroska"))
        assertEquals("mp4", MediaFormats.fromContentType("video/MP4"))
        assertEquals("hls", MediaFormats.fromContentType("application/vnd.apple.mpegurl"))
        assertNull(MediaFormats.fromContentType("application/octet-stream"))
    }

    @Test
    fun formatFromUrlIgnoresQueryAndFragment() {
        assertEquals("mkv", MediaFormats.fromUrl("https://cdn.example/x/Peli.2024.mkv?token=abc.mp4x"))
        assertEquals("hls", MediaFormats.fromUrl("https://cdn.example/live/index.m3u8#t=10"))
        assertNull(MediaFormats.fromUrl("https://cdn.example/dl/7c1e9a"))
    }

    @Test
    fun urlIsExtractedFromSharedText() {
        assertEquals(
            "https://example.com/v.mp4?a=1",
            IncomingIntentParser.extractUrl("Mira esto (https://example.com/v.mp4?a=1)."),
        )
        assertNull(IncomingIntentParser.extractUrl("sin enlace"))
    }
}
