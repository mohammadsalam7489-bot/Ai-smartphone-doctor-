package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Helper utility for Firebase App Check debugging and token injection.
 */
object AppCheckHelper {
    fun injectDebugToken(context: Context, intent: Intent?) {
        try {
            val debugToken = intent?.getStringExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")
            if (!debugToken.isNullOrBlank()) {
                val prefs = context.getSharedPreferences("com.google.firebase.appcheck.debug.SHARED_PREFS", Context.MODE_PRIVATE)
                prefs.edit().putString("debug_token", debugToken).commit()
                Log.d("AppCheckHelper", "Injected App Check debug token from intent")
            }
        } catch (e: Exception) {
            Log.e("AppCheckHelper", "Failed to inject debug token", e)
        }
    }
}
