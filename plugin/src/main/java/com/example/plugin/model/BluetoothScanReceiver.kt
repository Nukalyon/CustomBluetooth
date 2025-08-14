package com.example.plugin.model

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION
import android.util.Log

class BluetoothScanReceiver (
    private val onScanChanged: (String) -> Unit
) : BroadcastReceiver(){

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("DEBUG", "${intent?.action} received")
        when(intent?.action){
            // Action when scan mode of the device is changed
            BluetoothAdapter.ACTION_SCAN_MODE_CHANGED ->{
                var state =
                    if (VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothAdapter.EXTRA_SCAN_MODE, String::class.java)
                    }
                    else{
                        intent.getParcelableExtra(BluetoothAdapter.EXTRA_SCAN_MODE)
                    }
                /*
                https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#EXTRA_SCAN_MODE
                state can be a string value :
                - SCAN_MODE_NONE                        or 20
                - SCAN_MODE_CONNECTABLE                 or 21
                - SCAN_MODE_CONNECTABLE_DISCOVERABLE    or 23
                 */
                state?.let {
                    Log.d("DEBUG", "new state : $state")
                    onScanChanged(state)
                }
            }
        }
    }
}