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
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import de.splatgames.aether.pack.gui.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Inspector screen for viewing and analyzing APACK archives.
 * Redesigned with Fluent Design System styling.
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
        // Toolbar
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
                Row(modifier = Modifier.fillMaxSize()) {
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
                            .width(320.dp)
                            .fillMaxHeight()
                            .background(FluentTheme.colors.background.solid.base)
                            .padding(16.dp)
                    ) {
                        ArchiveInfoPanel(archiveState!!, i18n)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(FluentTheme.colors.stroke.surface.default)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (selectedEntry != null) {
                            EntryDetailsPanel(selectedEntry!!, i18n)
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Regular.DocumentBulletList,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = FluentTheme.colors.text.text.disabled
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun InspectorToolbar(
    archivePath: Path,
    onBack: () -> Unit,
    onExtract: () -> Unit,
    onVerify: () -> Unit,
    i18n: I18n
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FluentTheme.colors.background.solid.base)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Regular.ArrowLeft,
                contentDescription = i18n["common.close"],
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Archive name
        Icon(
            imageVector = Icons.Regular.Archive,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
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
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(i18n["inspector.toolbar.extract"])
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(onClick = onVerify) {
            Icon(
                imageVector = Icons.Regular.Checkmark,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
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
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Section title
        Text(
            text = "${i18n["inspector.entries"]} (${entries.size})",
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Table container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(FluentTheme.colors.background.card.default)
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FluentTheme.colors.subtleFill.secondary)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    modifier = Modifier.width(60.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
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

    val backgroundColor = when {
        isSelected -> AetherColors.AccentPrimary.copy(alpha = 0.15f)
        isHovered -> FluentTheme.colors.subtleFill.secondary
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .then(
                if (isSelected) {
                    Modifier.drawBehind {
                        drawRect(
                            color = AetherColors.AccentPrimary,
                            topLeft = Offset.Zero,
                            size = Size(3.dp.toPx(), size.height)
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .hoverable(interactionSource)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) AetherColors.AccentPrimary else FluentTheme.colors.text.text.secondary
            )
            Spacer(modifier = Modifier.width(8.dp))
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

        // Flags - use actual IDs instead of flags to avoid inconsistency
        Row(
            modifier = Modifier.width(60.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
            .clip(RoundedCornerShape(2.dp))
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
    // Get compression algorithm from first entry with actual compression (compressionId != 0)
    val compressionAlgorithm = state.entries
        .firstOrNull { it.compressionId != 0 }
        ?.let { FormatUtils.getCompressionName(it.compressionId) }

    // Get encryption algorithm from first entry with actual encryption (encryptionId != 0)
    val encryptionAlgorithm = state.entries
        .firstOrNull { it.encryptionId != 0 }
        ?.let { FormatUtils.getEncryptionName(it.encryptionId) }

    // Count entries with actual compression/encryption (by ID, not flag)
    val hasCompression = state.entries.any { it.compressionId != 0 }
    val hasEncryption = state.entries.any { it.encryptionId != 0 }

    Column {
        Text(
            text = i18n["inspector.info"],
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(FluentTheme.colors.background.card.default)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoRow(i18n["archive.format"], "APACK ${state.formatVersion}")
            InfoRow(i18n["archive.entries"], state.entries.size.toString())
            InfoRow(i18n["archive.original_size"], FormatUtils.formatSize(state.totalOriginalSize))
            InfoRow(i18n["archive.stored_size"], FormatUtils.formatSize(state.totalStoredSize))
            InfoRow(i18n["archive.compression_ratio"], FormatUtils.formatRatio(state.compressionRatio))
            InfoRow(i18n["archive.chunk_size"], FormatUtils.formatChunkSize(state.fileHeader.chunkSize()))
            InfoRow(i18n["archive.checksum"], FormatUtils.getChecksumName(state.fileHeader.checksumAlgorithm()))

            // Status badges with algorithm names - only show if entries actually have compression/encryption
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = FluentTheme.typography.caption,
            color = color
        )
    }
}

@Composable
private fun EntryDetailsPanel(entry: Entry, i18n: I18n) {
    val ratio = if (entry.originalSize > 0) {
        entry.storedSize.toDouble() / entry.originalSize
    } else {
        1.0
    }

    Column {
        Text(
            text = i18n["inspector.details"],
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(FluentTheme.colors.background.card.default)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
            Spacer(modifier = Modifier.height(16.dp))
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
                modifier = Modifier.size(48.dp),
                tint = AetherColors.Error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = i18n["common.error"],
                style = FluentTheme.typography.subtitle,
                color = AetherColors.Error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = FluentTheme.typography.body,
                color = FluentTheme.colors.text.text.secondary
            )
        }
    }
}
