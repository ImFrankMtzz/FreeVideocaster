package io.github.freevideocaster

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.freevideocaster.data.ColorStyle
import io.github.freevideocaster.data.HistoryEntry
import io.github.freevideocaster.data.SearchEngine
import io.github.freevideocaster.data.Settings
import io.github.freevideocaster.data.ThemeMode
import java.io.IOException
import io.github.freevideocaster.media.MediaRequest
import io.github.freevideocaster.roku.CastException
import io.github.freevideocaster.roku.Track
import io.github.freevideocaster.ui.browser.MediaSniffer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Tab { Home, Remote, History, Browser }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val caster = app.caster

    val roku = caster.roku.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val history = caster.history.entries

    /** Estado del Roku (reproducción, posición, cola), compartido con la notificación. */
    val live = caster.live

    var tab by mutableStateOf(Tab.Home)

    /** Pantalla de diagnóstico abierta sobre las pestañas, con un enlace opcional para probar. */
    var showDiagnostics by mutableStateOf(false)
    var diagnosticsLink by mutableStateOf("")

    /** Mensaje para mostrar en la barra inferior (snackbar). */
    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Navegador: última página abierta (vacía = la página de inicio del buscador elegido) y videos detectados. */
    var browserUrl by mutableStateOf("")
    val sniffer = MediaSniffer()

    // --- Ajustes -----------------------------------------------------------------------------

    val prefs = caster.prefs
    val adBlocker = app.adBlocker
    var showSettings by mutableStateOf(false)

    /** Resultado de «Actualizar listas»; null mientras no se haya pedido. */
    var listsStatus by mutableStateOf<String?>(null)
        private set
    var updatingLists by mutableStateOf(false)
        private set

    fun setTheme(value: ThemeMode) = save { setTheme(value) }
    fun setAmoled(value: Boolean) = save { setAmoled(value) }
    fun setColors(value: ColorStyle) = save { setColors(value) }
    fun setSearch(value: SearchEngine) = save { setSearch(value) }
    fun setAdBlock(value: Boolean) = save { setAdBlock(value) }
    fun setAutoSend(value: Boolean) = save { setAutoSend(value) }
    fun setAutoReturn(value: Boolean) = save { setAutoReturn(value) }
    fun setOnlineSubtitles(value: Boolean) = save { setOnlineSubtitles(value) }

    private fun save(change: suspend Settings.() -> Unit) {
        viewModelScope.launch { caster.settings.change() }
    }

    /** Descarga las listas de anuncios más recientes (solo cuando la persona lo pide). */
    fun updateFilterLists() {
        if (updatingLists) return
        viewModelScope.launch {
            updatingLists = true
            listsStatus = try {
                val count = adBlocker.update(getApplication<Application>().internetHttp)
                "Listas actualizadas ($count de 3)."
            } catch (e: IOException) {
                "No se pudo descargar: revisa tu conexión a internet."
            } finally {
                updatingLists = false
            }
        }
    }

    /** Envía el video; si no es para la cola, espera a que el Roku empiece y avisa si falla. */
    fun cast(request: MediaRequest, enqueue: Boolean, startMs: Long = 0) {
        viewModelScope.launch {
            busy = true
            try {
                val result = caster.cast(request, enqueue, startMs)
                message = result.message
                busy = false
                if (!result.queued) message = caster.awaitPlayback().message.ifBlank { result.message }
            } catch (e: CastException) {
                message = e.message
            } finally {
                busy = false
            }
        }
    }

    fun command(action: String, extra: Map<String, String> = emptyMap()) = quietly { caster.command(action, extra) }

    fun keypress(key: String) = quietly { caster.keypress(key) }

    fun removeFromQueue(index: Int) = quietly { caster.removeFromQueue(index) }

    fun setAudioTrack(track: Track) = quietly { caster.setAudioTrack(track.id) }

    /** Activa la pista indicada, o desactiva los subtítulos si es null. */
    fun setSubtitleTrack(track: Track?) = quietly { caster.setSubtitleTrack(track?.id) }

    fun removeHistory(entry: HistoryEntry) {
        viewModelScope.launch { caster.history.remove(entry) }
    }

    fun clearHistory() {
        viewModelScope.launch { caster.history.clear() }
    }

    fun openDiagnostics(link: String = "") {
        diagnosticsLink = link
        showDiagnostics = true
    }

    fun consumeMessage() {
        message = null
    }

    /** Órdenes cortas del control remoto: no muestran barra de progreso, solo avisan si fallan. */
    private fun quietly(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CastException) {
                message = e.message
            }
        }
    }
}
