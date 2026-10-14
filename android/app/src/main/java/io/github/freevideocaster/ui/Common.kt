package io.github.freevideocaster.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.freevideocaster.media.IncomingIntentParser
import io.github.freevideocaster.net.LocalNetwork

/** Tarjeta de aviso con un botón de acción. */
@Composable
fun NoticeCard(
    title: String,
    text: String,
    actions: @Composable () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}

/** Pide el permiso de red local de Android 17 si falta. En versiones anteriores no muestra nada. */
@Composable
fun LocalNetworkPermissionBanner() {
    if (!LocalNetwork.needsPermission()) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocalNetwork.hasPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LifecycleResumeEffect(Unit) {
        granted = LocalNetwork.hasPermission(context)
        onPauseOrDispose { }
    }
    if (granted) return

    NoticeCard(
        title = "Permiso de red local",
        text = "Android 17 pide tu permiso para que la app se comunique con el Roku en tu Wi-Fi. " +
            "Sin él no se puede enviar nada. Si ya lo negaste, actívalo en los ajustes de la app.",
    ) {
        Button(onClick = { launcher.launch(LocalNetwork.PERMISSION) }) { Text("Conceder") }
        OutlinedButton(onClick = { openAppSettings(context) }) { Text("Abrir ajustes") }
    }
}

/** Android 13+: pide permiso para mostrar los controles en la barra de notificaciones. */
@Composable
fun NotificationPermissionBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LifecycleResumeEffect(Unit) {
        granted = hasNotificationPermission(context)
        onPauseOrDispose { }
    }
    if (granted || dismissed) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Controles en la barra de notificaciones", style = MaterialTheme.typography.titleMedium)
            Text(
                "Actívalas para pausar, saltar y detener el video desde la barra y la pantalla de bloqueo, " +
                    "sin abrir la app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Activar") }
                TextButton(onClick = { dismissed = true }) { Text("Ahora no") }
            }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** Avisa si el teléfono no está en Wi-Fi. */
@Composable
fun WifiWarning() {
    val context = LocalContext.current
    var onLan by remember { mutableStateOf(LocalNetwork.isOnLocalNetwork(context)) }
    LifecycleResumeEffect(Unit) {
        onLan = LocalNetwork.isOnLocalNetwork(context)
        onPauseOrDispose { }
    }
    if (!onLan) {
        NoticeCard(
            title = "Sin Wi-Fi",
            text = "El teléfono no está conectado a una red Wi-Fi. Conéctalo a la misma red que el Roku.",
        )
    }
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** Primer enlace http(s) que haya en el portapapeles. */
fun clipboardUrl(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return IncomingIntentParser.extractUrl(clip.getItemAt(0).coerceToText(context)?.toString())
}

fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun formatTime(millis: Long): String {
    val total = millis / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
