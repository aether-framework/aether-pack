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
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Represents the header of an entry in an APACK archive file.
 *
 * <p>Each entry in an APACK archive begins with an entry header that describes
 * the entry's metadata, including its name, size, compression and encryption
 * settings, and optional custom attributes. Entry headers are aligned to
 * {@link FormatConstants#ENTRY_ALIGNMENT 8-byte} boundaries.</p>
 *
 * <h2>Binary Layout</h2>
 * <p>The entry header has a variable-length structure with the following fields
 * (all multi-byte integers are stored in Little-Endian byte order):</p>
 * <pre>
 * Offset  Size      Field           Description
 * ──────────────────────────────────────────────────────────────────
 * 0x00    4         magic           "ENTR" (ASCII)
 * 0x04    1         headerVersion   Entry header format version
 * 0x05    1         flags           Entry flags (see below)
 * 0x06    2         reserved        Reserved for future use
 * 0x08    8         entryId         Unique entry ID
 * 0x10    8         originalSize    Uncompressed size in bytes
 * 0x18    8         storedSize      Stored (compressed) size in bytes
 * 0x20    4         chunkCount      Number of data chunks
 * 0x24    1         compressionId   Compression algorithm ID
 * 0x25    1         encryptionId    Encryption algorithm ID
 * 0x26    2         nameLength      Entry name length in bytes
 * 0x28    2         mimeTypeLength  MIME type length in bytes
 * 0x2A    2         attrCount       Number of custom attributes
 * 0x2C    4         headerChecksum  CRC32 of header (excluding this)
 * 0x30    N         name            Entry name (UTF-8, no null terminator)
 * 0x30+N  M         mimeType        MIME type (UTF-8, no null terminator)
 * ...     ...       attributes      Custom attributes (key-value pairs)
 * ...     0-7       padding         Padding to 8-byte boundary
 * ──────────────────────────────────────────────────────────────────
 * Minimum size: 56 bytes (with empty name, no MIME type, no attributes)
 * </pre>
 *
 * <h2>Entry Flags</h2>
 * <p>The {@code flags} field is a bitmask containing the following flags:</p>
 * <ul>
 *   <li>{@link FormatConstants#ENTRY_FLAG_HAS_ATTRIBUTES} (0x01) - Has custom attributes</li>
 *   <li>{@link FormatConstants#ENTRY_FLAG_COMPRESSED} (0x02) - Entry is compressed</li>
 *   <li>{@link FormatConstants#ENTRY_FLAG_ENCRYPTED} (0x04) - Entry is encrypted</li>
 *   <li>{@link FormatConstants#ENTRY_FLAG_HAS_ECC} (0x08) - Has Reed-Solomon error correction</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create an entry header for a compressed file
 * EntryHeader header = EntryHeader.builder()
 *     .name("data/config.json")
 *     .mimeType("application/json")
 *     .originalSize(4096)
 *     .storedSize(1024)
 *     .compressed(true)
 *     .compressionId(FormatConstants.COMPRESSION_ZSTD)
 *     .chunkCount(1)
 *     .build();
 *
 * // Check entry properties
 * if (header.isCompressed()) {
 *     long ratio = (header.storedSize() * 100) / header.originalSize();
 *     System.out.println("Compression ratio: " + ratio + "%");
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This is an immutable record. The attributes list is defensively copied
 * during construction. Instances are thread-safe and can be freely shared
 * between threads.</p>
 *
 * @param headerVersion   the entry header format version; current version is
 *                        {@link #CURRENT_VERSION}
 * @param flags           bitmask of entry flags indicating compression, encryption,
 *                        attributes presence, and error correction
 * @param entryId         unique identifier for this entry within the archive;
 *                        used for random access via the TOC
 * @param originalSize    the uncompressed size of the entry data in bytes
 * @param storedSize      the stored size of the entry data in bytes (after
 *                        compression and/or encryption); equals originalSize
 *                        if no compression is applied
 * @param chunkCount      the number of data chunks that make up this entry;
 *                        entries are split into chunks of configurable size
 * @param compressionId   the compression algorithm ID used for this entry;
 *                        {@link FormatConstants#COMPRESSION_NONE} if not compressed
 * @param encryptionId    the encryption algorithm ID used for this entry;
 *                        {@link FormatConstants#ENCRYPTION_NONE} if not encrypted
 * @param headerChecksum  CRC32 checksum of the entry header for integrity verification
 * @param name            the entry name encoded in UTF-8; typically a relative path
 *                        with forward slashes as separators
 * @param mimeType        the MIME type of the entry content; may be empty string
 *                        if not specified
 * @param attributes      an immutable list of custom key-value attributes attached
 *                        to this entry; never {@code null}, may be empty
 *
 * @see FileHeader
 * @see ChunkHeader
 * @see Attribute
 * @see TocEntry
 * @see FormatConstants#ENTRY_HEADER_MIN_SIZE
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record EntryHeader(
        int headerVersion,
        int flags,
        long entryId,
        long originalSize,
        long storedSize,
        int chunkCount,
        int compressionId,
        int encryptionId,
        int headerChecksum,
        @NotNull String name,
        @NotNull String mimeType,
        @NotNull List<Attribute> attributes
) {

    /**
     * The current entry header format version.
     *
     * <p>This version number is incremented when the entry header format changes
     * in a way that requires reader updates. Readers should check this version
     * and handle older formats appropriately.</p>
     */
    public static final int CURRENT_VERSION = 1;

    /**
     * Compact constructor that creates a defensive copy of the attributes list.
     *
     * <p>This ensures that the entry header is truly immutable and that external
     * modifications to the original list do not affect this record.</p>
     */
    public EntryHeader {
        attributes = List.copyOf(attributes);
    }

    /**
     * Creates a new entry header builder initialized with default values.
     *
     * <p>The builder is pre-configured with:</p>
     * <ul>
     *   <li>Current header version ({@link #CURRENT_VERSION})</li>
     *   <li>No flags set</li>
     *   <li>No compression or encryption</li>
     *   <li>Empty name and MIME type</li>
     *   <li>Empty attributes list</li>
     * </ul>
     *
     * @return a new builder instance; never {@code null}
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Checks if this entry has custom attributes attached.
     *
     * <p>Custom attributes are key-value pairs that can store arbitrary
     * metadata with the entry, such as author information, timestamps,
     * or application-specific data.</p>
     *
     * @return {@code true} if the entry has custom attributes
     *         (bit 0 of flags is set), {@code false} otherwise
     *
     * @see FormatConstants#ENTRY_FLAG_HAS_ATTRIBUTES
     * @see #attributes()
     * @see Attribute
     */
    public boolean hasAttributes() {
        return (this.flags & FormatConstants.ENTRY_FLAG_HAS_ATTRIBUTES) != 0;
    }

    /**
     * Checks if this entry's data is compressed.
     *
     * <p>When an entry is compressed, its {@link #storedSize()} will typically
     * be smaller than its {@link #originalSize()}. The compression algorithm
     * used is identified by {@link #compressionId()}.</p>
     *
     * @return {@code true} if the entry is compressed
     *         (bit 1 of flags is set), {@code false} otherwise
     *
     * @see FormatConstants#ENTRY_FLAG_COMPRESSED
     * @see #compressionId()
     */
    public boolean isCompressed() {
        return (this.flags & FormatConstants.ENTRY_FLAG_COMPRESSED) != 0;
    }

    /**
     * Checks if this entry's data is encrypted.
     *
     * <p>When an entry is encrypted, the data chunks are encrypted using
     * the algorithm identified by {@link #encryptionId()}. The encryption
     * key is derived from the archive's master key.</p>
     *
     * @return {@code true} if the entry is encrypted
     *         (bit 2 of flags is set), {@code false} otherwise
     *
     * @see FormatConstants#ENTRY_FLAG_ENCRYPTED
     * @see #encryptionId()
     */
    public boolean isEncrypted() {
        return (this.flags & FormatConstants.ENTRY_FLAG_ENCRYPTED) != 0;
    }

    /**
     * Checks if this entry has Reed-Solomon error correction data.
     *
     * <p>When ECC is enabled, additional parity data is stored with each
     * chunk to allow recovery from bit errors. This is useful for archival
     * storage on potentially unreliable media.</p>
     *
     * @return {@code true} if error correction is present
     *         (bit 3 of flags is set), {@code false} otherwise
     *
     * @see FormatConstants#ENTRY_FLAG_HAS_ECC
     */
    public boolean hasEcc() {
        return (this.flags & FormatConstants.ENTRY_FLAG_HAS_ECC) != 0;
    }

    /**
     * A fluent builder for creating {@link EntryHeader} instances.
     *
     * <p>This builder provides a convenient way to construct entry headers with
     * customized settings. All setter methods return the builder instance for
     * method chaining.</p>
     *
     * <h2>Default Values</h2>
     * <p>The builder is initialized with sensible defaults:</p>
     * <ul>
     *   <li>Header version: {@link EntryHeader#CURRENT_VERSION}</li>
     *   <li>Flags: 0 (no compression, encryption, attributes, or ECC)</li>
     *   <li>Entry ID: 0</li>
     *   <li>Sizes: 0</li>
     *   <li>Compression/encryption: None</li>
     *   <li>Name and MIME type: Empty strings</li>
     *   <li>Attributes: Empty list</li>
     * </ul>
     *
     * <h2>Usage Example</h2>
     * <pre>{@code
     * EntryHeader header = EntryHeader.builder()
     *     .name("images/photo.jpg")
     *     .mimeType("image/jpeg")
     *     .entryId(1)
     *     .originalSize(102400)
     *     .storedSize(45000)
     *     .compressed(true)
     *     .compressionId(FormatConstants.COMPRESSION_ZSTD)
     *     .chunkCount(2)
     *     .attributes(List.of(
     *         Attribute.ofString("author", "John Doe"),
     *         Attribute.ofLong("timestamp", System.currentTimeMillis())
     *     ))
     *     .build();
     * }</pre>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public static final class Builder {

        private int headerVersion = CURRENT_VERSION;
        private int flags = 0;
        private long entryId = 0;
        private long originalSize = 0;
        private long storedSize = 0;
        private int chunkCount = 0;
        private int compressionId = FormatConstants.COMPRESSION_NONE;
        private int encryptionId = FormatConstants.ENCRYPTION_NONE;
        private int headerChecksum = 0;
        private @NotNull String name = "";
        private @NotNull String mimeType = "";
        private @NotNull List<Attribute> attributes = Collections.emptyList();

        private Builder() {
        }

        /**
         * Sets the entry header format version number.
         *
         * <p>The header version indicates the format of this specific entry header
         * within the archive. This allows the format to evolve while maintaining
         * backward compatibility with older entries. Readers should check this
         * version to determine which fields are present and how to interpret them.</p>
         *
         * <p>The current entry header version is {@link EntryHeader#CURRENT_VERSION}.
         * Older versions may have fewer fields or different interpretations of
         * existing fields.</p>
         *
         * @param headerVersion the entry header format version number to set; should
         *                      typically be {@link EntryHeader#CURRENT_VERSION} for
         *                      new entries; must be a non-negative integer
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see EntryHeader#CURRENT_VERSION
         */
        public @NotNull Builder headerVersion(final int headerVersion) {
            this.headerVersion = headerVersion;
            return this;
        }

        /**
         * Sets the raw entry flags bitmask directly.
         *
         * <p>The flags bitmask encodes multiple boolean properties of the entry
         * in a compact format. Each bit position represents a different property
         * as defined in {@link FormatConstants}:</p>
         * <ul>
         *   <li>Bit 0 ({@link FormatConstants#ENTRY_FLAG_HAS_ATTRIBUTES}): Entry has custom attributes</li>
         *   <li>Bit 1 ({@link FormatConstants#ENTRY_FLAG_COMPRESSED}): Entry data is compressed</li>
         *   <li>Bit 2 ({@link FormatConstants#ENTRY_FLAG_ENCRYPTED}): Entry data is encrypted</li>
         *   <li>Bit 3 ({@link FormatConstants#ENTRY_FLAG_HAS_ECC}): Entry has Reed-Solomon error correction</li>
         * </ul>
         *
         * <p>For type-safe flag manipulation, prefer using the individual flag methods
         * like {@link #compressed(boolean)}, {@link #encrypted(boolean)}, and
         * {@link #hasEcc(boolean)} instead of setting the raw bitmask directly.</p>
         *
         * @param flags the raw flags bitmask containing combined entry properties;
         *              only the lower 4 bits are currently defined; higher bits
         *              are reserved for future use
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see #compressed(boolean)
         * @see #encrypted(boolean)
         * @see #hasEcc(boolean)
         * @see FormatConstants#ENTRY_FLAG_HAS_ATTRIBUTES
         * @see FormatConstants#ENTRY_FLAG_COMPRESSED
         * @see FormatConstants#ENTRY_FLAG_ENCRYPTED
         * @see FormatConstants#ENTRY_FLAG_HAS_ECC
         */
        public @NotNull Builder flags(final int flags) {
            this.flags = flags;
            return this;
        }

        /**
         * Enables or disables the compression flag for this entry.
         *
         * <p>When compression is enabled (set to {@code true}), the corresponding
         * flag bit ({@link FormatConstants#ENTRY_FLAG_COMPRESSED}) is set in the
         * entry's flags field. This indicates that the entry's data chunks have
         * been compressed and must be decompressed during reading.</p>
         *
         * <p>When compression is enabled, the {@link #compressionId(int)} should
         * also be set to indicate which compression algorithm was used. The
         * {@link #storedSize(long)} will typically be smaller than
         * {@link #originalSize(long)} when compression is effective.</p>
         *
         * <p>Note that setting this flag does not actually compress the data;
         * it only marks the entry as compressed in the header. The actual
         * compression is performed by the archive writer.</p>
         *
         * @param compressed {@code true} to mark this entry as compressed and set
         *                   the compression flag bit; {@code false} to clear the
         *                   compression flag and mark the entry as uncompressed
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see FormatConstants#ENTRY_FLAG_COMPRESSED
         * @see #compressionId(int)
         * @see EntryHeader#isCompressed()
         */
        public @NotNull Builder compressed(final boolean compressed) {
            if (compressed) {
                this.flags |= FormatConstants.ENTRY_FLAG_COMPRESSED;
            } else {
                this.flags &= ~FormatConstants.ENTRY_FLAG_COMPRESSED;
            }
            return this;
        }

        /**
         * Enables or disables the encryption flag for this entry.
         *
         * <p>When encryption is enabled (set to {@code true}), the corresponding
         * flag bit ({@link FormatConstants#ENTRY_FLAG_ENCRYPTED}) is set in the
         * entry's flags field. This indicates that the entry's data chunks have
         * been encrypted and must be decrypted during reading using the archive's
         * encryption key.</p>
         *
         * <p>When encryption is enabled, the {@link #encryptionId(int)} should
         * also be set to indicate which encryption algorithm was used. The
         * encryption key is derived from the archive's master key stored in
         * the {@link EncryptionBlock}.</p>
         *
         * <p>Note that setting this flag does not actually encrypt the data;
         * it only marks the entry as encrypted in the header. The actual
         * encryption is performed by the archive writer.</p>
         *
         * @param encrypted {@code true} to mark this entry as encrypted and set
         *                  the encryption flag bit; {@code false} to clear the
         *                  encryption flag and mark the entry as unencrypted
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see FormatConstants#ENTRY_FLAG_ENCRYPTED
         * @see #encryptionId(int)
         * @see EntryHeader#isEncrypted()
         * @see EncryptionBlock
         */
        public @NotNull Builder encrypted(final boolean encrypted) {
            if (encrypted) {
                this.flags |= FormatConstants.ENTRY_FLAG_ENCRYPTED;
            } else {
                this.flags &= ~FormatConstants.ENTRY_FLAG_ENCRYPTED;
            }
            return this;
        }

        /**
         * Enables or disables Reed-Solomon error correction for this entry.
         *
         * <p>When ECC (Error Correction Code) is enabled (set to {@code true}),
         * the corresponding flag bit ({@link FormatConstants#ENTRY_FLAG_HAS_ECC})
         * is set in the entry's flags field. This indicates that additional
         * parity data has been computed and stored with each chunk to allow
         * recovery from bit errors during reading.</p>
         *
         * <p>Reed-Solomon error correction is particularly useful for archival
         * storage on potentially unreliable media (optical discs, magnetic tape,
         * network storage) where bit rot or transmission errors may occur over
         * time. The trade-off is increased storage size due to the parity data.</p>
         *
         * <p>Note that setting this flag does not actually compute the ECC data;
         * it only marks the entry as having ECC in the header. The actual ECC
         * computation is performed by the archive writer.</p>
         *
         * @param hasEcc {@code true} to mark this entry as having error correction
         *               data and set the ECC flag bit; {@code false} to clear the
         *               ECC flag and indicate no error correction is present
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see FormatConstants#ENTRY_FLAG_HAS_ECC
         * @see EntryHeader#hasEcc()
         */
        public @NotNull Builder hasEcc(final boolean hasEcc) {
            if (hasEcc) {
                this.flags |= FormatConstants.ENTRY_FLAG_HAS_ECC;
            } else {
                this.flags &= ~FormatConstants.ENTRY_FLAG_HAS_ECC;
            }
            return this;
        }

        /**
         * Sets the unique entry identifier within the archive.
         *
         * <p>The entry ID uniquely identifies this entry within the archive and
         * is used by the Table of Contents ({@link TocEntry}) to enable random
         * access to entries. Entry IDs are typically assigned sequentially
         * starting from 0 as entries are added to the archive.</p>
         *
         * <p>The entry ID must be unique within the archive. When reading an
         * archive, entries can be looked up by their ID through the TOC without
         * scanning through all entry headers sequentially.</p>
         *
         * @param entryId the unique entry identifier to assign to this entry;
         *                must be a non-negative value that is unique within
         *                the archive; typically assigned sequentially starting
         *                from 0
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see TocEntry#entryId()
         */
        public @NotNull Builder entryId(final long entryId) {
            this.entryId = entryId;
            return this;
        }

        /**
         * Sets the original (uncompressed) size of the entry data in bytes.
         *
         * <p>The original size represents the actual size of the entry's content
         * before any compression is applied. This value is essential for:</p>
         * <ul>
         *   <li>Allocating the correct buffer size when decompressing</li>
         *   <li>Calculating compression ratios</li>
         *   <li>Progress reporting during extraction</li>
         *   <li>Verifying that decompression produced the expected amount of data</li>
         * </ul>
         *
         * <p>For uncompressed entries, this value will equal {@link #storedSize(long)}.
         * For compressed entries, this value will typically be larger than the
         * stored size (unless compression is ineffective for the data).</p>
         *
         * @param originalSize the original uncompressed size of the entry data
         *                     in bytes; must be a non-negative value representing
         *                     the total number of bytes in the uncompressed content
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see #storedSize(long)
         * @see EntryHeader#originalSize()
         */
        public @NotNull Builder originalSize(final long originalSize) {
            this.originalSize = originalSize;
            return this;
        }

        /**
         * Sets the stored (compressed and/or encrypted) size of the entry data in bytes.
         *
         * <p>The stored size represents the actual number of bytes written to the
         * archive for this entry's data, after compression and/or encryption have
         * been applied. This value is used to:</p>
         * <ul>
         *   <li>Determine how many bytes to read from the archive</li>
         *   <li>Calculate the file offset of subsequent entries</li>
         *   <li>Compute compression ratios when compared to original size</li>
         *   <li>Verify archive integrity</li>
         * </ul>
         *
         * <p>For uncompressed, unencrypted entries, this value equals
         * {@link #originalSize(long)}. For compressed entries, this value is
         * typically smaller. For encrypted entries, this value may be slightly
         * larger due to encryption overhead (IV, authentication tag, padding).</p>
         *
         * @param storedSize the stored size of the entry data in bytes after
         *                   compression and/or encryption; must be a non-negative
         *                   value representing the actual bytes stored in the archive
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see #originalSize(long)
         * @see EntryHeader#storedSize()
         */
        public @NotNull Builder storedSize(final long storedSize) {
            this.storedSize = storedSize;
            return this;
        }

        /**
         * Sets the number of data chunks that make up this entry.
         *
         * <p>Entry data is split into fixed-size chunks (see
         * {@link FormatConstants#DEFAULT_CHUNK_SIZE}) for efficient processing.
         * Each chunk can be compressed and encrypted independently, enabling
         * random access within large files. The chunk count indicates how many
         * {@link ChunkHeader} structures follow the entry header.</p>
         *
         * <p>The chunk count is calculated as:</p>
         * <pre>
         * chunkCount = ceil(originalSize / chunkSize)
         * </pre>
         *
         * <p>For small files that fit within a single chunk, the count will be 1.
         * For empty files, the count may be 0 or 1 depending on implementation.</p>
         *
         * @param chunkCount the total number of data chunks for this entry;
         *                   must be a non-negative integer representing how
         *                   many chunk headers and data blocks follow
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see ChunkHeader
         * @see FormatConstants#DEFAULT_CHUNK_SIZE
         * @see EntryHeader#chunkCount()
         */
        public @NotNull Builder chunkCount(final int chunkCount) {
            this.chunkCount = chunkCount;
            return this;
        }

        /**
         * Sets the compression algorithm identifier for this entry.
         *
         * <p>The compression ID indicates which compression algorithm was used
         * to compress this entry's data chunks. This value is used during reading
         * to select the appropriate decompression implementation. The following
         * compression algorithms are supported:</p>
         * <ul>
         *   <li>{@link FormatConstants#COMPRESSION_NONE} (0): No compression</li>
         *   <li>{@link FormatConstants#COMPRESSION_ZSTD} (1): Zstandard compression</li>
         *   <li>{@link FormatConstants#COMPRESSION_LZ4} (2): LZ4 compression</li>
         * </ul>
         *
         * <p>If this value is set to anything other than {@code COMPRESSION_NONE},
         * the {@link #compressed(boolean)} flag should also be set to {@code true}
         * to properly indicate that the entry requires decompression.</p>
         *
         * @param compressionId the compression algorithm identifier; must be one
         *                      of the {@code COMPRESSION_*} constants defined in
         *                      {@link FormatConstants}
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see #compressed(boolean)
         * @see FormatConstants#COMPRESSION_NONE
         * @see FormatConstants#COMPRESSION_ZSTD
         * @see FormatConstants#COMPRESSION_LZ4
         * @see EntryHeader#compressionId()
         */
        public @NotNull Builder compressionId(final int compressionId) {
            this.compressionId = compressionId;
            return this;
        }

        /**
         * Sets the encryption algorithm identifier for this entry.
         *
         * <p>The encryption ID indicates which encryption algorithm was used
         * to encrypt this entry's data chunks. This value is used during reading
         * to select the appropriate decryption implementation. The following
         * encryption algorithms are supported:</p>
         * <ul>
         *   <li>{@link FormatConstants#ENCRYPTION_NONE} (0): No encryption</li>
         *   <li>{@link FormatConstants#ENCRYPTION_AES_256_GCM} (1): AES-256 in GCM mode</li>
         *   <li>{@link FormatConstants#ENCRYPTION_CHACHA20_POLY1305} (2): ChaCha20-Poly1305</li>
         * </ul>
         *
         * <p>If this value is set to anything other than {@code ENCRYPTION_NONE},
         * the {@link #encrypted(boolean)} flag should also be set to {@code true}
         * to properly indicate that the entry requires decryption.</p>
         *
         * @param encryptionId the encryption algorithm identifier; must be one
         *                     of the {@code ENCRYPTION_*} constants defined in
         *                     {@link FormatConstants}
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see #encrypted(boolean)
         * @see FormatConstants#ENCRYPTION_NONE
         * @see FormatConstants#ENCRYPTION_AES_256_GCM
         * @see FormatConstants#ENCRYPTION_CHACHA20_POLY1305
         * @see EntryHeader#encryptionId()
         * @see EncryptionBlock
         */
        public @NotNull Builder encryptionId(final int encryptionId) {
            this.encryptionId = encryptionId;
            return this;
        }

        /**
         * Sets the CRC32 checksum of the entry header for integrity verification.
         *
         * <p>The header checksum is a CRC32 value computed over the entry header
         * bytes (excluding the checksum field itself). This checksum is used to
         * verify the integrity of the entry header during reading. If the checksum
         * does not match, the entry header may be corrupted.</p>
         *
         * <p>The checksum is typically computed by the archive writer after all
         * other header fields have been set. When reading, the checksum is
         * recomputed and compared against this stored value to detect corruption.</p>
         *
         * @param headerChecksum the CRC32 checksum value computed over the entry
         *                       header bytes; this is a 32-bit unsigned value
         *                       stored as a signed int
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see EntryHeader#headerChecksum()
         */
        public @NotNull Builder headerChecksum(final int headerChecksum) {
            this.headerChecksum = headerChecksum;
            return this;
        }

        /**
         * Sets the entry name, which is typically a relative file path.
         *
         * <p>The entry name uniquely identifies the content within the archive
         * and is typically structured as a relative path using forward slashes
         * ({@code /}) as directory separators, regardless of the operating system.
         * Examples:</p>
         * <ul>
         *   <li>{@code "data/config.json"}</li>
         *   <li>{@code "images/logo.png"}</li>
         *   <li>{@code "readme.txt"}</li>
         * </ul>
         *
         * <p>The name is encoded in UTF-8 and stored in the entry header. The
         * maximum name length is {@link FormatConstants#MAX_ENTRY_NAME_LENGTH}
         * bytes (not characters). Names should not start with a slash or contain
         * backslashes for cross-platform compatibility.</p>
         *
         * @param name the entry name to set; must not be {@code null}; should
         *             be a valid UTF-8 string representing a relative path;
         *             must not exceed {@link FormatConstants#MAX_ENTRY_NAME_LENGTH}
         *             bytes when encoded
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see FormatConstants#MAX_ENTRY_NAME_LENGTH
         * @see EntryHeader#name()
         */
        public @NotNull Builder name(final @NotNull String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the MIME type of the entry content.
         *
         * <p>The MIME type provides a hint about the content type of the entry,
         * which can be used by applications to determine how to handle or display
         * the content. Common examples include:</p>
         * <ul>
         *   <li>{@code "application/json"} for JSON files</li>
         *   <li>{@code "image/png"} for PNG images</li>
         *   <li>{@code "text/plain"} for plain text files</li>
         *   <li>{@code "application/octet-stream"} for binary data</li>
         * </ul>
         *
         * <p>The MIME type is optional; an empty string indicates that no MIME
         * type was specified. The MIME type is encoded in UTF-8 and stored in
         * the entry header following the entry name.</p>
         *
         * @param mimeType the MIME type string to set; must not be {@code null};
         *                 may be an empty string to indicate no MIME type;
         *                 should follow the standard MIME type format
         *                 (type/subtype)
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see EntryHeader#mimeType()
         */
        public @NotNull Builder mimeType(final @NotNull String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        /**
         * Sets the list of custom attributes attached to this entry.
         *
         * <p>Custom attributes provide a flexible mechanism for storing arbitrary
         * key-value metadata with entries. Each attribute consists of a key string
         * and a typed value (string, integer, double, boolean, or raw bytes).
         * Common use cases include:</p>
         * <ul>
         *   <li>Author information: {@code Attribute.ofString("author", "John Doe")}</li>
         *   <li>Timestamps: {@code Attribute.ofLong("created", System.currentTimeMillis())}</li>
         *   <li>File permissions: {@code Attribute.ofLong("mode", 0644)}</li>
         *   <li>Checksums: {@code Attribute.ofBytes("sha256", hashBytes)}</li>
         * </ul>
         *
         * <p>When a non-empty list is provided, this method automatically sets
         * the {@link FormatConstants#ENTRY_FLAG_HAS_ATTRIBUTES} flag bit to
         * indicate that attributes are present in the entry header.</p>
         *
         * <p>The provided list is defensively copied during the build process
         * to ensure immutability of the resulting entry header.</p>
         *
         * @param attributes the list of custom attributes to attach to this entry;
         *                   must not be {@code null}; may be an empty list to
         *                   indicate no attributes; the list is copied during build
         * @return this builder instance to allow fluent method chaining for setting
         *         additional entry header properties
         *
         * @see Attribute
         * @see FormatConstants#ENTRY_FLAG_HAS_ATTRIBUTES
         * @see EntryHeader#attributes()
         * @see EntryHeader#hasAttributes()
         */
        public @NotNull Builder attributes(final @NotNull List<Attribute> attributes) {
            this.attributes = attributes;
            if (!attributes.isEmpty()) {
                this.flags |= FormatConstants.ENTRY_FLAG_HAS_ATTRIBUTES;
            }
            return this;
        }

        /**
         * Builds and returns a new immutable {@link EntryHeader} instance.
         *
         * <p>This method constructs a new entry header using all the values
         * that have been set on this builder. Any values not explicitly set
         * will use their default values as established when the builder was
         * created.</p>
         *
         * <p>The resulting entry header is immutable and thread-safe. The
         * attributes list is defensively copied to ensure that modifications
         * to the original list do not affect the created header.</p>
         *
         * <p>The builder can be reused after calling this method to create
         * additional entry headers with different or modified values.</p>
         *
         * @return a new immutable {@link EntryHeader} instance containing all
         *         the configured values; never {@code null}
         *
         * @see EntryHeader
         */
        public @NotNull EntryHeader build() {
            return new EntryHeader(
                    this.headerVersion,
                    this.flags,
                    this.entryId,
                    this.originalSize,
                    this.storedSize,
                    this.chunkCount,
                    this.compressionId,
                    this.encryptionId,
                    this.headerChecksum,
                    this.name,
                    this.mimeType,
                    this.attributes
            );
        }

    }

}
