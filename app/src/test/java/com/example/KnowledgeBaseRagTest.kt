package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiDoctorService
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.knowledge.KnowledgeCoverageAuditor
import com.example.data.knowledge.KnowledgeRepository
import com.example.data.knowledge.KnowledgeUpdateManager
import com.example.data.knowledge.PlaybookRagRetriever
import com.example.data.local.AppDatabase
import com.example.data.model.AiServiceStatus
import com.example.data.model.CoverageTier
import com.example.data.model.DeviceVitals
import com.example.data.repair.SafeRepairRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KnowledgeBaseRagTest {

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

    private fun testVitals(
        manufacturer: String = "Samsung",
        brand: String = "Samsung",
        model: String = "Galaxy S24",
        apiLevel: Int = 37,
        androidVersion: String = "17",
        ramUsedPct: Int = 62,
        storageUsedPct: Int = 68,
        batteryTempC: Float = 34.5f,
        isOnline: Boolean = true
    ) = DeviceVitals(
        manufacturer = manufacturer,
        model = model,
        brand = brand,
        androidVersion = androidVersion,
        apiLevel = apiLevel,
        batteryLevel = 65,
        batteryStatus = "Discharging",
        batteryPlugged = "On Battery",
        batteryTempC = batteryTempC,
        batteryHealth = "Good",
        storageTotalBytes = 128_000_000_000L,
        storageFreeBytes = 40_000_000_000L,
        storageUsedPercent = storageUsedPct,
        ramTotalBytes = 8_000_000_000L,
        ramFreeBytes = 3_000_000_000L,
        ramUsedPercent = ramUsedPct,
        isLowMemory = false,
        networkType = if (isOnline) "Wi-Fi" else "None",
        isInternetAvailable = isOnline,
        displayResolution = "1080x2340",
        refreshRate = 120f,
        screenTimeoutSeconds = 30,
        uptimeHours = 10f,
        healthScore = 82
    )

    @Test
    fun `audit confirms 100 percent extraction of all 29 categories and 466 unique PDF issues`() {
        val audit = KnowledgeCoverageAuditor.generateLiveAudit(context)

        assertEquals(29, audit.totalCategories)
        assertEquals(466, audit.totalIssues)
        assertEquals(466, audit.uniqueProblemIds)
        assertEquals(0, audit.missingFromExpected466Count)
        assertEquals(0, audit.incompleteFieldIssuesCount)
        assertEquals(466, audit.verificationStatusBreakdown["DOC-REFERENCED"])
        assertTrue("Expected cross-category overlaps detected without deletion", audit.overlappingIssuesCount > 0)
        assertTrue("Expected bespoke notes + category workflows sum to 466", audit.issuesWithBespokeNotes + audit.issuesUsingCategoryWorkflow == 466)

        // Verify all automaticActionIds in all 466 records are strictly registered in SafeRepairRegistry
        KnowledgeRepository.getAllIssues().forEach { issue ->
            assertTrue("${issue.problemId} empty solutions", issue.solutions.isNotEmpty())
            assertTrue("${issue.problemId} empty causes", issue.possibleCauses.isNotEmpty())
            assertTrue("${issue.problemId} empty questions", issue.diagnosticQuestions.isNotEmpty())
            assertTrue("${issue.problemId} invalid page ${issue.sourcePage}", issue.sourcePage in 4..137)
            issue.automaticActionIds.forEach { actionId ->
                assertTrue("${issue.problemId} unregistered action $actionId", SafeRepairRegistry.isRegistered(actionId))
            }
        }
    }

    @Test
    fun `compatibility manual v3 covers all 30 OEM families, Android 17 stable, Android 18 placeholder, and non-GMS Huawei`() {
        val families = DeviceCompatibilityEngine.getAllRegisteredFamilies()
        assertEquals(30, families.size)

        val samsung = DeviceCompatibilityEngine.resolveProfile("Samsung", "Samsung", "Galaxy S24", 37, "17")
        assertEquals(CoverageTier.B_OEM_FAMILY_VERIFIED, samsung.coverageTier)
        assertTrue(samsung.isPathVerifiedForBuild)
        assertFalse(samsung.androidCompatibility.isFutureOrPreview)
        assertTrue(samsung.androidCompatibility.apiRestrictions.any { it.contains("local-network") })

        val huawei = DeviceCompatibilityEngine.resolveProfile("Huawei", "Huawei", "Mate 60", 34, "14")
        assertFalse(huawei.gmsAvailable)
        assertFalse(huawei.isPathVerifiedForBuild)
        assertTrue(huawei.batterySettingsPath.contains("Search"))

        val unknown = DeviceCompatibilityEngine.resolveProfile("CustomVendor", "MyPhone", "Z9", 36, "16")
        assertEquals(CoverageTier.D_UNKNOWN, unknown.coverageTier)
        assertFalse(unknown.isPathVerifiedForBuild)
        val res = DeviceCompatibilityEngine.resolveSettingsPath("app_cache", unknown)
        assertFalse(res.isVerifiedPath)
        assertTrue(res.resolvedPath.contains("Search"))

        val android18 = DeviceCompatibilityEngine.resolveVersionCompatibility(38, "18")
        assertTrue(android18.isFutureOrPreview)
    }

    @Test
    fun `multilingual Hinglish Hindi Punjabi and direct ID RAG queries retrieve exact problems and compatibility paths`() {
        val vitals = testVitals()
        val aiService = AiDoctorService()

        // 1. "Mera phone bahut hang ho raha hai"
        val hangMatches = PlaybookRagRetriever.retrieveMatchingIssues("Mera phone bahut hang ho raha hai", vitals, context)
        assertTrue(hangMatches.first().issue.problemId.startsWith("AND-02-"))
        val hangResp = aiService.buildStructuredFallbackResponse(
            userProblem = "Mera phone bahut hang ho raha hai",
            vitals = vitals,
            status = AiServiceStatus.UNAVAILABLE_OFFLINE_FALLBACK,
            context = context
        )
        assertNotNull(hangResp.repairPlan?.matchedIssueId)
        assertTrue(hangResp.repairPlan!!.matchedIssueId!!.startsWith("AND-02-"))
        assertTrue(hangResp.actions.isNotEmpty())

        // 2. "Game apne aap band ho jaata hai" -> AND-11-001
        val gameMatches = PlaybookRagRetriever.retrieveMatchingIssues("Game apne aap band ho jaata hai", vitals, context)
        assertEquals("AND-11-001", gameMatches.first().issue.problemId)

        // 3. "Wi-Fi connect hai par internet nahi chal raha" -> AND-07-001
        val wifiMatches = PlaybookRagRetriever.retrieveMatchingIssues("Wi-Fi connect hai par internet nahi chal raha", vitals, context)
        assertEquals("AND-07-001", wifiMatches.first().issue.problemId)

        // 4. "Battery bahut jaldi khatam ho rahi hai" -> AND-05-001
        val batteryMatches = PlaybookRagRetriever.retrieveMatchingIssues("Battery bahut jaldi khatam ho rahi hai", vitals, context)
        assertEquals("AND-05-001", batteryMatches.first().issue.problemId)

        // 5. "Phone touch nahi le raha" -> AND-08-015 or Cat 08 / Cat 02
        val touchMatches = PlaybookRagRetriever.retrieveMatchingIssues("Phone touch nahi le raha", vitals, context)
        assertTrue(touchMatches.first().issue.categoryNumber in listOf(2, 8))

        // 6. Punjabi: "Mera phone apne aap band ho janda hai" -> AND-01-005 or Cat 01
        val punjabiMatches = PlaybookRagRetriever.retrieveMatchingIssues("Mera phone apne aap band ho janda hai", vitals, context)
        assertEquals(1, punjabiMatches.first().issue.categoryNumber)

        // 7. Direct problem ID lookup: AND-29-010
        val directMatches = PlaybookRagRetriever.retrieveMatchingIssues("AND-29-010", vitals, context)
        assertEquals("AND-29-010", directMatches.first().issue.problemId)
    }

    @Test
    fun `Room persistence and KnowledgeUpdateManager add new issues bump versions and protect verified records`() = runBlocking {
        KnowledgeRepository.initializeWithRoom(context)
        val dao = AppDatabase.getInstance(context).knowledgeDao()
        assertEquals(466, dao.getIssueCount())

        // 1. Add a brand new problem AND-30-001 + update existing AND-02-001
        val updateJson = """
            {
              "issues": [
                {
                  "problem_id": "AND-30-001",
                  "category_number": 30,
                  "category_name": "Foldable Hinge & Posture Problems",
                  "canonical_problem_name": "Foldable screen outer cover display switch delay",
                  "possible_causes": ["Cover hinge sensor lag", "Launcher posture refresh bug"],
                  "diagnostic_questions": ["Does delay happen in Safe Mode?"],
                  "solutions": ["Update launcher via Play Store", "Test outer display rotation toggle"],
                  "automatic_action_ids": ["repair_display_timeout", "repair_default_apps"],
                  "verification_status": "LAB-TESTED"
                },
                {
                  "problem_id": "AND-02-001",
                  "canonical_problem_name": "Phone ka slow chalna",
                  "solutions": ["Verified updated step 1: Free >= 15% storage first", "Step 2: Audit battery hogs & reboot"],
                  "verification_status": "DOC-REFERENCED"
                }
              ]
            }
        """.trimIndent()

        val result = KnowledgeUpdateManager.ingestUpdateContent(context, updateJson, "test_patch_v2_1.json")
        assertTrue(result.success)
        assertEquals(listOf("AND-30-001"), result.addedIssueIds)
        assertEquals(listOf("AND-02-001"), result.updatedIssueIds)
        assertEquals(467, dao.getIssueCount())

        // AND-02-001 version bumped to 2 with archived v1 history
        val updated02001 = KnowledgeRepository.findIssueById("AND-02-001")!!
        assertEquals(2, updated02001.version)
        assertTrue(updated02001.revisionHistory.isNotEmpty())

        // 2. Conflict check: try overwriting LAB-TESTED AND-30-001 -> must flag conflict and preserve LAB-TESTED solution!
        val conflictJson = """
            {
              "problem_id": "AND-30-001",
              "canonical_problem_name": "Foldable screen outer cover display switch delay",
              "solutions": ["Unverified conflicting step"]
            }
        """.trimIndent()
        val conflictResult = KnowledgeUpdateManager.ingestUpdateContent(context, conflictJson, "untrusted_override.json")
        assertTrue(conflictResult.success)
        assertTrue(conflictResult.conflicts.isNotEmpty())
        assertEquals(1, conflictResult.preservedVerifiedCount)
        val stillProtected = KnowledgeRepository.findIssueById("AND-30-001")!!
        assertEquals("LAB-TESTED", stillProtected.verificationStatus)
        assertTrue(stillProtected.solutions.first().contains("Update launcher"))
    }
}
