package com.example.plugin.model.thread

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.plugin.controller.CustomBluetoothController
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class ServerListenThread(
    private val adapter: BluetoothAdapter?,
    private val serverName: String,
    private val uuid: UUID?,
    private val messageListener: DataTransferService.MessageListener,
    private val controller : CustomBluetoothController
) : Thread() {
    // Creating the waiting area for incoming messages
    private val serverSocket : BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE){
        adapter?.listenUsingInsecureRfcommWithServiceRecord(serverName, uuid)
    }

    override fun run() {
        super.run()
        var shouldLoop = true
        while (shouldLoop){
            //Try to catch an entry
            val socket: BluetoothSocket? = try {
                serverSocket?.accept()
            } catch (e: IOException) {
                Log.d("DEBUG", "Socket's accept() method failed", e)
                shouldLoop = false
                null
            }
            socket?.also {
                // Socket found, waiting messages and no loop
                val dataTransferService = DataTransferService(socket, messageListener)
                controller.dataTransferService = dataTransferService
                dataTransferService.start()
                serverSocket?.close()
                shouldLoop = false
            }
        }
    }

    fun cancel() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.d("DEBUG", "Could not close the connect socket", e)
        }
    }
}