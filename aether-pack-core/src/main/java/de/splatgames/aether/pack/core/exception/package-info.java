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

/**
 * Exception hierarchy for APACK operations.
 *
 * <p>This package defines a structured exception hierarchy for all errors that
 * can occur during APACK archive operations. All exceptions extend the base
 * {@link de.splatgames.aether.pack.core.exception.ApackException} class,
 * allowing applications to catch all APACK-related errors with a single
 * catch clause while still being able to handle specific error types
 * when needed.</p>
 *
 * <h2>Exception Hierarchy</h2>
 * <pre>
 * java.lang.Exception
 *     │
 *     └── {@link de.splatgames.aether.pack.core.exception.ApackException ApackException}
 *             │   Base exception for all APACK errors
 *             │
 *             ├── {@link de.splatgames.aether.pack.core.exception.FormatException FormatException}
 *             │       │   Invalid or corrupted archive format
 *             │       │
 *             │       └── {@link de.splatgames.aether.pack.core.exception.UnsupportedVersionException UnsupportedVersionException}
 *             │               Archive version not supported by this library
 *             │
 *             ├── {@link de.splatgames.aether.pack.core.exception.ChecksumException ChecksumException}
 *             │       Data integrity verification failed
 *             │       (includes expected and actual checksum values)
 *             │
 *             ├── {@link de.splatgames.aether.pack.core.exception.CompressionException CompressionException}
 *             │       Compression or decompression operation failed
 *             │
 *             ├── {@link de.splatgames.aether.pack.core.exception.EncryptionException EncryptionException}
 *             │       Encryption or decryption operation failed
 *             │       (wrong key, corrupted ciphertext, auth failure)
 *             │
 *             └── {@link de.splatgames.aether.pack.core.exception.EntryNotFoundException EntryNotFoundException}
 *                     Requested entry does not exist in the archive
 * </pre>
 *
 * <h2>Design Philosophy</h2>
 * <p>The exception hierarchy follows these design principles:</p>
 * <ul>
 *   <li><strong>Checked Exceptions:</strong> All exceptions are checked
 *       (extend {@code Exception}), ensuring callers explicitly handle errors</li>
 *   <li><strong>Semantic Hierarchy:</strong> Exception types reflect the nature
 *       of the error, allowing precise handling</li>
 *   <li><strong>Rich Context:</strong> Exceptions include relevant details
 *       (checksums, entry names, offsets) for debugging</li>
 *   <li><strong>Exception Chaining:</strong> Original causes are preserved
 *       for complete error tracing</li>
 * </ul>
 *
 * <h2>Exception Descriptions</h2>
 * <table>
 *   <caption>APACK Exception Types</caption>
 *   <tr><th>Exception</th><th>When Thrown</th><th>Recovery</th></tr>
 *   <tr>
 *     <td>{@code FormatException}</td>
 *     <td>Invalid magic bytes, corrupted headers, malformed structures</td>
 *     <td>Archive is damaged; cannot recover</td>
 *   </tr>
 *   <tr>
 *     <td>{@code UnsupportedVersionException}</td>
 *     <td>Archive requires newer library version</td>
 *     <td>Upgrade library or use compatible version</td>
 *   </tr>
 *   <tr>
 *     <td>{@code ChecksumException}</td>
 *     <td>Data integrity check failed</td>
 *     <td>If ECC enabled, automatic correction; otherwise, corruption</td>
 *   </tr>
 *   <tr>
 *     <td>{@code CompressionException}</td>
 *     <td>Decompression failed (corrupted data)</td>
 *     <td>Data is corrupted; cannot recover without ECC</td>
 *   </tr>
 *   <tr>
 *     <td>{@code EncryptionException}</td>
 *     <td>Wrong password, tampered ciphertext, auth tag mismatch</td>
 *     <td>Verify password; if correct, data is corrupted</td>
 *   </tr>
 *   <tr>
 *     <td>{@code EntryNotFoundException}</td>
 *     <td>Requested entry name not in archive</td>
 *     <td>Check entry name spelling; list entries</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Catching All APACK Errors</h3>
 * <pre>{@code
 * try (AetherPackReader reader = AetherPackReader.open(path)) {
 *     processArchive(reader);
 * } catch (ApackException e) {
 *     // Handle any APACK-related error
 *     System.err.println("Archive error: " + e.getMessage());
 *     if (e.getCause() != null) {
 *         System.err.println("Caused by: " + e.getCause());
 *     }
 * }
 * }</pre>
 *
 * <h3>Specific Exception Handling</h3>
 * <pre>{@code
 * try (AetherPackReader reader = AetherPackReader.open(path)) {
 *     InputStream in = reader.getInputStream("config.json");
 *     // ...
 * } catch (EntryNotFoundException e) {
 *     // Entry doesn't exist - use defaults
 *     System.out.println("Config not found, using defaults");
 *     config = Config.defaults();
 *
 * } catch (ChecksumException e) {
 *     // Data corruption detected
 *     System.err.printf("Corruption detected!%n");
 *     System.err.printf("  Expected checksum: 0x%08X%n", e.getExpected());
 *     System.err.printf("  Actual checksum:   0x%08X%n", e.getActual());
 *     throw e;
 *
 * } catch (EncryptionException e) {
 *     // Wrong password or tampering
 *     System.err.println("Decryption failed - wrong password?");
 *     throw e;
 *
 * } catch (UnsupportedVersionException e) {
 *     // Archive from newer version
 *     System.err.printf("Archive requires version %s, but we are %s%n",
 *         e.getRequiredVersion(), e.getCurrentVersion());
 *     throw e;
 *
 * } catch (FormatException e) {
 *     // Invalid or corrupted format
 *     System.err.println("Invalid archive format: " + e.getMessage());
 *     throw e;
 *
 * } catch (ApackException e) {
 *     // Catch-all for any other APACK errors
 *     System.err.println("Unexpected error: " + e.getMessage());
 *     throw e;
 * }
 * }</pre>
 *
 * <h3>Throwing Exceptions in Custom Code</h3>
 * <pre>{@code
 * public void validateEntry(Entry entry) throws ApackException {
 *     if (entry.getOriginalSize() > MAX_SIZE) {
 *         throw new ApackException("Entry too large: " + entry.getOriginalSize());
 *     }
 *
 *     if (!entry.getName().startsWith("allowed/")) {
 *         throw new FormatException("Invalid entry path: " + entry.getName());
 *     }
 * }
 * }</pre>
 *
 * <h2>ChecksumException Details</h2>
 * <p>{@link de.splatgames.aether.pack.core.exception.ChecksumException} provides
 * additional context for debugging:</p>
 * <pre>{@code
 * try {
 *     reader.readEntry(entry);
 * } catch (ChecksumException e) {
 *     long expected = e.getExpected();  // Expected checksum value
 *     long actual = e.getActual();      // Computed checksum value
 *
 *     // Determine corruption extent
 *     long xor = expected ^ actual;
 *     int bitErrors = Long.bitCount(xor);
 *     System.err.printf("Detected ~%d bit errors%n", bitErrors);
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>Exception classes are immutable and thread-safe. They can be safely
 * thrown across thread boundaries and stored for later analysis.</p>
 *
 * @see de.splatgames.aether.pack.core.exception.ApackException
 * @see de.splatgames.aether.pack.core.exception.FormatException
 * @see de.splatgames.aether.pack.core.exception.ChecksumException
 * @see de.splatgames.aether.pack.core.exception.CompressionException
 * @see de.splatgames.aether.pack.core.exception.EncryptionException
 * @see de.splatgames.aether.pack.core.exception.EntryNotFoundException
 * @see de.splatgames.aether.pack.core.exception.UnsupportedVersionException
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.splatgames.aether.pack.core.exception;
