package com.stb6.sap

import android.app.Application
import android.widget.Toast
import com.stb6.sap.data.SettingsRepository
import com.stb6.sap.network.Feedback
import com.stb6.sap.network.HotspotController
import com.stb6.sap.network.AndroidP2pConnection
import com.stb6.sap.ui.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ErrorReport(val text: UiText, val detail: String?)

class SapApplication : Application(), Feedback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var toast: Toast? = null
    private val errors = MutableStateFlow<ErrorReport?>(null)
    val error = errors.asStateFlow()
    lateinit var settings: SettingsRepository
        private set
    lateinit var hotspot: HotspotController
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this, scope) { detail -> error(UiText(R.string.settings_failed), detail) }
        hotspot = HotspotController(AndroidP2pConnection(this), scope, settings, this)
    }

    override fun notify(text: UiText) {
        scope.launch {
            toast?.cancel()
            toast = Toast.makeText(this@SapApplication, text.resolve(this@SapApplication), Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    override fun error(text: UiText, detail: String?) { errors.value = ErrorReport(text, detail) }
    fun dismissError() { errors.value = null }
}
