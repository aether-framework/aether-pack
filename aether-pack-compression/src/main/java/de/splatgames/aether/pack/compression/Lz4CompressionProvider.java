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

package de.splatgames.aether.pack.compression;

import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.core.spi.CompressionProvider;
import net.jpountz.lz4.*;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * LZ4 compression provider implementation.
 *
 * <p>This class implements the {@link CompressionProvider} interface using
 * LZ4, an extremely fast compression algorithm. LZ4 prioritizes speed over
 * compression ratio, making it ideal for real-time applications, caching,
 * and latency-sensitive use cases.</p>
 *
 * <h2>Why Choose LZ4</h2>
 * <p>LZ4 is the best choice when:</p>
 * <ul>
 *   <li><strong>Speed is critical:</strong> LZ4 is one of the fastest compression
 *       algorithms available, with speeds exceeding 500 MB/s</li>
 *   <li><strong>Real-time processing:</strong> Low latency compression/decompression
 *       for streaming or interactive applications</li>
 *   <li><strong>CPU constrained:</strong> Minimal CPU usage compared to other algorithms</li>
 *   <li><strong>Temporary storage:</strong> Caching, IPC, or short-lived data</li>
 * </ul>
 *
 * <h2>Compression Modes</h2>
 * <table>
 *   <caption>LZ4 Compression Modes</caption>
 *   <tr><th>Level</th><th>Mode</th><th>Speed</th><th>Ratio</th><th>Use Case</th></tr>
 *   <tr><td>0</td><td>Fast</td><td>Extremely fast</td><td>Moderate</td><td>Default, real-time (recommended)</td></tr>
 *   <tr><td>1-9</td><td>HC Low</td><td>Fast</td><td>Better</td><td>Slightly better ratio needed</td></tr>
 *   <tr><td>10-17</td><td>HC High</td><td>Moderate</td><td>Good</td><td>Maximum LZ4 ratio</td></tr>
 * </table>
 *
 * <p><strong>Note:</strong> Even at maximum HC level (17), LZ4's compression ratio
 * is typically lower than ZSTD level 1. Choose LZ4 for speed, ZSTD for ratio.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Get the provider
 * Lz4CompressionProvider lz4 = new Lz4CompressionProvider();
 *
 * // Fast mode (default, recommended)
 * byte[] compressed = lz4.compressBlock(data, 0);
 * byte[] decompressed = lz4.decompressBlock(compressed, data.length);
 *
 * // High compression mode
 * byte[] hcCompressed = lz4.compressBlock(data, 9);
 *
 * // Stream compression
 * try (OutputStream out = lz4.compress(fileOut, 0)) {
 *     out.write(data);
 * }
 * }</pre>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><strong>Fast mode (level 0):</strong> ~500-800 MB/s compression, ~2000+ MB/s decompression</li>
 *   <li><strong>HC mode (level 9):</strong> ~50-100 MB/s compression, ~2000+ MB/s decompression</li>
 *   <li><strong>Memory usage:</strong> Very low, constant regardless of data size</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is stateless and thread-safe. The underlying {@link net.jpountz.lz4.LZ4Factory}
 * instance is shared, but all compression operations create thread-local state.</p>
 *
 * <h2>Dependencies</h2>
 * <p>Requires the {@code lz4-java} library (org.lz4:lz4-java).</p>
 *
 * @see CompressionProvider
 * @see CompressionRegistry#lz4()
 * @see ZstdCompressionProvider
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class Lz4CompressionProvider implements CompressionProvider {

    /**
     * Algorithm identifier string for LZ4 compression.
     * This value is used for configuration and registry lookup.
     */
    public static final String ID = "lz4";

    /**
     * Default compression level (0, fast mode).
     *
     * <p>Level 0 uses the standard LZ4 fast compressor, which provides
     * the best compression speed. This is recommended for most use cases
     * where LZ4 is chosen.</p>
     */
    public static final int DEFAULT_LEVEL = 0;

    /**
     * Minimum compression level (0, fast mode).
     *
     * <p>Level 0 is the fast mode using the standard LZ4 algorithm.
     * Levels 1+ use the LZ4HC (High Compression) algorithm.</p>
     */
    public static final int MIN_LEVEL = 0;

    /**
     * Maximum compression level (17, HC maximum).
     *
     * <p>Level 17 is the maximum LZ4HC compression level. While it provides
     * the best compression ratio LZ4 can achieve, it's still typically
     * lower than ZSTD at any level.</p>
     */
    public static final int MAX_LEVEL = 17;

    /**
     * Shared LZ4 factory instance.
     *
     * <p>Uses {@link LZ4Factory#fastestInstance()} which selects the fastest
     * available implementation (native JNI if available, otherwise pure Java).</p>
     */
    private static final LZ4Factory FACTORY = LZ4Factory.fastestInstance();

    /**
     * Creates a new LZ4 compression provider.
     *
     * <p>The provider is stateless and can be shared across threads.
     * For most use cases, prefer using {@link CompressionRegistry#lz4()}
     * to get a cached instance.</p>
     */
    public Lz4CompressionProvider() {
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #ID}
     */
    @Override
    public @NotNull String getId() {
        return ID;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_LZ4}
     */
    @Override
    public int getNumericId() {
        return FormatConstants.COMPRESSION_LZ4;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #DEFAULT_LEVEL} (fast mode)
     */
    @Override
    public int getDefaultLevel() {
        return DEFAULT_LEVEL;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #MIN_LEVEL}
     */
    @Override
    public int getMinLevel() {
        return MIN_LEVEL;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #MAX_LEVEL}
     */
    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation wraps the output stream with an {@link LZ4BlockOutputStream}
     * using 64 KB blocks. The compressor is selected based on the level:</p>
     * <ul>
     *   <li>Level 0: Fast compressor (standard LZ4)</li>
     *   <li>Levels 1-17: High compression (LZ4HC)</li>
     * </ul>
     *
     * @param output the output stream to write compressed data to; must not be
     *               {@code null}
     * @param level  the compression level ({@value #MIN_LEVEL} to {@value #MAX_LEVEL});
     *               0 for fast mode, 1-17 for high compression
     * @return a new output stream that compresses data written to it;
     *         never returns {@code null}
     * @throws IOException if an I/O error occurs during stream initialization
     */
    @Override
    public @NotNull OutputStream compress(
            final @NotNull OutputStream output,
            final int level) throws IOException {

        final LZ4Compressor compressor = getCompressor(level);
        return new LZ4BlockOutputStream(output, 1 << 16, compressor);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation wraps the input stream with an {@link LZ4BlockInputStream}
     * using the fast decompressor. LZ4 decompression is extremely fast regardless
     * of the compression level used during compression.</p>
     *
     * @param input the input stream containing LZ4-compressed data; must not be
     *              {@code null}; must contain valid LZ4 block format data
     * @return a new input stream that decompresses data as it is read;
     *         never returns {@code null}
     * @throws IOException if an I/O error occurs during stream initialization
     */
    @Override
    public @NotNull InputStream decompress(final @NotNull InputStream input) throws IOException {
        return new LZ4BlockInputStream(input, FACTORY.fastDecompressor());
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation uses the LZ4 native block compression API for
     * optimal performance. The compressed output is trimmed to the exact size.</p>
     *
     * @param data  the data to compress; must not be {@code null}
     * @param level the compression level ({@value #MIN_LEVEL} to {@value #MAX_LEVEL});
     *              0 for fast mode, 1-17 for high compression
     * @return the compressed data; never returns {@code null}; may be larger
     *         than the input for incompressible data
     * @throws IOException if compression fails
     */
    @Override
    public byte @NotNull [] compressBlock(
            final byte @NotNull [] data,
            final int level) throws IOException {

        final LZ4Compressor compressor = getCompressor(level);
        final int maxLen = compressor.maxCompressedLength(data.length);
        final byte[] compressed = new byte[maxLen];
        final int compressedLen = compressor.compress(data, 0, data.length, compressed, 0, maxLen);

        // Return exact-sized array
        if (compressedLen == maxLen) {
            return compressed;
        }
        final byte[] result = new byte[compressedLen];
        System.arraycopy(compressed, 0, result, 0, compressedLen);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation uses the LZ4 fast decompressor which requires
     * knowing the original size in advance. This is the fastest decompression
     * mode available in LZ4.</p>
     *
     * @param compressedData the compressed data; must not be {@code null};
     *                       must contain valid LZ4-compressed data
     * @param originalSize   the expected size of the decompressed data in bytes;
     *                       must match the actual decompressed size exactly
     * @return the decompressed data; never returns {@code null};
     *         length equals {@code originalSize}
     * @throws IOException if decompression fails or the data is corrupted
     */
    @Override
    public byte @NotNull [] decompressBlock(
            final byte @NotNull [] compressedData,
            final int originalSize) throws IOException {

        try {
            final LZ4FastDecompressor decompressor = FACTORY.fastDecompressor();
            final byte[] result = new byte[originalSize];
            decompressor.decompress(compressedData, 0, result, 0, originalSize);
            return result;
        } catch (final LZ4Exception e) {
            throw new IOException("LZ4 decompression failed", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>LZ4's worst-case expansion is relatively small. This method delegates
     * to the LZ4 library's {@code maxCompressedLength} calculation.</p>
     *
     * @param inputSize the size of the input data in bytes
     * @return the maximum possible compressed size in bytes
     */
    @Override
    public int maxCompressedSize(final int inputSize) {
        return FACTORY.fastCompressor().maxCompressedLength(inputSize);
    }

    /**
     * Selects the appropriate compressor for the given compression level.
     *
     * <p>Level 0 returns the fast compressor (standard LZ4 algorithm).
     * Levels 1-17 return the high compressor (LZ4HC) with the specified level.</p>
     *
     * @param level the compression level; 0 for fast mode, 1-17 for HC mode
     * @return the appropriate compressor instance; never returns {@code null}
     */
    private static @NotNull LZ4Compressor getCompressor(final int level) {
        if (level == 0) {
            return FACTORY.fastCompressor();
        }
        return FACTORY.highCompressor(level);
    }

}
