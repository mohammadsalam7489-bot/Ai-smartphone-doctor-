package com.example.data.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.data.privacy.PrivacyRedactor
import com.example.data.storage.SafeStorageManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.IDN
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

enum class ScanVerdict(val label: String) {
    VERIFIED_CLEAN("Verified Clean"),
    SUSPICIOUS_UNKNOWN("Suspicious / Unknown Evidence"),
    DANGEROUS_DISGUISED("Dangerous / Disguised File Type"),
    MALWARE_HASH_MATCH("Malware Threat Hash Match"),
    INVALID_APK_SIGNER("Invalid / Mismatched APK Signer")
}

data class FileSecurityReport(
    val fileName: String,
    val trueMimeType: String,
    val declaredExtension: String,
    val sha256: String,
    val sizeBytes: Long,
    val verdict: ScanVerdict,
    val findings: List<String>,
    val isApk: Boolean = false,
    val apkPackageName: String? = null,
    val apkSignerSha256: String? = null,
    val apkDangerousPermissions: List<String> = emptyList(),
    val quarantinedFilePath: String? = null
)

data class UrlSafetyReport(
    val normalizedHost: String,
    val isSuspicious: Boolean,
    val reasons: List<String>
)

data class ThreatFeedUpdateResult(
    val accepted: Boolean,
    val activeVersion: Int,
    val ruleCount: Int,
    val keptLastKnownGood: Boolean,
    val reason: String
)

data class FolderScanProgress(
    val cursorIndex: Int,
    val totalFiles: Int,
    val reports: List<FileSecurityReport>,
    val completed: Boolean
)

/**
 * Play-Policy-Compliant Security & Harmful Download Scanner (Blueprint Categories 2, 4, 5 & 7).
 * - Operates ONLY on user-selected SAF URIs and app-owned folders (never requests MANAGE_EXTERNAL_STORAGE).
 * - Classifies weak/missing evidence honestly as SUSPICIOUS_UNKNOWN (never claims "Safe").
 * - Never auto-deletes user files without explicit consent; moves flagged files only into isolated app quarantine.
 */
object AppSecurityScanner {

    private const val PREF_SECURITY = "ai_doctor_security_feed"
    private const val KEY_FEED_JSON = "last_known_good_feed_json"
    private const val KEY_FEED_VER = "last_known_good_feed_version"
    private const val KEY_SCAN_CURSOR = "app_folder_scan_cursor"

    // EICAR standard test vector SHA-256 + known malicious test samples for verification & detection
    private val builtInMaliciousHashes: Map<String, String> = mapOf(
        "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f" to "EICAR-Standard-Test-Signature",
        "deadbeef00000000000000000000000000000000000000000000000000000bad" to "Demo-Blocked-Trojan-Sample"
    )

    private val executableExtensions = setOf("exe", "bat", "cmd", "sh", "elf", "msi", "scr", "vbs", "js", "jar", "apk", "dex")
    private val documentImageExtensions = setOf("pdf", "jpg", "jpeg", "png", "webp", "txt", "doc", "docx", "csv")

    private val suspiciousPermissions = setOf(
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.READ_CALL_LOG",
        "android.permission.BIND_DEVICE_ADMIN"
    )

    /**
     * Cat 2 (#5) & Cat 5 (#4): Detects true MIME from file header magic bytes and flags double extensions
     * or mismatches (e.g. `invoice.pdf.exe`, `photo.jpg` with PE/ELF/ZIP-APK bytes).
     */
    fun detectTrueMimeAndHeader(file: File): Pair<String, String> {
        val header = ByteArray(16)
        val readCount = runCatching {
            FileInputStream(file).use { it.read(header) }
        }.getOrDefault(0)
        if (readCount <= 0) return "application/octet-stream" to "EMPTY"

        return when {
            readCount >= 2 && header[0] == 'M'.code.toByte() && header[1] == 'Z'.code.toByte() ->
                "application/x-dosexec" to "WINDOWS_PE_EXECUTABLE"
            readCount >= 4 && header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() && header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() ->
                "application/x-executable" to "ELF_BINARY"
            readCount >= 4 && header[0] == 'd'.code.toByte() && header[1] == 'e'.code.toByte() && header[2] == 'x'.code.toByte() && header[3] == '\n'.code.toByte() ->
                "application/vnd.android.dex" to "DALVIK_DEX"
            readCount >= 4 && header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte() ->
                "application/pdf" to "PDF_DOCUMENT"
            readCount >= 4 && header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte() ->
                "image/png" to "PNG_IMAGE"
            readCount >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() ->
                "image/jpeg" to "JPEG_IMAGE"
            readCount >= 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte() ->
                "application/zip" to "ZIP_OR_APK_CONTAINER"
            readCount >= 5 && String(header, 0, minOf(readCount, 12), Charsets.US_ASCII).startsWith("EICAR") ->
                "text/x-eicar" to "EICAR_TEST"
            else -> "application/octet-stream" to "UNKNOWN_BINARY"
        }
    }

