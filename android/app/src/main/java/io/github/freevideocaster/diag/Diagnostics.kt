package io.github.freevideocaster.diag

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.freevideocaster.data.Settings
import io.github.freevideocaster.media.LinkResolver
import io.github.freevideocaster.media.MediaRequest
import io.github.freevideocaster.net.LocalNetwork
import io.github.freevideocaster.roku.CastException
import io.github.freevideocaster.roku.ChannelClient
import io.github.freevideocaster.roku.EcpClient
import io.github.freevideocaster.roku.RokuDevice
import io.github.freevideocaster.roku.SsdpDiscovery
import kotlinx.coroutines.flow.first

enum class Level(val symbol: String) { Ok("✔"), Warn("⚠"), Fail("✘"), Info("ℹ") }

/** Una comprobación: qué se probó, cómo salió y, si falló, qué hacer. */
data class Check(
    val title: String,
    val level: Level,
    val detail: String,
    val hint: String? = null,
)

/** Revisa de punta a punta todo lo que puede impedir que un video llegue al Roku. */
class Diagnostics(
    private val context: Context,
    private val ecp: EcpClient,
    private val channel: ChannelClient,
    private val resolver: LinkResolver,
    private val discovery: SsdpDiscovery,
    private val settings: Settings,
) {

    suspend fun run(link: String? = null): List<Check> {
        val checks = mutableListOf<Check>()

        val onLan = LocalNetwork.isOnLocalNetwork(context)
        checks += if (onLan) {
            Check("Wi-Fi", Level.Ok, "Conectado a una red local.")
        } else {
            Check(
                "Wi-Fi", Level.Fail, "El teléfono no está en una red Wi-Fi.",
                "Conéctalo a la misma red que el Roku. Los datos móviles y las redes de invitados no sirven.",
            )
        }

        if (LocalNetwork.isVpnActive(context)) {
            checks += Check(
                "VPN", Level.Info,
                "Hay una VPN activa. La app habla con el Roku por el Wi-Fi directamente, así que no debería afectarle.",
                "Si el Roku no responde, prueba desactivar la VPN un momento.",
            )
        }

        val hasPermission = LocalNetwork.hasPermission(context)
        if (LocalNetwork.needsPermission()) {
            checks += if (hasPermission) {
                Check("Permiso de red local", Level.Ok, "Concedido.")
            } else {
                Check(
                    "Permiso de red local", Level.Fail, "Android bloquea el acceso a tu red local.",
                    "En la pantalla «Enviar» pulsa «Conceder», o actívalo en Ajustes › Apps › FreeVideocaster › Permisos.",
                )
            }
        }

        checks += notificationCheck()

        val saved = settings.roku.first()
        if (saved == null) {
            checks += Check(
                "Roku elegido", Level.Fail, "Aún no elegiste un Roku.",
                "Ve a «Enviar» y pulsa «Buscar Roku», o escribe su IP.",
            )
        } else {
            checks += Check("Roku elegido", Level.Ok, "${saved.name} (${saved.ip})")
            if (onLan && hasPermission) checks += rokuChecks(saved)
            else checks += Check("Conexión con el Roku", Level.Info, "No se probó: falta Wi-Fi o el permiso de red local.")
        }

        if (!link.isNullOrBlank()) checks += linkCheck(link.trim(), onLan && hasPermission)
        return checks
    }

    /** Informe en texto plano para copiar. Los enlaces se reducen a su dominio para no filtrar tokens. */
    fun report(checks: List<Check>): String = buildString {
        appendLine("FreeVideocaster · diagnóstico")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MODEL}")
        appendLine()
        checks.forEach { check ->
            appendLine("${check.level.symbol} ${check.title}: ${check.detail}")
            check.hint?.let { appendLine("   → $it") }
        }
    }

    private fun notificationCheck(): Check {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return Check("Notificaciones", Level.Ok, "No requieren permiso en esta versión de Android.")
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return if (granted) {
            Check("Notificaciones", Level.Ok, "Permitidas: tendrás controles en la barra y la pantalla de bloqueo.")
        } else {
            Check(
                "Notificaciones", Level.Warn, "Desactivadas: no habrá controles fuera de la app.",
                "Actívalas con el aviso de la pantalla «Enviar» o en Ajustes › Apps › FreeVideocaster › Notificaciones.",
            )
        }
    }

    private suspend fun rokuChecks(saved: RokuDevice): List<Check> {
        val checks = mutableListOf<Check>()

        val started = SystemClock.elapsedRealtime()
        var device: RokuDevice? = try {
            ecp.deviceInfo(saved.ip)
        } catch (e: CastException) {
            null
        }
        val elapsed = SystemClock.elapsedRealtime() - started

        if (device == null && saved.serial.isNotEmpty()) {
            val moved = runCatching {
                discovery.discover().mapNotNull { ip -> runCatching { ecp.deviceInfo(ip) }.getOrNull() }
            }.getOrDefault(emptyList()).firstOrNull { it.serial == saved.serial }
            if (moved != null) {
                settings.saveRoku(moved)
                checks += Check(
                    "Dirección del Roku", Level.Warn,
                    "El Roku cambió de IP: antes ${saved.ip}, ahora ${moved.ip}. Ya lo actualicé.",
                    "Para que no cambie, reserva su IP en el router (reserva DHCP).",
                )
                device = moved
            }
        }

        if (device == null) {
            checks += Check(
                "Conexión con el Roku", Level.Fail, "${saved.name} no responde en ${saved.ip}.",
                "Comprueba que esté encendido (no en reposo profundo), en la misma red Wi-Fi que el teléfono " +
                    "y que Configuración › Sistema › Configuración avanzada › Control por aplicaciones móviles " +
                    "no esté en «Desactivado».",
            )
            return checks
        }

        checks += if (elapsed > SLOW_MS) {
            Check("Conexión con el Roku", Level.Warn, "Responde, pero lento (${elapsed} ms).", "Revisa la señal Wi-Fi del Roku y del teléfono.")
        } else {
            Check("Conexión con el Roku", Level.Ok, "${device.name} responde en $elapsed ms.")
        }

        checks += when (device.ecpMode.lowercase()) {
            "permissive" -> Check("Control por aplicaciones móviles", Level.Ok, "Permitido (modo permisivo).")
            "enabled" -> Check("Control por aplicaciones móviles", Level.Ok, "Permitido.")
            "default" -> Check(
                "Control por aplicaciones móviles", Level.Ok, "Modo predeterminado.",
                "Si algún botón no responde, cámbialo a «Permisivo».",
            )
            "limited" -> Check(
                "Control por aplicaciones móviles", Level.Warn, "Limitado: algunos comandos pueden bloquearse.",
                "Configuración › Sistema › Configuración avanzada del sistema › Control por aplicaciones móviles › Permisivo.",
            )
            "disabled" -> Check(
                "Control por aplicaciones móviles", Level.Fail, "Desactivado: el Roku ignora a la app.",
                "Configuración › Sistema › Configuración avanzada del sistema › Control por aplicaciones móviles › Permisivo.",
            )
            else -> Check("Control por aplicaciones móviles", Level.Info, "El Roku no informó su modo (${device.ecpMode.ifBlank { "sin dato" }}).")
        }

        val screen = listOf(device.model, device.softwareVersion.takeIf { it.isNotBlank() }?.let { "Roku OS $it" }, device.uiResolution)
            .filter { !it.isNullOrBlank() }.joinToString(" · ")
        checks += Check(
            "Equipo", Level.Info, screen.ifBlank { "Sin datos del equipo." },
            if (device.uiResolution.equals("720p", ignoreCase = true)) {
                "La pantalla es 720p: los videos 4K se reducen o pueden fallar; elige versiones 1080p."
            } else {
                null
            },
        )

        checks += channelChecks(device)
        return checks
    }

    private suspend fun channelChecks(device: RokuDevice): List<Check> {
        val apps = runCatching { ecp.apps(device.ip) }.getOrNull()
            ?: return listOf(Check("Canal FreeVideocaster", Level.Info, "No se pudo leer la lista de canales."))
        val dev = apps.firstOrNull { it.first == EcpClient.DEV_CHANNEL }
        val checks = mutableListOf<Check>()

        if (dev == null) {
            checks += if (device.developerEnabled) {
                Check(
                    "Canal FreeVideocaster", Level.Fail, "El canal no está instalado en el Roku.",
                    "Instálalo desde el PC con scripts\\deploy-roku.ps1 (ver el README).",
                )
            } else {
                Check(
                    "Canal FreeVideocaster", Level.Fail, "El modo desarrollador del Roku está apagado y no hay canal.",
                    "Actívalo con el control: Inicio ×3, Arriba ×2, Derecha, Izquierda, Derecha, Izquierda, Derecha; luego instala el canal (README).",
                )
            }
            return checks
        }

        if (!dev.second.contains("FreeVideocaster", ignoreCase = true)) {
            checks += Check(
                "Canal FreeVideocaster", Level.Warn, "Hay otro canal de desarrollador instalado («${dev.second}»).",
                "El Roku admite uno solo: vuelve a instalar FreeVideocaster con deploy-roku.ps1.",
            )
        } else {
            checks += Check("Canal FreeVideocaster", Level.Ok, "Instalado en el Roku.")
        }

        val active = runCatching { ecp.activeAppId(device.ip) }.getOrNull()
        if (active == EcpClient.DEV_CHANNEL) {
            val started = SystemClock.elapsedRealtime()
            val status = channel.status(device.ip)
            val ms = SystemClock.elapsedRealtime() - started
            checks += if (status != null) {
                Check("Estado en vivo", Level.Ok, "El canal informa su estado (puerto 8061, $ms ms).")
            } else {
                Check(
                    "Estado en vivo", Level.Warn, "El canal está abierto pero no responde en el puerto 8061.",
                    "Probablemente es una versión antigua del canal: reinstálalo con deploy-roku.ps1.",
                )
            }
        } else {
            checks += Check(
                "Estado en vivo", Level.Info,
                "Solo existe mientras el canal está abierto; se abre solo al enviar un video.",
            )
        }
        return checks
    }

    private suspend fun linkCheck(link: String, canReachNetwork: Boolean): Check {
        val host = runCatching { java.net.URI(link).host }.getOrNull() ?: return Check(
            "Enlace", Level.Fail, "No parece una URL válida.", "Debe empezar por http:// o https://.",
        )
        if (!link.startsWith("http", ignoreCase = true)) {
            return Check("Enlace", Level.Fail, "No parece una URL válida.", "Debe empezar por http:// o https://.")
        }
        // El enlace se abre desde el teléfono, que también necesita red (no tiene que ser Wi-Fi).
        val resolved = resolver.resolve(MediaRequest(url = link))
        val finalHost = runCatching { java.net.URI(resolved.finalUrl).host }.getOrNull() ?: host
        val route = if (finalHost == host) host else "$host → $finalHost"
        return when (resolved.httpCode) {
            null -> Check(
                "Enlace", Level.Warn, "No se pudo comprobar ($route).",
                if (canReachNetwork) "Revisa la conexión a Internet del teléfono." else null,
            )
            in 200..299 -> if (resolved.isWebPage) Check(
                "Enlace", Level.Fail, "Es una página web, no un video ($route).",
                "Ábrela en la pestaña «Navegador» para que la app detecte el video, o usa el enlace directo del archivo.",
            ) else Check(
                "Enlace", if (resolved.format == null) Level.Warn else Level.Ok,
                "Vivo ($route, HTTP ${resolved.httpCode}, formato ${resolved.format ?: "desconocido"}).",
                if (resolved.format == null) "No se reconoció el formato: el Roku intentará detectarlo solo." else null,
            )
            401, 403, 404, 410 -> Check(
                "Enlace", Level.Fail, "El servidor respondió HTTP ${resolved.httpCode} ($route): el enlace caducó o no es válido.",
                "Vuelve a abrir el video desde Stremio para obtener uno nuevo.",
            )
            else -> Check("Enlace", Level.Warn, "El servidor respondió HTTP ${resolved.httpCode} ($route).")
        }
    }

    private companion object {
        const val SLOW_MS = 400L
    }
}
