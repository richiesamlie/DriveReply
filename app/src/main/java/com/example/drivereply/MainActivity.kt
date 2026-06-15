package com.example.drivereply

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.drivereply.theme.DriveReplyTheme
import com.example.drivereply.util.ApkUpdateInstaller

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      DriveReplyTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation(openUpdates = intent?.getBooleanExtra(ApkUpdateInstaller.EXTRA_OPEN_UPDATES, false) == true) } }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    // If the user tapped the in-app update progress notification while
    // the activity is already alive, the activity is reused. Recompose
    // with the new start destination.
    setIntent(intent)
    if (intent.getBooleanExtra(ApkUpdateInstaller.EXTRA_OPEN_UPDATES, false)) {
      recreate()
    }
  }
}
