package com.example.custombluetooth.view

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

@Composable
fun DeviceFoundScreen(
    state : State<UIState>,
    onClick: (BluetoothDevice) -> Unit,
    onStopScan : () -> Unit
){
    // Obtain screen width and height in dp for dimension calculation
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenWidthDp = configuration.screenWidthDp.dp

    // Calculate box width and height as per requirement
    val boxHeight = screenHeightDp * 0.8f
    val boxWidth = screenWidthDp * 0.9f

    // Centered container with fixed size
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB)), // Light background as per guidelines (#ffffff slight offwhite)
        contentAlignment = Alignment.Center
    ) {
        Surface(
            tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.medium, // Rounded corners ~0.75rem ~12dp
            shadowElevation = 8.dp,
            modifier = Modifier
                .width(boxWidth)
                .height(boxHeight)
                .zIndex(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row (
                    modifier = Modifier.fillMaxWidth()
                ){
                    // Title
                    Text(
                        text = "Bluetooth device Found",
//                        style = MaterialTheme.typography.headlineLarge.copy(
//                            fontWeight = FontWeight.ExtraBold,
//                            fontSize = 32.sp,
//                            color = Color.Black
//                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Button(
                        onClick = onStopScan,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(bottom = 24.dp)
                    ) {
                        Text(text = "Stop Scan")
                    }
                }


                // LazyColumn showing devices
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(state.value.scannedDevices) { device ->
                        DeviceListItem(device = device, onClick = onClick)
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceListItem(
    device: BluetoothDevice,
    onClick: (BluetoothDevice) -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(device) }
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 20.dp)) {
            Text(
                text = device.name?.takeIf { it.isNotBlank() } ?: "no name",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280) // Neutral gray as per guidelines
                )
            )
        }
    }
}