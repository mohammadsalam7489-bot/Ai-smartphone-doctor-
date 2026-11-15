package com.example.data.repair

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.model.AutomationCapability
import com.example.data.model.DeviceVitals
import com.example.data.model.DoctorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AutoFixItem(
    val id: String,
    val title: String,
    val category: String,
    val isAutoFixed: Boolean,
    val statusSummary: String,
    val capability: AutomationCapability = AutomationCapability.NORMAL_API,
    val restrictionReason: String? = null,
    val solutionGuide: List<String> = emptyList(),
    val action: DoctorAction? = null
)

data class AutoFixReport(
    val initialScore: Int,
    val afterScore: Int,
    val beforeSummary: String,
    val afterSummary: String,
    val autoFixedItems: List<AutoFixItem>,
    val restrictedItems: List<AutoFixItem>,
    val bytesCleared: Long,
    val ramFreeBeforeBytes: Long,
    val ramFreeAfterBytes: Long
)

/**
 * Honest Smart Auto-Fix & System Diagnostic Engine.
 * Executes ONLY real Android API actions within app sandbox and measures actual before/after vitals.
 * Never fabricates cleared megabytes or fake health score boosts.
 */
object AutoFixEngine {

    suspend fun executeSmartScanAndFix(
        context: Context,
        beforeVitals: DeviceVitals
    ): AutoFixReport = withContext(Dispatchers.IO) {
        val autoFixedList = mutableListOf<AutoFixItem>()
        val restrictedList = mutableListOf<AutoFixItem>()
        val oemProfile = DeviceCompatibilityEngine.getProfile(context)

        // 1. REAL AUTO-FIX: Clear ONLY this app's cache + external cache + stale update APKs (exact measured bytes)
        val externalCache = runCatching { context.externalCacheDir }.getOrNull()
        val cacheBefore = getDirSize(context.cacheDir) + getDirSize(context.codeCacheDir) + getDirSize(externalCache)
        val clearRes = SafeRepairActions.executeActionAsync(
            context,
            SafeRepairRegistry.findAction("auto_clear_own_cache")!!
        )
        val cacheAfter = getDirSize(context.cacheDir) + getDirSize(context.codeCacheDir) + getDirSize(externalCache)
        val clearedBytes = (cacheBefore - cacheAfter).coerceAtLeast(0L)

        autoFixedList.add(
            AutoFixItem(
                id = "autofix_cache",
                title = "Internal App Cache & Stale APK Cleanup",
                category = "Storage (Normal API)",
                isAutoFixed = true,
                capability = AutomationCapability.NORMAL_API,
                statusSummary = clearRes.message
            )
        )

        // 2. REAL AUTO-FIX: Deep Network DNS & Socket Verification
        val netRes = SafeRepairActions.executeActionAsync(
            context,
            SafeRepairRegistry.findAction("auto_refresh_network_socket")!!
        )
        autoFixedList.add(
            AutoFixItem(
                id = "autofix_network",
                title = "DNS, Captive Portal & Socket Audit",
                category = "Connectivity (Normal API)",
                isAutoFixed = true,
                capability = AutomationCapability.NORMAL_API,
                statusSummary = netRes.message
            )
        )

        // 3. REAL AUTO-ACTION: Optimize Screen Timeout + Adaptive Brightness IF WRITE_SETTINGS granted
        if (beforeVitals.screenTimeoutSeconds > 60 || beforeVitals.screenTimeoutSeconds < 0) {
            val canWrite = runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
            val displayAction = SafeRepairRegistry.findAction("auto_optimize_display_power")
                ?: SafeRepairRegistry.findAction("auto_screen_timeout_safe")!!
            if (canWrite) {
                val timeoutResult = SafeRepairActions.executeActionAsync(context, displayAction)
                if (timeoutResult.isSuccess) {
                    autoFixedList.add(
                        AutoFixItem(
                            id = "autofix_timeout",
                            title = "Display Timeout & Adaptive Brightness Optimized",
                            category = "Display (Special Permission Granted)",
                            isAutoFixed = true,
                            capability = AutomationCapability.SPECIAL_PERMISSION,
                            statusSummary = timeoutResult.message
                        )
                    )
                }
            } else {
                restrictedList.add(
                    AutoFixItem(
                        id = "restricted_display",
                        title = "Screen Sleep Timeout (${if (beforeVitals.screenTimeoutSeconds >= 0) "${beforeVitals.screenTimeoutSeconds}s" else "Auto"})",
                        category = "Display (Special Permission Required)",
                        isAutoFixed = false,
                        capability = AutomationCapability.SPECIAL_PERMISSION,
                        statusSummary = "Granting 'Modify system settings' lets the app auto-set 30s sleep timeout & Adaptive Brightness.",
                        restrictionReason = "Modifying system display settings requires 'Modify system settings' (WRITE_SETTINGS) permission or manual change in Display Settings.",
                        solutionGuide = listOf(
                            "Tap 'Apply' to grant 'Modify system settings' or open Display Settings.",
                            "Enable Adaptive Brightness and set Screen Timeout to 30 seconds."
                        ),
                        action = displayAction
                    )
                )
            }
        }

        // 4. RESTRICTED CHECK: Heavy Third-Party App Cache (Android Sandbox Restriction)
        if (beforeVitals.storageVerified &&
            (beforeVitals.storageUsedPercent > 70 || beforeVitals.storageFreeBytes < 8L * 1024 * 1024 * 1024)
        ) {
            restrictedList.add(
                AutoFixItem(
                    id = "restricted_app_cache",
                    title = "Third-Party App Cache & Storage (${beforeVitals.storageUsedPercent}% used)",
                    category = "Storage (Android Sandbox)",
                    isAutoFixed = false,
                    capability = AutomationCapability.MANUAL_SETTINGS,
                    statusSummary = "${RealDeviceDiagnostics.formatBytes(beforeVitals.storageFreeBytes)} free of ${RealDeviceDiagnostics.formatBytes(beforeVitals.storageTotalBytes)}.",
                    restrictionReason = "Android Sandboxing forbids third-party apps from reading or deleting other apps' cache sizes silently.",
                    solutionGuide = listOf(
                        "Open ${oemProfile.appCacheSettingsPath}",
                        "Pick heavy apps (WhatsApp, YouTube, Instagram, Chrome).",
                        "Tap 'Clear Cache' ONLY (never tap 'Clear Data' without backup)."
                    ),
                    action = SafeRepairRegistry.findAction("repair_storage_cleaner")
                )
            )
        }

        // 5. RESTRICTED CHECK: Thermal / Battery Saver (only evaluate verified readings)
        val highTempVerified = beforeVitals.temperatureVerified && beforeVitals.batteryTempC > 37f
        val lowBattVerified = beforeVitals.batteryVerified && beforeVitals.batteryLevel in 0..34
        if (highTempVerified || lowBattVerified || !beforeVitals.isPowerSaveMode) {
            val tempDisplay = if (beforeVitals.temperatureVerified) "${beforeVitals.batteryTempC}°C" else "Temp Unavailable"
            val battDisplay = if (beforeVitals.batteryVerified) "${beforeVitals.batteryLevel}%" else "Unavailable"
            restrictedList.add(
                AutoFixItem(
                    id = "restricted_battery_saver",
                    title = "Battery Saver & Background Limits ($tempDisplay)",
                    category = "Battery & Thermal (OS Policy)",
                    isAutoFixed = false,
                    capability = AutomationCapability.MANUAL_SETTINGS,
                    statusSummary = "Battery $battDisplay, $tempDisplay, PowerSaver=${if (beforeVitals.isPowerSaveMode) "ON" else "OFF"}.",
                    restrictionReason = "Android restricts background apps from enabling system Battery Saver or killing other apps silently.",
                    solutionGuide = listOf(
                        "Navigate to: ${oemProfile.batterySettingsPath}",
                        "Enable Battery Saver / Adaptive Battery if draining fast.",
                        "Restrict background activity for unused heavy apps."
                    ),
                    action = SafeRepairRegistry.findAction("repair_battery_saver")
                )
            )
        }

        // 6. RESTRICTED CHECK: High RAM Pressure (only evaluate verified readings)
        if (beforeVitals.ramVerified && (beforeVitals.ramUsedPercent > 75 || beforeVitals.isLowMemory)) {
            restrictedList.add(
                AutoFixItem(
                    id = "restricted_ram",
                    title = "High RAM Usage (${beforeVitals.ramUsedPercent}% used)",
                    category = "Memory (OS Process Isolation)",
                    isAutoFixed = false,
                    capability = AutomationCapability.MANUAL_SETTINGS,
                    statusSummary = "${RealDeviceDiagnostics.formatBytes(beforeVitals.ramFreeBytes)} free (LowMem=${beforeVitals.isLowMemory}).",
                    restrictionReason = "Android isolates processes; non-root apps cannot force-stop other apps' background services.",
                    solutionGuide = listOf(
                        "Tap below to open App Manager and Force Stop frozen apps.",
                        "Or restart your device to flush system memory leaks."
                    ),
                    action = SafeRepairRegistry.findAction("repair_app_cache")
                )
            )
        }

        // Collect REAL After Vitals (never inflate score synthetically!)
        val afterVitals = RealDeviceDiagnostics.getDeviceVitals(context)
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val afterMem = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(afterMem)

        val beforeRamStr = if (beforeVitals.ramVerified) RealDeviceDiagnostics.formatBytes(beforeVitals.ramFreeBytes) else "Unavailable"
        val afterRamStr = if (afterVitals.ramVerified) RealDeviceDiagnostics.formatBytes(afterVitals.ramFreeBytes) else "Unavailable"
        val beforeSleepStr = if (beforeVitals.screenTimeoutSeconds >= 0) "${beforeVitals.screenTimeoutSeconds}s" else "Unavailable"
        val afterSleepStr = if (afterVitals.screenTimeoutSeconds >= 0) "${afterVitals.screenTimeoutSeconds}s" else "Unavailable"

        AutoFixReport(
            initialScore = beforeVitals.healthScore,
            afterScore = afterVitals.healthScore,
            beforeSummary = "Score ${beforeVitals.healthScore}/100 • RAM Free $beforeRamStr • Sleep $beforeSleepStr",
            afterSummary = "Score ${afterVitals.healthScore}/100 • RAM Free $afterRamStr • Sleep $afterSleepStr",
            autoFixedItems = autoFixedList,
            restrictedItems = restrictedList,
            bytesCleared = clearedBytes,
            ramFreeBeforeBytes = beforeVitals.ramFreeBytes,
            ramFreeAfterBytes = afterVitals.ramFreeBytes
        )
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return total
    }

    private fun deleteDirContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) child.deleteRecursively() else child.delete()
        }
    }
}
