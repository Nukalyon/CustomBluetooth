package com.example.plugin.model

sealed class AppState {
    object Idle : AppState()
    object Scanning : AppState()
    object Connecting : AppState()
    object Connected : AppState()
    object Disconnecting : AppState()
    object Disconnected : AppState()
    data class Error(val message: String) : AppState()
}

// Bluetooth error types
sealed class BluetoothError {
    object PermissionDenied : BluetoothError()
    object BluetoothDisabled : BluetoothError()
    data class Unknown(val message: String) : BluetoothError()
}
