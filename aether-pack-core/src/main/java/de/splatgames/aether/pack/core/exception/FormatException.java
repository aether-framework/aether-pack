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
 * Exception thrown when the APACK format is invalid or corrupted.
 *
 * <p>This exception indicates structural problems with an APACK archive
 * that prevent it from being read correctly. Format errors typically
 * occur during the initial parsing phase when reading headers, trailers,
 * or other structural elements.</p>
 *
 * <h2>Common Causes</h2>
 * <ul>
 *   <li><strong>Invalid magic number</strong> - File doesn't start with "APACK"</li>
 *   <li><strong>Truncated file</strong> - Unexpected end of file during parsing</li>
 *   <li><strong>Invalid header values</strong> - Sizes, offsets, or counts out of range</li>
 *   <li><strong>Missing trailer</strong> - Container mode file without valid trailer</li>
 *   <li><strong>Corrupted structure</strong> - Internal pointers or offsets don't match</li>
 * </ul>
 *
 * <h2>Subclasses</h2>
 * <ul>
 *   <li>{@link UnsupportedVersionException} - File requires a newer reader version</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (AetherPackReader reader = AetherPackReader.open(path)) {
 *     // Process archive...
 * } catch (FormatException e) {
 *     if (e instanceof UnsupportedVersionException ve) {
 *         System.err.printf("Please upgrade: file requires version %d, " +
 *             "reader is version %d%n", ve.getRequiredVersion(), ve.getCurrentVersion());
 *     } else {
 *         System.err.println("Invalid archive: " + e.getMessage());
 *     }
 * }
 * }</pre>
 *
 * <h2>Recovery</h2>
 * <p>Format exceptions are generally not recoverable. The file is either
 * not a valid APACK archive or has been corrupted. If the archive has
 * error correction data (ECC), the reader will attempt recovery before
 * throwing this exception.</p>
 *
 * @see ApackException
 * @see UnsupportedVersionException
 * @see de.splatgames.aether.pack.core.format.FormatConstants
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class FormatException extends ApackException {

    /**
     * Creates a new format exception with the specified detail message.
     *
     * <p>This constructor creates an exception indicating that the archive
     * format is invalid or corrupted. The message should describe what
     * format element was invalid and, if possible, what was expected.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>"Invalid magic number: expected 'APACK', got 'ZIPF'"</li>
     *   <li>"Truncated file header: expected 64 bytes, got 32"</li>
     *   <li>"Invalid entry count: cannot be negative"</li>
     * </ul>
     *
     * @param message the detail message describing the format violation;
     *                must not be {@code null}; should specify which format
     *                element is invalid and provide context for diagnosis
     *
     * @see #FormatException(String, Throwable)
     */
    public FormatException(final @NotNull String message) {
        super(message);
    }

    /**
     * Creates a new format exception with the specified message and cause.
     *
     * <p>This constructor is used when a format error is detected as a
     * result of another exception, such as an I/O error during parsing
     * or a data conversion error.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * try {
     *     int entryCount = reader.readInt();
     * } catch (IOException e) {
     *     throw new FormatException("Failed to read entry count from header", e);
     * }
     * }</pre>
     *
     * @param message the detail message describing the format violation;
     *                must not be {@code null}; should explain what format
     *                element could not be read or validated
     * @param cause   the underlying exception that caused this error;
     *                may be {@code null}; typically an {@link java.io.IOException}
     *                or data conversion exception
     *
     * @see #FormatException(String)
     */
    public FormatException(final @NotNull String message, final @Nullable Throwable cause) {
        super(message, cause);
    }

}
