package com.drivereply.app.ui.main

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.activity.ComponentActivity
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.drivereply.app.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun appTitle_exists() {
    composeTestRule.setContent {
      MainScreen(onItemClick = {})
    }
    composeTestRule.onNodeWithText("DriveReply").assertExists()
  }
}

