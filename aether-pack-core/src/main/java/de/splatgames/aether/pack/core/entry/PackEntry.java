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

package de.splatgames.aether.pack.core.entry;

import de.splatgames.aether.pack.core.format.Attribute;
import de.splatgames.aether.pack.core.format.EntryHeader;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An immutable entry read from an APACK archive.
 *
 * <p>This class represents entry metadata as read from an existing archive.
 * It wraps an {@link EntryHeader} and provides the file offset where the
 * entry's data chunks begin. Once created, all properties are immutable.</p>
 *
 * <h2>Relationship to EntryHeader</h2>
 * <p>A {@code PackEntry} is a higher-level abstraction over the raw
 * {@link EntryHeader} record. While {@code EntryHeader} represents the
 * exact binary format, {@code PackEntry} provides:</p>
 * <ul>
 *   <li>Implementation of the {@link Entry} interface</li>
 *   <li>File offset tracking for data access</li>
 *   <li>Convenient {@link #toString()} representation</li>
 * </ul>
 *
 * <h2>Creation</h2>
 * <p>Instances are created by {@link de.splatgames.aether.pack.core.AetherPackReader}
 * during archive parsing. The reader extracts the entry header and calculates
 * the data offset:</p>
 * <pre>{@code
 * // Internal creation (by AetherPackReader)
 * EntryHeader header = HeaderIO.readEntryHeader(reader);
 * long dataOffset = reader.getPosition();
 * PackEntry entry = new PackEntry(header, dataOffset);
 * }</pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (AetherPackReader reader = AetherPackReader.open(path)) {
 *     // Find a specific entry
 *     PackEntry entry = (PackEntry) reader.getEntry("config.json")
 *         .orElseThrow(() -> new FileNotFoundException("config.json"));
 *
 *     // Access metadata
 *     System.out.println("Entry: " + entry.getName());
 *     System.out.println("Size: " + entry.getOriginalSize() + " bytes");
 *     System.out.println("Chunks: " + entry.getChunkCount());
 *     System.out.println("Offset: 0x" + Long.toHexString(entry.getOffset()));
 *
 *     // Access the raw header if needed
 *     EntryHeader header = entry.getHeader();
 *     System.out.println("Flags: 0x" + Integer.toHexString(header.flags()));
 *
 *     // Read the entry content
 *     try (InputStream in = reader.openEntry(entry)) {
 *         // Process content...
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is immutable and thread-safe. Multiple threads can safely
 * access the same {@code PackEntry} instance concurrently. However, reading
 * the actual entry content via {@code AetherPackReader} may require
 * synchronization depending on the reader's threading model.</p>
 *
 * @see Entry
 * @see EntryMetadata
 * @see EntryHeader
 * @see de.splatgames.aether.pack.core.AetherPackReader
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class PackEntry implements Entry {

    /** The underlying entry header containing all entry metadata. */
    private final @NotNull EntryHeader header;

    /** The absolute file offset to this entry's first data chunk. */
    private final long offset;

    /**
     * Constructs a new pack entry from an entry header and data offset.
     *
     * <p>This constructor is typically called by {@link de.splatgames.aether.pack.core.AetherPackReader}
     * when parsing an archive. The offset should point to the first byte of the entry's
     * chunk data, immediately following the entry header in the file.</p>
     *
     * @param header the entry header containing all entry metadata
     * @param offset the absolute file offset to the entry data (first chunk)
     * @throws NullPointerException if header is null
     */
    public PackEntry(final @NotNull EntryHeader header, final long offset) {
        this.header = header;
        this.offset = offset;
    }

    /**
     * Returns the underlying entry header containing raw binary format metadata.
     *
     * <p>The entry header is the low-level record that directly represents
     * the binary format structure stored in the archive file. While {@code PackEntry}
     * provides convenient accessor methods via the {@link Entry} interface, this
     * method gives direct access to the raw header for advanced use cases such as:</p>
     * <ul>
     *   <li>Accessing format-specific fields not exposed by the {@link Entry} interface</li>
     *   <li>Inspecting raw flags and processing parameters</li>
     *   <li>Debugging and format validation</li>
     *   <li>Re-serializing entry metadata</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <p>The {@link EntryHeader} record contains:</p>
     * <ul>
     *   <li>Entry identification (ID, name, MIME type)</li>
     *   <li>Size information (original, stored, chunk count)</li>
     *   <li>Processing flags (compression, encryption, ECC)</li>
     *   <li>Algorithm identifiers (compression ID, encryption ID)</li>
     *   <li>Custom attributes as key-value pairs</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * PackEntry entry = reader.getEntry("data.bin")
     *     .map(e -> (PackEntry) e)
     *     .orElseThrow();
     *
     * EntryHeader header = entry.getHeader();
     * System.out.println("Raw flags: 0x" + Integer.toHexString(header.flags()));
     * System.out.println("ECC parity: " + header.eccParityBytes());
     * }</pre>
     *
     * @return the underlying {@link EntryHeader} record containing all entry
     *         metadata as read from the archive; never {@code null}; the
     *         returned header is immutable
     *
     * @see EntryHeader
     * @see de.splatgames.aether.pack.core.io.HeaderIO#readEntryHeader(de.splatgames.aether.pack.core.io.BinaryReader)
     */
    public @NotNull EntryHeader getHeader() {
        return this.header;
    }

    /**
     * Returns the absolute file offset to this entry's chunk data.
     *
     * <p>This offset points to the first byte of the entry's data in the
     * archive file, immediately following the entry header. For entries with
     * multiple chunks, this is the starting position of the first chunk header.</p>
     *
     * <p>The offset is used by {@link de.splatgames.aether.pack.core.AetherPackReader}
     * to seek to the correct position when reading entry content. In container
     * mode archives, entries may not be contiguous in the file, so the offset
     * is essential for random access.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The offset is calculated during archive parsing as:</p>
     * <pre>
     * offset = position after reading entry header
     *        = entryHeaderStart + entryHeaderLength
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * PackEntry entry = (PackEntry) reader.getEntry("image.png").orElseThrow();
     *
     * // Log entry location for debugging
     * System.out.printf("Entry '%s' data starts at offset 0x%08X%n",
     *     entry.getName(), entry.getOffset());
     *
     * // Calculate approximate position in file
     * long fileSize = Files.size(archivePath);
     * double positionPercent = (double) entry.getOffset() / fileSize * 100;
     * System.out.printf("Located at %.1f%% into the file%n", positionPercent);
     * }</pre>
     *
     * @return the absolute byte offset from the beginning of the archive file
     *         to the start of this entry's chunk data; always a non-negative
     *         value; typically aligned to the chunk boundary
     *
     * @see #getHeader()
     * @see de.splatgames.aether.pack.core.AetherPackReader
     */
    public long getOffset() {
        return this.offset;
    }

    /**
     * {@inheritDoc}
     *
     * @return the unique entry ID from the underlying header
     */
    @Override
    public long getId() {
        return this.header.entryId();
    }

    /**
     * {@inheritDoc}
     *
     * @return the entry name from the underlying header
     */
    @Override
    public @NotNull String getName() {
        return this.header.name();
    }

    /**
     * {@inheritDoc}
     *
     * @return the MIME type from the underlying header, or empty string if not set
     */
    @Override
    public @NotNull String getMimeType() {
        return this.header.mimeType();
    }

    /**
     * {@inheritDoc}
     *
     * @return the original (uncompressed) size in bytes
     */
    @Override
    public long getOriginalSize() {
        return this.header.originalSize();
    }

    /**
     * {@inheritDoc}
     *
     * @return the stored (compressed/encrypted) size in bytes
     */
    @Override
    public long getStoredSize() {
        return this.header.storedSize();
    }

    /**
     * {@inheritDoc}
     *
     * @return the number of chunks this entry consists of
     */
    @Override
    public int getChunkCount() {
        return this.header.chunkCount();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code true} if this entry uses compression
     */
    @Override
    public boolean isCompressed() {
        return this.header.isCompressed();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code true} if this entry is encrypted
     */
    @Override
    public boolean isEncrypted() {
        return this.header.isEncrypted();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code true} if this entry has Reed-Solomon error correction
     */
    @Override
    public boolean hasEcc() {
        return this.header.hasEcc();
    }

    /**
     * {@inheritDoc}
     *
     * @return the compression algorithm ID, or 0 if not compressed
     */
    @Override
    public int getCompressionId() {
        return this.header.compressionId();
    }

    /**
     * {@inheritDoc}
     *
     * @return the encryption algorithm ID, or 0 if not encrypted
     */
    @Override
    public int getEncryptionId() {
        return this.header.encryptionId();
    }

    /**
     * {@inheritDoc}
     *
     * @return an unmodifiable list of custom attributes
     */
    @Override
    public @NotNull List<Attribute> getAttributes() {
        return this.header.attributes();
    }

    /**
     * Returns a string representation of this pack entry.
     *
     * <p>The format includes the entry name, original size, and processing flags
     * (compressed, encrypted). This is useful for debugging and logging.</p>
     *
     * @return a string representation in the format:
     *         {@code PackEntry[name=..., size=..., compressed=..., encrypted=...]}
     */
    @Override
    public String toString() {
        return "PackEntry[name=" + getName() +
                ", size=" + getOriginalSize() +
                ", compressed=" + isCompressed() +
                ", encrypted=" + isEncrypted() + "]";
    }

}
