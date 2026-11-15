package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.util.AppCheckConfigStatus
import com.example.util.AppCheckProviderMode
import com.example.util.configureAppCheck
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppCheckConfigTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        java.io.File(app.applicationInfo.dataDir, "databases").mkdirs()
        val firebaseApp = if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(app)
        } else {
            FirebaseApp.getInstance()
        }
        assertNotNull("FirebaseApp must initialize from provisioned google-services.json", firebaseApp)
        val persistenceKey = firebaseApp!!.persistenceKey

        app.getSharedPreferences("ai_doctor_appcheck_meta", Context.MODE_PRIVATE)
            .edit().clear().commit()
        app.getSharedPreferences("com.google.firebase.appcheck.debug.store.$persistenceKey", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun provisionedFirebaseOptionsAndRecaptchaKeyMatchAppPackage() {
        val firebaseApp = FirebaseApp.getInstance()
        assertEquals("com.aistudio.smartphonedoctor.oapeir", app.packageName)
        assertEquals("gen-lang-client-0331272483", firebaseApp.options.projectId)
        assertEquals("1:511303589397:android:00d5b97e3a8d3aae2ab81c", firebaseApp.options.applicationId)

        val resId = app.resources.getIdentifier("recaptcha_site_key", "string", app.packageName)
        assertTrue("recaptcha_site_key string resource must exist", resId != 0)
        val siteKey = app.getString(resId)
        assertEquals("6LeAPiEuAAAAANbOEVDIMIeEgFsbPwCkwwnrndTG", siteKey)
    }

    @Test
    fun standaloneApkLaunchUsesRecaptchaInsteadOfUnregisteredDebugToken() {
        val launcherIntent = Intent(Intent.ACTION_MAIN)
        configureAppCheck(app, launcherIntent)

        assertEquals(AppCheckProviderMode.RECAPTCHA_STANDALONE, AppCheckConfigStatus.providerMode)
        assertEquals("gen-lang-client-0331272483", AppCheckConfigStatus.projectId)
        assertTrue(AppCheckConfigStatus.hasRecaptchaSiteKey)

        val metaPrefs = app.getSharedPreferences("ai_doctor_appcheck_meta", Context.MODE_PRIVATE)
        assertFalse(metaPrefs.getBoolean("aistudio_debug_token_injected", false))
    }

    @Test
    fun aiStudioIntentInjectsDebugTokenSynchronouslyAndPersistsAcrossRelaunch() {
        val testSecret = "11111111-2222-3333-4444-555555555555"
        val studioIntent = Intent(Intent.ACTION_MAIN).apply {
            putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", testSecret)
        }

        configureAppCheck(app, studioIntent)
        assertEquals(AppCheckProviderMode.DEBUG_INTENT_INJECTED, AppCheckConfigStatus.providerMode)

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugStore = app.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.$persistenceKey",
            Context.MODE_PRIVATE
        )
        assertEquals(
            testSecret,
            debugStore.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
        )

        // Subsequent launch without extra inside AI Studio emulator keeps persisted debug mode
        configureAppCheck(app, Intent(Intent.ACTION_MAIN))
        assertEquals(AppCheckProviderMode.DEBUG_PERSISTED_INTENT, AppCheckConfigStatus.providerMode)
    }
}
