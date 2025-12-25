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
import de.splatgames.aether.pack.compression.CompressionRegistry
import de.splatgames.aether.pack.core.AetherPackWriter
import de.splatgames.aether.pack.core.ApackConfiguration
import de.splatgames.aether.pack.core.format.EncryptionBlock
import de.splatgames.aether.pack.core.format.FormatConstants
import de.splatgames.aether.pack.crypto.Argon2idKeyDerivation
import de.splatgames.aether.pack.crypto.EncryptionRegistry
import de.splatgames.aether.pack.crypto.KeyWrapper
import de.splatgames.aether.pack.gui.i18n.I18n
import de.splatgames.aether.pack.gui.navigation.Navigator
import de.splatgames.aether.pack.gui.state.AppState
import de.splatgames.aether.pack.gui.ui.theme.AetherColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Wizard for creating new APACK archives.
 * Redesigned with Fluent Design System styling.
 */
@Composable
fun CreateArchiveWizard(
    appState: AppState,
    i18n: I18n,
    navigator: Navigator
) {
    var currentStep by remember { mutableStateOf(0) }
    var selectedFiles by remember { mutableStateOf<List<Path>>(emptyList()) }
    var compressionAlgorithm by remember { mutableStateOf(appState.settings.defaultCompression) }
    var compressionLevel by remember { mutableStateOf(appState.settings.defaultCompressionLevel) }
    var chunkSizeKb by remember { mutableStateOf(appState.settings.defaultChunkSizeKb) }
    var enableEncryption by remember { mutableStateOf(false) }
    var encryptionAlgorithm by remember { mutableStateOf("aes-256-gcm") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var outputPath by remember { mutableStateOf<Path?>(null) }

    // Creation state
    var isCreating by remember { mutableStateOf(false) }
    var creationComplete by remember { mutableStateOf(false) }
    var creationError by remember { mutableStateOf<String?>(null) }
    var creationProgress by remember { mutableStateOf(0f) }
    var currentFileName by remember { mutableStateOf("") }
    var isProcessingLargeFile by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val steps = listOf(
        i18n["wizard.create.step.files"],
        i18n["wizard.create.step.compression"],
        i18n["wizard.create.step.encryption"],
        i18n["wizard.create.step.output"]
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        WizardHeader(
            title = i18n["wizard.create.title"],
            currentStep = currentStep,
            totalSteps = steps.size,
            stepName = steps[currentStep],
            onClose = { navigator.goBack() },
            i18n = i18n
        )

        // Step Indicator
        StepIndicator(
            currentStep = currentStep,
            steps = steps
        )

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(24.dp)
        ) {
            when {
                creationComplete -> {
                    CreationSuccessContent(
                        outputPath = outputPath!!,
                        i18n = i18n,
                        onClose = { navigator.goBack() },
                        onOpenInExplorer = {
                            try {
                                // Open the folder containing the file and select it
                                val file = outputPath!!.toFile()
                                if (java.awt.Desktop.isDesktopSupported()) {
                                    java.awt.Desktop.getDesktop().open(file.parentFile)
                                }
                            } catch (e: Exception) {
                                // Ignore errors opening explorer
                            }
                        },
                        onOpenInApp = {
                            navigator.navigate(de.splatgames.aether.pack.gui.navigation.Screen.Inspector(outputPath!!))
                        }
                    )
                }
                creationError != null -> {
                    CreationErrorContent(
                        message = creationError!!,
                        i18n = i18n,
                        onRetry = {
                            creationError = null
                            isCreating = false
                        }
                    )
                }
                isCreating -> {
                    CreationProgressContent(
                        progress = creationProgress,
                        currentFileName = currentFileName,
                        isIndeterminate = isProcessingLargeFile,
                        i18n = i18n
                    )
                }
                else -> when (currentStep) {
                    0 -> FileSelectionStep(
                        selectedFiles = selectedFiles,
                        onFilesChanged = { selectedFiles = it },
                        onFileRemoved = { pathToRemove ->
                            selectedFiles = selectedFiles.filter { it.toString() != pathToRemove.toString() }
                        },
                        i18n = i18n
                    )
                    1 -> CompressionStep(
                        algorithm = compressionAlgorithm,
                        onAlgorithmChanged = { compressionAlgorithm = it },
                        level = compressionLevel,
                        onLevelChanged = { compressionLevel = it },
                        chunkSizeKb = chunkSizeKb,
                        onChunkSizeChanged = { chunkSizeKb = it },
                        i18n = i18n
                    )
                    2 -> EncryptionStep(
                        enabled = enableEncryption,
                        onEnabledChanged = { enableEncryption = it },
                        algorithm = encryptionAlgorithm,
                        onAlgorithmChanged = { encryptionAlgorithm = it },
                        password = password,
                        onPasswordChanged = { password = it },
                        confirmPassword = confirmPassword,
                        onConfirmPasswordChanged = { confirmPassword = it },
                        i18n = i18n
                    )
                    3 -> OutputStep(
                        outputPath = outputPath,
                        onOutputPathChanged = { outputPath = it },
                        selectedFiles = selectedFiles,
                        compressionAlgorithm = compressionAlgorithm,
                        compressionLevel = compressionLevel,
                        enableEncryption = enableEncryption,
                        encryptionAlgorithm = encryptionAlgorithm,
                        i18n = i18n
                    )
                }
            }
        }

        // Footer - hide when creating or complete
        if (!isCreating && !creationComplete && creationError == null) {
            WizardFooter(
                currentStep = currentStep,
                totalSteps = steps.size,
                canGoNext = when (currentStep) {
                    0 -> selectedFiles.isNotEmpty()
                    2 -> !enableEncryption || (password.isNotEmpty() && password == confirmPassword)
                    3 -> outputPath != null
                    else -> true
                },
                onBack = { currentStep-- },
                onNext = { currentStep++ },
                onCancel = { navigator.goBack() },
                onFinish = {
                    isCreating = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                // Collect all files to add (expand directories)
                                val allFiles = mutableListOf<Pair<Path, String>>()

                                withContext(Dispatchers.Main) {
                                    currentFileName = i18n["wizard.create.scanning"]
                                    creationProgress = 0f
                                }

                                for (path in selectedFiles) {
                                    if (Files.isDirectory(path)) {
                                        // Walk directory and add all files with relative paths
                                        Files.walk(path).use { stream ->
                                            stream.filter { Files.isRegularFile(it) }.forEach { file ->
                                                val relativeName = path.fileName.toString() + "/" +
                                                        path.relativize(file).toString().replace("\\", "/")
                                                allFiles.add(file to relativeName)
                                            }
                                        }
                                    } else {
                                        allFiles.add(path to path.fileName.toString())
                                    }
                                }

                                // Build configuration
                                val configBuilder = ApackConfiguration.builder()
                                    .chunkSize(chunkSizeKb * 1024)

                                // Add compression if selected
                                if (compressionAlgorithm != "none") {
                                    try {
                                        val compressionProvider = when (compressionAlgorithm) {
                                            "zstd" -> CompressionRegistry.zstd()
                                            "lz4" -> CompressionRegistry.lz4()
                                            else -> null
                                        }
                                        if (compressionProvider != null) {
                                            configBuilder.compression(compressionProvider, compressionLevel)
                                        }
                                    } catch (e: Exception) {
                                        // Compression provider not available, continue without compression
                                    }
                                }

                                // Add encryption if enabled
                                if (enableEncryption && password.isNotEmpty()) {
                                    try {
                                        withContext(Dispatchers.Main) {
                                            currentFileName = i18n["wizard.create.deriving_key"]
                                        }

                                        // Get encryption provider
                                        val encryptionProvider = when (encryptionAlgorithm) {
                                            "aes-256-gcm" -> EncryptionRegistry.aes256Gcm()
                                            "chacha20-poly1305" -> EncryptionRegistry.chaCha20Poly1305()
                                            else -> EncryptionRegistry.aes256Gcm()
                                        }

                                        // Create KDF and derive key
                                        val kdf = Argon2idKeyDerivation()
                                        val salt = kdf.generateSalt()

                                        // Generate random Content Encryption Key (CEK)
                                        val cek = KeyWrapper.generateAes256Key()

                                        // Wrap CEK with password-derived key
                                        val wrappedKey = KeyWrapper.wrapWithPassword(
                                            cek,
                                            password.toCharArray(),
                                            salt,
                                            kdf
                                        )

                                        // Get cipher algorithm ID
                                        val cipherAlgorithmId = when (encryptionAlgorithm) {
                                            "aes-256-gcm" -> FormatConstants.ENCRYPTION_AES_256_GCM
                                            "chacha20-poly1305" -> FormatConstants.ENCRYPTION_CHACHA20_POLY1305
                                            else -> FormatConstants.ENCRYPTION_AES_256_GCM
                                        }

                                        // Build encryption block
                                        val encryptionBlock = EncryptionBlock.builder()
                                            .kdfAlgorithmId(FormatConstants.KDF_ARGON2ID)
                                            .cipherAlgorithmId(cipherAlgorithmId)
                                            .kdfIterations(3)
                                            .kdfMemory(65536) // 64 MB
                                            .kdfParallelism(4)
                                            .salt(salt)
                                            .wrappedKey(wrappedKey)
                                            .wrappedKeyTag(ByteArray(16)) // Tag is included in wrappedKey for AES Key Wrap
                                            .build()

                                        configBuilder.encryption(encryptionProvider, cek, encryptionBlock)
                                    } catch (e: Exception) {
                                        throw RuntimeException("Failed to setup encryption: ${e.message}", e)
                                    }
                                }

                                val config = configBuilder.build()

                                // Calculate total size for accurate progress tracking
                                val fileSizes = allFiles.map { (filePath, _) ->
                                    Files.size(filePath)
                                }
                                val totalBytes = fileSizes.sum()
                                var processedBytes = 0L

                                // Threshold for "large file" (10 MB)
                                val largeFileThreshold = 10 * 1024 * 1024L

                                // Create archive
                                AetherPackWriter.create(outputPath!!, config).use { writer ->
                                    allFiles.forEachIndexed { index, (filePath, entryName) ->
                                        val fileSize = fileSizes[index]
                                        val isLarge = fileSize > largeFileThreshold

                                        // Update progress before processing each file
                                        withContext(Dispatchers.Main) {
                                            currentFileName = entryName
                                            isProcessingLargeFile = isLarge
                                            creationProgress = if (totalBytes > 0) {
                                                processedBytes.toFloat() / totalBytes
                                            } else {
                                                index.toFloat() / allFiles.size
                                            }
                                        }

                                        // Small delay to allow UI to update
                                        delay(10)

                                        writer.addEntry(entryName, filePath)

                                        // Update bytes processed
                                        processedBytes += fileSize

                                        // Update progress after file is added
                                        withContext(Dispatchers.Main) {
                                            isProcessingLargeFile = false
                                            creationProgress = if (totalBytes > 0) {
                                                processedBytes.toFloat() / totalBytes
                                            } else {
                                                (index + 1).toFloat() / allFiles.size
                                            }
                                        }
                                    }
                                }
                            }
                            creationComplete = true
                        } catch (e: Exception) {
                            creationError = e.message ?: i18n["error.unknown"]
                        } finally {
                            isCreating = false
                        }
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
    i18n: I18n
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
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
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
    Column {
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

        if (selectedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(FluentTheme.colors.background.card.default),
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
                }
            }
        } else {
            Text(
                text = i18n.format("wizard.create.files_count", selectedFiles.size),
                style = FluentTheme.typography.bodyStrong,
                color = AetherColors.AccentPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(FluentTheme.colors.background.card.default)
            ) {
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
        androidx.compose.material3.LocalContentColor provides Color.White
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
