package io.github.freevideocaster.receive

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.freevideocaster.CastResult
import io.github.freevideocaster.MainActivity
import io.github.freevideocaster.PlaybackResult
import io.github.freevideocaster.caster
import io.github.freevideocaster.data.HistoryEntry
import io.github.freevideocaster.media.IncomingIntentParser
import io.github.freevideocaster.media.MediaRequest
import io.github.freevideocaster.media.TitleCleaner
import io.github.freevideocaster.roku.CastException
import io.github.freevideocaster.roku.LiveState
import io.github.freevideocaster.roku.Playback
import io.github.freevideocaster.ui.AppIcons
import io.github.freevideocaster.ui.ConnectionCard
import io.github.freevideocaster.ui.DeviceSheet
import io.github.freevideocaster.ui.LocalNetworkPermissionBanner
import io.github.freevideocaster.ui.NoticeCard
import io.github.freevideocaster.ui.NotificationPermissionBanner
import io.github.freevideocaster.ui.PosterTile
import io.github.freevideocaster.ui.RoundButton
import io.github.freevideocaster.ui.SkipButton
import io.github.freevideocaster.ui.WifiWarning
import io.github.freevideocaster.ui.copyToClipboard
import io.github.freevideocaster.ui.formatTime
import io.github.freevideocaster.ui.theme.FreeVideocasterTheme
import io.github.freevideocaster.ui.theme.rememberAppPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Se abre cuando otra app (Stremio, un navegador…) pasa un video a FreeVideocaster. */
class ReceiveActivity : ComponentActivity() {

    private val vm: ReceiveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.load(intent)
        setContent {
            FreeVideocasterTheme(rememberAppPrefs()) {
                ReceiveScreen(
                    vm = vm,
                    onClose = { finish() },
                    onOpenRemote = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_OPEN_REMOTE, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        finish()
                    },
                )
            }
        }
    }
}

class ReceiveViewModel(app: Application) : AndroidViewModel(app) {

    private val caster = app.caster

    val roku = caster.roku.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var parsed by mutableStateOf<IncomingIntentParser.Parsed?>(null)
        private set
    var sendSubtitles by mutableStateOf(true)
    var working by mutableStateOf(false)
        private set
    var progress by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Entrada del historial con un punto de reproducción guardado para este mismo enlace. */
    var resume by mutableStateOf<HistoryEntry?>(null)
        private set

    /** Título y etiquetas que se muestran: primero lo que manda la otra app, luego el nombre real del archivo. */
    var shown by mutableStateOf(TitleCleaner.Parsed("", ""))
        private set
    var identifying by mutableStateOf(false)
        private set

    /** Lo que ya se envió (null mientras no se haya enviado nada) y cómo fue la espera del Roku. */
    var sent by mutableStateOf<CastResult?>(null)
        private set
    var outcome by mutableStateOf<PlaybackResult?>(null)
        private set

    /** Segundos que faltan para el envío automático cuando hay un punto guardado y se puede elegir. */
    var countdown by mutableStateOf<Int?>(null)
        private set

    /** El video lo abrió un reproductor externo (Stremio…): se puede volver a esa app. */
    var fromPlayerIntent by mutableStateOf(false)
        private set

    /** La pantalla debe cerrarse sola para volver a la app anterior. */
    var returnNow by mutableStateOf(false)
        private set

    private var autoCancelled = false

    fun load(intent: Intent) {
        if (parsed != null) return
        val incoming = IncomingIntentParser.parse(intent)
        parsed = incoming
        fromPlayerIntent = intent.action == Intent.ACTION_VIEW
        val request = incoming.request ?: return
        shown = TitleCleaner.parse(
            listOf(request.title, TitleCleaner.nameFromUrl(request.url)).firstOrNull { !TitleCleaner.isUseless(it) }.orEmpty(),
        )
        viewModelScope.launch {
            resume = caster.history.find(request.key)?.takeIf { it.resumeMs > 0 }
            launch { identify(request) }
            autoSendIfWanted()
        }
    }

    /** Stremio casi nunca manda un título útil; el nombre real aparece al abrir el enlace. */
    private suspend fun identify(request: MediaRequest) {
        identifying = shown.title.isBlank()
        val better = runCatching { caster.describe(request) }.getOrNull()
        if (better != null && better.title.isNotBlank() && sent == null) shown = better
        identifying = false
    }

