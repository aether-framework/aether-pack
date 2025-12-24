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

package de.splatgames.aether.pack.core.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Exception thrown when a checksum validation fails.
 *
 * <p>This exception indicates that data integrity verification failed.
 * The stored checksum does not match the computed checksum of the data,
 * suggesting that the data has been corrupted or tampered with.</p>
 *
 * <h2>Checksum Locations</h2>
 * <p>APACK archives use checksums in multiple locations:</p>
 * <ul>
 *   <li><strong>Chunk checksums</strong> - Each data chunk has a checksum in its header</li>
 *   <li><strong>Header checksums</strong> - File header includes a header checksum</li>
 *   <li><strong>TOC checksums</strong> - Table of Contents is checksummed in the trailer</li>
 * </ul>
 *
 * <h2>Supported Algorithms</h2>
 * <ul>
 *   <li><strong>CRC-32</strong> - 32-bit checksum, legacy compatibility</li>
 *   <li><strong>XXH3-64</strong> - 64-bit hash, recommended for performance</li>
 * </ul>
 *
 * <h2>Diagnostic Information</h2>
 * <p>This exception provides both expected and actual checksum values,
 * which can be useful for debugging or forensic analysis:</p>
 * <pre>{@code
 * try {
 *     reader.readChunk(chunkIndex);
 * } catch (ChecksumException e) {
 *     System.err.printf("Checksum mismatch in chunk %d%n", chunkIndex);
 *     System.err.printf("  Expected: 0x%016X%n", e.getExpected());
 *     System.err.printf("  Actual:   0x%016X%n", e.getActual());
 *
 *     // Attempt recovery with ECC if available
 *     if (hasEcc) {
 *         attemptRecovery(chunkIndex);
 *     }
 * }
 * }</pre>
 *
 * <h2>Recovery</h2>
 * <p>If the archive was created with error correction (ECC), the reader
 * may be able to recover corrupted data. Without ECC, checksum failures
 * indicate unrecoverable data corruption.</p>
 *
 * @see ApackException
 * @see de.splatgames.aether.pack.core.checksum.ChecksumRegistry
 * @see de.splatgames.aether.pack.core.spi.ChecksumProvider
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class ChecksumException extends ApackException {

    /** The expected checksum value that was stored in the archive. */
    private final long expected;

    /** The actual checksum value computed from the data. */
    private final long actual;

    /**
     * Creates a new checksum exception with the specified message and checksum values.
     *
     * <p>This constructor creates a checksum exception that captures both the
     * expected and actual checksum values for diagnostic purposes. The message
     * is automatically enhanced with formatted checksum values in hexadecimal
     * format, providing a complete picture of the verification failure.</p>
     *
     * <p>The final exception message follows the format:</p>
     * <pre>
     * "{message} (expected: 0x{expected}, actual: 0x{actual})"
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * long storedChecksum = readStoredChecksum();
     * long computedChecksum = computeChecksum(data);
     * if (storedChecksum != computedChecksum) {
     *     throw new ChecksumException(
     *         "Chunk data corrupted at offset " + offset,
     *         storedChecksum,
     *         computedChecksum
     *     );
     * }
     * }</pre>
     *
     * @param message  the detail message describing the context of the checksum
     *                 failure; must not be {@code null}; should include
     *                 information about which data was being verified, such as
     *                 the chunk index, entry name, or file offset; the checksum
     *                 values will be automatically appended to this message in
     *                 hexadecimal format
     * @param expected the expected checksum value that was stored in the archive;
     *                 this is the checksum that was computed when the data was
     *                 originally written; the value is stored as a 64-bit long
     *                 to accommodate both 32-bit (CRC-32) and 64-bit (XXH3-64)
     *                 checksum algorithms; for 32-bit checksums, only the lower
     *                 32 bits are significant
     * @param actual   the actual checksum value that was computed from the current
     *                 data; this is the checksum calculated during verification;
     *                 if this differs from the expected value, the data has been
     *                 corrupted or modified since the archive was created; the
     *                 value format matches the expected value (32-bit or 64-bit)
     *
     * @see #getExpected()
     * @see #getActual()
     * @see de.splatgames.aether.pack.core.spi.ChecksumProvider
     */
    public ChecksumException(final @NotNull String message, final long expected, final long actual) {
        super(String.format("%s (expected: 0x%08X, actual: 0x%08X)", message, expected, actual));
        this.expected = expected;
        this.actual = actual;
    }

    /**
     * Returns the expected checksum value that was stored in the archive.
     *
     * <p>This method returns the checksum value that was originally computed
     * and stored when the data was written to the archive. This is the
     * "correct" or "known good" checksum that the actual computed checksum
     * should have matched.</p>
     *
     * <p>The checksum value is returned as a 64-bit long to accommodate
     * both 32-bit and 64-bit checksum algorithms:</p>
     * <ul>
     *   <li><strong>CRC-32</strong>: Only lower 32 bits are significant;
     *       upper 32 bits will be zero</li>
     *   <li><strong>XXH3-64</strong>: Full 64-bit value is significant</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * catch (ChecksumException e) {
     *     System.err.printf("Expected checksum: 0x%016X%n", e.getExpected());
     *     System.err.printf("Actual checksum:   0x%016X%n", e.getActual());
     *     // Log or report the mismatch for analysis
     * }
     * }</pre>
     *
     * @return the expected checksum value as a 64-bit long; this is the
     *         checksum that was stored in the archive when the data was
     *         originally written; the value will never be modified after
     *         exception construction; for 32-bit checksums (like CRC-32),
     *         the upper 32 bits will be zero; for 64-bit checksums (like
     *         XXH3-64), the full 64-bit value is used
     *
     * @see #getActual()
     * @see de.splatgames.aether.pack.core.format.ChunkHeader#checksum()
     */
    public long getExpected() {
        return this.expected;
    }

    /**
     * Returns the actual checksum value that was computed during verification.
     *
     * <p>This method returns the checksum value that was computed from the
     * current data during the verification process. When this value differs
     * from the expected value returned by {@link #getExpected()}, it indicates
     * that the data has been corrupted or modified since the archive was created.</p>
     *
     * <p>The checksum value is returned as a 64-bit long to accommodate
     * both 32-bit and 64-bit checksum algorithms:</p>
     * <ul>
     *   <li><strong>CRC-32</strong>: Only lower 32 bits are significant;
     *       upper 32 bits will be zero</li>
     *   <li><strong>XXH3-64</strong>: Full 64-bit value is significant</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * catch (ChecksumException e) {
     *     long diff = e.getExpected() ^ e.getActual();
     *     int bitErrors = Long.bitCount(diff);
     *     System.err.printf("Checksum differs in %d bits%n", bitErrors);
     *
     *     // Many bit errors suggest random corruption
     *     // Few bit errors might indicate bit-flip errors recoverable by ECC
     * }
     * }</pre>
     *
     * @return the actual checksum value as a 64-bit long; this is the
     *         checksum that was computed from the data during verification;
     *         if this differs from the expected value, data corruption has
     *         occurred; the value will never be modified after exception
     *         construction; for 32-bit checksums (like CRC-32), the upper
     *         32 bits will be zero; for 64-bit checksums (like XXH3-64),
     *         the full 64-bit value is used
     *
     * @see #getExpected()
     * @see de.splatgames.aether.pack.core.spi.ChecksumProvider.Checksum#getValue()
     */
    public long getActual() {
        return this.actual;
    }

}
