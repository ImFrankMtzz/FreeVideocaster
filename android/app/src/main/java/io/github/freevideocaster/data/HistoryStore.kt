package io.github.freevideocaster.data

import android.content.Context
import io.github.freevideocaster.media.MediaRequest
import io.github.freevideocaster.media.TitleCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryEntry(
    val title: String,
    val url: String,
    val subtitleUrl: String?,
    val headers: Map<String, String>,
    val timestamp: Long,
    val detail: String = "",
    /** Última posición vista; 0 si no se empezó o ya se terminó. */
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {
    val key: String by lazy { MediaRequest.keyOf(url) }

    /** Punto desde el que ofrecer «Continuar»; 0 si no vale la pena (muy al inicio o casi al final). */
    val resumeMs: Long
        get() = if (positionMs >= MIN_RESUME_MS && (durationMs <= 0 || positionMs < durationMs * FINISHED_RATIO)) positionMs else 0

    fun toRequest() = MediaRequest(url = url, title = title, subtitleUrl = subtitleUrl, headers = headers, detail = detail)

    companion object {
        const val MIN_RESUME_MS = 30_000L
        const val FINISHED_RATIO = 0.95
    }
}

/** Historial de videos enviados, guardado como JSON en el almacenamiento privado de la app. */
class HistoryStore(context: Context, scope: CoroutineScope) {

    private val file = File(context.filesDir, "history.json")
    private val mutex = Mutex()
    private var loaded = false
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries

    init {
        scope.launch { mutex.withLock { ensureLoaded() } }
    }

    /** Añade (o sube al principio) el video, conservando el progreso si ya estaba. */
    suspend fun add(request: MediaRequest) = update { list ->
        val previous = list.firstOrNull { it.url == request.url }
        val entry = HistoryEntry(
            title = request.title,
            url = request.url,
            subtitleUrl = request.subtitleUrl,
            headers = request.headers,
            timestamp = System.currentTimeMillis(),
            detail = request.detail,
            positionMs = previous?.positionMs ?: 0,
            durationMs = previous?.durationMs ?: 0,
        )
        (listOf(entry) + list.filterNot { it.url == request.url }).take(MAX_ENTRIES)
    }

    /** Guarda hasta dónde se vio. Si ya casi terminó, lo marca como visto (posición 0). */
    suspend fun updateProgress(key: String, positionMs: Long, durationMs: Long) = update { list ->
        list.map { entry ->
            if (entry.key != key) {
                entry
            } else {
                val finished = durationMs > 0 && positionMs >= durationMs * HistoryEntry.FINISHED_RATIO
                entry.copy(positionMs = if (finished) 0 else positionMs, durationMs = durationMs)
            }
        }
    }

    suspend fun find(key: String): HistoryEntry? = mutex.withLock {
        ensureLoaded()
        _entries.value.firstOrNull { it.key == key }
    }

    suspend fun remove(entry: HistoryEntry) = update { list -> list - entry }

    suspend fun clear() = update { emptyList() }

    private suspend fun update(change: (List<HistoryEntry>) -> List<HistoryEntry>) = mutex.withLock {
        ensureLoaded()
        val current = _entries.value
        val next = change(current)
        if (next == current) return@withLock
        _entries.value = next
        withContext(Dispatchers.IO) { file.writeText(toJson(next)) }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _entries.value = withContext(Dispatchers.IO) {
            runCatching { if (file.exists()) fromJson(file.readText()) else emptyList() }.getOrDefault(emptyList())
        }
        loaded = true
    }

    private fun toJson(list: List<HistoryEntry>): String = JSONArray().apply {
        list.forEach { entry ->
            put(
                JSONObject()
                    .put("title", entry.title)
                    .put("url", entry.url)
                    .put("subtitleUrl", entry.subtitleUrl ?: JSONObject.NULL)
                    .put("headers", JSONObject(entry.headers))
                    .put("timestamp", entry.timestamp)
                    .put("detail", entry.detail)
                    .put("positionMs", entry.positionMs)
                    .put("durationMs", entry.durationMs)
            )
        }
    }.toString()

    private fun fromJson(text: String): List<HistoryEntry> {
        val array = JSONArray(text)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val headers = item.optJSONObject("headers")
            // Entradas anteriores a la v0.2 guardaron el nombre crudo del archivo: se limpia al leerlas.
            val savedTitle = item.optString("title")
            val savedDetail = item.optString("detail")
            val cleaned = if (savedDetail.isEmpty()) TitleCleaner.parse(savedTitle) else null
            HistoryEntry(
                title = cleaned?.title?.ifBlank { null } ?: savedTitle,
                url = item.getString("url"),
                subtitleUrl = item.optString("subtitleUrl").takeIf { it.isNotEmpty() && it != "null" },
                headers = headers?.keys()?.asSequence()?.associateWith { headers.getString(it) }.orEmpty(),
                timestamp = item.optLong("timestamp"),
                detail = cleaned?.detail ?: savedDetail,
                positionMs = item.optLong("positionMs"),
                durationMs = item.optLong("durationMs"),
            )
        }
    }

    private companion object {
        const val MAX_ENTRIES = 100
    }
}
