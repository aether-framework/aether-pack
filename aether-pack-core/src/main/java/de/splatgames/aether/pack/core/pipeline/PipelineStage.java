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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A stage in the data processing pipeline.
 *
 * <p>Pipeline stages are composable transformations that can be chained
 * together to form a processing pipeline. Each stage wraps I/O streams
 * to apply its transformation (compression, encryption, checksumming, etc.)
 * as data flows through.</p>
 *
 * <h2>Stage Ordering</h2>
 * <p>Stages have a priority order that determines the wrapping sequence:</p>
 * <ul>
 *   <li><strong>100: Checksum</strong> - Innermost, operates on raw data</li>
 *   <li><strong>200: Compression</strong> - Middle layer</li>
 *   <li><strong>300: Encryption</strong> - Outermost, applied last on write</li>
 * </ul>
 *
 * <p>During encoding (writing), stages wrap in ascending priority order.
 * During decoding (reading), the order is reversed (descending priority).</p>
 *
 * <h2>Built-in Stages</h2>
 * <ul>
 *   <li>{@link ChecksumStage} - Computes/verifies checksums (XXH3, CRC32)</li>
 *   <li>{@link CompressionStage} - Applies compression (ZSTD, LZ4)</li>
 *   <li>{@link EncryptionStage} - Encrypts/decrypts data (AES-GCM, ChaCha20)</li>
 * </ul>
 *
 * <h2>Implementation Requirements</h2>
 * <ul>
 *   <li>Implementations <strong>must be thread-safe</strong></li>
 *   <li>Implementations <strong>must be stateless</strong></li>
 *   <li>All mutable state should be stored in {@link PipelineContext}</li>
 *   <li>{@link #wrapOutput} and {@link #wrapInput} must return valid streams</li>
 * </ul>
 *
 * <h2>Custom Stage Example</h2>
 * <pre>{@code
 * public class LoggingStage implements PipelineStage<LoggingConfig> {
 *     public static final int ORDER = 50;  // Before checksum
 *
 *     @Override public String getId() { return "logging"; }
 *     @Override public int getOrder() { return ORDER; }
 *
 *     @Override
 *     public OutputStream wrapOutput(OutputStream out, LoggingConfig config,
 *                                     PipelineContext ctx) {
 *         return new FilterOutputStream(out) {
 *             private long count = 0;
 *             @Override public void write(int b) throws IOException {
 *                 super.write(b);
 *                 if (++count % 1_000_000 == 0) {
 *                     System.out.println("Written: " + count + " bytes");
 *                 }
 *             }
 *         };
 *     }
 *
 *     @Override
 *     public InputStream wrapInput(InputStream in, LoggingConfig config,
 *                                   PipelineContext ctx) {
 *         return in;  // Pass-through on read
 *     }
 * }
 * }</pre>
 *
 * @param <C> the configuration type for this stage
 *
 * @see ProcessingPipeline
 * @see PipelineContext
 * @see ChecksumStage
 * @see CompressionStage
 * @see EncryptionStage
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface PipelineStage<C> {

    /**
     * Returns the unique identifier for this stage type.
     *
     * <p>The stage ID is used to identify the stage in the pipeline and
     * must be unique within a single pipeline configuration. The ID is
     * used when looking up stages, preventing duplicate registrations,
     * and for debugging and logging purposes.</p>
     *
     * <p>Standard stage IDs defined by the built-in implementations:</p>
     * <ul>
     *   <li>{@code "checksum"} - {@link ChecksumStage#ID}</li>
     *   <li>{@code "compression"} - {@link CompressionStage#ID}</li>
     *   <li>{@code "encryption"} - {@link EncryptionStage#ID}</li>
     * </ul>
     *
     * <p>Custom implementations should use descriptive, lowercase IDs
     * that don't conflict with built-in stages. Examples: "logging",
     * "deduplication", "metrics".</p>
     *
     * @return the unique stage identifier as a non-null string; must be
     *         unique within a pipeline configuration; should be lowercase,
     *         descriptive, and not conflict with built-in stage IDs;
     *         implementations should return the same value on every call
     *         (immutable identifier)
     *
     * @see ProcessingPipeline.Builder#addStage(PipelineStage, Object)
     */
    @NotNull String getId();

    /**
     * Returns the order priority for this stage.
     *
     * <p>The order value determines the sequence in which stages are applied
     * during pipeline processing. This is critical for correct data transformation
     * and recovery:</p>
     *
     * <ul>
     *   <li><strong>Encoding (write)</strong>: Stages are applied in ascending
     *       order (lowest priority first). Data flows through stages from
     *       innermost (closest to raw data) to outermost (closest to output).</li>
     *   <li><strong>Decoding (read)</strong>: Stages are applied in descending
     *       order (highest priority first). This reverses the encoding order
     *       to correctly unwrap the transformations.</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <table>
     *   <caption>Recommended Priority Assignments</caption>
     *   <tr><th>Priority</th><th>Layer</th><th>Stage</th><th>Constant</th></tr>
     *   <tr><td>100</td><td>Innermost</td><td>Checksum</td><td>{@link ChecksumStage#ORDER}</td></tr>
     *   <tr><td>200</td><td>Middle</td><td>Compression</td><td>{@link CompressionStage#ORDER}</td></tr>
     *   <tr><td>300</td><td>Outermost</td><td>Encryption</td><td>{@link EncryptionStage#ORDER}</td></tr>
     * </table>
     *
     * <p>Custom stages should choose priorities that place them correctly
     * relative to the standard stages. For example, a logging stage that
     * should see raw data would use a priority less than 100.</p>
     *
     * @return the priority value as an integer; lower values are applied first
     *         during encoding and last during decoding; negative values are
     *         allowed; implementations should return the same value on every
     *         call (immutable priority); the value determines the stage's
     *         position in the processing chain
     *
     * @see ChecksumStage#ORDER
     * @see CompressionStage#ORDER
     * @see EncryptionStage#ORDER
     */
    int getOrder();

    /**
     * Wraps an output stream to apply this stage's transformation during encoding.
     *
     * <p>This method is called during the write (encoding) phase of pipeline
     * processing. It wraps the provided output stream with a stream that
     * applies this stage's transformation. For example:</p>
     * <ul>
     *   <li>{@link ChecksumStage} wraps with a stream that computes checksums</li>
     *   <li>{@link CompressionStage} wraps with a compressing stream</li>
     *   <li>{@link EncryptionStage} wraps with an encrypting stream</li>
     * </ul>
     *
     * <p>Stages are applied in ascending order of priority (lowest first).
     * The first stage wraps the raw output, and each subsequent stage wraps
     * the stream returned by the previous stage. This creates a chain:</p>
     * <pre>
     * Raw Data → Stage(100) → Stage(200) → Stage(300) → Final Output
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>The returned stream must properly delegate to the underlying stream</li>
     *   <li>Resources should be cleaned up when the stream is closed</li>
     *   <li>State should be stored in the context, not in instance fields</li>
     *   <li>The implementation must be thread-safe</li>
     * </ul>
     *
     * @param output  the underlying output stream to wrap; must not be {@code null};
     *                this is either the raw output (for the first stage) or the
     *                wrapped stream from the previous stage; implementations must
     *                delegate writes to this stream after applying their transformation
     * @param config  the stage configuration; must not be {@code null}; contains
     *                algorithm-specific settings such as compression level, encryption
     *                key, or checksum algorithm; validated via {@link #validateConfig}
     *                before this method is called
     * @param context the pipeline context for sharing state between stages; must not
     *                be {@code null}; stages should store computed values (checksums,
     *                sizes, algorithm IDs) in the context for later retrieval;
     *                the context is shared across all stages in the pipeline
     * @return a wrapped output stream that applies this stage's transformation;
     *         never returns {@code null}; the returned stream must properly close
     *         the underlying stream when closed; may return the input stream
     *         unchanged if the stage is effectively disabled
     * @throws IOException if an I/O error occurs during stream setup, such as
     *         cipher initialization failure or resource allocation error
     *
     * @see #wrapInput(InputStream, Object, PipelineContext)
     * @see ProcessingPipeline#wrapOutput(OutputStream, PipelineContext)
     */
    @NotNull OutputStream wrapOutput(
            final @NotNull OutputStream output,
            final @NotNull C config,
            final @NotNull PipelineContext context) throws IOException;

    /**
     * Wraps an input stream to apply this stage's reverse transformation during decoding.
     *
     * <p>This method is called during the read (decoding) phase of pipeline
     * processing. It wraps the provided input stream with a stream that
     * reverses this stage's transformation. For example:</p>
     * <ul>
     *   <li>{@link ChecksumStage} wraps with a stream that verifies checksums</li>
     *   <li>{@link CompressionStage} wraps with a decompressing stream</li>
     *   <li>{@link EncryptionStage} wraps with a decrypting stream</li>
     * </ul>
     *
     * <p>Stages are applied in descending order of priority (highest first).
     * This reverses the encoding order to correctly unwrap transformations.
     * The first stage wraps the raw input, and each subsequent stage wraps
     * the stream returned by the previous stage:</p>
     * <pre>
     * Encrypted Input → Decrypt(300) → Decompress(200) → Verify(100) → Raw Data
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>The returned stream must properly delegate to the underlying stream</li>
     *   <li>Verification failures should throw appropriate exceptions</li>
     *   <li>State should be stored in the context, not in instance fields</li>
     *   <li>The implementation must be thread-safe</li>
     * </ul>
     *
     * @param input   the underlying input stream to wrap; must not be {@code null};
     *                this is either the raw input (for the first stage in reverse order)
     *                or the wrapped stream from the previous stage; implementations must
     *                read from this stream and apply their reverse transformation
     * @param config  the stage configuration; must not be {@code null}; contains
     *                algorithm-specific settings such as expected checksum values,
     *                decryption keys, or decompression parameters; validated via
     *                {@link #validateConfig} before this method is called
     * @param context the pipeline context for sharing state between stages; must not
     *                be {@code null}; stages may read configuration from the context
     *                and store computed values (such as verified checksums or
     *                decompressed sizes) for later retrieval
     * @return a wrapped input stream that applies this stage's reverse transformation;
     *         never returns {@code null}; the returned stream must properly close
     *         the underlying stream when closed; may return the input stream
     *         unchanged if the stage is effectively disabled
     * @throws IOException if an I/O error occurs during stream setup, such as
     *         cipher initialization failure, decryption key error, or resource
     *         allocation failure
     *
     * @see #wrapOutput(OutputStream, Object, PipelineContext)
     * @see ProcessingPipeline#wrapInput(InputStream, PipelineContext)
     */
    @NotNull InputStream wrapInput(
            final @NotNull InputStream input,
            final @NotNull C config,
            final @NotNull PipelineContext context) throws IOException;

    /**
     * Checks if this stage is enabled for the given configuration.
     *
     * <p>This method determines whether the stage should be applied during
     * pipeline processing. Disabled stages are completely skipped - their
     * {@link #wrapOutput} and {@link #wrapInput} methods are not called,
     * and the data flows directly to the next enabled stage.</p>
     *
     * <p>Common reasons for a stage to be disabled:</p>
     * <ul>
     *   <li>No compression provider specified (compression disabled)</li>
     *   <li>No encryption key provided (encryption disabled)</li>
     *   <li>Checksum verification not requested (checksum stage may be disabled)</li>
     *   <li>Feature toggle or configuration flag set to disable the stage</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <p>The default implementation returns {@code true}, meaning the stage
     * is always enabled. Override this method to implement conditional
     * enabling based on the configuration.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * @Override
     * public boolean isEnabled(CompressionConfig config) {
     *     return config.provider() != null;
     * }
     * }</pre>
     *
     * @param config the stage configuration; must not be {@code null}; this is
     *               the same configuration that would be passed to
     *               {@link #wrapOutput} and {@link #wrapInput} if the stage
     *               is enabled; implementations should examine the configuration
     *               to determine if the stage should be active
     * @return {@code true} if the stage should be applied to the data stream,
     *         {@code false} if the stage should be skipped; when {@code false}
     *         is returned, the stage's wrap methods are not called and data
     *         flows directly to the next stage
     *
     * @see CompressionConfig#isEnabled()
     * @see EncryptionConfig#isEnabled()
     * @see ChecksumConfig#isEnabled()
     */
    default boolean isEnabled(final @NotNull C config) {
        return true;
    }

    /**
     * Called after all data has been written through the pipeline.
     *
     * <p>This method is invoked after the output stream has been closed and
     * all data has flowed through the pipeline. It provides an opportunity
     * for stages to:</p>
     * <ul>
     *   <li>Finalize computed values (e.g., compute final checksum)</li>
     *   <li>Store final state in the context</li>
     *   <li>Release resources that weren't freed during stream close</li>
     *   <li>Perform post-processing operations</li>
     * </ul>
     *
     * <p>This method is called for all enabled stages in the same order
     * as {@link #wrapOutput} was called (ascending priority order).</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The default implementation does nothing. Override this method only
     * if your stage requires post-processing after data flow is complete.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>For most stages, finalization happens automatically when the wrapped
     * stream is closed. This method is called in addition to stream close
     * and is useful for operations that need access to the pipeline context
     * after all streams are closed.</p>
     *
     * @param config  the stage configuration; must not be {@code null}; the same
     *                configuration that was used for {@link #wrapOutput}; provides
     *                access to stage-specific settings needed for finalization
     * @param context the pipeline context; must not be {@code null}; may be read
     *                to access values set during processing or updated with final
     *                computed values; the context is shared across all stages
     * @throws IOException if finalization fails, such as when computing a final
     *         value requires I/O operations that fail, or when post-processing
     *         operations encounter errors
     *
     * @see ProcessingPipeline#finalize(PipelineContext)
     */
    default void finalize(
            final @NotNull C config,
            final @NotNull PipelineContext context) throws IOException {
        // Default: no-op
    }

    /**
     * Validates the configuration for this stage before pipeline execution.
     *
     * <p>This method is called when a stage is added to the pipeline builder,
     * allowing early detection of invalid configurations. It should check that
     * all required configuration values are present and within valid ranges.</p>
     *
     * <p>Common validations include:</p>
     * <ul>
     *   <li>Compression level within supported range</li>
     *   <li>Encryption key has correct length for the algorithm</li>
     *   <li>Required fields are not null when the stage is enabled</li>
     *   <li>Algorithm-specific parameter validation</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <p>The default implementation performs no validation. Override this
     * method to implement configuration checking for your stage.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * @Override
     * public void validateConfig(EncryptionConfig config) {
     *     if (config.provider() != null && config.key() != null) {
     *         int expected = config.provider().getKeySize();
     *         int actual = config.key().getEncoded().length;
     *         if (actual != expected) {
     *             throw new IllegalArgumentException(
     *                 "Invalid key size: expected " + expected + ", got " + actual);
     *         }
     *     }
     * }
     * }</pre>
     *
     * @param config the configuration to validate; must not be {@code null};
     *               implementations should check all relevant configuration
     *               values and throw an exception if any are invalid
     * @throws IllegalArgumentException if the configuration is invalid; the
     *         exception message should clearly describe what is invalid and
     *         what values are expected; this exception prevents the stage
     *         from being added to the pipeline
     *
     * @see ProcessingPipeline.Builder#addStage(PipelineStage, Object)
     */
    default void validateConfig(final @NotNull C config) {
        // Default: no validation
    }

}
