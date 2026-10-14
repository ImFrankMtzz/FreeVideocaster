package io.github.freevideocaster

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.freevideocaster.roku.Playback
import io.github.freevideocaster.ui.AppIcons
import io.github.freevideocaster.ui.PosterTile
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.freevideocaster.ui.DiagnosticsScreen
import io.github.freevideocaster.ui.HistoryScreen
import io.github.freevideocaster.ui.HomeScreen
import io.github.freevideocaster.ui.RemoteScreen
import io.github.freevideocaster.ui.browser.BrowserScreen
import io.github.freevideocaster.ui.theme.FreeVideocasterTheme
import io.github.freevideocaster.ui.theme.rememberAppPrefs

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            FreeVideocasterTheme(rememberAppPrefs()) { MainScreen(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_REMOTE, false) == true) {
            vm.showDiagnostics = false
            vm.tab = Tab.Remote
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_DIAGNOSTICS, false) == true) {
            vm.openDiagnostics(intent.getStringExtra(EXTRA_DIAGNOSTICS_LINK).orEmpty())
        }
    }

    companion object {
        const val EXTRA_OPEN_REMOTE = "open_remote"
        const val EXTRA_OPEN_DIAGNOSTICS = "open_diagnostics"
        const val EXTRA_DIAGNOSTICS_LINK = "diagnostics_link"
    }
}

private val Tab.label
    get() = when (this) {
        Tab.Home -> "Inicio"
        Tab.Remote -> "Control"
        Tab.History -> "Historial"
        Tab.Browser -> "Navegador"
    }

private val Tab.icon: ImageVector
    get() = when (this) {
        Tab.Home -> Icons.Filled.Home
        Tab.Remote -> Icons.Filled.PlayArrow
        Tab.History -> Icons.AutoMirrored.Filled.List
        Tab.Browser -> Icons.Filled.Search
    }

@Composable
private fun MainScreen(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    BackHandler(enabled = vm.showDiagnostics) { vm.showDiagnostics = false }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                MiniPlayer(vm)
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = vm.tab == tab && !vm.showDiagnostics,
                            onClick = {
                                vm.showDiagnostics = false
                                vm.tab = tab
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (vm.showDiagnostics) {
                DiagnosticsScreen(vm)
            } else {
                when (vm.tab) {
                    Tab.Home -> HomeScreen(vm)
                    Tab.Remote -> RemoteScreen(vm)
                    Tab.History -> HistoryScreen(vm)
                    Tab.Browser -> BrowserScreen(vm)
                }
            }
        }
    }
}

/**
 * Barra fija sobre las pestañas mientras algo se reproduce en el Roku: título, avance y pausa.
 * Tocarla abre el control. No aparece en la propia pantalla de control.
 */
@Composable
private fun MiniPlayer(vm: MainViewModel) {
    val live by vm.live.collectAsStateWithLifecycle()
    val state = live ?: return
    if (!(state.ours && state.isActive) || vm.tab == Tab.Remote || vm.showDiagnostics) return

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().clickable {
            vm.showDiagnostics = false
            vm.tab = Tab.Remote
        },
    ) {
        Column {
            LinearProgressIndicator(
                progress = { if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PosterTile(state.title, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title.ifBlank { "Reproduciendo" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when (state.playback) {
                            Playback.Playing -> "Reproduciendo en el Roku"
                            Playback.Paused -> "En pausa"
                            else -> "Cargando…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.keypress("Play") }) {
                    Icon(
                        if (state.playback == Playback.Playing) AppIcons.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.playback == Playback.Playing) "Pausar" else "Reproducir",
                    )
                }
            }
        }
    }
}
