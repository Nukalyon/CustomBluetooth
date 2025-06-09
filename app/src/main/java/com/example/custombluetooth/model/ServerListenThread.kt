package com.example.custombluetooth.model

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class ServerListenThread(
    private val adapter: BluetoothAdapter?,
    private val serverName: String,
    private val uuid: UUID?
) : Thread() {

    private val serverSocket : BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE){
        adapter?.listenUsingInsecureRfcommWithServiceRecord(serverName, uuid)
    }

    override fun run() {
        super.run()
        var shouldLoop = true
        while (shouldLoop){
            val socket: BluetoothSocket? = try {
                serverSocket?.accept()
            } catch (e: IOException) {
                Log.d("DEBUG", "Socket's accept() method failed", e)
                shouldLoop = false
                null
            }
            socket?.also {
                //manageMyConnectedSocket(it)
                serverSocket?.close()
                shouldLoop = false
            }
        }
    }

    private fun cancel() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.d("DEBUG", "Could not close the connect socket", e)
        }
    }
}