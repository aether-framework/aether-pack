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

package de.splatgames.aether.pack.gui.ui.screens.wizards.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import de.splatgames.aether.pack.gui.ui.components.FileDragDropContainer
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Wizard for creating new APACK archives.
 * Redesigned with Fluent Design System styling.
 *
 * State is persisted in AppState.createWizardState, allowing users to navigate away
 * during archive creation and return to see progress or completion status.
 */
@Composable
fun CreateArchiveWizard(
    appState: AppState,
    i18n: I18n,
    navigator: Navigator
) {
    val wizardState = appState.createWizardState

    val steps = listOf(
        i18n["wizard.create.step.files"],
        i18n["wizard.create.step.compression"],
        i18n["wizard.create.step.encryption"],
        i18n["wizard.create.step.output"]
    )

    // Function to start archive creation (uses application-level scope)
    val startCreation: () -> Unit = {
        wizardState.startCreation(i18n)
    }

    // Handle closing the wizard
    val handleClose: () -> Unit = {
        // Only reset if not currently creating and user explicitly closes
        if (!wizardState.isCreating) {
            wizardState.reset()
        }
        navigator.goBack()
    }

    // Handle closing after completion (reset state)
    val handleCloseComplete: () -> Unit = {
        wizardState.reset()
        navigator.goBack()
    }

    // Overwrite confirmation dialog
    if (wizardState.showOverwriteDialog) {
        FileExistsDialog(
            fileName = wizardState.outputPath?.fileName?.toString() ?: "",
            i18n = i18n,
            onOverwrite = { startCreation() },
            onChangeLocation = {
                wizardState.showOverwriteDialog = false
                // Open file chooser again
                val chooser = JFileChooser().apply {
                    dialogTitle = i18n["wizard.create.output_file"]
                    fileFilter = javax.swing.filechooser.FileNameExtensionFilter("APACK Archives (*.apack)", "apack")
                }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    var file = chooser.selectedFile
                    if (!file.name.endsWith(".apack")) {
                        file = java.io.File(file.absolutePath + ".apack")
                    }
                    wizardState.outputPath = file.toPath()
                }
            },
            onCancel = { wizardState.showOverwriteDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        WizardHeader(
            title = i18n["wizard.create.title"],
            currentStep = wizardState.currentStep,
            totalSteps = steps.size,
            stepName = steps[wizardState.currentStep],
            onClose = handleClose,
            i18n = i18n,
            showCloseButton = !wizardState.isCreating // Hide close button during creation
        )

        // Step Indicator
        StepIndicator(
            currentStep = wizardState.currentStep,
            steps = steps
        )

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(24.dp)
        ) {
            when {
                wizardState.creationComplete -> {
                    CreationSuccessContent(
                        outputPath = wizardState.outputPath!!,
                        i18n = i18n,
                        onClose = handleCloseComplete,
                        onOpenInExplorer = {
                            try {
                                // Open the folder containing the file and select it
                                val file = wizardState.outputPath!!.toFile()
                                if (java.awt.Desktop.isDesktopSupported()) {
                                    java.awt.Desktop.getDesktop().open(file.parentFile)
                                }
                            } catch (e: Exception) {
                                // Ignore errors opening explorer
                            }
                        },
                        onOpenInApp = {
                            val path = wizardState.outputPath!!
                            wizardState.reset()
                            navigator.navigate(de.splatgames.aether.pack.gui.navigation.Screen.Inspector(path))
                        }
                    )
                }
                wizardState.creationError != null -> {
                    CreationErrorContent(
                        message = wizardState.creationError!!,
                        i18n = i18n,
                        onRetry = {
                            wizardState.clearError()
                        }
                    )
                }
                wizardState.isCreating -> {
                    CreationProgressContent(
                        progress = wizardState.creationProgress,
                        currentFileName = wizardState.currentFileName,
                        isIndeterminate = wizardState.isProcessingLargeFile,
                        i18n = i18n
                    )
                }
                else -> when (wizardState.currentStep) {
                    0 -> FileSelectionStep(
                        selectedFiles = wizardState.selectedFiles,
                        onFilesChanged = { newFiles ->
                            wizardState.selectedFiles.clear()
                            wizardState.selectedFiles.addAll(newFiles)
                        },
                        onFileRemoved = { pathToRemove ->
                            wizardState.selectedFiles.removeAll { it.toString() == pathToRemove.toString() }
                        },
                        i18n = i18n
                    )
                    1 -> CompressionStep(
                        algorithm = wizardState.compressionAlgorithm,
                        onAlgorithmChanged = { wizardState.compressionAlgorithm = it },
                        level = wizardState.compressionLevel,
                        onLevelChanged = { wizardState.compressionLevel = it },
                        chunkSizeKb = wizardState.chunkSizeKb,
                        onChunkSizeChanged = { wizardState.chunkSizeKb = it },
                        i18n = i18n
                    )
                    2 -> EncryptionStep(
                        enabled = wizardState.enableEncryption,
                        onEnabledChanged = { wizardState.enableEncryption = it },
                        algorithm = wizardState.encryptionAlgorithm,
                        onAlgorithmChanged = { wizardState.encryptionAlgorithm = it },
                        password = wizardState.password,
                        onPasswordChanged = { wizardState.password = it },
                        confirmPassword = wizardState.confirmPassword,
                        onConfirmPasswordChanged = { wizardState.confirmPassword = it },
                        i18n = i18n
                    )
                    3 -> OutputStep(
                        outputPath = wizardState.outputPath,
                        onOutputPathChanged = { wizardState.outputPath = it },
                        selectedFiles = wizardState.selectedFiles,
                        compressionAlgorithm = wizardState.compressionAlgorithm,
                        compressionLevel = wizardState.compressionLevel,
                        enableEncryption = wizardState.enableEncryption,
                        encryptionAlgorithm = wizardState.encryptionAlgorithm,
                        i18n = i18n
                    )
                }
            }
        }

        // Footer - hide when creating or complete
        if (!wizardState.isCreating && !wizardState.creationComplete && wizardState.creationError == null) {
            WizardFooter(
                currentStep = wizardState.currentStep,
                totalSteps = steps.size,
                canGoNext = when (wizardState.currentStep) {
                    0 -> wizardState.selectedFiles.isNotEmpty()
                    2 -> !wizardState.enableEncryption || (wizardState.password.isNotEmpty() && wizardState.password == wizardState.confirmPassword)
                    3 -> wizardState.outputPath != null
                    else -> true
                },
                onBack = { wizardState.currentStep-- },
                onNext = { wizardState.currentStep++ },
                onCancel = handleClose,
                onFinish = {
                    // Check if file already exists
                    if (wizardState.outputPath != null && Files.exists(wizardState.outputPath!!)) {
                        wizardState.showOverwriteDialog = true
                    } else {
                        startCreation()
                    }
                },
                i18n = i18n
            )
        }
    }
}

