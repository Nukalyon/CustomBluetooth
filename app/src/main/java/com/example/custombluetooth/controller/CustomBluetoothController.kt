package com.example.custombluetooth.controller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.example.custombluetooth.model.BluetoothDeviceReceiver
import com.example.custombluetooth.model.BluetoothError
import com.example.custombluetooth.model.BluetoothReceiver
import com.example.custombluetooth.model.ConnectDeviceThread
import com.example.custombluetooth.model.AppState
import com.example.custombluetooth.model.ServerListenThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.UUID

class CustomBluetoothController private constructor(
    private val appContext: Context
) : IBluetoothController, DataTransferService.MessageListener{

    private val manager by lazy {
        appContext.getSystemService(BluetoothManager::class.java)
    }
    private val adapter by lazy {
        manager?.adapter
    }

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    private val _appState = MutableStateFlow<AppState>(AppState.Idle)
    private val _errorState = MutableStateFlow<BluetoothError?>(null)
    private val _debugMessages = MutableStateFlow<List<String>>(emptyList())

    override val scannedDevices: StateFlow<List<BluetoothDevice>>
        get() = _scannedDevices.asStateFlow()
    override val pairedDevices: StateFlow<List<BluetoothDevice>>
        get() = _pairedDevices.asStateFlow()
    override val appState: StateFlow<AppState>
        get() = _appState.asStateFlow()
    override val errorState: StateFlow<BluetoothError?>
        get() = _errorState.asStateFlow()
    override val debugMessages: StateFlow<List<String>>
        get() = _debugMessages.asStateFlow()

    var dataTransferService : DataTransferService ?= null


    // New config: filter modes
    enum class FilterMode {
        NONE,       // No filtering — add all devices
        AND,        // Match both regex and isSingleDevice condition
        OR          // Match either regex or isSingleDevice condition
    }
    // Configurable filters
    private val filterBySingleDevice = false
    private val filterByRegex = true
    private val filterMode = FilterMode.OR

    //private val isSingleDevice = true
    private val regex = "[a-zA-Z0-9]*".toRegex()
    @SuppressLint("MissingPermission")
    private val receiverDeviceFound = BluetoothDeviceReceiver { newDevice ->

        // Evaluate conditions
        val matchesRegex = if (filterByRegex) {
            regex.containsMatchIn(newDevice.name ?: "")
        } else {
            false
        }

        val shouldAddDevice = when (filterMode) {
            FilterMode.NONE -> true  // add all devices
            FilterMode.AND -> {
                // If filtering by both, device must satisfy both applicable filters
                val conditions = mutableListOf<Boolean>()
                if (filterBySingleDevice) conditions.add(filterBySingleDevice)
                if (filterByRegex) conditions.add(matchesRegex)
                // AND all true, or if empty (no filters), true
                conditions.all { it }
            }

            FilterMode.OR -> {
                // At least one condition true
                val conditions = mutableListOf<Boolean>()
                if (filterBySingleDevice) conditions.add(filterBySingleDevice)
                if (filterByRegex) conditions.add(matchesRegex)
                // OR all, or if empty, true
                if (conditions.isEmpty()) true else conditions.any { it }
            }
        }

        if (shouldAddDevice) {
            registerDebugMessage("DEBUG", "Adding device: $newDevice")
            _scannedDevices.update { devices ->
                if (newDevice in devices) devices else devices + newDevice
            }
            if (filterBySingleDevice) {
                stopDiscovery()
                connectToDevice(newDevice)
            }
        } else {
            registerDebugMessage(
                "DEBUG",
                "Ignoring device (filtered out): ${newDevice.name ?: newDevice.address}"
            )
        }
    }

    /*
    @SuppressLint("MissingPermission")
    private fun isMatchingParameters(device: BluetoothDevice, matchRegex: Boolean, matchUUID: Boolean) : Boolean{
        var res = false
        try {
            if(hasPermissions(Manifest.permission.BLUETOOTH_CONNECT)){
                if(matchRegex && matchUUID){
                    if(device.name == null){
                        res = device.address === SERVICE_UUID
                    }
                    else{
                        res = regex.containsMatchIn(device.name) || device.address == SERVICE_UUID
                    }
                }
                else if(matchRegex){
                    res = if(device.name == null) false else regex.containsMatchIn(device.name)
                }
                else{
                    res = device.address === SERVICE_UUID
                }
            }
        }
        catch (exc: IOException){
            registerDebugMessage("ERROR","isMatchingParameters exception : ${exc.printStackTrace()}")
        }
        return res
    }*/

    @SuppressLint("MissingPermission")
    private val bluetoothReceiver = BluetoothReceiver { isConnected, device ->
        var debug = ""
        if (isConnected) {
            _appState.value = AppState.Connected
            _errorState.value = null
            debug = "Connected to device: ${device.name} / ${device.address}"
        } else {
            _appState.value = AppState.Disconnected
            debug = "Disconnected from device: ${device.name} / ${device.address}"
        }
        registerDebugMessage("DEBUG",debug)
    }

    init {
        // Register receivers immediately on instance initialization
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            //addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        appContext.registerReceiver(bluetoothReceiver, filter)
        appContext.registerReceiver(receiverDeviceFound, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }


    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        if (hasPermissions(Manifest.permission.BLUETOOTH_SCAN)) {
            _errorState.value = null
            _appState.value = AppState.Scanning

            updatePairedDevices()
            adapter?.startDiscovery()
        } else {
            _errorState.value = BluetoothError.PermissionDenied
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        if (hasPermissions(Manifest.permission.BLUETOOTH_SCAN)) {
            _appState.value = AppState.Idle
            adapter?.cancelDiscovery()
            _scannedDevices.value = emptyList()
        }else {
            _errorState.value = BluetoothError.PermissionDenied
        }
    }

    override fun startServer() {
        try {
            if (hasPermissions(Manifest.permission.BLUETOOTH_CONNECT)) {
                registerDebugMessage("DEBUG","Starting Server ...")
                val thread = ServerListenThread(adapter, NAME_SERVER, toUuid(), this, this)
                thread.start()
            }else {
                _errorState.value = BluetoothError.PermissionDenied
            }
        } catch (exc: IOException) {
            registerDebugMessage("ERROR", "Exception caught, details:\n${exc.printStackTrace()}")
        }
    }

    @Volatile
    private var connectThread: ConnectDeviceThread? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connectToDevice(device: BluetoothDevice) {
        try {
            if (hasPermissions(Manifest.permission.BLUETOOTH_CONNECT)) {
                _appState.value = AppState.Connecting
                val thread = ConnectDeviceThread(adapter, toUuid(), device, this, this) // Pass listener
                connectThread = thread
                thread.start()
            }else{
                _errorState.value = BluetoothError.PermissionDenied
            }
        } catch (exc: IOException) {
            registerDebugMessage("ERROR", "Connect method failed, details:\n${exc.printStackTrace()}")
            _appState.value = AppState.Error("Connection failed: ${exc.message}")
        }
    }

    override fun onMessageReceived(message: String) {
        // Handle the received message (update UI, notify user, etc.)
        registerDebugMessage("DEBUG","Message received: $message")
    }

    override fun disconnectFromDevice() {
        _appState.value = AppState.Disconnecting
    }

    override fun sendMessage(message: String) {
        dataTransferService?.let {
            it.write(message.toByteArray())
            registerDebugMessage("DEBUG","$message is sent")
        }?: run {
            registerDebugMessage("DEBUG","dataTransferService isn't initialized")
        }
    }

    // Vérifie si la permission spécifiée est accordée
    fun hasPermissions(permission: String): Boolean {
        val temp = ActivityCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
        //registerDebugMessage("DEBUG","hasPermission? for ${permission.removePrefix("android.permission.")} = $temp")
        return temp
    }

    private fun toUuid() : UUID{
        return UUID.fromString(SERVICE_UUID)
    }

    @SuppressLint("MissingPermission")
    private fun updatePairedDevices() {
        registerDebugMessage("DEBUG", "updatePairedDevices called")
        if (!hasPermissions(Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }
        _pairedDevices.value = adapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun release() {
        try{
            appContext.unregisterReceiver(receiverDeviceFound)
            appContext.unregisterReceiver(bluetoothReceiver)
        }
        catch (exc: IOException){
            registerDebugMessage("ERROR", "UnregisterReceiver failed, details: ${exc.printStackTrace()}")
        }
    }

    fun registerDebugMessage(tag : String, mess : String){
        when(tag){
            "DEBUG" ->{
                Log.d(tag, mess)
            }
            "ERROR"->{
                Log.e(tag, mess)
            }
            "INFO"->{
                Log.i(tag,mess)
            }
        }
        _debugMessages.update { messages ->  if(!messages.contains(mess)) messages + mess else messages }
    }

    // Singleton Pattern
    companion object{
        @Volatile
        private var instance: CustomBluetoothController ?= null
        const val SERVICE_UUID = "9a2437a0-f4d5-4a64-8abf-3e3c45ad0293"
        const val NAME_SERVER = "link_arduino"
        const val ARDUINO_NAME = "FeatherBlue"
        const val TABLETTE = "Yannick"

        fun getInstance(context: Context) : CustomBluetoothController{
            return instance?:synchronized(this) {
                instance?: CustomBluetoothController(context).also { instance = it}
            }
        }
    }
}