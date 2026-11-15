package com.example.data.diagnostics

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import com.example.data.model.CollectorCapability
import com.example.data.model.CollectorPrivacyClass
import com.example.data.model.DeviceVitals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt
import kotlin.math.sin

object RealDeviceDiagnostics {

    fun getDeviceVitals(context: Context): DeviceVitals {
        val collectors = mutableListOf<CollectorCapability>()
        val unverifiedMetrics = mutableListOf<String>()
        val nowMs = System.currentTimeMillis()

        // 1. Battery Info (Real from BatteryManager broadcast sticky intent — never invent %)
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryVerified = batteryIntent != null && rawLevel >= 0 && scale > 0
        val batteryPct = if (batteryVerified) ((rawLevel * 100f) / scale).roundToInt().coerceIn(0, 100) else -1
        if (!batteryVerified) unverifiedMetrics.add("Battery Level (%)")

        val statusInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val batteryStatus = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unavailable"
        }

        val pluggedInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugged = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            0 -> "Battery Power"
            else -> "Unavailable"
        }

        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temperatureVerified = batteryIntent != null && tempRaw > 0
        val tempC = if (temperatureVerified) tempRaw / 10f else -1f
        if (!temperatureVerified) unverifiedMetrics.add("Battery Temperature (°C)")

        val healthInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unavailable"
        }

        collectors.add(
            CollectorCapability(
                collectorId = "col_battery",
                name = "Battery & Thermal Sensor",
                dataSource = "BatteryManager (ACTION_BATTERY_CHANGED)",
                requiredPermission = "None (Public sticky broadcast)",
                minSdk = 24,
                oemCompatibility = "Universal Android",
                privacyClass = CollectorPrivacyClass.PUBLIC_HARDWARE_INFO,
                isAvailable = batteryVerified,
                currentValueSummary = buildString {
                    append(if (batteryVerified) "$batteryPct%" else "Level Unavailable")
                    append(" • ")
                    append(if (temperatureVerified) "${tempC}°C" else "Temp Not Collected")
                    append(" • $batteryStatus ($health)")
                },
                errorNote = if (!batteryVerified || !temperatureVerified) {
                    "Battery sticky broadcast or thermistor did not return valid values on this build."
                } else null
            )
        )

        // 2. Storage Info (Real from StatFs)
        val dataDir = Environment.getDataDirectory()
        val stat = runCatching { StatFs(dataDir.path) }.getOrNull()
        val blockSize = stat?.blockSizeLong ?: 0L
        val totalBlocks = stat?.blockCountLong ?: 0L
        val availableBlocks = stat?.availableBlocksLong ?: 0L

        val storageTotalBytes = totalBlocks * blockSize
        val storageFreeBytes = availableBlocks * blockSize
        val storageUsedBytes = (storageTotalBytes - storageFreeBytes).coerceAtLeast(0L)
        val storageVerified = storageTotalBytes > 0L
        val storageUsedPct = if (storageVerified) {
            ((storageUsedBytes.toDouble() / storageTotalBytes) * 100).toInt().coerceIn(0, 100)
        } else {
            unverifiedMetrics.add("Internal Storage Partition")
            -1
        }

        collectors.add(
            CollectorCapability(
                collectorId = "col_storage",
                name = "Internal Partition Storage",
                dataSource = "android.os.StatFs (/data)",
                requiredPermission = "None (Partition stats only)",
                minSdk = 24,
                oemCompatibility = "Universal Android",
                privacyClass = CollectorPrivacyClass.SYSTEM_RESOURCE_METRICS,
                isAvailable = storageVerified,
                currentValueSummary = if (storageVerified) {
                    "${formatBytes(storageFreeBytes)} free / ${formatBytes(storageTotalBytes)} ($storageUsedPct% used)"
                } else {
                    "Not Collected (StatFs unavailable)"
                },
                errorNote = if (!storageVerified) "StatFs returned 0 total blocks." else null
            )
        )

        // 3. RAM Info (Real from ActivityManager)
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        val ramReadOk = runCatching { actManager?.getMemoryInfo(memInfo) }.isSuccess && actManager != null
        val ramTotalBytes = if (ramReadOk) memInfo.totalMem else 0L
        val ramFreeBytes = if (ramReadOk) memInfo.availMem else 0L
        val ramUsedBytes = (ramTotalBytes - ramFreeBytes).coerceAtLeast(0L)
        val ramVerified = ramTotalBytes > 0L
        val ramUsedPct = if (ramVerified) {
            ((ramUsedBytes.toDouble() / ramTotalBytes) * 100).toInt().coerceIn(0, 100)
        } else {
            unverifiedMetrics.add("System RAM Telemetry")
            -1
        }
        val isLowMem = if (ramVerified) memInfo.lowMemory else false

        collectors.add(
            CollectorCapability(
                collectorId = "col_ram",
                name = "System RAM & Memory Pressure",
                dataSource = "ActivityManager.getMemoryInfo()",
                requiredPermission = "None",
                minSdk = 24,
                oemCompatibility = "Universal Android",
                privacyClass = CollectorPrivacyClass.SYSTEM_RESOURCE_METRICS,
                isAvailable = ramVerified,
                currentValueSummary = if (ramVerified) {
                    "${formatBytes(ramFreeBytes)} free / ${formatBytes(ramTotalBytes)} ($ramUsedPct% used, LowMem=$isLowMem)"
                } else {
                    "Not Collected (ActivityManager MemoryInfo unavailable)"
                },
                errorNote = if (!ramVerified) "ActivityManager.getMemoryInfo returned 0 totalMem." else null
            )
        )

        // 4. Network & Wi-Fi Info (Real from ConnectivityManager & WifiManager)
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkVerified = connManager != null
        if (!networkVerified) unverifiedMetrics.add("Network Connectivity")
        val activeNetwork = connManager?.activeNetwork
        val caps = connManager?.getNetworkCapabilities(activeNetwork)

        val isInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val netType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            networkVerified -> "Disconnected"
            else -> "Unavailable"
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)

        collectors.add(
            CollectorCapability(
                collectorId = "col_network",
                name = "Network & Wi-Fi Link State",
                dataSource = "ConnectivityManager & WifiManager",
                requiredPermission = "ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE",
                minSdk = 24,
                oemCompatibility = "Universal Android",
                privacyClass = CollectorPrivacyClass.NETWORK_LINK_STATE,
                isAvailable = networkVerified,
                currentValueSummary = if (networkVerified) {
                    "$netType (${if (isInternet) "Validated Online" else "Offline"}) • Wi-Fi Radio=${if (isWifiEnabled) "ON" else "OFF"}"
                } else {
                    "Unavailable"
                }
            )
        )

        // 5. PowerManager Thermal & Power Saver
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = runCatching { powerManager?.isPowerSaveMode == true }.getOrDefault(false)
        var thermalVerified = false
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val code = runCatching { powerManager.currentThermalStatus }.getOrNull()
            if (code != null) {
                thermalVerified = true
                when (code) {
                    PowerManager.THERMAL_STATUS_NONE -> "Nominal"
                    PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
                    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttling"
                    PowerManager.THERMAL_STATUS_SEVERE -> "Severe Thermal Throttling"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Overheat"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency Shutdown Risk"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Thermal Shutdown"
                    else -> "Unknown ($code)"
                }
            } else {
                "Unavailable"
            }
        } else if (temperatureVerified) {
            thermalVerified = true
            if (tempC > 41f) "Elevated (${tempC}°C)" else "Nominal"
        } else {
            "Unavailable"
        }
        if (!thermalVerified) unverifiedMetrics.add("Thermal Status")

        collectors.add(
            CollectorCapability(
                collectorId = "col_thermal_power",
                name = "Power Saver & Thermal Throttling",
                dataSource = "PowerManager (isPowerSaveMode, currentThermalStatus)",
                requiredPermission = "None",
                minSdk = 24,
                oemCompatibility = "API 29+ for ThermalStatus; verified battery thermistor below API 29",
                privacyClass = CollectorPrivacyClass.PUBLIC_HARDWARE_INFO,
                isAvailable = powerManager != null && thermalVerified,
                currentValueSummary = "Thermal=$thermalStatus • PowerSaver=${if (isPowerSave) "ON" else "OFF"}",
                errorNote = if (!thermalVerified) "Thermal HAL/PowerManager status not reported on this device." else null
            )
        )

        // 6. Audio & Bluetooth State
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringerMode = when (audioManager?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            AudioManager.RINGER_MODE_NORMAL -> "Normal"
            else -> "Unavailable"
        }
        val maxMedia = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
        val currMedia = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
        val audioVerified = audioManager != null && maxMedia > 0 && currMedia >= 0
        val mediaVolPct = if (audioVerified) ((currMedia * 100f) / maxMedia).roundToInt() else -1
        if (!audioVerified) unverifiedMetrics.add("Audio Stream Volume")

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val isBtEnabled = runCatching { btManager?.adapter?.isEnabled == true }.getOrDefault(false)

        collectors.add(
            CollectorCapability(
                collectorId = "col_audio",
                name = "Audio Output & Volume Profile",
                dataSource = "AudioManager",
                requiredPermission = "None",
                minSdk = 24,
                oemCompatibility = "Universal Android",
                privacyClass = CollectorPrivacyClass.AUDIO_SYSTEM_STATE,
                isAvailable = audioVerified,
                currentValueSummary = if (audioVerified) {
                    "Media Vol=$mediaVolPct% • Ringer=$ringerMode • Bluetooth=${if (isBtEnabled) "ON" else "OFF"}"
                } else {
                    "Audio Volume Not Collected"
                }
            )
        )

        // 7. Display info & Write Settings Permission Status
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { context.display }.getOrNull() ?: windowManager?.defaultDisplay
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay
            }
        } catch (_: Exception) {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay
        }

        val refreshRateRaw = runCatching { display?.mode?.refreshRate }.getOrNull()
        val refreshRate = if (refreshRateRaw != null && refreshRateRaw > 0f) refreshRateRaw else -1f
        val metrics = DisplayMetrics()
        val resolution = try {
            @Suppress("DEPRECATION")
            display?.getRealMetrics(metrics)
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                "${metrics.widthPixels} x ${metrics.heightPixels}"
            } else {
                "Unavailable"
            }
        } catch (_: Exception) {
            "Unavailable"
        }
        val displayVerified = resolution != "Unavailable" && refreshRate > 0f
        if (!displayVerified) unverifiedMetrics.add("Display Resolution & Refresh Rate")

        val timeoutMs = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        }.getOrNull()
        val timeoutSec = if (timeoutMs != null && timeoutMs >= 0) timeoutMs / 1000 else -1
        if (timeoutSec < 0) unverifiedMetrics.add("Screen Timeout Setting")

        val canWriteSettings = runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
        val notificationsEnabled = runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(true)

        collectors.add(
            CollectorCapability(
                collectorId = "col_permissions",
                name = "System Permissions & Display Config",
                dataSource = "Settings.System & DisplayMetrics",
                requiredPermission = "None for read; WRITE_SETTINGS special perm for write",
                minSdk = 24,
                oemCompatibility = "Universal Android",
                privacyClass = CollectorPrivacyClass.PERMISSION_STATUS,
                isAvailable = true,
                currentValueSummary = "Sleep=${if (timeoutSec >= 0) "${timeoutSec}s" else "Unavailable"} • WriteSettings=$canWriteSettings • Notifs=$notificationsEnabled"
            )
        )

        val uptimeHours = SystemClock.elapsedRealtime() / (1000f * 60f * 60f)
        val cpuArch = Build.SUPPORTED_ABIS.firstOrNull() ?: System.getProperty("os.arch") ?: "Unknown"

        // 8. Own-App Process Exit / Crash Telemetry (Android 11+ / API 30+, 0 extra permissions;
        //    third-party app crash logs remain isolated by Android OS sandbox / READ_LOGS restriction)
        val ownAppExitSummary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && actManager != null) {
            runCatching {
                val exits = actManager.getHistoricalProcessExitReasons(context.packageName, 0, 3)
                if (exits.isEmpty()) {
                    "No recent unexpected exits recorded for this app (3rd-party crash logs isolated by OS)"
                } else {
                    val latest = exits.first()
                    "Last exit code=${latest.reason} (${latest.description ?: "normal"}); 3rd-party crash logs sandboxed"
                }
            }.getOrElse { "App exit check unavailable; 3rd-party crash logs sandboxed by Android" }
        } else {
            "API < 30 (ApplicationExitInfo requires Android 11+); 3rd-party crash logs restricted by OS"
        }

        collectors.add(
            CollectorCapability(
                collectorId = "col_app_exit",
                name = "App Crash / Process Exit Info",
                dataSource = "ActivityManager.getHistoricalProcessExitReasons",
                requiredPermission = "None for own app (API 30+); other apps' crash logs OS-sandboxed",
                minSdk = 30,
                oemCompatibility = "Android 11+ (API 30+) for own package only",
                privacyClass = CollectorPrivacyClass.SYSTEM_RESOURCE_METRICS,
                isAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && actManager != null,
                currentValueSummary = ownAppExitSummary
            )
        )

        // Overall Health Score Calculation — derived STRICTLY from verified readings
        var score = 100
        if (storageVerified) {
            if (storageUsedPct > 90) score -= 25
            else if (storageUsedPct > 80) score -= 15
        }
        if (ramVerified) {
            if (ramUsedPct > 85 || isLowMem) score -= 20
            else if (ramUsedPct > 75) score -= 10
        }
        if (temperatureVerified) {
            if (tempC > 42f) score -= 30
            else if (tempC > 38f) score -= 15
        }
        if (batteryVerified && health == "Overheating") score -= 20
        if (networkVerified && !isInternet) score -= 10
        if (batteryVerified && batteryPct in 0..14 && batteryStatus != "Charging") score -= 10

        val healthScore = score.coerceIn(10, 100)

        return DeviceVitals(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            brand = Build.BRAND.replaceFirstChar { it.uppercase() },
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            batteryLevel = batteryPct,
            batteryStatus = batteryStatus,
            batteryPlugged = plugged,
            batteryTempC = tempC,
            batteryHealth = health,
            storageTotalBytes = storageTotalBytes,
            storageFreeBytes = storageFreeBytes,
            storageUsedPercent = storageUsedPct,
            ramTotalBytes = ramTotalBytes,
            ramFreeBytes = ramFreeBytes,
            ramUsedPercent = ramUsedPct,
            isLowMemory = isLowMem,
            networkType = netType,
            isInternetAvailable = isInternet,
            displayResolution = resolution,
            refreshRate = refreshRate,
            screenTimeoutSeconds = timeoutSec,
            uptimeHours = uptimeHours,
            healthScore = healthScore,
            cpuArchitecture = cpuArch,
            thermalStatus = thermalStatus,
            isPowerSaveMode = isPowerSave,
            isWifiEnabled = isWifiEnabled,
            isBluetoothEnabled = isBtEnabled,
            ringerMode = ringerMode,
            mediaVolumePercent = mediaVolPct,
            notificationsEnabled = notificationsEnabled,
            canWriteSystemSettings = canWriteSettings,
            ownAppExitSummary = ownAppExitSummary,
            activeCollectorCapabilities = collectors,
            batteryVerified = batteryVerified,
            temperatureVerified = temperatureVerified,
            thermalVerified = thermalVerified,
            ramVerified = ramVerified,
            storageVerified = storageVerified,
            networkVerified = networkVerified,
            displayVerified = displayVerified,
            audioVerified = audioVerified,
            collectionTimestampMs = nowMs,
            unverifiedMetrics = unverifiedMetrics
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Unavailable"
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        return if (gb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f GB", gb)
        } else {
            val mb = bytes.toDouble() / (1024 * 1024)
            if (mb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f MB", mb)
            } else {
                val kb = bytes.toDouble() / 1024
                String.format(java.util.Locale.US, "%.0f KB", kb)
            }
        }
    }

    fun formatVitalsSummary(v: DeviceVitals, language: String = "English"): String {
        val batText = if (v.batteryVerified) "${v.batteryLevel}% (${v.batteryStatus})" else "Not Collected"
        val tempText = if (v.temperatureVerified) "${v.batteryTempC}°C" else "Not Collected"
        val storageText = if (v.storageVerified) {
            "${formatBytes(v.storageFreeBytes)} free of ${formatBytes(v.storageTotalBytes)} (${v.storageUsedPercent}% used)"
        } else "Not Collected"
        val ramText = if (v.ramVerified) {
            "${formatBytes(v.ramFreeBytes)} free of ${formatBytes(v.ramTotalBytes)} (${v.ramUsedPercent}% used, LowMem: ${v.isLowMemory})"
        } else "Not Collected"
        val displayText = if (v.displayVerified) {
            "${v.displayResolution} @ ${v.refreshRate.toInt()}Hz"
        } else "Not Collected"
        val timeoutText = if (v.screenTimeoutSeconds >= 0) "${v.screenTimeoutSeconds}s" else "Not Collected"
        val audioText = if (v.audioVerified) "${v.mediaVolumePercent}% (${v.ringerMode})" else "Not Collected"

        return buildString {
            append("• [VERIFIED: Build] Device: ${v.brand} ${v.model} (Android ${v.androidVersion}, API ${v.apiLevel}, Arch: ${v.cpuArchitecture})\n")
            append("• [${if (v.batteryVerified && v.temperatureVerified) "VERIFIED: BatteryManager/PowerManager" else "PARTIAL TELEMETRY"}] Battery: $batText • Temp: $tempText • Health: ${v.batteryHealth} • Thermal: ${v.thermalStatus} • PowerSaver: ${v.isPowerSaveMode}\n")
            append("• [${if (v.storageVerified) "VERIFIED: StatFs" else "NOT COLLECTED"}] Storage: $storageText\n")
            append("• [${if (v.ramVerified) "VERIFIED: ActivityManager" else "NOT COLLECTED"}] RAM: $ramText\n")
            append("• [${if (v.networkVerified) "VERIFIED: ConnectivityManager" else "NOT COLLECTED"}] Network: ${v.networkType} (Online: ${v.isInternetAvailable}, Wi-Fi Radio: ${v.isWifiEnabled}, Bluetooth: ${v.isBluetoothEnabled})\n")
            append("• [VERIFIED: Display/Audio/Settings] Display: $displayText (Sleep: $timeoutText, WriteSettings: ${v.canWriteSystemSettings}) • MediaVol: $audioText\n")
            append("• Crash/Exit Telemetry: ${v.ownAppExitSummary}\n")
            append("• Uptime: ${String.format(java.util.Locale.US, "%.1f", v.uptimeHours)} hours • Diagnostic Health Score (verified collectors): ${v.healthScore}/100")
            if (v.unverifiedMetrics.isNotEmpty()) {
                append("\n• [NOT COLLECTED / UNAVAILABLE]: ${v.unverifiedMetrics.joinToString(", ")}")
            }
            if (language == "Hindi") {
                append("\n• नोट: केवल '[VERIFIED]' चिह्नित मान वास्तविक डिवाइस सेंसर से पढ़े गए हैं; अनुपलब्ध मानों का अनुमान न लगाएं।")
            }
        }
    }

    fun triggerVibrationTest(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let {
            if (it.hasVibrator()) {
                val timings = longArrayOf(0, 200, 100, 200, 100, 400)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                it.vibrate(effect)
            }
        }
    }

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    fun playAudioDiagnosticBeep() {
        runCatching {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        }
        activeAudioTrack = null

        val sampleRate = 44100
        val durationSeconds = 1.2
        val numSamples = (durationSeconds * sampleRate).toInt()
        val sample = DoubleArray(numSamples)
        val generatedSnd = ByteArray(2 * numSamples)

        val freqOfTone = 587.33
        for (i in 0 until numSamples) {
            sample[i] = sin(2 * Math.PI * i / (sampleRate / freqOfTone))
        }

        var idx = 0
        for (dVal in sample) {
            val s = (dVal * 32767).toInt().toShort()
            generatedSnd[idx++] = (s.toInt() and 0x00ff).toByte()
            generatedSnd[idx++] = ((s.toInt() and 0xff00) ushr 8).toByte()
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(generatedSnd.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        activeAudioTrack = audioTrack
        audioTrack.write(generatedSnd, 0, generatedSnd.size)
        audioTrack.setNotificationMarkerPosition(numSamples)
        audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                runCatching {
                    track?.stop()
                    track?.release()
                }
                if (activeAudioTrack === track) {
                    activeAudioTrack = null
                }
            }
            override fun onPeriodicNotification(track: AudioTrack?) = Unit
        })
        audioTrack.play()
    }

    suspend fun runNetworkPingTest(host: String = "8.8.8.8", port: Int = 53): PingResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
                val latency = System.currentTimeMillis() - startTime
                PingResult(isSuccess = true, latencyMs = latency, host = host)
            }
        } catch (e: Exception) {
            PingResult(isSuccess = false, latencyMs = -1, host = host, error = e.localizedMessage)
        }
    }
}

data class PingResult(
    val isSuccess: Boolean,
    val latencyMs: Long,
    val host: String,
    val error: String? = null
)
