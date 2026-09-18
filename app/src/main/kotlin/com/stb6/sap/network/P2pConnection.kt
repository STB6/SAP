package com.stb6.sap.network

import com.stb6.sap.data.Settings

interface P2pConnection {
    val supported: Boolean
    val revision: Long
    fun hasPermission(): Boolean
    fun wifiEnabled(): Boolean
    fun listen(changed: () -> Unit, disabled: () -> Unit, disconnected: () -> Unit)
    suspend fun group(): P2pGroup?
    suspend fun address(): String?
    suspend fun creationInactive(): Boolean
    suspend fun create(settings: Settings, result: (Boolean) -> Unit)
    suspend fun remove()
    fun close()
    fun holdGroup()
    fun now(): Long
    fun trace(event: String)
}

data class P2pGroup(val isGroupOwner: Boolean, val state: HotspotState)
class P2pFailure(val reason: Int) : Exception("P2P operation failed: reason=$reason")
