package com.example.custombluetooth.model

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
            BluetoothDevice.ACTION_FOUND->{
                val device =
                    if(VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    }
                    else{
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                device?.let {
                    if(device.name != null){
                        onDeviceFound(device)
                    }
                }
            }
        }
    }
}