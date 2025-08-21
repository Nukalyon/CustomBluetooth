package com.example.plugin.model.receiver

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION
import android.util.Log
import androidx.annotation.RequiresPermission

class BluetoothConnectionReceiver (
    private val onConnectionStateChanged: (Boolean, BluetoothDevice) -> Unit
) : BroadcastReceiver(){

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("DEBUG", "${intent?.action} received")
        when(intent?.action){
            // Action when the device enter in mode Connected
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device: BluetoothDevice? =
                    if (VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java)
                    }
                    else{
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                Log.d("DEBUG", "Device connected: ${device?.name} / ${device?.address}")
                device?.let { onConnectionStateChanged(true, it) }
            }

            // Action when the device enter in mode Disconnected
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device: BluetoothDevice? =
                    if (VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java)
                    }
                    else{
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                Log.d("DEBUG", "Device disconnected: ${device?.name} / ${device?.address}")
                device?.let { onConnectionStateChanged(false, it) }
            }
        }
    }
}