package com.example.plugin

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.example.plugin.controller.CustomBluetoothController
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
        Toast.makeText(UnityPlayer.currentActivity, "PluginActivity -> onCreate", Toast.LENGTH_LONG).show()
    }

    companion object {
        private val controller = CustomBluetoothController.getInstance((UnityPlayer.currentActivity))

        fun UnitySendMessage(gameObject: String, methodName: String, message: String) {
            println("Message simulé vers Unity : $gameObject -> $methodName('$message')")
        }

        @JvmStatic
        fun getActivity(): Activity? {
            return UnityPlayer.currentActivity
        }

        @JvmStatic
        fun showToast(msg: String){
            Toast.makeText(UnityPlayer.currentActivity, msg, Toast.LENGTH_LONG).show()
        }

        @JvmStatic
        fun startScan() {
            controller.startDiscovery()
        }

        @JvmStatic
        fun stopScan() {
            controller.stopDiscovery()
        }

        @JvmStatic
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun connectToDevice(device: BluetoothDevice) {
            controller.connectToDevice(device)
        }

        @JvmStatic
        fun disconnect() {
            controller.disconnectFromDevice()
        }

        @JvmStatic
        fun sendMessage(message: String?) {
            CoroutineScope(Dispatchers.IO).launch {
                controller.sendMessage(message.toString())
            }
        }

        @JvmStatic
        fun setRegex(regexString: String){
            if(regexString.isBlank()){
                controller.regex = ".*".toRegex()
                return
            }
            controller.regex = regexString.toRegex()
        }

        @JvmStatic
        fun getRegex(): String {
            return controller.regex.toString()
        }

        @JvmStatic
        fun getBluetoothStatus(): Boolean {
            return controller.isBluetoothEnabled
        }
    }
}