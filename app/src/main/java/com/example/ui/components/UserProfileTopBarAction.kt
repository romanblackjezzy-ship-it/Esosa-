package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R

data class UserProfileHeaderInfo(
    val displayName: String,
    val email: String,
    val photoUrl: String?
)

@Composable
fun UserProfileTopBarAction(
    userInfo: UserProfileHeaderInfo,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    showNameChip: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    val resolvedName = userInfo.displayName.ifBlank {
        userInfo.email.substringBefore("@").ifBlank { "Member" }
    }
    val shortName = resolvedName.substringBefore(" ").ifBlank { resolvedName }
    val avatarInitials = resolvedName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifBlank { "E" }

    val profileDescription = if (userInfo.email.isNotBlank()) {
        "Signed in as $resolvedName (${userInfo.email}). Tap for profile options."
    } else {
        "Signed in as $resolvedName. Tap for profile options."
    }

    Box(modifier = modifier.padding(end = 6.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable { expanded = true }
                .semantics { contentDescription = profileDescription }
                .testTag("top_bar_user_profile_button")
        ) {
            Row(
                modifier = Modifier.padding(
                    start = 4.dp,
                    end = if (showNameChip) 8.dp else 4.dp,
                    top = 4.dp,
                    bottom = 4.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatarCircle(
                    photoUrl = userInfo.photoUrl,
                    initials = avatarInitials,
                    contentDescription = resolvedName,
                    sizeDp = 30
                )
                if (showNameChip) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = shortName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 96.dp)
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 230.dp, max = 300.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UserAvatarCircle(
                    photoUrl = userInfo.photoUrl,
                    initials = avatarInitials,
                    contentDescription = resolvedName,
                    sizeDp = 42
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resolvedName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (userInfo.email.isNotBlank()) {
                        Text(
                            text = userInfo.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.profile_google_authenticated),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_account_settings)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.ManageAccounts,
                        contentDescription = null
                    )
                },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
                modifier = Modifier.testTag("top_bar_account_settings_menu_item")
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.profile_sign_out),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    expanded = false
                    onSignOut()
                },
                modifier = Modifier.testTag("top_bar_sign_out_menu_item")
            )
        }
    }
}

@Composable
private fun UserAvatarCircle(
    photoUrl: String?,
    initials: String,
    contentDescription: String,
    sizeDp: Int
) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
