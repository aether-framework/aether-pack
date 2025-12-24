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

import de.splatgames.aether.pack.core.format.FormatConstants;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A buffered binary writer for writing Little-Endian encoded data to an output stream.
 *
 * <p>This class provides low-level methods for writing primitive types and strings
 * in the Little-Endian byte order used throughout the APACK format. It uses an
 * internal {@link ByteBuffer} for efficient buffered writing.</p>
 *
 * <h2>Byte Order</h2>
 * <p>All multi-byte integer values are written in Little-Endian byte order,
 * matching the APACK format specification and enabling efficient processing
 * on x86/x64 systems.</p>
 *
 * <h2>Buffering</h2>
 * <p>The writer maintains an internal buffer (default 8 KB) to minimize system
 * calls. Large writes that exceed the buffer capacity are written directly to
 * the underlying stream after flushing the buffer.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (BinaryWriter writer = new BinaryWriter(outputStream)) {
 *     writer.writeMagic();           // Write "APACK\0"
 *     writer.writeUInt16(1);         // Major version
 *     writer.writeUInt16(0);         // Minor version
 *     writer.writeInt32(chunkSize);  // Chunk size
 *     writer.writeInt64(timestamp);  // Timestamp
 *     writer.writeString(name);      // Entry name
 *     writer.writePadding(8);        // Align to 8 bytes
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is <strong>not thread-safe</strong>. External synchronization
 * is required if instances are shared between threads.</p>
 *
 * @see BinaryReader
 * @see FormatConstants
 * @see HeaderIO
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class BinaryWriter implements Closeable, Flushable {

    /**
     * Default buffer size for the internal write buffer (8 KB).
     * Provides a good balance between memory usage and I/O efficiency.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * The underlying output stream to which binary data is written.
     * This stream is owned by the writer and will be closed when the writer is closed.
     */
    private final @NotNull OutputStream output;

    /**
     * Internal byte buffer used for buffered writing and byte-order conversion.
     * Configured for Little-Endian byte order with configurable capacity.
     */
    private final @NotNull ByteBuffer buffer;

    /**
     * Counter tracking the total number of bytes written to the output stream.
     * Used for position tracking, offset calculation, and alignment verification.
     */
    private long bytesWritten;

    /**
     * Flag indicating whether this writer has been closed.
     * Once closed, all write operations will throw an IOException.
     */
    private boolean closed;

    /**
     * Creates a new binary writer with the default buffer size (8 KB).
     *
     * <p>The writer is initialized with an internal buffer configured for
     * Little-Endian byte order. The default buffer size of 8 KB provides a
     * good balance between memory usage and I/O efficiency for most use cases.</p>
     *
     * <p>The writer takes ownership of the output stream. When {@link #close()}
     * is called, the underlying stream will be flushed and closed as well.</p>
     *
     * @param output the output stream to write to; must not be {@code null};
     *               the writer will close this stream when closed
     * @throws NullPointerException if output is {@code null}
     */
    public BinaryWriter(final @NotNull OutputStream output) {
        this(output, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Creates a new binary writer with the specified buffer size.
     *
     * <p>The writer is initialized with an internal buffer configured for
     * Little-Endian byte order. The buffer size affects I/O efficiency:</p>
     * <ul>
     *   <li><strong>Smaller buffers:</strong> Lower memory usage, more frequent
     *       flushes to the underlying stream</li>
     *   <li><strong>Larger buffers:</strong> Fewer system calls, potentially
     *       better performance for high-throughput scenarios</li>
     * </ul>
     *
     * <p>The writer takes ownership of the output stream. When {@link #close()}
     * is called, the underlying stream will be flushed and closed as well.</p>
     *
     * @param output the output stream to write to; must not be {@code null};
     *               the writer will close this stream when closed
     * @param bufferSize the size of the internal buffer in bytes; must be
     *                   a positive integer; larger values may improve
     *                   performance at the cost of memory
     * @throws NullPointerException if output is {@code null}
     * @throws IllegalArgumentException if bufferSize is not positive
     */
    public BinaryWriter(final @NotNull OutputStream output, final int bufferSize) {
        this.output = output;
        this.buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN);
        this.bytesWritten = 0;
        this.closed = false;
    }

    /**
     * Returns the total number of bytes written to the underlying stream.
     *
     * <p>This counter is incremented by all write operations and can be used
     * to track the current position in the output, calculate offsets, and
     * verify alignment requirements.</p>
     *
     * <p>The value reflects the cumulative bytes written since the writer
     * was created. Note that due to buffering, some bytes may not yet have
     * been flushed to the underlying stream.</p>
     *
     * @return the total number of bytes that have been written to the stream
     *         since this writer was created; always non-negative
     */
    public long getBytesWritten() {
        return this.bytesWritten;
    }

    /**
     * Writes a single byte to the output stream.
     *
     * <p>Only the lowest 8 bits of the provided integer value are written.
     * The byte is first written to the internal buffer, which is flushed
     * to the underlying stream when full.</p>
     *
     * @param value the byte value to write; only the lowest 8 bits are used;
     *              values outside 0-255 will be truncated to 8 bits
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     */
    public void writeByte(final int value) throws IOException {
        ensureOpen();
        ensureCapacity(1);
        this.buffer.put((byte) value);
        this.bytesWritten++;
    }

    /**
     * Writes all bytes from the specified byte array to the output stream.
     *
     * <p>This is a convenience method equivalent to calling
     * {@code writeBytes(data, 0, data.length)}. All bytes in the array
     * are written to the output.</p>
     *
     * @param data the byte array containing the data to write; must not be
     *             {@code null}; all bytes in the array will be written
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     * @throws NullPointerException if data is {@code null}
     */
    public void writeBytes(final byte @NotNull [] data) throws IOException {
        writeBytes(data, 0, data.length);
    }

    /**
     * Writes a portion of a byte array to the output stream.
     *
     * <p>Writes {@code length} bytes from the specified byte array starting
     * at {@code offset}. If the data fits in the internal buffer, it is
     * buffered; otherwise, the buffer is flushed and the data is written
     * directly to the underlying stream.</p>
     *
     * @param data the byte array containing the data to write; must not be
     *             {@code null}; must have at least {@code offset + length} bytes
     * @param offset the starting position in the array from which to write;
     *               must be non-negative and less than {@code data.length}
     * @param length the number of bytes to write; must be non-negative;
     *               {@code offset + length} must not exceed {@code data.length}
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     * @throws NullPointerException if data is {@code null}
     * @throws ArrayIndexOutOfBoundsException if offset or length are invalid
     */
    public void writeBytes(final byte @NotNull [] data, final int offset, final int length) throws IOException {
        ensureOpen();

        if (length <= this.buffer.remaining()) {
            this.buffer.put(data, offset, length);
            this.bytesWritten += length;
        } else {
            flushBuffer();
            this.output.write(data, offset, length);
            this.bytesWritten += length;
        }
    }

    /**
     * Writes a 16-bit unsigned integer in Little-Endian byte order.
     *
     * <p>The value is written as two bytes with the least significant byte
     * first (Little-Endian), matching the APACK format specification. Only
     * the lower 16 bits of the provided integer are used.</p>
     *
     * @param value the 16-bit unsigned integer value to write; should be
     *              in the range 0 to 65535; values outside this range will
     *              be truncated to 16 bits
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     */
    public void writeUInt16(final int value) throws IOException {
        ensureOpen();
        ensureCapacity(2);
        this.buffer.putShort((short) value);
        this.bytesWritten += 2;
    }

    /**
     * Writes a 32-bit signed integer in Little-Endian byte order.
     *
     * <p>The value is written as four bytes with the least significant byte
     * first (Little-Endian), matching the APACK format specification. The
     * full signed range of Java {@code int} is supported.</p>
     *
     * @param value the 32-bit signed integer value to write; the full range
     *              of -2,147,483,648 to 2,147,483,647 is supported
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     */
    public void writeInt32(final int value) throws IOException {
        ensureOpen();
        ensureCapacity(4);
        this.buffer.putInt(value);
        this.bytesWritten += 4;
    }

    /**
     * Writes a 32-bit unsigned integer in Little-Endian byte order.
     *
     * <p>The value is written as four bytes with the least significant byte
     * first (Little-Endian). Only the lower 32 bits of the provided long
     * are used, allowing unsigned values up to 4,294,967,295 to be written.</p>
     *
     * @param value the 32-bit unsigned integer value to write; should be
     *              in the range 0 to 4,294,967,295; only the lower 32 bits
     *              of the long are written
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     */
    public void writeUInt32(final long value) throws IOException {
        writeInt32((int) value);
    }

    /**
     * Writes a 64-bit signed integer in Little-Endian byte order.
     *
     * <p>The value is written as eight bytes with the least significant byte
     * first (Little-Endian), matching the APACK format specification. The
     * full signed range of Java {@code long} is supported.</p>
     *
     * @param value the 64-bit signed integer value to write; the full range
     *              of Java {@code long} is supported
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     */
    public void writeInt64(final long value) throws IOException {
        ensureOpen();
        ensureCapacity(8);
        this.buffer.putLong(value);
        this.bytesWritten += 8;
    }

    /**
     * Writes a UTF-8 encoded string without a length prefix.
     *
     * <p>The string is encoded as UTF-8 and all resulting bytes are written
     * to the output. No length prefix or null terminator is written; the
     * caller must ensure the string length is known or determinable when
     * reading.</p>
     *
     * <p>Note that the byte length may differ from the character count for
     * strings containing non-ASCII characters, as UTF-8 uses variable-length
     * encoding (1-4 bytes per character).</p>
     *
     * @param value the string to write; must not be {@code null}; may be
     *              empty, in which case no bytes are written
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     * @throws NullPointerException if value is {@code null}
     */
    public void writeString(final @NotNull String value) throws IOException {
        writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes a UTF-8 encoded string with a 16-bit unsigned length prefix.
     *
     * <p>First, the UTF-8 byte length of the string is written as a 16-bit
     * unsigned integer in Little-Endian byte order. Then, the UTF-8 encoded
     * string bytes are written. This format is used throughout the APACK
     * format for variable-length strings like entry names.</p>
     *
     * <p>The maximum string byte length is 65535 bytes (the maximum value
     * of a 16-bit unsigned integer). Strings that exceed this limit will
     * cause an IOException.</p>
     *
     * @param value the string to write; must not be {@code null}; may be
     *              empty, in which case only the length prefix (0) is written
     * @throws IOException if an I/O error occurs, if the writer has been
     *                     closed, or if the UTF-8 encoded string exceeds
     *                     65535 bytes
     * @throws NullPointerException if value is {@code null}
     */
    public void writeLengthPrefixedString16(final @NotNull String value) throws IOException {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xFFFF) {
            throw new IOException("String too long for 16-bit length prefix: " + bytes.length);
        }
        writeUInt16(bytes.length);
        writeBytes(bytes);
    }

    /**
     * Writes padding bytes to align the current position to the specified boundary.
     *
     * <p>APACK uses alignment for efficient memory-mapped access. This method
     * calculates how many padding bytes (zeros) are needed to reach the next
     * alignment boundary based on the current bytes-written count, then writes
     * that many zero bytes.</p>
     *
     * <p>For example, if 100 bytes have been written and the alignment is 8,
     * this method will write 4 zero bytes to reach position 104 (which is
     * divisible by 8).</p>
     *
     * @param alignment the alignment boundary in bytes; must be a power of 2
     *                  (e.g., 2, 4, 8, 16); common values are 4 or 8 for
     *                  efficient memory access
     * @throws IOException if an I/O error occurs while writing padding bytes,
     *                     or if the writer has been closed
     */
    public void writePadding(final int alignment) throws IOException {
        final int position = (int) (this.bytesWritten % alignment);
        if (position > 0) {
            final int padding = alignment - position;
            for (int i = 0; i < padding; i++) {
                writeByte(0);
            }
        }
    }

    /**
     * Writes the specified number of zero bytes to the output stream.
     *
     * <p>This method writes {@code count} bytes, each with value 0x00. It is
     * useful for writing reserved fields, placeholders, or explicit padding.</p>
     *
     * @param count the number of zero bytes to write; must be non-negative;
     *              if zero, this method has no effect
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     */
    public void writeZeros(final int count) throws IOException {
        for (int i = 0; i < count; i++) {
            writeByte(0);
        }
    }

    /**
     * Writes the APACK file magic number to the output stream.
     *
     * <p>This method writes 6 bytes: the 5-byte "APACK" magic signature
     * followed by a null byte (0x00). This is typically the first operation
     * when creating a new APACK archive and identifies the file format.</p>
     *
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     *
     * @see FormatConstants#MAGIC
     */
    public void writeMagic() throws IOException {
        writeBytes(FormatConstants.MAGIC);
        writeByte(0); // null byte
    }

    /**
     * Flushes the internal buffer and the underlying output stream.
     *
     * <p>This ensures that all buffered data is written to the underlying
     * stream and that the stream itself is flushed.</p>
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void flush() throws IOException {
        ensureOpen();
        flushBuffer();
        this.output.flush();
    }

    /**
     * Closes this binary writer and releases any system resources associated
     * with the underlying output stream.
     *
     * <p>This method first flushes any buffered data, then closes the
     * underlying stream. If the writer is already closed, this method
     * has no effect.</p>
     *
     * @throws IOException if an I/O error occurs during flushing or closing
     */
    @Override
    public void close() throws IOException {
        if (!this.closed) {
            try {
                flush();
            } finally {
                this.closed = true;
                this.output.close();
            }
        }
    }

    /**
     * Ensures that the writer is not closed.
     *
     * @throws IOException if the writer has been closed
     */
    private void ensureOpen() throws IOException {
        if (this.closed) {
            throw new IOException("Writer is closed");
        }
    }

    /**
     * Ensures the buffer has capacity for the specified number of bytes.
     *
     * <p>If the buffer does not have enough space, it is flushed to make room.</p>
     *
     * @param bytes the number of bytes required
     * @throws IOException if an I/O error occurs during flushing
     */
    private void ensureCapacity(final int bytes) throws IOException {
        if (this.buffer.remaining() < bytes) {
            flushBuffer();
        }
    }

    /**
     * Flushes the internal buffer to the underlying output stream.
     *
     * <p>If the buffer contains data, it is written to the stream and the
     * buffer is cleared. If the buffer is empty, this method has no effect.</p>
     *
     * @throws IOException if an I/O error occurs while writing to the stream
     */
    private void flushBuffer() throws IOException {
        if (this.buffer.position() > 0) {
            this.buffer.flip();
            final byte[] data = new byte[this.buffer.remaining()];
            this.buffer.get(data);
            this.output.write(data);
            this.buffer.clear();
        }
    }

}
