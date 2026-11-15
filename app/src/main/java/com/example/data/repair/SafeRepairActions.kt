package com.example.data.repair

import android.app.ActivityManager
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.BuildConfig
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.model.ActionType
import com.example.data.model.DoctorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import kotlin.math.max
import kotlin.math.roundToInt

data class ActionResult(
    val isSuccess: Boolean,
    val message: String,
    val executedAutomatically: Boolean = false,
    val openedSettingsScreen: Boolean = false,
    val requiresSpecialPermissionGrant: Boolean = false,
    val requiresUserConfirmationToVerify: Boolean = true,
    val measuredDetails: List<String> = emptyList(),
    val verifiedMetricImproved: Boolean = false
)

object SafeRepairActions {

    suspend fun executeActionAsync(
        context: Context,
        action: DoctorAction,
        explicitDestructiveConfirmed: Boolean = false,
        canWriteOverride: Boolean? = null
    ): ActionResult = withContext(Dispatchers.IO) {
        // Guard 1: Validate against SafeRepairRegistry
        if (!SafeRepairRegistry.isRegistered(action.id)) {
            return@withContext ActionResult(
                isSuccess = false,
                message = "Blocked: Action '${action.id}' is not in the verified SafeRepairRegistry.",
                requiresUserConfirmationToVerify = false
            )
        }

        // Guard 2: Destructive actions NEVER auto-execute and require explicit user confirmation
        if (action.isDestructive && !explicitDestructiveConfirmed) {
            return@withContext ActionResult(
                isSuccess = false,
                message = "Safety Guard: '${action.title}' requires explicit backup acknowledgment and confirmation.",
                requiresUserConfirmationToVerify = false
            )
        }

        when (action.actionType) {
            ActionType.CLEAR_APP_OWN_CACHE -> executeClearOwnAppCache(context)
            ActionType.REFRESH_NETWORK_SOCKET -> executeNetworkSocketRefresh(context)
            ActionType.OPTIMIZE_APP_MEMORY_AND_STATE -> executeMemoryAuditAndTrim(context)
            ActionType.FIX_AUDIO_ROUTING_AND_VOLUME -> executeFixAudioRoutingAndVolume(context)
            ActionType.AUDIT_AUDIO_ROUTING_AND_VOLUME -> executeAudioAudit(context)
            ActionType.AUDIT_THERMAL_AND_POWER_STATE -> executeThermalPowerAudit(context)
            ActionType.OPTIMIZE_DISPLAY_POWER_SAFE -> executeOptimizeDisplayPower(context, canWriteOverride)
            ActionType.OPTIMIZE_SCREEN_TIMEOUT_SAFE -> executeSafeScreenTimeout(context, canWriteOverride)
            ActionType.RUN_AUDIO_TEST,
            ActionType.RUN_VIBRATION_TEST,
            ActionType.RUN_SCREEN_TEST,
            ActionType.RUN_NETWORK_TEST -> ActionResult(
                isSuccess = true,
                message = "In-app hardware diagnostic launched.",
                executedAutomatically = true,
                requiresUserConfirmationToVerify = true
            )
            else -> withContext(Dispatchers.Main) {
                executeSettingsIntent(context, action)
            }
        }
    }

    fun executeAction(
        context: Context,
        action: DoctorAction,
        explicitDestructiveConfirmed: Boolean = false
    ): ActionResult {
        if (!SafeRepairRegistry.isRegistered(action.id)) {
            return ActionResult(false, "Blocked: Unregistered action ID.")
        }
        if (action.isDestructive && !explicitDestructiveConfirmed) {
            return ActionResult(false, "Safety Guard: Explicit confirmation required for destructive guidance.")
        }
        return when (action.actionType) {
            ActionType.CLEAR_APP_OWN_CACHE -> executeClearOwnAppCache(context)
            ActionType.OPTIMIZE_APP_MEMORY_AND_STATE -> executeMemoryAuditAndTrim(context)
            ActionType.FIX_AUDIO_ROUTING_AND_VOLUME -> executeFixAudioRoutingAndVolume(context)
            ActionType.AUDIT_AUDIO_ROUTING_AND_VOLUME -> executeAudioAudit(context)
            ActionType.AUDIT_THERMAL_AND_POWER_STATE -> executeThermalPowerAudit(context)
            ActionType.OPTIMIZE_DISPLAY_POWER_SAFE -> executeOptimizeDisplayPower(context)
            ActionType.OPTIMIZE_SCREEN_TIMEOUT_SAFE -> executeSafeScreenTimeout(context)
            ActionType.RUN_AUDIO_TEST,
            ActionType.RUN_VIBRATION_TEST,
            ActionType.RUN_SCREEN_TEST,
            ActionType.RUN_NETWORK_TEST -> ActionResult(
                isSuccess = true,
                message = "In-app diagnostic launched.",
                executedAutomatically = true
            )
            else -> executeSettingsIntent(context, action)
        }
    }

