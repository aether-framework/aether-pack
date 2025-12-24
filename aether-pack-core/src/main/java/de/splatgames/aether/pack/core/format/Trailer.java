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

import java.util.Collections;
import java.util.List;

/**
 * Represents the file trailer of an APACK container-mode archive.
 *
 * <p>The trailer is located at the end of an APACK archive (container mode)
 * and contains the Table of Contents (TOC) for random access to entries.
 * The file header's {@link FileHeader#trailerOffset()} field points to the
 * start of the trailer.</p>
 *
 * <h2>Binary Layout</h2>
 * <p>The trailer has the following structure (Little-Endian byte order):</p>
 * <pre>
 * Offset  Size    Field              Description
 * ──────────────────────────────────────────────────────────────────
 * 0x00    4       magic              "ATRL" (ASCII)
 * 0x04    4       trailerVersion     Trailer format version
 * 0x08    8       tocOffset          Offset to TOC (relative to trailer start)
 * 0x10    8       tocSize            Size of TOC in bytes
 * 0x18    8       entryCount         Number of entries in TOC
 * 0x20    8       totalOriginalSize  Sum of all original entry sizes
 * 0x28    8       totalStoredSize    Sum of all stored entry sizes
 * 0x30    4       tocChecksum        CRC32 of entire TOC
 * 0x34    4       trailerChecksum    CRC32 of trailer (excluding this)
 * 0x38    8       fileSize           Total file size for verification
 * 0x40    N*40    tocEntries         TOC entries (40 bytes each)
 * ──────────────────────────────────────────────────────────────────
 * Header size: 64 bytes (0x40), followed by N * 40 bytes of TOC entries
 * </pre>
 *
 * <h2>Table of Contents (TOC)</h2>
 * <p>The TOC enables random access to entries by storing the file offset
 * and size information for each entry. Each TOC entry is a fixed 40 bytes
 * (see {@link TocEntry} and {@link FormatConstants#TOC_ENTRY_SIZE}).</p>
 *
 * <h2>Compression Statistics</h2>
 * <p>The {@code totalOriginalSize} and {@code totalStoredSize} fields
 * provide aggregate statistics that can be used to calculate the overall
 * compression ratio of the archive:</p>
 * <pre>{@code
 * double ratio = (double) trailer.totalStoredSize() / trailer.totalOriginalSize() * 100;
 * System.out.printf("Overall compression: %.1f%%\n", ratio);
 * }</pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create a trailer with TOC entries
 * List<TocEntry> entries = List.of(
 *     TocEntry.builder().entryId(1).entryOffset(64).originalSize(1024).build(),
 *     TocEntry.builder().entryId(2).entryOffset(1200).originalSize(2048).build()
 * );
 *
 * Trailer trailer = Trailer.builder()
 *     .tocEntries(entries)
 *     .totalOriginalSize(3072)
 *     .totalStoredSize(1500)
 *     .fileSize(2048)
 *     .build();
 *
 * // Iterate over entries
 * for (TocEntry entry : trailer.tocEntries()) {
 *     System.out.println("Entry at offset: " + entry.entryOffset());
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This is an immutable record. The TOC entries list is defensively copied
 * during construction. Instances are thread-safe and can be freely shared
 * between threads.</p>
 *
 * @param trailerVersion    the trailer format version; current version is
 *                          {@link #CURRENT_VERSION}
 * @param tocOffset         the offset to the TOC start relative to the trailer
 *                          start; typically immediately after the trailer header
 * @param tocSize           the total size of the TOC in bytes
 * @param entryCount        the number of entries in the TOC; must match
 *                          the size of {@code tocEntries}
 * @param totalOriginalSize the sum of all entry original (uncompressed) sizes
 *                          in bytes
 * @param totalStoredSize   the sum of all entry stored (compressed) sizes
 *                          in bytes
 * @param tocChecksum       CRC32 checksum of the entire TOC for integrity
 *                          verification
 * @param trailerChecksum   CRC32 checksum of the trailer header (excluding
 *                          this field) for integrity verification
 * @param fileSize          the total file size in bytes; used for verification
 *                          that the file is complete and not truncated
 * @param tocEntries        an immutable list of TOC entries for random access;
 *                          never {@code null}, may be empty
 *
 * @see FileHeader#trailerOffset()
 * @see TocEntry
 * @see StreamTrailer
 * @see FormatConstants#TRAILER_MAGIC
 * @see FormatConstants#TRAILER_HEADER_SIZE
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record Trailer(
        int trailerVersion,
        long tocOffset,
        long tocSize,
        long entryCount,
        long totalOriginalSize,
        long totalStoredSize,
        int tocChecksum,
        int trailerChecksum,
        long fileSize,
        @NotNull List<TocEntry> tocEntries
) {

    /**
     * The current trailer format version.
     *
     * <p>This version number is incremented when the trailer format changes
     * in a way that requires reader updates. Readers should check this version
     * and handle older formats appropriately.</p>
     */
    public static final int CURRENT_VERSION = 1;

    /**
     * Compact constructor that creates a defensive copy of the TOC entries list.
     *
     * <p>This ensures that the trailer is truly immutable and that external
     * modifications to the original list do not affect this record.</p>
     */
    public Trailer {
        tocEntries = List.copyOf(tocEntries);
    }

    /**
     * Creates a new trailer builder initialized with default values.
     *
     * <p>The builder is pre-configured with:</p>
     * <ul>
     *   <li>Current trailer version ({@link #CURRENT_VERSION})</li>
     *   <li>All numeric fields set to 0</li>
     *   <li>Empty TOC entries list</li>
     * </ul>
     *
     * @return a new builder instance; never {@code null}
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * A fluent builder for creating {@link Trailer} instances.
     *
     * <p>This builder provides a convenient way to construct trailers with
     * customized settings. All setter methods return the builder instance
     * for method chaining.</p>
     *
     * <h2>Automatic Entry Count</h2>
     * <p>When {@link #tocEntries(List)} is called, the entry count is
     * automatically set to match the list size.</p>
     *
     * <h2>Usage Example</h2>
     * <pre>{@code
     * Trailer trailer = Trailer.builder()
     *     .tocEntries(tocEntryList)
     *     .totalOriginalSize(sumOriginal)
     *     .totalStoredSize(sumStored)
     *     .fileSize(channel.size())
     *     .tocChecksum(computedTocCrc)
     *     .build();
     * }</pre>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public static final class Builder {

        private int trailerVersion = CURRENT_VERSION;
        private long tocOffset = 0;
        private long tocSize = 0;
        private long entryCount = 0;
        private long totalOriginalSize = 0;
        private long totalStoredSize = 0;
        private int tocChecksum = 0;
        private int trailerChecksum = 0;
        private long fileSize = 0;
        private @NotNull List<TocEntry> tocEntries = Collections.emptyList();

        private Builder() {
        }

        /**
         * Sets the trailer format version number.
         *
         * <p>The trailer version indicates the format of the trailer structure
         * and determines how readers should interpret the trailer fields. This
         * allows the trailer format to evolve while maintaining backward
         * compatibility with older archives.</p>
         *
         * <p>The current trailer version is {@link Trailer#CURRENT_VERSION}.
         * Readers should check this version and handle older formats appropriately,
         * or reject archives with unsupported newer versions.</p>
         *
         * @param trailerVersion the trailer format version number to set; should
         *                       typically be {@link Trailer#CURRENT_VERSION} for
         *                       new archives; must be a non-negative integer
         * @return this builder instance to allow fluent method chaining for setting
         *         additional trailer properties
         *
         * @see Trailer#CURRENT_VERSION
         */
        public @NotNull Builder trailerVersion(final int trailerVersion) {
            this.trailerVersion = trailerVersion;
            return this;
        }

        /**
         * Sets the offset to the Table of Contents (TOC) relative to the trailer start.
         *
         * <p>The TOC offset indicates where the TOC entries begin within the trailer
         * structure. This value is typically equal to the trailer header size
         * ({@link FormatConstants#TRAILER_HEADER_SIZE}), as the TOC immediately
         * follows the trailer header.</p>
         *
         * <p>This offset enables readers to quickly locate the TOC for random
         * access operations without parsing the entire trailer header.</p>
         *
         * @param tocOffset the byte offset from the start of the trailer to the
         *                  beginning of the TOC entries; must be a non-negative
         *                  value; typically equals the trailer header size
         * @return this builder instance to allow fluent method chaining for setting
         *         additional trailer properties
         *
         * @see FormatConstants#TRAILER_HEADER_SIZE
         */
        public @NotNull Builder tocOffset(final long tocOffset) {
            this.tocOffset = tocOffset;
            return this;
        }

        /**
         * Sets the total size of the Table of Contents (TOC) in bytes.
         *
         * <p>The TOC size represents the total number of bytes occupied by all
         * TOC entries. This value is calculated as:</p>
         * <pre>
         * tocSize = entryCount * {@link FormatConstants#TOC_ENTRY_SIZE}
         * </pre>
         *
         * <p>This value is used for:</p>
         * <ul>
         *   <li>Validating that the complete TOC has been read</li>
         *   <li>Allocating appropriately sized buffers for TOC reading</li>
         *   <li>Computing the checksum over the entire TOC</li>
         * </ul>
         *
         * @param tocSize the total size of the TOC in bytes; must be a non-negative
         *                value consistent with the entry count and TOC entry size
         * @return this builder instance to allow fluent method chaining for setting
         *         additional trailer properties
         *
         * @see FormatConstants#TOC_ENTRY_SIZE
         */
        public @NotNull Builder tocSize(final long tocSize) {
            this.tocSize = tocSize;
            return this;
        }

        /**
         * Sets the number of entries in the Table of Contents (TOC).
         *
         * <p>The entry count indicates how many {@link TocEntry} records are
         * stored in the TOC. This value should match the number of entries
         * in the archive and the size of the list passed to
         * {@link #tocEntries(List)}.</p>
         *
         * <p><strong>Note:</strong> This value is automatically updated when
         * {@link #tocEntries(List)} is called, so you typically don't need
         * to set it manually unless building a trailer for reading purposes.</p>
         *
         * @param entryCount the number of entries in the TOC; must be a
         *                   non-negative value matching the actual number
         *                   of TOC entries in the archive
         * @return this builder instance to allow fluent method chaining for setting
         *         additional trailer properties
         *
         * @see #tocEntries(List)
         */
        public @NotNull Builder entryCount(final long entryCount) {
            this.entryCount = entryCount;
            return this;
        }

        /**
         * Sets the sum of all entry original (uncompressed) sizes in bytes.
         *
         * <p>The total original size represents the aggregate uncompressed size
         * of all entries in the archive. This value is useful for:</p>
         * <ul>
         *   <li>Calculating the overall compression ratio of the archive</li>
         *   <li>Displaying progress during extraction operations</li>
         *   <li>Estimating disk space requirements for full extraction</li>
         * </ul>
         *
         * <p>The compression ratio can be calculated as:</p>
         * <pre>
         * ratio = (double) totalStoredSize / totalOriginalSize * 100;
         * </pre>
         *
         * @param totalOriginalSize the sum of all entry original sizes in bytes;
         *                          must be a non-negative value representing the
         *                          total uncompressed data size
         * @return this builder instance to allow fluent method chaining for setting
         *         additional trailer properties
         *
         * @see #totalStoredSize(long)
         * @see Trailer#totalOriginalSize()
         */
        public @NotNull Builder totalOriginalSize(final long totalOriginalSize) {
            this.totalOriginalSize = totalOriginalSize;
            return this;
        }

        /**
         * Sets the sum of all entry stored (compressed) sizes in bytes.
         *
         * <p>The total stored size represents the aggregate compressed/encrypted
         * size of all entries in the archive. This value reflects the actual
         * storage space used by entry data (not including headers and trailer).</p>
         *
         * <p>Comparing this to {@link #totalOriginalSize(long)} gives the
         * overall compression effectiveness. When no compression is used,
         * these values will be equal.</p>
         *
         * @param totalStoredSize the sum of all entry stored sizes in bytes;
         *                        must be a non-negative value representing the
         *                        total compressed data size in the archive
         * @return this builder instance to allow fluent method chaining for setting
         *         additional trailer properties
         *
         * @see #totalOriginalSize(long)
         * @see Trailer#totalStoredSize()
         */
        public @NotNull Builder totalStoredSize(final long totalStoredSize) {
            this.totalStoredSize = totalStoredSize;
            return this;
        }

        /**
         * Sets the CRC32 checksum of the entire Table of Contents (TOC).
         *
         * <p>The TOC checksum is computed over all TOC entry bytes and is used
         * to verify the integrity of the TOC during reading. If the checksum
         * does not match after reading the TOC, it indicates corruption of
         * the TOC data, which would prevent reliable random access to entries.</p>
         *
         * <p>The checksum is typically computed by the archive writer after
         * all TOC entries have been serialized. During reading, the checksum
         * is recomputed and compared against this stored value.</p>
         *
         * @param tocChecksum the CRC32 checksum value computed over all TOC
         *                    entry bytes; this is a 32-bit unsigned value
         *                    stored as a signed int
         * @return this builder instance to allow fluent method chaining for setting
         *         additional trailer properties
         *
         * @see Trailer#tocChecksum()
         */
        public @NotNull Builder tocChecksum(final int tocChecksum) {
            this.tocChecksum = tocChecksum;
            return this;
        }

        /**
         * Sets the CRC32 checksum of the trailer header for integrity verification.
         *
         * <p>The trailer checksum is computed over the trailer header bytes
         * (excluding the checksum field itself) and is used to verify the
         * integrity of the trailer metadata. This checksum protects fields
         * like version, TOC offset, entry count, and size statistics.</p>
         *
         * <p>The checksum is typically computed by the archive writer after
         * all trailer header fields have been set. During reading, the
         * checksum is recomputed and compared against this stored value.</p>
         *
         * @param trailerChecksum the CRC32 checksum value computed over the
         *                        trailer header bytes; this is a 32-bit unsigned
         *                        value stored as a signed int
         * @return this builder instance to allow fluent method chaining for setting
         *         additional trailer properties
         *
         * @see Trailer#trailerChecksum()
         */
        public @NotNull Builder trailerChecksum(final int trailerChecksum) {
            this.trailerChecksum = trailerChecksum;
            return this;
        }

        /**
         * Sets the total file size for verification that the archive is complete.
         *
         * <p>The file size stored in the trailer should match the actual file
         * size when reading. This provides an additional integrity check to
         * detect truncated or corrupted archives. If the actual file size
         * doesn't match this value, the archive may be incomplete.</p>
         *
         * <p>This value is typically set by the archive writer after all data
         * has been written, just before finalizing the trailer.</p>
         *
         * @param fileSize the total archive file size in bytes; must be a
         *                 positive value representing the complete file size
         *                 including header, all entries, and trailer
         * @return this builder instance to allow fluent method chaining for setting
         *         additional trailer properties
         *
         * @see Trailer#fileSize()
         */
        public @NotNull Builder fileSize(final long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        /**
         * Sets the Table of Contents (TOC) entries and automatically updates the entry count.
         *
         * <p>The TOC entries provide the index for random access to archive entries.
         * Each {@link TocEntry} contains the entry ID, file offset, sizes, and
         * a name hash for quick lookup. The list is stored in the trailer and
         * enables efficient entry lookup without sequential scanning.</p>
         *
         * <p>When this method is called, the {@link #entryCount(long)} is
         * automatically set to match the size of the provided list. The list
         * is defensively copied during the {@link #build()} call to ensure
         * immutability of the resulting trailer.</p>
         *
         * @param tocEntries the list of TOC entries to include in the trailer;
         *                   must not be {@code null}; may be empty for archives
         *                   with no entries; the list is copied during build
         * @return this builder instance to allow fluent method chaining for setting
         *         additional trailer properties
         *
         * @see TocEntry
         * @see Trailer#tocEntries()
         */
        public @NotNull Builder tocEntries(final @NotNull List<TocEntry> tocEntries) {
            this.tocEntries = tocEntries;
            this.entryCount = tocEntries.size();
            return this;
        }

        /**
         * Builds and returns a new immutable {@link Trailer} instance.
         *
         * <p>This method constructs a new trailer using all the values that
         * have been set on this builder. Any values not explicitly set will
         * use their default values as established when the builder was created.</p>
         *
         * <p>The resulting trailer is immutable and thread-safe. The TOC entries
         * list is defensively copied to ensure that modifications to the original
         * list do not affect the created trailer.</p>
         *
         * <p>The builder can be reused after calling this method to create
         * additional trailers with different or modified values.</p>
         *
         * @return a new immutable {@link Trailer} instance containing all the
         *         configured values; never {@code null}
         *
         * @see Trailer
         */
        public @NotNull Trailer build() {
            return new Trailer(
                    this.trailerVersion,
                    this.tocOffset,
                    this.tocSize,
                    this.entryCount,
                    this.totalOriginalSize,
                    this.totalStoredSize,
                    this.tocChecksum,
                    this.trailerChecksum,
                    this.fileSize,
                    this.tocEntries
            );
        }

    }

}
