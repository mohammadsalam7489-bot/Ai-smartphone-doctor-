package com.example.data.compatibility

import android.content.Context
import android.os.Build
import com.example.data.model.AndroidVersionCompatibility
import com.example.data.model.CoverageTier
import com.example.data.model.DeviceIdentitySummary
import com.example.data.model.OemCompatibilityProfile
import com.example.data.model.SettingsPathResolution
import org.json.JSONObject

data class OemFamilyRecord(
    val profileId: String,
    val manufacturer: String,
    val aliases: List<String>,
    val softwareFamily: String,
    val profileNotes: String,
    val gmsAvailable: Boolean,
    val formFactors: List<String>,
    val coverageTier: CoverageTier,
    val verificationStatus: String,
    val isPathVerified: Boolean,
    val settingsPaths: Map<String, String>
)

/**
 * Global Device, OEM Skin & Android Version Compatibility Engine (Manual v3.0).
 * Supports Android 7–17 (stable as of 12 Nov 2026) + Android 18+ unverified future placeholder,
 * and 30 Manufacturer / Software-Family profiles across Tiers A/B/C/D.
 * Never collects IMEI/serial/phone/account IDs, and never invents breadcrumbs for unverified devices.
 */
object DeviceCompatibilityEngine {

    private val GENERIC_PATHS = mapOf(
        "battery" to "Open Settings > Search 'Battery Saver' or 'Battery'",
        "storage" to "Open Settings > Search 'Storage'",
        "app_cache" to "Open Settings > Search 'Apps' > Select App > Storage > Clear Cache",
        "network" to "Open Settings > Search 'Wi-Fi' or 'Network'",
        "notifications" to "Open Settings > Search 'Notifications'",
        "display" to "Open Settings > Search 'Display' or 'Screen timeout'",
        "sound" to "Open Settings > Search 'Sound' or 'Volume'",
        "bluetooth" to "Open Settings > Search 'Bluetooth'",
        "system_update" to "Open Settings > Search 'System update' or 'Software update'",
        "accessibility" to "Open Settings > Search 'Accessibility'",
        "location" to "Open Settings > Search 'Location'",
        "security" to "Open Settings > Search 'Security' or 'Privacy'",
        "accounts" to "Open Settings > Search 'Accounts' or 'Sync'",
        "language" to "Open Settings > Search 'Languages' or 'Keyboard'",
        "default_apps" to "Open Settings > Search 'Default apps'",
        "developer" to "Open Settings > Search 'Developer options'"
    )

    private var loadedFromAssets: Boolean = false
    private var oemRegistry: List<OemFamilyRecord> = buildDefault30OemRegistry()
    private var customOverrides: MutableMap<String, OemFamilyRecord> = mutableMapOf()

