package com.example.plugin.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomBluetoothDevice(
    val name: String?,
    val address: String,   // Unique MAC address
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val rssi: Int? = null // Signal strength (optional)
) {
    @Serializable
    enum class DeviceType {
        AUDIO, COMPUTER, PHONE, UNKNOWN
    }
}