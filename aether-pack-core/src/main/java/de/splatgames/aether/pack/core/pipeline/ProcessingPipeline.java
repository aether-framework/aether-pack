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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A composable pipeline for processing data through multiple stages.
 *
 * <p>The processing pipeline chains together multiple {@link PipelineStage}
 * implementations (compression, encryption, checksum, etc.) in a configurable
 * order. It manages the wrapping of I/O streams and ensures stages are applied
 * in the correct sequence.</p>
 *
 * <h2>Stage Ordering</h2>
 * <p>Stages are ordered by their priority value:</p>
 * <ul>
 *   <li><strong>Encoding (write)</strong> - Lowest priority first (100 → 200 → 300)</li>
 *   <li><strong>Decoding (read)</strong> - Highest priority first (300 → 200 → 100)</li>
 * </ul>
 *
 * <p>This ensures that data is processed correctly in both directions:</p>
 * <pre>
 * Write: Raw Data → Checksum → Compress → Encrypt → Output
 * Read:  Input → Decrypt → Decompress → Verify Checksum → Raw Data
 * </pre>
 *
 * <h2>Standard Pipeline Configuration</h2>
 * <pre>{@code
 * // Build a pipeline with all three standard stages
 * ProcessingPipeline pipeline = ProcessingPipeline.builder()
 *     .addStage(new ChecksumStage(), ChecksumConfig.forWrite(xxhash))
 *     .addStage(new CompressionStage(), CompressionConfig.of(zstdProvider, 3))
 *     .addStage(new EncryptionStage(), EncryptionConfig.of(aesProvider, key))
 *     .build();
 * }</pre>
 *
 * <h2>Writing Data</h2>
 * <pre>{@code
 * PipelineContext context = new PipelineContext();
 * context.set(PipelineContext.ENTRY_NAME, "data.bin");
 *
 * try (OutputStream out = pipeline.wrapOutput(fileOutput, context)) {
 *     out.write(originalData);
 * }
 *
 * // Retrieve computed values after writing
 * long checksum = context.getRequired(PipelineContext.CHECKSUM);
 * long originalSize = context.getOrDefault(PipelineContext.ORIGINAL_SIZE, 0L);
 * }</pre>
 *
 * <h2>Reading Data</h2>
 * <pre>{@code
 * PipelineContext context = new PipelineContext();
 *
 * try (InputStream in = pipeline.wrapInput(fileInput, context)) {
 *     byte[] data = in.readAllBytes();
 * }
 *
 * // Checksum verification happens automatically during read if configured
 * }</pre>
 *
 * <h2>Empty Pipeline</h2>
 * <p>Use {@link #empty()} to create a pass-through pipeline that applies
 * no transformations:</p>
 * <pre>{@code
 * ProcessingPipeline passthrough = ProcessingPipeline.empty();
 * // Data flows through unchanged
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>The pipeline itself is immutable and thread-safe. However, the
 * {@link PipelineContext} passed to {@link #wrapOutput} and {@link #wrapInput}
 * should not be shared across concurrent operations.</p>
 *
 * @see PipelineStage
 * @see PipelineContext
 * @see ChecksumStage
 * @see CompressionStage
 * @see EncryptionStage
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ProcessingPipeline {

    /** Immutable list of pipeline stages sorted by priority. */
    private final @NotNull List<StageEntry<?>> stages;

    /**
     * Constructs a new processing pipeline with the specified stages.
     * <p>
     * The stages list is defensively copied to ensure immutability.
     * </p>
     *
     * @param stages the list of pipeline stages (will be copied)
     */
    private ProcessingPipeline(final @NotNull List<StageEntry<?>> stages) {
        this.stages = List.copyOf(stages);
    }

    /**
     * Creates a new pipeline builder for assembling a processing pipeline.
     *
     * <p>The builder allows stages to be added incrementally and then
     * constructs an immutable pipeline. Stages are automatically sorted
     * by priority when {@link Builder#build()} is called.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * ProcessingPipeline pipeline = ProcessingPipeline.builder()
     *     .addStage(new ChecksumStage(), ChecksumConfig.forWrite(xxh3))
     *     .addStage(new CompressionStage(), CompressionConfig.of(zstd, 3))
     *     .addStage(new EncryptionStage(), EncryptionConfig.of(aes, key))
     *     .build();
     * }</pre>
     *
     * @return a new empty builder instance; never returns {@code null};
     *         the builder can be reused after calling {@link Builder#build()}
     *         but this is not recommended as stages are not automatically
     *         cleared
     *
     * @see Builder
     * @see #empty()
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Creates an empty pipeline that passes data through unchanged.
     *
     * <p>The empty pipeline has no stages and acts as a pass-through.
     * Data written to the output stream is passed directly to the
     * underlying stream, and data read from the input stream comes
     * directly from the underlying stream.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>Entries that don't need compression, encryption, or checksums</li>
     *   <li>Testing and debugging without transformations</li>
     *   <li>Placeholder when pipeline configuration is optional</li>
     * </ul>
     *
     * @return a singleton empty pipeline instance; never returns {@code null};
     *         the returned pipeline has {@link #getStageCount()} returning 0
     *         and {@link #hasStages()} returning {@code false}
     *
     * @see #builder()
     */
    public static @NotNull ProcessingPipeline empty() {
        return new ProcessingPipeline(List.of());
    }

    /**
     * Returns the number of stages in this pipeline.
     *
     * <p>This count includes all stages regardless of whether they are
     * enabled or disabled. Disabled stages are still part of the pipeline
     * but are skipped during processing.</p>
     *
     * @return the total number of stages in the pipeline; returns 0 for
     *         an empty pipeline; this count is fixed after pipeline
     *         construction
     *
     * @see #hasStages()
     * @see #empty()
     */
    public int getStageCount() {
        return this.stages.size();
    }

    /**
     * Checks if the pipeline contains any stages.
     *
     * <p>This is a convenience method equivalent to
     * {@code getStageCount() > 0}. A pipeline with no stages acts
     * as a pass-through and doesn't transform data.</p>
     *
     * @return {@code true} if the pipeline contains at least one stage;
     *         {@code false} if the pipeline is empty and will act as
     *         a pass-through
     *
     * @see #getStageCount()
     * @see #empty()
     */
    public boolean hasStages() {
        return !this.stages.isEmpty();
    }

    /**
     * Wraps an output stream with all enabled pipeline stages for encoding.
     *
     * <p>This method wraps the provided output stream with each enabled
     * stage's transformation, creating a chain of streams. Stages are
     * applied in ascending order of priority (lowest first), so data
     * flows from innermost transformations to outermost.</p>
     *
     * <p>The typical stage order for encoding is:</p>
     * <pre>
     * Raw Data → Checksum(100) → Compress(200) → Encrypt(300) → Output
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * PipelineContext context = new PipelineContext();
     * context.set(PipelineContext.ENTRY_NAME, "data.bin");
     *
     * try (OutputStream out = pipeline.wrapOutput(fileOutput, context)) {
     *     out.write(originalData);
     * }
     *
     * // Retrieve computed values after writing
     * long checksum = context.getRequired(PipelineContext.CHECKSUM);
     * }</pre>
     *
     * @param output  the underlying output stream to wrap; must not be
     *                {@code null}; data written to the returned stream
     *                will flow through all enabled stages before reaching
     *                this stream
     * @param context the pipeline context for sharing state; must not be
     *                {@code null}; stages will store computed values
     *                (checksums, sizes) in this context for later retrieval
     * @return a wrapped output stream that applies all enabled stage
     *         transformations; never returns {@code null}; closing the
     *         returned stream closes the entire chain including the
     *         underlying stream
     * @throws IOException if an I/O error occurs during stream setup,
     *         such as cipher initialization or compression setup failure
     *
     * @see #wrapInput(InputStream, PipelineContext)
     * @see PipelineStage#wrapOutput(OutputStream, Object, PipelineContext)
     */
    public @NotNull OutputStream wrapOutput(
            final @NotNull OutputStream output,
            final @NotNull PipelineContext context) throws IOException {

        OutputStream result = output;

        // Apply stages in ascending order (lowest priority first)
        for (final StageEntry<?> entry : this.stages) {
            if (entry.isEnabled()) {
                result = entry.wrapOutput(result, context);
            }
        }

        return result;
    }

    /**
     * Wraps an input stream with all enabled pipeline stages for decoding.
     *
     * <p>This method wraps the provided input stream with each enabled
     * stage's reverse transformation, creating a chain of streams. Stages
     * are applied in descending order of priority (highest first), which
     * reverses the encoding order to correctly unwrap transformations.</p>
     *
     * <p>The typical stage order for decoding is:</p>
     * <pre>
     * Encrypted Input → Decrypt(300) → Decompress(200) → Verify(100) → Raw Data
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * PipelineContext context = new PipelineContext();
     *
     * try (InputStream in = pipeline.wrapInput(fileInput, context)) {
     *     byte[] data = in.readAllBytes();
     * }
     *
     * // Checksum verification happens automatically if configured
     * }</pre>
     *
     * @param input   the underlying input stream to wrap; must not be
     *                {@code null}; data read from the returned stream
     *                has flowed through all enabled stages from this
     *                source stream
     * @param context the pipeline context for sharing state; must not be
     *                {@code null}; stages may store values (verified
     *                checksums, decompressed sizes) in this context
     * @return a wrapped input stream that applies all enabled stage
     *         reverse transformations; never returns {@code null}; closing
     *         the returned stream closes the entire chain including the
     *         underlying stream
     * @throws IOException if an I/O error occurs during stream setup,
     *         such as cipher initialization or decompression setup failure
     *
     * @see #wrapOutput(OutputStream, PipelineContext)
     * @see PipelineStage#wrapInput(InputStream, Object, PipelineContext)
     */
    public @NotNull InputStream wrapInput(
            final @NotNull InputStream input,
            final @NotNull PipelineContext context) throws IOException {

        InputStream result = input;

        // Apply stages in descending order (highest priority first)
        for (int i = this.stages.size() - 1; i >= 0; i--) {
            final StageEntry<?> entry = this.stages.get(i);
            if (entry.isEnabled()) {
                result = entry.wrapInput(result, context);
            }
        }

        return result;
    }

    /**
     * Finalizes all enabled stages after processing is complete.
     *
     * <p>This method calls the {@link PipelineStage#finalize} method on
     * each enabled stage in priority order. It should be called after
     * the output stream has been closed to allow stages to perform
     * post-processing operations.</p>
     *
     * <p>Note that most stages perform finalization automatically when
     * their wrapped streams are closed. This method is provided for
     * stages that require additional finalization steps after stream
     * closure.</p>
     *
     * @param context the pipeline context; must not be {@code null}; stages
     *                may update the context with final computed values
     * @throws IOException if any stage's finalization fails; the exception
     *         from the first failing stage is thrown, but all stages
     *         attempt finalization regardless of earlier failures
     *
     * @see PipelineStage#finalize(Object, PipelineContext)
     */
    public void finalize(final @NotNull PipelineContext context) throws IOException {
        for (final StageEntry<?> entry : this.stages) {
            if (entry.isEnabled()) {
                entry.finalize(context);
            }
        }
    }

    /**
     * Internal entry holding a stage and its configuration.
     *
     * @param <C> the configuration type
     */
    private static final class StageEntry<C> implements Comparable<StageEntry<?>> {

        /** The pipeline stage implementation. */
        private final @NotNull PipelineStage<C> stage;

        /** The configuration for this stage. */
        private final @NotNull C config;

        /**
         * Constructs a new stage entry and validates the configuration.
         *
         * @param stage  the pipeline stage
         * @param config the stage configuration
         * @throws IllegalArgumentException if the configuration is invalid
         */
        StageEntry(final @NotNull PipelineStage<C> stage, final @NotNull C config) {
            this.stage = stage;
            this.config = config;
            this.stage.validateConfig(config);
        }

        /**
         * Returns the stage identifier.
         *
         * @return the stage ID
         */
        @NotNull String getId() {
            return this.stage.getId();
        }

        /**
         * Returns the stage order priority.
         *
         * @return the order value
         */
        int getOrder() {
            return this.stage.getOrder();
        }

        /**
         * Checks if this stage is enabled for its configuration.
         *
         * @return {@code true} if the stage is enabled
         */
        boolean isEnabled() {
            return this.stage.isEnabled(this.config);
        }

        /**
         * Wraps an output stream with this stage's transformation.
         *
         * @param output  the output stream to wrap
         * @param context the pipeline context
         * @return the wrapped output stream
         * @throws IOException if an I/O error occurs
         */
        @NotNull OutputStream wrapOutput(
                final @NotNull OutputStream output,
                final @NotNull PipelineContext context) throws IOException {
            return this.stage.wrapOutput(output, this.config, context);
        }

        /**
         * Wraps an input stream with this stage's transformation.
         *
         * @param input   the input stream to wrap
         * @param context the pipeline context
         * @return the wrapped input stream
         * @throws IOException if an I/O error occurs
         */
        @NotNull InputStream wrapInput(
                final @NotNull InputStream input,
                final @NotNull PipelineContext context) throws IOException {
            return this.stage.wrapInput(input, this.config, context);
        }

        /**
         * Finalizes this stage after processing is complete.
         *
         * @param context the pipeline context
         * @throws IOException if finalization fails
         */
        void finalize(final @NotNull PipelineContext context) throws IOException {
            this.stage.finalize(this.config, context);
        }

        /**
         * Compares this stage entry to another by order priority.
         *
         * @param other the other stage entry
         * @return negative if this stage has lower priority, positive if higher, 0 if equal
         */
        @Override
        public int compareTo(final @NotNull StageEntry<?> other) {
            return Integer.compare(this.getOrder(), other.getOrder());
        }

    }

    /**
     * Builder for constructing {@link ProcessingPipeline} instances.
     *
     * <p>The builder provides a fluent API for adding and configuring
     * pipeline stages. Stages can be added in any order - they are
     * automatically sorted by priority when {@link #build()} is called.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * ProcessingPipeline pipeline = ProcessingPipeline.builder()
     *     .addStage(new ChecksumStage(), ChecksumConfig.forWrite(xxh3))
     *     .addStage(new CompressionStage(), CompressionConfig.of(zstd, 3))
     *     .addStage(new EncryptionStage(), EncryptionConfig.of(aes, key))
     *     .build();
     * }</pre>
     *
     * <p><strong>\:</strong></p>
     * <p>Each stage ID can only appear once in the pipeline. Attempting
     * to add a stage with a duplicate ID throws an exception. Use
     * {@link #removeStage(String)} to replace a stage.</p>
     *
     * @see ProcessingPipeline#builder()
     */
    public static final class Builder {

        /** Mutable list of stage entries being assembled. */
        private final @NotNull List<StageEntry<?>> stages;

        /** Map for detecting duplicate stage IDs. */
        private final @NotNull Map<String, StageEntry<?>> stageById;

        /**
         * Constructs a new empty pipeline builder.
         *
         * <p>The builder starts with no stages. Use {@link #addStage} to
         * add stages to the pipeline configuration.</p>
         */
        private Builder() {
            this.stages = new ArrayList<>();
            this.stageById = new ConcurrentHashMap<>();
        }

        /**
         * Adds a stage to the pipeline configuration.
         *
         * <p>The stage is validated immediately using its
         * {@link PipelineStage#validateConfig} method. If validation
         * fails, an {@link IllegalArgumentException} is thrown and the
         * stage is not added.</p>
         *
         * <p>Stages can be added in any order - they are automatically
         * sorted by their {@link PipelineStage#getOrder() priority} when
         * {@link #build()} is called.</p>
         *
         * <p><strong>\:</strong></p>
         * <p>Each stage ID can only appear once in the pipeline. If a
         * stage with the same ID has already been added, an
         * {@link IllegalStateException} is thrown. Use
         * {@link #removeStage(String)} first if you need to replace
         * a stage.</p>
         *
         * @param stage  the pipeline stage to add; must not be {@code null};
         *               the stage's configuration is validated immediately
         * @param config the stage configuration; must not be {@code null};
         *               validated via {@link PipelineStage#validateConfig}
         *               before the stage is added
         * @param <C>    the configuration type; must match the stage's
         *               expected configuration type
         * @return this builder for method chaining; never returns {@code null}
         * @throws IllegalStateException    if a stage with the same ID has
         *                                  already been added to this builder
         * @throws IllegalArgumentException if the configuration is invalid
         *                                  according to the stage's validation
         *
         * @see PipelineStage#getId()
         * @see PipelineStage#validateConfig(Object)
         * @see #removeStage(String)
         */
        public <C> @NotNull Builder addStage(
                final @NotNull PipelineStage<C> stage,
                final @NotNull C config) {

            final String id = stage.getId();
            if (this.stageById.containsKey(id)) {
                throw new IllegalStateException("Stage with ID already exists: " + id);
            }

            final StageEntry<C> entry = new StageEntry<>(stage, config);
            this.stages.add(entry);
            this.stageById.put(id, entry);
            return this;
        }

        /**
         * Removes a stage from the pipeline configuration by its ID.
         *
         * <p>If no stage with the specified ID exists, this method does
         * nothing and returns the builder. This allows safe removal of
         * stages that may or may not be present.</p>
         *
         * <p><strong>\:</strong></p>
         * <ul>
         *   <li>Replacing a stage with a different configuration</li>
         *   <li>Conditionally removing a stage based on runtime conditions</li>
         *   <li>Creating modified copies of pipeline configurations</li>
         * </ul>
         *
         * @param stageId the ID of the stage to remove; must not be
         *                {@code null}; if no stage with this ID exists,
         *                the builder is returned unchanged
         * @return this builder for method chaining; never returns {@code null}
         *
         * @see PipelineStage#getId()
         * @see #addStage(PipelineStage, Object)
         * @see #clear()
         */
        public @NotNull Builder removeStage(final @NotNull String stageId) {
            final StageEntry<?> entry = this.stageById.remove(stageId);
            if (entry != null) {
                this.stages.remove(entry);
            }
            return this;
        }

        /**
         * Removes all stages from the builder.
         *
         * <p>After this method returns, the builder contains no stages
         * and can be reused to build a new pipeline configuration. This
         * is equivalent to creating a new builder with
         * {@link ProcessingPipeline#builder()}.</p>
         *
         * @return this builder for method chaining; never returns {@code null}
         *
         * @see #removeStage(String)
         */
        public @NotNull Builder clear() {
            this.stages.clear();
            this.stageById.clear();
            return this;
        }

        /**
         * Builds an immutable {@link ProcessingPipeline} from the configured stages.
         *
         * <p>This method creates a new pipeline instance containing all
         * stages that have been added to the builder. Stages are sorted
         * by their {@link PipelineStage#getOrder() priority} before the
         * pipeline is constructed, ensuring correct processing order.</p>
         *
         * <p><strong>\:</strong></p>
         * <p>The returned pipeline is immutable. Further modifications
         * to this builder do not affect previously built pipelines, and
         * the pipeline's stage list cannot be modified after construction.</p>
         *
         * <p><strong>\:</strong></p>
         * <p>The builder can be reused after calling this method, though
         * this is generally not recommended. Call {@link #clear()} first
         * if you want to build a completely new pipeline.</p>
         *
         * @return a new immutable pipeline containing all configured stages,
         *         sorted by priority; never returns {@code null}; returns
         *         a pipeline equivalent to {@link ProcessingPipeline#empty()}
         *         if no stages have been added
         *
         * @see ProcessingPipeline#builder()
         * @see ProcessingPipeline#empty()
         */
        public @NotNull ProcessingPipeline build() {
            final List<StageEntry<?>> sorted = new ArrayList<>(this.stages);
            sorted.sort(Comparator.naturalOrder());
            return new ProcessingPipeline(sorted);
        }

    }

}
