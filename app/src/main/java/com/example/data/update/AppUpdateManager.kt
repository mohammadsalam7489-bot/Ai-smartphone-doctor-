package com.example.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String = "",
    val fileSizeBytes: Long = 0L,
    val releaseNotes: String = "",
    val releaseNotesHi: String = "",
    val publishedAt: String = "",
    val mandatory: Boolean = false
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(
        val currentVersionCode: Long,
        val currentVersionName: String
    ) : UpdateState

    data class UpdateAvailable(
        val manifest: UpdateManifest,
        val currentVersionCode: Long,
        val currentVersionName: String
    ) : UpdateState

    data class Downloading(
        val manifest: UpdateManifest,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateState

    data class ReadyToInstall(
        val manifest: UpdateManifest,
        val apkFile: File
    ) : UpdateState

    data class NeedsInstallPermission(
        val manifest: UpdateManifest,
        val apkFile: File
    ) : UpdateState

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val lastManifest: UpdateManifest? = null
    ) : UpdateState
}

sealed interface UpdateCheckResult {
    data class Available(
        val manifest: UpdateManifest,
        val currentVersionCode: Long,
        val currentVersionName: String
    ) : UpdateCheckResult

    data class UpToDate(
        val currentVersionCode: Long,
        val currentVersionName: String
    ) : UpdateCheckResult

    data class Failure(val errorMessage: String) : UpdateCheckResult
}

sealed interface ApkDownloadResult {
    data class Success(val apkFile: File, val sha256: String) : ApkDownloadResult
    data class Failure(val errorMessage: String) : ApkDownloadResult
}

object AppUpdateManager {

