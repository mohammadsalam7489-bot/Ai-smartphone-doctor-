package com.example.data.repair

/**
 * Complete 94-Workflow Real-World Repair Catalog across all 10 categories
 * defined in PDF Section 6 ("Android AI Automatic Repair — Developer Blueprint & Real-World Repair Catalog").
 *
 * Every workflow accurately states its Android API scope, required permissions, risk gate,
 * verification predicate, privacy footprint, and honest OS boundaries (never faking
 * impossible system-wide actions).
 */
object BlueprintRepairCatalog {

    val allDefinitions: List<RepairDefinition> by lazy {
        cat1Storage + cat2Transfers + cat3Stability + cat4Network + cat5Security +
            cat6Privacy + cat7Updates + cat8Media + cat9Notifications + cat10AiEngine
    }

    fun findById(issueId: String): RepairDefinition? =
        allDefinitions.find { it.issueId.equals(issueId, ignoreCase = true) }

    // -------------------------------------------------------------------------
    // Category 1 — Safe storage cleanup and file housekeeping (12 workflows)
    // -------------------------------------------------------------------------
    private val cat1Storage = listOf(
        def(
            id = "BP-01-001", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("app is taking too much space", "show storage breakdown", "clear app junk", "app size badh gaya hai"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Inspect cacheDir, codeCacheDir, externalCacheDir, databases, updates, exports"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Verify own-app cache & reclaimable size decreased and zero temp leftovers remain"),
            successKey = "bp_storage_cache_cleared",
            failureKey = "bp_storage_cleanup_unresolved",
            actions = listOf("auto_storage_housekeeping", "auto_clear_own_cache", "repair_storage_cleaner"),
            privacy = "Reads own-app directory byte sizes locally; zero file names uploaded"
        ),
        def(
            id = "BP-01-002", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("storage fills up again", "temporary files keep returning", "temp loop"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Scan own-app .tmp/.part files and schedule WorkManager hygiene"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_schedule_maintenance",
            verifications = listOf("Verify temp files = 0 and WorkManager periodic worker is ENQUEUED/RUNNING"),
            successKey = "bp_temp_loop_fixed",
            failureKey = "bp_temp_loop_unresolved",
            actions = listOf("auto_schedule_maintenance", "auto_storage_housekeeping"),
            privacy = "Local WorkManager state & byte counts only"
        ),
        def(
            id = "BP-01-003", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("broken thumbnails in app", "grey preview icon", "thumbnail corrupt"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Decode own-app thumbnails folder with BitmapFactory inJustDecodeBounds"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            rollback = "handler_rollback_file_backup",
            verifications = listOf("Re-decode every thumbnail and assert width > 0 and height > 0"),
            successKey = "bp_thumbnails_repaired",
            failureKey = "bp_thumbnails_unresolved",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Local thumbnail decode only"
        ),
        def(
            id = "BP-01-004", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("download failed storage missing", "incomplete download wasting space", "part file stuck"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check updates/downloads directory for .part/.tmp orphaned payloads"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Verify partial files count == 0 in app updates and exports folders"),
            successKey = "bp_partial_downloads_cleaned",
            failureKey = "bp_partial_downloads_unresolved",
            actions = listOf("auto_storage_housekeeping", "auto_clear_own_cache"),
            privacy = "Local byte counts only"
        ),
        def(
            id = "BP-01-005", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("duplicate saved reports", "duplicate exports", "same file saved twice"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Compute SHA-256 across app exports folder to detect duplicate content"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            rollback = "handler_rollback_file_backup",
            verifications = listOf("Verify duplicateExportsCount == 0 and export_index.json matches unique hashes"),
            successKey = "bp_duplicates_deduped",
            failureKey = "bp_duplicates_unresolved",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Local SHA-256 digests only; never reads user content remotely"
        ),
        def(
            id = "BP-01-006", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("saved file missing in app list", "saved items not showing", "rebuild index"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Compare exports directory files against export_index.json"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            rollback = "handler_rollback_file_backup",
            verifications = listOf("Verify export_index.json exists and entries match unique files on disk"),
            successKey = "bp_index_rebuilt",
            failureKey = "bp_index_unresolved",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Own-app index counts only"
        ),
        def(
            id = "BP-01-007", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("app cannot open last saved item", "corrupted local file", "last save broken"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check .bak backup availability and atomic checksum in app storage"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            rollback = "handler_rollback_file_backup",
            verifications = listOf("Verify target config/export file is non-empty and passes JSON/decode check"),
            successKey = "bp_corrupt_file_restored",
            failureKey = "bp_corrupt_file_unresolved",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Local integrity check only"
        ),
        def(
            id = "BP-01-008", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("saved report is empty", "exported file 0 bytes", "blank export"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Inspect export directory for 0-byte or unreadable files"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Verify zero 0-byte export artifacts remain"),
            successKey = "bp_empty_export_rebuilt",
            failureKey = "bp_empty_export_unresolved",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Local size verification only"
        ),
        def(
            id = "BP-01-009", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("old data keeps coming back after clearing", "stale cache returns"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check cache namespace & codeCache timestamps"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_clear_own_cache",
            verifications = listOf("Verify cacheDir and codeCacheDir byte size equals 0 after purge"),
            successKey = "bp_stale_cache_invalidated",
            failureKey = "bp_stale_cache_unresolved",
            actions = listOf("auto_clear_own_cache"),
            privacy = "Local cache byte metrics only"
        ),
        def(
            id = "BP-01-010", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("clear WhatsApp cache automatically", "clean all apps junk", "system wide junk cleaner"),
            scope = CapabilityScope.SHARED_STORAGE,
            preconditions = listOf("Verify Android scoped storage & app sandbox isolation rules"),
            risk = BlueprintRiskLevel.UNSUPPORTED,
            handler = "handler_unsupported_sandbox",
            verifications = listOf("Verify honest explanation & guided System App Manager intent provided"),
            successKey = "bp_guided_settings_opened",
            failureKey = "bp_sandbox_unsupported",
            actions = listOf("repair_app_cache", "repair_storage_cleaner"),
            unsupported = "Android OS sandboxing forbids non-system apps from deleting other apps' caches or scanning arbitrary /sdcard folders without MANAGE_EXTERNAL_STORAGE (restricted by Google Play policy).",
            privacy = "None"
        ),
        def(
            id = "BP-01-011", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("same file listed multiple times in app", "duplicate records"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Audit export_index.json and SHA-256 deduplication"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Verify index keys have zero duplicate SHA-256 digests"),
            successKey = "bp_duplicate_records_merged",
            failureKey = "bp_duplicate_records_unresolved",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Local index hashes only"
        ),
        def(
            id = "BP-01-012", cat = 1, catName = "Safe Storage Cleanup & File Housekeeping",
            complaints = listOf("sd card disappeared mid save", "removable storage read only", "sd card unmounted"),
            scope = CapabilityScope.USER_SELECTED_URI,
            preconditions = listOf("Check Environment.getExternalStorageState writable status"),
            risk = BlueprintRiskLevel.CONFIRMATION_REQUIRED,
            handler = "handler_storage_volume_check",
            verifications = listOf("Verify writable volume state before write attempt or switch to internal filesDir"),
            successKey = "bp_volume_checked",
            failureKey = "bp_volume_read_only",
            actions = listOf("repair_storage_cleaner"),
            privacy = "Storage mount state only"
        )
    )

    // -------------------------------------------------------------------------
    // Category 2 — Download, upload and file-transfer reliability (10 workflows)
    // -------------------------------------------------------------------------
    private val cat2Transfers = listOf(
        def(
            id = "BP-02-001", cat = 2, catName = "Download, Upload & File-Transfer Reliability",
            complaints = listOf("download stopped halfway", "apk download interrupted", "resume download"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Verify ConnectivityManager validated internet & discard incomplete .part files"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Verify network validated + DNS OK and partial files cleared for clean retry"),
            successKey = "bp_download_recovery_ready",
            failureKey = "bp_download_offline",
            actions = listOf("auto_refresh_network_socket", "auto_storage_housekeeping"),
            privacy = "Network capability & byte size only"
        ),
        def(
            id = "BP-02-002", cat = 2, catName = "Download, Upload & File-Transfer Reliability",
            complaints = listOf("download worked earlier now says expired", "403 expired url link"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check HTTPS endpoint reachability and URL expiry signature"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Confirm online DNS/TCP handshake passes so fresh download token/URL can be fetched"),
            successKey = "bp_expired_link_diag_ok",
            failureKey = "bp_expired_link_offline",
            actions = listOf("auto_refresh_network_socket"),
            privacy = "Host connectivity status only; URL query tokens redacted"
        ),
        def(
            id = "BP-02-003", cat = 2, catName = "Download, Upload & File-Transfer Reliability",
            complaints = listOf("upload created duplicate entries", "double upload on retry"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Verify SHA-256 content key & deduplicate local export index"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Verify single canonical entry exists per unique SHA-256 digest"),
            successKey = "bp_upload_idempotency_ok",
            failureKey = "bp_upload_idempotency_fail",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "File digest hash only"
        ),
        def(
            id = "BP-02-004", cat = 2, catName = "Download, Upload & File-Transfer Reliability",
            complaints = listOf("upload freezes at 99 percent", "transfer stalled at end"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check active network transport, captive portal, and socket RTT"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Verify DNS resolution passes, captive portal == false, and TCP RTT succeeds"),
            successKey = "bp_transfer_link_healthy",
            failureKey = "bp_transfer_link_blocked",
            actions = listOf("auto_refresh_network_socket", "repair_network_reset"),
            privacy = "RTT latency ms only"
        ),
        def(
            id = "BP-02-005", cat = 2, catName = "Download, Upload & File-Transfer Reliability",
            complaints = listOf("downloaded file will not open", "corrupt download", "cannot open downloaded file"),
            scope = CapabilityScope.USER_SELECTED_URI,
            preconditions = listOf("Require user-selected SAF URI to verify true MIME magic bytes safely"),
            risk = BlueprintRiskLevel.CONFIRMATION_REQUIRED,
            handler = "handler_security_uri_scan",
            verifications = listOf("Verify magic bytes, SHA-256 readability, and absence of spoofing"),
            successKey = "bp_file_header_inspected",
            failureKey = "bp_file_header_corrupt",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Header bytes & SHA-256 computed strictly on device"
        ),
        def(
            id = "BP-02-006", cat = 2, catName = "Download, Upload & File-Transfer Reliability",
            complaints = listOf("switching app breaks download", "download cancels in background"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check WorkManager persistent maintenance worker scheduling"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_schedule_maintenance",
            verifications = listOf("Confirm unique WorkManager job state is ENQUEUED/RUNNING"),
            successKey = "bp_persistent_work_active",
            failureKey = "bp_persistent_work_failed",
            actions = listOf("auto_schedule_maintenance", "repair_battery_saver"),
            privacy = "Worker state metadata only"
        ),
        def(
            id = "BP-02-007", cat = 2, catName = "Download, Upload & File-Transfer Reliability",
            complaints = listOf("same file downloaded multiple times", "duplicate apk files"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Audit filesDir/updates and filesDir/exports for stale/duplicate files"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Verify stale/duplicate files removed and only canonical build kept"),
            successKey = "bp_duplicate_downloads_cleaned",
            failureKey = "bp_duplicate_downloads_unresolved",
            actions = listOf("auto_storage_housekeeping", "auto_clear_own_cache"),
            privacy = "Local filename & byte sizes only"
        ),
        def(
            id = "BP-02-008", cat = 2, catName = "Download, Upload & File-Transfer Reliability",
            complaints = listOf("failed download keeps restarting forever", "stuck download retry loop"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check partial download files and WorkManager retry cap"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Confirm .part retry loop artifact removed and WorkManager retry bound enforced"),
            successKey = "bp_retry_loop_stopped",
            failureKey = "bp_retry_loop_unresolved",
            actions = listOf("auto_storage_housekeeping", "auto_schedule_maintenance"),
            privacy = "Retry counter only"
        ),
        def(
            id = "BP-02-009", cat = 2, catName = "Download, Upload & File-Transfer Reliability",
            complaints = listOf("shared file upload stops when sender moves it", "uri permission expired during share"),
            scope = CapabilityScope.USER_SELECTED_URI,
            preconditions = listOf("Validate ContentResolver URI stream before immutable staging"),
            risk = BlueprintRiskLevel.CONFIRMATION_REQUIRED,
            handler = "handler_security_uri_scan",
            verifications = listOf("Confirm staged byte count > 0 or report revoked URI cleanly"),
            successKey = "bp_uri_staged_ok",
            failureKey = "bp_uri_revoked",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Staged size & SHA-256 only"
        ),
        def(
            id = "BP-02-010", cat = 2, catName = "Download, Upload & File-Transfer Reliability",
            complaints = listOf("zip extraction failed", "archive unzip failed path traversal", "corrupted zip"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Run Zip-Slip canonical path check & max uncompressed size guard on archive"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Verify archive extraction rejects ../ entries and cleans staging folder on abort"),
            successKey = "bp_archive_guard_verified",
            failureKey = "bp_archive_blocked",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Zip structure validation only"
        )
    )

    // -------------------------------------------------------------------------
    // Category 3 — App stability, speed and memory (10 workflows)
    // -------------------------------------------------------------------------
    private val cat3Stability = listOf(
        def(
            id = "BP-03-001", cat = 3, catName = "App Stability, Speed & Memory",
            complaints = listOf("app freezes on opening", "app hang", "phone lag", "screen sluggish"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Read ActivityManager MemoryInfo, heap & cacheDir bloat"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_memory_and_cache",
            verifications = listOf("Verify own cache cleared and live MemoryInfo & lowMemory state measured"),
            successKey = "bp_app_lag_reduced",
            failureKey = "bp_app_lag_unresolved",
            actions = listOf("auto_optimize_memory", "auto_clear_own_cache", "repair_app_cache"),
            privacy = "System MemoryInfo aggregates only"
        ),
        def(
            id = "BP-03-002", cat = 3, catName = "App Stability, Speed & Memory",
            complaints = listOf("app crashes when rotating or backgrounding", "application keeps stopping", "process exit"),
            scope = CapabilityScope.OWN_APP,
            apiRange = 24..36,
            preconditions = listOf("Read ApplicationExitInfo on Android 11+ & verify own-app state stability"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_memory_and_cache",
            verifications = listOf("Inspect recent process exit reasons (API 30+) and confirm memory trim"),
            successKey = "bp_exit_diagnostics_ok",
            failureKey = "bp_exit_diagnostics_partial",
            actions = listOf("auto_optimize_memory", "repair_app_cache"),
            privacy = "Own-app exit reason code only; logs redacted"
        ),
        def(
            id = "BP-03-003", cat = 3, catName = "App Stability, Speed & Memory",
            complaints = listOf("app gets slower the longer I use it", "memory leak", "ram fills up over time"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Measure JVM runtime heap & ActivityManager MemoryInfo before/after buffer release"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_memory_and_cache",
            verifications = listOf("Verify freed heap or reclaimed cache > 0 without killing other apps"),
            successKey = "bp_memory_released",
            failureKey = "bp_memory_unchanged",
            actions = listOf("auto_optimize_memory", "auto_clear_own_cache"),
            privacy = "Heap & MemoryInfo numeric totals only"
        ),
        def(
            id = "BP-03-004", cat = 3, catName = "App Stability, Speed & Memory",
            complaints = listOf("loading spinner never finishes", "screen stuck loading"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check connectivity validation and cancel stalled socket timeout"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Verify network handshake resolves or fails fast with bounded timeout"),
            successKey = "bp_timeout_diag_complete",
            failureKey = "bp_timeout_offline",
            actions = listOf("auto_refresh_network_socket"),
            privacy = "Timeout state & RTT ms only"
        ),
        def(
            id = "BP-03-005", cat = 3, catName = "App Stability, Speed & Memory",
            complaints = listOf("high memory after viewing images", "bitmap out of memory"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check app thumbnails & image cache bytes"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_memory_and_cache",
            verifications = listOf("Verify cache purged and runtime free heap checked"),
            successKey = "bp_image_heap_freed",
            failureKey = "bp_image_heap_unchanged",
            actions = listOf("auto_clear_own_cache", "auto_optimize_memory"),
            privacy = "Byte counts only"
        ),
        def(
            id = "BP-03-006", cat = 3, catName = "App Stability, Speed & Memory",
            complaints = listOf("battery draining while idle", "duplicate background workers", "kill other apps background"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Inspect WorkManager unique periodic job policy & Battery Saver telemetry"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_schedule_maintenance",
            verifications = listOf("Verify single unique periodic worker exists with KEEP policy"),
            successKey = "bp_worker_deduped",
            failureKey = "bp_worker_failed",
            actions = listOf("auto_schedule_maintenance", "auto_audit_thermal_power", "repair_battery_saver"),
            privacy = "Worker ID & battery level % only"
        ),
        def(
            id = "BP-03-007", cat = 3, catName = "App Stability, Speed & Memory",
            complaints = listOf("background tasks fail endlessly", "retry loop overheating"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check WorkManager retry policy & thermal status"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_thermal_audit",
            verifications = listOf("Verify PowerManager thermal state & BatteryManager temp read cleanly"),
            successKey = "bp_retry_and_thermal_checked",
            failureKey = "bp_thermal_sensor_unavailable",
            actions = listOf("auto_audit_thermal_power", "auto_schedule_maintenance"),
            privacy = "Battery °C & thermal enum only"
        ),
        def(
            id = "BP-03-008", cat = 3, catName = "App Stability, Speed & Memory",
            complaints = listOf("heavy background task drains low battery", "battery saver stops app"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Inspect isPowerSaveMode & enforce WorkManager battery-not-low constraint"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_schedule_maintenance",
            verifications = listOf("Verify periodic maintenance has requiresBatteryNotLow constraint active"),
            successKey = "bp_battery_constraints_verified",
            failureKey = "bp_battery_constraints_failed",
            actions = listOf("auto_schedule_maintenance", "repair_battery_saver"),
            privacy = "PowerSaver boolean only"
        ),
        def(
            id = "BP-03-009", cat = 3, catName = "App Stability, Speed & Memory",
            complaints = listOf("phone gets warm when app left open", "heating", "garam ho raha hai"),
            scope = CapabilityScope.OWN_APP,
            requiredPerms = listOf("android.permission.WRITE_SETTINGS (optional for screen timeout)"),
            preconditions = listOf("Read thermal sensor + screen timeout + brightness mode"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_thermal_audit",
            rollback = "handler_rollback_display_settings",
            verifications = listOf("Verify thermal telemetry read & optional display timeout/brightness applied"),
            successKey = "bp_thermal_audit_ok",
            failureKey = "bp_thermal_audit_unavailable",
            actions = listOf("auto_audit_thermal_power", "auto_optimize_display_power", "repair_battery_saver"),
            privacy = "Battery temp °C only"
        ),
        def(
            id = "BP-03-010", cat = 3, catName = "App Stability, Speed & Memory",
            complaints = listOf("offline startup crash", "network failure breaks launch"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Verify Room database & local 466-issue KB asset availability offline"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_offline_kb_integrity",
            verifications = listOf("Verify local KB loaded > 0 records without requiring network"),
            successKey = "bp_offline_kb_verified",
            failureKey = "bp_offline_kb_missing",
            actions = listOf("auto_optimize_memory"),
            privacy = "None"
        )
    )

    // -------------------------------------------------------------------------
    // Category 4 — Internet, Wi-Fi and network troubleshooting (10 workflows)
    // -------------------------------------------------------------------------
    private val cat4Network = listOf(
        def(
            id = "BP-04-001", cat = 4, catName = "Internet, Wi-Fi & Network Troubleshooting",
            complaints = listOf("app cannot connect though other apps work", "wifi connected no internet", "network issue"),
            scope = CapabilityScope.OWN_APP,
            requiredPerms = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"),
            preconditions = listOf("Inspect ConnectivityManager capabilities, DNS, and direct TCP handshake"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Verify NET_CAPABILITY_VALIDATED, DNS resolution OK, captivePortal == false"),
            successKey = "bp_network_verified",
            failureKey = "bp_network_unresolved",
            actions = listOf("auto_refresh_network_socket", "repair_network_reset"),
            privacy = "Connectivity state, DNS server list & RTT ms"
        ),
        def(
            id = "BP-04-002", cat = 4, catName = "Internet, Wi-Fi & Network Troubleshooting",
            complaints = listOf("wifi switches to mobile data slows down", "network keeps dropping"),
            scope = CapabilityScope.OWN_APP,
            requiredPerms = listOf("android.permission.ACCESS_NETWORK_STATE"),
            preconditions = listOf("Inspect active transport, validated bit & RTT across 8.8.8.8 / 1.1.1.1"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Confirm stable validated transport & TCP RTT"),
            successKey = "bp_network_transport_checked",
            failureKey = "bp_network_transport_failed",
            actions = listOf("auto_refresh_network_socket", "repair_network_reset"),
            privacy = "Transport type string only"
        ),
        def(
            id = "BP-04-003", cat = 4, catName = "Internet, Wi-Fi & Network Troubleshooting",
            complaints = listOf("server lookup failed", "dns error", "unknown host exception"),
            scope = CapabilityScope.OWN_APP,
            requiredPerms = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"),
            preconditions = listOf("Perform real InetAddress DNS lookup and TCP socket comparison"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Verify InetAddress resolution returns non-empty host address"),
            successKey = "bp_dns_verified",
            failureKey = "bp_dns_failed",
            actions = listOf("auto_refresh_network_socket", "repair_network_reset"),
            privacy = "DNS pass/fail boolean"
        ),
        def(
            id = "BP-04-004", cat = 4, catName = "Internet, Wi-Fi & Network Troubleshooting",
            complaints = listOf("certificate security warning", "ssl error", "clock wrong tls error"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check system clock sanity without ever disabling TLS certificate validation"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Confirm system clock year >= 2024 and HTTPS check passes cleanly"),
            successKey = "bp_tls_strict_verified",
            failureKey = "bp_tls_clock_or_proxy_issue",
            actions = listOf("repair_date_time", "auto_refresh_network_socket"),
            privacy = "System epoch year sanity only"
        ),
        def(
            id = "BP-04-005", cat = 4, catName = "Internet, Wi-Fi & Network Troubleshooting",
            complaints = listOf("hotel wifi login blocking internet", "captive portal sign in required"),
            scope = CapabilityScope.OWN_APP,
            requiredPerms = listOf("android.permission.ACCESS_NETWORK_STATE"),
            preconditions = listOf("Check NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Verify captivePortal capability == false after router portal login"),
            successKey = "bp_captive_portal_cleared",
            failureKey = "bp_captive_portal_active",
            actions = listOf("auto_refresh_network_socket", "repair_network_reset"),
            privacy = "Captive portal boolean only"
        ),
        def(
            id = "BP-04-006", cat = 4, catName = "Internet, Wi-Fi & Network Troubleshooting",
            complaints = listOf("slow internet duplicate requests", "action runs twice on bad connection"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Verify pipeline idempotency token / duplicate action guard"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Confirm idempotency key prevents duplicate mutation"),
            successKey = "bp_request_dedup_ok",
            failureKey = "bp_request_dedup_fail",
            actions = listOf("auto_refresh_network_socket"),
            privacy = "None"
        ),
        def(
            id = "BP-04-007", cat = 4, catName = "Internet, Wi-Fi & Network Troubleshooting",
            complaints = listOf("sync failed on metered data", "mobile data heavy sync"),
            scope = CapabilityScope.OWN_APP,
            requiredPerms = listOf("android.permission.ACCESS_NETWORK_STATE"),
            preconditions = listOf("Check ConnectivityManager.isActiveNetworkMetered"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_schedule_maintenance",
            verifications = listOf("Verify unmetered network constraint applied when requested"),
            successKey = "bp_metered_defer_ok",
            failureKey = "bp_metered_defer_fail",
            actions = listOf("auto_schedule_maintenance", "repair_mobile_data"),
            privacy = "Metered status boolean only"
        ),
        def(
            id = "BP-04-008", cat = 4, catName = "Internet, Wi-Fi & Network Troubleshooting",
            complaints = listOf("app says offline when online", "stale offline banner"),
            scope = CapabilityScope.OWN_APP,
            requiredPerms = listOf("android.permission.ACCESS_NETWORK_STATE"),
            preconditions = listOf("Refresh ConnectivityManager active network & live socket test"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Verify live socket probe matches ConnectivityManager capability"),
            successKey = "bp_network_state_synced",
            failureKey = "bp_network_truly_offline",
            actions = listOf("auto_refresh_network_socket"),
            privacy = "Online boolean & RTT ms"
        ),
        def(
            id = "BP-04-009", cat = 4, catName = "Internet, Wi-Fi & Network Troubleshooting",
            complaints = listOf("toggle wifi automatically", "turn on mobile data silently", "reset system dns without settings"),
            scope = CapabilityScope.SHARED_STORAGE,
            preconditions = listOf("Check Android Q+ API 29 restrictions on WifiManager.setWifiEnabled"),
            risk = BlueprintRiskLevel.UNSUPPORTED,
            handler = "handler_unsupported_sandbox",
            verifications = listOf("Verify Settings.Panel.ACTION_INTERNET_CONNECTIVITY intent provided"),
            successKey = "bp_wifi_panel_opened",
            failureKey = "bp_wifi_toggle_unsupported",
            actions = listOf("repair_network_reset", "repair_mobile_data"),
            unsupported = "Android 10+ (API 29+) forbids apps from toggling Wi-Fi or Mobile Data radios silently. Use the system Internet Settings Panel.",
            privacy = "None"
        ),
        def(
            id = "BP-04-010", cat = 4, catName = "Internet, Wi-Fi & Network Troubleshooting",
            complaints = listOf("vpn protection dropped", "always on vpn settings", "app blocking paused"),
            scope = CapabilityScope.VPN,
            preconditions = listOf("Check VpnService consent & notify user honestly when VPN tunnel is inactive"),
            risk = BlueprintRiskLevel.CONFIRMATION_REQUIRED,
            handler = "handler_unsupported_sandbox",
            verifications = listOf("Provide honest VPN policy guidance / Settings shortcut without spoofing VPN"),
            successKey = "bp_vpn_settings_guided",
            failureKey = "bp_vpn_not_active",
            actions = listOf("repair_network_reset", "repair_security_privacy"),
            privacy = "Network transport state only"
        )
    )

    // -------------------------------------------------------------------------
    // Category 5 — Security scanning and harmful-download prevention (12 workflows)
    // -------------------------------------------------------------------------
    private val cat5Security = listOf(
        def(
            id = "BP-05-001", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("downloaded apk safe check", "scan apk file", "virus check apk", "kya ye apk safe hai"),
            scope = CapabilityScope.USER_SELECTED_URI,
            preconditions = listOf("Stage user-selected SAF URI safely and compute SHA-256 + magic bytes + PackageInfo"),
            risk = BlueprintRiskLevel.CONFIRMATION_REQUIRED,
            handler = "handler_security_uri_scan",
            verifications = listOf("Verify report contains SHA-256, true MIME, APK signer cert (if APK) & honest verdict"),
            successKey = "bp_apk_scan_complete",
            failureKey = "bp_apk_scan_blocked",
            actions = listOf("repair_security_privacy"),
            privacy = "Analyzed locally on device; hash never linked to user identity"
        ),
        def(
            id = "BP-05-002", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("file flagged as malware", "quarantine dangerous file", "adware file"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Verify SHA-256 matches threat feed before isolating into filesDir/quarantine"),
            risk = BlueprintRiskLevel.CONFIRMATION_REQUIRED,
            handler = "handler_security_quarantine_app_folder",
            verifications = listOf("Confirm original file moved to non-executable .quarantined file with metadata"),
            successKey = "bp_malware_quarantined",
            failureKey = "bp_malware_quarantine_failed",
            actions = listOf("repair_security_privacy"),
            privacy = "File hash & redacted reason only"
        ),
        def(
            id = "BP-05-003", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("downloaded update won't install signature invalid", "apk signer mismatch", "app not installed conflict"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Inspect APK archive signing certificate SHA-256 vs installed package signer"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_verify_update_integrity",
            verifications = listOf("Confirm mismatched or unsigned APK is rejected and incomplete staging file deleted"),
            successKey = "bp_apk_signature_ok",
            failureKey = "bp_apk_signature_mismatch",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "APK certificate digest only"
        ),
        def(
            id = "BP-05-004", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("suspicious pdf wont open", "invoice.pdf.exe", "double extension fake file", "disguised file"),
            scope = CapabilityScope.USER_SELECTED_URI,
            preconditions = listOf("Read magic header bytes and split filename extensions"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_security_disguised_check",
            verifications = listOf("Confirm DANGEROUS_DISGUISED verdict raised on MZ/ELF/double-extension mismatch"),
            successKey = "bp_disguised_file_blocked",
            failureKey = "bp_disguised_check_unresolved",
            actions = listOf("repair_security_privacy"),
            privacy = "First 16 header bytes inspected locally"
        ),
        def(
            id = "BP-05-005", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("tampered download", "checksum mismatch", "sha256 wrong"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Compare downloaded SHA-256 against expected release manifest digest"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_verify_update_integrity",
            verifications = listOf("Confirm tampered file deleted cleanly and error surfaced"),
            successKey = "bp_tampered_file_rejected",
            failureKey = "bp_tamper_check_failed",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "SHA-256 hex digest only"
        ),
        def(
            id = "BP-05-006", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("check phishing link", "weird website link", "punycode link", "suspicious url redirect"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Normalize Unicode hostname via IDN.toASCII and check scheme/spoofing patterns"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_url_safety_check",
            verifications = listOf("Verify punycode/IDN homograph & http:// risks are flagged truthfully"),
            successKey = "bp_url_checked",
            failureKey = "bp_url_suspicious",
            actions = listOf("repair_security_privacy"),
            privacy = "Domain normalized locally; query params stripped"
        ),
        def(
            id = "BP-05-007", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("fake virus warning popup", "unwanted full screen ads", "adware browser notification"),
            scope = CapabilityScope.SHARED_STORAGE,
            preconditions = listOf("Check overlay/notification/device-admin abuse vector under Android sandbox"),
            risk = BlueprintRiskLevel.CONFIRMATION_REQUIRED,
            handler = "handler_adware_triage",
            verifications = listOf("Provide Settings shortcuts (Special App Access / Notification / App Manager) for user removal"),
            successKey = "bp_adware_guidance_ready",
            failureKey = "bp_adware_sandbox_limit",
            actions = listOf("repair_security_privacy", "repair_notifications", "repair_default_apps", "guarded_uninstall_unused"),
            unsupported = "Android sandboxing prevents user apps from silently uninstalling third-party adware or revoking other apps' permissions automatically.",
            privacy = "None"
        ),
        def(
            id = "BP-05-008", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("threat database offline fail", "rules update failed"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Verify digest of new threat feed before replacing last-known-good feed"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_threat_feed_atomic",
            verifications = listOf("Verify invalid/tampered feed rejects cleanly and preserves last-known-good rules"),
            successKey = "bp_threat_feed_verified",
            failureKey = "bp_threat_feed_kept_lkg",
            actions = listOf("auto_refresh_network_socket"),
            privacy = "Signed rule version integer only"
        ),
        def(
            id = "BP-05-009", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("unknown file warning", "weak evidence virus scan"),
            scope = CapabilityScope.USER_SELECTED_URI,
            preconditions = listOf("Ensure unknown hash with missing threat entry is NEVER falsely marked safe"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_security_unknown_honest",
            verifications = listOf("Confirm verdict is SUSPICIOUS_UNKNOWN and original file was NOT auto-deleted"),
            successKey = "bp_unknown_honest_verdict",
            failureKey = "bp_unknown_verdict_failed",
            actions = listOf("repair_security_privacy"),
            privacy = "SHA-256 only"
        ),
        def(
            id = "BP-05-010", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("full security scan interrupted", "resume app folder scan"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Read persisted cursor index in app-owned updates/exports folders"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_resumable_folder_scan",
            verifications = listOf("Verify cursor advances deterministically across calls without losing progress"),
            successKey = "bp_resumable_scan_ok",
            failureKey = "bp_resumable_scan_fail",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Numeric cursor offset only"
        ),
        def(
            id = "BP-05-011", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("scan entire phone storage without permission", "system wide antivirus"),
            scope = CapabilityScope.SHARED_STORAGE,
            preconditions = listOf("Enforce Google Play scoped storage / Photo Picker / SAF policy"),
            risk = BlueprintRiskLevel.UNSUPPORTED,
            handler = "handler_unsupported_sandbox",
            verifications = listOf("Explain honest Android scoped storage restriction and open SAF picker option"),
            successKey = "bp_saf_guided",
            failureKey = "bp_broad_scan_unsupported",
            actions = listOf("repair_security_privacy", "repair_storage_cleaner"),
            unsupported = "Scanning all files across shared storage silently requires restricted MANAGE_EXTERNAL_STORAGE permission. Use SAF/file picker or app-owned folder scan instead.",
            privacy = "None"
        ),
        def(
            id = "BP-05-012", cat = 5, catName = "Security Scanning & Harmful-Download Prevention",
            complaints = listOf("blocked list stops updating", "atomic security rules"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Validate feed JSON structure & digest before atomic SharedPreferences commit"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_threat_feed_atomic",
            verifications = listOf("Verify malformed feed keeps last-known-good rule set intact"),
            successKey = "bp_blocklist_atomic_ok",
            failureKey = "bp_blocklist_atomic_reverted",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Version metadata only"
        )
    )

    // -------------------------------------------------------------------------
    // Category 6 — Privacy, permissions and risky app behavior (8 workflows)
    // -------------------------------------------------------------------------
    private val cat6Privacy = listOf(
        def(
            id = "BP-06-001", cat = 6, catName = "Privacy, Permissions & Risky App Behavior",
            complaints = listOf("why does this app ask extra permissions", "unnecessary permissions audit"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Inspect AndroidManifest requested permissions vs granted status"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_privacy_audit",
            verifications = listOf("Verify zero broad storage/location/SMS permissions declared in manifest"),
            successKey = "bp_minimal_permissions_verified",
            failureKey = "bp_permission_audit_failed",
            actions = listOf("repair_security_privacy"),
            privacy = "Permission grant booleans only"
        ),
        def(
            id = "BP-06-002", cat = 6, catName = "Privacy, Permissions & Risky App Behavior",
            complaints = listOf("protect saved tokens", "encrypt local credentials", "keystore check"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Verify AES/GCM Keystore encryption helper & absence of plaintext secrets"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_privacy_audit",
            verifications = listOf("Confirm secrets never written in plaintext to logs or SharedPreferences"),
            successKey = "bp_keystore_vault_verified",
            failureKey = "bp_keystore_unavailable",
            actions = listOf("repair_security_privacy"),
            privacy = "Encryption self-test only"
        ),
        def(
            id = "BP-06-003", cat = 6, catName = "Privacy, Permissions & Risky App Behavior",
            complaints = listOf("redact sensitive info from logs", "privacy leak in bug report"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Run PrivacyRedactor across diagnostic output strings"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_privacy_redaction_check",
            verifications = listOf("Verify containsSensitiveLeak == false after redaction on sample secrets"),
            successKey = "bp_logs_redacted",
            failureKey = "bp_redaction_leak_found",
            actions = listOf("auto_privacy_retention_cleanup"),
            privacy = "Zero personal data retained"
        ),
        def(
            id = "BP-06-004", cat = 6, catName = "Privacy, Permissions & Risky App Behavior",
            complaints = listOf("signed out previous user data visible", "clear session cache on logout"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Purge session-scoped context & staging caches cleanly"),
            risk = BlueprintRiskLevel.CONFIRMATION_REQUIRED,
            handler = "handler_clear_session_cache",
            verifications = listOf("Verify troubleshooting session key & diagnostic_staging cleared"),
            successKey = "bp_session_isolated",
            failureKey = "bp_session_clear_failed",
            actions = listOf("auto_privacy_retention_cleanup"),
            privacy = "Clears session context"
        ),
        def(
            id = "BP-06-005", cat = 6, catName = "Privacy, Permissions & Risky App Behavior",
            complaints = listOf("turn off analytics tracking", "consent before telemetry"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Verify anonymized metrics only record when consentGranted == true"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_privacy_audit",
            verifications = listOf("Confirm opted-out metrics calls write 0 entries"),
            successKey = "bp_consent_gate_verified",
            failureKey = "bp_consent_gate_failed",
            actions = listOf("repair_security_privacy"),
            privacy = "Boolean consent flag only"
        ),
        def(
            id = "BP-06-006", cat = 6, catName = "Privacy, Permissions & Risky App Behavior",
            complaints = listOf("privacy notice won't load", "offline privacy terms"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check bundled in-app terms summary availability offline"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_privacy_audit",
            verifications = listOf("Confirm local privacy summary displays cleanly even offline"),
            successKey = "bp_privacy_notice_available",
            failureKey = "bp_privacy_notice_missing",
            actions = listOf("repair_security_privacy"),
            privacy = "None"
        ),
        def(
            id = "BP-06-007", cat = 6, catName = "Privacy, Permissions & Risky App Behavior",
            complaints = listOf("permission denied in phone settings crash", "security exception after revoking"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Re-check current runtime & special permission grants dynamically"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_privacy_audit",
            verifications = listOf("Confirm CapabilityEvaluator detects revoked state without SecurityException"),
            successKey = "bp_permission_recheck_safe",
            failureKey = "bp_permission_denied",
            actions = listOf("repair_app_cache", "repair_security_privacy"),
            privacy = "Permission status booleans only"
        ),
        def(
            id = "BP-06-008", cat = 6, catName = "Privacy, Permissions & Risky App Behavior",
            complaints = listOf("delete old temporary diagnostic files", "retention cleanup"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Scan diagnostic_staging, temp_reports & http_cache for files past TTL"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_privacy_retention_purge",
            verifications = listOf("Verify remainingExpiredCount == 0 via PrivacyRedactor"),
            successKey = "bp_retention_purged",
            failureKey = "bp_retention_purge_incomplete",
            actions = listOf("auto_privacy_retention_cleanup", "auto_storage_housekeeping"),
            privacy = "Deleted count & freed bytes only"
        )
    )

    // -------------------------------------------------------------------------
    // Category 7 — App updates, installation workflow & compatibility (7 workflows)
    // -------------------------------------------------------------------------
    private val cat7Updates = listOf(
        def(
            id = "BP-07-001", cat = 7, catName = "App Updates, Installation & Compatibility",
            complaints = listOf("app update failed mid install", "staged update corrupt"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Clean incomplete .part / stale APK artifacts in filesDir/updates"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Verify partial update artifacts removed safely"),
            successKey = "bp_staged_update_cleaned",
            failureKey = "bp_staged_update_stuck",
            actions = listOf("auto_clear_own_cache", "auto_storage_housekeeping"),
            privacy = "File count & bytes only"
        ),
        def(
            id = "BP-07-002", cat = 7, catName = "App Updates, Installation & Compatibility",
            complaints = listOf("update says not enough storage space"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Measure StatFs free bytes & reclaim own-app cache safely without touching user files"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Verify StatFs free bytes and own-app reclaimable bytes cleared"),
            successKey = "bp_update_space_checked",
            failureKey = "bp_update_low_storage",
            actions = listOf("auto_storage_housekeeping", "repair_storage_cleaner"),
            privacy = "Free space bytes only"
        ),
        def(
            id = "BP-07-003", cat = 7, catName = "App Updates, Installation & Compatibility",
            complaints = listOf("package conflict on update", "wrong signing certificate apk"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Verify APK signer cert SHA-256 matches current installed app signer"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_verify_update_integrity",
            verifications = listOf("Confirm mismatched signature update APK is rejected cleanly"),
            successKey = "bp_update_signer_verified",
            failureKey = "bp_update_signer_rejected",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Signer SHA-256 only"
        ),
        def(
            id = "BP-07-004", cat = 7, catName = "App Updates, Installation & Compatibility",
            complaints = listOf("button disappeared after android update", "android version compatibility issue"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Resolve DeviceCompatibilityEngine Android API restrictions & OEM profile"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_compatibility_audit",
            verifications = listOf("Confirm Build.VERSION.SDK_INT routed to verified compatibility profile"),
            successKey = "bp_api_routing_verified",
            failureKey = "bp_api_routing_fallback",
            actions = listOf("repair_settings_search", "repair_system_update"),
            privacy = "Manufacturer, model & API level only"
        ),
        def(
            id = "BP-07-005", cat = 7, catName = "App Updates, Installation & Compatibility",
            complaints = listOf("app broke after os upgrade", "android 15 16 feature blocked"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check minSdk/targetSdk capabilities and safe fallback intents"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_compatibility_audit",
            verifications = listOf("Confirm Intent resolution succeeds with root Settings fallback if needed"),
            successKey = "bp_upgrade_compat_verified",
            failureKey = "bp_upgrade_compat_fallback",
            actions = listOf("repair_system_update", "repair_settings_search"),
            privacy = "API level integer only"
        ),
        def(
            id = "BP-07-006", cat = 7, catName = "App Updates, Installation & Compatibility",
            complaints = listOf("update check stuck spinning"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Verify network socket handshake & bounded update HTTP timeouts"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Verify bounded timeout & clean offline failure state"),
            successKey = "bp_update_check_healthy",
            failureKey = "bp_update_check_offline",
            actions = listOf("auto_refresh_network_socket"),
            privacy = "HTTP status code only"
        ),
        def(
            id = "BP-07-007", cat = 7, catName = "App Updates, Installation & Compatibility",
            complaints = listOf("malformed version metadata", "corrupt update json", "downgrade attack"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Validate versionCode > 0, valid HTTPS URL scheme, valid versionName, valid SHA-256"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_verify_update_integrity",
            verifications = listOf("Confirm malformed or downgrade manifest returns null / Rejection safely"),
            successKey = "bp_manifest_validator_verified",
            failureKey = "bp_manifest_rejected",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "None"
        )
    )

    // -------------------------------------------------------------------------
    // Category 8 — Media, audio, camera & document workflows (8 workflows)
    // -------------------------------------------------------------------------
    private val cat8Media = listOf(
        def(
            id = "BP-08-001", cat = 8, catName = "Media, Audio, Camera & Document Workflows",
            complaints = listOf("photo preview blank in app", "media preview missing"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Inspect own-app thumbnails folder for zero-byte/corrupt previews"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Verify regenerated preview decodes with non-zero dimensions"),
            successKey = "bp_media_preview_rebuilt",
            failureKey = "bp_media_preview_failed",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Thumbnail dimensions only"
        ),
        def(
            id = "BP-08-002", cat = 8, catName = "Media, Audio, Camera & Document Workflows",
            complaints = listOf("photo access revoked", "picker permission lost", "gallery uri expired"),
            scope = CapabilityScope.USER_SELECTED_URI,
            preconditions = listOf("Verify Photo Picker / SAF URI stream cleanly without requesting READ_MEDIA_IMAGES"),
            risk = BlueprintRiskLevel.CONFIRMATION_REQUIRED,
            handler = "handler_security_uri_scan",
            verifications = listOf("Handle SecurityException cleanly and re-prompt Photo Picker when user taps attach"),
            successKey = "bp_picker_uri_safe",
            failureKey = "bp_picker_uri_expired",
            actions = listOf("repair_security_privacy"),
            privacy = "Only user-selected photo URI accessed"
        ),
        def(
            id = "BP-08-003", cat = 8, catName = "Media, Audio, Camera & Document Workflows",
            complaints = listOf("large export upload failed halfway", "chunked media transfer"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check network capability & temporary partial file hygiene"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_network_socket_audit",
            verifications = listOf("Verify online validation and temp cleanup before retry"),
            successKey = "bp_media_chunk_ready",
            failureKey = "bp_media_chunk_offline",
            actions = listOf("auto_refresh_network_socket", "auto_storage_housekeeping"),
            privacy = "Network capability only"
        ),
        def(
            id = "BP-08-004", cat = 8, catName = "Media, Audio, Camera & Document Workflows",
            complaints = listOf("no sound in app", "speaker muted", "audio issue", "awaaz nahi aa rahi", "low volume"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Snapshot AudioManager mode, speakerphone & STREAM_MUSIC volume"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_audio_repair_with_rollback",
            rollback = "handler_rollback_audio_state",
            verifications = listOf("Re-read AudioManager.mode == MODE_NORMAL & verify media volume increased if muted"),
            successKey = "bp_audio_fixed",
            failureKey = "bp_audio_unresolved",
            actions = listOf("auto_fix_audio_output", "repair_sound_levels", "test_audio"),
            privacy = "Audio stream volume integer only"
        ),
        def(
            id = "BP-08-005", cat = 8, catName = "Media, Audio, Camera & Document Workflows",
            complaints = listOf("weird characters in document", "corrupt pdf text", "binary file unreadable"),
            scope = CapabilityScope.USER_SELECTED_URI,
            preconditions = listOf("Check magic header bytes & encoding validation before rendering"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_security_disguised_check",
            verifications = listOf("Verify corrupt/mismatched document blocked safely before crash"),
            successKey = "bp_doc_encoding_checked",
            failureKey = "bp_doc_corrupt",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Magic bytes only"
        ),
        def(
            id = "BP-08-006", cat = 8, catName = "Media, Audio, Camera & Document Workflows",
            complaints = listOf("document thumbnail blank", "pdf page preview corrupted"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check app thumbnail cache integrity"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Confirm regenerated thumbnail decodes cleanly"),
            successKey = "bp_pdf_thumb_fixed",
            failureKey = "bp_pdf_thumb_failed",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "Local decode check only"
        ),
        def(
            id = "BP-08-007", cat = 8, catName = "Media, Audio, Camera & Document Workflows",
            complaints = listOf("empty saved file placeholder", "camera failed leaving blank draft"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Inspect staging/exports for 0-byte leftover draft files"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_housekeeping",
            verifications = listOf("Verify 0-byte incomplete placeholders deleted"),
            successKey = "bp_zero_byte_cleaned",
            failureKey = "bp_zero_byte_remaining",
            actions = listOf("auto_storage_housekeeping"),
            privacy = "File length integer only"
        ),
        def(
            id = "BP-08-008", cat = 8, catName = "Media, Audio, Camera & Document Workflows",
            complaints = listOf("cannot save external media", "storage volume unavailable"),
            scope = CapabilityScope.USER_SELECTED_URI,
            preconditions = listOf("Verify Environment external volume state before write"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_storage_volume_check",
            verifications = listOf("Confirm writable state or safe internal fallback"),
            successKey = "bp_media_volume_ok",
            failureKey = "bp_media_volume_readonly",
            actions = listOf("repair_storage_cleaner"),
            privacy = "Volume state string only"
        )
    )

    // -------------------------------------------------------------------------
    // Category 9 — Notifications, scheduled tasks & automation (7 workflows)
    // -------------------------------------------------------------------------
    private val cat9Notifications = listOf(
        def(
            id = "BP-09-001", cat = 9, catName = "Notifications, Scheduled Tasks & App Automation",
            complaints = listOf("duplicate notification alerts", "too many notifications from this app"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Use stable fixed notification IDs instead of random UUID IDs"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_notification_audit",
            verifications = listOf("Verify STABLE_SUMMARY_NOTIFICATION_ID & STABLE_PROGRESS_NOTIFICATION_ID constants active"),
            successKey = "bp_notif_dedup_ok",
            failureKey = "bp_notif_blocked",
            actions = listOf("repair_notifications"),
            privacy = "Notification permission boolean only"
        ),
        def(
            id = "BP-09-002", cat = 9, catName = "Notifications, Scheduled Tasks & App Automation",
            complaints = listOf("progress notification stuck after completion", "notification wont clear"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Invoke NotificationManagerCompat.cancel(STABLE_PROGRESS_NOTIFICATION_ID)"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_cancel_stale_notification",
            verifications = listOf("Confirm cancel call succeeds on completion"),
            successKey = "bp_stale_notif_cancelled",
            failureKey = "bp_stale_notif_failed",
            actions = listOf("auto_schedule_maintenance"),
            privacy = "None"
        ),
        def(
            id = "BP-09-003", cat = 9, catName = "Notifications, Scheduled Tasks & App Automation",
            complaints = listOf("scheduled backup maintenance did not run", "notifications delayed in doze"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Inspect WorkManager status & Battery Optimization exemptions safely"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_schedule_maintenance",
            verifications = listOf("Confirm WorkManager job enqueued cleanly and link battery optimization Settings"),
            successKey = "bp_workmanager_audited",
            failureKey = "bp_workmanager_unscheduled",
            actions = listOf("auto_schedule_maintenance", "repair_battery_saver"),
            privacy = "WorkInfo state only"
        ),
        def(
            id = "BP-09-004", cat = 9, catName = "Notifications, Scheduled Tasks & App Automation",
            complaints = listOf("maintenance slows down phone during use", "schedule idle cleanup"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Apply battery-not-low WorkManager constraint"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_schedule_maintenance",
            verifications = listOf("Verify requiresBatteryNotLow == true on periodic maintenance worker"),
            successKey = "bp_idle_cleanup_scheduled",
            failureKey = "bp_idle_cleanup_failed",
            actions = listOf("auto_schedule_maintenance"),
            privacy = "Constraint flags only"
        ),
        def(
            id = "BP-09-005", cat = 9, catName = "Notifications, Scheduled Tasks & App Automation",
            complaints = listOf("app keeps asking notification permission", "notification permission denied"),
            scope = CapabilityScope.OWN_APP,
            apiRange = 33..36,
            requiredPerms = listOf("android.permission.POST_NOTIFICATIONS (API 33+)"),
            preconditions = listOf("Check POST_NOTIFICATIONS cleanly; never force repeat popups if denied"),
            risk = BlueprintRiskLevel.CONFIRMATION_REQUIRED,
            handler = "handler_notification_audit",
            verifications = listOf("Verify graceful fallback to in-app UI banner & optional Settings link"),
            successKey = "bp_notif_perm_checked",
            failureKey = "bp_notif_perm_denied",
            actions = listOf("repair_notifications"),
            privacy = "Permission grant boolean only"
        ),
        def(
            id = "BP-09-006", cat = 9, catName = "Notifications, Scheduled Tasks & App Automation",
            complaints = listOf("tapping notification opens wrong screen", "notification tap broken"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Check PendingIntent FLAG_IMMUTABLE + explicit MainActivity destination"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_notification_audit",
            verifications = listOf("Confirm PendingIntent targets MainActivity with explicit extra routing"),
            successKey = "bp_pending_intent_ok",
            failureKey = "bp_pending_intent_fail",
            actions = listOf("repair_notifications"),
            privacy = "None"
        ),
        def(
            id = "BP-09-007", cat = 9, catName = "Notifications, Scheduled Tasks & App Automation",
            complaints = listOf("periodic tasks duplicate after restart", "post reboot duplicate work"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Use WorkManager.enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP)"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_schedule_maintenance",
            verifications = listOf("Verify workInfosForUniqueWork has at most 1 active periodic entry"),
            successKey = "bp_unique_periodic_ok",
            failureKey = "bp_unique_periodic_fail",
            actions = listOf("auto_schedule_maintenance"),
            privacy = "Worker count integer only"
        )
    )

    // -------------------------------------------------------------------------
    // Category 10 — AI assistant, diagnostics & repair engine (10 workflows)
    // -------------------------------------------------------------------------
    private val cat10AiEngine = listOf(
        def(
            id = "BP-10-001", cat = 10, catName = "AI Assistant, Diagnostics & Repair Engine",
            complaints = listOf("hinglish hindi punjabi problem", "mixed language phone problem"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Map multilingual phrasing to canonical BP-XX-YYY & AND-XX-YYY IDs"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_offline_kb_integrity",
            verifications = listOf("Confirm canonical issue ID resolved regardless of EN/HI/Hinglish wording"),
            successKey = "bp_multilingual_mapped",
            failureKey = "bp_multilingual_ambiguous",
            actions = listOf("auto_optimize_memory"),
            privacy = "Query processed locally when offline"
        ),
        def(
            id = "BP-10-002", cat = 10, catName = "AI Assistant, Diagnostics & Repair Engine",
            complaints = listOf("ai gave text instructions instead of fixing", "auto fix this directly"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Route diagnosable canonical issue to native Kotlin handler first"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_memory_and_cache",
            verifications = listOf("Confirm native handler executed and postcondition check ran"),
            successKey = "bp_native_handler_executed",
            failureKey = "bp_native_handler_failed",
            actions = listOf("auto_clear_own_cache", "auto_fix_audio_output", "auto_refresh_network_socket"),
            privacy = "Telemetry snapshot only"
        ),
        def(
            id = "BP-10-003", cat = 10, catName = "AI Assistant, Diagnostics & Repair Engine",
            complaints = listOf("app said fixed but problem still happens", "false fixed claim"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Enforce independent postcondition test before allowing FIXED status"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_verification_gate_check",
            verifications = listOf("Verify verification.passed == false forces UNRESOLVED instead of FIXED"),
            successKey = "bp_truthful_verification_ok",
            failureKey = "bp_verification_blocked_false_fix",
            actions = listOf("auto_audit_thermal_power"),
            privacy = "Outcome metric only"
        ),
        def(
            id = "BP-10-004", cat = 10, catName = "AI Assistant, Diagnostics & Repair Engine",
            complaints = listOf("ai asks unnecessary questions", "no question policy when obvious"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Run read-only diagnostics automatically before emitting any questions"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_no_question_auto_diag",
            verifications = listOf("Verify askedClarifyingQuestions == false when live signal diagnoses fault"),
            successKey = "bp_auto_diag_no_questions",
            failureKey = "bp_clarification_genuinely_needed",
            actions = listOf("auto_optimize_memory", "auto_refresh_network_socket", "auto_fix_audio_output"),
            privacy = "Verified device signals only"
        ),
        def(
            id = "BP-10-005", cat = 10, catName = "AI Assistant, Diagnostics & Repair Engine",
            complaints = listOf("destructive repair safety block", "protect photos contacts personal files"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Block CONFIRMATION_REQUIRED / destructive handler when consentConfirmed == false"),
            risk = BlueprintRiskLevel.CONFIRMATION_REQUIRED,
            handler = "handler_destructive_guard",
            verifications = listOf("Verify pipeline stops at NEEDS_APPROVAL without user confirmation"),
            successKey = "bp_destructive_confirmed",
            failureKey = "bp_destructive_blocked_safely",
            actions = listOf("guarded_clear_app_data", "guarded_uninstall_unused", "guarded_reset_network"),
            privacy = "Consent boolean only"
        ),
        def(
            id = "BP-10-006", cat = 10, catName = "AI Assistant, Diagnostics & Repair Engine",
            complaints = listOf("stop repeating already healthy repair", "idempotent repair check"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Detect already-healthy target state via precondition check before mutating"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_idempotent_health_check",
            verifications = listOf("Confirm idempotent skip triggered with zero redundant mutation"),
            successKey = "bp_already_healthy_verified",
            failureKey = "bp_needed_repair",
            actions = listOf("auto_clear_own_cache"),
            privacy = "None"
        ),
        def(
            id = "BP-10-007", cat = 10, catName = "AI Assistant, Diagnostics & Repair Engine",
            complaints = listOf("verification unavailable cannot confirm fix"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Treat missing/unavailable postcondition telemetry strictly as UNRESOLVED"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_verification_gate_check",
            verifications = listOf("Confirm missing telemetry emits UNRESOLVED"),
            successKey = "bp_verification_passed",
            failureKey = "bp_verification_unavailable_unresolved",
            actions = listOf("auto_audit_thermal_power"),
            privacy = "Collector availability boolean only"
        ),
        def(
            id = "BP-10-008", cat = 10, catName = "AI Assistant, Diagnostics & Repair Engine",
            complaints = listOf("strip secrets from diagnostic telemetry", "redact crash logs"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Run PrivacyRedactor before persistence/upload"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_privacy_redaction_check",
            verifications = listOf("Confirm zero tokens/emails/paths remain in redacted output"),
            successKey = "bp_telemetry_redacted",
            failureKey = "bp_telemetry_leak",
            actions = listOf("auto_privacy_retention_cleanup"),
            privacy = "Only anonymized issue ID & outcome recorded"
        ),
        def(
            id = "BP-10-009", cat = 10, catName = "AI Assistant, Diagnostics & Repair Engine",
            complaints = listOf("handler crash rollback", "recover failed repair cleanly"),
            scope = CapabilityScope.OWN_APP,
            preconditions = listOf("Isolate handler in try/catch and invoke rollbackHandlerId on error"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_rollback_audio_state",
            rollback = "handler_rollback_audio_state",
            verifications = listOf("Confirm pre-action snapshot restored cleanly after simulated exception"),
            successKey = "bp_rollback_recovered",
            failureKey = "bp_rollback_unresolved",
            actions = listOf("auto_fix_audio_output"),
            privacy = "Error class name redacted"
        ),
        def(
            id = "BP-10-010", cat = 10, catName = "AI Assistant, Diagnostics & Repair Engine",
            complaints = listOf("undo repair change", "restore previous setting snapshot"),
            scope = CapabilityScope.OWN_APP,
            requiredPerms = listOf("android.permission.WRITE_SETTINGS (optional)"),
            preconditions = listOf("Save pre-action audio/display snapshot and restore via rollback handler on request"),
            risk = BlueprintRiskLevel.SAFE_REVERSIBLE,
            handler = "handler_rollback_display_settings",
            rollback = "handler_rollback_display_settings",
            verifications = listOf("Verify restored settings match pre-action snapshot exactly"),
            successKey = "bp_snapshot_restored",
            failureKey = "bp_snapshot_restore_failed",
            actions = listOf("auto_optimize_display_power", "auto_fix_audio_output"),
            privacy = "Previous timeout/volume integer only"
        )
    )

    private fun def(
        id: String,
        cat: Int,
        catName: String,
        complaints: List<String>,
        scope: CapabilityScope,
        apiRange: IntRange = 24..36,
        requiredPerms: List<String> = emptyList(),
        preconditions: List<String>,
        risk: BlueprintRiskLevel,
        handler: String,
        rollback: String? = null,
        verifications: List<String>,
        successKey: String,
        failureKey: String,
        actions: List<String>,
        unsupported: String? = null,
        privacy: String
    ) = RepairDefinition(
        issueId = id,
        categoryNumber = cat,
        categoryName = catName,
        complaintExamples = complaints,
        supportedAndroidApi = apiRange,
        requiredPermissions = requiredPerms,
        capabilityScope = scope,
        preconditionChecks = preconditions,
        risk = risk,
        actionHandlerId = handler,
        rollbackHandlerId = rollback,
        verificationChecks = verifications,
        successMessageKey = successKey,
        failureMessageKey = failureKey,
        maxAttempts = 2,
        privacyDataCollected = privacy,
        mappedDoctorActionIds = actions,
        unsupportedReason = unsupported,
        skipsQuestionWhenDiagnosable = risk != BlueprintRiskLevel.CONFIRMATION_REQUIRED
    )
}
