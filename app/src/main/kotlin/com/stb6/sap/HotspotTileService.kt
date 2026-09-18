package com.stb6.sap

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.stb6.sap.data.NetworkRules
import com.stb6.sap.network.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HotspotTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as SapApplication
    private var listening: Job? = null
    private var requestedAction: String? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            launch {
                app.settings.awaitLoaded()
                app.hotspot.refresh()
            }
            app.hotspot.state.collect { state ->
                qsTile?.apply {
                    label = getString(R.string.app_name)
                    icon = Icon.createWithResource(this@HotspotTileService, R.drawable.ic_tile)
                    subtitle = getString(if (!app.hotspot.supported) R.string.state_unsupported else when (state.phase) {
                        Phase.Off -> R.string.hotspot_off
                        Phase.On -> R.string.hotspot_on
                        Phase.Starting -> R.string.state_starting
                        Phase.Stopping -> R.string.state_stopping
                        Phase.Checking, Phase.Unknown -> R.string.state_unknown
                    })
                    this.state = when {
                        !app.hotspot.supported || state.phase == Phase.Starting || state.phase == Phase.Stopping -> Tile.STATE_UNAVAILABLE
                        state.phase == Phase.On -> Tile.STATE_ACTIVE
                        else -> Tile.STATE_INACTIVE
                    }
                    contentDescription = "$label, $subtitle"
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        if (requestedAction != null) return
        val state = app.hotspot.state.value
        if (state.phase == Phase.Starting || state.phase == Phase.Stopping) return
        val notificationsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val config = app.settings.current.value
        if (!app.hotspot.supported || !app.hotspot.hasPermission() ||
            state.phase == Phase.Checking || state.phase == Phase.Unknown ||
            (state.phase != Phase.On && (config == null || app.error.value != null ||
                needsNotificationRequest(notificationsGranted, app.settings.notificationAsked) ||
                !NetworkRules.validSsid(config.ssid) || !NetworkRules.validPassword(config.password)))) {
            openApp()
            return
        }
        val action = if (state.phase == Phase.On) HotspotService.STOP else HotspotService.START
        requestedAction = action
        HotspotService.command(this, action, object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                requestedAction = null
                if (resultCode != 0 && listening?.isActive == true) openApp()
            }
        })
    }

    private fun openApp() {
        requestedAction = null
        startActivityAndCollapse(PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
