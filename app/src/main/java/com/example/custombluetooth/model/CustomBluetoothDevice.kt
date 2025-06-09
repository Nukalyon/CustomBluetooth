package com.example.custombluetooth.model

data class CustomBluetoothDevice(
    val name: String?,
    val address: String,   // Unique MAC address
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val rssi: Int? = null // Signal strength (optional)
) {
    enum class DeviceType {
        AUDIO, COMPUTER, PHONE, UNKNOWN
    }
}