    fun hasDoubleExtensionAttack(fileName: String): Boolean {
        val parts = fileName.lowercase().split('.').filter { it.isNotBlank() }
        if (parts.size < 3) return false
        val finalExt = parts.last()
        val disguisedExt = parts[parts.size - 2]
        return disguisedExt in documentImageExtensions && finalExt in executableExtensions
    }

    /**
     * Scans a file staged inside the app or selected via SAF URI cleanly and safely.
     */
    fun scanFileSafely(
        context: Context,
        file: File,
        originalFileName: String = file.name,
        expectedSignerSha256: String? = null,
        knownCleanHashes: Set<String> = emptySet(),
        extraThreatHashes: Map<String, String> = emptyMap()
    ): FileSecurityReport {
        val findings = mutableListOf<String>()
        val sha256 = computeSha256(file).orEmpty()
        val ext = originalFileName.substringAfterLast('.', "").lowercase()
        val (trueMime, headerTag) = detectTrueMimeAndHeader(file)

        // 1. Known malicious hash check (built-in + verified signed feed)
        val combinedThreatMap = builtInMaliciousHashes + loadPersistedThreatHashes(context) + extraThreatHashes
        val matchedThreatName = combinedThreatMap[sha256.lowercase()]
        if (matchedThreatName != null) {
            findings.add("SHA-256 matches known threat signature: $matchedThreatName")
        }

        // 2. Double extension or magic byte spoofing check
        val doubleExt = hasDoubleExtensionAttack(originalFileName)
        if (doubleExt) {
            findings.add("Double extension detected ('$originalFileName'): disguises executable extension '$ext'.")
        }
        val spoofedPdfOrImage = ext in documentImageExtensions && headerTag in setOf(
            "WINDOWS_PE_EXECUTABLE",
            "ELF_BINARY",
            "DALVIK_DEX",
            "ZIP_OR_APK_CONTAINER"
        )
        if (spoofedPdfOrImage) {
            findings.add("File extension '.$ext' mismatches true binary header ($headerTag / $trueMime).")
        }

        // 3. APK package metadata + signing certificate inspection when relevant
        val isApkCandidate = ext == "apk" || headerTag == "ZIP_OR_APK_CONTAINER"
        var pkgName: String? = null
        var signerSha: String? = null
        var riskyPerms: List<String> = emptyList()
        var signerMismatch = false

        if (isApkCandidate && ext == "apk") {
            val archiveInfo = readPackageArchiveInfo(context, file)
            if (archiveInfo != null) {
                pkgName = archiveInfo.packageName
                signerSha = extractSigningCertSha256(archiveInfo)
                riskyPerms = archiveInfo.requestedPermissions
                    ?.filter { it in suspiciousPermissions }
                    .orEmpty()
                if (riskyPerms.isNotEmpty()) {
                    findings.add("APK requests sensitive permissions: ${riskyPerms.joinToString()}")
                }
                if (expectedSignerSha256 != null && signerSha != null && !signerSha.equals(expectedSignerSha256, ignoreCase = true)) {
                    signerMismatch = true
                    findings.add("APK signing certificate mismatch! Expected $expectedSignerSha256, got $signerSha.")
                }
                if (expectedSignerSha256 != null && signerSha == null) {
                    signerMismatch = true
                    findings.add("APK has no readable signing certificate (unsigned or tampered archive).")
                }
            } else if (file.length() > 0L) {
                findings.add("APK metadata could not be parsed cleanly (corrupted or malformed manifest).")
            }
        }

        val verdict = when {
            matchedThreatName != null -> ScanVerdict.MALWARE_HASH_MATCH
            signerMismatch -> ScanVerdict.INVALID_APK_SIGNER
            doubleExt || spoofedPdfOrImage || headerTag in setOf("WINDOWS_PE_EXECUTABLE", "ELF_BINARY", "DALVIK_DEX") ->
                ScanVerdict.DANGEROUS_DISGUISED
            sha256 in knownCleanHashes && findings.isEmpty() -> ScanVerdict.VERIFIED_CLEAN
            findings.isNotEmpty() -> ScanVerdict.SUSPICIOUS_UNKNOWN
            else -> {
                findings.add("No threat feed match, but hash is unverified in local allowlist — classified conservatively as Unknown/Unverified.")
                ScanVerdict.SUSPICIOUS_UNKNOWN
            }
        }

        return FileSecurityReport(
            fileName = originalFileName,
            trueMimeType = trueMime,
            declaredExtension = ext,
            sha256 = sha256,
            sizeBytes = file.length(),
            verdict = verdict,
            findings = findings,
            isApk = ext == "apk",
            apkPackageName = pkgName,
            apkSignerSha256 = signerSha,
            apkDangerousPermissions = riskyPerms
        )
    }

