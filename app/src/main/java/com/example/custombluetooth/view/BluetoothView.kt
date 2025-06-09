package com.example.custombluetooth.view

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.custombluetooth.controller.CustomBluetoothController
import com.example.custombluetooth.model.BluetoothError
import com.example.custombluetooth.model.ConnectionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class BluetoothView private constructor(
    private val controller: CustomBluetoothController
) : ViewModel() {

    // Singleton Pattern
    companion object {
        @Volatile
        private var instance: BluetoothView? = null

        fun getInstance(controller: CustomBluetoothController) = instance ?: synchronized(this) {
            instance ?: BluetoothView(controller).also { instance = it }
        }
    }

    // StateFlow for managing the UI state
    private val _scannedDevices = controller.scannedDevices
    private val _pairedDevices = controller.pairedDevices
    private val _connectionState = controller.connectionState
    private val _errorState = controller.errorState
    private val _debugMessages = controller.debugMessages

    // Combine the states into a single flow for the UI
    val state = combine(
        _scannedDevices,
        _pairedDevices,
        _connectionState,
        _errorState,
        _debugMessages
    ) { scannedDevices, pairedDevices, connectionState, error , debug->
        // Create a UI state object or a simple data structure to hold the combined state
        UIState(
            scannedDevices = scannedDevices,
            bondedDevices = pairedDevices,
            connectionState = connectionState,
            errorDesc = error,
            debugMessages = debug
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UIState())

    init {
        // Listen for errors and update the state accordingly
        _errorState.onEach { error ->
            // Handle error updates if needed
        }.launchIn(viewModelScope)

        // Listen for connection state changes if needed
        _connectionState.onEach { connectionState ->
            // Handle connection state updates if needed
        }.launchIn(viewModelScope)
    }

    fun startScan() {
        controller.startDiscovery()
    }

    fun stopScan() {
        controller.stopDiscovery()
    }

    override fun onCleared() {
        super.onCleared()
        controller.release()
    }
}

// Define a simple UI state data class to hold the combined state
data class UIState(
    val scannedDevices: List<BluetoothDevice> = emptyList(),
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val errorDesc: BluetoothError? = null,
    val debugMessages : List<String> = emptyList()
)
