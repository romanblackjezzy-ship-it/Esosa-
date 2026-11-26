package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.data.media.CameraPhotoHelper
import com.example.data.media.ImageCompressionPreset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.data.model.ActiveSession
import com.example.data.model.AppThemeMode
import com.example.data.model.AppUpdateInfo
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.ChatWallpaper
import com.example.data.model.InAppInstallStage
import com.example.data.model.UpdateErrorState
import com.example.data.model.UserProfile
import com.example.notifications.EsosaNotificationManager
import com.example.ui.components.TelegramAvatarCircle
import com.example.ui.theme.telegramColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    userProfile: UserProfile,
    notificationManager: EsosaNotificationManager,
    availableUpdate: AppUpdateInfo?,
    updateStatusSummary: String,
    isCheckingUpdates: Boolean,
    autoUpdateCheckEnabled: Boolean,
    githubUpdateRepo: String,
    remoteManifestUrl: String = "",
    installStage: InAppInstallStage = InAppInstallStage.Idle,
    updateError: UpdateErrorState? = null,
    isPlayStoreDistribution: Boolean = false,
    activeSessions: List<ActiveSession> = emptyList(),
    blockedRooms: List<ChatRoom> = emptyList(),
    onUpdateLocalProfile: (String, String, String) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSendTestNotification: () -> Boolean,
    onUpdateThemeMode: (AppThemeMode) -> Unit = {},
    onUpdateWallpaper: (ChatWallpaper) -> Unit = {},
    onUpdateLanguage: (String) -> Unit = {},
    onSetTwoStepPin: (Boolean, String) -> Unit = { _, _ -> },
    onToggleContactSync: (Boolean) -> Unit = {},
    onToggleReadReceipts: (Boolean) -> Unit = {},
    onUpdateLastSeenVisibility: (String) -> Unit = {},
    onUpdateDefaultDisappearSeconds: (Int) -> Unit = {},
    onTerminateSession: (String) -> Unit = {},
    onTerminateAllOtherSessions: () -> Unit = {},
    onUnblockRoom: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    onToggleAutoUpdateCheck: (Boolean) -> Unit,
    onSaveGithubRepo: (String) -> Boolean,
    onSaveRemoteManifestUrl: (String) -> Boolean = { true },
    onCheckForUpdatesNow: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onRestartAndInstall: () -> Unit = {},
    onUpdateProfilePhoto: (ByteArray, (String) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var hasOsPermission by remember { mutableStateOf(notificationManager.hasNotificationPermission()) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showSessionsDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    var isUploadingDirectPhoto by remember { mutableStateOf(false) }
    var pendingDirectCaptureUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingDirectCaptureFile by remember { mutableStateOf<java.io.File?>(null) }

    val directTakePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingDirectCaptureUri
        val file = pendingDirectCaptureFile
        pendingDirectCaptureUri = null
        pendingDirectCaptureFile = null
        if (success && uri != null) {
            isUploadingDirectPhoto = true
            scope.launch {
                val bytes = runCatching {
                    CameraPhotoHelper.compressUriToJpegBytesWithSizeCap(
                        context = context,
                        uri = uri,
                        preset = ImageCompressionPreset.PROFILE_AVATAR
                    )
                }.getOrNull()
                file?.delete()
                if (bytes != null && bytes.isNotEmpty()) {
                    onUpdateProfilePhoto(bytes) {
                        isUploadingDirectPhoto = false
                    }
                } else {
                    isUploadingDirectPhoto = false
                }
            }
        } else {
            file?.delete()
            isUploadingDirectPhoto = false
        }
    }

    val directCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, file) = CameraPhotoHelper.createTempPhotoTarget(context)
            pendingDirectCaptureUri = uri
            pendingDirectCaptureFile = file
            directTakePictureLauncher.launch(uri)
        }
    }

    val launchDirectCamera: () -> Unit = {
        val hasCam = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCam) {
            val (uri, file) = CameraPhotoHelper.createTempPhotoTarget(context)
            pendingDirectCaptureUri = uri
            pendingDirectCaptureFile = file
            directTakePictureLauncher.launch(uri)
        } else {
            directCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasOsPermission = granted || notificationManager.hasNotificationPermission()
        if (granted) {
            onToggleNotifications(true)
            testStatus = "Notification permission granted."
        } else {
            testStatus = "Permission denied in system settings."
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.tab_profile),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = telegram.onTopBar
                        )
                        Text(
                            text = "Account, privacy, theme & updates",
                            style = MaterialTheme.typography.bodySmall,
                            color = telegram.onTopBarMuted
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier.testTag("edit_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.esosa_settings_edit_profile),
                            tint = telegram.onTopBar
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = telegram.topBarContainer,
                    titleContentColor = telegram.onTopBar,
                    actionIconContentColor = telegram.onTopBar
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Account Profile Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clickable(enabled = !isUploadingDirectPhoto) { launchDirectCamera() }
                                .testTag("settings_profile_avatar"),
                            contentAlignment = Alignment.Center
                        ) {
                            TelegramAvatarCircle(
                                title = userProfile.displayName,
                                seedKey = userProfile.id,
                                chatKind = ChatKind.DIRECT,
                                avatarKey = userProfile.avatarKey,
                                photoUrl = userProfile.photoUrl,
                                isOnline = true,
                                size = 64.dp
                            )
                            if (isUploadingDirectPhoto) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.PhotoCamera,
                                            contentDescription = stringResource(R.string.profile_photo_take),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile.displayName.ifBlank { "My Profile" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            val subtitleParts = buildList {
                                if (userProfile.username.isNotBlank()) add("@${userProfile.username}")
                                if (userProfile.phoneOrEmail.isNotBlank()) add(userProfile.phoneOrEmail)
                            }
                            if (subtitleParts.isNotEmpty()) {
                                Text(
                                    text = subtitleParts.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (userProfile.bio.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = userProfile.bio,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.testTag("settings_cloud_sync_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Sync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Cloud Firestore Sync Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { launchDirectCamera() },
                            enabled = !isUploadingDirectPhoto,
                            modifier = Modifier.weight(1f).testTag("take_profile_photo_settings_button")
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.profile_photo_take))
                        }
                        OutlinedButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier.weight(1f).testTag("edit_profile_dialog_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Profile")
                        }
                    }
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().testTag("settings_logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Log Out")
                    }
                }
            }

            // Appearance & Chat Customization Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Appearance & Chat Customization", style = MaterialTheme.typography.titleSmall)
                    }
                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = userProfile.themeMode == mode,
                                onClick = { onUpdateThemeMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("theme_mode_${mode.id}")
                            )
                        }
                    }
                    Text("Default Chat Wallpaper", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChatWallpaper.entries.forEach { wp ->
                            FilterChip(
                                selected = userProfile.wallpaper == wp,
                                onClick = { onUpdateWallpaper(wp) },
                                label = { Text(wp.label) },
                                modifier = Modifier.testTag("wallpaper_${wp.id}")
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguageDialog = true }
                            .padding(vertical = 4.dp)
                            .testTag("settings_language_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Language", style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(userProfile.language, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Privacy & Security Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Privacy & Security", style = MaterialTheme.typography.titleSmall)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPinDialog = true }
                            .padding(vertical = 4.dp)
                            .testTag("settings_two_step_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Two-Step Verification", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (userProfile.twoStepPinEnabled) "PIN Lock Active" else "Off — tap to set PIN",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (userProfile.twoStepPinEnabled) "On" else "Setup",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSessionsDialog = true }
                            .padding(vertical = 4.dp)
                            .testTag("settings_sessions_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Active Sessions", style = MaterialTheme.typography.bodyLarge)
                        }
                        Text("${activeSessions.size} devices", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBlockedDialog = true }
                            .padding(vertical = 4.dp)
                            .testTag("settings_blocked_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Blocked Users", style = MaterialTheme.typography.bodyLarge)
                        }
                        Text("${blockedRooms.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column {
                                Text("Contact Synchronization", style = MaterialTheme.typography.bodyMedium)
                                Text("Keep friend list synced", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(checked = userProfile.contactSyncEnabled, onCheckedChange = onToggleContactSync)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Read Receipts", style = MaterialTheme.typography.bodyMedium)
                            Text("Show double ticks when messages are read", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = userProfile.readReceiptsEnabled, onCheckedChange = onToggleReadReceipts)
                    }

                    Text("Last Seen & Online", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Everyone", "Contacts", "Nobody").forEach { option ->
                            FilterChip(
                                selected = userProfile.lastSeenVisibility == option,
                                onClick = { onUpdateLastSeenVisibility(option) },
                                label = { Text(option) }
                            )
                        }
                    }

                    Text("Default Disappearing Timer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Off", 30 to "30s", 3600 to "1h", 86400 to "24h").forEach { (sec, label) ->
                            FilterChip(
                                selected = userProfile.defaultDisappearSeconds == sec,
                                onClick = { onUpdateDefaultDisappearSeconds(sec) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // Notifications Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (userProfile.notificationsEnabled) Icons.Filled.NotificationsActive
                                else Icons.Filled.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Message Notifications", style = MaterialTheme.typography.titleSmall)
                                Text("System alerts for messages & updates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = userProfile.notificationsEnabled,
                            onCheckedChange = { checked ->
                                onToggleNotifications(checked)
                                if (checked && !hasOsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.testTag("notifications_switch")
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (hasOsPermission) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (hasOsPermission) "Android notification permission granted"
                                    else "System notification permission needed",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (!hasOsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                FilledTonalButton(
                                    onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    modifier = Modifier.testTag("grant_notification_permission_button")
                                ) { Text("Allow") }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (!hasOsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val posted = onSendTestNotification()
                                testStatus = if (posted) "Test notification posted to your Android shade."
                                else "Enable Android notification permission to preview alerts."
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("test_notification_button")
                    ) { Text("Send Test Alert") }

                    if (testStatus != null) {
                        Text(text = testStatus.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // App Updates & Version Management Card
            var repoDraft by remember(githubUpdateRepo) { mutableStateOf(githubUpdateRepo) }
            var manifestDraft by remember(remoteManifestUrl) { mutableStateOf(remoteManifestUrl) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.update_section_title), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Installed v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isPlayStoreDistribution) "Google Play" else "HTTPS Release",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.update_auto_check_title), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.update_auto_check_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoUpdateCheckEnabled,
                            onCheckedChange = onToggleAutoUpdateCheck,
                            modifier = Modifier.testTag("auto_update_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manifestDraft,
                            onValueChange = { manifestDraft = it },
                            label = { Text("Remote Manifest JSON (HTTPS)") },
                            placeholder = { Text("https://releases.example.com/esosa.json") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("remote_manifest_url_input")
                        )
                        FilledTonalButton(
                            onClick = { onSaveRemoteManifestUrl(manifestDraft) },
                            modifier = Modifier.testTag("save_remote_manifest_button")
                        ) { Text("Save") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = repoDraft,
                            onValueChange = { repoDraft = it },
                            label = { Text("GitHub Repo Fallback (owner/repo)") },
                            placeholder = { Text("e.g. username/Esosa") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("github_repo_input")
                        )
                        FilledTonalButton(
                            onClick = { onSaveGithubRepo(repoDraft) },
                            modifier = Modifier.testTag("save_github_repo_button")
                        ) { Text("Save") }
                    }

                    Text(
                        text = updateStatusSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (availableUpdate != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when (installStage) {
                        is InAppInstallStage.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Downloading update in background…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (installStage.totalBytesToDownload > 0L) {
                                    LinearProgressIndicator(
                                        progress = { installStage.progress },
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                                }
                            }
                        }

                        is InAppInstallStage.DownloadedReadyToInstall -> {
                            Button(
                                onClick = onRestartAndInstall,
                                modifier = Modifier.fillMaxWidth().testTag("settings_restart_install_button")
                            ) {
                                Text("Restart and Install Downloaded Update")
                            }
                        }

                        else -> Unit
                    }

                    if (updateError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = updateError.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = updateError.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = onCheckForUpdatesNow,
                            enabled = !isCheckingUpdates,
                            modifier = Modifier.weight(1f).testTag("check_updates_button")
                        ) {
                            if (isCheckingUpdates) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Checking…")
                            } else {
                                Text(stringResource(R.string.update_check_now))
                            }
                        }

                        if (availableUpdate != null) {
                            Button(
                                onClick = onOpenUpdateDialog,
                                modifier = Modifier.weight(1f).testTag("download_update_settings_button")
                            ) { Text("View v${availableUpdate.versionName}") }
                        }
                    }
                }
            }
        }
    }

    if (showEditProfileDialog) {
        EditLocalProfileDialog(
            initialProfile = userProfile,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, username, bio ->
                onUpdateLocalProfile(name, username, bio)
                showEditProfileDialog = false
            },
            onUpdateProfilePhoto = onUpdateProfilePhoto
        )
    }

    if (showPinDialog) {
        TwoStepPinDialog(
            currentlyEnabled = userProfile.twoStepPinEnabled,
            onDismiss = { showPinDialog = false },
            onSave = { enabled, pin ->
                onSetTwoStepPin(enabled, pin)
                showPinDialog = false
            }
        )
    }

    if (showSessionsDialog) {
        ActiveSessionsDialog(
            sessions = activeSessions,
            onDismiss = { showSessionsDialog = false },
            onTerminateSession = onTerminateSession,
            onTerminateAllOthers = onTerminateAllOtherSessions
        )
    }

    if (showBlockedDialog) {
        BlockedUsersDialog(
            blockedRooms = blockedRooms,
            onDismiss = { showBlockedDialog = false },
            onUnblock = onUnblockRoom
        )
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            currentLanguage = userProfile.language,
            onDismiss = { showLanguageDialog = false },
            onSelect = onUpdateLanguage
        )
    }
}

@Composable
private fun EditLocalProfileDialog(
    initialProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onUpdateProfilePhoto: (ByteArray, (String) -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf(initialProfile.displayName) }
    var username by remember { mutableStateOf(initialProfile.username) }
    var bio by remember { mutableStateOf(initialProfile.bio) }
    var currentPhotoUrl by remember { mutableStateOf(initialProfile.photoUrl) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingCaptureFile by remember { mutableStateOf<java.io.File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        val file = pendingCaptureFile
        pendingCaptureUri = null
        pendingCaptureFile = null
        if (success && uri != null) {
            isUploadingPhoto = true
            scope.launch {
                val bytes = runCatching {
                    CameraPhotoHelper.compressUriToJpegBytesWithSizeCap(
                        context = context,
                        uri = uri,
                        preset = ImageCompressionPreset.PROFILE_AVATAR
                    )
                }.getOrNull()
                file?.delete()
                if (bytes != null && bytes.isNotEmpty()) {
                    onUpdateProfilePhoto(bytes) { newUrl ->
                        currentPhotoUrl = newUrl
                        isUploadingPhoto = false
                    }
                } else {
                    isUploadingPhoto = false
                }
            }
        } else {
            file?.delete()
            isUploadingPhoto = false
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, file) = CameraPhotoHelper.createTempPhotoTarget(context)
            pendingCaptureUri = uri
            pendingCaptureFile = file
            takePictureLauncher.launch(uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .widthIn(max = 560.dp),
        title = { Text(stringResource(R.string.esosa_settings_edit_profile)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clickable(enabled = !isUploadingPhoto) {
                            val hasCam = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCam) {
                                val (uri, file) = CameraPhotoHelper.createTempPhotoTarget(context)
                                pendingCaptureUri = uri
                                pendingCaptureFile = file
                                takePictureLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    TelegramAvatarCircle(
                        title = displayName.ifBlank { initialProfile.displayName },
                        seedKey = initialProfile.id,
                        chatKind = ChatKind.DIRECT,
                        avatarKey = initialProfile.avatarKey,
                        photoUrl = currentPhotoUrl,
                        isOnline = true,
                        size = 80.dp
                    )
                    if (isUploadingPhoto) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(40.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = stringResource(R.string.profile_photo_take),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasCam) {
                            val (uri, file) = com.example.data.media.CameraPhotoHelper.createTempPhotoTarget(context)
                            pendingCaptureUri = uri
                            pendingCaptureFile = file
                            takePictureLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    enabled = !isUploadingPhoto,
                    modifier = Modifier.fillMaxWidth().testTag("take_profile_photo_button")
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_photo_take))
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 40) displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { if (it.length <= 32) username = it.filter { ch -> !ch.isWhitespace() } },
                    label = { Text("Username") },
                    prefix = { Text("@") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_username_input")
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 120) bio = it },
                    label = { Text("Bio") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("profile_bio_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(displayName, username, bio) },
                enabled = displayName.isNotBlank() && !isUploadingPhoto,
                modifier = Modifier.testTag("save_profile_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploadingPhoto) { Text("Cancel") }
        }
    )
}
