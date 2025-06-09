package com.example.custombluetooth.controller

import android.bluetooth.BluetoothDevice
import com.example.custombluetooth.model.BluetoothError
import com.example.custombluetooth.model.ConnectionState
import com.example.custombluetooth.model.ScanState
import kotlinx.coroutines.flow.StateFlow

interface IBluetoothController {
    // Liste des appareils que l'on va détecter
    val scannedDevices : StateFlow<List<BluetoothDevice>>
    // Liste des appareils avec qui on a déjà établi une connexion
    val pairedDevices : StateFlow<List<BluetoothDevice>>

    val scanState: StateFlow<ScanState>
    val connectionState: StateFlow<ConnectionState>
    val errorState: StateFlow<BluetoothError?>
    val debugMessages: StateFlow<List<String>>

    fun startDiscovery()
    fun stopDiscovery()
    fun startServer()
    fun connectToDevice(device: BluetoothDevice)
    fun disconnectFromDevice()
    fun sendMessage(message: String)
}