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
 * Represents the fixed-size header of a data chunk in an APACK archive.
 *
 * <p>Each entry in an APACK archive is divided into one or more chunks of
 * configurable size (see {@link FormatConstants#DEFAULT_CHUNK_SIZE}). Each
 * chunk has a header that precedes its data, describing the chunk's size,
 * checksum, and processing status.</p>
 *
 * <h2>Binary Layout</h2>
 * <p>The chunk header is exactly {@link FormatConstants#CHUNK_HEADER_SIZE}
 * (24) bytes with the following structure (Little-Endian byte order):</p>
 * <pre>
 * Offset  Size  Field         Description
 * ──────────────────────────────────────────────────────────────────
 * 0x00    4     magic         "CHNK" (ASCII)
 * 0x04    4     chunkIndex    Zero-based chunk index within entry
 * 0x08    4     originalSize  Uncompressed chunk size in bytes
 * 0x0C    4     storedSize    Stored (compressed) size in bytes
 * 0x10    4     checksum      CRC32 or XXH3-32 of chunk data
 * 0x14    4     flags         Chunk flags (see below)
 * ──────────────────────────────────────────────────────────────────
 * Total: 24 bytes (0x18)
 * </pre>
 *
 * <h2>Chunk Flags</h2>
 * <p>The {@code flags} field is a bitmask containing the following flags:</p>
 * <ul>
 *   <li>{@link FormatConstants#CHUNK_FLAG_LAST} (0x01) - This is the last chunk of the entry</li>
 *   <li>{@link FormatConstants#CHUNK_FLAG_COMPRESSED} (0x02) - This chunk is compressed</li>
 *   <li>{@link FormatConstants#CHUNK_FLAG_ENCRYPTED} (0x04) - This chunk is encrypted</li>
 * </ul>
 *
 * <h2>Chunk Processing</h2>
 * <p>When reading a chunk:</p>
 * <ol>
 *   <li>Read the chunk header (24 bytes)</li>
 *   <li>Read {@code storedSize} bytes of chunk data</li>
 *   <li>If encrypted, decrypt the data</li>
 *   <li>If compressed, decompress the data</li>
 *   <li>Verify the checksum against the processed data</li>
 * </ol>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create a chunk header for a compressed chunk
 * ChunkHeader header = ChunkHeader.builder()
 *     .chunkIndex(0)
 *     .originalSize(262144)
 *     .storedSize(128000)
 *     .checksum(0xABCD1234)
 *     .compressed(true)
 *     .last(true)
 *     .build();
 *
 * // Check chunk properties
 * if (header.isLast()) {
 *     System.out.println("Processing final chunk");
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This is an immutable record. Instances are thread-safe and can be
 * freely shared between threads.</p>
 *
 * @param chunkIndex   the zero-based index of this chunk within the entry;
 *                     the first chunk has index 0
 * @param originalSize the uncompressed size of this chunk's data in bytes;
 *                     typically equals the configured chunk size except for
 *                     the last chunk which may be smaller
 * @param storedSize   the stored size of this chunk's data in bytes after
 *                     compression and/or encryption; may be larger than
 *                     originalSize if encryption adds padding
 * @param checksum     the checksum of the chunk data for integrity verification;
 *                     algorithm depends on archive configuration
 * @param flags        bitmask of chunk flags indicating last chunk status,
 *                     compression, and encryption
 *
 * @see EntryHeader
 * @see FormatConstants#CHUNK_HEADER_SIZE
 * @see FormatConstants#DEFAULT_CHUNK_SIZE
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ChunkHeader(
        int chunkIndex,
        int originalSize,
        int storedSize,
        int checksum,
        int flags
) {

    /**
     * Creates a new chunk header builder initialized with default values.
     *
     * <p>The builder is pre-configured with all values set to zero and
     * no flags set.</p>
     *
     * @return a new builder instance; never {@code null}
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Checks if this is the last chunk of the entry.
     *
     * <p>The last chunk may have a smaller {@link #originalSize()} than
     * the configured chunk size. After reading this chunk, no more chunks
     * should be read for the current entry.</p>
     *
     * @return {@code true} if this is the final chunk
     *         (bit 0 of flags is set), {@code false} otherwise
     *
     * @see FormatConstants#CHUNK_FLAG_LAST
     */
    public boolean isLast() {
        return (this.flags & FormatConstants.CHUNK_FLAG_LAST) != 0;
    }

    /**
     * Checks if this chunk's data is compressed.
     *
     * <p>Even if the entry has compression enabled, individual chunks may
     * not be compressed if compression would not reduce their size. This
     * flag indicates whether this specific chunk requires decompression.</p>
     *
     * @return {@code true} if the chunk data is compressed
     *         (bit 1 of flags is set), {@code false} otherwise
     *
     * @see FormatConstants#CHUNK_FLAG_COMPRESSED
     */
    public boolean isCompressed() {
        return (this.flags & FormatConstants.CHUNK_FLAG_COMPRESSED) != 0;
    }

    /**
     * Checks if this chunk's data is encrypted.
     *
     * <p>When encrypted, the chunk data must be decrypted before any
     * decompression is applied. The encryption algorithm and key are
     * determined by the archive's encryption configuration.</p>
     *
     * @return {@code true} if the chunk data is encrypted
     *         (bit 2 of flags is set), {@code false} otherwise
     *
     * @see FormatConstants#CHUNK_FLAG_ENCRYPTED
     */
    public boolean isEncrypted() {
        return (this.flags & FormatConstants.CHUNK_FLAG_ENCRYPTED) != 0;
    }

    /**
     * A fluent builder for creating {@link ChunkHeader} instances.
     *
     * <p>This builder provides a convenient way to construct chunk headers
     * with customized settings. All setter methods return the builder
     * instance for method chaining.</p>
     *
     * <h2>Usage Example</h2>
     * <pre>{@code
     * ChunkHeader header = ChunkHeader.builder()
     *     .chunkIndex(3)
     *     .originalSize(65536)
     *     .storedSize(32000)
     *     .checksum(0x12345678)
     *     .compressed(true)
     *     .last(false)
     *     .build();
     * }</pre>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public static final class Builder {

        private int chunkIndex = 0;
        private int originalSize = 0;
        private int storedSize = 0;
        private int checksum = 0;
        private int flags = 0;

        private Builder() {
        }

        /**
         * Sets the zero-based chunk index within the entry.
         *
         * <p>The chunk index indicates the position of this chunk within the
         * sequence of chunks that make up an entry. The first chunk has index 0,
         * the second has index 1, and so on. This index is used to:</p>
         * <ul>
         *   <li>Verify chunks are read in the correct order</li>
         *   <li>Enable random access to specific chunks within an entry</li>
         *   <li>Detect missing or corrupted chunks during reading</li>
         * </ul>
         *
         * <p>The chunk index must be unique and sequential within an entry.
         * When reading, if a chunk index is missing or out of sequence, it
         * indicates data corruption or an incomplete write.</p>
         *
         * @param chunkIndex the zero-based chunk index within the entry; must be
         *                   a non-negative integer starting from 0 for the first
         *                   chunk and incrementing by 1 for each subsequent chunk
         * @return this builder instance to allow fluent method chaining for setting
         *         additional chunk header properties
         *
         * @see ChunkHeader#chunkIndex()
         */
        public @NotNull Builder chunkIndex(final int chunkIndex) {
            this.chunkIndex = chunkIndex;
            return this;
        }

        /**
         * Sets the original (uncompressed) size of this chunk's data in bytes.
         *
         * <p>The original size represents the size of the chunk data before any
         * compression is applied. This value is essential for:</p>
         * <ul>
         *   <li>Allocating the correct buffer size for decompression</li>
         *   <li>Verifying that decompression produced the expected output size</li>
         *   <li>Calculating compression ratios for this specific chunk</li>
         * </ul>
         *
         * <p>For most chunks, this value equals the configured chunk size
         * (see {@link FormatConstants#DEFAULT_CHUNK_SIZE}). The last chunk of
         * an entry may have a smaller original size if the entry data doesn't
         * evenly divide by the chunk size.</p>
         *
         * <p>For uncompressed chunks, this value equals {@link #storedSize(int)}.</p>
         *
         * @param originalSize the original uncompressed size of this chunk's data
         *                     in bytes; must be a positive integer not exceeding
         *                     the maximum chunk size
         * @return this builder instance to allow fluent method chaining for setting
         *         additional chunk header properties
         *
         * @see #storedSize(int)
         * @see FormatConstants#DEFAULT_CHUNK_SIZE
         * @see FormatConstants#MAX_CHUNK_SIZE
         * @see ChunkHeader#originalSize()
         */
        public @NotNull Builder originalSize(final int originalSize) {
            this.originalSize = originalSize;
            return this;
        }

        /**
         * Sets the stored (compressed and/or encrypted) size of this chunk's data in bytes.
         *
         * <p>The stored size represents the actual number of bytes written to the
         * archive for this chunk, after compression and/or encryption have been
         * applied. This value is used to:</p>
         * <ul>
         *   <li>Determine exactly how many bytes to read for this chunk</li>
         *   <li>Calculate the file offset of the next chunk</li>
         *   <li>Verify that the correct amount of data was written</li>
         * </ul>
         *
         * <p>For compressed chunks, this value is typically smaller than
         * {@link #originalSize(int)}. However, if the data is not compressible,
         * this value may equal or even exceed the original size. For encrypted
         * chunks, this value includes any encryption overhead (IV, padding,
         * authentication tag).</p>
         *
         * @param storedSize the stored size of this chunk's data in bytes after
         *                   compression and/or encryption; must be a positive
         *                   integer representing the actual bytes in the archive
         * @return this builder instance to allow fluent method chaining for setting
         *         additional chunk header properties
         *
         * @see #originalSize(int)
         * @see ChunkHeader#storedSize()
         */
        public @NotNull Builder storedSize(final int storedSize) {
            this.storedSize = storedSize;
            return this;
        }

        /**
         * Sets the checksum of the chunk data for integrity verification.
         *
         * <p>The checksum is computed over the original (uncompressed) chunk data
         * and is used to verify data integrity after decompression. The checksum
         * algorithm used depends on the archive configuration, typically CRC32
         * or XXH3-32 for chunk-level checksums.</p>
         *
         * <p>During reading, after decompressing the chunk data, the checksum is
         * recomputed and compared against this stored value. A mismatch indicates
         * data corruption, either in the compressed data or during decompression.</p>
         *
         * <p>The checksum is a 32-bit value stored as a signed int but should be
         * treated as an unsigned value for comparison purposes.</p>
         *
         * @param checksum the checksum value computed over the original uncompressed
         *                 chunk data; interpretation depends on the checksum algorithm
         *                 configured for the archive
         * @return this builder instance to allow fluent method chaining for setting
         *         additional chunk header properties
         *
         * @see ChunkHeader#checksum()
         */
        public @NotNull Builder checksum(final int checksum) {
            this.checksum = checksum;
            return this;
        }

        /**
         * Sets the raw chunk flags bitmask directly.
         *
         * <p>The flags bitmask encodes multiple boolean properties of the chunk
         * in a compact format. Each bit position represents a different property
         * as defined in {@link FormatConstants}:</p>
         * <ul>
         *   <li>Bit 0 ({@link FormatConstants#CHUNK_FLAG_LAST}): This is the last chunk</li>
         *   <li>Bit 1 ({@link FormatConstants#CHUNK_FLAG_COMPRESSED}): Chunk is compressed</li>
         *   <li>Bit 2 ({@link FormatConstants#CHUNK_FLAG_ENCRYPTED}): Chunk is encrypted</li>
         * </ul>
         *
         * <p>For type-safe flag manipulation, prefer using the individual flag methods
         * like {@link #last(boolean)}, {@link #compressed(boolean)}, and
         * {@link #encrypted(boolean)} instead of setting the raw bitmask directly.</p>
         *
         * @param flags the raw flags bitmask containing combined chunk properties;
         *              only the lower 3 bits are currently defined; higher bits
         *              are reserved for future use
         * @return this builder instance to allow fluent method chaining for setting
         *         additional chunk header properties
         *
         * @see #last(boolean)
         * @see #compressed(boolean)
         * @see #encrypted(boolean)
         * @see FormatConstants#CHUNK_FLAG_LAST
         * @see FormatConstants#CHUNK_FLAG_COMPRESSED
         * @see FormatConstants#CHUNK_FLAG_ENCRYPTED
         */
        public @NotNull Builder flags(final int flags) {
            this.flags = flags;
            return this;
        }

        /**
         * Marks whether this is the last chunk of the entry.
         *
         * <p>When set to {@code true}, the corresponding flag bit
         * ({@link FormatConstants#CHUNK_FLAG_LAST}) is set in the chunk's flags
         * field. This indicates that no more chunks follow for the current entry,
         * signaling the reader to stop reading chunks and proceed to the next
         * entry or the archive trailer.</p>
         *
         * <p>The last chunk may have a smaller {@link #originalSize(int)} than
         * the configured chunk size, as it contains only the remaining data
         * that didn't fill a complete chunk.</p>
         *
         * <p>This flag is essential for correct archive parsing. Without it,
         * the reader would not know when to stop reading chunks for an entry.</p>
         *
         * @param last {@code true} to mark this as the final chunk of the entry
         *             and set the last chunk flag bit; {@code false} to clear
         *             the flag and indicate more chunks follow
         * @return this builder instance to allow fluent method chaining for setting
         *         additional chunk header properties
         *
         * @see FormatConstants#CHUNK_FLAG_LAST
         * @see ChunkHeader#isLast()
         */
        public @NotNull Builder last(final boolean last) {
            if (last) {
                this.flags |= FormatConstants.CHUNK_FLAG_LAST;
            } else {
                this.flags &= ~FormatConstants.CHUNK_FLAG_LAST;
            }
            return this;
        }

        /**
         * Marks whether this chunk's data is compressed.
         *
         * <p>When set to {@code true}, the corresponding flag bit
         * ({@link FormatConstants#CHUNK_FLAG_COMPRESSED}) is set in the chunk's
         * flags field. This indicates that the stored data for this chunk has
         * been compressed and must be decompressed during reading.</p>
         *
         * <p>Even if the entry has compression enabled, individual chunks may
         * be stored uncompressed if compression would not reduce their size
         * (e.g., for already-compressed data like JPEG images or ZIP files).
         * This per-chunk flag allows optimal handling of mixed content.</p>
         *
         * <p>The compression algorithm is determined by the entry's
         * {@link EntryHeader#compressionId()}, not by this chunk flag.</p>
         *
         * @param compressed {@code true} to mark this chunk as compressed and set
         *                   the compression flag bit; {@code false} to indicate
         *                   the chunk data is stored uncompressed
         * @return this builder instance to allow fluent method chaining for setting
         *         additional chunk header properties
         *
         * @see FormatConstants#CHUNK_FLAG_COMPRESSED
         * @see ChunkHeader#isCompressed()
         * @see EntryHeader#compressionId()
         */
        public @NotNull Builder compressed(final boolean compressed) {
            if (compressed) {
                this.flags |= FormatConstants.CHUNK_FLAG_COMPRESSED;
            } else {
                this.flags &= ~FormatConstants.CHUNK_FLAG_COMPRESSED;
            }
            return this;
        }

        /**
         * Marks whether this chunk's data is encrypted.
         *
         * <p>When set to {@code true}, the corresponding flag bit
         * ({@link FormatConstants#CHUNK_FLAG_ENCRYPTED}) is set in the chunk's
         * flags field. This indicates that the stored data for this chunk has
         * been encrypted and must be decrypted during reading.</p>
         *
         * <p>When a chunk is both compressed and encrypted, the processing order
         * during reading is: decrypt first, then decompress. This is the reverse
         * of the writing order, which compresses first, then encrypts.</p>
         *
         * <p>The encryption algorithm and key are determined by the archive's
         * encryption configuration stored in the {@link EncryptionBlock}.</p>
         *
         * @param encrypted {@code true} to mark this chunk as encrypted and set
         *                  the encryption flag bit; {@code false} to indicate
         *                  the chunk data is stored unencrypted
         * @return this builder instance to allow fluent method chaining for setting
         *         additional chunk header properties
         *
         * @see FormatConstants#CHUNK_FLAG_ENCRYPTED
         * @see ChunkHeader#isEncrypted()
         * @see EntryHeader#encryptionId()
         * @see EncryptionBlock
         */
        public @NotNull Builder encrypted(final boolean encrypted) {
            if (encrypted) {
                this.flags |= FormatConstants.CHUNK_FLAG_ENCRYPTED;
            } else {
                this.flags &= ~FormatConstants.CHUNK_FLAG_ENCRYPTED;
            }
            return this;
        }

        /**
         * Builds and returns a new immutable {@link ChunkHeader} instance.
         *
         * <p>This method constructs a new chunk header using all the values
         * that have been set on this builder. Any values not explicitly set
         * will use their default values (typically 0 or false).</p>
         *
         * <p>The resulting chunk header is immutable and thread-safe. The
         * builder can be reused after calling this method to create additional
         * chunk headers with different or modified values.</p>
         *
         * @return a new immutable {@link ChunkHeader} instance containing all
         *         the configured values; never {@code null}
         *
         * @see ChunkHeader
         */
        public @NotNull ChunkHeader build() {
            return new ChunkHeader(
                    this.chunkIndex,
                    this.originalSize,
                    this.storedSize,
                    this.checksum,
                    this.flags
            );
        }

    }

}
