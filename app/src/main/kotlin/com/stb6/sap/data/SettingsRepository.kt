package com.stb6.sap.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.preferences by preferencesDataStore("settings")

class SettingsRepository(context: Context, private val scope: CoroutineScope, private val failure: (String?) -> Unit) : HotspotSettings {
    private val store = context.preferences
    private val mutable = MutableStateFlow<Settings?>(null)
    override val current = mutable.asStateFlow()
    private val failed = MutableStateFlow(false)
    val loadFailed = failed.asStateFlow()
    private var loading: Job? = null
    var notificationAsked = false
        private set
    var nearbyAsked = false
        private set
    fun markNotificationAsked() { notificationAsked = true; markAsked(NOTIFICATION_ASKED) }
    fun markNearbyAsked() { nearbyAsked = true; markAsked(NEARBY_ASKED) }
    private fun markAsked(key: Preferences.Key<Int>) {
        scope.launch {
            try { store.edit { it[key] = 1 } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e.toString()) }
        }
    }
    private val writes = Channel<Settings>(Channel.CONFLATED)
    init {
        load()
        scope.launch {
            for (value in writes) {
                try {
                    store.edit { p ->
                        p[SUFFIX] = value.suffix; p[PASSWORD] = value.password
                        p[BAND] = value.band; p[TIMEOUT] = value.timeoutMinutes
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { failure(e.toString()) }
            }
        }
    }
    fun load() {
        if (loading?.isActive == true || mutable.value != null) return
        failed.value = false
        loading = scope.launch {
            try {
                val stored = store.data.first()
                val prefs = if (stored[SUFFIX] != null && stored[PASSWORD] != null) stored else store.edit { p ->
                    if (p[SUFFIX] == null) p[SUFFIX] = "sap"
                    if (p[PASSWORD] == null) p[PASSWORD] = NetworkRules.password()
                }
                notificationAsked = prefs[NOTIFICATION_ASKED] == 1
                nearbyAsked = prefs[NEARBY_ASKED] == 1
                mutable.value = Settings(prefs[SUFFIX]!!, prefs[PASSWORD]!!,
                    prefs[BAND]?.takeIf { it == 2 || it == 5 } ?: 2,
                    (prefs[TIMEOUT] ?: 0).coerceIn(0, 9999))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed.value = true; failure(e.toString()) }
        }
    }
    fun update(transform: (Settings) -> Settings) {
        val old = mutable.value ?: return
        val value = transform(old)
        if (value == old) return
        mutable.value = value
        writes.trySend(value)
    }
    override suspend fun awaitLoaded(): Settings? {
        loading?.join()
        return mutable.value
    }
    private companion object {
        val NOTIFICATION_ASKED = intPreferencesKey("notification_asked")
        val NEARBY_ASKED = intPreferencesKey("nearby_asked")
        val SUFFIX = stringPreferencesKey("ssid_suffix")
        val PASSWORD = stringPreferencesKey("passphrase")
        val BAND = intPreferencesKey("band")
        val TIMEOUT = intPreferencesKey("auto_off_minutes")
    }
}
