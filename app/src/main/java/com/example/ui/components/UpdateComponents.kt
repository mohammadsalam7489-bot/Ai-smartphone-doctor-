package com.example.ui.components

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.update.AppUpdateManager
import com.example.data.update.UpdateState
import com.example.ui.i18n.LocalizedStrings
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary

/**
 * Compact segmented language toggle switch pill ([ EN | हिं ]).
 * Default is English; switching immediately updates the entire app UI.
 */
@Composable
fun LanguageToggleSwitch(
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHindi = currentLanguage.equals("Hindi", ignoreCase = true)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CyberSurfaceVariant,
        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
        modifier = modifier.testTag("language_toggle_btn")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!isHindi) CyberCyan else Color.Transparent)
                    .clickable { onSelectLanguage("English") }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("lang_option_en"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (!isHindi) Color(0xFF001B2E) else CyberTextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isHindi) CyberCyan else Color.Transparent)
                    .clickable { onSelectLanguage("Hindi") }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("lang_option_hi"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "हिन्दी",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHindi) Color(0xFF001B2E) else CyberTextSecondary
                )
            }
        }
    }
}

@Composable
fun UpdateAvailableBanner(
    updateState: UpdateState,
    strings: LocalizedStrings,
    onOpenDialog: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val versionName = when (updateState) {
        is UpdateState.UpdateAvailable -> updateState.manifest.versionName
        is UpdateState.ReadyToInstall -> updateState.manifest.versionName
        is UpdateState.Downloading -> "${updateState.manifest.versionName} (${updateState.progressPercent}%)"
        else -> return
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CyberCard,
        border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onOpenDialog() }
            .testTag("update_available_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(CyberGreen.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = strings.updateAvailableTitle,
                        tint = CyberGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = strings.updateBannerTitle(versionName),
                        color = CyberTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.updateDataPreservedNote,
                        color = CyberGreen,
                        fontSize = 9.5.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onOpenDialog,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberGreen,
                        contentColor = Color(0xFF00220F)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("banner_update_btn")
                ) {
                    Text(
                        text = strings.updateBannerAction,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                IconButton(
                    onClick = onDismissBanner,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.updateCloseBtn,
                        tint = CyberTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog(
    updateState: UpdateState,
    strings: LocalizedStrings,
    onStartDownload: () -> Unit,
    onInstallNow: (Context) -> Unit,
    onPermissionResult: (Context) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isHindi = strings.languageCode.equals("Hindi", ignoreCase = true)

    val installPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onPermissionResult(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = {
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
                        imageVector = when (updateState) {
                            is UpdateState.Error -> Icons.Default.ErrorOutline
                            is UpdateState.ReadyToInstall -> Icons.Default.InstallMobile
                            is UpdateState.UpToDate -> Icons.Default.CheckCircle
                            else -> Icons.Default.SystemUpdate
                        },
                        contentDescription = null,
                        tint = if (updateState is UpdateState.Error) CyberRed else CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = strings.updateAvailableTitle,
                    color = CyberTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("app_update_dialog_content"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (updateState) {
                    is UpdateState.Checking -> {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = CyberCyan,
                            trackColor = CyberSurfaceVariant
                        )
                        Text(
                            text = strings.updateChecking,
                            color = CyberTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    is UpdateState.UpToDate -> {
                        Text(
                            text = strings.updateUpToDate(
                                updateState.currentVersionName,
                                updateState.currentVersionCode.toInt()
                            ),
                            color = CyberGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    is UpdateState.UpdateAvailable -> {
                        val m = updateState.manifest
                        Surface(
                            color = CyberSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyberCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${strings.updateCurrentVersionLabel}:",
                                        color = CyberTextMuted,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "v${updateState.currentVersionName} (${updateState.currentVersionCode})",
                                        color = CyberTextPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${strings.updateNewVersionLabel}:",
                                        color = CyberCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "v${m.versionName} (${m.versionCode})" +
                                            if (m.fileSizeBytes > 0) " • ${RealDeviceDiagnostics.formatBytes(m.fileSizeBytes)}" else "",
                                        color = CyberGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (m.publishedAt.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isHindi) "रिलीज़ दिनांक:" else "Release Date:",
                                            color = CyberTextMuted,
                                            fontSize = 10.5.sp
                                        )
                                        Text(
                                            text = m.publishedAt.take(10),
                                            color = CyberTextPrimary,
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = CyberCardBorder)
                        Text(
                            text = strings.updateReleaseNotesLabel,
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isHindi && m.releaseNotesHi.isNotBlank()) m.releaseNotesHi else m.releaseNotes,
                            color = CyberTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        if (m.apkUrl.isNotBlank()) {
                            Text(
                                text = "APK: ${m.apkUrl}",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Surface(
                            color = CyberGreen.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = strings.updateDataPreservedNote,
                                color = CyberGreen,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            text = strings.updateDownloadingTitle,
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        LinearProgressIndicator(
                            progress = { (updateState.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CyberCyan,
                            trackColor = CyberSurfaceVariant
                        )
                        val downloadedTxt = RealDeviceDiagnostics.formatBytes(updateState.downloadedBytes)
                        val totalTxt = if (updateState.totalBytes > 0L) {
                            RealDeviceDiagnostics.formatBytes(updateState.totalBytes)
                        } else "..."
                        Text(
                            text = "${updateState.progressPercent}% ($downloadedTxt / $totalTxt)",
                            color = CyberTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    is UpdateState.ReadyToInstall -> {
                        Text(
                            text = "v${updateState.manifest.versionName} (${updateState.manifest.versionCode})",
                            color = CyberGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = strings.updateDataPreservedNote,
                            color = CyberTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    is UpdateState.NeedsInstallPermission -> {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = CyberAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.updatePermissionExplain,
                                color = CyberTextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    is UpdateState.Error -> {
                        Text(
                            text = updateState.message,
                            color = CyberRed,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    UpdateState.Idle -> {}
                }
            }
        },
        confirmButton = {
            when (updateState) {
                is UpdateState.UpdateAvailable -> {
                    Button(
                        onClick = onStartDownload,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = Color(0xFF001B2E)
                        ),
                        modifier = Modifier.testTag("dialog_update_now_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.updateNowBtn, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                is UpdateState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstallNow(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberGreen,
                            contentColor = Color(0xFF00220F)
                        ),
                        modifier = Modifier.testTag("dialog_install_now_btn")
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.updateInstallNowBtn, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                is UpdateState.NeedsInstallPermission -> {
                    Button(
                        onClick = {
                            val intent = AppUpdateManager.buildUnknownSourcesSettingsIntent(context)
                            installPermLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberAmber,
                            contentColor = Color(0xFF1F1200)
                        ),
                        modifier = Modifier.testTag("dialog_allow_install_perm_btn")
                    ) {
                        Text(strings.updateAllowPermissionBtn, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }

                is UpdateState.Error -> {
                    if (updateState.canRetry) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = Color(0xFF001B2E)
                            ),
                            modifier = Modifier.testTag("dialog_retry_update_btn")
                        ) {
                            Text(strings.updateRetryBtn, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                else -> {}
            }
        },
        dismissButton = {
            if (updateState !is UpdateState.Downloading) {
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, CyberCardBorder)
                ) {
                    Text(
                        text = if (updateState is UpdateState.UpdateAvailable) strings.updateLaterBtn else strings.updateCloseBtn,
                        color = CyberTextPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    )
}
