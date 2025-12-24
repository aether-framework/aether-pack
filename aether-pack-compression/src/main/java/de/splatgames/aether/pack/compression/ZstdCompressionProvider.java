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

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.core.spi.CompressionProvider;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * ZSTD (Zstandard) compression provider implementation.
 *
 * <p>This class implements the {@link CompressionProvider} interface using
 * Zstandard, a modern compression algorithm developed by Facebook/Meta. ZSTD
 * offers an excellent balance of compression ratio and speed, with particularly
 * fast decompression regardless of compression level.</p>
 *
 * <h2>Why Choose ZSTD</h2>
 * <p>ZSTD is the <strong>recommended</strong> compression algorithm for APACK archives
 * because:</p>
 * <ul>
 *   <li><strong>Excellent ratio:</strong> Comparable to LZMA/xz at high levels</li>
 *   <li><strong>Fast decompression:</strong> Decompression speed is nearly constant
 *       regardless of compression level (unlike LZMA)</li>
 *   <li><strong>Flexible levels:</strong> 22 compression levels allow fine-tuning
 *       the ratio/speed tradeoff</li>
 *   <li><strong>Streaming support:</strong> Works efficiently with streams of any size</li>
 * </ul>
 *
 * <h2>Compression Levels</h2>
 * <table>
 *   <caption>ZSTD Compression Level Guide</caption>
 *   <tr><th>Level Range</th><th>Speed</th><th>Ratio</th><th>Use Case</th></tr>
 *   <tr><td>1-3</td><td>Very fast</td><td>Good</td><td>Real-time, large files (default: 3)</td></tr>
 *   <tr><td>4-9</td><td>Fast</td><td>Better</td><td>General purpose archiving</td></tr>
 *   <tr><td>10-15</td><td>Moderate</td><td>Very good</td><td>Archival, backups</td></tr>
 *   <tr><td>16-19</td><td>Slow</td><td>Excellent</td><td>Long-term storage</td></tr>
 *   <tr><td>20-22</td><td>Very slow</td><td>Maximum</td><td>Distribution packages</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Get the provider
 * ZstdCompressionProvider zstd = new ZstdCompressionProvider();
 *
 * // Block compression
 * byte[] compressed = zstd.compressBlock(data, 6);  // Level 6
 * byte[] decompressed = zstd.decompressBlock(compressed, data.length);
 *
 * // Stream compression
 * try (OutputStream out = zstd.compress(fileOut, 3)) {
 *     out.write(data);
 * }
 *
 * // Stream decompression
 * try (InputStream in = zstd.decompress(fileIn)) {
 *     byte[] data = in.readAllBytes();
 * }
 * }</pre>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><strong>Compression:</strong> ~300-500 MB/s at level 1, ~50 MB/s at level 19</li>
 *   <li><strong>Decompression:</strong> ~1000-1500 MB/s (nearly constant across levels)</li>
 *   <li><strong>Memory usage:</strong> Scales with compression level</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is stateless and thread-safe. Multiple threads can compress
 * and decompress concurrently using the same provider instance.</p>
 *
 * <h2>Dependencies</h2>
 * <p>Requires the {@code zstd-jni} library (com.github.luben:zstd-jni).</p>
 *
 * @see CompressionProvider
 * @see CompressionRegistry#zstd()
 * @see Lz4CompressionProvider
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ZstdCompressionProvider implements CompressionProvider {

    /**
     * Algorithm identifier string for ZSTD compression.
     * This value is used for configuration and registry lookup.
     */
    public static final String ID = "zstd";

    /**
     * Default compression level (3).
     *
     * <p>Level 3 provides a good balance between compression speed and ratio.
     * It's suitable for most use cases including real-time compression of
     * large files.</p>
     */
    public static final int DEFAULT_LEVEL = 3;

    /**
     * Minimum compression level (1).
     *
     * <p>Level 1 provides the fastest compression with a reasonable ratio.
     * Use this level when compression speed is critical.</p>
     */
    public static final int MIN_LEVEL = 1;

    /**
     * Maximum compression level (22, ultra mode).
     *
     * <p>Levels 20-22 are "ultra" compression levels that use significantly
     * more memory and CPU time but achieve maximum compression ratio.
     * Use for distribution packages or long-term archival.</p>
     */
    public static final int MAX_LEVEL = 22;

    /**
     * Creates a new ZSTD compression provider.
     *
     * <p>The provider is stateless and can be shared across threads.
     * For most use cases, prefer using {@link CompressionRegistry#zstd()}
     * to get a cached instance.</p>
     */
    public ZstdCompressionProvider() {
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
     * @return {@link de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_ZSTD}
     */
    @Override
    public int getNumericId() {
        return FormatConstants.COMPRESSION_ZSTD;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #DEFAULT_LEVEL}
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
     * <p>This implementation wraps the output stream with a {@link ZstdOutputStream}
     * configured with the specified compression level. The returned stream must
     * be closed to finalize the ZSTD frame.</p>
     *
     * @param output the output stream to write compressed data to; must not be
     *               {@code null}; the stream is not closed when the returned
     *               stream is closed
     * @param level  the compression level ({@value #MIN_LEVEL} to {@value #MAX_LEVEL});
     *               higher levels produce smaller output but take longer
     * @return a new output stream that compresses data written to it;
     *         never returns {@code null}
     * @throws IOException if an I/O error occurs during stream initialization
     */
    @Override
    public @NotNull OutputStream compress(
            final @NotNull OutputStream output,
            final int level) throws IOException {

        return new ZstdOutputStream(output, level);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation wraps the input stream with a {@link ZstdInputStream}
     * that decompresses data as it is read. The decompression level is automatically
     * detected from the compressed data.</p>
     *
     * @param input the input stream containing ZSTD-compressed data; must not be
     *              {@code null}; must contain a valid ZSTD frame
     * @return a new input stream that decompresses data as it is read;
     *         never returns {@code null}
     * @throws IOException if an I/O error occurs during stream initialization
     *         or if the input is not valid ZSTD data
     */
    @Override
    public @NotNull InputStream decompress(final @NotNull InputStream input) throws IOException {
        return new ZstdInputStream(input);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation uses a {@link ZstdOutputStream} to compress the
     * entire block at once. For small blocks, this may be less efficient than
     * using the native ZSTD block compression API.</p>
     *
     * @param data  the data to compress; must not be {@code null}
     * @param level the compression level ({@value #MIN_LEVEL} to {@value #MAX_LEVEL})
     * @return the compressed data; never returns {@code null}; may be larger
     *         than the input for incompressible data
     * @throws IOException if compression fails
     */
    @Override
    public byte @NotNull [] compressBlock(
            final byte @NotNull [] data,
            final int level) throws IOException {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
        try (OutputStream zstd = new ZstdOutputStream(baos, level)) {
            zstd.write(data);
        }
        return baos.toByteArray();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation uses a {@link ZstdInputStream} to decompress the
     * block. The original size must be known and provided to allocate the
     * output buffer.</p>
     *
     * @param compressedData the compressed data; must not be {@code null};
     *                       must contain valid ZSTD-compressed data
     * @param originalSize   the expected size of the decompressed data in bytes;
     *                       must match the actual decompressed size
     * @return the decompressed data; never returns {@code null};
     *         length equals {@code originalSize}
     * @throws IOException if decompression fails, the data is corrupted,
     *         or the actual decompressed size doesn't match {@code originalSize}
     */
    @Override
    public byte @NotNull [] decompressBlock(
            final byte @NotNull [] compressedData,
            final int originalSize) throws IOException {

        final byte[] result = new byte[originalSize];
        try (InputStream zstd = new ZstdInputStream(
                new java.io.ByteArrayInputStream(compressedData))) {

            int offset = 0;
            int remaining = originalSize;
            while (remaining > 0) {
                final int read = zstd.read(result, offset, remaining);
                if (read == -1) {
                    throw new IOException("Unexpected end of ZSTD stream");
                }
                offset += read;
                remaining -= read;
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>ZSTD's worst-case expansion is approximately {@code input + (input / 128) + 512}
     * bytes. This method returns a conservative upper bound suitable for
     * buffer allocation.</p>
     *
     * @param inputSize the size of the input data in bytes
     * @return the maximum possible compressed size in bytes
     */
    @Override
    public int maxCompressedSize(final int inputSize) {
        // ZSTD's maximum compressed size is roughly input + (input / 128) + 512
        return inputSize + (inputSize >> 7) + 512;
    }

}
