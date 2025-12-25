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

package de.splatgames.aether.pack.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.splatgames.aether.pack.compression.CompressionRegistry
import de.splatgames.aether.pack.core.AetherPackWriter
import de.splatgames.aether.pack.core.ApackConfiguration
import de.splatgames.aether.pack.core.format.EncryptionBlock
import de.splatgames.aether.pack.core.format.FormatConstants
import de.splatgames.aether.pack.crypto.Argon2idKeyDerivation
import de.splatgames.aether.pack.crypto.EncryptionRegistry
import de.splatgames.aether.pack.crypto.KeyWrapper
import de.splatgames.aether.pack.gui.i18n.I18n
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Persistent state for the Create Archive Wizard.
 *
 * This state is maintained across navigation, allowing users to navigate away
 * from the wizard while an archive is being created and return to see the progress
 * or completion status.
 *
 * @param settingsState Settings for default values
 * @param applicationScope Application-level coroutine scope that survives navigation
 */
class CreateWizardState(
    private val settingsState: SettingsState,
    private val applicationScope: CoroutineScope
) {

    // ===== Wizard Step State =====

    /**
     * Current step in the wizard (0-based).
     */
    var currentStep by mutableStateOf(0)

    /**
     * Selected files and folders to include in the archive.
     */
    val selectedFiles = mutableStateListOf<Path>()

    /**
     * Selected compression algorithm ("zstd", "lz4", or "none").
     */
    var compressionAlgorithm by mutableStateOf(settingsState.defaultCompression)

    /**
     * Compression level.
     */
    var compressionLevel by mutableStateOf(settingsState.defaultCompressionLevel)

    /**
     * Chunk size in KB.
     */
    var chunkSizeKb by mutableStateOf(settingsState.defaultChunkSizeKb)

    /**
     * Whether encryption is enabled.
     */
    var enableEncryption by mutableStateOf(false)

    /**
     * Encryption algorithm ("aes-256-gcm" or "chacha20-poly1305").
     */
    var encryptionAlgorithm by mutableStateOf("aes-256-gcm")

    /**
     * Password for encryption.
     */
    var password by mutableStateOf("")

    /**
     * Password confirmation.
     */
    var confirmPassword by mutableStateOf("")

    /**
     * Output path for the archive.
     */
    var outputPath by mutableStateOf<Path?>(null)

    // ===== Creation State =====

    /**
     * Whether archive creation is in progress.
     */
    var isCreating by mutableStateOf(false)
        private set

    /**
     * Whether archive creation has completed successfully.
     */
    var creationComplete by mutableStateOf(false)
        private set

    /**
     * Error message if creation failed, null otherwise.
     */
    var creationError by mutableStateOf<String?>(null)
        private set

    /**
     * Creation progress (0.0 to 1.0).
     */
    var creationProgress by mutableStateOf(0f)
        private set

    /**
     * Name of the file currently being processed.
     */
    var currentFileName by mutableStateOf("")
        private set

    /**
     * Whether a large file is being processed (for indeterminate progress).
     */
    var isProcessingLargeFile by mutableStateOf(false)
        private set

    /**
     * Whether the overwrite confirmation dialog is shown.
     */
    var showOverwriteDialog by mutableStateOf(false)

    /**
     * Whether the wizard is active (has been started and not reset).
     */
    val isActive: Boolean
        get() = selectedFiles.isNotEmpty() || isCreating || creationComplete || creationError != null

    // ===== Internal State =====

    private var creationJob: Job? = null

    /**
     * Start the archive creation process.
     *
     * Uses the application-level coroutine scope so the creation continues
     * even when the user navigates away from the wizard.
     *
     * @param i18n Internationalization for messages
     */
    fun startCreation(i18n: I18n) {
        if (isCreating || outputPath == null) return

        isCreating = true
        showOverwriteDialog = false
        creationComplete = false
        creationError = null
        creationProgress = 0f

        creationJob = applicationScope.launch {
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
            } catch (e: CancellationException) {
                // Task was cancelled, don't set error
            } catch (e: Exception) {
                creationError = e.message ?: "Unknown error"
            } finally {
                isCreating = false
            }
        }
    }

    /**
     * Cancel the archive creation.
     */
    fun cancelCreation() {
        creationJob?.cancel()
        isCreating = false
    }

    /**
     * Clear the error state to allow retry.
     */
    fun clearError() {
        creationError = null
    }

    /**
     * Reset the wizard to its initial state.
     */
    fun reset() {
        cancelCreation()

        currentStep = 0
        selectedFiles.clear()
        compressionAlgorithm = settingsState.defaultCompression
        compressionLevel = settingsState.defaultCompressionLevel
        chunkSizeKb = settingsState.defaultChunkSizeKb
        enableEncryption = false
        encryptionAlgorithm = "aes-256-gcm"
        password = ""
        confirmPassword = ""
        outputPath = null

        isCreating = false
        creationComplete = false
        creationError = null
        creationProgress = 0f
        currentFileName = ""
        isProcessingLargeFile = false
        showOverwriteDialog = false
    }
}
