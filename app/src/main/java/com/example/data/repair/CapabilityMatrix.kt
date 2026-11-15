package com.example.data.repair

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.data.model.CapabilityMatrixEntry
import com.example.data.model.CapabilityTierCategory
import com.example.data.model.DeviceVitals

/**
 * Live Runtime Capability Matrix Auditor.
 * Classifies every diagnostic/repair action into the exact operational boundary
 * enforced by the current device OS version, granted permissions, and Play Policy rules.
 */
object CapabilityMatrix {

    fun buildLiveMatrix(
        context: Context,
        vitals: DeviceVitals,
        canWriteOverride: Boolean? = null
    ): List<CapabilityMatrixEntry> {
        val canWrite = canWriteOverride ?: runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
        val notificationsEnabled = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(vitals.notificationsEnabled)
        val dndAccess = runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.isNotificationPolicyAccessGranted == true
        }.getOrDefault(false)

        val supportsInAppPanels = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            runCatching {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).resolveActivity(context.packageManager) != null
            }.getOrDefault(false)

        val entries = mutableListOf<CapabilityMatrixEntry>()

        // === 1. PERFORMED AUTOMATICALLY (NORMAL APIS / ZERO RUNTIME PROMPTS) ===
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_auto_cache_apk",
                titleEn = "Purge App Cache & Stale Update APKs",
                titleHi = "ऐप कैश व पुराने अपडेट APK साफ़ करें",
                category = CapabilityTierCategory.AUTO_EXECUTED,
                androidApiOrPermission = "Context.cacheDir / codeCacheDir / filesDir/updates",
                isCurrentlyExecutableOnDevice = true,
                statusDetailEn = "Active: Clears temporary cache, external cache & obsolete APK downloads and verifies exact bytes freed.",
                statusDetailHi = "सक्रिय: इस ऐप का कैश व पुराने अपडेट APK हटाकर सटीक खाली स्थान मापता है।",
                guidedAlternativeActionId = "auto_clear_own_cache"
            )
        )
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_auto_audio_routing",
                titleEn = "Fix Stuck Audio Routing & Unmute Media Stream",
                titleHi = "ऑडियो रूटिंग रीसेट व म्यूट मीडिया सुधारें",
                category = CapabilityTierCategory.AUTO_EXECUTED,
                androidApiOrPermission = "AudioManager.setMode / setStreamVolume(STREAM_MUSIC)",
                isCurrentlyExecutableOnDevice = vitals.audioVerified,
                statusDetailEn = "Active: Safely normalizes MODE_NORMAL, clears stuck speakerphone state, raises muted STREAM_MUSIC to 45% & plays 587Hz tone.",
                statusDetailHi = "सक्रिय: म्यूट मीडिया वॉल्यूम (45% तक) व अटकी ऑडियो रूटिंग को स्वतः सुधारकर ध्वनि जाँचता है।",
                guidedAlternativeActionId = "auto_fix_audio_output"
            )
        )
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_auto_network_dns",
                titleEn = "Deep DNS, Captive Portal & TCP/TLS Network Audit",
                titleHi = "डीएनएस, कैप्टिव पोर्टल व टीसीपी नेटवर्क जाँच",
                category = CapabilityTierCategory.AUTO_EXECUTED,
                androidApiOrPermission = "INTERNET + ACCESS_NETWORK_STATE + InetAddress / Socket",
                isCurrentlyExecutableOnDevice = vitals.networkVerified,
                statusDetailEn = "Active: Tests DNS hostname lookup, 8.8.8.8/1.1.1.1 RTT ping, and captive portal/TLS status.",
                statusDetailHi = "सक्रिय: डीएनएस रिज़ॉल्यूशन, कैप्टिव पोर्टल और सर्वर पिंग विलंबता (ms) की लाइव जाँच करता है।",
                guidedAlternativeActionId = "auto_refresh_network_socket"
            )
        )
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_auto_memory_exit",
                titleEn = "RAM Pressure Audit, Heap Trim & Own Crash Telemetry",
                titleHi = "रैम दबाव, हीप सफ़ाई व क्रैश टेलीमेट्री जाँच",
                category = CapabilityTierCategory.AUTO_EXECUTED,
                androidApiOrPermission = "ActivityManager.MemoryInfo & ExitReasons (API ${vitals.apiLevel})",
                isCurrentlyExecutableOnDevice = vitals.ramVerified,
                statusDetailEn = if (vitals.apiLevel >= Build.VERSION_CODES.R) {
                    "Active (API ${vitals.apiLevel}): Releases in-app heap buffers & inspects historical process exit reasons."
                } else {
                    "Active: Releases in-app heap buffers & measures RAM pressure (ExitInfo requires Android 11+)."
                },
                statusDetailHi = "सक्रिय: ऐप मेमोरी बफ़र खाली करता है और सिस्टम रैम दबाव व क्रैश कारण पढ़ता है।",
                guidedAlternativeActionId = "auto_optimize_memory"
            )
        )
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_auto_thermal_hardware",
                titleEn = "Thermal Throttling, Battery, Display & Haptics Sensors",
                titleHi = "थर्मल थ्रॉटलिंग, बैटरी, डिस्प्ले व वाइब्रेशन जाँच",
                category = CapabilityTierCategory.AUTO_EXECUTED,
                androidApiOrPermission = "BatteryManager + PowerManager + Vibrator",
                isCurrentlyExecutableOnDevice = true,
                statusDetailEn = "Active: Reads live battery temp, thermal status, PowerSaver state & calibrates speaker/vibration tests.",
                statusDetailHi = "सक्रिय: लाइव तापमान, थर्मल स्तर, बैटरी सेवर स्थिति व हार्डवेयर परीक्षण चलाता है।",
                guidedAlternativeActionId = "auto_audit_thermal_power"
            )
        )

        // === 2. AUTOMATED AFTER RUNTIME OR SPECIAL PERMISSION ===
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_perm_write_settings",
                titleEn = "Auto-Optimize Screen Sleep Timeout (30s) & Adaptive Brightness",
                titleHi = "स्वचालित स्क्रीन टाइमआउट (30s) व ऑटो-ब्राइटनेस",
                category = CapabilityTierCategory.PERMISSION_GATED,
                androidApiOrPermission = "WRITE_SETTINGS (ACTION_MANAGE_WRITE_SETTINGS)",
                isCurrentlyExecutableOnDevice = canWrite,
                statusDetailEn = if (canWrite) {
                    "GRANTED: Ready to automatically apply 30s timeout + Adaptive Brightness and verify via read-back."
                } else {
                    "PENDING: Grant 'Modify system settings' once so the app can apply display power fixes automatically."
                },
                statusDetailHi = if (canWrite) {
                    "अनुमति स्वीकृत: 30 सेकंड स्क्रीन टाइमआउट और ऑटो-ब्राइटनेस स्वतः लागू करने के लिए तैयार।"
                } else {
                    "अनुमति आवश्यक: एक बार 'सिस्टम सेटिंग्स बदलें' अनुमति देने पर यह स्वतः काम करेगा।"
                },
                guidedAlternativeActionId = "auto_optimize_display_power"
            )
        )
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_perm_notifications",
                titleEn = "Repair Completion & Automatic APK Update Notifications",
                titleHi = "मरम्मत और ऐप अपडेट सूचनाएँ",
                category = CapabilityTierCategory.PERMISSION_GATED,
                androidApiOrPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "POST_NOTIFICATIONS (Runtime)" else "App Notification Channel",
                isCurrentlyExecutableOnDevice = notificationsEnabled,
                statusDetailEn = if (notificationsEnabled) {
                    "GRANTED: Update & diagnostic completion alerts enabled."
                } else {
                    "DISABLED: Enable notifications in App Notification Settings to receive APK update alerts."
                },
                statusDetailHi = if (notificationsEnabled) {
                    "अनुमति स्वीकृत: अपडेट व निदान अलर्ट चालू हैं।"
                } else {
                    "बंद है: सूचनाएँ पाने के लिए अधिसूचना सेटिंग्स में अनुमति चालू करें।"
                },
                guidedAlternativeActionId = "repair_notifications"
            )
        )
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_perm_dnd_policy",
                titleEn = "Do Not Disturb / Ringer Policy Awareness",
                titleHi = "DND (डू नॉट डिस्टर्ब) व रिंगर नीति स्थिति",
                category = CapabilityTierCategory.PERMISSION_GATED,
                androidApiOrPermission = "NotificationManager.isNotificationPolicyAccessGranted",
                isCurrentlyExecutableOnDevice = dndAccess,
                statusDetailEn = if (dndAccess) {
                    "GRANTED: DND policy inspection available; otherwise uses safe In-App Volume Panel."
                } else {
                    "RESTRICTED BY DEFAULT: App inspects DND safely and opens Android Sound/Volume Panel on tap."
                },
                statusDetailHi = if (dndAccess) {
                    "अनुमति स्वीकृत: रिंगर व DND स्थिति सीधे जांचने योग्य।"
                } else {
                    "डिफ़ॉल्ट सीमित: मीडिया वॉल्यूम स्वतः ठीक होगा; रिंगर/DND के लिए इन-ऐप वॉल्यूम पैनल खुलेगा।"
                },
                guidedAlternativeActionId = "repair_sound_levels"
            )
        )

        // === 3. REQUIRES IN-APP SETTINGS PANEL OR USER ACTION IN SETTINGS ===
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_panel_connectivity",
                titleEn = if (supportsInAppPanels) "In-App Wi-Fi / Mobile Internet Quick Panel" else "System Wi-Fi & Network Settings",
                titleHi = if (supportsInAppPanels) "इन-ऐप वाई-फाई / मोबाइल डेटा पैनल" else "सिस्टम वाई-फाई व नेटवर्क सेटिंग्स",
                category = CapabilityTierCategory.IN_APP_PANEL_OR_SETTINGS,
                androidApiOrPermission = if (supportsInAppPanels) "Settings.Panel.ACTION_INTERNET_CONNECTIVITY (Android 10+)" else "Settings.ACTION_WIFI_SETTINGS",
                isCurrentlyExecutableOnDevice = true,
                statusDetailEn = if (supportsInAppPanels) {
                    "Supported on Android ${vitals.androidVersion}: Opens quick connectivity sheet right over this app."
                } else {
                    "Opens native OEM Wi-Fi & Network settings directly."
                },
                statusDetailHi = if (supportsInAppPanels) {
                    "Android ${vitals.androidVersion} पर समर्थित: ऐप से बाहर जाए बिना नेटवर्क पैनल खुलता है।"
                } else {
                    "सीधे सिस्टम वाई-फाई सेटिंग्स खोलता है।"
                },
                guidedAlternativeActionId = "repair_network_reset"
            )
        )
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_settings_battery_storage_apps",
                titleEn = "Battery Saver, App Cache & Background Restriction Controls",
                titleHi = "बैटरी सेवर, अन्य ऐप कैश व बैकग्राउंड नियंत्रण",
                category = CapabilityTierCategory.IN_APP_PANEL_OR_SETTINGS,
                androidApiOrPermission = "ACTION_MANAGE_APPLICATIONS_SETTINGS / POWER_USAGE_SUMMARY",
                isCurrentlyExecutableOnDevice = true,
                statusDetailEn = "Launches exact OEM-resolved Android Settings page & verifies telemetry changes upon return.",
                statusDetailHi = "आपके फोन मॉडल के सटीक सिस्टम सेटिंग्स पृष्ठ को खोलता है और वापसी पर जाँच करता है।",
                guidedAlternativeActionId = "repair_app_cache"
            )
        )

        // === 4. STRICTLY BLOCKED BY ANDROID OS SECURITY SANDBOX ===
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_blocked_3p_cache",
                titleEn = "Silent Clear Cache / Data of Third-Party Apps",
                titleHi = "अन्य ऐप्स का कैश या डेटा चुपचाप साफ़ करना",
                category = CapabilityTierCategory.OS_SANDBOX_BLOCKED,
                androidApiOrPermission = "BLOCKED (DELETE_CACHE_FILES = signature|privileged)",
                isCurrentlyExecutableOnDevice = false,
                statusDetailEn = "Android isolates every app's storage sandbox. Guided App Manager opens safe Clear Cache.",
                statusDetailHi = "एंड्रॉइड सैंडबॉक्स अन्य ऐप्स की फ़ाइलें बदलने से रोकता है। ऐप मैनेजर में सुरक्षित मार्गदर्शन मिलता है।",
                guidedAlternativeActionId = "repair_app_cache"
            )
        )
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_blocked_force_stop",
                titleEn = "Silently Force-Stop or Kill Other Background Apps",
                titleHi = "अन्य ऐप्स को बैकग्राउंड में ज़बरदस्ती बंद करना",
                category = CapabilityTierCategory.OS_SANDBOX_BLOCKED,
                androidApiOrPermission = "BLOCKED (FORCE_STOP_PACKAGES = system/root only)",
                isCurrentlyExecutableOnDevice = false,
                statusDetailEn = "Non-system apps cannot terminate other processes on Android 14+. Opens App Manager safely.",
                statusDetailHi = "सुरक्षा नियम अन्य ऐप्स को सीधे बंद करने से रोकते हैं। ऐप मैनेजर में तुरंत विकल्प खुलता है।",
                guidedAlternativeActionId = "repair_app_cache"
            )
        )
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_blocked_silent_wifi_data",
                titleEn = "Silent Programmatic Wi-Fi / Mobile Data / Airplane Toggle",
                titleHi = "वाई-फाई, मोबाइल डेटा या फ्लाइट मोड चुपचाप बदलना",
                category = CapabilityTierCategory.OS_SANDBOX_BLOCKED,
                androidApiOrPermission = "BLOCKED on Android 10+ (API ${vitals.apiLevel}) • MODIFY_PHONE_STATE",
                isCurrentlyExecutableOnDevice = false,
                statusDetailEn = "WifiManager.setWifiEnabled() is deprecated/blocked since Android 10. Uses In-App Internet Panel instead.",
                statusDetailHi = "Android 10+ में कोई भी ऐप अपने आप वाई-फाई/डेटा बंद-चालू नहीं कर सकता। इन-ऐप पैनल दिया गया है।",
                guidedAlternativeActionId = "repair_network_reset"
            )
        )
        entries.add(
            CapabilityMatrixEntry(
                id = "cap_blocked_logs_firmware",
                titleEn = "Cross-App System Logs, Firmware Flashing & Factory Reset",
                titleHi = "अन्य ऐप्स के लॉग, फ़र्मवेयर फ्लैश व फ़ैक्टरी रीसेट",
                category = CapabilityTierCategory.OS_SANDBOX_BLOCKED,
                androidApiOrPermission = "BLOCKED (READ_LOGS / MASTER_CLEAR = privileged)",
                isCurrentlyExecutableOnDevice = false,
                statusDetailEn = "Prohibited by Android security & Google Play policy. Uses Official OS Update screen instead.",
                statusDetailHi = "ओएस सुरक्षा और प्ले नीति द्वारा पूर्णतः प्रतिबंधित। आधिकारिक सिस्टम अपडेट स्क्रीन उपलब्ध है।",
                guidedAlternativeActionId = "repair_system_update"
            )
        )

        // === 5. HARDWARE REPAIR REQUIRED ===
        val hwAlert = (vitals.temperatureVerified && vitals.batteryTempC >= 45f) ||
            vitals.batteryHealth.equals("Dead", true) ||
            vitals.batteryHealth.equals("Overheating", true) ||
            vitals.batteryHealth.equals("Over Voltage", true)

        entries.add(
            CapabilityMatrixEntry(
                id = "cap_hardware_service",
                titleEn = "Physical Battery, Speaker, Display Digitizer & Radio Faults",
                titleHi = "बैटरी सेल, स्पीकर, टच डिस्प्ले व एंटीना हार्डवेयर खराबी",
                category = CapabilityTierCategory.HARDWARE_SERVICE_REQUIRED,
                androidApiOrPermission = "Verified via Calibrated Tests & Battery/Thermal Sensors",
                isCurrentlyExecutableOnDevice = false,
                statusDetailEn = if (hwAlert) {
                    "ALERT DETECTED: Live battery/thermal telemetry (${vitals.batteryHealth}, ${if (vitals.temperatureVerified) "${vitals.batteryTempC}°C" else "N/A"}) indicates hardware inspection is needed."
                } else {
                    "No software patch can fix swollen batteries, dead pixels, broken speaker coils, or RF chip failure — verified via built-in hardware tests."
                },
                statusDetailHi = if (hwAlert) {
                    "चेतावनी: लाइव बैटरी/तापमान सेंसर (${vitals.batteryHealth}) हार्डवेयर जांच की आवश्यकता दर्शाता है।"
                } else {
                    "फूली बैटरी, टूटे स्पीकर या डेड टच पिक्सेल केवल अधिकृत सर्विस केंद्र में ठीक हो सकते हैं (इन-ऐप टेस्ट से पुष्टि करें)।"
                }
            )
        )

        return entries
    }
}
