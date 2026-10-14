package io.github.freevideocaster.net

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory

object LocalNetwork {

    /** Permiso de Android 17 (API 37) para hablar con dispositivos de la red local. */
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val ANDROID_17 = 37

    fun needsPermission(): Boolean = Build.VERSION.SDK_INT >= ANDROID_17

    fun hasPermission(context: Context): Boolean =
        !needsPermission() ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** true si el teléfono está en Wi-Fi o Ethernet (donde puede estar el Roku). */
    fun isOnLocalNetwork(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        // Con una VPN activa la red «activa» es la VPN; se mira también la red física de debajo.
        if (WifiTracker.network != null) return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** true si hay una VPN como red activa (el tráfico normal pasaría por ella). */
    fun isVpnActive(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}

/**
 * Sigue la red Wi-Fi/Ethernet real del teléfono. Con una VPN activa, el tráfico normal sale por la VPN y
 * nunca llega a un Roku de la red local; por eso las conexiones hacia el Roku se atan a esta red.
 */
object WifiTracker {
    private val current = AtomicReference<Network?>(null)

    val network: Network? get() = current.get()

    /** Se llama una vez al arrancar la app. */
    fun start(context: Context) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        // Por omisión un NetworkRequest excluye las VPN (NOT_VPN), que es justo lo que se quiere:
        // la red física de verdad, aunque haya una VPN encima.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        runCatching {
            manager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        current.set(network)
                    }

                    override fun onLost(network: Network) {
                        current.compareAndSet(network, null)
                    }
                },
            )
        }
    }
}
/** Fábrica de sockets que usa siempre el Wi-Fi si está disponible, y la red predeterminada si no. */
class WifiSocketFactory : SocketFactory() {
    private fun delegate(): SocketFactory = WifiTracker.network?.socketFactory ?: getDefault()

    override fun createSocket(): Socket = delegate().createSocket()

    override fun createSocket(host: String?, port: Int): Socket = delegate().createSocket(host, port)

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        delegate().createSocket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress?, port: Int): Socket = delegate().createSocket(host, port)

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        delegate().createSocket(address, port, localAddress, localPort)
}
