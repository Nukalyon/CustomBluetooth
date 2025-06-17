package com.example.custombluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.example.custombluetooth.controller.CustomBluetoothController
import com.example.custombluetooth.model.AppState
import com.example.custombluetooth.ui.theme.CustomBluetoothTheme
import com.example.custombluetooth.view.BluetoothView
import com.example.custombluetooth.view.DeviceFoundScreen
import com.example.custombluetooth.view.MainScreen

private const val SELECT_DEVICE_REQUEST_CODE = 0
private const val SELECT_DEVICE_DISCOVERABLE = 2

class MainActivity : ComponentActivity() {

    private val bluetoothManager by lazy {
        getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }
    private val isBluetoothEnabled : Boolean
        get() = bluetoothAdapter?.isEnabled == true

    private lateinit var controller: CustomBluetoothController

//    private val companionManager by lazy{
//        getSystemService(CompanionDeviceManager::class.java) as CompanionDeviceManager
//    }
//    private val executor : Executor = Executor{ it.run() }


//    var TIME_VISIBLE = 60
//    val makeDeviceVisible = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        when (result.resultCode) {
//            RESULT_OK -> {
//                addDebug("DEBUG", "makeDeviceVisible -> RESULT_OK")
//                val data = result.data
//                addDebug("DEBUG", "data = ${data.toString()}")
//            }
//            RESULT_CANCELED -> {
//                addDebug("DEBUG", "makeDeviceVisible -> RESULT_CANCELED")
//            }
//            else -> {
//                addDebug("ERROR", "makeDeviceVisible -> Unexpected result code: ${result.resultCode}")
//                addDebug("ERROR", "result = $result")
//            }
//        }
//    }


    @SuppressLint("ObsoleteSdkInt", "MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ){ /* Not needed ?*/}

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ){
            perms ->
                val canEnableBluetooth =
                    if (isSDKSupToVersion(Build.VERSION_CODES.S)) {
                        perms[Manifest.permission.BLUETOOTH_CONNECT] == true
                    } else { true }
                addDebug("INFO", "canEnableBluetooth = $canEnableBluetooth")
                if(canEnableBluetooth && !isBluetoothEnabled){
                    addDebug("DEBUG", "request enable Bluetooth")
                    enableBluetoothLauncher.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    )
                }
        }

        if (isSDKSupToVersion(Build.VERSION_CODES.S)) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADMIN
                )
            )
        }


        setContent{
            CustomBluetoothTheme {
                controller = CustomBluetoothController.getInstance(this)
                val view = BluetoothView.getInstance(controller)
                val state = view.state.collectAsState()

                Surface(
                    color = MaterialTheme.colorScheme.background
                ){
                    when(state.value.appState){
                        AppState.Scanning -> {
                            DeviceFoundScreen(state, controller::connectToDevice, view::stopScan)
                        }
                        AppState.Connecting -> {
                            Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                            ){
                                CircularProgressIndicator()
                                Text(text = "Connecting ...")
                            }
                        }
                        AppState.Connected -> {
                            Text(text = "Devices Connected")
                        }
                        else ->{
                            MainScreen(state, view)
                        }
                    }
                }
            }
        }
    }

//    @SuppressLint("MissingPermission", "NewApi")
//    private val associationLauncher = registerForActivityResult(
//        ActivityResultContracts.StartIntentSenderForResult()
//    ){
//        result ->
//        if(result.resultCode == RESULT_OK){
//            addDebug("INFO","AssociationLauncher Found a result -> RESULT_OK")
//            val data = result.data
//            val device =
//                if(isSDKSupToVersion(Build.VERSION_CODES.TIRAMISU)){
//                    // API 33 and higher
//                    val associationInfo = data?.getParcelableExtra(
//                        CompanionDeviceManager.EXTRA_ASSOCIATION,
//                        AssociationInfo::class.java
//                    )
//                    associationInfo?.associatedDevice?.bluetoothDevice
//                }
//                else{
//                    // API 32 and lower
//                    intent?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
//                }
//            addDebug("INFO","device found :${device?.name ?: device?.address}")
//            device?.createBond() ?: run {
//                addDebug("DEBUG","No device found")
//            }
//        }
//        else {
//            addDebug("ERROR","Association refused by user -> RESULT_CANCELED")
//        }
//    }

    // https://stackoverflow.com/questions/77294327/androids-companiondevicemanager-associate-fails-to-find-any-device-when-filte

