package io.github.freevideocaster.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {

    private val page = "https://peliculas.example/ver/123"

    private fun engine(vararg rules: String) = FilterEngine.parse(rules.joinToString("\n"))

    private fun FilterEngine.blocks(url: String, type: Int = RequestType.SCRIPT, from: String = page) =
        shouldBlock(url, from, type)

    @Test
    fun `una regla de dominio bloquea el dominio y sus subdominios`() {
        val e = engine("||ads.tracker.com^")
        assertTrue(e.blocks("https://ads.tracker.com/banner.js"))
        assertTrue(e.blocks("https://eu.ads.tracker.com/x?y=1"))
        assertFalse(e.blocks("https://tracker.com/banner.js"))
        assertFalse(e.blocks("https://notads.tracker.com/banner.js"))
        assertFalse(e.blocks("https://evilads.tracker.com.otro.net/banner.js"))
    }

    @Test
    fun `una regla con ruta solo bloquea esa ruta`() {
        val e = engine("||cdn.sitio.com/ads/")
        assertTrue(e.blocks("https://cdn.sitio.com/ads/uno.png", RequestType.IMAGE))
        assertFalse(e.blocks("https://cdn.sitio.com/video/uno.mp4", RequestType.MEDIA))
    }

    @Test
    fun `los patrones con asterisco y separador funcionan`() {
        val e = engine("/banner/*/img^", "-ad-unit.")
        assertTrue(e.blocks("https://x.com/banner/300x250/img?id=1", RequestType.IMAGE))
        assertTrue(e.blocks("https://x.com/js/top-ad-unit.js"))
        assertFalse(e.blocks("https://x.com/banner/300x250/imagen.png", RequestType.IMAGE))
    }

    @Test
    fun `una excepcion gana a una regla normal pero no a una importante`() {
        val e = engine("||ads.com^", "@@||ads.com/permitido/", "||malo.com^\$important", "@@||malo.com^")
        assertTrue(e.blocks("https://ads.com/otra"))
        assertFalse(e.blocks("https://ads.com/permitido/x.js"))
        assertTrue(e.blocks("https://malo.com/x.js"))
    }

    @Test
    fun `third-party distingue el propio sitio de los terceros`() {
        val e = engine("||metricas.com^\$third-party")
        assertTrue(e.blocks("https://metricas.com/t.js", from = "https://otro.net/p"))
        assertFalse(e.blocks("https://metricas.com/t.js", from = "https://www.metricas.com/p"))
    }

    @Test
    fun `domain limita las reglas a ciertas paginas`() {
        val e = engine("||popup.net^\$domain=peliculas.example|~vip.peliculas.example")
        assertTrue(e.blocks("https://popup.net/a.js", from = "https://peliculas.example/x"))
        assertTrue(e.blocks("https://popup.net/a.js", from = "https://www.peliculas.example/x"))
        assertFalse(e.blocks("https://popup.net/a.js", from = "https://vip.peliculas.example/x"))
        assertFalse(e.blocks("https://popup.net/a.js", from = "https://otra.com/x"))
    }

    @Test
    fun `las opciones de tipo se respetan`() {
        val e = engine("||solo-imagenes.com^\$image", "||menos-scripts.com^\$~script")
        assertTrue(e.blocks("https://solo-imagenes.com/a.png", RequestType.IMAGE))
        assertFalse(e.blocks("https://solo-imagenes.com/a.js", RequestType.SCRIPT))
        assertTrue(e.blocks("https://menos-scripts.com/a.png", RequestType.IMAGE))
        assertFalse(e.blocks("https://menos-scripts.com/a.js", RequestType.SCRIPT))
    }

    @Test
    fun `la pagina principal nunca se bloquea por una regla de red`() {
        val e = engine("||ads.com^")
        assertFalse(e.blocks("https://ads.com/", RequestType.DOCUMENT))
    }

    @Test
    fun `una regla popup solo actua en navegaciones`() {
        val e = engine("||salta.net^\$popup", "||anunciante.com^")
        assertTrue(e.blocks("https://salta.net/go", RequestType.POPUP))
        assertFalse(e.blocks("https://salta.net/go.js", RequestType.SCRIPT))
        // Una regla de dominio sin opciones también delata una redirección a un anunciante.
        assertTrue(e.blocks("https://anunciante.com/click", RequestType.POPUP))
        assertFalse(e.blocks("https://legitimo.com/", RequestType.POPUP))
    }

    @Test
    fun `una excepcion document apaga el bloqueo en toda la pagina`() {
        val e = engine("||ads.com^", "@@||peliculas.example^\$document")
        assertFalse(e.blocks("https://ads.com/x.js"))
        assertTrue(e.blocks("https://ads.com/x.js", from = "https://otra.com/"))
        assertTrue(e.isPageExempt("www.peliculas.example"))
    }

    @Test
    fun `las reglas que no se entienden se descartan sin afectar al resto`() {
        val e = engine(
            "/ads\\d+/",              // regex
            "||redirige.com^\$redirect=noopjs",
            "||csp.com^\$csp=script-src 'none'",
            "ab",                      // demasiado genérica
            "||bueno.com^",
        )
        assertEquals(1, e.networkRuleCount)
        assertTrue(e.blocks("https://bueno.com/x.js"))
        assertFalse(e.blocks("https://redirige.com/x.js"))
    }

    @Test
    fun `los comentarios y la cabecera se ignoran`() {
        val e = engine("[Adblock Plus 2.0]", "! Title: Prueba", "", "||ads.com^")
        assertEquals(1, e.networkRuleCount)
    }

    @Test
    fun `ocultar elementos genericos y por dominio`() {
        val e = engine("##.anuncio", "peliculas.example##div#pub", "otro.com##.solo-otro")
        val css = e.cosmeticCss("www.peliculas.example")
        assertTrue(".anuncio" in css)
        assertTrue("div#pub" in css)
        assertFalse(".solo-otro" in css)
        assertTrue(css.contains("display:none!important"))
    }

    @Test
    fun `una excepcion cosmetica quita el selector en ese sitio`() {
        val e = engine("##.anuncio", "##.banner", "peliculas.example#@#.anuncio")
        val css = e.cosmeticCss("peliculas.example")
        assertFalse(".anuncio" in css)
        assertTrue(".banner" in css)
        assertTrue(".anuncio" in e.cosmeticCss("otra.com"))
    }

    @Test
    fun `los selectores extendidos y peligrosos no se admiten`() {
        val e = engine(
            "##div:-abp-has(.x)",
            "##.a:has-text(Publicidad)",
            "##.b:xpath(//div)",
            "##.malo}body{display:none",
            "##+js(set, x, 1)",
            "##.bueno",
        )
        assertEquals(1, e.cosmeticRuleCount)
        assertTrue(".bueno" in e.cosmeticCss("x.com"))
    }

    @Test
    fun `elemhide y generichide apagan los selectores en un sitio`() {
        val e = engine("##.anuncio", "@@||sin-ocultar.com^\$elemhide", "@@||sin-genericos.com^\$generichide", "sin-genericos.com##.propio")
        assertEquals("", e.cosmeticCss("sin-ocultar.com"))
        val css = e.cosmeticCss("sin-genericos.com")
        assertFalse(".anuncio" in css)
        assertTrue(".propio" in css)
    }

    @Test
    fun `dominio base con sufijos de dos niveles`() {
        assertEquals("sitio.com", FilterEngine.baseDomain("cdn.sitio.com"))
        assertEquals("sitio.co.uk", FilterEngine.baseDomain("a.b.sitio.co.uk"))
        assertEquals("sitio.com.mx", FilterEngine.baseDomain("www.sitio.com.mx"))
        assertEquals("localhost", FilterEngine.baseDomain("localhost"))
    }

    @Test
    fun `host de una direccion`() {
        assertEquals("a.b.com", FilterEngine.hostOf("https://a.b.com:8080/x?y=1"))
        assertEquals("a.b.com", FilterEngine.hostOf("https://usuario:clave@a.b.com/x"))
        assertEquals(null, FilterEngine.hostOf("about:blank"))
    }

    @Test
    fun `tipo de recurso deducido de la direccion`() {
        assertEquals(RequestType.DOCUMENT, RequestType.guess("https://a.com/", true, null))
        assertEquals(RequestType.IMAGE, RequestType.guess("https://a.com/x.PNG?v=1", false, null))
        assertEquals(RequestType.SCRIPT, RequestType.guess("https://a.com/x.js", false, null))
        assertEquals(RequestType.IMAGE, RequestType.guess("https://a.com/pixel", false, "image/avif,image/webp,*/*"))
        assertEquals(RequestType.UNKNOWN, RequestType.guess("https://a.com/api", false, "*/*"))
    }
}
