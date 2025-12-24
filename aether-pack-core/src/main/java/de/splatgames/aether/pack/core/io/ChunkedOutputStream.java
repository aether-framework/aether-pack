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

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.BiConsumer;

/**
 * An output stream that writes data in chunks with headers.
 *
 * <p>This stream buffers data up to the configured chunk size, then writes
 * a chunk header followed by the chunk data. It optionally applies compression
 * and encryption to each chunk before writing.</p>
 *
 * <h2>Chunk Structure</h2>
 * <p>For each chunk, the stream writes:</p>
 * <ol>
 *   <li>Chunk header ({@link FormatConstants#CHUNK_HEADER_SIZE} bytes)</li>
 *   <li>Processed chunk data (compressed and/or encrypted)</li>
 * </ol>
 *
 * <h2>Processing Pipeline</h2>
 * <p>When the buffer fills up or the stream is closed:</p>
 * <ol>
 *   <li>Compute checksum on original data</li>
 *   <li>Compress data if compression is enabled</li>
 *   <li>Encrypt data if encryption is enabled</li>
 *   <li>Write chunk header with metadata</li>
 *   <li>Write processed chunk data</li>
 * </ol>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ChunkProcessor processor = ChunkProcessor.builder()
 *     .compression(zstdProvider, 3)
 *     .build();
 *
 * try (ChunkedOutputStream out = new ChunkedOutputStream(
 *         fileOutput, 256 * 1024, checksumProvider, processor, null)) {
 *     out.write(data);
 * }
 *
 * System.out.println("Wrote " + out.getChunkCount() + " chunks");
 * System.out.println("Original size: " + out.getTotalBytesWritten());
 * System.out.println("Stored size: " + out.getTotalStoredBytes());
 * }</pre>
 *
 * <h2>Callbacks</h2>
 * <p>An optional callback can be provided to receive chunk headers after each
 * chunk is written. This is useful for building a Table of Contents.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is <strong>not thread-safe</strong>. External synchronization
 * is required if instances are shared between threads.</p>
 *
 * @see ChunkedInputStream
 * @see ChunkProcessor
 * @see ChunkHeader
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ChunkedOutputStream extends OutputStream {

    /** The binary writer for writing chunk headers and data. */
    private final @NotNull BinaryWriter writer;

    /** The maximum size of unprocessed data per chunk in bytes. */
    private final int chunkSize;

    /** The checksum provider for computing chunk data checksums. */
    private final @NotNull ChecksumProvider checksumProvider;

    /** The processor for applying compression/encryption to chunks. */
    private final @NotNull ChunkProcessor chunkProcessor;

    /** The buffer for accumulating data until a full chunk is ready. */
    private final byte @NotNull [] buffer;

    /** Optional callback invoked after each chunk is written. */
    private final @Nullable BiConsumer<Integer, ChunkHeader> chunkCallback;

    /** Current write position within the buffer. */
    private int bufferPosition;

    /** Zero-based index of the next chunk to be written. */
    private int chunkIndex;

    /** Total uncompressed bytes written to this stream. */
    private long totalBytesWritten;

    /** Total bytes written to output including headers after processing. */
    private long totalStoredBytes;

    /** Flag indicating whether this stream has been closed. */
    private boolean closed;

    /** Flag indicating whether the last chunk has been written (with isLast flag). */
    private boolean lastChunkWritten;

    /**
     * Creates a new chunked output stream with default settings.
     *
     * <p>This constructor creates a chunked output stream that uses a pass-through
     * chunk processor (no compression or encryption) and has no chunk callback
     * configured. It's suitable for scenarios where only chunking and checksum
     * computation are needed.</p>
     *
     * <p>The chunk size determines the maximum amount of data that will be
     * buffered before being written as a chunk. Each chunk is written with
     * a header containing metadata (index, sizes, checksum, flags) followed
     * by the chunk data.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The chunk size must be within the valid range defined by the format:</p>
     * <ul>
     *   <li>Minimum: {@link FormatConstants#MIN_CHUNK_SIZE} bytes</li>
     *   <li>Maximum: {@link FormatConstants#MAX_CHUNK_SIZE} bytes</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * try (ChunkedOutputStream out = new ChunkedOutputStream(
     *         fileOutput, 256 * 1024, crc32Provider)) {
     *     out.write(data);
     * }
     * }</pre>
     *
     * @param output the underlying output stream to which chunks will be written;
     *               must not be {@code null}; the stream should be positioned at
     *               the correct location for writing chunk data; this stream will
     *               be closed when this chunked output stream is closed
     * @param chunkSize the maximum size of each data chunk in bytes before
     *                  processing (compression/encryption); must be between
     *                  {@link FormatConstants#MIN_CHUNK_SIZE} and
     *                  {@link FormatConstants#MAX_CHUNK_SIZE} inclusive
     * @param checksumProvider the checksum algorithm provider used to compute
     *                         integrity checksums for each chunk's original data;
     *                         must not be {@code null}; typically a CRC32 or
     *                         XXH3-64 provider
     * @throws IllegalArgumentException if the chunk size is outside the valid
     *                                  range defined by format constants
     *
     * @see #ChunkedOutputStream(OutputStream, int, ChecksumProvider, ChunkProcessor, BiConsumer)
     * @see FormatConstants#MIN_CHUNK_SIZE
     * @see FormatConstants#MAX_CHUNK_SIZE
     */
    public ChunkedOutputStream(
            final @NotNull OutputStream output,
            final int chunkSize,
            final @NotNull ChecksumProvider checksumProvider) {
        this(output, chunkSize, checksumProvider, ChunkProcessor.passThrough(), null);
    }

    /**
     * Creates a new chunked output stream with a chunk callback.
     *
     * <p>This constructor creates a chunked output stream with a pass-through
     * chunk processor (no compression or encryption) but with a callback that
     * is invoked after each chunk is written. This is useful for building a
     * Table of Contents or tracking write progress.</p>
     *
     * <p>The callback receives the chunk index (0-based) and the {@link ChunkHeader}
     * containing all metadata about the written chunk. This information can be
     * used to build TOC entries for container-mode archives.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The callback is invoked:</p>
     * <ul>
     *   <li>After each chunk's header and data have been written to the stream</li>
     *   <li>In order of chunk indices (0, 1, 2, ...)</li>
     *   <li>Synchronously within the write or close operation</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * List<ChunkHeader> headers = new ArrayList<>();
     * try (ChunkedOutputStream out = new ChunkedOutputStream(
     *         fileOutput, 256 * 1024, crc32Provider,
     *         (index, header) -> headers.add(header))) {
     *     out.write(data);
     * }
     * // headers now contains all chunk metadata for TOC building
     * }</pre>
     *
     * @param output the underlying output stream to which chunks will be written;
     *               must not be {@code null}; the stream should be positioned at
     *               the correct location for writing chunk data; this stream will
     *               be closed when this chunked output stream is closed
     * @param chunkSize the maximum size of each data chunk in bytes before
     *                  processing; must be between {@link FormatConstants#MIN_CHUNK_SIZE}
     *                  and {@link FormatConstants#MAX_CHUNK_SIZE} inclusive
     * @param checksumProvider the checksum algorithm provider used to compute
     *                         integrity checksums for each chunk's original data;
     *                         must not be {@code null}; typically CRC32 or XXH3-64
     * @param chunkCallback an optional callback that receives the chunk index and
     *                      header after each chunk is written; may be {@code null}
     *                      if no callback is needed; useful for building TOC entries
     *                      or progress tracking
     * @throws IllegalArgumentException if the chunk size is outside the valid
     *                                  range defined by format constants
     *
     * @see #ChunkedOutputStream(OutputStream, int, ChecksumProvider, ChunkProcessor, BiConsumer)
     * @see ChunkHeader
     */
    public ChunkedOutputStream(
            final @NotNull OutputStream output,
            final int chunkSize,
            final @NotNull ChecksumProvider checksumProvider,
            final @Nullable BiConsumer<Integer, ChunkHeader> chunkCallback) {
        this(output, chunkSize, checksumProvider, ChunkProcessor.passThrough(), chunkCallback);
    }

    /**
     * Creates a new chunked output stream with full configuration.
     *
     * <p>This is the primary constructor that allows complete control over the
     * chunking behavior, including compression, encryption, checksums, and
     * progress callbacks. All other constructors delegate to this one.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>For each chunk, the following processing steps are applied:</p>
     * <ol>
     *   <li>Compute checksum on the original (unprocessed) data</li>
     *   <li>Apply compression if enabled in the chunk processor</li>
     *   <li>Apply encryption if enabled in the chunk processor</li>
     *   <li>Write the chunk header with metadata</li>
     *   <li>Write the processed chunk data</li>
     *   <li>Invoke the callback with the chunk information</li>
     * </ol>
     *
     * <p><strong>\:</strong></p>
     * <p>If compression is enabled but would increase the data size (common with
     * already-compressed or high-entropy data), the chunk is stored uncompressed
     * and marked accordingly in the chunk header flags.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>Each chunk written to the output stream consists of:</p>
     * <ul>
     *   <li>Chunk header ({@link FormatConstants#CHUNK_HEADER_SIZE} bytes)</li>
     *   <li>Processed chunk data (variable size)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * ChunkProcessor processor = ChunkProcessor.builder()
     *     .compression(ZstdCompressionProvider.getInstance(), 6)
     *     .encryption(AesGcmEncryptionProvider.getInstance(), secretKey)
     *     .build();
     *
     * List<ChunkHeader> headers = new ArrayList<>();
     * try (ChunkedOutputStream out = new ChunkedOutputStream(
     *         fileOutput, 256 * 1024, crc32Provider, processor,
     *         (index, header) -> headers.add(header))) {
     *     out.write(largeData);
     * }
     *
     * System.out.printf("Wrote %d chunks%n", out.getChunkCount());
     * System.out.printf("Compression ratio: %.1f%%%n",
     *     (double) out.getTotalStoredBytes() / out.getTotalBytesWritten() * 100);
     * }</pre>
     *
     * @param output the underlying output stream to which chunks will be written;
     *               must not be {@code null}; the stream should be positioned at
     *               the correct location for writing chunk data; this stream will
     *               be closed when this chunked output stream is closed
     * @param chunkSize the maximum size of each data chunk in bytes before
     *                  processing (compression/encryption); must be between
     *                  {@link FormatConstants#MIN_CHUNK_SIZE} and
     *                  {@link FormatConstants#MAX_CHUNK_SIZE} inclusive; larger
     *                  chunks typically provide better compression ratios but
     *                  require more memory
     * @param checksumProvider the checksum algorithm provider used to compute
     *                         integrity checksums for each chunk's original
     *                         (unprocessed) data; must not be {@code null};
     *                         the checksum is computed before compression and
     *                         stored in the chunk header for verification during reading
     * @param chunkProcessor the processor that applies compression and/or encryption
     *                       transformations to each chunk; must not be {@code null};
     *                       use {@link ChunkProcessor#passThrough()} for no transformations
     * @param chunkCallback an optional callback that receives the chunk index (0-based)
     *                      and the {@link ChunkHeader} after each chunk is written;
     *                      may be {@code null} if no callback is needed; useful for
     *                      building Table of Contents entries or progress tracking
     * @throws IllegalArgumentException if the chunk size is outside the valid
     *                                  range defined by format constants
     *
     * @see ChunkProcessor
     * @see ChunkHeader
     * @see FormatConstants#MIN_CHUNK_SIZE
     * @see FormatConstants#MAX_CHUNK_SIZE
     */
    public ChunkedOutputStream(
            final @NotNull OutputStream output,
            final int chunkSize,
            final @NotNull ChecksumProvider checksumProvider,
            final @NotNull ChunkProcessor chunkProcessor,
            final @Nullable BiConsumer<Integer, ChunkHeader> chunkCallback) {

        if (chunkSize < FormatConstants.MIN_CHUNK_SIZE || chunkSize > FormatConstants.MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "Chunk size must be between " + FormatConstants.MIN_CHUNK_SIZE +
                            " and " + FormatConstants.MAX_CHUNK_SIZE + ": " + chunkSize);
        }

        this.writer = new BinaryWriter(output);
        this.chunkSize = chunkSize;
        this.checksumProvider = checksumProvider;
        this.chunkProcessor = chunkProcessor;
        this.buffer = new byte[chunkSize];
        this.chunkCallback = chunkCallback;
        this.bufferPosition = 0;
        this.chunkIndex = 0;
        this.totalBytesWritten = 0;
        this.totalStoredBytes = 0;
        this.closed = false;
    }

    /**
     * Returns the number of chunks written so far.
     *
     * <p>This method returns the count of complete chunks that have been flushed
     * to the underlying output stream. It does not include any data currently
     * buffered that has not yet been written as a chunk.</p>
     *
     * <p>The chunk count is incremented after each successful chunk write,
     * including during {@link #close()} or {@link #finish()} operations when
     * the final partial chunk is flushed.</p>
     *
     * <p>This value is useful for:</p>
     * <ul>
     *   <li>Progress tracking during large file writes</li>
     *   <li>Populating the {@link de.splatgames.aether.pack.core.format.StreamTrailer#chunkCount()}</li>
     *   <li>Verifying that writing completed successfully</li>
     * </ul>
     *
     * @return the number of complete chunks that have been written to the output
     *         stream so far; starts at 0 before any chunks are written; this value
     *         increases by 1 each time the internal buffer is flushed as a chunk
     *
     * @see #getTotalBytesWritten()
     * @see #getTotalStoredBytes()
     */
    public int getChunkCount() {
        return this.chunkIndex;
    }

    /**
     * Returns the total number of uncompressed bytes written to this stream.
     *
     * <p>This method returns the cumulative count of all bytes that have been
     * passed to the {@link #write} methods, regardless of how they were
     * processed (compressed/encrypted) before being written to the output.</p>
     *
     * <p>This represents the "original size" of the data, which is the size
     * the data would have if it were extracted and fully decompressed. This
     * value is used for:</p>
     * <ul>
     *   <li>Calculating compression ratios by comparing to {@link #getTotalStoredBytes()}</li>
     *   <li>Progress tracking based on input data size</li>
     *   <li>Populating entry and trailer size fields</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * double ratio = (double) out.getTotalStoredBytes() / out.getTotalBytesWritten() * 100;
     * System.out.printf("Compressed to %.1f%% of original size%n", ratio);
     * }</pre>
     *
     * @return the total number of bytes that have been written to this stream
     *         before compression and encryption processing; this value
     *         accumulates across all write operations and includes both
     *         flushed and buffered data
     *
     * @see #getTotalStoredBytes()
     * @see #getChunkCount()
     */
    public long getTotalBytesWritten() {
        return this.totalBytesWritten;
    }

    /**
     * Returns the total number of stored bytes after compression and encryption.
     *
     * <p>This method returns the cumulative size of all chunk headers and
     * processed chunk data that have been written to the underlying output
     * stream. This represents the actual space used in the archive for the
     * data written to this stream.</p>
     *
     * <p>The stored size includes:</p>
     * <ul>
     *   <li>Chunk headers ({@link FormatConstants#CHUNK_HEADER_SIZE} bytes each)</li>
     *   <li>Processed chunk data (after compression and/or encryption)</li>
     * </ul>
     *
     * <p>Note that this value only includes data from chunks that have been
     * flushed. Any data currently buffered is not included until it is
     * written as a chunk.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>By comparing this value with {@link #getTotalBytesWritten()}, you can
     * calculate the effective compression ratio achieved:</p>
     * <pre>{@code
     * long originalSize = out.getTotalBytesWritten();
     * long storedSize = out.getTotalStoredBytes();
     * double savings = (1.0 - (double) storedSize / originalSize) * 100;
     * System.out.printf("Space savings: %.1f%%%n", savings);
     * }</pre>
     *
     * @return the total number of bytes written to the underlying output stream,
     *         including chunk headers and processed chunk data; this value is
     *         updated after each chunk is flushed and does not include data
     *         still buffered
     *
     * @see #getTotalBytesWritten()
     * @see #getChunkCount()
     */
    public long getTotalStoredBytes() {
        return this.totalStoredBytes;
    }

    /**
     * Writes a single byte to the chunked output stream.
     *
     * <p>The byte is added to an internal buffer. When the buffer reaches
     * the configured chunk size, it is automatically flushed as a complete
     * chunk (with header, checksum computation, and optional compression/
     * encryption).</p>
     *
     * <p>This method is relatively inefficient for writing large amounts of
     * data due to the per-byte method call overhead. For better performance,
     * use {@link #write(byte[], int, int)} with larger byte arrays.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>When the buffer reaches capacity, the following occurs:</p>
     * <ol>
     *   <li>Checksum is computed on the buffered data</li>
     *   <li>Compression/encryption is applied if configured</li>
     *   <li>Chunk header and processed data are written</li>
     *   <li>Chunk callback is invoked if configured</li>
     *   <li>Buffer is reset for the next chunk</li>
     * </ol>
     *
     * @param b the byte to write; only the lower 8 bits of the integer
     *          argument are written; the upper 24 bits are ignored
     * @throws IOException if an I/O error occurs while writing a chunk
     *                     to the underlying stream, or if this stream
     *                     has been closed
     *
     * @see #write(byte[], int, int)
     */
    @Override
    public void write(final int b) throws IOException {
        ensureOpen();
        this.buffer[this.bufferPosition++] = (byte) b;
        this.totalBytesWritten++;

        if (this.bufferPosition >= this.chunkSize) {
            flushChunk(false);
        }
    }

    /**
     * Writes a portion of a byte array to the chunked output stream.
     *
     * <p>The specified bytes are copied to an internal buffer. When the buffer
     * reaches the configured chunk size, it is automatically flushed as a
     * complete chunk. Large writes that exceed the buffer capacity will be
     * split across multiple chunks automatically.</p>
     *
     * <p>This is the recommended method for writing data, as it is more
     * efficient than calling {@link #write(int)} repeatedly.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>When writing data larger than the remaining buffer space:</p>
     * <ol>
     *   <li>Fill the current buffer to capacity</li>
     *   <li>Flush the buffer as a complete chunk</li>
     *   <li>Repeat until all data is written</li>
     *   <li>Any remaining data stays buffered for the next write</li>
     * </ol>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * byte[] largeData = readFile("large-file.bin");
     * // This may create multiple chunks automatically
     * chunkedOut.write(largeData, 0, largeData.length);
     * }</pre>
     *
     * @param b the byte array containing the data to write; must not be
     *          {@code null}; the array is not modified by this method
     * @param off the starting offset within the byte array from which to
     *            begin copying data; must be non-negative and less than
     *            or equal to {@code b.length - len}
     * @param len the number of bytes to write from the array; must be
     *            non-negative; if zero, no bytes are written; may be
     *            larger than the chunk size, causing multiple chunks
     *            to be written
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if this stream has
     *                     been closed
     * @throws IndexOutOfBoundsException if {@code off} is negative,
     *                                   {@code len} is negative, or
     *                                   {@code off + len} exceeds {@code b.length}
     *
     * @see #write(int)
     */
    @Override
    public void write(final byte @NotNull [] b, final int off, final int len) throws IOException {
        ensureOpen();

        int remaining = len;
        int offset = off;

        while (remaining > 0) {
            final int available = this.chunkSize - this.bufferPosition;
            final int toCopy = Math.min(remaining, available);

            System.arraycopy(b, offset, this.buffer, this.bufferPosition, toCopy);
            this.bufferPosition += toCopy;
            this.totalBytesWritten += toCopy;
            offset += toCopy;
            remaining -= toCopy;

            if (this.bufferPosition >= this.chunkSize) {
                flushChunk(false);
            }
        }
    }

    /**
     * Flushes the underlying binary writer without flushing the chunk buffer.
     *
     * <p>This method flushes any data that has been passed to the underlying
     * output stream but may still be in operating system or stream buffers.
     * It does <strong>not</strong> flush the internal chunk buffer - any
     * partial chunk data remains buffered until the buffer is full or
     * the stream is finished/closed.</p>
     *
     * <p>To write any remaining buffered data as the final chunk, use
     * {@link #finish()} or {@link #close()} instead.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>Use this method when you need to ensure that all completed chunks
     * have been physically written to the output (e.g., to disk or network)
     * before continuing, without terminating the chunk sequence.</p>
     *
     * @throws IOException if an I/O error occurs while flushing the
     *                     underlying stream, or if this stream has
     *                     been closed
     *
     * @see #finish()
     * @see #close()
     */
    @Override
    public void flush() throws IOException {
        ensureOpen();
        this.writer.flush();
    }

    /**
     * Closes this chunked output stream and releases all associated resources.
     *
     * <p>This method performs the following operations in order:</p>
     * <ol>
     *   <li>Flushes any remaining buffered data as the final chunk (marked
     *       with the {@link FormatConstants#CHUNK_FLAG_LAST} flag)</li>
     *   <li>Flushes the underlying binary writer</li>
     *   <li>Closes the underlying output stream</li>
     * </ol>
     *
     * <p>If the stream is already closed, this method has no effect and
     * returns immediately without throwing an exception.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The final chunk written during close has the "last" flag set in its
     * header, which signals to the reader that no more chunks follow. This
     * chunk is written even if the buffer is empty (creating an empty final
     * chunk) when no chunks have been written yet, ensuring at least one
     * chunk exists in the stream.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>If an exception occurs during the close operation, the stream is
     * still marked as closed to prevent resource leaks, and the underlying
     * stream is closed in a finally block.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>Unlike {@link #finish()}, this method also closes the underlying
     * output stream. Use {@link #finish()} when you need to write additional
     * data (like trailers) to the same underlying stream after completing
     * the chunk sequence.</p>
     *
     * @throws IOException if an I/O error occurs while flushing the final
     *                     chunk or closing the underlying stream
     *
     * @see #finish()
     * @see #flush()
     */
    @Override
    public void close() throws IOException {
        if (!this.closed) {
            try {
                // Flush any remaining data as the last chunk
                // Also write a final empty chunk if data was exactly aligned to chunk size
                if (!this.lastChunkWritten) {
                    flushChunk(true);
                }
                this.writer.flush();
            } finally {
                this.closed = true;
                this.writer.close();
            }
        }
    }

    /**
     * Finishes writing chunks without closing the underlying stream.
     *
     * <p>This method flushes any remaining buffered data as the final chunk
     * (marked with the {@link FormatConstants#CHUNK_FLAG_LAST} flag) and
     * marks this stream as finished. Unlike {@link #close()}, it does
     * <strong>not</strong> close the underlying output stream.</p>
     *
     * <p>After calling this method, no more data can be written to this
     * stream. However, the underlying stream remains open and can be used
     * to write additional data such as trailers or other archive structures.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>This method is primarily used when writing APACK archives where
     * additional data needs to be written after the chunks:</p>
     * <pre>{@code
     * // Write file header
     * HeaderIO.writeFileHeader(writer, fileHeader);
     *
     * // Write entry header
     * HeaderIO.writeEntryHeader(writer, entryHeader);
     *
     * // Write chunk data
     * try (ChunkedOutputStream chunkOut = new ChunkedOutputStream(...)) {
     *     chunkOut.write(entryData);
     *     chunkOut.finish(); // Don't close underlying stream
     * }
     *
     * // Write trailer to the same stream
     * TrailerIO.writeStreamTrailer(writer, streamTrailer);
     * }</pre>
     *
     * <p><strong>\:</strong></p>
     * <p>Similar to {@link #close()}, the final chunk has the "last" flag
     * set and is written even if the buffer is empty when no chunks have
     * been written yet.</p>
     *
     * @throws IOException if an I/O error occurs while flushing the final
     *                     chunk or the underlying stream, or if this stream
     *                     has already been closed or finished
     *
     * @see #close()
     * @see #flush()
     */
    public void finish() throws IOException {
        ensureOpen();
        // Flush any remaining data as the last chunk
        // Also write a final empty chunk if data was exactly aligned to chunk size
        if (!this.lastChunkWritten) {
            flushChunk(true);
        }
        this.writer.flush();
        this.closed = true;
    }

    /**
     * Flushes the current buffer as a chunk.
     *
     * <p>This method processes the buffered data (computes checksum, applies
     * compression/encryption), writes the chunk header and data, and invokes
     * the callback if configured.</p>
     *
     * @param isLast {@code true} if this is the last chunk
     * @throws IOException if an I/O error occurs
     */
    private void flushChunk(final boolean isLast) throws IOException {
        final int originalSize = this.bufferPosition;

        // Compute checksum on original data
        final int checksum = (int) this.checksumProvider.compute(this.buffer, 0, originalSize);

        // Process chunk (compression/encryption)
        final ChunkProcessor.ProcessedChunk processed =
                this.chunkProcessor.processForWrite(this.buffer, originalSize);

        // Build flags
        int flags = 0;
        if (isLast) {
            flags |= FormatConstants.CHUNK_FLAG_LAST;
            this.lastChunkWritten = true;
        }
        if (processed.compressed()) {
            flags |= FormatConstants.CHUNK_FLAG_COMPRESSED;
        }
        if (processed.encrypted()) {
            flags |= FormatConstants.CHUNK_FLAG_ENCRYPTED;
        }

        // Create chunk header
        final ChunkHeader header = ChunkHeader.builder()
                .chunkIndex(this.chunkIndex)
                .originalSize(originalSize)
                .storedSize(processed.storedSize())
                .checksum(checksum)
                .flags(flags)
                .build();

        // Write chunk header
        writeChunkHeader(header);

        // Write processed chunk data
        this.writer.writeBytes(processed.data(), 0, processed.storedSize());

        // Update totals
        this.totalStoredBytes += processed.storedSize() + FormatConstants.CHUNK_HEADER_SIZE;

        // Callback
        if (this.chunkCallback != null) {
            this.chunkCallback.accept(this.chunkIndex, header);
        }

        // Reset for next chunk
        this.bufferPosition = 0;
        this.chunkIndex++;
    }

    /**
     * Writes a chunk header to the output.
     *
     * @param header the chunk header to write
     * @throws IOException if an I/O error occurs
     */
    private void writeChunkHeader(final @NotNull ChunkHeader header) throws IOException {
        this.writer.writeBytes(FormatConstants.CHUNK_MAGIC);
        this.writer.writeInt32(header.chunkIndex());
        this.writer.writeInt32(header.originalSize());
        this.writer.writeInt32(header.storedSize());
        this.writer.writeInt32(header.checksum());
        this.writer.writeInt32(header.flags());
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
