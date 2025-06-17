package com.example.custombluetooth.view

import android.bluetooth.BluetoothDevice
import com.example.custombluetooth.model.BluetoothError
import com.example.custombluetooth.model.AppState

// Define a simple UI state data class to hold the combined state
data class UIState(
    val scannedDevices: List<BluetoothDevice> = emptyList(),
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val appState: AppState = AppState.Idle,
    val errorDesc: BluetoothError? = null,
    val debugMessages : List<String> = emptyList()
)