@Composable
private fun WizardHeader(
    title: String,
    currentStep: Int,
    totalSteps: Int,
    stepName: String,
    onClose: () -> Unit,
    i18n: I18n,
    showCloseButton: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FluentTheme.colors.background.solid.base)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = FluentTheme.typography.subtitle
            )
            Text(
                text = "${i18n.format("wizard.step", currentStep + 1, totalSteps)}: $stepName",
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.secondary
            )
        }
        if (showCloseButton) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Regular.Dismiss,
                    contentDescription = i18n["common.close"],
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    steps: List<String>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FluentTheme.colors.background.solid.base)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        steps.forEachIndexed { index, _ ->
            val isCompleted = index < currentStep
            val isCurrent = index == currentStep

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            isCompleted -> AetherColors.AccentPrimary
                            isCurrent -> AetherColors.AccentPrimary.copy(alpha = 0.5f)
                            else -> FluentTheme.colors.subtleFill.secondary
                        }
                    )
            )
        }
    }
}

@Composable
private fun WizardFooter(
    currentStep: Int,
    totalSteps: Int,
    canGoNext: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    i18n: I18n
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FluentTheme.colors.background.solid.base)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onCancel) {
            Text(i18n["wizard.cancel"])
        }

        if (currentStep > 0) {
            Button(onClick = onBack) {
                Icon(
                    imageVector = Icons.Regular.ArrowLeft,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(i18n["wizard.back"])
            }
        }

        if (currentStep < totalSteps - 1) {
            AccentButton(
                onClick = onNext,
                enabled = canGoNext
            ) {
                Text(i18n["wizard.next"])
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Regular.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            AccentButton(
                onClick = onFinish,
                enabled = canGoNext
            ) {
                Icon(
                    imageVector = Icons.Regular.Checkmark,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(i18n["wizard.create"])
            }
        }
    }
}

@Composable
private fun FileSelectionStep(
    selectedFiles: List<Path>,
    onFilesChanged: (List<Path>) -> Unit,
    onFileRemoved: (Path) -> Unit,
    i18n: I18n
) {
    FileDragDropContainer(
        onFilesDropped = { droppedFiles ->
            val existingPaths = selectedFiles.map { it.toString() }.toSet()
            val newFiles = droppedFiles.filter { it.toString() !in existingPaths }
            if (newFiles.isNotEmpty()) {
                onFilesChanged(selectedFiles + newFiles)
            }
        },
        i18n = i18n,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val chooser = JFileChooser().apply {
                        isMultiSelectionEnabled = true
                        dialogTitle = i18n["wizard.create.add_files"]
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val existingPaths = selectedFiles.map { it.toString() }.toSet()
                        val newFiles = chooser.selectedFiles
                            .map { it.toPath() }
                            .filter { it.toString() !in existingPaths }
                        onFilesChanged(selectedFiles + newFiles)
                    }
                }) {
                    Icon(
                        imageVector = Icons.Regular.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(i18n["wizard.create.add_files"])
                }

                Button(onClick = {
                    val chooser = JFileChooser().apply {
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        dialogTitle = i18n["wizard.create.add_folder"]
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val newPath = chooser.selectedFile.toPath()
                        val existingPaths = selectedFiles.map { it.toString() }.toSet()
                        if (newPath.toString() !in existingPaths) {
                            onFilesChanged(selectedFiles + newPath)
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Regular.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(i18n["wizard.create.add_folder"])
                }

                if (selectedFiles.isNotEmpty()) {
                    Button(onClick = { onFilesChanged(emptyList()) }) {
                        Text(i18n["wizard.create.clear"])
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File list area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(FluentTheme.colors.background.card.default)
            ) {
                if (selectedFiles.isEmpty()) {
                    // Empty state with hint
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Regular.DocumentAdd,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = FluentTheme.colors.text.text.disabled
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = i18n["wizard.create.no_files"],
                                style = FluentTheme.typography.body,
                                color = FluentTheme.colors.text.text.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = i18n["wizard.create.drag_hint"],
                                style = FluentTheme.typography.caption,
                                color = FluentTheme.colors.text.text.disabled
                            )
                        }
                    }
                } else {
                    // File list
                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Text(
                            text = i18n.format("wizard.create.files_count", selectedFiles.size),
                            style = FluentTheme.typography.bodyStrong,
                            color = AetherColors.AccentPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                items = selectedFiles,
                                key = { it.toString() }
                            ) { path ->
                                FileListItem(
                                    path = path,
                                    onRemove = { onFileRemoved(path) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileListItem(
    path: Path,
    onRemove: () -> Unit
) {
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isRowHovered by rowInteractionSource.collectIsHoveredAsState()

    val buttonInteractionSource = remember { MutableInteractionSource() }
    val isButtonHovered by buttonInteractionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isRowHovered) FluentTheme.colors.subtleFill.secondary else Color.Transparent)
            .hoverable(rowInteractionSource)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (path.toFile().isDirectory) Icons.Regular.Folder else Icons.Regular.Document,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (path.toFile().isDirectory) AetherColors.Warning else FluentTheme.colors.text.text.secondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = path.fileName.toString(),
            style = FluentTheme.typography.body,
            modifier = Modifier.weight(1f)
        )
        // Always show remove button, highlight on hover
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isButtonHovered) FluentTheme.colors.subtleFill.tertiary else Color.Transparent)
                .hoverable(buttonInteractionSource)
                .clickable(
                    interactionSource = buttonInteractionSource,
                    indication = null,
                    onClick = onRemove
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Regular.Dismiss,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isButtonHovered) AetherColors.Error else FluentTheme.colors.text.text.disabled
            )
        }
    }
}

