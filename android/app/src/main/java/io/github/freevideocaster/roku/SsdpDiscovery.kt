package io.github.freevideocaster.roku

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import io.github.freevideocaster.net.WifiTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Busca Rokus en la red local con SSDP (M-SEARCH "roku:ecp"). Devuelve sus IPs. */
class SsdpDiscovery(context: Context) {

    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    suspend fun discover(timeoutMs: Long = 3000): List<String> = withContext(Dispatchers.IO) {
        // Android descarta el tráfico multicast salvo que la app tenga este "candado" activo.
        val lock = wifi.createMulticastLock("freevideocaster-ssdp").apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            DatagramSocket().use { socket ->
                // Con una VPN activa el multicast no saldría por la red local sin esto.
                runCatching { WifiTracker.network?.bindSocket(socket) }
                socket.soTimeout = 500
                val message = (
                    "M-SEARCH * HTTP/1.1\r\n" +
                        "Host: 239.255.255.250:1900\r\n" +
                        "Man: \"ssdp:discover\"\r\n" +
                        "ST: roku:ecp\r\n" +
                        "MX: 2\r\n\r\n"
                    ).toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) { socket.send(DatagramPacket(message, message.size, group, 1900)) }

                val found = linkedSetOf<String>()
                val buffer = ByteArray(2048)
                val deadline = SystemClock.elapsedRealtime() + timeoutMs
                while (SystemClock.elapsedRealtime() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val response = String(packet.data, 0, packet.length)
                    LOCATION.find(response)?.groupValues?.get(1)?.let { found += it }
                }
                found.toList()
            }
        } finally {
            lock.release()
        }
    }

    private companion object {
        val LOCATION = Regex("""(?im)^LOCATION:\s*http://([\d.]+):8060""")
    }
}
