package io.github.freevideocaster.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun `Google es el buscador por defecto`() {
        assertEquals(SearchEngine.Google, AppPrefs().search)
        assertEquals("https://www.google.com", AppPrefs().search.homeUrl)
    }

    @Test
    fun `la busqueda codifica espacios y acentos`() {
        assertEquals(
            "https://www.google.com/search?q=pel%C3%ADculas+gratis+2026",
            SearchEngine.Google.searchFor("películas gratis 2026"),
        )
        assertTrue(SearchEngine.DuckDuckGo.searchFor("a&b").endsWith("q=a%26b"))
    }

    @Test
    fun `los valores por defecto no cambian el aspecto de la app`() {
        val prefs = AppPrefs()
        assertEquals(ThemeMode.System, prefs.theme)
        assertEquals(ColorStyle.Wallpaper, prefs.colors)
        assertEquals(false, prefs.amoled)
        assertEquals(false, prefs.autoSend)
        assertEquals(true, prefs.adBlock)
    }
}
