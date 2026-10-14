package io.github.freevideocaster.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.freevideocaster.caster
import io.github.freevideocaster.roku.CastException
import io.github.freevideocaster.roku.LiveState
import io.github.freevideocaster.roku.RokuDevice
import kotlinx.coroutines.launch

private val Online = Color(0xFF2E9E6B)
private val Offline = Color(0xFFE0A200)

/** Descripción corta del equipo: «Roku TV · Modelo · 192.168.1.50». */
private fun RokuDevice.summary(): String =
    listOf(if (isTv) "Roku TV" else "Roku", model, ip).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * Tarjeta de conexión de la pantalla de inicio: invita a buscar el Roku si no hay ninguno y, si lo hay,
 * muestra su nombre y si responde.
 */
@Composable
fun ConnectionCard(roku: RokuDevice?, live: LiveState?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (roku == null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DeviceBadge(AppIcons.Cast, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Conecta tu Roku", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Búscalo en tu red Wi-Fi con un toque.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = onClick) { Text("Buscar") }
            }
        }
        return
    }

    val reachable = live?.reachable == true
    Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DeviceBadge(
                if (roku.isTv) AppIcons.Tv else AppIcons.Cast,
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(roku.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    roku.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (reachable) Online else if (live == null) MaterialTheme.colorScheme.outline else Offline)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            reachable -> "Conectado"
                            live == null -> "Comprobando…"
                            else -> "Sin respuesta"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            TextButton(onClick = onClick) { Text("Cambiar") }
        }
    }
}

@Composable
private fun DeviceBadge(icon: ImageVector, container: Color, content: Color) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content)
    }
}

/**
 * Hoja para elegir el dispositivo: busca en la red al abrirse, lista lo que encuentra, permite escribir
 * una IP y aclara con qué equipos funciona la app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSheet(onDismiss: () -> Unit, onDiagnose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected by context.caster.roku.collectAsStateWithLifecycle(initialValue = null)
    var searching by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<RokuDevice>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var manualIp by rememberSaveable { mutableStateOf("") }
    var showManual by rememberSaveable { mutableStateOf(false) }

    fun search() {
        scope.launch {
            searching = true
            error = null
            try {
                devices = context.caster.discover()
                if (devices.isEmpty()) {
                    error = "No encontré ningún Roku. Comprueba que esté encendido y conectado a la misma red Wi-Fi " +
                        "que el teléfono, o agrégalo con su IP."
                }
            } catch (e: CastException) {
                error = e.message
            } finally {
                searching = false
            }
        }
    }

    LaunchedEffect(Unit) { search() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Conectar a tu TV", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Elige el dispositivo al que quieres enviar videos. El teléfono y el dispositivo deben estar en la misma red Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (searching) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Buscando dispositivos…")
                }
            }

            devices.forEach { device ->
                val isSelected = selected?.serial?.isNotEmpty() == true && selected?.serial == device.serial ||
                    selected?.ip == device.ip
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            context.caster.select(device)
                            onDismiss()
                        }
                    },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        DeviceBadge(
                            if (device.isTv) AppIcons.Tv else AppIcons.Cast,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.titleSmall)
                            Text(device.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) Icon(Icons.Filled.Check, contentDescription = "Seleccionado")
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !searching, onClick = { search() }) { Text("Buscar de nuevo") }
                OutlinedButton(onClick = { showManual = !showManual }) { Text("Agregar por IP") }
            }

            if (showManual) {
                Text(
                    "Escribe la IP del Roku (en el Roku: Configuración › Red › Acerca de).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = manualIp.isNotBlank() && !searching,
                    onClick = {
                        scope.launch {
                            try {
                                context.caster.selectByIp(manualIp)
                                onDismiss()
                            } catch (e: CastException) {
                                error = e.message
                            }
                        }
                    },
                ) { Text("Usar esta IP") }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Compatibilidad", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Hoy funciona con Roku (televisores y reproductores). Fire TV, Chromecast y LG webOS " +
                            "todavía no son compatibles.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            TextButton(onClick = {
                onDismiss()
                onDiagnose()
            }) { Text("Solucionar problemas de conexión") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
