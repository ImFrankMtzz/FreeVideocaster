package io.github.freevideocaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.freevideocaster.MainViewModel
import io.github.freevideocaster.media.MediaRequest

/** Qué está mal con lo que se pegó, explicado para quien no sabe de enlaces; null si se ve bien. */
internal fun linkProblem(text: String): String? {
    val link = text.trim()
    return when {
        link.isEmpty() -> null
        link.startsWith("magnet:", ignoreCase = true) ->
            "Esto es un enlace magnet (de torrent), no un video. Para verlo, ábrelo con una app como Stremio " +
                "que lo convierta en un enlace de video."
        !link.startsWith("http://", ignoreCase = true) && !link.startsWith("https://", ignoreCase = true) ->
            "El enlace debe empezar por http:// o https://."
        else -> null
    }
}

/** Hoja para enviar a mano un enlace directo a un video (lo normal es enviar desde Stremio). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var subtitle by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val problem = linkProblem(url)
    val ready = url.isNotBlank() && problem == null

    fun send(enqueue: Boolean) {
        vm.cast(
            MediaRequest(url = url.trim(), title = title.trim(), subtitleUrl = subtitle.trim().ifBlank { null }),
            enqueue = enqueue,
        )
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Enviar un enlace", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pega la dirección de un video (termina en .mp4, .mkv o .m3u8). Si el video está dentro de una " +
                    "página web, ábrela en la pestaña «Navegador».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Enlace del video") },
                singleLine = true,
                isError = problem != null,
                supportingText = problem?.let { { Text(it) } },
                trailingIcon = {
                    TextButton(onClick = { clipboardUrl(context)?.let { url = it } }) { Text("Pegar") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { advanced = !advanced }) {
                Text(if (advanced) "Ocultar opciones avanzadas" else "Opciones avanzadas")
            }
            if (advanced) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Enlace de subtítulos .srt o .vtt (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = ready, onClick = { send(enqueue = false) }) { Text("Reproducir") }
                OutlinedButton(enabled = ready, onClick = { send(enqueue = true) }) { Text("Añadir a la cola") }
            }
        }
    }
}
