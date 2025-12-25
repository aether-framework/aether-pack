/*
 * Copyright (c) 2025 Splatgames.de Software and Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.splatgames.aether.pack.gui.ui.screens.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.icons.Icons
import com.konyaco.fluent.icons.regular.*
import de.splatgames.aether.pack.gui.i18n.I18n
import de.splatgames.aether.pack.gui.navigation.Navigator
import de.splatgames.aether.pack.gui.navigation.Screen
import de.splatgames.aether.pack.gui.state.AppState
import de.splatgames.aether.pack.gui.state.RecentFile
import de.splatgames.aether.pack.gui.ui.components.FluentAccentButton
import de.splatgames.aether.pack.gui.ui.components.FluentCard
import de.splatgames.aether.pack.gui.ui.components.FluentHeroButton
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import de.splatgames.aether.pack.gui.ui.theme.FluentTokens
import de.splatgames.aether.pack.gui.ui.theme.animatedElevation
import de.splatgames.aether.pack.gui.util.FormatUtils
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Dashboard screen - the main entry point of the application.
 * Redesigned with modern Fluent Design System styling and Hero section.
 */
@Composable
fun DashboardScreen(
    appState: AppState,
    i18n: I18n,
    navigator: Navigator
) {
    var showFileNotFoundError by remember { mutableStateOf(false) }
    var deletedFileName by remember { mutableStateOf("") }

    // Error dialog for deleted files
    if (showFileNotFoundError) {
        FileNotFoundDialog(
            fileName = deletedFileName,
            i18n = i18n,
            onDismiss = { showFileNotFoundError = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FluentTokens.Spacing.xl)
    ) {
        // Hero Section
        HeroSection(
            i18n = i18n,
            onCreateClick = { navigator.navigate(Screen.CreateWizard) },
            onOpenClick = { openArchiveDialog(navigator) }
        )

        Spacer(modifier = Modifier.height(FluentTokens.Spacing.xxl))

        // Quick Actions Section
        Text(
            text = i18n["dashboard.quick_actions"],
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.height(FluentTokens.Spacing.md))

        Row(
            horizontalArrangement = Arrangement.spacedBy(FluentTokens.Spacing.lg)
        ) {
            QuickActionCard(
                icon = Icons.Regular.Add,
                title = i18n["dashboard.action.create"],
                description = i18n["dashboard.action.create.desc"],
                accentColor = AetherColors.AccentPrimary,
                onClick = { navigator.navigate(Screen.CreateWizard) },
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Regular.FolderOpen,
                title = i18n["dashboard.action.open"],
                description = i18n["dashboard.action.open.desc"],
                accentColor = AetherColors.Info,
                onClick = { openArchiveDialog(navigator) },
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Regular.Checkmark,
                title = i18n["dashboard.action.verify"],
                description = i18n["dashboard.action.verify.desc"],
                accentColor = AetherColors.Success,
                onClick = { openArchiveForVerify(navigator) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(FluentTokens.Spacing.xxl))

        // Recent Files Section
        Text(
            text = i18n["dashboard.recent_files"],
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.height(FluentTokens.Spacing.md))

        if (appState.recentFiles.isEmpty()) {
            RecentFilesEmptyState(i18n = i18n)
        } else {
            RecentFilesList(
                recentFiles = appState.recentFiles,
                i18n = i18n,
                onFileClick = { file ->
                    if (file.exists) {
                        navigator.navigate(Screen.Inspector(file.path))
                    } else {
                        deletedFileName = file.fileName
                        showFileNotFoundError = true
                    }
                },
                onFileRemove = { file ->
                    appState.removeRecentFile(file.path)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Hero section with gradient background and quick actions.
 */
@Composable
private fun HeroSection(
    i18n: I18n,
    onCreateClick: () -> Unit,
    onOpenClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FluentTokens.Components.heroHeight)
            .clip(RoundedCornerShape(FluentTokens.Corner.large))
            .animatedElevation(
                isHovered = false,
                restLevel = FluentTokens.Elevation.level2,
                hoverLevel = FluentTokens.Elevation.level2,
                cornerRadius = FluentTokens.Corner.large
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        AetherColors.GradientStart,
                        AetherColors.GradientEnd
                    )
                )
            )
    ) {
        // Decorative background icon
        Icon(
            imageVector = Icons.Regular.Archive,
            contentDescription = null,
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 40.dp)
                .alpha(0.1f),
            tint = Color.White
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(FluentTokens.Spacing.xl),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = i18n["dashboard.welcome"],
                style = FluentTheme.typography.title,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(FluentTokens.Spacing.xs))
            Text(
                text = i18n["dashboard.subtitle"],
                style = FluentTheme.typography.body,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(FluentTokens.Spacing.lg))

            Row(
                horizontalArrangement = Arrangement.spacedBy(FluentTokens.Spacing.md)
            ) {
                FluentHeroButton(onClick = onCreateClick) {
                    Icon(
                        imageVector = Icons.Regular.Add,
                        contentDescription = null,
                        modifier = Modifier.size(FluentTokens.Components.iconSizeSmall)
                    )
                    Text(
                        text = i18n["dashboard.action.create"],
                        modifier = Modifier.padding(start = FluentTokens.Spacing.sm)
                    )
                }
                FluentHeroButton(onClick = onOpenClick) {
                    Icon(
                        imageVector = Icons.Regular.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(FluentTokens.Components.iconSizeSmall)
                    )
                    Text(
                        text = i18n["dashboard.action.open"],
                        modifier = Modifier.padding(start = FluentTokens.Spacing.sm)
                    )
                }
            }
        }
    }
}

/**
 * Quick action card with animated hover effects.
 */
@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered)
            AetherColors.CardBackgroundHover
        else
            AetherColors.CardBackground,
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "quickActionBg"
    )

    // Animated accent bar width
    val accentBarWidth by animateDpAsState(
        targetValue = if (isHovered) 48.dp else 32.dp,
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "accentBarWidth"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(FluentTokens.Corner.medium))
            .animatedElevation(
                isHovered = isHovered,
                restLevel = FluentTokens.Elevation.level1,
                hoverLevel = FluentTokens.Elevation.level2
            )
            .background(backgroundColor)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .hoverable(interactionSource)
            .padding(FluentTokens.Spacing.lg)
    ) {
        // Animated accent bar at top
        Box(
            modifier = Modifier
                .width(accentBarWidth)
                .height(FluentTokens.Components.accentBarHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )

        Spacer(modifier = Modifier.height(FluentTokens.Spacing.md))

        // Icon in circular background
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(FluentTokens.Components.iconSizeMedium),
                tint = accentColor
            )
        }

        Spacer(modifier = Modifier.height(FluentTokens.Spacing.md))

        Text(
            text = title,
            style = FluentTheme.typography.bodyStrong
        )

        Spacer(modifier = Modifier.height(FluentTokens.Spacing.xs))

        Text(
            text = description,
            style = FluentTheme.typography.caption,
            color = FluentTheme.colors.text.text.secondary
        )
    }
}

