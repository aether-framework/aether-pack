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

package de.splatgames.aether.pack.gui.ui.screens.wizards.extract

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.FluentTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.konyaco.fluent.component.Button
import com.konyaco.fluent.component.Icon
import com.konyaco.fluent.component.Text
import com.konyaco.fluent.icons.Icons
import com.konyaco.fluent.icons.regular.*
import de.splatgames.aether.pack.gui.i18n.I18n
import de.splatgames.aether.pack.gui.navigation.Navigator
import de.splatgames.aether.pack.gui.state.AppState
import de.splatgames.aether.pack.gui.ui.components.FluentAccentButton
import de.splatgames.aether.pack.gui.ui.components.FluentSectionCard
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import de.splatgames.aether.pack.gui.ui.theme.FluentTokens
import de.splatgames.aether.pack.gui.util.ArchiveUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser

/**
 * Wizard for extracting files from an APACK archive.
 * Redesigned with Fluent Design System styling.
 */
@Composable
fun ExtractWizard(
    archivePath: Path,
    appState: AppState,
    i18n: I18n,
    navigator: Navigator
) {
    var outputDir by remember { mutableStateOf(appState.settings.defaultOutputDir) }
    var overwrite by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var isEncrypted by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var extractionComplete by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var extractionProgress by remember { mutableStateOf(0f) }
    var currentFileName by remember { mutableStateOf("") }
    var isProcessingLargeFile by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Check if archive is encrypted
    LaunchedEffect(archivePath) {
        withContext(Dispatchers.IO) {
            try {
                isEncrypted = ArchiveUtils.isEncrypted(archivePath)
            } catch (e: Exception) {
                // Will be handled during extraction
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FluentTheme.colors.background.solid.base)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = i18n["wizard.extract.title"],
                    style = FluentTheme.typography.subtitle
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Regular.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AetherColors.AccentPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = archivePath.fileName.toString(),
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.secondary
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = { navigator.goBack() }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Regular.Dismiss,
                    contentDescription = i18n["common.close"],
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(24.dp)
        ) {
            when {
                extractionComplete -> {
                    SuccessContent(i18n, onClose = { navigator.goBack() })
                }
                errorMessage != null -> {
                    ErrorContent(
                        message = errorMessage!!,
                        i18n = i18n,
                        onRetry = { errorMessage = null }
                    )
                }
                isExtracting -> {
                    ExtractingContent(
                        progress = extractionProgress,
                        currentEntry = currentFileName,
                        isIndeterminate = isProcessingLargeFile,
                        i18n = i18n
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                    ) {
                        // Output directory
                        FluentSectionCard(title = i18n["wizard.extract.output_dir"]) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = outputDir.toString(),
                                    onValueChange = { },
                                    readOnly = true,
                                    modifier = Modifier.weight(1f),
                                    colors = outlinedTextFieldColors()
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    val chooser = JFileChooser().apply {
                                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                        dialogTitle = i18n["wizard.extract.output_dir"]
                                    }
                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        outputDir = chooser.selectedFile.toPath()
                                    }
                                }) {
                                    Text(i18n["common.browse"])
                                }
                            }
                        }

                        // Password field if encrypted
                        if (isEncrypted) {
                            Spacer(modifier = Modifier.height(16.dp))
                            FluentSectionCard(title = i18n["wizard.extract.password_required"]) {
                                Text(
                                    text = i18n["wizard.extract.enter_password"],
                                    style = FluentTheme.typography.caption,
                                    color = FluentTheme.colors.text.text.secondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { androidx.compose.material3.Text(i18n["wizard.create.password"]) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = outlinedTextFieldColors()
                                )
                            }
                        }

                        // Options
                        Spacer(modifier = Modifier.height(16.dp))
                        FluentSectionCard(title = i18n["settings.behavior"]) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { overwrite = !overwrite }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = overwrite,
                                    onCheckedChange = { overwrite = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AetherColors.AccentPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = i18n["wizard.extract.overwrite"],
                                    style = FluentTheme.typography.body
                                )
                            }
                        }
                    }
                }
            }
        }

        // Footer
        if (!extractionComplete && errorMessage == null && !isExtracting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FluentTheme.colors.background.solid.base)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { navigator.goBack() }) {
                    Text(i18n["wizard.cancel"])
                }

                FluentAccentButton(
                    onClick = {
                        isExtracting = true
                        extractionProgress = 0f
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    // Use ArchiveUtils to open with proper compression support
                                    // Pass password for encrypted archives
                                    val passwordToUse = if (isEncrypted && password.isNotEmpty()) password else null
                                    ArchiveUtils.openArchive(archivePath, passwordToUse).use { reader ->
                                        val entries = reader.entries.toList()

                                        // Calculate total size for accurate progress tracking
                                        val totalBytes = entries.sumOf { it.originalSize }
                                        var processedBytes = 0L

                                        // Threshold for "large file" (10 MB)
                                        val largeFileThreshold = 10 * 1024 * 1024L

                                        entries.forEachIndexed { index, entry ->
                                            val entryOutputPath = outputDir.resolve(entry.name)
                                            Files.createDirectories(entryOutputPath.parent)

                                            if (!overwrite && Files.exists(entryOutputPath)) {
                                                processedBytes += entry.originalSize
                                                return@forEachIndexed
                                            }

                                            val isLarge = entry.originalSize > largeFileThreshold

                                            // Update progress before processing
                                            withContext(Dispatchers.Main) {
                                                currentFileName = entry.name
                                                isProcessingLargeFile = isLarge
                                                extractionProgress = if (totalBytes > 0) {
                                                    processedBytes.toFloat() / totalBytes
                                                } else {
                                                    index.toFloat() / entries.size
                                                }
                                            }

                                            // Small delay to allow UI to update
                                            kotlinx.coroutines.delay(10)

                                            reader.getInputStream(entry).use { input ->
                                                Files.newOutputStream(entryOutputPath).use { output ->
                                                    input.copyTo(output)
                                                }
                                            }

                                            // Update bytes processed after file is extracted
                                            processedBytes += entry.originalSize

                                            // Update progress after file is done
                                            withContext(Dispatchers.Main) {
                                                isProcessingLargeFile = false
                                                extractionProgress = if (totalBytes > 0) {
                                                    processedBytes.toFloat() / totalBytes
                                                } else {
                                                    (index + 1).toFloat() / entries.size
                                                }
                                            }
                                        }
                                    }
                                }
                                extractionComplete = true
                            } catch (e: Exception) {
                                // Check for common decryption errors and provide user-friendly messages
                                errorMessage = when {
                                    e.message?.contains("tag mismatch", ignoreCase = true) == true ||
                                    e.message?.contains("integrity check", ignoreCase = true) == true ||
                                    e.message?.contains("AEADBadTagException", ignoreCase = true) == true ||
                                    e.message?.contains("mac check", ignoreCase = true) == true ||
                                    e.message?.contains("authentication tag", ignoreCase = true) == true -> {
                                        i18n["error.wrong_password"]
                                    }
                                    e.message?.contains("unwrap", ignoreCase = true) == true ||
                                    e.message?.contains("InvalidKeyException", ignoreCase = true) == true -> {
                                        i18n["error.wrong_password"]
                                    }
                                    else -> e.message ?: i18n["error.unknown"]
                                }
                            } finally {
                                isExtracting = false
                                isProcessingLargeFile = false
                            }
                        }
                    },
                    enabled = !isEncrypted || password.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Regular.ArrowDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(i18n["inspector.toolbar.extract"])
                }
            }
        }
    }
}

