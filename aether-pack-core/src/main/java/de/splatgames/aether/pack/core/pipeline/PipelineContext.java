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
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Type-safe context for sharing state between pipeline stages.
 *
 * <p>The pipeline context provides a key-value store for stages to
 * communicate and share computed values. It uses type-safe keys to
 * ensure compile-time type checking when storing and retrieving values.</p>
 *
 * <h2>Well-Known Keys</h2>
 * <p>The following keys are defined for common use cases:</p>
 * <table>
 *   <caption>Standard Context Keys</caption>
 *   <tr><th>Key</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>{@link #ORIGINAL_SIZE}</td><td>Long</td><td>Uncompressed data size</td></tr>
 *   <tr><td>{@link #COMPRESSED_SIZE}</td><td>Long</td><td>Compressed data size</td></tr>
 *   <tr><td>{@link #ENCRYPTED_SIZE}</td><td>Long</td><td>Encrypted data size</td></tr>
 *   <tr><td>{@link #CHECKSUM}</td><td>Long</td><td>Computed checksum value</td></tr>
 *   <tr><td>{@link #CHUNK_COUNT}</td><td>Integer</td><td>Number of chunks</td></tr>
 *   <tr><td>{@link #COMPRESSION_ID}</td><td>Integer</td><td>Algorithm ID used</td></tr>
 *   <tr><td>{@link #ENCRYPTION_ID}</td><td>Integer</td><td>Algorithm ID used</td></tr>
 *   <tr><td>{@link #ENTRY_NAME}</td><td>String</td><td>Entry being processed</td></tr>
 * </table>
 *
 * <h2>Custom Keys</h2>
 * <p>Create custom keys for domain-specific values:</p>
 * <pre>{@code
 * // Define custom key
 * Key<String> HASH_ALGORITHM = Key.of("hashAlgorithm", String.class);
 *
 * // Use in stage
 * context.set(HASH_ALGORITHM, "SHA-256");
 *
 * // Retrieve later
 * String algo = context.getOrDefault(HASH_ALGORITHM, "XXH3");
 * }</pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create context for a pipeline operation
 * PipelineContext context = new PipelineContext();
 * context.set(PipelineContext.ENTRY_NAME, "document.pdf");
 *
 * // Use with pipeline
 * try (OutputStream out = pipeline.wrapOutput(rawOutput, context)) {
 *     out.write(data);
 * }
 *
 * // After processing, retrieve computed values
 * long checksum = context.getRequired(PipelineContext.CHECKSUM);
 * long originalSize = context.getOrDefault(PipelineContext.ORIGINAL_SIZE, 0L);
 * Optional<Long> compressedSize = context.get(PipelineContext.COMPRESSED_SIZE);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. All operations use a {@link ConcurrentHashMap}
 * internally. The atomic update methods ({@link #addToLong}, {@link #addToInt})
 * are safe for concurrent modification.</p>
 *
 * @see PipelineStage
 * @see ProcessingPipeline
 * @see Key
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class PipelineContext {

    /**
     * Context key for type-safe value storage.
     *
     * @param <T> the value type
     */
    public static final class Key<T> {

        /** The descriptive name for debugging and logging. */
        private final @NotNull String name;

        /** The value type class for documentation and reflection. */
        private final @NotNull Class<T> type;

        /**
         * Constructs a new context key with the specified name and type.
         *
         * @param name the key name (for debugging and toString)
         * @param type the value type class
         */
        private Key(final @NotNull String name, final @NotNull Class<T> type) {
            this.name = name;
            this.type = type;
        }

        /**
         * Creates a new context key with the specified name and type.
         *
         * <p>This factory method creates a type-safe key for storing and
         * retrieving values from the pipeline context. The key's type
         * parameter ensures compile-time type checking when accessing
         * context values.</p>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * // Define custom keys for your stage
         * Key<String> HASH_ALGORITHM = Key.of("hashAlgorithm", String.class);
         * Key<byte[]> SIGNATURE = Key.of("signature", byte[].class);
         *
         * // Use in stage implementation
         * context.set(HASH_ALGORITHM, "SHA-256");
         * String algo = context.getOrDefault(HASH_ALGORITHM, "XXH3");
         * }</pre>
         *
         * @param name the key name; must not be {@code null}; used for debugging,
         *             logging, and the {@link #toString()} representation; should
         *             be descriptive and unique within your stage's domain
         * @param type the value type class; must not be {@code null}; used for
         *             type safety documentation; the actual type checking is
         *             performed at compile time via the generic parameter
         * @param <T>  the value type; determines what type of values can be
         *             stored and retrieved using this key
         * @return a new key instance; never returns {@code null}; each call
         *         creates a new key instance - use constants for keys that
         *         should be shared across components
         */
        public static <T> @NotNull Key<T> of(final @NotNull String name, final @NotNull Class<T> type) {
            return new Key<>(name, type);
        }

        /**
         * Returns the name of this context key.
         *
         * <p>The name is primarily used for debugging, logging, and the
         * {@link #toString()} representation. It should be descriptive
         * enough to identify the key's purpose.</p>
         *
         * @return the key name as provided to the {@link #of(String, Class)}
         *         factory method; never returns {@code null}; the value is
         *         immutable and will be the same on every call
         *
         * @see #toString()
         */
        public @NotNull String getName() {
            return this.name;
        }

        /**
         * Returns the type class of values stored under this key.
         *
         * <p>The type is used for documentation and can be useful for
         * runtime type checking or reflection-based operations. The
         * actual type safety is enforced at compile time through the
         * generic type parameter.</p>
         *
         * @return the value type class as provided to the {@link #of(String, Class)}
         *         factory method; never returns {@code null}; for example,
         *         {@code Long.class} for keys storing long values
         *
         * @see #toString()
         */
        public @NotNull Class<T> getType() {
            return this.type;
        }

        /**
         * Returns a string representation of this key.
         *
         * @return string in format "Key[name: Type]"
         */
        @Override
        public String toString() {
            return "Key[" + this.name + ": " + this.type.getSimpleName() + "]";
        }

    }

    // Well-known context keys
    /** Original (uncompressed) size of the data. */
    public static final Key<Long> ORIGINAL_SIZE = Key.of("originalSize", Long.class);

    /** Compressed size of the data. */
    public static final Key<Long> COMPRESSED_SIZE = Key.of("compressedSize", Long.class);

    /** Encrypted size of the data. */
    public static final Key<Long> ENCRYPTED_SIZE = Key.of("encryptedSize", Long.class);

    /** Computed checksum value. */
    public static final Key<Long> CHECKSUM = Key.of("checksum", Long.class);

    /** Computed checksum as bytes (for larger checksums). */
    public static final Key<byte[]> CHECKSUM_BYTES = Key.of("checksumBytes", byte[].class);

    /** Number of chunks processed. */
    public static final Key<Integer> CHUNK_COUNT = Key.of("chunkCount", Integer.class);

    /** Current chunk index (0-based). */
    public static final Key<Integer> CURRENT_CHUNK = Key.of("currentChunk", Integer.class);

    /** Entry ID being processed. */
    public static final Key<Long> ENTRY_ID = Key.of("entryId", Long.class);

    /** Entry name being processed. */
    public static final Key<String> ENTRY_NAME = Key.of("entryName", String.class);

    /** Compression algorithm ID used. */
    public static final Key<Integer> COMPRESSION_ID = Key.of("compressionId", Integer.class);

    /** Encryption algorithm ID used. */
    public static final Key<Integer> ENCRYPTION_ID = Key.of("encryptionId", Integer.class);

    /** Whether error correction is enabled. */
    public static final Key<Boolean> ECC_ENABLED = Key.of("eccEnabled", Boolean.class);

    /** Internal map storing all context key-value pairs. */
    private final @NotNull Map<Key<?>, Object> values;

    /**
     * Creates a new empty pipeline context.
     *
     * <p>The context is initialized with no values. Values can be added
     * using {@link #set(Key, Object)} or the atomic update methods
     * {@link #addToLong(Key, long)} and {@link #addToInt(Key, int)}.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * PipelineContext context = new PipelineContext();
     * context.set(PipelineContext.ENTRY_NAME, "document.pdf");
     *
     * try (OutputStream out = pipeline.wrapOutput(rawOutput, context)) {
     *     out.write(data);
     * }
     *
     * long checksum = context.getRequired(PipelineContext.CHECKSUM);
     * }</pre>
     */
    public PipelineContext() {
        this.values = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new context with initial values copied from another context.
     *
     * <p>This constructor creates a shallow copy of the source context.
     * All key-value pairs from the source are copied to the new context.
     * Modifications to the new context do not affect the source context,
     * and vice versa.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>Creating a child context that inherits parent values</li>
     *   <li>Preserving context state before modifications</li>
     *   <li>Isolating context modifications in parallel operations</li>
     * </ul>
     *
     * @param source the source context to copy values from; must not be
     *               {@code null}; all key-value pairs are copied to the
     *               new context; the source context is not modified
     */
    public PipelineContext(final @NotNull PipelineContext source) {
        this.values = new ConcurrentHashMap<>(source.values);
    }

    /**
     * Stores a value in the context under the specified key.
     *
     * <p>If a value already exists for the key, it is replaced with the
     * new value. The previous value is not returned - use {@link #get(Key)}
     * before calling this method if you need the previous value.</p>
     *
     * <p>This method is thread-safe and can be called concurrently from
     * multiple threads.</p>
     *
     * @param key   the key under which to store the value; must not be
     *              {@code null}; the key's type parameter must match the
     *              value's type
     * @param value the value to store; must not be {@code null}; the value
     *              replaces any previously stored value for this key
     * @param <T>   the value type; must match the key's type parameter
     * @return this context instance for method chaining; never returns
     *         {@code null}; allows fluent API usage
     *
     * @see #get(Key)
     * @see #getOrDefault(Key, Object)
     */
    public <T> @NotNull PipelineContext set(final @NotNull Key<T> key, final @NotNull T value) {
        this.values.put(key, value);
        return this;
    }

    /**
     * Retrieves a value from the context for the specified key.
     *
     * <p>This method returns an {@link Optional} that contains the value
     * if present, or is empty if no value exists for the key. Use this
     * method when the value may or may not be present and you want to
     * handle both cases explicitly.</p>
     *
     * <p>This method is thread-safe and can be called concurrently from
     * multiple threads.</p>
     *
     * @param key the key to look up; must not be {@code null}
     * @param <T> the value type; determined by the key's type parameter
     * @return an {@link Optional} containing the value if present, or an
     *         empty Optional if no value exists for the key; never returns
     *         {@code null}; the contained value is cast to the key's type
     *
     * @see #getOrDefault(Key, Object)
     * @see #getRequired(Key)
     * @see #has(Key)
     */
    @SuppressWarnings("unchecked")
    public <T> @NotNull Optional<T> get(final @NotNull Key<T> key) {
        final Object value = this.values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of((T) value);
    }

    /**
     * Retrieves a value from the context, returning a default if not present.
     *
     * <p>This method provides a convenient way to access context values
     * when a sensible default exists. If no value is stored for the key,
     * the default value is returned instead.</p>
     *
     * <p>This method is thread-safe and can be called concurrently from
     * multiple threads.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * long originalSize = context.getOrDefault(PipelineContext.ORIGINAL_SIZE, 0L);
     * int chunkCount = context.getOrDefault(PipelineContext.CHUNK_COUNT, 1);
     * }</pre>
     *
     * @param key          the key to look up; must not be {@code null}
     * @param defaultValue the value to return if the key is not present;
     *                     must not be {@code null}; returned directly if
     *                     no value exists for the key
     * @param <T>          the value type; determined by the key's type parameter
     * @return the value stored for the key, or the default value if the key
     *         is not present; never returns {@code null}
     *
     * @see #get(Key)
     * @see #getRequired(Key)
     */
    @SuppressWarnings("unchecked")
    public <T> @NotNull T getOrDefault(final @NotNull Key<T> key, final @NotNull T defaultValue) {
        final Object value = this.values.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }

    /**
     * Retrieves a required value from the context, throwing if not present.
     *
     * <p>This method is used when a value is expected to be present and
     * its absence indicates a programming error or invalid state. If the
     * key is not present, an {@link IllegalStateException} is thrown with
     * a descriptive message.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // After pipeline processing, checksum should always be set
     * long checksum = context.getRequired(PipelineContext.CHECKSUM);
     * }</pre>
     *
     * @param key the key to look up; must not be {@code null}
     * @param <T> the value type; determined by the key's type parameter
     * @return the value stored for the key; never returns {@code null};
     *         guaranteed to be present if the method returns normally
     * @throws IllegalStateException if no value exists for the key; the
     *         exception message includes the key's string representation
     *         for debugging purposes
     *
     * @see #get(Key)
     * @see #getOrDefault(Key, Object)
     * @see #has(Key)
     */
    public <T> @NotNull T getRequired(final @NotNull Key<T> key) {
        return get(key).orElseThrow(() ->
                new IllegalStateException("Required context key not present: " + key));
    }

    /**
     * Checks if a value exists for the specified key.
     *
     * <p>This method tests whether a value has been stored for the key
     * without retrieving the value. Useful for conditional logic based
     * on the presence of optional values.</p>
     *
     * @param key the key to check; must not be {@code null}
     * @return {@code true} if a value exists for the key; {@code false}
     *         if no value has been stored for this key
     *
     * @see #get(Key)
     * @see #getRequired(Key)
     */
    public boolean has(final @NotNull Key<?> key) {
        return this.values.containsKey(key);
    }

    /**
     * Removes a value from the context for the specified key.
     *
     * <p>This method removes and returns the value stored for the key.
     * If no value exists, an empty Optional is returned. The key can
     * be reused after removal to store a new value.</p>
     *
     * <p>This method is thread-safe and can be called concurrently from
     * multiple threads.</p>
     *
     * @param key the key whose value should be removed; must not be
     *            {@code null}
     * @param <T> the value type; determined by the key's type parameter
     * @return an {@link Optional} containing the removed value if it was
     *         present, or an empty Optional if no value existed for the
     *         key; never returns {@code null}
     *
     * @see #clear()
     */
    @SuppressWarnings("unchecked")
    public <T> @NotNull Optional<T> remove(final @NotNull Key<T> key) {
        final Object value = this.values.remove(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of((T) value);
    }

    /**
     * Removes all values from the context.
     *
     * <p>After this method returns, the context is empty and
     * {@link #isEmpty()} returns {@code true}. This is useful for
     * reusing a context instance for a new pipeline operation.</p>
     *
     * <p>This method is thread-safe and can be called concurrently from
     * multiple threads.</p>
     *
     * @see #remove(Key)
     * @see #isEmpty()
     */
    public void clear() {
        this.values.clear();
    }

    /**
     * Returns the number of values stored in the context.
     *
     * <p>This method returns the count of key-value pairs currently
     * stored in the context. The count may change if other threads
     * are modifying the context concurrently.</p>
     *
     * @return the number of key-value pairs in the context; never
     *         negative; returns 0 if the context is empty
     *
     * @see #isEmpty()
     */
    public int size() {
        return this.values.size();
    }

    /**
     * Checks if the context contains no values.
     *
     * <p>This method tests whether any values have been stored in the
     * context. Equivalent to {@code size() == 0}.</p>
     *
     * @return {@code true} if the context contains no key-value pairs;
     *         {@code false} if at least one value is stored
     *
     * @see #size()
     * @see #clear()
     */
    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    /**
     * Atomically adds a delta to a long value and returns the result.
     *
     * <p>This method performs an atomic read-modify-write operation on
     * the value stored for the key. If no value exists for the key, it
     * is treated as 0. The delta is added to the current value and the
     * result is stored back and returned.</p>
     *
     * <p>This method is thread-safe and can be used to safely increment
     * counters from multiple threads without external synchronization.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Count bytes processed across multiple chunks
     * long totalBytes = context.addToLong(PipelineContext.ORIGINAL_SIZE, chunkSize);
     * }</pre>
     *
     * @param key   the key for the long value to modify; must not be
     *              {@code null}; must be a key typed for {@link Long}
     * @param delta the amount to add to the current value; can be positive,
     *              negative, or zero; added to the current value (or 0 if
     *              no value exists)
     * @return the new value after adding the delta; if no value existed
     *         before, returns the delta itself
     *
     * @see #addToInt(Key, int)
     */
    public long addToLong(final @NotNull Key<Long> key, final long delta) {
        return (Long) this.values.compute(key, (k, v) -> {
            final long current = v == null ? 0L : (Long) v;
            return current + delta;
        });
    }

    /**
     * Atomically adds a delta to an integer value and returns the result.
     *
     * <p>This method performs an atomic read-modify-write operation on
     * the value stored for the key. If no value exists for the key, it
     * is treated as 0. The delta is added to the current value and the
     * result is stored back and returned.</p>
     *
     * <p>This method is thread-safe and can be used to safely increment
     * counters from multiple threads without external synchronization.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Count chunks processed
     * int chunkNumber = context.addToInt(PipelineContext.CHUNK_COUNT, 1);
     * }</pre>
     *
     * @param key   the key for the integer value to modify; must not be
     *              {@code null}; must be a key typed for {@link Integer}
     * @param delta the amount to add to the current value; can be positive,
     *              negative, or zero; added to the current value (or 0 if
     *              no value exists)
     * @return the new value after adding the delta; if no value existed
     *         before, returns the delta itself
     *
     * @see #addToLong(Key, long)
     */
    public int addToInt(final @NotNull Key<Integer> key, final int delta) {
        return (Integer) this.values.compute(key, (k, v) -> {
            final int current = v == null ? 0 : (Integer) v;
            return current + delta;
        });
    }

    /**
     * Returns a string representation of this context.
     *
     * @return string representation showing all key-value pairs
     */
    @Override
    public String toString() {
        return "PipelineContext" + this.values;
    }

}
