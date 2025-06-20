package com.unity3d.player

import android.content.Context

object UnityPlayer {
    @JvmStatic
    fun UnitySendMessage(gameObject: String, methodName: String, message: String) {
        println("Message simulé vers Unity : $gameObject -> $methodName('$message')")
    }

    @JvmStatic
    val currentActivity: Context? = null
}