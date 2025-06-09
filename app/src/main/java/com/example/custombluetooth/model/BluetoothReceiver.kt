package com.example.custombluetooth.model

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION
import android.util.Log
import androidx.annotation.RequiresPermission

class BluetoothReceiver(
    private val onConnectionStateChanged: (Boolean, BluetoothDevice) -> Unit
) : BroadcastReceiver() {

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("DEBUG", "${intent?.action} received")
        when(intent?.action){

            BluetoothAdapter.ACTION_STATE_CHANGED ->{
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                Log.d("DEBUG", "New state: $state")
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        Log.d("DEBUG", "Bluetooth is ON")
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        Log.d("DEBUG", "Bluetooth is OFF")
                        // Update connection state in the controller
                    }
                }
            }
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

            BluetoothAdapter.ACTION_SCAN_MODE_CHANGED ->{
                var state =
                if (VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothDevice::class.java)
                }
                else{
                    intent.getParcelableExtra(BluetoothAdapter.EXTRA_SCAN_MODE)
                }
                Log.d("DEBUG", "new state : $state")
            }
        }
    }
}