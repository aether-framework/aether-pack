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

package de.splatgames.aether.pack.core.format;

import org.jetbrains.annotations.NotNull;

/**
 * Represents the simplified trailer for stream-mode APACK archives.
 *
 * <p>Stream mode is designed for single-entry archives where the content
 * is written and read sequentially (e.g., piped data, streaming compression).
 * The stream trailer provides a simplified structure compared to the full
 * {@link Trailer} used in container mode.</p>
 *
 * <h2>Stream Mode vs Container Mode</h2>
 * <ul>
 *   <li><strong>Stream Mode:</strong> Single entry, sequential access,
 *       simplified trailer (this class), suitable for pipes and streams</li>
 *   <li><strong>Container Mode:</strong> Multiple entries, random access
 *       via TOC, full {@link Trailer} with entry index</li>
 * </ul>
 *
 * <h2>Binary Layout</h2>
 * <p>The stream trailer is exactly {@link FormatConstants#STREAM_TRAILER_SIZE}
 * (32) bytes with the following structure (Little-Endian byte order):</p>
 * <pre>
 * Offset  Size  Field            Description
 * ──────────────────────────────────────────────────────────────────
 * 0x00    4     magic            "STRL" (ASCII)
 * 0x04    4     reserved         Reserved for future use
 * 0x08    8     originalSize     Original (uncompressed) size in bytes
 * 0x10    8     storedSize       Stored (compressed) size in bytes
 * 0x18    4     chunkCount       Total number of chunks written
 * 0x1C    4     trailerChecksum  CRC32 of trailer (excluding this field)
 * ──────────────────────────────────────────────────────────────────
 * Total: 32 bytes (0x20)
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create a stream trailer after writing stream data
 * StreamTrailer trailer = StreamTrailer.builder()
 *     .originalSize(totalBytesWritten)
 *     .storedSize(compressedBytes)
 *     .chunkCount(numberOfChunks)
 *     .trailerChecksum(computedCrc32)
 *     .build();
 *
 * // Calculate compression ratio
 * double ratio = (double) trailer.storedSize() / trailer.originalSize() * 100;
 * System.out.printf("Compressed to %.1f%% of original size\n", ratio);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This is an immutable record. Instances are thread-safe and can be
 * freely shared between threads.</p>
 *
 * @param originalSize    the original (uncompressed) total size of the entry
 *                        data in bytes
 * @param storedSize      the stored (compressed/encrypted) total size of the
 *                        entry data in bytes
 * @param chunkCount      the total number of data chunks written to the stream
 * @param trailerChecksum CRC32 checksum of the trailer (excluding this field)
 *                        for integrity verification
 *
 * @see FileHeader#isStreamMode()
 * @see Trailer
 * @see FormatConstants#STREAM_TRAILER_MAGIC
 * @see FormatConstants#STREAM_TRAILER_SIZE
 * @see FormatConstants#FLAG_STREAM_MODE
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record StreamTrailer(
        long originalSize,
        long storedSize,
        int chunkCount,
        int trailerChecksum
) {

    /**
     * Creates a new stream trailer builder initialized with default values.
     *
     * <p>The builder is pre-configured with all values set to zero.</p>
     *
     * @return a new builder instance; never {@code null}
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * A fluent builder for creating {@link StreamTrailer} instances.
     *
     * <p>This builder provides a convenient way to construct stream trailers
     * with customized settings. All setter methods return the builder instance
     * for method chaining.</p>
     *
     * <h2>Usage Example</h2>
     * <pre>{@code
     * StreamTrailer trailer = StreamTrailer.builder()
     *     .originalSize(1048576)
     *     .storedSize(524288)
     *     .chunkCount(4)
     *     .trailerChecksum(0x12345678)
     *     .build();
     * }</pre>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public static final class Builder {

        private long originalSize = 0;
        private long storedSize = 0;
        private int chunkCount = 0;
        private int trailerChecksum = 0;

        private Builder() {
        }

        /**
         * Sets the original (uncompressed) total size of the stream data in bytes.
         *
         * <p>The original size represents the total uncompressed size of all data
         * written to the stream. For stream mode archives containing a single entry,
         * this is the size of that entry's original content before compression.</p>
         *
         * <p>This value is essential for:</p>
         * <ul>
         *   <li>Allocating the correct buffer size for decompression</li>
         *   <li>Calculating the compression ratio achieved</li>
         *   <li>Verifying that decompression produced the expected amount of data</li>
         *   <li>Progress reporting during extraction</li>
         * </ul>
         *
         * @param originalSize the original uncompressed size of the stream data
         *                     in bytes; must be a non-negative value representing
         *                     the total bytes before compression was applied
         * @return this builder instance to allow fluent method chaining for setting
         *         additional stream trailer properties
         *
         * @see #storedSize(long)
         * @see StreamTrailer#originalSize()
         */
        public @NotNull Builder originalSize(final long originalSize) {
            this.originalSize = originalSize;
            return this;
        }

        /**
         * Sets the stored (compressed and/or encrypted) total size of the stream data in bytes.
         *
         * <p>The stored size represents the total number of bytes written to the
         * stream after compression and/or encryption have been applied. This is the
         * actual size of the stream data in the archive file, excluding headers
         * and the trailer itself.</p>
         *
         * <p>Comparing this to {@link #originalSize(long)} gives the compression
         * ratio achieved:</p>
         * <pre>
         * double ratio = (double) storedSize / originalSize * 100;
         * System.out.printf("Compressed to %.1f%% of original size\n", ratio);
         * </pre>
         *
         * @param storedSize the stored size of the stream data in bytes after
         *                   compression and/or encryption; must be a non-negative
         *                   value representing the actual bytes in the archive
         * @return this builder instance to allow fluent method chaining for setting
         *         additional stream trailer properties
         *
         * @see #originalSize(long)
         * @see StreamTrailer#storedSize()
         */
        public @NotNull Builder storedSize(final long storedSize) {
            this.storedSize = storedSize;
            return this;
        }

        /**
         * Sets the total number of data chunks written to the stream.
         *
         * <p>The chunk count indicates how many {@link ChunkHeader} structures
         * and their associated data blocks were written to the stream. This
         * value is useful for:</p>
         * <ul>
         *   <li>Verifying that all chunks were successfully read</li>
         *   <li>Progress reporting during stream reading</li>
         *   <li>Validating stream integrity</li>
         * </ul>
         *
         * <p>The chunk count is determined by dividing the original data size
         * by the configured chunk size (rounding up).</p>
         *
         * @param chunkCount the total number of data chunks in the stream;
         *                   must be a non-negative integer; typically calculated
         *                   as ceil(originalSize / chunkSize)
         * @return this builder instance to allow fluent method chaining for setting
         *         additional stream trailer properties
         *
         * @see ChunkHeader
         * @see FormatConstants#DEFAULT_CHUNK_SIZE
         * @see StreamTrailer#chunkCount()
         */
        public @NotNull Builder chunkCount(final int chunkCount) {
            this.chunkCount = chunkCount;
            return this;
        }

        /**
         * Sets the CRC32 checksum of the trailer for integrity verification.
         *
         * <p>The trailer checksum is computed over the trailer bytes (excluding
         * the checksum field itself) and is used to verify the integrity of the
         * trailer metadata. This checksum protects the original size, stored size,
         * and chunk count fields from corruption.</p>
         *
         * <p>During reading, the checksum is recomputed from the trailer data
         * and compared against this stored value. A mismatch indicates that
         * the trailer has been corrupted or the stream was not properly closed.</p>
         *
         * @param trailerChecksum the CRC32 checksum value computed over the
         *                        trailer bytes; this is a 32-bit unsigned value
         *                        stored as a signed int
         * @return this builder instance to allow fluent method chaining for setting
         *         additional stream trailer properties
         *
         * @see StreamTrailer#trailerChecksum()
         */
        public @NotNull Builder trailerChecksum(final int trailerChecksum) {
            this.trailerChecksum = trailerChecksum;
            return this;
        }

        /**
         * Builds and returns a new immutable {@link StreamTrailer} instance.
         *
         * <p>This method constructs a new stream trailer using all the values
         * that have been set on this builder. Any values not explicitly set
         * will use their default values (typically 0).</p>
         *
         * <p>The resulting stream trailer is immutable and thread-safe. The
         * builder can be reused after calling this method to create additional
         * stream trailers with different or modified values.</p>
         *
         * @return a new immutable {@link StreamTrailer} instance containing all
         *         the configured values; never {@code null}
         *
         * @see StreamTrailer
         */
        public @NotNull StreamTrailer build() {
            return new StreamTrailer(
                    this.originalSize,
                    this.storedSize,
                    this.chunkCount,
                    this.trailerChecksum
            );
        }

    }

}
