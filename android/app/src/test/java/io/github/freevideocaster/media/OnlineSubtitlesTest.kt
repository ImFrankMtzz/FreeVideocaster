package io.github.freevideocaster.media

import io.github.freevideocaster.media.OnlineSubtitles.Candidate
import io.github.freevideocaster.media.OnlineSubtitles.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineSubtitlesTest {

    private fun candidate(language: String, release: String, url: String = "https://subs.example/$language/${release.hashCode()}") =
        Candidate(language, url, release)

    @Test
    fun imdbIdTakesTheFirstResultWhenThereIsNoYear() {
        val json = """{"metas":[{"id":"tt1","name":"A","releaseInfo":"2022-"},{"id":"tt2","name":"B","releaseInfo":"1999"}]}"""
        assertEquals("tt1", OnlineSubtitles.imdbIdFrom(json, null))
    }

    @Test
    fun imdbIdPrefersTheResultFromTheSameYear() {
        val json = """{"metas":[{"id":"tt1","name":"Dune","releaseInfo":"1984"},{"id":"tt2","name":"Dune","releaseInfo":"2021"}]}"""
        assertEquals("tt2", OnlineSubtitles.imdbIdFrom(json, 2021))
        // Un año que no aparece no descarta el resultado más relevante.
        assertEquals("tt1", OnlineSubtitles.imdbIdFrom(json, 2005))
    }

    @Test
    fun imdbIdIgnoresResultsThatAreNotImdbTitlesAndEmptyAnswers() {
        assertEquals("tt9", OnlineSubtitles.imdbIdFrom("""{"metas":[{"id":"kitsu:5"},{"id":"tt9"}]}""", null))
        assertNull(OnlineSubtitles.imdbIdFrom("""{"metas":[]}""", 2020))
        assertNull(OnlineSubtitles.imdbIdFrom("""{}""", null))
    }

    @Test
    fun candidatesKeepOnlySecureLinksAndFallBackToTheFileName() {
        val json = """{"subtitles":[
            {"lang":"spa","url":"https://s.example/1","movieReleaseName":"Serie.S01E01.1080p"},
            {"lang":"eng","url":"http://s.example/2","movieReleaseName":"inseguro"},
            {"lang":"eng","url":"https://s.example/3","subtitleFileName":"Serie.S01E01.srt"}
        ]}"""
        assertEquals(
            listOf(
                Candidate("spa", "https://s.example/1", "Serie.S01E01.1080p"),
                Candidate("eng", "https://s.example/3", "Serie.S01E01.srt"),
            ),
            OnlineSubtitles.candidatesFrom(json),
        )
        assertEquals(emptyList<Candidate>(), OnlineSubtitles.candidatesFrom("{}"))
    }

    @Test
    fun chooseOffersSpanishThenEnglishAndIgnoresOtherLanguages() {
        val result = OnlineSubtitles.choose(
            listOf(candidate("fre", "Serie.S01E01"), candidate("eng", "Serie.S01E01"), candidate("spa", "Serie.S01E01")),
            "Serie.S01E01.1080p",
        )
        assertEquals(listOf("spa", "eng"), result.map { it.language })
        assertEquals(listOf("En línea 1", "En línea 1"), result.map { it.label })
    }

    @Test
    fun chooseKeepsAtMostTwoPerLanguageAndNumbersThem() {
        val result = OnlineSubtitles.choose(List(5) { candidate("spa", "Serie.S01E01.v$it") }, "Serie.S01E01")
        assertEquals(listOf("En línea 1", "En línea 2"), result.map { it.label })
    }

    @Test
    fun chooseRanksTheVersionMostLikeTheFileFirst() {
        val generic = candidate("spa", "Serie.S01E01.HDTV.x264-AAA", "https://s.example/generic")
        val sameGroup = candidate("spa", "Serie.S01E01.1080p.WEB-DL.x265-TEPES", "https://s.example/match")
        val result = OnlineSubtitles.choose(listOf(generic, sameGroup), "Serie.S01E01.1080p.WEB-DL.DDP5.1.x265-TEPES.mkv")
        assertEquals("https://s.example/match", result[0].url)
        assertEquals("https://s.example/generic", result[1].url)
    }

    @Test
    fun chooseKeepsTheServiceOrderWhenNothingDistinguishesThem() {
        val first = candidate("eng", "A", "https://s.example/first")
        val second = candidate("eng", "B", "https://s.example/second")
        assertEquals(listOf("https://s.example/first", "https://s.example/second"), OnlineSubtitles.choose(listOf(first, second), "").map { it.url })
    }

    @Test
    fun chooseDropsDuplicatedLinks() {
        val one = candidate("spa", "A", "https://s.example/same")
        assertEquals(1, OnlineSubtitles.choose(listOf(one, one.copy(releaseName = "B")), "").size)
    }

    @Test
    fun channelParamHasOneTrackPerLine() {
        val text = OnlineSubtitles.toChannelParam(
            listOf(Subtitle("spa", "En línea 1", "https://s.example/a"), Subtitle("eng", "En línea 1", "https://s.example/b")),
        )
        assertEquals("spa|En línea 1|https://s.example/a\neng|En línea 1|https://s.example/b", text)
        assertEquals("", OnlineSubtitles.toChannelParam(emptyList()))
    }

    @Test
    fun queryIsASeriesOnlyWithSeasonAndEpisode() {
        assertEquals(true, OnlineSubtitles.Query("S", null, 1, 8, "").isSeries)
        assertEquals(false, OnlineSubtitles.Query("S", 2020, null, null, "").isSeries)
        assertEquals(false, OnlineSubtitles.Query("S", null, 1, null, "").isSeries)
    }
}
