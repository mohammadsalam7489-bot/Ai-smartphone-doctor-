package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.update.AppUpdateManager
import com.example.ui.DoctorViewModel
import com.example.ui.FuturisticSplashScreen
import com.example.ui.MainAppScreen
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {

    private var openUpdateDialogFromNotification by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)
        openUpdateDialogFromNotification =
            intent?.getBooleanExtra(AppUpdateManager.EXTRA_OPEN_UPDATE_DIALOG, false) == true

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        // STEP 2: Initialize UI
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberBackground
                ) {
                    val doctorViewModel: DoctorViewModel = viewModel()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val prefs = remember {
                        context.getSharedPreferences("ai_doctor_session", android.content.Context.MODE_PRIVATE)
                    }
                    val currentLanguage by doctorViewModel.currentLanguage.collectAsStateWithLifecycle()

                    var showSplash by remember { mutableStateOf(!openUpdateDialogFromNotification) }
                    var isLoggedIn by remember { mutableStateOf(prefs.getBoolean("is_logged_in", false)) }
                    var setupCompleted by remember { mutableStateOf(prefs.getBoolean("setup_completed", false)) }

                    LaunchedEffect(openUpdateDialogFromNotification) {
                        if (openUpdateDialogFromNotification) {
                            doctorViewModel.openUpdateDialog()
                            openUpdateDialogFromNotification = false
                        }
                    }

                    Crossfade(
                        targetState = showSplash,
                        animationSpec = tween(400),
                        label = "splash_crossfade"
                    ) { isSplashActive ->
                        if (isSplashActive) {
                            FuturisticSplashScreen(
                                onSplashFinished = { showSplash = false },
                                durationMs = 2600L
                            )
                        } else {
                            if (!isLoggedIn) {
                                com.example.ui.PhoneLoginScreen(
                                    currentLanguage = currentLanguage,
                                    onSelectLanguage = { doctorViewModel.setLanguage(it) },
                                    onLoginSuccess = { phone ->
                                        prefs.edit()
                                            .putBoolean("is_logged_in", true)
                                            .putString("logged_phone", phone)
                                            .apply()
                                        isLoggedIn = true
                                    },
                                    onSkip = {
                                        // Persist guest onboarding completion so session survives updates
                                        prefs.edit()
                                            .putBoolean("is_logged_in", true)
                                            .putBoolean("guest_mode", true)
                                            .apply()
                                        isLoggedIn = true
                                    }
                                )
                            } else if (!setupCompleted) {
                                com.example.ui.SystemSetupGate(
                                    currentLanguage = currentLanguage,
                                    onSelectLanguage = { doctorViewModel.setLanguage(it) },
                                    onSetupCompleted = {
                                        prefs.edit().putBoolean("setup_completed", true).apply()
                                        setupCompleted = true
                                    }
                                )
                            } else {
                                MainAppScreen(viewModel = doctorViewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(AppUpdateManager.EXTRA_OPEN_UPDATE_DIALOG, false)) {
            openUpdateDialogFromNotification = true
        }
    }
}
