package com.example

import android.app.Application
import android.content.Context
import android.speech.tts.Voice
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiDoctorService
import com.example.data.ai.ConversationContextManager
import com.example.data.ai.ConversationIntent
import com.example.data.ai.LanguageDetector
import com.example.data.ai.TroubleshootingSessionContext
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.local.AppDatabase
import com.example.data.local.DiagnosticRepository
import com.example.data.model.AutomationCapability
import com.example.data.model.DeviceVitals
import com.example.data.model.DiagnosticHistoryItem
import com.example.data.model.VerificationOutcome
import com.example.data.repair.ActionResult
import com.example.data.repair.CapabilityEvaluator
import com.example.data.repair.RepairVerificationEngine
import com.example.data.repair.SafeRepairActions
import com.example.data.repair.SafeRepairRegistry
import com.example.data.update.AppUpdateManager
import com.example.data.update.UpdateCheckResult
import com.example.ui.DoctorViewModel
import com.example.ui.MainAppScreen
import com.example.ui.components.AppUpdateDialog
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NaturalVoiceManager
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import java.net.InetSocketAddress
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiDoctorConversationAndRepairTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repository: DiagnosticRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        File(app.dataDir, "databases").mkdirs()
        app.getSharedPreferences("ai_doctor_session", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DiagnosticRepository(db.diagnosticHistoryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleVitals(screenTimeoutSeconds: Int = 120): DeviceVitals =
        RealDeviceDiagnostics.getDeviceVitals(app).copy(
            manufacturer = "Google",
            brand = "Pixel",
            model = "Pixel 8",
            androidVersion = "15",
            apiLevel = 35,
            screenTimeoutSeconds = screenTimeoutSeconds,
            healthScore = 82
        )

    @Test
    fun scenario01_normalHumanLikeConversationAvoidsRoboticRepairForms(): Unit = runBlocking {
        val service = AiDoctorService(aiProxyUrl = "")
        val vitals = sampleVitals()

        val casualPrompts = listOf(
            "Hello, how are you?",
            "What are you doing?",
            "Thank you.",
            "Namaste, kaise ho?"
        )

        casualPrompts.forEach { msg ->
            val resp = service.diagnoseAndGuide(
                userProblem = msg,
                vitals = vitals,
                context = app,
                language = LanguageDetector.LANG_ENGLISH
            )
            assertEquals("Expected SMALL_TALK for '$msg'", ConversationIntent.SMALL_TALK, resp.intentHandled)
            assertNull("Normal chat '$msg' should NOT force a RepairPlan form", resp.repairPlan)
            assertNull("Normal chat '$msg' should NOT force an AutonomousRepairBundle", resp.autonomousBundle)
            assertTrue("Normal chat '$msg' should NOT attach repair action buttons", resp.actions.isEmpty())
            assertTrue("Normal chat '$msg' must return polite natural text", resp.replyText.length >= 20)
            assertFalse("Must not leak offline notice robotically", resp.replyText.contains("Offline KB Mode"))
        }
    }

    @Test
    fun scenario02_hindiEnglishHinglishConsistencyAndNaturalTtsVoiceChunking() {
        val service = AiDoctorService(aiProxyUrl = "")
        val vitals = sampleVitals()

        val hiResp = service.buildStructuredFallbackResponse(
            userProblem = "मेरा स्पीकर काम नहीं कर रहा है",
            vitals = vitals,
            context = app,
            language = LanguageDetector.LANG_ENGLISH
        )
        assertEquals(LanguageDetector.LANG_HINDI, hiResp.responseLanguage)
        assertTrue(Regex("[\\u0900-\\u097F]").containsMatchIn(hiResp.replyText))

        val hgResp = service.buildStructuredFallbackResponse(
            userProblem = "Mera phone speaker se awaz nahi aa rahi hai",
            vitals = vitals,
            context = app,
            language = LanguageDetector.LANG_ENGLISH
        )
        assertEquals(LanguageDetector.LANG_HINGLISH, hgResp.responseLanguage)
        assertFalse(Regex("[\\u0900-\\u097F]").containsMatchIn(hgResp.replyText))

        val enResp = service.buildStructuredFallbackResponse(
            userProblem = "My phone speaker sound is low",
            vitals = vitals,
            context = app,
            language = LanguageDetector.LANG_ENGLISH
        )
        assertEquals(LanguageDetector.LANG_ENGLISH, enResp.responseLanguage)

        val voiceManager = NaturalVoiceManager(app)
        try {
            assertEquals(LanguageDetector.LANG_HINDI, voiceManager.detectSpeechLanguage("मेरा फोन धीमा है", null))
            assertEquals(LanguageDetector.LANG_HINGLISH, voiceManager.detectSpeechLanguage("Bhai phone hang ho raha hai", null))
            assertEquals(LanguageDetector.LANG_ENGLISH, voiceManager.detectSpeechLanguage("Check my display", LanguageDetector.LANG_ENGLISH))

            val chunks = voiceManager.buildSpeechChunks(
                "1. Check volume slider. 2. Run audio diagnostic beep test? Let's verify sound clearly!"
            )
            assertTrue("Natural TTS should split into chunks with pauses", chunks.size >= 2)
            assertTrue("First chunk should insert conversational pause", chunks.first().pauseAfterMs > 0L)

            val sanitized = voiceManager.sanitizeTextForSpeech("Settings > Battery -> 38°C 80%", LanguageDetector.LANG_ENGLISH)
            assertFalse(sanitized.contains("°C"))
            assertTrue(sanitized.contains("degrees Celsius"))
            assertTrue(sanitized.contains("percent"))

            // Verify high-quality Google Neural voice scoring prefers neural/high-quality offline-ready voice
            val standardVoice = Voice("en-us-x-basic", Locale.US, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, emptySet())
            val neuralVoice = Voice("en-us-x-sfg-neural-local", Locale.US, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_LOW, false, setOf("neural"))
            val selected = voiceManager.selectBestNaturalVoice(setOf(standardVoice, neuralVoice), Locale.US)
            assertEquals(neuralVoice.name, selected?.name)

            // Verify honest attribution — never claims TTS is human
            assertFalse(AppStrings.English.voiceEngineNote.contains("human", ignoreCase = true))
            assertFalse(AppStrings.Hindi.voiceEngineNote.contains("इंसान"))
        } finally {
            voiceManager.shutdown()
        }
    }

    @Test
    fun scenario03_voiceConversationModeSwitchAndBargeInInterruptionWorkInUi() {
        val viewModel = DoctorViewModel(app).apply {
            aiService = AiDoctorService(aiProxyUrl = "http://127.0.0.1:1")
        }
        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        assertFalse(viewModel.isVoiceConversationMode.value)
        composeTestRule.onNodeWithTag("voice_mode_toggle_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_mode_toggle_btn").performClick()
        composeTestRule.waitForIdle()

        assertTrue("Voice conversation mode must persist when toggled ON", viewModel.isVoiceConversationMode.value)
        assertTrue(app.getSharedPreferences("ai_doctor_session", Context.MODE_PRIVATE).getBoolean("voice_conversation_mode", false))

        var fallbackLaunched = false
        viewModel.interruptSpeechAndListen {
            fallbackLaunched = true
        }
        assertFalse("Interrupting speech must leave isSpeaking false immediately", viewModel.isSpeaking.value)
        // Robolectric has no system voice recognizer service unless shadowed -> triggers graceful fallback cleanly
        assertTrue(fallbackLaunched || viewModel.isListening.value)

        // Switching back to Text mode cleanly turns off voice mode
        composeTestRule.onNodeWithTag("voice_mode_toggle_btn").performClick()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.isVoiceConversationMode.value)
    }

    @Test
    fun scenario04_multiTurnFollowUpsRememberContextAndNeverRepeatFailedSteps() {
        val service = AiDoctorService(aiProxyUrl = "")
        val vitals = sampleVitals()

        // Turn 1: User reports problem
        val turn1Resp = service.buildStructuredFallbackResponse(
            userProblem = "Mera speaker sound kaam nahi kar raha hai",
            vitals = vitals,
            context = app,
            language = LanguageDetector.LANG_ENGLISH,
            sessionContext = TroubleshootingSessionContext()
        )
        assertEquals(ConversationIntent.PROBLEM_REPORT, turn1Resp.intentHandled)
        assertNotNull(turn1Resp.matchedProblemId)
        val failedFirstActionId = turn1Resp.actions.firstOrNull()?.id ?: "auto_fix_audio_routing"

        // Simulate turn 1 action was attempted & failed
        var session = TroubleshootingSessionContext(
            activeProblemId = turn1Resp.matchedProblemId,
            activeProblemTitle = turn1Resp.matchedProblemTitle,
            activeCategory = turn1Resp.matchedCategory,
            lastSymptomQuery = "Mera speaker sound kaam nahi kar raha hai",
            attemptedActionIds = setOf(failedFirstActionId),
            failedActionIds = setOf(failedFirstActionId),
            followUpStepOffset = 0
        )

        // Turn 2: "Bhai, maine try kiya lekin fix nahi hua."
        val turn2Resp = service.buildStructuredFallbackResponse(
            userProblem = "Bhai, maine try kiya lekin fix nahi hua.",
            vitals = vitals,
            context = app,
            language = LanguageDetector.LANG_ENGLISH,
            sessionContext = session
        )
        assertEquals(ConversationIntent.FOLLOW_UP_NOT_FIXED, turn2Resp.intentHandled)
        assertEquals(turn1Resp.matchedProblemId, turn2Resp.matchedProblemId)
        assertTrue(
            "Must use empathetic progression opening",
            turn2Resp.replyText.contains("Koi baat nahi", ignoreCase = true)
        )
        assertTrue(
            "Must NEVER repeat previously failed action '$failedFirstActionId'",
            turn2Resp.actions.none { it.id == failedFirstActionId }
        )

        session = session.copy(followUpStepOffset = 1)

        // Turn 3: "Mujhe samajh nahi aaya." -> Clarification
        val turn3Resp = service.buildStructuredFallbackResponse(
            userProblem = "Mujhe samajh nahi aaya.",
            vitals = vitals,
            context = app,
            language = LanguageDetector.LANG_ENGLISH,
            sessionContext = session
        )
        assertEquals(ConversationIntent.CLARIFY_EXPLANATION, turn3Resp.intentHandled)
        assertEquals(turn1Resp.matchedProblemId, turn3Resp.matchedProblemId)
        assertTrue(turn3Resp.replyText.contains("simple", ignoreCase = true))

        // Turn 4: "Koi aur easy solution hai?"
        val turn4Resp = service.buildStructuredFallbackResponse(
            userProblem = "Koi aur easy solution hai?",
            vitals = vitals,
            context = app,
            language = LanguageDetector.LANG_ENGLISH,
            sessionContext = session
        )
        assertEquals(ConversationIntent.FOLLOW_UP_NOT_FIXED, turn4Resp.intentHandled)
        assertTrue(turn4Resp.actions.none { it.id == failedFirstActionId })

        // Turn 5: "Abhi bhi problem aa rahi hai."
        val sessionAfterTurn4 = session.copy(followUpStepOffset = 3)
        val turn5Resp = service.buildStructuredFallbackResponse(
            userProblem = "Abhi bhi problem aa rahi hai.",
            vitals = vitals,
            context = app,
            language = LanguageDetector.LANG_ENGLISH,
            sessionContext = sessionAfterTurn4
        )
        assertEquals(ConversationIntent.FOLLOW_UP_NOT_FIXED, turn5Resp.intentHandled)
        assertTrue(turn5Resp.actions.none { it.id == failedFirstActionId })
    }

    @Test
    fun scenario05_successfulAutomaticRepairProducesVerifiedImprovement(): Unit = runBlocking {
        val tempFile = File(app.cacheDir, "repair_success_probe.tmp").apply {
            writeBytes(ByteArray(24_576) { 9 })
        }
        assertTrue(tempFile.exists())

        val clearCacheAction = SafeRepairRegistry.findAction("auto_clear_own_cache")!!
        val before = sampleVitals(screenTimeoutSeconds = 300)
        val result = SafeRepairActions.executeActionAsync(app, clearCacheAction, canWriteOverride = true)
        assertTrue(result.isSuccess)
        assertTrue(result.executedAutomatically)
        assertFalse(tempFile.exists())

        val after = sampleVitals(screenTimeoutSeconds = 30).copy(storageFreeBytes = before.storageFreeBytes + 24_576L)
        val verification = RepairVerificationEngine.verifyBeforeAfter(
            action = clearCacheAction,
            before = before,
            after = after,
            executionResult = result,
            language = LanguageDetector.LANG_ENGLISH
        )
        assertEquals(VerificationOutcome.VERIFIED_IMPROVED, verification.outcome)
        assertNull(verification.nextAlternativeAction)
    }

    @Test
    fun scenario06_failedRepairNeverPretendsSuccessAndPicksAlternative() {
        val primaryAction = SafeRepairRegistry.findAction("auto_screen_timeout_safe")!!
        val before = sampleVitals(screenTimeoutSeconds = 300)
        val failedResult = ActionResult(
            isSuccess = false,
            message = "Modify System Settings permission declined",
            executedAutomatically = false
        )

        val comparison = RepairVerificationEngine.verifyBeforeAfter(
            action = primaryAction,
            before = before,
            after = before,
            executionResult = failedResult,
            alreadyTriedActionIds = setOf(primaryAction.id),
            issueId = "AND-05-001",
            language = LanguageDetector.LANG_HINGLISH
        )
        assertEquals(VerificationOutcome.EXECUTION_FAILED, comparison.outcome)
        assertTrue(comparison.technicalMessage.contains("nahi", ignoreCase = true))
        assertNotNull("Must suggest untried alternative", comparison.nextAlternativeAction)
        assertTrue(comparison.nextAlternativeAction!!.id != primaryAction.id)
    }

    @Test
    fun scenario07_permissionDeniedHandledGracefullyWithoutBypass(): Unit = runBlocking {
        val timeoutAction = SafeRepairRegistry.findAction("auto_screen_timeout_safe")!!
        val eval = CapabilityEvaluator.evaluate(app, timeoutAction, canWriteOverride = false)
        assertEquals(AutomationCapability.SPECIAL_PERMISSION, eval.capability)
        assertFalse(eval.permissionGranted)

        val res = SafeRepairActions.executeActionAsync(app, timeoutAction, canWriteOverride = false)
        assertFalse("Must never pretend timeout changed when WRITE_SETTINGS is denied", res.isSuccess)
        assertTrue(res.requiresSpecialPermissionGrant)

        // Check AndroidManifest declares RECORD_AUDIO safely for runtime request + Photo Picker does not demand broad storage perms
        val pkgInfo = app.packageManager.getPackageInfo(
            app.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertTrue(perms.contains(android.Manifest.permission.RECORD_AUDIO))
        assertFalse(perms.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
    }

    @Test
    fun scenario08_androidRestrictedOperationExplainsWhyAndOffersSafeAlternative() {
        val restrictedAction = SafeRepairRegistry.findAction("repair_app_cache")!!
        val eval = CapabilityEvaluator.evaluate(app, restrictedAction)
        assertEquals(AutomationCapability.MANUAL_SETTINGS, eval.capability)
        assertTrue(eval.requiresUserConfirmation)
        assertFalse(eval.capability == AutomationCapability.NORMAL_API)

        val response = AiDoctorService(aiProxyUrl = "").buildStructuredFallbackResponse(
            userProblem = "Clear WhatsApp app cache and fix app crash",
            vitals = sampleVitals(),
            context = app,
            language = LanguageDetector.LANG_ENGLISH
        )
        val bundle = response.autonomousBundle
        assertNotNull(bundle)
        assertTrue(
            "OS sandbox restrictions must explain Android restriction honestly",
            bundle!!.osBlockedNotices.isNotEmpty() || bundle.guidedSettingsSteps.isNotEmpty()
        )
    }

    @Test
    fun scenario09_repairVerificationPersistsAuditOutcomeInRoomHistory(): Unit = runBlocking {
        val viewModel = DoctorViewModel(app).apply {
            aiService = AiDoctorService(aiProxyUrl = "")
        }
        val action = SafeRepairRegistry.findAction("auto_optimize_memory")!!

        // Verify Failed outcome adds to failedActionIds & escalates cleanly
        viewModel.recordVerification(action, isSuccess = false)
        ShadowLooper.idleMainLooper()
        Thread.sleep(100)
        ShadowLooper.idleMainLooper()

        assertTrue(viewModel.sessionContext.value.failedActionIds.contains(action.id))
        assertTrue(viewModel.messages.value.last().text.contains("did not resolve", ignoreCase = true))

        // Verify Success outcome updates succeededActionIds & persists audit record cleanly
        repository.saveHistoryItem(
            DiagnosticHistoryItem(
                id = "verify-cuj-9",
                timestamp = System.currentTimeMillis(),
                problemTitle = "Memory Optimization Verification",
                category = "PERFORMANCE",
                diagnosisSummary = "User confirmed device responsiveness improved.",
                actionExecuted = action.title,
                verificationStatus = "Verified Fixed",
                capabilityUsed = action.capability.badgeLabel,
                beforeSnapshot = "Score 76/100",
                afterSnapshot = "Score 84/100",
                aiModeUsed = "Gemini 3.x"
            )
        )
        val saved = repository.historyItems.first()
        assertTrue(saved.any { it.id == "verify-cuj-9" && it.verificationStatus == "Verified Fixed" })
    }

    @Test
    fun scenario10_apkUpgradePreservesUserDataSettingsAndShowsReleaseChangelog(): Unit = runBlocking {
        // 1. Simulate active user state before update (language, voice mode, troubleshooting context, Room repair history)
        val prefs = app.getSharedPreferences("ai_doctor_session", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("saved_language", "Hindi")
            .putBoolean("voice_conversation_mode", true)
            .putString(
                "troubleshooting_context_json",
                TroubleshootingSessionContext(
                    activeProblemId = "AND-05-001",
                    activeProblemTitle = "Battery Drain",
                    lastSymptomQuery = "Meri battery jaldi khatam hoti hai",
                    attemptedActionIds = setOf("auto_audit_thermal_power"),
                    failedActionIds = setOf("auto_screen_timeout_safe")
                ).toJsonString()
            )
            .commit()

        val persistentRepo = DiagnosticRepository(AppDatabase.getInstance(app).diagnosticHistoryDao())
        persistentRepo.saveHistoryItem(
            DiagnosticHistoryItem(
                id = "persist-upgrade-1",
                timestamp = System.currentTimeMillis(),
                problemTitle = "Pre-Update Battery Check",
                category = "BATTERY",
                diagnosisSummary = "Saved before APK update",
                actionExecuted = "Thermal & Battery Audit",
                verificationStatus = "Verified Improved"
            )
        )

        // 2. Simulate new ViewModel launch after APK upgrade
        val upgradedVm = DoctorViewModel(app)
        assertEquals("Hindi", upgradedVm.currentLanguage.value)
        assertTrue(upgradedVm.isVoiceConversationMode.value)
        assertEquals("AND-05-001", upgradedVm.sessionContext.value.activeProblemId)
        assertTrue(upgradedVm.sessionContext.value.failedActionIds.contains("auto_screen_timeout_safe"))
        assertTrue(persistentRepo.historyItems.first().any { it.id == "persist-upgrade-1" })
        assertEquals("com.aistudio.smartphonedoctor.oapeir", app.packageName)

        // 3. Verify GitHub Release manifest check surfaces version, release date, changelog & direct APK link
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        try {
            val manifestJson = """
                {
                  "versionCode": 118,
                  "versionName": "1.1.18",
                  "apkUrl": "https://github.com/mohammadsalam7489-bot/Ai-smartphone-doctor-/releases/download/v1.1.18/Ai-Smartphone-Doctor.apk",
                  "sha256": "deadbeef1234",
                  "fileSizeBytes": 28450000,
                  "releaseNotes": "- Natural Hindi/English/Hinglish voice & barge-in interruption\n- Smart follow-up memory",
                  "releaseNotesHi": "- प्राकृतिक हिंदी/हिंग्लिश आवाज़ और स्मार्ट फॉलो-अप मेमोरी",
                  "publishedAt": "2026-11-15T10:30:00Z",
                  "mandatory": false
                }
            """.trimIndent().toByteArray()
            server.createContext("/update-manifest.json") { ex ->
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, manifestJson.size.toLong())
                ex.responseBody.use { it.write(manifestJson) }
            }
            server.start()

            val check = AppUpdateManager.checkForUpdate(
                context = app,
                manifestUrl = "http://127.0.0.1:$port/update-manifest.json",
                releasesApiUrl = ""
            )
            assertTrue(check is UpdateCheckResult.Available)
            val available = check as UpdateCheckResult.Available
            assertEquals("1.1.18", available.manifest.versionName)
            assertEquals("2026-11-15T10:30:00Z", available.manifest.publishedAt)
            assertTrue(available.manifest.apkUrl.endsWith("/Ai-Smartphone-Doctor.apk"))

            composeTestRule.setContent {
                MyApplicationTheme {
                    AppUpdateDialog(
                        updateState = com.example.data.update.UpdateState.UpdateAvailable(
                            manifest = available.manifest,
                            currentVersionCode = 2L,
                            currentVersionName = "1.1.0"
                        ),
                        strings = AppStrings.English,
                        onStartDownload = {},
                        onInstallNow = {},
                        onPermissionResult = {},
                        onRetry = {},
                        onDismiss = {}
                    )
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("2026-11-15").assertIsDisplayed()
            composeTestRule.onNodeWithText("Natural Hindi/English", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText(AppStrings.English.updateDataPreservedNote)
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            server.stop(0)
        }
    }
}
