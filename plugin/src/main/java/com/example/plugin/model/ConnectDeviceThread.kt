package com.example.plugin.model

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.plugin.controller.CustomBluetoothController
import com.example.plugin.controller.DataTransferService
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class ConnectDeviceThread(
    private val adapter: BluetoothAdapter?,
    private val uuid: UUID,
    private val device: BluetoothDevice,
    private val messageListener: DataTransferService.MessageListener, // Add listener
    private val controller : CustomBluetoothController
) : Thread() {
    private val socket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
        device.createRfcommSocketToServiceRecord(uuid)
    }
    override fun run() {
        if (adapter?.isDiscovering == true) {
            adapter.cancelDiscovery()
        }
        try {
            socket?.let { socket ->
                socket.connect()
                val dataTransferService = DataTransferService(socket, messageListener) // Pass listener
                controller.dataTransferService = dataTransferService
                dataTransferService.start()
            }
        } catch (exc: IOException) {
            Log.d("DEBUG", "Could not connect to $device, Details:\n${exc.printStackTrace()}")
        }
    }

    private fun cancel(){
        try{
            socket?.close()
        }
        catch(exc : IOException){
            Log.d("DEBUG", "Could not close the client socket\n${exc.printStackTrace()}")
        }
    }
}