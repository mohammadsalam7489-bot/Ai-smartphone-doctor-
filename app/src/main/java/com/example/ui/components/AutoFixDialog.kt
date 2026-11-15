package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DoctorAction
import com.example.data.repair.AutoFixItem
import com.example.data.repair.AutoFixReport
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalizedStrings
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary

@Composable
fun AutoFixDialog(
    report: AutoFixReport?,
    isScanning: Boolean,
    strings: LocalizedStrings = AppStrings.English,
    onDismiss: () -> Unit,
    onExecuteRestrictedAction: (DoctorAction) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("autofix_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = CyberBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f))
                                .border(1.dp, CyberCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = strings.autoFixDialogTitle,
                                color = CyberTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = strings.autoFixDialogSubtitle,
                                fontSize = 9.sp,
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = strings.updateCloseBtn, tint = CyberTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CyberCardBorder)
                Spacer(modifier = Modifier.height(14.dp))

                if (isScanning || report == null) {
                    // Scanning State
                    ScanningStateView(strings = strings)
                } else {
                    // Results View
                    ResultsStateView(
                        report = report,
                        strings = strings,
                        onDismiss = onDismiss,
                        onExecuteRestrictedAction = onExecuteRestrictedAction
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanningStateView(strings: LocalizedStrings) {
    val isHindi = strings.languageCode.equals("Hindi", ignoreCase = true)
    val infiniteTransition = rememberInfiniteTransition(label = "scan_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_spin"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberCyan,
                    strokeWidth = 3.dp
                )
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier
                        .size(48.dp)
                        .rotate(rotation)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = strings.autoFixScanningTitle,
                color = CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isHindi) {
                    ScanStepItem("1. आंतरिक डायग्नोस्टिक कैश और सॉकेट जाँच", true)
                    ScanStepItem("2. नेटवर्क रेडियो और डीएनएस परीक्षण", true)
                    ScanStepItem("3. हीप मेमोरी और रनटाइम रैम नियंत्रण", true)
                    ScanStepItem("4. तृतीय-पक्ष ऐप कैश निरीक्षण", false)
                    ScanStepItem("5. थर्मल और बैटरी प्रबंधन नीतियां", false)
                } else {
                    ScanStepItem("1. Internal Diagnostic Cache & Sockets", true)
                    ScanStepItem("2. Network Radio & DNS Handshake", true)
                    ScanStepItem("3. Heap Memory & Runtime GC Handles", true)
                    ScanStepItem("4. Third-Party App Cache Check", false)
                    ScanStepItem("5. Thermal & Battery Throttling Policies", false)
                }
            }
        }
    }
}

@Composable
private fun ScanStepItem(label: String, isComplete: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (isComplete) CyberGreen else CyberCyan, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = CyberTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ResultsStateView(
    report: AutoFixReport,
    strings: LocalizedStrings,
    onDismiss: () -> Unit,
    onExecuteRestrictedAction: (DoctorAction) -> Unit
) {
    val isHindi = strings.languageCode.equals("Hindi", ignoreCase = true)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Honest Before / After Telemetry Banner
        item {
            val scoreDelta = report.afterScore - report.initialScore
            val deltaText = when {
                scoreDelta > 0 -> "+$scoreDelta"
                scoreDelta < 0 -> "$scoreDelta"
                else -> if (isHindi) "मापित" else "MEASURED"
            }
            val deltaColor = if (scoreDelta > 0) CyberGreen else CyberCyan

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CyberCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.autoFixBeforeAfterTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${report.initialScore}",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "  ➜  ",
                                    fontSize = 18.sp,
                                    color = CyberCyan
                                )
                                Text(
                                    text = "${report.afterScore}/100",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (report.afterScore >= 80) CyberGreen else CyberAmber,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Surface(
                            color = deltaColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, deltaColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = deltaText,
                                color = deltaColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = CyberCardBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${strings.beforeLabel} ${report.beforeSummary}",
                        fontSize = 10.5.sp,
                        color = CyberTextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${strings.afterLabel} ${report.afterSummary}",
                        fontSize = 10.5.sp,
                        color = CyberTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    val cacheLabel = if (isHindi) {
                        "साफ किया गया कैश (ऐप सैंडबॉक्स): "
                    } else {
                        "Cache Purged (Own App Sandbox): "
                    }
                    Text(
                        text = "$cacheLabel${com.example.data.diagnostics.RealDeviceDiagnostics.formatBytes(report.bytesCleared)}",
                        fontSize = 10.5.sp,
                        color = CyberGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Section 1: Automatically Executed Real API Actions
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CyberGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.autoFixExecutedHeader(report.autoFixedItems.size),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }

        items(report.autoFixedItems, key = { it.id }) { item ->
            AutoFixedCard(item)
        }

        // Section 2: Restricted Issues (Requiring User Approval or System Settings)
        if (report.restrictedItems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = CyberAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.autoFixRestrictedHeader(report.restrictedItems.size),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            items(report.restrictedItems, key = { it.id }) { item ->
                RestrictedIssueCard(
                    item = item,
                    strings = strings,
                    onActionClick = {
                        item.action?.let { onExecuteRestrictedAction(it) }
                    }
                )
            }
        }

        // Done button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001B2E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("autofix_done_button")
            ) {
                Text(
                    text = strings.autoFixFinishBtn,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AutoFixedCard(item: AutoFixItem) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(CyberGreen.copy(alpha = 0.15f))
                    .border(1.dp, CyberGreen.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CyberGreen,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        color = CyberTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = CyberSurfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.capability.badgeLabel,
                            color = CyberCyan,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.statusSummary,
                    color = CyberTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun RestrictedIssueCard(
    item: AutoFixItem,
    strings: LocalizedStrings,
    onActionClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberAmber.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CyberAmber.copy(alpha = 0.15f))
                        .border(1.dp, CyberAmber.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = CyberAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = CyberTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.statusSummary,
                        color = CyberAmber,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Restriction explanation box
            item.restrictionReason?.let { reason ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = reason,
                            color = CyberTextSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1-Tap Action Button to Jump to Settings
            item.action?.let {
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001B2E)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apply_restricted_fix_${item.id}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.autoFixApply1TapBtn,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Expandable Step-by-Step Instructions
            if (item.solutionGuide.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) strings.autoFixHideGuide else strings.autoFixViewGuide,
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.solutionGuide.forEachIndexed { idx, step ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "${idx + 1}.",
                                    color = CyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = step,
                                    color = CyberTextPrimary,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
