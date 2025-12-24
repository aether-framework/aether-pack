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

package de.splatgames.aether.pack.core.checksum;

import de.splatgames.aether.pack.core.spi.ChecksumProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for checksum algorithm providers.
 *
 * <p>This registry provides lookup and management of {@link ChecksumProvider}
 * implementations. Providers can be looked up by their string name or numeric ID,
 * and new providers can be registered programmatically.</p>
 *
 * <h2>Built-in Providers</h2>
 * <p>The following providers are registered automatically at class loading:</p>
 * <ul>
 *   <li><strong>crc32</strong> (ID: 0) - Standard CRC-32 checksum</li>
 *   <li><strong>xxh3-64</strong> (ID: 1) - XXH3 64-bit hash (default)</li>
 * </ul>
 *
 * <h2>ServiceLoader Discovery</h2>
 * <p>Additional providers are automatically discovered via {@link ServiceLoader}.
 * To register a custom provider, add a {@code META-INF/services} file:</p>
 * <pre>
 * META-INF/services/de.splatgames.aether.pack.core.spi.ChecksumProvider
 * </pre>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Get default provider (XXH3-64)
 * ChecksumProvider provider = ChecksumRegistry.getDefault();
 *
 * // Lookup by name
 * ChecksumProvider crc = ChecksumRegistry.requireByName("crc32");
 *
 * // Lookup by ID (for reading from binary format)
 * ChecksumProvider fromFile = ChecksumRegistry.requireById(header.checksumAlgorithm());
 *
 * // Optional lookup
 * Optional<ChecksumProvider> optional = ChecksumRegistry.getByName("sha256");
 * if (optional.isPresent()) {
 *     // Use the provider
 * }
 *
 * // Register a custom provider
 * ChecksumRegistry.register(new MyCustomChecksum());
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. All methods can be called concurrently from
 * multiple threads. The underlying maps use {@link ConcurrentHashMap}.</p>
 *
 * @see ChecksumProvider
 * @see Crc32Checksum
 * @see XxHash3Checksum
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ChecksumRegistry {

    /**
     * Map of checksum providers indexed by their numeric ID.
     * Used for efficient lookup when reading checksum algorithm IDs from binary format.
     */
    private static final Map<Integer, ChecksumProvider> BY_ID = new ConcurrentHashMap<>();

    /**
     * Map of checksum providers indexed by their lowercase string name.
     * Used for case-insensitive lookup by algorithm name.
     */
    private static final Map<String, ChecksumProvider> BY_NAME = new ConcurrentHashMap<>();

    static {
        // Register built-in providers
        register(new Crc32Checksum());
        register(new XxHash3Checksum());

        // Load additional providers via ServiceLoader
        ServiceLoader.load(ChecksumProvider.class).forEach(ChecksumRegistry::register);
    }

    /**
     * Private constructor to prevent instantiation.
     * <p>
     * This is a utility class with only static methods.
     * </p>
     */
    private ChecksumRegistry() {
        // Utility class
    }

    /**
     * Registers a checksum provider in the registry.
     *
     * <p>This method adds the specified provider to both the ID-based and name-based
     * lookup maps, making it available for retrieval via {@link #getById(int)},
     * {@link #getByName(String)}, or their require variants.</p>
     *
     * <p>If a provider with the same numeric ID or string name already exists,
     * it will be replaced with the new provider. This allows for overriding
     * built-in providers with custom implementations if needed.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The provider's name is converted to lowercase before registration,
     * allowing case-insensitive lookups via {@link #getByName(String)}.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>This method is thread-safe and can be called concurrently with lookups
     * and other registrations.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Register a custom checksum provider
     * ChecksumRegistry.register(new MyCustomChecksum());
     *
     * // The provider is now available for lookup
     * ChecksumProvider custom = ChecksumRegistry.requireByName("my-custom");
     * }</pre>
     *
     * @param provider the checksum provider to register; must not be {@code null};
     *                 the provider's {@link ChecksumProvider#getNumericId()} and
     *                 {@link ChecksumProvider#getId()} are used as lookup keys
     *
     * @see #getById(int)
     * @see #getByName(String)
     */
    public static void register(final @NotNull ChecksumProvider provider) {
        BY_ID.put(provider.getNumericId(), provider);
        BY_NAME.put(provider.getId().toLowerCase(), provider);
    }

    /**
     * Retrieves a checksum provider by its numeric identifier.
     *
     * <p>This method looks up a provider using the numeric ID stored in APACK
     * file headers. Numeric IDs are used in the binary format to identify
     * checksum algorithms in a compact, language-independent way.</p>
     *
     * <p>This is the preferred lookup method when reading archives, as the
     * file header contains the numeric algorithm ID rather than the string name.</p>
     *
     * <p><strong>Known IDs:</strong></p>
     * <ul>
     *   <li>{@code 0} - CRC-32</li>
     *   <li>{@code 1} - XXH3-64</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Reading from file header
     * FileHeader header = HeaderIO.readFileHeader(reader);
     * Optional<ChecksumProvider> provider = ChecksumRegistry.getById(header.checksumAlgorithm());
     *
     * if (provider.isPresent()) {
     *     // Use provider to verify chunk checksums
     * } else {
     *     // Unknown algorithm, cannot verify checksums
     * }
     * }</pre>
     *
     * @param id the numeric identifier of the checksum algorithm to retrieve;
     *           corresponds to {@link ChecksumProvider#getNumericId()}
     * @return an {@link Optional} containing the provider if found, or an empty
     *         {@link Optional} if no provider with the given ID is registered;
     *         never {@code null}
     *
     * @see #requireById(int)
     * @see #getByName(String)
     * @see ChecksumProvider#getNumericId()
     */
    public static @NotNull Optional<ChecksumProvider> getById(final int id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /**
     * Retrieves a checksum provider by its string identifier.
     *
     * <p>This method looks up a provider using its human-readable algorithm name.
     * The lookup is case-insensitive, so "CRC32", "crc32", and "Crc32" all match
     * the same provider.</p>
     *
     * <p>This is the preferred lookup method when the algorithm is specified
     * programmatically or in configuration files.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>{@code "crc32"} - Standard CRC-32 checksum</li>
     *   <li>{@code "xxh3-64"} - XXH3 64-bit hash (default, recommended)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Look up by name from configuration
     * String algorithmName = config.getChecksumAlgorithm();
     * Optional<ChecksumProvider> provider = ChecksumRegistry.getByName(algorithmName);
     *
     * if (provider.isEmpty()) {
     *     System.err.println("Unknown checksum algorithm: " + algorithmName);
     *     return;
     * }
     *
     * // Use the provider
     * long checksum = provider.get().compute(data);
     * }</pre>
     *
     * @param name the algorithm name to look up; must not be {@code null};
     *             the lookup is case-insensitive (converted to lowercase)
     * @return an {@link Optional} containing the provider if found, or an empty
     *         {@link Optional} if no provider with the given name is registered;
     *         never {@code null}
     *
     * @see #requireByName(String)
     * @see #getById(int)
     * @see ChecksumProvider#getId()
     */
    public static @NotNull Optional<ChecksumProvider> getByName(final @NotNull String name) {
        return Optional.ofNullable(BY_NAME.get(name.toLowerCase()));
    }

    /**
     * Retrieves a checksum provider by its numeric ID, throwing if not found.
     *
     * <p>This method is similar to {@link #getById(int)} but throws an exception
     * instead of returning an empty {@link Optional} when the provider is not
     * found. This is useful when the provider is required for correct operation
     * and its absence indicates an error condition.</p>
     *
     * <p>This is typically used when reading archives where the checksum algorithm
     * must be known to verify data integrity.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Reading from file header - provider must exist
     * FileHeader header = HeaderIO.readFileHeader(reader);
     * ChecksumProvider provider = ChecksumRegistry.requireById(header.checksumAlgorithm());
     *
     * // Verify chunk checksums using the provider
     * ChunkedInputStream in = new ChunkedInputStream(input, provider);
     * }</pre>
     *
     * @param id the numeric identifier of the checksum algorithm to retrieve;
     *           corresponds to {@link ChecksumProvider#getNumericId()}
     * @return the checksum provider with the specified ID; never {@code null}
     * @throws IllegalArgumentException if no provider with the given ID is
     *                                  registered in the registry
     *
     * @see #getById(int)
     * @see #requireByName(String)
     */
    public static @NotNull ChecksumProvider requireById(final int id) {
        return getById(id).orElseThrow(() ->
                new IllegalArgumentException("Unknown checksum algorithm ID: " + id));
    }

    /**
     * Retrieves a checksum provider by its name, throwing if not found.
     *
     * <p>This method is similar to {@link #getByName(String)} but throws an
     * exception instead of returning an empty {@link Optional} when the provider
     * is not found. The lookup is case-insensitive.</p>
     *
     * <p>This is typically used when the algorithm name comes from trusted
     * configuration and its presence is required for correct operation.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // From configuration - algorithm must be valid
     * String algorithm = config.getChecksumAlgorithm();
     * ChecksumProvider provider = ChecksumRegistry.requireByName(algorithm);
     *
     * // Create archive with specified checksum
     * ApackConfiguration config = ApackConfiguration.builder()
     *     .checksumProvider(provider)
     *     .build();
     * }</pre>
     *
     * @param name the algorithm name to look up; must not be {@code null};
     *             the lookup is case-insensitive (converted to lowercase)
     * @return the checksum provider with the specified name; never {@code null}
     * @throws IllegalArgumentException if no provider with the given name is
     *                                  registered in the registry
     *
     * @see #getByName(String)
     * @see #requireById(int)
     */
    public static @NotNull ChecksumProvider requireByName(final @NotNull String name) {
        return getByName(name).orElseThrow(() ->
                new IllegalArgumentException("Unknown checksum algorithm: " + name));
    }

    /**
     * Returns all registered checksum providers.
     *
     * <p>This method returns an unmodifiable view of all providers currently
     * registered in the registry. This is useful for discovering available
     * algorithms, building user interfaces, or diagnostic purposes.</p>
     *
     * <p>The returned collection includes both built-in providers (CRC-32 and
     * XXH3-64) and any custom providers that have been registered via
     * {@link #register(ChecksumProvider)} or discovered via {@link ServiceLoader}.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The returned collection is a snapshot-like view. Providers registered
     * after this method returns may not appear in the collection, but the
     * collection itself is safe to iterate concurrently.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // List all available algorithms
     * System.out.println("Available checksum algorithms:");
     * for (ChecksumProvider provider : ChecksumRegistry.getAll()) {
     *     System.out.printf("  %s (ID: %d, %d bytes)%n",
     *         provider.getId(),
     *         provider.getNumericId(),
     *         provider.getChecksumSize());
     * }
     * }</pre>
     *
     * @return an unmodifiable collection containing all registered checksum
     *         providers; never {@code null}; may be empty if all providers
     *         have been removed (unlikely in practice)
     *
     * @see #register(ChecksumProvider)
     * @see #getById(int)
     * @see #getByName(String)
     */
    public static @NotNull Collection<ChecksumProvider> getAll() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /**
     * Returns the default checksum provider (XXH3-64).
     *
     * <p>The default provider is {@link XxHash3Checksum} (XXH3-64), which offers
     * an excellent balance of speed and hash quality. XXH3 is significantly faster
     * than CRC-32 on modern processors while providing superior bit distribution
     * and collision resistance.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li><strong>Performance:</strong> XXH3 can process data at 10+ GB/s on
     *       modern CPUs with SIMD support</li>
     *   <li><strong>Quality:</strong> 64-bit output provides excellent collision
     *       resistance for data integrity verification</li>
     *   <li><strong>Compatibility:</strong> Well-established algorithm with
     *       implementations in many languages</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Use default checksum for new archives
     * ChecksumProvider provider = ChecksumRegistry.getDefault();
     *
     * // Create configuration with default checksum
     * ApackConfiguration config = ApackConfiguration.builder()
     *     .checksumProvider(ChecksumRegistry.getDefault())
     *     .build();
     * }</pre>
     *
     * @return the default checksum provider (XXH3-64); never {@code null}
     *
     * @see XxHash3Checksum
     * @see #requireByName(String)
     */
    public static @NotNull ChecksumProvider getDefault() {
        return requireByName("xxh3-64");
    }

}
