package io.github.freevideocaster.roku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelStatusTest {

    @Test
    fun parsesWhatTheRealChannelSends() {
        // Respuesta real del canal v2 en el Roku (claves en minúsculas, texto con escapes \u).
        val json = """{"duration":634000,"error":"","key":"abc123","position":96120,
            "queue":[{"key":"kA","title":"Cola-A"},{"key":"kB","title":"Día de ñandú"}],
            "seq":3,"state":"play","title":"Prueba HLS","v":2}"""
        val status = ChannelStatus.parse(json)!!
        assertEquals(Playback.Playing, status.playback)
        assertEquals("abc123", status.key)
        assertEquals(96_120L, status.positionMs)
        assertEquals(634_000L, status.durationMs)
        assertEquals(3, status.seq)
        assertEquals(listOf(QueueItem("kA", "Cola-A"), QueueItem("kB", "Día de ñandú")), status.queue)
    }

    @Test
    fun mapsEveryState() {
        fun state(name: String) = ChannelStatus.parse("""{"v":2,"state":"$name"}""")!!.playback
        assertEquals(Playback.Playing, state("play"))
        assertEquals(Playback.Paused, state("pause"))
        assertEquals(Playback.Buffering, state("buffering"))
        assertEquals(Playback.Idle, state("idle"))
        assertEquals(Playback.Idle, state("algo-raro"))
    }

    @Test
    fun idleStatusCarriesTheErrorMessage() {
        val status = ChannelStatus.parse("""{"v":2,"state":"idle","error":"Código -1. Error HTTP","seq":1,"queue":[]}""")!!
        assertEquals("Código -1. Error HTTP", status.error)
        assertEquals(emptyList<QueueItem>(), status.queue)
    }

    @Test
    fun parsesAudioAndSubtitleTracks() {
        // Respuesta real del Roku con un video de prueba de varios idiomas.
        val json = """{"v":2,"state":"play","audiocur":"2","subcur":"webvtt/5",
            "audio":[{"id":"1","lang":"eng","name":"BipBop Audio 1"},{"id":"2","lang":"eng","name":"BipBop Audio 2"}],
            "subs":[{"id":"webvtt/1","lang":"en","name":"English"},{"id":"webvtt/5","lang":"es","name":"Espa\u00F1ol"},{"id":"","lang":"xx","name":"sin id"}]}"""
        val status = ChannelStatus.parse(json)!!
        assertEquals(listOf(Track("1", "eng", "BipBop Audio 1"), Track("2", "eng", "BipBop Audio 2")), status.audio)
        assertEquals("2", status.audioCurrent)
        // Una pista sin identificador no se puede elegir y se descarta.
        assertEquals(listOf(Track("webvtt/1", "en", "English"), Track("webvtt/5", "es", "Español")), status.subtitles)
        assertEquals("webvtt/5", status.subtitleCurrent)
    }

    @Test
    fun statusWithoutTracksHasEmptyLists() {
        val status = ChannelStatus.parse("""{"v":2,"state":"idle"}""")!!
        assertEquals(emptyList<Track>(), status.audio)
        assertEquals("", status.subtitleCurrent)
    }

    @Test
    fun rejectsAnythingThatIsNotChannelV2() {
        assertNull(ChannelStatus.parse("""{"state":"play"}"""))
        assertNull(ChannelStatus.parse("""{"v":1,"state":"play"}"""))
        assertNull(ChannelStatus.parse("<html>no es json</html>"))
        assertNull(ChannelStatus.parse(""))
    }
}
