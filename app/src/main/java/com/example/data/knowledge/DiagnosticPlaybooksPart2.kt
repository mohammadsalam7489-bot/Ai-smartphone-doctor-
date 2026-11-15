package com.example.data.knowledge

/**
 * Core 6 Diagnostic Playbooks (Part 2 of 2) + RAG Retriever.
 * Covers: Storage, Notifications, Display/Touch, Audio, Gaming Performance, Boot/Startup.
 */
object DiagnosticPlaybooksPart2 {

    val playbooks: List<DiagnosticPlaybook> = listOf(
        DiagnosticPlaybook(
            id = "pb_storage",
            problemTitle = "Storage Full & Safe Space Recovery",
            category = "Storage & Cleanup",
            problemIdentification = "'Storage space running out' warning, unable to update apps, or camera refuses to save photos.",
            symptomsAndKeywords = listOf(
                "storage", "space", "full", "memory full", "jagah", "bhari", "bhar gaya", "cache", "cleanup", "junk"
            ),
            possibleCauses = listOf(
                "Accumulated temporary cache in social/video apps (WhatsApp, YouTube, Instagram, Telegram).",
                "Large WhatsApp/Telegram downloaded videos, APKs, or Trash/Bin items not emptied.",
                "High-resolution 4K videos and duplicate downloads."
            ),
            diagnosticQuestions = listOf(
                "Did you recently delete files? Some OEM Galleries keep deleted items in Trash for 30 days."
            ),
            requiredDeviceInfo = listOf("Total storage", "Free storage", "Storage used %"),
            androidVersionCompatibility = "Android 7.0 – 17+ (Scoped Storage enforced on Android 11+ / API 30+)",
            oemSettingsPathResolver = { oem -> "${oem.storageSettingsPath} | ${oem.appCacheSettingsPath}" },
            stepByStepSolution = listOf(
                "Purge this app's own internal cache safely (exact bytes measured).",
                "Open Native Storage Manager to inspect Videos, Large Files, Downloads, and Trash.",
                "Open App Manager -> sort by size -> tap 'Clear Cache' ONLY (never 'Clear Data') on media apps.",
                "Empty Gallery / Files 'Recently Deleted / Trash' folder."
            ),
            automaticActionIds = listOf("auto_clear_own_cache", "repair_storage_cleaner", "repair_app_cache", "guarded_uninstall_unused"),
            requiredPermissions = listOf("None; Play Policy prohibits broad READ_EXTERNAL_STORAGE/MANAGE_EXTERNAL_STORAGE for cleaners."),
            androidApiRestrictions = "Android Sandboxing & Google Play Policy prevent cleaner apps from silently deleting other apps' caches or user media.",
            alternativeSolutions = listOf(
                "Back up photos/videos to Google Photos/cloud before deleting local copies.",
                "Guarded: Uninstall unused heavy games/apps."
            ),
            verificationMethod = "Re-read StatFs free storage bytes and confirm >15% free space.",
            hardwareFaultIndicators = listOf("Storage shows corrupted/0 MB total or disappears after reboot."),
            escalationInstructions = "If storage usage stays unexplained even after clearing Trash and caches, inspect 'System' partition or consult OEM support.",
            vitalsBoostTrigger = { v -> if (v.storageUsedPercent > 80) 3 else 0 }
        ),

        DiagnosticPlaybook(
            id = "pb_notifications",
            problemTitle = "Missing / Delayed App Notifications Repair",
            category = "Notifications & Background",
            problemIdentification = "Messages or alerts arrive only when opening the app, or notifications do not ring.",
            symptomsAndKeywords = listOf(
                "notification", "notifications", "message nahi", "alert", "late", "delay", "dnd", "whatsapp message"
            ),
            possibleCauses = listOf(
                "App notification permission or specific notification channel disabled.",
                "Aggressive OEM battery optimization / Deep Sleep killing background push.",
                "Do Not Disturb (DND) or Silent/Vibrate mode active."
            ),
            diagnosticQuestions = listOf(
                "Are notifications missing for all apps or only one specific app (like WhatsApp)?"
            ),
            requiredDeviceInfo = listOf("notificationsEnabled", "ringerMode", "PowerSaver state", "OEM skin"),
            androidVersionCompatibility = "Android 13+ (API 33+) enforces runtime POST_NOTIFICATIONS permission.",
            oemSettingsPathResolver = { oem -> "${oem.notificationSettingsPath} | ${oem.batterySettingsPath}" },
            stepByStepSolution = listOf(
                "Verify notification permission and ringer mode.",
                "Open App Notification Settings and confirm 'Allow notifications' + relevant channels are ON.",
                "Open Battery Settings -> select the app -> change Battery optimization to 'Unrestricted' (and enable Autostart on Xiaomi/OPPO/Vivo).",
                "Verify Do Not Disturb schedules are off."
            ),
            automaticActionIds = listOf("repair_notifications", "repair_battery_saver", "repair_sound_levels"),
            requiredPermissions = listOf("None for NotificationManagerCompat status check; Settings intent opens app notification page."),
            androidApiRestrictions = "Apps cannot alter another app's notification channels, Do Not Disturb policy, or OEM Autostart whitelist.",
            alternativeSolutions = listOf(
                "Check if Data Saver or Background Data restriction is enabled for that app."
            ),
            verificationMethod = "Verify notificationsEnabled=true, Ringer=Normal, and ask user to test incoming message.",
            hardwareFaultIndicators = listOf("Audio speaker dead (check Audio Test if banners appear without sound)."),
            escalationInstructions = "Check OEM Security app (MIUI/ColorOS/iManager) Autostart rules if notifications still sleep when screen is off.",
            vitalsBoostTrigger = { v -> if (!v.notificationsEnabled || v.ringerMode != "Normal") 2 else 0 }
        ),

        DiagnosticPlaybook(
            id = "pb_display_touch",
            problemTitle = "Display, Touch Delay & Dead Pixel Diagnosis",
            category = "Display & Touch",
            problemIdentification = "Screen flickers, shows dead pixels/green line/burn-in, or touch feels laggy/erratic.",
            symptomsAndKeywords = listOf(
                "screen", "display", "touch", "pixel", "burn", "flicker", "ghost touch", "black", "kaam nahi", "bright"
            ),
            possibleCauses = listOf(
                "Damaged/thick screen protector or moisture/grease on glass.",
                "Battery Saver dropping display refresh rate from 120Hz to 60Hz.",
                "Accessibility features (TalkBack, Touch & Hold delay, Magnification) altering touch behavior.",
                "Hardware OLED/LCD panel or digitizer failure (dead pixels, vertical lines)."
            ),
            diagnosticQuestions = listOf(
                "Did the phone drop or get exposed to water recently?",
                "Do you see physical lines/spots, or does touch respond slowly?"
            ),
            requiredDeviceInfo = listOf("Display resolution", "Refresh rate Hz", "Screen timeout", "Thermal status"),
            androidVersionCompatibility = "Android 7.0 – 17+ (API 24+)",
            oemSettingsPathResolver = { oem -> oem.displaySettingsPath },
            stepByStepSolution = listOf(
                "Run full-screen RGB Sub-Pixel & Touch Tap Matrix Test.",
                "Clean screen and inspect without cracked tempered glass if ghost touches occur.",
                "Check Display Settings -> Refresh Rate / Touch sensitivity.",
                "Check Accessibility Settings if double-tap or talkback focus box appears."
            ),
            automaticActionIds = listOf("test_screen", "repair_display_timeout", "repair_accessibility"),
            requiredPermissions = listOf("None"),
            androidApiRestrictions = "Apps cannot override hardware refresh rate or accessibility touch filters directly.",
            alternativeSolutions = listOf(
                "Reboot phone to reset display driver state."
            ),
            verificationMethod = "User completes 5-color RGB screen inspection and confirms touch tap advances smoothly.",
            hardwareFaultIndicators = listOf(
                "Permanent green/pink vertical lines, black ink spots, or unresponsive strip across screen.",
                "Touch fails in the exact same physical area across all apps and tests."
            ),
            escalationInstructions = "Permanent lines, spots, or dead touch zones indicate physical panel/digitizer damage requiring screen replacement at an authorized center.",
            vitalsBoostTrigger = { _ -> 0 }
        ),

        DiagnosticPlaybook(
            id = "pb_audio",
            problemTitle = "Speaker, Volume & Audio Routing Repair",
            category = "Audio & Speaker",
            problemIdentification = "No sound from speaker, low/distorted audio, or calls/media routed incorrectly.",
            symptomsAndKeywords = listOf(
                "speaker", "sound", "audio", "volume", "awaaz", "mute", "mic", "distorted", "crackling", "awaz"
            ),
            possibleCauses = listOf(
                "Media/Ringtone volume muted or phone in Silent/DND mode.",
                "Audio silently routed to a connected Bluetooth device.",
                "Dust/lint/moisture blocking speaker grille.",
                "Physical speaker driver distortion or damage."
            ),
            diagnosticQuestions = listOf(
                "Does sound work on wired/Bluetooth earphones even if the phone speaker is silent?"
            ),
            requiredDeviceInfo = listOf("Media volume %", "Ringer mode", "Bluetooth enabled state"),
            androidVersionCompatibility = "Android 7.0 – 17+ (API 24+)",
            oemSettingsPathResolver = { oem -> "Settings > Sound & vibration" },
            stepByStepSolution = listOf(
                "Run automated AudioManager stream audit + 587Hz speaker test tone.",
                "If Bluetooth is ON, check Bluetooth Settings to ensure audio isn't hijacked by an idle headset.",
                "Open Sound Settings and raise Media, Call, and Ringtone sliders.",
                "Gently clean speaker mesh with a dry soft brush (never sharp pins or water)."
            ),
            automaticActionIds = listOf("auto_audit_audio", "test_audio", "repair_sound_levels", "repair_bluetooth"),
            requiredPermissions = listOf("None for AudioManager read & diagnostic playback."),
            androidApiRestrictions = "Apps should not forcibly override user Silent/DND policy without explicit request.",
            alternativeSolutions = listOf(
                "Restart phone to reset Android AudioFlinger service."
            ),
            verificationMethod = "AudioManager confirms non-zero volume + user confirms 587Hz test tone sounds clear & undistorted.",
            hardwareFaultIndicators = listOf("No sound during calibrated test tone even at 100% Media volume with Bluetooth OFF, or severe rattling/buzzing."),
            escalationInstructions = "If calibrated speaker test produces zero or buzzing sound at full volume with Bluetooth off, speaker module service is required.",
            vitalsBoostTrigger = { v -> if (v.mediaVolumePercent == 0 || v.ringerMode == "Silent") 1 else 0 }
        ),

        DiagnosticPlaybook(
            id = "pb_gaming",
            problemTitle = "Gaming FPS Drops & Thermal Throttling Diagnosis",
            category = "Gaming Performance",
            problemIdentification = "Games stutter, drop FPS after 10-15 minutes, ping spikes, or touch lags while gaming.",
            symptomsAndKeywords = listOf(
                "game", "gaming", "fps", "bgmi", "pubg", "free fire", "cod", "frame drop", "ping", "ms", "throttle"
            ),
            possibleCauses = listOf(
                "Thermal throttling (>39°C) cutting CPU/GPU clocks to protect battery.",
                "High network ping / packet jitter on Wi-Fi or 4G/5G.",
                "Low RAM (<1.5 GB free) or low storage causing texture streaming stutters.",
                "Playing while fast-charging (doubles heat generation)."
            ),
            diagnosticQuestions = listOf(
                "Do FPS drops start immediately, or after 15 minutes when the phone gets warm?",
                "Are you playing while plugged into a charger?"
            ),
            requiredDeviceInfo = listOf("Battery temp °C", "ThermalStatus", "RAM free", "Storage free", "Network Ping RTT", "Refresh rate"),
            androidVersionCompatibility = "Android 7.0 – 17+ (ThermalStatus API 29+)",
            oemSettingsPathResolver = { oem -> "${oem.batterySettingsPath} | ${oem.displaySettingsPath}" },
            stepByStepSolution = listOf(
                "Audit Thermal Status, RAM, Storage, and Network Ping RTT.",
                "Unplug charger before gaming and remove thick heat-trapping cases.",
                "Lower in-game graphics preset (e.g. Smooth + Extreme/High FPS) to stay under thermal throttle threshold.",
                "Ensure Battery Saver is OFF during gaming and close background downloads."
            ),
            automaticActionIds = listOf("auto_audit_thermal_power", "auto_optimize_memory", "test_network", "repair_battery_saver"),
            requiredPermissions = listOf("INTERNET, ACCESS_NETWORK_STATE"),
            androidApiRestrictions = "Android isolates games; third-party apps cannot read internal FPS counters or alter GPU driver clocks.",
            alternativeSolutions = listOf(
                "Use OEM built-in Game Space / Game Turbo performance profile.",
                "Switch to 5GHz Wi-Fi closer to router if ping >100ms."
            ),
            verificationMethod = "Re-check ThermalStatus, Battery Temp <38°C, and Network Ping <60ms.",
            hardwareFaultIndicators = listOf("Instant overheating >44°C within 2 minutes of light play or sudden shutdown."),
            escalationInstructions = "If device hits critical thermal shutdown repeatedly at low graphics settings, have thermal paste/battery health inspected.",
            vitalsBoostTrigger = { v -> if (v.batteryTempC > 38f) 2 else 0 }
        ),

        DiagnosticPlaybook(
            id = "pb_boot_startup",
            problemTitle = "Boot Loop, Unexpected Shutdown & Startup Diagnosis",
            category = "Boot & System Stability",
            problemIdentification = "Phone restarts on its own, shuts down before battery hits 0%, or gets stuck on logo.",
            symptomsAndKeywords = listOf(
                "boot", "restart", "reboot", "shutdown", "band ho janda", "band ho jata", "apne aap band", "logo", "startup", "power off"
            ),
            possibleCauses = listOf(
                "Critically full internal storage (<500MB free) preventing Android boot mount.",
                "Degraded battery unable to supply peak current (shuts down at 15-40%).",
                "Faulty third-party app or corrupted OS update.",
                "Stuck physical power button or thermal emergency shutdown."
            ),
            diagnosticQuestions = listOf(
                "Does it shut down at a specific battery % (like 25%), or randomly even when plugged in?",
                "Can it boot normally into Safe Mode?"
            ),
            requiredDeviceInfo = listOf("Battery health & temp", "Storage free %", "Uptime hours", "Android version"),
            androidVersionCompatibility = "Android 7.0 – 17+ (API 24+)",
            oemSettingsPathResolver = { oem -> "${oem.storageSettingsPath} | Settings > System Update" },
            stepByStepSolution = listOf(
                "Audit Battery Health/Temp, Storage Free, and System Uptime.",
                "Ensure at least 10% internal storage is free immediately.",
                "Test Safe Mode: Hold Power -> long-press 'Power off' -> tap 'Safe Mode'. If stable, uninstall the latest downloaded app.",
                "Check for official OS/security updates."
            ),
            automaticActionIds = listOf("auto_audit_thermal_power", "repair_storage_cleaner", "repair_system_update", "guarded_uninstall_unused"),
            requiredPermissions = listOf("None; hardware power/boot recovery cannot be invoked by user apps."),
            androidApiRestrictions = "Apps cannot access bootloader, recovery mode, or kernel panic logs without system/root privileges.",
            alternativeSolutions = listOf(
                "Back up important data to cloud/PC immediately before further troubleshooting."
            ),
            verificationMethod = "Confirm >10% free storage, stable battery temperature, and monitor uptime stability for 24h.",
            hardwareFaultIndicators = listOf(
                "Shuts down instantly when unplugged or above 15% battery.",
                "Stuck on OEM boot logo even in Safe Mode or restarts while pressing gently near power key."
            ),
            escalationInstructions = "Unexpected shutdowns with healthy storage or boot loops in Safe Mode indicate battery cell failure, stuck power flex, or motherboard fault — back up data and visit a service center.",
            vitalsBoostTrigger = { v -> if (v.batteryHealth != "Good" && v.batteryHealth != "Normal") 2 else 0 }
        )
    )
}
