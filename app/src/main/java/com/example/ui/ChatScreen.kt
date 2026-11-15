package com.example.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ai.LocalizedDiagnosticFormatter
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.model.AiServiceStatus
import com.example.data.model.AutomationCapability
import com.example.data.model.AutonomousRepairBundle
import com.example.data.model.AutonomousRepairStep
import com.example.data.model.AutonomousStepStatus
import com.example.data.model.BeforeAfterComparison
import com.example.data.model.ChatMessage
import com.example.data.model.DeviceVitals
import com.example.data.model.DoctorAction
import com.example.data.model.RepairPlan
import com.example.data.model.SenderType
import com.example.data.model.VerificationOutcome
import com.example.ui.components.AudioTestDialog
import com.example.ui.components.AutonomousBatchApprovalDialog
import com.example.ui.components.LanguageToggleSwitch
import com.example.ui.components.NetworkPingDialog
import com.example.ui.components.RepairApprovalDialog
import com.example.ui.components.RepairVerificationCard
import com.example.ui.components.ScreenTestDialog
import com.example.ui.components.VibrationTestDialog
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: DoctorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val vitals by viewModel.vitals.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val activeTest by viewModel.activeTest.collectAsStateWithLifecycle()
    val pingResult by viewModel.pingResult.collectAsStateWithLifecycle()
    val isPingRunning by viewModel.isPingRunning.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.actionFeedback.collectAsStateWithLifecycle()
    val attachedScreenshot by viewModel.attachedScreenshot.collectAsStateWithLifecycle()
    val pendingApprovalAction by viewModel.pendingApprovalAction.collectAsStateWithLifecycle()
    val pendingAutonomousConfirmation by viewModel.pendingAutonomousPlanConfirmation.collectAsStateWithLifecycle()

    val showAutoFixDialog by viewModel.showAutoFixDialog.collectAsStateWithLifecycle()
    val isAutoFixRunning by viewModel.isAutoFixRunning.collectAsStateWithLifecycle()
    val autoFixReport by viewModel.autoFixReport.collectAsStateWithLifecycle()
    val currentlySpeakingMessageId by viewModel.currentlySpeakingMessageId.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val aiServiceStatus by viewModel.aiServiceStatus.collectAsStateWithLifecycle()
    val aiStatusDetail by viewModel.aiStatusDetail.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val strings = AppStrings.forLanguage(currentLanguage)

    if (showAutoFixDialog) {
        com.example.ui.components.AutoFixDialog(
            report = autoFixReport,
            isScanning = isAutoFixRunning,
            strings = strings,
            onDismiss = { viewModel.closeAutoFixDialog() },
            onExecuteRestrictedAction = { action ->
                viewModel.handleAction(context, action)
            }
        )
    }

    var inputText by remember { mutableStateOf("") }
    var showVitalsDetails by remember { mutableStateOf(false) }

    // Voice recognition launcher
    val voiceRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.sendUserMessage(spokenText, isVoiceInput = true)
            }
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onScreenshotSelected(context, uri)
        }
    }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    LaunchedEffect(messages.size, isAnalyzing) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Repair Approval Dialog
    pendingApprovalAction?.let { action ->
        RepairApprovalDialog(
            action = action,
            strings = strings,
            onConfirm = { viewModel.confirmApprovalAndExecute(context) },
            onDismiss = { viewModel.cancelApproval() }
        )
    }

    // Autonomous Batch Repair Confirmation Dialog
    pendingAutonomousConfirmation?.let { (_, bundle) ->
        AutonomousBatchApprovalDialog(
            bundle = bundle,
            strings = strings,
            onConfirm = { viewModel.confirmAndExecuteAutonomousPlan(context) },
            onDismiss = { viewModel.cancelAutonomousPlanConfirmation() }
        )
    }

    // Hardware Test Dialogs
    val isHindi = currentLanguage.equals("Hindi", ignoreCase = true)
    when (activeTest) {
        HardwareTestDialogState.AUDIO -> {
            AudioTestDialog(
                isHindi = isHindi,
                onDismiss = { viewModel.closeTestDialog() },
                onPlayAgain = { viewModel.playAudioTestAgain() },
                onFeedback = { passed ->
                    viewModel.closeTestDialog()
                    val feedback = if (passed) {
                        if (isHindi) "स्पीकर ऑडियो परीक्षण सफल रहा! आवाज़ साफ सुनाई दे रही है।"
                        else "Speaker audio test passed! Sound is clean and audible."
                    } else {
                        if (isHindi) "स्पीकर ऑडियो परीक्षण विफल रहा। आवाज़ साफ नहीं आ रही।"
                        else "Speaker audio test failed. Sound was distorted or inaudible."
                    }
                    viewModel.sendUserMessage(feedback)
                }
            )
        }
        HardwareTestDialogState.VIBRATION -> {
            VibrationTestDialog(
                isHindi = isHindi,
                onDismiss = { viewModel.closeTestDialog() },
                onTriggerAgain = { viewModel.triggerVibrationTestAgain(context) },
                onFeedback = { passed ->
                    viewModel.closeTestDialog()
                    val feedback = if (passed) {
                        if (isHindi) "वाइब्रेशन मोटर परीक्षण सफल रहा! फोन वाइब्रेट हुआ।"
                        else "Vibration motor test passed! Phone haptic feedback is working."
                    } else {
                        if (isHindi) "वाइब्रेशन मोटर परीक्षण विफल रहा। फोन वाइब्रेट नहीं हुआ।"
                        else "Vibration motor test failed. Phone did not vibrate."
                    }
                    viewModel.sendUserMessage(feedback)
                }
            )
        }
        HardwareTestDialogState.SCREEN -> {
            ScreenTestDialog(
                isHindi = isHindi,
                onDismiss = { viewModel.closeTestDialog() },
                onFinish = {
                    viewModel.closeTestDialog()
                    viewModel.sendUserMessage(
                        if (isHindi) "स्क्रीन डेड पिक्सेल परीक्षण पूरा हो गया।"
                        else "Screen dead pixel test complete. Display colors inspected."
                    )
                }
            )
        }
        HardwareTestDialogState.NETWORK -> {
            NetworkPingDialog(
                pingResult = pingResult,
                isLoading = isPingRunning,
                isHindi = isHindi,
                onDismiss = { viewModel.closeTestDialog() },
                onRetest = { viewModel.runPingTest() }
            )
        }
        HardwareTestDialogState.NONE -> {}
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CyberBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FuturisticDoctorTopBar(
                vitals = vitals,
                vitalsExpanded = showVitalsDetails,
                currentLanguage = currentLanguage,
                strings = strings,
                onSelectLanguage = { lang -> viewModel.setLanguage(lang) },
                onToggleVitals = { showVitalsDetails = !showVitalsDetails },
                onResetChat = { viewModel.resetChat() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            if (!aiServiceStatus.isCloudAi) {
                val hasRetryableQuery = messages.any { it.sender == SenderType.DOCTOR && it.canRetryCloudAi && !it.retryPrompt.isNullOrBlank() }
                AiEngineStatusBanner(
                    status = aiServiceStatus,
                    detail = aiStatusDetail,
                    strings = strings,
                    canRetry = hasRetryableQuery && !isAnalyzing,
                    onRetry = { viewModel.retryLastCloudAiQuery() }
                )
            }

            AnimatedVisibility(visible = showVitalsDetails && vitals != null) {
                vitals?.let { currentVitals ->
                    FuturisticVitalsHeaderStrip(
                        vitals = currentVitals,
                        expanded = true,
                        strings = strings,
                        onToggleExpand = { showVitalsDetails = false }
                    )
                }
            }

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isMessageSpeaking = isSpeaking && currentlySpeakingMessageId == message.id
                    FuturisticMessageBubble(
                        message = message,
                        strings = strings,
                        isSpeaking = isMessageSpeaking,
                        isAnalyzing = isAnalyzing,
                        onToggleSpeech = { viewModel.toggleSpeech(message) },
                        onRetryCloudAi = if (message.canRetryCloudAi && !message.retryPrompt.isNullOrBlank()) {
                            { viewModel.retryMessage(message.id) }
                        } else null,
                        onRequestAutonomousExecution = if (message.autonomousBundle != null) {
                            { viewModel.requestAutonomousExecution(message.id) }
                        } else null,
                        onActionClick = { action ->
                            viewModel.handleAction(context, action)
                        },
                        onVerifySuccess = { action ->
                            viewModel.recordVerification(action, true)
                        },
                        onVerifyFailed = { action ->
                            viewModel.recordVerification(action, false)
                        }
                    )
                }

                // Clean starter grid on fresh screen (vanishes as soon as user starts chatting)
                if (messages.size <= 1 && !isAnalyzing) {
                    item(key = "quick_start_section") {
                        QuickStartStarterCard(
                            strings = strings,
                            onRunAutoFix = { viewModel.startAutoFixScanAndRepair() },
                            onQuickPrompt = { prompt -> viewModel.sendUserMessage(prompt) }
                        )
                    }
                }

                if (isAnalyzing) {
                    item {
                        FuturisticDoctorThinkingIndicator(strings = strings)
                    }
                }
            }

            // Attached Screenshot Preview Bar
            attachedScreenshot?.let { bmp ->
                Surface(
                    color = CyberCard,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = strings.attachScreenshot,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.screenshotAttachedTitle,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = strings.screenshotPrivacyNote,
                                fontSize = 10.sp,
                                color = CyberAmber
                            )
                        }
                        IconButton(onClick = { viewModel.removeAttachedScreenshot() }) {
                            Icon(Icons.Default.Close, contentDescription = strings.updateCloseBtn, tint = CyberRed)
                        }
                    }
                }
            }

            // Clean Input Bar with toggleable Attach menu (+ Screenshot / Vitals)
            FuturisticChatInputBar(
                text = inputText,
                hasAttachment = attachedScreenshot != null,
                strings = strings,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() || attachedScreenshot != null) {
                        viewModel.sendUserMessage(inputText)
                        inputText = ""
                    }
                },
                onAttachVitals = {
                    vitals?.let {
                        val prefix = if (isHindi) {
                            "डॉक्टर, कृपया मेरे फोन का लाइव टेलीमेट्री विवरण देखें:\n"
                        } else {
                            "Doctor, please check my current device vitals:\n"
                        }
                        viewModel.sendUserMessage(prefix + RealDeviceDiagnostics.formatVitalsSummary(it))
                    }
                },
                onPickScreenshot = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onVoiceInput = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isHindi) "hi-IN" else "en-US")
                        putExtra(
                            RecognizerIntent.EXTRA_PROMPT,
                            if (isHindi) "अपने फोन की समस्या बोलें..." else "Speak your phone problem..."
                        )
                    }
                    try {
                        voiceRecognitionLauncher.launch(intent)
                    } catch (_: Exception) {
                        viewModel.reportFeedback(
                            if (isHindi) "इस डिवाइस पर वॉइस इनपुट उपलब्ध नहीं है। कृपया समस्या टाइप करें।"
                            else "Voice recognition is not available on this device. Please type your problem."
                        )
                    }
                },
                isAnalyzing = isAnalyzing
            )
        }
    }
}