//    @SuppressLint("MissingPermission")
//    fun launchAssociation() {
//        // https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing?hl=fr
//        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
//            .setNamePattern(Pattern.compile(".*"))
//            //.addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)), null)
//            .build()
//
//        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
//            .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_NEARBY_DEVICE_STREAMING)
//            .addDeviceFilter(deviceFilter)
//            .setSingleDevice(false)
//            .build()
//
//        try {
//            if(Manifest.permission.BLUETOOTH_SCAN.hasPermissions()){
//                addDebug("DEBUG","isDicovering = ${bluetoothAdapter?.isDiscovering}")
//            }
//            if(hasPermissions(
//                    listOf( Manifest.permission.BLUETOOTH_SCAN,
//                            Manifest.permission.BLUETOOTH_CONNECT)
//            )){
//                addDebug("INFO","SDK IS ${Build.VERSION.SDK_INT}")
//                if (isSDKSupToVersion(Build.VERSION_CODES.TIRAMISU)) {
//
//                    addDebug("DEBUG", "SDK IS >= UPSIDE_DOWN_CAKE")
//                    companionManager.associate(
//                        pairingRequest,
//                        executor,
//                        object : CompanionDeviceManager.Callback() {
//                            override fun onAssociationPending(intentSender: IntentSender) {
//                                addDebug("DEBUG", "onAssociationPending enter")
//                                val request = IntentSenderRequest.Builder(intentSender).build()
//                                addDebug("DEBUG", "AssociationLauncher start")
//                                associationLauncher.launch(request)
//                            }
//
//                            override fun onAssociationCreated(associationInfo: AssociationInfo) {
//                                Toast.makeText(
//                                    applicationContext,
//                                    "Device found :\nName: ${associationInfo.id}, address: ${associationInfo.deviceMacAddress}",
//                                    Toast.LENGTH_LONG
//                                ).show()
//                            }
//
//                            override fun onFailure(p0: CharSequence?) {
//                                addDebug("ERROR", "onFailure enter")
//                            }
//                        })
//                } else {
//                    addDebug("DEBUG", "SDK IS < TIRAMISU")
//                    companionManager.associate(
//                        pairingRequest,
//                        object : CompanionDeviceManager.Callback() {
//
//    //                        override fun onAssociationPending(intentSender: IntentSender) {
//    //                            addDebug("DEBUG", "onAssociationPending - launching intent for API < 33")
//    //                            try {
//    //                                startIntentSenderForResult(
//    //                                    intentSender,
//    //                                    SELECT_DEVICE_REQUEST_CODE,
//    //                                    null, 0, 0, 0
//    //                                )
//    //                            } catch (e: SendIntentException) {
//    //                                Log.e("DEBUG", "Failed to launch IntentSender: ${e.message}")
//    //                            }
//    //                        }
//
//                            override fun onDeviceFound(intentSender: IntentSender) {
//                                addDebug("DEBUG", "onDeviceFound enter")
//                                val request = IntentSenderRequest.Builder(intentSender).build()
//                                addDebug("DEBUG", "AssociationLauncher start")
//                                associationLauncher.launch(request)
//                            }
//
//    //                        @SuppressLint("NewApi", "MissingPermission")
//    //                        override fun onAssociationCreated(associationInfo: AssociationInfo) {
//    //                            val result: BluetoothDevice? =
//    //                                associationInfo.associatedDevice?.bluetoothDevice
//    //                            if (result != null) {
//    //                                Toast.makeText(
//    //                                    applicationContext,
//    //                                    "We have found this device :\n" +
//    //                                            "Name: ${result.name ?: "N/o"}, address: ${result.address}",
//    //                                    Toast.LENGTH_LONG
//    //                                ).show()
//    //                            }
//    //                        }
//
//                            override fun onFailure(error: CharSequence?) {
//                                addDebug("ERROR", "Association failed: $error")
//                            }
//                        }, null
//                    )
//                    addDebug("DEBUG", "After manager called associate()")
//                }
//            }
//            else{
//                //Request visibility of the device
//                //if granted
//                if(hasPermissions(listOf(
//                        Manifest.permission.BLUETOOTH_CONNECT,
//                        Manifest.permission.BLUETOOTH_SCAN
//                )) && bluetoothAdapter?.isEnabled == true){
//
//                    val visibility = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
//                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, TIME_VISIBLE)
//                    makeDeviceVisible.launch(visibility)
//                }
//            }
//        }
//        catch (exc : IOException){
//            addDebug("ERROR", "Exception during associate(): ${exc.message}")
//        }
//    }

    private fun isSDKSupToVersion(version : Int) : Boolean{
        return (Build.VERSION.SDK_INT >= version)
    }

    private fun hasPermissions(permission: List<String>): Boolean {
        var res = true
        permission.forEach {
            perm ->
            val temp = ActivityCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
            addDebug("DEBUG","hasPermission? for ${perm.removePrefix("android.permission.")} = $temp")
            if(!temp){
                res = false
            }
        }
        return res
    }

    private fun String.hasPermissions(): Boolean{
        return this@MainActivity.controller.hasPermissions(this)
    }

    private fun addDebug(tag : String, mess : String){
        controller.registerDebugMessage(tag, mess)
    }


    /*      NEW WAY TO DEAL WITH INTENTRESULT -> Contracts !  */
    // https://stackoverflow.com/questions/79327516/what-is-the-correct-way-to-write-a-ble-characteristic-from-the-main-activity-in
    @SuppressLint("MissingPermission")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        when(requestCode) {
            SELECT_DEVICE_REQUEST_CODE ->{
                when(resultCode){
                    RESULT_OK ->{
                        if(isSDKSupToVersion(Build.VERSION_CODES.TIRAMISU)){
                            val deviceToPair: BluetoothDevice? =
                                if(isSDKSupToVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)){
                                    val associationInfo : AssociationInfo ?=
                                        data?.getParcelableExtra(
                                            CompanionDeviceManager.EXTRA_ASSOCIATION,
                                            AssociationInfo::class.java
                                        )
                                    associationInfo?.associatedDevice?.bluetoothDevice
                                }
                                else{
                                    data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                                }
                            deviceToPair?.createBond()
                        }
                        //Check older Version ?
                    }
                    RESULT_CANCELED ->{
                        // too bad
                    }
                }
            }
            else ->
                super.onActivityResult(requestCode, resultCode, data)
        }
    }
}