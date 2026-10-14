package io.github.freevideocaster.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.freevideocaster.MainViewModel
import io.github.freevideocaster.adblock.AdBlocker
import io.github.freevideocaster.data.SearchEngine
import io.github.freevideocaster.media.MediaFormats
import io.github.freevideocaster.media.MediaRequest
import kotlinx.coroutines.delay
import org.json.JSONArray

/**
 * Navegador integrado: detecta videos (.m3u8, .mp4, .mkv, .mpd y etiquetas <video>)
 * y permite enviarlos al Roku con las cabeceras de la página (Referer, Cookie, User-Agent).
 * No funciona con videos protegidos (DRM) ni con los que solo existen como "blob:".
 */
@Composable
fun BrowserScreen(vm: MainViewModel) {
    val found by vm.sniffer.found.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val blockedCount by vm.adBlocker.blocked.collectAsStateWithLifecycle()
    // TextFieldValue (y no String) para poder seleccionar todo el texto al tocar la barra, como un navegador.
    var address by remember { mutableStateOf(TextFieldValue(vm.browserUrl)) }
    val focus = LocalFocusManager.current
    var editing by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val addressInteraction = remember { MutableInteractionSource() }
    fun go() {
        keyboard?.hide() // si no, el teclado tapa la lista de videos detectados
        focus.clearFocus()
        webView?.loadUrl(toUrl(address.text, prefs.search))
    }
    // Al tocar la barra se selecciona todo (como en un navegador) para escribir encima sin borrar a mano.
    // Se espera un instante porque la propia pulsación coloca el cursor justo antes.
    LaunchedEffect(addressInteraction) {
        addressInteraction.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                delay(60)
                address = address.copy(selection = TextRange(0, address.text.length))
            }
        }
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    // La primera página espera (unos instantes) a que el bloqueador lea sus listas, para que no se cuele nada.
    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        if (prefs.adBlock) vm.adBlocker.awaitReady()
        view.loadUrl(vm.browserUrl.ifBlank { prefs.search.homeUrl })
    }
    // Con el bloqueo activo, las ventanas nuevas llegan a la app (que decide); apagado, WebView las abre en la misma vista.
    LaunchedEffect(webView, prefs.adBlock) { webView?.settings?.setSupportMultipleWindows(prefs.adBlock) }
    // Al activar o desactivar el bloqueo, la página abierta se recarga para que el cambio se note.
    var lastAdBlock by remember { mutableStateOf(prefs.adBlock) }
    LaunchedEffect(prefs.adBlock) {
        if (prefs.adBlock != lastAdBlock) {
            lastAdBlock = prefs.adBlock
            webView?.reload()
        }
    }

    // Revisa cada 3 s las etiquetas <video> de la página (muchos sitios cargan el video tarde).
    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        while (true) {
            delay(3000)
            scanVideoTags(view, vm.sniffer)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = canGoBack, onClick = { webView?.goBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                singleLine = true,
                placeholder = { Text("Dirección o búsqueda") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .onFocusChanged { state -> editing = state.isFocused },
                interactionSource = addressInteraction,
            )
            TextButton(onClick = { go() }) { Text("Ir") }
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                createWebView(
                    context = context,
                    sniffer = vm.sniffer,
                    blocker = vm.adBlocker,
                    onNavigate = { url, back ->
                        // No se pisa lo que la persona está escribiendo.
                        if (!editing) address = TextFieldValue(url)
                        vm.browserUrl = url
                        canGoBack = back
                    },
                ).also { webView = it }
            },
            onRelease = { it.destroy() },
        )

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (found.isEmpty()) "Ningún video detectado todavía. Si hace falta, dale play en la página."
                        else "Videos detectados (${found.size})",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = prefs.adBlock,
                        onClick = { vm.setAdBlock(!prefs.adBlock) },
                        label = { Text(if (prefs.adBlock) "Anuncios: $blockedCount" else "Bloqueo apagado") },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 180.dp)) {
                    items(found, key = { it.url }) { media ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (media.format ?: "video").uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    media.url,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { vm.cast(media.toRequest(), enqueue = false) }) { Text("Enviar") }
                            TextButton(onClick = { vm.cast(media.toRequest(), enqueue = true) }) { Text("Cola") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun DetectedMedia.toRequest() =
    MediaRequest(url = url, title = pageTitle, format = format, headers = headers)

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: android.content.Context,
    sniffer: MediaSniffer,
    blocker: AdBlocker,
    onNavigate: (url: String, canGoBack: Boolean) -> Unit,
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = true
    val userAgent = settings.userAgentString

    webViewClient = object : WebViewClient() {
        // Se escribe en el hilo principal y se lee en el de red.
        @Volatile private var pageUrl = ""
        @Volatile private var pageTitle = ""

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            // Los anuncios se cortan antes de mirar si traen un video: así no aparecen como "detectados".
            blocker.intercept(request, pageUrl)?.let { return it }
            val url = request.url.toString()
            val format = MediaFormats.fromUrl(url) ?: if (".m3u8" in url.lowercase()) "hls" else null
            if (format != null) {
                val referer = request.requestHeaders["Referer"] ?: pageUrl
                sniffer.add(DetectedMedia(url, format, headersFor(url, referer, userAgent), pageTitle))
            }
            return null // No se modifica nada: la página carga normalmente.
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            return blocker.blocksNavigation(request.url.toString(), pageUrl, request.hasGesture(), request.isRedirect)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            pageUrl = url
            pageTitle = ""
            sniffer.reset()
        }

        // Los recuadros de anuncios se ocultan en cuanto se ve la página nueva, y otra vez al terminar por si acaso.
        override fun onPageCommitVisible(view: WebView, url: String) {
            blocker.cosmeticScript(url)?.let { view.evaluateJavascript(it, null) }
        }

        override fun onPageFinished(view: WebView, url: String) {
            pageTitle = view.title.orEmpty()
            blocker.cosmeticScript(url)?.let { view.evaluateJavascript(it, null) }
            scanVideoTags(view, sniffer)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            pageUrl = url
            onNavigate(url, view.canGoBack())
        }
    }

    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!blocker.enabled) return false
            // Una ventana que nadie pidió con un toque es un anuncio emergente. Si fue un toque (un enlace
            // «abrir en otra pestaña»), se abre en esta misma vista, pasando antes por el filtro.
            if (!isUserGesture) {
                blocker.blockPopup()
                return false
            }
            val carrier = WebView(view.context)
            carrier.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(carrier: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url.toString()
                    if (!blocker.blocksNavigation(target, view.url.orEmpty(), userInitiated = true, isRedirect = false)) {
                        view.post { view.loadUrl(target) }
                    }
                    view.post { carrier.destroy() }
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = carrier
            resultMsg.sendToTarget()
            return true
        }
    }
}

