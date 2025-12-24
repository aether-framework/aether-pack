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

package de.splatgames.aether.pack.core.pipeline;

import de.splatgames.aether.pack.core.spi.CompressionProvider;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Pipeline stage for data compression and decompression.
 *
 * <p>This stage wraps I/O streams with compression (on write) or
 * decompression (on read) using a pluggable {@link CompressionProvider}.
 * It operates at priority 200, placing it between checksum (100) and
 * encryption (300) in the pipeline.</p>
 *
 * <h2>Processing Order</h2>
 * <pre>
 * Write: Raw Data → [Checksum] → [Compress] → [Encrypt] → Output
 * Read:  Input → [Decrypt] → [Decompress] → [Checksum] → Raw Data
 * </pre>
 *
 * <p>Compression is applied after checksum computation, ensuring that
 * checksums are calculated on the original uncompressed data.</p>
 *
 * <h2>Context Values</h2>
 * <p>This stage updates the following context values:</p>
 * <ul>
 *   <li>{@link PipelineContext#COMPRESSION_ID} - The algorithm ID used</li>
 *   <li>{@link PipelineContext#ORIGINAL_SIZE} - Bytes written to compression stream</li>
 * </ul>
 *
 * <h2>Supported Algorithms</h2>
 * <ul>
 *   <li><strong>ZSTD</strong> - High compression ratio, levels 1-22</li>
 *   <li><strong>LZ4</strong> - Very fast, levels 1-12</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * CompressionProvider zstd = CompressionRegistry.requireByName("zstd");
 * CompressionConfig config = CompressionConfig.of(zstd, 3);
 *
 * ProcessingPipeline pipeline = ProcessingPipeline.builder()
 *     .addStage(new CompressionStage(), config)
 *     .build();
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is stateless and thread-safe. Each stream wrapping
 * operation creates new independent stream instances.</p>
 *
 * @see CompressionConfig
 * @see CompressionProvider
 * @see PipelineStage
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class CompressionStage implements PipelineStage<CompressionConfig> {

    /** Stage order priority (200 = middle layer). */
    public static final int ORDER = 200;

    /** Stage identifier. */
    public static final String ID = "compression";

    /**
     * Creates a new compression stage instance.
     *
     * <p>The compression stage is stateless and can be safely shared across
     * multiple pipelines. Each invocation of {@link #wrapOutput} or
     * {@link #wrapInput} creates independent compression stream instances,
     * so a single stage instance can be used concurrently.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * CompressionStage stage = new CompressionStage();
     *
     * // Can be reused with different configurations
     * ProcessingPipeline fastPipeline = ProcessingPipeline.builder()
     *     .addStage(stage, CompressionConfig.of(zstd, 1))  // Fast
     *     .build();
     *
     * ProcessingPipeline smallPipeline = ProcessingPipeline.builder()
     *     .addStage(stage, CompressionConfig.of(zstd, 19))  // Best ratio
     *     .build();
     * }</pre>
     *
     * @see CompressionConfig
     * @see ProcessingPipeline.Builder#addStage(PipelineStage, Object)
     */
    public CompressionStage() {
    }

    /**
     * {@inheritDoc}
     *
     * @return the stage identifier "{@value #ID}"; this ID is used to
     *         prevent duplicate stage registration in a pipeline and
     *         for debugging/logging purposes
     */
    @Override
    public @NotNull String getId() {
        return ID;
    }

    /**
     * {@inheritDoc}
     *
     * @return the order priority {@value #ORDER}; this places the compression
     *         stage between checksum (100) and encryption (300), ensuring
     *         data is compressed after checksum computation and before encryption
     */
    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The compression stage is enabled when a compression provider is
     * configured in the {@link CompressionConfig}. When disabled, this stage
     * passes data through unchanged without any compression.</p>
     *
     * @param config the compression configuration to check; must not be {@code null}
     * @return {@code true} if a compression provider is configured,
     *         {@code false} if the provider is {@code null}
     */
    @Override
    public boolean isEnabled(final @NotNull CompressionConfig config) {
        return config.isEnabled();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method wraps the output stream with a compressing stream from
     * the configured provider. Data written to the returned stream is
     * compressed before being passed to the underlying stream.</p>
     *
     * <p>The compression algorithm ID is stored in the pipeline context under
     * {@link PipelineContext#COMPRESSION_ID}, and the original (uncompressed)
     * byte count is stored under {@link PipelineContext#ORIGINAL_SIZE} when
     * the stream is closed.</p>
     *
     * <p>If no compression provider is configured, the original stream is
     * returned unchanged.</p>
     *
     * @param output  the underlying output stream to wrap; must not be {@code null};
     *                compressed data will be written to this stream
     * @param config  the compression configuration containing the provider and
     *                level; must not be {@code null}; if the provider is {@code null},
     *                the output stream is returned unchanged
     * @param context the pipeline context where compression metadata will be
     *                stored; must not be {@code null}; after the stream is closed,
     *                {@link PipelineContext#ORIGINAL_SIZE} will contain the
     *                uncompressed byte count
     * @return a wrapped output stream that compresses data, or the original
     *         stream if no provider is configured; never returns {@code null}
     * @throws IOException if an I/O error occurs during compression stream setup
     */
    @Override
    public @NotNull OutputStream wrapOutput(
            final @NotNull OutputStream output,
            final @NotNull CompressionConfig config,
            final @NotNull PipelineContext context) throws IOException {

        final CompressionProvider provider = config.provider();
        if (provider == null) {
            return output;
        }

        // Store compression info in context
        context.set(PipelineContext.COMPRESSION_ID, provider.getNumericId());

        return new CountingCompressionOutputStream(
                provider.compress(output, config.level()),
                context
        );
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method wraps the input stream with a decompressing stream from
     * the configured provider. Data read from the returned stream is
     * automatically decompressed.</p>
     *
     * <p>If no compression provider is configured, the original stream is
     * returned unchanged.</p>
     *
     * @param input   the underlying input stream to wrap; must not be {@code null};
     *                compressed data will be read from this stream
     * @param config  the compression configuration containing the provider; must
     *                not be {@code null}; if the provider is {@code null}, the
     *                input stream is returned unchanged
     * @param context the pipeline context for sharing state; must not be {@code null};
     *                not currently used by this method but required by the interface
     * @return a wrapped input stream that decompresses data, or the original
     *         stream if no provider is configured; never returns {@code null}
     * @throws IOException if an I/O error occurs during decompression stream setup
     */
    @Override
    public @NotNull InputStream wrapInput(
            final @NotNull InputStream input,
            final @NotNull CompressionConfig config,
            final @NotNull PipelineContext context) throws IOException {

        final CompressionProvider provider = config.provider();
        if (provider == null) {
            return input;
        }

        return provider.decompress(input);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method validates that the configured compression level is within
     * the supported range for the provider. Different compression algorithms
     * support different level ranges:</p>
     * <ul>
     *   <li><strong>ZSTD</strong>: Levels 1-22 (levels 20+ are very slow)</li>
     *   <li><strong>LZ4</strong>: Levels 1-12 (higher levels use HC mode)</li>
     * </ul>
     *
     * @param config the compression configuration to validate; must not be
     *               {@code null}; validation is only performed if a provider
     *               is configured
     * @throws IllegalArgumentException if the compression level is outside
     *         the supported range for the configured provider; the exception
     *         message includes the valid level range
     */
    @Override
    public void validateConfig(final @NotNull CompressionConfig config) {
        if (config.provider() != null && !config.provider().supportsLevel(config.level())) {
            throw new IllegalArgumentException(
                    "Compression level " + config.level() + " not supported by " +
                            config.provider().getId() + ". Valid range: " +
                            config.provider().getMinLevel() + " - " + config.provider().getMaxLevel());
        }
    }

    /**
     * Output stream wrapper that tracks the original (uncompressed) byte count.
     *
     * <p>This internal class wraps a compression stream and counts the bytes
     * written before compression. This provides the original data size, which
     * is stored in the pipeline context when the stream is closed.</p>
     *
     * <p>The byte count represents the uncompressed size, not the compressed
     * size. This is important for:</p>
     * <ul>
     *   <li>Calculating compression ratio</li>
     *   <li>Pre-allocating decompression buffers</li>
     *   <li>Progress reporting during decompression</li>
     * </ul>
     */
    private static final class CountingCompressionOutputStream extends OutputStream {

        /** The underlying compression stream to write data to. */
        private final @NotNull OutputStream delegate;

        /** The pipeline context for storing the original byte count. */
        private final @NotNull PipelineContext context;

        /** Counter tracking uncompressed bytes written. */
        private long bytesWritten;

        /**
         * Constructs a new counting compression output stream.
         *
         * <p>The stream wraps a compression stream and tracks all bytes written
         * to it. When closed, the total byte count (original size) is stored
         * in the pipeline context.</p>
         *
         * @param delegate the underlying compression stream to write data to;
         *                 must not be {@code null}; this should be a stream
         *                 returned by {@link CompressionProvider#compress}
         * @param context  the pipeline context where the byte count will be
         *                 stored when the stream is closed; must not be {@code null};
         *                 the count is stored under {@link PipelineContext#ORIGINAL_SIZE}
         */
        CountingCompressionOutputStream(
                final @NotNull OutputStream delegate,
                final @NotNull PipelineContext context) {
            this.delegate = delegate;
            this.context = context;
            this.bytesWritten = 0;
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
         * Writes a byte array and updates the byte counter.
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
         * Flushes the underlying stream.
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void flush() throws IOException {
            this.delegate.flush();
        }

        /**
         * Closes the stream and stores the original (uncompressed) size in the context.
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void close() throws IOException {
            this.delegate.close();
            // Update context with original size (uncompressed bytes written to this stream)
            this.context.set(PipelineContext.ORIGINAL_SIZE, this.bytesWritten);
        }

    }

}
