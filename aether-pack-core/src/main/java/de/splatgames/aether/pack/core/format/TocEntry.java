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
 * Represents a fixed-size entry in the Table of Contents (TOC) for random access.
 *
 * <p>TOC entries are stored in the archive {@link Trailer} and enable efficient
 * random access to entries without sequential scanning. Each TOC entry is exactly
 * {@link FormatConstants#TOC_ENTRY_SIZE} (40) bytes.</p>
 *
 * <h2>Binary Layout</h2>
 * <p>The TOC entry has the following structure (Little-Endian byte order):</p>
 * <pre>
 * Offset  Size  Field          Description
 * ──────────────────────────────────────────────────────────────────
 * 0x00    8     entryId        Unique entry ID within archive
 * 0x08    8     entryOffset    Absolute file offset to entry header
 * 0x10    8     originalSize   Original (uncompressed) size in bytes
 * 0x18    8     storedSize     Stored (compressed) size in bytes
 * 0x20    4     nameHash       XXH3-32 hash of entry name
 * 0x24    4     entryChecksum  CRC32 of entry header
 * ──────────────────────────────────────────────────────────────────
 * Total: 40 bytes (0x28)
 * </pre>
 *
 * <h2>Random Access Lookup</h2>
 * <p>The {@code nameHash} field enables fast name-based lookups without reading
 * each entry header. The lookup process is:</p>
 * <ol>
 *   <li>Compute XXH3-32 hash of the desired entry name</li>
 *   <li>Search TOC entries for matching hash values</li>
 *   <li>For hash matches, seek to {@code entryOffset} and read entry name</li>
 *   <li>Compare actual name to confirm match (handles hash collisions)</li>
 * </ol>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create a TOC entry for an archived file
 * TocEntry entry = TocEntry.builder()
 *     .entryId(1)
 *     .entryOffset(64)  // After file header
 *     .originalSize(102400)
 *     .storedSize(45000)
 *     .nameHash(computeXxh3Hash("data/config.json"))
 *     .entryChecksum(headerCrc32)
 *     .build();
 *
 * // Use TOC entry to seek to entry data
 * channel.position(entry.entryOffset());
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This is an immutable record. Instances are thread-safe and can be
 * freely shared between threads.</p>
 *
 * @param entryId       the unique identifier for this entry within the archive;
 *                      entry IDs are typically assigned sequentially starting from 0
 * @param entryOffset   the absolute file offset (in bytes) to the start of this
 *                      entry's {@link EntryHeader}
 * @param originalSize  the original (uncompressed) size of the entry data in bytes
 * @param storedSize    the stored (compressed/encrypted) size of the entry data
 *                      in bytes; equals originalSize if no compression applied
 * @param nameHash      XXH3-32 hash of the entry name for fast lookup; used to
 *                      quickly filter candidates before reading full entry headers
 * @param entryChecksum CRC32 checksum of the entry header for integrity verification
 *
 * @see Trailer
 * @see EntryHeader
 * @see FormatConstants#TOC_ENTRY_SIZE
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record TocEntry(
        long entryId,
        long entryOffset,
        long originalSize,
        long storedSize,
        int nameHash,
        int entryChecksum
) {

    /**
     * Creates a new TOC entry builder initialized with default values.
     *
     * <p>The builder is pre-configured with all values set to zero.</p>
     *
     * @return a new builder instance; never {@code null}
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * A fluent builder for creating {@link TocEntry} instances.
     *
     * <p>This builder provides a convenient way to construct TOC entries
     * with customized settings. All setter methods return the builder
     * instance for method chaining.</p>
     *
     * <h2>Usage Example</h2>
     * <pre>{@code
     * TocEntry entry = TocEntry.builder()
     *     .entryId(0)
     *     .entryOffset(64)
     *     .originalSize(1048576)
     *     .storedSize(524288)
     *     .nameHash(0x12345678)
     *     .entryChecksum(0xABCDEF01)
     *     .build();
     * }</pre>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public static final class Builder {

        private long entryId = 0;
        private long entryOffset = 0;
        private long originalSize = 0;
        private long storedSize = 0;
        private int nameHash = 0;
        private int entryChecksum = 0;

        private Builder() {
        }

        /**
         * Sets the unique entry identifier within the archive.
         *
         * <p>The entry ID uniquely identifies this entry within the archive and
         * corresponds to the {@link EntryHeader#entryId()} of the actual entry.
         * Entry IDs are typically assigned sequentially starting from 0 as
         * entries are added to the archive.</p>
         *
         * <p>This ID is used for:</p>
         * <ul>
         *   <li>Direct entry lookup by ID through the TOC</li>
         *   <li>Correlating TOC entries with their corresponding entry headers</li>
         *   <li>Verifying archive consistency during reading</li>
         * </ul>
         *
         * @param entryId the unique entry identifier within the archive; must be
         *                a non-negative value that uniquely identifies this entry;
         *                should match the corresponding {@link EntryHeader#entryId()}
         * @return this builder instance to allow fluent method chaining for setting
         *         additional TOC entry properties
         *
         * @see EntryHeader#entryId()
         * @see TocEntry#entryId()
         */
        public @NotNull Builder entryId(final long entryId) {
            this.entryId = entryId;
            return this;
        }

        /**
         * Sets the absolute file offset to the entry header.
         *
         * <p>The entry offset specifies the exact byte position in the archive
         * file where this entry's {@link EntryHeader} begins. This offset is
         * measured from the start of the file (byte 0) and enables direct
         * seeking to an entry without sequential scanning.</p>
         *
         * <p>When performing random access to read an entry:</p>
         * <ol>
         *   <li>Look up the entry in the TOC by name hash or ID</li>
         *   <li>Seek to the {@code entryOffset} position</li>
         *   <li>Read the entry header and chunk data from that position</li>
         * </ol>
         *
         * @param entryOffset the absolute byte offset from the start of the file
         *                    to the beginning of this entry's header; must be a
         *                    positive value (entries cannot start at offset 0
         *                    since the file header occupies that space)
         * @return this builder instance to allow fluent method chaining for setting
         *         additional TOC entry properties
         *
         * @see TocEntry#entryOffset()
         */
        public @NotNull Builder entryOffset(final long entryOffset) {
            this.entryOffset = entryOffset;
            return this;
        }

        /**
         * Sets the original (uncompressed) size of the entry data in bytes.
         *
         * <p>The original size is stored in the TOC to enable quick access to
         * entry metadata without reading the full entry header. This value
         * should match the corresponding {@link EntryHeader#originalSize()}.</p>
         *
         * <p>This size information in the TOC is useful for:</p>
         * <ul>
         *   <li>Displaying entry sizes in directory listings</li>
         *   <li>Calculating compression ratios without reading entry headers</li>
         *   <li>Estimating extraction space requirements</li>
         * </ul>
         *
         * @param originalSize the original uncompressed size of the entry data
         *                     in bytes; must be a non-negative value matching
         *                     the corresponding entry header's original size
         * @return this builder instance to allow fluent method chaining for setting
         *         additional TOC entry properties
         *
         * @see EntryHeader#originalSize()
         * @see TocEntry#originalSize()
         */
        public @NotNull Builder originalSize(final long originalSize) {
            this.originalSize = originalSize;
            return this;
        }

        /**
         * Sets the stored (compressed/encrypted) size of the entry data in bytes.
         *
         * <p>The stored size is included in the TOC for quick access to entry
         * metadata without reading the full entry header. This value should
         * match the corresponding {@link EntryHeader#storedSize()}.</p>
         *
         * <p>This size information enables:</p>
         * <ul>
         *   <li>Quick calculation of compression ratios</li>
         *   <li>Determining the byte range occupied by an entry in the file</li>
         *   <li>Verifying that all entry data fits within the file</li>
         * </ul>
         *
         * @param storedSize the stored size of the entry data in bytes after
         *                   compression and/or encryption; must be a non-negative
         *                   value matching the corresponding entry header's stored size
         * @return this builder instance to allow fluent method chaining for setting
         *         additional TOC entry properties
         *
         * @see EntryHeader#storedSize()
         * @see TocEntry#storedSize()
         */
        public @NotNull Builder storedSize(final long storedSize) {
            this.storedSize = storedSize;
            return this;
        }

        /**
         * Sets the XXH3-32 hash of the entry name for fast lookup.
         *
         * <p>The name hash enables efficient name-based lookup in the TOC without
         * reading full entry headers. When looking up an entry by name:</p>
         * <ol>
         *   <li>Compute the XXH3-32 hash of the desired name</li>
         *   <li>Scan TOC entries for matching hash values</li>
         *   <li>For hash matches, read the actual entry name to confirm (handles collisions)</li>
         * </ol>
         *
         * <p>Using a hash reduces the need to read and compare full entry names
         * during lookup, especially for archives with many entries. Hash collisions
         * are handled by comparing actual names for hash matches.</p>
         *
         * @param nameHash the XXH3-32 hash value computed over the UTF-8 encoded
         *                 entry name bytes; this is a 32-bit value stored as a
         *                 signed int
         * @return this builder instance to allow fluent method chaining for setting
         *         additional TOC entry properties
         *
         * @see TocEntry#nameHash()
         */
        public @NotNull Builder nameHash(final int nameHash) {
            this.nameHash = nameHash;
            return this;
        }

        /**
         * Sets the CRC32 checksum of the entry header for integrity verification.
         *
         * <p>The entry checksum is stored in the TOC to enable verification of
         * entry header integrity without first reading the full header. This
         * value should match the corresponding {@link EntryHeader#headerChecksum()}.</p>
         *
         * <p>When reading an entry:</p>
         * <ol>
         *   <li>Look up the entry in the TOC</li>
         *   <li>Seek to the entry offset and read the header</li>
         *   <li>Verify the header checksum matches this stored value</li>
         * </ol>
         *
         * @param entryChecksum the CRC32 checksum value of the entry header;
         *                      must match the corresponding entry header's
         *                      checksum field
         * @return this builder instance to allow fluent method chaining for setting
         *         additional TOC entry properties
         *
         * @see EntryHeader#headerChecksum()
         * @see TocEntry#entryChecksum()
         */
        public @NotNull Builder entryChecksum(final int entryChecksum) {
            this.entryChecksum = entryChecksum;
            return this;
        }

        /**
         * Builds and returns a new immutable {@link TocEntry} instance.
         *
         * <p>This method constructs a new TOC entry using all the values that
         * have been set on this builder. Any values not explicitly set will
         * use their default values (typically 0).</p>
         *
         * <p>The resulting TOC entry is immutable and thread-safe. The builder
         * can be reused after calling this method to create additional TOC
         * entries with different or modified values.</p>
         *
         * @return a new immutable {@link TocEntry} instance containing all the
         *         configured values; never {@code null}
         *
         * @see TocEntry
         */
        public @NotNull TocEntry build() {
            return new TocEntry(
                    this.entryId,
                    this.entryOffset,
                    this.originalSize,
                    this.storedSize,
                    this.nameHash,
                    this.entryChecksum
            );
        }

    }

}
