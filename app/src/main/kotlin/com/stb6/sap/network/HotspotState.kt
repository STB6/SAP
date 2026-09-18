package com.stb6.sap.network

enum class Phase { Off, Checking, Starting, On, Stopping, Unknown }
data class Client(val address: String, val ip: String?)
data class HotspotState(
    val phase: Phase = Phase.Off,
    val ssid: String = "",
    val password: String = "",
    val frequency: Int = 0,
    val interfaceName: String = "",
    val hostAddress: String? = null,
    val clients: List<Client> = emptyList(),
) {
    val switchChecked: Boolean get() = phase == Phase.Starting || phase == Phase.On || phase == Phase.Unknown
    val canStart: Boolean get() = phase == Phase.Off || phase == Phase.Checking
    val locked: Boolean get() = phase != Phase.Off
}
