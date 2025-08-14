package com.example.plugin.controller

import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class DataTransferService(
    private val socket: BluetoothSocket,
    private val messageListener: MessageListener // Callback interface for message handling
) : Thread() {

    // Main variables needed for the input and output
    private val inputStream: InputStream = socket.inputStream
    private val outputStream: OutputStream = socket.outputStream
    private val buffer: ByteArray = ByteArray(1024)

    // Method used by the server side, mainly to wait a connection, read the inputStream and
    // when the bytes are loaded, call the callback with the message received
    override fun run() {
        super.run()
        var numBytes: Int
        while (true) {
            numBytes = try {
                inputStream.read(buffer)
            } catch (exc: IOException) {
                Log.d("DEBUG", "Input stream disconnected, details:\n${exc.printStackTrace()}")
                break
            }

            // Process the received bytes
            if (numBytes > 0) {
                val receivedMessage = String(buffer, 0, numBytes)
                messageListener.onMessageReceived(receivedMessage) // Notify listener
            }
        }
    }

    // Method used by the client to send a message by the outputStream
    // for the inputStream in the other device to catch
    fun write(bytes: ByteArray) {
        try {
            outputStream.write(bytes)
        } catch (e: IOException) {
            Log.e("DEBUG", "Error occurred when sending data", e)
        }
    }

    fun cancel() {
        try {
            socket.close()
        } catch (exc: IOException) {
            Log.d("DEBUG", "Closing socket failed, details:\n${exc.printStackTrace()}")
        }
    }

    // Callback interface for message handling
    interface MessageListener {
        fun onMessageReceived(message: String)
    }
}
