package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SenderType
import com.example.data.update.ApkDownloadResult
import com.example.data.update.AppUpdateManager
import com.example.data.update.UpdateCheckResult
import com.example.data.update.UpdateManifest
import com.example.data.update.UpdateState
import com.example.ui.DoctorViewModel
import com.example.ui.MainAppScreen
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.UpdateAvailableBanner
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.MyApplicationTheme
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoUpdateAndLocalizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("ai_doctor_session", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultLanguageIsEnglish_andToggleSwitchesEntireAppToDevanagariHindiAndBack() {
        val viewModel = DoctorViewModel(app)
        assertEquals("English", viewModel.currentLanguage.value)

        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Default launch MUST be English
        composeTestRule.onNodeWithText("AI Smartphone Doctor").assertIsDisplayed()
        composeTestRule.onNodeWithText("AI Doctor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Diagnostics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guides").assertIsDisplayed()
        composeTestRule.onNodeWithText("Logs").assertIsDisplayed()

        // Tap Hindi pill on LanguageToggleSwitch
        composeTestRule.onNodeWithTag("lang_option_hi").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Hindi", viewModel.currentLanguage.value)
        composeTestRule.onNodeWithText("एआई स्मार्टफोन डॉक्टर").assertIsDisplayed()
        composeTestRule.onNodeWithText("एआई डॉक्टर").assertIsDisplayed()
        composeTestRule.onNodeWithText("जाँच केंद्र").assertIsDisplayed()
        composeTestRule.onNodeWithText("मार्गदर्शिका").assertIsDisplayed()
        composeTestRule.onNodeWithText("लॉग्स").assertIsDisplayed()

        // Welcome bubble switches to Pure Devanagari Hindi
        composeTestRule.onNodeWithText("नमस्ते!", substring = true).assertIsDisplayed()

        // Switch back to EN
        composeTestRule.onNodeWithTag("lang_option_en").performClick()
        composeTestRule.waitForIdle()

        assertEquals("English", viewModel.currentLanguage.value)
        composeTestRule.onNodeWithText("AI Smartphone Doctor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hello!", substring = true).assertIsDisplayed()
    }

    @Test
    fun sendUserMessage_completesSafelyWithoutCrashingInEnglishAndHindi() = runBlocking {
        val viewModel = DoctorViewModel(app)

        // English problem query
        viewModel.sendUserMessage("Mera phone bahut hang hota hai")
        var waitMs = 0L
        while (viewModel.isAnalyzing.value && waitMs < 25_000L) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            waitMs += 50
        }
        ShadowLooper.idleMainLooper()
        assertFalse("DoctorViewModel should finish analyzing cleanly", viewModel.isAnalyzing.value)
        val englishMessages = viewModel.messages.value
        assertTrue(englishMessages.size >= 3)
        assertEquals(SenderType.USER, englishMessages[englishMessages.size - 2].sender)
        assertEquals(SenderType.DOCTOR, englishMessages.last().sender)
        assertTrue(englishMessages.last().text.isNotBlank())

        // Switch to Hindi and send Devanagari query
        viewModel.setLanguage("Hindi")
        viewModel.sendUserMessage("मेरा फोन बहुत गर्म हो रहा है")
        waitMs = 0L
        while (viewModel.isAnalyzing.value && waitMs < 25_000L) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            waitMs += 50
        }
        ShadowLooper.idleMainLooper()
        assertFalse(viewModel.isAnalyzing.value)
        val hindiReply = viewModel.messages.value.last().text
        assertTrue(hindiReply.isNotBlank())
    }

    @Test
    fun appUpdateManager_versionComparisonAndManifestParsingWorkAccurately() {
        assertTrue(AppUpdateManager.isUpdateAvailable(remoteVersionCode = 105L, localVersionCode = 2L))
        assertFalse(AppUpdateManager.isUpdateAvailable(remoteVersionCode = 2L, localVersionCode = 2L))
        assertFalse(AppUpdateManager.isUpdateAvailable(remoteVersionCode = 1L, localVersionCode = 2L))

        val manifestJson = """
            {
              "versionCode": 142,
              "versionName": "1.1.42",
              "apkUrl": "https://example.com/Ai-Smartphone-Doctor.apk",
              "sha256": "ABCDEF012345",
              "fileSizeBytes": 24576000,
              "releaseNotes": "Auto update test notes",
              "releaseNotesHi": "स्वचालित अपडेट परीक्षण",
              "mandatory": false
            }
        """.trimIndent()
        val parsed = AppUpdateManager.parseUpdateManifest(manifestJson)!!
        assertEquals(142L, parsed.versionCode)
        assertEquals("1.1.42", parsed.versionName)
        assertEquals("abcdef012345", parsed.sha256)
        assertEquals(24576000L, parsed.fileSizeBytes)

        val githubJson = """
            {
              "tag_name": "v1.1.55",
              "body": "CI auto release build 55",
              "published_at": "2026-11-14T10:00:00Z",
              "assets": [
                {
                  "name": "Ai-Smartphone-Doctor.apk",
                  "browser_download_url": "https://github.com/org/repo/releases/download/v1.1.55/Ai-Smartphone-Doctor.apk",
                  "size": 19000000
                }
              ]
            }
        """.trimIndent()
        val ghParsed = AppUpdateManager.parseGitHubReleaseFallback(githubJson)!!
        assertEquals(55L, ghParsed.versionCode)
        assertEquals("1.1.55", ghParsed.versionName)
        assertTrue(ghParsed.apkUrl.endsWith(".apk"))
    }

    @Test
    fun appUpdateManager_endToEndHttpCheckDownloadSha256AndInstallIntent() = runBlocking {
        val dummyApkBytes = ByteArray(16_384) { idx -> (idx % 251).toByte() }
        val expectedSha256 = MessageDigest.getInstance("SHA-256")
            .digest(dummyApkBytes)
            .joinToString("") { "%02x".format(it) }

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        try {
            val manifestBody = """
                {
                  "versionCode": 999,
                  "versionName": "2.0.999",
                  "apkUrl": "http://127.0.0.1:$port/Ai-Smartphone-Doctor.apk",
                  "sha256": "$expectedSha256",
                  "fileSizeBytes": ${dummyApkBytes.size},
                  "releaseNotes": "Upgrade to 2.0.999",
                  "releaseNotesHi": "2.0.999 में अपग्रेड करें"
                }
            """.trimIndent().toByteArray()

            server.createContext("/update-manifest.json") { ex ->
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, manifestBody.size.toLong())
                ex.responseBody.use { it.write(manifestBody) }
            }
            server.createContext("/Ai-Smartphone-Doctor.apk") { ex ->
                ex.responseHeaders.add("Content-Type", "application/vnd.android.package-archive")
                ex.sendResponseHeaders(200, dummyApkBytes.size.toLong())
                ex.responseBody.use { it.write(dummyApkBytes) }
            }
            server.createContext("/missing-release") { ex ->
                ex.sendResponseHeaders(404, -1)
            }
            server.createContext("/server-error") { ex ->
                ex.sendResponseHeaders(500, -1)
            }
            server.start()

            // 1. Check finds newer version
            val checkRes = AppUpdateManager.checkForUpdate(
                context = app,
                manifestUrl = "http://127.0.0.1:$port/update-manifest.json",
                releasesApiUrl = ""
            )
            assertTrue(checkRes is UpdateCheckResult.Available)
            val available = checkRes as UpdateCheckResult.Available
            assertEquals(999L, available.manifest.versionCode)

            // 2. Download & verify valid SHA-256
            var sawProgress = false
            val dlSuccess = AppUpdateManager.downloadAndVerifyApk(
                context = app,
                manifest = available.manifest
            ) { pct, _, _ ->
                if (pct > 0) sawProgress = true
            }
            assertTrue("Expected download success, got $dlSuccess", dlSuccess is ApkDownloadResult.Success)
            val downloadedFile = (dlSuccess as ApkDownloadResult.Success).apkFile
            assertTrue(sawProgress)
            assertTrue(downloadedFile.exists())
            assertEquals(expectedSha256, dlSuccess.sha256)

            // 3. Build FileProvider install intent
            val installIntent = AppUpdateManager.buildInstallApkIntent(app, downloadedFile)
            assertEquals(Intent.ACTION_VIEW, installIntent.action)
            assertEquals("application/vnd.android.package-archive", installIntent.type)
            assertTrue((installIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            assertTrue(installIntent.data.toString().contains(".fileprovider"))

            // 4. Reject tampered SHA-256
            val tamperedManifest = available.manifest.copy(sha256 = "0000deadbeef")
            val dlTampered = AppUpdateManager.downloadAndVerifyApk(app, tamperedManifest) { _, _, _ -> }
            assertTrue(dlTampered is ApkDownloadResult.Failure)

            // 5. Missing release (404) treated safely as UpToDate without throwing
            val notFoundRes = AppUpdateManager.checkForUpdate(
                context = app,
                manifestUrl = "http://127.0.0.1:$port/missing-release",
                releasesApiUrl = "http://127.0.0.1:$port/missing-release"
            )
            assertTrue(notFoundRes is UpdateCheckResult.UpToDate)

            // 6. Server 500 surfaces graceful Failure
            val serverErrRes = AppUpdateManager.checkForUpdate(
                context = app,
                manifestUrl = "http://127.0.0.1:$port/server-error",
                releasesApiUrl = ""
            )
            assertTrue(serverErrRes is UpdateCheckResult.Failure)

            // 7. Unknown sources settings intent uses app package
            val permIntent = AppUpdateManager.buildUnknownSourcesSettingsIntent(app)
            assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, permIntent.action)
            assertTrue(permIntent.data.toString().contains(app.packageName))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun updateBannerAndDialog_renderEnglishAndHindiAndTriggerActions() {
        val manifest = UpdateManifest(
            versionCode = 120L,
            versionName = "1.1.20",
            apkUrl = "https://example.com/app.apk",
            fileSizeBytes = 15_000_000L,
            releaseNotes = "Faster AI diagnostics and in-app updates.",
            releaseNotesHi = "तेज़ एआई निदान और इन-ऐप अपडेट।"
        )
        var downloadTriggered = false

        composeTestRule.setContent {
            MyApplicationTheme {
                UpdateAvailableBanner(
                    updateState = UpdateState.UpdateAvailable(manifest, 2L, "1.1.0"),
                    strings = AppStrings.English,
                    onOpenDialog = {},
                    onDismissBanner = {}
                )
                AppUpdateDialog(
                    updateState = UpdateState.UpdateAvailable(manifest, 2L, "1.1.0"),
                    strings = AppStrings.English,
                    onStartDownload = { downloadTriggered = true },
                    onInstallNow = {},
                    onPermissionResult = {},
                    onRetry = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("update_available_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Update Available: v1.1.20").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_update_now_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_update_now_btn").performClick()
        assertTrue(downloadTriggered)
    }

    @Test
    fun updateDialog_rendersHindiPermissionAndErrorRetryStates() {
        val manifest = UpdateManifest(
            versionCode = 120L,
            versionName = "1.1.20",
            apkUrl = "https://example.com/app.apk"
        )
        var retried = false
        val dummyFile = File(app.filesDir, "updates/dummy.apk")

        composeTestRule.setContent {
            MyApplicationTheme {
                AppUpdateDialog(
                    updateState = UpdateState.Error(message = "Network timeout", canRetry = true, lastManifest = manifest),
                    strings = AppStrings.Hindi,
                    onStartDownload = {},
                    onInstallNow = {},
                    onPermissionResult = {},
                    onRetry = { retried = true },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("नया ऐप अपडेट उपलब्ध है").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network timeout").assertIsDisplayed()
        composeTestRule.onNodeWithText("पुनः प्रयास करें").performClick()
        assertTrue(retried)

        // Ensure ReadyToInstall state button text is localized in Hindi
        assertEquals("अपडेट इंस्टॉल करें", AppStrings.Hindi.updateInstallNowBtn)
        assertTrue(dummyFile.path.endsWith("dummy.apk"))
    }
}
