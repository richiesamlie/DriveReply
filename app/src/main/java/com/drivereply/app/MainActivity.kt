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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.drivereply.app.theme.DriveReplyTheme
import com.drivereply.app.util.ApkUpdateInstaller

class MainActivity : ComponentActivity() {
  private var openUpdates by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    openUpdates = consumeOpenUpdatesExtra(intent)

    enableEdgeToEdge()
    setContent {
      DriveReplyTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
              openUpdates = openUpdates,
              onOpenUpdatesConsumed = { openUpdates = false }
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (consumeOpenUpdatesExtra(intent)) {
      openUpdates = true
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
