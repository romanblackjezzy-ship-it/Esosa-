package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ActiveSession
import com.example.data.model.ChatRoom

@Composable
fun TwoStepPinDialog(
    currentlyEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Boolean, String) -> Unit
) {
    var enabled by remember { mutableStateOf(currentlyEnabled) }
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .widthIn(max = 560.dp),
        title = { Text("Two-Step Verification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable 2-Step PIN Lock", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (enabled) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { raw -> pin = raw.filter { it.isDigit() }.take(8) },
                        label = { Text("4–8 Digit Security PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("two_step_pin_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(enabled, pin) },
                enabled = !enabled || pin.length >= 4,
                modifier = Modifier.testTag("save_two_step_pin_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ActiveSessionsDialog(
    sessions: List<ActiveSession>,
    onDismiss: () -> Unit,
    onTerminateSession: (String) -> Unit,
    onTerminateAllOthers: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Active Sessions (${sessions.size})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyColumn(modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(session.deviceName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${session.platform} • ${session.location}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (session.isCurrent) "Current Session" else session.lastActiveLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (session.isCurrent) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!session.isCurrent) {
                                    TextButton(onClick = { onTerminateSession(session.id) }) {
                                        Text("End", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                if (sessions.count { !it.isCurrent } > 0) {
                    OutlinedButton(
                        onClick = onTerminateAllOthers,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Terminate All Other Sessions", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun BlockedUsersDialog(
    blockedRooms: List<ChatRoom>,
    onDismiss: () -> Unit,
    onUnblock: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked Contacts (${blockedRooms.size})") },
        text = {
            if (blockedRooms.isEmpty()) {
                Text(
                    "No blocked contacts. You can block a user anytime from their User Info panel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.height(200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(blockedRooms, key = { it.id }) { room ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(room.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                    if (room.handle.isNotBlank()) {
                                        Text(
                                            text = if (room.handle.contains("@")) room.handle else "@${room.handle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                FilledTonalButton(onClick = { onUnblock(room.id) }) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun LanguagePickerDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val languages = listOf("English", "Spanish", "French", "German", "Japanese", "Portuguese")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Language") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                languages.forEach { lang ->
                    Surface(
                        color = if (lang == currentLanguage) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(lang)
                                onDismiss()
                            }
                    ) {
                        Text(
                            text = lang,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (lang == currentLanguage) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