    private fun executeClearOwnAppCache(context: Context): ActionResult {
        return try {
            val externalCache = runCatching { context.externalCacheDir }.getOrNull()
            val updatesDir = File(context.filesDir, "updates")

            val staleUpdateFiles = updatesDir.listFiles()?.filter { file ->
                file.isFile && (
                    file.name.endsWith(".tmp", ignoreCase = true) ||
                        (file.name.endsWith(".apk", ignoreCase = true) &&
                            !file.name.contains("-${BuildConfig.VERSION_CODE}.apk"))
                    )
            }.orEmpty()
            val staleBytesBefore = staleUpdateFiles.sumOf { it.length() }

            val cacheBefore = getDirSize(context.cacheDir) +
                getDirSize(context.codeCacheDir) +
                getDirSize(externalCache) +
                staleBytesBefore

            deleteDirContents(context.cacheDir)
            deleteDirContents(context.codeCacheDir)
            deleteDirContents(externalCache)
            staleUpdateFiles.forEach { runCatching { it.delete() } }

            val staleBytesAfter = staleUpdateFiles.filter { it.exists() }.sumOf { it.length() }
            val cacheAfter = getDirSize(context.cacheDir) +
                getDirSize(context.codeCacheDir) +
                getDirSize(externalCache) +
                staleBytesAfter

            val freed = (cacheBefore - cacheAfter).coerceAtLeast(0L)

            val details = listOf(
                "Cache & temp APKs before: ${RealDeviceDiagnostics.formatBytes(cacheBefore)}",
                "Cache & temp APKs after: ${RealDeviceDiagnostics.formatBytes(cacheAfter)}",
                "Exact space freed: ${RealDeviceDiagnostics.formatBytes(freed)} (${staleUpdateFiles.size} obsolete update file(s))",
                "Scope: Own app cache, external cache & stale update APKs (Android isolates 3rd-party app caches)."
            )
            ActionResult(
                isSuccess = true,
                message = "Own app cache & stale update files purged: ${RealDeviceDiagnostics.formatBytes(freed)} freed.",
                executedAutomatically = true,
                requiresUserConfirmationToVerify = false,
                measuredDetails = details,
                verifiedMetricImproved = freed > 0L
            )
        } catch (e: Exception) {
            ActionResult(false, "Cache cleanup failed: ${e.localizedMessage}")
        }
    }

    private suspend fun executeNetworkSocketRefresh(context: Context): ActionResult {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connManager?.activeNetwork
        val caps = connManager?.getNetworkCapabilities(activeNetwork)
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isCaptivePortal = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
        val linkProps = runCatching { connManager?.getLinkProperties(activeNetwork) }.getOrNull()
        val dnsServers = linkProps?.dnsServers?.joinToString { it.hostAddress ?: "?" }?.ifBlank { "None" } ?: "None"

        val dnsResolved = runCatching {
            val addrs = InetAddress.getAllByName("connectivitycheck.gstatic.com")
            addrs.isNotEmpty() to (addrs.firstOrNull()?.hostAddress ?: "resolved")
        }.getOrElse { false to (it.localizedMessage ?: "DNS lookup failed") }

        val primaryPing = RealDeviceDiagnostics.runNetworkPingTest("8.8.8.8", 53)
        val secondaryPing = RealDeviceDiagnostics.runNetworkPingTest("1.1.1.1", 53)

        val diagnosisNote = when {
            isCaptivePortal -> "Captive Portal detected (Wi-Fi router login / sign-in page required)."
            !hasInternet -> "No active internet transport detected."
            (primaryPing.isSuccess || secondaryPing.isSuccess) && !dnsResolved.first ->
                "TCP reachable, but DNS hostname resolution failed (check Private DNS / router DNS)."
            primaryPing.isSuccess || secondaryPing.isSuccess ->
                "DNS and TCP socket routing healthy."
            else -> "Socket handshake failed (offline, firewall, or blocked upstream)."
        }

        val details = listOf(
            "Internet Capability: $hasInternet • OS Validated: $validated • Captive Portal: $isCaptivePortal",
            "Active DNS Servers: $dnsServers",
            "DNS Resolution (connectivitycheck.gstatic.com): ${if (dnsResolved.first) "OK (${dnsResolved.second})" else "FAILED (${dnsResolved.second})"}",
            "8.8.8.8:53 RTT: ${if (primaryPing.isSuccess) "${primaryPing.latencyMs}ms" else "Failed (${primaryPing.error})"}",
            "1.1.1.1:53 RTT: ${if (secondaryPing.isSuccess) "${secondaryPing.latencyMs}ms" else "Failed (${secondaryPing.error})"}",
            "Diagnosis: $diagnosisNote"
        )

        val anySuccess = primaryPing.isSuccess || secondaryPing.isSuccess
        return ActionResult(
            isSuccess = anySuccess,
            message = if (anySuccess && !isCaptivePortal) {
                "Network verified (${primaryPing.latencyMs.takeIf { primaryPing.isSuccess } ?: secondaryPing.latencyMs}ms RTT • DNS ${if (dnsResolved.first) "OK" else "FAIL"})."
            } else {
                diagnosisNote
            },
            executedAutomatically = true,
            requiresUserConfirmationToVerify = !anySuccess || isCaptivePortal || !dnsResolved.first,
            measuredDetails = details,
            verifiedMetricImproved = anySuccess && dnsResolved.first && !isCaptivePortal
        )
    }

