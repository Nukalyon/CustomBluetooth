package com.example.plugin.model

import kotlinx.serialization.json.Json

// This class will help to send The list of CustomBluetoothDevice as a single string
class CustomBluetoothDeviceMapper {
    private val json = Json {
        prettyPrint = true // Optional: for better readability
        ignoreUnknownKeys = true
    }
    fun encodeSingleToJson(device: CustomBluetoothDevice): String {
        return try {
            json.encodeToString(device)
        } catch (e: Exception) {
            throw BluetoothMappingException("Failed to encode CustomBluetoothDevice to JSON", e)
        }
    }
    fun encodeMultipleToJson(devices: List<CustomBluetoothDevice>) : String{
        return Json.encodeToString(devices)
    }

    fun decodeSingleToDevice(json : String): CustomBluetoothDevice{
        return try {
            Json.decodeFromString<CustomBluetoothDevice>(json)
        }catch (e: Exception) {
            throw BluetoothMappingException("Failed to decode string (Unity) to CustomBluetoothDevice", e)
        }
    }
}
class BluetoothMappingException(message: String, cause: Throwable? = null) : Exception(message, cause)