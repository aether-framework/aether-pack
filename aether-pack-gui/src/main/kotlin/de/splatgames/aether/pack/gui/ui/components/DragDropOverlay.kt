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

package de.splatgames.aether.pack.gui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.icons.Icons
import com.konyaco.fluent.icons.regular.Archive
import com.konyaco.fluent.icons.regular.DocumentAdd
import de.splatgames.aether.pack.gui.i18n.I18n
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path

/**
 * Container with drag & drop support for APACK archives.
 * Shows a visual overlay when files are dragged over.
 *
 * @param onFilesDropped Callback when .apack files are dropped
 * @param i18n Internationalization provider
 * @param enabled Whether drag & drop is enabled (default true)
 * @param content The content to wrap
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun DragDropContainer(
    onFilesDropped: (List<Path>) -> Unit,
    i18n: I18n,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }

    val dragAndDropTarget = remember(enabled) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                if (enabled) isDragging = true
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragging = false
                if (!enabled) return false

                try {
                    val transferable = event.awtTransferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>

                        val apackFiles = files
                            .filter { it.extension.equals("apack", ignoreCase = true) }
                            .map { it.toPath() }

                        if (apackFiles.isNotEmpty()) {
                            onFilesDropped(apackFiles)
                            return true
                        }
                    }
                } catch (e: Exception) {
                    // Ignore drag & drop errors
                }

                return false
            }
        }
    }

    Box(
        modifier = modifier
            .dragAndDropTarget(
                shouldStartDragAndDrop = { enabled },
                target = dragAndDropTarget
            )
    ) {
        content()

        // Drag overlay (only when enabled)
        AnimatedVisibility(
            visible = isDragging && enabled,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FluentTheme.colors.background.mica.base.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 2.dp,
                            color = AetherColors.AccentPrimary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(FluentTheme.colors.background.card.default)
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Regular.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = AetherColors.AccentPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = i18n["dragdrop.drop_here"],
                        style = FluentTheme.typography.subtitle,
                        color = AetherColors.AccentPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = i18n["dragdrop.apack_only"],
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary
                    )
                }
            }
        }
    }
}

/**
 * Container with drag & drop support for any files.
 * Used in the Create Archive wizard.
 *
 * @param onFilesDropped Callback when files are dropped
 * @param i18n Internationalization provider
 * @param content The content to wrap
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun FileDragDropContainer(
    onFilesDropped: (List<Path>) -> Unit,
    i18n: I18n,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }

    // Use rememberUpdatedState to always have the latest callback
    val currentOnFilesDropped by rememberUpdatedState(onFilesDropped)

    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                isDragging = true
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragging = false

                try {
                    val transferable = event.awtTransferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>

                        val validFiles = files
                            .filter { it.exists() }
                            .map { it.toPath() }

                        if (validFiles.isNotEmpty()) {
                            currentOnFilesDropped(validFiles)
                            return true
                        }
                    }
                } catch (e: Exception) {
                    // Ignore drag & drop errors
                }

                return false
            }
        }
    }

    Box(
        modifier = modifier
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = dragAndDropTarget
            )
    ) {
        content()

        // Drag overlay
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FluentTheme.colors.background.mica.base.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 2.dp,
                            color = AetherColors.AccentPrimary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(FluentTheme.colors.background.card.default)
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Regular.DocumentAdd,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = AetherColors.AccentPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = i18n["dragdrop.drop_files_here"],
                        style = FluentTheme.typography.subtitle,
                        color = AetherColors.AccentPrimary
                    )
                }
            }
        }
    }
}
