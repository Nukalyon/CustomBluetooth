package com.example.plugin

import android.content.Context
import com.unity3d.player.MyUnityPlayer


// https://docs.unity3d.com/6000.1/Documentation/Manual/android-plugins-java-code-from-c-sharp.html
object BluetoothUnityBridge {

    private var plugin: KotlinBluetoothPlugin? = null

    @JvmStatic
    fun init(context: Context) {
        plugin = KotlinBluetoothPlugin(context)
    }

    @JvmStatic
    fun isinit(): Boolean {
        return plugin != null
    }

    @JvmStatic
    fun startScan() {
        plugin?.startScan()
    }

    @JvmStatic
    fun stopScan() {
        plugin?.stopScan()
    }

    @JvmStatic
    fun sendMessage(message: String) {
        plugin?.sendMessage(message)
    }

    @JvmStatic
    fun disconnect() {
        plugin?.disconnect()
    }

    @JvmStatic
    fun notifyUnity(message: String) {
        MyUnityPlayer.UnitySendMessage("BluetoothReceiver", "OnMessageArrived", message)
    }
}
