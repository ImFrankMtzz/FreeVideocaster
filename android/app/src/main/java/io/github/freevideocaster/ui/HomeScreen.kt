package io.github.freevideocaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.freevideocaster.MainViewModel
import io.github.freevideocaster.Tab
import io.github.freevideocaster.data.HistoryEntry
import io.github.freevideocaster.media.MediaRequest

/** Video corto de un servidor de pruebas, para comprobar que todo funciona sin depender de Stremio. */
private const val SAMPLE_URL = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_5MB.mp4"

@Composable
fun HomeScreen(vm: MainViewModel) {
    val roku by vm.roku.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var showDevices by rememberSaveable { mutableStateOf(false) }
    var showLink by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FreeVideocaster", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Envía videos a tu Roku",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.showSettings = true }) {
                Icon(AppIcons.Settings, contentDescription = "Ajustes")
            }
        }

        LocalNetworkPermissionBanner()
        WifiWarning()
        NotificationPermissionBanner()

        ConnectionCard(roku, live, onClick = { showDevices = true })

        if (history.isEmpty()) {
            GettingStartedCard(
                canTest = roku != null,
                onTest = { vm.cast(MediaRequest(url = SAMPLE_URL, title = "Video de ejemplo"), enqueue = false) },
            )
        } else {
            SectionHeader("Seguir viendo") {
                TextButton(onClick = { vm.tab = Tab.History }) { Text("Ver todo") }
            }
            history.take(RECENT_COUNT).forEach { entry ->
                RecentRow(entry) { vm.cast(entry.toRequest(), enqueue = false, startMs = entry.resumeMs) }
            }
        }

        LinkCard(onClick = { showLink = true })

        TextButton(onClick = { vm.openDiagnostics() }) { Text("Diagnóstico de conexión") }
    }

    if (showDevices) DeviceSheet(onDismiss = { showDevices = false }, onDiagnose = { vm.openDiagnostics() })
    if (showLink) LinkSheet(vm, onDismiss = { showLink = false })
    if (vm.showSettings) SettingsSheet(vm, onDismiss = { vm.showSettings = false })
}

private const val RECENT_COUNT = 4

/** Un video reciente: tocarlo lo envía al Roku, retomando donde se quedó si había avance. */
@Composable
private fun RecentRow(entry: HistoryEntry, onPlay: () -> Unit) {
    val resume = entry.resumeMs
    Card(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterTile(entry.title, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { "Video" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when {
                    resume > 0 -> "Continuar desde ${formatTime(resume)}"
                    entry.detail.isNotBlank() -> entry.detail
                    else -> entry.url.toUri().host.orEmpty()
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (resume > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (resume > 0 && entry.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (resume.toFloat() / entry.durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Guía de tres pasos para quien aún no ha enviado nada, con un video de prueba. */
@Composable
private fun GettingStartedCard(canTest: Boolean, onTest: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cómo empezar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Step(1, "Abre Stremio y elige una película o un episodio.")
            Step(
                2,
                "Cuando pregunte con qué app reproducir, elige «FreeVideocaster». " +
                    "(Stremio › Ajustes › Reproductor › Reproductor externo.)",
            )
            Step(3, "Se reproduce en tu Roku y desde aquí puedes controlarlo.")
            Button(enabled = canTest, onClick = onTest) { Text("Probar con un video de ejemplo") }
            if (!canTest) {
                Text(
                    "Primero conecta tu Roku.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** Entrada para pegar a mano el enlace directo de un video. */
@Composable
private fun LinkCard(onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Enviar un enlace", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Pega la dirección de un video",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(AppIcons.Paste, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
