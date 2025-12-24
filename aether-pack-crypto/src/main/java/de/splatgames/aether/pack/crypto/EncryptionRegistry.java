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

package de.splatgames.aether.pack.crypto;

import de.splatgames.aether.pack.core.spi.EncryptionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for encryption providers used by APACK archives.
 *
 * <p>This registry manages all available encryption algorithms and provides
 * lookup methods for retrieving providers by string ID or numeric ID.
 * Providers are discovered both statically (built-in implementations) and
 * dynamically via {@link ServiceLoader}.</p>
 *
 * <h2>Built-in Providers</h2>
 * <p>The following encryption providers are always available:</p>
 * <table>
 *   <caption>Built-in Encryption Algorithms</caption>
 *   <tr><th>ID</th><th>Numeric ID</th><th>Key Size</th><th>Description</th></tr>
 *   <tr><td>{@code aes-256-gcm}</td><td>1</td><td>256 bits</td><td>AES-256 in GCM mode</td></tr>
 *   <tr><td>{@code chacha20-poly1305}</td><td>2</td><td>256 bits</td><td>ChaCha20 with Poly1305 MAC</td></tr>
 * </table>
 *
 * <h2>Provider Discovery</h2>
 * <p>Custom providers can be registered in two ways:</p>
 * <ul>
 *   <li><strong>ServiceLoader:</strong> Implement {@link EncryptionProvider} and
 *       declare it in {@code META-INF/services/de.splatgames.aether.pack.core.spi.EncryptionProvider}</li>
 *   <li><strong>Programmatic:</strong> Call {@link #register(EncryptionProvider)} at startup</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Get a provider by string ID
 * EncryptionProvider aes = EncryptionRegistry.require("aes-256-gcm");
 *
 * // Get a provider by numeric ID (used in binary format)
 * EncryptionProvider provider = EncryptionRegistry.requireById(1);
 *
 * // Use convenience methods for built-in providers
 * EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
 * EncryptionProvider chacha = EncryptionRegistry.chaCha20Poly1305();
 *
 * // Generate a key and encrypt
 * SecretKey key = aes.generateKey();
 * OutputStream encrypted = aes.encrypt(output, key);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. The internal maps use {@link ConcurrentHashMap},
 * and registration uses {@code putIfAbsent} to avoid race conditions during
 * dynamic provider loading.</p>
 *
 * @see EncryptionProvider
 * @see Aes256GcmEncryptionProvider
 * @see ChaCha20Poly1305EncryptionProvider
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class EncryptionRegistry {

    /**
     * Map of providers indexed by their lowercase string ID.
     * Used for case-insensitive lookup by algorithm name.
     */
    private static final Map<String, EncryptionProvider> BY_ID = new ConcurrentHashMap<>();

    /**
     * Map of providers indexed by their numeric ID.
     * Used for efficient lookup when reading from binary format.
     */
    private static final Map<Integer, EncryptionProvider> BY_NUMERIC_ID = new ConcurrentHashMap<>();

    static {
        // Register built-in providers
        register(new Aes256GcmEncryptionProvider());
        register(new ChaCha20Poly1305EncryptionProvider());

        // Load providers via ServiceLoader
        final ServiceLoader<EncryptionProvider> loader = ServiceLoader.load(EncryptionProvider.class);
        for (final EncryptionProvider provider : loader) {
            register(provider);
        }
    }

    /**
     * Private constructor to prevent instantiation.
     *
     * <p>This is a utility class with only static methods and should not be instantiated.</p>
     */
    private EncryptionRegistry() {
    }

    /**
     * Registers an encryption provider with this registry.
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
     * @param provider the encryption provider to register; must not be {@code null};
     *                 the provider's {@link EncryptionProvider#getId()} and
     *                 {@link EncryptionProvider#getNumericId()} methods are called
     *                 to determine the lookup keys
     * @throws NullPointerException if {@code provider} is {@code null}
     *
     * @see #get(String)
     * @see #getById(int)
     */
    public static void register(final @NotNull EncryptionProvider provider) {
        BY_ID.putIfAbsent(provider.getId(), provider);
        BY_NUMERIC_ID.putIfAbsent(provider.getNumericId(), provider);
    }

    /**
     * Returns an encryption provider by its string identifier.
     *
     * <p>The lookup is case-insensitive; the ID is converted to lowercase
     * before searching. If no provider with the given ID is registered,
     * an empty {@link Optional} is returned.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * Optional<EncryptionProvider> provider = EncryptionRegistry.get("aes-256-gcm");
     * if (provider.isPresent()) {
     *     SecretKey key = provider.get().generateKey();
     * }
     * }</pre>
     *
     * @param id the provider's string identifier; must not be {@code null};
     *           common values include {@code "aes-256-gcm"} and
     *           {@code "chacha20-poly1305"}; case-insensitive
     * @return an {@link Optional} containing the provider if found, or
     *         {@link Optional#empty()} if no provider with the given ID exists;
     *         never returns {@code null}
     * @throws NullPointerException if {@code id} is {@code null}
     *
     * @see #require(String)
     * @see #getById(int)
     */
    public static @NotNull Optional<EncryptionProvider> get(final @NotNull String id) {
        return Optional.ofNullable(BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    /**
     * Returns an encryption provider by its numeric identifier.
     *
     * <p>Numeric IDs are used in the binary format to identify the encryption
     * algorithm. This method is typically called when reading archive headers
     * to resolve the algorithm for decryption.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>{@code 1} - AES-256-GCM</li>
     *   <li>{@code 2} - ChaCha20-Poly1305</li>
     * </ul>
     *
     * @param numericId the provider's numeric identifier as stored in the
     *                  binary format; must match a registered provider's
     *                  {@link EncryptionProvider#getNumericId()} value
     * @return an {@link Optional} containing the provider if found, or
     *         {@link Optional#empty()} if no provider with the given ID exists;
     *         never returns {@code null}
     *
     * @see #requireById(int)
     * @see #get(String)
     */
    public static @NotNull Optional<EncryptionProvider> getById(final int numericId) {
        return Optional.ofNullable(BY_NUMERIC_ID.get(numericId));
    }

    /**
     * Returns an encryption provider by string ID, throwing if not found.
     *
     * <p>This method is equivalent to {@link #get(String)} but throws an
     * exception instead of returning an empty {@link Optional} when the
     * provider is not found. Use this method when a missing provider
     * indicates a programming error or invalid configuration.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Throws NoSuchElementException if not found
     * EncryptionProvider aes = EncryptionRegistry.require("aes-256-gcm");
     * SecretKey key = aes.generateKey();
     * }</pre>
     *
     * @param id the provider's string identifier; must not be {@code null};
     *           case-insensitive
     * @return the encryption provider; never returns {@code null}
     * @throws NoSuchElementException if no provider with the given ID is registered;
     *                                the exception message includes the requested ID
     * @throws NullPointerException if {@code id} is {@code null}
     *
     * @see #get(String)
     * @see #requireById(int)
     */
    public static @NotNull EncryptionProvider require(final @NotNull String id) {
        return get(id).orElseThrow(() ->
                new NoSuchElementException("Unknown encryption provider: " + id));
    }

    /**
     * Returns an encryption provider by numeric ID, throwing if not found.
     *
     * <p>This method is equivalent to {@link #getById(int)} but throws an
     * exception instead of returning an empty {@link Optional}. This is
     * typically used when reading archives, where an unknown algorithm ID
     * indicates file corruption or an unsupported format version.</p>
     *
     * @param numericId the provider's numeric identifier as stored in the
     *                  binary format
     * @return the encryption provider; never returns {@code null}
     * @throws NoSuchElementException if no provider with the given numeric ID
     *                                is registered; the exception message
     *                                includes the requested ID
     *
     * @see #getById(int)
     * @see #require(String)
     */
    public static @NotNull EncryptionProvider requireById(final int numericId) {
        return getById(numericId).orElseThrow(() ->
                new NoSuchElementException("Unknown encryption provider ID: " + numericId));
    }

    /**
     * Returns all registered encryption providers.
     *
     * <p>The returned collection is an unmodifiable view of the currently
     * registered providers. It reflects the state at the time of the call
     * and may not include providers registered concurrently.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * for (EncryptionProvider provider : EncryptionRegistry.getAll()) {
     *     System.out.println(provider.getId() + " (key size: " + provider.getKeySize() + " bytes)");
     * }
     * }</pre>
     *
     * @return an unmodifiable collection containing all registered encryption
     *         providers; never returns {@code null}; the collection may be
     *         empty if no providers are registered (though this should not
     *         happen in normal operation due to built-in providers)
     */
    public static @NotNull Collection<EncryptionProvider> getAll() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /**
     * Returns the built-in AES-256-GCM encryption provider.
     *
     * <p>This is a convenience method equivalent to
     * {@code require("aes-256-gcm")} but without the overhead of
     * string lookup. Use this method when you specifically need
     * AES-256-GCM encryption.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li><strong>Key size:</strong> 256 bits (32 bytes)</li>
     *   <li><strong>Nonce size:</strong> 96 bits (12 bytes)</li>
     *   <li><strong>Tag size:</strong> 128 bits (16 bytes)</li>
     *   <li><strong>Mode:</strong> Authenticated Encryption with Associated Data (AEAD)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
     * SecretKey key = aes.generateKey();
     * OutputStream encrypted = aes.encrypt(output, key);
     * }</pre>
     *
     * @return the AES-256-GCM encryption provider; never returns {@code null}
     *
     * @see Aes256GcmEncryptionProvider
     * @see #chaCha20Poly1305()
     */
    public static @NotNull EncryptionProvider aes256Gcm() {
        return BY_ID.get(Aes256GcmEncryptionProvider.ID);
    }

    /**
     * Returns the built-in ChaCha20-Poly1305 encryption provider.
     *
     * <p>This is a convenience method equivalent to
     * {@code require("chacha20-poly1305")} but without the overhead of
     * string lookup. ChaCha20-Poly1305 is an alternative to AES-GCM that
     * is faster in software implementations without hardware AES acceleration.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li><strong>Key size:</strong> 256 bits (32 bytes)</li>
     *   <li><strong>Nonce size:</strong> 96 bits (12 bytes)</li>
     *   <li><strong>Tag size:</strong> 128 bits (16 bytes)</li>
     *   <li><strong>Mode:</strong> Authenticated Encryption with Associated Data (AEAD)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * EncryptionProvider chacha = EncryptionRegistry.chaCha20Poly1305();
     * SecretKey key = chacha.generateKey();
     * OutputStream encrypted = chacha.encrypt(output, key);
     * }</pre>
     *
     * @return the ChaCha20-Poly1305 encryption provider; never returns {@code null}
     *
     * @see ChaCha20Poly1305EncryptionProvider
     * @see #aes256Gcm()
     */
    public static @NotNull EncryptionProvider chaCha20Poly1305() {
        return BY_ID.get(ChaCha20Poly1305EncryptionProvider.ID);
    }

}