    /**
     * Stages a user-selected SAF URI first and runs scanFileSafely without requesting broad storage permissions.
     */
    fun scanUserSelectedUri(
        context: Context,
        uri: Uri,
        displayNameHint: String = "selected_document"
    ): Result<FileSecurityReport> {
        val staged = SafeStorageManager.stageUserSelectedUriSafely(context, uri, displayNameHint)
        if (!staged.success || staged.stagedFile == null) {
            return Result.failure(IllegalStateException(staged.errorReason ?: "Could not access selected URI."))
        }
        return try {
            val report = scanFileSafely(context, staged.stagedFile, displayNameHint)
            Result.success(report)
        } finally {
            runCatching { staged.stagedFile.delete() }
        }
    }

    /**
     * Cat 5 (#11): Moves a dangerous/quarantined app-owned file into isolated non-executable storage (`filesDir/quarantine`)
     * with redacted metadata manifest so it cannot be launched accidentally.
     */
    fun quarantineFileSafely(context: Context, file: File, reason: String): File? {
        if (!file.exists() || !file.isFile) return null
        val quarantineDir = File(context.filesDir, "quarantine").apply { if (!exists()) mkdirs() }
        val quarantineId = UUID.randomUUID().toString()
        val quarantinedTarget = File(quarantineDir, "$quarantineId.quarantined")
        return runCatching {
            file.copyTo(quarantinedTarget, overwrite = true)
            quarantinedTarget.setExecutable(false, false)
            file.delete()
            val meta = JSONObject().apply {
                put("originalName", file.name)
                put("sha256", computeSha256(quarantinedTarget).orEmpty())
                put("reason", PrivacyRedactor.redactSensitiveData(reason))
                put("quarantinedAtMs", System.currentTimeMillis())
            }
            SafeStorageManager.writeFileAtomicallyWithBackup(
                File(quarantineDir, "$quarantineId.meta.json"),
                meta.toString().toByteArray(Charsets.UTF_8)
            )
            quarantinedTarget
        }.getOrNull()
    }

    /**
     * Cat 4 (#9) & Cat 5 (#8 & #12): Validates signed threat feed update payload atomically and
     * preserves last-known-good feed if new payload is tampered or malformed.
     */
    fun updateThreatFeedAtomically(
        context: Context,
        feedJson: String,
        expectedDigestHex: String
    ): ThreatFeedUpdateResult {
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt(KEY_FEED_VER, 1)

        val actualDigest = PrivacyRedactor.sha256Hex(feedJson.toByteArray(Charsets.UTF_8))
        if (expectedDigestHex.isBlank() || !actualDigest.equals(expectedDigestHex, ignoreCase = true)) {
            return ThreatFeedUpdateResult(
                accepted = false,
                activeVersion = currentVersion,
                ruleCount = loadPersistedThreatHashes(context).size + builtInMaliciousHashes.size,
                keptLastKnownGood = true,
                reason = "Signature/checksum mismatch on incoming threat feed; kept last-known-good rules."
            )
        }

        return runCatching {
            val obj = JSONObject(feedJson)
            val version = obj.optInt("version", -1)
            val hashesObj = obj.optJSONObject("maliciousHashes") ?: JSONObject()
            if (version <= 0 || hashesObj.length() == 0) {
                return@runCatching ThreatFeedUpdateResult(
                    accepted = false,
                    activeVersion = currentVersion,
                    ruleCount = loadPersistedThreatHashes(context).size + builtInMaliciousHashes.size,
                    keptLastKnownGood = true,
                    reason = "Malformed threat feed metadata; retained last-known-good rules."
                )
            }
            prefs.edit()
                .putString(KEY_FEED_JSON, feedJson)
                .putInt(KEY_FEED_VER, version)
                .apply()
            ThreatFeedUpdateResult(
                accepted = true,
                activeVersion = version,
                ruleCount = hashesObj.length() + builtInMaliciousHashes.size,
                keptLastKnownGood = false,
                reason = "Activated verified threat feed v$version atomically."
            )
        }.getOrElse {
            ThreatFeedUpdateResult(
                accepted = false,
                activeVersion = currentVersion,
                ruleCount = loadPersistedThreatHashes(context).size + builtInMaliciousHashes.size,
                keptLastKnownGood = true,
                reason = "JSON parse error in threat feed; kept last-known-good rules."
            )
        }
    }

