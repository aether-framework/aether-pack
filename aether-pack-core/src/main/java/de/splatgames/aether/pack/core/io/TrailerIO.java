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

import de.splatgames.aether.pack.core.exception.FormatException;
import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.core.format.StreamTrailer;
import de.splatgames.aether.pack.core.format.TocEntry;
import de.splatgames.aether.pack.core.format.Trailer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Utility class for serializing and deserializing APACK trailers and TOC entries.
 *
 * <p>This class provides static methods for reading and writing the binary
 * representations of {@link Trailer}, {@link StreamTrailer}, and {@link TocEntry}
 * structures. It handles all the low-level details of byte order and field encoding.</p>
 *
 * <h2>Supported Structures</h2>
 * <ul>
 *   <li>{@link Trailer} - Container-mode trailer with TOC for random access</li>
 *   <li>{@link StreamTrailer} - Simplified stream-mode trailer</li>
 *   <li>{@link TocEntry} - Individual Table of Contents entries</li>
 * </ul>
 *
 * <h2>Container Mode Trailer</h2>
 * <p>In container mode, the trailer contains:</p>
 * <ul>
 *   <li>Table of Contents for random entry access</li>
 *   <li>Aggregate statistics (total sizes, entry count)</li>
 *   <li>File integrity checksums</li>
 * </ul>
 *
 * <h2>Stream Mode Trailer</h2>
 * <p>In stream mode, a simplified trailer is used with:</p>
 * <ul>
 *   <li>Original and stored size totals</li>
 *   <li>Chunk count</li>
 *   <li>Trailer checksum</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Writing a container trailer
 * List<TocEntry> tocEntries = buildTocEntries();
 * Trailer trailer = Trailer.builder()
 *     .tocEntries(tocEntries)
 *     .totalOriginalSize(totalOriginal)
 *     .totalStoredSize(totalStored)
 *     .fileSize(channel.size())
 *     .build();
 * TrailerIO.writeTrailer(writer, trailer);
 *
 * // Reading a container trailer
 * Trailer readTrailer = TrailerIO.readTrailer(reader);
 * List<TocEntry> entries = TrailerIO.readTocEntries(reader, (int) readTrailer.entryCount());
 *
 * // Writing a stream trailer
 * StreamTrailer streamTrailer = StreamTrailer.builder()
 *     .originalSize(originalBytes)
 *     .storedSize(storedBytes)
 *     .chunkCount(chunks)
 *     .build();
 * TrailerIO.writeStreamTrailer(writer, streamTrailer);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All methods in this class are stateless and thread-safe. However, the
 * {@link BinaryReader} and {@link BinaryWriter} instances passed to these
 * methods are not thread-safe.</p>
 *
 * @see Trailer
 * @see StreamTrailer
 * @see TocEntry
 * @see HeaderIO
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class TrailerIO {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private TrailerIO() {
        // Utility class
    }

    // ==================== Container Trailer ====================

    /**
     * Writes a container-mode trailer with its Table of Contents to the output stream.
     *
     * <p>This method writes the complete trailer structure for container-mode APACK
     * archives. The trailer provides random access capabilities by including a Table
     * of Contents (TOC) that indexes all entries in the archive.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The trailer is written in the following order:</p>
     * <ol>
     *   <li>TOC entries (one {@link TocEntry} per archive entry)</li>
     *   <li>Trailer header with magic, version, offsets, sizes, and checksums</li>
     * </ol>
     *
     * <p><strong>\:</strong></p>
     * <p>The trailer header is exactly {@link FormatConstants#TRAILER_HEADER_SIZE} bytes
     * and follows the TOC entries in the file. This layout allows readers to seek
     * to the end of the file, read the trailer header to find the TOC location,
     * and then seek back to read individual entries.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Build TOC entries from written entry data
     * List<TocEntry> tocEntries = new ArrayList<>();
     * for (EntryInfo entry : writtenEntries) {
     *     tocEntries.add(TocEntry.builder()
     *         .entryId(entry.id())
     *         .entryOffset(entry.offset())
     *         .originalSize(entry.originalSize())
     *         .storedSize(entry.storedSize())
     *         .nameHash(hashName(entry.name()))
     *         .build());
     * }
     *
     * // Create and write trailer
     * Trailer trailer = Trailer.builder()
     *     .tocEntries(tocEntries)
     *     .totalOriginalSize(totalOriginal)
     *     .totalStoredSize(totalStored)
     *     .fileSize(outputChannel.position())
     *     .build();
     *
     * TrailerIO.writeTrailer(writer, trailer);
     * }</pre>
     *
     * @param writer the binary writer to which the trailer will be written;
     *               must not be {@code null}; should be positioned at the
     *               intended trailer location (typically end of archive data)
     * @param trailer the container trailer containing TOC entries and aggregate
     *                statistics to write; must not be {@code null}; the
     *                {@link Trailer#tocEntries()} list determines which entries
     *                are written to the TOC
     * @throws IOException if an I/O error occurs while writing to the underlying
     *                     output stream
     *
     * @see #readTrailer(BinaryReader)
     * @see #writeTocEntry(BinaryWriter, TocEntry)
     * @see Trailer
     */
    public static void writeTrailer(
            final @NotNull BinaryWriter writer,
            final @NotNull Trailer trailer) throws IOException {

        final long tocStartOffset = writer.getBytesWritten();

        // Write TOC entries
        for (final TocEntry entry : trailer.tocEntries()) {
            writeTocEntry(writer, entry);
        }

        final long tocEndOffset = writer.getBytesWritten();
        final long tocSize = tocEndOffset - tocStartOffset;

        // Compute TOC checksum
        // Note: In a real implementation, we would compute this during TOC writing
        final int tocChecksum = 0; // Placeholder

        // Write trailer header
        writer.writeBytes(FormatConstants.TRAILER_MAGIC);
        writer.writeInt32(trailer.trailerVersion());
        writer.writeInt64(0); // tocOffset relative to trailer start - will be fixed
        writer.writeInt64(tocSize);
        writer.writeInt64(trailer.entryCount());
        writer.writeInt64(trailer.totalOriginalSize());
        writer.writeInt64(trailer.totalStoredSize());
        writer.writeInt32(tocChecksum);
        writer.writeInt32(0); // trailerChecksum - placeholder
        writer.writeInt64(trailer.fileSize());
    }

    /**
     * Reads a container-mode trailer header from the input stream.
     *
     * <p>This method reads and parses the trailer header structure for container-mode
     * APACK archives. The trailer provides metadata needed for random access and
     * archive validation.</p>
     *
     * <p><strong>Important:</strong> This method reads only the trailer header,
     * <em>not</em> the TOC entries. The TOC entries must be read separately using
     * {@link #readTocEntries(BinaryReader, int)} after positioning the reader at
     * the TOC offset indicated by the trailer.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>To read a complete container trailer with entries:</p>
     * <ol>
     *   <li>Seek to end of file minus {@link FormatConstants#TRAILER_HEADER_SIZE}</li>
     *   <li>Read the trailer header with this method</li>
     *   <li>Seek to the TOC offset indicated in the trailer</li>
     *   <li>Read TOC entries with {@link #readTocEntries(BinaryReader, int)}</li>
     * </ol>
     *
     * <p><strong>\:</strong></p>
     * <p>This method validates the trailer magic number and throws a
     * {@link FormatException} if it doesn't match the expected value.
     * This helps detect corrupted files or non-APACK data.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Position at trailer header
     * channel.position(fileSize - FormatConstants.TRAILER_SIZE);
     *
     * // Read trailer header
     * Trailer trailer = TrailerIO.readTrailer(reader);
     *
     * // Position at TOC
     * channel.position(trailer.tocOffset());
     *
     * // Read TOC entries
     * List<TocEntry> entries = TrailerIO.readTocEntries(reader, (int) trailer.entryCount());
     * }</pre>
     *
     * @param reader the binary reader from which to read the trailer; must not be
     *               {@code null}; should be positioned at the start of the trailer
     *               header (typically file size minus {@link FormatConstants#TRAILER_HEADER_SIZE})
     * @return the parsed trailer structure containing metadata and offsets, but
     *         with an empty TOC entries list (entries must be read separately);
     *         never {@code null}
     * @throws IOException if an I/O error occurs while reading from the underlying
     *                     input stream
     * @throws FormatException if the trailer magic number is invalid, indicating
     *                         file corruption or incorrect positioning
     *
     * @see #writeTrailer(BinaryWriter, Trailer)
     * @see #readTocEntries(BinaryReader, int)
     * @see Trailer
     */
    public static @NotNull Trailer readTrailer(
            final @NotNull BinaryReader reader) throws IOException, FormatException {

        // Read and validate magic
        final byte[] magic = reader.readBytes(4);
        if (!Arrays.equals(magic, FormatConstants.TRAILER_MAGIC)) {
            throw new FormatException("Invalid trailer magic");
        }

        final int trailerVersion = reader.readInt32();
        final long tocOffset = reader.readInt64();
        final long tocSize = reader.readInt64();
        final long entryCount = reader.readInt64();
        final long totalOriginalSize = reader.readInt64();
        final long totalStoredSize = reader.readInt64();
        final int tocChecksum = reader.readInt32();
        final int trailerChecksum = reader.readInt32();
        final long fileSize = reader.readInt64();

        // TOC entries are read separately (before trailer in file)
        return new Trailer(
                trailerVersion,
                tocOffset,
                tocSize,
                entryCount,
                totalOriginalSize,
                totalStoredSize,
                tocChecksum,
                trailerChecksum,
                fileSize,
                List.of() // Entries loaded separately
        );
    }

    /**
     * Reads multiple Table of Contents entries from the input stream.
     *
     * <p>This method reads a sequence of TOC entries from the current reader
     * position. It is typically called after reading the trailer header to
     * load the complete Table of Contents for random entry access.</p>
     *
     * <p>Each TOC entry is exactly {@link FormatConstants#TOC_ENTRY_SIZE} bytes
     * and contains the metadata needed to locate and identify an archive entry
     * without reading the entry header itself.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>Before calling this method, the reader should be positioned at the
     * start of the TOC. The TOC offset can be obtained from the trailer
     * structure returned by {@link #readTrailer(BinaryReader)}.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>All entries are loaded into memory at once. For archives with millions
     * of entries, consider reading entries on-demand instead of loading all
     * at once.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Read trailer to get entry count and TOC location
     * Trailer trailer = TrailerIO.readTrailer(reader);
     *
     * // Position reader at TOC start
     * channel.position(trailer.tocOffset());
     *
     * // Read all TOC entries
     * List<TocEntry> entries = TrailerIO.readTocEntries(reader, (int) trailer.entryCount());
     *
     * // Find entry by name hash
     * int targetHash = computeNameHash("data/config.json");
     * TocEntry found = entries.stream()
     *     .filter(e -> e.nameHash() == targetHash)
     *     .findFirst()
     *     .orElseThrow();
     * }</pre>
     *
     * @param reader the binary reader from which to read the TOC entries;
     *               must not be {@code null}; should be positioned at the
     *               start of the TOC (as indicated by the trailer's tocOffset)
     * @param entryCount the number of TOC entries to read; must be non-negative;
     *                   typically obtained from {@link Trailer#entryCount()}
     * @return a list containing all parsed TOC entries in the order they were
     *         stored; never {@code null}; the list has exactly {@code entryCount}
     *         elements if reading succeeds
     * @throws IOException if an I/O error occurs while reading from the underlying
     *                     input stream, or if end-of-file is reached before all
     *                     entries are read
     *
     * @see #readTocEntry(BinaryReader)
     * @see #readTrailer(BinaryReader)
     * @see TocEntry
     */
    public static @NotNull List<TocEntry> readTocEntries(
            final @NotNull BinaryReader reader,
            final int entryCount) throws IOException {

        final List<TocEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(readTocEntry(reader));
        }
        return entries;
    }

    // ==================== TOC Entry ====================

    /**
     * Writes a single Table of Contents entry to the output stream.
     *
     * <p>This method writes one TOC entry in the binary format used by APACK
     * archives. Each entry is exactly {@link FormatConstants#TOC_ENTRY_SIZE}
     * bytes and contains the metadata needed to locate and identify an
     * archive entry without reading its full header.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The TOC entry has the following structure (Little-Endian byte order):</p>
     * <pre>
     * Offset  Size  Field         Description
     * ────────────────────────────────────────────────────
     * 0x00    8     entryId       Unique entry identifier
     * 0x08    8     entryOffset   Byte offset to entry header
     * 0x10    8     originalSize  Uncompressed entry size
     * 0x18    8     storedSize    Compressed entry size
     * 0x20    4     nameHash      Hash of entry name for lookup
     * 0x24    4     entryChecksum CRC32 of entry data
     * ────────────────────────────────────────────────────
     * Total: 40 bytes
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <p>The name hash allows efficient entry lookup without storing full
     * paths in the TOC. When searching for an entry by name, the caller
     * computes the same hash and filters entries by hash match.</p>
     *
     * @param writer the binary writer to which the TOC entry will be written;
     *               must not be {@code null}; the entry is written at the
     *               current stream position
     * @param entry the TOC entry containing the metadata to write; must not be
     *              {@code null}; all fields are written in order
     * @throws IOException if an I/O error occurs while writing to the underlying
     *                     output stream
     *
     * @see #readTocEntry(BinaryReader)
     * @see #writeTrailer(BinaryWriter, Trailer)
     * @see TocEntry
     * @see FormatConstants#TOC_ENTRY_SIZE
     */
    public static void writeTocEntry(
            final @NotNull BinaryWriter writer,
            final @NotNull TocEntry entry) throws IOException {

        writer.writeInt64(entry.entryId());
        writer.writeInt64(entry.entryOffset());
        writer.writeInt64(entry.originalSize());
        writer.writeInt64(entry.storedSize());
        writer.writeInt32(entry.nameHash());
        writer.writeInt32(entry.entryChecksum());
    }

    /**
     * Reads a single Table of Contents entry from the input stream.
     *
     * <p>This method reads one TOC entry from the current reader position,
     * parsing exactly {@link FormatConstants#TOC_ENTRY_SIZE} bytes of data.
     * The entry contains metadata for locating and identifying an archive
     * entry without reading its full header.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The TOC entry is read with the following structure (Little-Endian):</p>
     * <pre>
     * Offset  Size  Field         Description
     * ────────────────────────────────────────────────────
     * 0x00    8     entryId       Unique entry identifier
     * 0x08    8     entryOffset   Byte offset to entry header
     * 0x10    8     originalSize  Uncompressed entry size
     * 0x18    8     storedSize    Compressed entry size
     * 0x20    4     nameHash      Hash of entry name for lookup
     * 0x24    4     entryChecksum CRC32 of entry data
     * ────────────────────────────────────────────────────
     * Total: 40 bytes
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <p>This method is typically called in a loop to read multiple entries,
     * or via {@link #readTocEntries(BinaryReader, int)} which handles the
     * iteration:</p>
     * <pre>{@code
     * // Read single entry
     * TocEntry entry = TrailerIO.readTocEntry(reader);
     *
     * // Use entry to seek to actual entry data
     * channel.position(entry.entryOffset());
     * EntryHeader header = HeaderIO.readEntryHeader(reader);
     * }</pre>
     *
     * @param reader the binary reader from which to read the TOC entry;
     *               must not be {@code null}; exactly
     *               {@link FormatConstants#TOC_ENTRY_SIZE} bytes are read
     *               from the current position
     * @return the parsed TOC entry containing entry location and identification
     *         metadata; never {@code null}
     * @throws IOException if an I/O error occurs while reading from the underlying
     *                     input stream, or if end-of-file is reached before all
     *                     entry bytes are read
     *
     * @see #writeTocEntry(BinaryWriter, TocEntry)
     * @see #readTocEntries(BinaryReader, int)
     * @see TocEntry
     * @see FormatConstants#TOC_ENTRY_SIZE
     */
    public static @NotNull TocEntry readTocEntry(
            final @NotNull BinaryReader reader) throws IOException {

        final long entryId = reader.readInt64();
        final long entryOffset = reader.readInt64();
        final long originalSize = reader.readInt64();
        final long storedSize = reader.readInt64();
        final int nameHash = reader.readInt32();
        final int entryChecksum = reader.readInt32();

        return new TocEntry(
                entryId,
                entryOffset,
                originalSize,
                storedSize,
                nameHash,
                entryChecksum
        );
    }

    // ==================== Stream Trailer ====================

    /**
     * Writes a stream-mode trailer to the output stream.
     *
     * <p>This method writes the simplified trailer structure used by stream-mode
     * APACK archives. Stream mode is optimized for single-entry archives that
     * are written and read sequentially (e.g., piped data, streaming compression).</p>
     *
     * <p>Unlike the container-mode trailer, the stream trailer does not include
     * a Table of Contents since there is only one entry and random access is
     * not required.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The stream trailer is exactly {@link FormatConstants#STREAM_TRAILER_SIZE}
     * (32) bytes with the following structure (Little-Endian byte order):</p>
     * <pre>
     * Offset  Size  Field            Description
     * ─────────────────────────────────────────────────────────
     * 0x00    4     magic            "STRL" (stream trailer magic)
     * 0x04    4     reserved         Reserved for future use
     * 0x08    8     originalSize     Original uncompressed size
     * 0x10    8     storedSize       Stored compressed size
     * 0x18    4     chunkCount       Number of chunks written
     * 0x1C    4     trailerChecksum  CRC32 of trailer bytes
     * ─────────────────────────────────────────────────────────
     * Total: 32 bytes
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // After finishing chunk writing
     * chunkedOutput.finish();
     *
     * // Create and write stream trailer
     * StreamTrailer trailer = StreamTrailer.builder()
     *     .originalSize(chunkedOutput.getTotalBytesWritten())
     *     .storedSize(chunkedOutput.getTotalStoredBytes())
     *     .chunkCount(chunkedOutput.getChunkCount())
     *     .trailerChecksum(computeTrailerChecksum())
     *     .build();
     *
     * TrailerIO.writeStreamTrailer(writer, trailer);
     * }</pre>
     *
     * @param writer the binary writer to which the stream trailer will be written;
     *               must not be {@code null}; should be positioned immediately
     *               after the last chunk data
     * @param trailer the stream trailer containing size and chunk count metadata
     *                to write; must not be {@code null}
     * @throws IOException if an I/O error occurs while writing to the underlying
     *                     output stream
     *
     * @see #readStreamTrailer(BinaryReader)
     * @see StreamTrailer
     * @see FormatConstants#STREAM_TRAILER_SIZE
     * @see FormatConstants#STREAM_TRAILER_MAGIC
     */
    public static void writeStreamTrailer(
            final @NotNull BinaryWriter writer,
            final @NotNull StreamTrailer trailer) throws IOException {

        writer.writeBytes(FormatConstants.STREAM_TRAILER_MAGIC);
        writer.writeInt32(0); // reserved
        writer.writeInt64(trailer.originalSize());
        writer.writeInt64(trailer.storedSize());
        writer.writeInt32(trailer.chunkCount());
        writer.writeInt32(trailer.trailerChecksum());
    }

    /**
     * Reads a stream-mode trailer from the input stream.
     *
     * <p>This method reads and parses the simplified trailer structure used by
     * stream-mode APACK archives. The stream trailer is located at the end of
     * the archive, immediately after the last chunk's data.</p>
     *
     * <p>Stream mode is optimized for single-entry archives where data is
     * written and read sequentially. The stream trailer provides summary
     * information about the stream without a Table of Contents.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The stream trailer is exactly {@link FormatConstants#STREAM_TRAILER_SIZE}
     * (32) bytes with the following structure (Little-Endian byte order):</p>
     * <pre>
     * Offset  Size  Field            Description
     * ─────────────────────────────────────────────────────────
     * 0x00    4     magic            "STRL" (stream trailer magic)
     * 0x04    4     reserved         Reserved for future use
     * 0x08    8     originalSize     Original uncompressed size
     * 0x10    8     storedSize       Stored compressed size
     * 0x18    4     chunkCount       Number of chunks in stream
     * 0x1C    4     trailerChecksum  CRC32 of trailer bytes
     * ─────────────────────────────────────────────────────────
     * Total: 32 bytes
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <p>This method validates the stream trailer magic number and throws a
     * {@link FormatException} if it doesn't match the expected "STRL" value.
     * This helps detect corrupted files, incorrect positioning, or attempts
     * to read a container-mode archive as stream-mode.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Read all chunks first (stream must be read sequentially)
     * try (ChunkedInputStream in = new ChunkedInputStream(input, checksumProvider)) {
     *     byte[] data = in.readAllBytes();
     * }
     *
     * // Read stream trailer
     * StreamTrailer trailer = TrailerIO.readStreamTrailer(reader);
     *
     * // Verify data integrity
     * if (data.length != trailer.originalSize()) {
     *     throw new IOException("Size mismatch: expected " + trailer.originalSize()
     *         + " but got " + data.length);
     * }
     * }</pre>
     *
     * @param reader the binary reader from which to read the stream trailer;
     *               must not be {@code null}; should be positioned at the start
     *               of the stream trailer (immediately after the last chunk)
     * @return the parsed stream trailer containing size and chunk count metadata;
     *         never {@code null}
     * @throws IOException if an I/O error occurs while reading from the underlying
     *                     input stream
     * @throws FormatException if the stream trailer magic number is invalid,
     *                         indicating file corruption, incorrect positioning,
     *                         or attempting to read a container-mode trailer
     *
     * @see #writeStreamTrailer(BinaryWriter, StreamTrailer)
     * @see StreamTrailer
     * @see FormatConstants#STREAM_TRAILER_SIZE
     * @see FormatConstants#STREAM_TRAILER_MAGIC
     */
    public static @NotNull StreamTrailer readStreamTrailer(
            final @NotNull BinaryReader reader) throws IOException, FormatException {

        // Read and validate magic
        final byte[] magic = reader.readBytes(4);
        if (!Arrays.equals(magic, FormatConstants.STREAM_TRAILER_MAGIC)) {
            throw new FormatException("Invalid stream trailer magic");
        }

        reader.readInt32(); // reserved

        final long originalSize = reader.readInt64();
        final long storedSize = reader.readInt64();
        final int chunkCount = reader.readInt32();
        final int trailerChecksum = reader.readInt32();

        return new StreamTrailer(
                originalSize,
                storedSize,
                chunkCount,
                trailerChecksum
        );
    }

}
