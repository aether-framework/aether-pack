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

/**
 * Processing pipeline infrastructure for composable data transformations.
 *
 * <p>The pipeline package provides a flexible, extensible framework for
 * chaining together data processing stages. Each stage wraps I/O streams
 * to apply its transformation (compression, encryption, checksumming)
 * as data flows through.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * +-------------------+     +------------------+     +------------------+
 * | ProcessingPipeline | --&gt; | PipelineStage&lt;C&gt; | --&gt; | PipelineContext  |
 * +-------------------+     +------------------+     +------------------+
 *         |                         |                        |
 *         |                         v                        |
 *         |              +--------------------+               |
 *         +-------------&gt;| ChecksumStage (100)|&lt;--------------+
 *         |              | CompressionStage   |               |
 *         |              | EncryptionStage    |               |
 *         |              +--------------------+               |
 *         |                         |                        |
 *         v                         v                        v
 *   Manages stages          Wraps streams            Shares state
 * </pre>
 *
 * <h2>Stage Order</h2>
 * <p>Stages are ordered by priority. During encoding (write), stages
 * are applied from lowest to highest priority:</p>
 * <ol>
 *   <li><strong>Checksum (100)</strong> - Innermost, operates on raw data</li>
 *   <li><strong>Compression (200)</strong> - Middle layer</li>
 *   <li><strong>Encryption (300)</strong> - Outermost, applied last</li>
 * </ol>
 *
 * <p>During decoding (read), the order is reversed (encryption first,
 * then decompression, then checksum verification).</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link de.splatgames.aether.pack.core.pipeline.ProcessingPipeline} -
 *       Main entry point for building and using pipelines</li>
 *   <li>{@link de.splatgames.aether.pack.core.pipeline.PipelineStage} -
 *       Interface for implementing custom stages</li>
 *   <li>{@link de.splatgames.aether.pack.core.pipeline.PipelineContext} -
 *       Type-safe context for sharing state between stages</li>
 * </ul>
 *
 * <h2>Built-in Stages</h2>
 * <ul>
 *   <li>{@link de.splatgames.aether.pack.core.pipeline.ChecksumStage} -
 *       Computes/verifies XXH3 or CRC32 checksums</li>
 *   <li>{@link de.splatgames.aether.pack.core.pipeline.CompressionStage} -
 *       Applies ZSTD or LZ4 compression</li>
 *   <li>{@link de.splatgames.aether.pack.core.pipeline.EncryptionStage} -
 *       Encrypts with AES-256-GCM or ChaCha20-Poly1305</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Build a pipeline with all three stages
 * ProcessingPipeline pipeline = ProcessingPipeline.builder()
 *     .addStage(new ChecksumStage(), ChecksumConfig.forWrite(xxhash))
 *     .addStage(new CompressionStage(), CompressionConfig.of(zstdProvider, 3))
 *     .addStage(new EncryptionStage(), EncryptionConfig.of(aesProvider, key))
 *     .build();
 *
 * // Write data through the pipeline
 * PipelineContext context = new PipelineContext();
 * try (OutputStream out = pipeline.wrapOutput(fileOutput, context)) {
 *     out.write(data);
 * }
 *
 * // Retrieve computed values from context
 * long checksum = context.getRequired(PipelineContext.CHECKSUM);
 * long originalSize = context.getOrDefault(PipelineContext.ORIGINAL_SIZE, 0L);
 * }</pre>
 *
 * @see de.splatgames.aether.pack.core.pipeline.ProcessingPipeline
 * @see de.splatgames.aether.pack.core.pipeline.PipelineStage
 * @see de.splatgames.aether.pack.core.pipeline.PipelineContext
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.splatgames.aether.pack.core.pipeline;