@Composable
private fun CompressionStep(
    algorithm: String,
    onAlgorithmChanged: (String) -> Unit,
    level: Int,
    onLevelChanged: (Int) -> Unit,
    chunkSizeKb: Int,
    onChunkSizeChanged: (Int) -> Unit,
    i18n: I18n
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
    ) {
        SectionCard(title = i18n["wizard.create.algorithm"]) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("zstd" to "ZSTD", "lz4" to "LZ4", "none" to i18n["compression.none"]).forEach { (value, label) ->
                    SelectableChip(
                        selected = algorithm == value,
                        onClick = { onAlgorithmChanged(value) },
                        label = label
                    )
                }
            }
        }

        if (algorithm != "none") {
            Spacer(modifier = Modifier.height(16.dp))
            SectionCard(title = "${i18n["wizard.create.level"]}: $level") {
                val maxLevel = if (algorithm == "zstd") 22 else 17
                Slider(
                    value = level.toFloat(),
                    onValueChange = { onLevelChanged(it.toInt()) },
                    valueRange = 1f..maxLevel.toFloat(),
                    steps = maxLevel - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = AetherColors.AccentPrimary,
                        activeTrackColor = AetherColors.AccentPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionCard(title = i18n["wizard.create.chunk_size"]) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(64, 128, 256, 512, 1024).forEach { size ->
                    SelectableChip(
                        selected = chunkSizeKb == size,
                        onClick = { onChunkSizeChanged(size) },
                        label = if (size >= 1024) "${size / 1024} MB" else "$size KB"
                    )
                }
            }
        }
    }
}

