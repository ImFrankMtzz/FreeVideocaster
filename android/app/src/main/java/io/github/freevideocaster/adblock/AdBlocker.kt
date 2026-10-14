package io.github.freevideocaster.adblock

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/**
 * Bloqueador de anuncios del navegador integrado. Carga las listas (las de la app o las que la persona
 * haya actualizado), las convierte en un [FilterEngine] y decide qué peticiones, ventanas y redirecciones
 * se cortan. Todo ocurre en el teléfono: no se envía nada a ningún servidor.
 */
class AdBlocker(private val context: Context, private val scope: CoroutineScope) {

    @Volatile
    private var engine: FilterEngine? = null

    /** Si está apagado, el navegador no toca nada. */
    @Volatile
    var enabled: Boolean = true

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _blocked = MutableStateFlow(0)

    /** Anuncios cortados en la página actual (peticiones, ventanas emergentes y redirecciones). */
    val blocked: StateFlow<Int> = _blocked.asStateFlow()

    private var loading: Job? = null

    /** Cuántas reglas hay cargadas (0 mientras no esté listo). */
    val ruleCount: Int get() = engine?.let { it.networkRuleCount + it.cosmeticRuleCount } ?: 0

    /** Fecha de las listas descargadas por la persona, o null si se usan las que trae la app. */
    val updatedAt: Long?
        get() = LISTS.mapNotNull { File(listsDir(), it).takeIf(File::exists)?.lastModified() }.maxOrNull()

    /** Empieza a leer las listas en segundo plano (una sola vez). */
    fun ensureLoaded() {
        if (_ready.value || loading?.isActive == true) return
        loading = scope.launch(Dispatchers.Default) { load() }
    }

    /** Espera a que las listas estén listas, sin pasar de [timeoutMs]. */
    suspend fun awaitReady(timeoutMs: Long = 5000) {
        ensureLoaded()
        withTimeoutOrNull(timeoutMs) { ready.first { it } }
    }

    private fun load() {
        val texts = LISTS.map { name -> readList(name) } + readAsset(EXTRAS)
        engine = FilterEngine.parse(texts)
        _ready.value = true
    }

    private fun listsDir() = File(context.filesDir, "filters")

    private fun readList(name: String): String {
        val downloaded = File(listsDir(), name)
        if (downloaded.exists()) {
            runCatching { return downloaded.readText() }
        }
        return readAsset(name)
    }

    private fun readAsset(name: String): String =
        runCatching { context.assets.open("filters/$name").bufferedReader().use { it.readText() } }.getOrDefault("")

    /**
     * Descarga las listas más recientes. Solo se llama cuando la persona lo pide.
     * Devuelve cuántas listas se actualizaron; lanza [IOException] si no se pudo con ninguna.
     */
    suspend fun update(http: OkHttpClient): Int = withContext(Dispatchers.IO) {
        val dir = listsDir().also { it.mkdirs() }
        var updated = 0
        var failure: IOException? = null
        for ((name, url) in SOURCES) {
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val text = response.body.string()
                    // Una página de error o una lista vacía no deben reemplazar a la buena.
                    if (text.length < MIN_LIST_SIZE || !text.startsWith("[Adblock")) throw IOException("lista no válida")
                    val temp = File(dir, "$name.tmp")
                    temp.writeText(text)
                    temp.copyTo(File(dir, name), overwrite = true)
                    temp.delete()
                    updated++
                }
            } catch (e: IOException) {
                failure = e
            }
        }
        if (updated == 0) throw failure ?: IOException("sin listas")
        withContext(Dispatchers.Default) { load() }
        updated
    }

    // --- Decisiones del navegador ------------------------------------------------------------

    private fun countOne() = _blocked.update { it + 1 }

    /** Llamado desde `shouldInterceptRequest`: devuelve una respuesta vacía si la petición es un anuncio. */
    fun intercept(request: WebResourceRequest, pageUrl: String): WebResourceResponse? {
        // El contador empieza de cero con cada página nueva. Se hace aquí y no en onPageStarted porque este
        // método corre en otro hilo y ve las peticiones de la página en orden; onPageStarted llegaba tarde y
        // borraba los primeros anuncios ya contados.
        if (request.isForMainFrame) {
            _blocked.value = 0
            return null
        }
        if (!enabled) return null
        val active = engine ?: return null
        val url = request.url.toString()
        if (!url.startsWith("http")) return null
        val type = RequestType.guess(url, isMainFrame = false, accept = request.requestHeaders["Accept"])
        if (!active.shouldBlock(url, pageUrl, type)) return null
        countOne()
        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    /**
     * Decide si la página principal puede ir a [url]. Se corta lo que no es web (intent://, market://…,
     * que abren otras apps), los anunciantes conocidos y los saltos a otro sitio que no pidió la persona.
     */
    fun blocksNavigation(url: String, pageUrl: String, userInitiated: Boolean, isRedirect: Boolean): Boolean {
        if (!enabled) return false
        val scheme = url.substringBefore(':', "").lowercase()
        if (scheme !in WEB_SCHEMES) {
            countOne()
            return true
        }
        if (scheme != "http" && scheme != "https") return false
        val active = engine
        if (active != null && active.shouldBlock(url, pageUrl, RequestType.POPUP)) {
            countOne()
            return true
        }
        if (!userInitiated && !isRedirect && pageUrl.startsWith("http")) {
            val from = FilterEngine.hostOf(pageUrl.lowercase())
            val to = FilterEngine.hostOf(url.lowercase())
            if (from != null && to != null && FilterEngine.baseDomain(from) != FilterEngine.baseDomain(to)) {
                countOne()
                return true
            }
        }
        return false
    }

    /** Una ventana nueva que pide la página (casi siempre un anuncio emergente): se cuenta y se corta. */
    fun blockPopup() = countOne()

    /** Script que oculta los recuadros de anuncios de la página; null si no hay nada que ocultar. */
    fun cosmeticScript(pageUrl: String): String? {
        if (!enabled) return null
        val host = FilterEngine.hostOf(pageUrl.lowercase()) ?: return null
        val css = engine?.cosmeticCss(host).orEmpty()
        if (css.isEmpty()) return null
        return "(function(){if(document.getElementById('fvc-hide'))return;" +
            "var s=document.createElement('style');s.id='fvc-hide';s.textContent=" + jsString(css) + ";" +
            "(document.head||document.documentElement).appendChild(s);})()"
    }

    private fun jsString(text: String): String = org.json.JSONObject.quote(text)

    companion object {
        private val LISTS = listOf("easylist.txt", "easyprivacy.txt", "easylistspanish.txt")
        private const val EXTRAS = "extras.txt"
        private const val MIN_LIST_SIZE = 20_000
        private val WEB_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript")

        private val SOURCES = mapOf(
            "easylist.txt" to "https://easylist.to/easylist/easylist.txt",
            "easyprivacy.txt" to "https://easylist.to/easylist/easyprivacy.txt",
            "easylistspanish.txt" to "https://easylist-downloads.adblockplus.org/easylistspanish.txt",
        )
    }
}
