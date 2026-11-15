package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.ai.AiDoctorService
import com.example.data.ai.ConversationIntent
import com.example.data.ai.DoctorResponse
import com.example.data.ai.LanguageDetector
import com.example.data.ai.LocalizedDiagnosticFormatter
import com.example.data.ai.TroubleshootingSessionContext
import com.example.data.diagnostics.PingResult
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.knowledge.KnowledgeCoverageAudit
import com.example.data.knowledge.KnowledgeCoverageAuditor
import com.example.data.knowledge.KnowledgeImportResult
import com.example.data.knowledge.KnowledgeRepository
import com.example.data.knowledge.KnowledgeUpdateManager
import com.example.data.knowledge.PlaybookRagRetriever
import com.example.data.local.AppDatabase
import com.example.data.local.DiagnosticRepository
import com.example.data.maintenance.MaintenanceScheduleStatus
import com.example.data.maintenance.MaintenanceWorkScheduler
import com.example.data.model.ActionType
import com.example.data.model.AiServiceStatus
import com.example.data.model.AutonomousRepairBundle
import com.example.data.model.AutonomousRepairStep
import com.example.data.model.AutonomousStepStatus
import com.example.data.model.BeforeAfterComparison
import com.example.data.model.CapabilityMatrixEntry
import com.example.data.model.ChatMessage
import com.example.data.model.DeviceVitals
import com.example.data.model.DiagnosticHistoryItem
import com.example.data.model.DoctorAction
import com.example.data.model.RepairLevel
import com.example.data.model.SenderType
import com.example.data.model.VerificationOutcome
import com.example.data.privacy.PrivacyRedactor
import com.example.data.repair.ActionResult
import com.example.data.repair.AutoFixEngine
import com.example.data.repair.AutoFixReport
import com.example.data.repair.AutonomousRepairOrchestrator
import com.example.data.repair.CapabilityEvaluator
import com.example.data.repair.CapabilityMatrix
import com.example.data.repair.PipelineExecutionParams
import com.example.data.repair.RepairPipelineEngine
import com.example.data.repair.RepairPipelineReport
import com.example.data.repair.RepairVerificationEngine
import com.example.data.repair.SafeRepairActions
import com.example.data.security.AppSecurityScanner
import com.example.data.security.FileSecurityReport
import com.example.data.security.UrlSafetyReport
import com.example.data.storage.AppStorageBreakdown
import com.example.data.storage.HousekeepingResult
import com.example.data.storage.SafeStorageManager
import com.example.data.update.ApkDownloadResult
import com.example.data.update.AppUpdateManager
import com.example.data.update.UpdateCheckResult
import com.example.data.update.UpdateManifest
import com.example.data.update.UpdateState
import com.example.ui.i18n.AppStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class HardwareTestDialogState {
    NONE,
    AUDIO,
    VIBRATION,
    SCREEN,
    NETWORK
}

class DoctorViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionPrefs by lazy {
        application.getSharedPreferences("ai_doctor_session", Context.MODE_PRIVATE)
    }
    internal var aiService = AiDoctorService()
    private val voiceManager = com.example.util.NaturalVoiceManager(application)
    private val speechInputManager = com.example.util.LiveSpeechInputManager(application)
    private val repository: DiagnosticRepository by lazy {
        val dao = AppDatabase.getInstance(application).diagnosticHistoryDao()
        DiagnosticRepository(dao)
    }

    val isSpeaking = voiceManager.isSpeaking
    val currentlySpeakingMessageId = voiceManager.currentlySpeakingMessageId
    val activeVoiceEngineInfo = voiceManager.activeVoiceEngineInfo
    val isListening = speechInputManager.isListening
    val partialVoiceTranscript = speechInputManager.partialTranscript
    val voiceSoundLevelDb = speechInputManager.rmsLevelDb

    private val _isVoiceConversationMode = MutableStateFlow(
        runCatching { sessionPrefs.getBoolean("voice_conversation_mode", false) }.getOrDefault(false)
    )
    val isVoiceConversationMode: StateFlow<Boolean> = _isVoiceConversationMode.asStateFlow()

    private val _sessionContext = MutableStateFlow(
        runCatching {
            TroubleshootingSessionContext.fromJsonString(
                sessionPrefs.getString("troubleshooting_context_json", null)
            )
        }.getOrDefault(TroubleshootingSessionContext())
    )
    val sessionContext: StateFlow<TroubleshootingSessionContext> = _sessionContext.asStateFlow()

    private val _vitals = MutableStateFlow<DeviceVitals?>(null)
    val vitals: StateFlow<DeviceVitals?> = _vitals.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _aiServiceStatus = MutableStateFlow(AiServiceStatus.ONLINE_GEMINI)
    val aiServiceStatus: StateFlow<AiServiceStatus> = _aiServiceStatus.asStateFlow()

    private val _aiStatusDetail = MutableStateFlow("Gemini 3.x Ready")
    val aiStatusDetail: StateFlow<String> = _aiStatusDetail.asStateFlow()

    private val _activeTest = MutableStateFlow(HardwareTestDialogState.NONE)
    val activeTest: StateFlow<HardwareTestDialogState> = _activeTest.asStateFlow()

    private val _pingResult = MutableStateFlow<PingResult?>(null)
    val pingResult: StateFlow<PingResult?> = _pingResult.asStateFlow()

    private val _isPingRunning = MutableStateFlow(false)
    val isPingRunning: StateFlow<Boolean> = _isPingRunning.asStateFlow()

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    private val _attachedScreenshot = MutableStateFlow<Bitmap?>(null)
    val attachedScreenshot: StateFlow<Bitmap?> = _attachedScreenshot.asStateFlow()

    private val _attachedScreenshotName = MutableStateFlow<String?>(null)
    val attachedScreenshotName: StateFlow<String?> = _attachedScreenshotName.asStateFlow()

    // Approval Dialog State (Level 3 Settings + Level 4 Destructive Guarded + Special Permissions)
    private val _pendingApprovalAction = MutableStateFlow<DoctorAction?>(null)
    val pendingApprovalAction: StateFlow<DoctorAction?> = _pendingApprovalAction.asStateFlow()

    // Live Runtime Capability Matrix (5-tier capability audit for this device)
    private val _capabilityMatrix = MutableStateFlow<List<CapabilityMatrixEntry>>(emptyList())
    val capabilityMatrix: StateFlow<List<CapabilityMatrixEntry>> = _capabilityMatrix.asStateFlow()

    // Batch Autonomous Repair Confirmation State (messageId -> bundle)
    private val _pendingAutonomousPlanConfirmation = MutableStateFlow<Pair<String, AutonomousRepairBundle>?>(null)
    val pendingAutonomousPlanConfirmation: StateFlow<Pair<String, AutonomousRepairBundle>?> =
        _pendingAutonomousPlanConfirmation.asStateFlow()

    // Persistent Room-backed Diagnostic History
    val historyItems: StateFlow<List<DiagnosticHistoryItem>> by lazy {
        repository.historyItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _showAutoFixDialog = MutableStateFlow(false)
    val showAutoFixDialog: StateFlow<Boolean> = _showAutoFixDialog.asStateFlow()

    private val _isAutoFixRunning = MutableStateFlow(false)
    val isAutoFixRunning: StateFlow<Boolean> = _isAutoFixRunning.asStateFlow()

    // English is always the strict default unless user explicitly switched & persisted Hindi
    private val _currentLanguage = MutableStateFlow(
        runCatching {
            val saved = sessionPrefs.getString("saved_language", "English") ?: "English"
            if (saved.equals("Hindi", ignoreCase = true)) "Hindi" else "English"
        }.getOrDefault("English")
    )
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    // Automatic In-App APK Update state
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _isUpdateBannerDismissed = MutableStateFlow(false)
    val isUpdateBannerDismissed: StateFlow<Boolean> = _isUpdateBannerDismissed.asStateFlow()

    private var lastUpdateManifest: UpdateManifest? = null

    fun setLanguage(lang: String) {
        val normalized = if (lang.equals("Hindi", ignoreCase = true)) "Hindi" else "English"
        _currentLanguage.value = normalized
        runCatching { sessionPrefs.edit().putString("saved_language", normalized).apply() }

        // Immediately refresh greeting bubble if chat hasn't started yet
        val currentMsgs = _messages.value
        if (currentMsgs.size <= 1 && (currentMsgs.isEmpty() || currentMsgs.first().id == WELCOME_MESSAGE_ID)) {
            sendWelcomeMessage()
        }
    }

    private val _autoFixReport = MutableStateFlow<AutoFixReport?>(null)
    val autoFixReport: StateFlow<AutoFixReport?> = _autoFixReport.asStateFlow()

    private val _coverageAudit = MutableStateFlow<KnowledgeCoverageAudit?>(null)
    val coverageAudit: StateFlow<KnowledgeCoverageAudit?> = _coverageAudit.asStateFlow()

    private val _lastImportResult = MutableStateFlow<KnowledgeImportResult?>(null)
    val lastImportResult: StateFlow<KnowledgeImportResult?> = _lastImportResult.asStateFlow()

    // Blueprint modules: Own-app Storage Housekeeping, Maintenance Worker, Security Scanner & 8-Stage Pipeline
    private val _storageBreakdown = MutableStateFlow<AppStorageBreakdown?>(null)
    val storageBreakdown: StateFlow<AppStorageBreakdown?> = _storageBreakdown.asStateFlow()

    private val _maintenanceStatus = MutableStateFlow<MaintenanceScheduleStatus?>(null)
    val maintenanceStatus: StateFlow<MaintenanceScheduleStatus?> = _maintenanceStatus.asStateFlow()

    private val _lastHousekeepingResult = MutableStateFlow<HousekeepingResult?>(null)
    val lastHousekeepingResult: StateFlow<HousekeepingResult?> = _lastHousekeepingResult.asStateFlow()

    private val _lastSecurityScanReport = MutableStateFlow<FileSecurityReport?>(null)
    val lastSecurityScanReport: StateFlow<FileSecurityReport?> = _lastSecurityScanReport.asStateFlow()

    private val _lastUrlCheckReport = MutableStateFlow<UrlSafetyReport?>(null)
    val lastUrlCheckReport: StateFlow<UrlSafetyReport?> = _lastUrlCheckReport.asStateFlow()

    private val _lastPipelineReport = MutableStateFlow<RepairPipelineReport?>(null)
    val lastPipelineReport: StateFlow<RepairPipelineReport?> = _lastPipelineReport.asStateFlow()

    // Track active playbook / KB issue ID and tried action IDs for intelligent escalation
    private var lastMatchedPlaybookId: String? = _sessionContext.value.activeProblemId
    private val triedActionIdsInSession = mutableSetOf<String>().apply {
        addAll(_sessionContext.value.attemptedActionIds)
        addAll(_sessionContext.value.failedActionIds)
    }

    companion object {
        private const val TAG = "DoctorViewModel"
        private const val WELCOME_MESSAGE_ID = "welcome_msg"
        private const val PREF_KEY_CONTEXT_JSON = "troubleshooting_context_json"
        private const val PREF_KEY_VOICE_MODE = "voice_conversation_mode"
    }

    private fun persistSessionContext(ctx: TroubleshootingSessionContext) {
        _sessionContext.value = ctx
        lastMatchedPlaybookId = ctx.activeProblemId
        triedActionIdsInSession.clear()
        triedActionIdsInSession.addAll(ctx.attemptedActionIds)
        triedActionIdsInSession.addAll(ctx.failedActionIds)
        runCatching {
            sessionPrefs.edit().putString(PREF_KEY_CONTEXT_JSON, ctx.toJsonString()).apply()
        }
    }

    private fun updateSessionContextFromResponse(userQuery: String, response: DoctorResponse) {
        val current = _sessionContext.value
        val updated = when (response.intentHandled) {
            ConversationIntent.SMALL_TALK -> current
            ConversationIntent.CREATOR_IDENTITY -> current.copy(
                lastAssistantSummary = response.replyText.take(500),
                isCreatorConversationActive = true,
                creatorTopicsDiscussed = current.creatorTopicsDiscussed + listOfNotNull(response.creatorFacetDiscussed),
                creatorTurnCount = current.creatorTurnCount + 1,
                lastCreatorFacet = response.creatorFacetDiscussed ?: current.lastCreatorFacet
            )
            ConversationIntent.CLARIFY_EXPLANATION -> current.copy(
                activeProblemId = response.matchedProblemId ?: current.activeProblemId,
                activeProblemTitle = response.matchedProblemTitle ?: current.activeProblemTitle,
                activeCategory = response.matchedCategory ?: current.activeCategory,
                lastAssistantSummary = response.replyText.take(500)
            )
            ConversationIntent.FOLLOW_UP_NOT_FIXED -> current.copy(
                activeProblemId = response.matchedProblemId ?: current.activeProblemId,
                activeProblemTitle = response.matchedProblemTitle ?: current.activeProblemTitle,
                activeCategory = response.matchedCategory ?: current.activeCategory,
                lastAssistantSummary = response.replyText.take(500),
                followUpStepOffset = current.followUpStepOffset + 1
            )
            ConversationIntent.PROBLEM_REPORT -> {
                val isClarify = response.matchedProblemId == "CLARIFY-REQUIRED"
                val sameProblem = !isClarify && response.matchedProblemId != null &&
                    response.matchedProblemId == current.activeProblemId
                TroubleshootingSessionContext(
                    activeProblemId = if (isClarify) current.activeProblemId else (response.matchedProblemId ?: response.repairPlan?.playbookId),
                    activeProblemTitle = if (isClarify) current.activeProblemTitle else (response.matchedProblemTitle ?: response.repairPlan?.problemTitle),
                    activeCategory = if (isClarify) current.activeCategory else response.matchedCategory,
                    lastSymptomQuery = if (isClarify) (current.lastSymptomQuery ?: userQuery) else userQuery,
                    attemptedActionIds = if (sameProblem) current.attemptedActionIds else emptySet(),
                    failedActionIds = if (sameProblem) current.failedActionIds else emptySet(),
                    succeededActionIds = if (sameProblem) current.succeededActionIds else emptySet(),
                    lastAssistantSummary = response.replyText.take(500),
                    followUpStepOffset = if (sameProblem) current.followUpStepOffset else 0,
                    isCreatorConversationActive = false,
                    creatorTopicsDiscussed = current.creatorTopicsDiscussed,
                    creatorTurnCount = current.creatorTurnCount,
                    lastCreatorFacet = current.lastCreatorFacet
                )
            }
        }
        persistSessionContext(updated)
    }

    init {
        runCatching {
            KnowledgeRepository.ensureLoadedSynchronously(application)
            _coverageAudit.value = KnowledgeCoverageAuditor.generateLiveAudit(application)
        }
        viewModelScope.launch {
            try {
                KnowledgeRepository.initializeWithRoom(application)
                _coverageAudit.value = KnowledgeCoverageAuditor.generateLiveAudit(application)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "Room KB initialization fallback: ${t.localizedMessage}")
            }
        }
        refreshVitals()
        sendWelcomeMessage()
        // Automatically check for new APK release in background without blocking UI
        checkForUpdates(silent = true)
    }

    fun checkForUpdates(silent: Boolean = false) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            if (!silent) {
                _updateState.value = UpdateState.Checking
                _showUpdateDialog.value = true
            }
            try {
                when (val result = AppUpdateManager.checkForUpdate(app)) {
                    is UpdateCheckResult.Available -> {
                        lastUpdateManifest = result.manifest
                        _isUpdateBannerDismissed.value = false
                        _updateState.value = UpdateState.UpdateAvailable(
                            manifest = result.manifest,
                            currentVersionCode = result.currentVersionCode,
                            currentVersionName = result.currentVersionName
                        )
                        AppUpdateManager.showUpdateAvailableNotification(
                            context = app,
                            manifest = result.manifest,
                            language = _currentLanguage.value
                        )
                    }

                    is UpdateCheckResult.UpToDate -> {
                        _updateState.value = UpdateState.UpToDate(
                            currentVersionCode = result.currentVersionCode,
                            currentVersionName = result.currentVersionName
                        )
                        if (!silent) {
                            _actionFeedback.value = AppStrings.forLanguage(_currentLanguage.value).updateNoUpdateToast
                        }
                    }

                    is UpdateCheckResult.Failure -> {
                        if (!silent) {
                            _updateState.value = UpdateState.Error(
                                message = result.errorMessage,
                                canRetry = true,
                                lastManifest = lastUpdateManifest
                            )
                            _showUpdateDialog.value = true
                        } else {
                            _updateState.value = UpdateState.Idle
                        }
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (!silent) {
                    _updateState.value = UpdateState.Error(
                        message = t.localizedMessage ?: "Update check failed",
                        canRetry = true,
                        lastManifest = lastUpdateManifest
                    )
                    _showUpdateDialog.value = true
                }
            }
        }
    }

    fun startUpdateDownload() {
        val manifest = when (val s = _updateState.value) {
            is UpdateState.UpdateAvailable -> s.manifest
            is UpdateState.Error -> s.lastManifest
            else -> lastUpdateManifest
        } ?: return

        lastUpdateManifest = manifest
        val app = getApplication<Application>()
        _showUpdateDialog.value = true
        _updateState.value = UpdateState.Downloading(manifest, 0, 0L, manifest.fileSizeBytes)

        viewModelScope.launch {
            try {
                val res = AppUpdateManager.downloadAndVerifyApk(app, manifest) { pct, dl, total ->
                    _updateState.value = UpdateState.Downloading(manifest, pct, dl, total)
                }
                when (res) {
                    is ApkDownloadResult.Success -> {
                        if (AppUpdateManager.canRequestPackageInstalls(app)) {
                            _updateState.value = UpdateState.ReadyToInstall(manifest, res.apkFile)
                            AppUpdateManager.launchApkInstaller(app, res.apkFile)
                                .onFailure { err ->
                                    _actionFeedback.value = err.localizedMessage
                                }
                        } else {
                            _updateState.value = UpdateState.NeedsInstallPermission(manifest, res.apkFile)
                        }
                    }

                    is ApkDownloadResult.Failure -> {
                        _updateState.value = UpdateState.Error(
                            message = res.errorMessage,
                            canRetry = true,
                            lastManifest = manifest
                        )
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _updateState.value = UpdateState.Error(
                    message = t.localizedMessage ?: "Download interrupted",
                    canRetry = true,
                    lastManifest = manifest
                )
            }
        }
    }

    fun installDownloadedApk(context: Context) {
        val s = _updateState.value
        val (manifest, apkFile) = when (s) {
            is UpdateState.ReadyToInstall -> s.manifest to s.apkFile
            is UpdateState.NeedsInstallPermission -> s.manifest to s.apkFile
            else -> return
        }

        if (!AppUpdateManager.canRequestPackageInstalls(context)) {
            _updateState.value = UpdateState.NeedsInstallPermission(manifest, apkFile)
            return
        }

        AppUpdateManager.launchApkInstaller(context, apkFile)
            .onFailure { err ->
                _updateState.value = UpdateState.Error(
                    message = err.localizedMessage ?: "Could not open Android package installer",
                    canRetry = true,
                    lastManifest = manifest
                )
            }
    }

    fun onInstallPermissionResult(context: Context) {
        val s = _updateState.value
        if (s is UpdateState.NeedsInstallPermission && AppUpdateManager.canRequestPackageInstalls(context)) {
            _updateState.value = UpdateState.ReadyToInstall(s.manifest, s.apkFile)
            installDownloadedApk(context)
        }
    }

    fun retryUpdate() {
        val s = _updateState.value
        if (s is UpdateState.Error && s.lastManifest != null) {
            startUpdateDownload()
        } else {
            checkForUpdates(silent = false)
        }
    }

    fun openUpdateDialog() {
        _showUpdateDialog.value = true
        if (_updateState.value is UpdateState.Idle) {
            checkForUpdates(silent = false)
        }
    }

    fun closeUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun dismissUpdateBanner() {
        _isUpdateBannerDismissed.value = true
    }

    fun importKnowledgeUpdate(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    KnowledgeUpdateManager.importFromUri(context, uri)
                }
                _lastImportResult.value = result
                _coverageAudit.value = KnowledgeCoverageAuditor.generateLiveAudit(context)
                _actionFeedback.value = result.summaryMessage
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _actionFeedback.value = t.localizedMessage ?: "Import failed"
            }
        }
    }

    fun selectTab(index: Int) {
        _currentTab.value = index
    }

    fun refreshVitals() {
        runCatching {
            val app = getApplication<Application>()
            val currentVitals = RealDeviceDiagnostics.getDeviceVitals(app)
            _vitals.value = currentVitals
            _capabilityMatrix.value = CapabilityMatrix.buildLiveMatrix(app, currentVitals)
            _storageBreakdown.value = runCatching { SafeStorageManager.inspectOwnAppStorageBreakdown(app) }.getOrNull()
            _maintenanceStatus.value = runCatching { MaintenanceWorkScheduler.inspectStatus(app) }.getOrNull()
        }
    }

    fun runOwnStorageHousekeeping() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                SafeStorageManager.runFullOwnAppHousekeeping(app)
            }
            _lastHousekeepingResult.value = result
            _storageBreakdown.value = result.breakdownAfter
            _actionFeedback.value = result.summary
            runCatching {
                repository.saveHistoryItem(
                    DiagnosticHistoryItem(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        problemTitle = "Own-App Storage Housekeeping",
                        category = "STORAGE HYGIENE",
                        diagnosisSummary = PrivacyRedactor.redactSensitiveData(result.summary),
                        actionExecuted = "SafeStorageManager.runFullOwnAppHousekeeping",
                        verificationStatus = if (result.verifiedClean) "Verified Fixed" else "Unresolved",
                        capabilityUsed = "OWN_APP",
                        beforeSnapshot = result.breakdownBefore.formatSummaryLine(),
                        afterSnapshot = result.breakdownAfter.formatSummaryLine(),
                        aiModeUsed = _aiServiceStatus.value.badgeText
                    )
                )
            }
        }
    }

    fun scheduleMaintenanceWork() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val scheduled = withContext(Dispatchers.IO) {
                MaintenanceWorkScheduler.schedulePeriodicMaintenance(app)
            }
            val status = withContext(Dispatchers.IO) {
                runCatching { MaintenanceWorkScheduler.inspectStatus(app) }.getOrNull()
            }
            _maintenanceStatus.value = status
            _actionFeedback.value = if (scheduled) {
                "Scheduled battery-safe WorkManager maintenance (${status?.workStateName ?: "ENQUEUED"})."
            } else {
                "Could not schedule WorkManager periodic task."
            }
        }
    }

    fun scanUserSelectedFile(context: Context, uri: Uri, displayNameHint: String = "selected_file") {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                AppSecurityScanner.scanUserSelectedUri(context, uri, displayNameHint)
            }
            res.onSuccess { report ->
                _lastSecurityScanReport.value = report
                _actionFeedback.value = "Scan verdict: ${report.verdict.label} (${report.trueMimeType})"
            }.onFailure { err ->
                _actionFeedback.value = err.localizedMessage ?: "Selected file could not be scanned."
            }
        }
    }

    fun checkUrlSafety(rawUrl: String) {
        if (rawUrl.isBlank()) return
        val report = AppSecurityScanner.inspectUrlSafety(rawUrl)
        _lastUrlCheckReport.value = report
        _actionFeedback.value = if (report.isSuspicious) {
            "Suspicious link indicators: ${report.reasons.joinToString()}"
        } else {
            "URL host verified normalized (${report.normalizedHost})."
        }
    }

    fun runBlueprintPipeline(queryOrIssueId: String, consentConfirmed: Boolean = false): RepairPipelineReport? {
        val app = getApplication<Application>()
        val report = RepairPipelineEngine.executePipeline(
            context = app,
            queryOrIssueId = queryOrIssueId,
            params = PipelineExecutionParams(
                consentConfirmed = consentConfirmed,
                language = _currentLanguage.value
            )
        )
        _lastPipelineReport.value = report
        refreshVitals()
        return report
    }

    fun startAutoFixScanAndRepair() {
        val beforeVitals = RealDeviceDiagnostics.getDeviceVitals(getApplication())
        _vitals.value = beforeVitals
        _showAutoFixDialog.value = true
        _isAutoFixRunning.value = true
        _autoFixReport.value = null

        viewModelScope.launch {
            try {
                delay(700)
                val report = AutoFixEngine.executeSmartScanAndFix(getApplication(), beforeVitals)
                _autoFixReport.value = report
                _isAutoFixRunning.value = false
                refreshVitals()

                val item = DiagnosticHistoryItem(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    problemTitle = "Smart Auto-Fix & Telemetry Scan",
                    category = "AUTO-FIX",
                    diagnosisSummary = "Ran ${report.autoFixedItems.size} real API checks/repairs (${RealDeviceDiagnostics.formatBytes(report.bytesCleared)} app cache cleared). ${report.restrictedItems.size} OS-restricted items guided.",
                    actionExecuted = "AutoFixEngine (Real Sandbox APIs)",
                    verificationStatus = "Verified Executed",
                    capabilityUsed = "NORMAL_API + MANUAL_SETTINGS",
                    beforeSnapshot = report.beforeSummary,
                    afterSnapshot = report.afterSummary,
                    aiModeUsed = _aiServiceStatus.value.badgeText
                )
                repository.saveHistoryItem(item)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _isAutoFixRunning.value = false
                _showAutoFixDialog.value = false
                _actionFeedback.value = t.localizedMessage ?: "Auto-Fix scan encountered an error."
            }
        }
    }

    fun closeAutoFixDialog() {
        _showAutoFixDialog.value = false
    }

    private fun sendWelcomeMessage() {
        val currentVitals = _vitals.value ?: RealDeviceDiagnostics.getDeviceVitals(getApplication())
        val strings = AppStrings.forLanguage(_currentLanguage.value)
        val welcomeText = strings.welcomeTemplate(currentVitals.brand, currentVitals.model)

        _messages.value = listOf(
            ChatMessage(
                id = WELCOME_MESSAGE_ID,
                sender = SenderType.DOCTOR,
                text = welcomeText,
                actions = emptyList(),
                aiStatus = _aiServiceStatus.value,
                aiStatusDetail = _aiStatusDetail.value
            )
        )
    }

    fun onScreenshotSelected(context: Context, uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                val scaled = if (bitmap.width > 1024 || bitmap.height > 1024) {
                    val scale = 1024f / maxOf(bitmap.width, bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else bitmap

                _attachedScreenshot.value = scaled
                _attachedScreenshotName.value = "screenshot_${System.currentTimeMillis()}.png"
                _actionFeedback.value = "Screenshot attached. Please ensure no OTPs/passwords are visible."
            }
        } catch (e: Exception) {
            _actionFeedback.value = "Could not load image: ${e.localizedMessage}"
        }
    }

    fun removeAttachedScreenshot() {
        _attachedScreenshot.value = null
        _attachedScreenshotName.value = null
    }

    fun sendUserMessage(text: String, isVoiceInput: Boolean = false) {
        val trimmed = text.trim()
        val currentScreenshot = _attachedScreenshot.value
        val currentScreenshotName = _attachedScreenshotName.value

        if (trimmed.isEmpty() && currentScreenshot == null) return
        if (_isAnalyzing.value) return

        refreshVitals()
        val currentVitals = _vitals.value ?: RealDeviceDiagnostics.getDeviceVitals(getApplication())

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = SenderType.USER,
            text = if (trimmed.isNotBlank()) trimmed else "Doctor, please inspect this attached screenshot.",
            attachedScreenshot = currentScreenshot,
            attachedScreenshotName = currentScreenshotName
        )

        removeAttachedScreenshot()
        // Interrupt any ongoing AI voice response if user sends a new message
        if (_isSpeakingValueSafe()) {
            voiceManager.stop()
        }
        _messages.value = _messages.value + userMsg
        _isAnalyzing.value = true

        viewModelScope.launch {
            try {
                val history = _messages.value.takeLast(6).map {
                    val role = if (it.sender == SenderType.USER) "user" else "model"
                    role to it.text
                }

                val response = aiService.diagnoseAndGuide(
                    userProblem = userMsg.text,
                    vitals = currentVitals,
                    screenshot = userMsg.attachedScreenshot,
                    conversationHistory = history,
                    context = getApplication(),
                    language = _currentLanguage.value,
                    sessionContext = _sessionContext.value
                )

                _aiServiceStatus.value = response.aiStatus
                _aiStatusDetail.value = response.aiStatusDetail ?: response.aiStatus.badgeText
                updateSessionContextFromResponse(userMsg.text, response)

                val doctorMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = SenderType.DOCTOR,
                    text = response.replyText,
                    actions = response.actions,
                    repairPlan = response.repairPlan,
                    autonomousBundle = response.autonomousBundle,
                    aiStatus = response.aiStatus,
                    aiStatusDetail = response.aiStatusDetail,
                    responseLanguage = response.responseLanguage,
                    retryPrompt = userMsg.text,
                    retryScreenshot = userMsg.attachedScreenshot,
                    canRetryCloudAi = !response.aiStatus.isCloudAi
                )
                _messages.value = _messages.value + doctorMsg

                if (isVoiceInput || _isVoiceConversationMode.value) {
                    voiceManager.speak(
                        text = doctorMsg.text,
                        messageId = doctorMsg.id,
                        explicitLanguage = response.responseLanguage
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "sendUserMessage failed safely", t)
                _aiServiceStatus.value = AiServiceStatus.ERROR_FALLBACK
                _aiStatusDetail.value = t.localizedMessage ?: "Diagnostic error"
                val detectedLang = LanguageDetector.detectResponseLanguage(userMsg.text, _currentLanguage.value)
                val fallbackText = when (detectedLang) {
                    LanguageDetector.LANG_HINDI ->
                        "क्षमा करें, निदान के दौरान तकनीकी त्रुटि आई (${t.localizedMessage ?: "अज्ञात त्रुटि"})। कृपया पुनः प्रयास करें या नीचे दिए हार्डवेयर परीक्षण चलाएं।"
                    LanguageDetector.LANG_HINGLISH ->
                        "Maaf kijiye, diagnosis ke waqt technical error aa gaya (${t.localizedMessage ?: "Unknown error"}). Aap Retry Cloud AI dabayein ya neeche hardware test chalayein."
                    else ->
                        "A diagnostic error occurred: ${t.localizedMessage ?: "Unknown error"}. Tap Retry Cloud AI or run a hardware test below."
                }
                val errorMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = SenderType.DOCTOR,
                    text = fallbackText,
                    aiStatus = AiServiceStatus.ERROR_FALLBACK,
                    aiStatusDetail = _aiStatusDetail.value,
                    responseLanguage = detectedLang,
                    retryPrompt = userMsg.text,
                    retryScreenshot = userMsg.attachedScreenshot,
                    canRetryCloudAi = true
                )
                _messages.value = _messages.value + errorMsg
                if (isVoiceInput || _isVoiceConversationMode.value) {
                    voiceManager.speak(
                        text = errorMsg.text,
                        messageId = errorMsg.id,
                        explicitLanguage = detectedLang
                    )
                }
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun retryLastCloudAiQuery() {
        val target = _messages.value.lastOrNull {
            it.sender == SenderType.DOCTOR && it.canRetryCloudAi && !it.retryPrompt.isNullOrBlank()
        } ?: return
        retryMessage(target.id)
    }

    fun retryMessage(messageId: String) {
        if (_isAnalyzing.value) return
        val targetMsg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val prompt = targetMsg.retryPrompt ?: return

        refreshVitals()
        val currentVitals = _vitals.value ?: RealDeviceDiagnostics.getDeviceVitals(getApplication())
        _isAnalyzing.value = true

        viewModelScope.launch {
            try {
                val history = _messages.value
                    .filter { it.id != messageId }
                    .takeLast(6)
                    .map {
                        val role = if (it.sender == SenderType.USER) "user" else "model"
                        role to it.text
                    }

                val response = aiService.diagnoseAndGuide(
                    userProblem = prompt,
                    vitals = currentVitals,
                    screenshot = targetMsg.retryScreenshot,
                    conversationHistory = history,
                    context = getApplication(),
                    language = _currentLanguage.value,
                    sessionContext = _sessionContext.value
                )

                _aiServiceStatus.value = response.aiStatus
                _aiStatusDetail.value = response.aiStatusDetail ?: response.aiStatus.badgeText
                updateSessionContextFromResponse(prompt, response)

                val updatedMsg = targetMsg.copy(
                    text = response.replyText,
                    timestamp = System.currentTimeMillis(),
                    actions = response.actions,
                    repairPlan = response.repairPlan,
                    autonomousBundle = response.autonomousBundle,
                    aiStatus = response.aiStatus,
                    aiStatusDetail = response.aiStatusDetail,
                    responseLanguage = response.responseLanguage,
                    canRetryCloudAi = !response.aiStatus.isCloudAi
                )
                _messages.value = _messages.value.map { if (it.id == messageId) updatedMsg else it }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "retryMessage failed safely", t)
                _aiServiceStatus.value = AiServiceStatus.ERROR_FALLBACK
                _aiStatusDetail.value = t.localizedMessage ?: "Retry failed"
                _actionFeedback.value = _aiStatusDetail.value
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun _isSpeakingValueSafe(): Boolean = voiceManager.isSpeaking.value

    fun isLiveSpeechAvailable(): Boolean = speechInputManager.isAvailable()

    fun setVoiceConversationMode(enabled: Boolean) {
        _isVoiceConversationMode.value = enabled
        runCatching {
            sessionPrefs.edit().putBoolean(PREF_KEY_VOICE_MODE, enabled).apply()
        }
        if (!enabled) {
            voiceManager.stop()
            speechInputManager.stopListening()
        }
    }

    fun toggleVoiceConversationMode() {
        setVoiceConversationMode(!_isVoiceConversationMode.value)
    }

    fun interruptSpeechAndListen(onNeedFallbackIntent: () -> Unit) {
        // Immediate barge-in interruption when user starts speaking
        voiceManager.stop()
        if (_isAnalyzing.value) return

        val preferredLang = _messages.value
            .lastOrNull { it.sender == SenderType.DOCTOR && !it.responseLanguage.isNullOrBlank() }
            ?.responseLanguage
            ?: _currentLanguage.value

        speechInputManager.startListening(
            language = preferredLang,
            onInterruptAiSpeech = { voiceManager.stop() },
            onFinalTranscript = { spoken ->
                sendUserMessage(spoken, isVoiceInput = true)
            },
            onErrorOrUnavailable = { useIntentFallback, reason ->
                if (useIntentFallback) {
                    onNeedFallbackIntent()
                } else if (reason.isNotBlank()) {
                    _actionFeedback.value = reason
                }
            }
        )
    }

    fun stopListeningOnly() {
        speechInputManager.stopListening()
    }

    fun toggleSpeech(message: ChatMessage) {
        if (currentlySpeakingMessageId.value == message.id) {
            voiceManager.stop()
        } else {
            voiceManager.speak(
                text = message.text,
                messageId = message.id,
                explicitLanguage = message.responseLanguage ?: _currentLanguage.value
            )
        }
    }

    fun stopSpeech() {
        voiceManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        speechInputManager.destroy()
        voiceManager.shutdown()
    }

    fun handleAction(context: Context, action: DoctorAction) {
        when (action.actionType) {
            ActionType.RUN_AUDIO_TEST -> {
                _activeTest.value = HardwareTestDialogState.AUDIO
                RealDeviceDiagnostics.playAudioDiagnosticBeep()
            }
            ActionType.RUN_VIBRATION_TEST -> {
                _activeTest.value = HardwareTestDialogState.VIBRATION
                RealDeviceDiagnostics.triggerVibrationTest(context)
            }
            ActionType.RUN_SCREEN_TEST -> {
                _activeTest.value = HardwareTestDialogState.SCREEN
            }
            ActionType.RUN_NETWORK_TEST -> {
                _activeTest.value = HardwareTestDialogState.NETWORK
                runPingTest()
            }
            else -> {
                val eligibility = CapabilityEvaluator.evaluate(context, action)
                if (!eligibility.isRegistered || !eligibility.isSupportedOnDevice) {
                    _actionFeedback.value = eligibility.restrictionSummary
                    return
                }

                // Always request explicit confirmation for Level 3 settings, Special Permissions, or Destructive Guarded actions
                if (eligibility.requiresUserConfirmation || action.level == RepairLevel.LEVEL_3 || action.isDestructive) {
                    _pendingApprovalAction.value = action
                } else {
                    executeAndVerifyAction(context, action, explicitDestructiveConfirmed = false)
                }
            }
        }
    }

    fun confirmApprovalAndExecute(context: Context) {
        val action = _pendingApprovalAction.value
        _pendingApprovalAction.value = null
        if (action != null) {
            executeAndVerifyAction(
                context = context,
                action = action,
                explicitDestructiveConfirmed = action.isDestructive
            )
        }
    }

    fun cancelApproval() {
        _pendingApprovalAction.value = null
    }

    fun requestAutonomousExecution(messageId: String) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val bundle = msg.autonomousBundle ?: return
        if (bundle.executableAutoSteps.isEmpty() || bundle.isExecuting) return
        _pendingAutonomousPlanConfirmation.value = messageId to bundle
    }

    fun cancelAutonomousPlanConfirmation() {
        _pendingAutonomousPlanConfirmation.value = null
    }

    fun confirmAndExecuteAutonomousPlan(
        context: Context,
        canWriteOverride: Boolean? = null
    ) {
        val pending = _pendingAutonomousPlanConfirmation.value ?: return
        _pendingAutonomousPlanConfirmation.value = null
        executeAutonomousPlanInternal(context, pending.first, canWriteOverride)
    }

    private fun executeAutonomousPlanInternal(
        context: Context,
        messageId: String,
        canWriteOverride: Boolean? = null
    ) {
        val targetMsg = _messages.value.firstOrNull { it.id == messageId } ?: return
        val initialBundle = targetMsg.autonomousBundle ?: return
        if (initialBundle.executableAutoSteps.isEmpty()) return

        val activeLang = targetMsg.responseLanguage ?: _currentLanguage.value
        val beforeVitals = RealDeviceDiagnostics.getDeviceVitals(context)
        val beforeSnap = AutonomousRepairOrchestrator.formatVitalsSnapshotLine(beforeVitals, activeLang)

        val executingBundle = initialBundle.copy(
            isExecuting = true,
            beforeVitalsSummary = beforeSnap,
            executableAutoSteps = initialBundle.executableAutoSteps.map {
                it.copy(status = AutonomousStepStatus.EXECUTING)
            }
        )
        _messages.value = _messages.value.map {
            if (it.id == messageId) it.copy(autonomousBundle = executingBundle) else it
        }

        viewModelScope.launch {
            try {
                val playbook = PlaybookRagRetriever.findById(lastMatchedPlaybookId)
                val stepOutcomes = mutableListOf<Pair<AutonomousRepairStep, ActionResult>>()

                for (step in initialBundle.executableAutoSteps) {
                    val result = SafeRepairActions.executeActionAsync(
                        context = context,
                        action = step.action,
                        explicitDestructiveConfirmed = false,
                        canWriteOverride = canWriteOverride
                    )
                    triedActionIdsInSession.add(step.action.id)
                    stepOutcomes.add(step to result)
                }

                // Re-collect live telemetry after autonomous repairs
                val afterVitals = RealDeviceDiagnostics.getDeviceVitals(context)
                _vitals.value = afterVitals
                _capabilityMatrix.value = CapabilityMatrix.buildLiveMatrix(context, afterVitals, canWriteOverride)
                val afterSnap = AutonomousRepairOrchestrator.formatVitalsSnapshotLine(afterVitals, activeLang)

                val verifiedSteps = stepOutcomes.map { (step, result) ->
                    val comparison = RepairVerificationEngine.verifyBeforeAfter(
                        action = step.action,
                        before = beforeVitals,
                        after = afterVitals,
                        executionResult = result,
                        playbook = playbook,
                        alreadyTriedActionIds = triedActionIdsInSession,
                        issueId = lastMatchedPlaybookId,
                        language = activeLang
                    )
                    val stepStatus = when (comparison.outcome) {
                        VerificationOutcome.VERIFIED_IMPROVED -> AutonomousStepStatus.VERIFIED_IMPROVED
                        VerificationOutcome.NEEDS_USER_CONFIRMATION -> AutonomousStepStatus.NEEDS_USER_CONFIRMATION
                        VerificationOutcome.NO_MEASURABLE_CHANGE -> AutonomousStepStatus.EXECUTED_NO_SHIFT
                        VerificationOutcome.EXECUTION_FAILED -> AutonomousStepStatus.BLOCKED_OR_FAILED
                    }
                    step.copy(
                        status = stepStatus,
                        resultMessage = result.message,
                        measuredDetails = result.measuredDetails,
                        beforeAfter = comparison
                    )
                }

                // Persist attempted, failed, and succeeded actions so follow-ups never repeat failed steps
                val failedBatchIds = verifiedSteps
                    .filter { it.status == AutonomousStepStatus.BLOCKED_OR_FAILED }
                    .map { it.action.id }
                    .toSet()
                val succeededBatchIds = verifiedSteps
                    .filter { it.status == AutonomousStepStatus.VERIFIED_IMPROVED }
                    .map { it.action.id }
                    .toSet()
                val attemptedBatchIds = verifiedSteps.map { it.action.id }.toSet()
                val curCtx = _sessionContext.value
                persistSessionContext(
                    curCtx.copy(
                        attemptedActionIds = curCtx.attemptedActionIds + attemptedBatchIds,
                        failedActionIds = curCtx.failedActionIds + failedBatchIds,
                        succeededActionIds = curCtx.succeededActionIds + succeededBatchIds
                    )
                )

                val improvedCount = verifiedSteps.count { it.status == AutonomousStepStatus.VERIFIED_IMPROVED }
                val needsConfirmStep = verifiedSteps.firstOrNull { it.status == AutonomousStepStatus.NEEDS_USER_CONFIRMATION }
                val overallSummary = when (activeLang) {
                    LanguageDetector.LANG_HINDI ->
                        "${verifiedSteps.size} स्वचालित चरण पूर्ण ($improvedCount में सत्यापित सुधार) • ${initialBundle.guidedSettingsSteps.size + initialBundle.permissionGatedSteps.size} कार्य उपयोगकर्ता सेटिंग्स/अनुमति पर निर्भर।"
                    LanguageDetector.LANG_HINGLISH ->
                        "${verifiedSteps.size} autonomous steps run hue ($improvedCount verified improved) • ${initialBundle.guidedSettingsSteps.size + initialBundle.permissionGatedSteps.size} steps OS settings/permission par hain."
                    else ->
                        "Executed ${verifiedSteps.size} safe step(s) ($improvedCount verified improved); ${initialBundle.guidedSettingsSteps.size + initialBundle.permissionGatedSteps.size} OS-restricted item(s) remain guided."
                }

                val completedBundle = executingBundle.copy(
                    executableAutoSteps = verifiedSteps,
                    isExecuting = false,
                    isApprovedAndExecuted = true,
                    beforeVitalsSummary = beforeSnap,
                    afterVitalsSummary = afterSnap,
                    overallOutcomeSummary = overallSummary,
                    needsPhysicalConfirmation = needsConfirmStep != null
                )

                val auditDetails = verifiedSteps.joinToString("\n") { s ->
                    "• ${s.action.title}: ${s.resultMessage ?: "Done"} [${s.beforeAfter?.outcome?.label ?: s.status.name}]"
                }

                val summaryHeader = when (activeLang) {
                    LanguageDetector.LANG_HINDI -> "**स्वचालित मरम्मत और सत्यापन रिपोर्ट:**"
                    LanguageDetector.LANG_HINGLISH -> "**Autonomous Repair & Verification Report:**"
                    else -> "**Autonomous Repair & Verification Report:**"
                }
                val reportMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = SenderType.DOCTOR,
                    text = "$summaryHeader\n$overallSummary\n\n$auditDetails",
                    verificationRequestedForAction = needsConfirmStep?.action,
                    responseLanguage = activeLang
                )
                _messages.value = _messages.value.map {
                    if (it.id == messageId) it.copy(autonomousBundle = completedBundle) else it
                } + reportMsg
                _actionFeedback.value = overallSummary

                val executedTitles = verifiedSteps.joinToString(", ") { it.action.title }
                val verificationLabel = when {
                    improvedCount > 0 -> VerificationOutcome.VERIFIED_IMPROVED.label
                    needsConfirmStep != null -> VerificationOutcome.NEEDS_USER_CONFIRMATION.label
                    else -> VerificationOutcome.NO_MEASURABLE_CHANGE.label
                }

                runCatching {
                    repository.saveHistoryItem(
                        DiagnosticHistoryItem(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            problemTitle = initialBundle.problemTitle,
                            category = "AUTONOMOUS REPAIR",
                            diagnosisSummary = "$overallSummary\n$auditDetails",
                            actionExecuted = executedTitles,
                            verificationStatus = verificationLabel,
                            playbookId = lastMatchedPlaybookId,
                            capabilityUsed = verifiedSteps.joinToString("+") { it.capability.badgeLabel }.take(64),
                            beforeSnapshot = beforeSnap,
                            afterSnapshot = afterSnap,
                            aiModeUsed = _aiServiceStatus.value.badgeText
                        )
                    )
                }.onFailure { err ->
                    Log.w(TAG, "Failed to persist autonomous repair audit entry: ${err.localizedMessage}")
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Autonomous batch execution failed safely", t)
                val failedBundle = executingBundle.copy(
                    isExecuting = false,
                    overallOutcomeSummary = t.localizedMessage ?: "Autonomous execution failed"
                )
                _messages.value = _messages.value.map {
                    if (it.id == messageId) it.copy(autonomousBundle = failedBundle) else it
                }
                _actionFeedback.value = t.localizedMessage ?: "Autonomous execution failed"
            }
        }
    }

    private fun executeAndVerifyAction(
        context: Context,
        action: DoctorAction,
        explicitDestructiveConfirmed: Boolean
    ) {
        viewModelScope.launch {
            try {
                val beforeVitals = RealDeviceDiagnostics.getDeviceVitals(context)
                val result: ActionResult = SafeRepairActions.executeActionAsync(
                    context = context,
                    action = action,
                    explicitDestructiveConfirmed = explicitDestructiveConfirmed
                )
                _actionFeedback.value = result.message

                refreshVitals()
                val afterVitals = _vitals.value ?: RealDeviceDiagnostics.getDeviceVitals(context)
                triedActionIdsInSession.add(action.id)

                val activeLang = _messages.value
                    .lastOrNull { it.sender == SenderType.DOCTOR && !it.responseLanguage.isNullOrBlank() }
                    ?.responseLanguage
                    ?: _currentLanguage.value
                val playbook = PlaybookRagRetriever.findById(lastMatchedPlaybookId)
                val comparison: BeforeAfterComparison = RepairVerificationEngine.verifyBeforeAfter(
                    action = action,
                    before = beforeVitals,
                    after = afterVitals,
                    executionResult = result,
                    playbook = playbook,
                    alreadyTriedActionIds = triedActionIdsInSession,
                    issueId = lastMatchedPlaybookId,
                    language = activeLang
                )

                val curCtx = _sessionContext.value
                persistSessionContext(
                    curCtx.copy(
                        attemptedActionIds = curCtx.attemptedActionIds + action.id,
                        failedActionIds = if (comparison.outcome == VerificationOutcome.EXECUTION_FAILED) {
                            curCtx.failedActionIds + action.id
                        } else {
                            curCtx.failedActionIds
                        },
                        succeededActionIds = if (comparison.outcome == VerificationOutcome.VERIFIED_IMPROVED) {
                            curCtx.succeededActionIds + action.id
                        } else {
                            curCtx.succeededActionIds
                        }
                    )
                )

                // Persist audit entry to Room DB
                repository.saveHistoryItem(
                    DiagnosticHistoryItem(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        problemTitle = action.title,
                        category = action.actionType.name.replace("OPEN_", "").replace("RUN_", ""),
                        diagnosisSummary = comparison.technicalMessage,
                        actionExecuted = action.title,
                        verificationStatus = comparison.outcome.label,
                        playbookId = lastMatchedPlaybookId,
                        capabilityUsed = action.capability.badgeLabel,
                        beforeSnapshot = comparison.beforeSummary,
                        afterSnapshot = comparison.afterSummary,
                        aiModeUsed = _aiServiceStatus.value.badgeText
                    )
                )

                val measuredLines = if (result.measuredDetails.isNotEmpty()) {
                    "\n• " + result.measuredDetails.joinToString("\n• ")
                } else ""

                val localizedActionTitle = LocalizedDiagnosticFormatter.localizeAction(action, activeLang).title
                val headerPrefix = when (activeLang) {
                    LanguageDetector.LANG_HINDI -> "**निष्पादन और सत्यापन ($localizedActionTitle):**"
                    LanguageDetector.LANG_HINGLISH -> "**Execution aur Verification ($localizedActionTitle):**"
                    else -> "**Execution & Verification (${action.title}):**"
                }

                val statusMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = SenderType.DOCTOR,
                    text = "$headerPrefix\n${comparison.technicalMessage}$measuredLines",
                    verificationComparison = comparison,
                    verificationRequestedForAction = if (comparison.outcome == VerificationOutcome.NEEDS_USER_CONFIRMATION ||
                        comparison.outcome == VerificationOutcome.NO_MEASURABLE_CHANGE
                    ) action else null,
                    actions = listOfNotNull(comparison.nextAlternativeAction),
                    responseLanguage = activeLang
                )
                _messages.value = _messages.value + statusMsg
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _actionFeedback.value = t.localizedMessage ?: "Action execution failed"
            }
        }
    }

    fun recordVerification(action: DoctorAction, isSuccess: Boolean) {
        val afterVitals = RealDeviceDiagnostics.getDeviceVitals(getApplication())
        _vitals.value = afterVitals
        val status = if (isSuccess) "Verified Fixed" else "Unresolved"
        val isHindi = _currentLanguage.value.equals("Hindi", ignoreCase = true)

        viewModelScope.launch {
            runCatching {
                repository.saveHistoryItem(
                    DiagnosticHistoryItem(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        problemTitle = action.title,
                        category = action.actionType.name.replace("OPEN_", "").replace("RUN_", ""),
                        diagnosisSummary = if (isSuccess) "User confirmed issue resolved after repair." else "Issue persisted after ${action.title}; escalated to next safe option.",
                        actionExecuted = action.title,
                        verificationStatus = status,
                        playbookId = lastMatchedPlaybookId,
                        capabilityUsed = action.capability.badgeLabel,
                        afterSnapshot = "Score ${afterVitals.healthScore}/100 • RAM Free ${RealDeviceDiagnostics.formatBytes(afterVitals.ramFreeBytes)} • Storage Free ${RealDeviceDiagnostics.formatBytes(afterVitals.storageFreeBytes)}",
                        aiModeUsed = _aiServiceStatus.value.badgeText
                    )
                )
            }
        }

        val curCtx = _sessionContext.value
        persistSessionContext(
            curCtx.copy(
                attemptedActionIds = curCtx.attemptedActionIds + action.id,
                failedActionIds = if (isSuccess) curCtx.failedActionIds else (curCtx.failedActionIds + action.id),
                succeededActionIds = if (isSuccess) (curCtx.succeededActionIds + action.id) else curCtx.succeededActionIds
            )
        )

        if (isSuccess) {
            _messages.value = _messages.value + ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = SenderType.DOCTOR,
                text = if (isHindi) {
                    "सुधार सत्यापित! ✅ आपका निदान ऑडिट रिकॉर्ड स्थायी रूप से **लॉग्स** में सहेजा गया है।"
                } else {
                    "Verified Fixed! ✅ Your diagnostic audit record has been saved permanently in **Logs**."
                }
            )
        } else {
            val playbook = PlaybookRagRetriever.findById(lastMatchedPlaybookId)
            val kbIssue = KnowledgeRepository.findIssueById(lastMatchedPlaybookId)
            val nextAlt = RepairVerificationEngine.selectNextSafeAlternative(
                failedOrCurrentActionId = action.id,
                playbook = playbook,
                alreadyTriedActionIds = triedActionIdsInSession,
                issueId = kbIssue?.problemId
            )
            val escalationText = kbIssue?.escalation?.joinToString(" ")
                ?: playbook?.escalationInstructions
                ?: if (isHindi) {
                    "यदि सुरक्षित सॉफ़्टवेयर चरणों से समस्या हल नहीं होती है, तो सेफ मोड में जांचें या अधिकृत सर्विस केंद्र से सलाह लें।"
                } else {
                    "If safe software steps do not resolve the problem, test in Safe Mode or consult an authorized service center."
                }

            _messages.value = _messages.value + ChatMessage(
                id = UUID.randomUUID().toString(),
                sender = SenderType.DOCTOR,
                text = if (isHindi) {
                    "नोट किया गया — **${action.title}** से समस्या हल नहीं हुई। आइए **अगले सुरक्षित समाधान** की ओर बढ़ें।\n\n**आगे का मार्गदर्शन:** $escalationText"
                } else {
                    "Noted — **${action.title}** did not resolve the problem. Let's move to the **next safe solution**.\n\n**Escalation Guide:** $escalationText"
                },
                actions = listOfNotNull(nextAlt)
            )
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            runCatching { repository.clearAll() }
            val isHindi = _currentLanguage.value.equals("Hindi", ignoreCase = true)
            _actionFeedback.value = if (isHindi) "निदान ऑडिट लॉग्स हटाए गए।" else "Diagnostic audit logs cleared."
        }
    }

    fun runPingTest() {
        viewModelScope.launch {
            _isPingRunning.value = true
            _pingResult.value = null
            val result = runCatching { RealDeviceDiagnostics.runNetworkPingTest() }.getOrNull()
            _pingResult.value = result
            _isPingRunning.value = false
        }
    }

    fun closeTestDialog() {
        _activeTest.value = HardwareTestDialogState.NONE
    }

    fun playAudioTestAgain() {
        RealDeviceDiagnostics.playAudioDiagnosticBeep()
    }

    fun triggerVibrationTestAgain(context: Context) {
        RealDeviceDiagnostics.triggerVibrationTest(context)
    }

    fun reportFeedback(message: String) {
        _actionFeedback.value = message
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }

    fun resetChat() {
        voiceManager.stop()
        speechInputManager.stopListening()
        persistSessionContext(TroubleshootingSessionContext())
        refreshVitals()
        sendWelcomeMessage()
    }
}
