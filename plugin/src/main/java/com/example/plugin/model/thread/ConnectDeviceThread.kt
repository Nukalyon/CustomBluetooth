package com.example.plugin.model.thread

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.plugin.controller.CustomBluetoothController
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
    // Create the anchor point, socket for the connection
    private val socket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
        device.createRfcommSocketToServiceRecord(uuid)
    }

    override fun run() {
        // Cancel the discovery because we are trying to connect
        if (adapter?.isDiscovering == true) {
            adapter.cancelDiscovery()
        }
        try {
            socket?.let { socket ->
                socket.connect()
                // Creation of the service for the information exchange with the callback
                val dataTransferService =
                    DataTransferService(socket, messageListener) // Pass listener
                controller.dataTransferService = dataTransferService
                dataTransferService.start()
            }
        } catch (exc: IOException) {
            Log.d("DEBUG", "Could not connect to $device, Details:\n${exc.printStackTrace()}")
        }
    }

    fun cancel(){
        try{
            socket?.close()
        }
        catch(exc : IOException){
            Log.d("DEBUG", "Could not close the client socket\n${exc.printStackTrace()}")
        }
    }
}