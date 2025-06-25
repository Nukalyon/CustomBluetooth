package com.example.plugin

import android.bluetooth.BluetoothDevice

interface IBluetoothPlugin {
    fun startScan()
    fun stopScan()
    fun connectToDevice(device: BluetoothDevice)
    fun disconnect()
    fun sendMessage(message: String?)
}
