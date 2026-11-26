package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PersonPin
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.core.UiState
import com.example.data.media.CameraPhotoHelper
import com.example.data.media.ImageCompressionPreset
import com.example.data.model.SocialFormatters
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.data.model.SocialStory
import com.example.data.model.UserFollowEdge
import com.example.data.model.UserProfile
import com.example.ui.components.InstagramUserAvatar
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.InstagramHairline
import com.example.ui.theme.InstagramHeartRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ProfileGridTab {
    POSTS,
    REELS,
    TAGGED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramProfileScreen(
    currentUid: String,
    userProfile: UserProfile,
    postsState: UiState<List<SocialPost>>,
    savedPostIds: Set<String>,
    followEdges: List<UserFollowEdge>,
    onOpenCreate: () -> Unit,
    onSelectPost: (SocialPost) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenCalls: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateLocalProfile: (displayName: String, username: String, bio: String) -> Unit,
    onUpdateProfilePhoto: (ByteArray, (String) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    stories: List<SocialStory> = emptyList(),
    onOpenStory: (SocialStory) -> Unit = {},
    onAddStoryClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeGridTab by remember { mutableStateOf(ProfileGridTab.POSTS) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showSavedPostsSheet by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAvatarChoiceSheet by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var cameraTempFile by remember { mutableStateOf<File?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    fun handleAvatarUri(uri: Uri) {
        isUploadingAvatar = true
        scope.launch {
            val bytesResult = withContext(Dispatchers.IO) {
                runCatching {
                    CameraPhotoHelper.compressUriToJpegBytesWithSizeCap(
                        context = context,
                        uri = uri,
                        maxBytes = ImageCompressionPreset.PROFILE_AVATAR.hardMaxBytes,
                        preset = ImageCompressionPreset.PROFILE_AVATAR
                    )
                }
            }
            bytesResult.onSuccess { bytes ->
                onUpdateProfilePhoto(bytes) {
                    isUploadingAvatar = false
                }
            }.onFailure {
                isUploadingAvatar = false
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) handleAvatarUri(uri)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        if (saved && cameraTempUri != null) {
            handleAvatarUri(cameraTempUri!!)
        } else {
            cameraTempFile?.delete()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runCatching { CameraPhotoHelper.createTempPhotoTarget(context) }
                .onSuccess { (uri, file) ->
                    cameraTempUri = uri
                    cameraTempFile = file
                    takePictureLauncher.launch(uri)
                }
        }
    }

    fun launchCameraForAvatar() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            runCatching { CameraPhotoHelper.createTempPhotoTarget(context) }
                .onSuccess { (uri, file) ->
                    cameraTempUri = uri
                    cameraTempFile = file
                    takePictureLauncher.launch(uri)
                }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val handleLabel = userProfile.username.ifBlank {
        userProfile.displayName.lowercase().replace(" ", "_").ifBlank { "esosa_user" }
    }
    val myLatestStory = remember(stories, currentUid) {
        stories.firstOrNull { it.authorId == currentUid }
    }

    val allPosts = (postsState as? UiState.Success)?.data.orEmpty()
    val myPosts = remember(allPosts, currentUid) { allPosts.filter { it.authorId == currentUid } }
    val myReels = remember(myPosts) { myPosts.filter { it.mediaType == SocialMediaType.REEL } }
    val taggedPosts = remember(allPosts, handleLabel) {
        val cleanHandle = handleLabel.trim().removePrefix("@")
        allPosts.filter { post ->
            cleanHandle.isNotBlank() && post.taggedUsernames.any { tag ->
                tag.trim().removePrefix("@").equals(cleanHandle, ignoreCase = true)
            }
        }
    }
    val savedPosts = remember(allPosts, savedPostIds) { allPosts.filter { savedPostIds.contains(it.id) } }

    val displayedPosts = when (activeGridTab) {
        ProfileGridTab.POSTS -> myPosts
        ProfileGridTab.REELS -> myReels
        ProfileGridTab.TAGGED -> taggedPosts
    }
    val chunkedRows = remember(displayedPosts) { displayedPosts.chunked(3) }

    val followersCount = remember(followEdges, currentUid) {
        followEdges.count { it.targetUid == currentUid }
    }
    val followingCount = remember(followEdges, currentUid) {
        followEdges.count { it.followerId == currentUid }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("instagram_profile_screen")
    ) {
        // Instagram Profile Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = handleLabel,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenCreate,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("profile_add_post_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddBox,
                        contentDescription = "Create post",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(25.dp)
                    )
                }
                IconButton(
                    onClick = { showMenuSheet = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("profile_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Profile Header Section
            item(key = "profile_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier
                                .clickable { showAvatarChoiceSheet = true }
                                .testTag("profile_avatar_button")
                        ) {
                            InstagramUserAvatar(
                                displayName = userProfile.displayName.ifBlank { handleLabel },
                                photoUrl = userProfile.photoUrl,
                                seedKey = currentUid,
                                size = 86.dp,
                                showStoryRing = myLatestStory != null,
                                isStoryViewed = false
                            )
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(InstagramBlue)
                                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CameraAlt,
                                    contentDescription = "Update photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(20.dp))

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProfileStatColumn(
                                count = SocialFormatters.formatCompactCount(myPosts.size),
                                label = "posts"
                            )
                            ProfileStatColumn(
                                count = SocialFormatters.formatCompactCount(followersCount),
                                label = "followers"
                            )
                            ProfileStatColumn(
                                count = SocialFormatters.formatCompactCount(followingCount),
                                label = "following"
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = userProfile.displayName.ifBlank { handleLabel },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (userProfile.bio.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = userProfile.bio,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (isUploadingAvatar) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Updating profile photo…",
                            style = MaterialTheme.typography.bodySmall,
                            color = InstagramBlue
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Instagram Action Buttons: Edit profile & Share profile
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileSecondaryButton(
                            label = "Edit profile",
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_edit_button")
                        )
                        ProfileSecondaryButton(
                            label = "Share profile",
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Follow @$handleLabel on Esosa!")
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(sendIntent, "Share profile"))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_share_button")
                        )
                    }
                }
            }

            // 3-Tab Switcher: Posts | Reels | Tagged
            item(key = "profile_tabs") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    ProfileGridTabItem(
                        icon = Icons.Outlined.GridOn,
                        contentDescription = "Posts",
                        selected = activeGridTab == ProfileGridTab.POSTS,
                        onClick = { activeGridTab = ProfileGridTab.POSTS },
                        modifier = Modifier.weight(1f).testTag("profile_tab_posts")
                    )
                    ProfileGridTabItem(
                        icon = Icons.Outlined.PlayCircle,
                        contentDescription = "Reels",
                        selected = activeGridTab == ProfileGridTab.REELS,
                        onClick = { activeGridTab = ProfileGridTab.REELS },
                        modifier = Modifier.weight(1f).testTag("profile_tab_reels")
                    )
                    ProfileGridTabItem(
                        icon = Icons.Outlined.PersonPin,
                        contentDescription = "Tagged",
                        selected = activeGridTab == ProfileGridTab.TAGGED,
                        onClick = { activeGridTab = ProfileGridTab.TAGGED },
                        modifier = Modifier.weight(1f).testTag("profile_tab_tagged")
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = InstagramHairline)
            }

            if (displayedPosts.isEmpty()) {
                item(key = "profile_empty_grid") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 52.dp)
                            .testTag("profile_empty_grid"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val (title, subtitle) = when (activeGridTab) {
                            ProfileGridTab.POSTS -> "Capture the moment" to "Photos you share will appear on your profile grid."
                            ProfileGridTab.REELS -> "No Reels yet" to "Share a vertical Reel to showcase it here."
                            ProfileGridTab.TAGGED -> "Photos and videos of you" to "When people tag @$handleLabel in posts, they'll appear here."
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (activeGridTab != ProfileGridTab.TAGGED) {
                            Spacer(Modifier.height(14.dp))
                            TextButton(onClick = onOpenCreate) {
                                Text(
                                    text = if (activeGridTab == ProfileGridTab.REELS) "Create Reel" else "Share first photo",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = InstagramBlue
                                )
                            }
                        }
                    }
                }
            } else {
                items(chunkedRows) { rowPosts ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 1.5.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        for (index in 0 until 3) {
                            val post = rowPosts.getOrNull(index)
                            if (post != null) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onSelectPost(post) }
                                        .testTag("profile_grid_tile_${post.id}")
                                ) {
                                    AsyncImage(
                                        model = post.imageUrl,
                                        contentDescription = post.caption.ifBlank { "Post" },
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (post.mediaType == SocialMediaType.REEL) {
                                        Icon(
                                            imageVector = Icons.Outlined.PlayCircle,
                                            contentDescription = "Reel",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .testTag("profile_menu_sheet")
            ) {
                MenuSheetItem(
                    icon = Icons.Outlined.Forum,
                    label = "Messages",
                    onClick = {
                        showMenuSheet = false
                        onOpenMessages()
                    }
                )
                MenuSheetItem(
                    icon = Icons.Outlined.Call,
                    label = "Voice & Video Calls",
                    onClick = {
                        showMenuSheet = false
                        onOpenCalls()
                    }
                )
                MenuSheetItem(
                    icon = Icons.Outlined.BookmarkBorder,
                    label = "Saved posts (${savedPosts.size})",
                    modifier = Modifier.testTag("profile_menu_saved_posts"),
                    onClick = {
                        showMenuSheet = false
                        showSavedPostsSheet = true
                    }
                )
                MenuSheetItem(
                    icon = Icons.Outlined.Settings,
                    label = "Settings, Privacy & Updates",
                    onClick = {
                        showMenuSheet = false
                        onOpenSettings()
                    }
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            tint = InstagramHeartRed
                        )
                    },
                    headlineContent = {
                        Text(
                            text = "Log out",
                            color = InstagramHeartRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier.clickable {
                        showMenuSheet = false
                        onSignOut()
                    }
                )
            }
        }
    }

    if (showSavedPostsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSavedPostsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .padding(bottom = 24.dp)
                    .testTag("profile_saved_posts_sheet")
            ) {
                Text(
                    text = "Saved posts",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                if (savedPosts.isEmpty()) {
                    Text(
                        text = "Tap the bookmark icon on any post to save it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    val savedRows = savedPosts.chunked(3)
                    LazyColumn(
                        modifier = Modifier.height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        items(savedRows) { rowPosts ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                            ) {
                                for (idx in 0 until 3) {
                                    val savedItem = rowPosts.getOrNull(idx)
                                    if (savedItem != null) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable {
                                                    showSavedPostsSheet = false
                                                    onSelectPost(savedItem)
                                                }
                                        ) {
                                            AsyncImage(
                                                model = savedItem.imageUrl,
                                                contentDescription = savedItem.caption.ifBlank { "Saved post" },
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAvatarChoiceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarChoiceSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = if (myLatestStory != null) "Profile & Story" else "Update profile photo",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
                if (myLatestStory != null) {
                    MenuSheetItem(
                        icon = Icons.Outlined.PlayCircle,
                        label = "View your story",
                        onClick = {
                            showAvatarChoiceSheet = false
                            onOpenStory(myLatestStory)
                        }
                    )
                }
                MenuSheetItem(
                    icon = Icons.Outlined.AddCircleOutline,
                    label = "Add to your story",
                    onClick = {
                        showAvatarChoiceSheet = false
                        onAddStoryClick()
                    }
                )
                MenuSheetItem(
                    icon = Icons.Outlined.CameraAlt,
                    label = "Take photo with camera",
                    onClick = {
                        showAvatarChoiceSheet = false
                        launchCameraForAvatar()
                    }
                )
                MenuSheetItem(
                    icon = Icons.Outlined.GridOn,
                    label = "Choose from library",
                    onClick = {
                        showAvatarChoiceSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }
        }
    }

    if (showEditProfileDialog) {
        var nameDraft by remember(userProfile) { mutableStateOf(userProfile.displayName) }
        var usernameDraft by remember(userProfile) { mutableStateOf(userProfile.username) }
        var bioDraft by remember(userProfile) { mutableStateOf(userProfile.bio) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { if (it.length <= 60) nameDraft = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = usernameDraft,
                        onValueChange = { if (it.length <= 32) usernameDraft = it },
                        label = { Text("Username") },
                        prefix = { Text("@") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bioDraft,
                        onValueChange = { if (it.length <= 140) bioDraft = it },
                        label = { Text("Bio") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateLocalProfile(nameDraft, usernameDraft, bioDraft)
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InstagramBlue)
                ) {
                    Text("Done", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileStatColumn(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ProfileSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProfileGridTabItem(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(2.dp))
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent)
        )
    }
}

@Composable
private fun MenuSheetItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}