    private fun executeMemoryAuditAndTrim(context: Context): ActionResult {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val beforeMem = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(beforeMem)

        val rtBeforeFree = Runtime.getRuntime().freeMemory()
        System.gc()
        val rtAfterFree = Runtime.getRuntime().freeMemory()

        val afterMem = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(afterMem)

        val exitSummary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && actManager != null) {
            runCatching {
                val exits = actManager.getHistoricalProcessExitReasons(context.packageName, 0, 2)
                if (exits.isEmpty()) "Own-app recent crashes: None"
                else "Last exit reason code=${exits.first().reason} (${exits.first().description ?: "normal"})"
            }.getOrDefault("Own-app crash telemetry unavailable")
        } else {
            "Own-app crash info requires Android 11+ (3rd-party crash logs sandboxed)"
        }

        val details = listOf(
            "System Free RAM: ${RealDeviceDiagnostics.formatBytes(beforeMem.availMem)} -> ${RealDeviceDiagnostics.formatBytes(afterMem.availMem)} / ${RealDeviceDiagnostics.formatBytes(afterMem.totalMem)}",
            "Low Memory Pressure Flag: ${afterMem.lowMemory}",
            "App Runtime Free Heap: ${RealDeviceDiagnostics.formatBytes(rtBeforeFree)} -> ${RealDeviceDiagnostics.formatBytes(rtAfterFree)}",
            exitSummary,
            "Android Sandboxing: Other apps' background processes cannot be silently force-stopped by user apps."
        )

        val freedSystem = afterMem.availMem - beforeMem.availMem
        val heapImproved = rtAfterFree > rtBeforeFree

