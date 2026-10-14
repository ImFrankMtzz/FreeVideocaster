package io.github.freevideocaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.freevideocaster.MainViewModel
import io.github.freevideocaster.Tab
import io.github.freevideocaster.roku.LiveState
import io.github.freevideocaster.roku.Playback
import io.github.freevideocaster.roku.Track
import io.github.freevideocaster.roku.TrackLabels

private enum class TrackKind { Audio, Subtitles }

@Composable
fun RemoteScreen(vm: MainViewModel) {
    val roku by vm.roku.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val state = live ?: LiveState.Offline
    var sheet by remember { mutableStateOf<TrackKind?>(null) }

    val device = roku
    if (device == null) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(AppIcons.Tv, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Aún no hay un Roku conectado", style = MaterialTheme.typography.titleMedium)
            Text(
                "Conéctalo para controlar lo que se reproduce.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { vm.tab = Tab.Home }) { Text("Conectar") }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(device.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        NowPlayingCard(state, vm)
        TransportCard(state, vm) { sheet = it }
        RemoteCard(supportsVolume = device.supportsVolume, onKey = vm::keypress)
        QueueCard(state, vm)
    }

    when (sheet) {
        TrackKind.Audio -> TrackSheet(
            title = "Audio",
            hint = "Elige el idioma o la pista de sonido del video.",
            tracks = state.audio,
            currentId = state.audioCurrent,
            allowOff = false,
            onSelect = { track -> track?.let(vm::setAudioTrack) },
            onDismiss = { sheet = null },
        )
        TrackKind.Subtitles -> TrackSheet(
            title = "Subtítulos",
            hint = "Elige un idioma o desactívalos.",
            tracks = state.subtitles,
            currentId = state.subtitleCurrent,
            allowOff = true,
            onSelect = vm::setSubtitleTrack,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

/** Qué se reproduce ahora: título, estado, barra de avance y avisos. */
@Composable
private fun NowPlayingCard(state: LiveState, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val hasTitle = state.ours && state.title.isNotBlank()
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasTitle) {
                    PosterTile(state.title, size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (hasTitle) {
                        Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val stateText = when {
                        !state.reachable -> "Sin conexión con el Roku"
                        state.playback == Playback.Playing -> "Reproduciendo"
                        state.playback == Playback.Paused -> "En pausa"
                        state.playback == Playback.Buffering -> "Cargando…"
                        state.error.isNotBlank() -> "No se pudo reproducir"
                        else -> "Nada en reproducción"
                    }
                    Text(
                        if (state.isActive && !state.ours) "$stateText (en otro canal)" else stateText,
                        style = if (hasTitle) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        color = if (hasTitle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (state.durationMs > 0 && state.isActive) {
                var dragging by remember { mutableStateOf<Float?>(null) }
                val fraction = dragging ?: (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                Slider(
                    value = fraction,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { vm.command("seek", mapOf("pos" to ((it * state.durationMs) / 1000).toLong().toString())) }
                        dragging = null
                    },
                    enabled = state.ours,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime((fraction * state.durationMs).toLong()), style = MaterialTheme.typography.labelMedium)
                    Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (state.isActive && (state.videoCodec != null || state.audioCodec != null)) {
                Text(
                    "Video: ${state.videoCodec ?: "?"} · Audio: ${state.audioCodec ?: "?"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.audioWarning?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.playback == Playback.Idle && state.error.isNotBlank()) {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (!state.reachable || (state.playback == Playback.Idle && state.error.isNotBlank())) {
                TextButton(onClick = { vm.openDiagnostics() }) { Text("Diagnosticar") }
            }
        }
    }
}

/** Reproducción: saltos, pausa grande al centro, detener/siguiente y selectores de audio y subtítulos. */
@Composable
private fun TransportCard(state: LiveState, vm: MainViewModel, openSheet: (TrackKind) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                SkipButton(30, forward = false) { vm.command("skip", mapOf("delta" to "-30")) }
                SkipButton(10, forward = false) { vm.command("skip", mapOf("delta" to "-10")) }
                val playing = state.playback == Playback.Playing
                RoundButton(
                    label = if (playing) "Pausar" else "Reproducir",
                    onFire = { vm.keypress("Play") },
                    size = 72.dp,
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(if (playing) AppIcons.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(38.dp))
                }
                SkipButton(10, forward = true) { vm.command("skip", mapOf("delta" to "10")) }
                SkipButton(30, forward = true) { vm.command("skip", mapOf("delta" to "30")) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                QuickAction("Detener", onClick = { vm.command("stop") }) { Icon(AppIcons.Stop, contentDescription = null) }
                QuickAction("Siguiente", onClick = { vm.command("next") }, enabled = state.queue.isNotEmpty()) {
                    Icon(AppIcons.SkipNext, contentDescription = null)
                }
                QuickAction(
                    "Subtítulos",
                    onClick = { openSheet(TrackKind.Subtitles) },
                    enabled = state.canChooseSubtitles,
                    value = if (state.canChooseSubtitles) currentLabel(state.subtitles, state.subtitleCurrent, "Desactivados") else null,
                ) { Icon(AppIcons.Subtitles, contentDescription = null) }
                QuickAction(
                    "Audio",
                    onClick = { openSheet(TrackKind.Audio) },
                    enabled = state.canChooseAudio,
                    value = if (state.canChooseAudio) currentLabel(state.audio, state.audioCurrent, "Automático") else null,
                ) { Icon(AppIcons.Audiotrack, contentDescription = null) }
            }
        }
    }
}

private fun currentLabel(tracks: List<Track>, currentId: String, fallback: String): String {
    val index = tracks.indexOfFirst { it.id == currentId }
    return if (index < 0) fallback else TrackLabels.label(tracks[index], index)
}

/** El control del Roku: Atrás e Inicio a un lado, cruceta con «OK» y balancín de volumen. */
@Composable
private fun RemoteCard(supportsVolume: Boolean, onKey: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Control del Roku", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    LabeledRound("Atrás", onKey = { onKey("Back") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    LabeledRound("Inicio Roku", onKey = { onKey("Home") }) { Icon(Icons.Filled.Home, contentDescription = null) }
                }
                DPad(onKey = onKey, size = 176.dp)
                if (supportsVolume) VolumeRocker(onKey = onKey) else Spacer(Modifier.width(64.dp))
            }
        }
    }
}

@Composable
private fun LabeledRound(label: String, onKey: () -> Unit, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RoundButton(label = label, onFire = onKey, size = 56.dp, icon = icon)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun QueueCard(state: LiveState, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("A continuación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.queue.isEmpty()) {
                Text(
                    "Vacía. Usa «Añadir a la cola» al enviar un video.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.queue.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PosterTile(item.title, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.removeFromQueue(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Quitar de la cola")
                        }
                    }
                }
                OutlinedButton(onClick = { vm.command("clearqueue") }) { Text("Vaciar cola") }
            }
        }
    }
}
