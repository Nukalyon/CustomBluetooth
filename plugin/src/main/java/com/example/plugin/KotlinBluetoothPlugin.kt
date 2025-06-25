package com.example.plugin

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.annotation.RequiresPermission
import com.example.plugin.controller.CustomBluetoothController
import kotlinx.coroutines.*

class KotlinBluetoothPlugin(
    context : Context
) : IBluetoothPlugin{

    private val controller = CustomBluetoothController.getInstance(context)

    override fun startScan() {
        controller.startDiscovery()
    }

    override fun stopScan() {
        controller.stopDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connectToDevice(device: BluetoothDevice) {
        controller.connectToDevice(device)
    }

    override fun disconnect() {
        controller.disconnectFromDevice()
    }

    override fun sendMessage(message: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            controller.sendMessage(message.toString())
        }
    }
}