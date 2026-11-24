package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.media.CameraPhotoHelper
import com.example.data.model.CreateFormat
import com.example.data.model.MediaCropSpec
import com.example.data.model.PostFilter
import com.example.data.model.SocialMediaType
import com.example.ui.components.CreateCropAndFilterStep
import com.example.ui.components.CreateMediaPickerStep
import com.example.ui.components.CreateStep
import com.example.ui.components.TagPeopleBottomSheet
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.InstagramHairline
import com.example.ui.theme.InstagramHeartRed
import java.io.File

private enum class PendingCameraCapture {
    PHOTO,
    VIDEO
}

private val QuickSuggestedLocations = listOf(
    "Lagos",
    "London",
    "New York",
    "Tokyo",
    "Paris"
)

@Composable
fun InstagramCreateScreen(
    initialFormat: CreateFormat = CreateFormat.POST,
    isPublishing: Boolean,
    uploadProgress: Float?,
    actionError: String?,
    suggestedUsernames: List<String> = emptyList(),
    initialSelectedUri: Uri? = null,
    initialIsVideo: Boolean = false,
    onPublishPostOrReel: (
        imageUri: Uri,
        mediaType: SocialMediaType,
        caption: String,
        location: String,
        audioLabel: String,
        aspectRatio: Float,
        isVideo: Boolean,
        cropSpec: MediaCropSpec,
        filter: PostFilter,
        taggedUsernames: List<String>
    ) -> Unit,
    onPublishStory: (
        imageUri: Uri?,
        caption: String,
        isVideo: Boolean,
        cropSpec: MediaCropSpec,
        filter: PostFilter
    ) -> Unit,
    onClearError: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var format by remember(initialFormat) { mutableStateOf(initialFormat) }
    var currentStep by remember { mutableStateOf(CreateStep.PICKER) }
    var selectedUri by remember(initialSelectedUri) { mutableStateOf(initialSelectedUri) }
    var isVideo by remember(initialSelectedUri, initialIsVideo) { mutableStateOf(initialIsVideo) }

    var cropSpec by remember(initialFormat) {
        mutableStateOf(
            MediaCropSpec(
                targetAspectRatio = if (initialFormat == CreateFormat.REEL || initialFormat == CreateFormat.STORY) 0.8f else 1f
            )
        )
    }
    var selectedFilter by remember { mutableStateOf(PostFilter.NORMAL) }
    var caption by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var audioLabel by remember { mutableStateOf("") }
    var taggedUsernames by remember { mutableStateOf<List<String>>(emptyList()) }

    var showTagPeopleSheet by remember { mutableStateOf(false) }
    var showLocationEditor by remember { mutableStateOf(false) }
    var cameraTempFile by remember { mutableStateOf<File?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCaptureMode by remember { mutableStateOf(PendingCameraCapture.PHOTO) }
    var localError by remember { mutableStateOf<String?>(null) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            isVideo = CameraPhotoHelper.isVideoUri(context, uri)
            cropSpec = MediaCropSpec(
                targetAspectRatio = if (format == CreateFormat.REEL || format == CreateFormat.STORY) 0.8f else 1f
            )
            selectedFilter = PostFilter.NORMAL
            localError = null
            onClearError()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        if (saved && cameraTempUri != null) {
            selectedUri = cameraTempUri
            isVideo = false
            cropSpec = MediaCropSpec(
                targetAspectRatio = if (format == CreateFormat.REEL || format == CreateFormat.STORY) 0.8f else 1f
            )
            selectedFilter = PostFilter.NORMAL
            localError = null
            onClearError()
        } else {
            cameraTempFile?.delete()
        }
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { saved ->
        if (saved && cameraTempUri != null) {
            selectedUri = cameraTempUri
            isVideo = true
            cropSpec = MediaCropSpec(
                targetAspectRatio = if (format == CreateFormat.POST) 1f else 0.8f
            )
            selectedFilter = PostFilter.NORMAL
            localError = null
            onClearError()
        } else {
            cameraTempFile?.delete()
        }
    }

    fun startCapture(mode: PendingCameraCapture) {
        when (mode) {
            PendingCameraCapture.PHOTO -> {
                runCatching { CameraPhotoHelper.createTempPhotoTarget(context) }
                    .onSuccess { (uri, file) ->
                        cameraTempUri = uri
                        cameraTempFile = file
                        takePictureLauncher.launch(uri)
                    }
                    .onFailure { localError = "Could not open camera." }
            }
            PendingCameraCapture.VIDEO -> {
                runCatching { CameraPhotoHelper.createTempVideoTarget(context) }
                    .onSuccess { (uri, file) ->
                        cameraTempUri = uri
                        cameraTempFile = file
                        captureVideoLauncher.launch(uri)
                    }
                    .onFailure { localError = "Could not start video recorder." }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCapture(pendingCaptureMode)
        } else {
            localError = "Camera permission is required to capture photos or video."
        }
    }

    fun launchCameraCapture(mode: PendingCameraCapture) {
        pendingCaptureMode = mode
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startCapture(mode)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun stepBack() {
        when (currentStep) {
            CreateStep.PICKER -> onCancel()
            CreateStep.EDIT -> currentStep = CreateStep.PICKER
            CreateStep.DETAILS -> {
                currentStep = if (selectedUri != null) CreateStep.EDIT else CreateStep.PICKER
            }
        }
    }

    fun performShare() {
        when (format) {
            CreateFormat.POST -> selectedUri?.let { uri ->
                onPublishPostOrReel(
                    uri,
                    SocialMediaType.PHOTO,
                    caption,
                    location,
                    if (isVideo) audioLabel else "",
                    cropSpec.targetAspectRatio,
                    isVideo,
                    cropSpec,
                    selectedFilter,
                    taggedUsernames
                )
            }
            CreateFormat.REEL -> selectedUri?.let { uri ->
                onPublishPostOrReel(
                    uri,
                    SocialMediaType.REEL,
                    caption,
                    location,
                    audioLabel.ifBlank { "Original audio" },
                    cropSpec.targetAspectRatio,
                    isVideo,
                    cropSpec,
                    selectedFilter,
                    taggedUsernames
                )
            }
            CreateFormat.STORY -> onPublishStory(
                selectedUri,
                caption,
                isVideo,
                cropSpec,
                selectedFilter
            )
        }
    }

    BackHandler(enabled = currentStep != CreateStep.PICKER && !isPublishing) {
        stepBack()
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
                onClick = ::stepBack,
                enabled = !isPublishing,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(if (currentStep == CreateStep.PICKER) "create_close_button" else "create_back_button")
            ) {
                Icon(
                    imageVector = if (currentStep == CreateStep.PICKER) Icons.Filled.Close
                    else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (currentStep == CreateStep.PICKER) "Cancel" else "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (currentStep) {
                        CreateStep.PICKER -> when (format) {
                            CreateFormat.POST -> "New post"
                            CreateFormat.REEL -> "New reel"
                            CreateFormat.STORY -> "Add to story"
                        }
                        CreateStep.EDIT -> "Edit & crop"
                        CreateStep.DETAILS -> when (format) {
                            CreateFormat.POST -> "New post"
                            CreateFormat.REEL -> "New reel"
                            CreateFormat.STORY -> "Story details"
                        }
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when (currentStep) {
                        CreateStep.PICKER -> "Step 1 of 3 · Select media"
                        CreateStep.EDIT -> "Step 2 of 3 · Crop & filters"
                        CreateStep.DETAILS -> "Step 3 of 3 · Caption & tags"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (currentStep) {
                CreateStep.PICKER -> {
                    val canAdvanceFromPicker = !isPublishing && (selectedUri != null || format == CreateFormat.STORY)
                    TextButton(
                        onClick = {
                            currentStep = if (selectedUri != null) CreateStep.EDIT else CreateStep.DETAILS
                        },
                        enabled = canAdvanceFromPicker,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("create_picker_next_button")
                    ) {
                        Text(
                            text = "Next",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = if (canAdvanceFromPicker) InstagramBlue else InstagramBlue.copy(alpha = 0.4f)
                        )
                    }
                }
                CreateStep.EDIT -> {
                    TextButton(
                        onClick = { currentStep = CreateStep.DETAILS },
                        enabled = !isPublishing,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("create_edit_next_button")
                    ) {
                        Text(
                            text = "Next",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = InstagramBlue
                        )
                    }
                }
                CreateStep.DETAILS -> {
                    TextButton(
                        onClick = ::performShare,
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
            }
        }

        if (isPublishing) {
            LinearProgressIndicator(
                progress = { (uploadProgress ?: 0.2f).coerceIn(0f, 1f) },
                color = InstagramBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        } else {
            HorizontalDivider(thickness = 0.5.dp, color = InstagramHairline)
        }

        val err = localError ?: actionError
        if (!err.isNullOrBlank()) {
            Surface(
                color = InstagramHeartRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = err,
                    color = InstagramHeartRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                (slideInHorizontally { width -> if (forward) width / 3 else -width / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> if (forward) -width / 3 else width / 3 } + fadeOut())
            },
            label = "create_step_transition",
            modifier = Modifier.weight(1f)
        ) { step ->
            when (step) {
                CreateStep.PICKER -> CreateMediaPickerStep(
                    format = format,
                    selectedUri = selectedUri,
                    isVideo = isVideo,
                    isPublishing = isPublishing,
                    cropSpec = cropSpec,
                    onSelectFormat = { chosen ->
                        format = chosen
                        cropSpec = cropSpec.copy(
                            targetAspectRatio = if (chosen == CreateFormat.REEL || chosen == CreateFormat.STORY) 0.8f else 1f
                        )
                    },
                    onOpenGallery = {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onTakePhoto = { launchCameraCapture(PendingCameraCapture.PHOTO) },
                    onRecordVideo = { launchCameraCapture(PendingCameraCapture.VIDEO) },
                    onClearMedia = {
                        selectedUri = null
                        isVideo = false
                    },
                    onContinue = {
                        currentStep = if (selectedUri != null) CreateStep.EDIT else CreateStep.DETAILS
                    }
                )

                CreateStep.EDIT -> {
                    val uri = selectedUri
                    if (uri != null) {
                        CreateCropAndFilterStep(
                            mediaUri = uri,
                            isVideo = isVideo,
                            cropSpec = cropSpec,
                            selectedFilter = selectedFilter,
                            onCropSpecChange = { cropSpec = it },
                            onSelectFilter = { selectedFilter = it },
                            onNext = { currentStep = CreateStep.DETAILS }
                        )
                    } else {
                        currentStep = CreateStep.PICKER
                    }
                }

                CreateStep.DETAILS -> CreatePostDetailsStep(
                    format = format,
                    selectedUri = selectedUri,
                    isVideo = isVideo,
                    selectedFilter = selectedFilter,
                    caption = caption,
                    location = location,
                    audioLabel = audioLabel,
                    taggedUsernames = taggedUsernames,
                    showLocationEditor = showLocationEditor,
                    isPublishing = isPublishing,
                    canShare = canShare,
                    onEditMedia = {
                        if (selectedUri != null) currentStep = CreateStep.EDIT else currentStep = CreateStep.PICKER
                    },
                    onCaptionChange = { if (it.length <= 2200) caption = it },
                    onOpenTagPeople = { showTagPeopleSheet = true },
                    onRemoveTag = { tag -> taggedUsernames = taggedUsernames.filterNot { it == tag } },
                    onToggleLocationEditor = { showLocationEditor = !showLocationEditor },
                    onLocationChange = { if (it.length <= 80) location = it },
                    onAudioLabelChange = { if (it.length <= 80) audioLabel = it },
                    onShareClick = ::performShare
                )
            }
        }
    }

    if (showTagPeopleSheet) {
        TagPeopleBottomSheet(
            selectedUsernames = taggedUsernames,
            suggestedUsernames = suggestedUsernames,
            onToggleUsername = { target ->
                val clean = target.trim().removePrefix("@")
                if (clean.isNotBlank()) {
                    taggedUsernames = if (taggedUsernames.contains(clean)) {
                        taggedUsernames.filterNot { it == clean }
                    } else {
                        (taggedUsernames + clean).take(20)
                    }
                }
            },
            onAddCustomUsername = { custom ->
                val clean = custom.trim().removePrefix("@").take(32)
                if (clean.isNotBlank() && !taggedUsernames.contains(clean)) {
                    taggedUsernames = (taggedUsernames + clean).take(20)
                }
            },
            onDismiss = { showTagPeopleSheet = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreatePostDetailsStep(
    format: CreateFormat,
    selectedUri: Uri?,
    isVideo: Boolean,
    selectedFilter: PostFilter,
    caption: String,
    location: String,
    audioLabel: String,
    taggedUsernames: List<String>,
    showLocationEditor: Boolean,
    isPublishing: Boolean,
    canShare: Boolean,
    onEditMedia: () -> Unit,
    onCaptionChange: (String) -> Unit,
    onOpenTagPeople: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onToggleLocationEditor: () -> Unit,
    onLocationChange: (String) -> Unit,
    onAudioLabelChange: (String) -> Unit,
    onShareClick: () -> Unit
) {
    val thumbMatrix = remember(selectedFilter) { ColorMatrix(selectedFilter.colorMatrixValues()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail + Caption Header Row (Instagram style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (selectedUri != null) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(0.75.dp, InstagramHairline, RoundedCornerShape(10.dp))
                        .clickable(enabled = !isPublishing, onClick = onEditMedia)
                        .testTag("create_details_thumbnail"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo) {
                        Surface(
                            color = Color(0xFF1C1C1E),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Videocam,
                                    contentDescription = "Edit video",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = selectedUri,
                            contentDescription = "Edit cropped photo",
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(thumbMatrix),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            OutlinedTextField(
                value = caption,
                onValueChange = onCaptionChange,
                label = {
                    Text(
                        if (format == CreateFormat.STORY) "Story caption or status"
                        else "Write a caption..."
                    )
                },
                placeholder = { Text("Add a caption, #hashtags, or @mentions") },
                minLines = 3,
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = InstagramBlue,
                    unfocusedBorderColor = InstagramHairline
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("create_caption_input")
            )
        }

        if (format != CreateFormat.STORY) {
            HorizontalDivider(thickness = 0.5.dp, color = InstagramHairline)

            // Tag People Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable(enabled = !isPublishing, onClick = onOpenTagPeople)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("create_tag_people_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Tag people",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (taggedUsernames.isNotEmpty()) {
                    Text(
                        text = if (taggedUsernames.size == 1) "@${taggedUsernames.first()}"
                        else "${taggedUsernames.size} people",
                        style = MaterialTheme.typography.labelLarge,
                        color = InstagramBlue
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open tag people picker",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (taggedUsernames.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    taggedUsernames.forEach { tagged ->
                        FilterChip(
                            selected = true,
                            onClick = { if (!isPublishing) onRemoveTag(tagged) },
                            label = { Text("@$tagged") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove @$tagged",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = InstagramBlue.copy(alpha = 0.12f),
                                selectedLabelColor = InstagramBlue
                            ),
                            modifier = Modifier.testTag("tagged_chip_$tagged")
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = InstagramHairline)

            // Add Location Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable(enabled = !isPublishing, onClick = onToggleLocationEditor)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("create_location_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (location.isBlank()) "Add location" else location,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (location.isBlank()) MaterialTheme.colorScheme.onBackground else InstagramBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (location.isNotBlank()) {
                    IconButton(
                        onClick = { onLocationChange("") },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear location",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Add location",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showLocationEditor || location.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = onLocationChange,
                        label = { Text("Location (optional)") },
                        placeholder = { Text("Search or type a city or venue") },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_location_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuickSuggestedLocations.forEach { city ->
                            SuggestionChip(
                                onClick = { onLocationChange(city) },
                                label = { Text(city) },
                                modifier = Modifier.testTag("location_suggestion_$city")
                            )
                        }
                    }
                }
            }

            if (format == CreateFormat.REEL || isVideo) {
                HorizontalDivider(thickness = 0.5.dp, color = InstagramHairline)
                OutlinedTextField(
                    value = audioLabel,
                    onValueChange = onAudioLabelChange,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("create_audio_input")
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onShareClick,
            enabled = canShare,
            colors = ButtonDefaults.buttonColors(
                containerColor = InstagramBlue,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(50.dp)
                .testTag("create_bottom_share_button")
        ) {
            Text(
                text = if (isPublishing) "Sharing…" else "Share",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
