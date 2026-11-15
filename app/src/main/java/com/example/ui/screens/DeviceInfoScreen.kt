package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onBack: () -> Unit
) {
    val specs = listOf(
        "Manufacturer" to Build.MANUFACTURER,
        "Device Model" to Build.MODEL,
        "Product" to Build.PRODUCT,
        "Android Version" to Build.VERSION.RELEASE,
        "SDK API Level" to Build.VERSION.SDK_INT.toString(),
        "Hardware Board" to Build.BOARD,
        "Hardware Brand" to Build.BRAND,
        "Device Hardware" to Build.HARDWARE,
        "Build Fingerprint" to Build.FINGERPRINT
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Hardware Specs") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("specs_back_btn")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            specs.forEach { (label, value) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spec_$label"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
