package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.DoctorViewModel
import com.example.ui.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatUiSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun cleanInitialLaunch_hidesTelemetryWall_stripsMarkdownStars_andTogglesDrawers() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = DoctorViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Core controls and clean starter card are visible
        composeTestRule.onNodeWithTag("toggle_vitals_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("starter_autofix_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("problem_input_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_attach_menu_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_input_mic_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("send_message_button").assertIsDisplayed()

        // Raw ** asterisks must never appear visibly in chat text
        composeTestRule.onNodeWithText("**", substring = true).assertDoesNotExist()

        // Vitals strip is hidden initially and toggles open on tapping Health % pill
        composeTestRule.onNodeWithTag("vitals_header_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toggle_vitals_pill").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vitals_header_card").assertIsDisplayed()

        // Attach options (Screenshot / Send Vitals) are hidden initially and toggle open on '+'
        composeTestRule.onNodeWithTag("pick_screenshot_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("attach_vitals_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toggle_attach_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pick_screenshot_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("attach_vitals_button").assertIsDisplayed()
    }

    @Test
    fun bottomNavigation_switchesAcrossAllFourTabsWithoutCrash() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = DoctorViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_tab_diagnostics").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("refresh_diagnostics_btn").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_guides").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("KNOWLEDGE DATABASE v2.0").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_history").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("DIAGNOSTIC AUDIT LOGS").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("problem_input_field").assertIsDisplayed()
    }
}
