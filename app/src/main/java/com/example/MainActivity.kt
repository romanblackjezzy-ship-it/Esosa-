package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.notifications.EsosaNotificationManager
import com.example.ui.EsosaApp

class MainActivity : ComponentActivity() {

    private var deepLinkRoomId by mutableStateOf<String?>(null)
    private var deepLinkPostId by mutableStateOf<String?>(null)
    private var deepLinkStoryId by mutableStateOf<String?>(null)
    private var deepLinkTab by mutableStateOf<String?>(null)
    private var deepLinkShowUpdatePrompt by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        extractDeepLinks(intent)
        enableEdgeToEdge()

        setContent {
            EsosaApp(
                initialRoomId = deepLinkRoomId,
                initialPostId = deepLinkPostId,
                initialStoryId = deepLinkStoryId,
                initialTab = deepLinkTab,
                initialShowUpdatePrompt = deepLinkShowUpdatePrompt
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractDeepLinks(intent)
    }

    private fun extractDeepLinks(intent: Intent?) {
        deepLinkRoomId = intent?.getStringExtra(EsosaNotificationManager.EXTRA_ROOM_ID)
        deepLinkPostId = intent?.getStringExtra(EsosaNotificationManager.EXTRA_POST_ID)
        deepLinkStoryId = intent?.getStringExtra(EsosaNotificationManager.EXTRA_STORY_ID)
        deepLinkTab = intent?.getStringExtra(EsosaNotificationManager.EXTRA_TARGET_TAB)
        deepLinkShowUpdatePrompt =
            intent?.getBooleanExtra(EsosaNotificationManager.EXTRA_SHOW_UPDATE_DIALOG, false) ?: false
    }
}
