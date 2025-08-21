package com.example.plugin.model.receiver

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION
import androidx.annotation.RequiresPermission

class BluetoothDeviceReceiver (
    private val onDeviceFound : (BluetoothDevice) -> Unit
) : BroadcastReceiver(){

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action){
            // Action when the device find another bluetooth device visible
            BluetoothDevice.ACTION_FOUND->{
                val device =
                    if(VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    }
                    else{
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                // If the device is not null and the name is not null, we show it
                // Can implement for the mac address here (else name = "N/O" then send)
                device?.let {
                    if(device.name != null){
                        onDeviceFound(device)
                    }
                }
            }
        }
    }
}