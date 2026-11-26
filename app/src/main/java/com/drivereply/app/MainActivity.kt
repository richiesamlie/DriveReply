package com.drivereply.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.drivereply.app.theme.DriveReplyTheme
import com.drivereply.app.util.ApkUpdateInstaller

class MainActivity : ComponentActivity() {
  companion object {
    private const val STATE_OPEN_UPDATES_TOKEN = "state_open_updates_token"
  }

  private var openUpdatesToken by mutableIntStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    openUpdatesToken = savedInstanceState?.getInt(STATE_OPEN_UPDATES_TOKEN, 0) ?: 0
    if (consumeOpenUpdatesExtra(intent)) {
      openUpdatesToken += 1
    }

    enableEdgeToEdge()
    setContent {
      DriveReplyTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
              openUpdatesToken = openUpdatesToken,
              onOpenUpdatesConsumed = { openUpdatesToken = 0 }
          )
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putInt(STATE_OPEN_UPDATES_TOKEN, openUpdatesToken)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (consumeOpenUpdatesExtra(intent)) {
      openUpdatesToken += 1
    }
  }

  private fun consumeOpenUpdatesExtra(intent: Intent?): Boolean {
    if (intent?.getBooleanExtra(ApkUpdateInstaller.EXTRA_OPEN_UPDATES, false) == true) {
      intent.removeExtra(ApkUpdateInstaller.EXTRA_OPEN_UPDATES)
      return true
    }
    return false
  }
}
