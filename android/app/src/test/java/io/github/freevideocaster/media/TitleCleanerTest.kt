package io.github.freevideocaster.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleCleanerTest {

    private fun check(raw: String, title: String, detail: String) {
        val parsed = TitleCleaner.parse(raw)
        assertEquals("título de «$raw»", title, parsed.title)
        assertEquals("detalle de «$raw»", detail, parsed.detail)
    }

    @Test
    fun movieWithYearAndLanguages() =
        check("Exit.8.2025.WEB-DL.1080p-Dual-Lat.mkv", "Exit 8", "2025 · 1080p · WEB-DL · Dual · Lat")

    @Test
    fun movieWithCodecAndAudio() =
        check("Cry-Baby.1990.1080p.BluRay.H264.AAC-RARBG.mp4", "Cry-Baby", "1990 · 1080p · BluRay · H.264 · AAC")

    @Test
    fun bracketedReleaseGroupAndParenthesizedYear() =
        check(
            "Cry-Baby (1990) [BDMux720p Ita-Eng-Spa-Por] by Pitt@Sk8.mkv",
            "Cry-Baby",
            "1990 · 720p · BDMux · Ita · Eng · Spa",
        )

    @Test
    fun seriesEpisodeIsKeptInTheTitle() =
        check("Show.Name.S02E05.720p.HDTV.x264-GRP.mkv", "Show Name S02E05", "720p · HDTV · H.264")

    @Test
    fun episodeNumbersArePadded() = check("Show s1e3 1080p WEB-DL", "Show S01E03", "1080p · WEB-DL")

    @Test
    fun uhdRemuxKeepsTheMainBadges() =
        check(
            "The.Matrix.1999.2160p.UHD.BluRay.x265.10bit.HDR.DDP5.1.Atmos-GRP.mkv",
            "The Matrix",
            "1999 · 4K · BluRay · HEVC · DDP5.1 · HDR",
        )

    @Test
    fun titleThatContainsAYear() =
        check("Blade.Runner.2049.2017.1080p.WEB-DL.mkv", "Blade Runner 2049", "2017 · 1080p · WEB-DL")

    @Test
    fun titleThatIsAYear() = check("1917.2019.1080p.BluRay.mkv", "1917", "2019 · 1080p · BluRay")

    @Test
    fun yearWithoutMarkersIsNotTreatedAsYear() = check("Movie 1917", "Movie 1917", "")

    @Test
    fun parenthesizedYearWithoutMarkers() = check("Película: ñandú (2020)", "Película: ñandú", "2020")

    @Test
    fun streamingManifestExtensionsAreDropped() {
        check("x36xhzz.m3u8", "x36xhzz", "")
        check("Serie.S01E02.720p.HDTV.mpd", "Serie S01E02", "720p · HDTV")
    }

    @Test
    fun alreadyCleanTitlesAreUntouched() {
        check("Breaking Bad", "Breaking Bad", "")
        check("Exit 8", "Exit 8", "")
        check("Up", "Up", "")
    }

    @Test
    fun uselessTitlesAreDetected() {
        listOf(
            "", " ", "n", "playback", "Video", "a1b2c3d4e5f6a7b8c9d0", "12345", null,
            // Título que pone el navegador a una página que es solo un video.
            "test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_5MB.mp4",
            "https://cdn.example/v.mp4",
        ).forEach {
            assertTrue("«$it» debería ser inútil", TitleCleaner.isUseless(it))
        }
        listOf("Exit 8", "Up", "Cry-Baby", "300 Spartans", "Película 1/2", "Serie.S01E03.1080p.WEB-DL.mkv").forEach {
            assertFalse("«$it» debería servir", TitleCleaner.isUseless(it))
        }
    }

    @Test
    fun searchDataComesFromTheName() {
        val episode = TitleCleaner.parse("Mi.Serie.S01E08.1080p.WEB-DL.mkv")
        assertEquals("Mi Serie", episode.searchTitle)
        assertEquals(1, episode.season)
        assertEquals(8, episode.episode)
        assertNull(episode.year)

        val movie = TitleCleaner.parse("Dune.2021.1080p.BluRay.mkv")
        assertEquals("Dune", movie.searchTitle)
        assertEquals(2021, movie.year)
        assertNull(movie.season)
        assertNull(movie.episode)

        assertEquals("Show", TitleCleaner.parse("Show 2x05 720p").searchTitle)
        assertEquals(5, TitleCleaner.parse("Show 2x05 720p").episode)
    }

    @Test
    fun nameFromUrlDecodesTheLastSegment() {
        assertEquals("Mi Video.mkv", TitleCleaner.nameFromUrl("https://cdn.example/dl/Mi%20Video.mkv?token=abc#x"))
        assertEquals("playback", TitleCleaner.nameFromUrl("https://host.example/a/b/playback"))
        assertNull(TitleCleaner.nameFromUrl("https://host.example/"))
    }
}
