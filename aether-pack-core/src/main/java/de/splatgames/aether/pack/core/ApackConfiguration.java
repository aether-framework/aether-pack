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

package de.splatgames.aether.pack.core;

import de.splatgames.aether.pack.core.checksum.ChecksumRegistry;
import de.splatgames.aether.pack.core.format.EncryptionBlock;
import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.core.io.ChunkProcessor;
import de.splatgames.aether.pack.core.spi.ChecksumProvider;
import de.splatgames.aether.pack.core.spi.CompressionProvider;
import de.splatgames.aether.pack.core.spi.EncryptionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;

/**
 * Immutable configuration for APACK archive operations.
 *
 * <p>This record encapsulates all settings that control how APACK archives are
 * created and read. It defines the chunk size, checksum algorithm, compression,
 * encryption, and various format options.</p>
 *
 * <h2>Creating a Configuration</h2>
 * <p>Use the fluent {@link Builder} to create configurations:</p>
 * <pre>{@code
 * ApackConfiguration config = ApackConfiguration.builder()
 *     .chunkSize(128 * 1024)           // 128 KB chunks
 *     .compression(zstdProvider, 6)    // ZSTD level 6
 *     .encryption(aesProvider, key)    // AES-256-GCM
 *     .enableRandomAccess(true)
 *     .build();
 * }</pre>
 *
 * <h2>Default Configuration</h2>
 * <p>The {@link #DEFAULT} configuration provides sensible defaults:</p>
 * <ul>
 *   <li>Chunk size: 256 KB</li>
 *   <li>Checksum: XXH3-64 (fast, high quality)</li>
 *   <li>Compression: None</li>
 *   <li>Encryption: None</li>
 *   <li>Random access: Enabled</li>
 *   <li>Stream mode: Disabled</li>
 * </ul>
 *
 * <h2>Chunk Size Considerations</h2>
 * <p>The chunk size affects both performance and granularity:</p>
 * <ul>
 *   <li><strong>Smaller chunks (16-64 KB):</strong> Better random access performance,
 *       lower memory usage, but more overhead per chunk</li>
 *   <li><strong>Larger chunks (256 KB - 1 MB):</strong> Better compression ratios,
 *       lower overhead, but higher memory usage and coarser access granularity</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This is an immutable record. Instances can be safely shared between threads
 * and reused for multiple archive operations.</p>
 *
 * @param chunkSize           the size of data chunks in bytes; must be between
 *                            {@link FormatConstants#MIN_CHUNK_SIZE} and
 *                            {@link FormatConstants#MAX_CHUNK_SIZE}
 * @param checksumProvider    the checksum algorithm for data integrity verification;
 *                            must not be {@code null}
 * @param compressionProvider the compression provider, or {@code null} for no compression
 * @param compressionLevel    the compression level; interpretation depends on the provider
 * @param encryptionProvider  the encryption provider, or {@code null} for no encryption
 * @param encryptionKey       the encryption key; required if encryption provider is set
 * @param encryptionBlock     the encryption metadata block containing KDF params, salt,
 *                            and wrapped key; required if encryption provider is set
 * @param enableRandomAccess  {@code true} to write a table of contents enabling
 *                            random access to entries
 * @param streamMode          {@code true} for stream mode (single entry, optimized for
 *                            streaming); {@code false} for container mode (multiple entries)
 *
 * @see AetherPackWriter#create(java.nio.file.Path, ApackConfiguration)
 * @see AetherPackReader#open(java.nio.file.Path, ApackConfiguration)
 * @see Builder
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ApackConfiguration(
        int chunkSize,
        @NotNull ChecksumProvider checksumProvider,
        @Nullable CompressionProvider compressionProvider,
        int compressionLevel,
        @Nullable EncryptionProvider encryptionProvider,
        @Nullable SecretKey encryptionKey,
        @Nullable EncryptionBlock encryptionBlock,
        boolean enableRandomAccess,
        boolean streamMode
) {

    /**
     * The default configuration with standard settings.
     *
     * <p>This configuration uses:</p>
     * <ul>
     *   <li>256 KB chunk size</li>
     *   <li>XXH3-64 checksums</li>
     *   <li>No compression</li>
     *   <li>No encryption</li>
     *   <li>Random access enabled</li>
     *   <li>Stream mode disabled</li>
     * </ul>
     */
    public static final ApackConfiguration DEFAULT = builder().build();

    /**
     * Canonical constructor with validation.
     *
     * <p>Validates that the chunk size is within allowed bounds and that
     * an encryption key is provided when encryption is enabled.</p>
     *
     * @throws IllegalArgumentException if chunk size is out of bounds or
     *                                  encryption provider is set without a key
     */
    public ApackConfiguration {
        if (chunkSize < FormatConstants.MIN_CHUNK_SIZE || chunkSize > FormatConstants.MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "Chunk size must be between " + FormatConstants.MIN_CHUNK_SIZE +
                            " and " + FormatConstants.MAX_CHUNK_SIZE + ": " + chunkSize);
        }
        if (encryptionProvider != null && encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key required when encryption provider is set");
        }
    }

    /**
     * Returns whether compression is enabled in this configuration.
     *
     * <p>Compression is enabled when a compression provider has been set.</p>
     *
     * @return {@code true} if compression is enabled, {@code false} otherwise
     */
    public boolean isCompressionEnabled() {
        return this.compressionProvider != null;
    }

    /**
     * Returns whether encryption is enabled in this configuration.
     *
     * <p>Encryption is enabled when both an encryption provider and a key
     * have been set.</p>
     *
     * @return {@code true} if encryption is enabled, {@code false} otherwise
     */
    public boolean isEncryptionEnabled() {
        return this.encryptionProvider != null && this.encryptionKey != null;
    }

    /**
     * Creates a {@link ChunkProcessor} based on this configuration.
     *
     * <p>The returned processor will apply compression and/or encryption
     * according to this configuration's settings. This is typically called
     * internally by {@link AetherPackWriter} and {@link AetherPackReader}.</p>
     *
     * @return a new chunk processor configured according to this configuration;
     *         never {@code null}
     *
     * @see ChunkProcessor
     */
    public @NotNull ChunkProcessor createChunkProcessor() {
        final ChunkProcessor.Builder builder = ChunkProcessor.builder();

        if (this.compressionProvider != null) {
            builder.compression(this.compressionProvider, this.compressionLevel);
        }

        if (this.encryptionProvider != null && this.encryptionKey != null) {
            builder.encryption(this.encryptionProvider, this.encryptionKey);
        }

        return builder.build();
    }

    /**
     * Creates a new configuration builder with default values.
     *
     * <p>The builder starts with the same defaults as {@link #DEFAULT}.</p>
     *
     * @return a new builder instance; never {@code null}
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * A fluent builder for creating {@link ApackConfiguration} instances.
     *
     * <p>This builder provides a convenient way to construct configurations with
     * only the desired options modified from defaults. All setter methods return
     * the builder instance for method chaining.</p>
     *
     * <h2>Example Usage</h2>
     * <pre>{@code
     * ApackConfiguration config = ApackConfiguration.builder()
     *     .chunkSize(64 * 1024)
     *     .compression(CompressionRegistry.zstd(), 3)
     *     .checksumAlgorithm("sha256")
     *     .build();
     * }</pre>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public static final class Builder {

        /** Chunk size in bytes (default: 256 KB). */
        private int chunkSize = FormatConstants.DEFAULT_CHUNK_SIZE;

        /** Checksum provider for data integrity (default: XXH3-64). */
        private @NotNull ChecksumProvider checksumProvider = ChecksumRegistry.getDefault();

        /** Compression provider, or null to disable compression. */
        private @Nullable CompressionProvider compressionProvider = null;

        /** Compression level (-1 = use provider default). */
        private int compressionLevel = -1;

        /** Encryption provider, or null to disable encryption. */
        private @Nullable EncryptionProvider encryptionProvider = null;

        /** Encryption key (required when encryptionProvider is set). */
        private @Nullable SecretKey encryptionKey = null;

        /** Encryption metadata block (required when encryptionProvider is set). */
        private @Nullable EncryptionBlock encryptionBlock = null;

        /** Whether to generate a table-of-contents for random access. */
        private boolean enableRandomAccess = true;

        /** Whether to use stream mode (single entry, no trailer). */
        private boolean streamMode = false;

        /**
         * Private constructor for creating a builder with default values.
         *
         * <p>Use {@link ApackConfiguration#builder()} to obtain a builder instance.</p>
         */
        private Builder() {
        }

        /**
         * Sets the chunk size in bytes.
         *
         * <p>The chunk size determines how data is split for storage and processing.
         * Larger chunks generally improve compression ratios but increase memory usage.</p>
         *
         * @param chunkSize the chunk size in bytes; must be between
         *                  {@link FormatConstants#MIN_CHUNK_SIZE} and
         *                  {@link FormatConstants#MAX_CHUNK_SIZE}
         * @return this builder for method chaining
         */
        public @NotNull Builder chunkSize(final int chunkSize) {
            this.chunkSize = chunkSize;
            return this;
        }

        /**
         * Sets the checksum provider for data integrity verification.
         *
         * @param checksumProvider the checksum provider; must not be {@code null}
         * @return this builder for method chaining
         *
         * @see ChecksumRegistry
         */
        public @NotNull Builder checksumProvider(final @NotNull ChecksumProvider checksumProvider) {
            this.checksumProvider = checksumProvider;
            return this;
        }

        /**
         * Sets the checksum algorithm by name.
         *
         * <p>This is a convenience method that looks up the provider from
         * {@link ChecksumRegistry}. Valid names include "xxh3-64", "crc32", "sha256".</p>
         *
         * @param algorithm the algorithm name
         * @return this builder for method chaining
         * @throws java.util.NoSuchElementException if the algorithm is not found
         */
        public @NotNull Builder checksumAlgorithm(final @NotNull String algorithm) {
            this.checksumProvider = ChecksumRegistry.requireByName(algorithm);
            return this;
        }

        /**
         * Enables compression with the default level.
         *
         * <p>The default compression level is determined by the provider.</p>
         *
         * @param provider the compression provider; must not be {@code null}
         * @return this builder for method chaining
         */
        public @NotNull Builder compression(final @NotNull CompressionProvider provider) {
            this.compressionProvider = provider;
            if (this.compressionLevel < 0) {
                this.compressionLevel = provider.getDefaultLevel();
            }
            return this;
        }

        /**
         * Enables compression with a specific level.
         *
         * @param provider the compression provider; must not be {@code null}
         * @param level    the compression level; interpretation depends on the provider
         * @return this builder for method chaining
         */
        public @NotNull Builder compression(final @NotNull CompressionProvider provider, final int level) {
            this.compressionProvider = provider;
            this.compressionLevel = level;
            return this;
        }

        /**
         * Sets the compression level without changing the provider.
         *
         * <p>This is useful when you want to change only the level after setting
         * the provider.</p>
         *
         * @param level the compression level
         * @return this builder for method chaining
         */
        public @NotNull Builder compressionLevel(final int level) {
            this.compressionLevel = level;
            return this;
        }

        /**
         * Enables encryption with the specified provider and key.
         *
         * <p>Both provider and key must be provided together for encryption to work.
         * When creating an archive, you should also provide an {@link EncryptionBlock}
         * using {@link #encryptionBlock(EncryptionBlock)} to store the key derivation
         * parameters.</p>
         *
         * @param provider the encryption provider; must not be {@code null}
         * @param key      the encryption key; must not be {@code null}
         * @return this builder for method chaining
         */
        public @NotNull Builder encryption(final @NotNull EncryptionProvider provider, final @NotNull SecretKey key) {
            this.encryptionProvider = provider;
            this.encryptionKey = key;
            return this;
        }

        /**
         * Enables encryption with the specified provider, key, and encryption block.
         *
         * <p>This is the complete encryption setup that includes all necessary
         * metadata for password-based key derivation. The encryption block contains
         * the salt, wrapped key, and KDF parameters needed to decrypt the archive.</p>
         *
         * @param provider the encryption provider; must not be {@code null}
         * @param key      the encryption key (DEK); must not be {@code null}
         * @param block    the encryption metadata block; must not be {@code null}
         * @return this builder for method chaining
         */
        public @NotNull Builder encryption(
                final @NotNull EncryptionProvider provider,
                final @NotNull SecretKey key,
                final @NotNull EncryptionBlock block) {
            this.encryptionProvider = provider;
            this.encryptionKey = key;
            this.encryptionBlock = block;
            return this;
        }

        /**
         * Sets the encryption block containing key derivation metadata.
         *
         * <p>The encryption block contains the salt, wrapped key, and KDF parameters
         * needed to derive the encryption key from a password. This should be set
         * together with the encryption provider and key.</p>
         *
         * @param block the encryption metadata block; must not be {@code null}
         * @return this builder for method chaining
         *
         * @see EncryptionBlock
         */
        public @NotNull Builder encryptionBlock(final @NotNull EncryptionBlock block) {
            this.encryptionBlock = block;
            return this;
        }

        /**
         * Sets whether to enable random access support.
         *
         * <p>When enabled (default), a table of contents is written to the archive
         * trailer, allowing entries to be accessed by name or ID without sequential
         * scanning.</p>
         *
         * @param enableRandomAccess {@code true} to enable random access, {@code false} to disable
         * @return this builder for method chaining
         */
        public @NotNull Builder enableRandomAccess(final boolean enableRandomAccess) {
            this.enableRandomAccess = enableRandomAccess;
            return this;
        }

        /**
         * Sets whether to use stream mode.
         *
         * <p>Stream mode is optimized for single-entry archives that are read
         * sequentially. In this mode, the archive format is simplified and
         * the trailer may be omitted.</p>
         *
         * @param streamMode {@code true} to enable stream mode, {@code false} for container mode
         * @return this builder for method chaining
         */
        public @NotNull Builder streamMode(final boolean streamMode) {
            this.streamMode = streamMode;
            return this;
        }

        /**
         * Builds the configuration with the current settings.
         *
         * <p>This method validates the configuration before returning.</p>
         *
         * @return a new immutable configuration instance
         * @throws IllegalArgumentException if the configuration is invalid
         */
        public @NotNull ApackConfiguration build() {
            final int level = this.compressionLevel >= 0
                    ? this.compressionLevel
                    : (this.compressionProvider != null ? this.compressionProvider.getDefaultLevel() : 0);

            return new ApackConfiguration(
                    this.chunkSize,
                    this.checksumProvider,
                    this.compressionProvider,
                    level,
                    this.encryptionProvider,
                    this.encryptionKey,
                    this.encryptionBlock,
                    this.enableRandomAccess,
                    this.streamMode
            );
        }

    }

}