/**
 * Empty state for recent files.
 */
@Composable
private fun RecentFilesEmptyState(i18n: I18n) {
    FluentCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FluentTokens.Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Regular.DocumentBulletList,
                contentDescription = null,
                modifier = Modifier.size(FluentTokens.Components.iconSizeHero),
                tint = FluentTheme.colors.text.text.disabled
            )
            Spacer(modifier = Modifier.height(FluentTokens.Spacing.md))
            Text(
                text = i18n["dashboard.no_recent_files"],
                style = FluentTheme.typography.body,
                color = FluentTheme.colors.text.text.secondary
            )
            Spacer(modifier = Modifier.height(FluentTokens.Spacing.xs))
            Text(
                text = i18n["dashboard.drop_hint"],
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.disabled
            )
        }
    }
}

/**
 * Recent files list with FluentCard container.
 */
@Composable
private fun RecentFilesList(
    recentFiles: List<RecentFile>,
    i18n: I18n,
    onFileClick: (RecentFile) -> Unit,
    onFileRemove: (RecentFile) -> Unit,
    modifier: Modifier = Modifier
) {
    FluentCard(
        modifier = modifier.fillMaxWidth(),
        elevation = FluentTokens.Elevation.level1
    ) {
        Column(modifier = Modifier.padding(0.dp)) {
            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FluentTheme.colors.subtleFill.secondary)
                    .padding(horizontal = FluentTokens.Spacing.lg, vertical = FluentTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = i18n["common.name"],
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
                    modifier = Modifier.weight(2f)
                )
                Text(
                    text = i18n["common.size"],
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = i18n["archive.entries"],
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(40.dp))
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FluentTokens.Components.dividerThickness)
                    .background(FluentTheme.colors.stroke.divider.default)
            )

            LazyColumn {
                items(recentFiles) { file ->
                    RecentFileRow(
                        file = file,
                        onClick = { onFileClick(file) },
                        onRemove = { onFileRemove(file) }
                    )
                    // Subtle divider between rows
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(FluentTokens.Components.dividerThickness)
                            .padding(horizontal = FluentTokens.Spacing.lg)
                            .background(FluentTheme.colors.stroke.divider.default.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentFileRow(
    file: RecentFile,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered)
            FluentTheme.colors.subtleFill.secondary
        else
            Color.Transparent,
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "recentFileBg"
    )

    val iconTint = if (file.exists) {
        AetherColors.AccentPrimary
    } else {
        AetherColors.Error
    }

    // Animated remove button opacity
    val removeButtonAlpha by animateColorAsState(
        targetValue = if (isHovered)
            FluentTheme.colors.text.text.secondary
        else
            Color.Transparent,
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "removeButtonAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .hoverable(interactionSource)
            .padding(horizontal = FluentTokens.Spacing.lg, vertical = FluentTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File Icon and Name
        Row(
            modifier = Modifier.weight(2f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Regular.Archive,
                contentDescription = null,
                modifier = Modifier.size(FluentTokens.Components.iconSizeMedium),
                tint = iconTint
            )
            Spacer(modifier = Modifier.width(FluentTokens.Spacing.md))
            Column {
                Text(
                    text = file.fileName,
                    style = FluentTheme.typography.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = file.directory.toString(),
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Size
        Text(
            text = FormatUtils.formatSize(file.fileSize),
            style = FluentTheme.typography.body,
            color = FluentTheme.colors.text.text.secondary,
            modifier = Modifier.weight(1f)
        )

        // Entry Count
        Text(
            text = file.entryCount.toString(),
            style = FluentTheme.typography.body,
            color = FluentTheme.colors.text.text.secondary,
            modifier = Modifier.weight(1f)
        )

        // Remove Button (always visible with animated opacity)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(FluentTokens.Corner.small))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Regular.Dismiss,
                contentDescription = "Remove",
                modifier = Modifier.size(FluentTokens.Components.iconSizeSmall),
                tint = removeButtonAlpha
            )
        }
    }
}

private fun openArchiveDialog(navigator: Navigator) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Open Archive"
        fileFilter = FileNameExtensionFilter("APACK Archives (*.apack)", "apack")
        isAcceptAllFileFilterUsed = false
    }

    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val path = chooser.selectedFile.toPath()
        navigator.navigate(Screen.Inspector(path))
    }
}

