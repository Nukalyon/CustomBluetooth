package com.example.custombluetooth.model

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    object Connecting : ScanState()
    object Disconnecting : ScanState()
    data class Error(val message: String) : ScanState()
}

// Connection states for device connection lifecycle
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
}

// Bluetooth error types
sealed class BluetoothError {
    object PermissionDenied : BluetoothError()
    object BluetoothDisabled : BluetoothError()
    data class Unknown(val message: String) : BluetoothError()
}
