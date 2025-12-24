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
 * Interface for error correction codecs.
 *
 * <p>Error correction codes (ECC) provide the ability to detect and correct
 * errors in data, offering resilience against data corruption from storage
 * media degradation, transmission errors, or bit flips.</p>
 *
 * <h2>How Error Correction Works</h2>
 * <p>ECC codecs add redundant data (parity bytes) that allow:</p>
 * <ul>
 *   <li><strong>Detection</strong> - Identify that errors have occurred</li>
 *   <li><strong>Correction</strong> - Recover the original data without retransmission</li>
 * </ul>
 *
 * <h2>Built-in Implementation</h2>
 * <p>The primary implementation is {@link ReedSolomonCodec}, which:</p>
 * <ul>
 *   <li>Uses GF(2^8) arithmetic for byte-level operations</li>
 *   <li>Can correct up to (parityBytes / 2) symbol errors</li>
 *   <li>Uses systematic encoding (original data preserved, parity appended)</li>
 * </ul>
 *
 * <h2>Correction Capability</h2>
 * <table>
 *   <caption>Parity Bytes vs Correction Capability</caption>
 *   <tr><th>Parity Bytes</th><th>Max Errors</th><th>Overhead</th></tr>
 *   <tr><td>8</td><td>4</td><td>~3.2%</td></tr>
 *   <tr><td>16</td><td>8</td><td>~6.3%</td></tr>
 *   <tr><td>32</td><td>16</td><td>~12.5%</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create codec with 16 parity bytes (can correct 8 errors)
 * ErrorCorrectionCodec codec = new ReedSolomonCodec(16);
 *
 * // Encode data
 * byte[] original = "Hello, World!".getBytes(StandardCharsets.UTF_8);
 * byte[] encoded = codec.encode(original);
 *
 * // Simulate corruption (flip some bytes)
 * encoded[5] ^= 0xFF;
 * encoded[10] ^= 0xFF;
 *
 * // Decode and recover original data
 * try {
 *     byte[] recovered = codec.decode(encoded);
 *     assert Arrays.equals(original, recovered);
 * } catch (EccException e) {
 *     System.err.println("Too many errors to correct");
 * }
 *
 * // Verify without decoding
 * boolean isValid = codec.verify(encoded);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations should be thread-safe and stateless. The same codec
 * instance can be used concurrently from multiple threads.</p>
 *
 * @see ReedSolomonCodec
 * @see EccConfiguration
 * @see GaloisField
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface ErrorCorrectionCodec {

    /**
     * Returns the unique identifier for this error correction codec.
     *
     * <p>The identifier is a human-readable string that uniquely identifies the
     * ECC algorithm implementation. This identifier can be used for configuration,
     * logging, and to store the algorithm type in archive headers.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>{@code "reed-solomon"} - Reed-Solomon codec ({@link ReedSolomonCodec})</li>
     * </ul>
     *
     * @return the unique algorithm identifier string; never {@code null};
     *         typically lowercase with hyphens (e.g., "reed-solomon")
     *
     * @see ReedSolomonCodec#ID
     */
    @NotNull String getId();

    /**
     * Returns the number of parity bytes added per encoded block.
     *
     * <p>Parity bytes are redundant data appended to the original data during
     * encoding. These bytes enable error detection and correction during
     * decoding. More parity bytes provide better correction capability at the
     * cost of increased storage overhead.</p>
     *
     * <p>For Reed-Solomon, the relationship between parity bytes and correction
     * capability is: maximum correctable errors = parityBytes / 2.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The overhead percentage can be calculated as:</p>
     * <pre>{@code
     * double overhead = (double) codec.getParitySize() / dataSize * 100;
     * }</pre>
     *
     * @return the number of parity bytes appended to each encoded block;
     *         always a positive integer; for Reed-Solomon this is always even
     *
     * @see #getMaxCorrectableErrors()
     * @see #encode(byte[])
     */
    int getParitySize();

    /**
     * Returns the maximum data block size this codec can handle.
     *
     * <p>Due to the mathematical properties of Galois Field arithmetic, there
     * is a maximum size limit for data blocks. For GF(2^8) based codecs like
     * Reed-Solomon, this is 255 - parityBytes bytes.</p>
     *
     * <p>Data larger than this limit must be split into smaller blocks,
     * each encoded separately. The {@link EccConfiguration#interleaveFactor()}
     * can help with this by spreading data across multiple blocks.</p>
     *
     * @return the maximum number of data bytes that can be encoded in a single
     *         block; attempting to encode more bytes will result in an exception
     *
     * @see #encode(byte[])
     * @see EccConfiguration#interleaveFactor()
     */
    int getMaxDataSize();

    /**
     * Returns the maximum number of byte errors that can be corrected.
     *
     * <p>This is the error correction capability of the codec - the maximum
     * number of corrupted bytes that can be detected and corrected to recover
     * the original data.</p>
     *
     * <p>For Reed-Solomon, this is exactly half the parity bytes:
     * {@code parityBytes / 2}. More parity bytes provide stronger protection
     * but increase storage overhead.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li><strong>Symbol error:</strong> One byte corrupted (any bit pattern change)</li>
     *   <li><strong>Random errors:</strong> Errors at unrelated positions</li>
     *   <li><strong>Burst errors:</strong> Contiguous sequence of errors (use interleaving)</li>
     * </ul>
     *
     * @return the maximum number of corrupted bytes that can be corrected;
     *         if more errors occur, correction will fail
     *
     * @see #getParitySize()
     * @see #decode(byte[])
     */
    int getMaxCorrectableErrors();

    /**
     * Encodes the given data by computing and appending parity bytes.
     *
     * <p>This method uses systematic encoding, which preserves the original data
     * and appends the computed parity bytes at the end. The output format is:</p>
     * <pre>
     * [Original Data (n bytes)] + [Parity (p bytes)] = [Encoded (n+p bytes)]
     * </pre>
     *
     * <p>The returned array contains the original data unchanged, followed by
     * the computed parity bytes. This allows reading the original data without
     * decoding if error correction is not needed.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The input data must not exceed {@link #getMaxDataSize()} bytes. For
     * larger data, split it into smaller blocks and encode each separately.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
     * byte[] encoded = codec.encode(data);
     * // encoded.length == data.length + codec.getParitySize()
     * }</pre>
     *
     * @param data the original data to encode; must not be {@code null};
     *             length must not exceed {@link #getMaxDataSize()}
     * @return a new byte array containing the original data followed by the
     *         computed parity bytes; never {@code null}; length equals
     *         {@code data.length + getParitySize()}
     * @throws IllegalArgumentException if the data exceeds the maximum size
     *
     * @see #decode(byte[])
     * @see #getMaxDataSize()
     * @see #getParitySize()
     */
    byte @NotNull [] encode(byte @NotNull [] data);

    /**
     * Decodes encoded data, automatically detecting and correcting errors.
     *
     * <p>This method attempts to recover the original data from the encoded
     * codeword. If errors are detected (via syndrome calculation), the codec
     * will attempt to locate and correct them. If successful, the original
     * data is returned; otherwise, an {@link EccException} is thrown.</p>
     *
     * <p><strong>\:</strong></p>
     * <ol>
     *   <li>Calculate syndromes to detect errors</li>
     *   <li>If syndromes are all zero, data is valid - return original</li>
     *   <li>Find error locations using Berlekamp-Massey algorithm</li>
     *   <li>Calculate error magnitudes using Forney algorithm</li>
     *   <li>Apply corrections and return recovered data</li>
     * </ol>
     *
     * <p><strong>\:</strong></p>
     * <p>The codec can correct up to {@link #getMaxCorrectableErrors()} byte
     * errors. If more errors occur, correction will fail and an exception
     * is thrown.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * try {
     *     byte[] recovered = codec.decode(encodedData);
     *     // Data successfully recovered
     * } catch (EccException e) {
     *     // Too many errors - data unrecoverable
     *     System.err.println("Unrecoverable: " + e.getMessage());
     * }
     * }</pre>
     *
     * @param encodedData the encoded data containing original data and parity
     *                    bytes; must not be {@code null}; length must be at
     *                    least {@link #getParitySize()} bytes
     * @return the recovered original data without parity bytes; never {@code null};
     *         length equals {@code encodedData.length - getParitySize()}
     * @throws EccException if the data contains too many errors to correct,
     *                      or if the encoded data format is invalid
     *
     * @see #encode(byte[])
     * @see #verify(byte[])
     * @see #getMaxCorrectableErrors()
     */
    byte @NotNull [] decode(byte @NotNull [] encodedData) throws EccException;

    /**
     * Verifies the integrity of encoded data without performing full decoding.
     *
     * <p>This method is a fast check to determine if the encoded data is valid
     * (contains no errors) without actually correcting errors or extracting
     * the original data. It calculates the syndromes and checks if they are
     * all zero.</p>
     *
     * <p>This is useful for:</p>
     * <ul>
     *   <li>Quick integrity checks during file scanning</li>
     *   <li>Validating data before expensive decode operations</li>
     *   <li>Detecting corruption without attempting recovery</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <p>Verification is faster than full decoding because it only needs to
     * compute syndromes, skipping the error location and correction steps.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>{@code true} - Data has no detectable errors</li>
     *   <li>{@code false} - Data is corrupted (but might still be correctable)</li>
     * </ul>
     *
     * @param encodedData the encoded data to verify; must not be {@code null}
     * @return {@code true} if the data passes integrity verification with no
     *         detectable errors; {@code false} if corruption is detected or
     *         the data format is invalid
     *
     * @see #decode(byte[])
     */
    boolean verify(byte @NotNull [] encodedData);

    /**
     * Exception thrown when error correction fails.
     */
    class EccException extends Exception {

        /**
         * Constructs a new ECC exception with the specified message.
         *
         * @param message the detail message explaining the error
         */
        public EccException(final @NotNull String message) {
            super(message);
        }

        /**
         * Constructs a new ECC exception with the specified message and cause.
         *
         * @param message the detail message explaining the error
         * @param cause the underlying cause of this exception
         */
        public EccException(final @NotNull String message, final @NotNull Throwable cause) {
            super(message, cause);
        }

    }

}
