package com.stb6.sap.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import com.stb6.sap.R
import com.stb6.sap.SapApplication
import com.stb6.sap.ui.theme.*

@Composable
fun SapNavigation(app: SapApplication, onStart: () -> Unit, onPermissions: () -> Unit) {
    val navigation = rememberNavController()
    val error by app.error.collectAsStateWithLifecycle()
    error?.let { report ->
        ErrorDialog(title = report.text.text(), message = null, detail = report.detail,
            confirmLabel = stringResource(R.string.close), onDismiss = app::dismissError)
    }
    Box(Modifier.fillMaxSize()) {
        NavHost(navigation, startDestination = "home",
            enterTransition = { pageEnter() }, exitTransition = { pageExit() },
            popEnterTransition = { pagePopEnter() }, popExitTransition = { pagePopExit() }) {
            composable("home") {
                HotspotScreen(app, onStart, onPermissions,
                    onClients = { navigation.navigate("clients") { launchSingleTop = true } })
            }
            composable("clients") {
                val state by app.hotspot.state.collectAsStateWithLifecycle()
                ConnectedDevicesScreen(state.clients, onBack = { navigation.popBackStack() })
            }
        }
    }
}
