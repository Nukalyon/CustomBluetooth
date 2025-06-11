package com.example.custombluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.custombluetooth.controller.CustomBluetoothController
import com.example.custombluetooth.ui.theme.CustomBluetoothTheme
import com.example.custombluetooth.view.BluetoothView
import java.io.IOException
import java.util.concurrent.Executor
import java.util.regex.Pattern

private const val SELECT_DEVICE_REQUEST_CODE = 0

class MainActivity : ComponentActivity() {

    private val bluetoothManager by lazy {
        getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }
    private val isBluetoothEnabled : Boolean
        get() = bluetoothAdapter?.isEnabled == true

    private val companionManager by lazy{
        getSystemService(CompanionDeviceManager::class.java) as CompanionDeviceManager
    }
    private val executor : Executor = Executor{ it.run() }


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
                if(canEnableBluetooth && !isBluetoothEnabled){
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
                val btController = CustomBluetoothController.getInstance(applicationContext)
                val view = BluetoothView.getInstance(btController)
                val state = view.state.collectAsState()

                Surface(
                    color = MaterialTheme.colorScheme.background
                ){
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceAround,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Button(onClick = view::startScan, modifier = Modifier.weight(1f)) {
                            Text("Start Scan")
                        }
                        Button(onClick = view::stopScan, modifier = Modifier.weight(1f)) {
                            Text("Stop Scan")
                        }
                        Button(onClick = { launchAssociation() }, modifier = Modifier.weight(1f)) {
                            Text("Association")
                        }

                        Spacer(modifier = Modifier.weight(1f)) // Pushes log box to the bottom

                        // Log box at the bottom
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(350.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(8.dp)
                        ) {
                            val scrollState = rememberScrollState()

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                            ) {
                                Text(
                                    text = "Debug Log",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                state.value.debugMessages.takeLast(50).forEach {
                                    Text(
                                        text = it,
                                        color = Color.Green,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private val associationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ){
        result ->
        if(result.resultCode == RESULT_OK){
            Log.d("DEBUG","AssociationLauncher Found a result -> RESULT_OK")
            val data = result.data
            val device =
                if(isSDKSupToVersion(Build.VERSION_CODES.TIRAMISU)){
                    // API 33 and higher
                    val associationInfo = data?.getParcelableExtra(
                        CompanionDeviceManager.EXTRA_ASSOCIATION,
                        AssociationInfo::class.java
                    )
                    associationInfo?.associatedDevice?.bluetoothDevice
                }
                else{
                    // API 32 and lower
                    intent?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                }
            Log.d("DEBUG","device found :${device?.name ?: device?.address}")
            device?.createBond() ?: run {
                Log.d("DEBUG","No device found")
            }
        }
        else {
            Log.d("DEBUG","Association refused by user -> RESULT_CANCELED")
        }
    }

    fun launchAssociation() {
//        Log.d("DEBUG","Enter launchAssociation()")
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
//            Log.e("DEBUG", "Missing BLUETOOTH_SCAN permission")
//        }
        // https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing?hl=fr
        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile(".*"))
            //.addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)), null)
            .build()

        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
//            .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_NEARBY_DEVICE_STREAMING)
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(false)
            .build()

        try {

            if(hasPermissions(
                    listOf( Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT)
            )){
                if (isSDKSupToVersion(Build.VERSION_CODES.TIRAMISU)) {

                    Log.d("DEBUG", "SDK IS >= UPSIDE_DOWN_CAKE")
                    companionManager.associate(
                        pairingRequest,
                        executor,
                        object : CompanionDeviceManager.Callback() {
                            override fun onAssociationPending(intentSender: IntentSender) {
                                Log.d("DEBUG", "onAssociationPending enter")
                                val request = IntentSenderRequest.Builder(intentSender).build()
                                Log.d("DEBUG", "AssociationLauncher start")
                                associationLauncher.launch(request)

                            }

                            @SuppressLint("MissingPermission")
                            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                                Toast.makeText(
                                    applicationContext,
                                    "Device found :\nName: ${associationInfo.id}, address: ${associationInfo.deviceMacAddress}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            override fun onFailure(p0: CharSequence?) {
                                Log.d("DEBUG", "onFailure enter")
                            }
                        })
                } else {
                    Log.d("DEBUG", "SDK IS < TIRAMISU")
                    companionManager.associate(
                        pairingRequest,
                        object : CompanionDeviceManager.Callback() {

    //                        override fun onAssociationPending(intentSender: IntentSender) {
    //                            Log.d("DEBUG", "onAssociationPending - launching intent for API < 33")
    //                            try {
    //                                startIntentSenderForResult(
    //                                    intentSender,
    //                                    SELECT_DEVICE_REQUEST_CODE,
    //                                    null, 0, 0, 0
    //                                )
    //                            } catch (e: SendIntentException) {
    //                                Log.e("DEBUG", "Failed to launch IntentSender: ${e.message}")
    //                            }
    //                        }

                            override fun onDeviceFound(intentSender: IntentSender) {
                                Log.d("DEBUG", "onDeviceFound enter")
                                val request = IntentSenderRequest.Builder(intentSender).build()
                                Log.d("DEBUG", "AssociationLauncher start")
                                associationLauncher.launch(request)
                            }

    //                        @SuppressLint("NewApi", "MissingPermission")
    //                        override fun onAssociationCreated(associationInfo: AssociationInfo) {
    //                            val result: BluetoothDevice? =
    //                                associationInfo.associatedDevice?.bluetoothDevice
    //                            if (result != null) {
    //                                Toast.makeText(
    //                                    applicationContext,
    //                                    "We have found this device :\n" +
    //                                            "Name: ${result.name ?: "N/o"}, address: ${result.address}",
    //                                    Toast.LENGTH_LONG
    //                                ).show()
    //                            }
    //                        }

                            override fun onFailure(error: CharSequence?) {
                                Log.e("DEBUG", "Association failed: $error")
                            }
                        }, null
                    )
                    Log.d("DEBUG", "After manager called associate()")
                }
            }
        }
        catch (exc : IOException){
            Log.e("DEBUG", "Exception during associate(): ${exc.message}")
        }
    }

    private fun isSDKSupToVersion(version : Int) : Boolean{
        return (Build.VERSION.SDK_INT >= version)
    }

    private fun hasPermissions(permission: List<String>): Boolean {
        var res : Boolean = true
        permission.forEach {
            perm ->
            val temp = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
            Log.e("DEBUG","hasPermission? for $perm = $temp")
            if(!temp){
                res = false
            }
        }
        return res
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