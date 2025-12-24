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
 * Represents the 64-byte file header of an APACK archive file.
 *
 * <p>The file header is the first structure in every APACK file and contains
 * essential metadata for archive identification, version compatibility checking,
 * and format configuration. It is always exactly {@link FormatConstants#FILE_HEADER_SIZE}
 * (64) bytes in size.</p>
 *
 * <h2>Binary Layout</h2>
 * <p>The file header has the following structure (all multi-byte integers are
 * stored in Little-Endian byte order):</p>
 * <pre>
 * Offset  Size  Field               Description
 * ──────────────────────────────────────────────────────────────────
 * 0x00    5     magic               "APACK" (ASCII)
 * 0x05    1     versionMajor        Format major version
 * 0x06    1     versionMinor        Format minor version
 * 0x07    1     versionPatch        Format patch version
 * 0x08    1     compatLevel         Minimum reader version required
 * 0x09    1     modeFlags           Mode flags (see below)
 * 0x0A    1     checksumAlgorithm   Checksum algorithm ID
 * 0x0B    1     reserved            Reserved for future use
 * 0x0C    4     chunkSize           Default chunk size in bytes
 * 0x10    4     headerChecksum      CRC32 of bytes 0x00-0x0F
 * 0x14    8     entryCount          Number of entries (0 for stream mode)
 * 0x1C    8     trailerOffset       Absolute offset to trailer
 * 0x24    8     creationTimestamp   Creation time (ms since Unix epoch)
 * 0x2C    20    reserved            Reserved for future use
 * ──────────────────────────────────────────────────────────────────
 * Total: 64 bytes (0x40)
 * </pre>
 *
 * <h2>Mode Flags</h2>
 * <p>The {@code modeFlags} field is a bitmask containing the following flags:</p>
 * <ul>
 *   <li>{@link FormatConstants#FLAG_STREAM_MODE} (0x01) - Stream mode (single entry)</li>
 *   <li>{@link FormatConstants#FLAG_ENCRYPTED} (0x02) - Encryption enabled</li>
 *   <li>{@link FormatConstants#FLAG_COMPRESSED} (0x04) - Compression enabled</li>
 *   <li>{@link FormatConstants#FLAG_RANDOM_ACCESS} (0x08) - Random access TOC present</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create a file header with the builder
 * FileHeader header = FileHeader.builder()
 *     .chunkSize(128 * 1024)
 *     .compressed(true)
 *     .randomAccess(true)
 *     .checksumAlgorithm(FormatConstants.CHECKSUM_XXH3_64)
 *     .build();
 *
 * // Check flags
 * if (header.isCompressed()) {
 *     System.out.println("Archive uses compression");
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This is an immutable record. Instances are thread-safe and can be
 * freely shared between threads.</p>
 *
 * @param versionMajor      the format major version; current version is
 *                          {@link FormatConstants#FORMAT_VERSION_MAJOR}
 * @param versionMinor      the format minor version; current version is
 *                          {@link FormatConstants#FORMAT_VERSION_MINOR}
 * @param versionPatch      the format patch version; current version is
 *                          {@link FormatConstants#FORMAT_VERSION_PATCH}
 * @param compatLevel       the minimum reader version required to read this file;
 *                          readers with a lower version should refuse to open the file
 * @param modeFlags         bitmask of mode flags indicating stream mode, encryption,
 *                          compression, and random access capabilities
 * @param checksumAlgorithm the checksum algorithm ID used for data integrity
 *                          verification (e.g., {@link FormatConstants#CHECKSUM_CRC32},
 *                          {@link FormatConstants#CHECKSUM_XXH3_64})
 * @param chunkSize         the default chunk size in bytes; must be between
 *                          {@link FormatConstants#MIN_CHUNK_SIZE} and
 *                          {@link FormatConstants#MAX_CHUNK_SIZE}
 * @param headerChecksum    CRC32 checksum of header bytes 0x00-0x0F for
 *                          header integrity verification
 * @param entryCount        the number of entries in the archive; always 0 for
 *                          stream mode archives where entry count is unknown
 * @param trailerOffset     the absolute file offset to the trailer; 0 if no
 *                          trailer is present (e.g., in streaming scenarios)
 * @param creationTimestamp the archive creation timestamp in milliseconds
 *                          since the Unix epoch (January 1, 1970 00:00:00 UTC)
 *
 * @see FormatConstants
 * @see EntryHeader
 * @see Trailer
 * @see de.splatgames.aether.pack.core.io.HeaderIO
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record FileHeader(
        int versionMajor,
        int versionMinor,
        int versionPatch,
        int compatLevel,
        int modeFlags,
        int checksumAlgorithm,
        int chunkSize,
        int headerChecksum,
        long entryCount,
        long trailerOffset,
        long creationTimestamp
) {

    /**
     * Creates a new file header builder initialized with default values.
     *
     * <p>The builder is pre-configured with:</p>
     * <ul>
     *   <li>Current format version ({@link FormatConstants#FORMAT_VERSION_MAJOR}.
     *       {@link FormatConstants#FORMAT_VERSION_MINOR}.
     *       {@link FormatConstants#FORMAT_VERSION_PATCH})</li>
     *   <li>Default chunk size ({@link FormatConstants#DEFAULT_CHUNK_SIZE})</li>
     *   <li>XXH3-64 checksum algorithm</li>
     *   <li>Current timestamp as creation time</li>
     *   <li>No mode flags set</li>
     * </ul>
     *
     * @return a new builder instance; never {@code null}
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Checks if this archive uses stream mode (single entry).
     *
     * <p>Stream mode archives contain exactly one entry and are optimized
     * for sequential reading/writing. They use a simplified trailer structure
     * and do not support random access.</p>
     *
     * @return {@code true} if stream mode is enabled (bit 0 of modeFlags is set),
     *         {@code false} for container mode (multiple entries)
     *
     * @see FormatConstants#FLAG_STREAM_MODE
     */
    public boolean isStreamMode() {
        return (this.modeFlags & FormatConstants.FLAG_STREAM_MODE) != 0;
    }

    /**
     * Checks if this archive has encryption enabled.
     *
     * <p>When encryption is enabled, the archive contains an encryption block
     * after the file header with key derivation parameters and the wrapped
     * data encryption key.</p>
     *
     * @return {@code true} if encryption is enabled (bit 1 of modeFlags is set),
     *         {@code false} otherwise
     *
     * @see FormatConstants#FLAG_ENCRYPTED
     * @see EncryptionBlock
     */
    public boolean isEncrypted() {
        return (this.modeFlags & FormatConstants.FLAG_ENCRYPTED) != 0;
    }

    /**
     * Checks if this archive has compression enabled.
     *
     * <p>When compression is enabled, entry data chunks may be compressed
     * using the algorithm specified in each entry's header. Individual
     * chunks track whether they are actually compressed via chunk flags.</p>
     *
     * @return {@code true} if compression is enabled (bit 2 of modeFlags is set),
     *         {@code false} otherwise
     *
     * @see FormatConstants#FLAG_COMPRESSED
     * @see ChunkHeader#isCompressed()
     */
    public boolean isCompressed() {
        return (this.modeFlags & FormatConstants.FLAG_COMPRESSED) != 0;
    }

    /**
     * Checks if this archive has a random access Table of Contents.
     *
     * <p>When random access is enabled, the archive trailer contains a TOC
     * that maps entry IDs and name hashes to file offsets, allowing entries
     * to be accessed directly without sequential scanning.</p>
     *
     * @return {@code true} if random access is enabled (bit 3 of modeFlags is set),
     *         {@code false} otherwise
     *
     * @see FormatConstants#FLAG_RANDOM_ACCESS
     * @see Trailer
     * @see TocEntry
     */
    public boolean hasRandomAccess() {
        return (this.modeFlags & FormatConstants.FLAG_RANDOM_ACCESS) != 0;
    }

    /**
     * A fluent builder for creating {@link FileHeader} instances.
     *
     * <p>This builder provides a convenient way to construct file headers with
     * customized settings. All setter methods return the builder instance for
     * method chaining.</p>
     *
     * <h2>Default Values</h2>
     * <p>The builder is initialized with sensible defaults:</p>
     * <ul>
     *   <li>Version: Current format version (1.0.0)</li>
     *   <li>Compatibility level: 1</li>
     *   <li>Mode flags: 0 (container mode, no encryption/compression)</li>
     *   <li>Checksum algorithm: XXH3-64</li>
     *   <li>Chunk size: 256 KB</li>
     *   <li>Creation timestamp: Current system time</li>
     * </ul>
     *
     * <h2>Usage Example</h2>
     * <pre>{@code
     * FileHeader header = FileHeader.builder()
     *     .compressed(true)
     *     .encrypted(true)
     *     .randomAccess(true)
     *     .chunkSize(512 * 1024)
     *     .entryCount(42)
     *     .build();
     * }</pre>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public static final class Builder {

        private int versionMajor = FormatConstants.FORMAT_VERSION_MAJOR;
        private int versionMinor = FormatConstants.FORMAT_VERSION_MINOR;
        private int versionPatch = FormatConstants.FORMAT_VERSION_PATCH;
        private int compatLevel = FormatConstants.COMPAT_LEVEL;
        private int modeFlags = 0;
        private int checksumAlgorithm = FormatConstants.CHECKSUM_XXH3_64;
        private int chunkSize = FormatConstants.DEFAULT_CHUNK_SIZE;
        private int headerChecksum = 0;
        private long entryCount = 0;
        private long trailerOffset = 0;
        private long creationTimestamp = System.currentTimeMillis();

        private Builder() {
        }

        /**
         * Sets the format major version number for this archive.
         *
         * <p>The major version number indicates significant format changes that are
         * not backwards compatible. Readers should refuse to open archives with a
         * major version higher than what they support. The current format major
         * version is {@link FormatConstants#FORMAT_VERSION_MAJOR}.</p>
         *
         * <p>In semantic versioning terms, a change in major version indicates that
         * the binary format has changed in a way that older readers cannot understand.
         * For example, changes to the file header structure, chunk format, or trailer
         * layout would require a major version increment.</p>
         *
         * @param versionMajor the major version number to set; must be a non-negative
         *                     integer representing the format generation. The current
         *                     version is {@value FormatConstants#FORMAT_VERSION_MAJOR}.
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         */
        public @NotNull Builder versionMajor(final int versionMajor) {
            this.versionMajor = versionMajor;
            return this;
        }

        /**
         * Sets the format minor version number for this archive.
         *
         * <p>The minor version number indicates backwards-compatible feature additions.
         * Readers supporting a given major version should be able to read archives with
         * any minor version of that major, though they may not understand all features.
         * The current format minor version is {@link FormatConstants#FORMAT_VERSION_MINOR}.</p>
         *
         * <p>Minor version increments typically indicate new optional features that
         * older readers can safely ignore, such as new attribute types, additional
         * compression algorithms, or new metadata fields in reserved space.</p>
         *
         * @param versionMinor the minor version number to set; must be a non-negative
         *                     integer. The current version is
         *                     {@value FormatConstants#FORMAT_VERSION_MINOR}.
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         */
        public @NotNull Builder versionMinor(final int versionMinor) {
            this.versionMinor = versionMinor;
            return this;
        }

        /**
         * Sets the format patch version number for this archive.
         *
         * <p>The patch version number indicates bug fixes or clarifications to the
         * format specification that do not change the binary format. Archives with
         * different patch versions of the same major.minor should be fully
         * interchangeable. The current format patch version is
         * {@link FormatConstants#FORMAT_VERSION_PATCH}.</p>
         *
         * <p>Patch version increments are used for documentation updates, reference
         * implementation bug fixes, or other non-format-changing modifications.</p>
         *
         * @param versionPatch the patch version number to set; must be a non-negative
         *                     integer. The current version is
         *                     {@value FormatConstants#FORMAT_VERSION_PATCH}.
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         */
        public @NotNull Builder versionPatch(final int versionPatch) {
            this.versionPatch = versionPatch;
            return this;
        }

        /**
         * Sets the minimum compatibility level required to read this archive file.
         *
         * <p>The compatibility level provides fine-grained control over reader
         * requirements beyond the major/minor/patch version. A reader must have a
         * compatibility level greater than or equal to this value to open the archive.
         * This allows writers to indicate when they've used features that require
         * specific reader capabilities.</p>
         *
         * <p>For example, if a new optional encryption algorithm is used, the writer
         * can set a higher compat level to ensure only readers supporting that
         * algorithm will attempt to open the file. The current compatibility level
         * is {@link FormatConstants#COMPAT_LEVEL}.</p>
         *
         * @param compatLevel the minimum compatibility level required; must be a
         *                    non-negative integer. Readers with a lower level will
         *                    refuse to open the archive, typically throwing an
         *                    {@link de.splatgames.aether.pack.core.exception.UnsupportedVersionException}.
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         */
        public @NotNull Builder compatLevel(final int compatLevel) {
            this.compatLevel = compatLevel;
            return this;
        }

        /**
         * Sets the raw mode flags bitmask directly for this archive.
         *
         * <p>The mode flags field is a bitmask that controls fundamental archive
         * behavior including streaming mode, encryption, compression, and random
         * access capabilities. Each bit corresponds to a specific feature flag.</p>
         *
         * <p>For type-safe and more readable flag manipulation, it is recommended
         * to use the individual boolean setter methods instead:</p>
         * <ul>
         *   <li>{@link #streamMode(boolean)} - for single-entry streaming archives</li>
         *   <li>{@link #encrypted(boolean)} - to enable encryption</li>
         *   <li>{@link #compressed(boolean)} - to enable compression</li>
         *   <li>{@link #randomAccess(boolean)} - to enable TOC for random access</li>
         * </ul>
         *
         * <p>This method is useful when you need to set multiple flags at once
         * from a pre-computed value or when restoring flags from a parsed header.</p>
         *
         * @param modeFlags the mode flags bitmask to set; a combination of flag
         *                  constants OR'd together:
         *                  {@link FormatConstants#FLAG_STREAM_MODE} (0x01),
         *                  {@link FormatConstants#FLAG_ENCRYPTED} (0x02),
         *                  {@link FormatConstants#FLAG_COMPRESSED} (0x04),
         *                  {@link FormatConstants#FLAG_RANDOM_ACCESS} (0x08)
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         * @see FormatConstants#FLAG_STREAM_MODE
         * @see FormatConstants#FLAG_ENCRYPTED
         * @see FormatConstants#FLAG_COMPRESSED
         * @see FormatConstants#FLAG_RANDOM_ACCESS
         */
        public @NotNull Builder modeFlags(final int modeFlags) {
            this.modeFlags = modeFlags;
            return this;
        }

        /**
         * Enables or disables stream mode for this archive.
         *
         * <p>Stream mode is designed for single-entry archives that are written and
         * read sequentially. When enabled, the archive structure is optimized for
         * streaming scenarios where the total size is unknown at the start of writing.</p>
         *
         * <p>Key characteristics of stream mode:</p>
         * <ul>
         *   <li>Contains exactly one entry</li>
         *   <li>Uses a simplified stream trailer instead of full trailer with TOC</li>
         *   <li>Entry count in header is set to 0 (unknown)</li>
         *   <li>Does not support random access</li>
         *   <li>Ideal for piped or network streaming scenarios</li>
         * </ul>
         *
         * <p>When disabled (container mode), the archive can contain multiple entries
         * and supports full random access via the Table of Contents in the trailer.</p>
         *
         * @param streamMode {@code true} to enable stream mode for single-entry
         *                   sequential access; {@code false} for container mode
         *                   which supports multiple entries and random access
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         * @see FormatConstants#FLAG_STREAM_MODE
         * @see StreamTrailer
         */
        public @NotNull Builder streamMode(final boolean streamMode) {
            if (streamMode) {
                this.modeFlags |= FormatConstants.FLAG_STREAM_MODE;
            } else {
                this.modeFlags &= ~FormatConstants.FLAG_STREAM_MODE;
            }
            return this;
        }

        /**
         * Enables or disables encryption for this archive.
         *
         * <p>When encryption is enabled, the archive will contain an encryption block
         * immediately after the file header. This block contains the key derivation
         * function parameters (salt, iterations, memory cost for Argon2id) and the
         * wrapped Data Encryption Key (DEK).</p>
         *
         * <p>The actual data encryption is performed at the chunk level using AEAD
         * ciphers (AES-256-GCM or ChaCha20-Poly1305), providing both confidentiality
         * and integrity protection. Each chunk is encrypted with a unique nonce.</p>
         *
         * <p>Note that enabling this flag only indicates that encryption is used;
         * the actual encryption provider and key must be configured separately
         * when creating the archive writer.</p>
         *
         * @param encrypted {@code true} to mark this archive as encrypted, which
         *                  indicates an encryption block is present and all chunk
         *                  data is encrypted; {@code false} for unencrypted archives
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         * @see FormatConstants#FLAG_ENCRYPTED
         * @see EncryptionBlock
         */
        public @NotNull Builder encrypted(final boolean encrypted) {
            if (encrypted) {
                this.modeFlags |= FormatConstants.FLAG_ENCRYPTED;
            } else {
                this.modeFlags &= ~FormatConstants.FLAG_ENCRYPTED;
            }
            return this;
        }

        /**
         * Enables or disables compression for this archive.
         *
         * <p>When compression is enabled, entry data may be compressed before storage.
         * The actual compression is performed at the chunk level, and each chunk's
         * header indicates whether that specific chunk is compressed. This allows
         * for selective compression - chunks that don't compress well can be stored
         * uncompressed to avoid expansion.</p>
         *
         * <p>Supported compression algorithms include:</p>
         * <ul>
         *   <li>ZSTD (Zstandard) - High compression ratio, configurable levels 1-22</li>
         *   <li>LZ4 - Very fast compression and decompression, levels 1-12</li>
         * </ul>
         *
         * <p>Note that enabling this flag only indicates that compression may be used;
         * the actual compression provider and level must be configured separately
         * when creating the archive writer. Individual entries or chunks may still
         * be stored uncompressed if compression provides no benefit.</p>
         *
         * @param compressed {@code true} to enable compression for this archive,
         *                   indicating that chunks may be compressed; {@code false}
         *                   for uncompressed storage of all data
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         * @see FormatConstants#FLAG_COMPRESSED
         * @see ChunkHeader#isCompressed()
         */
        public @NotNull Builder compressed(final boolean compressed) {
            if (compressed) {
                this.modeFlags |= FormatConstants.FLAG_COMPRESSED;
            } else {
                this.modeFlags &= ~FormatConstants.FLAG_COMPRESSED;
            }
            return this;
        }

        /**
         * Enables or disables random access support via Table of Contents.
         *
         * <p>When random access is enabled, the archive trailer contains a Table of
         * Contents (TOC) that maps entry IDs and name hashes to their file offsets.
         * This allows readers to seek directly to any entry without scanning through
         * all preceding entries.</p>
         *
         * <p>Each TOC entry contains:</p>
         * <ul>
         *   <li>Entry ID - unique identifier for direct lookup</li>
         *   <li>Entry offset - absolute file position of the entry header</li>
         *   <li>Original and stored sizes - for progress calculation</li>
         *   <li>Name hash - XXH3-32 hash for fast name-based lookup</li>
         * </ul>
         *
         * <p>Random access is recommended for container mode archives that will be
         * read multiple times or where specific entries need to be accessed without
         * reading the entire archive. It should be disabled for pure streaming
         * scenarios where sequential access is sufficient.</p>
         *
         * @param randomAccess {@code true} to include a Table of Contents in the
         *                     trailer for O(1) entry lookup; {@code false} to omit
         *                     the TOC, requiring sequential scanning to find entries
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         * @see FormatConstants#FLAG_RANDOM_ACCESS
         * @see Trailer
         * @see TocEntry
         */
        public @NotNull Builder randomAccess(final boolean randomAccess) {
            if (randomAccess) {
                this.modeFlags |= FormatConstants.FLAG_RANDOM_ACCESS;
            } else {
                this.modeFlags &= ~FormatConstants.FLAG_RANDOM_ACCESS;
            }
            return this;
        }

        /**
         * Sets the checksum algorithm ID used for data integrity verification.
         *
         * <p>The checksum algorithm is used to compute integrity checksums for
         * all data in the archive, including chunk data, headers, and the trailer.
         * The algorithm ID is stored in the file header so readers know which
         * algorithm to use for verification.</p>
         *
         * <p>Available checksum algorithms:</p>
         * <ul>
         *   <li>{@link FormatConstants#CHECKSUM_CRC32} (0) - Standard CRC-32,
         *       widely compatible but slower than XXH3</li>
         *   <li>{@link FormatConstants#CHECKSUM_XXH3_64} (1) - XXH3 64-bit hash,
         *       extremely fast with excellent distribution (recommended)</li>
         *   <li>{@link FormatConstants#CHECKSUM_XXH3_128} (2) - XXH3 128-bit hash,
         *       for applications requiring higher collision resistance</li>
         * </ul>
         *
         * <p>The default algorithm is XXH3-64, which provides an excellent balance
         * of speed and reliability for most use cases.</p>
         *
         * @param checksumAlgorithm the numeric algorithm ID to use; should be one
         *                          of the {@code CHECKSUM_*} constants from
         *                          {@link FormatConstants}. Invalid IDs will cause
         *                          reader errors when opening the archive.
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         * @see FormatConstants#CHECKSUM_CRC32
         * @see FormatConstants#CHECKSUM_XXH3_64
         * @see FormatConstants#CHECKSUM_XXH3_128
         */
        public @NotNull Builder checksumAlgorithm(final int checksumAlgorithm) {
            this.checksumAlgorithm = checksumAlgorithm;
            return this;
        }

        /**
         * Sets the default chunk size in bytes for splitting entry data.
         *
         * <p>Entry data is split into chunks of this size for processing. Each chunk
         * is independently compressed (if enabled), encrypted (if enabled), and
         * checksummed. The chunk size affects several performance characteristics:</p>
         *
         * <ul>
         *   <li><strong>Smaller chunks (16-64 KB):</strong>
         *       <ul>
         *         <li>Better random access granularity</li>
         *         <li>Lower memory usage during processing</li>
         *         <li>More overhead (more chunk headers)</li>
         *         <li>Potentially worse compression ratio</li>
         *       </ul>
         *   </li>
         *   <li><strong>Larger chunks (256 KB - 1 MB):</strong>
         *       <ul>
         *         <li>Better compression ratio</li>
         *         <li>Less overhead (fewer chunk headers)</li>
         *         <li>Higher memory usage</li>
         *         <li>Coarser random access granularity</li>
         *       </ul>
         *   </li>
         * </ul>
         *
         * <p>The chunk size must be between {@link FormatConstants#MIN_CHUNK_SIZE}
         * (1 KB) and {@link FormatConstants#MAX_CHUNK_SIZE} (64 MB). The default
         * is {@link FormatConstants#DEFAULT_CHUNK_SIZE} (256 KB), which provides
         * a good balance for most use cases.</p>
         *
         * @param chunkSize the chunk size in bytes; must be between
         *                  {@value FormatConstants#MIN_CHUNK_SIZE} and
         *                  {@value FormatConstants#MAX_CHUNK_SIZE} bytes inclusive
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         * @throws IllegalArgumentException if the chunk size is outside the valid range
         *                                  (validation occurs at build time or write time)
         * @see FormatConstants#DEFAULT_CHUNK_SIZE
         * @see FormatConstants#MIN_CHUNK_SIZE
         * @see FormatConstants#MAX_CHUNK_SIZE
         */
        public @NotNull Builder chunkSize(final int chunkSize) {
            this.chunkSize = chunkSize;
            return this;
        }

        /**
         * Sets the CRC32 checksum of the file header for integrity verification.
         *
         * <p>The header checksum covers bytes 0x00 through 0x0F of the file header
         * (the first 16 bytes containing magic, version, compat level, mode flags,
         * checksum algorithm, and reserved byte). This allows early detection of
         * header corruption before attempting to parse the rest of the file.</p>
         *
         * <p>This value is typically computed automatically by the writer after
         * all other header fields are set. Manual setting is only needed when
         * reconstructing a header from raw bytes or for testing purposes.</p>
         *
         * <p>The checksum uses standard CRC-32 (IEEE 802.3 polynomial) regardless
         * of the data checksum algorithm selected for the archive contents.</p>
         *
         * @param headerChecksum the CRC32 checksum value computed over header bytes
         *                       0x00-0x0F; stored as an unsigned 32-bit integer
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         */
        public @NotNull Builder headerChecksum(final int headerChecksum) {
            this.headerChecksum = headerChecksum;
            return this;
        }

        /**
         * Sets the total number of entries contained in this archive.
         *
         * <p>For container mode archives, this field contains the actual count of
         * entries in the archive. For stream mode archives, this is set to 0 because
         * the entry count is not known until streaming is complete.</p>
         *
         * <p>This value is typically updated by the writer after all entries have
         * been written. During the initial write, it may be set to 0 or an estimate,
         * then updated in a second pass when writing to a seekable output.</p>
         *
         * <p>Readers can use this value to:</p>
         * <ul>
         *   <li>Pre-allocate collections for entries</li>
         *   <li>Validate the trailer's entry count matches</li>
         *   <li>Provide progress information during extraction</li>
         * </ul>
         *
         * @param entryCount the number of entries in the archive; should be 0 for
         *                   stream mode or when the count is unknown; otherwise
         *                   a non-negative count of entries
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         */
        public @NotNull Builder entryCount(final long entryCount) {
            this.entryCount = entryCount;
            return this;
        }

        /**
         * Sets the absolute file offset to the trailer structure.
         *
         * <p>The trailer offset points to the start of the trailer structure at the
         * end of the archive file. The trailer contains the Table of Contents (if
         * random access is enabled) and summary metadata about the archive.</p>
         *
         * <p>This value is typically set to 0 initially and updated after all entries
         * have been written, when the trailer's position is known. For stream mode
         * archives, this points to the stream trailer. For non-seekable outputs,
         * this value may remain 0.</p>
         *
         * <p>Readers use this offset to seek directly to the trailer for random
         * access operations. If the offset is 0 or invalid, readers must scan
         * sequentially through the file.</p>
         *
         * @param trailerOffset the absolute byte offset from the start of the file
         *                      to the trailer structure; 0 if unknown or not applicable
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         * @see Trailer
         * @see StreamTrailer
         */
        public @NotNull Builder trailerOffset(final long trailerOffset) {
            this.trailerOffset = trailerOffset;
            return this;
        }

        /**
         * Sets the archive creation timestamp.
         *
         * <p>The creation timestamp records when the archive was originally created.
         * By default, this is set to the current system time when the builder is
         * instantiated. This timestamp can be used for:</p>
         * <ul>
         *   <li>Displaying archive information to users</li>
         *   <li>Cache invalidation logic</li>
         *   <li>Audit trails and logging</li>
         *   <li>Reproducible builds (by setting a fixed timestamp)</li>
         * </ul>
         *
         * <p>The timestamp is stored as milliseconds since the Unix epoch
         * (January 1, 1970, 00:00:00 UTC). This provides millisecond precision
         * and is compatible with Java's {@link System#currentTimeMillis()} and
         * {@link java.time.Instant}.</p>
         *
         * @param creationTimestamp the creation time in milliseconds since the Unix
         *                          epoch (January 1, 1970, 00:00:00 UTC); typically
         *                          obtained from {@link System#currentTimeMillis()}
         * @return this builder instance to allow fluent method chaining for setting
         *         additional header properties
         */
        public @NotNull Builder creationTimestamp(final long creationTimestamp) {
            this.creationTimestamp = creationTimestamp;
            return this;
        }

        /**
         * Builds and returns a new immutable {@link FileHeader} instance.
         *
         * <p>This method creates a new FileHeader with all the values configured
         * through this builder. The returned header is immutable and can be safely
         * shared between threads or stored for later use.</p>
         *
         * <p>After calling this method, the builder can continue to be used to
         * create additional headers with different values. The built header is
         * independent of the builder's state.</p>
         *
         * <p>Example usage:</p>
         * <pre>{@code
         * FileHeader header = FileHeader.builder()
         *     .compressed(true)
         *     .chunkSize(128 * 1024)
         *     .build();
         *
         * // The header is now ready to be written
         * HeaderIO.writeFileHeader(writer, header);
         * }</pre>
         *
         * @return a new immutable {@link FileHeader} instance containing all the
         *         configured values; never {@code null}
         */
        public @NotNull FileHeader build() {
            return new FileHeader(
                    this.versionMajor,
                    this.versionMinor,
                    this.versionPatch,
                    this.compatLevel,
                    this.modeFlags,
                    this.checksumAlgorithm,
                    this.chunkSize,
                    this.headerChecksum,
                    this.entryCount,
                    this.trailerOffset,
                    this.creationTimestamp
            );
        }

    }

}
