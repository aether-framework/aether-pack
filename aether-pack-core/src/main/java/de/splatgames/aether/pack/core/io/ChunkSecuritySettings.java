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

import de.splatgames.aether.pack.core.format.FormatConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Security settings for chunk validation during archive reading.
 *
 * <p>This record defines configurable limits for chunk size validation,
 * decompression bomb protection, and encryption overhead validation.
 * All settings have secure defaults and are bounded by absolute hard-caps
 * that cannot be exceeded.</p>
 *
 * <h2>Default Values</h2>
 * <ul>
 *   <li><b>maxChunkSize:</b> 64 MB - Maximum allowed uncompressed chunk size</li>
 *   <li><b>maxCompressionRatio:</b> 100,000x - Maximum compression expansion ratio</li>
 *   <li><b>maxEncryptionOverhead:</b> 1,024 bytes - Maximum encryption overhead per chunk</li>
 * </ul>
 *
 * <h2>Hard-Caps</h2>
 * <p>Even when overriding defaults, these absolute limits cannot be exceeded:</p>
 * <ul>
 *   <li><b>ABSOLUTE_MAX_CHUNK_SIZE:</b> 256 MB</li>
 *   <li><b>ABSOLUTE_MAX_COMPRESSION_RATIO:</b> 1,000,000x</li>
 *   <li><b>ABSOLUTE_MAX_ENCRYPTION_OVERHEAD:</b> 8,192 bytes</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Use secure defaults
 * ChunkSecuritySettings settings = ChunkSecuritySettings.DEFAULT;
 *
 * // Custom settings for specific use case
 * ChunkSecuritySettings custom = ChunkSecuritySettings.builder()
 *     .maxChunkSize(128 * 1024 * 1024) // 128 MB
 *     .maxCompressionRatio(200_000L)
 *     .build();
 *
 * // Use with reader
 * try (AetherPackReader reader = AetherPackReader.open(path, processor, custom)) {
 *     // ...
 * }
 * }</pre>
 *
 * @param maxChunkSize         Maximum allowed uncompressed chunk size in bytes.
 *                             Must be positive and at most {@link #ABSOLUTE_MAX_CHUNK_SIZE}.
 * @param maxCompressionRatio  Maximum allowed compression ratio (originalSize / storedSize).
 *                             Must be positive and at most {@link #ABSOLUTE_MAX_COMPRESSION_RATIO}.
 * @param maxEncryptionOverhead Maximum allowed encryption overhead in bytes
 *                             (storedSize - originalSize for encrypted chunks).
 *                             Must be non-negative and at most {@link #ABSOLUTE_MAX_ENCRYPTION_OVERHEAD}.
 *
 * @author Claude Code
 * @since 0.1.0
 */
public record ChunkSecuritySettings(
        int maxChunkSize,
        long maxCompressionRatio,
        int maxEncryptionOverhead
) {

    // ==================== Absolute Hard-Caps ====================

    /**
     * Absolute maximum chunk size that can be configured (256 MB).
     *
     * <p>This hard-cap prevents configuration of unreasonably large chunk sizes
     * that could lead to out-of-memory conditions even with explicit override.</p>
     */
    public static final int ABSOLUTE_MAX_CHUNK_SIZE = 256 * 1024 * 1024;

    /**
     * Absolute maximum compression ratio that can be configured (1,000,000x).
     *
     * <p>This hard-cap provides an ultimate safeguard against decompression bombs
     * even when the ratio limit is explicitly increased.</p>
     */
    public static final long ABSOLUTE_MAX_COMPRESSION_RATIO = 1_000_000L;

    /**
     * Absolute maximum encryption overhead that can be configured (8 KB).
     *
     * <p>This hard-cap prevents acceptance of chunks with unreasonably large
     * claimed encryption overhead, which could indicate malformed data.</p>
     */
    public static final int ABSOLUTE_MAX_ENCRYPTION_OVERHEAD = 8192;

    // ==================== Secure Defaults ====================

    /**
     * Default maximum chunk size (64 MB).
     *
     * <p>This matches {@link FormatConstants#MAX_CHUNK_SIZE} and provides
     * protection against memory exhaustion from oversized chunks.</p>
     */
    public static final int DEFAULT_MAX_CHUNK_SIZE = FormatConstants.MAX_CHUNK_SIZE;

    /**
     * Default maximum compression ratio (100,000x).
     *
     * <p>Legitimate highly-compressible data (like zeros) can achieve ratios
     * of 5,000x or more. This default allows for such cases while still
     * providing protection against decompression bombs.</p>
     */
    public static final long DEFAULT_MAX_COMPRESSION_RATIO = 100_000L;

    /**
     * Default maximum encryption overhead (1,024 bytes).
     *
     * <p>Typical authenticated encryption overhead is around 28-32 bytes
     * (IV + authentication tag). This default allows generous headroom
     * for future algorithms while detecting obviously malformed data.</p>
     */
    public static final int DEFAULT_MAX_ENCRYPTION_OVERHEAD = 1024;

    /**
     * Default security settings with secure, conservative limits.
     *
     * <p>These defaults are suitable for most use cases and provide strong
     * protection against malicious or malformed archives.</p>
     */
    public static final ChunkSecuritySettings DEFAULT = new ChunkSecuritySettings(
            DEFAULT_MAX_CHUNK_SIZE,
            DEFAULT_MAX_COMPRESSION_RATIO,
            DEFAULT_MAX_ENCRYPTION_OVERHEAD
    );

    // ==================== Validation ====================

    /**
     * Canonical constructor with validation against hard-caps.
     *
     * @throws IllegalArgumentException if any parameter exceeds its hard-cap
     *                                  or is invalid (negative/zero where not allowed)
     */
    public ChunkSecuritySettings {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException(
                    "maxChunkSize must be positive, got: " + maxChunkSize);
        }
        if (maxChunkSize > ABSOLUTE_MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "maxChunkSize " + maxChunkSize + " exceeds absolute hard-cap of " +
                            ABSOLUTE_MAX_CHUNK_SIZE + " bytes (" + (ABSOLUTE_MAX_CHUNK_SIZE / 1024 / 1024) + " MB)");
        }

        if (maxCompressionRatio <= 0) {
            throw new IllegalArgumentException(
                    "maxCompressionRatio must be positive, got: " + maxCompressionRatio);
        }
        if (maxCompressionRatio > ABSOLUTE_MAX_COMPRESSION_RATIO) {
            throw new IllegalArgumentException(
                    "maxCompressionRatio " + maxCompressionRatio + " exceeds absolute hard-cap of " +
                            ABSOLUTE_MAX_COMPRESSION_RATIO);
        }

        if (maxEncryptionOverhead < 0) {
            throw new IllegalArgumentException(
                    "maxEncryptionOverhead must be non-negative, got: " + maxEncryptionOverhead);
        }
        if (maxEncryptionOverhead > ABSOLUTE_MAX_ENCRYPTION_OVERHEAD) {
            throw new IllegalArgumentException(
                    "maxEncryptionOverhead " + maxEncryptionOverhead + " exceeds absolute hard-cap of " +
                            ABSOLUTE_MAX_ENCRYPTION_OVERHEAD + " bytes");
        }
    }

    // ==================== Builder ====================

    /**
     * Creates a new builder initialized with default values.
     *
     * @return a new builder instance
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new builder initialized with the values from this settings instance.
     *
     * @return a new builder with copied values
     */
    public @NotNull Builder toBuilder() {
        return new Builder()
                .maxChunkSize(this.maxChunkSize)
                .maxCompressionRatio(this.maxCompressionRatio)
                .maxEncryptionOverhead(this.maxEncryptionOverhead);
    }

    /**
     * Builder for {@link ChunkSecuritySettings}.
     *
     * <p>The builder starts with secure default values. Only the settings
     * that need to be customized must be explicitly set.</p>
     */
    public static final class Builder {

        private int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
        private long maxCompressionRatio = DEFAULT_MAX_COMPRESSION_RATIO;
        private int maxEncryptionOverhead = DEFAULT_MAX_ENCRYPTION_OVERHEAD;

        private Builder() {
        }

        /**
         * Sets the maximum allowed uncompressed chunk size.
         *
         * @param maxChunkSize maximum chunk size in bytes;
         *                     must be positive and at most {@link #ABSOLUTE_MAX_CHUNK_SIZE}
         * @return this builder
         */
        public @NotNull Builder maxChunkSize(final int maxChunkSize) {
            this.maxChunkSize = maxChunkSize;
            return this;
        }

        /**
         * Sets the maximum allowed compression ratio.
         *
         * @param maxCompressionRatio maximum ratio (originalSize / storedSize);
         *                            must be positive and at most {@link #ABSOLUTE_MAX_COMPRESSION_RATIO}
         * @return this builder
         */
        public @NotNull Builder maxCompressionRatio(final long maxCompressionRatio) {
            this.maxCompressionRatio = maxCompressionRatio;
            return this;
        }

        /**
         * Sets the maximum allowed encryption overhead per chunk.
         *
         * @param maxEncryptionOverhead maximum overhead in bytes;
         *                              must be non-negative and at most {@link #ABSOLUTE_MAX_ENCRYPTION_OVERHEAD}
         * @return this builder
         */
        public @NotNull Builder maxEncryptionOverhead(final int maxEncryptionOverhead) {
            this.maxEncryptionOverhead = maxEncryptionOverhead;
            return this;
        }

        /**
         * Builds the settings instance.
         *
         * @return a new {@link ChunkSecuritySettings} with the configured values
         * @throws IllegalArgumentException if any value exceeds its hard-cap
         */
        public @NotNull ChunkSecuritySettings build() {
            return new ChunkSecuritySettings(
                    this.maxChunkSize,
                    this.maxCompressionRatio,
                    this.maxEncryptionOverhead
            );
        }
    }
}
