package com.example.plugin

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BluetoothPermissionManager(private val context: Context) {

    companion object {
        // Bluetooth permissions for different Android versions
        private val BLUETOOTH_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            Log.d("INFO","VERSION.SDK_INT < S")
            null
        }

        const val REQUEST_CODE_BLUETOOTH = 1001
    }

    fun checkAllPermissionsGranted(): Boolean {
        return BLUETOOTH_PERMISSIONS?.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } == true
    }

    fun requestBluetoothPermissions(activity: Activity) {
        val neededPermissions = BLUETOOTH_PERMISSIONS?.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }?.toTypedArray()

        neededPermissions?.let {
            if (it.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    activity,
                    neededPermissions,
                    REQUEST_CODE_BLUETOOTH
                )
            }
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        callback: (Boolean) -> Unit
    ) {
        if (requestCode == REQUEST_CODE_BLUETOOTH) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            callback(allGranted)
        }
    }
}