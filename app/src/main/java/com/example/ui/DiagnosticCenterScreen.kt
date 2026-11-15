package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.model.ActionType
import com.example.data.model.DeviceVitals
import com.example.data.model.DoctorAction
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.BuildConfig
import com.example.data.model.CapabilityMatrixEntry
import com.example.data.model.CapabilityTierCategory
import com.example.data.repair.SafeRepairRegistry
import com.example.data.update.UpdateState
import com.example.ui.components.AutoFixDialog
import com.example.ui.components.LanguageToggleSwitch
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalizedStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticCenterScreen(
    viewModel: DoctorViewModel,
    onNavigateToChatWithQuery: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val vitals by viewModel.vitals.collectAsStateWithLifecycle()
    val showAutoFixDialog by viewModel.showAutoFixDialog.collectAsStateWithLifecycle()
    val isAutoFixRunning by viewModel.isAutoFixRunning.collectAsStateWithLifecycle()
    val autoFixReport by viewModel.autoFixReport.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val capabilityMatrix by viewModel.capabilityMatrix.collectAsStateWithLifecycle()
    val strings = AppStrings.forLanguage(currentLanguage)
    val isHindi = currentLanguage.equals("Hindi", ignoreCase = true)
    var matrixExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val oemProfile = DeviceCompatibilityEngine.getProfile(context)

    if (showAutoFixDialog) {
        AutoFixDialog(
            report = autoFixReport,
            isScanning = isAutoFixRunning,
            strings = strings,
            onDismiss = { viewModel.closeAutoFixDialog() },
            onExecuteRestrictedAction = { action ->
                viewModel.handleAction(context, action)
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CyberBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.doctor_badge_transparent),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(1.dp, CyberCyan, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = strings.diagnosticHudTitle,
                                    color = CyberTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(CyberGreen, CircleShape)
                                )
                            }
                            Text(
                                text = strings.diagnosticHudSubtitle,
                                fontSize = 9.sp,
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                actions = {
                    LanguageToggleSwitch(
                        currentLanguage = currentLanguage,
                        onSelectLanguage = { lang -> viewModel.setLanguage(lang) }
                    )
                    IconButton(
                        onClick = { viewModel.refreshVitals() },
                        modifier = Modifier.testTag("refresh_diagnostics_btn")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = if (isHindi) "टेलीमेट्री रीफ़्रेश करें" else "Refresh Telemetry",
                            tint = CyberCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("diagnostic_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Holographic HUD Health Gauge
            item {
                vitals?.let { v ->
                    FuturisticHealthGaugeCard(
                        vitals = v,
                        oemFlavor = oemProfile.osFlavor,
                        strings = strings,
                        onDeepScanClick = {
                            onNavigateToChatWithQuery(strings.deepDiagnosisQuery)
                        }
                    )
                }
            }

            // 1-Tap Smart Auto-Fix Banner
            item {
                FuturisticAutoFixBanner(
                    strings = strings,
                    onRunAutoFix = { viewModel.startAutoFixScanAndRepair() }
                )
            }

            // Automatic APK Updates Card
            item {
                AutomaticAppUpdateCard(
                    updateState = updateState,
                    strings = strings,
                    onCheckUpdates = { viewModel.checkForUpdates(silent = false) },
                    onOpenUpdateDialog = { viewModel.openUpdateDialog() }
                )
            }

            // Quick Hardware Test Grid
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.hardwareTestMatrixTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = strings.calibratedSensorsLabel,
                            fontSize = 9.sp,
                            color = CyberTextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HardwareTestTile(
                            title = strings.hwSpeakerTitle,
                            subtitle = strings.hwSpeakerSub,
                            testTagSuffix = "Speaker",
                            icon = Icons.Default.GraphicEq,
                            accentColor = CyberTeal,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.handleAction(
                                    context,
                                    DoctorAction(
                                        id = "test_audio",
                                        title = if (isHindi) "स्पीकर परीक्षण" else "Speaker Test",
                                        description = if (isHindi) "कैलिब्रेटेड ऑडियो जाँच" else "Calibrated audio check",
                                        actionType = ActionType.RUN_AUDIO_TEST
                                    )
                                )
                            }
                        )
                        HardwareTestTile(
                            title = strings.hwHapticsTitle,
                            subtitle = strings.hwHapticsSub,
                            testTagSuffix = "Haptics",
                            icon = Icons.Default.Vibration,
                            accentColor = CyberBlue,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.handleAction(
                                    context,
                                    DoctorAction(
                                        id = "test_vibe",
                                        title = if (isHindi) "वाइब्रेशन परीक्षण" else "Vibration Test",
                                        description = if (isHindi) "मल्टी-फ़्रीक्वेंसी पल्स" else "Multi-frequency pulse",
                                        actionType = ActionType.RUN_VIBRATION_TEST
                                    )
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HardwareTestTile(
                            title = strings.hwDisplayTitle,
                            subtitle = strings.hwDisplaySub,
                            testTagSuffix = "Display",
                            icon = Icons.Default.DisplaySettings,
                            accentColor = CyberPurple,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.handleAction(
                                    context,
                                    DoctorAction(
                                        id = "test_screen",
                                        title = if (isHindi) "स्क्रीन पिक्सेल परीक्षण" else "Screen Test",
                                        description = if (isHindi) "आरजीबी सब-पिक्सेल जाँच" else "RGB sub-pixel check",
                                        actionType = ActionType.RUN_SCREEN_TEST
                                    )
                                )
                            }
                        )
                        HardwareTestTile(
                            title = strings.hwNetworkTitle,
                            subtitle = strings.hwNetworkSub,
                            testTagSuffix = "Network",
                            icon = Icons.Default.NetworkCheck,
                            accentColor = CyberGreen,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.handleAction(
                                    context,
                                    DoctorAction(
                                        id = "test_net",
                                        title = if (isHindi) "नेटवर्क पिंग परीक्षण" else "Network Test",
                                        description = if (isHindi) "लाइव टीसीपी विलंबता जाँच" else "Live TCP latency ping",
                                        actionType = ActionType.RUN_NETWORK_TEST
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Diagnostic Categories Grid
            item {
                Text(
                    text = strings.telemetryModulesTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                vitals?.let { v ->
                    val batText = if (v.batteryVerified) "${v.batteryLevel}%" else strings.notCollectedLabel
                    val tempText = if (v.temperatureVerified) "${v.batteryTempC}°C" else strings.notCollectedLabel
                    val powerDetail = if (isHindi) {
                        "स्थिति: ${v.batteryStatus} • स्रोत: ${v.batteryPlugged}"
                    } else {
                        "Status: ${v.batteryStatus} • Power source: ${v.batteryPlugged}"
                    }
                    val powerPrompt = if (isHindi) {
                        "डॉक्टर, कृपया मेरी बैटरी स्वास्थ्य और तापमान ($tempText) का विश्लेषण करें।"
                    } else {
                        "Doctor, please analyze my battery health and thermal temperature ($tempText)."
                    }
                    FuturisticCategoryCard(
                        title = strings.modPowerTitle,
                        status = "$batText • $tempText (${v.batteryHealth})",
                        detail = powerDetail,
                        testKey = "power",
                        icon = Icons.Default.BatteryChargingFull,
                        accentColor = if ((v.temperatureVerified && v.batteryTempC > 40f) || (v.batteryVerified && v.batteryLevel < 20)) CyberRed else CyberGreen,
                        progressPercent = if (v.batteryVerified) v.batteryLevel / 100f else 0.1f,
                        onClick = { onNavigateToChatWithQuery(powerPrompt) }
                    )
                }
            }

            item {
                vitals?.let { v ->
                    val storageStatus = if (v.storageVerified) {
                        if (isHindi) "${RealDeviceDiagnostics.formatBytes(v.storageFreeBytes)} खाली (${v.storageUsedPercent}% उपयोग)"
                        else "${RealDeviceDiagnostics.formatBytes(v.storageFreeBytes)} Free (${v.storageUsedPercent}% used)"
                    } else strings.notCollectedLabel
                    val storageDetail = if (v.storageVerified) {
                        if (isHindi) "कुल: ${RealDeviceDiagnostics.formatBytes(v.storageTotalBytes)} • सिस्टम विभाजन सक्रिय"
                        else "Total: ${RealDeviceDiagnostics.formatBytes(v.storageTotalBytes)} • System partitions allocated"
                    } else strings.notCollectedLabel
                    val storagePrompt = if (isHindi) {
                        "डॉक्टर, मेरा स्टोरेज ${if (v.storageVerified) "${v.storageUsedPercent}% भरा है" else "स्थिति जांचनी है"}। सुरक्षित तरीके से जगह खाली करने का तरीका बताएं।"
                    } else {
                        "Doctor, my storage is ${if (v.storageVerified) "${v.storageUsedPercent}% used" else "needs checking"}. Guide me on how to free up space safely."
                    }
                    FuturisticCategoryCard(
                        title = strings.modStorageTitle,
                        status = storageStatus,
                        detail = storageDetail,
                        testKey = "storage",
                        icon = Icons.Default.Storage,
                        accentColor = if (v.storageVerified && v.storageUsedPercent > 85) CyberRed else CyberCyan,
                        progressPercent = if (v.storageVerified) v.storageUsedPercent / 100f else 0.1f,
                        onClick = { onNavigateToChatWithQuery(storagePrompt) }
                    )
                }
            }

            item {
                vitals?.let { v ->
                    val ramStatus = if (v.ramVerified) {
                        if (isHindi) "${RealDeviceDiagnostics.formatBytes(v.ramFreeBytes)} उपलब्ध (${v.ramUsedPercent}% उपयोग)"
                        else "${RealDeviceDiagnostics.formatBytes(v.ramFreeBytes)} Available (${v.ramUsedPercent}% used)"
                    } else strings.notCollectedLabel
                    val ramDetail = if (isHindi) {
                        "कम मेमोरी स्थिति: ${if (v.isLowMemory) "हाँ" else "नहीं"} • सत्यापित: ${if (v.ramVerified) "हाँ" else "नहीं"}"
                    } else {
                        "Low Memory Flag: ${v.isLowMemory} • Verified: ${v.ramVerified}"
                    }
                    val ramPrompt = if (isHindi) {
                        "डॉक्टर, मेरी रैम जांचें और फोन की गति बढ़ाने के सुरक्षित उपाय बताएं।"
                    } else {
                        "Doctor, check my RAM memory usage (${if (v.ramVerified) "${v.ramUsedPercent}% used" else "uncollected"}) and how to boost performance."
                    }
                    FuturisticCategoryCard(
                        title = strings.modRamTitle,
                        status = ramStatus,
                        detail = ramDetail,
                        testKey = "ram",
                        icon = Icons.Default.Memory,
                        accentColor = if (v.ramVerified && v.ramUsedPercent > 85) CyberAmber else CyberBlue,
                        progressPercent = if (v.ramVerified) v.ramUsedPercent / 100f else 0.1f,
                        onClick = { onNavigateToChatWithQuery(ramPrompt) }
                    )
                }
            }

            item {
                vitals?.let { v ->
                    val netDetail = if (isHindi) {
                        "सक्रिय सॉकेट टेलीमेट्री • डीएनएस पिंग परीक्षण उपलब्ध"
                    } else {
                        "Active socket telemetry • DNS latency ping available"
                    }
                    val netPrompt = if (isHindi) {
                        "डॉक्टर, मैं अपने इंटरनेट की गति जांचना और नेटवर्क समस्या ठीक करना चाहता हूँ।"
                    } else {
                        "Doctor, I want to test my internet latency and troubleshoot network connection."
                    }
                    FuturisticCategoryCard(
                        title = strings.modNetworkTitle,
                        status = "${v.networkType} • ${if (v.isInternetAvailable) strings.onlineConnected else strings.noConnection}",
                        detail = netDetail,
                        testKey = "network",
                        icon = Icons.Default.NetworkCheck,
                        accentColor = if (v.networkVerified && v.isInternetAvailable) CyberGreen else CyberRed,
                        progressPercent = if (v.networkVerified && v.isInternetAvailable) 1f else 0.2f,
                        onClick = { onNavigateToChatWithQuery(netPrompt) }
                    )
                }
            }

            item {
                vitals?.let { v ->
                    val timeoutStr = if (v.screenTimeoutSeconds >= 0) "${v.screenTimeoutSeconds}s" else strings.notCollectedLabel
                    val displayDetail = if (isHindi) {
                        "स्क्रीन समय समाप्ति: $timeoutStr • अपटाइम: ${String.format(java.util.Locale.US, "%.1f", v.uptimeHours)}h"
                    } else {
                        "Screen Timeout: $timeoutStr • Uptime: ${String.format(java.util.Locale.US, "%.1f", v.uptimeHours)}h"
                    }
                    val displayPrompt = if (isHindi) {
                        "डॉक्टर, स्क्रीन के रंग और डेड पिक्सेल जांचने तथा डिस्प्ले समय अनुकूलित करने में मदद करें।"
                    } else {
                        "Doctor, help me test screen colors for dead pixels and optimize display timeout."
                    }
                    FuturisticCategoryCard(
                        title = strings.modDisplayTitle,
                        status = if (v.displayVerified) "${v.displayResolution} @ ${v.refreshRate.toInt()}Hz" else strings.notCollectedLabel,
                        detail = displayDetail,
                        testKey = "display",
                        icon = Icons.Default.DisplaySettings,
                        accentColor = CyberPurple,
                        progressPercent = if (v.displayVerified) 0.9f else 0.1f,
                        onClick = { onNavigateToChatWithQuery(displayPrompt) }
                    )
                }
            }

            item {
                vitals?.let { v ->
                    val gamingStatus = when {
                        !v.temperatureVerified -> strings.notCollectedLabel
                        v.batteryTempC > 38f -> if (isHindi) "तापमान वृद्धि चेतावनी (${v.batteryTempC}°C)" else "Thermal Rise Alert (${v.batteryTempC}°C)"
                        else -> if (isHindi) "सामान्य तापमान (${v.batteryTempC}°C)" else "Optimal Temperature (${v.batteryTempC}°C)"
                    }
                    val gamingDetail = if (isHindi) {
                        "थर्मल: ${v.thermalStatus} • बैटरी सेवर: ${if (v.isPowerSaveMode) "चालू" else "बंद"} • एबीआई: ${v.cpuArchitecture}"
                    } else {
                        "Thermal: ${v.thermalStatus} • PowerSaver: ${if (v.isPowerSaveMode) "ON" else "OFF"} • ABI: ${v.cpuArchitecture}"
                    }
                    val gamingPrompt = if (isHindi) {
                        "डॉक्टर, गेमिंग के दौरान मेरा फोन गर्म होता है और एफपीएस कम हो जाते हैं। मुझे क्या सेटिंग करनी चाहिए?"
                    } else {
                        "Doctor, my phone heats up and drops FPS during gaming. What should I configure?"
                    }
                    FuturisticCategoryCard(
                        title = strings.modGamingTitle,
                        status = gamingStatus,
                        detail = gamingDetail,
                        testKey = "gaming",
                        icon = Icons.Default.Gamepad,
                        accentColor = if (v.temperatureVerified && v.batteryTempC > 39f) CyberAmber else CyberTeal,
                        progressPercent = if (v.temperatureVerified) ((v.batteryTempC - 20f) / 40f).coerceIn(0.1f, 1f) else 0.1f,
                        onClick = { onNavigateToChatWithQuery(gamingPrompt) }
                    )
                }
            }

            item {
                vitals?.let { v ->
                    if (v.activeCollectorCapabilities.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CyberCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("collector_capabilities_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = strings.liveCollectorTitle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.8.sp
                                )
                                v.activeCollectorCapabilities.forEach { cap ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${cap.name} (${cap.dataSource})",
                                            fontSize = 10.5.sp,
                                            color = CyberTextSecondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = cap.currentValueSummary,
                                            fontSize = 10.5.sp,
                                            color = if (cap.isAvailable) CyberGreen else CyberAmber,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (capabilityMatrix.isNotEmpty()) {
                item(key = "capability_matrix_header") {
                    CapabilityMatrixCard(
                        entries = capabilityMatrix,
                        expanded = matrixExpanded,
                        onToggleExpand = { matrixExpanded = !matrixExpanded },
                        strings = strings
                    )
                }
                if (matrixExpanded) {
                    val orderedCategories = CapabilityTierCategory.entries.sortedBy { it.order }
                    orderedCategories.forEach { category ->
                        val catEntries = capabilityMatrix.filter { it.category == category }
                        if (catEntries.isNotEmpty()) {
                            item(key = "cap_tier_${category.name}") {
                                CapabilityMatrixTierSection(
                                    category = category,
                                    catEntries = catEntries,
                                    strings = strings,
                                    onRunAction = { action -> viewModel.handleAction(context, action) }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun AutomaticAppUpdateCard(
    updateState: UpdateState,
    strings: LocalizedStrings,
    onCheckUpdates: () -> Unit,
    onOpenUpdateDialog: () -> Unit
) {
    val isHindi = strings.languageCode.equals("Hindi", ignoreCase = true)
    val hasUpdate = updateState is UpdateState.UpdateAvailable ||
        updateState is UpdateState.ReadyToInstall ||
        updateState is UpdateState.Downloading

    val statusText = when (updateState) {
        is UpdateState.Checking -> strings.updateChecking
        is UpdateState.UpdateAvailable -> strings.updateBannerTitle(updateState.manifest.versionName)
        is UpdateState.Downloading -> "${strings.updateDownloadingTitle} (${updateState.progressPercent}%)"
        is UpdateState.ReadyToInstall -> "${strings.updateInstallNowBtn}: v${updateState.manifest.versionName}"
        is UpdateState.NeedsInstallPermission -> strings.updateAllowPermissionBtn
        is UpdateState.UpToDate -> strings.updateUpToDate(updateState.currentVersionName, updateState.currentVersionCode.toInt())
        is UpdateState.Error -> updateState.message
        UpdateState.Idle -> "${strings.updateCurrentVersionLabel}: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (hasUpdate) CyberGreen.copy(alpha = 0.7f) else CyberCardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("automatic_app_update_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background((if (hasUpdate) CyberGreen else CyberCyan).copy(alpha = 0.15f))
                            .border(1.dp, if (hasUpdate) CyberGreen else CyberCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = strings.updateCheckCardTitle,
                            tint = if (hasUpdate) CyberGreen else CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = strings.updateCheckCardTitle,
                            color = CyberTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            color = CyberCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Button(
                    onClick = if (hasUpdate) onOpenUpdateDialog else onCheckUpdates,
                    enabled = updateState !is UpdateState.Checking,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasUpdate) CyberGreen else CyberSurfaceVariant,
                        contentColor = if (hasUpdate) Color(0xFF00220F) else CyberCyan
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag(if (hasUpdate) "card_open_update_btn" else "check_updates_btn")
                ) {
                    Text(
                        text = if (hasUpdate) strings.updateBannerAction else strings.updateCheckBtn,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusText,
                color = when {
                    hasUpdate -> CyberGreen
                    updateState is UpdateState.Error -> CyberRed
                    else -> CyberTextSecondary
                },
                fontSize = 11.sp
            )
            if (isHindi) {
                Text(
                    text = strings.updateDataPreservedNote,
                    color = CyberTextMuted,
                    fontSize = 9.5.sp
                )
            }
        }
    }
}

@Composable
fun FuturisticHealthGaugeCard(
    vitals: DeviceVitals,
    oemFlavor: String,
    strings: LocalizedStrings = AppStrings.English,
    onDeepScanClick: () -> Unit
) {
    val scoreColor = when {
        vitals.healthScore >= 80 -> CyberGreen
        vitals.healthScore >= 60 -> CyberAmber
        else -> CyberRed
    }

    val infiniteTransition = rememberInfiniteTransition(label = "gauge_glow")
    val ringSpin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gauge_spin"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Top Row: Device Name & OS Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${vitals.brand} ${vitals.model}".uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        color = CyberTextPrimary,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$oemFlavor • Android ${vitals.androidVersion} (API ${vitals.apiLevel})",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
                ) {
                    Text(
                        text = if (strings.languageCode.equals("Hindi", ignoreCase = true)) "सिस्टम सक्रिय" else "SYS OK",
                        color = scoreColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Center: Circular Arc Gauge & Telemetry readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Circular Gauge Canvas
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 8.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                        // Track Arc
                        drawArc(
                            color = Color(0xFF14243B),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Progress Arc
                        val sweep = (270f * (vitals.healthScore / 100f))
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(CyberBlue, CyberCyan, scoreColor)
                            ),
                            startAngle = 135f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${vitals.healthScore}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = strings.healthScoreLabel,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextMuted,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Metric Pills in column (never display unverified sentinel readings)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FuturisticMetricTag(
                        strings.metricBattery,
                        if (vitals.batteryVerified) "${vitals.batteryLevel}%" else strings.notCollectedLabel,
                        when {
                            !vitals.batteryVerified -> CyberAmber
                            vitals.batteryLevel < 20 -> CyberRed
                            else -> CyberGreen
                        }
                    )
                    FuturisticMetricTag(
                        strings.metricThermal,
                        if (vitals.temperatureVerified) "${vitals.batteryTempC}°C" else strings.notCollectedLabel,
                        if (vitals.temperatureVerified && vitals.batteryTempC > 40f) CyberRed else CyberCyan
                    )
                    FuturisticMetricTag(
                        strings.metricStorage,
                        if (vitals.storageVerified) "${vitals.storageUsedPercent}%" else strings.notCollectedLabel,
                        if (vitals.storageVerified && vitals.storageUsedPercent > 85) CyberRed else CyberTeal
                    )
                    FuturisticMetricTag(
                        strings.metricRamUsed,
                        if (vitals.ramVerified) "${vitals.ramUsedPercent}%" else strings.notCollectedLabel,
                        if (vitals.ramVerified && vitals.ramUsedPercent > 85) CyberAmber else CyberBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Deep AI Scan Button
            Button(
                onClick = onDeepScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deep_ai_scan_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan.copy(alpha = 0.15f),
                    contentColor = CyberCyan
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.startAiNeuralDiagnosis,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun FuturisticMetricTag(label: String, value: String, accentColor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CyberSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accentColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$label: ",
                color = CyberTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = value,
                color = CyberTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun HardwareTestTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    testTagSuffix: String = title,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clickable { onClick() }
            .testTag("hw_tile_${testTagSuffix}"),
        shape = RoundedCornerShape(14.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    color = CyberTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = CyberTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun FuturisticCategoryCard(
    title: String,
    status: String,
    detail: String,
    icon: ImageVector,
    accentColor: Color,
    progressPercent: Float,
    testKey: String = title.take(8).trim(),
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("category_${testKey}"),
        shape = RoundedCornerShape(16.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = CyberTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = status,
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mini progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFF091424), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressPercent.coerceIn(0.05f, 1f))
                        .height(4.dp)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = detail,
                color = CyberTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun FuturisticAutoFixBanner(
    strings: LocalizedStrings = AppStrings.English,
    onRunAutoFix: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRunAutoFix() }
            .testTag("autofix_banner")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(CyberCyan.copy(alpha = 0.15f))
                    .border(1.5.dp, CyberCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = strings.oneTapAutoFixTitle,
                    tint = CyberCyan,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = strings.oneTapAutoFixTitle,
                        color = CyberCyan,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = CyberGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = strings.autoBadge,
                            color = CyberGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = strings.oneTapAutoFixSubtitle,
                    color = CyberTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onRunAutoFix,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001B2E)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("run_autofix_banner_btn")
            ) {
                Text(
                    text = strings.runBtn,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun CapabilityMatrixCard(
    entries: List<CapabilityMatrixEntry>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    strings: LocalizedStrings
) {
    val autoCount = entries.count { it.category == CapabilityTierCategory.AUTO_EXECUTED }
    val permCount = entries.count { it.category == CapabilityTierCategory.PERMISSION_GATED }
    val panelCount = entries.count { it.category == CapabilityTierCategory.IN_APP_PANEL_OR_SETTINGS }
    val blockedCount = entries.count { it.category == CapabilityTierCategory.OS_SANDBOX_BLOCKED }
    val hwCount = entries.count { it.category == CapabilityTierCategory.HARDWARE_SERVICE_REQUIRED }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("capability_matrix_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable { onToggleExpand() }
                    .testTag("capability_matrix_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = strings.capabilityMatrixTitle,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = strings.capabilityMatrixTitle,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = strings.capabilityMatrixSubtitle,
                            fontSize = 9.5.sp,
                            color = CyberTextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) strings.hideDetails else strings.viewEvidence,
                    tint = CyberCyan
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "AUTO: $autoCount • PERM: $permCount • PANEL/SETTINGS: $panelCount • OS BLOCKED: $blockedCount • HW: $hwCount",
                color = CyberTextMuted,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun CapabilityMatrixTierSection(
    category: CapabilityTierCategory,
    catEntries: List<CapabilityMatrixEntry>,
    strings: LocalizedStrings,
    onRunAction: (DoctorAction) -> Unit
) {
    val isHindi = strings.languageCode.equals("Hindi", ignoreCase = true)
    val (catLabel, catColor) = when (category) {
        CapabilityTierCategory.AUTO_EXECUTED -> strings.capTierAutoLabel to CyberGreen
        CapabilityTierCategory.PERMISSION_GATED -> strings.capTierPermissionLabel to CyberAmber
        CapabilityTierCategory.IN_APP_PANEL_OR_SETTINGS -> strings.capTierPanelSettingsLabel to CyberCyan
        CapabilityTierCategory.OS_SANDBOX_BLOCKED -> strings.capTierOsBlockedLabel to CyberRed
        CapabilityTierCategory.HARDWARE_SERVICE_REQUIRED -> strings.capTierHardwareLabel to CyberPurple
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, catColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = catLabel,
                color = catColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            catEntries.forEach { entry ->
                val linkedAction = entry.guidedAlternativeActionId?.let { SafeRepairRegistry.findAction(it) }
                val pillText = when (category) {
                    CapabilityTierCategory.AUTO_EXECUTED -> if (entry.isCurrentlyExecutableOnDevice) "ACTIVE" else "UNAVAIL"
                    CapabilityTierCategory.PERMISSION_GATED -> if (entry.isCurrentlyExecutableOnDevice) "GRANTED" else "PENDING"
                    CapabilityTierCategory.IN_APP_PANEL_OR_SETTINGS -> "SETTINGS"
                    CapabilityTierCategory.OS_SANDBOX_BLOCKED -> "OS BLOCKED"
                    CapabilityTierCategory.HARDWARE_SERVICE_REQUIRED -> "SERVICE"
                }

                Surface(
                    color = CyberSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, catColor.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isHindi) entry.titleHi else entry.titleEn,
                                color = CyberTextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = catColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = pillText,
                                    color = catColor,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = entry.androidApiOrPermission,
                            color = CyberCyan,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isHindi) entry.statusDetailHi else entry.statusDetailEn,
                            color = CyberTextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                        if (linkedAction != null) {
                            OutlinedButton(
                                onClick = { onRunAction(linkedAction) },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, catColor.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("cap_matrix_action_${entry.id}")
                            ) {
                                Text(
                                    text = strings.runBtn,
                                    color = catColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
