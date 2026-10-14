package io.github.freevideocaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.freevideocaster.MainViewModel
import io.github.freevideocaster.data.ColorStyle
import io.github.freevideocaster.data.SearchEngine
import io.github.freevideocaster.data.ThemeMode
import java.text.DateFormat
import java.util.Date

/** Ajustes de la app: apariencia, navegador y envío desde otras apps. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val ready by vm.adBlocker.ready.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall)

            Group("Apariencia") {
                ChoiceRow("Tema", ThemeMode.entries, prefs.theme, { it.label }) { vm.setTheme(it) }
                ChoiceRow("Colores", ColorStyle.entries, prefs.colors, { it.label }) { vm.setColors(it) }
                SwitchRow(
                    title = "Negro AMOLED",
                    detail = "Fondo negro puro con el tema oscuro: ahorra batería en pantallas OLED.",
                    checked = prefs.amoled,
                ) { vm.setAmoled(it) }
            }

            HorizontalDivider()

            Group("Navegador") {
                ChoiceRow("Buscador", SearchEngine.entries, prefs.search, { it.label }) { vm.setSearch(it) }
                SwitchRow(
                    title = "Bloquear anuncios",
                    detail = "Corta anuncios, ventanas emergentes y redirecciones. Se aplica en el navegador de la app.",
                    checked = prefs.adBlock,
                ) { vm.setAdBlock(it) }
                if (prefs.adBlock) {
                    val updated = vm.adBlocker.updatedAt
                    Text(
                        buildString {
                            append(if (ready) "${vm.adBlocker.ruleCount} reglas cargadas. " else "Las listas se cargan al abrir el navegador. ")
                            append(
                                if (updated != null) "Actualizadas el ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(updated))}."
                                else "Son las que trae la app.",
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(enabled = !vm.updatingLists, onClick = { vm.updateFilterLists() }) {
                        Text(if (vm.updatingLists) "Descargando…" else "Actualizar listas")
                    }
                    vm.listsStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            HorizontalDivider()

            Group("Al recibir un video de otra app") {
                SwitchRow(
                    title = "Enviar automáticamente",
                    detail = "Se envía al Roku sin pulsar «Reproducir ahora». Si ya lo habías empezado, pregunta si continuar.",
                    checked = prefs.autoSend,
                ) { vm.setAutoSend(it) }
                SwitchRow(
                    title = "Volver a la app anterior",
                    detail = "Cuando el Roku empieza a reproducir, regresa a Stremio.",
                    checked = prefs.autoReturn,
                ) { vm.setAutoReturn(it) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