    private suspend fun autoSendIfWanted() {
        if (!caster.prefs.value.autoSend || parsed?.request == null) return
        withTimeoutOrNull(2000) { roku.filterNotNull().first() } ?: return
        if (resume != null) {
            // Con un punto guardado se da un instante para elegir entre continuar o empezar de nuevo.
            for (left in 3 downTo 1) {
                countdown = left
                delay(1000)
                if (autoCancelled) {
                    countdown = null
                    return
                }
            }
            countdown = null
        }
        if (!autoCancelled) send(enqueue = false)
    }

    /** Con [fromStart] se ignora el punto guardado y se empieza desde cero. */
    fun send(enqueue: Boolean, fromStart: Boolean = false) {
        autoCancelled = true
        countdown = null
        if (working) return
        val request = parsed?.request ?: return
        val toSend = if (sendSubtitles) request else request.copy(subtitleUrl = null)
        val startMs = if (fromStart) 0L else resume?.resumeMs ?: 0L
        viewModelScope.launch {
            working = true
            error = null
            sent = null
            outcome = null
            try {
                val result = caster.cast(toSend, enqueue, startMs) { progress = it }
                sent = result
                shown = TitleCleaner.Parsed(result.title, result.detail) // el mismo título que verá el Roku
                if (!result.queued) {
                    progress = "Esperando a que el Roku empiece…"
                    val played = caster.awaitPlayback()
                    outcome = played
                    if (played.started && caster.prefs.value.autoReturn) returnNow = true
                }
            } catch (e: CastException) {
                error = e.message
            } finally {
                working = false
                progress = null
            }
        }
    }

    fun command(action: String, extra: Map<String, String> = emptyMap()) = quietly { caster.command(action, extra) }

    fun keypress(key: String) = quietly { caster.keypress(key) }

    private fun quietly(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CastException) {
                error = e.message
            }
        }
    }
}

