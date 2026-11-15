package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiDoctorService
import com.example.data.ai.LanguageDetector
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.model.ActionType
import com.example.data.model.CapabilityTierCategory
import com.example.data.model.ChatMessage
import com.example.data.model.DeviceVitals
import com.example.data.model.RepairPlan
import com.example.data.model.SenderType
import com.example.data.repair.AutonomousRepairOrchestrator
import com.example.data.repair.CapabilityMatrix
import com.example.data.repair.SafeRepairActions
import com.example.data.repair.SafeRepairRegistry
import com.example.ui.AutonomousRepairPlanCard
import com.example.ui.DiagnosticCenterScreen
import com.example.ui.DoctorViewModel
import com.example.ui.components.AutonomousBatchApprovalDialog
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutonomousRepairEngineTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("ai_doctor_session", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun sampleVitals(canWrite: Boolean = false) = DeviceVitals(
        manufacturer = "Samsung",
        model = "Galaxy S24",
        brand = "Samsung",
        androidVersion = "14",
        apiLevel = 34,
        batteryLevel = 65,
        batteryStatus = "Discharging",
        batteryPlugged = "On Battery",
        batteryTempC = 35.0f,
        batteryHealth = "Good",
        storageTotalBytes = 128_000_000_000L,
        storageFreeBytes = 40_000_000_000L,
        storageUsedPercent = 69,
        ramTotalBytes = 8_000_000_000L,
        ramFreeBytes = 2_400_000_000L,
        ramUsedPercent = 70,
        isLowMemory = false,
        networkType = "Wi-Fi",
        isInternetAvailable = true,
        displayResolution = "1080x2340",
        refreshRate = 120f,
        screenTimeoutSeconds = 120,
        uptimeHours = 5.0f,
        healthScore = 82,
        mediaVolumePercent = 0,
        canWriteSystemSettings = canWrite
    )

    @Test
    fun capabilityMatrixCoversAllFiveTiersAndReflectsWriteSettingsState() {
        val vitals = sampleVitals()

        val matrixDenied = CapabilityMatrix.buildLiveMatrix(app, vitals, canWriteOverride = false)
        val categories = matrixDenied.map { it.category }.toSet()
        assertEquals(5, categories.size)
        assertTrue(CapabilityTierCategory.AUTO_EXECUTED in categories)
        assertTrue(CapabilityTierCategory.PERMISSION_GATED in categories)
        assertTrue(CapabilityTierCategory.IN_APP_PANEL_OR_SETTINGS in categories)
        assertTrue(CapabilityTierCategory.OS_SANDBOX_BLOCKED in categories)
        assertTrue(CapabilityTierCategory.HARDWARE_SERVICE_REQUIRED in categories)

        val writeEntryDenied = matrixDenied.first { it.id == "cap_perm_write_settings" }
        assertFalse(writeEntryDenied.isCurrentlyExecutableOnDevice)
        assertTrue(writeEntryDenied.statusDetailEn.contains("PENDING"))

        val matrixGranted = CapabilityMatrix.buildLiveMatrix(app, vitals, canWriteOverride = true)
        val writeEntryGranted = matrixGranted.first { it.id == "cap_perm_write_settings" }
        assertTrue(writeEntryGranted.isCurrentlyExecutableOnDevice)
        assertTrue(writeEntryGranted.statusDetailEn.contains("GRANTED"))
    }

    @Test
    fun safeRepairActionsExecuteRealCacheApkPurgeAudioUnmuteAndDisplayOptimize() = runBlocking {
        // 1. Cache + stale APK cleanup in filesDir/updates
        val cacheTmp = File(app.cacheDir, "stale_cache_test.tmp").apply {
            writeBytes(ByteArray(2048) { 1 })
        }
        val updatesDir = File(app.filesDir, "updates").apply { mkdirs() }
        val staleApk = File(updatesDir, "AI-Smartphone-Doctor-old.apk").apply {
            writeBytes(ByteArray(4096) { 2 })
        }
        val clearCacheAction = SafeRepairRegistry.findAction("auto_clear_own_cache")!!
        val cacheResult = SafeRepairActions.executeActionAsync(app, clearCacheAction)
        assertTrue(cacheResult.isSuccess)
        assertTrue(cacheResult.executedAutomatically)
        assertTrue(cacheResult.verifiedMetricImproved)
        assertFalse(cacheTmp.exists())
        assertFalse(staleApk.exists())

        // 2. Real AudioManager unmute & routing reset
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        val fixAudioAction = SafeRepairRegistry.findAction("auto_fix_audio_output")!!
        val audioResult = SafeRepairActions.executeActionAsync(app, fixAudioAction)
        assertTrue(audioResult.isSuccess)
        assertTrue(audioResult.executedAutomatically)
        assertTrue(audioResult.verifiedMetricImproved)
        assertEquals(AudioManager.MODE_NORMAL, audioManager.mode)
        assertTrue(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0)

        // 3. Display Power Optimizer (WRITE_SETTINGS denied vs granted)
        val displayAction = SafeRepairRegistry.findAction("auto_optimize_display_power")!!
        val deniedRes = SafeRepairActions.executeActionAsync(app, displayAction, canWriteOverride = false)
        assertFalse(deniedRes.isSuccess)
        assertTrue(deniedRes.requiresSpecialPermissionGrant)

        val grantedRes = SafeRepairActions.executeActionAsync(app, displayAction, canWriteOverride = true)
        assertTrue(grantedRes.isSuccess)
        assertTrue(grantedRes.executedAutomatically)
        assertTrue(grantedRes.verifiedMetricImproved)
        assertEquals(
            30_000,
            Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
        )
        assertEquals(
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)
        )

        // 4. Settings intents (including Android Q+ Panel resolution / fallback)
        val wifiIntent = SafeRepairActions.buildSettingsIntent(app, ActionType.OPEN_WIFI_SETTINGS)
        assertNotNull(wifiIntent)
        assertFalse(wifiIntent?.action.isNullOrBlank())
        val soundIntent = SafeRepairActions.buildSettingsIntent(app, ActionType.OPEN_SOUND_SETTINGS)
        assertNotNull(soundIntent)
        assertFalse(soundIntent?.action.isNullOrBlank())
    }

    @Test
    fun autonomousRepairOrchestratorBuildsCategorizedBundleAndSkipsAmbiguousQueries() {
        val vitals = sampleVitals()
        val oem = DeviceCompatibilityEngine.resolveProfile("Samsung", "Samsung", "Galaxy S24", 34, "14")
        val recommended = listOfNotNull(
            SafeRepairRegistry.findAction("auto_clear_own_cache"),
            SafeRepairRegistry.findAction("auto_optimize_display_power"),
            SafeRepairRegistry.findAction("repair_battery_saver"),
            SafeRepairRegistry.findAction("test_audio")
        )

        // With WRITE_SETTINGS denied -> display optimizer goes to permissionGatedSteps
        val deniedBundle = AutonomousRepairOrchestrator.buildBundle(
            context = app,
            userProblem = "My phone battery is draining fast, hanging, and speaker sound has issue",
            vitals = vitals,
            recommendedActions = recommended,
            repairPlan = RepairPlan(
                playbookId = "pb_battery_drain",
                problemTitle = "Battery & Performance Drain",
                confidenceLevel = "High",
                confirmedEvidence = emptyList(),
                possibleCauses = emptyList(),
                diagnosticQuestions = emptyList(),
                unverifiedAssumptions = emptyList(),
                hardwareFaultIndicators = emptyList(),
                escalationInstructions = "Check Safe Mode",
                verificationMethod = "Re-check battery & RAM"
            ),
            oemProfile = oem,
            language = LanguageDetector.LANG_ENGLISH,
            canWriteOverride = false
        )
        assertNotNull(deniedBundle)
        assertTrue(deniedBundle!!.executableAutoSteps.any { it.action.id == "auto_clear_own_cache" })
        assertTrue(deniedBundle.executableAutoSteps.any { it.action.id == "auto_fix_audio_output" })
        assertTrue(deniedBundle.executableAutoSteps.none { it.action.id == "test_audio" })
        assertTrue(deniedBundle.permissionGatedSteps.any { it.action.id == "auto_optimize_display_power" })
        assertTrue(deniedBundle.guidedSettingsSteps.any { it.action.id == "repair_battery_saver" })
        assertTrue(deniedBundle.osBlockedNotices.isNotEmpty())
        assertTrue(deniedBundle.hardwareEscalationNotes.isNotEmpty())

        // With WRITE_SETTINGS granted -> display optimizer moves into executableAutoSteps
        val grantedBundle = AutonomousRepairOrchestrator.buildBundle(
            context = app,
            userProblem = "My phone battery is draining fast",
            vitals = vitals,
            recommendedActions = recommended,
            repairPlan = null,
            oemProfile = oem,
            language = LanguageDetector.LANG_HINDI,
            canWriteOverride = true
        )
        assertNotNull(grantedBundle)
        assertTrue(grantedBundle!!.executableAutoSteps.any { it.action.id == "auto_optimize_display_power" })
        assertTrue(grantedBundle.permissionGatedSteps.isEmpty())

        // Ambiguous query (CLARIFY-REQUIRED) must return null
        val clarifyBundle = AutonomousRepairOrchestrator.buildBundle(
            context = app,
            userProblem = "help",
            vitals = vitals,
            recommendedActions = recommended,
            repairPlan = RepairPlan(
                playbookId = "CLARIFY-REQUIRED",
                problemTitle = "Clarification",
                confidenceLevel = "Low",
                confirmedEvidence = emptyList(),
                possibleCauses = emptyList(),
                diagnosticQuestions = emptyList(),
                unverifiedAssumptions = emptyList(),
                hardwareFaultIndicators = emptyList(),
                escalationInstructions = "",
                verificationMethod = ""
            ),
            oemProfile = oem,
            language = LanguageDetector.LANG_ENGLISH
        )
        assertNull(clarifyBundle)
    }

    @Test
    fun endToEndDoctorViewModelAutonomousBatchExecutionUpdatesStateAndPersistsHistory() = runBlocking {
        // Seed muted audio + cache file so real execution has measurable work to perform
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        File(app.cacheDir, "vm_autonomous_test.tmp").writeBytes(ByteArray(1024) { 9 })

        val viewModel = DoctorViewModel(app).apply {
            aiService = AiDoctorService(aiProxyUrl = "")
        }

        viewModel.sendUserMessage("Speaker sound is not coming and phone is hanging slow")
        waitForViewModelIdle(viewModel)

        val aiMsg = viewModel.messages.value.last { it.sender == SenderType.DOCTOR }
        assertNotNull("Expected autonomousBundle on doctor diagnosis", aiMsg.autonomousBundle)
        assertTrue(aiMsg.autonomousBundle!!.executableAutoSteps.isNotEmpty())

        viewModel.requestAutonomousExecution(aiMsg.id)
        assertNotNull(viewModel.pendingAutonomousPlanConfirmation.value)

        viewModel.confirmAndExecuteAutonomousPlan(app, canWriteOverride = true)
        waitForAutonomousComplete(viewModel, aiMsg.id)

        val updatedAiMsg = viewModel.messages.value.first { it.id == aiMsg.id }
        val completedBundle = updatedAiMsg.autonomousBundle!!
        assertTrue(completedBundle.isApprovedAndExecuted)
        assertFalse(completedBundle.isExecuting)
        assertNotNull(completedBundle.beforeVitalsSummary)
        assertNotNull(completedBundle.afterVitalsSummary)
        assertNotNull(completedBundle.overallOutcomeSummary)

        // Verification report message appended
        val lastMsg = viewModel.messages.value.last()
        assertEquals(SenderType.DOCTOR, lastMsg.sender)
        assertTrue(lastMsg.text.contains("Autonomous Repair & Verification Report"))

        // History persisted in Room DB
        val history = viewModel.historyItems.first { it.isNotEmpty() }
        assertTrue(history.any { it.category == "AUTONOMOUS REPAIR" })
    }

    @Test
    fun uiRendersAutonomousRepairCardBatchDialogAndCapabilityMatrixInEnglishAndHindi() {
        val vitals = sampleVitals()
        val oem = DeviceCompatibilityEngine.resolveProfile("Samsung", "Samsung", "Galaxy S24", 34, "14")
        val bundle = AutonomousRepairOrchestrator.buildBundle(
            context = app,
            userProblem = "मेरा फोन बहुत गर्म और धीमा है",
            vitals = vitals,
            recommendedActions = listOfNotNull(
                SafeRepairRegistry.findAction("auto_clear_own_cache"),
                SafeRepairRegistry.findAction("auto_optimize_display_power"),
                SafeRepairRegistry.findAction("repair_battery_saver")
            ),
            repairPlan = null,
            oemProfile = oem,
            language = LanguageDetector.LANG_HINDI,
            canWriteOverride = false
        )!!

        var confirmed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AutonomousRepairPlanCard(
                        messageId = "m1",
                        bundle = bundle,
                        strings = AppStrings.Hindi,
                        isAnalyzing = false,
                        onAuthorizeAndRun = { confirmed = true },
                        onActionClick = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("autonomous_repair_plan_card_m1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("run_autonomous_plan_btn_m1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(AppStrings.Hindi.autonomousRunBatchBtn).assertIsDisplayed()
        composeTestRule.onNodeWithTag("run_autonomous_plan_btn_m1").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun uiBatchApprovalDialogAndCapabilityMatrixExpandInDiagnosticCenter() {
        val viewModel = DoctorViewModel(app).apply {
            aiService = AiDoctorService(aiProxyUrl = "")
        }

        composeTestRule.setContent {
            MyApplicationTheme {
                DiagnosticCenterScreen(
                    viewModel = viewModel,
                    onNavigateToChatWithQuery = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("diagnostic_lazy_column")
            .performScrollToNode(hasTestTag("capability_matrix_card"))
        composeTestRule.onNodeWithTag("capability_matrix_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("capability_matrix_toggle").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("diagnostic_lazy_column")
            .performScrollToNode(hasText(AppStrings.English.capTierAutoLabel))
        composeTestRule.onNodeWithText(AppStrings.English.capTierAutoLabel).assertIsDisplayed()

        composeTestRule.onNodeWithTag("diagnostic_lazy_column")
            .performScrollToNode(hasText(AppStrings.English.capTierOsBlockedLabel))
        composeTestRule.onNodeWithText(AppStrings.English.capTierOsBlockedLabel).assertIsDisplayed()

        composeTestRule.onNodeWithTag("diagnostic_lazy_column")
            .performScrollToNode(hasText(AppStrings.English.capTierHardwareLabel))
        composeTestRule.onNodeWithText(AppStrings.English.capTierHardwareLabel).assertIsDisplayed()
    }

    @Test
    fun batchApprovalDialogDisplaysStepsAndTriggersConfirmCallback() {
        val vitals = sampleVitals()
        val oem = DeviceCompatibilityEngine.resolveProfile("Google", "Google", "Pixel 8", 34, "14")
        val bundle = AutonomousRepairOrchestrator.buildBundle(
            context = app,
            userProblem = "Phone hanging and audio muted",
            vitals = vitals,
            recommendedActions = listOfNotNull(
                SafeRepairRegistry.findAction("auto_clear_own_cache"),
                SafeRepairRegistry.findAction("auto_fix_audio_output")
            ),
            repairPlan = null,
            oemProfile = oem,
            language = LanguageDetector.LANG_ENGLISH,
            canWriteOverride = true
        )!!

        var approved = false
        composeTestRule.setContent {
            MyApplicationTheme {
                AutonomousBatchApprovalDialog(
                    bundle = bundle,
                    strings = AppStrings.English,
                    onConfirm = { approved = true },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("confirm_batch_autonomous_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_batch_autonomous_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_batch_autonomous_btn").performClick()
        assertTrue(approved)
    }

    private fun waitForViewModelIdle(viewModel: DoctorViewModel) = runBlocking {
        var elapsed = 0L
        while (viewModel.isAnalyzing.value && elapsed < 15_000L) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(40)
            elapsed += 40
        }
        ShadowLooper.idleMainLooper()
        assertFalse(viewModel.isAnalyzing.value)
    }

    private fun waitForAutonomousComplete(viewModel: DoctorViewModel, messageId: String) = runBlocking {
        var elapsed = 0L
        while (elapsed < 15_000L) {
            ShadowLooper.idleMainLooper()
            val bundle = viewModel.messages.value.firstOrNull { it.id == messageId }?.autonomousBundle
            if (bundle != null && bundle.isApprovedAndExecuted && !bundle.isExecuting) break
            Thread.sleep(50)
            elapsed += 50
        }
        ShadowLooper.idleMainLooper()
        val doneBundle = viewModel.messages.value.first { it.id == messageId }.autonomousBundle!!
        assertTrue("Expected autonomous batch execution to finish cleanly", doneBundle.isApprovedAndExecuted)
    }
}