@Composable
private fun EncryptionStep(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    algorithm: String,
    onAlgorithmChanged: (String) -> Unit,
    password: String,
    onPasswordChanged: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChanged: (String) -> Unit,
    i18n: I18n
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(FluentTheme.colors.background.card.default)
                .clickable { onEnabledChanged(!enabled) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
                colors = CheckboxDefaults.colors(
                    checkedColor = AetherColors.AccentPrimary
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = i18n["wizard.create.enable_encryption"],
                    style = FluentTheme.typography.bodyStrong
                )
                Text(
                    text = i18n["wizard.create.step.encryption.desc"],
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary
                )
            }
        }

        if (enabled) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionCard(title = i18n["wizard.create.algorithm"]) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "aes-256-gcm" to "AES-256-GCM",
                        "chacha20-poly1305" to "ChaCha20-Poly1305"
                    ).forEach { (value, label) ->
                        SelectableChip(
                            selected = algorithm == value,
                            onClick = { onAlgorithmChanged(value) },
                            label = label
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionCard(title = i18n["wizard.create.password"]) {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    label = { androidx.compose.material3.Text(i18n["wizard.create.password"]) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = outlinedTextFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = onConfirmPasswordChanged,
                    label = { androidx.compose.material3.Text(i18n["wizard.create.confirm_password"]) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                    colors = outlinedTextFieldColors()
                )
                if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = i18n["wizard.create.password_mismatch"],
                        style = FluentTheme.typography.caption,
                        color = AetherColors.Error
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputStep(
    outputPath: Path?,
    onOutputPathChanged: (Path?) -> Unit,
    selectedFiles: List<Path>,
    compressionAlgorithm: String,
    compressionLevel: Int,
    enableEncryption: Boolean,
    encryptionAlgorithm: String,
    i18n: I18n
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
    ) {
        SectionCard(title = i18n["wizard.create.output_file"]) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = outputPath?.toString() ?: "",
                    onValueChange = { },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    placeholder = { androidx.compose.material3.Text(i18n["wizard.create.browse"]) },
                    colors = outlinedTextFieldColors()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val chooser = JFileChooser().apply {
                        dialogTitle = i18n["wizard.create.output_file"]
                        fileFilter = FileNameExtensionFilter("APACK Archives (*.apack)", "apack")
                    }
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        var file = chooser.selectedFile
                        if (!file.name.endsWith(".apack")) {
                            file = java.io.File(file.absolutePath + ".apack")
                        }
                        onOutputPathChanged(file.toPath())
                    }
                }) {
                    Text(i18n["wizard.create.browse"])
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionCard(title = i18n["wizard.create.summary"]) {
            SummaryRow(i18n["archive.entries"], selectedFiles.size.toString())
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow(
                i18n["archive.compressed"],
                if (compressionAlgorithm == "none") i18n["flag.no"]
                else "$compressionAlgorithm (Level $compressionLevel)"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow(
                i18n["archive.encrypted"],
                if (enableEncryption) encryptionAlgorithm else i18n["flag.no"]
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(FluentTheme.colors.background.card.default)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = FluentTheme.typography.bodyStrong,
            color = AetherColors.AccentPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SelectableChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    val backgroundColor = if (selected) AetherColors.AccentPrimary else FluentTheme.colors.subtleFill.secondary
    val textColor = if (selected) Color.White else FluentTheme.colors.text.text.primary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = FluentTheme.typography.body,
            color = textColor
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = FluentTheme.typography.body,
            color = FluentTheme.colors.text.text.secondary
        )
        Text(
            text = value,
            style = FluentTheme.typography.body
        )
    }
}

@Composable
private fun CreationProgressContent(
    progress: Float,
    currentFileName: String,
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
                text = i18n["wizard.create.creating"],
                style = FluentTheme.typography.subtitle
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentFileName,
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

            // Hint that user can navigate away
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = i18n["wizard.create.background_hint"],
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.disabled
            )
        }
    }
}

