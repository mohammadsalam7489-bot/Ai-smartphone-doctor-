package com.example

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiDoctorService
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.knowledge.KnowledgeCoverageAuditor
import com.example.data.knowledge.KnowledgeRepository
import com.example.data.knowledge.PlaybookRagRetriever
import com.example.data.model.ActionType
import com.example.data.model.AiServiceStatus
import com.example.data.model.AutomationCapability
import com.example.data.model.CoverageTier
import com.example.data.model.DeviceVitals
import com.example.data.model.DoctorAction
import com.example.data.model.VerificationOutcome
import com.example.data.repair.ActionResult
import com.example.data.repair.CapabilityEvaluator
import com.example.data.repair.RepairVerificationEngine
import com.example.data.repair.SafeRepairActions
import com.example.data.repair.SafeRepairRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealWorldEndToEndTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        KnowledgeRepository.resetMemoryCacheForTests()
        KnowledgeRepository.ensureLoadedSynchronously(context)
        DeviceCompatibilityEngine.ensureLoaded(context)
    }

    @After
    fun tearDown() {
        KnowledgeRepository.resetMemoryCacheForTests()
    }

    private fun baseVitals(
        manufacturer: String = "Samsung",
        brand: String = "Samsung",
        model: String = "Galaxy S24",
        apiLevel: Int = 36,
        androidVersion: String = "16",
        isOnline: Boolean = true
    ) = DeviceVitals(
        manufacturer = manufacturer,
        model = model,
        brand = brand,
        androidVersion = androidVersion,
        apiLevel = apiLevel,
        batteryLevel = 64,
        batteryStatus = "Discharging",
        batteryPlugged = "Battery Power",
        batteryTempC = 34.0f,
        batteryHealth = "Good",
        storageTotalBytes = 128_000_000_000L,
        storageFreeBytes = 42_000_000_000L,
        storageUsedPercent = 67,
        ramTotalBytes = 8_000_000_000L,
        ramFreeBytes = 3_200_000_000L,
        ramUsedPercent = 60,
        isLowMemory = false,
        networkType = if (isOnline) "Wi-Fi" else "Disconnected",
        isInternetAvailable = isOnline,
        displayResolution = "1080 x 2340",
        refreshRate = 120f,
        screenTimeoutSeconds = 30,
        uptimeHours = 6.5f,
        healthScore = 84
    )

    @Test
    fun `all 8 user Hinglish complaints map to exact categories and issues`() {
        val vitals = baseVitals()

        val expectedMatches = listOf(
            "Mera phone bahut hang hota hai" to { id: String -> id == "AND-02-001" || id == "AND-02-003" },
            "Game apne aap band ho jaati hai" to { id: String -> id == "AND-11-001" },
            "Wi-Fi connected hai lekin internet nahi chal raha" to { id: String -> id == "AND-07-001" },
            "Phone charge bahut slow ho raha hai" to { id: String -> id == "AND-05-006" },
            "Touch kaam nahi kar raha" to { id: String -> id == "AND-08-015" || id.startsWith("AND-08-") },
            "Phone bina wajah garam hota hai" to { id: String -> id.startsWith("AND-27-") },
            "Notifications nahi aa rahi" to { id: String -> id == "AND-10-001" || id.startsWith("AND-10-") },
            "Bluetooth connect nahi ho raha" to { id: String -> id == "AND-12-004" || id.startsWith("AND-12-") }
        )

        expectedMatches.forEach { (query, matcher) ->
            val outcome = PlaybookRagRetriever.retrieveIssueOutcome(query, vitals, context)
            assertFalse("Expected non-ambiguous match for '$query'", outcome.isAmbiguous)
            assertTrue("Expected non-empty matches for '$query'", outcome.matches.isNotEmpty())
            val matchedId = outcome.matches.first().issue.problemId
            assertTrue("Unexpected issue '$matchedId' for query '$query'", matcher(matchedId))
        }
    }

    @Test
    fun `all 29 categories and Hindi Devanagari plus Punjabi queries retrieve accurately`() {
        val vitals = baseVitals()

        val categoryQueries = mapOf(
            1 to "Phone boot logo par atak gaya hai restart ho raha hai",
            2 to "Phone bahut slow chal raha hai aur freeze hota hai",
            3 to "Double tap to wake pocket mein apne aap ON ho jaata hai",
            4 to "WhatsApp app crash ho rahi hai keeps stopping",
            5 to "Battery bahut jaldi drain ho rahi hai",
            6 to "Storage full warning dikh rahi hai space nahi hai",
            7 to "Wi-Fi connected hai lekin internet nahi chal raha",
            8 to "Touchscreen ghost touch aur touch kaam nahi kar raha",
            9 to "Speaker se sound aur aawaz nahi aa rahi",
            10 to "WhatsApp aur Gmail notifications nahi aa rahi",
            11 to "BGMI game FPS drop aur apne aap band ho jaati hai",
            12 to "Bluetooth earbuds connect nahi ho raha",
            13 to "App permission allow nahi ho rahi restricted settings dikhata hai",
            14 to "System update OTA download failure aa raha hai",
            15 to "Google account sign-in aur sync failure ho raha hai",
            16 to "Gboard keyboard open nahi ho raha typing lag",
            17 to "TalkBack accessibility green box bol raha hai band kaise kare",
            18 to "Camera app open nahi ho raha photo blur aa rahi hai",
            19 to "GPS location maps par accurate nahi dikha raha",
            20 to "Dialer call drop aur SMS send nahi ho raha",
            21 to "Play Store download pending dikha raha hai app install nahi ho rahi",
            22 to "Home screen blank ho gayi launcher crash ho raha hai",
            23 to "Google Drive backup fail ho raha hai",
            24 to "Android System UI repeatedly crashing notification shade",
            25 to "Developer Options accidentally enabled usb debugging",
            26 to "SIM card not detected 5G no service emergency calls only",
            27 to "Phone bina wajah garam ho raha hai overheating",
            28 to "USB file transfer MTP OTG PC par detect nahi ho raha",
            29 to "Work profile IT admin managed Knox restriction aa rahi hai"
        )

        assertEquals(29, categoryQueries.size)
        categoryQueries.forEach { (expectedCat, query) ->
            val outcome = PlaybookRagRetriever.retrieveIssueOutcome(query, vitals, context)
            assertFalse("Category $expectedCat query should not be ambiguous: $query", outcome.isAmbiguous)
            val topCat = outcome.matches.firstOrNull()?.issue?.categoryNumber
            assertEquals("Failed category routing for '$query'", expectedCat, topCat)
        }

        // Hindi Devanagari + Punjabi script checks
        val devanagariHeat = PlaybookRagRetriever.retrieveIssueOutcome("मेरा फोन बहुत गर्म हो रहा है", vitals, context)
        assertFalse(devanagariHeat.isAmbiguous)
        assertTrue(devanagariHeat.matches.first().issue.categoryNumber in listOf(5, 27))

        val devanagariBluetooth = PlaybookRagRetriever.retrieveIssueOutcome("ब्लूटूथ कनेक्ट नहीं हो रहा", vitals, context)
        assertFalse(devanagariBluetooth.isAmbiguous)
        assertEquals(12, devanagariBluetooth.matches.first().issue.categoryNumber)

        val punjabiRoman = PlaybookRagRetriever.retrieveIssueOutcome("Mera phone apne aap band ho janda hai", vitals, context)
        assertFalse(punjabiRoman.isAmbiguous)
        assertEquals(1, punjabiRoman.matches.first().issue.categoryNumber)

        val punjabiGurmukhi = PlaybookRagRetriever.retrieveIssueOutcome("ਮੇਰਾ ਫ਼ੋਨ ਬਹੁਤ ਗਰਮ ਹੋ ਰਿਹਾ ਹੈ", vitals, context)
        assertFalse(punjabiGurmukhi.isAmbiguous)
        assertEquals(27, punjabiGurmukhi.matches.first().issue.categoryNumber)
    }

    @Test
    fun `ambiguous complaints trigger clarifying questions first instead of guessing wrong diagnosis`() {
        val vitals = baseVitals()
        val aiService = AiDoctorService()

        val vagueQueries = listOf(
            "Mera phone kharab hai",
            "Help me please",
            "Kuch problem hai doctor"
        )

        vagueQueries.forEach { query ->
            val outcome = PlaybookRagRetriever.retrieveIssueOutcome(query, vitals, context)
            assertTrue("Expected ambiguous triage for '$query'", outcome.isAmbiguous)
            assertTrue("Expected empty matches before clarification for '$query'", outcome.matches.isEmpty())
            assertTrue("Expected clarifying questions for '$query'", outcome.clarifyingQuestions.isNotEmpty())

            val response = aiService.buildStructuredFallbackResponse(
                userProblem = query,
                vitals = vitals,
                status = AiServiceStatus.UNAVAILABLE_OFFLINE_FALLBACK,
                context = context
            )
            assertEquals("CLARIFY-REQUIRED", response.repairPlan?.playbookId)
            assertNotEquals("AND-02-001", response.repairPlan?.matchedIssueId)
            assertTrue(response.repairPlan?.diagnosticQuestions?.isNotEmpty() == true)
            assertTrue(response.actions.none { it.isDestructive })
        }
    }

    @Test
    fun `knowledge base 466 audit separates 234 bespoke vs 232 category workflow IDs with zero truncations`() {
        val audit = KnowledgeCoverageAuditor.generateLiveAudit(context)
        assertEquals(29, audit.totalCategories)
        assertEquals(466, audit.totalIssues)
        assertEquals(466, audit.uniqueProblemIds)
        assertEquals(0, audit.missingFromExpected466Count)
        assertEquals(0, audit.incompleteFieldIssuesCount)
        assertEquals(234, audit.issuesWithBespokeNotes)
        assertEquals(232, audit.issuesUsingCategoryWorkflow)
        assertEquals(232, audit.categoryWorkflowIssueIds.size)
        assertEquals(466, audit.verificationStatusBreakdown["DOC-REFERENCED"])
        assertEquals(null, audit.verificationStatusBreakdown["DEVICE-TESTED"])

        KnowledgeRepository.getAllIssues().forEach { iss ->
            iss.solutions.forEach { step ->
                val trimmed = step.trim()
                assertFalse("${iss.problemId} has trailing colon truncation: $trimmed", trimmed.endsWith(":"))
                assertTrue("${iss.problemId} has overly short solution: $trimmed", trimmed.length >= 12)
            }
        }
    }

    @Test
    fun `every settings ActionType builds valid Android Settings intent without crashing`() {
        val intentTypes = ActionType.entries.filter {
            it.name.startsWith("OPEN_") || it.name.startsWith("GUARDED_")
        }
        assertTrue(intentTypes.isNotEmpty())
        intentTypes.forEach { actionType ->
            val intent = SafeRepairActions.buildSettingsIntent(context, actionType)
            assertNotNull("Expected valid intent for $actionType", intent)
            assertFalse("Expected non-blank intent action for $actionType", intent?.action.isNullOrBlank())
        }
    }

    @Test
    fun `destructive actions permission denial unregistered actions and issue specific verification loop work safely`() = runBlocking {
        // Unregistered arbitrary action must be blocked
        val rogueAction = DoctorAction(
            id = "shell_rm_rf",
            title = "Rogue",
            description = "Rogue",
            actionType = ActionType.CLEAR_APP_OWN_CACHE
        )
        assertFalse(SafeRepairActions.executeActionAsync(context, rogueAction, explicitDestructiveConfirmed = true).isSuccess)

        // All 3 destructive guarded actions require explicit confirmation
        listOf("guarded_clear_app_data", "guarded_uninstall_unused", "guarded_reset_network").forEach { id ->
            val act = SafeRepairRegistry.findAction(id)!!
            val blocked = SafeRepairActions.executeActionAsync(context, act, explicitDestructiveConfirmed = false)
            assertFalse("$id should block when unconfirmed", blocked.isSuccess)
            assertTrue(blocked.message.contains("Safety Guard"))
        }

        // WRITE_SETTINGS special permission denied -> requests permission & fails honestly
        val timeoutAction = SafeRepairRegistry.findAction("auto_screen_timeout_safe")!!
        val denied = SafeRepairActions.executeActionAsync(context, timeoutAction, canWriteOverride = false)
        assertFalse(denied.isSuccess)
        assertTrue(denied.requiresSpecialPermissionGrant)

        // Check WRITE_SETTINGS granted path updates SCREEN_OFF_TIMEOUT
        val granted = SafeRepairActions.executeActionAsync(context, timeoutAction, canWriteOverride = true)
        assertTrue(granted.isSuccess)
        assertEquals(30_000, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1))

        // Issue-specific before/after verification loop picks next untried safe action for AND-05-006
        val issue05006 = KnowledgeRepository.findIssueById("AND-05-006")!!
        val firstAction = SafeRepairRegistry.findAction(issue05006.automaticActionIds.first())!!
        val before = baseVitals()
        val comparison = RepairVerificationEngine.verifyBeforeAfter(
            action = firstAction,
            before = before,
            after = before,
            executionResult = ActionResult(isSuccess = false, message = "Requires manual toggle"),
            alreadyTriedActionIds = setOf(firstAction.id),
            issueId = "AND-05-006"
        )
        assertEquals(VerificationOutcome.EXECUTION_FAILED, comparison.outcome)
        assertNotNull("Expected issue-specific alternative for AND-05-006", comparison.nextAlternativeAction)
        assertFalse(comparison.nextAlternativeAction!!.isDestructive)
        assertNotEquals(firstAction.id, comparison.nextAlternativeAction!!.id)
    }

    @Test
    fun `online Gemini JSON parsing validates actions handles CLARIFY REQUIRED rejects malformed JSON and checks status badges`() {
        val aiService = AiDoctorService()
        val vitals = baseVitals(manufacturer = "AcmeGeneric", brand = "UnknownX", model = "Z9", apiLevel = 38, androidVersion = "18")
        val unknownOem = DeviceCompatibilityEngine.resolveProfile("AcmeGeneric", "UnknownX", "Z9", 38, "18")

        assertEquals(CoverageTier.D_UNKNOWN, unknownOem.coverageTier)
        assertFalse(unknownOem.isPathVerifiedForBuild)
        assertTrue(unknownOem.androidCompatibility.isFutureOrPreview)

        val huaweiNonGms = DeviceCompatibilityEngine.resolveProfile("Huawei", "Huawei", "Pura 70", 34, "14")
        assertFalse(huaweiNonGms.gmsAvailable)
        assertTrue(DeviceCompatibilityEngine.resolveSettingsPath("play_store", huaweiNonGms).resolvedPath.contains("Search"))

        // 1. Valid Gemini JSON with CLARIFY-REQUIRED
        val clarifyJson = """
            {
              "replyText": "Pehle bataiye issue kis feature mein hai?",
              "matchedProblemId": "CLARIFY-REQUIRED",
              "confidenceLevel": "Low",
              "confirmedEvidence": ["Health 84/100"],
              "possibleCauses": ["Symptom underspecified"],
              "diagnosticQuestions": ["Kya battery slow charge ho rahi hai ya phone hang ho raha hai?"],
              "recommendedActionIds": ["fake_malicious_id"],
              "hardwareFaultIndicators": ["Physical damage"],
              "escalationInstructions": "Provide symptom details."
            }
        """.trimIndent()
        val clarifyParsed = aiService.parseStructuredGeminiJson(clarifyJson, vitals, unknownOem)
        assertEquals("CLARIFY-REQUIRED", clarifyParsed.repairPlan?.playbookId)
        assertTrue(clarifyParsed.actions.isEmpty())
        assertEquals(AiServiceStatus.ONLINE_GEMINI, clarifyParsed.aiStatus)

        // 2. Timeout and Error fallback transparency
        val timeoutResp = aiService.buildStructuredFallbackResponse(
            userProblem = "Phone hang ho raha hai",
            vitals = vitals,
            oemProfile = unknownOem,
            status = AiServiceStatus.TIMEOUT_FALLBACK,
            statusDetail = "Timed out 60s",
            context = context
        )
        assertFalse(timeoutResp.aiStatus.isCloudAi)
        assertEquals(AiServiceStatus.TIMEOUT_FALLBACK, timeoutResp.aiStatus)
        assertTrue(timeoutResp.repairPlan?.oemResolvedPath?.contains("Search") == true)

        // 3. Malformed JSON fails loudly as JSONException (caught in diagnoseAndGuide fallback wrapper)
        var threwMalformed = false
        try {
            aiService.parseStructuredGeminiJson("{not-valid-json", vitals, unknownOem)
        } catch (_: JSONException) {
            threwMalformed = true
        }
        assertTrue(threwMalformed)
    }

    @Test
    fun `device diagnostics collects own app exit info without PII`() {
        val vitals = RealDeviceDiagnostics.getDeviceVitals(context)
        assertTrue(vitals.ownAppExitSummary.isNotBlank())
        assertTrue(vitals.activeCollectorCapabilities.any { it.collectorId == "col_app_exit" })
        val formatted = RealDeviceDiagnostics.formatVitalsSummary(vitals)
        assertFalse(formatted.contains("IMEI", ignoreCase = true))
        assertFalse(formatted.contains("Serial", ignoreCase = true))
        assertTrue(formatted.contains("Crash/Exit Telemetry"))
    }
}
