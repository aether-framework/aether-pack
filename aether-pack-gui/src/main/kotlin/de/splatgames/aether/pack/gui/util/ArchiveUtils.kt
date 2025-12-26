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

package de.splatgames.aether.pack.gui.util

import de.splatgames.aether.pack.compression.CompressionRegistry
import de.splatgames.aether.pack.core.AetherPackReader
import de.splatgames.aether.pack.core.format.EncryptionBlock
import de.splatgames.aether.pack.core.format.FormatConstants
import de.splatgames.aether.pack.core.io.ChunkProcessor
import de.splatgames.aether.pack.crypto.Argon2idKeyDerivation
import de.splatgames.aether.pack.crypto.EncryptionRegistry
import de.splatgames.aether.pack.crypto.KeyWrapper
import de.splatgames.aether.pack.crypto.Pbkdf2KeyDerivation
import java.nio.file.Path
import javax.crypto.SecretKey

/**
 * Utility functions for working with APACK archives.
 */
object ArchiveUtils {

    /**
     * Checks if an archive is encrypted.
     *
     * @param archivePath Path to the archive file
     * @return true if the archive is encrypted
     */
    fun isEncrypted(archivePath: Path): Boolean {
        AetherPackReader.open(archivePath).use { reader ->
            return reader.fileHeader.isEncrypted
        }
    }

    /**
     * Gets the encryption block from an encrypted archive.
     *
     * @param archivePath Path to the archive file
     * @return The encryption block, or null if not encrypted
     */
    fun getEncryptionBlock(archivePath: Path): EncryptionBlock? {
        AetherPackReader.open(archivePath).use { reader ->
            return reader.encryptionBlock
        }
    }

    /**
     * Derives the decryption key from a password using the archive's encryption block.
     *
     * @param encryptionBlock The encryption block containing KDF parameters
     * @param password The password to derive the key from
     * @return The derived content encryption key (CEK)
     * @throws Exception if key derivation fails (e.g., wrong password)
     */
    fun deriveKey(encryptionBlock: EncryptionBlock, password: String): SecretKey {
        // Create the appropriate KDF based on the algorithm ID
        // Argon2id constructor: (memoryKiB, iterations, parallelism, saltLength)
        val kdf = when (encryptionBlock.kdfAlgorithmId()) {
            FormatConstants.KDF_ARGON2ID -> Argon2idKeyDerivation(
                encryptionBlock.kdfMemory(),      // memoryKiB
                encryptionBlock.kdfIterations(),   // iterations (time cost)
                encryptionBlock.kdfParallelism(),  // parallelism
                encryptionBlock.salt().size        // saltLength
            )
            FormatConstants.KDF_PBKDF2_SHA256 -> Pbkdf2KeyDerivation(
                encryptionBlock.kdfIterations(),   // iterations
                encryptionBlock.salt().size        // saltLength
            )
            else -> throw IllegalStateException("Unknown KDF algorithm ID: ${encryptionBlock.kdfAlgorithmId()}")
        }

        // Unwrap the content encryption key using the password
        val cek = KeyWrapper.unwrapWithPassword(
            encryptionBlock.wrappedKey(),
            password.toCharArray(),
            encryptionBlock.salt(),
            kdf,
            "AES"
        )

        return cek
    }

    /**
     * Creates a ChunkProcessor configured for reading the specified archive.
     *
     * This function reads the archive metadata to determine which compression
     * and encryption algorithms are used, then configures the ChunkProcessor
     * accordingly.
     *
     * @param archivePath Path to the archive file
     * @param password Password for encrypted archives (required if archive is encrypted)
     * @return A ChunkProcessor configured for the archive's compression and encryption
     * @throws Exception if providers cannot be found or configuration fails
     */
    fun createChunkProcessor(
        archivePath: Path,
        password: String? = null
    ): ChunkProcessor {
        // First, read the archive with pass-through to get metadata
        AetherPackReader.open(archivePath).use { reader ->
            val entries = reader.entries
            val header = reader.fileHeader
            val encryptionBlock = reader.encryptionBlock

            // Find first entry with compression to get the compression ID
            val compressionId = entries
                .firstOrNull { it.compressionId != 0 }
                ?.compressionId ?: 0

            // Find first entry with encryption to get the encryption ID
            val encryptionId = entries
                .firstOrNull { it.encryptionId != 0 }
                ?.encryptionId ?: 0

            val builder = ChunkProcessor.builder()

            // Configure compression if needed
            if (compressionId != 0) {
                val compressionProvider = CompressionRegistry.getById(compressionId)
                    .orElseThrow {
                        IllegalStateException("Compression provider not available for ID: $compressionId. " +
                            "Make sure the compression module is on the classpath.")
                    }
                builder.compression(compressionProvider)
            }

            // Configure encryption if needed
            if (header.isEncrypted && encryptionBlock != null && encryptionId != 0) {
                if (password.isNullOrEmpty()) {
                    throw IllegalStateException("Password required for encrypted archive")
                }

                // Get encryption provider
                val encryptionProvider = EncryptionRegistry.getById(encryptionId)
                    .orElseThrow {
                        IllegalStateException("Encryption provider not available for ID: $encryptionId. " +
                            "Make sure the crypto module is on the classpath.")
                    }

                // Derive the content encryption key from password
                val cek = deriveKey(encryptionBlock, password)

                builder.encryption(encryptionProvider, cek)
            }

            return builder.build()
        }
    }

    /**
     * Opens an archive with the appropriate ChunkProcessor for its compression and encryption.
     *
     * This is a convenience function that creates the ChunkProcessor and opens
     * the archive in one call.
     *
     * @param archivePath Path to the archive file
     * @param password Password for encrypted archives (required if archive is encrypted)
     * @return An AetherPackReader configured for the archive
     */
    fun openArchive(
        archivePath: Path,
        password: String? = null
    ): AetherPackReader {
        val processor = createChunkProcessor(archivePath, password)
        return AetherPackReader.open(archivePath, processor)
    }
}
