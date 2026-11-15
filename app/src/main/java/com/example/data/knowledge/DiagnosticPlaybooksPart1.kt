package com.example.data.knowledge

/**
 * Core 7 Diagnostic Playbooks (Part 1 of 2) derived from the AI Smartphone Doctor Blueprint.
 * Covers: Phone Hanging, Slow Phone, App Crashing, Wi-Fi, Mobile Data, Bluetooth, Battery Drain.
 */
object DiagnosticPlaybooksPart1 {

    val playbooks: List<DiagnosticPlaybook> = listOf(
        DiagnosticPlaybook(
            id = "pb_phone_hanging",
            problemTitle = "Phone Hanging / UI Freeze Repair",
            category = "Performance & Stability",
            problemIdentification = "Device screen becomes unresponsive, touches freeze for seconds, or launcher restarts.",
            symptomsAndKeywords = listOf(
                "hang", "freeze", "frozen", "stuck", "unresponsive", "not responding",
                "टका", "अटक", "hang ho", "ruk jata", "chal nahi raha", "jam"
            ),
            possibleCauses = listOf(
                "Critically low free internal storage (<10% free) starving Android swap/ART.",
                "High RAM memory pressure (isLowMemory=true) causing background process thrashing.",
                "Thermal throttling (>40°C) reducing CPU/GPU clock speeds.",
                "Long device uptime accumulating stale system services.",
                "Misbehaving or corrupted third-party background app."
            ),
            diagnosticQuestions = listOf(
                "Does the freeze happen inside one specific app or across the whole phone?",
                "Did this start after installing a recent app or OS update?"
            ),
            requiredDeviceInfo = listOf("RAM usage % & isLowMemory", "Free internal storage", "Battery temp & ThermalStatus", "Uptime hours"),
            androidVersionCompatibility = "Android 7.0 – 17+ (API 24+)",
            oemSettingsPathResolver = { oem -> "${oem.appCacheSettingsPath} | ${oem.storageSettingsPath}" },
            stepByStepSolution = listOf(
                "Run RAM & Storage telemetry check (automatically executed).",
                "Perform a full device restart (Power + Volume Up or Power menu) to clear OS memory leaks.",
                "Open Storage Manager and keep at least 10-15% internal space free.",
                "Open App Manager, select recently freezing apps, and tap 'Force Stop' + 'Clear Cache' (not Clear Data)."
            ),
            automaticActionIds = listOf("auto_optimize_memory", "auto_clear_own_cache", "repair_app_cache", "repair_storage_cleaner"),
            requiredPermissions = listOf("None for RAM/Storage inspection; manual approval for System Settings."),
            androidApiRestrictions = "Android sandboxing prohibits third-party apps from killing other apps' processes or rebooting the device without root/system privileges.",
            alternativeSolutions = listOf(
                "Boot into Safe Mode (hold Power > long-press Power Off) to check if a third-party app causes the freeze.",
                "Check for pending OEM OS updates."
            ),
            verificationMethod = "Compare before/after available RAM, lowMemory flag, and confirm UI responsiveness with user.",
            hardwareFaultIndicators = listOf(
                "Phone hangs even on the boot logo or in Safe Mode.",
                "Random colored lines or physical touch digitizer dead zones."
            ),
            escalationInstructions = "If hanging persists in Safe Mode with >20% free storage, back up data and visit an authorized service center for eMMC/UFS storage health inspection.",
            vitalsBoostTrigger = { v -> (if (v.isLowMemory || v.ramUsedPercent > 82) 3 else 0) + (if (v.storageUsedPercent > 88) 2 else 0) }
        ),

        DiagnosticPlaybook(
            id = "pb_slow_phone",
            problemTitle = "Slow Phone & Lag Diagnosis",
            category = "Performance",
            problemIdentification = "Animations stutter, apps take long to open, and switching screens feels sluggish.",
            symptomsAndKeywords = listOf(
                "slow", "lag", "sluggish", "lagging", "delay", "dheema", "slow chal", "smooth nahi", "bhaari"
            ),
            possibleCauses = listOf(
                "Low internal storage (<15% free) slowing flash write speeds.",
                "High RAM pressure (>80% used) forcing frequent app reloads.",
                "Battery Saver mode throttling CPU frequency and refresh rate.",
                "Elevated thermal state throttling SoC performance.",
                "Too many background auto-start apps."
            ),
            diagnosticQuestions = listOf(
                "Is Battery Saver currently enabled?",
                "How much free storage do you have left?"
            ),
            requiredDeviceInfo = listOf("RAM used %", "Storage free %", "PowerSaver state", "Battery temp", "Uptime"),
            androidVersionCompatibility = "Android 7.0 – 17+ (API 24+)",
            oemSettingsPathResolver = { oem -> "${oem.storageSettingsPath} | ${oem.batterySettingsPath}" },
            stepByStepSolution = listOf(
                "Inspect RAM, Storage, Battery Saver, and Thermal throttling telemetry.",
                "Clear diagnostic app cache and review heavy apps' cache in Storage Manager.",
                "Disable Battery Saver temporarily if maximum responsiveness is needed and battery >30%.",
                "Restart device if uptime exceeds 72 hours."
            ),
            automaticActionIds = listOf("auto_optimize_memory", "auto_audit_thermal_power", "repair_storage_cleaner", "repair_app_cache"),
            requiredPermissions = listOf("None for telemetry; System Settings requires user interaction."),
            androidApiRestrictions = "Non-system apps cannot change global animation scales or kill background apps silently.",
            alternativeSolutions = listOf(
                "Uninstall rarely used heavy apps (via guarded App Manager).",
                "Check OEM system updates for performance patches."
            ),
            verificationMethod = "Re-check RAM free bytes, storage free %, PowerSaver state, and confirm smoother response.",
            hardwareFaultIndicators = listOf("Severe lag immediately after factory reset with cool temperature."),
            escalationInstructions = "If performance remains degraded even with >25% free storage, cool temperature, and Battery Saver off, check for official OS patches or service diagnostics.",
            vitalsBoostTrigger = { v -> if (v.ramUsedPercent > 75 || v.storageUsedPercent > 80 || v.isPowerSaveMode) 2 else 0 }
        ),

        DiagnosticPlaybook(
            id = "pb_app_crashing",
            problemTitle = "App Crashing / Keeps Stopping Repair",
            category = "Applications",
            problemIdentification = "A specific app closes unexpectedly, shows 'keeps stopping', or fails to load.",
            symptomsAndKeywords = listOf(
                "crash", "crashing", "keeps stopping", "force close", "band ho jata", "open nahi ho raha", "app error", "anr"
            ),
            possibleCauses = listOf(
                "Corrupted temporary app cache or WebView mismatch.",
                "Insufficient free internal storage preventing app database writes.",
                "Outdated app version or OS compatibility bug.",
                "Corrupted local app storage (requires guarded Clear Data only after backup)."
            ),
            diagnosticQuestions = listOf(
                "Which app is crashing, and does it crash immediately on launch?",
                "Did you update the app or Android System WebView recently?"
            ),
            requiredDeviceInfo = listOf("Free storage", "RAM pressure", "Android version/API level"),
            androidVersionCompatibility = "Android 7.0 – 17+ (API 24+)",
            oemSettingsPathResolver = { oem -> oem.appCacheSettingsPath },
            stepByStepSolution = listOf(
                "Verify free storage is >2 GB so apps can write temp files.",
                "Open App Manager -> select the crashing app -> tap 'Force Stop'.",
                "Tap 'Storage & Cache' -> tap 'Clear Cache' ONLY (preserves logins & chats).",
                "Check Play Store for app & 'Android System WebView' updates."
            ),
            automaticActionIds = listOf("auto_clear_own_cache", "repair_app_cache", "repair_storage_cleaner", "guarded_clear_app_data"),
            requiredPermissions = listOf("None for guidance; clearing another app's cache requires user tap in App Settings."),
            androidApiRestrictions = "Android security isolates every app's /data directory; no third-party app can read crash logs or clear cache of another app.",
            alternativeSolutions = listOf(
                "Last resort (destructive guarded): Back up chats/logins first, then Clear App Data or reinstall the app.",
                "Check System Update for OS WebView/framework patches."
            ),
            verificationMethod = "Re-open the affected app after clearing cache and confirm whether it launches without crashing.",
            hardwareFaultIndicators = listOf("Multiple unrelated system apps crashing simultaneously alongside random reboots."),
            escalationInstructions = "If all apps crash after cache clear and OS update, back up data and contact OEM support.",
            vitalsBoostTrigger = { v -> if (v.storageUsedPercent > 90) 2 else 0 }
        ),

        DiagnosticPlaybook(
            id = "pb_wifi",
            problemTitle = "Wi-Fi Connectivity & DNS Troubleshooting",
            category = "Network & Wi-Fi",
            problemIdentification = "Wi-Fi connects without internet, disconnects repeatedly, or has very high ping.",
            symptomsAndKeywords = listOf(
                "wifi", "wi-fi", "no internet", "connected without internet", "dns", "router", "hotspot", "net slow"
            ),
            possibleCauses = listOf(
                "Wi-Fi radio disabled or router WAN offline.",
                "Stale DHCP IP lease or unresponsive ISP DNS server.",
                "Weak 5GHz signal or router channel congestion.",
                "Battery Saver or VPN blocking background sockets."
            ),
            diagnosticQuestions = listOf(
                "Do other devices on the same Wi-Fi have working internet?",
                "Does Wi-Fi say 'Connected without internet'?"
            ),
            requiredDeviceInfo = listOf("Network transport", "NET_CAPABILITY_VALIDATED", "Wi-Fi radio state", "TCP Ping RTT"),
            androidVersionCompatibility = "Android 10+ (API 29+) blocks programmatic Wi-Fi enable/disable.",
            oemSettingsPathResolver = { oem -> oem.networkSettingsPath },
            stepByStepSolution = listOf(
                "Run real DNS & TCP socket handshake test (8.8.8.8 & 1.1.1.1).",
                "Open Wi-Fi Settings -> tap connected network -> 'Forget' and reconnect with password.",
                "Toggle Airplane Mode ON for 10 seconds then OFF.",
                "Optionally set Private DNS (Settings > Network > Private DNS) to 'dns.google'."
            ),
            automaticActionIds = listOf("auto_refresh_network_socket", "test_network", "repair_network_reset", "guarded_reset_network"),
            requiredPermissions = listOf("INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE"),
            androidApiRestrictions = "Since Android 10 (API 29), third-party apps cannot silently toggle Wi-Fi or modify saved Wi-Fi SSIDs/DNS.",
            alternativeSolutions = listOf(
                "Restart the Wi-Fi router for 30 seconds.",
                "Last resort (guarded): Reset Wi-Fi, mobile & Bluetooth settings after noting passwords."
            ),
            verificationMethod = "Re-run TCP ping test and verify validated online status + reduced RTT ms.",
            hardwareFaultIndicators = listOf("Wi-Fi toggle turns greyed out or immediately switches back OFF on its own."),
            escalationInstructions = "If Wi-Fi toggle refuses to turn on or detects zero nearby networks in multiple locations, Wi-Fi IC/antenna hardware inspection is needed.",
            vitalsBoostTrigger = { v -> if (!v.isInternetAvailable || v.networkType == "Wi-Fi") 2 else 0 }
        ),

        DiagnosticPlaybook(
            id = "pb_mobile_data",
            problemTitle = "Mobile Data & Cellular Signal Troubleshooting",
            category = "Network & Cellular",
            problemIdentification = "Mobile data icon shows no internet, 4G/5G drops, or pages fail to load on SIM data.",
            symptomsAndKeywords = listOf(
                "mobile data", "cellular", "sim", "4g", "5g", "network nahi", "internet nahi", "data slow", "volte", "apn"
            ),
            possibleCauses = listOf(
                "Mobile data disabled, SIM data limit reached, or Data Saver active.",
                "Incorrect APN (Access Point Name) or stuck 5G/4G tower registration.",
                "Carrier outage or expired data pack."
            ),
            diagnosticQuestions = listOf(
                "Are you able to make normal voice calls on the SIM?",
                "Is Data Saver or Roaming restriction enabled?"
            ),
            requiredDeviceInfo = listOf("Network transport", "Online validation", "TCP RTT latency"),
            androidVersionCompatibility = "Android 7.0 – 17+ (API 24+)",
            oemSettingsPathResolver = { oem -> oem.networkSettingsPath },
            stepByStepSolution = listOf(
                "Verify live network transport and socket ping latency.",
                "Toggle Airplane Mode ON for 15 seconds then OFF to re-register SIM with cell tower.",
                "Open Mobile Data / SIM Settings and verify preferred network type (5G/4G Auto) and Data Saver OFF.",
                "In Access Point Names (APN), tap menu -> 'Reset to default'."
            ),
            automaticActionIds = listOf("auto_refresh_network_socket", "repair_mobile_data", "test_network", "guarded_reset_network"),
            requiredPermissions = listOf("INTERNET, ACCESS_NETWORK_STATE (SIM/APN controls are carrier/system protected)"),
            androidApiRestrictions = "Android forbids third-party apps from toggling mobile data, switching SIMs, or editing carrier APN settings.",
            alternativeSolutions = listOf(
                "Temporarily switch Preferred Network from 5G to 4G/LTE if 5G signal is weak.",
                "Re-insert SIM card or test SIM in another phone."
            ),
            verificationMethod = "Re-check network transport = Mobile Data, Validated Online = true, and TCP Ping RTT.",
            hardwareFaultIndicators = listOf("Phone shows 'No SIM' or 'Baseband Unknown' across multiple working SIMs."),
            escalationInstructions = "Contact mobile carrier for SIM/plan issues, or service center if 'No SIM / Invalid IMEI' occurs with multiple working SIMs.",
            vitalsBoostTrigger = { v -> if (!v.isInternetAvailable || v.networkType == "Mobile Data") 2 else 0 }
        ),

        DiagnosticPlaybook(
            id = "pb_bluetooth",
            problemTitle = "Bluetooth Pairing & Audio Drop Troubleshooting",
            category = "Connectivity & Peripherals",
            problemIdentification = "Bluetooth headphones/watch won't pair, disconnects randomly, or has audio stutter.",
            symptomsAndKeywords = listOf(
                "bluetooth", "earbuds", "headphone", "airpods", "tws", "pair", "connect nahi", "bt", "watch"
            ),
            possibleCauses = listOf(
                "Bluetooth adapter off or stale pairing bond keys.",
                "Accessory connected to another nearby device (multipoint conflict).",
                "2.4GHz Wi-Fi interference or low accessory battery.",
                "Audio routed to phone speaker instead of Bluetooth media profile."
            ),
            diagnosticQuestions = listOf(
                "Does the Bluetooth device pair with a different phone?",
                "Is it failing to pair, or pairing with no audio?"
            ),
            requiredDeviceInfo = listOf("Bluetooth adapter state", "Audio ringer/media state", "Android version"),
            androidVersionCompatibility = "Android 12+ (API 31+) uses BLUETOOTH_CONNECT/SCAN runtime permissions for active scanning.",
            oemSettingsPathResolver = { oem -> "Settings > Bluetooth / Connected devices (${oem.networkSettingsPath})" },
            stepByStepSolution = listOf(
                "Verify Bluetooth adapter state in telemetry.",
                "Open Bluetooth Settings -> tap the gear icon next to the device -> select 'Forget / Unpair'.",
                "Put accessory back into pairing mode and pair freshly.",
                "Ensure 'Media Audio' and 'Call Audio' toggles are enabled inside that paired device's settings."
            ),
            automaticActionIds = listOf("repair_bluetooth", "auto_audit_audio", "guarded_reset_network"),
            requiredPermissions = listOf("None to inspect adapter state via BluetoothManager; Settings intent opens native pairing UI."),
            androidApiRestrictions = "Android prevents apps from silently unpairing or resetting system Bluetooth profiles without explicit user action.",
            alternativeSolutions = listOf(
                "Restart phone and turn off Bluetooth on nearby paired devices.",
                "Last resort (guarded): Reset Wi-Fi, Mobile & Bluetooth settings."
            ),
            verificationMethod = "Verify Bluetooth adapter = ON, pair accessory, and run Audio Test to confirm sound plays in headset.",
            hardwareFaultIndicators = listOf("Bluetooth toggle turns off immediately or cannot detect any devices within 1 meter."),
            escalationInstructions = "If Bluetooth fails to stay ON after network reset, service center hardware antenna/chip check is required.",
            vitalsBoostTrigger = { v -> if (!v.isBluetoothEnabled) 1 else 0 }
        ),

        DiagnosticPlaybook(
            id = "pb_battery_drain",
            problemTitle = "Battery Drain & Overheating Diagnosis",
            category = "Battery & Thermals",
            problemIdentification = "Battery percentage drops rapidly or phone feels noticeably hot (>39°C) during normal use.",
            symptomsAndKeywords = listOf(
                "battery", "drain", "garam", "heat", "overheat", "heating", "hot", "fast drain", "charging slow", "charge"
            ),
            possibleCauses = listOf(
                "High battery temperature (>38°C) due to gaming, camera, or fast charging in hot environment.",
                "Long screen sleep timeout (>60s) and high brightness / 120Hz refresh rate.",
                "Background app wake-locks or weak cellular signal boosting modem power.",
                "Degraded lithium battery cell (after 2+ years)."
            ),
            diagnosticQuestions = listOf(
                "Does heating happen while charging, gaming, or even while the screen is off?",
                "Which app shows highest % in Settings > Battery Usage?"
            ),
            requiredDeviceInfo = listOf("Battery level %", "Battery temp °C", "Charging status", "ThermalStatus", "PowerSaver state", "Screen timeout"),
            androidVersionCompatibility = "Android 7.0 – 17+ (ThermalStatus API 29+)",
            oemSettingsPathResolver = { oem -> oem.batterySettingsPath },
            stepByStepSolution = listOf(
                "Audit real thermal status, charging state, and screen timeout.",
                "Optimize Screen Timeout to 30 seconds (automatic via WRITE_SETTINGS if permitted, else Display Settings).",
                "Open Battery Settings -> inspect top battery-consuming app -> set background usage to 'Restricted' if unnecessary.",
                "Enable Battery Saver when battery is low or phone is warm, and unplug fast charger for 10 mins if >42°C."
            ),
            automaticActionIds = listOf("auto_audit_thermal_power", "auto_screen_timeout_safe", "repair_battery_saver", "repair_display_timeout"),
            requiredPermissions = listOf("WRITE_SETTINGS (optional special permission for 30s timeout automation)"),
            androidApiRestrictions = "Android blocks third-party apps from toggling system Battery Saver or killing other apps' wake-locks.",
            alternativeSolutions = listOf(
                "Lower refresh rate from 120Hz to 60Hz in Display Settings.",
                "Remove thick phone case while fast charging."
            ),
            verificationMethod = "Compare before/after battery temp °C, screen timeout seconds, and Battery Saver status.",
            hardwareFaultIndicators = listOf(
                "Battery swells/pushes back cover up (STOP using immediately!).",
                "Phone shuts down at 20-30% battery or temp exceeds 45°C while idle."
            ),
            escalationInstructions = "If battery swells, drops suddenly from 30% to 0%, or overheats while idle, visit an authorized service center for battery replacement.",
            vitalsBoostTrigger = { v -> (if (v.batteryTempC > 38f) 3 else 0) + (if (v.batteryLevel < 25) 2 else 0) }
        )
    )
}
