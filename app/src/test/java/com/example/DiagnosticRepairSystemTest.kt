package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiDoctorService
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.knowledge.PlaybookRagRetriever
import com.example.data.local.AppDatabase
import com.example.data.local.DiagnosticRepository
import com.example.data.model.ActionType
import com.example.data.model.AiServiceStatus
import com.example.data.model.AutomationCapability
import com.example.data.model.DeviceVitals
import com.example.data.model.DiagnosticHistoryItem
import com.example.data.model.DoctorAction
import com.example.data.model.RepairLevel
import com.example.data.model.VerificationOutcome
import com.example.data.repair.ActionResult
import com.example.data.repair.AutoFixEngine
import com.example.data.repair.CapabilityEvaluator
import com.example.data.repair.RepairVerificationEngine
import com.example.data.repair.SafeRepairActions
import com.example.data.repair.SafeRepairRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticRepairSystemTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: DiagnosticRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DiagnosticRepository(db.diagnosticHistoryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleVitals(
        ramFreeBytes: Long = 2_000_000_000L,
        ramUsedPercent: Int = 55,
        storageFreeBytes: Long = 20_000_000_000L,
        storageUsedPercent: Int = 60,
        batteryTempC: Float = 33.5f,
        screenTimeoutSeconds: Int = 60,
        healthScore: Int = 85
    ) = DeviceVitals(
        manufacturer = "Samsung",
        model = "Galaxy S24",
        brand = "Samsung",
        androidVersion = "14",
        apiLevel = 34,
        batteryLevel = 72,
        batteryStatus = "Discharging",
        batteryPlugged = "On Battery",
        batteryTempC = batteryTempC,
        batteryHealth = "Good",
        storageTotalBytes = 128_000_000_000L,
        storageFreeBytes = storageFreeBytes,
        storageUsedPercent = storageUsedPercent,
        ramTotalBytes = 8_000_000_000L,
        ramFreeBytes = ramFreeBytes,
        ramUsedPercent = ramUsedPercent,
        isLowMemory = false,
        networkType = "Wi-Fi",
        isInternetAvailable = true,
        displayResolution = "1080x2340",
        refreshRate = 120f,
        screenTimeoutSeconds = screenTimeoutSeconds,
        uptimeHours = 12f,
        healthScore = healthScore
    )

    @Test
    fun `all 13 modular playbooks contain complete 14-point structure and valid action IDs`() {
        val playbooks = PlaybookRagRetriever.allPlaybooks
        assertEquals(13, playbooks.size)

        val oemProfile = DeviceCompatibilityEngine.resolveProfile("Samsung", "Samsung", "S24", 35, "15")
        playbooks.forEach { pb ->
            assertTrue("${pb.id} problemIdentification blank", pb.problemIdentification.isNotBlank())
            assertTrue("${pb.id} possibleCauses empty", pb.possibleCauses.isNotEmpty())
            assertTrue("${pb.id} diagnosticQuestions empty", pb.diagnosticQuestions.isNotEmpty())
            assertTrue("${pb.id} requiredDeviceInfo empty", pb.requiredDeviceInfo.isNotEmpty())
            assertTrue("${pb.id} androidVersionCompatibility blank", pb.androidVersionCompatibility.isNotBlank())
            assertTrue("${pb.id} OEM path blank", pb.oemSettingsPathResolver(oemProfile).isNotBlank())
            assertTrue("${pb.id} stepByStepSolution empty", pb.stepByStepSolution.isNotEmpty())
            assertTrue("${pb.id} automaticActionIds empty", pb.automaticActionIds.isNotEmpty())
            pb.automaticActionIds.forEach { actionId ->
                assertTrue("${pb.id} references unregistered action $actionId", SafeRepairRegistry.isRegistered(actionId))
            }
            assertTrue("${pb.id} requiredPermissions empty", pb.requiredPermissions.isNotEmpty())
            assertTrue("${pb.id} androidApiRestrictions blank", pb.androidApiRestrictions.isNotBlank())
            assertTrue("${pb.id} alternativeSolutions empty", pb.alternativeSolutions.isNotEmpty())
            assertTrue("${pb.id} verificationMethod blank", pb.verificationMethod.isNotBlank())
            assertTrue("${pb.id} hardwareFaultIndicators empty", pb.hardwareFaultIndicators.isNotEmpty())
            assertTrue("${pb.id} escalationInstructions blank", pb.escalationInstructions.isNotBlank())
        }
    }

    @Test
    fun `RAG retriever matches multilingual Hinglish Punjabi and English queries accurately`() {
        val vitals = sampleVitals()

        val hangMatch = PlaybookRagRetriever.retrieveRelevantPlaybooks("Mera phone bahut hang ho raha hai", vitals)
        assertEquals("pb_phone_hanging", hangMatch.first().playbook.id)

        val punjabiShutdownMatch = PlaybookRagRetriever.retrieveRelevantPlaybooks("Mera phone apne aap band ho janda hai", vitals)
        assertEquals("pb_boot_startup", punjabiShutdownMatch.first().playbook.id)

        val wifiMatch = PlaybookRagRetriever.retrieveRelevantPlaybooks("Wi-Fi connected hai par internet nahi chal raha", vitals)
        assertEquals("pb_wifi", wifiMatch.first().playbook.id)

        val gamingMatch = PlaybookRagRetriever.retrieveRelevantPlaybooks("Gaming mein FPS drop aur heating problem", vitals)
        assertEquals("pb_gaming", gamingMatch.first().playbook.id)

        val audioMatch = PlaybookRagRetriever.retrieveRelevantPlaybooks("Speaker se awaaz nahi aa rahi", vitals)
        assertEquals("pb_audio", audioMatch.first().playbook.id)
    }

    @Test
    fun `device compatibility engine resolves all OEMs, unknown fallback, Android 16 and Android 17+`() {
        val requiredOems = listOf(
            "Samsung" to "Samsung",
            "Xiaomi" to "Xiaomi",
            "Xiaomi" to "Redmi",
            "Xiaomi" to "POCO",
            "OnePlus" to "OnePlus",
            "realme" to "realme",
            "OPPO" to "OPPO",
            "vivo" to "Vivo",
            "vivo" to "iQOO",
            "Motorola" to "motorola",
            "Nothing" to "Nothing",
            "Google" to "Pixel"
        )

        requiredOems.forEach { (mfg, brand) ->
            val profile = DeviceCompatibilityEngine.resolveProfile(mfg, brand, "TestModel", 35, "15")
            assertTrue("Expected verified profile for $brand", profile.isPathVerifiedForBuild)
            assertFalse(profile.batterySettingsPath.contains("Search"))
        }

        val unknown = DeviceCompatibilityEngine.resolveProfile("GenericCo", "UnknownBrand", "X1", 34, "14")
        assertFalse(unknown.isPathVerifiedForBuild)
        assertTrue(unknown.batterySettingsPath.contains("Search"))

        val android16 = DeviceCompatibilityEngine.resolveVersionCompatibility(36, "16")
        assertTrue(android16.versionLabel.contains("Android 16"))
        assertFalse(android16.isFutureOrPreview)

        val android17 = DeviceCompatibilityEngine.resolveVersionCompatibility(37, "17")
        assertTrue(android17.versionLabel.contains("Android 17"))
        assertFalse(android17.isFutureOrPreview)

        val android18 = DeviceCompatibilityEngine.resolveVersionCompatibility(38, "18")
        assertTrue(android18.versionLabel.contains("Android 18"))
        assertTrue(android18.isFutureOrPreview)
    }

    @Test
    fun `capability evaluator and destructive action safety guards enforce strict policy`() = runBlocking {
        // 1. Reject unregistered action ID
        val fakeAction = DoctorAction(
            id = "arbitrary_root_delete",
            title = "Fake",
            description = "Fake",
            actionType = ActionType.CLEAR_APP_OWN_CACHE
        )
        val fakeEligibility = CapabilityEvaluator.evaluate(context, fakeAction)
        assertFalse(fakeEligibility.isRegistered)

        val blockedResult = SafeRepairActions.executeActionAsync(context, fakeAction, explicitDestructiveConfirmed = true)
        assertFalse(blockedResult.isSuccess)

        // 2. Destructive guarded action blocked unless explicitDestructiveConfirmed = true
        val clearDataAction = SafeRepairRegistry.findAction("guarded_clear_app_data")!!
        assertTrue(clearDataAction.isDestructive)
        val blockedDestructive = SafeRepairActions.executeActionAsync(context, clearDataAction, explicitDestructiveConfirmed = false)
        assertFalse(blockedDestructive.isSuccess)
        assertTrue(blockedDestructive.message.contains("Safety Guard"))

        // 3. Special permission WRITE_SETTINGS: denied -> requests permission, never silently bypassed
        val timeoutAction = SafeRepairRegistry.findAction("auto_screen_timeout_safe")!!
        val deniedEligibility = CapabilityEvaluator.evaluate(context, timeoutAction, canWriteOverride = false)
        assertEquals(AutomationCapability.SPECIAL_PERMISSION, deniedEligibility.capability)
        assertFalse(deniedEligibility.permissionGranted)

        val deniedResult = SafeRepairActions.executeActionAsync(context, timeoutAction, canWriteOverride = false)
        assertFalse(deniedResult.isSuccess)
        assertTrue(deniedResult.requiresSpecialPermissionGrant)

        // 4. Special permission WRITE_SETTINGS: granted -> safely updates SCREEN_OFF_TIMEOUT to 30,000 ms
        val grantedEligibility = CapabilityEvaluator.evaluate(context, timeoutAction, canWriteOverride = true)
        assertTrue(grantedEligibility.permissionGranted)

        val grantedResult = SafeRepairActions.executeActionAsync(context, timeoutAction, canWriteOverride = true)
        assertTrue(grantedResult.isSuccess)
        assertTrue(grantedResult.executedAutomatically)
    }

    @Test
    fun `real cache cleanup deletes actual temp bytes with zero fabricated numbers`() = runBlocking {
        val tempFile = File(context.cacheDir, "diagnostic_test_payload.tmp")
        val payload = ByteArray(16_384) { 42 }
        tempFile.writeBytes(payload)
        assertTrue(tempFile.exists())

        val clearOwnCache = SafeRepairRegistry.findAction("auto_clear_own_cache")!!
        val result = SafeRepairActions.executeActionAsync(context, clearOwnCache)
        assertTrue(result.isSuccess)
        assertTrue(result.executedAutomatically)
        assertFalse(tempFile.exists())
        assertTrue(result.measuredDetails.any { it.contains("16.0 KB") || it.contains("16384") || it.contains("freed") })

        // Also test AutoFixEngine creates honest report without inflated score
        val beforeVitals = RealDeviceDiagnostics.getDeviceVitals(context)
        val temp2 = File(context.cacheDir, "autofix_test.tmp")
        temp2.writeBytes(ByteArray(4096) { 7 })
        val report = AutoFixEngine.executeSmartScanAndFix(context, beforeVitals)
        assertEquals(4096L, report.bytesCleared)
        assertTrue(report.autoFixedItems.isNotEmpty())
        assertFalse(temp2.exists())
    }

    @Test
    fun `before after verification engine detects real improvements and selects next safe alternative on failure`() {
        val before = sampleVitals(screenTimeoutSeconds = 300, healthScore = 76)
        val afterImproved = sampleVitals(screenTimeoutSeconds = 30, healthScore = 80)
        val timeoutAction = SafeRepairRegistry.findAction("auto_screen_timeout_safe")!!

        val improved = RepairVerificationEngine.verifyBeforeAfter(
            action = timeoutAction,
            before = before,
            after = afterImproved,
            executionResult = ActionResult(
                isSuccess = true,
                message = "Updated timeout 300s -> 30s",
                executedAutomatically = true,
                requiresUserConfirmationToVerify = false
            )
        )
        assertEquals(VerificationOutcome.VERIFIED_IMPROVED, improved.outcome)
        assertTrue(improved.metricDeltas.any { it.contains("300s -> 30s") })
        assertNull(improved.nextAlternativeAction)

        // Failure case -> must pick an untried safe alternative
        val playbook = PlaybookRagRetriever.findById("pb_phone_hanging")!!
        val firstAction = SafeRepairRegistry.findAction(playbook.automaticActionIds.first())!!
        val failed = RepairVerificationEngine.verifyBeforeAfter(
            action = firstAction,
            before = before,
            after = before,
            executionResult = ActionResult(isSuccess = false, message = "Permission not granted"),
            playbook = playbook,
            alreadyTriedActionIds = setOf(firstAction.id)
        )
        assertEquals(VerificationOutcome.EXECUTION_FAILED, failed.outcome)
        assertNotNull(failed.nextAlternativeAction)
        assertFalse(failed.nextAlternativeAction!!.isDestructive)
        assertTrue(failed.nextAlternativeAction!!.id != firstAction.id)
    }

    @Test
    fun `AiDoctorService validates action IDs from SafeRepairRegistry and provides structured fallback`() {
        val aiService = AiDoctorService()
        val vitals = sampleVitals()
        val oem = DeviceCompatibilityEngine.resolveProfile("Samsung", "Samsung", "S24", 34, "14")

        val simulatedJson = """
            {
              "replyText": "RAM usage high hai aur battery garam hai.",
              "matchedPlaybookId": "pb_phone_hanging",
              "confidenceLevel": "High",
              "confirmedEvidence": ["RAM 78% used"],
              "possibleCauses": ["Background services", "Low storage swap"],
              "diagnosticQuestions": ["Kya ek specific app par hang hota hai?"],
              "unverifiedAssumptions": ["Other apps cache isolated"],
              "recommendedActionIds": ["auto_optimize_memory", "injected_malicious_rm_rf", "repair_app_cache"],
              "hardwareFaultIndicators": ["Repeated freeze in Safe Mode"],
              "escalationInstructions": "Test in Safe Mode."
            }
        """.trimIndent()

        val parsed = aiService.parseStructuredGeminiJson(
            jsonString = simulatedJson,
            vitals = vitals,
            oemProfile = oem,
            fallbackPlaybookId = "pb_phone_hanging"
        )
        assertEquals(AiServiceStatus.ONLINE_GEMINI, parsed.aiStatus)
        assertEquals(2, parsed.actions.size)
        assertTrue(parsed.actions.none { it.id == "injected_malicious_rm_rf" })
        assertNotNull(parsed.repairPlan)
        assertEquals("pb_phone_hanging", parsed.repairPlan?.playbookId)

        val fallback = aiService.buildStructuredFallbackResponse(
            userProblem = "Mera phone hang ho raha hai",
            vitals = vitals,
            oemProfile = oem,
            status = AiServiceStatus.UNAVAILABLE_OFFLINE_FALLBACK,
            statusDetail = "Offline test"
        )
        assertFalse(fallback.aiStatus.isCloudAi)
        assertTrue(fallback.replyText.contains("Cloud AI"))
        assertNotNull(fallback.repairPlan)
        assertTrue(fallback.actions.isNotEmpty())
    }

    @Test
    fun `persistent Room database saves retrieves and clears diagnostic audit logs`() = runBlocking {
        val entry = DiagnosticHistoryItem(
            id = "audit-1",
            timestamp = 1700000000000L,
            problemTitle = "Phone Hanging Repair",
            category = "PERFORMANCE",
            diagnosisSummary = "Cleared own cache & verified free RAM",
            actionExecuted = "Inspect RAM & Release App Buffers",
            verificationStatus = "Verified Improved",
            playbookId = "pb_phone_hanging",
            capabilityUsed = "AUTO API",
            beforeSnapshot = "Score 70/100",
            afterSnapshot = "Score 75/100",
            aiModeUsed = "GEMINI 3.8 FLASH ACTIVE"
        )

        repository.saveHistoryItem(entry)
        val savedList = repository.historyItems.first()
        assertEquals(1, savedList.size)
        assertEquals("audit-1", savedList.first().id)
        assertEquals("AUTO API", savedList.first().capabilityUsed)
        assertEquals("Score 70/100", savedList.first().beforeSnapshot)

        repository.clearAll()
        val afterClear = repository.historyItems.first()
        assertTrue(afterClear.isEmpty())
    }
}
