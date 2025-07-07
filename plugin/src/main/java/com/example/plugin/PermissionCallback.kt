package com.example.plugin

interface PermissionCallback {
    fun onPermissionResult(granted: Boolean)
}