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

package de.splatgames.aether.pack.core.io;

import de.splatgames.aether.pack.core.spi.CompressionProvider;
import de.splatgames.aether.pack.core.spi.EncryptionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Processes chunk data by applying compression and/or encryption transformations.
 *
 * <p>This class implements the data transformation pipeline for individual chunks
 * in an APACK archive. It applies transformations in the correct order for both
 * writing (compress-then-encrypt) and reading (decrypt-then-decompress).</p>
 *
 * <h2>Processing Pipeline</h2>
 * <p>When writing:</p>
 * <ol>
 *   <li>Compress the data (if compression is enabled)</li>
 *   <li>Encrypt the result (if encryption is enabled)</li>
 * </ol>
 * <p>When reading:</p>
 * <ol>
 *   <li>Decrypt the data (if encrypted)</li>
 *   <li>Decompress the result (if compressed)</li>
 * </ol>
 *
 * <h2>Compression Behavior</h2>
 * <p>When compression is enabled, the processor only uses compressed data if it
 * is actually smaller than the original. If compression would increase size
 * (which can happen with already-compressed or random data), the original
 * data is stored uncompressed and the chunk is marked accordingly.</p>
 *
 * <h2>Creating a ChunkProcessor</h2>
 * <p>Use the fluent {@link Builder} to create processors:</p>
 * <pre>{@code
 * // Compression only
 * ChunkProcessor compressor = ChunkProcessor.builder()
 *     .compression(zstdProvider, 6)
 *     .build();
 *
 * // Encryption only
 * ChunkProcessor encryptor = ChunkProcessor.builder()
 *     .encryption(aesProvider, secretKey)
 *     .build();
 *
 * // Both compression and encryption
 * ChunkProcessor processor = ChunkProcessor.builder()
 *     .compression(zstdProvider, 3)
 *     .encryption(aesProvider, secretKey)
 *     .build();
 *
 * // Pass-through (no transformations)
 * ChunkProcessor passThrough = ChunkProcessor.passThrough();
 * }</pre>
 *
 * <h2>Processing Chunks</h2>
 * <pre>{@code
 * // Writing: compress and encrypt
 * ProcessedChunk result = processor.processForWrite(chunkData, chunkData.length);
 * output.write(result.data());
 * // Store result.compressed() and result.encrypted() in chunk header
 *
 * // Reading: decrypt and decompress
 * byte[] original = processor.processForRead(
 *     storedData, expectedSize,
 *     chunkHeader.isCompressed(), chunkHeader.isEncrypted());
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>Instances of this class are immutable and thread-safe. However, the
 * underlying compression and encryption providers may have their own
 * thread-safety requirements.</p>
 *
 * @see ProcessedChunk
 * @see CompressionProvider
 * @see EncryptionProvider
 * @see de.splatgames.aether.pack.core.ApackConfiguration#createChunkProcessor()
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ChunkProcessor {

    /**
     * The compression provider used for compressing/decompressing chunk data.
     * Null if compression is disabled.
     */
    private final @Nullable CompressionProvider compressionProvider;

    /**
     * The compression level to use when compressing data.
     * Ignored when compression is disabled.
     */
    private final int compressionLevel;

    /**
     * The encryption provider used for encrypting/decrypting chunk data.
     * Null if encryption is disabled.
     */
    private final @Nullable EncryptionProvider encryptionProvider;

    /**
     * The secret key used for encryption and decryption operations.
     * Null if encryption is disabled.
     */
    private final @Nullable SecretKey encryptionKey;

    /**
     * Private constructor used by the builder.
     *
     * @param compressionProvider the compression provider, or null if disabled
     * @param compressionLevel    the compression level
     * @param encryptionProvider  the encryption provider, or null if disabled
     * @param encryptionKey       the encryption key, or null if disabled
     */
    private ChunkProcessor(
            final @Nullable CompressionProvider compressionProvider,
            final int compressionLevel,
            final @Nullable EncryptionProvider encryptionProvider,
            final @Nullable SecretKey encryptionKey) {

        this.compressionProvider = compressionProvider;
        this.compressionLevel = compressionLevel;
        this.encryptionProvider = encryptionProvider;
        this.encryptionKey = encryptionKey;
    }

    /**
     * Creates a new builder for constructing {@link ChunkProcessor} instances.
     *
     * <p>The builder starts with no transformations enabled. Use the builder's
     * {@link Builder#compression(CompressionProvider, int)} and
     * {@link Builder#encryption(EncryptionProvider, SecretKey)} methods to
     * configure the desired transformations.</p>
     *
     * @return a new builder instance for configuring and creating a chunk
     *         processor; never {@code null}
     *
     * @see Builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Creates a processor that applies no transformations (pass-through mode).
     *
     * <p>The returned processor will pass data through unchanged for both
     * write and read operations. This is useful when compression and
     * encryption are disabled but a processor instance is still required.</p>
     *
     * @return a chunk processor that does not compress or encrypt data;
     *         never {@code null}; the processor is immutable and can be
     *         shared between threads
     */
    public static @NotNull ChunkProcessor passThrough() {
        return new ChunkProcessor(null, 0, null, null);
    }

    /**
     * Checks if compression is enabled for this processor.
     *
     * <p>Compression is enabled when a {@link CompressionProvider} was
     * configured via the builder. When enabled, the processor will attempt
     * to compress data during {@link #processForWrite} operations, though
     * it may skip compression if it would increase the data size.</p>
     *
     * @return {@code true} if a compression provider is configured and
     *         compression will be attempted during processing; {@code false}
     *         if data passes through uncompressed
     *
     * @see #getCompressionProvider()
     */
    public boolean isCompressionEnabled() {
        return this.compressionProvider != null;
    }

    /**
     * Checks if encryption is enabled for this processor.
     *
     * <p>Encryption is enabled when both an {@link EncryptionProvider} and
     * a {@link SecretKey} were configured via the builder. When enabled,
     * the processor will encrypt data during {@link #processForWrite}
     * operations and decrypt during {@link #processForRead} operations.</p>
     *
     * @return {@code true} if both an encryption provider and key are
     *         configured and encryption will be applied during processing;
     *         {@code false} if data passes through unencrypted
     *
     * @see #getEncryptionProvider()
     */
    public boolean isEncryptionEnabled() {
        return this.encryptionProvider != null && this.encryptionKey != null;
    }

    /**
     * Returns the compression provider configured for this processor.
     *
     * <p>The compression provider defines the compression algorithm used
     * (e.g., ZSTD, LZ4). If no compression provider was configured,
     * this method returns {@code null}.</p>
     *
     * @return the compression provider used by this processor, or {@code null}
     *         if compression is not enabled; the provider is immutable and
     *         can be used to identify the compression algorithm
     *
     * @see #isCompressionEnabled()
     * @see CompressionProvider
     */
    public @Nullable CompressionProvider getCompressionProvider() {
        return this.compressionProvider;
    }

    /**
     * Returns the encryption provider configured for this processor.
     *
     * <p>The encryption provider defines the encryption algorithm used
     * (e.g., AES-256-GCM, ChaCha20-Poly1305). If no encryption provider
     * was configured, this method returns {@code null}.</p>
     *
     * @return the encryption provider used by this processor, or {@code null}
     *         if encryption is not enabled; the provider is immutable and
     *         can be used to identify the encryption algorithm
     *
     * @see #isEncryptionEnabled()
     * @see EncryptionProvider
     */
    public @Nullable EncryptionProvider getEncryptionProvider() {
        return this.encryptionProvider;
    }

    /**
     * Processes chunk data for writing by applying compression then encryption.
     *
     * <p>This method applies the configured transformations in the correct
     * order for storage: first compression (if enabled), then encryption
     * (if enabled). The processing order is reversed during reading.</p>
     *
     * <p>Compression behavior: If compression is enabled, the processor
     * compares the compressed size to the original size. If compression
     * would increase the size (common for already-compressed data), the
     * original data is stored and the chunk is marked as not compressed.</p>
     *
     * <p>The returned {@link ProcessedChunk} contains the transformed data
     * and metadata flags indicating which transformations were applied.
     * This information is needed when writing the chunk header.</p>
     *
     * @param data the original unprocessed chunk data; must not be {@code null};
     *             may contain more bytes than {@code originalSize}
     * @param originalSize the number of valid bytes in the data array to process;
     *                     must be positive and not exceed {@code data.length}
     * @return a {@link ProcessedChunk} containing the transformed data and
     *         metadata about applied transformations; never {@code null}
     * @throws IOException if compression or encryption fails due to an
     *                     underlying I/O or cryptographic error
     *
     * @see ProcessedChunk
     * @see #processForRead(byte[], int, boolean, boolean)
     */
    public @NotNull ProcessedChunk processForWrite(
            final byte @NotNull [] data,
            final int originalSize) throws IOException {

        byte[] result = new byte[originalSize];
        System.arraycopy(data, 0, result, 0, originalSize);

        boolean compressed = false;
        boolean encrypted = false;

        // Step 1: Compression
        if (this.compressionProvider != null) {
            final byte[] compressedData = this.compressionProvider.compressBlock(result, this.compressionLevel);
            // Only use compressed data if it's smaller
            if (compressedData.length < result.length) {
                result = compressedData;
                compressed = true;
            }
        }

        // Step 2: Encryption
        if (this.encryptionProvider != null && this.encryptionKey != null) {
            try {
                result = this.encryptionProvider.encryptBlock(result, this.encryptionKey);
                encrypted = true;
            } catch (final GeneralSecurityException e) {
                throw new IOException("Encryption failed", e);
            }
        }

        return new ProcessedChunk(result, originalSize, result.length, compressed, encrypted);
    }

    /**
     * Processes stored chunk data for reading by applying decryption then decompression.
     *
     * <p>This method reverses the transformations applied by {@link #processForWrite}
     * to recover the original chunk data. The processing order is the reverse of
     * writing: first decryption (if the chunk is encrypted), then decompression
     * (if the chunk is compressed).</p>
     *
     * <p>The transformation flags ({@code compressed} and {@code encrypted}) are
     * typically read from the chunk header and indicate which transformations
     * were applied when the chunk was written. These flags must match the actual
     * state of the data for processing to succeed.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>This method performs validation to ensure that the processor is properly
     * configured for the required transformations:</p>
     * <ul>
     *   <li>If the chunk is encrypted but no encryption key is configured, an
     *       {@link IOException} is thrown immediately</li>
     *   <li>If the chunk is compressed but no compression provider is configured,
     *       an {@link IOException} is thrown after any decryption</li>
     *   <li>Decryption failures (e.g., wrong key, corrupted data) result in an
     *       {@link IOException} wrapping the underlying security exception</li>
     *   <li>Decompression failures result in an {@link IOException} from the
     *       compression provider</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Read chunk header to get transformation flags
     * ChunkHeader header = headerIO.readChunkHeader(input);
     *
     * // Read the stored chunk data
     * byte[] storedData = new byte[header.storedSize()];
     * input.readFully(storedData);
     *
     * // Process to recover original data
     * byte[] originalData = processor.processForRead(
     *     storedData,
     *     header.originalSize(),
     *     header.isCompressed(),
     *     header.isEncrypted()
     * );
     * }</pre>
     *
     * @param data the stored (potentially encrypted and/or compressed) chunk data
     *             as read from the archive; must not be {@code null}; the array
     *             is not modified by this method
     * @param originalSize the expected size of the original unprocessed data in bytes;
     *                     this value is used by the decompression algorithm to
     *                     allocate the output buffer and verify correct decompression;
     *                     must match the value stored in the chunk header
     * @param compressed {@code true} if the chunk data was compressed during writing
     *                   and needs to be decompressed; {@code false} if the data was
     *                   stored uncompressed (either because compression was disabled
     *                   or because compression would have increased the size)
     * @param encrypted {@code true} if the chunk data was encrypted during writing
     *                  and needs to be decrypted; {@code false} if the data was
     *                  stored unencrypted
     * @return the original unprocessed chunk data after applying the reverse
     *         transformations; never {@code null}; the returned array will have
     *         a length equal to {@code originalSize} if decompression was applied,
     *         or equal to {@code data.length} if only decryption or no
     *         transformation was applied
     * @throws IOException if processing fails due to any of the following conditions:
     *                     <ul>
     *                       <li>The chunk is encrypted but no encryption key was
     *                           configured on this processor</li>
     *                       <li>Decryption fails due to an invalid key, corrupted
     *                           ciphertext, or authentication tag mismatch</li>
     *                       <li>The chunk is compressed but no compression provider
     *                           was configured on this processor</li>
     *                       <li>Decompression fails due to corrupted compressed data
     *                           or size mismatch</li>
     *                     </ul>
     *
     * @see #processForWrite(byte[], int)
     * @see de.splatgames.aether.pack.core.format.ChunkHeader#isCompressed()
     * @see de.splatgames.aether.pack.core.format.ChunkHeader#isEncrypted()
     */
    public byte @NotNull [] processForRead(
            final byte @NotNull [] data,
            final int originalSize,
            final boolean compressed,
            final boolean encrypted) throws IOException {

        // Defense-in-depth: Validate originalSize to prevent decompression bombs
        if (originalSize < 0 || originalSize > de.splatgames.aether.pack.core.format.FormatConstants.MAX_CHUNK_SIZE) {
            throw new IOException("Invalid originalSize: " + originalSize +
                    " (must be 0-" + de.splatgames.aether.pack.core.format.FormatConstants.MAX_CHUNK_SIZE + ")");
        }

        byte[] result = data;

        // Step 1: Decryption
        if (encrypted) {
            if (this.encryptionProvider == null || this.encryptionKey == null) {
                throw new IOException("Data is encrypted but no encryption key provided");
            }
            try {
                result = this.encryptionProvider.decryptBlock(result, this.encryptionKey);
            } catch (final GeneralSecurityException e) {
                throw new IOException("Decryption failed", e);
            }
        }

        // Step 2: Decompression
        if (compressed) {
            if (this.compressionProvider == null) {
                throw new IOException("Data is compressed but no compression provider available");
            }
            result = this.compressionProvider.decompressBlock(result, originalSize);

            // Validate decompressed size matches expected originalSize
            if (result.length != originalSize) {
                throw new IOException("Decompression size mismatch: expected " +
                        originalSize + " bytes, got " + result.length +
                        " (possible truncation or corruption)");
            }
        } else {
            // For uncompressed data, originalSize should equal the data length
            if (result.length != originalSize) {
                throw new IOException("Chunk size mismatch: declared originalSize " +
                        originalSize + " bytes, but actual data is " + result.length +
                        " bytes (possible corruption)");
            }
        }

        return result;
    }

    /**
     * Represents the result of processing a chunk for writing.
     *
     * <p>This record contains the processed (compressed and/or encrypted) data
     * along with metadata about what transformations were applied. This information
     * is needed when writing chunk headers.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * ProcessedChunk result = processor.processForWrite(data, data.length);
     *
     * // Write chunk header with transformation flags
     * ChunkHeader header = ChunkHeader.builder()
     *     .originalSize(result.originalSize())
     *     .storedSize(result.storedSize())
     *     .compressed(result.compressed())
     *     .encrypted(result.encrypted())
     *     .build();
     *
     * // Write processed data
     * output.write(result.data());
     * }</pre>
     *
     * @param data         the processed (transformed) data ready for storage
     * @param originalSize the original unprocessed size in bytes
     * @param storedSize   the size of the processed data in bytes
     * @param compressed   {@code true} if compression was applied to this chunk
     * @param encrypted    {@code true} if encryption was applied to this chunk
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public record ProcessedChunk(
            byte @NotNull [] data,
            int originalSize,
            int storedSize,
            boolean compressed,
            boolean encrypted
    ) {
    }

    /**
     * A fluent builder for creating {@link ChunkProcessor} instances.
     *
     * <p>This builder allows configuring compression and encryption settings
     * for chunk processing. Both are optional; a processor with neither enabled
     * acts as a pass-through.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>Compression: Disabled (no provider)</li>
     *   <li>Encryption: Disabled (no provider/key)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * ChunkProcessor processor = ChunkProcessor.builder()
     *     .compression(zstdProvider, 3)
     *     .encryption(aesGcmProvider, secretKey)
     *     .build();
     * }</pre>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public static final class Builder {

        /** The compression provider to use, or null for no compression. */
        private @Nullable CompressionProvider compressionProvider;

        /** The compression level (-1 means use provider default). */
        private int compressionLevel = -1;

        /** The encryption provider to use, or null for no encryption. */
        private @Nullable EncryptionProvider encryptionProvider;

        /** The encryption key, or null for no encryption. */
        private @Nullable SecretKey encryptionKey;

        /**
         * Private constructor for the builder.
         */
        private Builder() {
        }

        /**
         * Configures compression using the provider's default compression level.
         *
         * <p>This method enables compression for the chunk processor using the
         * specified compression provider. The compression level will be set to
         * the provider's default level, which is typically a balanced trade-off
         * between compression ratio and speed.</p>
         *
         * <p>When compression is enabled, the processor will attempt to compress
         * chunk data during {@link ChunkProcessor#processForWrite} operations.
         * However, if the compressed data would be larger than the original
         * (which can occur with already-compressed or high-entropy data), the
         * original data is stored uncompressed.</p>
         *
         * <p>If a compression level has already been set via
         * {@link #compression(CompressionProvider, int)}, calling this method
         * will preserve that level. Otherwise, the provider's default level
         * will be used.</p>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * ChunkProcessor processor = ChunkProcessor.builder()
         *     .compression(ZstdCompressionProvider.getInstance())
         *     .build();
         * }</pre>
         *
         * @param provider the compression provider that implements the compression
         *                 algorithm to use (e.g., ZSTD, LZ4); must not be {@code null};
         *                 the provider's {@link CompressionProvider#getDefaultLevel()}
         *                 will be used if no explicit level was set
         * @return this builder instance to allow fluent method chaining for setting
         *         additional chunk processor configuration options
         *
         * @see #compression(CompressionProvider, int)
         * @see CompressionProvider#getDefaultLevel()
         * @see ChunkProcessor#isCompressionEnabled()
         */
        public @NotNull Builder compression(final @NotNull CompressionProvider provider) {
            this.compressionProvider = provider;
            if (this.compressionLevel < 0) {
                this.compressionLevel = provider.getDefaultLevel();
            }
            return this;
        }

        /**
         * Configures compression with a specific compression level.
         *
         * <p>This method enables compression for the chunk processor using the
         * specified compression provider and compression level. The level controls
         * the trade-off between compression ratio and processing speed:</p>
         * <ul>
         *   <li>Lower levels (e.g., 1-3) provide faster compression with lower ratios</li>
         *   <li>Higher levels (e.g., 9-19) provide better compression but are slower</li>
         *   <li>The exact level range depends on the compression algorithm</li>
         * </ul>
         *
         * <p>When compression is enabled, the processor will attempt to compress
         * chunk data during {@link ChunkProcessor#processForWrite} operations.
         * However, if the compressed data would be larger than the original
         * (which can occur with already-compressed or high-entropy data), the
         * original data is stored uncompressed and the chunk is marked accordingly.</p>
         *
         * <p><strong>\:</strong></p>
         * <p>Different compression algorithms support different level ranges:</p>
         * <ul>
         *   <li><strong>ZSTD:</strong> Levels 1-22, with negative levels for faster modes</li>
         *   <li><strong>LZ4:</strong> Typically levels 1-16</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * // High compression for archival
         * ChunkProcessor archival = ChunkProcessor.builder()
         *     .compression(ZstdCompressionProvider.getInstance(), 19)
         *     .build();
         *
         * // Fast compression for real-time streaming
         * ChunkProcessor streaming = ChunkProcessor.builder()
         *     .compression(Lz4CompressionProvider.getInstance(), 1)
         *     .build();
         * }</pre>
         *
         * @param provider the compression provider that implements the compression
         *                 algorithm to use (e.g., ZSTD, LZ4); must not be {@code null};
         *                 the provider determines which compression algorithm is used
         * @param level the compression level to use; the valid range depends on the
         *              compression algorithm; higher values typically provide better
         *              compression ratios at the cost of slower processing; values
         *              outside the provider's supported range may be clamped or cause
         *              an error during compression
         * @return this builder instance to allow fluent method chaining for setting
         *         additional chunk processor configuration options
         *
         * @see #compression(CompressionProvider)
         * @see CompressionProvider#getMinLevel()
         * @see CompressionProvider#getMaxLevel()
         * @see ChunkProcessor#isCompressionEnabled()
         */
        public @NotNull Builder compression(
                final @NotNull CompressionProvider provider,
                final int level) {

            this.compressionProvider = provider;
            this.compressionLevel = level;
            return this;
        }

        /**
         * Configures encryption with the specified provider and secret key.
         *
         * <p>This method enables encryption for the chunk processor using the
         * specified encryption provider and secret key. When encryption is enabled,
         * chunk data will be encrypted after compression (if enabled) during
         * {@link ChunkProcessor#processForWrite} operations, and decrypted before
         * decompression during {@link ChunkProcessor#processForRead} operations.</p>
         *
         * <p>The secret key must be compatible with the encryption provider's
         * algorithm requirements. For example:</p>
         * <ul>
         *   <li><strong>AES-256-GCM:</strong> Requires a 256-bit (32-byte) AES key</li>
         *   <li><strong>ChaCha20-Poly1305:</strong> Requires a 256-bit (32-byte) key</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <ul>
         *   <li>The secret key should be derived from a password using a secure
         *       key derivation function (KDF) such as Argon2id or PBKDF2</li>
         *   <li>Each chunk is encrypted with a unique nonce/IV generated by
         *       the encryption provider to ensure security</li>
         *   <li>Authenticated encryption (AEAD) is used to detect tampering</li>
         *   <li>The encrypted data includes the authentication tag for verification</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * // Derive key from password using Argon2id
         * SecretKey key = keyDeriver.deriveKey(password, salt);
         *
         * // Configure processor with encryption
         * ChunkProcessor processor = ChunkProcessor.builder()
         *     .compression(zstdProvider, 6)
         *     .encryption(AesGcmEncryptionProvider.getInstance(), key)
         *     .build();
         * }</pre>
         *
         * @param provider the encryption provider that implements the encryption
         *                 algorithm to use (e.g., AES-256-GCM, ChaCha20-Poly1305);
         *                 must not be {@code null}; the provider determines the
         *                 encryption algorithm, key size requirements, and output format
         * @param key the secret key to use for encryption and decryption operations;
         *            must not be {@code null}; the key must be compatible with the
         *            encryption provider's algorithm (e.g., a 256-bit key for AES-256);
         *            typically derived from a password using a secure KDF
         * @return this builder instance to allow fluent method chaining for setting
         *         additional chunk processor configuration options
         *
         * @see ChunkProcessor#isEncryptionEnabled()
         * @see EncryptionProvider#encryptBlock(byte[], SecretKey)
         * @see EncryptionProvider#decryptBlock(byte[], SecretKey)
         */
        public @NotNull Builder encryption(
                final @NotNull EncryptionProvider provider,
                final @NotNull SecretKey key) {

            this.encryptionProvider = provider;
            this.encryptionKey = key;
            return this;
        }

        /**
         * Builds and returns a new immutable {@link ChunkProcessor} instance.
         *
         * <p>This method constructs a new chunk processor using all the configuration
         * that has been set on this builder. The resulting processor is immutable
         * and thread-safe, making it suitable for concurrent use across multiple
         * threads.</p>
         *
         * <p><strong>\:</strong></p>
         * <p>If no configuration was set on this builder, the resulting processor
         * acts as a pass-through that applies no transformations:</p>
         * <ul>
         *   <li>No compression provider: Data is not compressed</li>
         *   <li>No encryption provider/key: Data is not encrypted</li>
         *   <li>Compression level defaults to the provider's default if a provider
         *       is set but no level was specified</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <p>The processor supports four configuration modes:</p>
         * <ol>
         *   <li><strong>Pass-through:</strong> Neither compression nor encryption</li>
         *   <li><strong>Compression only:</strong> Only compression provider set</li>
         *   <li><strong>Encryption only:</strong> Only encryption provider and key set</li>
         *   <li><strong>Both:</strong> Both compression and encryption configured</li>
         * </ol>
         *
         * <p><strong>\:</strong></p>
         * <p>The builder can be reused after calling this method to create additional
         * chunk processors with the same or modified configuration. The returned
         * processor is independent of the builder state.</p>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * // Create a processor with both compression and encryption
         * ChunkProcessor processor = ChunkProcessor.builder()
         *     .compression(ZstdCompressionProvider.getInstance(), 6)
         *     .encryption(AesGcmEncryptionProvider.getInstance(), secretKey)
         *     .build();
         *
         * // Use the processor
         * ProcessedChunk result = processor.processForWrite(data, data.length);
         * }</pre>
         *
         * @return a new immutable {@link ChunkProcessor} instance configured with
         *         the compression and encryption settings from this builder;
         *         never {@code null}; the processor is thread-safe and can be
         *         shared across multiple threads
         *
         * @see ChunkProcessor#passThrough()
         * @see ChunkProcessor#isCompressionEnabled()
         * @see ChunkProcessor#isEncryptionEnabled()
         */
        public @NotNull ChunkProcessor build() {
            final int level = this.compressionLevel >= 0
                    ? this.compressionLevel
                    : (this.compressionProvider != null ? this.compressionProvider.getDefaultLevel() : 0);

            return new ChunkProcessor(
                    this.compressionProvider,
                    level,
                    this.encryptionProvider,
                    this.encryptionKey
            );
        }

    }

}