@Composable
private fun ReceiveScreen(vm: ReceiveViewModel, onClose: () -> Unit, onOpenRemote: () -> Unit) {
    val context = LocalContext.current
    val roku by vm.roku.collectAsStateWithLifecycle()
    val live by context.caster.live.collectAsStateWithLifecycle()
    val parsed = vm.parsed
    val request = parsed?.request
    var showDevices by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(vm.returnNow) { if (vm.returnNow) onClose() }

    fun openDiagnostics() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_DIAGNOSTICS, true)
                .putExtra(MainActivity.EXTRA_DIAGNOSTICS_LINK, request?.url.orEmpty())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        onClose()
    }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val sent = vm.sent
            if (sent != null) {
                SentContent(
                    vm = vm,
                    sent = sent,
                    live = live,
                    rokuName = roku?.name ?: "tu Roku",
                    onOpenRemote = onOpenRemote,
                    onClose = onClose,
                    onDiagnose = { openDiagnostics() },
                )
            } else {
                Text("Enviar al Roku", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                LocalNetworkPermissionBanner()
                WifiWarning()
                NotificationPermissionBanner()

                // El video: miniatura, título limpio y etiquetas (sin nombres de release ni títulos como «playback»).
                Card(Modifier.fillMaxWidth()) {
                    if (request == null) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("No se encontró un enlace de video", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Mira los detalles técnicos de abajo para ver qué envió la otra app.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        val shown = vm.shown
                        val title = when {
                            shown.title.isNotBlank() -> shown.title
                            vm.identifying -> "Identificando el video…"
                            else -> request.url.toUri().host?.let { "Video de $it" } ?: "Video"
                        }
                        val resume = vm.resume
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PosterTile(shown.title.ifBlank { title }, size = 72.dp)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    if (shown.detail.isNotBlank()) {
                                        Text(shown.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            if (resume != null) {
                                LinearProgressIndicator(
                                    progress = { (resume.resumeMs.toFloat() / resume.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    "Ya lo empezaste: viste hasta ${formatTime(resume.resumeMs)} de ${formatTime(resume.durationMs)}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (request.subtitleUrl != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = vm.sendSubtitles, onCheckedChange = { vm.sendSubtitles = it })
                                    Text("  Enviar subtítulos", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                ConnectionCard(roku, live, onClick = { showDevices = true })

                val enabled = request != null && roku != null && !vm.working
                val resume = vm.resume
                if (resume != null) {
                    Button(enabled = enabled, onClick = { vm.send(enqueue = false) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continuar desde ${formatTime(resume.resumeMs)}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = enabled, onClick = { vm.send(enqueue = false, fromStart = true) }) { Text("Desde el inicio") }
                        TextButton(enabled = enabled, onClick = { vm.send(enqueue = true) }) { Text("Añadir a la cola") }
                    }
                } else {
                    Button(enabled = enabled, onClick = { vm.send(enqueue = false) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Reproducir ahora")
                    }
                    TextButton(enabled = enabled, onClick = { vm.send(enqueue = true) }) { Text("Añadir a la cola") }
                }
                vm.countdown?.let { left ->
                    Text(
                        "Continuando solo en $left s. Toca un botón para elegir otra opción.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (vm.working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    vm.progress?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                vm.error?.let { message ->
                    NoticeCard(title = "No se pudo enviar", text = message) {
                        OutlinedButton(onClick = { openDiagnostics() }) { Text("Diagnosticar") }
                    }
                }

                // Diagnóstico: todo lo que llegó en el intent (útil para ver si Stremio manda subtítulos).
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Ocultar detalles técnicos" else "Ver detalles técnicos")
                }
                if (showDetails && parsed != null) {
                    val text = parsed.details.joinToString("\n") { (key, value) -> "$key = $value" }
                    SelectionContainer {
                        Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { copyToClipboard(context, "Detalles del intent", text) }) { Text("Copiar") }
                }
            }
        }
    }

    if (showDevices) DeviceSheet(onDismiss = { showDevices = false }, onDiagnose = { openDiagnostics() })
}

/**
 * Lo que se ve tras enviar: qué se reproduce, cómo va y un botón grande para abrir el control.
 * Reemplaza a la tarjeta pequeña con «Abrir el control» que había antes.
 */
@Composable
private fun SentContent(
    vm: ReceiveViewModel,
    sent: CastResult,
    live: LiveState?,
    rokuName: String,
    onOpenRemote: () -> Unit,
    onClose: () -> Unit,
    onDiagnose: () -> Unit,
) {
    val request = vm.parsed?.request
    val outcome = vm.outcome
    // Solo se muestra el avance si el Roku reproduce justo este video (y no uno anterior que aún no terminó).
    val state = live?.takeIf { it.ours && it.isActive && !sent.queued && (request == null || it.key.isEmpty() || it.key == request.key) }
    val failed = outcome?.failed == true

    val header = when {
        failed -> "No se pudo reproducir"
        sent.queued -> "Añadido a la cola"
        state?.playback == Playback.Paused -> "En pausa"
        state?.playback == Playback.Playing -> "Reproduciendo en $rokuName"
        else -> "Enviando a $rokuName…"
    }
    Text(header, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val shown = vm.shown
            PosterTile(shown.title, size = 128.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                shown.title.ifBlank { "Video" },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (shown.detail.isNotBlank()) {
                Text(shown.detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            }
            if (state != null && state.durationMs > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(state.positionMs), style = MaterialTheme.typography.labelMedium)
                    Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelMedium)
                }
            } else if (vm.working) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                vm.progress?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }

    // Avisos: el motivo del fallo, o lo que haya que saber del audio y de una espera larga.
    if (outcome != null && outcome.failed) {
        NoticeCard(title = "El Roku no pudo reproducirlo", text = outcome.message.removePrefix("El Roku no pudo reproducirlo.").trim()) {
            OutlinedButton(onClick = onDiagnose) { Text("Diagnosticar") }
        }
    } else if (outcome != null) {
        val note = if (outcome.started) outcome.message.removePrefix("Reproduciendo en $rokuName.").trim() else outcome.message
        if (note.isNotBlank()) {
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (sent.queued) {
        Text(sent.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    vm.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

    // Pausa y saltos al alcance del pulgar, sin entrar al control completo.
    if (state != null) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            }
        }
    }

    Button(onClick = onOpenRemote, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Abrir el control", style = MaterialTheme.typography.titleMedium)
    }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(if (vm.fromPlayerIntent) "Volver a Stremio" else "Cerrar")
    }
}
