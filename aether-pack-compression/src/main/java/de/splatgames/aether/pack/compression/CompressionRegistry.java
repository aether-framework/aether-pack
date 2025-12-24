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

import de.splatgames.aether.pack.core.spi.CompressionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for compression providers used by APACK archives.
 *
 * <p>This registry manages all available compression algorithms and provides
 * lookup methods for retrieving providers by string ID or numeric ID.
 * Providers are discovered both statically (built-in implementations) and
 * dynamically via {@link ServiceLoader}.</p>
 *
 * <h2>Built-in Providers</h2>
 * <p>The following compression providers are always available:</p>
 * <table>
 *   <caption>Built-in Compression Algorithms</caption>
 *   <tr><th>ID</th><th>Numeric ID</th><th>Default Level</th><th>Description</th></tr>
 *   <tr><td>{@code zstd}</td><td>1</td><td>3</td><td>Zstandard - excellent ratio, fast decompression</td></tr>
 *   <tr><td>{@code lz4}</td><td>2</td><td>0</td><td>LZ4 - extremely fast, lower ratio</td></tr>
 * </table>
 *
 * <h2>Provider Discovery</h2>
 * <p>Custom providers can be registered in two ways:</p>
 * <ul>
 *   <li><strong>ServiceLoader:</strong> Implement {@link CompressionProvider} and
 *       declare it in {@code META-INF/services/de.splatgames.aether.pack.core.spi.CompressionProvider}</li>
 *   <li><strong>Programmatic:</strong> Call {@link #register(CompressionProvider)} at startup</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Get a provider by string ID
 * CompressionProvider zstd = CompressionRegistry.require("zstd");
 *
 * // Get a provider by numeric ID (used in binary format)
 * CompressionProvider provider = CompressionRegistry.requireById(1);
 *
 * // Use convenience methods for built-in providers
 * CompressionProvider zstd = CompressionRegistry.zstd();
 * CompressionProvider lz4 = CompressionRegistry.lz4();
 *
 * // Compress data
 * byte[] compressed = zstd.compressBlock(data, zstd.getDefaultLevel());
 * byte[] decompressed = zstd.decompressBlock(compressed, data.length);
 * }</pre>
 *
 * <h2>Choosing an Algorithm</h2>
 * <ul>
 *   <li><strong>ZSTD:</strong> Best for archival, backups, and when compression ratio
 *       matters. Decompression is very fast even at high compression levels.</li>
 *   <li><strong>LZ4:</strong> Best for real-time applications, caching, and when
 *       speed is critical. Lower compression ratio but extremely fast.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. The internal maps use {@link java.util.concurrent.ConcurrentHashMap},
 * and registration uses {@code putIfAbsent} to avoid race conditions during
 * dynamic provider loading.</p>
 *
 * @see CompressionProvider
 * @see ZstdCompressionProvider
 * @see Lz4CompressionProvider
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class CompressionRegistry {

    /**
     * Map of providers indexed by their lowercase string ID.
     * Used for case-insensitive lookup by algorithm name.
     */
    private static final Map<String, CompressionProvider> BY_ID = new ConcurrentHashMap<>();

    /**
     * Map of providers indexed by their numeric ID.
     * Used for efficient lookup when reading from binary format.
     */
    private static final Map<Integer, CompressionProvider> BY_NUMERIC_ID = new ConcurrentHashMap<>();

    static {
        // Register built-in providers
        register(new ZstdCompressionProvider());
        register(new Lz4CompressionProvider());

        // Load providers via ServiceLoader
        final ServiceLoader<CompressionProvider> loader = ServiceLoader.load(CompressionProvider.class);
        for (final CompressionProvider provider : loader) {
            register(provider);
        }
    }

    /**
     * Private constructor to prevent instantiation.
     *
     * <p>This is a utility class with only static methods and should not be instantiated.</p>
     */
    private CompressionRegistry() {
    }

    /**
     * Registers a compression provider with this registry.
     *
     * <p>The provider is indexed by both its string ID (case-insensitive) and
     * its numeric ID. If a provider with the same ID is already registered,
     * this method silently ignores the duplicate, making registration idempotent.</p>
     *
     * <p>This method is typically called during static initialization for built-in
     * providers and by the {@link ServiceLoader} mechanism for custom providers.
     * It can also be called programmatically to register providers at runtime.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>This method is thread-safe and can be called from multiple threads
     * concurrently. Registration uses atomic {@code putIfAbsent} operations.</p>
     *
     * @param provider the compression provider to register; must not be {@code null};
     *                 the provider's {@link CompressionProvider#getId()} and
     *                 {@link CompressionProvider#getNumericId()} methods are called
     *                 to determine the lookup keys
     * @throws NullPointerException if {@code provider} is {@code null}
     *
     * @see #get(String)
     * @see #getById(int)
     */
    public static void register(final @NotNull CompressionProvider provider) {
        BY_ID.putIfAbsent(provider.getId(), provider);
        BY_NUMERIC_ID.putIfAbsent(provider.getNumericId(), provider);
    }

    /**
     * Returns a compression provider by its string identifier.
     *
     * <p>The lookup is case-insensitive; the ID is converted to lowercase
     * before searching. If no provider with the given ID is registered,
     * an empty {@link Optional} is returned.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * Optional<CompressionProvider> provider = CompressionRegistry.get("zstd");
     * if (provider.isPresent()) {
     *     byte[] compressed = provider.get().compressBlock(data, 3);
     * }
     * }</pre>
     *
     * @param id the provider's string identifier; must not be {@code null};
     *           common values include {@code "zstd"} and {@code "lz4"};
     *           case-insensitive
     * @return an {@link Optional} containing the provider if found, or
     *         {@link Optional#empty()} if no provider with the given ID exists;
     *         never returns {@code null}
     * @throws NullPointerException if {@code id} is {@code null}
     *
     * @see #require(String)
     * @see #getById(int)
     */
    public static @NotNull Optional<CompressionProvider> get(final @NotNull String id) {
        return Optional.ofNullable(BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    /**
     * Returns a compression provider by its numeric identifier.
     *
     * <p>Numeric IDs are used in the binary format to identify the compression
     * algorithm. This method is typically called when reading archive headers
     * to resolve the algorithm for decompression.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>{@code 1} - ZSTD (Zstandard)</li>
     *   <li>{@code 2} - LZ4</li>
     * </ul>
     *
     * @param numericId the provider's numeric identifier as stored in the
     *                  binary format; must match a registered provider's
     *                  {@link CompressionProvider#getNumericId()} value
     * @return an {@link Optional} containing the provider if found, or
     *         {@link Optional#empty()} if no provider with the given ID exists;
     *         never returns {@code null}
     *
     * @see #requireById(int)
     * @see #get(String)
     */
    public static @NotNull Optional<CompressionProvider> getById(final int numericId) {
        return Optional.ofNullable(BY_NUMERIC_ID.get(numericId));
    }

    /**
     * Returns a compression provider by string ID, throwing if not found.
     *
     * <p>This method is equivalent to {@link #get(String)} but throws an
     * exception instead of returning an empty {@link Optional} when the
     * provider is not found. Use this method when a missing provider
     * indicates a programming error or invalid configuration.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Throws NoSuchElementException if not found
     * CompressionProvider zstd = CompressionRegistry.require("zstd");
     * byte[] compressed = zstd.compressBlock(data, 3);
     * }</pre>
     *
     * @param id the provider's string identifier; must not be {@code null};
     *           case-insensitive
     * @return the compression provider; never returns {@code null}
     * @throws NoSuchElementException if no provider with the given ID is registered;
     *                                the exception message includes the requested ID
     * @throws NullPointerException if {@code id} is {@code null}
     *
     * @see #get(String)
     * @see #requireById(int)
     */
    public static @NotNull CompressionProvider require(final @NotNull String id) {
        return get(id).orElseThrow(() ->
                new NoSuchElementException("Unknown compression provider: " + id));
    }

    /**
     * Returns a compression provider by numeric ID, throwing if not found.
     *
     * <p>This method is equivalent to {@link #getById(int)} but throws an
     * exception instead of returning an empty {@link Optional}. This is
     * typically used when reading archives, where an unknown algorithm ID
     * indicates file corruption or an unsupported format version.</p>
     *
     * @param numericId the provider's numeric identifier as stored in the
     *                  binary format
     * @return the compression provider; never returns {@code null}
     * @throws NoSuchElementException if no provider with the given numeric ID
     *                                is registered; the exception message
     *                                includes the requested ID
     *
     * @see #getById(int)
     * @see #require(String)
     */
    public static @NotNull CompressionProvider requireById(final int numericId) {
        return getById(numericId).orElseThrow(() ->
                new NoSuchElementException("Unknown compression provider ID: " + numericId));
    }

    /**
     * Returns all registered compression providers.
     *
     * <p>The returned collection is an unmodifiable view of the currently
     * registered providers. It reflects the state at the time of the call
     * and may not include providers registered concurrently.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * for (CompressionProvider provider : CompressionRegistry.getAll()) {
     *     System.out.println(provider.getId() + " (levels: " +
     *         provider.getMinLevel() + "-" + provider.getMaxLevel() + ")");
     * }
     * }</pre>
     *
     * @return an unmodifiable collection containing all registered compression
     *         providers; never returns {@code null}; the collection contains
     *         at least ZSTD and LZ4 in normal operation
     */
    public static @NotNull Collection<CompressionProvider> getAll() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /**
     * Returns the built-in ZSTD compression provider.
     *
     * <p>This is a convenience method equivalent to {@code require("zstd")}
     * but without the overhead of string lookup. Use this method when you
     * specifically need ZSTD compression.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li><strong>Compression ratio:</strong> Excellent (comparable to LZMA/xz)</li>
     *   <li><strong>Compression speed:</strong> Good, configurable via level</li>
     *   <li><strong>Decompression speed:</strong> Very fast at all levels</li>
     *   <li><strong>Levels:</strong> 1-22 (default: 3)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * CompressionProvider zstd = CompressionRegistry.zstd();
     * byte[] compressed = zstd.compressBlock(data, 6);  // Medium compression
     * }</pre>
     *
     * @return the ZSTD compression provider; never returns {@code null}
     *
     * @see ZstdCompressionProvider
     * @see #lz4()
     */
    public static @NotNull CompressionProvider zstd() {
        return BY_ID.get(ZstdCompressionProvider.ID);
    }

    /**
     * Returns the built-in LZ4 compression provider.
     *
     * <p>This is a convenience method equivalent to {@code require("lz4")}
     * but without the overhead of string lookup. Use this method when you
     * specifically need LZ4 compression for maximum speed.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li><strong>Compression ratio:</strong> Moderate (lower than ZSTD)</li>
     *   <li><strong>Compression speed:</strong> Extremely fast</li>
     *   <li><strong>Decompression speed:</strong> Extremely fast</li>
     *   <li><strong>Levels:</strong> 0 (fast mode), 1-17 (HC mode)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * CompressionProvider lz4 = CompressionRegistry.lz4();
     * byte[] compressed = lz4.compressBlock(data, 0);  // Fast mode
     * }</pre>
     *
     * @return the LZ4 compression provider; never returns {@code null}
     *
     * @see Lz4CompressionProvider
     * @see #zstd()
     */
    public static @NotNull CompressionProvider lz4() {
        return BY_ID.get(Lz4CompressionProvider.ID);
    }

}