    fun ensureLoaded(context: Context) {
        if (loadedFromAssets) return
        runCatching {
            val jsonText = context.assets.open("device_compatibility_v3.json")
                .bufferedReader()
                .use { it.readText() }
            val root = JSONObject(jsonText)
            val arr = root.optJSONArray("oem_families") ?: return
            val parsed = mutableListOf<OemFamilyRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val aliasesArr = obj.optJSONArray("aliases")
                val aliases = mutableListOf<String>()
                if (aliasesArr != null) {
                    for (j in 0 until aliasesArr.length()) aliases.add(aliasesArr.getString(j))
                }
                val ffArr = obj.optJSONArray("form_factors")
                val formFactors = mutableListOf<String>()
                if (ffArr != null) {
                    for (j in 0 until ffArr.length()) formFactors.add(ffArr.getString(j))
                }
                val pathsObj = obj.optJSONObject("settings_paths")
                val pathsMap = mutableMapOf<String, String>()
                if (pathsObj != null) {
                    val keys = pathsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        pathsMap[k] = pathsObj.getString(k)
                    }
                }
                val tier = runCatching {
                    CoverageTier.valueOf(obj.optString("coverage_tier", "D_UNKNOWN"))
                }.getOrDefault(CoverageTier.D_UNKNOWN)

                parsed.add(
                    OemFamilyRecord(
                        profileId = obj.getString("profile_id"),
                        manufacturer = obj.getString("manufacturer"),
                        aliases = aliases,
                        softwareFamily = obj.getString("software_family"),
                        profileNotes = obj.optString("profile_notes"),
                        gmsAvailable = obj.optBoolean("gms_available", true),
                        formFactors = formFactors.ifEmpty { listOf("phone") },
                        coverageTier = tier,
                        verificationStatus = obj.optString("verification_status", "doc_referenced"),
                        isPathVerified = obj.optBoolean("is_path_verified", false),
                        settingsPaths = pathsMap
                    )
                )
            }
            if (parsed.isNotEmpty()) {
                oemRegistry = parsed
                loadedFromAssets = true
            }
        }
    }

    fun registerOrUpdateCustomProfile(record: OemFamilyRecord) {
        customOverrides[record.profileId] = record
    }

    fun getAllRegisteredFamilies(): List<OemFamilyRecord> {
        val merged = oemRegistry.associateBy { it.profileId }.toMutableMap()
        customOverrides.forEach { (id, rec) -> merged[id] = rec }
        return merged.values.toList()
    }

    /**
     * Detects minimum useful device identity without collecting IMEI, serial number,
     * phone number, or account IDs (Manual v3.0 Section 3 & Google Play privacy rules).
     */
    fun detectMinimumDeviceIdentity(): DeviceIdentitySummary {
        val profile = getProfile()
        val release = Build.VERSION.RELEASE ?: "14"
        val patch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH ?: "Unknown"
        } else "Unknown"

        return DeviceIdentitySummary(
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            brand = Build.BRAND ?: "Unknown",
            model = Build.MODEL ?: "Unknown",
            device = Build.DEVICE ?: "Unknown",
            product = Build.PRODUCT ?: "Unknown",
            androidRelease = release,
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = patch,
            formFactor = profile.formFactors.firstOrNull() ?: "phone",
            gmsExpected = profile.gmsAvailable
        )
    }

    fun getProfile(context: Context? = null): OemCompatibilityProfile {
        if (context != null) ensureLoaded(context)
        return resolveProfile(
            manufacturer = Build.MANUFACTURER ?: "",
            brand = Build.BRAND ?: "",
            model = Build.MODEL ?: "",
            apiLevel = Build.VERSION.SDK_INT,
            androidVersion = Build.VERSION.RELEASE ?: "14"
        )
    }

    fun resolveVersionCompatibility(apiLevel: Int, androidVersion: String): AndroidVersionCompatibility {
        return when {
            apiLevel >= 38 -> AndroidVersionCompatibility(
                apiLevel = apiLevel,
                versionLabel = "Android 18+ (API $apiLevel — Unverified Future Placeholder)",
                isFutureOrPreview = true,
                apiRestrictions = listOf(
                    "Unverified future Android release placeholder — verify official CDD/API notes before assuming automated repair paths.",
                    "Use Settings Search fallback and runtime permission checks."
                ),
                supportedAutomationNotes = "Placeholder only; add verified instructions after official release documentation and physical testing."
            )
            apiLevel == 37 -> AndroidVersionCompatibility(
                apiLevel = 37,
                versionLabel = "Android 17 (API 37 — Stable)",
                isFutureOrPreview = false,
                apiRestrictions = listOf(
                    "Android 17 (stable as of June 16, 2026) adds local-network access controls for apps targeting API 37.",
                    "Strict scoped storage, foreground service gates, and runtime/special permission controls.",
                    "System Wi-Fi/Cellular/Battery Saver and cross-app cache clearing remain user-mediated."
                ),
                supportedAutomationNotes = "Uses approved local-network permissions or privacy-preserving pickers, ConnectivityManager, PowerManager thermal API, and verified Settings intents."
            )
            apiLevel == 36 -> AndroidVersionCompatibility(
                apiLevel = 36,
                versionLabel = "Android 16 (API 36 — Stable)",
                isFutureOrPreview = false,
                apiRestrictions = listOf(
                    "Separate stable profile from Android 17; predictive & adaptive thermal/background limits active.",
                    "Other apps' cache clearing & force-stop remain strictly sandboxed.",
                    "System write changes require explicit ACTION_MANAGE_WRITE_SETTINGS consent."
                ),
                supportedAutomationNotes = "Full support for PowerManager thermal status, ConnectivityManager diagnostics, and Intent-based System Settings."
            )
            apiLevel == 35 -> AndroidVersionCompatibility(
                apiLevel = 35,
                versionLabel = "Android 15 (API 35 — Stable)",
                isFutureOrPreview = false,
                apiRestrictions = listOf(
                    "Foreground service timeout & Private Space isolation enforced.",
                    "Third-party cache clearing restricted to own app directories."
                ),
                supportedAutomationNotes = "Thermal API, ConnectivityManager, and verified Settings intents supported."
            )
            apiLevel >= 33 -> AndroidVersionCompatibility(
                apiLevel = apiLevel,
                versionLabel = "Android ${if (apiLevel == 34) "14" else "13"} (API $apiLevel — Stable)",
                isFutureOrPreview = false,
                apiRestrictions = listOf(
                    "Runtime notification permission model & photo picker privacy active.",
                    "Background app killing & silent Wi-Fi toggling blocked by OS."
                ),
                supportedAutomationNotes = "Standard modern Android diagnostic & repair APIs supported."
            )
            apiLevel >= 29 -> AndroidVersionCompatibility(
                apiLevel = apiLevel,
                versionLabel = "Android $androidVersion (API $apiLevel — Stable)",
                isFutureOrPreview = false,
                apiRestrictions = listOf(
                    "Android 10+ prohibits apps from toggling Wi-Fi programmatically (Settings Panel required).",
                    "PowerManager thermal status available from API 29+."
                ),
                supportedAutomationNotes = "Audio, Battery, Connectivity, and Thermal status APIs supported."
            )
            else -> AndroidVersionCompatibility(
                apiLevel = apiLevel,
                versionLabel = "Android $androidVersion (API $apiLevel — Legacy)",
                isFutureOrPreview = false,
                apiRestrictions = listOf(
                    "Legacy Android API surface; thermal status API unavailable below API 29."
                ),
                supportedAutomationNotes = "Core Battery, Storage, RAM, and Settings Intent flows supported."
            )
        }
    }

    fun resolveProfile(
        manufacturer: String,
        brand: String,
        model: String,
        apiLevel: Int,
        androidVersion: String
    ): OemCompatibilityProfile {
        val m = manufacturer.lowercase().trim()
        val b = brand.lowercase().trim()
        val mod = model.lowercase().trim()
        val versionCompat = resolveVersionCompatibility(apiLevel, androidVersion)
        val families = getAllRegisteredFamilies()

        // Match exact sub-brand first (e.g., Redmi/POCO/iQOO/Pixel) before parent manufacturer
        val matched = families.firstOrNull { fam ->
            fam.aliases.any { alias ->
                b == alias || mod.contains(alias)
            }
        } ?: families.firstOrNull { fam ->
            fam.aliases.any { alias ->
                m.contains(alias) || b.contains(alias)
            }
        }

        if (matched != null) {
            val sp = matched.settingsPaths
            val useSearchFallback = !matched.isPathVerified || versionCompat.isFutureOrPreview
            return OemCompatibilityProfile(
                oemName = matched.manufacturer,
                osFlavor = matched.softwareFamily,
                batterySettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("battery") else sp["battery"] ?: GENERIC_PATHS.getValue("battery"),
                storageSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("storage") else sp["storage"] ?: GENERIC_PATHS.getValue("storage"),
                appCacheSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("app_cache") else sp["app_cache"] ?: GENERIC_PATHS.getValue("app_cache"),
                networkSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("network") else sp["network"] ?: GENERIC_PATHS.getValue("network"),
                notificationSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("notifications") else sp["notifications"] ?: GENERIC_PATHS.getValue("notifications"),
                displaySettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("display") else sp["display"] ?: GENERIC_PATHS.getValue("display"),
                soundSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("sound") else sp["sound"] ?: GENERIC_PATHS.getValue("sound"),
                bluetoothSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("bluetooth") else sp["bluetooth"] ?: GENERIC_PATHS.getValue("bluetooth"),
                systemUpdateSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("system_update") else sp["system_update"] ?: GENERIC_PATHS.getValue("system_update"),
                accessibilitySettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("accessibility") else sp["accessibility"] ?: GENERIC_PATHS.getValue("accessibility"),
                locationSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("location") else sp["location"] ?: GENERIC_PATHS.getValue("location"),
                securitySettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("security") else sp["security"] ?: GENERIC_PATHS.getValue("security"),
                accountsSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("accounts") else sp["accounts"] ?: GENERIC_PATHS.getValue("accounts"),
                languageSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("language") else sp["language"] ?: GENERIC_PATHS.getValue("language"),
                defaultAppsSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("default_apps") else sp["default_apps"] ?: GENERIC_PATHS.getValue("default_apps"),
                developerSettingsPath = if (useSearchFallback) GENERIC_PATHS.getValue("developer") else sp["developer"] ?: GENERIC_PATHS.getValue("developer"),
                specialNotes = buildString {
                    append(matched.profileNotes)
                    if (!matched.gmsAvailable) {
                        append(" Note: Google Mobile Services (Play Store/Play Services) may be absent on this profile.")
                    }
                    if (!matched.isPathVerified) {
                        append(" Exact model menu breadcrumbs unverified — Settings Search fallback active.")
                    }
                },
                profileId = matched.profileId,
                coverageTier = matched.coverageTier,
                verificationStatus = matched.verificationStatus,
                gmsAvailable = matched.gmsAvailable,
                formFactors = matched.formFactors,
                isPathVerifiedForBuild = matched.isPathVerified && !versionCompat.isFutureOrPreview,
                androidCompatibility = versionCompat
            )
        }

        val safeDisplayOem = manufacturer.ifBlank { brand }.ifBlank { "Android Device" }
            .replaceFirstChar { it.uppercase() }
        return OemCompatibilityProfile(
            oemName = safeDisplayOem,
            osFlavor = "Unknown / Custom OEM Skin",
            batterySettingsPath = GENERIC_PATHS.getValue("battery"),
            storageSettingsPath = GENERIC_PATHS.getValue("storage"),
            appCacheSettingsPath = GENERIC_PATHS.getValue("app_cache"),
            networkSettingsPath = GENERIC_PATHS.getValue("network"),
            notificationSettingsPath = GENERIC_PATHS.getValue("notifications"),
            displaySettingsPath = GENERIC_PATHS.getValue("display"),
            soundSettingsPath = GENERIC_PATHS.getValue("sound"),
            bluetoothSettingsPath = GENERIC_PATHS.getValue("bluetooth"),
            systemUpdateSettingsPath = GENERIC_PATHS.getValue("system_update"),
            accessibilitySettingsPath = GENERIC_PATHS.getValue("accessibility"),
            locationSettingsPath = GENERIC_PATHS.getValue("location"),
            securitySettingsPath = GENERIC_PATHS.getValue("security"),
            accountsSettingsPath = GENERIC_PATHS.getValue("accounts"),
            languageSettingsPath = GENERIC_PATHS.getValue("language"),
            defaultAppsSettingsPath = GENERIC_PATHS.getValue("default_apps"),
            developerSettingsPath = GENERIC_PATHS.getValue("developer"),
            specialNotes = "Exact menu labels for $safeDisplayOem are unverified (Tier D). Use Android Settings Search instead of guessing breadcrumbs.",
            profileId = "OEM-UNKNOWN-${safeDisplayOem.uppercase().replace(Regex("[^A-Z0-9]"), "")}",
            coverageTier = CoverageTier.D_UNKNOWN,
            verificationStatus = "unknown",
            gmsAvailable = true,
            formFactors = listOf("phone"),
            isPathVerifiedForBuild = false,
            androidCompatibility = versionCompat
        )
    }

    /**
     * Settings-path resolution engine (Manual v3.0 Section 6).
     * Never guesses breadcrumbs when profile is Tier D / unverified.
     */
    fun resolveSettingsPath(settingsKey: String, profile: OemCompatibilityProfile): SettingsPathResolution {
        val (canonical, path, aliases, searchTerms) = when (settingsKey) {
            "battery" -> Quad("Battery & Power Usage", profile.batterySettingsPath, listOf("Battery", "Device care", "Power saving"), listOf("Battery", "Battery saver", "App battery"))
            "storage" -> Quad("Storage Manager", profile.storageSettingsPath, listOf("Storage", "Device care > Storage", "Space cleanup"), listOf("Storage", "Free up space"))
            "app_cache" -> Quad("App Storage & Cache", profile.appCacheSettingsPath, listOf("Apps", "App management", "Manage apps"), listOf("Apps", "Clear cache"))
            "network" -> Quad("Wi-Fi & Mobile Network", profile.networkSettingsPath, listOf("Connections", "Network & internet", "Wi-Fi"), listOf("Wi-Fi", "Mobile network", "Private DNS"))
            "notifications" -> Quad("App Notifications", profile.notificationSettingsPath, listOf("Notifications", "Status bar", "Control center"), listOf("Notifications", "App notifications"))
            "display" -> Quad("Display & Brightness", profile.displaySettingsPath, listOf("Display", "Display & touch", "Refresh rate"), listOf("Display", "Screen timeout"))
            "sound" -> Quad("Sound & Vibration", profile.soundSettingsPath, listOf("Sounds", "Sound & vibration", "Do Not Disturb"), listOf("Sound", "Volume", "Do Not Disturb"))
            "bluetooth" -> Quad("Bluetooth & Connected Devices", profile.bluetoothSettingsPath, listOf("Bluetooth", "Connected devices", "Connections"), listOf("Bluetooth", "Paired devices"))
            "system_update" -> Quad("OS & Software Update", profile.systemUpdateSettingsPath, listOf("Software update", "System update", "About device"), listOf("System update", "Software update"))
            "accessibility" -> Quad("Accessibility", profile.accessibilitySettingsPath, listOf("Accessibility", "Additional settings", "Shortcuts & accessibility"), listOf("Accessibility", "TalkBack"))
            "location" -> Quad("Location & GPS", profile.locationSettingsPath, listOf("Location", "Location services"), listOf("Location", "Location accuracy"))
            "security" -> Quad("Security & Privacy", profile.securitySettingsPath, listOf("Security & privacy", "Permission manager", "Device admin"), listOf("Security", "Privacy", "Permission manager"))
            "accounts" -> Quad("Accounts & Cloud Sync", profile.accountsSettingsPath, listOf("Passwords & accounts", "Accounts & sync", "Users & accounts"), listOf("Accounts", "Sync", "Backup"))
            "language" -> Quad("Languages & Keyboard Input", profile.languageSettingsPath, listOf("Languages & input", "General management", "Keyboard"), listOf("Keyboard", "Language", "Input method"))
            "default_apps" -> Quad("Default Apps & Launcher", profile.defaultAppsSettingsPath, listOf("Default apps", "Choose default apps"), listOf("Default apps", "Home app"))
            "developer" -> Quad("Developer & USB Options", profile.developerSettingsPath, listOf("Developer options", "USB configuration"), listOf("Developer options", "USB debugging"))
            else -> Quad("Android Settings Search", "Open Settings > Search bar", listOf("Settings"), listOf("Settings"))
        }

        val resolved = if (profile.isPathVerifiedForBuild) {
            path
        } else {
            "Open Settings > Search '${searchTerms.first()}' (exact ${profile.oemName} menu unverified — avoiding guesswork)"
        }

        return SettingsPathResolution(
            actionId = "OPEN_${settingsKey.uppercase()}_SETTINGS",
            canonicalSetting = canonical,
            resolvedPath = resolved,
            oemAliases = aliases,
            fallbackSearchTerms = searchTerms,
            isVerifiedPath = profile.isPathVerifiedForBuild,
            coverageTier = profile.coverageTier
        )
    }

    fun getAllSupportedBrandsSummary(): List<String> =
        getAllRegisteredFamilies().map { "${it.manufacturer} (${it.softwareFamily})" }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun buildDefault30OemRegistry(): List<OemFamilyRecord> = listOf(
        OemFamilyRecord(
            "OEM-GOOGLE-PIXELUI", "Google", listOf("google", "pixel"), "Pixel Android / Pixel UI",
            "Pixel phones, tablets; features differ by generation.", true, listOf("phone", "tablet", "foldable"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery > Battery Saver / Battery usage",
                "storage" to "Settings > Storage > Free up space",
                "app_cache" to "Settings > Apps > See all apps > [Select App] > Storage & cache > Clear cache",
                "network" to "Settings > Network & internet > Internet / SIMs",
                "notifications" to "Settings > Notifications > App notifications",
                "display" to "Settings > Display & touch",
                "sound" to "Settings > Sound & vibration",
                "bluetooth" to "Settings > Connected devices > Connection preferences > Bluetooth",
                "system_update" to "Settings > System > Software updates",
                "accessibility" to "Settings > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Security & privacy",
                "accounts" to "Settings > Passwords, passkeys & accounts",
                "language" to "Settings > System > Languages / Keyboard",
                "default_apps" to "Settings > Apps > Default apps",
                "developer" to "Settings > System > Developer options"
            )
        ),
        OemFamilyRecord(
            "OEM-SAMSUNG-ONEUI", "Samsung", listOf("samsung"), "One UI",
            "Galaxy S, Z, A, M, F, tablets, rugged/enterprise.", true, listOf("phone", "tablet", "foldable", "rugged"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery (or Device care > Battery) > Power saving / Background usage limits",
                "storage" to "Settings > Device care > Storage",
                "app_cache" to "Settings > Apps > [Select App] > Storage > Clear cache",
                "network" to "Settings > Connections > Wi-Fi / Mobile networks",
                "notifications" to "Settings > Notifications > App notifications",
                "display" to "Settings > Display > Screen timeout / Motion smoothness",
                "sound" to "Settings > Sounds and vibration",
                "bluetooth" to "Settings > Connections > Bluetooth",
                "system_update" to "Settings > Software update > Download and install",
                "accessibility" to "Settings > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Security and privacy",
                "accounts" to "Settings > Accounts and backup > Manage accounts",
                "language" to "Settings > General management > Language / Keyboard list and default",
                "default_apps" to "Settings > Apps > Choose default apps",
                "developer" to "Settings > Developer options"
            )
        ),
        OemFamilyRecord(
            "OEM-XIAOMI-HYPEROS", "Xiaomi", listOf("xiaomi", "mi"), "HyperOS; older MIUI",
            "Xiaomi numbered, T, Mix, tablets; region variants.", true, listOf("phone", "tablet", "foldable"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery (or Security app > Battery) > App battery saver",
                "storage" to "Settings > About phone > Storage",
                "app_cache" to "Settings > Apps > Manage apps > [Select App] > Clear data > Clear cache",
                "network" to "Settings > Wi-Fi / Mobile networks",
                "notifications" to "Settings > Notifications & Control center > App notifications",
                "display" to "Settings > Display & brightness",
                "sound" to "Settings > Sound & vibration",
                "bluetooth" to "Settings > Bluetooth",
                "system_update" to "Settings > About phone > Xiaomi HyperOS / MIUI version",
                "accessibility" to "Settings > Additional settings > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Privacy & Security",
                "accounts" to "Settings > Accounts & sync",
                "language" to "Settings > Additional settings > Languages & input",
                "default_apps" to "Settings > Apps > Manage apps > 3-dots > Default apps",
                "developer" to "Settings > Additional settings > Developer options"
            )
        ),
        OemFamilyRecord(
            "OEM-REDMI-HYPEROS", "Redmi", listOf("redmi"), "HyperOS / MIUI",
            "Redmi Note, numbered Redmi, Redmi Pad.", true, listOf("phone", "tablet"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery > App battery saver / Battery saver",
                "storage" to "Settings > About phone > Storage",
                "app_cache" to "Settings > Apps > Manage apps > [Select App] > Clear data > Clear cache",
                "network" to "Settings > Wi-Fi / Mobile networks",
                "notifications" to "Settings > Notifications & Control center > App notifications",
                "display" to "Settings > Display & brightness",
                "sound" to "Settings > Sound & vibration",
                "bluetooth" to "Settings > Bluetooth",
                "system_update" to "Settings > About phone > OS update",
                "accessibility" to "Settings > Additional settings > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Privacy & Security",
                "accounts" to "Settings > Accounts & sync",
                "language" to "Settings > Additional settings > Languages & input",
                "default_apps" to "Settings > Apps > Manage apps > 3-dots > Default apps",
                "developer" to "Settings > Additional settings > Developer options"
            )
        ),
        OemFamilyRecord(
            "OEM-POCO-HYPEROS", "POCO", listOf("poco"), "HyperOS / MIUI",
            "F, X, M, C families; game controls vary.", true, listOf("phone", "tablet"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery > App battery saver / Performance mode",
                "storage" to "Settings > About phone > Storage",
                "app_cache" to "Settings > Apps > Manage apps > [Select App] > Clear data > Clear cache",
                "network" to "Settings > Wi-Fi / Mobile networks",
                "notifications" to "Settings > Notifications & Status bar",
                "display" to "Settings > Display",
                "sound" to "Settings > Sound & vibration",
                "bluetooth" to "Settings > Bluetooth",
                "system_update" to "Settings > About phone > System update",
                "accessibility" to "Settings > Additional settings > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Security status / Privacy protection",
                "accounts" to "Settings > Accounts & sync",
                "language" to "Settings > Additional settings > Languages & input",
                "default_apps" to "Settings > Apps > Manage apps > 3-dots > Default apps",
                "developer" to "Settings > Additional settings > Developer options"
            )
        ),
        OemFamilyRecord(
            "OEM-OPPO-COLOROS", "OPPO", listOf("oppo"), "ColorOS",
            "Find, Reno, A, tablets; version/market matters.", true, listOf("phone", "tablet", "foldable"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery > Power saving mode / More battery settings",
                "storage" to "Settings > About device > Storage",
                "app_cache" to "Settings > Apps > App management > [Select App] > Storage usage > Clear cache",
                "network" to "Settings > Wi-Fi / Mobile network",
                "notifications" to "Settings > Notifications & status bar",
                "display" to "Settings > Display & brightness",
                "sound" to "Settings > Sound & vibration",
                "bluetooth" to "Settings > Bluetooth",
                "system_update" to "Settings > About device > Software update",
                "accessibility" to "Settings > System settings > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Security & privacy",
                "accounts" to "Settings > Users & accounts",
                "language" to "Settings > System settings > Keyboard & input method",
                "default_apps" to "Settings > Apps > Default apps",
                "developer" to "Settings > System settings > Developer options"
            )
        ),
        OemFamilyRecord(
            "OEM-ONEPLUS-OXYGENOS", "OnePlus", listOf("oneplus"), "OxygenOS; historical variants",
            "Numbered, Nord, tablets; don't assume identical to ColorOS.", true, listOf("phone", "tablet", "foldable"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery > Power saving mode / Battery health",
                "storage" to "Settings > About device > Storage",
                "app_cache" to "Settings > Apps > App management > [Select App] > Storage usage > Clear cache",
                "network" to "Settings > Wi-Fi / Mobile network",
                "notifications" to "Settings > Notifications & status bar > App notifications",
                "display" to "Settings > Display & brightness",
                "sound" to "Settings > Sound & vibration",
                "bluetooth" to "Settings > Bluetooth",
                "system_update" to "Settings > About device > OxygenOS update",
                "accessibility" to "Settings > System settings > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Security & privacy",
                "accounts" to "Settings > Users & accounts",
                "language" to "Settings > System settings > Languages & keyboard",
                "default_apps" to "Settings > Apps > Default apps",
                "developer" to "Settings > System settings > Developer options"
            )
        ),
        OemFamilyRecord(
            "OEM-REALME-REALMEUI", "realme", listOf("realme"), "realme UI",
            "Numbered, GT, Narzo, C, tablets; region/build differences.", true, listOf("phone", "tablet"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery > Power saving mode / App battery management",
                "storage" to "Settings > Storage",
                "app_cache" to "Settings > Apps > App management > [Select App] > Storage usage > Clear cache",
                "network" to "Settings > Wi-Fi / Mobile network",
                "notifications" to "Settings > Notifications & status bar",
                "display" to "Settings > Display & brightness",
                "sound" to "Settings > Sound & vibration",
                "bluetooth" to "Settings > Bluetooth",
                "system_update" to "Settings > About device > realme UI update",
                "accessibility" to "Settings > System settings > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Security & privacy",
                "accounts" to "Settings > Users & accounts",
                "language" to "Settings > System settings > Keyboard & input method",
                "default_apps" to "Settings > Apps > Default apps",
                "developer" to "Settings > System settings > Developer options"
            )
        ),
        OemFamilyRecord(
            "OEM-VIVO-FUNTOUCH", "vivo", listOf("vivo"), "Funtouch OS / OriginOS",
            "Market-dependent; detect actual installed skin.", true, listOf("phone", "tablet", "foldable"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery (or iManager > Power management) > Background power consumption",
                "storage" to "Settings > RAM & storage space",
                "app_cache" to "Settings > Apps > See all apps > [Select App] > Storage & cache > Clear cache",
                "network" to "Settings > Network & internet",
                "notifications" to "Settings > Notifications > App settings",
                "display" to "Settings > Display & brightness",
                "sound" to "Settings > Sound & vibration",
                "bluetooth" to "Settings > Bluetooth & devices",
                "system_update" to "Settings > System update",
                "accessibility" to "Settings > Shortcuts & accessibility > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Security / Privacy",
                "accounts" to "Settings > Passwords & accounts",
                "language" to "Settings > System > Languages & input",
                "default_apps" to "Settings > Apps > Default apps",
                "developer" to "Settings > System > Developer options"
            )
        ),
        OemFamilyRecord(
            "OEM-IQOO-FUNTOUCH", "iQOO", listOf("iqoo"), "Funtouch / OriginOS variants",
            "Separate model profile even where UI resembles vivo.", true, listOf("phone"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery > Background power consumption / Ultra Game Mode",
                "storage" to "Settings > RAM & storage space",
                "app_cache" to "Settings > Apps > See all apps > [Select App] > Storage & cache > Clear cache",
                "network" to "Settings > Network & internet",
                "notifications" to "Settings > Notifications > App settings",
                "display" to "Settings > Display & brightness",
                "sound" to "Settings > Sound & vibration",
                "bluetooth" to "Settings > Bluetooth & devices",
                "system_update" to "Settings > System update",
                "accessibility" to "Settings > Shortcuts & accessibility > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Security / Privacy",
                "accounts" to "Settings > Passwords & accounts",
                "language" to "Settings > System > Languages & input",
                "default_apps" to "Settings > Apps > Default apps",
                "developer" to "Settings > System > Developer options"
            )
        ),
        OemFamilyRecord(
            "OEM-MOTOROLA-HELLOUI", "Motorola", listOf("motorola", "moto"), "Hello UI / My UX / near-stock",
            "G, Edge, Razr, ThinkPhone; carrier variants.", true, listOf("phone", "foldable"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery > Battery Saver / Adaptive Battery",
                "storage" to "Settings > Storage > Free up space",
                "app_cache" to "Settings > Apps > See all apps > [Select App] > Storage & cache > Clear cache",
                "network" to "Settings > Network & internet",
                "notifications" to "Settings > Notifications > App notifications",
                "display" to "Settings > Display",
                "sound" to "Settings > Sound & vibration",
                "bluetooth" to "Settings > Connected devices",
                "system_update" to "Settings > System updates",
                "accessibility" to "Settings > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Security & privacy",
                "accounts" to "Settings > Passwords & accounts",
                "language" to "Settings > System > Languages & input",
                "default_apps" to "Settings > Apps > Default apps",
                "developer" to "Settings > System > Developer options"
            )
        ),
        OemFamilyRecord(
            "OEM-NOTHING-OS", "Nothing", listOf("nothing", "cmf"), "Nothing OS",
            "Phone and CMF; version and model matter.", true, listOf("phone"),
            CoverageTier.B_OEM_FAMILY_VERIFIED, "source_checked", true,
            mapOf(
                "battery" to "Settings > Battery > Battery Saver / Sleep standby optimization",
                "storage" to "Settings > Storage",
                "app_cache" to "Settings > Apps > All apps > [Select App] > Storage & cache > Clear cache",
                "network" to "Settings > Network & internet",
                "notifications" to "Settings > Notifications > App settings",
                "display" to "Settings > Display",
                "sound" to "Settings > Sound & vibration",
                "bluetooth" to "Settings > Connected devices",
                "system_update" to "Settings > System > System update",
                "accessibility" to "Settings > Accessibility",
                "location" to "Settings > Location",
                "security" to "Settings > Security & privacy",
                "accounts" to "Settings > Passwords & accounts",
                "language" to "Settings > System > Languages & input",
                "default_apps" to "Settings > Apps > Default apps",
                "developer" to "Settings > System > Developer options"
            )
        ),
        OemFamilyRecord("OEM-HONOR-MAGIC", "HONOR", listOf("honor"), "MagicOS", "Magic, X, tablets; regional services differ.", true, listOf("phone", "tablet", "foldable"), CoverageTier.C_OS_BASELINE_VERIFIED, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-HUAWEI-EMUI", "Huawei", listOf("huawei"), "EMUI / HarmonyOS", "Separate profile; Google services (GMS) may be absent.", false, listOf("phone", "tablet", "foldable"), CoverageTier.D_UNKNOWN, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-SONY-XPERIA", "Sony", listOf("sony", "xperia"), "Xperia UI / near-stock", "Xperia; camera/audio/display functions model-specific.", true, listOf("phone"), CoverageTier.C_OS_BASELINE_VERIFIED, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-ASUS-ZENUI", "ASUS", listOf("asus", "zenfone", "rog"), "ZenUI / ROG UI", "Zenfone, ROG; game controls model-specific.", true, listOf("phone"), CoverageTier.C_OS_BASELINE_VERIFIED, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-LENOVO-ZUI", "Lenovo", listOf("lenovo"), "ZUI / Lenovo Android", "Phones/tablets; regional naming differs.", true, listOf("phone", "tablet"), CoverageTier.C_OS_BASELINE_VERIFIED, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-HMD-NOKIA", "HMD / Nokia", listOf("hmd", "nokia"), "Near-stock or vendor build", "Model and support lifecycle vary.", true, listOf("phone", "tablet"), CoverageTier.C_OS_BASELINE_VERIFIED, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-TECNO-HIOS", "TECNO", listOf("tecno"), "HiOS", "Camon, Phantom, Pova, Spark, tablets.", true, listOf("phone", "tablet"), CoverageTier.C_OS_BASELINE_VERIFIED, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-INFINIX-XOS", "Infinix", listOf("infinix"), "XOS", "Zero, Note, GT, Hot, tablets.", true, listOf("phone", "tablet"), CoverageTier.C_OS_BASELINE_VERIFIED, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-ITEL-OS", "itel", listOf("itel"), "itel OS / Android Go variants", "Entry-level builds may have reduced features.", true, listOf("phone"), CoverageTier.D_UNKNOWN, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-TCL-UI", "TCL", listOf("tcl"), "TCL UI / Android variants", "Phones/tablets and carrier models.", true, listOf("phone", "tablet"), CoverageTier.C_OS_BASELINE_VERIFIED, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-ZTE-MYOS", "ZTE / nubia / RedMagic", listOf("zte", "nubia", "redmagic"), "MyOS / nubia UI / RedMagic OS", "Gaming/performance menus need exact model.", true, listOf("phone"), CoverageTier.D_UNKNOWN, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-SHARP-AQUOS", "Sharp", listOf("sharp", "aquos"), "AQUOS UI / Android variants", "Japan carrier and regional builds.", true, listOf("phone"), CoverageTier.D_UNKNOWN, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-FAIRPHONE", "Fairphone", listOf("fairphone"), "Fairphone software", "Model generation and support policy.", true, listOf("phone"), CoverageTier.C_OS_BASELINE_VERIFIED, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-KYOCERA-RUGGED", "Kyocera", listOf("kyocera"), "DuraForce / rugged UI", "Rugged model and enterprise profile.", true, listOf("rugged", "phone"), CoverageTier.D_UNKNOWN, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-RUGGED-VENDOR", "Ulefone / Doogee / Blackview / Oukitel", listOf("ulefone", "doogee", "blackview", "oukitel"), "Vendor Android builds", "Large model range; exact firmware validation.", true, listOf("rugged", "phone", "tablet"), CoverageTier.D_UNKNOWN, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-CROSSCALL-SONIM", "Crosscall / Sonim", listOf("crosscall", "sonim"), "Rugged Android", "Enterprise, push-to-talk and device controls.", true, listOf("rugged", "phone"), CoverageTier.D_UNKNOWN, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-ENTERPRISE-ADMIN", "Zebra / Honeywell / Point Mobile / UROVO / Datalogic / Bluebird", listOf("zebra", "honeywell", "point mobile", "urovo", "datalogic", "bluebird"), "Enterprise Android", "Scanner, kiosk, OEMConfig and admin profiles.", true, listOf("enterprise", "rugged"), CoverageTier.D_UNKNOWN, "doc_referenced", false, GENERIC_PATHS),
        OemFamilyRecord("OEM-TABLET-POS-KIOSK", "Acer / Panasonic / Getac / Sunmi / iMin / Elo / Ascom", listOf("acer", "panasonic", "getac", "sunmi", "imin", "elo", "ascom"), "Tablet, POS, rugged, kiosk", "Separate form factor and deployment profile.", true, listOf("tablet", "pos", "kiosk", "rugged"), CoverageTier.D_UNKNOWN, "doc_referenced", false, GENERIC_PATHS)
    )
}
