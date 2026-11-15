package com.example.data.repair

import com.example.data.model.ActionType
import com.example.data.model.AutomationCapability
import com.example.data.model.DoctorAction
import com.example.data.model.RepairLevel
import com.example.data.model.RiskClassification

/**
 * Zero-Trust Approved Repair Registry.
 * Every executable or guided repair action must be registered and reviewed here.
 * The AI Brain can only request action IDs from this registry—never arbitrary shell commands.
 */
object SafeRepairRegistry {

    val approvedActions: List<DoctorAction> = listOf(
        // === LEVEL 2: REAL IN-APP ANDROID API ACTIONS ===
        DoctorAction(
            id = "auto_clear_own_cache",
            title = "🧹 Clear Diagnostic App Cache",
            description = "Purges this app's own temporary cache & codeCache directories and reports exact bytes freed",
            actionType = ActionType.CLEAR_APP_OWN_CACHE,
            level = RepairLevel.LEVEL_2,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Removes temporary buffers inside AI Smartphone Doctor without touching user data or other apps",
            whatWillChange = "Deletes files inside context.cacheDir and context.codeCacheDir",
            capability = AutomationCapability.NORMAL_API,
            riskClass = RiskClassification.SAFE_APP_LEVEL,
            requiredPermission = "None (Own app sandbox)",
            verificationMethod = "Measures exact bytes deleted and recalculates directory size",
            fallbackManualSteps = listOf(
                "To clear cache of heavy third-party apps (WhatsApp, YouTube, Instagram), open System App Settings."
            )
        ),
        DoctorAction(
            id = "auto_refresh_network_socket",
            title = "🌐 Refresh DNS & Socket Handshake",
            description = "Inspects ConnectivityManager link/DNS capabilities and tests TCP round-trip latency",
            actionType = ActionType.REFRESH_NETWORK_SOCKET,
            level = RepairLevel.LEVEL_2,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Verifies DNS resolution and socket routing latency to 8.8.8.8 & 1.1.1.1",
            whatWillChange = "Executes controlled DNS/TCP socket handshake check",
            capability = AutomationCapability.NORMAL_API,
            riskClass = RiskClassification.SAFE_APP_LEVEL,
            requiredPermission = "INTERNET, ACCESS_NETWORK_STATE",
            verificationMethod = "Compares live online validation and RTT ping latency in ms"
        ),
        DoctorAction(
            id = "auto_optimize_memory",
            title = "🧠 Inspect RAM & Release App Buffers",
            description = "Measures real system MemoryInfo, releases dormant in-app buffers, and reports true free RAM",
            actionType = ActionType.OPTIMIZE_APP_MEMORY_AND_STATE,
            level = RepairLevel.LEVEL_2,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Checks true system memory pressure; Android sandboxing forbids killing other apps silently",
            whatWillChange = "Releases internal app heap references and snapshots ActivityManager.MemoryInfo",
            capability = AutomationCapability.NORMAL_API,
            riskClass = RiskClassification.SAFE_APP_LEVEL,
            requiredPermission = "None",
            verificationMethod = "Compares before/after available system RAM and lowMemory flag"
        ),
        DoctorAction(
            id = "auto_fix_audio_output",
            title = "🔊 Fix Audio Routing & Unmute Media",
            description = "Safely resets stuck communication routing to MODE_NORMAL, raises muted media volume to audible level & plays verification tone",
            actionType = ActionType.FIX_AUDIO_ROUTING_AND_VOLUME,
            level = RepairLevel.LEVEL_2,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Low/muted media volume or stuck IN_COMMUNICATION routing is a common software cause of 'no sound'",
            whatWillChange = "Resets AudioManager.mode to MODE_NORMAL, restores muted STREAM_MUSIC volume safely, and plays 587Hz test tone",
            capability = AutomationCapability.NORMAL_API,
            riskClass = RiskClassification.SAFE_APP_LEVEL,
            requiredPermission = "None (MODIFY_AUDIO_SETTINGS not required for app/media volume)",
            verificationMethod = "Compares before/after media volume %, audio mode, and speaker audio tone output",
            fallbackManualSteps = listOf(
                "If Do Not Disturb or Bluetooth headphones are active, adjust sliders in the In-App Volume Panel."
            )
        ),
        DoctorAction(
            id = "auto_audit_audio",
            title = "🔊 Audit Audio Streams & Speaker",
            description = "Inspects AudioManager volume sliders, ringer mode, Bluetooth routing & plays test tone",
            actionType = ActionType.AUDIT_AUDIO_ROUTING_AND_VOLUME,
            level = RepairLevel.LEVEL_1,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Detects muted media stream, Do Not Disturb, or active Bluetooth routing before hardware blame",
            whatWillChange = "Reads AudioManager state and plays 587Hz diagnostic tone",
            capability = AutomationCapability.NORMAL_API,
            riskClass = RiskClassification.READ_ONLY,
            requiredPermission = "None",
            verificationMethod = "Reports exact stream volume % and asks user to confirm speaker audio clarity"
        ),
        DoctorAction(
            id = "auto_audit_thermal_power",
            title = "🌡️ Audit Thermal & Power Governor",
            description = "Reads PowerManager thermal throttling level, Battery Saver state & battery temperature",
            actionType = ActionType.AUDIT_THERMAL_AND_POWER_STATE,
            level = RepairLevel.LEVEL_1,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Confirms whether thermal throttling or Battery Saver is limiting CPU/FPS",
            whatWillChange = "Reads PowerManager and BatteryManager telemetry (read-only)",
            capability = AutomationCapability.NORMAL_API,
            riskClass = RiskClassification.READ_ONLY,
            requiredPermission = "None",
            verificationMethod = "Compares battery temperature °C and thermal status"
        ),
        DoctorAction(
            id = "auto_optimize_display_power",
            title = "🔆 Auto-Optimize Screen Timeout (30s) & Adaptive Brightness",
            description = "Automatically applies 30s sleep timeout + Adaptive Brightness when WRITE_SETTINGS permission is granted",
            actionType = ActionType.OPTIMIZE_DISPLAY_POWER_SAFE,
            level = RepairLevel.LEVEL_2,
            riskLevel = "Low",
            isReversible = true,
            whyRequired = "Excessive screen-on timeout and fixed high brightness are leading causes of battery drain and display heating",
            whatWillChange = "Sets SCREEN_OFF_TIMEOUT = 30,000 ms and SCREEN_BRIGHTNESS_MODE = AUTOMATIC, then verifies via read-back",
            capability = AutomationCapability.SPECIAL_PERMISSION,
            riskClass = RiskClassification.SPECIAL_PERMISSION_WRITE,
            requiredPermission = "android.permission.WRITE_SETTINGS (Special Permission)",
            verificationMethod = "Re-reads Settings.System timeout and brightness mode from ContentResolver to verify persistence",
            fallbackManualSteps = listOf(
                "Grant 'Modify system settings' permission when prompted, OR",
                "Open Display Settings to enable Adaptive Brightness and 30s timeout manually."
            )
        ),
        DoctorAction(
            id = "auto_screen_timeout_safe",
            title = "⏱️ Optimize Screen Timeout to 30s",
            description = "Sets screen sleep timeout to 30s if Modify System Settings permission is granted",
            actionType = ActionType.OPTIMIZE_SCREEN_TIMEOUT_SAFE,
            level = RepairLevel.LEVEL_2,
            riskLevel = "Low",
            isReversible = true,
            whyRequired = "Long screen timeout drains battery and increases OLED/LCD heat",
            whatWillChange = "Updates SCREEN_OFF_TIMEOUT to 30,000 ms (requires WRITE_SETTINGS consent)",
            capability = AutomationCapability.SPECIAL_PERMISSION,
            riskClass = RiskClassification.SPECIAL_PERMISSION_WRITE,
            requiredPermission = "android.permission.WRITE_SETTINGS (Special Permission)",
            verificationMethod = "Reads Settings.System.SCREEN_OFF_TIMEOUT back to confirm 30s applied",
            fallbackManualSteps = listOf(
                "Grant 'Modify system settings' permission when prompted, OR",
                "Open Display Settings and manually set Screen timeout to 30 seconds."
            )
        ),

        // === LEVEL 1: HARDWARE DIAGNOSTIC TESTS ===
        DoctorAction(
            id = "test_audio",
            title = "🔊 Run Speaker Audio Test",
            description = "Plays calibrated 587.33 Hz diagnostic frequency sound",
            actionType = ActionType.RUN_AUDIO_TEST,
            level = RepairLevel.LEVEL_1,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Verifies physical speaker membrane response",
            whatWillChange = "Plays short pure tone via AudioTrack",
            capability = AutomationCapability.NORMAL_API,
            riskClass = RiskClassification.READ_ONLY,
            verificationMethod = "User confirms clear & undistorted sound"
        ),
        DoctorAction(
            id = "test_vibration",
            title = "📳 Run Vibration Motor Test",
            description = "Triggers multi-pulse haptic actuator waveform",
            actionType = ActionType.RUN_VIBRATION_TEST,
            level = RepairLevel.LEVEL_1,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Verifies physical haptic vibration hardware",
            whatWillChange = "Activates vibration motor briefly",
            capability = AutomationCapability.NORMAL_API,
            riskClass = RiskClassification.READ_ONLY,
            requiredPermission = "VIBRATE",
            verificationMethod = "User confirms haptic pulses felt"
        ),
        DoctorAction(
            id = "test_screen",
            title = "🎨 Run Screen Pixel & Touch Test",
            description = "Full-screen RGB matrix inspection for dead pixels, tint & burn-in",
            actionType = ActionType.RUN_SCREEN_TEST,
            level = RepairLevel.LEVEL_1,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Reveals stuck/dead sub-pixels and display uniformity defects",
            whatWillChange = "Displays full-screen solid RGB/White/Black test screens",
            capability = AutomationCapability.NORMAL_API,
            riskClass = RiskClassification.READ_ONLY,
            verificationMethod = "User inspects screen colors and confirms touch tap advancement"
        ),
        DoctorAction(
            id = "test_network",
            title = "🌐 Run Network Ping Latency Test",
            description = "Measures real TCP/DNS round-trip latency to 8.8.8.8:53",
            actionType = ActionType.RUN_NETWORK_TEST,
            level = RepairLevel.LEVEL_1,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Measures actual internet packet response time",
            whatWillChange = "Performs socket handshake to 8.8.8.8:53",
            capability = AutomationCapability.NORMAL_API,
            riskClass = RiskClassification.READ_ONLY,
            requiredPermission = "INTERNET",
            verificationMethod = "Displays measured RTT latency in ms"
        ),

        // === LEVEL 3: USER-APPROVED ANDROID SYSTEM SETTINGS INTENTS ===
        DoctorAction(
            id = "repair_battery_saver",
            title = "⚡ Open Battery & Power Settings",
            description = "Inspect battery usage and toggle Battery Saver / Background Limits",
            actionType = ActionType.OPEN_BATTERY_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Android power policy prevents apps from toggling system Battery Saver silently",
            whatWillChange = "Opens native Battery Usage / Battery Saver settings",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_storage_cleaner",
            title = "💾 Open Storage Manager",
            description = "Inspect storage breakdown, Downloads, and large unused media",
            actionType = ActionType.OPEN_STORAGE_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = false,
            whyRequired = "Low free storage (<15%) causes severe lag, hang, and update failures",
            whatWillChange = "Opens native Android Storage Manager",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT,
            verificationMethod = "Re-check StatFs free storage percentage after returning"
        ),
        DoctorAction(
            id = "repair_app_cache",
            title = "📱 Open App Manager (Clear App Cache)",
            description = "Force stop freezing apps or clear heavy app cache safely",
            actionType = ActionType.OPEN_APP_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Android sandboxing forbids third-party apps from clearing other apps' caches silently",
            whatWillChange = "Opens Android Application Manager (Clear Cache only — keeps chats & logins)",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_network_reset",
            title = "📶 Open Wi-Fi & Network Settings",
            description = "Reconnect Wi-Fi, configure Private DNS, or inspect saved networks",
            actionType = ActionType.OPEN_WIFI_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Android 10+ restricts apps from programmatically toggling Wi-Fi state",
            whatWillChange = "Opens system Wi-Fi preferences",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT,
            verificationMethod = "Re-run connectivity and RTT ping test after returning"
        ),
        DoctorAction(
            id = "repair_mobile_data",
            title = "📡 Open Mobile Data & SIM Settings",
            description = "Check preferred network mode (5G/4G), APN, Data Saver & roaming",
            actionType = ActionType.OPEN_MOBILE_DATA_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Carrier & SIM network settings are protected system privileges",
            whatWillChange = "Opens Mobile Data / Network Operator settings",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT,
            verificationMethod = "Re-check network transport and online status"
        ),
        DoctorAction(
            id = "repair_bluetooth",
            title = "🎧 Open Bluetooth Settings",
            description = "Unpair stale Bluetooth audio/device bonds and re-pair",
            actionType = ActionType.OPEN_BLUETOOTH_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Resets corrupted Bluetooth pairing profiles",
            whatWillChange = "Opens paired Bluetooth devices screen",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_display_timeout",
            title = "☀️ Open Display & Refresh Rate Settings",
            description = "Adjust brightness, adaptive refresh rate, and screen timeout",
            actionType = ActionType.OPEN_DISPLAY_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Reduces display battery drain and thermal output",
            whatWillChange = "Opens Android Display settings",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT,
            verificationMethod = "Re-reads Screen Timeout and Refresh Rate after returning"
        ),
        DoctorAction(
            id = "repair_sound_levels",
            title = "🔊 Open Sound & Vibration Settings",
            description = "Inspect media, call, alarm volumes, DND & haptic toggles",
            actionType = ActionType.OPEN_SOUND_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "System volume policies and DND settings are user-controlled",
            whatWillChange = "Opens Android Sound & Vibration settings",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT,
            verificationMethod = "Re-reads AudioManager volume % and ringer mode"
        ),
        DoctorAction(
            id = "repair_notifications",
            title = "🔔 Open App Notification Settings",
            description = "Verify notification permissions, channels, and DND exceptions",
            actionType = ActionType.OPEN_NOTIFICATION_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Fixes muted channels or disabled notification toggles",
            whatWillChange = "Opens Android Notification preferences",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT,
            verificationMethod = "Re-checks NotificationManagerCompat.areNotificationsEnabled()"
        ),
        DoctorAction(
            id = "repair_accessibility",
            title = "♿ Open Accessibility & Touch Settings",
            description = "Inspect TalkBack, touch & hold delay, or screen magnification",
            actionType = ActionType.OPEN_ACCESSIBILITY_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Accessibility Services require explicit manual user enablement in Settings",
            whatWillChange = "Opens Android Accessibility settings",
            capability = AutomationCapability.ACCESSIBILITY_SERVICE,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_system_update",
            title = "🔄 Check Official OS & Security Update",
            description = "Verify if an official manufacturer firmware patch is pending",
            actionType = ActionType.OPEN_SYSTEM_UPDATE,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = false,
            whyRequired = "OS updates patch kernel memory leaks, modem bugs, and thermal issues",
            whatWillChange = "Opens official OEM System Update screen",
            capability = AutomationCapability.OEM_RESTRICTED,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_settings_search",
            title = "🔍 Open Android Settings (Search)",
            description = "Safe fallback when exact OEM menu path varies by software version",
            actionType = ActionType.OPEN_SETTINGS_SEARCH,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Avoids unverified menu paths across custom OEM skins or new Android versions",
            whatWillChange = "Opens root Android Settings so you can use the Search bar",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_location_gps",
            title = "📍 Open Location & GPS Settings",
            description = "Verify Location toggle, accuracy mode & per-app location access",
            actionType = ActionType.OPEN_LOCATION_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Location/GPS accuracy & app permissions are user-controlled system settings",
            whatWillChange = "Opens Android Location Services settings",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_security_privacy",
            title = "🛡️ Open Security & Privacy Settings",
            description = "Inspect Permission Manager, Device Admin apps, Work Profile & security status",
            actionType = ActionType.OPEN_SECURITY_PRIVACY_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Security, Restricted Settings, and Device Admin policies cannot be changed automatically",
            whatWillChange = "Opens Android Security & Privacy Settings",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_accounts_sync",
            title = "☁️ Open Accounts & Cloud Sync Settings",
            description = "Inspect Google Account sync errors, backup schedule & login status",
            actionType = ActionType.OPEN_ACCOUNTS_SYNC_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Account authentication and sync schedules are protected OS credentials",
            whatWillChange = "Opens Android Accounts & Sync Settings",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_language_input",
            title = "⌨️ Open Language & Keyboard Input Settings",
            description = "Select default keyboard (Gboard/OEM), spell-checker & system language",
            actionType = ActionType.OPEN_LANGUAGE_INPUT_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Active IME keyboard selection requires explicit user confirmation in System Settings",
            whatWillChange = "Opens Android Languages & Input Settings",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_default_apps",
            title = "🏠 Open Default Apps & Launcher Settings",
            description = "Inspect default Home Launcher, Phone, SMS & Browser handlers",
            actionType = ActionType.OPEN_DEFAULT_APPS_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Default app roles are protected Android user choices",
            whatWillChange = "Opens Android Default Apps settings",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_date_time",
            title = "🕒 Open Date & Time Settings",
            description = "Enable Automatic date/time & timezone to fix HTTPS/Play Store/Sync SSL errors",
            actionType = ActionType.OPEN_DATE_TIME_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Safe",
            isReversible = true,
            whyRequired = "Wrong system clock causes Play Store, TLS/SSL, and Google sync failures",
            whatWillChange = "Opens Android Date & Time settings",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),
        DoctorAction(
            id = "repair_developer_options",
            title = "🛠️ Open Developer / USB Options",
            description = "Inspect USB debugging, default USB configuration, and window animations safely",
            actionType = ActionType.OPEN_DEVELOPER_OPTIONS_SETTINGS,
            level = RepairLevel.LEVEL_3,
            riskLevel = "Low",
            isReversible = true,
            whyRequired = "Developer Options and USB Debugging require explicit manual user control",
            whatWillChange = "Opens Developer Options (or Settings search if disabled)",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.SYSTEM_SETTINGS_INTENT
        ),

        // === HIGH-RISK / DESTRUCTIVE GUARDED ACTIONS (NEVER AUTO-EXECUTED) ===
        DoctorAction(
            id = "guarded_clear_app_data",
            title = "⚠️ Guided: Clear App Data (Logins & Local Data Reset)",
            description = "Last-resort guide when Clear Cache fails for a persistently crashing app",
            actionType = ActionType.GUARDED_CLEAR_APP_DATA_GUIDE,
            level = RepairLevel.LEVEL_4,
            riskLevel = "High Risk (Data Loss)",
            isReversible = false,
            whyRequired = "Only needed if corrupted app database causes repeated crashes after Clear Cache",
            whatWillChange = "Opens App Manager after explicit backup confirmation; NEVER deletes data automatically",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.DESTRUCTIVE_GUARDED,
            isDestructive = true,
            backupGuidance = "WARNING: Clearing App Data erases app logins, offline files, and local chats unless backed up to cloud (e.g., WhatsApp Google Drive backup) first."
        ),
        DoctorAction(
            id = "guarded_uninstall_unused",
            title = "⚠️ Guided: Review & Uninstall Heavy / Unused Apps",
            description = "Safely inspect and remove unused apps causing storage full or background battery drain",
            actionType = ActionType.GUARDED_UNINSTALL_UNUSED_GUIDE,
            level = RepairLevel.LEVEL_4,
            riskLevel = "Medium Risk (App Removal)",
            isReversible = false,
            whyRequired = "Frees gigabytes of storage and stops rogue background services",
            whatWillChange = "Opens Application Manager for user-selected uninstallation; never auto-uninstalls",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.DESTRUCTIVE_GUARDED,
            isDestructive = true,
            backupGuidance = "Before uninstalling any app, verify important photos, documents, or game progress are synced to your account."
        ),
        DoctorAction(
            id = "guarded_reset_network",
            title = "⚠️ Guided: Reset Wi-Fi, Mobile & Bluetooth Settings",
            description = "Last-resort network stack reset guide when Wi-Fi/BT/Mobile Data all fail",
            actionType = ActionType.GUARDED_RESET_NETWORK_GUIDE,
            level = RepairLevel.LEVEL_4,
            riskLevel = "Medium Risk (Saved Passwords Cleared)",
            isReversible = false,
            whyRequired = "Clears corrupted Wi-Fi credentials, Bluetooth bonds, and APN configs",
            whatWillChange = "Opens System Settings search/menu; never performs reset automatically",
            capability = AutomationCapability.MANUAL_SETTINGS,
            riskClass = RiskClassification.DESTRUCTIVE_GUARDED,
            isDestructive = true,
            backupGuidance = "WARNING: Resetting network settings deletes all saved Wi-Fi passwords and paired Bluetooth devices. Keep your Wi-Fi password handy."
        )
    )

    private val idAliasMap = mapOf(
        "action_battery" to "repair_battery_saver",
        "action_storage" to "repair_storage_cleaner",
        "action_apps" to "repair_app_cache",
        "action_wifi" to "repair_network_reset",
        "action_display" to "repair_display_timeout",
        "action_sound" to "repair_sound_levels",
        "action_bt" to "repair_bluetooth",
        "action_update" to "repair_system_update"
    )

    fun findAction(id: String): DoctorAction? {
        val canonicalId = idAliasMap[id] ?: id
        return approvedActions.find { it.id == canonicalId }
    }

    fun isRegistered(id: String): Boolean = findAction(id) != null
}
