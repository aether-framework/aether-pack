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
 * Exception thrown when encryption or decryption fails.
 *
 * <p>This exception indicates that a cryptographic operation could not
 * be completed. This can occur during both writing (encryption) and
 * reading (decryption) of encrypted archives.</p>
 *
 * <h2>Common Causes</h2>
 * <ul>
 *   <li><strong>Wrong password</strong> - Decryption key derived from wrong password</li>
 *   <li><strong>Wrong key</strong> - Incorrect encryption key provided</li>
 *   <li><strong>Corrupted ciphertext</strong> - Encrypted data has been modified</li>
 *   <li><strong>Authentication failure</strong> - AEAD tag verification failed</li>
 *   <li><strong>Algorithm unavailable</strong> - Required cipher not available in JCE</li>
 *   <li><strong>Invalid key length</strong> - Key doesn't match algorithm requirements</li>
 * </ul>
 *
 * <h2>Supported Algorithms</h2>
 * <ul>
 *   <li><strong>AES-256-GCM</strong> - 256-bit key, 12-byte nonce, 16-byte tag</li>
 *   <li><strong>ChaCha20-Poly1305</strong> - 256-bit key, 12-byte nonce, 16-byte tag</li>
 * </ul>
 *
 * <h2>AEAD Properties</h2>
 * <p>All supported algorithms use Authenticated Encryption with Associated
 * Data (AEAD). This means that any tampering with the ciphertext will be
 * detected during decryption, resulting in this exception.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try {
 *     SecretKey key = deriveKey(password);
 *     try (AetherPackReader reader = AetherPackReader.open(path, key)) {
 *         // Read encrypted entries...
 *     }
 * } catch (EncryptionException e) {
 *     if (e.getCause() instanceof AEADBadTagException) {
 *         System.err.println("Wrong password or corrupted archive");
 *     } else {
 *         System.err.println("Decryption failed: " + e.getMessage());
 *     }
 * }
 * }</pre>
 *
 * <h2>Security Considerations</h2>
 * <p>For security reasons, the exception message intentionally does not
 * distinguish between "wrong password" and "corrupted data" to prevent
 * information leakage that could aid cryptographic attacks.</p>
 *
 * @see ApackException
 * @see de.splatgames.aether.pack.core.spi.EncryptionProvider
 * @see de.splatgames.aether.pack.core.format.EncryptionBlock
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class EncryptionException extends ApackException {

    /**
     * Creates a new encryption exception with the specified detail message.
     *
     * <p>This constructor creates an encryption exception without an underlying
     * cause. Use this constructor when the encryption error is detected
     * directly by the APACK library, such as when encountering an unknown
     * encryption algorithm ID or when key validation fails before attempting
     * cryptographic operations.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The message should clearly describe the encryption failure while
     * avoiding security-sensitive details. Include context such as:</p>
     * <ul>
     *   <li>The encryption algorithm involved (if known)</li>
     *   <li>The operation that failed (encryption or decryption)</li>
     *   <li>General error category (authentication, key, algorithm)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <p>For security reasons, messages should NOT distinguish between
     * "wrong password" and "corrupted data" to prevent information leakage
     * that could aid cryptographic attacks. Use generic messages like
     * "decryption failed" rather than "authentication tag mismatch".</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>"Unknown encryption algorithm ID: 0x42"</li>
     *   <li>"AES-GCM encryption provider not found"</li>
     *   <li>"Decryption failed: authentication error"</li>
     *   <li>"Invalid key length: expected 32 bytes, got 16"</li>
     * </ul>
     *
     * @param message the detail message describing the encryption failure;
     *                must not be {@code null}; should provide sufficient
     *                context to diagnose the problem without revealing
     *                security-sensitive information; avoid distinguishing
     *                between authentication failures and data corruption;
     *                this message is returned by {@link #getMessage()} and
     *                included in stack traces
     *
     * @see #EncryptionException(String, Throwable)
     * @see de.splatgames.aether.pack.core.spi.EncryptionProvider
     */
    public EncryptionException(final @NotNull String message) {
        super(message);
    }

    /**
     * Creates a new encryption exception with the specified message and cause.
     *
     * <p>This constructor creates an encryption exception that wraps an
     * underlying exception. Use this constructor when the encryption error
     * is caused by another exception, typically from the Java Cryptography
     * Extension (JCE) or a third-party cryptographic library.</p>
     *
     * <p>The cause exception is preserved for diagnostic purposes and can
     * be retrieved using {@link #getCause()}. This creates an exception
     * chain that helps trace the root cause of the encryption failure.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>{@link javax.crypto.AEADBadTagException} - AEAD authentication tag
     *       verification failed (wrong key or corrupted data)</li>
     *   <li>{@link java.security.InvalidKeyException} - Invalid key format,
     *       length, or type for the cipher</li>
     *   <li>{@link javax.crypto.IllegalBlockSizeException} - Ciphertext length
     *       not aligned to block size</li>
     *   <li>{@link javax.crypto.BadPaddingException} - Invalid PKCS padding
     *       (not used with AEAD ciphers)</li>
     *   <li>{@link java.security.NoSuchAlgorithmException} - Cipher algorithm
     *       not available in the JCE provider</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * try {
     *     cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
     *     byte[] decrypted = cipher.doFinal(ciphertext);
     * } catch (GeneralSecurityException e) {
     *     throw new EncryptionException(
     *         "Decryption failed for entry: " + entryName, e);
     * }
     * }</pre>
     *
     * @param message the detail message describing the encryption failure;
     *                must not be {@code null}; should explain the high-level
     *                operation that failed without revealing security-sensitive
     *                information; the underlying cause provides additional
     *                technical details for debugging
     * @param cause   the underlying exception that caused the encryption
     *                failure; may be {@code null} if no cause is available
     *                or applicable; if provided, can be retrieved via
     *                {@link #getCause()}; typically a JCE exception such as
     *                {@link javax.crypto.AEADBadTagException} or
     *                {@link java.security.GeneralSecurityException}
     *
     * @see #EncryptionException(String)
     * @see de.splatgames.aether.pack.core.spi.EncryptionProvider#decryptBlock(byte[], javax.crypto.SecretKey, byte[])
     */
    public EncryptionException(final @NotNull String message, final @Nullable Throwable cause) {
        super(message, cause);
    }

}
