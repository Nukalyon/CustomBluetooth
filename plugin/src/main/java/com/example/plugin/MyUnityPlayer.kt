package com.example.plugin

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.example.plugin.controller.CustomBluetoothController
import com.example.plugin.model.CustomBluetoothDeviceMapper
import com.unity3d.player.UnityPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/***    LE GOAT
 *  https://www.youtube.com/watch?v=R-cYild8ZYs
 */

class MyUnityPlayer : UnityPlayerActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple debug feedback
        UnityPlayer.currentActivity?.let {
            Toast.makeText(it, "PluginActivity -> onCreate", Toast.LENGTH_LONG).show()
        }

        // Safely initialize Bluetooth controller after Unity activity is available
        controller = CustomBluetoothController.getInstance(
            requireNotNull(UnityPlayer.currentActivity) { "Unity activity is null" }
        )

        permissionManager = BluetoothPermissionManager(
            requireNotNull(UnityPlayer.currentActivity) { "Unity activity is null" }
        )

        permissionManager?.let {
            if(!it.checkAllPermissionsGranted()){
                it.requestBluetoothPermissions(UnityPlayer.currentActivity)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        permissionManager?.onRequestPermissionsResult(requestCode, grantResults) { allGranted ->
            if (allGranted) {
                // Permissions granted, proceed with Bluetooth operations
                Log.d("INFO", "Bluetooth permissions granted")
                showToast("Bluetooth permissions granted")
            } else {
                // Handle the case where permissions are not granted
                Log.d("INFO", "Bluetooth permissions not granted")
                showToast("Bluetooth permissions are required to access paired devices.")
            }
        }
    }

    companion object {
        // Controller reference used for Bluetooth actions
        private var controller: CustomBluetoothController? = null
        private var permissionManager : BluetoothPermissionManager ?= null

        // Simulated Unity messaging for debug
        @JvmStatic
        fun unitySendMessage(gameObject: String, methodName: String, message: String) {
            println("Message sent to Unity: $gameObject -> $methodName('$message')")
        }

        // Display a message to the user via Android Toast
        @JvmStatic
        fun showToast(msg: String) {
            Toast.makeText(UnityPlayer.currentActivity, msg, Toast.LENGTH_LONG).show()
        }

        // Starts Bluetooth scan
        @JvmStatic
        fun startScan() {
            controller?.startDiscovery()
        }

        // Stops Bluetooth scan
        @JvmStatic
        fun stopScan() {
            controller?.stopDiscovery()
        }

        // Initiates connection to a specific Bluetooth device
        @JvmStatic
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun connectToDevice(jsonDevice: String) {
            try{
                // help to make the bridge between Unity and Android for a BluetoothDevice
                Log.d("Unity", "Device received : $jsonDevice")
                val customDevice = CustomBluetoothDeviceMapper().decodeSingleToDevice(jsonDevice)
                Log.d("Unity", "Device received (1) : $customDevice")
                val device = controller?.toBluetoothDevice(customDevice)
                Log.d("Unity", "Device decoded (2) : $device")
                device?.let { controller?.connectToDevice(it) }
            }
            catch (exc : Exception){
                Log.d("Unity","Couldn't decode the device for connection", exc)
            }
        }

        // Disconnects from the current Bluetooth device
        @JvmStatic
        fun disconnect() {
            controller?.disconnectFromDevice()
        }

        // Sends a message over Bluetooth, using a background coroutine
        @JvmStatic
        fun sendMessage(message: String?) {
            CoroutineScope(Dispatchers.IO).launch {
                controller?.sendMessage(message.orEmpty())
            }
        }

        // Sets the regex filter for Bluetooth device names
        @JvmStatic
        fun setRegex(regexString: String) {
            controller?.regex = if (regexString.isBlank()) ".*".toRegex() else regexString.toRegex()
        }

        // Retrieves the current regex pattern as a string
        @JvmStatic
        fun getRegex(): String = controller?.regex.toString()

        // Checks if Bluetooth is currently enabled
        @JvmStatic
        fun getBluetoothStatus(): Boolean = controller?.isBluetoothEnabled == true

        @JvmStatic
        fun isDeviceVisible() : Boolean = controller?.isDeviceVisible == true

        // Called from Unity to retrieve paired devices
        @JvmStatic
        fun getPairedDevices() {
            Log.d("Unity","getPairedDevices called")
            showToast("getPairedDevices called")
            sendJsonToUnity("List_Paired_Devices", "OnDevicesPairedReceive", controller?.getPairedDevicesAsString().toString())
        }

        // Send the convert bonded devices as Json to an unity gameObject
        @JvmStatic
        fun sendJsonToUnity(gameObject : String, method : String, jsonDevices : String){
            Log.d("Unity","sendListToUnity called")
            showToast("sendListToUnity called")
            UnityPlayer.UnitySendMessage(gameObject, method, jsonDevices)
        }


    }
}