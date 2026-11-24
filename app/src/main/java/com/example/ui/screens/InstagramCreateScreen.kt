package com.example.ui.screens

import android.Manifest
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.media.CameraPhotoHelper
import com.example.data.model.CreateFormat
import com.example.data.model.SocialMediaType
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.InstagramHairline
import com.example.ui.theme.InstagramHeartRed
import java.io.File

@Composable
fun InstagramCreateScreen(
    initialFormat: CreateFormat = CreateFormat.POST,
    isPublishing: Boolean,
    uploadProgress: Float?,
    actionError: String?,
    onPublishPostOrReel: (
        imageUri: Uri,
        mediaType: SocialMediaType,
        caption: String,
        location: String,
        audioLabel: String,
        aspectRatio: Float
    ) -> Unit,
    onPublishStory: (imageUri: Uri?, caption: String) -> Unit,
    onClearError: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var format by remember(initialFormat) { mutableStateOf(initialFormat) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var audioLabel by remember { mutableStateOf("") }
    var aspectRatio by remember { mutableFloatStateOf(1f) }
    var cameraTempFile by remember { mutableStateOf<File?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            localError = null
            onClearError()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        if (saved && cameraTempUri != null) {
            selectedUri = cameraTempUri
            localError = null
            onClearError()
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
                .onFailure { localError = "Could not open camera." }
        } else {
            localError = "Camera permission is required to capture photos."
        }
    }

    fun launchCamera() {
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
                .onFailure { localError = "Could not open camera." }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val canShare = !isPublishing && when (format) {
        CreateFormat.POST, CreateFormat.REEL -> selectedUri != null
        CreateFormat.STORY -> selectedUri != null || caption.isNotBlank()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("instagram_create_screen")
    ) {
        // Top Instagram Create Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onCancel,
                enabled = !isPublishing,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = when (format) {
                    CreateFormat.POST -> "New post"
                    CreateFormat.REEL -> "New reel"
                    CreateFormat.STORY -> "Add to story"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            TextButton(
                onClick = {
                    when (format) {
                        CreateFormat.POST -> selectedUri?.let { uri ->
                            onPublishPostOrReel(
                                uri,
                                SocialMediaType.PHOTO,
                                caption,
                                location,
                                "",
                                aspectRatio
                            )
                        }
                        CreateFormat.REEL -> selectedUri?.let { uri ->
                            onPublishPostOrReel(
                                uri,
                                SocialMediaType.REEL,
                                caption,
                                location,
                                audioLabel.ifBlank { "Original audio" },
                                0.75f
                            )
                        }
                        CreateFormat.STORY -> onPublishStory(selectedUri, caption)
                    }
                },
                enabled = canShare,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("create_share_button")
            ) {
                Text(
                    text = if (isPublishing) "Sharing…" else "Share",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = if (canShare) InstagramBlue else InstagramBlue.copy(alpha = 0.4f)
                )
            }
        }

        if (isPublishing) {
            LinearProgressIndicator(
                progress = { (uploadProgress ?: 0.2f).coerceIn(0f, 1f) },
                color = InstagramBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        } else {
            HorizontalDivider(thickness = 0.5.dp, color = InstagramHairline)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Segmented Format Switcher: Post | Reel | Story
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CreateFormat.entries.forEach { item ->
                    val selected = format == item
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (selected) 1.dp else 0.dp,
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable(enabled = !isPublishing) { format = item }
                            .testTag("create_format_${item.name.lowercase()}")
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                    }
                }
            }

            // Media Preview Canvas
            val previewRatio = when {
                format == CreateFormat.REEL || format == CreateFormat.STORY -> 0.8f
                else -> aspectRatio
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(previewRatio.coerceIn(0.75f, 1.33f))
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(0.75.dp, InstagramHairline, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                val uri = selectedUri
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Selected media preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clickable(enabled = !isPublishing) { selectedUri = null }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove selected photo",
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp).size(18.dp)
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (format == CreateFormat.STORY) {
                                "Pick a photo or type a text story below"
                            } else {
                                "Choose a photo from your library or snap one now"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Photo Picker + Camera buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isPublishing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InstagramBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_pick_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Photo Library")
                }

                OutlinedButton(
                    onClick = ::launchCamera,
                    enabled = !isPublishing,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Camera", color = MaterialTheme.colorScheme.onBackground)
                }
            }

            // Aspect ratio chips for standard photo posts
            if (format == CreateFormat.POST) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frame:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf("1:1" to 1f, "4:5" to 0.8f, "4:3" to 1.33f).forEach { (label, ratio) ->
                        val isSelected = aspectRatio == ratio
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { aspectRatio = ratio }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSelected) MaterialTheme.colorScheme.background
                                else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { if (it.length <= 2200) caption = it },
                label = {
                    Text(
                        if (format == CreateFormat.STORY) "Story caption or status"
                        else "Write a caption..."
                    )
                },
                placeholder = { Text("Share thoughts, #tags, or context") },
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = InstagramBlue,
                    unfocusedBorderColor = InstagramHairline
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_caption_input")
            )

            if (format != CreateFormat.STORY) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { if (it.length <= 80) location = it },
                    label = { Text("Add location (optional)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = InstagramBlue,
                        unfocusedBorderColor = InstagramHairline
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (format == CreateFormat.REEL) {
                OutlinedTextField(
                    value = audioLabel,
                    onValueChange = { if (it.length <= 80) audioLabel = it },
                    label = { Text("Audio label (optional)") },
                    placeholder = { Text("Original audio") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = InstagramBlue,
                        unfocusedBorderColor = InstagramHairline
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val err = localError ?: actionError
            if (!err.isNullOrBlank()) {
                Surface(
                    color = InstagramHeartRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        color = InstagramHeartRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