    /**
     * Cat 5 (#6): Normalizes Unicode/IDN domains to Punycode ASCII and detects homograph phishing or insecure APK URLs.
     */
    fun inspectUrlSafety(rawUrl: String): UrlSafetyReport {
        val trimmed = rawUrl.trim()
        val reasons = mutableListOf<String>()
        val javaUri = runCatching { URI(trimmed) }.getOrNull()
        val androidUri = runCatching { Uri.parse(trimmed) }.getOrNull()
        val scheme = (javaUri?.scheme ?: androidUri?.scheme ?: trimmed.substringBefore("://", ""))
            .lowercase()
        val rawHost = javaUri?.host?.takeIf { it.isNotBlank() }
            ?: androidUri?.host?.takeIf { it.isNotBlank() }
            ?: trimmed.substringAfter("://", "")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .substringAfterLast("@")
                .substringBefore(":")
        val asciiHost = runCatching { IDN.toASCII(rawHost) }.getOrDefault(rawHost).lowercase()

        if (scheme == "http" && !asciiHost.startsWith("127.0.0.") && asciiHost != "localhost") {
            reasons.add("Insecure cleartext HTTP transport (must use HTTPS for software/APK links).")
        }
        if (asciiHost.contains("xn--")) {
            reasons.add("IDN/Punycode homograph domain detected ('$asciiHost'); verify visual lookalike spoofing.")
        }
        if (rawHost.any { it.code > 127 }) {
            reasons.add("Non-ASCII Unicode characters present in domain hostname.")
        }
        if (trimmed.contains("@") && scheme.startsWith("http")) {
            reasons.add("Embedded '@' userinfo trick detected in HTTP URL.")
        }
        return UrlSafetyReport(
            normalizedHost = asciiHost.ifBlank { trimmed },
            isSuspicious = reasons.isNotEmpty(),
            reasons = reasons
        )
    }

    /**
     * Cat 5 (#10): Resumable app-owned folder scan with persisted cursor across calls.
     */
    fun scanAppOwnedFoldersStep(context: Context, batchSize: Int = 5): FolderScanProgress {
        val dirs = listOf(
            File(context.filesDir, "updates"),
            File(context.filesDir, "exports"),
            File(context.filesDir, "diagnostic_staging")
        )
        val allFiles = dirs.flatMap { dir ->
            if (!dir.exists()) emptyList()
            else dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
        }
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        val cursor = prefs.getInt(KEY_SCAN_CURSOR, 0).coerceIn(0, allFiles.size)

        if (allFiles.isEmpty()) {
            prefs.edit().putInt(KEY_SCAN_CURSOR, 0).apply()
            return FolderScanProgress(0, 0, emptyList(), completed = true)
        }

        val endIdx = (cursor + batchSize).coerceAtMost(allFiles.size)
        val slice = allFiles.subList(cursor, endIdx)
        val reports = slice.map { f -> scanFileSafely(context, f, f.name) }
        val completed = endIdx >= allFiles.size
        prefs.edit().putInt(KEY_SCAN_CURSOR, if (completed) 0 else endIdx).apply()

        return FolderScanProgress(
            cursorIndex = if (completed) 0 else endIdx,
            totalFiles = allFiles.size,
            reports = reports,
            completed = completed
        )
    }

    fun getCurrentAppSigningCertSha256(context: Context): String? {
        return runCatching {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            extractSigningCertSha256(pkgInfo)
        }.getOrNull()
    }

    private fun readPackageArchiveInfo(context: Context, apkFile: File): PackageInfo? {
        return runCatching {
            val flags = PackageManager.GET_PERMISSIONS or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }
        }.getOrNull()
    }

    private fun extractSigningCertSha256(pkgInfo: PackageInfo): String? {
        val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            val signers = signingInfo?.apkContentsSigners ?: signingInfo?.signingCertificateHistory
            signers?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.firstOrNull()?.toByteArray()
        }
        return certBytes?.let { PrivacyRedactor.sha256Hex(it) }
    }

    private fun loadPersistedThreatHashes(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_FEED_JSON, null) ?: return emptyMap()
        return runCatching {
            val hashes = JSONObject(raw).optJSONObject("maliciousHashes") ?: return@runCatching emptyMap()
            buildMap {
                val keys = hashes.keys()
                while (keys.hasNext()) {
                    val k = keys.next().lowercase()
                    put(k, hashes.optString(k, "Persisted-Feed-Threat"))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun computeSha256(file: File): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buf = ByteArray(16 * 1024)
                var read = fis.read(buf)
                while (read != -1) {
                    digest.update(buf, 0, read)
                    read = fis.read(buf)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
}
