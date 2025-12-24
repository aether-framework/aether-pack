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

package de.splatgames.aether.pack.core.ecc;

import org.jetbrains.annotations.NotNull;

/**
 * Configuration for error correction processing.
 *
 * <p>This record holds the settings for error correction, including the
 * amount of redundancy and interleaving for burst error protection.</p>
 *
 * <h2>Parity Bytes</h2>
 * <p>The number of parity bytes determines the correction capability:</p>
 * <ul>
 *   <li><strong>Max correctable errors</strong> = parityBytes / 2</li>
 *   <li><strong>Space overhead</strong> = parityBytes / dataSize</li>
 * </ul>
 *
 * <h2>Interleaving</h2>
 * <p>Interleaving spreads data across multiple ECC blocks, providing
 * protection against burst errors (contiguous corrupted regions):</p>
 * <ul>
 *   <li><strong>Factor 1</strong> - No interleaving, protects against random errors</li>
 *   <li><strong>Factor 4</strong> - Can handle burst errors up to 4x single block capacity</li>
 *   <li><strong>Factor 8+</strong> - For severely degraded storage media</li>
 * </ul>
 *
 * <h2>Preset Configurations</h2>
 * <table>
 *   <caption>Preset ECC Configurations</caption>
 *   <tr><th>Preset</th><th>Parity</th><th>Interleave</th><th>Corrects</th><th>Use Case</th></tr>
 *   <tr><td>{@link #LOW_OVERHEAD}</td><td>8</td><td>1</td><td>4 errors</td><td>Minor corruption</td></tr>
 *   <tr><td>{@link #DEFAULT}</td><td>16</td><td>1</td><td>8 errors</td><td>General use</td></tr>
 *   <tr><td>{@link #HIGH_REDUNDANCY}</td><td>32</td><td>4</td><td>16 errors</td><td>Archival, unreliable media</td></tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Use a preset
 * EccConfiguration config = EccConfiguration.DEFAULT;
 *
 * // Custom configuration
 * EccConfiguration custom = EccConfiguration.builder()
 *     .parityBytes(24)      // Can correct 12 errors
 *     .interleaveFactor(2)  // Protect against small burst errors
 *     .build();
 *
 * // Use with codec
 * ReedSolomonCodec codec = new ReedSolomonCodec(config);
 * }</pre>
 *
 * <h2>Validation</h2>
 * <p>The compact constructor enforces:</p>
 * <ul>
 *   <li>Parity bytes must be between 2 and 255</li>
 *   <li>Parity bytes must be even (required by Reed-Solomon)</li>
 *   <li>Interleave factor must be between 1 and 16</li>
 * </ul>
 *
 * @param parityBytes     number of parity bytes per block (determines correction capability)
 * @param interleaveFactor interleave factor for burst error protection (1 = no interleaving)
 *
 * @see ReedSolomonCodec
 * @see ErrorCorrectionCodec
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record EccConfiguration(
        int parityBytes,
        int interleaveFactor
) {

    /**
     * Default configuration: 16 parity bytes, no interleaving.
     * <p>
     * Can correct up to 8 byte errors per block.
     * </p>
     */
    public static final EccConfiguration DEFAULT = new EccConfiguration(16, 1);

    /**
     * High redundancy configuration: 32 parity bytes, interleave factor 4.
     * <p>
     * Can correct up to 16 byte errors per block with burst error protection.
     * </p>
     */
    public static final EccConfiguration HIGH_REDUNDANCY = new EccConfiguration(32, 4);

    /**
     * Low overhead configuration: 8 parity bytes, no interleaving.
     * <p>
     * Can correct up to 4 byte errors per block.
     * </p>
     */
    public static final EccConfiguration LOW_OVERHEAD = new EccConfiguration(8, 1);

    /**
     * Validates the configuration.
     */
    public EccConfiguration {
        if (parityBytes < 2 || parityBytes > 255) {
            throw new IllegalArgumentException("Parity bytes must be between 2 and 255");
        }
        if (parityBytes % 2 != 0) {
            throw new IllegalArgumentException("Parity bytes must be even");
        }
        if (interleaveFactor < 1 || interleaveFactor > 16) {
            throw new IllegalArgumentException("Interleave factor must be between 1 and 16");
        }
    }

    /**
     * Calculates and returns the maximum number of correctable errors per block.
     *
     * <p>This is a convenience method that computes the error correction capability
     * based on the configured parity bytes. For Reed-Solomon coding, the correction
     * capability is exactly half the number of parity bytes.</p>
     *
     * <p>The formula is: {@code maxCorrectableErrors = parityBytes / 2}</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * EccConfiguration config = EccConfiguration.builder()
     *     .parityBytes(32)
     *     .build();
     *
     * int maxErrors = config.maxCorrectableErrors();  // Returns 16
     * System.out.printf("Can correct up to %d byte errors%n", maxErrors);
     * }</pre>
     *
     * @return the maximum number of byte errors that can be corrected per block;
     *         this is always a positive integer equal to half the parity bytes
     *
     * @see #parityBytes()
     * @see ReedSolomonCodec#getMaxCorrectableErrors()
     */
    public int maxCorrectableErrors() {
        return this.parityBytes / 2;
    }

    /**
     * Creates a new builder for constructing {@link EccConfiguration} instances.
     *
     * <p>The builder is initialized with default values matching {@link #DEFAULT}:
     * 16 parity bytes and interleave factor 1 (no interleaving). Use the builder
     * methods to customize these values before calling {@link Builder#build()}.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * EccConfiguration custom = EccConfiguration.builder()
     *     .parityBytes(24)      // Can correct 12 errors
     *     .interleaveFactor(4)  // Burst error protection
     *     .build();
     * }</pre>
     *
     * @return a new builder instance with default values; never {@code null}
     *
     * @see Builder
     * @see #DEFAULT
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * A fluent builder for creating {@link EccConfiguration} instances.
     *
     * <p>The builder is initialized with the same values as {@link #DEFAULT}:
     * 16 parity bytes and no interleaving.</p>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public static final class Builder {

        /** Number of parity bytes per encoded block (default: 16). */
        private int parityBytes = 16;

        /** Interleave factor for burst error protection (default: 1 = no interleaving). */
        private int interleaveFactor = 1;

        /**
         * Constructs a new builder with default values.
         * <p>
         * Defaults: 16 parity bytes, no interleaving (factor 1).
         * </p>
         */
        private Builder() {
        }

        /**
         * Sets the number of parity bytes per encoded block.
         *
         * <p>The parity bytes determine the error correction capability and storage
         * overhead of the ECC encoding. The relationship is:</p>
         * <ul>
         *   <li><strong>Correction capability:</strong> parityBytes / 2 errors</li>
         *   <li><strong>Storage overhead:</strong> parityBytes / dataSize</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <table>
         *   <caption>Parity bytes vs capability</caption>
         *   <tr><th>Parity</th><th>Corrects</th><th>Use Case</th></tr>
         *   <tr><td>8</td><td>4 errors</td><td>Minor corruption, low overhead</td></tr>
         *   <tr><td>16</td><td>8 errors</td><td>General use (default)</td></tr>
         *   <tr><td>32</td><td>16 errors</td><td>Archival, unreliable media</td></tr>
         * </table>
         *
         * <p><strong>\:</strong></p>
         * <ul>
         *   <li>Must be between 2 and 255 inclusive</li>
         *   <li>Must be an even number (Reed-Solomon requirement)</li>
         * </ul>
         *
         * @param parityBytes the number of parity bytes per block; must be an
         *                    even number between 2 and 255 inclusive
         * @return this builder instance to allow fluent method chaining
         * @throws IllegalArgumentException if validation fails during {@link #build()}
         *
         * @see EccConfiguration#maxCorrectableErrors()
         */
        public @NotNull Builder parityBytes(final int parityBytes) {
            this.parityBytes = parityBytes;
            return this;
        }

        /**
         * Sets the interleave factor for burst error protection.
         *
         * <p>Interleaving provides protection against burst errors (contiguous
         * sequences of corrupted bytes) by spreading data across multiple ECC
         * blocks. Without interleaving, a burst error may exceed a single block's
         * correction capability; with interleaving, the burst is distributed
         * across multiple blocks, each handling a smaller portion.</p>
         *
         * <p><strong>\:</strong></p>
         * <p>Data bytes are distributed across N blocks in a round-robin fashion:</p>
         * <pre>
         * Original: [1,2,3,4,5,6,7,8,9,10,11,12] (factor=4)
         * Block 1:  [1,5,9]
         * Block 2:  [2,6,10]
         * Block 3:  [3,7,11]
         * Block 4:  [4,8,12]
         * </pre>
         *
         * <p><strong>\:</strong></p>
         * <table>
         *   <caption>Interleave factor impact</caption>
         *   <tr><th>Factor</th><th>Burst Tolerance</th><th>Use Case</th></tr>
         *   <tr><td>1</td><td>parityBytes/2</td><td>Random errors only</td></tr>
         *   <tr><td>4</td><td>4 × parityBytes/2</td><td>Small burst errors</td></tr>
         *   <tr><td>8</td><td>8 × parityBytes/2</td><td>Large burst errors</td></tr>
         * </table>
         *
         * @param interleaveFactor the interleave factor; must be between 1 and 16
         *                         inclusive; 1 means no interleaving; higher values
         *                         provide better burst error protection at the cost
         *                         of increased memory usage
         * @return this builder instance to allow fluent method chaining
         * @throws IllegalArgumentException if validation fails during {@link #build()}
         *
         * @see EccConfiguration#interleaveFactor()
         */
        public @NotNull Builder interleaveFactor(final int interleaveFactor) {
            this.interleaveFactor = interleaveFactor;
            return this;
        }

        /**
         * Builds and returns a new immutable {@link EccConfiguration} instance.
         *
         * <p>This method validates all configured values and constructs a new
         * configuration object. The resulting configuration is immutable and
         * thread-safe.</p>
         *
         * <p><strong>\:</strong></p>
         * <ul>
         *   <li>Parity bytes must be between 2 and 255 inclusive</li>
         *   <li>Parity bytes must be an even number</li>
         *   <li>Interleave factor must be between 1 and 16 inclusive</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * EccConfiguration config = EccConfiguration.builder()
         *     .parityBytes(24)
         *     .interleaveFactor(2)
         *     .build();  // Validates and creates configuration
         *
         * ReedSolomonCodec codec = new ReedSolomonCodec(config);
         * }</pre>
         *
         * @return a new immutable {@link EccConfiguration} instance with the
         *         configured parity bytes and interleave factor; never {@code null}
         * @throws IllegalArgumentException if any configured value is outside
         *                                  its valid range
         *
         * @see EccConfiguration
         */
        public @NotNull EccConfiguration build() {
            return new EccConfiguration(this.parityBytes, this.interleaveFactor);
        }

    }

}
