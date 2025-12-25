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

package de.splatgames.aether.pack.gui.ui.screens.inspector

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Button
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.icons.Icons
import com.konyaco.fluent.icons.regular.*
import de.splatgames.aether.pack.core.AetherPackReader
import de.splatgames.aether.pack.core.entry.Entry
import de.splatgames.aether.pack.gui.i18n.I18n
import de.splatgames.aether.pack.gui.navigation.Navigator
import de.splatgames.aether.pack.gui.navigation.Screen
import de.splatgames.aether.pack.gui.state.AppState
import de.splatgames.aether.pack.gui.state.ArchiveState
import de.splatgames.aether.pack.gui.ui.components.FluentCard
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import de.splatgames.aether.pack.gui.ui.theme.FluentTokens
import de.splatgames.aether.pack.gui.ui.theme.LocalAetherColors
import de.splatgames.aether.pack.gui.ui.theme.animatedElevation
import de.splatgames.aether.pack.gui.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Inspector screen for viewing and analyzing APACK archives.
 * Redesigned with modern Fluent Design System styling.
 */
@Composable
fun InspectorScreen(
    archivePath: Path,
    appState: AppState,
    i18n: I18n,
    navigator: Navigator
) {
    var archiveState by remember { mutableStateOf<ArchiveState?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedEntry by remember { mutableStateOf<Entry?>(null) }
    val scope = rememberCoroutineScope()

    // Load archive on first composition
    LaunchedEffect(archivePath) {
        scope.launch {
            try {
                val state = withContext(Dispatchers.IO) {
                    AetherPackReader.open(archivePath).use { reader ->
                        ArchiveState(
                            path = archivePath,
                            fileHeader = reader.fileHeader,
                            encryptionBlock = reader.encryptionBlock,
                            entries = reader.entries.toList()
                        )
                    }
                }
                archiveState = state
                appState.addRecentFile(archivePath, state.entries.size)
            } catch (e: Exception) {
                errorMessage = e.message ?: i18n["error.unknown"]
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar with subtle elevation
        InspectorToolbar(
            archivePath = archivePath,
            onBack = { navigator.goBack() },
            onExtract = { navigator.navigate(Screen.ExtractWizard(archivePath)) },
            onVerify = { navigator.navigate(Screen.VerifyWizard(archivePath)) },
            i18n = i18n
        )

        // Content
        when {
            errorMessage != null -> {
                ErrorContent(errorMessage!!, i18n)
            }
            archiveState == null -> {
                LoadingContent(i18n)
            }
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(FluentTokens.Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(FluentTokens.Spacing.lg)
                ) {
                    // Entry list (left panel)
                    EntryList(
                        entries = archiveState!!.entries,
                        selectedEntry = selectedEntry,
                        onSelectEntry = { selectedEntry = it },
                        i18n = i18n,
                        modifier = Modifier.weight(1f)
                    )

                    // Right panel - Archive info and entry details
                    Column(
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(FluentTokens.Spacing.lg)
                    ) {
                        ArchiveInfoPanel(archiveState!!, i18n)

                        if (selectedEntry != null) {
                            EntryDetailsPanel(selectedEntry!!, i18n)
                        } else {
                            // Empty state placeholder
                            FluentCard(
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Regular.DocumentBulletList,
                                            contentDescription = null,
                                            modifier = Modifier.size(FluentTokens.Components.iconSizeLarge),
                                            tint = FluentTheme.colors.text.text.disabled
                                        )
                                        Spacer(modifier = Modifier.height(FluentTokens.Spacing.sm))
                                        Text(
                                            text = i18n["inspector.select_entry"],
                                            style = FluentTheme.typography.body,
                                            color = FluentTheme.colors.text.text.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorToolbar(
    archivePath: Path,
    onBack: () -> Unit,
    onExtract: () -> Unit,
    onVerify: () -> Unit,
    i18n: I18n
) {
    val colors = LocalAetherColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backButtonBg by animateColorAsState(
        targetValue = if (isHovered)
            FluentTheme.colors.subtleFill.secondary
        else
            Color.Transparent,
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "backButtonBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animatedElevation(
                isHovered = false,
                restLevel = FluentTokens.Elevation.level1,
                hoverLevel = FluentTokens.Elevation.level1,
                cornerRadius = 0.dp
            )
            .background(colors.sidebarBackground)
            .padding(horizontal = FluentTokens.Spacing.lg, vertical = FluentTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(FluentTokens.Corner.small))
                .background(backButtonBg)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onBack
                )
                .hoverable(interactionSource),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Regular.ArrowLeft,
                contentDescription = i18n["common.close"],
                modifier = Modifier.size(FluentTokens.Components.iconSizeMedium)
            )
        }

        Spacer(modifier = Modifier.width(FluentTokens.Spacing.md))

        // Archive name with icon
        Icon(
            imageVector = Icons.Regular.Archive,
            contentDescription = null,
            modifier = Modifier.size(FluentTokens.Components.iconSizeMedium),
            tint = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.width(FluentTokens.Spacing.sm))
        Text(
            text = archivePath.fileName.toString(),
            style = FluentTheme.typography.subtitle,
            modifier = Modifier.weight(1f)
        )

        // Action buttons
        Button(onClick = onExtract) {
            Icon(
                imageVector = Icons.Regular.ArrowDownload,
                contentDescription = null,
                modifier = Modifier.size(FluentTokens.Components.iconSizeSmall)
            )
            Spacer(modifier = Modifier.width(FluentTokens.Spacing.sm))
            Text(i18n["inspector.toolbar.extract"])
        }

        Spacer(modifier = Modifier.width(FluentTokens.Spacing.sm))

        Button(onClick = onVerify) {
            Icon(
                imageVector = Icons.Regular.Checkmark,
                contentDescription = null,
                modifier = Modifier.size(FluentTokens.Components.iconSizeSmall)
            )
            Spacer(modifier = Modifier.width(FluentTokens.Spacing.sm))
            Text(i18n["inspector.toolbar.verify"])
        }
    }
}

@Composable
private fun EntryList(
    entries: List<Entry>,
    selectedEntry: Entry?,
    onSelectEntry: (Entry) -> Unit,
    i18n: I18n,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Section title
        Text(
            text = "${i18n["inspector.entries"]} (${entries.size})",
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )

        Spacer(modifier = Modifier.height(FluentTokens.Spacing.md))

        // Table container with FluentCard styling
        FluentCard(
            modifier = Modifier.fillMaxSize(),
            elevation = FluentTokens.Elevation.level1
        ) {
            Column(modifier = Modifier.padding(0.dp)) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FluentTheme.colors.subtleFill.secondary)
                        .padding(horizontal = FluentTokens.Spacing.lg, vertical = FluentTokens.Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = i18n["entry.name"],
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = i18n["entry.original_size"],
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary,
                        modifier = Modifier.width(90.dp)
                    )
                    Text(
                        text = i18n["entry.ratio"],
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary,
                        modifier = Modifier.width(60.dp)
                    )
                    Text(
                        text = "Flags",
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary,
                        modifier = Modifier.width(70.dp)
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FluentTokens.Components.dividerThickness)
                        .background(FluentTheme.colors.stroke.divider.default)
                )

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries) { entry ->
                        EntryListItem(
                            entry = entry,
                            isSelected = entry == selectedEntry,
                            onClick = { onSelectEntry(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryListItem(
    entry: Entry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val ratio = if (entry.originalSize > 0) {
        entry.storedSize.toDouble() / entry.originalSize
    } else {
        1.0
    }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> AetherColors.AccentPrimary.copy(alpha = 0.15f)
            isHovered -> FluentTheme.colors.subtleFill.secondary
            else -> Color.Transparent
        },
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "entryBg"
    )

    // Animated indicator width
    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) FluentTokens.Components.accentIndicatorWidth else 0.dp,
        animationSpec = tween(FluentTokens.Animation.fast),
        label = "indicatorWidth"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .drawBehind {
                if (indicatorWidth.toPx() > 0) {
                    drawRect(
                        color = AetherColors.AccentPrimary,
                        topLeft = Offset.Zero,
                        size = Size(indicatorWidth.toPx(), size.height)
                    )
                }
            }
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
        // File icon and name
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Regular.Document,
                contentDescription = null,
                modifier = Modifier.size(FluentTokens.Components.iconSizeSmall),
                tint = if (isSelected) AetherColors.AccentPrimary else FluentTheme.colors.text.text.secondary
            )
            Spacer(modifier = Modifier.width(FluentTokens.Spacing.sm))
            Text(
                text = entry.name,
                style = FluentTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Size
        Text(
            text = FormatUtils.formatSize(entry.originalSize),
            style = FluentTheme.typography.caption,
            color = FluentTheme.colors.text.text.secondary,
            modifier = Modifier.width(90.dp)
        )

        // Ratio
        Text(
            text = FormatUtils.formatRatio(ratio),
            style = FluentTheme.typography.caption,
            color = FluentTheme.colors.text.text.secondary,
            modifier = Modifier.width(60.dp)
        )

        // Flags
        Row(
            modifier = Modifier.width(70.dp),
            horizontalArrangement = Arrangement.spacedBy(FluentTokens.Spacing.xs)
        ) {
            if (entry.compressionId != 0) {
                FlagBadge("C", AetherColors.Compressed)
            }
            if (entry.encryptionId != 0) {
                FlagBadge("E", AetherColors.Encrypted)
            }
            if (entry.hasEcc()) {
                FlagBadge("R", AetherColors.Ecc)
            }
        }
    }
}

@Composable
private fun FlagBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(FluentTokens.Corner.small))
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = FluentTheme.typography.caption,
            color = color
        )
    }
}

@Composable
private fun ArchiveInfoPanel(state: ArchiveState, i18n: I18n) {
    // Get compression algorithm from first entry with actual compression
    val compressionAlgorithm = state.entries
        .firstOrNull { it.compressionId != 0 }
        ?.let { FormatUtils.getCompressionName(it.compressionId) }

    // Get encryption algorithm from first entry with actual encryption
    val encryptionAlgorithm = state.entries
        .firstOrNull { it.encryptionId != 0 }
        ?.let { FormatUtils.getEncryptionName(it.encryptionId) }

    val hasCompression = state.entries.any { it.compressionId != 0 }
    val hasEncryption = state.entries.any { it.encryptionId != 0 }

    Column {
        Text(
            text = i18n["inspector.info"],
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.height(FluentTokens.Spacing.md))

        FluentCard(elevation = FluentTokens.Elevation.level1) {
            Column(
                verticalArrangement = Arrangement.spacedBy(FluentTokens.Spacing.sm)
            ) {
                InfoRow(i18n["archive.format"], "APACK ${state.formatVersion}")
                InfoRow(i18n["archive.entries"], state.entries.size.toString())
                InfoRow(i18n["archive.original_size"], FormatUtils.formatSize(state.totalOriginalSize))
                InfoRow(i18n["archive.stored_size"], FormatUtils.formatSize(state.totalStoredSize))
                InfoRow(i18n["archive.compression_ratio"], FormatUtils.formatRatio(state.compressionRatio))
                InfoRow(i18n["archive.chunk_size"], FormatUtils.formatChunkSize(state.fileHeader.chunkSize()))
                InfoRow(i18n["archive.checksum"], FormatUtils.getChecksumName(state.fileHeader.checksumAlgorithm()))

                // Status badges with algorithm names
                if (hasEncryption || hasCompression) {
                    Spacer(modifier = Modifier.height(FluentTokens.Spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FluentTokens.Spacing.sm)
                    ) {
                        if (hasEncryption && encryptionAlgorithm != null) {
                            StatusBadge(encryptionAlgorithm, AetherColors.Encrypted)
                        }
                        if (hasCompression && compressionAlgorithm != null) {
                            StatusBadge(compressionAlgorithm, AetherColors.Compressed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(FluentTokens.Corner.small))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = FluentTokens.Spacing.sm, vertical = FluentTokens.Spacing.xs)
    ) {
        Text(
            text = label,
            style = FluentTheme.typography.caption,
            color = color
        )
    }
}

@Composable
private fun ColumnScope.EntryDetailsPanel(entry: Entry, i18n: I18n) {
    val ratio = if (entry.originalSize > 0) {
        entry.storedSize.toDouble() / entry.originalSize
    } else {
        1.0
    }

    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = i18n["inspector.details"],
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.height(FluentTokens.Spacing.md))

        FluentCard(
            modifier = Modifier.fillMaxSize(),
            elevation = FluentTokens.Elevation.level1
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(FluentTokens.Spacing.sm)
            ) {
                InfoRow(i18n["entry.name"], entry.name)
                if (entry.mimeType.isNotEmpty()) {
                    InfoRow(i18n["entry.mime_type"], entry.mimeType)
                }
                InfoRow(i18n["entry.original_size"], FormatUtils.formatSize(entry.originalSize))
                InfoRow(i18n["entry.stored_size"], FormatUtils.formatSize(entry.storedSize))
                InfoRow(i18n["entry.ratio"], FormatUtils.formatRatio(ratio))
                InfoRow(i18n["entry.chunks"], entry.chunkCount.toString())
                InfoRow(i18n["entry.compressed"], if (entry.compressionId != 0) {
                    FormatUtils.getCompressionName(entry.compressionId)
                } else {
                    i18n["flag.no"]
                })
                InfoRow(i18n["entry.encrypted"], if (entry.encryptionId != 0) {
                    FormatUtils.getEncryptionName(entry.encryptionId)
                } else {
                    i18n["flag.no"]
                })
                InfoRow(i18n["entry.ecc"], if (entry.hasEcc()) i18n["flag.yes"] else i18n["flag.no"])
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = FluentTheme.typography.caption,
            color = FluentTheme.colors.text.text.secondary
        )
        Text(
            text = value,
            style = FluentTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LoadingContent(i18n: I18n) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = AetherColors.AccentPrimary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(FluentTokens.Spacing.lg))
            Text(
                text = i18n["common.loading"],
                style = FluentTheme.typography.body,
                color = FluentTheme.colors.text.text.secondary
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, i18n: I18n) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Regular.ErrorCircle,
                contentDescription = null,
                modifier = Modifier.size(FluentTokens.Components.iconSizeHero),
                tint = AetherColors.Error
            )
            Spacer(modifier = Modifier.height(FluentTokens.Spacing.lg))
            Text(
                text = i18n["common.error"],
                style = FluentTheme.typography.subtitle,
                color = AetherColors.Error
            )
            Spacer(modifier = Modifier.height(FluentTokens.Spacing.sm))
            Text(
                text = message,
                style = FluentTheme.typography.body,
                color = FluentTheme.colors.text.text.secondary
            )
        }
    }
}
