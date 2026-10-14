package io.github.freevideocaster.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.freevideocaster.caster
import io.github.freevideocaster.data.AppPrefs
import io.github.freevideocaster.data.ColorStyle
import io.github.freevideocaster.data.ThemeMode

private val Brand = Color(0xFF00AA8C)
private val BrandDark = Color(0xFF006B58)

/** Con los valores por defecto de [AppPrefs] queda igual que siempre: sigue al sistema y usa los colores del fondo. */
@Composable
fun FreeVideocasterTheme(prefs: AppPrefs = AppPrefs(), content: @Composable () -> Unit) {
    val dark = when (prefs.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val dynamic = prefs.colors == ColorStyle.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var colors: ColorScheme = when {
        dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Brand)
        else -> lightColorScheme(primary = BrandDark)
    }
    if (dark && prefs.amoled) colors = colors.toAmoled()

    // enableEdgeToEdge() decide los íconos de las barras según el tema del sistema; si la persona
    // fuerza claro u oscuro hay que repetirlo, o los íconos quedarían invisibles.
    val activity = context as? ComponentActivity
    DisposableEffect(activity, dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
        onDispose {}
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private val LIGHT_SCRIM = Color(0xE6FFFFFF).toArgb()
private val DARK_SCRIM = Color(0x801B1B1B).toArgb()

/** Preferencias en vivo: al cambiar un ajuste, las pantallas abiertas se actualizan solas. */
@Composable
fun rememberAppPrefs(): AppPrefs = LocalContext.current.caster.prefs.collectAsStateWithLifecycle().value

/** Fondo negro puro y superficies casi negras (ahorra batería en pantallas OLED). */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)
