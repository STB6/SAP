package com.stb6.sap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.stb6.sap.data.NetworkRules
import com.stb6.sap.ui.SapNavigation
import com.stb6.sap.ui.UiText
import com.stb6.sap.ui.theme.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as SapApplication
    private var permissions = PermissionFlow()
    private var permissionJob: Job? = null

    private val nearby = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissions.nearbyResult(granted)
        continuePermissions()
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions.notificationResult()
        continuePermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions = PermissionFlow(
            step = savedInstanceState?.getString(KEY_PERMISSION_STEP)
                ?.let { saved -> PermissionStep.entries.firstOrNull { it.name == saved } } ?: PermissionStep.Initial,
            startAfterPermissions = savedInstanceState?.getBoolean(KEY_START_AFTER) ?: false,
        )
        enableEdgeToEdge()
        setContent { AppTheme { SapNavigation(app, ::start, ::openAppInfo) } }
    }

    // The permission dialog may outlive this activity (rotation); the pending start must survive it.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_START_AFTER, permissions.startAfterPermissions)
        outState.putString(KEY_PERMISSION_STEP, permissions.step.name)
    }

    override fun onResume() {
        super.onResume()
        app.hotspot.refresh()
        continuePermissions()
    }

    override fun onPause() {
        permissions.leftForeground()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) continuePermissions() else permissions.leftForeground()
    }

    private fun continuePermissions() {
        permissionJob?.cancel()
        permissionJob = lifecycleScope.launch {
            app.settings.current.filterNotNull().first()
            lifecycle.withResumed {
                if (window.decorView.hasWindowFocus()) permissions.recoverWaiting(app.hotspot.hasPermission())
                advancePermissions()
            }
        }
    }

    private fun advancePermissions() {
        while (true) {
            val action = permissions.next(app.hotspot.hasPermission(), app.settings.nearbyAsked,
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                app.settings.notificationAsked)
            when (action) {
                PermissionAction.None -> return
                PermissionAction.RequestNearby -> { nearby.launch(Manifest.permission.NEARBY_WIFI_DEVICES); return }
                PermissionAction.RequestNotifications -> { notifications.launch(Manifest.permission.POST_NOTIFICATIONS); return }
                PermissionAction.RecordNearby -> app.settings.markNearbyAsked()
                PermissionAction.RecordNotifications -> app.settings.markNotificationAsked()
                PermissionAction.NearbyRequired -> {
                    app.settings.markNearbyAsked()
                    app.notify(UiText(R.string.nearby_required))
                }
                PermissionAction.Start -> HotspotService.command(this, HotspotService.START)
            }
        }
    }

    private fun start() {
        val config = app.settings.current.value ?: return
        val nameValid = NetworkRules.validSsid(config.ssid)
        val passwordValid = NetworkRules.validPassword(config.password)
        if (!nameValid || !passwordValid) {
            app.notify(UiText(when {
                !nameValid && !passwordValid -> R.string.invalid_both
                !nameValid -> R.string.invalid_ssid
                else -> R.string.invalid_password
            }))
            return
        }
        if (!app.hotspot.supported) { app.notify(UiText(R.string.unsupported)); return }
        permissions.requestStart(app.hotspot.hasPermission())
        continuePermissions()
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }
}

private const val KEY_START_AFTER = "start_after_permissions"

private const val KEY_PERMISSION_STEP = "permission_step"
