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

import de.splatgames.aether.pack.core.format.ChunkHeader;
import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.core.spi.ChecksumProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * An input stream that reads data from chunks with headers.
 *
 * <p>This stream reads chunk headers and their associated data, performing
 * decryption and decompression as needed. It validates chunk magic numbers
 * and optionally verifies data integrity using checksums.</p>
 *
 * <h2>Chunk Processing</h2>
 * <p>For each chunk, the stream:</p>
 * <ol>
 *   <li>Reads and validates the chunk header</li>
 *   <li>Reads the stored chunk data</li>
 *   <li>Decrypts data if encrypted</li>
 *   <li>Decompresses data if compressed</li>
 *   <li>Validates checksum (if enabled)</li>
 * </ol>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ChunkProcessor processor = ChunkProcessor.builder()
 *     .compression(zstdProvider)
 *     .build();
 *
 * try (ChunkedInputStream in = new ChunkedInputStream(
 *         fileInput, checksumProvider, processor, true, null)) {
 *     byte[] buffer = new byte[8192];
 *     int read;
 *     while ((read = in.read(buffer)) != -1) {
 *         output.write(buffer, 0, read);
 *     }
 * }
 *
 * System.out.println("Read " + in.getChunkCount() + " chunks");
 * System.out.println("Total bytes: " + in.getTotalBytesRead());
 * }</pre>
 *
 * <h2>Checksum Validation</h2>
 * <p>When checksum validation is enabled, the stream verifies each chunk's
 * integrity by computing the checksum on the decompressed/decrypted data
 * and comparing it with the stored checksum. An {@link IOException} is
 * thrown if a mismatch is detected.</p>
 *
 * <h2>Callbacks</h2>
 * <p>An optional callback can be provided to receive chunk headers as they
 * are read. This is useful for progress reporting or metadata collection.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is <strong>not thread-safe</strong>. External synchronization
 * is required if instances are shared between threads.</p>
 *
 * @see ChunkedOutputStream
 * @see ChunkProcessor
 * @see ChunkHeader
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ChunkedInputStream extends InputStream {

    /** The binary reader for reading chunk headers and data. */
    private final @NotNull BinaryReader reader;

    /** The checksum provider for verifying chunk data integrity. */
    private final @NotNull ChecksumProvider checksumProvider;

    /** The processor for applying decryption/decompression to chunks. */
    private final @NotNull ChunkProcessor chunkProcessor;

    /** Whether to verify checksums after processing each chunk. */
    private final boolean validateChecksums;

    /** Optional callback invoked after each chunk is read. */
    private final @Nullable Consumer<ChunkHeader> chunkCallback;

    /** Security settings for chunk validation (decompression bomb protection, size limits). */
    private final @NotNull ChunkSecuritySettings securitySettings;

    /** The current chunk's decompressed/decrypted data buffer. */
    private byte @Nullable [] currentChunk;

    /** Current read position within the current chunk buffer. */
    private int chunkPosition;

    /** Zero-based index of the next chunk to be read. */
    private int chunkIndex;

    /** Flag indicating whether the last chunk has been read. */
    private boolean lastChunkRead;

    /** Flag indicating whether this stream has been closed. */
    private boolean closed;

    /** Total decompressed bytes read from this stream. */
    private long totalBytesRead;

    /**
     * Creates a new chunked input stream with default settings.
     *
     * <p>This constructor creates a chunked input stream that uses a pass-through
     * chunk processor (no decompression or decryption), validates checksums,
     * and has no chunk callback configured. It's suitable for reading uncompressed,
     * unencrypted chunk data where integrity verification is desired.</p>
     *
     * <p>The stream will automatically read chunk headers, validate them,
     * read chunk data, and verify checksums as data is consumed through
     * the standard {@link InputStream} methods.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>No decompression or decryption (pass-through processor)</li>
     *   <li>Checksum validation enabled</li>
     *   <li>No progress callback</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * try (ChunkedInputStream in = new ChunkedInputStream(
     *         fileInput, crc32Provider)) {
     *     byte[] buffer = new byte[8192];
     *     int read;
     *     while ((read = in.read(buffer)) != -1) {
     *         output.write(buffer, 0, read);
     *     }
     * }
     * }</pre>
     *
     * @param input the underlying input stream from which chunks will be read;
     *              must not be {@code null}; the stream should be positioned at
     *              the start of the first chunk header; this stream will be
     *              closed when this chunked input stream is closed
     * @param checksumProvider the checksum algorithm provider used to verify
     *                         the integrity of each chunk's data; must not be
     *                         {@code null}; should match the provider used when
     *                         writing the chunks (typically CRC32 or XXH3-64)
     *
     * @see #ChunkedInputStream(InputStream, ChecksumProvider, ChunkProcessor, boolean, Consumer)
     */
    public ChunkedInputStream(
            final @NotNull InputStream input,
            final @NotNull ChecksumProvider checksumProvider) {
        this(input, checksumProvider, ChunkProcessor.passThrough(), true, null, ChunkSecuritySettings.DEFAULT);
    }

    /**
     * Creates a new chunked input stream with checksum validation and callback options.
     *
     * <p>This constructor creates a chunked input stream with a pass-through
     * chunk processor (no decompression or decryption) but allows configuring
     * whether checksums are validated and provides an optional callback for
     * progress tracking or metadata collection.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>When enabled, each chunk's integrity is verified by:</p>
     * <ol>
     *   <li>Computing the checksum on the decompressed/decrypted data</li>
     *   <li>Comparing it with the checksum stored in the chunk header</li>
     *   <li>Throwing an {@link IOException} if they don't match</li>
     * </ol>
     * <p>Disabling checksum validation improves performance but sacrifices
     * data integrity verification.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The callback receives each {@link ChunkHeader} as chunks are read,
     * which is useful for:</p>
     * <ul>
     *   <li>Progress reporting (chunk index / total chunks)</li>
     *   <li>Collecting statistics (original sizes, stored sizes)</li>
     *   <li>Logging or debugging chunk processing</li>
     * </ul>
     *
     * @param input the underlying input stream from which chunks will be read;
     *              must not be {@code null}; the stream should be positioned at
     *              the start of the first chunk header
     * @param checksumProvider the checksum algorithm provider used for integrity
     *                         verification; must not be {@code null}; the algorithm
     *                         must match what was used when writing the chunks
     * @param validateChecksums {@code true} to verify each chunk's checksum after
     *                          reading and processing; {@code false} to skip checksum
     *                          verification for improved performance at the cost of
     *                          not detecting data corruption
     * @param chunkCallback an optional callback that receives the {@link ChunkHeader}
     *                      after each chunk is read and processed; may be {@code null}
     *                      if no callback is needed; invoked synchronously during
     *                      read operations
     *
     * @see #ChunkedInputStream(InputStream, ChecksumProvider, ChunkProcessor, boolean, Consumer)
     */
    public ChunkedInputStream(
            final @NotNull InputStream input,
            final @NotNull ChecksumProvider checksumProvider,
            final boolean validateChecksums,
            final @Nullable Consumer<ChunkHeader> chunkCallback) {
        this(input, checksumProvider, ChunkProcessor.passThrough(), validateChecksums, chunkCallback, ChunkSecuritySettings.DEFAULT);
    }

    /**
     * Creates a new chunked input stream with full configuration.
     *
     * <p>This is the primary constructor that allows complete control over the
     * chunk reading behavior, including decompression, decryption, checksum
     * validation, and progress callbacks. All other constructors delegate to
     * this one.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>For each chunk, the following processing steps are applied:</p>
     * <ol>
     *   <li>Read and validate the chunk header (magic number, indices)</li>
     *   <li>Read the stored (compressed/encrypted) chunk data</li>
     *   <li>Decrypt the data if the chunk is marked as encrypted</li>
     *   <li>Decompress the data if the chunk is marked as compressed</li>
     *   <li>Verify the checksum if validation is enabled</li>
     *   <li>Invoke the callback with the chunk header</li>
     *   <li>Buffer the processed data for reading</li>
     * </ol>
     *
     * <p><strong>\:</strong></p>
     * <p>The chunk processor must be configured to match the transformations
     * applied when writing:</p>
     * <ul>
     *   <li>Same compression provider (or none if chunks aren't compressed)</li>
     *   <li>Same encryption provider and key (or none if chunks aren't encrypted)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <p>Various errors may occur during reading:</p>
     * <ul>
     *   <li>Invalid chunk magic: {@link IOException} with descriptive message</li>
     *   <li>Unexpected chunk index: {@link IOException} with expected vs actual</li>
     *   <li>Checksum mismatch: {@link IOException} with hex values</li>
     *   <li>Decryption failure: {@link IOException} wrapping security exception</li>
     *   <li>Decompression failure: {@link IOException} from provider</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * ChunkProcessor processor = ChunkProcessor.builder()
     *     .compression(ZstdCompressionProvider.getInstance())
     *     .encryption(AesGcmEncryptionProvider.getInstance(), secretKey)
     *     .build();
     *
     * AtomicInteger chunksRead = new AtomicInteger();
     * try (ChunkedInputStream in = new ChunkedInputStream(
     *         fileInput, crc32Provider, processor, true,
     *         header -> chunksRead.incrementAndGet())) {
     *
     *     byte[] data = in.readAllBytes();
     *     System.out.printf("Read %d chunks, %d bytes%n",
     *         chunksRead.get(), data.length);
     * }
     * }</pre>
     *
     * @param input the underlying input stream from which chunks will be read;
     *              must not be {@code null}; the stream should be positioned at
     *              the start of the first chunk header; this stream will be
     *              closed when this chunked input stream is closed
     * @param checksumProvider the checksum algorithm provider used for integrity
     *                         verification; must not be {@code null}; the algorithm
     *                         must match what was used when writing the chunks
     *                         (typically CRC32 or XXH3-64)
     * @param chunkProcessor the processor that applies decompression and/or decryption
     *                       transformations to each chunk; must not be {@code null};
     *                       use {@link ChunkProcessor#passThrough()} for no transformations;
     *                       must be configured with the same providers and keys used
     *                       when writing the chunks
     * @param validateChecksums {@code true} to verify each chunk's checksum after
     *                          reading and processing, throwing an {@link IOException}
     *                          if a mismatch is detected; {@code false} to skip
     *                          checksum verification for improved performance
     * @param chunkCallback an optional callback that receives the {@link ChunkHeader}
     *                      after each chunk is successfully read and processed;
     *                      may be {@code null} if no callback is needed; useful for
     *                      progress tracking or collecting chunk statistics
     *
     * @see ChunkProcessor
     * @see ChunkHeader
     * @see ChunkedOutputStream
     */
    public ChunkedInputStream(
            final @NotNull InputStream input,
            final @NotNull ChecksumProvider checksumProvider,
            final @NotNull ChunkProcessor chunkProcessor,
            final boolean validateChecksums,
            final @Nullable Consumer<ChunkHeader> chunkCallback) {
        this(input, checksumProvider, chunkProcessor, validateChecksums, chunkCallback, ChunkSecuritySettings.DEFAULT);
    }

    /**
     * Creates a new chunked input stream with full configuration and custom security settings.
     *
     * <p>This constructor extends the standard configuration with explicit security settings
     * for chunk validation. Use this when you need to override the default security limits
     * for specific use cases (e.g., processing archives with unusually large chunks).</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The security settings control:</p>
     * <ul>
     *   <li><b>Maximum chunk size:</b> Upper bound for uncompressed chunk data</li>
     *   <li><b>Maximum compression ratio:</b> Protection against decompression bombs</li>
     *   <li><b>Maximum encryption overhead:</b> Bounds on encryption-related size increase</li>
     * </ul>
     *
     * <p>Even when customizing these limits, they cannot exceed the absolute hard-caps
     * defined in {@link ChunkSecuritySettings}.</p>
     *
     * @param input            the underlying input stream from which chunks will be read;
     *                         must not be {@code null}
     * @param checksumProvider the checksum algorithm provider for integrity verification;
     *                         must not be {@code null}
     * @param chunkProcessor   the processor for decompression/decryption transformations;
     *                         must not be {@code null}
     * @param validateChecksums {@code true} to verify each chunk's checksum
     * @param chunkCallback    an optional callback for chunk header notification;
     *                         may be {@code null}
     * @param securitySettings the security settings for chunk validation;
     *                         must not be {@code null}
     *
     * @see ChunkSecuritySettings
     * @see ChunkSecuritySettings#DEFAULT
     */
    public ChunkedInputStream(
            final @NotNull InputStream input,
            final @NotNull ChecksumProvider checksumProvider,
            final @NotNull ChunkProcessor chunkProcessor,
            final boolean validateChecksums,
            final @Nullable Consumer<ChunkHeader> chunkCallback,
            final @NotNull ChunkSecuritySettings securitySettings) {

        this.reader = new BinaryReader(input);
        this.checksumProvider = checksumProvider;
        this.chunkProcessor = chunkProcessor;
        this.validateChecksums = validateChecksums;
        this.chunkCallback = chunkCallback;
        this.securitySettings = securitySettings;
        this.currentChunk = null;
        this.chunkPosition = 0;
        this.chunkIndex = 0;
        this.lastChunkRead = false;
        this.closed = false;
        this.totalBytesRead = 0;
    }

    /**
     * Returns the number of chunks that have been read so far.
     *
     * <p>This method returns the count of complete chunks that have been read
     * and processed from the underlying input stream. This includes the current
     * chunk being read from, if any.</p>
     *
     * <p>The chunk count is incremented after each chunk header is successfully
     * read and the chunk data is processed (decompressed/decrypted). It is
     * useful for:</p>
     * <ul>
     *   <li>Progress tracking when the total chunk count is known</li>
     *   <li>Verifying that all expected chunks were read</li>
     *   <li>Debugging and logging chunk processing</li>
     * </ul>
     *
     * @return the number of chunks that have been successfully read and
     *         processed so far; starts at 0 before any chunks are read;
     *         this value increases by 1 each time a new chunk is loaded
     *
     * @see #getTotalBytesRead()
     * @see #isFinished()
     */
    public int getChunkCount() {
        return this.chunkIndex;
    }

    /**
     * Returns the total number of uncompressed bytes read from this stream.
     *
     * <p>This method returns the cumulative count of all bytes that have been
     * returned through the {@link #read} methods. This represents the
     * "original size" of the data after decompression and decryption.</p>
     *
     * <p>Note that this counts only bytes that have been consumed by the
     * caller through read operations. Bytes that have been loaded into
     * the internal buffer but not yet read are not included until they
     * are actually consumed.</p>
     *
     * <p>This value is useful for:</p>
     * <ul>
     *   <li>Progress tracking based on output data size</li>
     *   <li>Verifying that the expected amount of data was read</li>
     *   <li>Calculating effective decompression ratios</li>
     * </ul>
     *
     * @return the total number of bytes that have been read from this stream
     *         after decompression and decryption processing; this value
     *         accumulates across all read operations
     *
     * @see #getChunkCount()
     * @see #isFinished()
     */
    public long getTotalBytesRead() {
        return this.totalBytesRead;
    }

    /**
     * Checks whether all chunks have been read from this stream.
     *
     * <p>This method returns {@code true} when the last chunk (marked with
     * the {@link FormatConstants#CHUNK_FLAG_LAST} flag) has been read and
     * all its data has been consumed. It is useful for determining when
     * the stream has been fully processed.</p>
     *
     * <p>The stream is considered finished when:</p>
     * <ol>
     *   <li>A chunk with the "last" flag has been read, AND</li>
     *   <li>All bytes from that chunk have been consumed through read operations</li>
     * </ol>
     *
     * <p>Note that after this method returns {@code true}, subsequent read
     * operations will return -1 (end of stream).</p>
     *
     * @return {@code true} if the last chunk has been read and all its data
     *         has been consumed; {@code false} if there may be more data
     *         available, either in the current chunk buffer or in subsequent
     *         chunks not yet read
     *
     * @see #getChunkCount()
     * @see #getTotalBytesRead()
     */
    public boolean isFinished() {
        return this.lastChunkRead && (this.currentChunk == null ||
                this.chunkPosition >= this.currentChunk.length);
    }

    /**
     * Reads a single byte from this chunked input stream.
     *
     * <p>This method reads from the current chunk's decompressed/decrypted data
     * buffer. When the current chunk is exhausted, it automatically loads the
     * next chunk from the underlying stream (reading its header, processing
     * its data, and optionally verifying its checksum).</p>
     *
     * <p>This method is relatively inefficient for reading large amounts of
     * data due to the per-byte method call overhead. For better performance,
     * use {@link #read(byte[], int, int)} with a buffer.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>When the current chunk buffer is exhausted:</p>
     * <ol>
     *   <li>Read the next chunk header from the stream</li>
     *   <li>Validate the chunk magic and index</li>
     *   <li>Read the stored chunk data</li>
     *   <li>Decrypt and decompress as indicated by chunk flags</li>
     *   <li>Verify the checksum if validation is enabled</li>
     *   <li>Invoke the chunk callback if configured</li>
     * </ol>
     *
     * @return the byte read as an unsigned integer in the range 0-255,
     *         or {@code -1} if the end of the stream has been reached
     *         (i.e., all chunks have been read and all data consumed)
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, if chunk validation fails,
     *                     if decryption or decompression fails, if checksum
     *                     verification fails, or if this stream has been closed
     *
     * @see #read(byte[], int, int)
     */
    @Override
    public int read() throws IOException {
        ensureOpen();

        if (!ensureChunkAvailable()) {
            return -1;
        }

        final int b = this.currentChunk[this.chunkPosition++] & 0xFF;
        this.totalBytesRead++;
        return b;
    }

    /**
     * Reads up to {@code len} bytes from this chunked input stream into a byte array.
     *
     * <p>This method reads from the current chunk's decompressed/decrypted data
     * buffer, automatically loading additional chunks as needed to satisfy the
     * request. A single call may span multiple chunks when reading large amounts
     * of data.</p>
     *
     * <p>This is the recommended method for reading data, as it is more efficient
     * than calling {@link #read()} repeatedly.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>When the requested length exceeds the current chunk's remaining data:</p>
     * <ol>
     *   <li>Copy available data from the current chunk</li>
     *   <li>Load the next chunk (if not at end of stream)</li>
     *   <li>Continue copying until the request is satisfied or no more data</li>
     * </ol>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>Returns the actual number of bytes read (may be less than {@code len})</li>
     *   <li>Returns {@code -1} only if no bytes were read and end of stream reached</li>
     *   <li>A return value less than {@code len} is normal at end of stream</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * byte[] buffer = new byte[8192];
     * int bytesRead;
     * while ((bytesRead = chunkedIn.read(buffer, 0, buffer.length)) != -1) {
     *     output.write(buffer, 0, bytesRead);
     * }
     * }</pre>
     *
     * @param b the buffer into which the data is read; must not be {@code null};
     *          the buffer is filled starting at offset {@code off}
     * @param off the starting offset in the buffer at which data should be
     *            written; must be non-negative and less than {@code b.length}
     * @param len the maximum number of bytes to read; must be non-negative;
     *            if zero, no bytes are read and 0 is returned
     * @return the total number of bytes actually read into the buffer, or
     *         {@code -1} if the end of the stream has been reached and no
     *         bytes were read; the return value may be less than {@code len}
     *         when reading near the end of the stream
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, if chunk validation fails,
     *                     if decryption or decompression fails, if checksum
     *                     verification fails, or if this stream has been closed
     * @throws IndexOutOfBoundsException if {@code off} is negative,
     *                                   {@code len} is negative, or
     *                                   {@code off + len} exceeds {@code b.length}
     *
     * @see #read()
     * @see #available()
     */
    @Override
    public int read(final byte @NotNull [] b, final int off, final int len) throws IOException {
        ensureOpen();

        if (len == 0) {
            return 0;
        }

        if (!ensureChunkAvailable()) {
            return -1;
        }

        int totalRead = 0;
        int remaining = len;
        int offset = off;

        while (remaining > 0) {
            if (this.currentChunk == null || this.chunkPosition >= this.currentChunk.length) {
                if (!ensureChunkAvailable()) {
                    break;
                }
            }

            final int available = this.currentChunk.length - this.chunkPosition;
            final int toCopy = Math.min(remaining, available);

            System.arraycopy(this.currentChunk, this.chunkPosition, b, offset, toCopy);
            this.chunkPosition += toCopy;
            this.totalBytesRead += toCopy;
            offset += toCopy;
            remaining -= toCopy;
            totalRead += toCopy;
        }

        return totalRead > 0 ? totalRead : -1;
    }

    /**
     * Returns the number of bytes that can be read without blocking.
     *
     * <p>This method returns the number of bytes remaining in the current
     * chunk's decompressed/decrypted data buffer. It does <strong>not</strong>
     * account for additional chunks that may be available in the underlying
     * stream - only the currently buffered chunk data is considered.</p>
     *
     * <p>A return value of 0 does not necessarily mean the stream is exhausted;
     * it may simply mean that the current chunk has been consumed and reading
     * more data will trigger loading the next chunk (which may block).</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>Returns 0 if no chunk has been loaded yet</li>
     *   <li>Returns 0 if the current chunk has been fully consumed</li>
     *   <li>Does not trigger loading the next chunk</li>
     *   <li>Useful for non-blocking reads when data is already buffered</li>
     * </ul>
     *
     * @return the number of bytes that can be read from the current chunk
     *         buffer without blocking; this value is always non-negative;
     *         a return value of 0 means no data is currently buffered
     * @throws IOException if this stream has been closed
     *
     * @see #read(byte[], int, int)
     */
    @Override
    public int available() throws IOException {
        ensureOpen();
        if (this.currentChunk == null) {
            return 0;
        }
        return this.currentChunk.length - this.chunkPosition;
    }

    /**
     * Closes this chunked input stream and releases all associated resources.
     *
     * <p>This method closes the underlying binary reader (and consequently the
     * underlying input stream) and marks this stream as closed. Any subsequent
     * read operations will throw an {@link IOException}.</p>
     *
     * <p>If the stream is already closed, this method has no effect and returns
     * immediately without throwing an exception. This makes it safe to call
     * {@code close()} multiple times, which is useful in try-with-resources
     * statements and cleanup code.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>After closing:</p>
     * <ul>
     *   <li>The underlying input stream is closed</li>
     *   <li>Internal chunk buffer references may be released</li>
     *   <li>No further read operations can be performed</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * try (ChunkedInputStream in = new ChunkedInputStream(fileInput, checksumProvider)) {
     *     byte[] data = in.readAllBytes();
     *     // Process data...
     * } // Stream automatically closed here
     * }</pre>
     *
     * @throws IOException if an I/O error occurs while closing the underlying
     *                     input stream; the stream is still marked as closed
     *                     even if this exception is thrown
     */
    @Override
    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            this.reader.close();
        }
    }

    /**
     * Ensures a chunk is available for reading.
     *
     * <p>If the current chunk is exhausted, this method loads the next chunk
     * from the stream. If all chunks have been read, it returns {@code false}.</p>
     *
     * @return {@code true} if data is available, {@code false} if EOF
     * @throws IOException if an I/O error occurs while reading the next chunk
     */
    private boolean ensureChunkAvailable() throws IOException {
        if (this.currentChunk != null && this.chunkPosition < this.currentChunk.length) {
            return true;
        }

        if (this.lastChunkRead) {
            return false;
        }

        return readNextChunk();
    }

    /**
     * Reads the next chunk from the stream.
     *
     * <p>This method reads the chunk header, validates it, reads the stored data,
     * applies decryption and decompression as needed, and optionally validates
     * the checksum.</p>
     *
     * @return {@code true} if a chunk was read, {@code false} if EOF
     * @throws IOException if an I/O error occurs or validation fails
     */
    private boolean readNextChunk() throws IOException {
        try {
            // Read and validate chunk header
            final ChunkHeader header = readChunkHeader();

            // Validate chunk index
            if (header.chunkIndex() != this.chunkIndex) {
                throw new IOException("Unexpected chunk index: expected " + this.chunkIndex +
                        ", got " + header.chunkIndex());
            }

            // Read stored chunk data
            final byte[] storedData = this.reader.readBytes(header.storedSize());

            // Process chunk (decrypt then decompress)
            final boolean compressed = header.isCompressed();
            final boolean encrypted = header.isEncrypted();

            this.currentChunk = this.chunkProcessor.processForRead(
                    storedData, header.originalSize(), compressed, encrypted);
            this.chunkPosition = 0;

            // Validate checksum on original (decompressed/decrypted) data
            if (this.validateChecksums) {
                final int computed = (int) this.checksumProvider.compute(
                        this.currentChunk, 0, this.currentChunk.length);
                if (computed != header.checksum()) {
                    throw new IOException(String.format(
                            "Chunk %d checksum mismatch (expected: 0x%08X, actual: 0x%08X)",
                            this.chunkIndex, header.checksum(), computed));
                }
            }

            // Check if this is the last chunk
            this.lastChunkRead = header.isLast();

            // Callback
            if (this.chunkCallback != null) {
                this.chunkCallback.accept(header);
            }

            this.chunkIndex++;
            return true;

        } catch (final EOFException e) {
            // EOF is only acceptable at the very beginning of the stream (before any chunks)
            // Once we've started reading chunks, EOF mid-stream indicates file truncation
            if (this.chunkIndex > 0) {
                throw new IOException("Unexpected EOF: file truncated after " +
                        this.chunkIndex + " chunks (expected more data)", e);
            }
            // EOF at the beginning is acceptable (empty stream)
            return false;
        }
    }

    /**
     * Reads a chunk header from the stream.
     *
     * <p>Validates the chunk magic number and parses all header fields.</p>
     *
     * @return the parsed chunk header
     * @throws IOException if an I/O error occurs or the magic is invalid
     */
    private @NotNull ChunkHeader readChunkHeader() throws IOException {
        // Read and validate magic
        final byte[] magic = this.reader.readBytes(4);
        if (!Arrays.equals(magic, FormatConstants.CHUNK_MAGIC)) {
            throw new IOException("Invalid chunk magic at chunk " + this.chunkIndex);
        }

        final int index = this.reader.readInt32();
        final int originalSize = this.reader.readInt32();
        final int storedSize = this.reader.readInt32();

        // Validate chunk sizes to prevent decompression bombs and OOM attacks
        final int maxChunkSize = this.securitySettings.maxChunkSize();
        if (originalSize < 0 || originalSize > maxChunkSize) {
            throw new IOException("Invalid chunk originalSize: " + originalSize +
                    " (must be 0-" + maxChunkSize + ")");
        }
        if (storedSize < 0 || storedSize > maxChunkSize) {
            throw new IOException("Invalid chunk storedSize: " + storedSize +
                    " (must be 0-" + maxChunkSize + ")");
        }

        final int checksum = this.reader.readInt32();
        final int flags = this.reader.readInt32();

        // Validate size relationship based on compression and encryption flags
        final boolean isCompressed = (flags & FormatConstants.CHUNK_FLAG_COMPRESSED) != 0;
        final boolean isEncrypted = (flags & FormatConstants.CHUNK_FLAG_ENCRYPTED) != 0;

        // Maximum allowed encryption overhead (IV + auth tag + padding)
        // AES-GCM: 12 (IV) + 16 (tag) = 28 bytes, ChaCha20-Poly1305: similar
        // Default: 1024 bytes for future algorithms or additional metadata
        final int maxEncryptionOverhead = this.securitySettings.maxEncryptionOverhead();

        // Size validation rules:
        // - Uncompressed, unencrypted: storedSize == originalSize
        // - Encrypted only: storedSize >= originalSize (with bounded overhead)
        // - Compressed only: storedSize <= originalSize (compression reduces size)
        // - Compressed AND encrypted: storedSize can vary (compression reduces, encryption adds)

        if (!isCompressed && !isEncrypted && originalSize != storedSize) {
            throw new IOException("Chunk size mismatch: uncompressed chunk has originalSize=" +
                    originalSize + " but storedSize=" + storedSize +
                    " (they must be equal for uncompressed, unencrypted data)");
        }

        // For encrypted-only data (no compression):
        // - storedSize must be >= originalSize (encryption may add overhead, or be equal for stream ciphers)
        // - storedSize must not exceed originalSize + MAX_ENCRYPTION_OVERHEAD
        if (isEncrypted && !isCompressed) {
            if (storedSize < originalSize) {
                throw new IOException("Suspicious chunk sizes: encrypted chunk has storedSize=" +
                        storedSize + " which is smaller than originalSize=" + originalSize +
                        " (encryption cannot reduce size)");
            }
            final long overhead = (long) storedSize - originalSize;
            if (overhead > maxEncryptionOverhead) {
                throw new IOException("Excessive encryption overhead: storedSize=" +
                        storedSize + ", originalSize=" + originalSize +
                        ", overhead=" + overhead + " bytes (max allowed: " + maxEncryptionOverhead + ")");
            }
        }

        // For compressed-only data (no encryption), storedSize should be <= originalSize
        if (isCompressed && !isEncrypted && originalSize < storedSize) {
            throw new IOException("Suspicious chunk sizes: compressed chunk has originalSize=" +
                    originalSize + " but storedSize=" + storedSize +
                    " (originalSize should be >= storedSize for compressed data)");
        }

        // Prevent decompression bombs with two layers of protection:
        // 1. Hard limit: originalSize must not exceed maxChunkSize (prevents large allocations)
        // 2. Ratio limit: compressed data shouldn't expand more than maxCompressionRatio
        if (isCompressed) {
            // Hard limit check (already validated above, but explicit for decompression context)
            if (originalSize > maxChunkSize) {
                throw new IOException("Potential decompression bomb: claimed originalSize=" +
                        originalSize + " exceeds maxChunkSize=" + maxChunkSize);
            }
            // Ratio limit check
            final long maxCompressionRatio = this.securitySettings.maxCompressionRatio();
            if (storedSize > 0 && originalSize > storedSize * maxCompressionRatio) {
                throw new IOException("Potential decompression bomb: originalSize=" +
                        originalSize + " is more than " + maxCompressionRatio + "x storedSize=" + storedSize);
            }
        }

        return ChunkHeader.builder()
                .chunkIndex(index)
                .originalSize(originalSize)
                .storedSize(storedSize)
                .checksum(checksum)
                .flags(flags)
                .build();
    }

    /**
     * Ensures that the stream is not closed.
     *
     * @throws IOException if the stream has been closed
     */
    private void ensureOpen() throws IOException {
        if (this.closed) {
            throw new IOException("Stream is closed");
        }
    }

}
