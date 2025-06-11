package com.example.custombluetooth.controller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.custombluetooth.model.BluetoothDeviceReceiver
import com.example.custombluetooth.model.BluetoothError
import com.example.custombluetooth.model.BluetoothReceiver
import com.example.custombluetooth.model.ConnectDeviceThread
import com.example.custombluetooth.model.ConnectionState
import com.example.custombluetooth.model.ScanState
import com.example.custombluetooth.model.ServerListenThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.UUID

/**
 * To continue
 * https://www.blackbox.ai/chat/4iY6KyH
 */

private const val SELECT_DEVICE_REQUEST_CODE = 0

class CustomBluetoothController private constructor(
    private val appContext: Context
) : IBluetoothController, DataTransferService.MessageListener{

    private val manager by lazy {
        appContext.getSystemService(BluetoothManager::class.java)
    }
    private val adapter by lazy {
        manager?.adapter
    }
    val companionManager : CompanionDeviceManager by lazy {
        appContext.getSystemService(CompanionDeviceManager::class.java)
    }

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val _errorState = MutableStateFlow<BluetoothError?>(null)
    private val _debugMessages = MutableStateFlow<List<String>>(emptyList())

    override val scannedDevices: StateFlow<List<BluetoothDevice>>
        get() = _scannedDevices.asStateFlow()
    override val pairedDevices: StateFlow<List<BluetoothDevice>>
        get() = _pairedDevices.asStateFlow()
    override val scanState: StateFlow<ScanState>
        get() = _scanState.asStateFlow()
    override val connectionState: StateFlow<ConnectionState>
        get() = _connectionState.asStateFlow()
    override val errorState: StateFlow<BluetoothError?>
        get() = _errorState.asStateFlow()
    override val debugMessages: StateFlow<List<String>>
        get() = _debugMessages.asStateFlow()
    private lateinit var debug : String

    private val receiverDeviceFound = BluetoothDeviceReceiver{
        newDevice ->
        debug = "FoundDeviceReceiver returned $newDevice"
        Log.d("DEBUG", debug)

        _debugMessages.update { messages ->
            if(!messages.contains(debug)) messages + debug else messages}
        _scannedDevices.update{   devices ->
            if(newDevice in devices) devices else devices + newDevice
        }
    }

    @SuppressLint("MissingPermission")
    private val bluetoothReceiver = BluetoothReceiver { isConnected, device ->
        if (isConnected) {
            _connectionState.value = ConnectionState.Connected
            _errorState.value = null
            debug = "Connected to device: ${device.name} / ${device.address}"
            debug = "Connected to device: ${device.name} / ${device.address}"
        } else {
            _connectionState.value = ConnectionState.Disconnected
            debug = "Disconnected from device: ${device.name} / ${device.address}"
        }
        Log.d("Debug",debug.toString())
        _debugMessages.update { messages -> messages + debug }
    }

    init {
        // Register receivers immediately on instance initialization
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        appContext.registerReceiver(bluetoothReceiver, filter)
        appContext.registerReceiver(receiverDeviceFound, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }


    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        if (hasPermissions(Manifest.permission.BLUETOOTH_SCAN)) {
            _errorState.value = null
            _scanState.value = ScanState.Scanning

            updatePairedDevices()
            adapter?.startDiscovery()
        } else {
            _errorState.value = BluetoothError.PermissionDenied
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        if (hasPermissions(Manifest.permission.BLUETOOTH_SCAN)) {
            _scanState.value = ScanState.Idle
            adapter?.cancelDiscovery()
        }
    }

    override fun startServer() {
        try {
            if (hasPermissions(Manifest.permission.BLUETOOTH_CONNECT)) {
                val thread = ServerListenThread(adapter, NAME_SERVER, toUuid())
                thread.start()
            }
        } catch (exc: IOException) {
            Log.d("DEBUG", "Exception caught, details:\n${exc.printStackTrace()}")
        }
    }

    @Volatile
    private var connectThread: ConnectDeviceThread? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connectToDevice(device: BluetoothDevice) {
        try {
            _connectionState.value = ConnectionState.Connecting
            val thread = ConnectDeviceThread(adapter, toUuid(), device, this) // Pass listener
            connectThread = thread
            thread.start()
        } catch (exc: IOException) {
            Log.d("DEBUG", "Connect method failed, details:\n${exc.printStackTrace()}")
            _connectionState.value = ConnectionState.Failed("Connection failed: ${exc.message}")
        }
    }

    override fun onMessageReceived(message: String) {
        // Handle the received message (update UI, notify user, etc.)
        Log.d("DEBUG", "Message received: $message")
        // You can also update a StateFlow or notify the UI here
    }

    override fun disconnectFromDevice() {
        _scanState.value = ScanState.Disconnecting
    }

    override fun sendMessage(message: String) {
        TODO("Not yet implemented")
    }

    // Vérifie si la permission spécifiée est accordée
    private fun hasPermissions(permission: String): Boolean {
        debug = "hasPermission? for $permission"
        _debugMessages.update { messages -> messages + debug }
        Log.d("DEBUG", debug)
        return appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun toUuid() : UUID{
        return UUID.fromString(SERVICE_UUID)
    }

    @SuppressLint("MissingPermission")
    private fun updatePairedDevices() {
        debug = "updatePairedDevices called"
        Log.d("DEBUG", debug)
        _debugMessages.update { messages -> messages + debug }
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
            Log.d("DEBUG", "UnregisterReceiver failed, details:\n${exc.printStackTrace()}")
        }
    }

    // Singleton Pattern
    companion object{
        @Volatile
        private var instance: CustomBluetoothController ?= null
        const val SERVICE_UUID = "9a2437a0-f4d5-4a64-8abf-3e3c45ad0293"
        const val NAME_SERVER = "link_arduino"
        const val ARDUINO_NAME = "FeatherBlue"  //by name or address ?
        const val TABLETTE = "Yannick"  //by name or address ?

        fun getInstance(context: Context) : CustomBluetoothController{
            return instance?:synchronized(this) {
                instance?: CustomBluetoothController(context).also { instance = it}
            }
        }
    }
}