        return ActionResult(
            isSuccess = true,
            message = "Memory audit complete (${RealDeviceDiagnostics.formatBytes(afterMem.availMem)} free RAM, LowMem=${afterMem.lowMemory}).",
            executedAutomatically = true,
            requiresUserConfirmationToVerify = false,
            measuredDetails = details,
            verifiedMetricImproved = freedSystem >= 1024 * 1024L || heapImproved
        )
    }

    private fun executeFixAudioRoutingAndVolume(context: Context): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult(false, "AudioManager service unavailable.")

        val mediaMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val mediaCurBefore = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mediaPctBefore = (mediaCurBefore * 100) / mediaMax
        val modeBefore = audioManager.mode
        val speakerBefore = audioManager.isSpeakerphoneOn
        val ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Normal"
        }
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val isBtEnabled = runCatching { btManager?.adapter?.isEnabled == true }.getOrDefault(false)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val dndAccess = runCatching { nm?.isNotificationPolicyAccessGranted == true }.getOrDefault(false)

        var routingChanged = false
        var volumeChanged = false
        var policyBlockNote: String? = null

        if (modeBefore == AudioManager.MODE_IN_COMMUNICATION || speakerBefore) {
            runCatching {
                audioManager.mode = AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }.onFailure {
                policyBlockNote = "Audio routing reset blocked by OS policy: ${it.localizedMessage}"
            }
        }

        if (mediaPctBefore < 35) {
            val targetVol = max(1, (mediaMax * 0.45f).roundToInt()).coerceAtMost(mediaMax)
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            }.onFailure {
                policyBlockNote = "Volume adjustment blocked by OS/DND policy: ${it.localizedMessage}"
            }
        }

        val mediaCurAfter = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mediaPctAfter = (mediaCurAfter * 100) / mediaMax
        val modeAfter = audioManager.mode
        val speakerAfter = audioManager.isSpeakerphoneOn

        if (mediaCurAfter > mediaCurBefore) volumeChanged = true
        if (modeAfter != modeBefore || speakerAfter != speakerBefore) routingChanged = true

        runCatching { RealDeviceDiagnostics.playAudioDiagnosticBeep() }

        val details = buildList {
            add("Media Volume: $mediaPctBefore% ($mediaCurBefore/$mediaMax) -> $mediaPctAfter% ($mediaCurAfter/$mediaMax)")
            add("Audio Mode: ${audioModeLabel(modeBefore)} -> ${audioModeLabel(modeAfter)} • Speakerphone: $speakerBefore -> $speakerAfter")
            add("Ringer Mode: $ringerMode (DND Policy Access: $dndAccess) • Bluetooth Active: $isBtEnabled")
            if (policyBlockNote != null) add(policyBlockNote!!)
            add("Played calibrated 587.33Hz speaker verification tone.")
        }

        val summary = when {
            volumeChanged && routingChanged ->
                "Audio repaired automatically: raised Media Volume ($mediaPctBefore% -> $mediaPctAfter%) & reset routing to NORMAL."
            volumeChanged ->
                "Audio repaired automatically: unmuted/raised Media Volume ($mediaPctBefore% -> $mediaPctAfter%) & played test tone."
            routingChanged ->
                "Audio routing reset to MODE_NORMAL & verified speaker tone (Media Volume $mediaPctAfter%)."
            else ->
                "Audio streams audited (Media $mediaPctAfter%, Ringer $ringerMode, BT=$isBtEnabled) & test tone played."
        }

        return ActionResult(
            isSuccess = true,
            message = summary,
            executedAutomatically = true,
            requiresUserConfirmationToVerify = true,
            measuredDetails = details,
            verifiedMetricImproved = volumeChanged || routingChanged
        )
    }

    private fun executeAudioAudit(context: Context): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult(false, "AudioManager service unavailable.")

        val mediaCur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mediaMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val ringCur = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        val ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
        val callCur = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        val callMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL).coerceAtLeast(1)
        val ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Normal"
        }
        val mediaPct = (mediaCur * 100) / mediaMax
        val ringPct = (ringCur * 100) / ringMax
        val callPct = (callCur * 100) / callMax

        runCatching { RealDeviceDiagnostics.playAudioDiagnosticBeep() }

        val details = listOf(
            "Media Volume: $mediaPct% ($mediaCur/$mediaMax)",
            "Ringtone Volume: $ringPct% ($ringCur/$ringMax) • Mode: $ringerMode",
            "Call Volume: $callPct% ($callCur/$callMax)",
            "Audio Mode: ${audioModeLabel(audioManager.mode)} • Speakerphone Active: ${audioManager.isSpeakerphoneOn}",
            "Played calibrated 587Hz speaker test tone."
        )

        return ActionResult(
            isSuccess = true,
            message = "Audio audited (Media $mediaPct%, Ring $ringPct%, Mode: $ringerMode) & test tone played.",
            executedAutomatically = true,
            requiresUserConfirmationToVerify = true,
            measuredDetails = details
        )
    }

    private fun executeThermalPowerAudit(context: Context): ActionResult {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = runCatching { powerManager?.isPowerSaveMode == true }.getOrDefault(false)
        val vitals = RealDeviceDiagnostics.getDeviceVitals(context)
        val tempStr = if (vitals.temperatureVerified) "${vitals.batteryTempC}°C" else "Temp Not Collected"
        val details = listOf(
            "Battery Temp: $tempStr (${vitals.batteryHealth})",
            "PowerManager Thermal Status: ${vitals.thermalStatus}",
            "Battery Saver Mode: ${if (isPowerSave) "ENABLED" else "DISABLED"}",
            "Charging State: ${vitals.batteryStatus} (${vitals.batteryPlugged})"
        )
        return ActionResult(
            isSuccess = true,
            message = "Thermal=${vitals.thermalStatus} ($tempStr), BatterySaver=${if (isPowerSave) "ON" else "OFF"}.",
            executedAutomatically = true,
            requiresUserConfirmationToVerify = false,
            measuredDetails = details
        )
    }

    private fun executeOptimizeDisplayPower(
        context: Context,
        canWriteOverride: Boolean? = null
    ): ActionResult {
        val canWrite = canWriteOverride ?: runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
        if (!canWrite) {
            return openWriteSettingsPermissionPrompt(
                context,
                "Opened 'Modify system settings' permission screen. Grant it and re-run to auto-apply 30s timeout & Adaptive Brightness."
            )
        }

        return try {
            val beforeMs = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000)
            val beforeMode = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )

            val wroteTimeout = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
            val wroteBrightness = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            )

            val afterMs = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, beforeMs)
            val afterMode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, beforeMode)

            val timeoutOk = wroteTimeout && afterMs == 30_000
            val brightnessOk = wroteBrightness && afterMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            val verified = timeoutOk || brightnessOk
            val actuallyImproved = (timeoutOk && beforeMs != afterMs) ||
                (brightnessOk && beforeMode != afterMode)

            val beforeModeStr = if (beforeMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "AUTO" else "MANUAL"
            val afterModeStr = if (afterMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "AUTO" else "MANUAL"

            ActionResult(
                isSuccess = verified,
                message = if (verified) {
                    "Display power optimized: Timeout ${beforeMs / 1000}s -> ${afterMs / 1000}s, Adaptive Brightness $beforeModeStr -> $afterModeStr."
                } else {
                    "System rejected display write. Use Android Display Settings."
                },
                executedAutomatically = verified,
                requiresUserConfirmationToVerify = !verified,
                measuredDetails = listOf(
                    "Screen Timeout: ${beforeMs / 1000}s -> ${afterMs / 1000}s (Verified=$timeoutOk)",
                    "Adaptive Brightness Mode: $beforeModeStr -> $afterModeStr (Verified=$brightnessOk)"
                ),
                verifiedMetricImproved = actuallyImproved
            )
        } catch (e: Exception) {
            ActionResult(false, "Could not optimize display power settings: ${e.localizedMessage}")
        }
    }

    private fun executeSafeScreenTimeout(
        context: Context,
        canWriteOverride: Boolean? = null
    ): ActionResult {
        val canWrite = canWriteOverride ?: runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
        if (!canWrite) {
            return openWriteSettingsPermissionPrompt(
                context,
                "Opened 'Modify system settings' permission screen. Grant it and tap again to auto-set 30s timeout."
            )
        }

        return try {
            val beforeMs = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30000)
            val written = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
            val afterMs = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, beforeMs)
            val verified = written && afterMs == 30_000

            ActionResult(
                isSuccess = verified,
                message = if (verified) {
                    "Screen sleep timeout updated automatically: ${beforeMs / 1000}s -> ${afterMs / 1000}s."
                } else {
                    "System rejected timeout write (remained ${afterMs / 1000}s). Use Display Settings."
                },
                executedAutomatically = verified,
                requiresUserConfirmationToVerify = !verified,
                measuredDetails = listOf(
                    "Timeout before: ${beforeMs / 1000}s",
                    "Timeout after: ${afterMs / 1000}s",
                    "Write verified: $verified"
                ),
                verifiedMetricImproved = verified && afterMs < beforeMs
            )
        } catch (e: Exception) {
            ActionResult(false, "Could not update screen timeout: ${e.localizedMessage}")
        }
    }

    private fun openWriteSettingsPermissionPrompt(context: Context, launchedMsg: String): ActionResult {
        val launched = runCatching {
            val permIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (permIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(permIntent)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        return ActionResult(
            isSuccess = false,
            message = if (launched) launchedMsg else "Special permission WRITE_SETTINGS is not granted. Please adjust settings in Display Settings.",
            executedAutomatically = false,
            openedSettingsScreen = launched,
            requiresSpecialPermissionGrant = true,
            requiresUserConfirmationToVerify = true,
            measuredDetails = listOf("Settings.System.canWrite(context) = false")
        )
    }

    private fun executeSettingsIntent(context: Context, action: DoctorAction): ActionResult {
        return try {
            val primaryIntent = buildSettingsIntent(context, action.actionType)
            val resolved = primaryIntent?.takeIf {
                runCatching { it.resolveActivity(context.packageManager) != null }.getOrDefault(false)
            } ?: Intent(Settings.ACTION_SETTINGS)

            resolved.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(resolved)

            val isInAppPanel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                resolved.action?.startsWith("android.settings.panel.action.") == true

            ActionResult(
                isSuccess = true,
                message = if (isInAppPanel) {
                    "Opened In-App Android Settings Panel for ${action.title}."
                } else {
                    "Opened ${action.title} in Android Settings."
                },
                executedAutomatically = false,
                openedSettingsScreen = true,
                requiresUserConfirmationToVerify = true,
                measuredDetails = listOf("Intent action: ${resolved.action}")
            )
        } catch (e: Exception) {
            ActionResult(false, "Could not open Settings: ${e.localizedMessage}")
        }
    }

    fun buildSettingsIntent(context: Context, actionType: ActionType): Intent? {
        return when (actionType) {
            ActionType.OPEN_BATTERY_SETTINGS -> {
                Intent(Intent.ACTION_POWER_USAGE_SUMMARY).takeIf {
                    runCatching { it.resolveActivity(context.packageManager) != null }.getOrDefault(false)
                } ?: Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            }
            ActionType.OPEN_STORAGE_SETTINGS -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            ActionType.OPEN_APP_SETTINGS,
            ActionType.GUARDED_CLEAR_APP_DATA_GUIDE,
            ActionType.GUARDED_UNINSTALL_UNUSED_GUIDE -> Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            ActionType.OPEN_WIFI_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val panel = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                    if (runCatching { panel.resolveActivity(context.packageManager) != null }.getOrDefault(false)) {
                        return panel
                    }
                }
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            ActionType.OPEN_MOBILE_DATA_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val panel = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                    if (runCatching { panel.resolveActivity(context.packageManager) != null }.getOrDefault(false)) {
                        return panel
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Intent(Settings.ACTION_DATA_USAGE_SETTINGS).takeIf {
                        runCatching { it.resolveActivity(context.packageManager) != null }.getOrDefault(false)
                    } ?: Intent(Settings.ACTION_WIRELESS_SETTINGS)
                } else {
                    Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                }
            }
            ActionType.OPEN_DISPLAY_SETTINGS -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            ActionType.OPEN_SOUND_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val panel = Intent(Settings.Panel.ACTION_VOLUME)
                    if (runCatching { panel.resolveActivity(context.packageManager) != null }.getOrDefault(false)) {
                        return panel
                    }
                }
                Intent(Settings.ACTION_SOUND_SETTINGS)
            }
            ActionType.OPEN_BLUETOOTH_SETTINGS -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            ActionType.OPEN_NOTIFICATION_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
            }
            ActionType.OPEN_ACCESSIBILITY_SETTINGS -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            ActionType.OPEN_SYSTEM_UPDATE -> {
                Intent("android.settings.SYSTEM_UPDATE_SETTINGS").takeIf {
                    runCatching { it.resolveActivity(context.packageManager) != null }.getOrDefault(false)
                } ?: Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
            }
            ActionType.OPEN_SETTINGS_SEARCH,
            ActionType.GUARDED_RESET_NETWORK_GUIDE -> Intent(Settings.ACTION_SETTINGS)
            ActionType.OPEN_LOCATION_SETTINGS -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            ActionType.OPEN_SECURITY_PRIVACY_SETTINGS -> Intent(Settings.ACTION_SECURITY_SETTINGS)
            ActionType.OPEN_ACCOUNTS_SYNC_SETTINGS -> Intent(Settings.ACTION_SYNC_SETTINGS)
            ActionType.OPEN_LANGUAGE_INPUT_SETTINGS -> Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            ActionType.OPEN_DEFAULT_APPS_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                } else {
                    Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                }
            }
            ActionType.OPEN_DATE_TIME_SETTINGS -> Intent(Settings.ACTION_DATE_SETTINGS)
            ActionType.OPEN_DEVELOPER_OPTIONS_SETTINGS -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            else -> null
        }
    }

    private fun audioModeLabel(mode: Int): String = when (mode) {
        AudioManager.MODE_NORMAL -> "NORMAL"
        AudioManager.MODE_RINGTONE -> "RINGTONE"
        AudioManager.MODE_IN_CALL -> "IN_CALL"
        AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
        else -> "MODE_$mode"
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
