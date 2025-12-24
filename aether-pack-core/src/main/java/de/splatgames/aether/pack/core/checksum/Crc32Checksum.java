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
import org.jetbrains.annotations.NotNull;

import java.util.zip.CRC32;

/**
 * CRC-32 checksum provider using Java's built-in implementation.
 *
 * <p>This provider wraps {@link java.util.zip.CRC32} to implement the
 * {@link ChecksumProvider} interface. CRC-32 is a widely-used error-detecting
 * code commonly used in network communications and storage systems.</p>
 *
 * <h2>Properties</h2>
 * <ul>
 *   <li><strong>Algorithm ID:</strong> "crc32"</li>
 *   <li><strong>Numeric ID:</strong> {@link FormatConstants#CHECKSUM_CRC32} (0)</li>
 *   <li><strong>Output Size:</strong> 32 bits (4 bytes)</li>
 *   <li><strong>Polynomial:</strong> 0xEDB88320 (IEEE 802.3)</li>
 * </ul>
 *
 * <h2>Performance</h2>
 * <p>CRC-32 is hardware-accelerated on modern x86 processors with SSE 4.2
 * support, providing excellent performance for data integrity verification.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ChecksumProvider crc = ChecksumRegistry.requireByName("crc32");
 * long checksum = crc.compute(data);
 *
 * // Or use directly
 * Crc32Checksum provider = new Crc32Checksum();
 * ChecksumProvider.Checksum calc = provider.createChecksum();
 * calc.update(data);
 * int crc32 = calc.getValueAsInt();
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. Each call to {@link #createChecksum()}
 * returns a new independent calculator instance.</p>
 *
 * @see XxHash3Checksum
 * @see ChecksumRegistry
 * @see FormatConstants#CHECKSUM_CRC32
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class Crc32Checksum implements ChecksumProvider {

    /**
     * Creates a new CRC-32 checksum provider instance.
     */
    public Crc32Checksum() {
        // Stateless provider
    }

    /**
     * Algorithm identifier string for CRC-32 checksum.
     * This value is used for registry lookup and configuration purposes.
     */
    public static final String ID = "crc32";

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
        return FormatConstants.CHECKSUM_CRC32;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getChecksumSize() {
        return 4;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Checksum createChecksum() {
        return new Crc32ChecksumImpl();
    }

    /**
     * CRC32 checksum implementation wrapping Java's {@link CRC32}.
     */
    private static final class Crc32ChecksumImpl implements Checksum {

        /** The underlying Java CRC32 instance used for checksum computation. */
        private final @NotNull CRC32 crc32;

        /**
         * Constructs a new CRC32 checksum calculator.
         */
        Crc32ChecksumImpl() {
            this.crc32 = new CRC32();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void update(final int b) {
            this.crc32.update(b);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void update(final byte @NotNull [] data, final int offset, final int length) {
            this.crc32.update(data, offset, length);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getValue() {
            return this.crc32.getValue();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void reset() {
            this.crc32.reset();
        }

    }

}
