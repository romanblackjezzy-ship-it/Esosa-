package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AppUpdateInfo
import com.example.data.model.ChatKind
import com.example.data.model.UserProfile
import com.example.notifications.EsosaNotificationManager
import com.example.ui.components.TelegramAvatarCircle
import com.example.ui.theme.telegramColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userProfile: UserProfile,
    notificationManager: EsosaNotificationManager,
    availableUpdate: AppUpdateInfo?,
    updateStatusSummary: String,
    isCheckingUpdates: Boolean,
    autoUpdateCheckEnabled: Boolean,
    githubUpdateRepo: String,
    onUpdateLocalProfile: (String, String, String) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSendTestNotification: () -> Boolean,
    onToggleAutoUpdateCheck: (Boolean) -> Unit,
    onSaveGithubRepo: (String) -> Boolean,
    onCheckForUpdatesNow: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
    var hasOsPermission by remember { mutableStateOf(notificationManager.hasNotificationPermission()) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

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
                            text = "Profile, notifications & updates",
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
                            contentDescription = stringResource(R.string.telegram_settings_edit_profile),
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
            // Telegram-style Local Profile Card (No Sign-In Needed)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelegramAvatarCircle(
                        title = userProfile.displayName,
                        seedKey = userProfile.id,
                        chatKind = ChatKind.DIRECT,
                        size = 62.dp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "@${userProfile.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (userProfile.bio.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = userProfile.bio,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Local Room Storage • No login required",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // Notifications Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (userProfile.notificationsEnabled) Icons.Filled.NotificationsActive
                                else Icons.Filled.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Message Notifications",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "System alerts for AI replies & updates",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (hasOsPermission) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.error,
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
                                    onClick = {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    modifier = Modifier.testTag("grant_notification_permission_button")
                                ) {
                                    Text("Allow")
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (!hasOsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val posted = onSendTestNotification()
                                testStatus = if (posted) {
                                    "Test notification posted to your Android shade."
                                } else {
                                    "Enable Android notification permission to preview alerts."
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_notification_button")
                    ) {
                        Text("Send Test Alert")
                    }

                    if (testStatus != null) {
                        Text(
                            text = testStatus.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // App Updates & GitHub Release Feed Card
            var repoDraft by remember(githubUpdateRepo) { mutableStateOf(githubUpdateRepo) }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.update_section_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Installed v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.update_auto_check_title),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.update_auto_check_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                            value = repoDraft,
                            onValueChange = { repoDraft = it },
                            label = { Text("GitHub Repo (owner/repo)") },
                            placeholder = { Text("e.g. username/Esosa") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("github_repo_input")
                        )
                        FilledTonalButton(
                            onClick = { onSaveGithubRepo(repoDraft) },
                            modifier = Modifier.testTag("save_github_repo_button")
                        ) {
                            Text("Save")
                        }
                    }

                    Text(
                        text = updateStatusSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (availableUpdate != null) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onCheckForUpdatesNow,
                            enabled = !isCheckingUpdates,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("check_updates_button")
                        ) {
                            if (isCheckingUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Checking…")
                            } else {
                                Text(stringResource(R.string.update_check_now))
                            }
                        }

                        if (availableUpdate != null) {
                            Button(
                                onClick = onOpenUpdateDialog,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("download_update_settings_button")
                            ) {
                                Text("Update v${availableUpdate.versionName}")
                            }
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
            }
        )
    }
}

@Composable
private fun EditLocalProfileDialog(
    initialProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var displayName by remember { mutableStateOf(initialProfile.displayName) }
    var username by remember { mutableStateOf(initialProfile.username) }
    var bio by remember { mutableStateOf(initialProfile.bio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.telegram_settings_edit_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 40) displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { if (it.length <= 32) username = it.filter { ch -> !ch.isWhitespace() } },
                    label = { Text("Username") },
                    prefix = { Text("@") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_username_input")
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 120) bio = it },
                    label = { Text("Bio") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_bio_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(displayName, username, bio) },
                enabled = displayName.isNotBlank(),
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
