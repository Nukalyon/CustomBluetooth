package com.example.plugin.controller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.example.plugin.MyUnityPlayer
import com.example.plugin.model.AppState
import com.example.plugin.model.BluetoothConnectionReceiver
import com.example.plugin.model.BluetoothDeviceReceiver
import com.example.plugin.model.BluetoothError
import com.example.plugin.model.BluetoothScanReceiver
import com.example.plugin.model.BluetoothStateReceiver
import com.example.plugin.model.ConnectDeviceThread
import com.example.plugin.model.CustomBluetoothDevice
import com.example.plugin.model.CustomBluetoothDeviceMapper
import com.example.plugin.model.ServerListenThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

class CustomBluetoothController private constructor(
    private val appContext: Context
) : IBluetoothController, DataTransferService.MessageListener{

    private val manager by lazy {
        appContext.getSystemService(BluetoothManager::class.java)
    }
    private val adapter by lazy { manager?.adapter }

    internal val isBluetoothEnabled : Boolean
        get() = adapter?.isEnabled == true

    // The scanMode we are looking for is Connectable and Discoverable in our case
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

    // Keep track of the last device connect, maybe reconnect in the future
    internal var connectedDevice : BluetoothDevice ?= null
        get() = field
        set(value) {field = value}

    // All the service / thread used, cancel when something happen
    var dataTransferService : DataTransferService   ?= null
    var serverListenThread  : ServerListenThread    ?= null
    var connectDeviceThread : ConnectDeviceThread   ?= null

    internal var regex : Regex = ".*".toRegex()
        get() = field
        set(value) { field = value }

    // All the receivers and the method to handle each case
    // | -> BluetoothDeviceReceiver -> Triggered when a device found when scanning
    // | -> BluetoothConnectionReceiver -> Triggered when the device connect / disconnect
    // | -> BluetoothStateReceiver -> Triggered when the bluetooth turns ON / OFF
    // | -> BluetoothScanReceiver -> Triggered when the scan mode changes (never saw it)
    @SuppressLint("MissingPermission")
    private val receiverDeviceFound = BluetoothDeviceReceiver { newDevice ->
        handleDeviceFound(newDevice)
    }
    @SuppressLint("MissingPermission")
    private val bluetoothConnectionReceiver = BluetoothConnectionReceiver { isConnected, device ->
        handleConnectionStateChange(isConnected, device)
    }
    private val bluetoothStateReceiver = BluetoothStateReceiver{ isEnabled ->
        handleBluetoothStateChanged(isEnabled)
    }
    private val bluetoothScanReceiver = BluetoothScanReceiver{ scanMode ->
        handleScanModeChanged(scanMode)
    }

    init {
        // Register receivers immediately on instance initialization
        registerReceivers()
        // Launch a coroutine to watch the change in the appstate
        observeAppState()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleDeviceFound(newDevice: BluetoothDevice) {
        if(regex.containsMatchIn(newDevice.name ?: "")){
            var deviceCanBeAdded = false
            if(!_scannedDevices.value.contains(newDevice) && !_pairedDevices.value.contains(newDevice)){
                deviceCanBeAdded = true
            }
            _scannedDevices.update { devices ->
                if (deviceCanBeAdded){
                    devices + newDevice
                } else devices
            }
            // Create a device to send to Unity
            var deviceToSend = CustomBluetoothDeviceMapper().encodeSingleToJson(
                newDevice.toCustomBluetoothDevice()
            )
            if(deviceCanBeAdded){
                // Send the device found with regex comparison
                MyUnityPlayer.sendStringToUnity("List_Scanned_Devices", "OnDevicesScannedReceive", deviceToSend)
            }
        }
        else{
            registerDebugMessage(
                "DEBUG",
                "Ignoring device (filtered out): ${newDevice.name ?: newDevice.address}"
            )
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleConnectionStateChange(isConnected: Boolean, device: BluetoothDevice) {
        var debug = ""
        if (isConnected) {
            _appState.value = AppState.Connected
            _errorState.value = null
            connectedDevice = device
            debug = "Connected to device: ${device.name} / ${device.address}"
        } else {
            _appState.value = AppState.Disconnected
            debug = "Disconnected from device: ${device.name} / ${device.address}"
        }
        registerDebugMessage("DEBUG",debug)
    }

    private fun handleBluetoothStateChanged(isEnabled: Boolean) {
        // Send the status of the bluetooth to a Unity Object / script
        MyUnityPlayer.sendBluetoothState(isEnabled)
    }

    private fun handleScanModeChanged(mode: String) {
        // Debug for the string
        MyUnityPlayer.showToast("mode changed : $mode")
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        // For each receiver, we attach a filter
        appContext.registerReceiver(bluetoothConnectionReceiver, filter)
        appContext.registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        appContext.registerReceiver(bluetoothScanReceiver, IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
        appContext.registerReceiver(receiverDeviceFound, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    private fun observeAppState() {
        // Launch a coroutine to collect changes in _appState
        CoroutineScope(Dispatchers.Main).launch {
            _appState.collect { newState ->
                // Call the method from MyUnityPlayer when the appState changes
                MyUnityPlayer.sendAppState(newState)
            }
        }
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
                serverListenThread = ServerListenThread(adapter, NAME_SERVER, toUuid(), this, this)
                _appState.value = AppState.Connecting
                serverListenThread?.start()
            }else {
                _errorState.value = BluetoothError.PermissionDenied
            }
        } catch (exc: IOException) {
            registerDebugMessage("ERROR", "Exception caught, details:\n${exc.printStackTrace()}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connectToDevice(device: BluetoothDevice) {
        try {
            if (hasPermissions(Manifest.permission.BLUETOOTH_CONNECT)) {
                _appState.value = AppState.Connecting
                connectDeviceThread = ConnectDeviceThread(adapter, toUuid(), device, this, this) // Pass listener
                connectDeviceThread?.start()
            }else{
                _errorState.value = BluetoothError.PermissionDenied
            }
        } catch (exc: IOException) {
            registerDebugMessage("ERROR", "Connect method failed, details:\n${exc.printStackTrace()}")
            _appState.value = AppState.Error("Connection failed: ${exc.message}")
        }
    }

    override fun disconnectFromDevice() {
        try{
            _appState.value = AppState.Disconnecting
            // If the device is client, close the connection
            connectDeviceThread?.cancel()
            // If the device is server, close the listening
            serverListenThread?.cancel()
            // For both, close the bridge
            dataTransferService?.cancel()
        }
        catch (exc : Exception){
            // Error
            _errorState.value = BluetoothError.Unknown("Failed Disconnection with:\n${exc.printStackTrace()}")
        }
    }

    override fun sendMessage(message: String) {
        dataTransferService?.let {
            it.write(message.toByteArray())
            registerDebugMessage("DEBUG","$message is sent")
        }?: run {
            registerDebugMessage("DEBUG","dataTransferService isn't initialized")
        }
    }

    // Callback from the DataTransferListener
    override fun onMessageReceived(message: String) {
        // Handle the received message (update UI, notify user, etc.)
        registerDebugMessage("DEBUG","Message received: $message")
        MyUnityPlayer.sendStringToUnity("Container_MessageReceived", "OnMessageReceive", message.trim())
    }

    // Check if the permission is granted
    // Could be exchanged with the BluetoothPermissionManager.checkPermissionGranted(permission)
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
        if (!hasPermissions(Manifest.permission.BLUETOOTH_CONNECT)) return
        _pairedDevices.value = adapter?.bondedDevices?.toList() ?: emptyList()
    }

    // Unregister all the receivers
    fun release() {
        try{
            appContext.unregisterReceiver(receiverDeviceFound)
            appContext.unregisterReceiver(bluetoothConnectionReceiver)
            appContext.unregisterReceiver(bluetoothScanReceiver)
            appContext.unregisterReceiver(bluetoothStateReceiver)
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
        // Useless for the Unity App, not for android app
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
            // For each devices, map them into a CustomBluetoothDevice
            val customDevices = bondedDevices.map { it.toCustomBluetoothDevice() }
            // Return JSON string of all paired devices
            return CustomBluetoothDeviceMapper().encodeMultipleToJson(customDevices)
        } catch (e: Exception) {
            registerDebugMessage("ERROR", "Failed to update paired devices: ${e.message}")
            registerDebugMessage("Unity", "Failed to update paired devices: ${e.message}")
            return "[]" // Return empty array JSON string on error
        }
    }

    // The following device conversion are needed especially when we want to transfer or receive
    // a device by json.
    // The serialization help for 80% of the work but not entirely so we help it a little

    fun toBluetoothDevice(device : CustomBluetoothDevice): BluetoothDevice? {
        return adapter?.getRemoteDevice(device.address)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun BluetoothDevice.toCustomBluetoothDevice(): CustomBluetoothDevice {
        return CustomBluetoothDevice(
            name = this.name,
            address = this.address,
            deviceType = when (this.bluetoothClass.majorDeviceClass) {
                BluetoothClass.Device.Major.AUDIO_VIDEO -> CustomBluetoothDevice.DeviceType.AUDIO
                BluetoothClass.Device.Major.COMPUTER -> CustomBluetoothDevice.DeviceType.COMPUTER
                BluetoothClass.Device.Major.PHONE -> CustomBluetoothDevice.DeviceType.PHONE
                else -> CustomBluetoothDevice.DeviceType.UNKNOWN
            },
            rssi = null // RSSI not available for bonded devices
        )
    }


    // Singleton Pattern
    companion object{
        @Volatile
        internal var instance: CustomBluetoothController ?= null
        // Service and Name are used to create the bridge for information exchange
        const val SERVICE_UUID = "9a2437a0-f4d5-4a64-8abf-3e3c45ad0293"
        const val NAME_SERVER = "link_arduino"

        fun getInstance(context: Context) : CustomBluetoothController{
            return instance?:synchronized(this) {
                instance?: CustomBluetoothController(context).also { instance = it}
            }
        }
    }
}