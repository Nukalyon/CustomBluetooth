package com.example.custombluetooth

import android.annotation.SuppressLint
import android.app.ComponentCaller
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
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
import com.example.custombluetooth.controller.CustomBluetoothController.Companion.SERVICE_UUID
import com.example.custombluetooth.controller.CustomBluetoothController.Companion.TABLETTE
import com.example.custombluetooth.ui.theme.CustomBluetoothTheme
import com.example.custombluetooth.view.BluetoothView
import java.util.UUID
import java.util.concurrent.Executor
import java.util.regex.Pattern

private const val SELECT_DEVICE_REQUEST_CODE = 0

class MainActivity : ComponentActivity() {

    private val bluetoothManager by lazy {
        applicationContext.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }
    private val isBluetoothEnabled : Boolean
        get() = bluetoothAdapter?.isEnabled == true

    private val companionManager by lazy{
        applicationContext.getSystemService(CompanionDeviceManager::class.java)
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
                        perms[android.Manifest.permission.BLUETOOTH_CONNECT] == true
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
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
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

    @SuppressLint("MissingPermission")
    private val associationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ){
        result ->
        Log.d("DEBUG","AssociationLauncher Found a result")
        if(result.resultCode == RESULT_OK){
            val data = result.data
            val device =
                if(isSDKSupToVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)){
                    // API 34 and higher
                    val associationInfo = data?.getParcelableExtra(
                        CompanionDeviceManager.EXTRA_ASSOCIATION,
                        AssociationInfo::class.java
                    )
                    associationInfo?.associatedDevice?.bluetoothDevice
                }
                else{
                    // API 33 and lower
                    intent?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                }
            Log.d("DEBUG","device found :${device?.name ?: device?.address}")
            device?.createBond() ?: run {
                Log.d("DEBUG","No device found")
            }
        }
        else {
            Log.d("DEBUG","Association refused by user")
        }
    }

    fun launchAssociation() {
        Log.d("DEBUG","Enter launchAssociation()")
        // https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing?hl=fr
        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile("One"))
            //.addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)), null)
            .build()

        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(true)
            .build()

        if(isSDKSupToVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
        {
            Log.d("DEBUG","SDK IS >= UPSIDE_DOWN_CAKE")
            companionManager.associate(pairingRequest,
                object: CompanionDeviceManager.Callback(){
                    override fun onAssociationPending(intentSender: IntentSender) {
                        super.onAssociationPending(intentSender)
                        Log.d("DEBUG","onAssociationPending enter")
                        val request = IntentSenderRequest.Builder(intentSender).build()
                        Log.d("DEBUG","AssociationLauncher start")
                        associationLauncher.launch(request)
                    }

                    override fun onFailure(p0: CharSequence?) {
                        Log.d("DEBUG","onFailure enter")
                    }
                }, null)
        }
        else {
            Log.d("DEBUG","SDK IS < UPSIDE_DOWN_CAKE")
            if(isSDKSupToVersion(Build.VERSION_CODES.TIRAMISU))
            {
                Log.d("DEBUG","SDK IS >= TIRAMISU")
                companionManager.associate(
                    pairingRequest,
                    executor,
                    object : CompanionDeviceManager.Callback() {
                        override fun onAssociationPending(intentSender: IntentSender) {
                            startIntentSenderForResult(
                                intentSender,
                                SELECT_DEVICE_REQUEST_CODE,
                                null,
                                0,
                                0,
                                0
                            )
                        }

                        @SuppressLint("NewApi", "MissingPermission")
                        override fun onAssociationCreated(associationInfo: AssociationInfo) {
                            val result : BluetoothDevice? = associationInfo.associatedDevice?.bluetoothDevice
                            if(result != null){
                                Toast.makeText(applicationContext,
                                    "We have found this device :\n" +
                                            "Name: ${result.name ?: "N/o"}, address: ${result.address}",
                                    Toast.LENGTH_LONG).show()
                            }
                        }

                        override fun onFailure(p0: CharSequence?) {
                            TODO("Not yet implemented")
                        }
                    }
                )
            }
            else{
                Log.d("DEBUG","SDK IS < TIRAMISU")
            }
        }
    }

    private fun isSDKSupToVersion(version : Int) : Boolean{
        return (Build.VERSION.SDK_INT >= version)
    }

    /*      NEW WAY TO DEAL WITH INTENTRESULT -> Contracts !*/
    // https://stackoverflow.com/questions/79327516/what-is-the-correct-way-to-write-a-ble-characteristic-from-the-main-activity-in
    @SuppressLint("MissingPermission")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        caller: ComponentCaller,
    ) {
        when(requestCode) {
            SELECT_DEVICE_REQUEST_CODE ->{
                when(resultCode){
                    RESULT_OK ->{
                        if(isSDKSupToVersion(Build.VERSION_CODES.TIRAMISU)){
                            val associationInfo : AssociationInfo ?= data?.getParcelableExtra(
                                CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)

                            val deviceToPair: BluetoothDevice? =
                                if(isSDKSupToVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)){
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
                super.onActivityResult(requestCode, resultCode, data, caller)
        }
    }
}