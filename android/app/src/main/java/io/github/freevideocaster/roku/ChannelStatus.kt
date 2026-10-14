package io.github.freevideocaster.roku

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class QueueItem(val key: String, val title: String)

/** Pista de audio o de subtítulos tal como la informa el canal (id sirve para elegirla). */
data class Track(val id: String, val language: String, val name: String)

/** Lo que informa el canal FreeVideocaster v2 en http://ROKU:8061/status. */
data class ChannelStatus(
    val playback: Playback,
    val key: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val queue: List<QueueItem>,
    val error: String,
    /** Aumenta con cada video nuevo que arranca en el canal. */
    val seq: Int,
    val audio: List<Track> = emptyList(),
    val audioCurrent: String = "",
    val subtitles: List<Track> = emptyList(),
    /** Vacío si los subtítulos están desactivados. */
    val subtitleCurrent: String = "",
) {
    companion object {
        /** null si no es una respuesta del canal v2. */
        fun parse(text: String): ChannelStatus? = try {
            val json = JSONObject(text)
            if (json.optInt("v", 0) < 2) {
                null
            } else {
                val queue = json.optJSONArray("queue")
                ChannelStatus(
                    playback = when (json.optString("state")) {
                        "play" -> Playback.Playing
                        "pause" -> Playback.Paused
                        "buffering" -> Playback.Buffering
                        else -> Playback.Idle
                    },
                    key = json.optString("key"),
                    title = json.optString("title"),
                    positionMs = json.optLong("position"),
                    durationMs = json.optLong("duration"),
                    queue = if (queue == null) {
                        emptyList()
                    } else {
                        (0 until queue.length()).map { index ->
                            val item = queue.getJSONObject(index)
                            QueueItem(item.optString("key"), item.optString("title"))
                        }
                    },
                    error = json.optString("error"),
                    seq = json.optInt("seq"),
                    audio = tracks(json.optJSONArray("audio")),
                    audioCurrent = json.optString("audiocur"),
                    subtitles = tracks(json.optJSONArray("subs")),
                    subtitleCurrent = json.optString("subcur"),
                )
            }
        } catch (e: JSONException) {
            null
        }

        private fun tracks(array: JSONArray?): List<Track> =
            if (array == null) {
                emptyList()
            } else {
                (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    Track(item.optString("id"), item.optString("lang"), item.optString("name"))
                }.filter { it.id.isNotEmpty() }
            }
    }
}
