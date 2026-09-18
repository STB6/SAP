package com.stb6.sap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import com.stb6.sap.network.Phase
import com.stb6.sap.ui.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HotspotService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as SapApplication
    private var observing = false
    private var pendingCommands = 0
    private var latestStartId = 0
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        trace("received ${intent?.action?.substringAfterLast('.')}")
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW))
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        ++pendingCommands
        scope.launch {
            try {
                when (intent?.action) {
                    START -> app.hotspot.start()
                    STOP -> app.hotspot.stop()
                    ADOPT -> Unit
                }
            } finally {
                val expected = when (intent?.action) {
                    START -> Phase.On
                    STOP -> Phase.Off
                    else -> null
                }
                intent?.getParcelableExtra(RESULT, ResultReceiver::class.java)?.send(
                    if (expected == app.hotspot.state.value.phase) 0 else 1, null)
                --pendingCommands
                if (pendingCommands == 0 && app.hotspot.state.value.phase == Phase.Off) finishService()
            }
        }
        if (!observing) {
            observing = true
            scope.launch {
                app.hotspot.state.collect {
                    if (it.phase == Phase.Off && pendingCommands == 0) finishService()
                    else manager.notify(1, notification())
                }
            }
        }
        return START_NOT_STICKY
    }
    private fun notification(): android.app.Notification {
        val state = app.hotspot.state.value
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, HotspotService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val count = state.clients.size
        val text = when {
            state.phase != Phase.On -> getString(R.string.state_working)
            count == 0 -> getString(R.string.notification_no_devices, state.ssid)
            else -> resources.getQuantityString(R.plurals.notification_devices, count, state.ssid, count)
        }
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name)).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.action_stop), stop).build()
    }
    private fun finishService() {
        val stopped = stopSelfResult(latestStartId)
        trace("finish stopped=$stopped")
        if (stopped) stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onDestroy() { trace("destroy"); scope.cancel(); super.onDestroy() }
    private fun trace(event: String) {
        if (BuildConfig.DEBUG) Log.d("SapHotspotService", "$event startId=$latestStartId pending=$pendingCommands")
    }
    companion object {
        const val START = "com.stb6.sap.START"
        const val STOP = "com.stb6.sap.STOP"
        const val ADOPT = "com.stb6.sap.ADOPT"
        private const val CHANNEL = "hotspot"
        private const val RESULT = "result"
        fun command(context: Context, action: String, result: ResultReceiver? = null) {
            try { context.startForegroundService(Intent(context, HotspotService::class.java).setAction(action).putExtra(RESULT, result)) }
            catch (e: RuntimeException) {
                (context.applicationContext as SapApplication).error(UiText(R.string.service_failed), e.toString())
                result?.send(1, null)
            }
        }
    }
}
