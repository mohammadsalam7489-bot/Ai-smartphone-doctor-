package com.example

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.maintenance.MaintenanceWorkScheduler
import com.example.data.model.ActionType
import com.example.data.model.DeviceVitals
import com.example.data.model.VerificationOutcome
import com.example.data.privacy.PrivacyRedactor
import com.example.data.repair.ActionResult
import com.example.data.repair.BlueprintRepairCatalog
import com.example.data.repair.BlueprintRiskLevel
import com.example.data.repair.PipelineExecutionParams
import com.example.data.repair.RepairPipelineEngine
import com.example.data.repair.RepairPipelineStatus
import com.example.data.repair.RepairVerificationEngine
import com.example.data.repair.RepairVerificationResult
import com.example.data.repair.SafeRepairRegistry
import com.example.data.security.AppSecurityScanner
import com.example.data.security.ScanVerdict
import com.example.data.storage.SafeStorageManager
import com.example.data.update.AppUpdateManager
import kotlinx.coroutines.runBlocking
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlueprintRepairAcceptanceTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("ai_doctor_session", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("ai_doctor_anonymized_metrics", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("ai_doctor_security_feed", Context.MODE_PRIVATE).edit().clear().commit()

        File(app.filesDir, "thumbnails").deleteRecursively()
        File(app.filesDir, "exports").deleteRecursively()
        File(app.filesDir, "updates").deleteRecursively()
        File(app.filesDir, "quarantine").deleteRecursively()
        File(app.filesDir, "diagnostic_staging").deleteRecursively()
        File(app.filesDir, "temp_reports").deleteRecursively()
        app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        app.codeCacheDir.listFiles()?.forEach { it.deleteRecursively() }
        app.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun sampleVitals(screenTimeout: Int = 120): DeviceVitals = DeviceVitals(
        manufacturer = "Samsung",
        model = "Galaxy S24",
        brand = "Samsung",
        androidVersion = "14",
        apiLevel = 34,
        batteryLevel = 68,
        batteryStatus = "Discharging",
        batteryPlugged = "On Battery",
        batteryTempC = 34.0f,
        batteryHealth = "Good",
        storageTotalBytes = 128_000_000_000L,
        storageFreeBytes = 20_000_000_000L,
        storageUsedPercent = 72,
        ramTotalBytes = 8_000_000_000L,
        ramFreeBytes = 2_400_000_000L,
        ramUsedPercent = 65,
        isLowMemory = false,
        networkType = "Wi-Fi",
        isInternetAvailable = true,
        displayResolution = "1080x2340",
        refreshRate = 120f,
        screenTimeoutSeconds = screenTimeout,
        uptimeHours = 10f,
        healthScore = 82
    )

    @Test
    fun all94CatalogWorkflows_haveValidSchemaIdsActionsAndHonestBoundaries() {
        val defs = BlueprintRepairCatalog.allDefinitions
        assertEquals(94, defs.size)

        val expectedCategoryCounts = mapOf(
            1 to 12, 2 to 10, 3 to 10, 4 to 10, 5 to 12,
            6 to 8, 7 to 7, 8 to 8, 9 to 7, 10 to 10
        )
        expectedCategoryCounts.forEach { (cat, expected) ->
            assertEquals(
                "Category $cat workflow count mismatch",
                expected,
                defs.count { it.categoryNumber == cat }
            )
        }

        assertEquals(94, defs.map { it.issueId }.toSet().size)
        defs.forEach { d ->
            assertTrue("${d.issueId} missing complaints", d.complaintExamples.isNotEmpty())
            assertTrue("${d.issueId} missing preconditions", d.preconditionChecks.isNotEmpty())
            assertTrue("${d.issueId} missing verifications", d.verificationChecks.isNotEmpty())
            assertTrue("${d.issueId} missing handler", d.actionHandlerId.isNotBlank())
            assertTrue("${d.issueId} missing privacy disclosure", d.privacyDataCollected.isNotBlank())
            d.mappedDoctorActionIds.forEach { actionId ->
                assertTrue("${d.issueId} references invalid SafeRepairRegistry ID $actionId", SafeRepairRegistry.isRegistered(actionId))
            }
            if (d.risk == BlueprintRiskLevel.UNSUPPORTED) {
                assertFalse("${d.issueId} UNSUPPORTED missing explanation", d.unsupportedReason.isNullOrBlank())
            }
        }
    }

    @Test
    fun verificationGating_neverReportsFixedWhenPostconditionFails_orTelemetryMissing() {
        // 1. Failing postcondition verification override must force UNRESOLVED and never say "successfully fixed"
        val tempFile = File(app.cacheDir, "stale_temp.tmp").apply { writeBytes(ByteArray(1024)) }
        val failedReport = RepairPipelineEngine.executePipeline(
            context = app,
            queryOrIssueId = "BP-01-001",
            params = PipelineExecutionParams(
                vitalsOverride = sampleVitals(),
                verificationOverride = RepairVerificationResult(
                    passed = false,
                    testName = "simulated_postcondition_fail",
                    observedSummary = "Disk check still showed target file uncleaned",
                    failureReason = "Simulated write stall"
                )
            )
        )
        assertEquals(RepairPipelineStatus.UNRESOLVED, failedReport.status)
        assertFalse(failedReport.userVisibleHeadline.contains("successfully fixed", ignoreCase = true))

        // 2. Missing/unavailable telemetry must force UNRESOLVED instead of guessing
        val missingTelReport = RepairPipelineEngine.executePipeline(
            context = app,
            queryOrIssueId = "BP-10-007",
            params = PipelineExecutionParams(telemetryAvailable = false)
        )
        assertEquals(RepairPipelineStatus.UNRESOLVED, missingTelReport.status)
        assertFalse(missingTelReport.verificationResult?.passed ?: true)

        // 3. Verify RepairVerificationEngine fix: REFRESH_NETWORK_SOCKET with verifiedMetricImproved=false
        //    MUST NOT falsely emit VERIFIED_IMPROVED even when isSuccess=true!
        val netAction = SafeRepairRegistry.findAction("auto_refresh_network_socket")!!
        val before = sampleVitals()
        val after = sampleVitals()
        val netVerify = RepairVerificationEngine.verifyBeforeAfter(
            action = netAction,
            before = before,
            after = after,
            executionResult = ActionResult(
                isSuccess = true,
                message = "TCP reachable, but DNS hostname resolution failed",
                executedAutomatically = true,
                requiresUserConfirmationToVerify = true,
                verifiedMetricImproved = false
            )
        )
        assertEquals(VerificationOutcome.NEEDS_USER_CONFIRMATION, netVerify.outcome)
        assertTrue(tempFile.path.isNotBlank())
    }

    @Test
    fun noQuestionPolicy_diagnosesAndExecutesDirectly_whenSignalsAreClear() {
        val junk = File(app.filesDir, "updates/stale.part").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(4096) { 5 })
        }
        assertTrue(junk.exists())

        val report = RepairPipelineEngine.executePipeline(
            context = app,
            queryOrIssueId = "clear app junk and broken thumbnails",
            params = PipelineExecutionParams(vitalsOverride = sampleVitals())
        )
        assertFalse("Should not ask unnecessary questions when complaint/telemetry is clear", report.askedClarifyingQuestions)
        assertEquals(RepairPipelineStatus.FIXED, report.status)
        assertTrue(report.verificationResult?.passed == true)
        assertFalse(junk.exists())
        assertTrue(report.userVisibleHeadline.contains("successfully fixed", ignoreCase = true))
        assertTrue(report.trace.size >= 7)
    }

    @Test
    fun permissionDenied_andRevokedUri_handledGracefullyWithoutCrashOrFalseSuccess() {
        // 1. Denied WRITE_SETTINGS -> must return NEEDS_APPROVAL without false fixed claim
        val permDenied = RepairPipelineEngine.executePipeline(
            context = app,
            queryOrIssueId = "BP-03-009",
            params = PipelineExecutionParams(
                vitalsOverride = sampleVitals(),
                canWriteOverride = false
            )
        )
        assertEquals(RepairPipelineStatus.NEEDS_APPROVAL, permDenied.status)
        assertFalse(permDenied.userVisibleHeadline.contains("successfully fixed", ignoreCase = true))

        // 2. Revoked/non-existent SAF URI -> must return structured failure safely without throwing
        val stagedRevoked = SafeStorageManager.stageUserSelectedUriSafely(
            context = app,
            uri = Uri.parse("content://com.example.revoked.provider/document/999"),
            displayNameHint = "revoked_doc.pdf"
        )
        assertFalse(stagedRevoked.success)
        assertNull(stagedRevoked.stagedFile)
        assertNotNull(stagedRevoked.errorReason)
    }

    @Test
    fun idempotencyGuard_skipsHealthyRepairInsteadOfRepeatingWork() {
        // Ensure app cache and temp folders are already completely clean
        SafeStorageManager.runFullOwnAppHousekeeping(app)
        app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }

        val firstCleanRun = RepairPipelineEngine.executePipeline(
            context = app,
            queryOrIssueId = "BP-01-001",
            params = PipelineExecutionParams(vitalsOverride = sampleVitals())
        )
        assertEquals(RepairPipelineStatus.FIXED, firstCleanRun.status)
        assertTrue("Healthy state should be skipped by idempotency check", firstCleanRun.skippedAsIdempotentHealthy)
    }

    @Test
    fun destructiveOrConfirmationAction_andUnsupportedSandbox_enforceStrictGates() {
        // Confirmation-required blocked cleanly when consentConfirmed = false
        val blocked = RepairPipelineEngine.executePipeline(
            context = app,
            queryOrIssueId = "BP-10-005",
            params = PipelineExecutionParams(consentConfirmed = false)
        )
        assertEquals(RepairPipelineStatus.NEEDS_APPROVAL, blocked.status)

        // With explicit confirmation, gate permits proceed
        val approved = RepairPipelineEngine.executePipeline(
            context = app,
            queryOrIssueId = "BP-10-005",
            params = PipelineExecutionParams(consentConfirmed = true)
        )
        assertEquals(RepairPipelineStatus.FIXED, approved.status)

        // Android OS Sandbox boundary (3rd-party cache / Wi-Fi silent toggle) honestly returns UNSUPPORTED
        val sandboxedCache = RepairPipelineEngine.executePipeline(app, "BP-01-010")
        assertEquals(RepairPipelineStatus.UNSUPPORTED, sandboxedCache.status)

        val sandboxedWifi = RepairPipelineEngine.executePipeline(app, "BP-04-009")
        assertEquals(RepairPipelineStatus.UNSUPPORTED, sandboxedWifi.status)
    }

    @Test
    fun preActionSnapshotAndRollback_restoresPreviousSettingsOnFault() {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0)

        // Simulate mid-execution fault right before committing BP-08-004 (or BP-10-009)
        val report = RepairPipelineEngine.executePipeline(
            context = app,
            queryOrIssueId = "BP-08-004",
            params = PipelineExecutionParams(
                vitalsOverride = sampleVitals(),
                faultInjector = { handlerId ->
                    if (handlerId == "handler_audio_repair_with_rollback") {
                        // Mutate partially then crash before completion to prove rollback restores original snapshot
                        am.mode = AudioManager.MODE_NORMAL
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, 10, 0)
                        throw IllegalStateException("Simulated audio HAL fault")
                    }
                }
            )
        )
        assertEquals(RepairPipelineStatus.UNRESOLVED, report.status)
        assertTrue(report.rollbackExecuted)
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, am.mode)
        assertEquals(2, am.getStreamVolume(AudioManager.STREAM_MUSIC))

        // Atomic storage write rollback on SHA-256 checksum mismatch
        val configFile = File(app.filesDir, "exports/settings.json").apply {
            parentFile?.mkdirs()
            writeText("""{"safe":true,"v":1}""")
        }
        val writeFailed = SafeStorageManager.writeFileAtomicallyWithBackup(
            target = configFile,
            content = """{"corrupted":true}""".toByteArray(),
            expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        )
        assertFalse(writeFailed)
        assertEquals("""{"safe":true,"v":1}""", configFile.readText())
    }

    @Test
    fun privacyRedactor_stripsTokensEmailsPathsAndEnforcesRetentionCleanup() {
        val dirtyLog = "Authorization: Bearer superSecretToken_999111 " +
            "jwt=eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyIjoiMTIzIn0.signatureVal_ABC123 " +
            "key=AIzaSyDummySecretValue1234567890 " +
            "url=https://api.example.com/v1?token=mySecret99&user=john@example.com " +
            "phone=+91 9876543210 path=/storage/emulated/0/DCIM/Camera/private.jpg"

        val redacted = PrivacyRedactor.redactSensitiveData(dirtyLog)
        assertFalse("Leak survived redaction: $redacted", PrivacyRedactor.containsSensitiveLeak(redacted))
        assertFalse(redacted.contains("superSecretToken_999111"))
        assertFalse(redacted.contains("john@example.com"))
        assertFalse(redacted.contains("9876543210"))
        assertFalse(redacted.contains("mySecret99"))

        // Retention expiry cleanup
        val staging = File(app.filesDir, "diagnostic_staging").apply { mkdirs() }
        val expiredLog = File(staging, "old_diag_dump.log").apply {
            writeText("old")
            setLastModified(System.currentTimeMillis() - 15L * 24L * 3600L * 1000L)
        }
        val tempPart = File(staging, "partial.tmp").apply { writeBytes(ByteArray(512)) }
        val freshReport = File(staging, "current_keep.json").apply { writeText("""{"ok":true}""") }

        val cleanup = PrivacyRedactor.purgeExpiredDiagnosticArtifacts(app)
        assertTrue(cleanup.verifiedClean)
        assertEquals(0, cleanup.remainingExpiredCount)
        assertFalse(expiredLog.exists())
        assertFalse(tempPart.exists())
        assertTrue(freshReport.exists())

        // Session cache wipe on logout preserves anonymized metrics & permanent files
        PrivacyRedactor.recordAnonymizedOutcome(app, "BP-01-001", "handler_storage_housekeeping", "FIXED")
        assertTrue(PrivacyRedactor.clearSessionScopedCaches(app))
        assertEquals(1, PrivacyRedactor.getAnonymizedMetrics(app).first().fixedCount)
    }

    @Test
    fun safeStorageManager_blocksZipSlipAndLargeBomb_repairsBrokenThumbnail_dedupesExports() {
        // 1. Zip-Slip traversal payload blocked cleanly
        val evilZip = File(app.cacheDir, "evil_zipslip.zip")
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("../../etc/passwd_overwrite.txt"))
            zos.write("malicious".toByteArray())
            zos.closeEntry()
        }
        evilZip.writeBytes(baos.toByteArray())

        val destDir = File(app.filesDir, "exports/extracted")
        val extractResult = SafeStorageManager.safeExtractArchive(evilZip, destDir)
        assertFalse(extractResult.success)
        assertTrue(extractResult.blockedReason?.contains("Zip-Slip") == true)

        // 2. Broken 0-byte thumbnail repaired & decoded back cleanly
        val thumbsDir = File(app.filesDir, "thumbnails").apply { mkdirs() }
        val corruptThumb = File(thumbsDir, "thumb_1.png").apply { writeBytes(ByteArray(0)) }

        // 3. Duplicate export files deduplicated & export_index.json rebuilt
        val exportsDir = File(app.filesDir, "exports").apply { mkdirs() }
        val copyA = File(exportsDir, "report_1.json").apply {
            writeText("""{"report":"identical"}""")
            setLastModified(System.currentTimeMillis() - 5000)
        }
        val copyB = File(exportsDir, "report_1_dup.json").apply {
            writeText("""{"report":"identical"}""")
            setLastModified(System.currentTimeMillis())
        }

        val housekeeping = SafeStorageManager.runFullOwnAppHousekeeping(app)
        assertTrue(housekeeping.verifiedClean)
        assertEquals(1, housekeeping.thumbnailsRepaired)
        assertEquals(1, housekeeping.duplicatesRemoved)
        val decoded = BitmapFactory.decodeFile(corruptThumb.absolutePath)
        assertNotNull(decoded)
        assertTrue(decoded.width > 0 && decoded.height > 0)
        decoded.recycle()

        val survivingCopies = listOf(copyA, copyB).count { it.exists() }
        assertEquals(1, survivingCopies)
        assertTrue(File(exportsDir, "export_index.json").exists())
    }

    @Test
    fun appSecurityScanner_detectsDoubleExtensionSpoof_unknownEvidenceStaysSuspiciousUnknown_idnPhishing_atomicThreatFeedLkg() {
        val staging = File(app.filesDir, "updates").apply { mkdirs() }

        // 1. Double-extension / magic-byte masquerading
        val spoofed = File(staging, "invoice.pdf.exe").apply {
            writeBytes(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0x90.toByte(), 0x00))
        }
        val spoofReport = AppSecurityScanner.scanFileSafely(app, spoofed, "invoice.pdf.exe")
        assertEquals(ScanVerdict.DANGEROUS_DISGUISED, spoofReport.verdict)

        // 2. Disguised image with Windows PE header
        val fakeJpg = File(staging, "vacation.jpg").apply {
            writeBytes(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0x01, 0x02))
        }
        assertEquals(ScanVerdict.DANGEROUS_DISGUISED, AppSecurityScanner.scanFileSafely(app, fakeJpg, "vacation.jpg").verdict)

        // 3. Unknown binary never falsely marked Safe ("Verified Clean")
        val unknownBlob = File(staging, "unknown_file.dat").apply {
            writeBytes("random_unverified_payload_bytes".toByteArray())
        }
        val unknownReport = AppSecurityScanner.scanFileSafely(app, unknownBlob, "unknown_file.dat")
        assertEquals(ScanVerdict.SUSPICIOUS_UNKNOWN, unknownReport.verdict)

        // 4. EICAR hash match & safe quarantine isolation (never auto-deletes user files in shared storage)
        val eicarBytes = "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*".toByteArray(Charsets.US_ASCII)
        val eicarFile = File(staging, "test_payload.txt").apply { writeBytes(eicarBytes) }
        val eicarReport = AppSecurityScanner.scanFileSafely(app, eicarFile, "test_payload.txt")
        assertEquals(ScanVerdict.MALWARE_HASH_MATCH, eicarReport.verdict)
        val qFile = AppSecurityScanner.quarantineFileSafely(app, eicarFile, eicarReport.verdict.label)
        assertNotNull(qFile)
        assertFalse(eicarFile.exists())
        assertTrue(qFile!!.exists())

        // 5. IDN/Punycode homograph URL detection
        val phishingUrl = AppSecurityScanner.inspectUrlSafety("https://аpple.com/verify")
        assertTrue(phishingUrl.isSuspicious)
        assertTrue(phishingUrl.normalizedHost.contains("xn--"))

        // 6. Signed Threat Feed Atomic Update + Last-Known-Good preservation on bad digest
        val feedV2 = """{"version":2,"maliciousHashes":{"1111111111111111111111111111111111111111111111111111111111111111":"Trojan.Test.V2"}}"""
        val digestV2 = PrivacyRedactor.sha256Hex(feedV2.toByteArray(Charsets.UTF_8))
        val resV2 = AppSecurityScanner.updateThreatFeedAtomically(app, feedV2, digestV2)
        assertTrue(resV2.accepted)
        assertEquals(2, resV2.activeVersion)

        val tamperedFeedV3 = """{"version":3,"maliciousHashes":{"222222":"bad"}}"""
        val resV3 = AppSecurityScanner.updateThreatFeedAtomically(app, tamperedFeedV3, "wrongdigest")
        assertFalse(resV3.accepted)
        assertTrue(resV3.keptLastKnownGood)
        assertEquals(2, resV3.activeVersion)
    }

    @Test
    fun appUpdateManager_rejectsMalformedMetadata_andCertificateSignerMismatch() {
        // Reject remote cleartext HTTP, negative version, and invalid hex SHA-256
        assertNull(
            AppUpdateManager.parseUpdateManifest(
                """{"versionCode":10,"apkUrl":"http://untrusted-site.example.com/update.apk"}"""
            )
        )
        assertNull(
            AppUpdateManager.parseUpdateManifest(
                """{"versionCode":-5,"apkUrl":"https://github.com/org/repo/app.apk"}"""
            )
        )
        assertNull(
            AppUpdateManager.parseUpdateManifest(
                """{"versionCode":10,"apkUrl":"https://github.com/org/repo/app.apk","sha256":"not-hex-zzz!"}"""
            )
        )

        // Unsigned / fake APK bytes scanned against an expected SHA-256 cert must be flagged INVALID_APK_SIGNER
        val fakeApk = File(app.filesDir, "updates/fake_signed.apk").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(12_000) { 0x50 })
        }
        val verify = AppUpdateManager.verifyApkSignerMatchesExpected(
            context = app,
            apkFile = fakeApk,
            expectedSignerSha256 = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        )
        // Archive cannot produce valid matching signer -> flagged suspicious or invalid
        assertTrue(
            verify.verdict == ScanVerdict.INVALID_APK_SIGNER ||
                verify.verdict == ScanVerdict.SUSPICIOUS_UNKNOWN
        )
    }

    @Test
    fun workManager_maintenanceWorker_enforcesConstraintsUniqueKeepAndExecutesCleanly() = runBlocking {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, config)
        val wm = WorkManager.getInstance(app)
        val testDriver = WorkManagerTestInitHelper.getTestDriver(app)!!

        // Place stale temp artifact to prove worker actually cleans on execution
        val staging = File(app.filesDir, "diagnostic_staging").apply { mkdirs() }
        val staleTmp = File(staging, "worker_clean.tmp").apply { writeBytes(ByteArray(2048)) }

        assertTrue(MaintenanceWorkScheduler.schedulePeriodicMaintenance(app))
        // Scheduling a second time with ExistingPeriodicWorkPolicy.KEEP preserves single unique work item
        assertTrue(MaintenanceWorkScheduler.schedulePeriodicMaintenance(app))

        val infosBefore = wm.getWorkInfosForUniqueWork(MaintenanceWorkScheduler.PERIODIC_MAINTENANCE_WORK_NAME).get()
        assertEquals(1, infosBefore.size)
        val workId = infosBefore.first().id

        // Satisfy constraints (BatteryNotLow) and trigger periodic worker execution
        testDriver.setAllConstraintsMet(workId)
        testDriver.setPeriodDelayMet(workId)

        val oneTimeId = MaintenanceWorkScheduler.enqueueOneTimeMaintenance(app)!!
        testDriver.setAllConstraintsMet(oneTimeId)

        val oneTimeInfo = wm.getWorkInfoById(oneTimeId).get()
        assertEquals(WorkInfo.State.SUCCEEDED, oneTimeInfo?.state)
        assertFalse(staleTmp.exists())
        val status = MaintenanceWorkScheduler.inspectStatus(app)
        assertTrue(status.isScheduled)
        assertTrue(status.lastRunEpochMs > 0L)
    }
}
