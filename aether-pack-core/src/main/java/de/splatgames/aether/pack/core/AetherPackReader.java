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

package de.splatgames.aether.pack.core;

import de.splatgames.aether.pack.core.checksum.ChecksumRegistry;
import de.splatgames.aether.pack.core.entry.Entry;
import de.splatgames.aether.pack.core.entry.PackEntry;
import de.splatgames.aether.pack.core.exception.ApackException;
import de.splatgames.aether.pack.core.exception.EntryNotFoundException;
import de.splatgames.aether.pack.core.exception.FormatException;
import de.splatgames.aether.pack.core.format.EncryptionBlock;
import de.splatgames.aether.pack.core.format.EntryHeader;
import de.splatgames.aether.pack.core.format.FileHeader;
import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.core.format.TocEntry;
import de.splatgames.aether.pack.core.io.BinaryReader;
import de.splatgames.aether.pack.core.io.ChunkProcessor;
import de.splatgames.aether.pack.core.io.ChunkSecuritySettings;
import de.splatgames.aether.pack.core.io.ChunkedInputStream;
import de.splatgames.aether.pack.core.io.HeaderIO;
import de.splatgames.aether.pack.core.io.TrailerIO;
import de.splatgames.aether.pack.core.spi.ChecksumProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Reader for accessing APACK archive files.
 *
 * <p>This class provides the primary API for reading existing APACK archives. It supports
 * random access to entries, iteration over all entries, and streaming data extraction
 * with automatic decompression and decryption when appropriately configured.</p>
 *
 * <h2>Basic Usage</h2>
 * <p>The simplest way to read an archive is using the try-with-resources pattern:</p>
 * <pre>{@code
 * try (AetherPackReader reader = AetherPackReader.open(Path.of("archive.apack"))) {
 *     for (Entry entry : reader) {
 *         System.out.println("Entry: " + entry.getName() + " (" + entry.getOriginalSize() + " bytes)");
 *         try (InputStream input = reader.getInputStream(entry)) {
 *             // Process entry data
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Random Access by Name</h2>
 * <p>Entries can be accessed directly by name without iteration:</p>
 * <pre>{@code
 * try (AetherPackReader reader = AetherPackReader.open(path)) {
 *     byte[] data = reader.readAllBytes("config/settings.json");
 *     // or
 *     Optional<Entry> entry = reader.getEntry("data.bin");
 * }
 * }</pre>
 *
 * <h2>Reading Compressed Archives</h2>
 * <p>For archives created with compression, provide a matching {@link ChunkProcessor}:</p>
 * <pre>{@code
 * ChunkProcessor processor = ChunkProcessor.builder()
 *     .compression(CompressionRegistry.zstd())
 *     .build();
 *
 * try (AetherPackReader reader = AetherPackReader.open(path, processor)) {
 *     // Data is automatically decompressed during reading
 * }
 * }</pre>
 *
 * <h2>Reading Encrypted Archives</h2>
 * <p>For encrypted archives, provide the encryption key:</p>
 * <pre>{@code
 * ChunkProcessor processor = ChunkProcessor.builder()
 *     .encryption(EncryptionRegistry.aes256Gcm(), secretKey)
 *     .build();
 *
 * try (AetherPackReader reader = AetherPackReader.open(path, processor)) {
 *     // Data is automatically decrypted during reading
 * }
 * }</pre>
 *
 * <h2>Entry Lookup Performance</h2>
 * <p>This reader builds hash-based indexes for fast entry lookup:</p>
 * <ul>
 *   <li><strong>By ID:</strong> O(1) lookup using a {@link HashMap}</li>
 *   <li><strong>By name:</strong> O(1) average case using XXH3 name hashing with collision handling</li>
 *   <li><strong>Iteration:</strong> O(n) in entry order</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is <strong>not thread-safe</strong>. The underlying {@link SeekableByteChannel}
 * is shared across all read operations, so concurrent reads from multiple threads
 * will result in undefined behavior. External synchronization is required for
 * multi-threaded access.</p>
 *
 * <h2>Resource Management</h2>
 * <p>This class implements {@link Closeable} and holds a file channel that must be
 * released. Always use try-with-resources or explicitly call {@link #close()}.</p>
 *
 * @see AetherPackWriter
 * @see Entry
 * @see ChunkProcessor
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class AetherPackReader implements Closeable, Iterable<Entry> {

    /** The seekable channel for random access to archive data. */
    private final @NotNull SeekableByteChannel channel;

    /** The parsed file header containing format metadata. */
    private final @NotNull FileHeader fileHeader;

    /** The checksum provider for verifying data integrity. */
    private final @NotNull ChecksumProvider checksumProvider;

    /** The chunk processor for decompression/decryption. */
    private final @NotNull ChunkProcessor chunkProcessor;

    /** Security settings for chunk validation (decompression bomb protection, size limits). */
    private final @NotNull ChunkSecuritySettings securitySettings;

    /** Encryption block containing KDF params, salt, wrapped key (null if not encrypted). */
    private final @Nullable EncryptionBlock encryptionBlock;

    /** Immutable list of all entries in archive order. */
    private final @NotNull List<PackEntry> entries;

    /** Index for O(1) entry lookup by ID. */
    private final @NotNull Map<Long, PackEntry> entriesById;

    /** Index for O(1) entry lookup by name hash (with collision lists). */
    private final @NotNull Map<Integer, List<PackEntry>> entriesByNameHash;

    /** Whether this reader has been closed. */
    private boolean closed;

    /**
     * Private constructor for creating a reader instance.
     *
     * <p>This constructor initializes the reader state and builds lookup indexes
     * for efficient entry access by ID and name. Use the static factory methods
     * instead of calling this constructor directly.</p>
     *
     * @param channel          the seekable byte channel for reading archive data
     * @param fileHeader        the parsed file header
     * @param checksumProvider  the checksum provider for data verification
     * @param chunkProcessor    the chunk processor for decompression/decryption
     * @param securitySettings  the security settings for chunk validation
     * @param encryptionBlock   the encryption metadata block, or {@code null} if not encrypted
     * @param entries           the list of entries loaded from the archive
     *
     * @see #open(Path)
     * @see #open(SeekableByteChannel, ChunkProcessor)
     */
    private AetherPackReader(
            final @NotNull SeekableByteChannel channel,
            final @NotNull FileHeader fileHeader,
            final @NotNull ChecksumProvider checksumProvider,
            final @NotNull ChunkProcessor chunkProcessor,
            final @NotNull ChunkSecuritySettings securitySettings,
            final @Nullable EncryptionBlock encryptionBlock,
            final @NotNull List<PackEntry> entries) {

        this.channel = channel;
        this.fileHeader = fileHeader;
        this.checksumProvider = checksumProvider;
        this.chunkProcessor = chunkProcessor;
        this.securitySettings = securitySettings;
        this.encryptionBlock = encryptionBlock;
        this.entries = List.copyOf(entries);
        this.entriesById = new HashMap<>();
        this.entriesByNameHash = new HashMap<>();
        this.closed = false;

        // Build indexes
        for (final PackEntry entry : entries) {
            this.entriesById.put(entry.getId(), entry);

            final int nameHash = computeNameHash(entry.getName());
            this.entriesByNameHash
                    .computeIfAbsent(nameHash, k -> new ArrayList<>())
                    .add(entry);
        }
    }

    /**
     * Opens an APACK file for reading.
     *
     * <p>This factory method opens the specified file and reads its header and
     * table of contents. No data processing (decompression/decryption) is configured;
     * use this for uncompressed, unencrypted archives or when you need to inspect
     * the archive structure before configuring processing.</p>
     *
     * <p>The file must be a valid APACK archive with the correct magic number
     * and a compatible format version.</p>
     *
     * @param path the file path to open; must not be {@code null}
     * @return a new reader instance
     * @throws IOException if the file cannot be opened or read
     * @throws ApackException if the file format is invalid or unsupported
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws java.nio.file.NoSuchFileException if the file does not exist
     */
    public static @NotNull AetherPackReader open(final @NotNull Path path) throws IOException, ApackException {
        return open(path, ChunkProcessor.passThrough());
    }

    /**
     * Opens an APACK file for reading with a configuration for decompression/decryption.
     *
     * <p>This factory method opens the file and configures a {@link ChunkProcessor}
     * from the provided configuration. The processor handles decompression and/or
     * decryption of chunk data during reading.</p>
     *
     * @param path   the file path to open; must not be {@code null}
     * @param config the configuration with compression/encryption settings; must not be {@code null}
     * @return a new reader instance
     * @throws IOException if the file cannot be opened or read
     * @throws ApackException if the file format is invalid or unsupported
     * @throws NullPointerException if {@code path} or {@code config} is {@code null}
     *
     * @see ApackConfiguration#createChunkProcessor()
     */
    public static @NotNull AetherPackReader open(
            final @NotNull Path path,
            final @NotNull ApackConfiguration config) throws IOException, ApackException {
        return open(path, config.createChunkProcessor());
    }

    /**
     * Opens an APACK file for reading with a chunk processor.
     *
     * <p>This factory method provides the most control over data processing. The
     * provided chunk processor will be used to decompress and/or decrypt all chunk
     * data read from the archive.</p>
     *
     * <p>If opening fails after the file channel is acquired (e.g., due to format
     * validation failure), the channel is automatically closed before the exception
     * is propagated.</p>
     *
     * @param path           the file path to open; must not be {@code null}
     * @param chunkProcessor the chunk processor for decompression/decryption; must not be {@code null}
     * @return a new reader instance
     * @throws IOException if the file cannot be opened or read
     * @throws ApackException if the file format is invalid or unsupported
     * @throws NullPointerException if {@code path} or {@code chunkProcessor} is {@code null}
     *
     * @see ChunkProcessor#builder()
     */
    public static @NotNull AetherPackReader open(
            final @NotNull Path path,
            final @NotNull ChunkProcessor chunkProcessor) throws IOException, ApackException {
        return open(path, chunkProcessor, ChunkSecuritySettings.DEFAULT);
    }

    /**
     * Opens an APACK file for reading with custom security settings.
     *
     * <p>This factory method allows configuring security limits for chunk validation,
     * such as maximum chunk size and decompression ratio limits. Use this when you
     * need to process archives with unusually large chunks or high compression ratios.</p>
     *
     * @param path             the file path to open; must not be {@code null}
     * @param chunkProcessor   the chunk processor for decompression/decryption; must not be {@code null}
     * @param securitySettings the security settings for chunk validation; must not be {@code null}
     * @return a new reader instance
     * @throws IOException if the file cannot be opened or read
     * @throws ApackException if the file format is invalid or unsupported
     *
     * @see ChunkSecuritySettings
     * @see ChunkSecuritySettings#builder()
     */
    public static @NotNull AetherPackReader open(
            final @NotNull Path path,
            final @NotNull ChunkProcessor chunkProcessor,
            final @NotNull ChunkSecuritySettings securitySettings) throws IOException, ApackException {
        final SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ);
        try {
            return open(channel, chunkProcessor, securitySettings);
        } catch (final Exception e) {
            channel.close();
            throw e;
        }
    }

    /**
     * Opens an APACK file from a seekable channel.
     *
     * <p>This factory method allows reading from any seekable data source, not just
     * file system files. The channel must be positioned at the start of the APACK
     * data (position 0) and must support both reading and seeking.</p>
     *
     * <p><strong>Note:</strong> The reader takes ownership of the channel. It will
     * be closed when the reader is closed. Do not use the channel directly after
     * passing it to this method.</p>
     *
     * @param channel the seekable byte channel; must not be {@code null}
     * @return a new reader instance
     * @throws IOException if an I/O error occurs during reading
     * @throws ApackException if the file format is invalid or unsupported
     * @throws NullPointerException if {@code channel} is {@code null}
     */
    public static @NotNull AetherPackReader open(final @NotNull SeekableByteChannel channel) throws IOException, ApackException {
        return open(channel, ChunkProcessor.passThrough());
    }

    /**
     * Opens an APACK file from a seekable channel with a chunk processor.
     *
     * <p>This factory method accepts both a custom data source and a custom
     * processing pipeline. The channel must be positioned at position 0
     * and must support reading and seeking.</p>
     *
     * <p><strong>Note:</strong> The reader takes ownership of the channel. It will
     * be closed when the reader is closed.</p>
     *
     * @param channel        the seekable byte channel; must not be {@code null}
     * @param chunkProcessor the chunk processor for decompression/decryption; must not be {@code null}
     * @return a new reader instance
     * @throws IOException if an I/O error occurs during reading
     * @throws ApackException if the file format is invalid or unsupported
     * @throws FormatException if the checksum algorithm is unknown
     * @throws NullPointerException if {@code channel} or {@code chunkProcessor} is {@code null}
     */
    public static @NotNull AetherPackReader open(
            final @NotNull SeekableByteChannel channel,
            final @NotNull ChunkProcessor chunkProcessor) throws IOException, ApackException {
        return open(channel, chunkProcessor, ChunkSecuritySettings.DEFAULT);
    }

    /**
     * Opens an APACK file from a seekable channel with a chunk processor and custom security settings.
     *
     * <p>This is the most flexible factory method, accepting a custom data source,
     * processing pipeline, and security settings. The channel must be positioned at
     * position 0 and must support reading and seeking.</p>
     *
     * <p>The opening process performs the following steps:</p>
     * <ol>
     *   <li>Read and validate the file header</li>
     *   <li>Resolve the checksum provider from the header's algorithm ID</li>
     *   <li>If random access is enabled, read the trailer and table of contents</li>
     *   <li>Load all entry headers and build lookup indexes</li>
     * </ol>
     *
     * <p><strong>Note:</strong> The reader takes ownership of the channel. It will
     * be closed when the reader is closed.</p>
     *
     * @param channel          the seekable byte channel; must not be {@code null}
     * @param chunkProcessor   the chunk processor for decompression/decryption; must not be {@code null}
     * @param securitySettings the security settings for chunk validation; must not be {@code null}
     * @return a new reader instance
     * @throws IOException if an I/O error occurs during reading
     * @throws ApackException if the file format is invalid or unsupported
     * @throws FormatException if the checksum algorithm is unknown
     * @throws NullPointerException if any parameter is {@code null}
     *
     * @see ChunkSecuritySettings
     * @see ChunkSecuritySettings#builder()
     */
    public static @NotNull AetherPackReader open(
            final @NotNull SeekableByteChannel channel,
            final @NotNull ChunkProcessor chunkProcessor,
            final @NotNull ChunkSecuritySettings securitySettings) throws IOException, ApackException {
        // Read file header
        channel.position(0);
        final BinaryReader headerReader = new BinaryReader(Channels.newInputStream(channel));
        final FileHeader fileHeader = HeaderIO.readFileHeader(headerReader);

        // Get checksum provider
        final ChecksumProvider checksumProvider = ChecksumRegistry.getById(fileHeader.checksumAlgorithm())
                .orElseThrow(() -> new FormatException("Unknown checksum algorithm: " + fileHeader.checksumAlgorithm()));

        // Read encryption block if encrypted AND an encryption block is present
        // (EncryptionBlock is optional - older API allowed direct key encryption without EncryptionBlock)
        EncryptionBlock encryptionBlock = null;
        if (fileHeader.isEncrypted()) {
            // Peek at next 4 bytes to check for ENCR magic
            final long currentPos = channel.position();
            final byte[] peekBytes = new byte[4];
            final int bytesRead = Channels.newInputStream(channel).read(peekBytes);
            // Always seek back first to restore position
            channel.position(currentPos);
            if (bytesRead == 4 && java.util.Arrays.equals(peekBytes, FormatConstants.ENCRYPTION_MAGIC)) {
                // ENCR magic found - read the full encryption block
                final BinaryReader encReader = new BinaryReader(Channels.newInputStream(channel));
                encryptionBlock = HeaderIO.readEncryptionBlock(encReader);
            }
            // If no ENCR magic, leave encryptionBlock as null (direct key encryption)
            // Position is restored, so entry reading will work correctly
        }

        // Read entries
        final List<PackEntry> entries = new ArrayList<>();

        // Validate trailer offset if random access is enabled
        final long fileSize = channel.size();
        final long trailerOffset = fileHeader.trailerOffset();

        if (fileHeader.hasRandomAccess()) {
            // Reject negative trailer offsets (corruption/attack)
            if (trailerOffset < 0) {
                throw new FormatException("Invalid negative trailer offset: " + trailerOffset);
            }

            if (trailerOffset > 0) {
                // Validate trailer offset against file size to detect corruption/truncation
                if (trailerOffset < FormatConstants.FILE_HEADER_SIZE) {
                    throw new FormatException("Trailer offset points into file header: " + trailerOffset);
                }
                if (trailerOffset >= fileSize) {
                    throw new FormatException("Trailer offset beyond file end: " +
                            trailerOffset + " >= " + fileSize);
                }
                // trailerOffset points to TOC entries start, need space for TOC + trailer header
                // Layout: [TOC entries: entryCount * TOC_ENTRY_SIZE][Trailer header: TRAILER_HEADER_SIZE]
                final long minTrailerSize = (fileHeader.entryCount() * FormatConstants.TOC_ENTRY_SIZE)
                        + FormatConstants.TRAILER_HEADER_SIZE;
                if (trailerOffset + minTrailerSize > fileSize) {
                    throw new FormatException("File truncated: not enough space for TOC and trailer at offset " +
                            trailerOffset + " (need " + minTrailerSize + " bytes, file size: " + fileSize + ")");
                }
            }
        }

        if (fileHeader.hasRandomAccess() && trailerOffset > 0) {
            // Read TOC from trailer
            channel.position(fileHeader.trailerOffset());
            final BinaryReader trailerReader = new BinaryReader(Channels.newInputStream(channel));
            final List<TocEntry> tocEntries = TrailerIO.readTocEntries(trailerReader, (int) fileHeader.entryCount());

            // Load entry headers from TOC
            for (final TocEntry toc : tocEntries) {
                // Validate stored size against remaining file space
                final long storedSize = toc.storedSize();
                final long entryOffset = toc.entryOffset();
                if (storedSize < 0) {
                    throw new FormatException("Invalid negative stored size: " + storedSize);
                }
                if (entryOffset < FormatConstants.FILE_HEADER_SIZE) {
                    throw new FormatException("Entry offset points into file header: " + entryOffset);
                }
                if (storedSize > (fileSize - entryOffset)) {
                    throw new FormatException("Entry stored size exceeds remaining file: " +
                            storedSize + " > " + (fileSize - entryOffset));
                }

                channel.position(entryOffset);
                final BinaryReader entryReader = new BinaryReader(Channels.newInputStream(channel));
                final EntryHeader rawHeader = HeaderIO.readEntryHeader(entryReader);

                // Merge sizes from TOC (entry header has 0 because it's written before data)
                final EntryHeader entryHeader = EntryHeader.builder()
                        .headerVersion(rawHeader.headerVersion())
                        .flags(rawHeader.flags())
                        .entryId(rawHeader.entryId())
                        .originalSize(toc.originalSize())
                        .storedSize(toc.storedSize())
                        .chunkCount(rawHeader.chunkCount())
                        .compressionId(rawHeader.compressionId())
                        .encryptionId(rawHeader.encryptionId())
                        .headerChecksum(rawHeader.headerChecksum())
                        .name(rawHeader.name())
                        .mimeType(rawHeader.mimeType())
                        .attributes(rawHeader.attributes())
                        .build();

                entries.add(new PackEntry(entryHeader, toc.entryOffset()));
            }
        } else {
            // Sequential read of entries
            channel.position(FormatConstants.FILE_HEADER_SIZE);
            long currentOffset = FormatConstants.FILE_HEADER_SIZE;

            for (long i = 0; i < fileHeader.entryCount(); i++) {
                final BinaryReader entryReader = new BinaryReader(Channels.newInputStream(channel));
                final EntryHeader entryHeader = HeaderIO.readEntryHeader(entryReader);

                // Validate stored size against remaining file space
                final long storedSize = entryHeader.storedSize();
                if (storedSize < 0) {
                    throw new FormatException("Invalid negative stored size: " + storedSize);
                }
                if (storedSize > (fileSize - currentOffset)) {
                    throw new FormatException("Entry stored size exceeds remaining file: " +
                            storedSize + " > " + (fileSize - currentOffset));
                }

                entries.add(new PackEntry(entryHeader, currentOffset));

                // Skip entry data to next entry
                // This is a simplification; in practice we'd need to track the exact position
                currentOffset = channel.position();
            }
        }

        return new AetherPackReader(channel, fileHeader, checksumProvider, chunkProcessor, securitySettings, encryptionBlock, entries);
    }

    /**
     * Returns the file header of this archive.
     *
     * <p>The file header contains format metadata including version information,
     * chunk size, entry count, and various flags. This can be used to inspect
     * archive properties without reading entry data.</p>
     *
     * @return the file header; never {@code null}
     *
     * @see FileHeader
     */
    public @NotNull FileHeader getFileHeader() {
        return this.fileHeader;
    }

    /**
     * Returns the security settings used for chunk validation.
     *
     * <p>These settings control limits for decompression bomb protection,
     * maximum chunk size, and encryption overhead validation.</p>
     *
     * @return the security settings; never {@code null}
     *
     * @see ChunkSecuritySettings
     */
    public @NotNull ChunkSecuritySettings getSecuritySettings() {
        return this.securitySettings;
    }

    /**
     * Returns the encryption block containing key derivation metadata.
     *
     * <p>The encryption block is present only in encrypted archives and contains
     * the salt, wrapped key, and KDF parameters needed to derive the decryption
     * key from a password.</p>
     *
     * @return the encryption block, or {@code null} if the archive is not encrypted
     *
     * @see EncryptionBlock
     * @see FileHeader#isEncrypted()
     */
    public @Nullable EncryptionBlock getEncryptionBlock() {
        return this.encryptionBlock;
    }

    /**
     * Returns the number of entries in this archive.
     *
     * <p>This is an O(1) operation as the count is cached during opening.</p>
     *
     * @return the entry count, always {@code >= 0}
     */
    public int getEntryCount() {
        return this.entries.size();
    }

    /**
     * Returns an unmodifiable list of all entries.
     *
     * <p>The entries are returned in the order they appear in the archive.
     * The returned list is immutable; attempts to modify it will throw
     * {@link UnsupportedOperationException}.</p>
     *
     * @return unmodifiable list of entries; never {@code null}
     */
    public @NotNull List<? extends Entry> getEntries() {
        return this.entries;
    }

    /**
     * Returns an entry by its unique ID.
     *
     * <p>Entry IDs are assigned during archive creation and are unique within
     * an archive. This is an O(1) operation using hash-based lookup.</p>
     *
     * @param id the entry ID
     * @return the entry with the specified ID
     * @throws EntryNotFoundException if no entry with the given ID exists
     *
     * @see Entry#getId()
     */
    public @NotNull Entry getEntry(final long id) throws EntryNotFoundException {
        final PackEntry entry = this.entriesById.get(id);
        if (entry == null) {
            throw new EntryNotFoundException(id);
        }
        return entry;
    }

    /**
     * Returns an entry by name.
     *
     * <p>Entry names are the paths within the archive (e.g., "data/config.json").
     * Names are case-sensitive and must match exactly.</p>
     *
     * <p>This operation uses XXH3 hash-based lookup for O(1) average case
     * performance, with fallback to linear search within hash buckets to
     * handle potential collisions.</p>
     *
     * @param name the entry name (path within the archive); must not be {@code null}
     * @return an {@link Optional} containing the entry if found, or empty if not found
     * @throws NullPointerException if {@code name} is {@code null}
     *
     * @see #hasEntry(String)
     * @see #readAllBytes(String)
     */
    public @NotNull Optional<Entry> getEntry(final @NotNull String name) {
        final int nameHash = computeNameHash(name);
        final List<PackEntry> candidates = this.entriesByNameHash.get(nameHash);

        if (candidates == null) {
            return Optional.empty();
        }

        // Check for exact match (handles hash collisions)
        return candidates.stream()
                .filter(e -> e.getName().equals(name))
                .map(e -> (Entry) e)
                .findFirst();
    }

    /**
     * Checks if an entry with the given name exists.
     *
     * <p>This is a convenience method equivalent to
     * {@code getEntry(name).isPresent()}.</p>
     *
     * @param name the entry name to check; must not be {@code null}
     * @return {@code true} if an entry with the given name exists, {@code false} otherwise
     * @throws NullPointerException if {@code name} is {@code null}
     *
     * @see #getEntry(String)
     */
    public boolean hasEntry(final @NotNull String name) {
        return getEntry(name).isPresent();
    }

    /**
     * Returns an input stream for reading an entry's data.
     *
     * <p>The returned stream reads the raw chunk data and applies any configured
     * processing (decompression, decryption) automatically. The stream should
     * be closed after use, but closing it does not close this reader.</p>
     *
     * <p><strong>Important:</strong> Due to the single underlying channel, only
     * one entry can be read at a time. Starting to read a new entry will invalidate
     * any previously returned input stream.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * try (InputStream input = reader.getInputStream(entry)) {
     *     byte[] data = input.readAllBytes();
     *     // Process data...
     * }
     * }</pre>
     *
     * @param entry the entry to read; must be from this reader
     * @return an input stream for reading the entry's decompressed/decrypted data
     * @throws IOException if an I/O error occurs or the reader is closed
     * @throws IllegalArgumentException if the entry is not from this reader
     * @throws IllegalStateException if this reader has been closed
     *
     * @see #readAllBytes(Entry)
     */
    public @NotNull InputStream getInputStream(final @NotNull Entry entry) throws IOException {
        ensureOpen();

        if (!(entry instanceof PackEntry packEntry)) {
            throw new IllegalArgumentException("Entry must be from this reader");
        }

        final long entryOffset = packEntry.getOffset();

        // Seek to entry header start
        this.channel.position(entryOffset);

        // Read the entry header to determine its exact size
        // We use a NonClosingInputStream to prevent closing the channel
        final BinaryReader headerReader = new BinaryReader(
                new NonClosingInputStream(Channels.newInputStream(this.channel)));
        try {
            HeaderIO.readEntryHeader(headerReader);
        } catch (final FormatException e) {
            throw new IOException("Failed to read entry header", e);
        }

        // Get the exact header size from the bytes read by BinaryReader
        final long headerSize = headerReader.getBytesRead();

        // Explicitly set channel position to data start
        // This is necessary because Channels.newInputStream() may not sync position correctly
        this.channel.position(entryOffset + headerSize);

        // Create the ChunkedInputStream for reading the actual data
        // Use NonClosingInputStream to prevent closing the channel when the ChunkedInputStream is closed
        return new ChunkedInputStream(
                new NonClosingInputStream(Channels.newInputStream(this.channel)),
                this.checksumProvider,
                this.chunkProcessor,
                true,
                null,
                this.securitySettings
        );
    }

    /**
     * Reads all data from an entry into a byte array.
     *
     * <p>This convenience method reads the entire entry into memory. For large entries,
     * consider using {@link #getInputStream(Entry)} and processing the data in chunks
     * to avoid memory issues.</p>
     *
     * <p>The data is automatically decompressed and decrypted according to the
     * reader's configured {@link ChunkProcessor}.</p>
     *
     * @param entry the entry to read; must be from this reader
     * @return the complete entry data as a byte array
     * @throws IOException if an I/O error occurs during reading
     * @throws IllegalArgumentException if the entry is not from this reader
     * @throws IllegalStateException if this reader has been closed
     * @throws OutOfMemoryError if the entry is too large to fit in memory
     *
     * @see #getInputStream(Entry)
     */
    public byte @NotNull [] readAllBytes(final @NotNull Entry entry) throws IOException {
        try (InputStream input = getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    /**
     * Reads all data from an entry by name.
     *
     * <p>This convenience method combines entry lookup and data reading in one call.
     * It is equivalent to:</p>
     * <pre>{@code
     * Entry entry = reader.getEntry(name).orElseThrow(() -> new EntryNotFoundException(name));
     * byte[] data = reader.readAllBytes(entry);
     * }</pre>
     *
     * @param name the entry name (path within the archive); must not be {@code null}
     * @return the complete entry data as a byte array
     * @throws IOException if an I/O error occurs during reading
     * @throws EntryNotFoundException if no entry with the given name exists
     * @throws IllegalStateException if this reader has been closed
     * @throws OutOfMemoryError if the entry is too large to fit in memory
     *
     * @see #getEntry(String)
     * @see #readAllBytes(Entry)
     */
    public byte @NotNull [] readAllBytes(final @NotNull String name) throws IOException, EntryNotFoundException {
        final Entry entry = getEntry(name)
                .orElseThrow(() -> new EntryNotFoundException(name));
        return readAllBytes(entry);
    }

    /**
     * Returns an iterator over all entries in this archive.
     *
     * <p>The iterator returns entries in the order they appear in the archive.
     * The iterator does not support the {@code remove()} operation.</p>
     *
     * <p>This method enables the use of enhanced for-loops:</p>
     * <pre>{@code
     * for (Entry entry : reader) {
     *     System.out.println(entry.getName());
     * }
     * }</pre>
     *
     * @return an iterator over entries; never {@code null}
     */
    @Override
    public @NotNull Iterator<Entry> iterator() {
        return new Iterator<>() {
            private int index = 0;

            /**
             * Checks if there are more entries to iterate over.
             *
             * @return {@code true} if more entries exist, {@code false} otherwise
             */
            @Override
            public boolean hasNext() {
                return this.index < AetherPackReader.this.entries.size();
            }

            /**
             * Returns the next entry in the iteration.
             *
             * @return the next entry
             * @throws NoSuchElementException if no more entries exist
             */
            @Override
            public Entry next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return AetherPackReader.this.entries.get(this.index++);
            }
        };
    }

    /**
     * Returns a sequential stream of all entries.
     *
     * <p>The stream provides a functional interface for processing entries:</p>
     * <pre>{@code
     * reader.stream()
     *     .filter(e -> e.getName().endsWith(".json"))
     *     .forEach(e -> System.out.println(e.getName()));
     * }</pre>
     *
     * <p>The returned stream is sequential (not parallel) because the underlying
     * channel does not support concurrent access.</p>
     *
     * @return a sequential stream of entries; never {@code null}
     */
    public @NotNull Stream<Entry> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * Closes this reader and releases the underlying file channel.
     *
     * <p>After closing, any attempt to read entries will throw an
     * {@link IOException}. This method is idempotent; calling it multiple
     * times has no effect after the first call.</p>
     *
     * <p>Any input streams previously obtained from {@link #getInputStream(Entry)}
     * will become invalid after closing.</p>
     *
     * @throws IOException if an I/O error occurs while closing the channel
     */
    @Override
    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            this.channel.close();
        }
    }

    /**
     * Ensures this reader is still open.
     *
     * @throws IOException if this reader has been closed
     */
    private void ensureOpen() throws IOException {
        if (this.closed) {
            throw new IOException("Reader is closed");
        }
    }

    /**
     * Computes the XXH3 hash of an entry name for lookup indexing.
     *
     * <p>This hash is used for O(1) average-case entry lookup by name.
     * The name is encoded using {@link FormatConstants#STRING_CHARSET} before hashing.</p>
     *
     * @param name the entry name to hash
     * @return the 32-bit XXH3 hash value
     */
    private static int computeNameHash(final @NotNull String name) {
        return de.splatgames.aether.pack.core.checksum.XxHash3Checksum.hash32(
                name.getBytes(FormatConstants.STRING_CHARSET));
    }

    /**
     * An input stream wrapper that does not close the underlying stream.
     *
     * <p>This wrapper is used to prevent the {@link ChunkedInputStream} from closing
     * the underlying channel when it is closed. This allows multiple entries to be
     * read from the same archive without reopening it.</p>
     */
    private static final class NonClosingInputStream extends InputStream {

        /** The underlying input stream (not closed by this wrapper). */
        private final @NotNull InputStream delegate;

        /**
         * Creates a new non-closing input stream wrapping the specified delegate.
         *
         * @param delegate the input stream to wrap
         */
        NonClosingInputStream(final @NotNull InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return this.delegate.read();
        }

        @Override
        public int read(final byte @NotNull [] b, final int off, final int len) throws IOException {
            return this.delegate.read(b, off, len);
        }

        @Override
        public int available() throws IOException {
            return this.delegate.available();
        }

        @Override
        public long skip(final long n) throws IOException {
            return this.delegate.skip(n);
        }

        /**
         * Does nothing - intentionally does not close the underlying stream.
         */
        @Override
        public void close() {
            // Intentionally empty - do not close the underlying stream
        }
    }

}
