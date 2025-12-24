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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Service Provider Interface for compression algorithms in APACK archives.
 *
 * <p>This interface defines the contract for pluggable compression providers.
 * Implementations provide both streaming and block-based compression/decompression
 * capabilities.</p>
 *
 * <h2>Built-in Implementations</h2>
 * <p>The {@code aether-pack-compression} module provides:</p>
 * <ul>
 *   <li><strong>ZSTD</strong> - High compression ratio with fast decompression</li>
 *   <li><strong>LZ4</strong> - Extremely fast compression/decompression</li>
 * </ul>
 *
 * <h2>Implementation Requirements</h2>
 * <ul>
 *   <li>Implementations <strong>must be thread-safe</strong> - multiple threads
 *       may call methods concurrently</li>
 *   <li>Implementations <strong>must be stateless</strong> - do not store any
 *       mutable state between calls</li>
 *   <li>Block methods should be able to compress/decompress data of any size
 *       within reasonable memory limits</li>
 * </ul>
 *
 * <h2>Registration</h2>
 * <p>Implementations can be registered via:</p>
 * <ul>
 *   <li>{@link java.util.ServiceLoader} - Add a META-INF/services file</li>
 *   <li>Programmatic registration via the compression registry</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Block compression
 * CompressionProvider zstd = CompressionRegistry.requireByName("zstd");
 * byte[] compressed = zstd.compressBlock(data, 6);
 * byte[] decompressed = zstd.decompressBlock(compressed, data.length);
 *
 * // Stream compression
 * try (OutputStream out = zstd.compress(fileOut, 3)) {
 *     out.write(data);
 * }
 *
 * try (InputStream in = zstd.decompress(fileIn)) {
 *     byte[] read = in.readAllBytes();
 * }
 * }</pre>
 *
 * <h2>Compression Levels</h2>
 * <p>Higher compression levels generally produce smaller output but take
 * longer to compress. The valid range varies by algorithm:</p>
 * <ul>
 *   <li><strong>ZSTD:</strong> 1-22 (default: 3)</li>
 *   <li><strong>LZ4:</strong> 1-12 (default: 9)</li>
 * </ul>
 *
 * @see EncryptionProvider
 * @see ChecksumProvider
 * @see de.splatgames.aether.pack.core.io.ChunkProcessor
 * @see de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_ZSTD
 * @see de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_LZ4
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface CompressionProvider {

    /**
     * Returns the unique string identifier for this compression algorithm.
     *
     * <p>The identifier is a human-readable string that uniquely identifies the
     * compression algorithm implementation. This identifier is used for configuration,
     * logging, and to look up providers in registries.</p>
     *
     * <p><strong>Naming Convention:</strong>
     * Identifiers should be lowercase and match the algorithm's common name:</p>
     * <ul>
     *   <li>{@code "zstd"} - Zstandard compression</li>
     *   <li>{@code "lz4"} - LZ4 compression</li>
     *   <li>{@code "gzip"} - GZIP compression (deflate with headers)</li>
     *   <li>{@code "deflate"} - Raw DEFLATE compression</li>
     * </ul>
     *
     * <p><strong>Uniqueness:</strong>
     * <p>Each provider implementation must return a unique identifier. Registering
     * multiple providers with the same ID will cause conflicts.</p>
     *
     * @return the unique algorithm identifier string; never {@code null};
     *         typically lowercase (e.g., "zstd", "lz4")
     *
     * @see #getNumericId()
     */
    @NotNull String getId();

    /**
     * Returns the numeric identifier for this compression algorithm.
     *
     * <p>The numeric ID is stored in the binary archive format to identify
     * which compression algorithm was used for each entry. When reading an
     * archive, this ID is used to look up the appropriate provider for
     * decompression.</p>
     *
     * <p><strong>Standard IDs:</strong></p>
     * <table>
     *   <caption>Reserved Compression Algorithm IDs</caption>
     *   <tr><th>ID</th><th>Algorithm</th><th>Constant</th></tr>
     *   <tr><td>0</td><td>None</td><td>{@link de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_NONE}</td></tr>
     *   <tr><td>1</td><td>ZSTD</td><td>{@link de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_ZSTD}</td></tr>
     *   <tr><td>2</td><td>LZ4</td><td>{@link de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_LZ4}</td></tr>
     * </table>
     *
     * <p><strong>Custom IDs:</strong>
     * <p>Custom implementations should use IDs >= 128 to avoid conflicts with
     * future standard algorithm assignments.</p>
     *
     * @return the numeric algorithm identifier; standard algorithms use values
     *         0-127; custom implementations should use 128+
     *
     * @see #getId()
     * @see de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_ZSTD
     * @see de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_LZ4
     */
    int getNumericId();

    /**
     * Returns the default compression level for this algorithm.
     *
     * <p>The default level represents a balanced trade-off between compression
     * ratio and speed for typical use cases. This level is used when no
     * explicit level is specified in compression methods.</p>
     *
     * <p><strong>Typical Defaults:</strong></p>
     * <table>
     *   <caption>Default Compression Levels</caption>
     *   <tr><th>Algorithm</th><th>Default</th><th>Characteristics</th></tr>
     *   <tr><td>ZSTD</td><td>3</td><td>Good ratio, fast compression</td></tr>
     *   <tr><td>LZ4</td><td>9</td><td>Maximum compression for LZ4</td></tr>
     * </table>
     *
     * @return the default compression level; always within the range
     *         [{@link #getMinLevel()}, {@link #getMaxLevel()}]
     *
     * @see #getMinLevel()
     * @see #getMaxLevel()
     * @see #compress(OutputStream)
     * @see #compressBlock(byte[])
     */
    int getDefaultLevel();

    /**
     * Returns the minimum supported compression level.
     *
     * <p>Lower compression levels typically result in faster compression
     * but larger output. The minimum level represents the fastest compression
     * setting available for this algorithm.</p>
     *
     * <p><strong>Typical Minimums:</strong></p>
     * <ul>
     *   <li><strong>ZSTD:</strong> 1 (fastest mode)</li>
     *   <li><strong>LZ4:</strong> 1 (fastest mode)</li>
     * </ul>
     *
     * @return the minimum compression level; always a positive integer or zero
     *
     * @see #getMaxLevel()
     * @see #getDefaultLevel()
     * @see #supportsLevel(int)
     */
    int getMinLevel();

    /**
     * Returns the maximum supported compression level.
     *
     * <p>Higher compression levels typically result in better compression
     * ratios but slower compression. The maximum level represents the
     * highest compression setting available for this algorithm.</p>
     *
     * <p><strong>Typical Maximums:</strong></p>
     * <ul>
     *   <li><strong>ZSTD:</strong> 22 (ultra mode for maximum compression)</li>
     *   <li><strong>LZ4:</strong> 12 (high compression mode)</li>
     * </ul>
     *
     * <p><strong>Performance Note:</strong>
     * <p>Maximum levels can be significantly slower than default levels.
     * Consider the trade-off between compression ratio and CPU time.</p>
     *
     * @return the maximum compression level; always >= {@link #getMinLevel()}
     *
     * @see #getMinLevel()
     * @see #getDefaultLevel()
     * @see #supportsLevel(int)
     */
    int getMaxLevel();

    /**
     * Checks if the specified compression level is supported by this provider.
     *
     * <p>This method validates that the given level falls within the valid
     * range for this compression algorithm. It is equivalent to:</p>
     * <pre>{@code
     * level >= getMinLevel() && level <= getMaxLevel()
     * }</pre>
     *
     * <p>Use this method to validate user input before calling compression
     * methods to provide meaningful error messages.</p>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * int userLevel = config.getCompressionLevel();
     * if (!provider.supportsLevel(userLevel)) {
     *     throw new IllegalArgumentException(
     *         "Compression level " + userLevel + " not supported. " +
     *         "Valid range: " + provider.getMinLevel() + "-" + provider.getMaxLevel());
     * }
     * }</pre>
     *
     * @param level the compression level to check
     * @return {@code true} if the level is within the valid range
     *         [{@link #getMinLevel()}, {@link #getMaxLevel()}]; {@code false}
     *         otherwise
     *
     * @see #getMinLevel()
     * @see #getMaxLevel()
     */
    default boolean supportsLevel(final int level) {
        return level >= getMinLevel() && level <= getMaxLevel();
    }

    /**
     * Creates a compressing output stream that wraps the given output stream.
     *
     * <p>Data written to the returned stream is compressed using this algorithm
     * at the specified compression level before being written to the underlying
     * output stream. The returned stream must be closed to finalize compression
     * and flush any buffered data.</p>
     *
     * <p><strong>Stream Lifecycle:</strong></p>
     * <ol>
     *   <li>Create the compressing stream</li>
     *   <li>Write data to the compressing stream</li>
     *   <li>Close the compressing stream (finalizes compression)</li>
     *   <li>The underlying stream is typically NOT closed automatically</li>
     * </ol>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * try (FileOutputStream fos = new FileOutputStream("data.zst");
     *      OutputStream compressed = provider.compress(fos, 6)) {
     *     compressed.write(data);
     * }  // Compressed data is finalized and written to file
     * }</pre>
     *
     * @param output the underlying output stream to write compressed data to;
     *               must not be {@code null}; the stream should be positioned
     *               at the desired write location
     * @param level  the compression level to use; must be within the range
     *               [{@link #getMinLevel()}, {@link #getMaxLevel()}]
     * @return a new output stream that compresses data before writing;
     *         never {@code null}; must be closed to finalize compression
     * @throws IOException if an I/O error occurs during stream creation
     * @throws IllegalArgumentException if the level is not supported
     *
     * @see #compress(OutputStream)
     * @see #decompress(InputStream)
     */
    @NotNull OutputStream compress(final @NotNull OutputStream output, final int level) throws IOException;

    /**
     * Creates a compressing output stream using the default compression level.
     *
     * <p>This convenience method creates a compressing stream using the
     * provider's default compression level. It is equivalent to:</p>
     * <pre>{@code
     * compress(output, getDefaultLevel())
     * }</pre>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * try (OutputStream compressed = provider.compress(outputStream)) {
     *     compressed.write(data);
     * }
     * }</pre>
     *
     * @param output the underlying output stream to write compressed data to;
     *               must not be {@code null}
     * @return a new output stream that compresses data before writing;
     *         never {@code null}; must be closed to finalize compression
     * @throws IOException if an I/O error occurs during stream creation
     *
     * @see #compress(OutputStream, int)
     * @see #getDefaultLevel()
     */
    default @NotNull OutputStream compress(final @NotNull OutputStream output) throws IOException {
        return compress(output, getDefaultLevel());
    }

    /**
     * Creates a decompressing input stream that wraps the given input stream.
     *
     * <p>Data read from the returned stream is decompressed from the compressed
     * data in the underlying input stream. The underlying stream should contain
     * data that was compressed with the same algorithm.</p>
     *
     * <p><strong>Stream Behavior:</strong></p>
     * <ul>
     *   <li>Reading returns decompressed bytes</li>
     *   <li>EOF is reached when all compressed data has been decompressed</li>
     *   <li>Closing the decompressing stream releases internal resources</li>
     *   <li>The underlying stream is typically NOT closed automatically</li>
     * </ul>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * try (FileInputStream fis = new FileInputStream("data.zst");
     *      InputStream decompressed = provider.decompress(fis)) {
     *     byte[] originalData = decompressed.readAllBytes();
     * }
     * }</pre>
     *
     * @param input the underlying input stream containing compressed data;
     *              must not be {@code null}; the stream should be positioned
     *              at the start of the compressed data
     * @return a new input stream that decompresses data while reading;
     *         never {@code null}
     * @throws IOException if an I/O error occurs during stream creation,
     *                     or if the stream doesn't contain valid compressed data
     *
     * @see #compress(OutputStream, int)
     * @see #decompressBlock(byte[], int)
     */
    @NotNull InputStream decompress(final @NotNull InputStream input) throws IOException;

    /**
     * Compresses a byte array in memory at the specified compression level.
     *
     * <p>This method performs block compression, where the entire input is
     * compressed in a single operation and returned as a new byte array.
     * This is efficient for small to medium-sized data that fits in memory.</p>
     *
     * <p><strong>Memory Usage:</strong>
     * Block compression requires memory proportional to the input size.
     * For very large data, consider using streaming compression via
     * {@link #compress(OutputStream, int)} instead.</p>
     *
     * <p><strong>Incompressible Data:</strong>
     * For incompressible data (e.g., already compressed files, random data),
     * the output may be larger than the input due to compression overhead.
     * Use {@link #maxCompressedSize(int)} to estimate the worst case.</p>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * byte[] original = Files.readAllBytes(path);
     * byte[] compressed = provider.compressBlock(original, 6);
     * System.out.printf("Compressed %d bytes to %d bytes (%.1f%%)%n",
     *     original.length, compressed.length,
     *     100.0 * compressed.length / original.length);
     * }</pre>
     *
     * @param data  the data to compress; must not be {@code null}; can be empty
     * @param level the compression level; must be within the range
     *              [{@link #getMinLevel()}, {@link #getMaxLevel()}]
     * @return a new byte array containing the compressed data; never {@code null};
     *         may be larger than input for incompressible data
     * @throws IOException if compression fails due to algorithm errors
     * @throws IllegalArgumentException if the level is not supported
     *
     * @see #compressBlock(byte[])
     * @see #decompressBlock(byte[], int)
     * @see #maxCompressedSize(int)
     */
    byte @NotNull [] compressBlock(final byte @NotNull [] data, final int level) throws IOException;

    /**
     * Compresses a byte array in memory using the default compression level.
     *
     * <p>This convenience method performs block compression using the provider's
     * default compression level. It is equivalent to:</p>
     * <pre>{@code
     * compressBlock(data, getDefaultLevel())
     * }</pre>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * byte[] compressed = provider.compressBlock(originalData);
     * }</pre>
     *
     * @param data the data to compress; must not be {@code null}; can be empty
     * @return a new byte array containing the compressed data; never {@code null}
     * @throws IOException if compression fails due to algorithm errors
     *
     * @see #compressBlock(byte[], int)
     * @see #getDefaultLevel()
     */
    default byte @NotNull [] compressBlock(final byte @NotNull [] data) throws IOException {
        return compressBlock(data, getDefaultLevel());
    }

    /**
     * Decompresses a byte array in memory.
     *
     * <p>This method performs block decompression, where the entire compressed
     * input is decompressed in a single operation and returned as a new byte
     * array. The original size hint is used for efficient buffer allocation.</p>
     *
     * <p><strong>Original Size Hint:</strong>
     * The {@code originalSize} parameter helps allocate an appropriately
     * sized buffer. If the hint is too small, the implementation may need to
     * reallocate. If it's too large, memory is wasted. Some compression formats
     * (like ZSTD) include the original size in the compressed data, in which
     * case the hint may be ignored.</p>
     *
     * <p><strong>Error Handling:</strong>
     * Decompression will fail if:</p>
     * <ul>
     *   <li>The data was not compressed with the same algorithm</li>
     *   <li>The compressed data is corrupted or truncated</li>
     *   <li>The original size hint is incorrect (for some algorithms)</li>
     * </ul>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * // Read compressed chunk from archive
     * byte[] compressed = readChunk(offset, storedSize);
     *
     * // Decompress using known original size
     * byte[] decompressed = provider.decompressBlock(compressed, originalSize);
     * assert decompressed.length == originalSize;
     * }</pre>
     *
     * @param compressedData the compressed data to decompress; must not be
     *                       {@code null}; must contain valid compressed data
     * @param originalSize   the expected decompressed size in bytes; used as a
     *                       hint for buffer allocation; must be non-negative
     * @return a new byte array containing the decompressed data; never {@code null};
     *         length should equal the actual original size
     * @throws IOException if decompression fails due to corrupted data,
     *                     wrong algorithm, or other errors
     *
     * @see #compressBlock(byte[], int)
     * @see #decompress(InputStream)
     */
    byte @NotNull [] decompressBlock(final byte @NotNull [] compressedData, final int originalSize) throws IOException;

    /**
     * Estimates the maximum possible compressed size for the given input size.
     *
     * <p>This method returns an upper bound on the compressed size, useful for
     * pre-allocating buffers. The actual compressed size depends on the data's
     * compressibility and may be much smaller (for compressible data) or close
     * to this estimate (for incompressible data).</p>
     *
     * <p><strong>Why Compressed Size Can Exceed Input:</strong>
     * Compression algorithms add overhead (headers, dictionaries, etc.) that
     * can make incompressible data slightly larger. This method accounts for
     * that worst-case scenario.</p>
     *
     * <p><strong>Default Estimate:</strong>
     * The default implementation uses a conservative estimate:</p>
     * <pre>
     * maxSize = inputSize + (inputSize / 10) + 64
     * </pre>
     * <p>This adds 10% plus a fixed overhead of 64 bytes for headers.</p>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * int inputSize = data.length;
     * int maxCompressed = provider.maxCompressedSize(inputSize);
     * ByteBuffer buffer = ByteBuffer.allocate(maxCompressed);
     *
     * byte[] compressed = provider.compressBlock(data, 3);
     * assert compressed.length <= maxCompressed;
     * }</pre>
     *
     * @param inputSize the size of the input data in bytes; must be non-negative
     * @return the maximum possible compressed size in bytes; always >= inputSize
     *         for the worst case of incompressible data
     *
     * @see #compressBlock(byte[], int)
     */
    default int maxCompressedSize(final int inputSize) {
        // Default conservative estimate: input + 10% + 64 bytes header
        return inputSize + (inputSize / 10) + 64;
    }

}
