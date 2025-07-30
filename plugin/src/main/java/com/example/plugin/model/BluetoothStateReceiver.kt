package com.example.plugin.model

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

class BluetoothStateReceiver(
    private val onStateChanged: (Boolean) -> Unit
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
                        onStateChanged(true)
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        Log.d("DEBUG", "Bluetooth is OFF")
                        onStateChanged(false)
                    }
                }
            }
        }
    }
}