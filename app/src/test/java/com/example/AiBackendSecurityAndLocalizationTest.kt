package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiDoctorService
import com.example.data.ai.LanguageDetector
import com.example.data.ai.LocalizedDiagnosticFormatter
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.model.AiServiceStatus
import com.example.data.model.AutomationCapability
import com.example.data.model.ChatMessage
import com.example.data.model.DeviceVitals
import com.example.data.model.SenderType
import com.example.data.repair.SafeRepairRegistry
import com.example.ui.DoctorViewModel
import com.example.ui.FuturisticMessageBubble
import com.example.ui.MainAppScreen
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.MyApplicationTheme
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiBackendSecurityAndLocalizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("ai_doctor_session", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun buildConfigHasZeroGeminiApiKeyAndOnlySupportedGemini3xModels() {
        val fieldNames = BuildConfig::class.java.declaredFields.map { it.name }
        assertTrue(
            "GEMINI_API_KEY must NEVER be compiled into APK BuildConfig",
            fieldNames.none { it.contains("GEMINI_API_KEY", ignoreCase = true) }
        )
        assertTrue(
            "AI_PROXY_URL build config field should exist for optional secure backend proxy",
            fieldNames.contains("AI_PROXY_URL")
        )

        val models = AiDoctorService.SUPPORTED_GEMINI_MODELS
        assertTrue(models.isNotEmpty())
        assertTrue(
            "Only Gemini 3.x models are allowed",
            models.all { it.startsWith("gemini-3") }
        )
        assertTrue(
            "Retired Gemini 1.5 / 2.0 / 2.5 models must never be configured",
            models.none { it.contains("1.5") || it.contains("2.0") || it.contains("2.5") }
        )
    }

    @Test
    fun proxyHttpErrorSurfacesWarningAndRetryButtonRecoversToLiveCloudAiInUi() {
        val shouldFail = AtomicBoolean(true)
        val capturedRequestBody = AtomicReference("")

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        try {
            server.createContext("/v1/diagnose") { exchange ->
                val reqBody = exchange.requestBody.bufferedReader().use { it.readText() }
                capturedRequestBody.set(reqBody)

                if (shouldFail.get()) {
                    val errPayload = """{"error":{"message":"Upstream Gemini quota or 503 unavailable"}}"""
                        .toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(503, errPayload.size.toLong())
                    exchange.responseBody.use { it.write(errPayload) }
                } else {
                    val dataJson = JSONObject().apply {
                        put("replyText", "1. Run the thermal & battery audit automatically inside the app.\n2. Open Battery Settings to check high-drain background apps.")
                        put("problemTitle", "Verified Battery Drain Triage")
                        put("matchedProblemId", "AND-05-001")
                        put("confidenceLevel", "High")
                        put("confirmedEvidence", listOf("Device telemetry collected cleanly from Android APIs."))
                        put("possibleCauses", listOf("Background wakelock or high screen brightness."))
                        put("diagnosticQuestions", listOf("Does battery drop mostly during idle or active screen use?"))
                        put("unverifiedAssumptions", listOf("Per-app private battery wake stats are sandboxed."))
                        put("recommendedActionIds", listOf("auto_audit_thermal_power", "repair_battery_saver"))
                        put("hardwareFaultIndicators", listOf("Swollen battery or sudden shutdown above 20%."))
                        put("escalationInstructions", "Test in Safe Mode if drain persists.")
                        put("verificationMethod", "Compare before/after battery discharge telemetry.")
                    }
                    val okPayload = JSONObject().apply {
                        put("modelUsed", "gemini-3.5-flash-lite")
                        put("rawJson", dataJson.toString())
                    }.toString().toByteArray()

                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, okPayload.size.toLong())
                    exchange.responseBody.use { it.write(okPayload) }
                }
            }
            server.start()

            val viewModel = DoctorViewModel(app).apply {
                aiService = AiDoctorService(aiProxyUrl = "http://127.0.0.1:$port")
            }

            composeTestRule.setContent {
                MyApplicationTheme {
                    MainAppScreen(viewModel = viewModel)
                }
            }
            composeTestRule.waitForIdle()

            // Step 1: Trigger Cloud AI call while backend returns 503
            viewModel.sendUserMessage("My battery drains fast even when idle")
            waitForViewModelIdle(viewModel)
            composeTestRule.waitForIdle()

            assertEquals(AiServiceStatus.ERROR_FALLBACK, viewModel.aiServiceStatus.value)
            assertTrue(
                "Actual HTTP error must be surfaced to user, not hidden",
                viewModel.aiStatusDetail.value.contains("Upstream Gemini quota or 503 unavailable")
            )
            val failedDoctorMsg = viewModel.messages.value.last()
            assertEquals(SenderType.DOCTOR, failedDoctorMsg.sender)
            assertTrue(failedDoctorMsg.canRetryCloudAi)

            // Both banner and per-message Retry buttons must render cleanly
            composeTestRule.onNodeWithTag("ai_status_banner").assertIsDisplayed()
            composeTestRule.onNodeWithTag("retry_cloud_ai_banner_btn").assertIsDisplayed()
            composeTestRule.onNodeWithTag("retry_cloud_ai_msg_btn_${failedDoctorMsg.id}")
                .performScrollTo()
                .assertIsDisplayed()

            // Verify user problem actually reached backend payload cleanly without any secret key
            val sentJson = JSONObject(capturedRequestBody.get())
            assertTrue(sentJson.getString("userProblem").contains("battery drains fast"))
            assertFalse(capturedRequestBody.get().contains("AIza", ignoreCase = false))

            // Step 2: Recover server and tap Retry button inside Compose UI
            shouldFail.set(false)
            composeTestRule.onNodeWithTag("retry_cloud_ai_banner_btn").performClick()
            waitForViewModelIdle(viewModel)
            composeTestRule.waitForIdle()

            assertEquals(AiServiceStatus.ONLINE_GEMINI, viewModel.aiServiceStatus.value)
            assertTrue(viewModel.aiStatusDetail.value.contains("gemini-3.5-flash-lite"))
            val recoveredMsg = viewModel.messages.value.last()
            assertFalse(recoveredMsg.canRetryCloudAi)
            assertTrue(recoveredMsg.text.contains("Run the thermal & battery audit"))

            // Warning banner is gone after recovery, and separated action headers render
            composeTestRule.onNodeWithTag("retry_cloud_ai_banner_btn").assertDoesNotExist()
            composeTestRule.onNodeWithTag("auto_actions_group_header")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("permission_actions_group_header")
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun languageConsistencyEnforcedAcrossHindiHinglishAndEnglish() {
        val vitals = RealDeviceDiagnostics.getDeviceVitals(app)
        val oem = DeviceCompatibilityEngine.resolveProfile(vitals.manufacturer, vitals.brand, vitals.model, vitals.apiLevel, vitals.androidVersion)
        val service = AiDoctorService(aiProxyUrl = "")

        // 1. Pure Devanagari Hindi input
        val hindiQuery = "मेरा फोन बहुत गर्म हो रहा है और बैटरी जल्दी खत्म होती है"
        val hindiResp = service.buildStructuredFallbackResponse(
            userProblem = hindiQuery,
            vitals = vitals,
            oemProfile = oem,
            context = app,
            language = LanguageDetector.LANG_ENGLISH
        )
        assertEquals(LanguageDetector.LANG_HINDI, hindiResp.responseLanguage)
        assertTrue(Regex("[\\u0900-\\u097F]").containsMatchIn(hindiResp.replyText))
        assertFalse(hindiResp.replyText.contains("Offline KB Mode"))
        assertFalse(hindiResp.replyText.contains("Practical Safe Solutions"))
        assertTrue(hindiResp.repairPlan!!.confirmedEvidence.all { Regex("[\\u0900-\\u097F]").containsMatchIn(it) })
        assertTrue(hindiResp.repairPlan!!.unverifiedAssumptions.all { Regex("[\\u0900-\\u097F]").containsMatchIn(it) })

        val localizedHiAction = LocalizedDiagnosticFormatter.localizeAction(
            SafeRepairRegistry.findAction("auto_audit_thermal_power")!!,
            LanguageDetector.LANG_HINDI
        )
        assertTrue(Regex("[\\u0900-\\u097F]").containsMatchIn(localizedHiAction.title))

        // 2. Natural Hinglish input
        val hinglishQuery = "Mera phone bahut hang ho raha hai aur apps band ho jati hain"
        val hinglishResp = service.buildStructuredFallbackResponse(
            userProblem = hinglishQuery,
            vitals = vitals,
            oemProfile = oem,
            context = app,
            language = LanguageDetector.LANG_ENGLISH
        )
        assertEquals(LanguageDetector.LANG_HINGLISH, hinglishResp.responseLanguage)
        assertTrue(hinglishResp.replyText.contains("Cloud AI"))
        assertFalse(Regex("[\\u0900-\\u097F]").containsMatchIn(hinglishResp.replyText))

        // 3. English input
        val englishQuery = "Wi-Fi keeps disconnecting randomly on Android"
        val englishResp = service.buildStructuredFallbackResponse(
            userProblem = englishQuery,
            vitals = vitals,
            oemProfile = oem,
            context = app,
            language = LanguageDetector.LANG_ENGLISH
        )
        assertEquals(LanguageDetector.LANG_ENGLISH, englishResp.responseLanguage)
        assertFalse(Regex("[\\u0900-\\u097F]").containsMatchIn(englishResp.replyText))
        assertTrue(englishResp.replyText.contains("Practical Safe Solutions") || englishResp.replyText.contains("Notice"))
    }

    @Test
    fun telemetryNeverInventsFakeReadingsWhenCollectorsAreUnavailable() {
        val unverifiedVitals = DeviceVitals(
            manufacturer = "TestOEM",
            model = "TestPhone",
            brand = "TestOEM",
            androidVersion = "15",
            apiLevel = 35,
            batteryLevel = -1,
            batteryStatus = "Unavailable",
            batteryPlugged = "Unavailable",
            batteryTempC = -1f,
            batteryHealth = "Unavailable",
            storageTotalBytes = 0L,
            storageFreeBytes = 0L,
            storageUsedPercent = -1,
            ramTotalBytes = 0L,
            ramFreeBytes = 0L,
            ramUsedPercent = -1,
            isLowMemory = false,
            networkType = "Disconnected",
            isInternetAvailable = false,
            displayResolution = "Unavailable",
            refreshRate = -1f,
            screenTimeoutSeconds = -1,
            uptimeHours = 1f,
            healthScore = 70,
            batteryVerified = false,
            temperatureVerified = false,
            thermalVerified = false,
            ramVerified = false,
            storageVerified = false,
            networkVerified = true,
            displayVerified = false,
            audioVerified = false,
            unverifiedMetrics = listOf("Battery Level (%)", "Battery Temperature (°C)", "System RAM Telemetry", "Internal Storage Partition")
        )

        val oem = DeviceCompatibilityEngine.resolveProfile("TestOEM", "TestOEM", "TestPhone", 35, "15")
        val enEvidence = LocalizedDiagnosticFormatter.buildVerifiedTelemetryEvidence(unverifiedVitals, oem, LanguageDetector.LANG_ENGLISH)
        val enAssumptions = LocalizedDiagnosticFormatter.buildUnverifiedAssumptions(unverifiedVitals, oem, LanguageDetector.LANG_ENGLISH)

        assertTrue(enEvidence.none { it.contains("-1%") || it.contains("-1.0°C") || it.contains("100%") || it.contains("50%") })
        assertTrue(enEvidence.any { it.contains("Not collected") || it.contains("unavailable", ignoreCase = true) })
        assertTrue(enAssumptions.any { it.contains("Battery Temperature") })

        val hiEvidence = LocalizedDiagnosticFormatter.buildVerifiedTelemetryEvidence(unverifiedVitals, oem, LanguageDetector.LANG_HINDI)
        val hiAssumptions = LocalizedDiagnosticFormatter.buildUnverifiedAssumptions(unverifiedVitals, oem, LanguageDetector.LANG_HINDI)
        assertTrue(hiEvidence.none { it.contains("-1%") || it.contains("32.0") })
        assertTrue(hiAssumptions.any { it.contains("एकत्र नहीं हो सका") })

        // Verify live collector flags when running in Robolectric (battery broadcast isn't guaranteed)
        val liveVitals = RealDeviceDiagnostics.getDeviceVitals(app)
        if (!liveVitals.batteryVerified) {
            assertEquals(-1, liveVitals.batteryLevel)
            assertTrue(liveVitals.unverifiedMetrics.any { it.contains("Battery", ignoreCase = true) })
        }
        if (!liveVitals.temperatureVerified) {
            assertTrue(liveVitals.batteryTempC < 0f)
            assertTrue(liveVitals.unverifiedMetrics.any { it.contains("Temperature", ignoreCase = true) })
        }
    }

    @Test
    fun messageBubbleSeparatesAutoInAppActionsFromUserPermissionSettingsActionsInAllLanguages() {
        val mixedActions = listOfNotNull(
            SafeRepairRegistry.findAction("auto_optimize_memory"),
            SafeRepairRegistry.findAction("repair_app_cache")
        )
        assertEquals(2, mixedActions.size)
        assertEquals(AutomationCapability.NORMAL_API, mixedActions[0].capability)
        assertEquals(AutomationCapability.MANUAL_SETTINGS, mixedActions[1].capability)

        val hindiDoctorMsg = ChatMessage(
            id = "msg_hi_sep",
            sender = SenderType.DOCTOR,
            text = "आपके फोन की जाँच पूरी हो गई है।",
            actions = mixedActions,
            aiStatus = AiServiceStatus.ERROR_FALLBACK,
            aiStatusDetail = "क्लाउड एआई अनुपलब्ध: परीक्षण त्रुटि",
            responseLanguage = LanguageDetector.LANG_HINDI,
            retryPrompt = "मेरा फोन धीमा है",
            canRetryCloudAi = true
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                FuturisticMessageBubble(
                    message = hindiDoctorMsg,
                    strings = AppStrings.Hindi,
                    onRetryCloudAi = {},
                    onActionClick = {},
                    onVerifySuccess = {},
                    onVerifyFailed = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("auto_actions_group_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("permission_actions_group_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retry_cloud_ai_msg_btn_msg_hi_sep").assertIsDisplayed()
        composeTestRule.onNodeWithText(AppStrings.Hindi.autoActionsHeader).assertIsDisplayed()
        composeTestRule.onNodeWithText(AppStrings.Hindi.permissionSettingsActionsHeader).assertIsDisplayed()
    }

    private fun waitForViewModelIdle(viewModel: DoctorViewModel) = runBlocking {
        var elapsed = 0L
        while (viewModel.isAnalyzing.value && elapsed < 15_000L) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(40)
            elapsed += 40
        }
        ShadowLooper.idleMainLooper()
        assertFalse("ViewModel analysis should complete promptly", viewModel.isAnalyzing.value)
    }
}
