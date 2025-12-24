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

import java.util.Arrays;

/**
 * Reed-Solomon error correction codec using GF(2^8).
 *
 * <p>Reed-Solomon is a powerful error correction algorithm widely used in
 * storage systems, QR codes, CDs, DVDs, and satellite communications. This
 * implementation operates on bytes (symbols in GF(2^8)) and can correct
 * up to (parityBytes / 2) symbol errors.</p>
 *
 * <h2>Algorithm Overview</h2>
 * <p>Reed-Solomon treats data as coefficients of a polynomial and adds
 * redundant coefficients (parity) such that the resulting codeword is
 * divisible by a generator polynomial. Errors can be detected and
 * corrected by solving for the error locations and magnitudes.</p>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li><strong>Berlekamp-Massey:</strong> Finds error locator polynomial</li>
 *   <li><strong>Chien Search:</strong> Finds error positions</li>
 *   <li><strong>Forney Algorithm:</strong> Calculates error magnitudes</li>
 * </ul>
 *
 * <h2>Encoding</h2>
 * <p>Uses systematic encoding where the original data is preserved and
 * parity bytes are appended:</p>
 * <pre>
 * [Original Data (n bytes)] + [Parity (p bytes)]
 *          └──────────────────────┘
 *               Codeword (n + p bytes)
 * </pre>
 *
 * <h2>Limits</h2>
 * <ul>
 *   <li><strong>Max codeword:</strong> 255 bytes (GF(2^8) field size - 1)</li>
 *   <li><strong>Max data:</strong> 255 - parityBytes</li>
 *   <li><strong>Max correctable:</strong> parityBytes / 2 errors</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create codec (16 parity bytes = correct up to 8 errors)
 * ReedSolomonCodec codec = new ReedSolomonCodec(16);
 *
 * // Encode data
 * byte[] data = new byte[200];  // max 239 bytes with 16 parity
 * fillWithData(data);
 * byte[] encoded = codec.encode(data);  // 216 bytes
 *
 * // Simulate errors
 * encoded[10] ^= 0x55;
 * encoded[50] ^= 0xAA;
 * encoded[100] ^= 0xFF;
 *
 * // Decode (errors are corrected automatically)
 * try {
 *     byte[] recovered = codec.decode(encoded);
 *     assert Arrays.equals(data, recovered);
 * } catch (EccException e) {
 *     // More than 8 errors - unrecoverable
 * }
 *
 * // Verify integrity without full decode
 * if (!codec.verify(encoded)) {
 *     System.out.println("Data is corrupted");
 * }
 * }</pre>
 *
 * <h2>Performance</h2>
 * <p>Operations use precomputed GF(2^8) log/exp tables for speed:</p>
 * <ul>
 *   <li><strong>Encode:</strong> O(n × p) where n = data size, p = parity</li>
 *   <li><strong>Decode:</strong> O(n × p + p³) for error correction</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. The generator polynomial is computed once
 * at construction, and all operations are stateless.</p>
 *
 * @see ErrorCorrectionCodec
 * @see EccConfiguration
 * @see GaloisField
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ReedSolomonCodec implements ErrorCorrectionCodec {

    /**
     * Codec identifier string for Reed-Solomon error correction.
     * This value is used for configuration and logging purposes.
     */
    public static final String ID = "reed-solomon";

    /**
     * Maximum codeword length in bytes (255 for GF(2^8)).
     * The maximum data size is this value minus the parity bytes.
     */
    private static final int MAX_CODEWORD_LENGTH = 255;

    /** The number of parity bytes appended during encoding. */
    private final int parityBytes;

    /** Precomputed generator polynomial coefficients for encoding. */
    private final int[] generatorPoly;

    /**
     * Creates a Reed-Solomon codec with the specified number of parity bytes.
     *
     * <p>This constructor creates a codec with the given error correction capability.
     * The generator polynomial is computed once during construction, making
     * subsequent encoding and decoding operations efficient.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The codec can correct up to (parityBytes / 2) byte errors per block.
     * The maximum data size is limited to (255 - parityBytes) bytes due to
     * the properties of GF(2^8).</p>
     *
     * <p><strong>\:</strong></p>
     * <table>
     *   <caption>Parity bytes vs capability</caption>
     *   <tr><th>Parity</th><th>Max Errors</th><th>Max Data</th></tr>
     *   <tr><td>8</td><td>4</td><td>247 bytes</td></tr>
     *   <tr><td>16</td><td>8</td><td>239 bytes</td></tr>
     *   <tr><td>32</td><td>16</td><td>223 bytes</td></tr>
     * </table>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Create codec that can correct 8 errors
     * ReedSolomonCodec codec = new ReedSolomonCodec(16);
     *
     * byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
     * byte[] encoded = codec.encode(data);
     * }</pre>
     *
     * @param parityBytes the number of parity bytes to use; must be an even
     *                    number between 2 and 254 inclusive; determines both
     *                    correction capability and storage overhead
     * @throws IllegalArgumentException if parityBytes is not an even number
     *                                  or is outside the valid range (2-254)
     *
     * @see #ReedSolomonCodec()
     * @see #ReedSolomonCodec(EccConfiguration)
     */
    public ReedSolomonCodec(final int parityBytes) {
        if (parityBytes < 2 || parityBytes > 254 || parityBytes % 2 != 0) {
            throw new IllegalArgumentException(
                    "Parity bytes must be an even number between 2 and 254: " + parityBytes);
        }
        this.parityBytes = parityBytes;
        this.generatorPoly = buildGeneratorPolynomial(parityBytes);
    }

    /**
     * Creates a Reed-Solomon codec with default configuration.
     *
     * <p>This convenience constructor creates a codec with the default settings
     * from {@link EccConfiguration#DEFAULT}: 16 parity bytes, allowing correction
     * of up to 8 byte errors per block.</p>
     *
     * <p>The default configuration provides a good balance between error correction
     * capability and storage overhead for general-purpose use.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li><strong>Parity bytes:</strong> 16</li>
     *   <li><strong>Max correctable errors:</strong> 8</li>
     *   <li><strong>Max data size:</strong> 239 bytes</li>
     *   <li><strong>Storage overhead:</strong> ~6.7% for 239-byte blocks</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * ReedSolomonCodec codec = new ReedSolomonCodec();
     * byte[] encoded = codec.encode(data);
     * }</pre>
     *
     * @see EccConfiguration#DEFAULT
     * @see #ReedSolomonCodec(int)
     */
    public ReedSolomonCodec() {
        this(EccConfiguration.DEFAULT.parityBytes());
    }

    /**
     * Creates a Reed-Solomon codec from an ECC configuration.
     *
     * <p>This constructor allows creating a codec using the settings defined
     * in an {@link EccConfiguration} instance. This is useful when using
     * preset configurations or custom configurations built with the
     * configuration builder.</p>
     *
     * <p>Note that only the {@link EccConfiguration#parityBytes()} value is
     * used from the configuration. The interleave factor is handled at a
     * higher level in the processing pipeline.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Using a preset
     * ReedSolomonCodec highRedundancy = new ReedSolomonCodec(EccConfiguration.HIGH_REDUNDANCY);
     *
     * // Using a custom configuration
     * EccConfiguration custom = EccConfiguration.builder()
     *     .parityBytes(24)
     *     .build();
     * ReedSolomonCodec customCodec = new ReedSolomonCodec(custom);
     * }</pre>
     *
     * @param config the ECC configuration to use; must not be {@code null};
     *               the {@link EccConfiguration#parityBytes()} determines
     *               the codec's error correction capability
     *
     * @see EccConfiguration
     * @see EccConfiguration#DEFAULT
     * @see EccConfiguration#HIGH_REDUNDANCY
     * @see EccConfiguration#LOW_OVERHEAD
     */
    public ReedSolomonCodec(final @NotNull EccConfiguration config) {
        this(config.parityBytes());
    }

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
    public int getParitySize() {
        return this.parityBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxDataSize() {
        return MAX_CODEWORD_LENGTH - this.parityBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxCorrectableErrors() {
        return this.parityBytes / 2;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte @NotNull [] encode(final byte @NotNull [] data) {
        if (data.length > getMaxDataSize()) {
            throw new IllegalArgumentException(
                    "Data too large: " + data.length + " > " + getMaxDataSize());
        }

        // Create output array: data + parity
        final byte[] encoded = new byte[data.length + this.parityBytes];
        System.arraycopy(data, 0, encoded, 0, data.length);

        // Compute parity bytes using polynomial division
        final int[] remainder = new int[this.parityBytes];

        for (int i = 0; i < data.length; i++) {
            final int coef = GaloisField.add(data[i] & 0xFF, remainder[0]);

            // Shift remainder and update
            System.arraycopy(remainder, 1, remainder, 0, this.parityBytes - 1);
            remainder[this.parityBytes - 1] = 0;

            for (int j = 0; j < this.parityBytes; j++) {
                remainder[j] = GaloisField.add(remainder[j],
                        GaloisField.mul(this.generatorPoly[j], coef));
            }
        }

        // Append parity bytes
        for (int i = 0; i < this.parityBytes; i++) {
            encoded[data.length + i] = (byte) remainder[i];
        }

        return encoded;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte @NotNull [] decode(final byte @NotNull [] encodedData) throws EccException {
        if (encodedData.length < this.parityBytes) {
            throw new EccException("Encoded data too short");
        }
        if (encodedData.length > MAX_CODEWORD_LENGTH) {
            throw new EccException("Encoded data too long: " + encodedData.length);
        }

        // Convert to int array
        final int[] received = new int[encodedData.length];
        for (int i = 0; i < encodedData.length; i++) {
            received[i] = encodedData[i] & 0xFF;
        }

        // Calculate syndromes
        final int[] syndromes = calculateSyndromes(received);

        // Check if all syndromes are zero (no errors)
        boolean hasErrors = false;
        for (final int syndrome : syndromes) {
            if (syndrome != 0) {
                hasErrors = true;
                break;
            }
        }

        if (hasErrors) {
            // Find error locations and magnitudes using Berlekamp-Massey and Forney
            correctErrors(received, syndromes);
        }

        // Extract original data
        final int dataLength = encodedData.length - this.parityBytes;
        final byte[] data = new byte[dataLength];
        for (int i = 0; i < dataLength; i++) {
            data[i] = (byte) received[i];
        }

        return data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verify(final byte @NotNull [] encodedData) {
        if (encodedData.length < this.parityBytes || encodedData.length > MAX_CODEWORD_LENGTH) {
            return false;
        }

        final int[] received = new int[encodedData.length];
        for (int i = 0; i < encodedData.length; i++) {
            received[i] = encodedData[i] & 0xFF;
        }

        final int[] syndromes = calculateSyndromes(received);
        for (final int syndrome : syndromes) {
            if (syndrome != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds the generator polynomial for the given number of parity bytes.
     * <p>
     * The generator polynomial is computed as:
     * g(x) = (x - α^0)(x - α^1)...(x - α^(n-1))
     * where n is the number of parity bytes and α is the primitive element.
     * </p>
     *
     * @param parityBytes the number of parity bytes (number of roots)
     * @return the generator polynomial coefficients
     */
    private static int[] buildGeneratorPolynomial(final int parityBytes) {
        int[] poly = {1};

        for (int i = 0; i < parityBytes; i++) {
            final int[] factor = {GaloisField.exp(i), 1};
            poly = multiplyPolynomials(poly, factor);
        }

        return poly;
    }

    /**
     * Multiplies two polynomials in GF(2^8).
     * <p>
     * Performs standard polynomial multiplication where coefficients
     * are multiplied using Galois field multiplication and added using
     * Galois field addition (XOR).
     * </p>
     *
     * @param a first polynomial (coefficients in ascending degree order)
     * @param b second polynomial (coefficients in ascending degree order)
     * @return product polynomial a(x) × b(x)
     */
    private static int[] multiplyPolynomials(final int[] a, final int[] b) {
        final int[] result = new int[a.length + b.length - 1];

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < b.length; j++) {
                result[i + j] = GaloisField.add(result[i + j],
                        GaloisField.mul(a[i], b[j]));
            }
        }

        return result;
    }

    /**
     * Calculates the syndromes for the received codeword.
     * <p>
     * Syndromes are computed by evaluating the received polynomial at
     * the roots of the generator polynomial (α^0, α^1, ..., α^(n-1)).
     * If all syndromes are zero, the codeword is valid.
     * </p>
     *
     * @param received the received codeword as integer array
     * @return array of syndrome values
     */
    private int[] calculateSyndromes(final int[] received) {
        final int[] syndromes = new int[this.parityBytes];

        for (int i = 0; i < this.parityBytes; i++) {
            syndromes[i] = GaloisField.polyEval(received, received.length, GaloisField.exp(i));
        }

        return syndromes;
    }

    /**
     * Corrects errors in the received codeword using Berlekamp-Massey algorithm.
     * <p>
     * This method implements the complete error correction process:
     * </p>
     * <ol>
     *   <li>Find error locator polynomial using Berlekamp-Massey</li>
     *   <li>Find error positions using Chien search</li>
     *   <li>Calculate error magnitudes using Forney algorithm</li>
     *   <li>Apply corrections to the received codeword</li>
     * </ol>
     *
     * @param received the received codeword (modified in place)
     * @param syndromes the computed syndrome values
     * @throws EccException if there are too many errors to correct
     */
    private void correctErrors(final int[] received, final int[] syndromes) throws EccException {
        // Berlekamp-Massey algorithm to find error locator polynomial
        final int[] errorLocator = berlekampMassey(syndromes);

        // Find error positions using Chien search
        final int[] errorPositions = chienSearch(errorLocator, received.length);

        if (errorPositions.length == 0 && !allZero(syndromes)) {
            throw new EccException("Too many errors to correct");
        }

        // Calculate error magnitudes using Forney algorithm
        final int[] errorMagnitudes = forneyAlgorithm(syndromes, errorLocator, errorPositions);

        // Correct the errors
        for (int i = 0; i < errorPositions.length; i++) {
            final int pos = received.length - 1 - errorPositions[i];
            if (pos >= 0 && pos < received.length) {
                received[pos] = GaloisField.add(received[pos], errorMagnitudes[i]);
            }
        }
    }

    /**
     * Berlekamp-Massey algorithm to find error locator polynomial.
     * <p>
     * This iterative algorithm finds the shortest linear feedback shift register
     * that generates the syndrome sequence. The resulting polynomial's roots
     * correspond to the error locations.
     * </p>
     *
     * @param syndromes the syndrome values computed from the received codeword
     * @return the error locator polynomial Λ(x)
     */
    private int[] berlekampMassey(final int[] syndromes) {
        final int n = syndromes.length;
        int[] errorLocator = {1};
        int[] prevLocator = {1};
        int prevDiscrepancy = 1;
        int prevDegree = 0;

        for (int i = 0; i < n; i++) {
            // Calculate discrepancy
            int discrepancy = syndromes[i];
            for (int j = 1; j < errorLocator.length; j++) {
                if (i - j >= 0) {
                    discrepancy = GaloisField.add(discrepancy,
                            GaloisField.mul(errorLocator[j], syndromes[i - j]));
                }
            }

            if (discrepancy != 0) {
                final int[] temp = errorLocator.clone();

                // Update error locator polynomial
                final int scale = GaloisField.div(discrepancy, prevDiscrepancy);
                final int shift = i - prevDegree;

                if (shift >= 0) {
                    final int[] adjusted = new int[prevLocator.length + shift];
                    System.arraycopy(prevLocator, 0, adjusted, shift, prevLocator.length);
                    for (int j = 0; j < adjusted.length; j++) {
                        adjusted[j] = GaloisField.mul(adjusted[j], scale);
                    }

                    // Ensure errorLocator is large enough
                    if (adjusted.length > errorLocator.length) {
                        errorLocator = Arrays.copyOf(errorLocator, adjusted.length);
                    }
                    for (int j = 0; j < adjusted.length; j++) {
                        errorLocator[j] = GaloisField.add(errorLocator[j], adjusted[j]);
                    }
                }

                if (2 * (errorLocator.length - 1) <= i) {
                    prevLocator = temp;
                    prevDiscrepancy = discrepancy;
                    prevDegree = i;
                }
            }
        }

        return errorLocator;
    }

    /**
     * Chien search to find error positions.
     * <p>
     * Evaluates the error locator polynomial at all possible field elements
     * (α^0, α^1, ..., α^(n-1)) to find the roots, which correspond to error positions.
     * This is more efficient than general root-finding algorithms due to the
     * structure of the finite field.
     * </p>
     *
     * @param errorLocator the error locator polynomial Λ(x)
     * @param codewordLength the length of the received codeword
     * @return array of error positions (indices in the codeword)
     */
    private int[] chienSearch(final int[] errorLocator, final int codewordLength) {
        final int numErrors = errorLocator.length - 1;
        final int[] positions = new int[numErrors];
        int found = 0;

        for (int i = 0; i < codewordLength && found < numErrors; i++) {
            final int eval = GaloisField.polyEval(errorLocator, errorLocator.length,
                    GaloisField.exp(i));
            if (eval == 0) {
                positions[found++] = i;
            }
        }

        return Arrays.copyOf(positions, found);
    }

    /**
     * Forney algorithm to calculate error magnitudes.
     * <p>
     * Once error positions are known from Chien search, this algorithm
     * computes the actual error values by evaluating:
     * </p>
     * <pre>
     * e_i = -X_i × Ω(X_i^(-1)) / Λ'(X_i^(-1))
     * </pre>
     * <p>
     * where Ω(x) is the error evaluator polynomial and Λ'(x) is the
     * formal derivative of the error locator polynomial.
     * </p>
     *
     * @param syndromes the syndrome values
     * @param errorLocator the error locator polynomial Λ(x)
     * @param errorPositions the positions where errors occurred
     * @return array of error magnitudes corresponding to each position
     */
    private int[] forneyAlgorithm(final int[] syndromes, final int[] errorLocator,
                                   final int[] errorPositions) {
        final int[] magnitudes = new int[errorPositions.length];

        // Calculate error evaluator polynomial
        final int[] errorEvaluator = new int[syndromes.length];
        for (int i = 0; i < syndromes.length; i++) {
            errorEvaluator[i] = syndromes[i];
            for (int j = 1; j <= i && j < errorLocator.length; j++) {
                errorEvaluator[i] = GaloisField.add(errorEvaluator[i],
                        GaloisField.mul(errorLocator[j], syndromes[i - j]));
            }
        }

        // Calculate formal derivative of error locator
        final int[] derivative = new int[errorLocator.length - 1];
        for (int i = 1; i < errorLocator.length; i += 2) {
            if (i < errorLocator.length) {
                derivative[i - 1] = errorLocator[i];
            }
        }

        // Calculate magnitudes
        for (int i = 0; i < errorPositions.length; i++) {
            final int xi = GaloisField.exp(errorPositions[i]);
            final int xiInv = GaloisField.inverse(xi);

            final int numerator = GaloisField.polyEval(errorEvaluator,
                    Math.min(errorEvaluator.length, syndromes.length), xiInv);
            int denominator = GaloisField.polyEval(derivative,
                    derivative.length, xiInv);

            if (denominator == 0) {
                denominator = 1; // Fallback to avoid division by zero
            }

            magnitudes[i] = GaloisField.mul(xi, GaloisField.div(numerator, denominator));
        }

        return magnitudes;
    }

    /**
     * Checks if all elements in the array are zero.
     *
     * @param array the array to check
     * @return {@code true} if all elements are zero, {@code false} otherwise
     */
    private static boolean allZero(final int[] array) {
        for (final int value : array) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

}
