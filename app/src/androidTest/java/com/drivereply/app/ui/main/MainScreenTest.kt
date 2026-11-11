package com.drivereply.app.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.drivereply.app.Templates
import com.drivereply.app.data.MessageTemplate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI tests for [MainScreenContent] without requiring [com.drivereply.app.DriveReplyApplication] or ViewModel instantiation. */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun appTitle_andServiceOfflineState_exist() {
        composeTestRule.setContent {
            MainScreenContent(
                uiState = MainUiState(
                    isServiceEnabled = false,
                    isDriving = false,
                    isAutoReplyReady = false,
                    readinessBlockers = listOf("Protection is turned OFF")
                )
            )
        }
        composeTestRule.onNodeWithText("DriveReply").assertExists()
        composeTestRule.onNodeWithText("Service Offline").assertExists()
        composeTestRule.onNodeWithText("Auto-Reply Not Ready").assertExists()
    }

    @Test
    fun activeDrivingState_andTemplateStats_renderCorrectly() {
        val activeTemplate = MessageTemplate(
            id = "tpl-1",
            name = "Commute Mode",
            body = "I'm driving right now.",
            isActive = true
        )
        composeTestRule.setContent {
            MainScreenContent(
                uiState = MainUiState(
                    isServiceEnabled = true,
                    isDriving = true,
                    activeTemplate = activeTemplate,
                    templates = listOf(activeTemplate),
                    repliesToday = 7,
                    isAutoReplyReady = true
                )
            )
        }
        composeTestRule.onNodeWithText("Driving Detected").assertExists()
        composeTestRule.onNodeWithText("7").assertExists()
        composeTestRule.onNodeWithText("Quick Template Switch").assertExists()
    }

    @Test
    fun openTemplatesAction_invokesItemClickWithTemplatesKey() {
        var clickedKey: Any? = null
        composeTestRule.setContent {
            MainScreenContent(
                uiState = MainUiState(
                    isServiceEnabled = true,
                    hasNotificationListener = true,
                    isNotificationListenerConnected = true,
                    activeTemplate = null,
                    isAutoReplyReady = false,
                    readinessBlockers = listOf("No active reply template selected")
                ),
                onItemClick = { clickedKey = it }
            )
        }
        composeTestRule.onNodeWithText("Open Templates").performClick()
        assertEquals(Templates, clickedKey)
    }
}