    const val NOTIFICATION_CHANNEL_ID = "app_updates_channel"
    const val NOTIFICATION_ID = 7001
    const val EXTRA_OPEN_UPDATE_DIALOG = "open_update_dialog"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getCurrentVersionInfo(context: Context): Pair<Long, String> {
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val code = PackageInfoCompat.getLongVersionCode(pkgInfo)
            val name = pkgInfo.versionName ?: BuildConfig.VERSION_NAME
            code to name
        } catch (_: Exception) {
            BuildConfig.VERSION_CODE.toLong() to BuildConfig.VERSION_NAME
        }
    }

    fun isUpdateAvailable(remoteVersionCode: Long, localVersionCode: Long): Boolean {
        return remoteVersionCode > localVersionCode
    }

    internal fun parseUpdateManifest(jsonString: String): UpdateManifest? {
        return try {
            val obj = JSONObject(jsonString)
            val versionCode = obj.optLong("versionCode", -1L)
            val versionName = obj.optString("versionName").trim()
            val apkUrl = obj.optString("apkUrl").trim()
            if (versionCode <= 0L || apkUrl.isBlank()) return null

            UpdateManifest(
                versionCode = versionCode,
                versionName = versionName.ifBlank { versionCode.toString() },
                apkUrl = apkUrl,
                sha256 = obj.optString("sha256", "").trim().lowercase(),
                fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                releaseNotes = obj.optString("releaseNotes", "Bug fixes, stability improvements, and diagnostic updates.").trim(),
                releaseNotesHi = obj.optString("releaseNotesHi", "बग सुधार, बेहतर प्रदर्शन और नए निदान अपडेट।").trim(),
                publishedAt = obj.optString("publishedAt", "").trim(),
                mandatory = obj.optBoolean("mandatory", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseGitHubReleaseFallback(jsonString: String): UpdateManifest? {
        return try {
            val obj = JSONObject(jsonString)
            val tagName = obj.optString("tag_name", "").trim()
            val body = obj.optString("body", "").trim()
            val publishedAt = obj.optString("published_at", "").trim()
            val assets = obj.optJSONArray("assets") ?: return null

            var apkDownloadUrl = ""
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
            if (apkDownloadUrl.isBlank()) return null

            // Derive versionCode from tag (e.g. v1.1.42 or build-42)
            val nums = Regex("\\d+").findAll(tagName).map { it.value.toLongOrNull() ?: 0L }.toList()
            val derivedCode = when {
                nums.size >= 3 -> nums.last()
                nums.isNotEmpty() -> nums.last()
                else -> 0L
            }
            if (derivedCode <= 0L) return null

            UpdateManifest(
                versionCode = derivedCode,
                versionName = tagName.removePrefix("v").ifBlank { derivedCode.toString() },
                apkUrl = apkDownloadUrl,
                sha256 = "",
                fileSizeBytes = apkSize,
                releaseNotes = body.ifBlank { "New GitHub automated release build $tagName available." },
                releaseNotesHi = body.ifBlank { "नया स्वचालित ऐप अपडेट $tagName उपलब्ध है।" },
                publishedAt = publishedAt,
                mandatory = false
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun checkForUpdate(
        context: Context,
        manifestUrl: String = BuildConfig.UPDATE_MANIFEST_URL,
        releasesApiUrl: String = BuildConfig.GITHUB_RELEASES_API_URL
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val (localCode, localName) = getCurrentVersionInfo(context)
        try {
            // 1. Try primary manifest JSON from latest release endpoint
            val manifestReq = Request.Builder()
                .url(manifestUrl)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()

            var remoteManifest: UpdateManifest? = null
            var lastHttpError: String? = null

            runCatching {
                client.newCall(manifestReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val raw = resp.body?.string().orEmpty()
                        remoteManifest = parseUpdateManifest(raw)
                    } else {
                        lastHttpError = "Manifest HTTP ${resp.code}"
                    }
                }
            }.onFailure { lastHttpError = it.localizedMessage }

            // 2. Fallback to GitHub Releases REST API if manifest not found
            if (remoteManifest == null && releasesApiUrl.isNotBlank()) {
                val apiReq = Request.Builder()
                    .url(releasesApiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "AI-Smartphone-Doctor-Android")
                    .build()
                runCatching {
                    client.newCall(apiReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val raw = resp.body?.string().orEmpty()
                            remoteManifest = parseGitHubReleaseFallback(raw)
                        } else if (resp.code == 404) {
                            // No release published yet — treat cleanly as up-to-date
                            return@withContext UpdateCheckResult.UpToDate(localCode, localName)
                        } else {
                            lastHttpError = "GitHub API HTTP ${resp.code}"
                        }
                    }
                }.onFailure { lastHttpError = it.localizedMessage }
            }

            val resolved = remoteManifest
            if (resolved == null) {
                return@withContext if (lastHttpError?.contains("404") == true) {
                    UpdateCheckResult.UpToDate(localCode, localName)
                } else {
                    UpdateCheckResult.Failure(
                        lastHttpError ?: "Unable to fetch update metadata. Check internet connection."
                    )
                }
            }

            if (isUpdateAvailable(resolved.versionCode, localCode)) {
                UpdateCheckResult.Available(resolved, localCode, localName)
            } else {
                UpdateCheckResult.UpToDate(localCode, localName)
            }
        } catch (t: Throwable) {
            UpdateCheckResult.Failure(t.localizedMessage ?: "Update check failed")
        }
    }

    suspend fun downloadAndVerifyApk(
        context: Context,
        manifest: UpdateManifest,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): ApkDownloadResult = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.filesDir, "updates").apply {
                if (!exists()) mkdirs()
            }
            // Cleanup older update files to preserve device storage
            updatesDir.listFiles()?.forEach { old ->
                if (old.isFile && old.name.endsWith(".apk")) {
                    runCatching { old.delete() }
                }
            }

            val safeName = manifest.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val targetApk = File(updatesDir, "AiSmartphoneDoctor-$safeName.apk")
            val tempApk = File(updatesDir, "AiSmartphoneDoctor-$safeName.apk.part")

            val req = Request.Builder()
                .url(manifest.apkUrl)
                .header("User-Agent", "AI-Smartphone-Doctor-Android")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext ApkDownloadResult.Failure("Download failed (HTTP ${resp.code})")
                }
                val body = resp.body ?: return@withContext ApkDownloadResult.Failure("Empty download stream")
                val totalBytes = if (body.contentLength() > 0L) body.contentLength() else manifest.fileSizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastPct = -1

                body.byteStream().use { input ->
                    FileOutputStream(tempApk).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            val pct = if (totalBytes > 0L) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else 0
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct, downloaded, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                if (!tempApk.exists() || tempApk.length() < 10_000L) {
                    runCatching { tempApk.delete() }
                    return@withContext ApkDownloadResult.Failure("Downloaded APK is incomplete or corrupted.")
                }

                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (manifest.sha256.isNotBlank() && !manifest.sha256.equals(actualSha256, ignoreCase = true)) {
                    runCatching { tempApk.delete() }
                    return@withContext ApkDownloadResult.Failure("APK integrity verification (SHA-256) failed. Download aborted.")
                }

                // Verify APK package archive matches this app's applicationId so user never overwrites wrong app
                val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageArchiveInfo(
                        tempApk.absolutePath,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageArchiveInfo(tempApk.absolutePath, 0)
                }

                if (archiveInfo != null && archiveInfo.packageName != context.packageName) {
                    runCatching { tempApk.delete() }
                    return@withContext ApkDownloadResult.Failure(
                        "Package ID mismatch (${archiveInfo.packageName}). Update blocked for safety."
                    )
                }

                if (targetApk.exists()) targetApk.delete()
                if (!tempApk.renameTo(targetApk)) {
                    tempApk.copyTo(targetApk, overwrite = true)
                    tempApk.delete()
                }

                ApkDownloadResult.Success(targetApk, actualSha256)
            }
        } catch (t: Throwable) {
            ApkDownloadResult.Failure(t.localizedMessage ?: "APK download failed")
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun buildInstallApkIntent(context: Context, apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Result<Unit> {
        return runCatching {
            val intent = buildInstallApkIntent(context, apkFile)
            context.startActivity(intent)
        }
    }

    fun showUpdateAvailableNotification(
        context: Context,
        manifest: UpdateManifest,
        language: String = "English"
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when a new version of AI Smartphone Doctor is available"
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.createNotificationChannel(channel)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_UPDATE_DIALOG, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isHindi = language.equals("Hindi", ignoreCase = true)
            val title = if (isHindi) {
                "नया अपडेट उपलब्ध है (v${manifest.versionName})"
            } else {
                "Update Available: v${manifest.versionName}"
            }
            val body = if (isHindi) {
                "नया संस्करण डाउनलोड और इंस्टॉल करने के लिए टैप करें। आपका डेटा सुरक्षित रहेगा।"
            } else {
                "Tap to download and install the latest version. Your login & data remain safe."
            }

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
            // Do not crash if notification service is unavailable
        }
    }
}
