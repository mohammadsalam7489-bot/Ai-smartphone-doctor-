package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.update.UpdateState
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.UpdateAvailableBanner
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTextMuted

@Composable
fun MainAppScreen(
    viewModel: DoctorViewModel
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val isUpdateBannerDismissed by viewModel.isUpdateBannerDismissed.collectAsStateWithLifecycle()

    val strings = AppStrings.forLanguage(currentLanguage)

    BackHandler(enabled = currentTab != 0) {
        viewModel.selectTab(0)
    }

    if (showUpdateDialog) {
        AppUpdateDialog(
            updateState = updateState,
            strings = strings,
            onStartDownload = { viewModel.startUpdateDownload() },
            onInstallNow = { ctx -> viewModel.installDownloadedApk(ctx) },
            onPermissionResult = { ctx -> viewModel.onInstallPermissionResult(ctx) },
            onRetry = { viewModel.retryUpdate() },
            onDismiss = { viewModel.closeUpdateDialog() }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CyberBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(
                color = CyberSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                NavigationBar(
                    containerColor = CyberSurface,
                    contentColor = CyberCyan,
                    modifier = Modifier.testTag("main_bottom_nav_bar")
                ) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberSurfaceVariant,
                        unselectedIconColor = CyberTextMuted,
                        unselectedTextColor = CyberTextMuted
                    )

                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = strings.navChat) },
                        label = {
                            Text(
                                strings.navChat,
                                fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier.testTag("nav_tab_chat")
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        icon = { Icon(Icons.Default.MonitorHeart, contentDescription = strings.navDiagnostics) },
                        label = {
                            Text(
                                strings.navDiagnostics,
                                fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier.testTag("nav_tab_diagnostics")
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        icon = { Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = strings.navGuides) },
                        label = {
                            Text(
                                strings.navGuides,
                                fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier.testTag("nav_tab_guides")
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { viewModel.selectTab(3) },
                        icon = { Icon(Icons.Default.History, contentDescription = strings.navLogs) },
                        label = {
                            Text(
                                strings.navLogs,
                                fontWeight = if (currentTab == 3) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = itemColors,
                        modifier = Modifier.testTag("nav_tab_history")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            if (!isUpdateBannerDismissed &&
                (updateState is UpdateState.UpdateAvailable ||
                    updateState is UpdateState.Downloading ||
                    updateState is UpdateState.ReadyToInstall)
            ) {
                UpdateAvailableBanner(
                    updateState = updateState,
                    strings = strings,
                    onOpenDialog = { viewModel.openUpdateDialog() },
                    onDismissBanner = { viewModel.dismissUpdateBanner() }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    0 -> ChatScreen(viewModel = viewModel)
                    1 -> DiagnosticCenterScreen(
                        viewModel = viewModel,
                        onNavigateToChatWithQuery = { query ->
                            viewModel.selectTab(0)
                            viewModel.sendUserMessage(query)
                        }
                    )
                    2 -> ManualGuidesScreen(viewModel = viewModel)
                    3 -> HistoryScreen(viewModel = viewModel)
                }
            }
        }
    }
}
