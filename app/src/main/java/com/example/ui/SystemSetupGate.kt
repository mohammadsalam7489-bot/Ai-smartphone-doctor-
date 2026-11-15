package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.update.AppUpdateManager
import com.example.ui.components.LanguageToggleSwitch
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary

@Composable
fun SystemSetupGate(
    currentLanguage: String = "English",
    onSelectLanguage: (String) -> Unit = {},
    onSetupCompleted: () -> Unit
) {
    val context = LocalContext.current
    val strings = AppStrings.forLanguage(currentLanguage)

    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var canInstallUpdates by remember {
        mutableStateOf(AppUpdateManager.canRequestPackageInstalls(context))
    }
    var canWriteSettings by remember {
        mutableStateOf(runCatching { Settings.System.canWrite(context) }.getOrDefault(false))
    }
    var agreedToTerms by remember { mutableStateOf(false) }
    var isAuditCompleted by remember { mutableStateOf(false) }
    var auditSummary by remember { mutableStateOf("...") }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    val installPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        canInstallUpdates = AppUpdateManager.canRequestPackageInstalls(context)
    }

    val writeSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        canWriteSettings = runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
    }

    LaunchedEffect(currentLanguage) {
        val vitals = RealDeviceDiagnostics.getDeviceVitals(context)
        val ramFree = RealDeviceDiagnostics.formatBytes(vitals.ramFreeBytes)
        val storeFree = RealDeviceDiagnostics.formatBytes(vitals.storageFreeBytes)
        auditSummary = if (currentLanguage.equals("Hindi", ignoreCase = true)) {
            "स्वास्थ्य ${vitals.healthScore}/100 • खाली रैम $ramFree • खाली स्टोरेज $storeFree"
        } else {
            "Vitals Verified: Health ${vitals.healthScore}/100 • RAM Free $ramFree • Storage Free $storeFree"
        }
        isAuditCompleted = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F223D),
                        Color(0xFF091424),
                        CyberBackground
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("system_setup_gate"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                LanguageToggleSwitch(
                    currentLanguage = currentLanguage,
                    onSelectLanguage = onSelectLanguage
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Header Shield Icon
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = CyberSurface,
                border = androidx.compose.foundation.BorderStroke(2.dp, CyberCyan)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = strings.setupTitle,
                color = CyberCyan,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = strings.setupSubtitle,
                color = CyberTextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Setup Checklist Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CyberCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SetupCheckItem(
                        icon = Icons.Default.NotificationsActive,
                        title = strings.setupNotifTitle,
                        status = if (hasNotifPermission) strings.setupNotifDescGranted else strings.setupNotifDescPending,
                        isDone = hasNotifPermission,
                        actionLabel = if (!hasNotifPermission) strings.setupGrantBtn else strings.setupActiveBtn,
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )

                    SetupCheckItem(
                        icon = Icons.Default.SystemUpdate,
                        title = strings.setupInstallTitle,
                        status = if (canInstallUpdates) strings.setupInstallDescGranted else strings.setupInstallDescPending,
                        isDone = canInstallUpdates,
                        actionLabel = if (!canInstallUpdates) strings.setupGrantBtn else strings.setupActiveBtn,
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallUpdates) {
                                installPermLauncher.launch(AppUpdateManager.buildUnknownSourcesSettingsIntent(context))
                            }
                        }
                    )

                    SetupCheckItem(
                        icon = Icons.Default.Memory,
                        title = strings.setupAuditTitle,
                        status = auditSummary,
                        isDone = isAuditCompleted
                    )

                    SetupCheckItem(
                        icon = Icons.Default.Security,
                        title = strings.setupWriteTitle,
                        status = if (canWriteSettings) strings.setupWriteDescGranted else strings.setupWriteDescPending,
                        isDone = canWriteSettings,
                        actionLabel = if (!canWriteSettings) strings.setupGrantBtn else strings.setupActiveBtn,
                        onAction = {
                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            writeSettingsLauncher.launch(intent)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Terms & Conditions Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = CyberSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { agreedToTerms = !agreedToTerms },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = agreedToTerms,
                            onCheckedChange = { agreedToTerms = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CyberCyan,
                                uncheckedColor = CyberTextSecondary,
                                checkmarkColor = CyberBackground
                            ),
                            modifier = Modifier.testTag("terms_checkbox")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.setupAgreeTitle,
                                color = CyberTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.setupAgreeSub,
                                color = CyberTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF091424),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = strings.setupTermsHeader,
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = strings.setupTermsBody,
                                color = CyberTextSecondary,
                                fontSize = 9.5.sp,
                                lineHeight = 13.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Proceed Button
            Button(
                onClick = { if (agreedToTerms) onSetupCompleted() },
                enabled = agreedToTerms,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("proceed_to_doctor_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    disabledContainerColor = CyberSurface
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (agreedToTerms) CyberBackground else CyberTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.setupLaunchBtn,
                        color = if (agreedToTerms) CyberBackground else CyberTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCheckItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    status: String,
    isDone: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = if (isDone) CyberGreen.copy(alpha = 0.2f) else CyberSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isDone) CyberGreen else CyberCardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isDone) CyberGreen else CyberTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
                    text = status,
                    color = if (isDone) CyberGreen else CyberTextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