/** Busca etiquetas <video>/<source> con una URL http(s) directa. */
private fun scanVideoTags(view: WebView, sniffer: MediaSniffer) {
    val pageUrl = view.url ?: return
    val title = view.title.orEmpty()
    val userAgent = view.settings.userAgentString
    view.evaluateJavascript(FIND_VIDEOS_JS) { result ->
        val array = runCatching { JSONArray(result) }.getOrNull() ?: return@evaluateJavascript
        for (i in 0 until array.length()) {
            val url = array.optString(i)
            if (url.startsWith("http")) {
                sniffer.add(DetectedMedia(url, MediaFormats.fromUrl(url), headersFor(url, pageUrl, userAgent), title))
            }
        }
    }
}

private fun headersFor(url: String, referer: String, userAgent: String): Map<String, String> = buildMap {
    if (referer.isNotBlank()) put("Referer", referer)
    put("User-Agent", userAgent)
    CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
}

private fun toUrl(text: String, engine: SearchEngine): String {
    val trimmed = text.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        ' ' !in trimmed && '.' in trimmed -> "https://$trimmed"
        else -> engine.searchFor(trimmed)
    }
}

private const val FIND_VIDEOS_JS = """
(function() {
  var urls = [];
  document.querySelectorAll('video, video source').forEach(function(e) {
    var u = e.currentSrc || e.src;
    if (u && u.indexOf('http') === 0) urls.push(u);
  });
  return urls;
})()
"""
