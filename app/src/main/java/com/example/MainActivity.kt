package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.core.configureAppCheck
import com.example.notifications.EsosaNotificationManager
import com.example.ui.EsosaApp
import com.example.ui.theme.EsosaTheme

class MainActivity : ComponentActivity() {

    private var deepLinkRoomId by mutableStateOf<String?>(null)
    private var deepLinkTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI, ViewModel, or Firebase call
        configureAppCheck(this, intent)

        extractDeepLinks(intent)
        enableEdgeToEdge()

        setContent {
            EsosaTheme {
                EsosaApp(
                    initialRoomId = deepLinkRoomId,
                    initialTab = deepLinkTab
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractDeepLinks(intent)
    }

    private fun extractDeepLinks(intent: Intent?) {
        deepLinkRoomId = intent?.getStringExtra(EsosaNotificationManager.EXTRA_ROOM_ID)
        deepLinkTab = intent?.getStringExtra(EsosaNotificationManager.EXTRA_TARGET_TAB)
    }
}