@Composable
private fun ExtractingContent(
    progress: Float,
    currentEntry: String,
    isIndeterminate: Boolean,
    i18n: I18n
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            CircularProgressIndicator(
                color = AetherColors.AccentPrimary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = i18n["wizard.extract.extracting"],
                style = FluentTheme.typography.subtitle
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentEntry,
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.secondary
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (isIndeterminate) {
                // Show indeterminate progress for large files
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AetherColors.AccentPrimary,
                    trackColor = FluentTheme.colors.subtleFill.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = i18n["common.please_wait"],
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary
                )
            } else {
                // Show determinate progress
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = AetherColors.AccentPrimary,
                    trackColor = FluentTheme.colors.subtleFill.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(i18n: I18n, onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Regular.CheckmarkCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = AetherColors.Success
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = i18n["wizard.extract.success"],
                style = FluentTheme.typography.subtitle,
                color = AetherColors.Success
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onClose) {
                Text(i18n["common.close"])
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    i18n: I18n,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Regular.ErrorCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = AetherColors.Error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = i18n["wizard.extract.failed"],
                style = FluentTheme.typography.subtitle,
                color = AetherColors.Error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = FluentTheme.typography.body,
                color = FluentTheme.colors.text.text.secondary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text(i18n["wizard.back"])
            }
        }
    }
}

/**
 * Creates consistent OutlinedTextField colors for theme compatibility.
 */
@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FluentTheme.colors.text.text.primary,
    unfocusedTextColor = FluentTheme.colors.text.text.primary,
    focusedBorderColor = AetherColors.AccentPrimary,
    unfocusedBorderColor = FluentTheme.colors.stroke.control.default,
    focusedLabelColor = AetherColors.AccentPrimary,
    unfocusedLabelColor = FluentTheme.colors.text.text.secondary,
    cursorColor = AetherColors.AccentPrimary,
    focusedPlaceholderColor = FluentTheme.colors.text.text.secondary,
    unfocusedPlaceholderColor = FluentTheme.colors.text.text.secondary
)
