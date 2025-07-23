package com.example.plugin.controller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.example.plugin.MyUnityPlayer
import com.example.plugin.model.AppState
import com.example.plugin.model.BluetoothDeviceReceiver
import com.example.plugin.model.BluetoothError
import com.example.plugin.model.BluetoothReceiver
import com.example.plugin.model.ConnectDeviceThread
import com.example.plugin.model.CustomBluetoothDevice
import com.example.plugin.model.CustomBluetoothDeviceMapper
import com.example.plugin.model.ServerListenThread
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
    internal val isBluetoothEnabled : Boolean
        get() = adapter?.isEnabled == true

    internal val isDeviceVisible : Boolean
        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        get() = adapter?.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE


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


    //internal var regex : Regex = "[A-Za-z0-9]*".toRegex()
    internal var regex : Regex = ".*".toRegex()
        get() = field
        set(value) { field = value }

    @SuppressLint("MissingPermission")
    private val receiverDeviceFound = BluetoothDeviceReceiver { newDevice ->
        if(regex.containsMatchIn(newDevice.name ?: "")){
            _scannedDevices.update { devices ->
                if (newDevice !in devices){
                    devices + newDevice
                } else devices
            }
            // Create a device to send to Unity
            var deviceToSend = CustomBluetoothDeviceMapper().encodeSingleToJson(
                CustomBluetoothDevice(
                    name = newDevice.name,
                    address = newDevice.address,
                    deviceType = when (newDevice.bluetoothClass.majorDeviceClass) {
                        BluetoothClass.Device.Major.AUDIO_VIDEO -> CustomBluetoothDevice.DeviceType.AUDIO
                        BluetoothClass.Device.Major.COMPUTER -> CustomBluetoothDevice.DeviceType.COMPUTER
                        BluetoothClass.Device.Major.PHONE -> CustomBluetoothDevice.DeviceType.PHONE
                        else -> CustomBluetoothDevice.DeviceType.UNKNOWN
                    },
                    rssi = null
                )
            )
            // Send the device found without regex comparison
            MyUnityPlayer.sendJsonToUnity("List_Scanned_Devices", "OnDevicesScannedReceive", deviceToSend)
        }
        else{
            registerDebugMessage(
                "DEBUG",
                "Ignoring device (filtered out): ${newDevice.name ?: newDevice.address}"
            )
        }
    }

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
            "Unity"->{
                Log.d(tag,mess)
            }
        }
        _debugMessages.update { messages ->  if(!messages.contains(mess)) messages + mess else messages }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevicesAsString(): String {
        registerDebugMessage("Unity", "hasPermissions(Manifest.permission.BLUETOOTH_CONNECT) = " + hasPermissions(Manifest.permission.BLUETOOTH_CONNECT))
        if (!hasPermissions(Manifest.permission.BLUETOOTH_CONNECT)) {
            return "[]" // Return empty array JSON string if no permissions
        }
        try {
            val bondedDevices = adapter?.bondedDevices.orEmpty()
            registerDebugMessage("Unity", "bonded list size = " + bondedDevices.size)
            val customDevices = bondedDevices.map { bluetoothDevice ->
                CustomBluetoothDevice(
                    name = bluetoothDevice.name,
                    address = bluetoothDevice.address,
                    deviceType = when (bluetoothDevice.bluetoothClass.majorDeviceClass) {
                        BluetoothClass.Device.Major.AUDIO_VIDEO -> CustomBluetoothDevice.DeviceType.AUDIO
                        BluetoothClass.Device.Major.COMPUTER -> CustomBluetoothDevice.DeviceType.COMPUTER
                        BluetoothClass.Device.Major.PHONE -> CustomBluetoothDevice.DeviceType.PHONE
                        else -> CustomBluetoothDevice.DeviceType.UNKNOWN
                    },
                    rssi = null // RSSI not available for bonded devices
                )
            }
            // Return JSON string of all paired devices
            return CustomBluetoothDeviceMapper().encodeMultipleToJson(customDevices)
        } catch (e: Exception) {
            registerDebugMessage("ERROR", "Failed to update paired devices: ${e.message}")
            registerDebugMessage("Unity", "Failed to update paired devices: ${e.message}")
            return "[]" // Return empty array JSON string on error
        }
    }

    fun toBluetoothDevice(device : CustomBluetoothDevice): BluetoothDevice? {
        return adapter?.getRemoteDevice(device.address)
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