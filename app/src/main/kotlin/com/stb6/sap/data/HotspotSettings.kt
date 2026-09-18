package com.stb6.sap.data

import kotlinx.coroutines.flow.StateFlow

interface HotspotSettings {
    val current: StateFlow<Settings?>
    suspend fun awaitLoaded(): Settings?
}
