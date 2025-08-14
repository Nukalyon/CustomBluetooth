package com.example.plugin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.example.plugin.controller.CustomBluetoothController
import com.example.plugin.model.AppState
import com.example.plugin.model.CustomBluetoothDeviceMapper
import com.unity3d.player.UnityPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MyUnityPlayer : UnityPlayerActivity(){


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safely initialize Bluetooth controller after Unity activity is available
        controller = CustomBluetoothController.getInstance(
            requireNotNull(UnityPlayer.currentActivity) { "Unity activity is null for controller" }
        )

        // Initialize the Permission Manager
        // Provide feedback if not allowed ?
        permissionManager = BluetoothPermissionManager(
            requireNotNull(UnityPlayer.currentActivity) { "Unity activity is null" }
        )

        //Check if all the permissions listed are granted, else -> request them
        permissionManager?.let {
            if(!it.checkAllPermissionsGranted()){
                it.requestBluetoothPermissions(UnityPlayer.currentActivity)
            }
        }
    }

    // Just checking the result of the user for the request of permission
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
                //showToast("Bluetooth permissions granted")
            } else {
                // Handle the case where permissions are not granted
                Log.d("INFO", "Bluetooth permissions not granted")
                //showToast("Bluetooth permissions are required to access paired devices.")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == BluetoothPermissionManager.REQUEST_ACTIVATION){
            // Check not necessary because we check the event of activation
            if(resultCode == RESULT_OK){
                // Bluetooth Activation Granted !
            }
            else{
                // Shame on you user !
            }
        }
        if(requestCode == BluetoothPermissionManager.REQUEST_VISIBILITY){
            // Check not necessary
            if(resultCode == RESULT_OK){
                // Device is being visible for TIME_VISIBLE seconds
            }
            else{
                // Device can not be found by others devices
            }
        }
    }


    companion object {
        // Controller reference used for Bluetooth actions
        private var controller: CustomBluetoothController? = null
        // PermissionManager to extract all the logic based on the permission
        private var permissionManager : BluetoothPermissionManager ?= null
        var TIME_VISIBLE = 60   // Time in seconds the device will be visible

        /************************************************************************
         *  Add methods :
         *      - Add the tag JVMStatic
         *      - Add JVMName with the name you want the caller to find the method         *
         *************************************************************************/

        // Display a message to the user via Android Toast
        @JvmStatic
        @JvmName("showToast")
        fun showToast(msg: String) {
            Toast.makeText(UnityPlayer.currentActivity, msg, Toast.LENGTH_LONG).show()
        }

        // Starts Bluetooth scan
        @JvmStatic
        @JvmName("startScan")
        fun startScan() {
            controller?.startDiscovery()
        }

        // Stops Bluetooth scan
        @JvmStatic
        @JvmName("stopScan")
        fun stopScan() {
            controller?.stopDiscovery()
        }

        // Initiates connection to a specific Bluetooth device
        @JvmStatic
        @JvmName("connectToDevice")
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun connectToDevice(jsonDevice: String) {
            try{
                // help to make the bridge between Unity and Android for a BluetoothDevice
                //Log.d("Unity", "Device received : $jsonDevice")
                val customDevice = CustomBluetoothDeviceMapper().decodeSingleToDevice(jsonDevice)
                //Log.d("Unity", "Device received (1) : $customDevice")
                val device = controller?.toBluetoothDevice(customDevice)
                //Log.d("Unity", "Device decoded (2) : $device")
                device?.let { controller?.connectToDevice(it) }
            }
            catch (exc : Exception){
                Log.d("Unity","Couldn't decode the device for connection", exc)
            }
        }

        // Start the server
        @JvmStatic
        @JvmName("startServer")
        fun startServer(){
            controller?.startServer()
        }

        // Disconnects from the current Bluetooth device
        @JvmStatic
        @JvmName("disconnect")
        fun disconnect(){
            controller?.disconnectFromDevice()
        }

        // Sends a message over Bluetooth, using a background coroutine
        @JvmStatic
        @JvmName("sendMessage")
        fun sendMessage(message: String?) {
            CoroutineScope(Dispatchers.IO).launch {
                controller?.sendMessage(message.orEmpty())
            }
        }

        // Sets the regex filter for Bluetooth device names
        @JvmStatic
        @JvmName("setRegex")
        fun setRegex(regexString: String) {
            controller?.regex = if (regexString.isBlank()) ".*".toRegex() else regexString.toRegex()
        }

        // Retrieves the current regex pattern as a string
        @JvmStatic
        @JvmName("getRegex")
        fun getRegex(): String = controller?.regex.toString()

        // Checks if Bluetooth is currently enabled
        @JvmStatic
        @JvmName("getBluetoothStatus")
        fun getBluetoothStatus(): Boolean = controller?.isBluetoothEnabled == true

        // Get the visibility of the device
        @JvmStatic
        @JvmName("isDeviceVisible")
        fun isDeviceVisible() : Boolean = controller?.isDeviceVisible == true

        // Called from Unity to retrieve paired devices
        @JvmStatic
        @JvmName("getPairedDevices")
        fun getPairedDevices() {
            Log.d("Unity","getPairedDevices called")
            sendStringToUnity("List_Paired_Devices", "OnDevicesPairedReceive", controller?.getPairedDevicesAsString().toString())
        }

        // Called when the appState was Connected and Bluetooth turned OFF then ON
        @SuppressLint("MissingPermission")
        @JvmStatic
        @JvmName("retryConnection")
        fun retryConnection(){
            Log.d("Unity","Test reconnection to device : " + controller?.connectedDevice )
            controller?.connectedDevice?.let { device ->
                controller?.connectToDevice(device)
            }
        }

        // Explicit
        @JvmStatic
        @JvmName("requestBluetoothActivation")
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun requestBluetoothActivation(){
            //Log.d("Unity", "Enter requestBluetoothActivation kotlin")
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) {
                //Log.d("Unity", "Build >=")
                permissionManager?.requestBluetooth(controller?.isBluetoothEnabled == true)
            }
        }

        // Explicit
        @JvmStatic
        @JvmName("requestVisibility")
        fun requestVisibility(){
            permissionManager?.requestVisibility(TIME_VISIBLE)
        }

        // Send the convert bonded devices as Json to an unity gameObject
        @JvmStatic
        @JvmName("sendJsonToUnity")
        fun sendStringToUnity(gameObject : String, method : String, jsonDevices : String){
            Log.d("Unity","sendListToUnity called")
            //showToast("sendListToUnity called")
            UnityPlayer.UnitySendMessage(gameObject, method, jsonDevices)
        }

        // Called from the plugin when the state of the app changes
        @JvmStatic
        @JvmName("sendAppState")
        fun sendAppState(state: AppState) {
            showToast("App state changed to : $state")
            UnityPlayer.UnitySendMessage("StateManager","OnAppStateChange", state.toString())
        }

        // Send the state of the bluetooth when it turn ON or OFF
        @JvmStatic
        @JvmName("sendBluetoothState")
        fun sendBluetoothState(state: Boolean){
            showToast("Bluetooth turned " + if (state) "ON" else "OFF")
            UnityPlayer.UnitySendMessage("StateManager","OnBluetoothStateChange", if (state) "ON" else "OFF")
        }
    }
}