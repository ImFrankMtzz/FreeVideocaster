package io.github.freevideocaster.ui.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Un video encontrado en la página, con las cabeceras que el Roku necesitará para pedirlo. */
data class DetectedMedia(
    val url: String,
    val format: String?,
    val headers: Map<String, String>,
    val pageTitle: String,
)

/** Lista de videos detectados en la página actual. Seguro para usar desde varios hilos. */
class MediaSniffer {
    private val _found = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val found: StateFlow<List<DetectedMedia>> = _found

    fun add(media: DetectedMedia) {
        _found.update { list ->
            if (list.any { it.url == media.url }) list else (listOf(media) + list).take(MAX_ITEMS)
        }
    }

    fun reset() {
        _found.value = emptyList()
    }

    private companion object {
        const val MAX_ITEMS = 30
    }
}
