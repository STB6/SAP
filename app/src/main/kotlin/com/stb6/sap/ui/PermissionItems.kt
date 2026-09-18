package com.stb6.sap.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stb6.sap.R
import com.stb6.sap.ui.settings.ValueRow
import com.stb6.sap.ui.theme.Dimens
import com.stb6.sap.ui.theme.StatusColors

@Composable
fun PermissionItems(onClick: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun nearby() = ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    fun notifications() = NotificationManagerCompat.from(context).areNotificationsEnabled()
    var nearbyGranted by remember { mutableStateOf(nearby()) }
    var notificationGranted by remember { mutableStateOf(notifications()) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            nearbyGranted = nearby(); notificationGranted = notifications()
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    listOf(R.string.permission_nearby to nearbyGranted, R.string.permission_notifications to notificationGranted).forEach { (label, granted) ->
        ValueRow(stringResource(label), stringResource(if (granted) R.string.allowed else R.string.not_allowed),
            modifier = Modifier.tapTarget(bleed = Dimens.TapBleed, onClick = onClick),
            valueColor = if (granted) StatusColors.ok else StatusColors.bad)
    }
}
