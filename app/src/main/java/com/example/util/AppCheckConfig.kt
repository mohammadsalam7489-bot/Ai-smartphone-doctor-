package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val META_PREFS_NAME = "ai_doctor_appcheck_meta"
private const val KEY_INJECTED_DEBUG_TOKEN = "aistudio_debug_token_injected"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

enum class AppCheckProviderMode {
    UNINITIALIZED,
    DEBUG_INTENT_INJECTED,
    DEBUG_PERSISTED_INTENT,
    RECAPTCHA_STANDALONE,
    RECAPTCHA_RELEASE,
    DEBUG_FALLBACK_NO_SITE_KEY,
    MISCONFIGURED
}

object AppCheckConfigStatus {
    @Volatile
    var providerMode: AppCheckProviderMode = AppCheckProviderMode.UNINITIALIZED
        internal set

    @Volatile
    var projectId: String? = null
        internal set

    @Volatile
    var hasRecaptchaSiteKey: Boolean = false
        internal set

    fun summary(): String =
        "mode=${providerMode.name}, project=${projectId ?: "none"}, recaptchaKey=${hasRecaptchaSiteKey}"
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        } else {
            try {
                FirebaseApp.getInstance()
            } catch (_: Exception) {
                null
            }
        }

        if (app == null || FirebaseApp.getApps(context).isEmpty()) {
            AppCheckConfigStatus.providerMode = AppCheckProviderMode.MISCONFIGURED
            AppCheckConfigStatus.projectId = null
            Log.w(TAG, "FirebaseApp is not initialized yet (google-services.json missing or unprovisioned). Skipping App Check configuration.")
            return
        }

        AppCheckConfigStatus.projectId = runCatching { app.options.projectId }.getOrNull()

        val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
        val siteKey = if (resId != 0) context.getString(resId).trim() else ""
        AppCheckConfigStatus.hasRecaptchaSiteKey = siteKey.isNotEmpty()

        val appCheck = FirebaseAppCheck.getInstance(app)

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
            val persistenceKey = app.persistenceKey
            val debugStorePrefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            val metaPrefs = context.getSharedPreferences(META_PREFS_NAME, Context.MODE_PRIVATE)

            if (!debugToken.isNullOrEmpty()) {
                // CRITICAL: MUST use synchronous commit() so DebugAppCheckProviderFactory
                // reads the injected token immediately before any Firebase request fires.
                debugStorePrefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                metaPrefs.edit()
                    .putBoolean(KEY_INJECTED_DEBUG_TOKEN, true)
                    .commit()
                appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
                AppCheckConfigStatus.providerMode = AppCheckProviderMode.DEBUG_INTENT_INJECTED
                Log.d(TAG, "App Check debug secret injected from Intent and DebugAppCheckProviderFactory installed.")
            } else {
                val hadInjectedToken = metaPrefs.getBoolean(KEY_INJECTED_DEBUG_TOKEN, false)
                val persistedSecret = debugStorePrefs.getString(DEBUG_SECRET_KEY, null)

                if (hadInjectedToken && !persistedSecret.isNullOrBlank()) {
                    appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
                    AppCheckConfigStatus.providerMode = AppCheckProviderMode.DEBUG_PERSISTED_INTENT
                    Log.d(TAG, "Persisted AI Studio debug token found; DebugAppCheckProviderFactory installed.")
                } else if (siteKey.isNotEmpty()) {
                    // Standalone / GitHub-downloaded APK on a physical device has NO AI Studio Intent debug token.
                    // Using DebugAppCheckProviderFactory here would generate a random unregistered UUID that fails
                    // with HTTP 403 ("Firebase App Check token is invalid"). Use reCAPTCHA Enterprise instead.
                    appCheck.installAppCheckProviderFactory(RecaptchaAppCheckProviderFactory.getInstance(siteKey))
                    AppCheckConfigStatus.providerMode = AppCheckProviderMode.RECAPTCHA_STANDALONE
                    Log.d(TAG, "Standalone APK detected (no debug Intent token); RecaptchaAppCheckProviderFactory installed.")
                } else {
                    appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
                    AppCheckConfigStatus.providerMode = AppCheckProviderMode.DEBUG_FALLBACK_NO_SITE_KEY
                    Log.w(TAG, "No debug token Intent extra and no recaptcha_site_key found; falling back to DebugAppCheckProviderFactory.")
                }
            }
        } else {
            if (siteKey.isNotEmpty()) {
                appCheck.installAppCheckProviderFactory(RecaptchaAppCheckProviderFactory.getInstance(siteKey))
                AppCheckConfigStatus.providerMode = AppCheckProviderMode.RECAPTCHA_RELEASE
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed for release build.")
            } else {
                AppCheckConfigStatus.providerMode = AppCheckProviderMode.MISCONFIGURED
                Log.e(TAG, "recaptcha_site_key string resource is missing or blank in release build.")
            }
        }
    } catch (e: Exception) {
        AppCheckConfigStatus.providerMode = AppCheckProviderMode.MISCONFIGURED
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
