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
import org.jetbrains.annotations.Nullable;

/**
 * Base exception for all Aether Pack related errors.
 *
 * <p>This is the root of the APACK exception hierarchy. All exceptions
 * thrown by APACK operations extend this class, allowing callers to
 * catch all APACK-related errors with a single catch clause.</p>
 *
 * <h2>Exception Hierarchy</h2>
 * <pre>
 * ApackException
 * ├── FormatException          - Invalid or corrupted file format
 * │   └── UnsupportedVersionException - Version compatibility issues
 * ├── ChecksumException        - Checksum verification failures
 * ├── CompressionException     - Compression/decompression errors
 * ├── EncryptionException      - Encryption/decryption errors
 * └── EntryNotFoundException   - Entry lookup failures
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (AetherPackReader reader = AetherPackReader.open(path)) {
 *     InputStream in = reader.openEntry("config.json");
 *     // ...
 * } catch (FormatException e) {
 *     System.err.println("Invalid archive format: " + e.getMessage());
 * } catch (ChecksumException e) {
 *     System.err.println("Data corruption detected!");
 *     System.err.printf("Expected: 0x%08X, Actual: 0x%08X%n",
 *         e.getExpected(), e.getActual());
 * } catch (ApackException e) {
 *     // Catch-all for other APACK errors
 *     System.err.println("APACK error: " + e.getMessage());
 * }
 * }</pre>
 *
 * <h2>Checked Exception</h2>
 * <p>This is a checked exception extending {@link Exception}. This design
 * ensures that callers explicitly handle potential errors during archive
 * operations, promoting robust error handling.</p>
 *
 * @see FormatException
 * @see ChecksumException
 * @see CompressionException
 * @see EncryptionException
 * @see EntryNotFoundException
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class ApackException extends Exception {

    /**
     * Creates a new exception with the specified detail message.
     *
     * <p>This constructor creates an exception with a descriptive message
     * explaining what went wrong. The message should be specific enough
     * to help diagnose the issue.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>Include the operation that failed (e.g., "reading entry header")</li>
     *   <li>Include relevant context (e.g., entry name, offset, expected values)</li>
     *   <li>Use clear, actionable language</li>
     * </ul>
     *
     * @param message the detail message describing the error condition;
     *                must not be {@code null}; will be returned by
     *                {@link #getMessage()}; should be descriptive enough
     *                for logging and debugging
     *
     * @see #ApackException(String, Throwable)
     */
    public ApackException(final @NotNull String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and cause.
     *
     * <p>This constructor is used when the exception is caused by another
     * exception. The cause is preserved for diagnostic purposes and can
     * be retrieved using {@link #getCause()}. This creates an exception
     * chain that helps trace the root cause of the problem.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * try {
     *     cipher.doFinal(data);
     * } catch (GeneralSecurityException e) {
     *     throw new ApackException("Failed to decrypt entry data", e);
     * }
     * }</pre>
     *
     * @param message the detail message describing the error condition;
     *                must not be {@code null}; should explain the high-level
     *                operation that failed
     * @param cause   the underlying exception that caused this error;
     *                may be {@code null} if no cause is available or
     *                applicable; if provided, can be retrieved via
     *                {@link #getCause()}
     *
     * @see #ApackException(String)
     */
    public ApackException(final @NotNull String message, final @Nullable Throwable cause) {
        super(message, cause);
    }

}
