package com.example.custombluetooth.model

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.custombluetooth.controller.DataTransferService
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class ConnectDeviceThread(
    private val adapter: BluetoothAdapter?,
    private val uuid: UUID,
    private val device: BluetoothDevice,
    private val messageListener: DataTransferService.MessageListener // Add listener
) : Thread() {
    private val socket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
        device.createRfcommSocketToServiceRecord(uuid)
    }
    private var dataTransferService: DataTransferService? = null
    override fun run() {
        super.run()
        if (adapter?.isDiscovering == true) {
            adapter.cancelDiscovery()
        }
        try {
            socket?.let { socket ->
                socket.connect()
                dataTransferService = DataTransferService(socket, messageListener) // Pass listener
                dataTransferService?.start()
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