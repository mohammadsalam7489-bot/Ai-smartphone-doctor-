package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import com.example.data.ai.LocalizedDiagnosticFormatter
import com.example.data.model.AutonomousRepairBundle
import com.example.data.model.DoctorAction
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalizedStrings
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBackground
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

@Composable
fun RepairApprovalDialog(
    action: DoctorAction,
    strings: LocalizedStrings = AppStrings.English,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isHindi = strings.languageCode.equals("Hindi", ignoreCase = true)
    var destructiveBackupConfirmed by remember(action.id) { mutableStateOf(!action.isDestructive) }
    val accentColor = if (action.isDestructive) CyberRed else CyberCyan

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (action.isDestructive) Icons.Default.Warning else Icons.Default.Security,
                        contentDescription = if (action.isDestructive) strings.approvalHighRiskTitle else strings.approvalSecurityTitle,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (action.isDestructive) strings.approvalHighRiskTitle else strings.approvalSecurityTitle,
                        color = if (action.isDestructive) CyberRed else CyberTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${action.level.code}: ${action.capability.badgeLabel} • ${action.riskClass.label.uppercase()}",
                        color = accentColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = action.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CyberTextPrimary
                )

                DetailRow(
                    label = strings.approvalScopeLabel,
                    content = action.whatWillChange,
                    icon = Icons.Default.Info,
                    tint = CyberCyan
                )

                DetailRow(
                    label = strings.approvalRationaleLabel,
                    content = action.whyRequired,
                    icon = Icons.Default.Warning,
                    tint = CyberAmber
                )

                if (!action.requiredPermission.isNullOrBlank()) {
                    DetailRow(
                        label = strings.approvalPermissionLabel,
                        content = action.requiredPermission,
                        icon = Icons.Default.Security,
                        tint = CyberGreen
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val riskColor = if (action.isDestructive) CyberRed else CyberGreen
                    Surface(
                        color = riskColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, riskColor.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${strings.approvalRiskPrefix}${action.riskLevel.uppercase()}",
                            color = riskColor,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = CyberSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (action.isReversible) strings.approvalReversibleYes else strings.approvalReversibleNo,
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (action.isDestructive) {
                    Surface(
                        color = CyberRed.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("destructive_backup_warning")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            val defaultWarn = if (isHindi) {
                                "चेतावनी: आगे बढ़ने से पहले अपनी चैट, लॉगिन, फ़ोटो और महत्वपूर्ण फ़ाइलों का बैकअप अवश्य लें। डॉक्टर बिना अनुमति के डेटा नहीं हटाता।"
                            } else {
                                "WARNING: Back up chats, logins, photos, and important files before proceeding. Doctor never executes destructive deletions automatically."
                            }
                            Text(
                                text = action.backupGuidance ?: defaultWarn,
                                fontSize = 11.sp,
                                color = CyberTextPrimary,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = destructiveBackupConfirmed,
                                    onCheckedChange = { destructiveBackupConfirmed = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = CyberRed,
                                        uncheckedColor = CyberAmber
                                    ),
                                    modifier = Modifier.testTag("destructive_confirm_checkbox")
                                )
                                Text(
                                    text = strings.approvalConfirmBackup,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberAmber
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = CyberSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val guaranteeText = if (isHindi) {
                            "🔒 एंड्रॉइड सुरक्षा गारंटी: डॉक्टर कभी भी सिस्टम सुरक्षा का उल्लंघन नहीं करता और न ही कोई छिपा हुआ बदलाव करता है। ${action.capability.explanation}"
                        } else {
                            "🔒 Android Security Guarantee: Doctor never bypasses OS security or performs hidden changes. ${action.capability.explanation}"
                        }
                        Text(
                            text = guaranteeText,
                            fontSize = 11.sp,
                            color = CyberTextSecondary,
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = destructiveBackupConfirmed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (action.isDestructive) CyberRed else CyberCyan,
                    contentColor = if (action.isDestructive) Color.White else Color(0xFF001B2E)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_repair_approval_btn")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (action.isDestructive) strings.approvalConfirmOpenGuide else strings.approvalProceed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("cancel_repair_approval_btn")
            ) {
                Text(strings.approvalCancel, color = CyberTextPrimary, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun RepairVerificationCard(
    action: DoctorAction,
    strings: LocalizedStrings = AppStrings.English,
    onVerifiedSuccess: () -> Unit,
    onVerifiedFailed: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("repair_verification_card"),
        shape = RoundedCornerShape(12.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.verifyTelemetryTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyberCyan
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.verifyQuestion(action.title, action.verificationMethod),
                fontSize = 12.sp,
                color = CyberTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onVerifiedSuccess,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color(0xFF00220F)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("verify_success_btn")
                ) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.verifyFixedBtn, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onVerifiedFailed,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("verify_failed_btn")
                ) {
                    Icon(Icons.Default.ThumbDown, contentDescription = null, tint = CyberRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.verifyStillExistsBtn, color = CyberRed, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AutonomousBatchApprovalDialog(
    bundle: AutonomousRepairBundle,
    strings: LocalizedStrings = AppStrings.English,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isHindi = strings.languageCode.equals("Hindi", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CyberGreen.copy(alpha = 0.15f))
                        .border(1.dp, CyberGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = strings.autonomousBatchTitle,
                        tint = CyberGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = strings.autonomousBatchTitle,
                        color = CyberTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = bundle.problemTitle,
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = strings.autonomousBatchSubtitle,
                    fontSize = 11.5.sp,
                    color = CyberTextSecondary,
                    lineHeight = 16.sp
                )

                bundle.executableAutoSteps.forEachIndexed { idx, step ->
                    val localized = LocalizedDiagnosticFormatter.localizeAction(step.action, strings.languageCode)
                    Surface(
                        color = CyberSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
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
                                    text = "${idx + 1}. ${localized.title}",
                                    color = CyberGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = CyberGreen.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = localized.capabilityBadge,
                                        color = CyberGreen,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${strings.approvalScopeLabel} ${step.action.whatWillChange}",
                                color = CyberTextSecondary,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val permText = step.action.requiredPermission
                                    ?: if (isHindi) "सामान्य ऐप-स्तर API" else "Standard App API"
                                Text(
                                    text = permText,
                                    color = CyberTextMuted,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (step.action.isReversible) strings.approvalReversibleYes else strings.approvalReversibleNo,
                                    color = CyberCyan,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberGreen,
                    contentColor = Color(0xFF00220F)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_batch_autonomous_btn")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.approvalProceed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("cancel_batch_autonomous_btn")
            ) {
                Text(strings.approvalCancel, color = CyberTextPrimary, fontSize = 12.sp)
            }
        }
    )
}

@Composable
private fun DetailRow(
    label: String,
    content: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = tint
            )
            Text(
                text = content,
                fontSize = 11.sp,
                color = CyberTextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}
