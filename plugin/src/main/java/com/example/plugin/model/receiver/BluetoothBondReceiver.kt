package com.example.plugin.model.receiver

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION
import androidx.annotation.RequiresPermission

class BluetoothBondReceiver(
    private val onCreateBond : (BluetoothDevice, Boolean) -> Unit
) : BroadcastReceiver() {

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action){
            BluetoothDevice.ACTION_BOND_STATE_CHANGED ->{
                val device =
                    if(VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    }
                    else{
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                val bondState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.ERROR
                    )
                when(bondState){
                    BluetoothDevice.BOND_NONE->{        // Value of 10
                        // Do nothing
                    }
                    BluetoothDevice.BOND_BONDING->{     // Value of 11
                        // send true because the device is bonding
                        device?.let {
                            onCreateBond(device, true)
                        }
                    }
                    BluetoothDevice.BOND_BONDED->{      // Value of 12
                        // send false because already bonded
                        device?.let {
                            onCreateBond(device, false)
                        }
                    }
                }
            }
        }
    }
}