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
import org.jetbrains.annotations.Nullable;

/**
 * Configuration for the {@link CompressionStage} pipeline stage.
 *
 * <p>This record holds the compression settings used when processing
 * data through the pipeline. It specifies which compression algorithm
 * to use and at what compression level.</p>
 *
 * <h2>Compression Levels</h2>
 * <p>Compression levels are provider-specific:</p>
 * <table>
 *   <caption>Compression Level Ranges</caption>
 *   <tr><th>Algorithm</th><th>Min</th><th>Max</th><th>Default</th></tr>
 *   <tr><td>ZSTD</td><td>1</td><td>22</td><td>3</td></tr>
 *   <tr><td>LZ4</td><td>1</td><td>12</td><td>1</td></tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Disable compression
 * CompressionConfig disabled = CompressionConfig.DISABLED;
 *
 * // Use default level
 * CompressionProvider zstd = CompressionRegistry.requireByName("zstd");
 * CompressionConfig config = CompressionConfig.of(zstd);
 *
 * // Specify compression level
 * CompressionConfig highCompression = CompressionConfig.of(zstd, 19);
 * CompressionConfig fastCompression = CompressionConfig.of(zstd, 1);
 * }</pre>
 *
 * @param provider the compression provider (null to disable compression)
 * @param level    the compression level (provider-specific, 0 for default)
 *
 * @see CompressionStage
 * @see CompressionProvider
 * @see de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_ZSTD
 * @see de.splatgames.aether.pack.core.format.FormatConstants#COMPRESSION_LZ4
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record CompressionConfig(
        @Nullable CompressionProvider provider,
        int level
) {

    /**
     * Pre-defined configuration that disables compression.
     *
     * <p>Use this constant when creating entries that should not be compressed.
     * This is more efficient than creating a new instance with a null provider.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Already-compressed files don't benefit from additional compression
     * if (entry.getName().endsWith(".jpg") || entry.getName().endsWith(".zip")) {
     *     pipeline.addStage(new CompressionStage(), CompressionConfig.DISABLED);
     * }
     * }</pre>
     */
    public static final CompressionConfig DISABLED = new CompressionConfig(null, 0);

    /**
     * Creates a compression configuration using the provider's default compression level.
     *
     * <p>This factory method creates a configuration that uses the compression
     * algorithm's default level, which is typically a good balance between
     * compression ratio and speed. Use this when you don't have specific
     * performance or size requirements.</p>
     *
     * <p><strong>\:</strong></p>
     * <table>
     *   <caption>Provider Default Levels</caption>
     *   <tr><th>Algorithm</th><th>Default Level</th><th>Characteristic</th></tr>
     *   <tr><td>ZSTD</td><td>3</td><td>Good balance of speed and ratio</td></tr>
     *   <tr><td>LZ4</td><td>1</td><td>Maximum speed</td></tr>
     * </table>
     *
     * @param provider the compression provider to use; must not be {@code null};
     *                 determines the compression algorithm (ZSTD, LZ4, etc.);
     *                 the provider's {@link CompressionProvider#getDefaultLevel()}
     *                 method is called to obtain the default compression level
     * @return a new compression configuration using the provider's default level;
     *         never returns {@code null}; the returned configuration will have
     *         {@link #isEnabled()} return {@code true}
     *
     * @see #of(CompressionProvider, int)
     * @see CompressionProvider#getDefaultLevel()
     */
    public static @NotNull CompressionConfig of(final @NotNull CompressionProvider provider) {
        return new CompressionConfig(provider, provider.getDefaultLevel());
    }

    /**
     * Creates a compression configuration with a specific compression level.
     *
     * <p>This factory method creates a configuration that uses a custom
     * compression level. Use this when you have specific requirements for
     * compression ratio vs. speed trade-offs.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li><strong>Lower levels</strong>: Faster compression, larger output</li>
     *   <li><strong>Higher levels</strong>: Slower compression, smaller output</li>
     *   <li>Decompression speed is generally independent of the level used</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <table>
     *   <caption>Valid Compression Levels</caption>
     *   <tr><th>Algorithm</th><th>Min</th><th>Max</th><th>Notes</th></tr>
     *   <tr><td>ZSTD</td><td>1</td><td>22</td><td>Levels 20+ are very slow</td></tr>
     *   <tr><td>LZ4</td><td>1</td><td>12</td><td>Higher levels use HC mode</td></tr>
     * </table>
     *
     * @param provider the compression provider to use; must not be {@code null};
     *                 determines the compression algorithm and validates that the
     *                 level is within the supported range
     * @param level    the compression level to use; must be within the provider's
     *                 supported range as defined by {@link CompressionProvider#getMinLevel()}
     *                 and {@link CompressionProvider#getMaxLevel()}; using 0 typically
     *                 means "use default level"
     * @return a new compression configuration with the specified level; never
     *         returns {@code null}; the returned configuration will have
     *         {@link #isEnabled()} return {@code true}
     *
     * @see #of(CompressionProvider)
     * @see CompressionProvider#getMinLevel()
     * @see CompressionProvider#getMaxLevel()
     */
    public static @NotNull CompressionConfig of(
            final @NotNull CompressionProvider provider,
            final int level) {
        return new CompressionConfig(provider, level);
    }

    /**
     * Checks if compression is enabled in this configuration.
     *
     * <p>Compression is considered enabled when a compression provider is
     * specified (not null). When this method returns {@code false}, the
     * {@link CompressionStage} will pass data through unchanged.</p>
     *
     * <p>This method is called by {@link CompressionStage#isEnabled} to
     * determine whether the stage should be applied to the data stream.</p>
     *
     * @return {@code true} if a compression provider is configured and
     *         compression should be applied to data flowing through the
     *         pipeline; {@code false} if the provider is {@code null} and
     *         data should pass through uncompressed
     *
     * @see CompressionStage#isEnabled(CompressionConfig)
     * @see #DISABLED
     */
    public boolean isEnabled() {
        return this.provider != null;
    }

}