@Composable
private fun CreationSuccessContent(
    outputPath: Path,
    i18n: I18n,
    onClose: () -> Unit,
    onOpenInExplorer: () -> Unit,
    onOpenInApp: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Icon(
                imageVector = Icons.Regular.CheckmarkCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = AetherColors.Success
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = i18n["wizard.create.success"],
                style = FluentTheme.typography.subtitle,
                color = AetherColors.Success
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = outputPath.fileName.toString(),
                style = FluentTheme.typography.body,
                color = FluentTheme.colors.text.text.secondary
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Primary action - Open in App (most prominent)
            AccentButton(
                onClick = onOpenInApp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Regular.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(i18n["wizard.create.open_in_app"])
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenInExplorer,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Regular.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(i18n["wizard.create.open_in_explorer"])
                }

                Button(
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(i18n["common.close"])
                }
            }
        }
    }
}

@Composable
private fun AccentButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val backgroundColor = if (enabled) AetherColors.AccentPrimary else AetherColors.AccentPrimary.copy(alpha = 0.5f)

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides Color.White,
        com.konyaco.fluent.LocalContentColor provides Color.White
    ) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun CreationErrorContent(
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
                text = i18n["wizard.create.failed"],
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

@Composable
private fun FileExistsDialog(
    fileName: String,
    i18n: I18n,
    onOverwrite: () -> Unit,
    onChangeLocation: () -> Unit,
    onCancel: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(FluentTheme.colors.background.solid.base)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Regular.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = AetherColors.Warning
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = i18n["dialog.file_exists.title"],
                    style = FluentTheme.typography.subtitle
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = i18n.format("dialog.file_exists.message", fileName),
                style = FluentTheme.typography.body,
                color = FluentTheme.colors.text.text.secondary
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DialogButton(onClick = onCancel) {
                    Text(i18n["common.cancel"])
                }
                DialogButton(onClick = onChangeLocation) {
                    Icon(
                        imageVector = Icons.Regular.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(i18n["dialog.file_exists.change_location"])
                }
                DialogAccentButton(onClick = onOverwrite) {
                    Icon(
                        imageVector = Icons.Regular.ArrowSync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(i18n["dialog.file_exists.overwrite"])
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor = when {
        !enabled -> FluentTheme.colors.subtleFill.disabled
        isHovered -> FluentTheme.colors.subtleFill.tertiary
        else -> FluentTheme.colors.subtleFill.secondary
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
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
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
