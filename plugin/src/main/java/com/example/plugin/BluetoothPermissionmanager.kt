package com.example.plugin

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


// This class could be replaced with the UnityPlayer requestPermission method I think
class BluetoothPermissionManager(private val context: Context){

    companion object {
        // Bluetooth permissions for different Android versions
        private val BLUETOOTH_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            Log.d("INFO","VERSION.SDK_INT < S")
            null
        }

        const val REQUEST_CODE_BLUETOOTH = 1001
        const val REQUEST_ACTIVATION = 1002
        const val REQUEST_VISIBILITY = 1003
    }

    // Check for a single permission is granted and is in the list
    fun checkPermissionGranted(permission: String) : Boolean{
        var res : Boolean = false
        BLUETOOTH_PERMISSIONS?.let {
            res =
            if(it.contains(permission)){
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            } else{
                false
            }
        }
        return res
    }

    // Check for all the permission registered are granted
    fun checkAllPermissionsGranted(): Boolean {
        return BLUETOOTH_PERMISSIONS?.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } == true
    }

    fun requestBluetoothPermissions(activity: Activity?) {
        // Check from all the permissions if any is not granted
        val neededPermissions = BLUETOOTH_PERMISSIONS?.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }?.toTypedArray()

        //For each permission not granted, ask the user
        neededPermissions?.let {
            if (it.isNotEmpty()) {
                activity?.let{
                    ActivityCompat.requestPermissions(
                        activity,
                        neededPermissions,
                        REQUEST_CODE_BLUETOOTH
                    )
                }
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
            //Link the callback in MyUnityPlayer
            callback(allGranted)
        }
    }

    @SuppressLint("MissingPermission")
    fun requestBluetooth(isBluetoothEnabled: Boolean) {
        Log.d("Unity", "Enter requestBluetooth Permission")
        if(checkPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT) && !isBluetoothEnabled){
            Log.d("Unity", "Permission granted and bl disabled")
            (context as Activity).startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ACTIVATION)
        }
    }

    fun requestVisibility(timeVisibleInSeconds: Int) {
        Log.d("Unity","Enter requestVisibility")
        val visibility = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, timeVisibleInSeconds)
        (context as Activity).startActivityForResult(visibility, REQUEST_VISIBILITY)
    }
}