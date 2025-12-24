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

import de.splatgames.aether.pack.core.exception.ChecksumException;
import de.splatgames.aether.pack.core.spi.ChecksumProvider;
import de.splatgames.aether.pack.core.spi.ChecksumProvider.Checksum;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Pipeline stage for checksum computation and verification.
 *
 * <p>This stage computes checksums on data as it passes through the
 * pipeline. During writes, the computed checksum is stored in the
 * context. During reads, the checksum can be verified against an
 * expected value to detect data corruption.</p>
 *
 * <h2>Processing Order</h2>
 * <pre>
 * Write: Raw Data → [Checksum] → [Compress] → [Encrypt] → Output
 * Read:  Input → [Decrypt] → [Decompress] → [Checksum] → Raw Data
 * </pre>
 *
 * <p>At priority 100, this is the innermost layer, meaning checksums
 * are computed on the original uncompressed data. This is important
 * because it ensures data integrity verification happens on the
 * actual content, not compressed or encrypted representations.</p>
 *
 * <h2>Context Values</h2>
 * <p>This stage updates the following context values:</p>
 * <ul>
 *   <li>{@link PipelineContext#CHECKSUM} - The computed checksum (64-bit)</li>
 *   <li>{@link PipelineContext#CHECKSUM_BYTES} - Raw bytes (for &gt;64-bit checksums)</li>
 * </ul>
 *
 * <h2>Verification</h2>
 * <p>When configured with {@link ChecksumConfig#forRead}, the stage
 * will throw a {@link ChecksumException} wrapped in an {@link IOException}
 * if the computed checksum doesn't match the expected value.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ChecksumProvider xxh3 = ChecksumRegistry.getDefault();
 *
 * // For writing
 * ProcessingPipeline writePipeline = ProcessingPipeline.builder()
 *     .addStage(new ChecksumStage(), ChecksumConfig.forWrite(xxh3))
 *     .build();
 *
 * PipelineContext ctx = new PipelineContext();
 * try (OutputStream out = writePipeline.wrapOutput(fileOut, ctx)) {
 *     out.write(data);
 * }
 * long checksum = ctx.getRequired(PipelineContext.CHECKSUM);
 *
 * // For reading with verification
 * ProcessingPipeline readPipeline = ProcessingPipeline.builder()
 *     .addStage(new ChecksumStage(), ChecksumConfig.forRead(xxh3, checksum))
 *     .build();
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is stateless and thread-safe. Each stream wrapping
 * operation creates new independent checksum calculator instances.</p>
 *
 * @see ChecksumConfig
 * @see ChecksumProvider
 * @see PipelineStage
 * @see de.splatgames.aether.pack.core.exception.ChecksumException
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ChecksumStage implements PipelineStage<ChecksumConfig> {

    /** Stage order priority (100 = innermost layer). */
    public static final int ORDER = 100;

    /** Stage identifier. */
    public static final String ID = "checksum";

    /**
     * Creates a new checksum stage instance.
     *
     * <p>The checksum stage is stateless and can be safely shared across
     * multiple pipelines. Each invocation of {@link #wrapOutput} or
     * {@link #wrapInput} creates independent checksum calculator instances,
     * so a single stage instance can be used concurrently.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * ChecksumStage stage = new ChecksumStage();
     *
     * // Can be reused across multiple pipelines
     * ProcessingPipeline pipeline1 = ProcessingPipeline.builder()
     *     .addStage(stage, ChecksumConfig.forWrite(xxh3))
     *     .build();
     *
     * ProcessingPipeline pipeline2 = ProcessingPipeline.builder()
     *     .addStage(stage, ChecksumConfig.forRead(xxh3, expectedChecksum))
     *     .build();
     * }</pre>
     *
     * @see ChecksumConfig
     * @see ProcessingPipeline.Builder#addStage(PipelineStage, Object)
     */
    public ChecksumStage() {
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
     * @return the order priority {@value #ORDER}; this places the checksum
     *         stage as the innermost layer, ensuring checksums are computed
     *         on the original uncompressed and unencrypted data
     */
    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The checksum stage is enabled when a checksum provider is configured
     * in the {@link ChecksumConfig}. When disabled, this stage passes data
     * through unchanged without computing any checksum.</p>
     *
     * @param config the checksum configuration to check; must not be {@code null}
     * @return {@code true} if a checksum provider is configured,
     *         {@code false} if the provider is {@code null}
     */
    @Override
    public boolean isEnabled(final @NotNull ChecksumConfig config) {
        return config.isEnabled();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method wraps the output stream with a {@link ChecksumOutputStream}
     * that computes a running checksum as data is written. When the stream is
     * closed, the computed checksum is stored in the pipeline context under
     * {@link PipelineContext#CHECKSUM}.</p>
     *
     * <p>If no checksum provider is configured, the original stream is
     * returned unchanged.</p>
     *
     * @param output  the underlying output stream to wrap; must not be {@code null}
     * @param config  the checksum configuration containing the provider; must not
     *                be {@code null}; if the provider is {@code null}, the output
     *                stream is returned unchanged
     * @param context the pipeline context where the computed checksum will be
     *                stored; must not be {@code null}; after the stream is closed,
     *                {@link PipelineContext#CHECKSUM} will contain the computed value
     * @return a wrapped output stream that computes checksums, or the original
     *         stream if no provider is configured; never returns {@code null}
     * @throws IOException if an I/O error occurs during stream setup
     */
    @Override
    public @NotNull OutputStream wrapOutput(
            final @NotNull OutputStream output,
            final @NotNull ChecksumConfig config,
            final @NotNull PipelineContext context) throws IOException {

        final ChecksumProvider provider = config.provider();
        if (provider == null) {
            return output;
        }

        return new ChecksumOutputStream(output, provider.createChecksum(), context);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method wraps the input stream with a {@link ChecksumInputStream}
     * that computes a running checksum as data is read. When the stream reaches
     * EOF or is closed, the computed checksum is stored in the pipeline context
     * and optionally verified against an expected value.</p>
     *
     * <p>If verification is enabled via {@link ChecksumConfig#forRead} and the
     * computed checksum doesn't match the expected value, an {@link IOException}
     * wrapping a {@link ChecksumException} is thrown.</p>
     *
     * @param input   the underlying input stream to wrap; must not be {@code null}
     * @param config  the checksum configuration containing the provider and
     *                verification settings; must not be {@code null}; if the
     *                provider is {@code null}, the input stream is returned unchanged
     * @param context the pipeline context where the computed checksum will be
     *                stored; must not be {@code null}; after reading completes,
     *                {@link PipelineContext#CHECKSUM} will contain the computed value
     * @return a wrapped input stream that computes and optionally verifies checksums,
     *         or the original stream if no provider is configured; never returns
     *         {@code null}
     * @throws IOException if an I/O error occurs during stream setup
     */
    @Override
    public @NotNull InputStream wrapInput(
            final @NotNull InputStream input,
            final @NotNull ChecksumConfig config,
            final @NotNull PipelineContext context) throws IOException {

        final ChecksumProvider provider = config.provider();
        if (provider == null) {
            return input;
        }

        return new ChecksumInputStream(
                input,
                provider.createChecksum(),
                context,
                config.verifyOnRead(),
                config.expectedChecksum()
        );
    }

    /**
     * Output stream wrapper that computes a checksum as data is written.
     *
     * <p>This internal class wraps an output stream and maintains a running
     * checksum of all data written through it. When the stream is closed,
     * the final checksum value is stored in the pipeline context for later
     * retrieval.</p>
     *
     * <p>The checksum is computed incrementally as data flows through,
     * so there is minimal memory overhead regardless of the data size.</p>
     *
     * @see ChecksumInputStream
     */
    private static final class ChecksumOutputStream extends OutputStream {

        /** The underlying output stream to write data to. */
        private final @NotNull OutputStream delegate;

        /** The checksum calculator for computing running checksum. */
        private final @NotNull Checksum checksum;

        /** The pipeline context for storing the final checksum value. */
        private final @NotNull PipelineContext context;

        /**
         * Constructs a new checksum output stream.
         *
         * <p>The stream is initialized with an empty checksum state. As data
         * is written, the checksum is updated incrementally. The final value
         * is only computed and stored when the stream is closed.</p>
         *
         * @param delegate the underlying output stream to write data to; must
         *                 not be {@code null}; all write operations are delegated
         *                 to this stream after updating the checksum
         * @param checksum the checksum calculator instance; must not be {@code null};
         *                 should be a fresh instance from
         *                 {@link ChecksumProvider#createChecksum()}
         * @param context  the pipeline context where the final checksum will be
         *                 stored when the stream is closed; must not be {@code null};
         *                 the checksum is stored under {@link PipelineContext#CHECKSUM}
         */
        ChecksumOutputStream(
                final @NotNull OutputStream delegate,
                final @NotNull Checksum checksum,
                final @NotNull PipelineContext context) {
            this.delegate = delegate;
            this.checksum = checksum;
            this.context = context;
        }

        /**
         * Writes a single byte, passing it to the delegate and updating the checksum.
         *
         * @param b the byte to write
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void write(final int b) throws IOException {
            this.delegate.write(b);
            this.checksum.update((byte) b);
        }

        /**
         * Writes a byte array, passing it to the delegate and updating the checksum.
         *
         * @param b   the byte array
         * @param off the start offset in the array
         * @param len the number of bytes to write
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void write(final byte @NotNull [] b, final int off, final int len) throws IOException {
            this.delegate.write(b, off, len);
            this.checksum.update(b, off, len);
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
         * Closes the stream and stores the computed checksum in the context.
         * <p>
         * The checksum is stored both as a 64-bit long value ({@link PipelineContext#CHECKSUM})
         * and, if the checksum is larger than 64 bits, as a byte array
         * ({@link PipelineContext#CHECKSUM_BYTES}).
         * </p>
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void close() throws IOException {
            this.delegate.close();
            // Store computed checksum in context
            this.context.set(PipelineContext.CHECKSUM, this.checksum.getValue());

            final byte[] checksumBytes = this.checksum.getBytes();
            if (checksumBytes != null && checksumBytes.length > 8) {
                this.context.set(PipelineContext.CHECKSUM_BYTES, checksumBytes);
            }
        }

    }

    /**
     * Input stream wrapper that computes and optionally verifies a checksum as data is read.
     *
     * <p>This internal class wraps an input stream and maintains a running
     * checksum of all data read through it. When the stream reaches EOF or
     * is closed, the final checksum value is stored in the pipeline context
     * and optionally verified against an expected value.</p>
     *
     * <p>The checksum is computed incrementally as data flows through,
     * so there is minimal memory overhead regardless of the data size.
     * Verification happens at the end of reading, not during.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>When verification is enabled and the computed checksum doesn't match
     * the expected value, an {@link IOException} wrapping a
     * {@link ChecksumException} is thrown. This typically indicates data
     * corruption that occurred after the original checksum was computed.</p>
     *
     * @see ChecksumOutputStream
     * @see ChecksumException
     */
    private static final class ChecksumInputStream extends InputStream {

        /** The underlying input stream to read data from. */
        private final @NotNull InputStream delegate;

        /** The checksum calculator for computing running checksum. */
        private final @NotNull Checksum checksum;

        /** The pipeline context for storing the final checksum value. */
        private final @NotNull PipelineContext context;

        /** Whether to verify the checksum against the expected value. */
        private final boolean verify;

        /** The expected checksum value for verification. */
        private final long expectedChecksum;

        /** Whether checksum finalization has been performed. */
        private boolean finished;

        /**
         * Constructs a new checksum input stream.
         *
         * <p>The stream is initialized with an empty checksum state. As data
         * is read, the checksum is updated incrementally. The final value
         * is computed and stored when EOF is reached or the stream is closed.</p>
         *
         * @param delegate         the underlying input stream to read data from;
         *                         must not be {@code null}; all read operations
         *                         are delegated to this stream before updating
         *                         the checksum
         * @param checksum         the checksum calculator instance; must not be
         *                         {@code null}; should be a fresh instance from
         *                         {@link ChecksumProvider#createChecksum()}
         * @param context          the pipeline context where the final checksum
         *                         will be stored; must not be {@code null}; the
         *                         checksum is stored under {@link PipelineContext#CHECKSUM}
         * @param verify           whether to verify the computed checksum against
         *                         the expected value; if {@code true} and checksums
         *                         don't match, an exception is thrown at EOF
         * @param expectedChecksum the expected checksum value to verify against;
         *                         only used if {@code verify} is {@code true};
         *                         should be the checksum computed when the data
         *                         was originally written
         */
        ChecksumInputStream(
                final @NotNull InputStream delegate,
                final @NotNull Checksum checksum,
                final @NotNull PipelineContext context,
                final boolean verify,
                final long expectedChecksum) {
            this.delegate = delegate;
            this.checksum = checksum;
            this.context = context;
            this.verify = verify;
            this.expectedChecksum = expectedChecksum;
            this.finished = false;
        }

        /**
         * Reads a single byte, updating the checksum and finishing if end of stream is reached.
         *
         * @return the byte read, or -1 if end of stream
         * @throws IOException if an I/O error occurs or checksum verification fails
         */
        @Override
        public int read() throws IOException {
            final int b = this.delegate.read();
            if (b == -1) {
                finish();
            } else {
                this.checksum.update((byte) b);
            }
            return b;
        }

        /**
         * Reads bytes into an array, updating the checksum and finishing if end of stream is reached.
         *
         * @param b   the byte array to read into
         * @param off the start offset in the array
         * @param len the maximum number of bytes to read
         * @return the number of bytes read, or -1 if end of stream
         * @throws IOException if an I/O error occurs or checksum verification fails
         */
        @Override
        public int read(final byte @NotNull [] b, final int off, final int len) throws IOException {
            final int read = this.delegate.read(b, off, len);
            if (read == -1) {
                finish();
            } else {
                this.checksum.update(b, off, read);
            }
            return read;
        }

        /**
         * Returns the number of bytes that can be read without blocking.
         *
         * @return the number of available bytes
         * @throws IOException if an I/O error occurs
         */
        @Override
        public int available() throws IOException {
            return this.delegate.available();
        }

        /**
         * Closes the stream and performs checksum finalization and verification.
         *
         * @throws IOException if an I/O error occurs or checksum verification fails
         */
        @Override
        public void close() throws IOException {
            this.delegate.close();
            finish();
        }

        /**
         * Finalizes checksum computation and optionally verifies it against the expected value.
         * <p>
         * This method stores the computed checksum in the context and, if verification is
         * enabled, compares it to the expected value. If the checksums don't match, an
         * {@link IOException} wrapping a {@link ChecksumException} is thrown.
         * </p>
         * <p>
         * This method is idempotent - calling it multiple times has no effect after the
         * first call.
         * </p>
         *
         * @throws IOException if checksum verification fails
         */
        private void finish() throws IOException {
            if (this.finished) {
                return;
            }
            this.finished = true;

            final long computed = this.checksum.getValue();
            this.context.set(PipelineContext.CHECKSUM, computed);

            final byte[] checksumBytes = this.checksum.getBytes();
            if (checksumBytes != null && checksumBytes.length > 8) {
                this.context.set(PipelineContext.CHECKSUM_BYTES, checksumBytes);
            }

            if (this.verify && computed != this.expectedChecksum) {
                throw new IOException(new ChecksumException(
                        "Checksum verification failed", this.expectedChecksum, computed));
            }
        }

    }

}
