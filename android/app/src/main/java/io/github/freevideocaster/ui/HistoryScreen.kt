package io.github.freevideocaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.freevideocaster.MainViewModel
import io.github.freevideocaster.data.HistoryEntry
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val entries by vm.history.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Historial", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Borrar todo") }
        }
        if (entries.isEmpty()) {
            Text("Aquí aparecerán los videos que envíes.", Modifier.padding(horizontal = 16.dp))
            return@Column
        }
        Text(
            "Los enlaces de TorBox caducan: si uno ya no funciona, vuelve a abrirlo desde Stremio.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { "${it.timestamp}-${it.url}" }) { entry ->
                HistoryItem(entry, dateFormat.format(Date(entry.timestamp)), vm)
                HorizontalDivider()
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todo el historial?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    confirmClear = false
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun HistoryItem(entry: HistoryEntry, date: String, vm: MainViewModel) {
    val resume = entry.resumeMs
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PosterTile(entry.title, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                entry.title.ifBlank { "Video" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val host = entry.url.toUri().host.orEmpty()
        Text(
            listOf(entry.detail, host, date).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (resume > 0 && entry.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (resume.toFloat() / entry.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Text(
                "Viste hasta ${formatTime(resume)} de ${formatTime(entry.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (resume > 0) {
                Button(onClick = { vm.cast(entry.toRequest(), enqueue = false, startMs = resume) }) { Text("Continuar") }
                OutlinedButton(onClick = { vm.cast(entry.toRequest(), enqueue = false) }) { Text("Inicio") }
            } else {
                Button(onClick = { vm.cast(entry.toRequest(), enqueue = false) }) { Text("Reproducir") }
            }
            TextButton(onClick = { vm.cast(entry.toRequest(), enqueue = true, startMs = resume) }) { Text("Cola") }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { vm.removeHistory(entry) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                }
            }
        }
    }
}
