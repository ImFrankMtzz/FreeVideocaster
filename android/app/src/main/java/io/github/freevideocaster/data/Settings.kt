package io.github.freevideocaster.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.freevideocaster.roku.RokuDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) { System("Sistema"), Light("Claro"), Dark("Oscuro") }

enum class ColorStyle(val label: String) { Wallpaper("Del fondo de pantalla"), Brand("Verde FreeVideocaster") }

enum class SearchEngine(val label: String, private val searchUrl: String, val homeUrl: String) {
    Google("Google", "https://www.google.com/search?q=", "https://www.google.com"),
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?q=", "https://duckduckgo.com"),
    Bing("Bing", "https://www.bing.com/search?q=", "https://www.bing.com");

    fun searchFor(query: String): String = searchUrl + java.net.URLEncoder.encode(query, "UTF-8")
}

/** Preferencias de la persona (no del Roku). Los valores por defecto son los de una instalación nueva. */
data class AppPrefs(
    val theme: ThemeMode = ThemeMode.System,
    val amoled: Boolean = false,
    val colors: ColorStyle = ColorStyle.Wallpaper,
    val search: SearchEngine = SearchEngine.Google,
    val adBlock: Boolean = true,
    /** Enviar al Roku en cuanto llega el video de otra app, sin pulsar «Reproducir ahora». */
    val autoSend: Boolean = false,
    /** Volver a la app anterior (p. ej. Stremio) cuando el Roku ya reproduce. */
    val autoReturn: Boolean = false,
    /** Buscar subtítulos en línea (español e inglés) al enviar un video. */
    val onlineSubtitles: Boolean = true,
)

/** Guarda el último Roku usado (se recuerda por número de serie por si cambia su IP) y las preferencias. */
class Settings(private val context: Context) {

    private object Keys {
        val ip = stringPreferencesKey("roku_ip")
        val serial = stringPreferencesKey("roku_serial")
        val name = stringPreferencesKey("roku_name")
        val model = stringPreferencesKey("roku_model")
        val isTv = booleanPreferencesKey("roku_is_tv")
        val supportsVolume = booleanPreferencesKey("roku_supports_volume")

        val theme = stringPreferencesKey("pref_theme")
        val amoled = booleanPreferencesKey("pref_amoled")
        val colors = stringPreferencesKey("pref_colors")
        val search = stringPreferencesKey("pref_search")
        val adBlock = booleanPreferencesKey("pref_adblock")
        val autoSend = booleanPreferencesKey("pref_auto_send")
        val autoReturn = booleanPreferencesKey("pref_auto_return")
        val onlineSubtitles = booleanPreferencesKey("pref_online_subtitles")
    }

    val roku: Flow<RokuDevice?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ip]?.let { ip ->
            RokuDevice(
                ip = ip,
                serial = prefs[Keys.serial].orEmpty(),
                name = prefs[Keys.name] ?: "Roku",
                model = prefs[Keys.model].orEmpty(),
                isTv = prefs[Keys.isTv] ?: false,
                supportsVolume = prefs[Keys.supportsVolume] ?: false,
            )
        }
    }

    suspend fun saveRoku(device: RokuDevice) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ip] = device.ip
            prefs[Keys.serial] = device.serial
            prefs[Keys.name] = device.name
            prefs[Keys.model] = device.model
            prefs[Keys.isTv] = device.isTv
            prefs[Keys.supportsVolume] = device.supportsVolume
        }
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            theme = enumOrDefault(p[Keys.theme], ThemeMode.System),
            amoled = p[Keys.amoled] ?: false,
            colors = enumOrDefault(p[Keys.colors], ColorStyle.Wallpaper),
            search = enumOrDefault(p[Keys.search], SearchEngine.Google),
            adBlock = p[Keys.adBlock] ?: true,
            autoSend = p[Keys.autoSend] ?: false,
            autoReturn = p[Keys.autoReturn] ?: false,
            onlineSubtitles = p[Keys.onlineSubtitles] ?: true,
        )
    }

    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setAmoled(value: Boolean) = context.dataStore.edit { it[Keys.amoled] = value }
    suspend fun setColors(value: ColorStyle) = context.dataStore.edit { it[Keys.colors] = value.name }
    suspend fun setSearch(value: SearchEngine) = context.dataStore.edit { it[Keys.search] = value.name }
    suspend fun setAdBlock(value: Boolean) = context.dataStore.edit { it[Keys.adBlock] = value }
    suspend fun setAutoSend(value: Boolean) = context.dataStore.edit { it[Keys.autoSend] = value }
    suspend fun setAutoReturn(value: Boolean) = context.dataStore.edit { it[Keys.autoReturn] = value }
    suspend fun setOnlineSubtitles(value: Boolean) = context.dataStore.edit { it[Keys.onlineSubtitles] = value }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
