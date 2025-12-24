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

import de.splatgames.aether.pack.core.checksum.XxHash3Checksum;
import de.splatgames.aether.pack.core.entry.EntryMetadata;
import de.splatgames.aether.pack.core.format.*;
import de.splatgames.aether.pack.core.io.BinaryWriter;
import de.splatgames.aether.pack.core.io.ChunkProcessor;
import de.splatgames.aether.pack.core.io.ChunkedOutputStream;
import de.splatgames.aether.pack.core.io.HeaderIO;
import de.splatgames.aether.pack.core.io.TrailerIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Writer for creating APACK archive files.
 *
 * <p>This class provides the primary API for creating new APACK archives. It supports
 * adding entries from various sources (streams, files, byte arrays) with optional
 * compression and encryption. The writer handles all low-level format details including
 * chunked data storage, header/trailer management, and table-of-contents generation.</p>
 *
 * <h2>Basic Usage</h2>
 * <p>The simplest way to create an archive is using the try-with-resources pattern:</p>
 * <pre>{@code
 * try (AetherPackWriter writer = AetherPackWriter.create(Path.of("archive.apack"))) {
 *     writer.addEntry("file.txt", inputStream);
 *     writer.addEntry("data.bin", Path.of("/path/to/data.bin"));
 * }
 * }</pre>
 *
 * <h2>With Compression</h2>
 * <p>To enable compression, configure the writer with an appropriate
 * {@link ApackConfiguration}:</p>
 * <pre>{@code
 * ApackConfiguration config = ApackConfiguration.builder()
 *     .compression(CompressionRegistry.zstd(), 3)  // ZSTD level 3
 *     .build();
 *
 * try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
 *     writer.addEntry("document.pdf", pdfStream);
 * }
 * }</pre>
 *
 * <h2>With Encryption</h2>
 * <p>For encrypted archives, provide both an encryption provider and key:</p>
 * <pre>{@code
 * EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
 * SecretKey key = aes.generateKey();
 *
 * ApackConfiguration config = ApackConfiguration.builder()
 *     .encryption(aes, key)
 *     .build();
 *
 * try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
 *     writer.addEntry("secrets.dat", secretData);
 * }
 * }</pre>
 *
 * <h2>File Header Updates</h2>
 * <p>When writing to a file path (as opposed to a generic {@link OutputStream}),
 * the writer will seek back and update the file header with the correct entry count
 * and trailer offset after closing. This enables random access reading of the
 * archive. When writing to a non-seekable stream, these values remain as placeholders
 * and the archive can only be read sequentially.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is <strong>not thread-safe</strong>. Concurrent calls to
 * {@link #addEntry} from multiple threads may result in corrupted output.
 * External synchronization is required for multi-threaded access.</p>
 *
 * <h2>Resource Management</h2>
 * <p>This class implements {@link Closeable} and must be closed after use to ensure
 * the trailer is written and all resources are released. Failure to close the writer
 * will result in an incomplete or corrupted archive file.</p>
 *
 * @see AetherPackReader
 * @see ApackConfiguration
 * @see EntryMetadata
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class AetherPackWriter implements Closeable {

    /** Wrapper stream that counts bytes written for offset tracking. */
    private final @NotNull CountingOutputStream countingOutput;

    /** Binary writer for serializing format structures. */
    private final @NotNull BinaryWriter writer;

    /** Configuration controlling compression, encryption, and format options. */
    private final @NotNull ApackConfiguration config;

    /** Accumulated table-of-contents entries for the trailer. */
    private final @NotNull List<TocEntry> tocEntries;

    /** Optional file path for header update after closing (null for stream mode). */
    private final @Nullable Path filePath;

    /** Whether the file header has been written. */
    private boolean headerWritten;

    /** Counter for auto-assigning unique entry IDs. */
    private long entryIdCounter;

    /** Running total of uncompressed bytes across all entries. */
    private long totalOriginalSize;

    /** Running total of stored bytes across all entries. */
    private long totalStoredSize;

    /** File offset where the trailer begins. */
    private long trailerOffset;

    /** Whether this writer has been closed. */
    private boolean closed;

    /**
     * Private constructor for creating a writer instance.
     *
     * <p>This constructor initializes the writer state and wraps the output stream
     * with necessary adapters. Use the static factory methods instead of calling
     * this constructor directly.</p>
     *
     * @param output   the output stream to write to
     * @param config   the configuration for the archive
     * @param filePath optional file path for header updates, or {@code null} for stream-only mode
     *
     * @see #create(OutputStream)
     * @see #create(Path)
     */
    private AetherPackWriter(
            final @NotNull OutputStream output,
            final @NotNull ApackConfiguration config,
            final @Nullable Path filePath) {

        this.countingOutput = new CountingOutputStream(output);
        this.writer = new BinaryWriter(this.countingOutput);
        this.config = config;
        this.tocEntries = new ArrayList<>();
        this.filePath = filePath;
        this.headerWritten = false;
        this.entryIdCounter = 1;
        this.totalOriginalSize = 0;
        this.totalStoredSize = 0;
        this.trailerOffset = 0;
        this.closed = false;
    }

    /**
     * Creates a new writer with default configuration.
     *
     * <p>This factory method creates a writer that writes to the specified output stream
     * using the default configuration ({@link ApackConfiguration#DEFAULT}). The default
     * configuration uses:</p>
     * <ul>
     *   <li>256 KB chunk size</li>
     *   <li>XXH3-64 checksums</li>
     *   <li>No compression</li>
     *   <li>No encryption</li>
     *   <li>Random access enabled</li>
     * </ul>
     *
     * <p><strong>Note:</strong> When writing to a generic output stream, the file header
     * cannot be updated with the final entry count and trailer offset. Use
     * {@link #create(Path)} for full random-access support.</p>
     *
     * @param output the output stream to write to; must not be {@code null}
     * @return a new writer instance
     * @throws NullPointerException if {@code output} is {@code null}
     */
    public static @NotNull AetherPackWriter create(final @NotNull OutputStream output) {
        return create(output, ApackConfiguration.DEFAULT);
    }

    /**
     * Creates a new writer with the specified configuration.
     *
     * <p>This factory method allows full control over the archive format and processing
     * options through the provided configuration. The configuration determines compression,
     * encryption, chunk size, and other format parameters.</p>
     *
     * <p><strong>Note:</strong> When writing to a generic output stream, the file header
     * cannot be updated with the final entry count and trailer offset. Use
     * {@link #create(Path, ApackConfiguration)} for full random-access support.</p>
     *
     * @param output the output stream to write to; must not be {@code null}
     * @param config the configuration specifying format options; must not be {@code null}
     * @return a new writer instance
     * @throws NullPointerException if {@code output} or {@code config} is {@code null}
     *
     * @see ApackConfiguration#builder()
     */
    public static @NotNull AetherPackWriter create(
            final @NotNull OutputStream output,
            final @NotNull ApackConfiguration config) {
        return new AetherPackWriter(output, config, null);
    }

    /**
     * Creates a new writer for the specified file path.
     *
     * <p>This factory method creates a writer that writes directly to a file using
     * the default configuration. Writing to a file path enables the writer to seek
     * back and update the file header after closing, which is required for random
     * access reading.</p>
     *
     * <p>If the file already exists, it will be overwritten.</p>
     *
     * @param path the file path to write to; must not be {@code null}
     * @return a new writer instance
     * @throws IOException if the file cannot be created or opened for writing
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws SecurityException if a security manager denies write access
     */
    public static @NotNull AetherPackWriter create(final @NotNull Path path) throws IOException {
        return create(path, ApackConfiguration.DEFAULT);
    }

    /**
     * Creates a new writer for the specified file path with configuration.
     *
     * <p>This is the recommended factory method for creating archives with full
     * feature support. Writing to a file path enables:</p>
     * <ul>
     *   <li>Automatic header updates with final entry count and trailer offset</li>
     *   <li>Random access reading of the resulting archive</li>
     *   <li>Accurate file size in the trailer</li>
     * </ul>
     *
     * <p>If the file already exists, it will be overwritten.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * ApackConfiguration config = ApackConfiguration.builder()
     *     .compression(CompressionRegistry.zstd(), 6)
     *     .encryption(EncryptionRegistry.aes256Gcm(), secretKey)
     *     .chunkSize(128 * 1024)  // 128 KB chunks
     *     .build();
     *
     * try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
     *     writer.addEntry("data.bin", inputStream);
     * }
     * }</pre>
     *
     * @param path   the file path to write to; must not be {@code null}
     * @param config the configuration specifying format options; must not be {@code null}
     * @return a new writer instance
     * @throws IOException if the file cannot be created or opened for writing
     * @throws NullPointerException if {@code path} or {@code config} is {@code null}
     * @throws SecurityException if a security manager denies write access
     *
     * @see ApackConfiguration#builder()
     */
    public static @NotNull AetherPackWriter create(
            final @NotNull Path path,
            final @NotNull ApackConfiguration config) throws IOException {
        return new AetherPackWriter(Files.newOutputStream(path), config, path);
    }

    /**
     * Returns the number of entries written so far.
     *
     * <p>This count reflects only entries that have been fully written to the archive.
     * An entry is counted after its data has been completely processed and its
     * table-of-contents entry has been recorded.</p>
     *
     * @return the number of entries written, always {@code >= 0}
     */
    public int getEntryCount() {
        return this.tocEntries.size();
    }

    /**
     * Adds an entry with a simple name.
     *
     * <p>This convenience method creates an entry with the specified name and default
     * metadata (no MIME type, no custom attributes). The entry data is read from the
     * provided input stream until EOF.</p>
     *
     * <p>The input stream is read completely but is <strong>not closed</strong> by this
     * method. The caller is responsible for closing the input stream.</p>
     *
     * @param name  the entry name (path within the archive); must not be {@code null}
     * @param input the input stream containing the entry data; must not be {@code null}
     * @throws IOException if an I/O error occurs during reading or writing
     * @throws IllegalStateException if this writer has been closed
     * @throws NullPointerException if {@code name} or {@code input} is {@code null}
     *
     * @see #addEntry(EntryMetadata, InputStream)
     * @see #addEntry(String, Path)
     */
    public void addEntry(final @NotNull String name, final @NotNull InputStream input) throws IOException {
        addEntry(EntryMetadata.of(name), input);
    }

    /**
     * Adds an entry with full metadata control.
     *
     * <p>This method provides complete control over entry metadata including name,
     * MIME type, custom attributes, and processing hints. The entry data is read
     * from the provided input stream until EOF.</p>
     *
     * <p>The data is processed according to the writer's configuration:</p>
     * <ul>
     *   <li>Split into chunks of the configured size</li>
     *   <li>Optionally compressed (if compression is enabled)</li>
     *   <li>Optionally encrypted (if encryption is enabled)</li>
     *   <li>Checksummed for integrity verification</li>
     * </ul>
     *
     * <p>The input stream is read completely but is <strong>not closed</strong> by this
     * method. The caller is responsible for closing the input stream.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * EntryMetadata metadata = EntryMetadata.builder()
     *     .name("document.pdf")
     *     .mimeType("application/pdf")
     *     .attribute("author", "John Doe")
     *     .attribute("created", "2024-01-15")
     *     .build();
     *
     * writer.addEntry(metadata, pdfInputStream);
     * }</pre>
     *
     * @param metadata the entry metadata; must not be {@code null}
     * @param input    the input stream containing the entry data; must not be {@code null}
     * @throws IOException if an I/O error occurs during reading or writing
     * @throws IllegalStateException if this writer has been closed
     * @throws NullPointerException if {@code metadata} or {@code input} is {@code null}
     *
     * @see EntryMetadata#builder()
     */
    public void addEntry(final @NotNull EntryMetadata metadata, final @NotNull InputStream input) throws IOException {
        ensureOpen();
        ensureHeaderWritten();

        // Assign entry ID if not set
        final long entryId = metadata.getId() != 0 ? metadata.getId() : this.entryIdCounter++;

        // Record entry offset
        final long entryOffset = this.countingOutput.getBytesWritten();

        // Write entry header placeholder (will be updated)
        final long headerOffset = this.countingOutput.getBytesWritten();

        // Create a temporary entry header with placeholders
        // Determine compression/encryption from config
        final boolean useCompression = this.config.isCompressionEnabled();
        final boolean useEncryption = this.config.isEncryptionEnabled();
        final int compressionId = useCompression && this.config.compressionProvider() != null
                ? this.config.compressionProvider().getNumericId()
                : metadata.getCompressionId();
        final int encryptionId = useEncryption && this.config.encryptionProvider() != null
                ? this.config.encryptionProvider().getNumericId()
                : metadata.getEncryptionId();

        EntryHeader entryHeader = EntryHeader.builder()
                .entryId(entryId)
                .name(metadata.getName())
                .mimeType(metadata.getMimeType())
                .attributes(metadata.getAttributes())
                .compressionId(compressionId)
                .compressed(useCompression)
                .encryptionId(encryptionId)
                .encrypted(useEncryption)
                .hasEcc(metadata.hasEcc())
                .build();

        // Write entry header
        HeaderIO.writeEntryHeader(this.writer, entryHeader);
        this.writer.flush();

        // Track data offset
        final long dataOffset = this.countingOutput.getBytesWritten();

        // Write chunks with compression/encryption
        int chunkCount = 0;
        long originalSize = 0;
        long storedSize = 0;

        // Create chunk processor from config
        final ChunkProcessor chunkProcessor = this.config.createChunkProcessor();

        try (ChunkedOutputStream chunkedOutput = new ChunkedOutputStream(
                new NonClosingOutputStream(this.countingOutput),
                this.config.chunkSize(),
                this.config.checksumProvider(),
                chunkProcessor,
                (index, header) -> {
                    // Chunk callback - could be used for progress tracking
                })) {

            final byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                chunkedOutput.write(buffer, 0, read);
            }
            chunkedOutput.finish();

            chunkCount = chunkedOutput.getChunkCount();
            originalSize = chunkedOutput.getTotalBytesWritten();
        }

        storedSize = this.countingOutput.getBytesWritten() - dataOffset;

        // Update metadata
        metadata.setOriginalSize(originalSize);
        metadata.setStoredSize(storedSize);
        metadata.setChunkCount(chunkCount);

        // Update totals
        this.totalOriginalSize += originalSize;
        this.totalStoredSize += storedSize;

        // Create TOC entry
        final int nameHash = XxHash3Checksum.hash32(metadata.getName().getBytes(FormatConstants.STRING_CHARSET));
        final TocEntry tocEntry = TocEntry.builder()
                .entryId(entryId)
                .entryOffset(entryOffset)
                .originalSize(originalSize)
                .storedSize(storedSize)
                .nameHash(nameHash)
                .entryChecksum(0) // TODO: compute
                .build();

        this.tocEntries.add(tocEntry);
    }

    /**
     * Adds an entry from a file.
     *
     * <p>This convenience method reads the entire contents of the specified file
     * and adds it as an entry with the given name. The file is opened, read completely,
     * and closed automatically.</p>
     *
     * <p>The entry name can differ from the actual file name, allowing for path
     * remapping within the archive:</p>
     * <pre>{@code
     * // Store as "config/settings.json" regardless of original path
     * writer.addEntry("config/settings.json", Path.of("/etc/myapp/settings.json"));
     * }</pre>
     *
     * @param name the entry name (path within the archive); must not be {@code null}
     * @param path the file path to read from; must not be {@code null}
     * @throws IOException if the file cannot be read or an I/O error occurs during writing
     * @throws IllegalStateException if this writer has been closed
     * @throws NullPointerException if {@code name} or {@code path} is {@code null}
     * @throws java.nio.file.NoSuchFileException if the file does not exist
     *
     * @see #addEntry(String, InputStream)
     */
    public void addEntry(final @NotNull String name, final @NotNull Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            addEntry(name, input);
        }
    }

    /**
     * Adds an entry from a byte array.
     *
     * <p>This convenience method adds the contents of the byte array as an entry.
     * This is useful for small, in-memory data that doesn't require streaming.</p>
     *
     * <p>The byte array is not copied; however, since the data is written immediately,
     * modifications to the array after this method returns will not affect the archive.</p>
     *
     * @param name the entry name (path within the archive); must not be {@code null}
     * @param data the entry data; must not be {@code null}
     * @throws IOException if an I/O error occurs during writing
     * @throws IllegalStateException if this writer has been closed
     * @throws NullPointerException if {@code name} or {@code data} is {@code null}
     *
     * @see #addEntry(String, InputStream)
     */
    public void addEntry(final @NotNull String name, final byte @NotNull [] data) throws IOException {
        addEntry(name, new java.io.ByteArrayInputStream(data));
    }

    /**
     * Closes this writer, finishing the archive.
     *
     * <p>This method performs the following operations in order:</p>
     * <ol>
     *   <li>Writes the file header (if not already written)</li>
     *   <li>Writes the trailer containing the table of contents</li>
     *   <li>Flushes all buffered data</li>
     *   <li>Updates the file header with entry count and trailer offset
     *       (only when writing to a file path)</li>
     *   <li>Closes the underlying output stream</li>
     * </ol>
     *
     * <p>This method is idempotent; calling it multiple times has no effect after
     * the first call. It is safe to call even if no entries were added (creates
     * an empty archive).</p>
     *
     * <p><strong>Important:</strong> Failure to call this method (or use
     * try-with-resources) will result in an incomplete archive that cannot be read.</p>
     *
     * @throws IOException if an I/O error occurs during finalization
     */
    @Override
    public void close() throws IOException {
        if (!this.closed) {
            try {
                ensureHeaderWritten();
                writeTrailer();
                this.writer.flush();
            } finally {
                this.closed = true;
                this.writer.close();
            }
            // Update file header with correct values (only works for file-based writers)
            updateFileHeader();
        }
    }

    /**
     * Ensures this writer is still open.
     *
     * @throws IOException if this writer has been closed
     */
    private void ensureOpen() throws IOException {
        if (this.closed) {
            throw new IOException("Writer is closed");
        }
    }

    /**
     * Ensures the file header has been written.
     *
     * <p>This method is called before writing the first entry to guarantee
     * the header is present. Subsequent calls have no effect.</p>
     *
     * @throws IOException if an I/O error occurs during header writing
     */
    private void ensureHeaderWritten() throws IOException {
        if (!this.headerWritten) {
            writeHeader();
            this.headerWritten = true;
        }
    }

    /**
     * Writes the file header to the output stream.
     *
     * <p>The header contains format identification, version information, and
     * configuration settings. Entry count and trailer offset are initially set
     * to zero and updated later if writing to a seekable file.</p>
     *
     * @throws IOException if an I/O error occurs during writing
     */
    private void writeHeader() throws IOException {
        int modeFlags = 0;
        if (this.config.streamMode()) {
            modeFlags |= FormatConstants.FLAG_STREAM_MODE;
        }
        if (this.config.enableRandomAccess()) {
            modeFlags |= FormatConstants.FLAG_RANDOM_ACCESS;
        }
        if (this.config.isEncryptionEnabled()) {
            modeFlags |= FormatConstants.FLAG_ENCRYPTED;
        }

        final FileHeader header = FileHeader.builder()
                .modeFlags(modeFlags)
                .checksumAlgorithm(this.config.checksumProvider().getNumericId())
                .chunkSize(this.config.chunkSize())
                .entryCount(0) // Will be updated in trailer
                .trailerOffset(0) // Will be updated
                .build();

        HeaderIO.writeFileHeader(this.writer, header);

        // Write encryption block if encryption is enabled
        if (this.config.isEncryptionEnabled() && this.config.encryptionBlock() != null) {
            HeaderIO.writeEncryptionBlock(this.writer, this.config.encryptionBlock());
        }

        this.writer.flush();
    }

    /**
     * Writes the trailer containing the table of contents.
     *
     * <p>The trailer is written at the end of the archive and contains metadata
     * about all entries for efficient random access. The trailer offset is
     * recorded for later header updates.</p>
     *
     * @throws IOException if an I/O error occurs during writing
     */
    private void writeTrailer() throws IOException {
        this.trailerOffset = this.countingOutput.getBytesWritten();

        // Build trailer
        final Trailer trailer = Trailer.builder()
                .tocEntries(this.tocEntries)
                .entryCount(this.tocEntries.size())
                .totalOriginalSize(this.totalOriginalSize)
                .totalStoredSize(this.totalStoredSize)
                .fileSize(0) // Will be set after writing
                .build();

        TrailerIO.writeTrailer(this.writer, trailer);
        this.writer.flush();
    }

    /**
     * Updates the file header with correct entry count and trailer offset.
     * This is only possible when writing to a file path.
     */
    private void updateFileHeader() throws IOException {
        if (this.filePath == null) {
            return;
        }

        try (SeekableByteChannel channel = Files.newByteChannel(
                this.filePath, StandardOpenOption.WRITE)) {

            // Seek to entry count position in file header
            // Header layout: magic(6) + version(6) + compat(2) + flags(1) + checksumAlgo(1) +
            //                chunkSize(4) + headerCRC(4) = 24 bytes
            //                entryCount at offset 24 (8 bytes)
            //                trailerOffset at offset 32 (8 bytes)
            channel.position(24);

            final ByteBuffer buffer = ByteBuffer.allocate(16);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(this.tocEntries.size());
            buffer.putLong(this.trailerOffset);
            buffer.flip();

            channel.write(buffer);
        }
    }

    /**
     * Output stream wrapper that counts the total number of bytes written.
     *
     * <p>This internal utility class wraps an output stream and maintains a running
     * count of all bytes written through it. This is essential for tracking file
     * offsets during archive creation.</p>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    private static final class CountingOutputStream extends OutputStream {

        /** The underlying output stream to delegate writes to. */
        private final @NotNull OutputStream delegate;

        /** Total number of bytes written through this stream. */
        private long bytesWritten;

        /**
         * Creates a new counting output stream wrapping the specified delegate.
         *
         * @param delegate the output stream to wrap
         */
        CountingOutputStream(final @NotNull OutputStream delegate) {
            this.delegate = delegate;
            this.bytesWritten = 0;
        }

        /**
         * Returns the total number of bytes written through this stream.
         *
         * @return the byte count, always {@code >= 0}
         */
        long getBytesWritten() {
            return this.bytesWritten;
        }

        /**
         * Writes a single byte and increments the byte counter.
         *
         * @param b the byte to write
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void write(final int b) throws IOException {
            this.delegate.write(b);
            this.bytesWritten++;
        }

        /**
         * Writes a portion of a byte array and updates the byte counter.
         *
         * @param b   the byte array
         * @param off the start offset in the array
         * @param len the number of bytes to write
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void write(final byte @NotNull [] b, final int off, final int len) throws IOException {
            this.delegate.write(b, off, len);
            this.bytesWritten += len;
        }

        /**
         * Flushes the delegate stream.
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void flush() throws IOException {
            this.delegate.flush();
        }

        /**
         * Closes the delegate stream.
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void close() throws IOException {
            this.delegate.close();
        }
    }

    /**
     * Output stream wrapper that prevents closing of the delegate stream.
     *
     * <p>This internal utility class wraps an output stream and intercepts calls
     * to {@link #close()}, preventing the delegate stream from being closed.
     * This is used when passing streams to components that close their streams
     * on completion, but we need the underlying stream to remain open.</p>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    private static final class NonClosingOutputStream extends OutputStream {

        /** The underlying output stream (not closed by this wrapper). */
        private final @NotNull OutputStream delegate;

        /**
         * Creates a new non-closing output stream wrapping the specified delegate.
         *
         * @param delegate the output stream to wrap
         */
        NonClosingOutputStream(final @NotNull OutputStream delegate) {
            this.delegate = delegate;
        }

        /**
         * Writes a single byte to the delegate stream.
         *
         * @param b the byte to write
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void write(final int b) throws IOException {
            this.delegate.write(b);
        }

        /**
         * Writes a portion of a byte array to the delegate stream.
         *
         * @param b   the byte array
         * @param off the start offset in the array
         * @param len the number of bytes to write
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void write(final byte @NotNull [] b, final int off, final int len) throws IOException {
            this.delegate.write(b, off, len);
        }

        /**
         * Flushes the delegate stream.
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void flush() throws IOException {
            this.delegate.flush();
        }

        /**
         * Does nothing. The delegate stream remains open.
         */
        @Override
        public void close() {
            // Don't close delegate
        }
    }

}
