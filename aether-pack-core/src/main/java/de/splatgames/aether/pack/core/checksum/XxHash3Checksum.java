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

import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.core.spi.ChecksumProvider;
import net.openhft.hashing.LongHashFunction;
import org.jetbrains.annotations.NotNull;

/**
 * XXH3-64 checksum provider using the zero-allocation-hashing library.
 *
 * <p>XXH3 is an extremely fast non-cryptographic hash function from the
 * xxHash family. It provides significantly better performance than CRC-32
 * while also offering superior bit distribution and collision resistance.</p>
 *
 * <h2>Properties</h2>
 * <ul>
 *   <li><strong>Algorithm ID:</strong> "xxh3-64"</li>
 *   <li><strong>Numeric ID:</strong> {@link FormatConstants#CHECKSUM_XXH3_64} (1)</li>
 *   <li><strong>Output Size:</strong> 64 bits (8 bytes)</li>
 * </ul>
 *
 * <h2>Performance</h2>
 * <p>XXH3 is optimized for modern processors with SIMD instructions and can
 * process data at speeds exceeding 10 GB/s on high-end CPUs. This makes it
 * the recommended checksum algorithm for APACK archives.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Via registry (recommended)
 * ChecksumProvider xxh3 = ChecksumRegistry.getDefault();
 * long checksum = xxh3.compute(data);
 *
 * // Static convenience methods
 * long hash64 = XxHash3Checksum.hash64(data);
 * int hash32 = XxHash3Checksum.hash32(data);  // Lower 32 bits
 *
 * // Streaming (less efficient, buffers all data)
 * ChecksumProvider.Checksum calc = xxh3.createChecksum();
 * calc.update(chunk1);
 * calc.update(chunk2);
 * long result = calc.getValue();
 * }</pre>
 *
 * <h2>Implementation Note</h2>
 * <p>The streaming implementation buffers all data internally since the
 * underlying library doesn't provide a true streaming API. For large data,
 * prefer using {@link #compute(byte[], int, int)} or the static methods
 * {@link #hash64(byte[])} and {@link #hash32(byte[])} directly.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. The static hash methods and
 * {@link #compute(byte[], int, int)} can be called concurrently.
 * Each call to {@link #createChecksum()} returns a new independent instance.</p>
 *
 * @see Crc32Checksum
 * @see ChecksumRegistry
 * @see FormatConstants#CHECKSUM_XXH3_64
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class XxHash3Checksum implements ChecksumProvider {

    /**
     * Creates a new XXH3-64 checksum provider instance.
     */
    public XxHash3Checksum() {
        // Stateless provider
    }

    /**
     * Algorithm identifier string for XXH3-64 hash.
     * This value is used for registry lookup and configuration purposes.
     */
    public static final String ID = "xxh3-64";

    /** Shared XXH3 hash function instance from zero-allocation-hashing library. */
    private static final LongHashFunction XXH3 = LongHashFunction.xx3();

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull String getId() {
        return ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumericId() {
        return FormatConstants.CHECKSUM_XXH3_64;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getChecksumSize() {
        return 8;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Checksum createChecksum() {
        return new XxHash3ChecksumImpl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long compute(final byte @NotNull [] data, final int offset, final int length) {
        // Direct computation is more efficient than streaming for single blocks
        return XXH3.hashBytes(data, offset, length);
    }

    /**
     * Computes the XXH3-64 hash and returns only the lower 32 bits.
     *
     * <p>This convenience method computes the full 64-bit XXH3 hash and returns
     * just the lower 32 bits. This is useful in scenarios where a 32-bit value
     * is required (such as compatibility with existing APIs or storage formats)
     * but the quality of XXH3's bit distribution is desired.</p>
     *
     * <p>The lower 32 bits of XXH3-64 provide better quality than CRC-32 for
     * most use cases while maintaining compatibility with 32-bit checksum fields.</p>
     *
     * <p><strong>Example Usage:</strong></p>
     * <pre>{@code
     * // Compute 32-bit hash for name lookup
     * String entryName = "data/config.json";
     * int nameHash = XxHash3Checksum.hash32(entryName.getBytes(StandardCharsets.UTF_8));
     *
     * // Use in TOC entry
     * TocEntry entry = TocEntry.builder()
     *     .nameHash(nameHash)
     *     // ... other fields
     *     .build();
     * }</pre>
     *
     * @param data the byte array containing the data to hash; must not be
     *             {@code null}; the entire array is hashed
     * @return the lower 32 bits of the 64-bit XXH3 hash value; this is
     *         equivalent to {@code (int) hash64(data)}
     *
     * @see #hash64(byte[])
     * @see #compute(byte[], int, int)
     */
    public static int hash32(final byte @NotNull [] data) {
        return (int) XXH3.hashBytes(data);
    }

    /**
     * Computes the full 64-bit XXH3 hash of the given data.
     *
     * <p>This is the primary static convenience method for computing XXH3 hashes.
     * It provides direct access to the full 64-bit hash value without needing
     * to instantiate a {@link ChecksumProvider} or create a streaming checksum
     * calculator.</p>
     *
     * <p>This method is equivalent to calling:</p>
     * <pre>{@code
     * ChecksumProvider provider = new XxHash3Checksum();
     * long hash = provider.compute(data);
     * }</pre>
     *
     * <p><strong>Performance:</strong></p>
     * <p>This method is highly optimized and can achieve speeds exceeding 10 GB/s
     * on modern processors with SIMD instruction support. It is more efficient
     * than using the streaming API for single-block hashing.</p>
     *
     * <p><strong>Example Usage:</strong></p>
     * <pre>{@code
     * // Compute checksum for verification
     * byte[] fileData = Files.readAllBytes(path);
     * long checksum = XxHash3Checksum.hash64(fileData);
     *
     * // Store or compare checksum
     * if (checksum != expectedChecksum) {
     *     throw new IOException("Checksum mismatch");
     * }
     * }</pre>
     *
     * @param data the byte array containing the data to hash; must not be
     *             {@code null}; the entire array is hashed
     * @return the 64-bit XXH3 hash value
     *
     * @see #hash32(byte[])
     * @see #compute(byte[], int, int)
     */
    public static long hash64(final byte @NotNull [] data) {
        return XXH3.hashBytes(data);
    }

    /**
     * XXH3-64 streaming checksum implementation.
     * <p>
     * Note: The zero-allocation-hashing library doesn't provide a true streaming API,
     * so this implementation buffers all data and computes the hash on {@link #getValue()}.
     * For large data, prefer using {@link #compute(byte[], int, int)} directly.
     * </p>
     */
    private static final class XxHash3ChecksumImpl implements Checksum {

        /** Internal buffer for accumulating data before hash computation. */
        private byte[] buffer;

        /** Current write position within the buffer. */
        private int position;

        /** Cached hash value from the last computation. */
        private long cachedValue;

        /** Flag indicating whether the buffer has been modified since last hash computation. */
        private boolean dirty;

        /**
         * Constructs a new XXH3 checksum calculator with an initial buffer size of 1024 bytes.
         */
        XxHash3ChecksumImpl() {
            this.buffer = new byte[1024];
            this.position = 0;
            this.cachedValue = 0;
            this.dirty = false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void update(final int b) {
            ensureCapacity(1);
            this.buffer[this.position++] = (byte) b;
            this.dirty = true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void update(final byte @NotNull [] data, final int offset, final int length) {
            ensureCapacity(length);
            System.arraycopy(data, offset, this.buffer, this.position, length);
            this.position += length;
            this.dirty = true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getValue() {
            if (this.dirty) {
                this.cachedValue = XXH3.hashBytes(this.buffer, 0, this.position);
                this.dirty = false;
            }
            return this.cachedValue;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void reset() {
            this.position = 0;
            this.cachedValue = 0;
            this.dirty = false;
        }

        /**
         * Ensures the internal buffer has sufficient capacity for additional bytes.
         * <p>
         * If the buffer is too small, it is expanded to at least double its current size
         * or the required size, whichever is larger.
         * </p>
         *
         * @param additional the number of additional bytes that need to be stored
         */
        private void ensureCapacity(final int additional) {
            final int required = this.position + additional;
            if (required > this.buffer.length) {
                final int newSize = Math.max(this.buffer.length * 2, required);
                final byte[] newBuffer = new byte[newSize];
                System.arraycopy(this.buffer, 0, newBuffer, 0, this.position);
                this.buffer = newBuffer;
            }
        }

    }

}
