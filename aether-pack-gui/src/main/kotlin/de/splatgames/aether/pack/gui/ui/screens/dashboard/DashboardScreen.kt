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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import de.splatgames.aether.pack.gui.util.FormatUtils
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Dashboard screen - the main entry point of the application.
 * Redesigned with Fluent Design System styling.
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
            .padding(24.dp)
    ) {
        // Welcome Header
        Text(
            text = i18n["dashboard.welcome"],
            style = FluentTheme.typography.title
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = i18n["dashboard.subtitle"],
            style = FluentTheme.typography.body,
            color = FluentTheme.colors.text.text.secondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Quick Actions Section
        Text(
            text = i18n["dashboard.quick_actions"],
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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

        Spacer(modifier = Modifier.height(32.dp))

        // Recent Files Section
        Text(
            text = i18n["dashboard.recent_files"],
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (appState.recentFiles.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(FluentTheme.colors.background.card.default),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Regular.DocumentBulletList,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = FluentTheme.colors.text.text.disabled
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = i18n["dashboard.no_recent_files"],
                        style = FluentTheme.typography.body,
                        color = FluentTheme.colors.text.text.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = i18n["dashboard.drop_hint"],
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.disabled
                    )
                }
            }
        } else {
            // Recent Files Table
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(FluentTheme.colors.background.card.default)
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FluentTheme.colors.subtleFill.secondary)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    Spacer(modifier = Modifier.width(40.dp)) // Space for remove button
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(appState.recentFiles) { file ->
                        RecentFileRow(
                            file = file,
                            onClick = {
                                if (file.exists) {
                                    navigator.navigate(Screen.Inspector(file.path))
                                } else {
                                    deletedFileName = file.fileName
                                    showFileNotFoundError = true
                                }
                            },
                            onRemove = {
                                appState.removeRecentFile(file.path)
                            }
                        )
                    }
                }
            }
        }
    }
}

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

    val backgroundColor = if (isHovered) {
        FluentTheme.colors.subtleFill.tertiary
    } else {
        FluentTheme.colors.background.card.default
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .hoverable(interactionSource)
            .padding(16.dp)
    ) {
        // Accent bar at top
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = accentColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = FluentTheme.typography.bodyStrong
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            style = FluentTheme.typography.caption,
            color = FluentTheme.colors.text.text.secondary
        )
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

    val backgroundColor = if (isHovered) {
        FluentTheme.colors.subtleFill.secondary
    } else {
        Color.Transparent
    }

    val iconTint = if (file.exists) {
        AetherColors.AccentPrimary
    } else {
        AetherColors.Error
    }

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
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                modifier = Modifier.size(20.dp),
                tint = iconTint
            )
            Spacer(modifier = Modifier.width(12.dp))
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

        // Remove Button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            if (isHovered) {
                Icon(
                    imageVector = Icons.Regular.Dismiss,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = FluentTheme.colors.text.text.secondary
                )
            }
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
                .clip(RoundedCornerShape(8.dp))
                .background(FluentTheme.colors.background.solid.base)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Regular.ErrorCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = AetherColors.Error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = i18n["common.error"],
                style = FluentTheme.typography.subtitle
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = i18n["error.file_deleted_or_moved"],
                style = FluentTheme.typography.body,
                color = FluentTheme.colors.text.text.secondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = fileName,
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.disabled
            )
            Spacer(modifier = Modifier.height(24.dp))
            DialogAccentButton(onClick = onDismiss) {
                Text(i18n["common.ok"])
            }
        }
    }
}

@Composable
private fun DialogAccentButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor = when {
        !enabled -> AetherColors.AccentPrimary.copy(alpha = 0.5f)
        isHovered -> AetherColors.AccentHover
        else -> AetherColors.AccentPrimary
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides Color.White,
        com.konyaco.fluent.LocalContentColor provides Color.White
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .pointerHoverIcon(PointerIcon.Hand)
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
