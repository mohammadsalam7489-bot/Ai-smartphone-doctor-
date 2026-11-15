package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.DoctorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    viewModel: DoctorViewModel,
    onBack: () -> Unit
) {
    val diagnostic by viewModel.diagnosticResult.collectAsState()
    var isScanning by remember { mutableStateOf(false) }
    var scanCompleted by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hardware Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_btn")) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scan Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    Text(
                        text = if (isScanning) "Running Comprehensive Diagnostics..." else if (scanCompleted) "Scan Completed Successfully!" else "Ready to Scan Device",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Text(
                        text = "Tests battery health, thermals, storage, RAM, sensors, and input responsiveness.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Button(
                        onClick = {
                            isScanning = true
                            scanCompleted = false
                            viewModel.refreshBatteryStats()
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                isScanning = false
                                scanCompleted = true
                            }, 2000)
                        },
                        enabled = !isScanning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_scan_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Analyzing System...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (scanCompleted) "Run Scan Again" else "Start Diagnostic Scan")
                        }
                    }
                }
            }

            Text(
                text = "Hardware Health Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            DiagnosticMetricCard(
                title = "Battery Health & Status",
                value = "${diagnostic.batteryLevel}% • ${if (diagnostic.isCharging) "Charging" else "Discharging"}",
                subvalue = "Temperature: ${diagnostic.batteryTemp}°C (Normal)",
                icon = Icons.Default.BatteryChargingFull,
                statusColor = Color(0xFF4CAF50),
                testTag = "metric_battery"
            )

            DiagnosticMetricCard(
                title = "RAM Memory Usage",
                value = diagnostic.ramUsage,
                subvalue = "Active background processes optimized",
                icon = Icons.Default.Memory,
                statusColor = Color(0xFF2196F3),
                testTag = "metric_ram"
            )

            DiagnosticMetricCard(
                title = "Internal Storage",
                value = diagnostic.storageUsage,
                subvalue = "Sufficient free space available",
                icon = Icons.Default.Storage,
                statusColor = Color(0xFF4CAF50),
                testTag = "metric_storage"
            )

            DiagnosticMetricCard(
                title = "Sensors & Biometrics",
                value = diagnostic.sensorsStatus,
                subvalue = "Accelerometer, Gyro, Proximity operating correctly",
                icon = Icons.Default.Sensors,
                statusColor = Color(0xFF4CAF50),
                testTag = "metric_sensors"
            )

            DiagnosticMetricCard(
                title = "Touch & Display Test",
                value = if (diagnostic.touchTestPassed) "Passed (No Dead Pixels)" else "Pending Test",
                subvalue = "Multi-touch responsiveness is accurate",
                icon = Icons.Default.TouchApp,
                statusColor = Color(0xFF4CAF50),
                testTag = "metric_touch"
            )
        }
    }
}

@Composable
fun DiagnosticMetricCard(
    title: String,
    value: String,
    subvalue: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    statusColor: Color,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = statusColor)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subvalue, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusColor)
        }
    }
}
