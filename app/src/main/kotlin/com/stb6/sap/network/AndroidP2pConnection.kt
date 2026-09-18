package com.stb6.sap.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.stb6.sap.BuildConfig
import com.stb6.sap.HotspotService
import com.stb6.sap.data.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidP2pConnection(private val context: Context) : P2pConnection {
    private val manager = context.getSystemService(WifiP2pManager::class.java)
    override val supported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) && manager != null
    override var revision = 0L
        private set
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var changed: () -> Unit = {}
    private var disabled: () -> Unit = {}
    private var disconnected: () -> Unit = {}

    override fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    override fun wifiEnabled() = context.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
    override fun now() = SystemClock.elapsedRealtime()
    override fun holdGroup() = HotspotService.command(context, HotspotService.ADOPT)
    override fun trace(event: String) { if (BuildConfig.DEBUG) Log.d("SapHotspot", event) }
    override fun listen(changed: () -> Unit, disabled: () -> Unit, disconnected: () -> Unit) {
        this.changed = changed
        this.disabled = disabled
        this.disconnected = disconnected
    }

    private fun ensureChannel(): WifiP2pManager.Channel {
        check(supported) { "Wi-Fi Direct unavailable" }
        check(hasPermission()) { "Nearby devices permission required" }
        channel?.let { return it }
        val id = ++revision
        val created = checkNotNull(manager.initialize(context, Looper.getMainLooper()) {
            if (id != revision) return@initialize
            ++revision
            channel = null
            disconnected()
        }) { "P2P initialization failed" }
        channel = created
        if (receiver == null) {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION &&
                        intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_DISABLED) disabled()
                    else changed()
                }
            }.also {
                ContextCompat.registerReceiver(context, it, IntentFilter().apply {
                    addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                    addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                }, ContextCompat.RECEIVER_EXPORTED)
            }
        }
        return created
    }

    private suspend fun <T> read(call: (WifiP2pManager.Channel, (T) -> Unit) -> Unit): T = withTimeout(ACTION_TIMEOUT_MS) {
        val current = ensureChannel()
        suspendCancellableCoroutine { continuation ->
            call(current) { value ->
                if (continuation.isActive) {
                    when {
                        channel !== current -> continuation.resumeWithException(IllegalStateException("P2P channel changed"))
                        !hasPermission() -> continuation.resumeWithException(SecurityException("Nearby permission revoked"))
                        else -> continuation.resume(value)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun group(): P2pGroup? = read { current, result ->
        manager.requestGroupInfo(current) { group ->
            result(group?.let {
                P2pGroup(it.isGroupOwner, HotspotState(phase = Phase.On, ssid = it.networkName,
                    password = it.passphrase.orEmpty(), frequency = it.frequency, interfaceName = it.getInterface(),
                    clients = it.clientList.map { client -> Client(client.deviceAddress.orEmpty(), client.ipAddress?.hostAddress) }))
            })
        }
    }

    override suspend fun address(): String? = read { current, result ->
        manager.requestConnectionInfo(current) { info ->
            result(info.groupOwnerAddress?.takeUnless { it.isAnyLocalAddress || it.isLoopbackAddress }?.hostAddress)
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun creationInactive(): Boolean = read { current, result ->
        manager.requestNetworkInfo(current) { info ->
            result(info.detailedState in setOf(NetworkInfo.DetailedState.IDLE, NetworkInfo.DetailedState.DISCONNECTED, NetworkInfo.DetailedState.FAILED))
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun create(settings: Settings, result: (Boolean) -> Unit) {
        val request = WifiP2pConfig.Builder().setNetworkName(settings.ssid).setPassphrase(settings.password)
            .setGroupOperatingBand(if (settings.band == 5) WifiP2pConfig.GROUP_OWNER_BAND_5GHZ else WifiP2pConfig.GROUP_OWNER_BAND_2GHZ).build()
        action(result) { manager.createGroup(ensureChannel(), request, it) }
    }

    override suspend fun remove() = action { manager.removeGroup(ensureChannel(), it) }

    private suspend fun action(result: ((Boolean) -> Unit)? = null, call: (WifiP2pManager.ActionListener) -> Unit) = withTimeout(ACTION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            call(object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    result?.invoke(true)
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onFailure(reason: Int) {
                    result?.invoke(false)
                    if (continuation.isActive) continuation.resumeWithException(P2pFailure(reason))
                }
            })
        }
    }

    override fun close() {
        ++revision
        channel?.close()
        channel = null
        receiver?.let(context::unregisterReceiver)
        receiver = null
    }

    private companion object { const val ACTION_TIMEOUT_MS = 4_000L }
}
