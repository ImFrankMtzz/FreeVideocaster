package io.github.freevideocaster.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Pruebas con las listas reales que trae la app (`src/main/assets/filters`). */
class BundledListsTest {

    private val names = listOf("easylist.txt", "easyprivacy.txt", "easylistspanish.txt", "extras.txt")

    private val engine: FilterEngine by lazy {
        FilterEngine.parse(names.map { File("src/main/assets/filters/$it").readText() })
    }

    private val page = "https://peliculas.example/ver/123"

    @Test
    fun `las listas se leen completas y rapido`() {
        val start = System.nanoTime()
        val rules = engine.networkRuleCount
        val seconds = (System.nanoTime() - start) / 1e9
        println("Reglas de red: $rules, reglas de ocultar: ${engine.cosmeticRuleCount}, lectura: ${"%.2f".format(seconds)} s")
        assertTrue("muy pocas reglas de red: $rules", rules > 80_000)
        assertTrue("muy pocas reglas de ocultar: ${engine.cosmeticRuleCount}", engine.cosmeticRuleCount > 10_000)
        assertTrue("lectura lenta: $seconds s", seconds < 20)
    }

    @Test
    fun `bloquea anunciantes y rastreadores conocidos`() {
        val ads = listOf(
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://securepubads.g.doubleclick.net/tag/js/gpt.js",
            "https://www.google-analytics.com/analytics.js",
            "https://static.criteo.net/js/ld/publishertag.js",
            "https://cdn.taboola.com/libtrc/loader.js",
            "https://a.exoclick.com/tag.php",
            "https://juicyads.com/adshow.php",
        )
        for (url in ads) assertTrue("debía bloquear $url", engine.shouldBlock(url, page, RequestType.SCRIPT))
    }

    @Test
    fun `no bloquea sitios ni servidores normales`() {
        val fine = listOf(
            "https://www.google.com/search?q=peliculas",
            "https://github.com/",
            "https://cdnjs.cloudflare.com/ajax/libs/jquery/3.7.1/jquery.min.js",
            "https://fonts.googleapis.com/css2?family=Roboto",
            "https://images.example/poster.jpg",
            "https://web.stremio.com/",
            "https://store-1.tb-cdn.io/video/pelicula.mkv",
            "https://peliculas.example/static/player.js",
        )
        for (url in fine) assertFalse("no debía bloquear $url", engine.shouldBlock(url, page, RequestType.SCRIPT))
    }

    @Test
    fun `las redirecciones a anunciantes se reconocen como navegaciones`() {
        assertTrue(engine.shouldBlock("https://a.exoclick.com/click", page, RequestType.POPUP))
        assertFalse(engine.shouldBlock("https://www.google.com/", page, RequestType.POPUP))
    }

    @Test
    fun `ocultar elementos genera un CSS razonable`() {
        val css = engine.cosmeticCss("peliculas.example")
        println("CSS genérico: ${css.length / 1024} KB")
        assertTrue(css.length > 10_000)
        assertTrue("display:none!important" in css)
        assertFalse("{{" in css)
    }

    @Test
    fun `consultar miles de direcciones es rapido`() {
        val urls = (1..2000).map { "https://cdn$it.sitio$it.example/assets/app$it.js?v=$it" }
        engine.shouldBlock(urls[0], page, RequestType.SCRIPT) // calienta
        val start = System.nanoTime()
        urls.forEach { engine.shouldBlock(it, page, RequestType.SCRIPT) }
        val millis = (System.nanoTime() - start) / 1e6
        println("2000 consultas: ${"%.0f".format(millis)} ms")
        assertTrue("consultas lentas: $millis ms", millis < 3000)
    }
}
