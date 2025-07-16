package com.example.plugin.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// This class will help to send The list of CustomBluetoothDevice as a single string
class CustomBluetoothDeviceMapper {
    private val json = Json {
        prettyPrint = true // Optional: for better readability
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

//        return try{
//            //json.encodeString convert also a list, no need to concatenate
//            Json.encodeToString(devices)
////            var res = ""
////            for(device in devices){
////                res += encodeSingleToJson(device)
////            }
////            return res
//        }
//        catch (e: Exception) {
//            throw BluetoothMappingException("Failed to encode CustomBluetoothDevice to JSON", e)
//        }
    }
}
class BluetoothMappingException(message: String, cause: Throwable? = null) : Exception(message, cause)