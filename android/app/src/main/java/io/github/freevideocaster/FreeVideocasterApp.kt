package io.github.freevideocaster

import android.app.Application
import android.content.Context
import io.github.freevideocaster.adblock.AdBlocker
import io.github.freevideocaster.data.HistoryStore
import io.github.freevideocaster.data.Settings
import io.github.freevideocaster.media.LinkResolver
import io.github.freevideocaster.media.OnlineSubtitles
import io.github.freevideocaster.net.WifiSocketFactory
import io.github.freevideocaster.net.WifiTracker
import io.github.freevideocaster.roku.ChannelClient
import io.github.freevideocaster.roku.EcpClient
import io.github.freevideocaster.roku.SsdpDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class FreeVideocasterApp : Application() {

    lateinit var caster: Caster
        private set

    lateinit var adBlocker: AdBlocker
        private set

    /** Cliente normal (sin atar al Wi-Fi): para descargas de internet como las listas del bloqueador. */
    lateinit var internetHttp: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()
        WifiTracker.start(this)
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        // El Roku está en la red local: sus peticiones salen por el Wi-Fi aunque haya una VPN activa.
        val localHttp = http.newBuilder().socketFactory(WifiSocketFactory()).build()
        caster = Caster(
            context = this,
            ecp = EcpClient(localHttp),
            channel = ChannelClient(localHttp),
            resolver = LinkResolver(http),
            subtitles = OnlineSubtitles(http),
            discovery = SsdpDiscovery(this),
            settings = Settings(this),
            history = HistoryStore(this, appScope),
            scope = appScope,
        )
        internetHttp = http
        adBlocker = AdBlocker(this, appScope).also { blocker ->
            blocker.enabled = caster.prefs.value.adBlock
            appScope.launch { caster.prefs.collect { blocker.enabled = it.adBlock } }
        }
    }
}

val Context.caster: Caster
    get() = (applicationContext as FreeVideocasterApp).caster

val Context.adBlocker: AdBlocker
    get() = (applicationContext as FreeVideocasterApp).adBlocker

val Context.internetHttp: OkHttpClient
    get() = (applicationContext as FreeVideocasterApp).internetHttp
