package io.github.freevideocaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.freevideocaster.MainViewModel
import io.github.freevideocaster.caster
import io.github.freevideocaster.diag.Check
import io.github.freevideocaster.diag.Level
import kotlinx.coroutines.launch

/** Revisa de punta a punta por qué un video puede no llegar al Roku, y explica cómo arreglarlo. */
@Composable
fun DiagnosticsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(true) }
    var checks by remember { mutableStateOf<List<Check>>(emptyList()) }
    var link by rememberSaveable { mutableStateOf(vm.diagnosticsLink) }

    fun run() {
        scope.launch {
            running = true
            checks = context.caster.diagnostics.run(link.ifBlank { null })
            running = false
        }
    }

    LaunchedEffect(Unit) { run() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Diagnóstico", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Prueba la red, los permisos, el Roku, el canal y, si quieres, un enlace concreto.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text("Enlace para probar (opcional)") },
            singleLine = true,
            trailingIcon = {
                TextButton(onClick = { clipboardUrl(context)?.let { link = it } }) { Text("Pegar") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !running, onClick = { run() }) { Text("Probar de nuevo") }
            OutlinedButton(
                enabled = !running && checks.isNotEmpty(),
                onClick = { copyToClipboard(context, "Diagnóstico", context.caster.diagnostics.report(checks)) },
            ) { Text("Copiar informe") }
        }
        if (running) LinearProgressIndicator(Modifier.fillMaxWidth())

        checks.forEach { check -> CheckCard(check) }
    }
}

@Composable
private fun CheckCard(check: Check) {
    val color = when (check.level) {
        Level.Ok -> Color(0xFF2E9E6B)
        Level.Warn -> Color(0xFFE0A200)
        Level.Fail -> MaterialTheme.colorScheme.error
        Level.Info -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(check.level.symbol, color = color, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(check.title, style = MaterialTheme.typography.titleSmall)
                Text(check.detail, style = MaterialTheme.typography.bodyMedium)
                check.hint?.let {
                    Text("→ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