private fun openArchiveForVerify(navigator: Navigator) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select Archive to Verify"
        fileFilter = FileNameExtensionFilter("APACK Archives (*.apack)", "apack")
        isAcceptAllFileFilterUsed = false
    }

    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val path = chooser.selectedFile.toPath()
        navigator.navigate(Screen.VerifyWizard(path))
    }
}

@Composable
private fun FileNotFoundDialog(
    fileName: String,
    i18n: I18n,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(FluentTokens.Corner.medium))
                .animatedElevation(
                    isHovered = false,
                    restLevel = FluentTokens.Elevation.level4,
                    hoverLevel = FluentTokens.Elevation.level4
                )
                .background(FluentTheme.colors.background.solid.base)
                .padding(FluentTokens.Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Regular.ErrorCircle,
                contentDescription = null,
                modifier = Modifier.size(FluentTokens.Components.iconSizeHero),
                tint = AetherColors.Error
            )
            Spacer(modifier = Modifier.height(FluentTokens.Spacing.lg))
            Text(
                text = i18n["common.error"],
                style = FluentTheme.typography.subtitle
            )
            Spacer(modifier = Modifier.height(FluentTokens.Spacing.sm))
            Text(
                text = i18n["error.file_deleted_or_moved"],
                style = FluentTheme.typography.body,
                color = FluentTheme.colors.text.text.secondary
            )
            Spacer(modifier = Modifier.height(FluentTokens.Spacing.xs))
            Text(
                text = fileName,
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.disabled
            )
            Spacer(modifier = Modifier.height(FluentTokens.Spacing.xl))
            FluentAccentButton(onClick = onDismiss) {
                Text(i18n["common.ok"])
            }
        }
    }
}
