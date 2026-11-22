package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.model.InAppInstallStage
import com.example.data.model.UpdateErrorState
import com.example.data.model.UpdatePriority
import com.example.ui.theme.esosaGlassColors

@Composable
fun GlassUpdatePromptOverlay(
    visible: Boolean,
    update: AppUpdateInfo?,
    installStage: InAppInstallStage,
    errorState: UpdateErrorState?,
    installedVersionName: String = BuildConfig.VERSION_NAME,
    installedVersionCode: Int = BuildConfig.VERSION_CODE,
    onUpdateNow: () -> Unit,
    onRemindMeLater: () -> Unit,
    onSkipVersion: () -> Unit,
    onRestartAndInstall: () -> Unit,
    onRetry: () -> Unit,
    onOpenFallbackUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = MaterialTheme.esosaGlassColors
    val isMaintenance = installStage is InAppInstallStage.MaintenanceBlocked
    val isCritical = update?.effectiveIsCritical == true || isMaintenance

    AnimatedVisibility(
        visible = visible && (update != null || errorState != null || isMaintenance),
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(initialScale = 0.94f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
            scaleOut(targetScale = 0.95f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(glass.backdropScrim)
                .pointerInput(isCritical) {
                    detectTapGestures {
                        if (!isCritical) onDismiss()
                    }
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .testTag("glass_update_overlay"),
            contentAlignment = Alignment.Center
        ) {
            // Ambient glass glow backdrop
            Box(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures { /* consume tap inside card */ }
                    }
            ) {
                val cardShape = RoundedCornerShape(28.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(glass.surfaceTop, glass.surfaceBottom)
                            )
                        )
                        .background(
                            Brush.radialGradient(
                                colors = listOf(glass.ambientGlowPrimary, Color.Transparent),
                                radius = 720f
                            )
                        )
                        .border(1.2.dp, glass.specularBorder, cardShape)
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp)
                        .testTag("app_update_dialog"),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header: App Logo + Priority Badge + Dismiss (when not critical)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        )
                                    )
                                    .border(1.dp, glass.specularBorder, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                EsosaBrandLogo(size = 38.dp)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (update != null) {
                                    PriorityGlassBadge(
                                        priority = update.priority,
                                        isMaintenance = isMaintenance
                                    )
                                }
                                Text(
                                    text = if (isMaintenance) "Service Maintenance"
                                    else "A New Update Is Available",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("glass_update_heading")
                                )
                            }
                        }

                        if (!isCritical) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_glass_update_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close update notification",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Version Comparison Glass Pill
                    if (update != null) {
                        Surface(
                            color = glass.innerSectionSurface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, glass.specularBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Installed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "v$installedVersionName ($installedVersionCode)",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("installed_version_label")
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (update.releaseDate.isNotBlank()) "Latest • ${update.releaseDate}" else "Latest Release",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "v${update.versionName}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("latest_version_label")
                                    )
                                }
                            }
                        }
                    }

                    // Critical / Maintenance Explanation Callout
                    if (isMaintenance && installStage is InAppInstallStage.MaintenanceBlocked) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = installStage.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    } else if (isCritical && update != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "This critical update includes essential security & stability fixes required to continue using Esosa.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Short Summary & Release Breakdown (What's New / Features / Bug Fixes)
                    if (update != null && !isMaintenance) {
                        Surface(
                            color = glass.innerSectionSurface,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, glass.specularBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "What's New",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = update.releaseNotes.ifBlank {
                                        "Performance improvements, reliability upgrades, and security enhancements."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (update.newFeatures.isNotEmpty()) {
                                    Text(
                                        text = "New Features",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    update.newFeatures.forEach { feature ->
                                        ReleaseBulletRow(
                                            icon = Icons.Filled.AutoAwesome,
                                            iconTint = MaterialTheme.colorScheme.secondary,
                                            text = feature
                                        )
                                    }
                                }

                                if (update.bugFixes.isNotEmpty()) {
                                    Text(
                                        text = "Bug Fixes & Improvements",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    update.bugFixes.forEach { fix ->
                                        ReleaseBulletRow(
                                            icon = Icons.Filled.Build,
                                            iconTint = MaterialTheme.colorScheme.primary,
                                            text = fix
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Download Progress or Ready-to-Install Status
                    when (installStage) {
                        is InAppInstallStage.Downloading -> {
                            Surface(
                                color = glass.innerSectionSurface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val percent = (installStage.progress * 100).toInt()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Downloading verified update…",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = if (installStage.totalBytesToDownload > 0L) "$percent%" else "Starting",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (installStage.totalBytesToDownload > 0L) {
                                        LinearProgressIndicator(
                                            progress = { installStage.progress },
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }
                        }

                        is InAppInstallStage.DownloadedReadyToInstall -> {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "Download complete! Tap Restart and Install to finish updating.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        is InAppInstallStage.Installing -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Installing verified update…", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        else -> Unit
                    }

                    // Actionable Error Card
                    if (errorState != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_error_banner")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = errorState.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    text = errorState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (errorState.canRetry) {
                                        OutlinedButton(
                                            onClick = onRetry,
                                            modifier = Modifier.testTag("retry_update_button")
                                        ) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Retry")
                                        }
                                    }
                                    if (errorState.hasFallbackAction && update != null && update.downloadUrl.startsWith("https://")) {
                                        TextButton(
                                            onClick = { onOpenFallbackUrl(update.downloadUrl) }
                                        ) {
                                            Text("Open Release Page")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Primary & Secondary Buttons
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (installStage) {
                            is InAppInstallStage.DownloadedReadyToInstall -> {
                                Button(
                                    onClick = onRestartAndInstall,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("restart_and_install_button")
                                ) {
                                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Restart and Install")
                                }
                            }

                            is InAppInstallStage.MaintenanceBlocked -> {
                                OutlinedButton(
                                    onClick = onRetry,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("retry_update_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Check Status Again")
                                }
                            }

                            else -> if (update != null) {
                                Button(
                                    onClick = onUpdateNow,
                                    enabled = update.downloadAvailable && installStage !is InAppInstallStage.Downloading,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCritical) glass.criticalAccent else MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("download_app_update_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (!update.downloadAvailable) "Staged for Rollout"
                                        else if (isCritical) "Install Required Update"
                                        else "Update Now"
                                    )
                                }
                            }
                        }

                        if (!isCritical && update != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onRemindMeLater,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("later_app_update_button")
                                ) {
                                    Text("Remind Me Later", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(
                                    onClick = onSkipVersion,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("skip_app_update_button")
                                ) {
                                    Text("Skip")
                                }
                            }
                        }
                    }

                    // Distribution & Security Footer
                    if (update != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Verified via ${update.sourceLabel} • HTTPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlexibleUpdateFloatingBar(
    installStage: InAppInstallStage,
    onRestartAndInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = installStage is InAppInstallStage.Downloading ||
        installStage is InAppInstallStage.DownloadedReadyToInstall ||
        installStage is InAppInstallStage.Installing
    val glass = MaterialTheme.esosaGlassColors

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = glass.surfaceTop,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, glass.specularBorder),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("flexible_update_bar")
        ) {
            when (installStage) {
                is InAppInstallStage.Downloading -> {
                    val pct = (installStage.progress * 100).toInt()
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text("Downloading Esosa update…", style = MaterialTheme.typography.labelLarge)
                            }
                            if (installStage.totalBytesToDownload > 0L) {
                                Text("$pct%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Update downloaded & ready",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Button(
                            onClick = onRestartAndInstall,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("bar_restart_and_install_button")
                        ) {
                            Text("Restart & Install")
                        }
                    }
                }

                is InAppInstallStage.Installing -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Restarting to apply update…", style = MaterialTheme.typography.labelLarge)
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun PriorityGlassBadge(
    priority: UpdatePriority,
    isMaintenance: Boolean
) {
    val glass = MaterialTheme.esosaGlassColors
    val (bg, fg, label) = when {
        isMaintenance -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            "Maintenance Lock"
        )
        priority == UpdatePriority.CRITICAL -> Triple(
            glass.criticalAccent.copy(alpha = 0.16f),
            glass.criticalAccent,
            "Critical Update"
        )
        priority == UpdatePriority.RECOMMENDED -> Triple(
            glass.recommendedAccent.copy(alpha = 0.18f),
            glass.recommendedAccent,
            "Recommended"
        )
        else -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.primary,
            "Optional Update"
        )
    }

    Surface(
        color = bg,
        shape = CircleShape,
        modifier = Modifier.testTag("update_priority_badge")
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ReleaseBulletRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.padding(top = 2.dp).size(15.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
