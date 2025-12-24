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
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A binary reader for reading Little-Endian encoded data from an input stream.
 *
 * <p>This class provides low-level methods for reading primitive types and strings
 * in the Little-Endian byte order used throughout the APACK format. It uses an
 * internal {@link ByteBuffer} for byte-order conversion.</p>
 *
 * <h2>Byte Order</h2>
 * <p>All multi-byte integer values are read in Little-Endian byte order,
 * matching the APACK format specification.</p>
 *
 * <h2>EOF Handling</h2>
 * <p>Methods that require a specific number of bytes will throw
 * {@link EOFException} if the end of stream is reached before all
 * bytes can be read. This ensures data integrity and early failure
 * on truncated files.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (BinaryReader reader = new BinaryReader(inputStream)) {
 *     reader.readAndValidateMagic();  // Validate "APACK\0"
 *     int major = reader.readUInt16();
 *     int minor = reader.readUInt16();
 *     int chunkSize = reader.readInt32();
 *     long timestamp = reader.readInt64();
 *     String name = reader.readLengthPrefixedString16();
 *     reader.skipPadding(8);          // Skip to 8-byte boundary
 * }
 * }</pre>
 *
 * <h2>Byte Tracking</h2>
 * <p>The reader tracks the total number of bytes read via {@link #getBytesRead()}.
 * This is useful for calculating offsets and verifying alignment.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is <strong>not thread-safe</strong>. External synchronization
 * is required if instances are shared between threads.</p>
 *
 * @see BinaryWriter
 * @see FormatConstants
 * @see HeaderIO
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class BinaryReader implements Closeable {

    /**
     * The underlying input stream from which binary data is read.
     * This stream is owned by the reader and will be closed when the reader is closed.
     */
    private final @NotNull InputStream input;

    /**
     * Internal byte buffer used for byte-order conversion of multi-byte values.
     * Configured for Little-Endian byte order with a capacity of 8 bytes.
     */
    private final @NotNull ByteBuffer buffer;

    /**
     * Counter tracking the total number of bytes read from the input stream.
     * Used for position tracking, offset calculation, and alignment verification.
     */
    private long bytesRead;

    /**
     * Flag indicating whether this reader has been closed.
     * Once closed, all read operations will throw an IOException.
     */
    private boolean closed;

    /**
     * Creates a new binary reader that reads from the specified input stream.
     *
     * <p>The reader is initialized with an 8-byte internal buffer configured
     * for Little-Endian byte order. The byte counter is initialized to zero.</p>
     *
     * <p>The reader takes ownership of the input stream. When {@link #close()}
     * is called, the underlying stream will be closed as well.</p>
     *
     * @param input the input stream to read from; must not be {@code null};
     *              the stream should be positioned at the start of the data
     *              to be read; the reader will close this stream when closed
     * @throws NullPointerException if input is {@code null}
     */
    public BinaryReader(final @NotNull InputStream input) {
        this.input = input;
        this.buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        this.bytesRead = 0;
        this.closed = false;
    }

    /**
     * Returns the total number of bytes read from the underlying stream.
     *
     * <p>This counter is incremented by all read operations and can be used
     * to track the current position in the stream, calculate offsets, and
     * verify alignment requirements.</p>
     *
     * <p>The value reflects the cumulative bytes read since the reader was
     * created. Skip operations also contribute to this count.</p>
     *
     * @return the total number of bytes that have been read from the stream
     *         since this reader was created; always non-negative
     */
    public long getBytesRead() {
        return this.bytesRead;
    }

    /**
     * Reads a single byte from the underlying input stream.
     *
     * <p>The byte is returned as an unsigned value in the range 0-255.
     * This method blocks until input data is available, end of stream
     * is detected, or an exception is thrown.</p>
     *
     * @return the byte read as an unsigned integer value in the range 0 to 255;
     *         the returned value represents a single byte of data
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, or if the reader has been closed
     * @throws EOFException if the end of stream is reached before a byte
     *                      can be read; this indicates a truncated or
     *                      incomplete input
     */
    public int readByte() throws IOException {
        ensureOpen();
        final int value = this.input.read();
        if (value < 0) {
            throw new EOFException("Unexpected end of stream");
        }
        this.bytesRead++;
        return value;
    }

    /**
     * Reads exactly the specified number of bytes from the underlying stream.
     *
     * <p>This method blocks until all requested bytes have been read, end of
     * stream is detected, or an exception is thrown. Unlike the standard
     * {@link InputStream#read(byte[])} method, this method guarantees that
     * exactly {@code length} bytes are returned.</p>
     *
     * <p>A new byte array is allocated for each call. For performance-critical
     * code that reuses buffers, use {@link #readFully(byte[], int, int)} instead.</p>
     *
     * @param length the exact number of bytes to read; must be non-negative;
     *               if zero, an empty byte array is returned
     * @return a newly allocated byte array containing exactly {@code length}
     *         bytes read from the stream; never {@code null}
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, or if the reader has been closed
     * @throws EOFException if the end of stream is reached before all bytes
     *                      can be read; this indicates a truncated input
     */
    public byte @NotNull [] readBytes(final int length) throws IOException {
        ensureOpen();
        // Validate length to prevent OOM attacks and integer overflow
        if (length < 0) {
            throw new IOException("Invalid negative length: " + length);
        }
        if (length > FormatConstants.MAX_CHUNK_SIZE) {
            throw new IOException("Requested size exceeds maximum: " + length +
                    " (max: " + FormatConstants.MAX_CHUNK_SIZE + ")");
        }
        final byte[] data = new byte[length];
        readFully(data, 0, length);
        return data;
    }

    /**
     * Reads exactly the specified number of bytes into the provided buffer.
     *
     * <p>This method blocks until all requested bytes have been read, end of
     * stream is detected, or an exception is thrown. It handles the case where
     * the underlying stream returns fewer bytes than requested by looping until
     * all bytes are read.</p>
     *
     * <p>This method is more efficient than {@link #readBytes(int)} when the
     * caller can reuse an existing buffer.</p>
     *
     * @param data the byte array to read into; must not be {@code null};
     *             must have sufficient capacity to hold {@code offset + length} bytes
     * @param offset the starting position in the array where bytes should be
     *               written; must be non-negative and less than {@code data.length}
     * @param length the exact number of bytes to read; must be non-negative;
     *               {@code offset + length} must not exceed {@code data.length}
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, or if the reader has been closed
     * @throws EOFException if the end of stream is reached before all bytes
     *                      can be read; the exception message includes the
     *                      number of bytes that could not be read
     * @throws ArrayIndexOutOfBoundsException if offset or length are invalid
     */
    public void readFully(final byte @NotNull [] data, final int offset, final int length) throws IOException {
        ensureOpen();
        int remaining = length;
        int pos = offset;
        while (remaining > 0) {
            final int read = this.input.read(data, pos, remaining);
            if (read < 0) {
                throw new EOFException("Unexpected end of stream: expected " + remaining + " more bytes");
            }
            pos += read;
            remaining -= read;
            this.bytesRead += read;
        }
    }

    /**
     * Reads a 16-bit unsigned integer in Little-Endian byte order.
     *
     * <p>Two bytes are read from the stream and interpreted as a 16-bit
     * unsigned integer with the least significant byte first (Little-Endian).
     * The result is returned as a Java {@code int} to preserve the full
     * unsigned range of 0 to 65535.</p>
     *
     * @return the 16-bit unsigned integer value read from the stream;
     *         the value is in the range 0 to 65535 inclusive
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, or if the reader has been closed
     * @throws EOFException if the end of stream is reached before both
     *                      bytes can be read
     */
    public int readUInt16() throws IOException {
        ensureOpen();
        this.buffer.clear();
        this.buffer.limit(2);
        readFully(this.buffer.array(), 0, 2);
        return this.buffer.getShort(0) & 0xFFFF;
    }

    /**
     * Reads a 32-bit signed integer in Little-Endian byte order.
     *
     * <p>Four bytes are read from the stream and interpreted as a 32-bit
     * signed integer with the least significant byte first (Little-Endian).
     * The full signed range of -2,147,483,648 to 2,147,483,647 is supported.</p>
     *
     * @return the 32-bit signed integer value read from the stream;
     *         the value is in the full range of Java {@code int}
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, or if the reader has been closed
     * @throws EOFException if the end of stream is reached before all
     *                      four bytes can be read
     */
    public int readInt32() throws IOException {
        ensureOpen();
        this.buffer.clear();
        this.buffer.limit(4);
        readFully(this.buffer.array(), 0, 4);
        return this.buffer.getInt(0);
    }

    /**
     * Reads a 32-bit unsigned integer in Little-Endian byte order.
     *
     * <p>Four bytes are read from the stream and interpreted as a 32-bit
     * unsigned integer with the least significant byte first (Little-Endian).
     * The result is returned as a Java {@code long} to preserve the full
     * unsigned range of 0 to 4,294,967,295.</p>
     *
     * @return the 32-bit unsigned integer value read from the stream;
     *         the value is in the range 0 to 4,294,967,295 inclusive,
     *         returned as a {@code long} to preserve the unsigned range
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, or if the reader has been closed
     * @throws EOFException if the end of stream is reached before all
     *                      four bytes can be read
     */
    public long readUInt32() throws IOException {
        return readInt32() & 0xFFFFFFFFL;
    }

    /**
     * Reads a 64-bit signed integer in Little-Endian byte order.
     *
     * <p>Eight bytes are read from the stream and interpreted as a 64-bit
     * signed integer with the least significant byte first (Little-Endian).
     * The full signed range of Java {@code long} is supported.</p>
     *
     * @return the 64-bit signed integer value read from the stream;
     *         the value is in the full range of Java {@code long}
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, or if the reader has been closed
     * @throws EOFException if the end of stream is reached before all
     *                      eight bytes can be read
     */
    public long readInt64() throws IOException {
        ensureOpen();
        this.buffer.clear();
        this.buffer.limit(8);
        readFully(this.buffer.array(), 0, 8);
        return this.buffer.getLong(0);
    }

    /**
     * Reads a UTF-8 encoded string of the specified byte length.
     *
     * <p>Exactly {@code length} bytes are read from the stream and decoded
     * as a UTF-8 string. This method does not expect or read a length prefix;
     * the caller must know the exact byte length of the string.</p>
     *
     * <p>Note that the byte length may differ from the character count for
     * strings containing non-ASCII characters, as UTF-8 uses variable-length
     * encoding (1-4 bytes per character).</p>
     *
     * @param length the exact number of bytes to read and decode; must be
     *               non-negative; if zero, an empty string is returned
     * @return the decoded UTF-8 string; never {@code null}; may be empty
     *         if {@code length} is zero
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, or if the reader has been closed
     * @throws EOFException if the end of stream is reached before all
     *                      bytes can be read
     */
    public @NotNull String readString(final int length) throws IOException {
        if (length == 0) {
            return "";
        }
        final byte[] bytes = readBytes(length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads a UTF-8 encoded string with a 16-bit unsigned length prefix.
     *
     * <p>First, a 16-bit unsigned integer is read in Little-Endian byte order
     * to determine the string's byte length. Then, that many bytes are read
     * and decoded as a UTF-8 string. This format is used throughout the
     * APACK format for variable-length strings like entry names.</p>
     *
     * <p>The maximum string byte length is 65535 bytes (the maximum value
     * of a 16-bit unsigned integer).</p>
     *
     * @return the decoded UTF-8 string; never {@code null}; may be empty
     *         if the length prefix is zero
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, or if the reader has been closed
     * @throws EOFException if the end of stream is reached before the
     *                      length prefix or string bytes can be read
     */
    public @NotNull String readLengthPrefixedString16() throws IOException {
        final int length = readUInt16();
        return readString(length);
    }

    /**
     * Skips the specified number of bytes in the input stream.
     *
     * <p>This method advances the stream position by {@code count} bytes
     * without returning the skipped data. The bytes-read counter is
     * incremented accordingly.</p>
     *
     * <p>If the underlying stream's skip operation doesn't skip all bytes
     * in a single call, this method will repeatedly try to skip or read
     * until all bytes are consumed.</p>
     *
     * @param count the number of bytes to skip; must be non-negative;
     *              if zero, this method has no effect
     * @throws IOException if an I/O error occurs while skipping bytes,
     *                     or if the reader has been closed
     * @throws EOFException if the end of stream is reached before all
     *                      bytes can be skipped
     */
    public void skip(final long count) throws IOException {
        ensureOpen();
        long remaining = count;
        while (remaining > 0) {
            final long skipped = this.input.skip(remaining);
            if (skipped <= 0) {
                // Fallback: read and discard
                final int toRead = (int) Math.min(remaining, 8192);
                final byte[] discard = new byte[toRead];
                final int read = this.input.read(discard);
                if (read < 0) {
                    throw new EOFException("Unexpected end of stream while skipping");
                }
                remaining -= read;
                this.bytesRead += read;
            } else {
                remaining -= skipped;
                this.bytesRead += skipped;
            }
        }
    }

    /**
     * Skips padding bytes to align the current position to the specified boundary.
     *
     * <p>APACK uses alignment for efficient memory-mapped access. This method
     * calculates how many padding bytes are needed to reach the next alignment
     * boundary based on the current bytes-read count, then skips that many bytes.</p>
     *
     * <p>For example, if 100 bytes have been read and the alignment is 8,
     * this method will skip 4 bytes to reach position 104 (which is divisible
     * by 8).</p>
     *
     * @param alignment the alignment boundary in bytes; must be a power of 2
     *                  (e.g., 2, 4, 8, 16); common values are 4 or 8 for
     *                  efficient memory access
     * @throws IOException if an I/O error occurs while skipping bytes,
     *                     or if the reader has been closed
     * @throws EOFException if the end of stream is reached before all
     *                      padding bytes can be skipped
     */
    public void skipPadding(final int alignment) throws IOException {
        final int position = (int) (this.bytesRead % alignment);
        if (position > 0) {
            final int padding = alignment - position;
            skip(padding);
        }
    }

    /**
     * Reads and validates the APACK file magic number.
     *
     * <p>This method reads 6 bytes (5 bytes for "APACK" plus 1 null byte)
     * and verifies they match the expected APACK magic number. This is
     * typically the first operation when reading an APACK archive.</p>
     *
     * <p>If the magic number is invalid, a {@link FormatException} is thrown
     * with a descriptive message. This helps quickly identify files that
     * are not valid APACK archives.</p>
     *
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, or if the reader has been closed
     * @throws EOFException if the end of stream is reached before all
     *                      magic bytes can be read
     * @throws FormatException if the magic bytes do not match the expected
     *                         "APACK" signature followed by a null byte;
     *                         this indicates the file is not a valid APACK archive
     *
     * @see FormatConstants#MAGIC
     */
    public void readAndValidateMagic() throws IOException, FormatException {
        final byte[] magic = readBytes(5);
        if (!Arrays.equals(magic, FormatConstants.MAGIC)) {
            throw new FormatException("Invalid magic number: expected APACK");
        }
        final int nullByte = readByte();
        if (nullByte != 0) {
            throw new FormatException("Invalid magic: expected null byte after APACK");
        }
    }

    /**
     * Reads the magic number bytes without validation.
     *
     * <p>This method reads 6 bytes (5 bytes for the magic plus 1 null byte)
     * and returns the first 5 bytes. Unlike {@link #readAndValidateMagic()},
     * this method does not verify that the bytes match the expected APACK
     * signature.</p>
     *
     * <p>This is useful when you need to check the magic manually or when
     * reading files that might have different magic numbers.</p>
     *
     * @return a byte array containing the 5 magic bytes read from the stream;
     *         never {@code null}; the null byte is consumed but not returned
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, or if the reader has been closed
     * @throws EOFException if the end of stream is reached before all
     *                      magic bytes can be read
     */
    public byte @NotNull [] readMagic() throws IOException {
        final byte[] magic = readBytes(5);
        readByte(); // null byte
        return magic;
    }

    /**
     * Closes this binary reader and releases any system resources associated
     * with the underlying input stream.
     *
     * <p>If the reader is already closed, this method has no effect.</p>
     *
     * @throws IOException if an I/O error occurs while closing the underlying stream
     */
    @Override
    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            this.input.close();
        }
    }

    /**
     * Ensures that the reader is not closed.
     *
     * @throws IOException if the reader has been closed
     */
    private void ensureOpen() throws IOException {
        if (this.closed) {
            throw new IOException("Reader is closed");
        }
    }

}
