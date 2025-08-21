package com.example.plugin.controller

import android.bluetooth.BluetoothDevice
import com.example.plugin.model.AppState
import com.example.plugin.model.BluetoothError
import kotlinx.coroutines.flow.StateFlow

interface IBluetoothController {
    val scannedDevices : StateFlow<List<BluetoothDevice>>
    val pairedDevices : StateFlow<List<BluetoothDevice>>

    val appState: StateFlow<AppState>
    val errorState: StateFlow<BluetoothError?>
    val debugMessages: StateFlow<List<String>>

    fun startDiscovery()
    fun stopDiscovery()
    fun startServer()
    fun connectToDevice(device: BluetoothDevice)
    fun pairToDevice(device: BluetoothDevice)
    fun disconnectFromDevice()
    fun sendMessage(message: String)
}