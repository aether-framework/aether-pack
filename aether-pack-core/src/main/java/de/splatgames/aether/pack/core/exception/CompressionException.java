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

package de.splatgames.aether.pack.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when compression or decompression fails.
 *
 * <p>This exception indicates that a compression operation could not
 * be completed. This can occur during both writing (compression) and
 * reading (decompression) of compressed entries.</p>
 *
 * <h2>Common Causes</h2>
 * <ul>
 *   <li><strong>Invalid compressed data</strong> - Corrupted or truncated compressed stream</li>
 *   <li><strong>Unknown algorithm</strong> - Compression algorithm ID not recognized</li>
 *   <li><strong>Algorithm unavailable</strong> - Required codec not on classpath</li>
 *   <li><strong>Buffer overflow</strong> - Decompressed data exceeds expected size</li>
 *   <li><strong>Dictionary mismatch</strong> - Wrong or missing compression dictionary</li>
 * </ul>
 *
 * <h2>Supported Algorithms</h2>
 * <ul>
 *   <li><strong>ZSTD</strong> - High compression ratio, configurable levels 1-22</li>
 *   <li><strong>LZ4</strong> - Very fast compression/decompression</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (AetherPackReader reader = AetherPackReader.open(path)) {
 *     try (InputStream in = reader.openEntry("data.bin")) {
 *         // Read decompressed content...
 *     }
 * } catch (CompressionException e) {
 *     System.err.println("Decompression failed: " + e.getMessage());
 *     if (e.getCause() != null) {
 *         System.err.println("Cause: " + e.getCause().getMessage());
 *     }
 * }
 * }</pre>
 *
 * <h2>Recovery</h2>
 * <p>Compression exceptions are typically not recoverable without the original
 * uncompressed data. If the archive has error correction (ECC), the reader
 * may be able to repair corrupted chunks before decompression.</p>
 *
 * <h2>Module Dependencies</h2>
 * <p>Compression support requires the {@code aether-pack-compression} module
 * on the classpath. Without it, only uncompressed entries can be read.</p>
 *
 * @see ApackException
 * @see de.splatgames.aether.pack.core.spi.CompressionProvider
 * @see de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_ZSTD
 * @see de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_LZ4
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class CompressionException extends ApackException {

    /**
     * Creates a new compression exception with the specified detail message.
     *
     * <p>This constructor creates a compression exception without an underlying
     * cause. Use this constructor when the compression error is detected
     * directly by the APACK library, such as when encountering an unknown
     * compression algorithm ID or when decompressed data exceeds the expected
     * size.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The message should clearly describe the compression failure and
     * include relevant context:</p>
     * <ul>
     *   <li>The compression algorithm involved (if known)</li>
     *   <li>The operation that failed (compression or decompression)</li>
     *   <li>The entry name or chunk index (if applicable)</li>
     *   <li>Expected vs actual sizes (for buffer overflows)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>"Unknown compression algorithm ID: 0x42"</li>
     *   <li>"ZSTD compression provider not found on classpath"</li>
     *   <li>"Decompressed size 65536 exceeds declared size 32768"</li>
     *   <li>"Compression level 25 not supported by ZSTD (max: 22)"</li>
     * </ul>
     *
     * @param message the detail message describing the compression failure;
     *                must not be {@code null}; should provide sufficient
     *                context to diagnose the problem, including the
     *                compression algorithm, operation type, and any relevant
     *                size or level information; this message is returned by
     *                {@link #getMessage()} and included in stack traces
     *
     * @see #CompressionException(String, Throwable)
     * @see de.splatgames.aether.pack.core.spi.CompressionProvider
     */
    public CompressionException(final @NotNull String message) {
        super(message);
    }

    /**
     * Creates a new compression exception with the specified message and cause.
     *
     * <p>This constructor creates a compression exception that wraps an
     * underlying exception. Use this constructor when the compression error
     * is caused by another exception, such as when a native compression
     * library throws an error or when an I/O operation fails during
     * compression or decompression.</p>
     *
     * <p>The cause exception is preserved for diagnostic purposes and can
     * be retrieved using {@link #getCause()}. This creates an exception
     * chain that helps trace the root cause of the compression failure.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>{@link java.io.IOException} - I/O failure during streaming compression</li>
     *   <li>{@link RuntimeException} from native ZSTD or LZ4 libraries</li>
     *   <li>{@link IllegalArgumentException} - Invalid compression parameters</li>
     *   <li>{@link OutOfMemoryError} - Insufficient memory for compression buffers</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * try {
     *     byte[] compressed = zstdCompressor.compress(data);
     * } catch (RuntimeException e) {
     *     throw new CompressionException(
     *         "ZSTD compression failed for entry: " + entryName, e);
     * }
     * }</pre>
     *
     * @param message the detail message describing the compression failure;
     *                must not be {@code null}; should explain the high-level
     *                operation that failed, including context such as entry
     *                name, algorithm, or chunk index; the underlying cause
     *                provides additional technical details
     * @param cause   the underlying exception that caused the compression
     *                failure; may be {@code null} if no cause is available
     *                or applicable; if provided, can be retrieved via
     *                {@link #getCause()}; typically a native library exception
     *                or an I/O exception encountered during streaming
     *
     * @see #CompressionException(String)
     * @see de.splatgames.aether.pack.core.spi.CompressionProvider#compressBlock(byte[], int)
     * @see de.splatgames.aether.pack.core.spi.CompressionProvider#decompressBlock(byte[], int)
     */
    public CompressionException(final @NotNull String message, final @Nullable Throwable cause) {
        super(message, cause);
    }

}
