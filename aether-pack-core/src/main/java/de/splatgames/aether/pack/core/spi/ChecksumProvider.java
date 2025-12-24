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

package de.splatgames.aether.pack.core.spi;

import org.jetbrains.annotations.NotNull;

/**
 * Service Provider Interface for checksum algorithms in APACK archives.
 *
 * <p>This interface defines the contract for pluggable checksum providers.
 * Checksums are used throughout APACK for data integrity verification of
 * chunks, headers, and the table of contents.</p>
 *
 * <h2>Built-in Implementations</h2>
 * <p>The core module provides:</p>
 * <ul>
 *   <li><strong>CRC32</strong> - Standard 32-bit CRC (compatible with java.util.zip)</li>
 *   <li><strong>XXH3-64</strong> - High-performance 64-bit hash (default, recommended)</li>
 * </ul>
 *
 * <h2>Implementation Requirements</h2>
 * <ul>
 *   <li>Provider instances <strong>must be thread-safe</strong></li>
 *   <li>Provider instances <strong>must be stateless</strong></li>
 *   <li>{@link #createChecksum()} must return a fresh, independent calculator each time</li>
 *   <li>{@link Checksum} instances are <strong>NOT thread-safe</strong></li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // One-shot computation (convenience)
 * ChecksumProvider xxh3 = ChecksumRegistry.getDefault();
 * long checksum = xxh3.compute(data);
 *
 * // Incremental computation
 * ChecksumProvider.Checksum calc = xxh3.createChecksum();
 * calc.update(header);
 * calc.update(body);
 * long result = calc.getValue();
 * calc.reset();  // Reuse for next computation
 *
 * // Get as 32-bit integer (for chunk headers)
 * int checksum32 = calc.getValueAsInt();
 * }</pre>
 *
 * <h2>Performance Characteristics</h2>
 * <table>
 *   <caption>Checksum Algorithm Comparison</caption>
 *   <tr><th>Algorithm</th><th>Size</th><th>Speed</th><th>Quality</th></tr>
 *   <tr><td>CRC32</td><td>32-bit</td><td>Fast</td><td>Good error detection</td></tr>
 *   <tr><td>XXH3-64</td><td>64-bit</td><td>Very Fast</td><td>Excellent distribution</td></tr>
 * </table>
 *
 * <h2>Registration</h2>
 * <p>Use {@link de.splatgames.aether.pack.core.checksum.ChecksumRegistry} to
 * look up or register providers.</p>
 *
 * @see CompressionProvider
 * @see EncryptionProvider
 * @see de.splatgames.aether.pack.core.checksum.ChecksumRegistry
 * @see de.splatgames.aether.pack.core.format.FormatConstants#CHECKSUM_CRC32
 * @see de.splatgames.aether.pack.core.format.FormatConstants#CHECKSUM_XXH3_64
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface ChecksumProvider {

    /**
     * Returns the unique string identifier for this checksum algorithm.
     *
     * <p>The identifier is a human-readable string that uniquely identifies the
     * checksum algorithm implementation. This identifier is used for configuration,
     * logging, debugging, and to look up providers in the
     * {@link de.splatgames.aether.pack.core.checksum.ChecksumRegistry}.</p>
     *
     * <p><strong>:</strong></p>
     * <p>Identifiers should be lowercase, use hyphens for word separation, and
     * include bit width for clarity:</p>
     * <ul>
     *   <li>{@code "crc32"} - CRC-32 algorithm</li>
     *   <li>{@code "xxh3-64"} - XXH3 with 64-bit output</li>
     *   <li>{@code "xxh3-128"} - XXH3 with 128-bit output</li>
     * </ul>
     *
     * <p><strong>:</strong></p>
     * <p>Each provider implementation must return a unique identifier. Registering
     * multiple providers with the same ID will cause conflicts.</p>
     *
     * @return the unique algorithm identifier string; never {@code null};
     *         typically lowercase with hyphens (e.g., "xxh3-64")
     *
     * @see #getNumericId()
     * @see de.splatgames.aether.pack.core.checksum.ChecksumRegistry#getByName(String)
     */
    @NotNull String getId();

    /**
     * Returns the numeric identifier for this checksum algorithm.
     *
     * <p>The numeric ID is stored in the binary archive format to identify
     * which checksum algorithm was used. When reading an archive, this ID
     * is used to look up the appropriate provider for verification.</p>
     *
     * <p><strong>:</strong></p>
     * <table>
     *   <caption>Reserved Checksum Algorithm IDs</caption>
     *   <tr><th>ID</th><th>Algorithm</th><th>Constant</th></tr>
     *   <tr><td>0</td><td>CRC-32</td><td>{@link de.splatgames.aether.pack.core.format.FormatConstants#CHECKSUM_CRC32}</td></tr>
     *   <tr><td>1</td><td>XXH3-64</td><td>{@link de.splatgames.aether.pack.core.format.FormatConstants#CHECKSUM_XXH3_64}</td></tr>
     * </table>
     *
     * <p><strong>:</strong></p>
     * <p>Custom implementations should use IDs >= 128 to avoid conflicts with
     * future standard algorithm assignments.</p>
     *
     * @return the numeric algorithm identifier; standard algorithms use values
     *         0-127; custom implementations should use 128+
     *
     * @see #getId()
     * @see de.splatgames.aether.pack.core.format.FormatConstants#CHECKSUM_CRC32
     * @see de.splatgames.aether.pack.core.format.FormatConstants#CHECKSUM_XXH3_64
     * @see de.splatgames.aether.pack.core.checksum.ChecksumRegistry#getById(int)
     */
    int getNumericId();

    /**
     * Returns the size of the checksum output in bytes.
     *
     * <p>This method returns the number of bytes produced by the checksum
     * algorithm. This is important for:</p>
     * <ul>
     *   <li>Allocating buffers for checksum storage</li>
     *   <li>Parsing checksum values from binary data</li>
     *   <li>Selecting appropriate storage fields in format headers</li>
     * </ul>
     *
     * <p><strong>:</strong></p>
     * <table>
     *   <caption>Checksum Sizes</caption>
     *   <tr><th>Algorithm</th><th>Size (bytes)</th><th>Size (bits)</th></tr>
     *   <tr><td>CRC-32</td><td>4</td><td>32</td></tr>
     *   <tr><td>XXH3-64</td><td>8</td><td>64</td></tr>
     *   <tr><td>XXH3-128</td><td>16</td><td>128</td></tr>
     * </table>
     *
     * @return the checksum size in bytes; always a positive value; typically
     *         4 for 32-bit checksums, 8 for 64-bit checksums
     *
     * @see Checksum#getValue()
     * @see Checksum#getBytes()
     */
    int getChecksumSize();

    /**
     * Creates a new checksum calculator instance for incremental computation.
     *
     * <p>Each call to this method returns a fresh, independent calculator
     * instance. The returned calculator maintains internal state and can
     * be used for incremental checksum computation by calling
     * {@link Checksum#update(byte[], int, int)} one or more times.</p>
     *
     * <p><strong>:</strong></p>
     * <p>The provider itself is thread-safe, but the returned {@link Checksum}
     * instances are <strong>NOT thread-safe</strong>. Each thread should
     * obtain its own calculator instance.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * // Create calculator
     * ChecksumProvider.Checksum calc = provider.createChecksum();
     *
     * // Incremental updates
     * calc.update(headerBytes);
     * calc.update(dataBytes);
     *
     * // Get result
     * long checksum = calc.getValue();
     *
     * // Optionally reuse for another computation
     * calc.reset();
     * calc.update(otherData);
     * long otherChecksum = calc.getValue();
     * }</pre>
     *
     * @return a new checksum calculator instance; never {@code null}; the
     *         returned instance is independent of any other instance and
     *         maintains its own internal state
     *
     * @see Checksum
     * @see #compute(byte[])
     */
    @NotNull Checksum createChecksum();

    /**
     * Computes the checksum of the given data in a single call.
     *
     * <p>This is a convenience method for computing the checksum of a complete
     * byte array without needing to create and manage a {@link Checksum}
     * instance. It is functionally equivalent to:</p>
     * <pre>{@code
     * Checksum c = createChecksum();
     * c.update(data, 0, data.length);
     * return c.getValue();
     * }</pre>
     *
     * <p>For computing checksums of multiple data segments or for incremental
     * processing, use {@link #createChecksum()} instead.</p>
     *
     * <p><strong>:</strong></p>
     * <p>Some implementations may optimize this method for single-call
     * computation, avoiding the overhead of creating a stateful calculator.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * byte[] fileContent = Files.readAllBytes(path);
     * long checksum = provider.compute(fileContent);
     * System.out.printf("Checksum: 0x%016X%n", checksum);
     * }</pre>
     *
     * @param data the byte array to compute the checksum of; must not be
     *             {@code null}; the entire array is processed
     * @return the computed checksum value as a long; for checksums smaller
     *         than 64 bits, the value is contained in the lower bits
     *
     * @see #compute(byte[], int, int)
     * @see #createChecksum()
     */
    default long compute(final byte @NotNull [] data) {
        return compute(data, 0, data.length);
    }

    /**
     * Computes the checksum of a portion of the given data array.
     *
     * <p>This method computes the checksum of a contiguous segment within
     * the provided byte array, starting at the specified offset and
     * processing the specified number of bytes.</p>
     *
     * <p>This is useful when:</p>
     * <ul>
     *   <li>Working with buffers that contain additional data</li>
     *   <li>Processing chunks or segments of a larger data structure</li>
     *   <li>Avoiding array copies for performance</li>
     * </ul>
     *
     * <p><strong>:</strong></p>
     * <p>Implementations should validate that {@code offset} and {@code length}
     * define a valid range within the array. Invalid bounds may result in
     * {@link ArrayIndexOutOfBoundsException}.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * byte[] buffer = new byte[8192];
     * int bytesRead = inputStream.read(buffer);
     *
     * // Compute checksum of only the read portion
     * long checksum = provider.compute(buffer, 0, bytesRead);
     * }</pre>
     *
     * @param data   the byte array containing the data; must not be {@code null}
     * @param offset the starting offset within the array; must be non-negative
     *               and less than {@code data.length}
     * @param length the number of bytes to process; must be non-negative and
     *               {@code offset + length} must not exceed {@code data.length}
     * @return the computed checksum value as a long; for checksums smaller
     *         than 64 bits, the value is contained in the lower bits
     * @throws ArrayIndexOutOfBoundsException if offset or length are invalid
     *
     * @see #compute(byte[])
     * @see #createChecksum()
     */
    default long compute(final byte @NotNull [] data, final int offset, final int length) {
        final Checksum checksum = createChecksum();
        checksum.update(data, offset, length);
        return checksum.getValue();
    }

    /**
     * A mutable checksum calculator for incremental computation.
     *
     * <p>Instances of this interface maintain state and are used to compute
     * checksums incrementally by calling {@link #update(byte[], int, int)}
     * one or more times, then retrieving the result with {@link #getValue()}.</p>
     *
     * <p><strong>:</strong></p>
     * <p>Instances are <strong>NOT thread-safe</strong>. Each thread should
     * use its own instance obtained from {@link ChecksumProvider#createChecksum()}.</p>
     *
     * <p><strong>:</strong></p>
     * <p>Instances can be reused by calling {@link #reset()} between computations:</p>
     * <pre>{@code
     * Checksum calc = provider.createChecksum();
     * calc.update(data1);
     * long checksum1 = calc.getValue();
     *
     * calc.reset();
     * calc.update(data2);
     * long checksum2 = calc.getValue();
     * }</pre>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    interface Checksum {

        /**
         * Updates the checksum with a single byte value.
         *
         * <p>This method adds one byte to the checksum calculation. The byte
         * is specified as an {@code int} where only the lower 8 bits are used.
         * This matches the convention used by {@link java.io.InputStream#read()}.</p>
         *
         * <p>For efficiency when processing multiple bytes, prefer using
         * {@link #update(byte[], int, int)} instead of calling this method
         * repeatedly.</p>
         *
         * <p><strong>:</strong></p>
         * <pre>{@code
         * int byteRead = inputStream.read();
         * if (byteRead != -1) {
         *     checksum.update(byteRead);
         * }
         * }</pre>
         *
         * @param b the byte value to add to the checksum; only the lower 8 bits
         *          are used; the value is treated as unsigned (0-255)
         *
         * @see #update(byte[], int, int)
         * @see #update(byte)
         */
        void update(int b);

        /**
         * Updates the checksum with all bytes from the given array.
         *
         * <p>This convenience method processes the entire byte array, adding
         * all bytes to the checksum calculation. It is equivalent to:</p>
         * <pre>{@code
         * update(data, 0, data.length)
         * }</pre>
         *
         * <p><strong>:</strong></p>
         * <pre>{@code
         * byte[] header = readHeader();
         * checksum.update(header);
         *
         * byte[] body = readBody();
         * checksum.update(body);
         *
         * long result = checksum.getValue();
         * }</pre>
         *
         * @param data the byte array to add to the checksum; must not be
         *             {@code null}; all bytes in the array are processed
         *
         * @see #update(byte[], int, int)
         */
        default void update(final byte @NotNull [] data) {
            update(data, 0, data.length);
        }

        /**
         * Updates the checksum with a portion of the given byte array.
         *
         * <p>This method adds the specified range of bytes from the array
         * to the checksum calculation. It is the primary method for bulk
         * updates and is typically more efficient than single-byte updates.</p>
         *
         * <p><strong>:</strong></p>
         * <ul>
         *   <li>{@code offset} must be >= 0</li>
         *   <li>{@code length} must be >= 0</li>
         *   <li>{@code offset + length} must not exceed {@code data.length}</li>
         * </ul>
         *
         * <p><strong>:</strong></p>
         * <pre>{@code
         * byte[] buffer = new byte[4096];
         * int bytesRead;
         * while ((bytesRead = input.read(buffer)) != -1) {
         *     checksum.update(buffer, 0, bytesRead);
         * }
         * long finalChecksum = checksum.getValue();
         * }</pre>
         *
         * @param data   the byte array containing data to add; must not be
         *               {@code null}
         * @param offset the starting offset within the array; must be non-negative
         * @param length the number of bytes to process; must be non-negative
         * @throws ArrayIndexOutOfBoundsException if the specified range is
         *                                        outside the array bounds
         *
         * @see #update(byte[])
         * @see #update(int)
         */
        void update(byte @NotNull [] data, int offset, int length);

        /**
         * Returns the current checksum value as a 64-bit long.
         *
         * <p>This method returns the computed checksum based on all bytes
         * that have been added via the {@code update} methods since the
         * last {@link #reset()} call (or since creation).</p>
         *
         * <p>For checksums smaller than 64 bits (e.g., CRC-32), the value
         * is contained in the lower bits with zeros in the upper bits.
         * For 32-bit checksums, cast to {@code int} to get the standard
         * representation, or use {@link #getValueAsInt()}.</p>
         *
         * <p><strong>:</strong></p>
         * <p>This method can be called multiple times without affecting the
         * internal state. The same value is returned until more data is
         * added or {@link #reset()} is called.</p>
         *
         * <p><strong>:</strong></p>
         * <pre>{@code
         * checksum.update(data);
         * long value = checksum.getValue();
         * System.out.printf("Checksum: 0x%016X%n", value);
         * }</pre>
         *
         * @return the current checksum value; for checksums smaller than
         *         64 bits, the value is in the lower bits
         *
         * @see #getValueAsInt()
         * @see #getBytes()
         */
        long getValue();

        /**
         * Returns the current checksum value as a 32-bit integer.
         *
         * <p>This convenience method returns the lower 32 bits of the checksum
         * value. For 32-bit checksum algorithms (like CRC-32), this returns
         * the complete value. For 64-bit algorithms, only the lower 32 bits
         * are returned.</p>
         *
         * <p>This method is useful for:</p>
         * <ul>
         *   <li>Storing checksums in 32-bit format fields</li>
         *   <li>Compatibility with APIs expecting 32-bit values</li>
         *   <li>Name hashing where 32 bits is sufficient</li>
         * </ul>
         *
         * <p><strong>:</strong></p>
         * <pre>{@code
         * // For chunk headers that use 32-bit checksums
         * int checksum32 = calc.getValueAsInt();
         * chunkHeader.setChecksum(checksum32);
         * }</pre>
         *
         * @return the lower 32 bits of the checksum value; equivalent to
         *         {@code (int) getValue()}
         *
         * @see #getValue()
         */
        default int getValueAsInt() {
            return (int) getValue();
        }

        /**
         * Resets the checksum to its initial state.
         *
         * <p>After calling this method, the checksum calculator is in the
         * same state as a newly created instance. All previously added
         * data is discarded, and subsequent {@link #getValue()} calls
         * will return the checksum of only the data added after the reset.</p>
         *
         * <p>This method allows reusing a single calculator instance for
         * multiple independent computations, avoiding the overhead of
         * creating new instances.</p>
         *
         * <p><strong>:</strong></p>
         * <pre>{@code
         * Checksum calc = provider.createChecksum();
         *
         * // First computation
         * calc.update(data1);
         * long checksum1 = calc.getValue();
         *
         * // Reset and reuse for second computation
         * calc.reset();
         * calc.update(data2);
         * long checksum2 = calc.getValue();
         * }</pre>
         *
         * @see ChecksumProvider#createChecksum()
         */
        void reset();

        /**
         * Returns the checksum value as a byte array.
         *
         * <p>This method converts the checksum value to its byte representation.
         * The returned array has a fixed length of 8 bytes (64 bits), with the
         * value encoded in big-endian byte order (most significant byte first).</p>
         *
         * <p>For checksums smaller than 64 bits, the upper bytes will be zero.
         * For example, a 32-bit CRC-32 checksum would have the first 4 bytes
         * as zeros.</p>
         *
         * <p><strong>:</strong></p>
         * <p>The bytes are returned in big-endian order:</p>
         * <pre>
         * index: [0]   [1]   [2]   [3]   [4]   [5]   [6]   [7]
         * bits:  63-56 55-48 47-40 39-32 31-24 23-16 15-8  7-0
         * </pre>
         *
         * <p><strong>:</strong></p>
         * <pre>{@code
         * byte[] checksumBytes = calc.getBytes();
         * // Write checksum to output stream
         * output.write(checksumBytes);
         * }</pre>
         *
         * @return a new 8-byte array containing the checksum value in big-endian
         *         byte order; never {@code null}; the returned array is a new
         *         instance each call
         *
         * @see #getValue()
         */
        default byte @NotNull [] getBytes() {
            final long value = getValue();
            return new byte[] {
                    (byte) (value >> 56),
                    (byte) (value >> 48),
                    (byte) (value >> 40),
                    (byte) (value >> 32),
                    (byte) (value >> 24),
                    (byte) (value >> 16),
                    (byte) (value >> 8),
                    (byte) value
            };
        }

        /**
         * Updates the checksum with a single byte.
         *
         * <p>This convenience method accepts a {@code byte} directly instead
         * of an {@code int}. It converts the signed byte to an unsigned value
         * and delegates to {@link #update(int)}.</p>
         *
         * <p>The conversion is: {@code update(b & 0xFF)}</p>
         *
         * <p><strong>:</strong></p>
         * <pre>{@code
         * byte[] data = ...;
         * for (byte b : data) {
         *     checksum.update(b);
         * }
         * // Equivalent but less efficient than:
         * // checksum.update(data);
         * }</pre>
         *
         * @param b the byte to add to the checksum; treated as unsigned (0-255)
         *
         * @see #update(int)
         * @see #update(byte[])
         */
        default void update(final byte b) {
            update(b & 0xFF);
        }

    }

}
