package com.example.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.model.CreateFormat
import com.example.data.model.MediaCropSpec
import com.example.data.model.PostFilter
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.InstagramHairline
import java.util.Locale

enum class CreateStep {
    PICKER,
    EDIT,
    DETAILS
}

private data class AspectOption(
    val label: String,
    val ratio: Float,
    val tagSuffix: String
)

private val CropAspectOptions = listOf(
    AspectOption("1:1", 1f, "1_1"),
    AspectOption("4:5", 0.8f, "4_5"),
    AspectOption("4:3", 1.33f, "4_3"),
    AspectOption("16:9", 1.77f, "16_9")
)

@Composable
fun CreateMediaPickerStep(
    format: CreateFormat,
    selectedUri: Uri?,
    isVideo: Boolean,
    isPublishing: Boolean,
    cropSpec: MediaCropSpec,
    onSelectFormat: (CreateFormat) -> Unit,
    onOpenGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRecordVideo: () -> Unit,
    onClearMedia: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
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
                        .clickable(enabled = !isPublishing) { onSelectFormat(item) }
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
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }

        // Large Media Preview Canvas
        val previewRatio = when (format) {
            CreateFormat.REEL, CreateFormat.STORY -> 0.8f
            CreateFormat.POST -> cropSpec.targetAspectRatio.coerceIn(0.75f, 1.35f)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(previewRatio)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(0.75.dp, InstagramHairline, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selectedUri != null) {
                if (isVideo) {
                    CreateVideoPreviewBox(
                        videoUri = selectedUri,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("create_video_preview")
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = "Video selected",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Video",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                        }
                    }
                } else {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = "Selected photo preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("create_photo_preview")
                    )
                }

                IconButton(
                    onClick = onClearMedia,
                    enabled = !isPublishing,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("create_clear_media_button")
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove selected media",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Surface(
                        color = InstagramBlue.copy(alpha = 0.1f),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AddPhotoAlternate,
                                contentDescription = null,
                                tint = InstagramBlue,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Text(
                        text = when (format) {
                            CreateFormat.STORY -> "Pick a photo/video or write a text story"
                            CreateFormat.REEL -> "Choose a video or clip for your Reel"
                            CreateFormat.POST -> "Select a photo or video to share"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Crop frames, apply Instagram-style filters, tag friends, and add your location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Gallery Picker (Photo & Video)
        Button(
            onClick = onOpenGallery,
            enabled = !isPublishing,
            colors = ButtonDefaults.buttonColors(
                containerColor = InstagramBlue,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("create_pick_gallery_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Choose Photo or Video from Gallery",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        // Camera Photo + Camera Video side-by-side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onTakePhoto,
                enabled = !isPublishing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("create_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Take Photo", color = MaterialTheme.colorScheme.onBackground)
            }

            OutlinedButton(
                onClick = onRecordVideo,
                enabled = !isPublishing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("create_record_video_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Record Video", color = MaterialTheme.colorScheme.onBackground)
            }
        }

        if (selectedUri != null || format == CreateFormat.STORY) {
            Button(
                onClick = onContinue,
                enabled = !isPublishing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("create_picker_continue_button")
            ) {
                Text(
                    text = if (selectedUri != null) "Next: Crop & Filters" else "Next: Write Text Story",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun CreateCropAndFilterStep(
    mediaUri: Uri,
    isVideo: Boolean,
    cropSpec: MediaCropSpec,
    selectedFilter: PostFilter,
    onCropSpecChange: (MediaCropSpec) -> Unit,
    onSelectFilter: (PostFilter) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColorMatrix = remember(selectedFilter) {
        ColorMatrix(selectedFilter.colorMatrixValues())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Crop & Live Filter Studio Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(cropSpec.targetAspectRatio.coerceIn(0.65f, 1.8f))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .clipToBounds()
                    .testTag("create_crop_preview_frame")
            ) {
                val frameWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val frameHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                if (isVideo) {
                    CreateVideoPreviewBox(
                        videoUri = mediaUri,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Cover filter: ${selectedFilter.label}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                } else {
                    AsyncImage(
                        model = mediaUri,
                        contentDescription = "Crop and filter preview",
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(activeColorMatrix),
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(cropSpec.targetAspectRatio) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (cropSpec.scale * zoom).coerceIn(1f, 3f)
                                    val maxOffsetX = (frameWidthPx * (newScale - 1f) / 2f).coerceAtLeast(frameWidthPx * 0.25f)
                                    val maxOffsetY = (frameHeightPx * (newScale - 1f) / 2f).coerceAtLeast(frameHeightPx * 0.25f)
                                    val deltaXFraction = pan.x / maxOffsetX
                                    val deltaYFraction = pan.y / maxOffsetY
                                    onCropSpecChange(
                                        cropSpec.copy(
                                            scale = newScale,
                                            offsetXFraction = (cropSpec.offsetXFraction + deltaXFraction).coerceIn(-1f, 1f),
                                            offsetYFraction = (cropSpec.offsetYFraction + deltaYFraction).coerceIn(-1f, 1f)
                                        )
                                    )
                                }
                            }
                            .graphicsLayer {
                                val maxShiftX = (frameWidthPx * (cropSpec.scale - 1f) / 2f).coerceAtLeast(frameWidthPx * 0.12f)
                                val maxShiftY = (frameHeightPx * (cropSpec.scale - 1f) / 2f).coerceAtLeast(frameHeightPx * 0.12f)
                                scaleX = cropSpec.scale
                                scaleY = cropSpec.scale
                                translationX = cropSpec.offsetXFraction * maxShiftX
                                translationY = cropSpec.offsetYFraction * maxShiftY
                            }
                    )

                    // 3x3 Rule-of-thirds crop grid overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val lineColor = Color.White.copy(alpha = 0.36f)
                        val stroke = 1.dp.toPx()
                        for (i in 1..2) {
                            val x = size.width * (i / 3f)
                            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), stroke)
                            val y = size.height * (i / 3f)
                            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke)
                        }
                    }

                    // Crop controls badge row
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.62f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Crop,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1fx", cropSpec.scale),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                            }
                        }
                        if (cropSpec.scale > 1.01f || cropSpec.offsetXFraction != 0f || cropSpec.offsetYFraction != 0f) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.62f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .clickable {
                                        onCropSpecChange(
                                            cropSpec.copy(scale = 1f, offsetXFraction = 0f, offsetYFraction = 0f)
                                        )
                                    }
                                    .testTag("create_reset_crop_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Reset crop",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Reset",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Aspect Ratio Bar + Quick Zoom Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CropAspectOptions.forEach { option ->
                val isSelected = kotlin.math.abs(cropSpec.targetAspectRatio - option.ratio) < 0.04f
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onCropSpecChange(cropSpec.copy(targetAspectRatio = option.ratio))
                        }
                        .testTag("crop_ratio_${option.tagSuffix}")
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            if (!isVideo) {
                Spacer(Modifier.weight(1f))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val nextZoom = if (cropSpec.scale >= 2f) 1f else (cropSpec.scale + 0.5f).coerceAtMost(2.5f)
                            onCropSpecChange(cropSpec.copy(scale = nextZoom))
                        }
                        .testTag("create_zoom_step_button")
                ) {
                    Text(
                        text = if (cropSpec.scale >= 2f) "1x" else "Zoom +",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Basic Filters Strip
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isVideo) "Cover Filters" else "Filters",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("create_filter_strip")
            ) {
                items(PostFilter.entries, key = { it.id }) { filter ->
                    val isSelected = selectedFilter == filter
                    val thumbMatrix = remember(filter) { ColorMatrix(filter.colorMatrixValues()) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clickable { onSelectFilter(filter) }
                            .testTag("create_filter_${filter.id}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.75.dp,
                                    color = if (isSelected) InstagramBlue else InstagramHairline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVideo) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF405DE6), Color(0xFFE1306C), Color(0xFFFCAF45))
                                            )
                                        )
                                        .graphicsLayer { alpha = 0.85f },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filter.label.take(2).uppercase(Locale.US),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = mediaUri,
                                    contentDescription = filter.label,
                                    contentScale = ContentScale.Crop,
                                    colorFilter = ColorFilter.colorMatrix(thumbMatrix),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = if (isSelected) InstagramBlue else MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(
                containerColor = InstagramBlue,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp)
                .testTag("create_edit_continue_button")
        ) {
            Text(
                text = "Next: Caption & Tags",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun CreateVideoPreviewBox(
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        start()
                    }
                    setOnErrorListener { _, _, _ -> true }
                    setVideoURI(videoUri)
                }
            },
            update = { view ->
                if (!view.isPlaying) {
                    runCatching { view.start() }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Surface(
            color = Color.Black.copy(alpha = 0.4f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Looping preview",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagPeopleBottomSheet(
    selectedUsernames: List<String>,
    suggestedUsernames: List<String>,
    onToggleUsername: (String) -> Unit,
    onAddCustomUsername: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var usernameDraft by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("tag_people_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tag people",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tag_people_done_button")
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = InstagramBlue
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = InstagramHairline)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = usernameDraft,
                    onValueChange = { if (it.length <= 32) usernameDraft = it },
                    label = { Text("Enter @username") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = InstagramBlue,
                        unfocusedBorderColor = InstagramHairline
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tag_people_input")
                )
                Button(
                    onClick = {
                        val clean = usernameDraft.trim().removePrefix("@")
                        if (clean.isNotBlank()) {
                            onAddCustomUsername(clean)
                            usernameDraft = ""
                        }
                    },
                    enabled = usernameDraft.trim().removePrefix("@").isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InstagramBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("tag_people_add_button")
                ) {
                    Text("Add")
                }
            }

            if (selectedUsernames.isNotEmpty()) {
                Text(
                    text = "Tagged (${selectedUsernames.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedUsernames.forEach { tag ->
                        FilterChip(
                            selected = true,
                            onClick = { onToggleUsername(tag) },
                            label = { Text("@$tag") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove @$tag",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = InstagramBlue.copy(alpha = 0.14f),
                                selectedLabelColor = InstagramBlue
                            )
                        )
                    }
                }
            }

            val suggestions = (suggestedUsernames + listOf("maya.studio", "leo.motion", "aria.travels", "dev.kofi"))
                .map { it.trim().removePrefix("@") }
                .filter { it.isNotBlank() }
                .distinct()
                .take(10)

            if (suggestions.isNotEmpty()) {
                Text(
                    text = "Suggested people",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    suggestions.forEach { candidate ->
                        val isTagged = selectedUsernames.contains(candidate)
                        FilterChip(
                            selected = isTagged,
                            onClick = { onToggleUsername(candidate) },
                            label = { Text("@$candidate") },
                            leadingIcon = if (isTagged) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("tag_suggestion_$candidate")
                        )
                    }
                }
            }
        }
    }
}