@Composable
fun AiEngineStatusBanner(
    status: AiServiceStatus,
    detail: String,
    strings: LocalizedStrings = AppStrings.English,
    canRetry: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    val isHindi = strings.languageCode.equals("Hindi", ignoreCase = true)
    val bannerColor = if (status.isCloudAi) CyberGreen else CyberAmber
    val badgeTitle = if (isHindi && !status.isCloudAi) "स्थानीय सत्यापित ज्ञानकोश मोड" else status.badgeText
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bannerColor.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, bannerColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("ai_status_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(bannerColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = badgeTitle,
                        color = bannerColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!status.isCloudAi && detail.isNotBlank()) {
                        Text(
                            text = detail,
                            color = CyberTextSecondary,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (!status.isCloudAi && onRetry != null) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onRetry,
                    enabled = canRetry,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("retry_cloud_ai_banner_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = strings.retryCloudAiBtn,
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.retryCloudAiBtn,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuturisticDoctorTopBar(
    vitals: DeviceVitals?,
    vitalsExpanded: Boolean,
    currentLanguage: String,
    strings: LocalizedStrings,
    onSelectLanguage: (String) -> Unit,
    onToggleVitals: () -> Unit,
    onResetChat: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = CyberSurface
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.doctor_badge_transparent),
                    contentDescription = strings.appTitle,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, CyberCyan, CircleShape)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = strings.appTitle,
                            color = CyberCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(CyberGreen, CircleShape)
                        )
                    }
                    Text(
                        text = vitals?.let { "${it.brand} ${it.model} • Android ${it.androidVersion}" }
                            ?: strings.readyToDiagnose,
                        fontSize = 10.sp,
                        color = CyberTextSecondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            LanguageToggleSwitch(
                currentLanguage = currentLanguage,
                onSelectLanguage = onSelectLanguage,
                modifier = Modifier.padding(end = 4.dp)
            )

            vitals?.let { v ->
                val scoreColor = when {
                    v.healthScore >= 80 -> CyberGreen
                    v.healthScore >= 60 -> CyberAmber
                    else -> CyberRed
                }
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable { onToggleVitals() }
                        .testTag("toggle_vitals_pill"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = if (vitalsExpanded) scoreColor.copy(alpha = 0.16f) else CyberSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, scoreColor.copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(scoreColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${v.healthScore}%",
                                fontWeight = FontWeight.Bold,
                                color = scoreColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = strings.toggleVitalsCd,
                                tint = scoreColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onResetChat,
                modifier = Modifier.testTag("reset_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = strings.resetChatCd,
                    tint = CyberCyan
                )
            }
        }
    )
}

@Composable
fun FuturisticVitalsHeaderStrip(
    vitals: DeviceVitals,
    expanded: Boolean,
    strings: LocalizedStrings,
    onToggleExpand: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onToggleExpand() }
            .testTag("vitals_header_card"),
        shape = RoundedCornerShape(14.dp),
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = when {
                            !vitals.batteryVerified -> CyberAmber
                            vitals.batteryLevel < 20 -> CyberRed
                            else -> CyberGreen
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (vitals.batteryVerified) "${vitals.batteryLevel}%" else strings.notCollectedLabel,
                        color = CyberTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (vitals.storageVerified) {
                            "${RealDeviceDiagnostics.formatBytes(vitals.storageFreeBytes)} ${strings.storageFreeSuffix}"
                        } else strings.notCollectedLabel,
                        color = CyberTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = CyberBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (vitals.ramVerified) "${RealDeviceDiagnostics.formatBytes(vitals.ramFreeBytes)} RAM" else strings.notCollectedLabel,
                        color = CyberTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (vitals.networkVerified && vitals.isInternetAvailable) CyberGreen else CyberRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = vitals.networkType,
                        color = CyberTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                val tempDisplay = if (vitals.temperatureVerified) "${vitals.batteryTempC}°C" else strings.notCollectedLabel
                val storageDisplay = if (vitals.storageVerified) {
                    "${RealDeviceDiagnostics.formatBytes(vitals.storageTotalBytes)} (${vitals.storageUsedPercent}%)"
                } else strings.notCollectedLabel
                val ramDisplay = if (vitals.ramVerified) {
                    "${RealDeviceDiagnostics.formatBytes(vitals.ramTotalBytes)} (${vitals.ramUsedPercent}%, LowMem: ${vitals.isLowMemory})"
                } else strings.notCollectedLabel
                val displayStr = if (vitals.displayVerified) {
                    "${vitals.displayResolution} @ ${vitals.refreshRate.toInt()}Hz (${if (vitals.screenTimeoutSeconds >= 0) "${vitals.screenTimeoutSeconds}s" else strings.notCollectedLabel})"
                } else strings.notCollectedLabel
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = CyberCardBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.telemetryMatrixHeader,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• ${strings.modPowerTitle}: $tempDisplay (${vitals.thermalStatus}) • PowerSaver: ${if (vitals.isPowerSaveMode) "ON" else "OFF"}\n" +
                                "• ${strings.metricStorage}: $storageDisplay\n" +
                                "• ${strings.metricRamUsed}: $ramDisplay\n" +
                                "• ${strings.hwNetworkTitle}: Wi-Fi=${vitals.isWifiEnabled}, BT=${vitals.isBluetoothEnabled}, Ringer=${vitals.ringerMode}\n" +
                                "• ${strings.hwDisplayTitle}: $displayStr\n" +
                                "• WriteSettings=${vitals.canWriteSystemSettings} • ${vitals.cpuArchitecture}",
                        fontSize = 11.sp,
                        color = CyberTextSecondary,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FuturisticMessageBubble(
    message: ChatMessage,
    strings: LocalizedStrings,
    isSpeaking: Boolean = false,
    isAnalyzing: Boolean = false,
    onToggleSpeech: (() -> Unit)? = null,
    onRetryCloudAi: (() -> Unit)? = null,
    onRequestAutonomousExecution: (() -> Unit)? = null,
    onActionClick: (DoctorAction) -> Unit,
    onVerifySuccess: (DoctorAction) -> Unit,
    onVerifyFailed: (DoctorAction) -> Unit
) {
    val isUser = message.sender == SenderType.USER
    val msgStrings = if (!isUser && !message.responseLanguage.isNullOrBlank()) {
        AppStrings.forLanguage(message.responseLanguage)
    } else {
        strings
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isUser) "user_message_${message.id}" else "doctor_message_${message.id}"),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Image(
                painter = painterResource(id = R.drawable.doctor_badge_transparent),
                contentDescription = strings.appTitle,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, CyberCyan, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 520.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            message.attachedScreenshot?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = strings.attachScreenshot,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, CyberBlue, RoundedCornerShape(12.dp))
                        .padding(bottom = 6.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) CyberBlue else CyberCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isUser) CyberCyan.copy(alpha = 0.4f) else CyberCardBorder
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (!isUser) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = msgStrings.verifiedAiDiagnosis,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSpeaking) CyberCyan.copy(alpha = 0.2f) else CyberSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSpeaking) CyberCyan else CyberCardBorder
                                ),
                                modifier = Modifier
                                    .clickable { onToggleSpeech?.invoke() }
                                    .testTag("speech_toggle_btn_${message.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = if (isSpeaking) msgStrings.voiceStop else msgStrings.voicePlay,
                                        tint = if (isSpeaking) CyberCyan else CyberTextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isSpeaking) msgStrings.voiceStop else msgStrings.voicePlay,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSpeaking) CyberCyan else CyberTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = formatChatMarkdown(message.text),
                        color = if (isUser) Color.White else CyberTextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.5.sp
                    )

                    if (!isUser && onRetryCloudAi != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (!message.aiStatusDetail.isNullOrBlank()) {
                            Text(
                                text = message.aiStatusDetail,
                                color = CyberAmber,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        OutlinedButton(
                            onClick = onRetryCloudAi,
                            enabled = !isAnalyzing,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("retry_cloud_ai_msg_btn_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = msgStrings.retryCloudAiBtn,
                                tint = CyberCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = msgStrings.retryCloudAiBtn,
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            message.repairPlan?.let { plan ->
                Spacer(modifier = Modifier.height(8.dp))
                StructuredRepairPlanCard(plan = plan, strings = msgStrings)
            }

            message.autonomousBundle?.let { bundle ->
                Spacer(modifier = Modifier.height(8.dp))
                AutonomousRepairPlanCard(
                    messageId = message.id,
                    bundle = bundle,
                    strings = msgStrings,
                    isAnalyzing = isAnalyzing,
                    onAuthorizeAndRun = { onRequestAutonomousExecution?.invoke() },
                    onActionClick = onActionClick
                )
            }

            message.verificationComparison?.let { comparison ->
                Spacer(modifier = Modifier.height(8.dp))
                BeforeAfterComparisonCard(comparison = comparison, strings = msgStrings)
            }

            val bundledActionIds = remember(message.autonomousBundle) {
                message.autonomousBundle?.let { b ->
                    (b.executableAutoSteps.map { it.action.id } +
                        b.permissionGatedSteps.map { it.action.id } +
                        b.guidedSettingsSteps.map { it.action.id }).toSet()
                } ?: emptySet()
            }
            val remainingActions = remember(message.actions, bundledActionIds) {
                message.actions.filter { it.id !in bundledActionIds }
            }
            val bundleRenderedAutoHeader = message.autonomousBundle?.executableAutoSteps?.isNotEmpty() == true
            val bundleRenderedPermissionHeader = message.autonomousBundle?.let {
                it.permissionGatedSteps.isNotEmpty() || it.guidedSettingsSteps.isNotEmpty()
            } == true

            if (remainingActions.isNotEmpty()) {
                val autoActions = remainingActions.filter { it.capability == AutomationCapability.NORMAL_API }
                val permissionActions = remainingActions.filter { it.capability != AutomationCapability.NORMAL_API }

                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (autoActions.isNotEmpty()) {
                        Text(
                            text = msgStrings.autoActionsHeader,
                            color = CyberGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = if (!bundleRenderedAutoHeader) {
                                Modifier.testTag("auto_actions_group_header")
                            } else {
                                Modifier
                            }
                        )
                        autoActions.forEach { action ->
                            FuturisticActionCard(action = action, strings = msgStrings, onClick = { onActionClick(action) })
                        }
                    }
                    if (permissionActions.isNotEmpty()) {
                        Text(
                            text = msgStrings.permissionSettingsActionsHeader,
                            color = CyberAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .padding(top = if (autoActions.isNotEmpty()) 4.dp else 0.dp)
                                .then(
                                    if (!bundleRenderedPermissionHeader) {
                                        Modifier.testTag("permission_actions_group_header")
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                        permissionActions.forEach { action ->
                            FuturisticActionCard(action = action, strings = msgStrings, onClick = { onActionClick(action) })
                        }
                    }
                }
            }

            message.verificationRequestedForAction?.let { action ->
                Spacer(modifier = Modifier.height(8.dp))
                RepairVerificationCard(
                    action = action,
                    strings = msgStrings,
                    onVerifiedSuccess = { onVerifySuccess(action) },
                    onVerifiedFailed = { onVerifyFailed(action) }
                )
            }

            Text(
                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = CyberTextMuted,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
fun StructuredRepairPlanCard(plan: RepairPlan, strings: LocalizedStrings) {
    var expanded by remember(plan.playbookId, plan.problemTitle) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("repair_plan_card"),
        shape = RoundedCornerShape(12.dp),
        color = CyberSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberTeal.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildString {
                            append("${strings.structuredRepairPlan} • ")
                            append(plan.confidenceLevel.uppercase())
                            if (!plan.severityLabel.isNullOrBlank()) append(" • ${plan.severityLabel}")
                            if (!plan.riskTierLabel.isNullOrBlank()) append(" / ${plan.riskTierLabel}")
                        },
                        color = CyberTeal,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = plan.problemTitle,
                        color = CyberTextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!plan.sourceCitation.isNullOrBlank() || !plan.coverageTierLabel.isNullOrBlank()) {
                        Text(
                            text = listOfNotNull(plan.sourceCitation, plan.coverageTierLabel).joinToString(" • "),
                            color = CyberCyan,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = if (expanded) strings.hideDetails else strings.viewEvidence,
                    color = CyberCyan,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = CyberCardBorder)
                    if (!plan.oemResolvedPath.isNullOrBlank()) {
                        Text(
                            text = strings.oemResolvedPathLabel,
                            color = CyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = plan.oemResolvedPath,
                            color = CyberGreen,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (plan.relatedOverlapIds.isNotEmpty()) {
                        Text(
                            text = "IDs: ${plan.relatedOverlapIds.joinToString(", ")}",
                            color = CyberAmber,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (plan.confirmedEvidence.isNotEmpty()) {
                        Text(
                            text = strings.confirmedEvidenceLabel,
                            color = CyberGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        plan.confirmedEvidence.forEach { ev ->
                            Text("• $ev", color = CyberTextSecondary, fontSize = 10.5.sp)
                        }
                    }
                    if (plan.possibleCauses.isNotEmpty()) {
                        Text(
                            text = strings.possibleCausesLabel,
                            color = CyberAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        plan.possibleCauses.forEach { c ->
                            Text("• $c", color = CyberTextSecondary, fontSize = 10.5.sp)
                        }
                    }
                    if (plan.diagnosticQuestions.isNotEmpty()) {
                        Text(
                            text = strings.diagnosticQuestionsLabel,
                            color = CyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        plan.diagnosticQuestions.forEach { q ->
                            Text("? $q", color = CyberTextPrimary, fontSize = 10.5.sp)
                        }
                    }
                    if (plan.unverifiedAssumptions.isNotEmpty()) {
                        Text(
                            text = strings.unverifiedAssumptionsLabel,
                            color = CyberTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        plan.unverifiedAssumptions.forEach { u ->
                            Text("◦ $u", color = CyberTextMuted, fontSize = 10.sp)
                        }
                    }
                    if (plan.hardwareFaultIndicators.isNotEmpty()) {
                        Text(
                            text = strings.hardwareIndicatorsLabel,
                            color = CyberRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        plan.hardwareFaultIndicators.forEach { h ->
                            Text("⚠ $h", color = CyberTextSecondary, fontSize = 10.5.sp)
                        }
                    }
                    Text(
                        text = "${strings.verificationLabel} ${plan.verificationMethod}",
                        color = CyberGreen,
                        fontSize = 10.5.sp
                    )
                    Text(
                        text = "${strings.escalationLabel} ${plan.escalationInstructions}",
                        color = CyberTextMuted,
                        fontSize = 10.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BeforeAfterComparisonCard(comparison: BeforeAfterComparison, strings: LocalizedStrings) {
    val outcomeColor = when (comparison.outcome) {
        VerificationOutcome.VERIFIED_IMPROVED -> CyberGreen
        VerificationOutcome.NEEDS_USER_CONFIRMATION -> CyberCyan
        VerificationOutcome.NO_MEASURABLE_CHANGE -> CyberAmber
        VerificationOutcome.EXECUTION_FAILED -> CyberRed
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("before_after_comparison_card"),
        shape = RoundedCornerShape(12.dp),
        color = CyberSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, outcomeColor.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.beforeAfterAudit,
                    color = CyberCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Surface(
                    color = outcomeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = LocalizedDiagnosticFormatter.localizeOutcomeLabel(comparison.outcome, strings.languageCode),
                        color = outcomeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "${strings.beforeLabel} ${comparison.beforeSummary}",
                color = CyberTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${strings.afterLabel}  ${comparison.afterSummary}",
                color = CyberTextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            comparison.metricDeltas.forEach { delta ->
                Text(
                    text = "Δ $delta",
                    color = outcomeColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AutonomousRepairPlanCard(
    messageId: String,
    bundle: AutonomousRepairBundle,
    strings: LocalizedStrings,
    isAnalyzing: Boolean,
    onAuthorizeAndRun: () -> Unit,
    onActionClick: (DoctorAction) -> Unit
) {
    val isHindi = strings.languageCode.equals("Hindi", ignoreCase = true)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("autonomous_repair_plan_card_$messageId"),
        shape = RoundedCornerShape(14.dp),
        color = CyberSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (bundle.isApprovedAndExecuted) CyberGreen.copy(alpha = 0.65f) else CyberCyan.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = strings.autonomousPlanHeader,
                        tint = if (bundle.isApprovedAndExecuted) CyberGreen else CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.autonomousPlanHeader,
                        color = if (bundle.isApprovedAndExecuted) CyberGreen else CyberCyan,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                val summaryBadge = buildString {
                    append("${bundle.executableAutoSteps.size} AUTO")
                    val guidedCount = bundle.permissionGatedSteps.size + bundle.guidedSettingsSteps.size
                    if (guidedCount > 0) append(" • $guidedCount GUIDED")
                }
                Surface(
                    color = CyberSurfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = summaryBadge,
                        color = CyberTextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (bundle.executableAutoSteps.isNotEmpty()) {
                Text(
                    text = strings.autoActionsHeader,
                    color = CyberGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("auto_actions_group_header")
                )

                bundle.executableAutoSteps.forEach { step ->
                    AutonomousStepItemRow(step = step, strings = strings)
                }

                if (!bundle.isApprovedAndExecuted) {
                    Button(
                        onClick = onAuthorizeAndRun,
                        enabled = !bundle.isExecuting && !isAnalyzing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberGreen,
                            contentColor = Color(0xFF00220F)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("run_autonomous_plan_btn_$messageId")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = strings.autonomousRunBatchBtn,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (bundle.isExecuting) strings.autonomousExecutingLabel else strings.autonomousRunBatchBtn,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (bundle.isApprovedAndExecuted &&
                bundle.beforeVitalsSummary != null &&
                bundle.afterVitalsSummary != null
            ) {
                Surface(
                    color = CyberCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberGreen.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("autonomous_before_after_card_$messageId")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = strings.beforeAfterAudit,
                            color = CyberGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (!bundle.overallOutcomeSummary.isNullOrBlank()) {
                            Text(
                                text = bundle.overallOutcomeSummary,
                                color = CyberTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${strings.beforeLabel} ${bundle.beforeVitalsSummary}",
                            color = CyberTextMuted,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${strings.afterLabel} ${bundle.afterVitalsSummary}",
                            color = CyberCyan,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (bundle.permissionGatedSteps.isNotEmpty() || bundle.guidedSettingsSteps.isNotEmpty()) {
                Text(
                    text = strings.permissionSettingsActionsHeader,
                    color = CyberAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .testTag("permission_actions_group_header")
                )

                bundle.permissionGatedSteps.forEach { step ->
                    val localized = LocalizedDiagnosticFormatter.localizeAction(step.action, strings.languageCode)
                    Surface(
                        color = CyberCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberAmber.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = localized.title,
                                    color = CyberAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = step.restrictionSummary,
                                    color = CyberTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onActionClick(step.action) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberAmber,
                                    contentColor = Color(0xFF1E1200)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("auto_perm_btn_${step.action.id}")
                            ) {
                                Text(
                                    text = strings.setupGrantBtn,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                bundle.guidedSettingsSteps.forEach { step ->
                    val localized = LocalizedDiagnosticFormatter.localizeAction(step.action, strings.languageCode)
                    Surface(
                        color = CyberCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = localized.title,
                                    color = CyberCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = localized.description,
                                    color = CyberTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onActionClick(step.action) },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("auto_guided_btn_${step.action.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = localized.title,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.actionRun,
                                    color = CyberCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            if (bundle.osBlockedNotices.isNotEmpty()) {
                Surface(
                    color = CyberSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = strings.autonomousOsRestrictionsLabel,
                            color = CyberAmber,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        bundle.osBlockedNotices.forEach { notice ->
                            Text(
                                text = "• $notice",
                                color = CyberTextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            if (bundle.hardwareEscalationNotes.isNotEmpty()) {
                Surface(
                    color = CyberRed.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = strings.autonomousHardwareAlertsLabel,
                                tint = CyberRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = strings.autonomousHardwareAlertsLabel,
                                color = CyberRed,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        bundle.hardwareEscalationNotes.forEach { note ->
                            Text(
                                text = "• $note",
                                color = CyberTextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutonomousStepItemRow(
    step: AutonomousRepairStep,
    strings: LocalizedStrings
) {
    val localized = LocalizedDiagnosticFormatter.localizeAction(step.action, strings.languageCode)
    val isHindi = strings.languageCode.equals("Hindi", ignoreCase = true)
    val (statusLabel, statusColor) = when (step.status) {
        AutonomousStepStatus.PENDING_APPROVAL -> (if (isHindi) "तैयार" else "READY") to CyberCyan
        AutonomousStepStatus.EXECUTING -> (if (isHindi) "सक्रिय..." else "RUNNING") to CyberAmber
        AutonomousStepStatus.VERIFIED_IMPROVED -> (if (isHindi) "सुधार सत्यापित" else "VERIFIED") to CyberGreen
        AutonomousStepStatus.NEEDS_USER_CONFIRMATION -> (if (isHindi) "पुष्टि आवश्यक" else "CHECK") to CyberCyan
        AutonomousStepStatus.EXECUTED_NO_SHIFT -> (if (isHindi) "जाँचा गया" else "CHECKED") to CyberTextSecondary
        AutonomousStepStatus.PERMISSION_REQUIRED -> (if (isHindi) "अनुमति आवश्यक" else "PERM REQ") to CyberAmber
        AutonomousStepStatus.SETTINGS_GUIDED -> (if (isHindi) "सेटिंग्स विकल्प" else "GUIDED") to CyberCyan
        AutonomousStepStatus.BLOCKED_OR_FAILED -> (if (isHindi) "असफल / अवरुद्ध" else "FAILED") to CyberRed
    }

    Surface(
        color = CyberCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auto_step_row_${step.action.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = localized.title,
                    color = CyberTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = localized.description,
                color = CyberTextSecondary,
                fontSize = 10.5.sp
            )
            step.measuredDetails.take(3).forEach { detail ->
                Text(
                    text = "• $detail",
                    color = statusColor,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun FuturisticActionCard(
    action: DoctorAction,
    strings: LocalizedStrings,
    onClick: () -> Unit
) {
    val localized = LocalizedDiagnosticFormatter.localizeAction(action, strings.languageCode)
    val accent = if (action.isDestructive) CyberRed else CyberCyan

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("action_card_${action.id}"),
        shape = RoundedCornerShape(12.dp),
        color = CyberSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 3.dp)
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = localized.capabilityBadge,
                            color = accent,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = CyberSurfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = localized.riskLabel,
                            color = if (action.isDestructive) CyberRed else CyberTextSecondary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = localized.title,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = localized.description,
                    color = CyberTextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onClick,
                modifier = Modifier.testTag("action_btn_${action.id}"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = if (action.isDestructive) Color.White else Color(0xFF001B2E)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (action.isDestructive) strings.actionReview else strings.actionRun,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun FuturisticDoctorThinkingIndicator(strings: LocalizedStrings) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("doctor_thinking_indicator"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.doctor_badge_transparent),
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(1.dp, CyberCyan, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CyberCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(CyberCyan, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.analyzingTelemetry,
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun QuickStartStarterCard(
    strings: LocalizedStrings,
    onRunAutoFix: () -> Unit,
    onQuickPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CyberCyan.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRunAutoFix() }
                .testTag("starter_autofix_card")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = strings.autoBadge,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.autoFixStarterTitle,
                        color = CyberTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.autoFixStarterSubtitle,
                        color = CyberTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        val row1 = listOf(
            strings.chipBatteryLabel to strings.chipBatteryPrompt,
            strings.chipHangLabel to strings.chipHangPrompt
        )
        val row2 = listOf(
            strings.chipWifiLabel to strings.chipWifiPrompt,
            strings.chipHeatLabel to strings.chipHeatPrompt
        )
        listOf(row1, row2).forEachIndexed { rowIdx, pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEachIndexed { colIdx, (label, prompt) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable { onQuickPrompt(prompt) }
                            .testTag("symptom_chip_${rowIdx}_$colIdx")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = label,
                                color = CyberTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
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

@Composable
fun FuturisticChatInputBar(
    text: String,
    hasAttachment: Boolean = false,
    strings: LocalizedStrings,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachVitals: () -> Unit,
    onPickScreenshot: () -> Unit,
    onVoiceInput: () -> Unit,
    isAnalyzing: Boolean
) {
    val canSend = (text.isNotBlank() || hasAttachment) && !isAnalyzing
    var showAttachMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = showAttachMenu) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyberCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberTeal.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            showAttachMenu = false
                            onPickScreenshot()
                        }
                        .testTag("pick_screenshot_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = strings.attachScreenshot,
                            tint = CyberTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.attachScreenshot, color = CyberTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyberCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            showAttachMenu = false
                            onAttachVitals()
                        }
                        .testTag("attach_vitals_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = strings.attachSendVitals,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.attachSendVitals, color = CyberTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Surface(
            color = CyberSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showAttachMenu = !showAttachMenu },
                    modifier = Modifier.testTag("toggle_attach_menu_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (showAttachMenu) CyberCyan.copy(alpha = 0.2f) else CyberSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showAttachMenu) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Attach Options",
                            tint = CyberCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            strings.inputPlaceholder,
                            color = CyberTextMuted,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("problem_input_field"),
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedTextColor = CyberTextPrimary,
                        unfocusedTextColor = CyberTextPrimary,
                        cursorColor = CyberCyan
                    )
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onVoiceInput,
                    modifier = Modifier.testTag("voice_input_mic_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.14f))
                            .border(1.dp, CyberCyan.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = CyberCyan
                        )
                    }
                }

                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.testTag("send_message_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (canSend) CyberCyan else CyberSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Problem",
                            tint = if (canSend) Color(0xFF001B2E) else CyberTextMuted
                        )
                    }
                }
            }
        }
    }
}

private fun formatChatMarkdown(raw: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < raw.length) {
            if (i + 1 < raw.length && raw[i] == '*' && raw[i + 1] == '*') {
                val end = raw.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(raw.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }
            if (raw[i] == '`') {
                val end = raw.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = CyberCyan)) {
                        append(raw.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }
            append(raw[i])
            i++
        }
    }
}
