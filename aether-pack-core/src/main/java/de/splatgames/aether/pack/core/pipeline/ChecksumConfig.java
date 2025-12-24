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

import de.splatgames.aether.pack.core.spi.ChecksumProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration for the {@link ChecksumStage} pipeline stage.
 *
 * <p>This record holds the checksum settings used when processing
 * data through the pipeline. It specifies which checksum algorithm
 * to use and whether to verify the checksum during reading.</p>
 *
 * <h2>Operation Modes</h2>
 * <ul>
 *   <li><strong>Write mode</strong> - Compute checksum, store in context</li>
 *   <li><strong>Read with verify</strong> - Compute and compare to expected</li>
 *   <li><strong>Read without verify</strong> - Compute only, no comparison</li>
 * </ul>
 *
 * <h2>Supported Algorithms</h2>
 * <table>
 *   <caption>Checksum Algorithms</caption>
 *   <tr><th>Algorithm</th><th>Size</th><th>Speed</th><th>Use Case</th></tr>
 *   <tr><td>XXH3-64</td><td>64 bits</td><td>Very Fast</td><td>Default, recommended</td></tr>
 *   <tr><td>CRC-32</td><td>32 bits</td><td>Fast</td><td>Legacy compatibility</td></tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * ChecksumProvider xxh3 = ChecksumRegistry.getDefault();
 *
 * // For writing (compute only)
 * ChecksumConfig writeConfig = ChecksumConfig.forWrite(xxh3);
 *
 * // For reading with verification
 * long expectedChecksum = 0x1234567890ABCDEFL;
 * ChecksumConfig readConfig = ChecksumConfig.forRead(xxh3, expectedChecksum);
 *
 * // For reading without verification (compute only)
 * ChecksumConfig noVerify = ChecksumConfig.forReadNoVerify(xxh3);
 *
 * // Disable checksum entirely
 * ChecksumConfig disabled = ChecksumConfig.DISABLED;
 * }</pre>
 *
 * @param provider         the checksum provider (null to disable)
 * @param verifyOnRead     whether to verify checksum on read
 * @param expectedChecksum the expected checksum value (only used if verifyOnRead is true)
 *
 * @see ChecksumStage
 * @see ChecksumProvider
 * @see de.splatgames.aether.pack.core.checksum.ChecksumRegistry
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record ChecksumConfig(
        @Nullable ChecksumProvider provider,
        boolean verifyOnRead,
        long expectedChecksum
) {

    /**
     * Pre-defined configuration that disables checksum computation.
     *
     * <p>Use this constant when checksums are not needed, such as for
     * data that has other integrity guarantees or for testing purposes.
     * Note that disabling checksums removes an important data integrity
     * protection layer.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>Disabling checksums is generally not recommended for production
     * archives. Checksums provide early detection of data corruption that
     * might otherwise go unnoticed until the data is used.</p>
     */
    public static final ChecksumConfig DISABLED = new ChecksumConfig(null, false, 0);

    /**
     * Creates a checksum configuration for write operations (compute only).
     *
     * <p>This factory method creates a configuration for use during archive
     * writing. The checksum is computed as data flows through the pipeline
     * and stored in the {@link PipelineContext} for later retrieval. No
     * verification is performed since there is no expected value to compare
     * against during writing.</p>
     *
     * <p>After writing is complete, retrieve the computed checksum from the
     * context using {@link PipelineContext#CHECKSUM}.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * ChecksumProvider xxh3 = ChecksumRegistry.getDefault();
     * ChecksumConfig config = ChecksumConfig.forWrite(xxh3);
     *
     * PipelineContext ctx = new PipelineContext();
     * try (OutputStream out = pipeline.wrapOutput(fileOut, ctx)) {
     *     out.write(data);
     * }
     *
     * // Retrieve computed checksum
     * long checksum = ctx.getRequired(PipelineContext.CHECKSUM);
     * }</pre>
     *
     * @param provider the checksum provider to use; must not be {@code null};
     *                 determines the checksum algorithm (XXH3-64 or CRC-32);
     *                 the provider creates the checksum calculator that will
     *                 compute the hash as data is written
     * @return a new checksum configuration for writing; never returns
     *         {@code null}; the returned configuration will have
     *         {@link #isEnabled()} return {@code true} and
     *         {@link #verifyOnRead()} return {@code false}
     *
     * @see #forRead(ChecksumProvider, long)
     * @see PipelineContext#CHECKSUM
     */
    public static @NotNull ChecksumConfig forWrite(final @NotNull ChecksumProvider provider) {
        return new ChecksumConfig(provider, false, 0);
    }

    /**
     * Creates a checksum configuration for read operations with verification.
     *
     * <p>This factory method creates a configuration for use during archive
     * reading with checksum verification enabled. The checksum is computed
     * as data flows through the pipeline and compared to the expected value.
     * If the computed checksum doesn't match, a {@link de.splatgames.aether.pack.core.exception.ChecksumException}
     * is thrown.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The checksum is verified when the input stream reaches end-of-file
     * or when the stream is closed. This means errors are detected at the
     * end of reading, not during.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * ChecksumProvider xxh3 = ChecksumRegistry.getDefault();
     * long storedChecksum = entryHeader.getChecksum();
     * ChecksumConfig config = ChecksumConfig.forRead(xxh3, storedChecksum);
     *
     * try (InputStream in = pipeline.wrapInput(fileIn, ctx)) {
     *     byte[] data = in.readAllBytes();  // May throw if checksum fails
     * }
     * }</pre>
     *
     * @param provider         the checksum provider to use; must not be {@code null};
     *                         determines the checksum algorithm; must match the
     *                         algorithm used when the data was written
     * @param expectedChecksum the expected checksum value to verify against; this
     *                         should be the checksum that was computed and stored
     *                         when the data was originally written; if the computed
     *                         checksum doesn't match this value, an exception is thrown
     * @return a new checksum configuration for reading with verification; never
     *         returns {@code null}; the returned configuration will have
     *         {@link #isEnabled()} return {@code true} and
     *         {@link #verifyOnRead()} return {@code true}
     *
     * @see #forReadNoVerify(ChecksumProvider)
     * @see de.splatgames.aether.pack.core.exception.ChecksumException
     */
    public static @NotNull ChecksumConfig forRead(
            final @NotNull ChecksumProvider provider,
            final long expectedChecksum) {
        return new ChecksumConfig(provider, true, expectedChecksum);
    }

    /**
     * Creates a checksum configuration for read operations without verification.
     *
     * <p>This factory method creates a configuration for reading that computes
     * the checksum but doesn't verify it against an expected value. The computed
     * checksum is stored in the {@link PipelineContext} for later retrieval but
     * no exception is thrown if it differs from any expected value.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>Computing checksum for display or logging purposes</li>
     *   <li>Migrating data where verification is handled separately</li>
     *   <li>Testing or debugging scenarios</li>
     *   <li>When verification is performed at a higher level</li>
     * </ul>
     *
     * @param provider the checksum provider to use; must not be {@code null};
     *                 determines the checksum algorithm; the checksum is computed
     *                 but not compared to any expected value
     * @return a new checksum configuration for reading without verification;
     *         never returns {@code null}; the returned configuration will have
     *         {@link #isEnabled()} return {@code true} and
     *         {@link #verifyOnRead()} return {@code false}
     *
     * @see #forRead(ChecksumProvider, long)
     */
    public static @NotNull ChecksumConfig forReadNoVerify(final @NotNull ChecksumProvider provider) {
        return new ChecksumConfig(provider, false, 0);
    }

    /**
     * Checks if checksum computation is enabled in this configuration.
     *
     * <p>Checksum computation is considered enabled when a provider is
     * specified (not null). When this method returns {@code false}, the
     * {@link ChecksumStage} will pass data through unchanged without
     * computing any checksum.</p>
     *
     * <p>This method is called by {@link ChecksumStage#isEnabled} to
     * determine whether the stage should be applied to the data stream.</p>
     *
     * @return {@code true} if a checksum provider is configured and
     *         checksum computation should be performed on data flowing
     *         through the pipeline; {@code false} if the provider is
     *         {@code null} and no checksum should be computed
     *
     * @see ChecksumStage#isEnabled(ChecksumConfig)
     * @see #DISABLED
     */
    public boolean isEnabled() {
        return this.provider != null;
    }

}
