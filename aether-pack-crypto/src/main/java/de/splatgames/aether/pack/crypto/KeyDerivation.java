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

package de.splatgames.aether.pack.crypto;

import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;

/**
 * Interface for key derivation functions (KDFs).
 *
 * <p>Key derivation functions are used to derive cryptographic keys from
 * passwords or other low-entropy secrets. They are intentionally designed
 * to be computationally expensive (in terms of time, memory, or both) to
 * resist brute-force and dictionary attacks.</p>
 *
 * <h2>Purpose</h2>
 * <p>KDFs solve the problem of converting human-memorable passwords into
 * high-entropy cryptographic keys suitable for encryption. Without a KDF,
 * an attacker could efficiently try millions of password guesses per second.
 * A properly configured KDF limits attacks to hundreds or thousands of
 * guesses per second.</p>
 *
 * <h2>Available Implementations</h2>
 * <table>
 *   <caption>Key Derivation Function Implementations</caption>
 *   <tr><th>Class</th><th>Algorithm</th><th>Recommendation</th></tr>
 *   <tr>
 *     <td>{@link Argon2idKeyDerivation}</td>
 *     <td>Argon2id</td>
 *     <td><strong>Recommended</strong> - Winner of Password Hashing Competition</td>
 *   </tr>
 *   <tr>
 *     <td>{@link Pbkdf2KeyDerivation}</td>
 *     <td>PBKDF2-HMAC-SHA256</td>
 *     <td>Fallback when Argon2 is unavailable</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create a KDF with default parameters
 * KeyDerivation kdf = new Argon2idKeyDerivation();
 *
 * // Generate a random salt (store this with the encrypted data)
 * byte[] salt = kdf.generateSalt();
 *
 * // Derive a 256-bit key from a password
 * SecretKey key = kdf.deriveKey("user-password", salt, 32);
 *
 * // Use the key for encryption
 * EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
 * OutputStream encrypted = aes.encrypt(output, key);
 * }</pre>
 *
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li><strong>Salt:</strong> Always use a unique, random salt for each password.
 *       The salt should be at least 16 bytes (128 bits) and stored with the
 *       encrypted data.</li>
 *   <li><strong>Parameters:</strong> Use strong parameters that make key derivation
 *       take at least 100ms on target hardware. Store parameters with the encrypted
 *       data using {@link #getParameters()}.</li>
 *   <li><strong>Password handling:</strong> Use {@code char[]} instead of {@code String}
 *       for passwords so they can be cleared from memory after use.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations should be thread-safe. Multiple threads can derive keys
 * concurrently using the same KDF instance.</p>
 *
 * @see Argon2idKeyDerivation
 * @see Pbkdf2KeyDerivation
 * @see KeyWrapper
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface KeyDerivation {

    /**
     * Returns the algorithm identifier for this KDF.
     *
     * <p>The identifier is a human-readable string that uniquely identifies
     * the algorithm. It is used for configuration and logging purposes.</p>
     *
     * @return the algorithm identifier string; never returns {@code null};
     *         examples: {@code "argon2id"}, {@code "pbkdf2-sha256"}
     */
    @NotNull String getId();

    /**
     * Returns the numeric identifier for this KDF.
     *
     * <p>The numeric ID is used in the binary format to identify the KDF
     * algorithm. This allows efficient storage and lookup without string
     * comparison.</p>
     *
     * @return the numeric identifier; unique for each algorithm
     */
    int getNumericId();

    /**
     * Derives a cryptographic key from a password.
     *
     * <p>This method applies the key derivation function to transform a
     * low-entropy password into a high-entropy key suitable for cryptographic
     * operations. The same password and salt will always produce the same key.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>Clear the password array after use: {@code Arrays.fill(password, '\0')}</li>
     *   <li>Use a unique random salt for each encryption operation</li>
     *   <li>Store the salt alongside the encrypted data</li>
     * </ul>
     *
     * @param password  the password as a character array; must not be {@code null};
     *                  should be cleared after use for security
     * @param salt      the salt bytes; must not be {@code null};
     *                  must be at least {@link #getMinSaltLength()} bytes;
     *                  should be randomly generated using {@link #generateSalt()}
     * @param keyLength the desired key length in bytes; typically 32 for 256-bit keys
     * @return the derived secret key; never returns {@code null}
     * @throws GeneralSecurityException if key derivation fails due to
     *         invalid parameters or algorithm unavailability
     * @throws IllegalArgumentException if the salt is too short
     *
     * @see #deriveKey(String, byte[], int)
     * @see #generateSalt()
     */
    @NotNull SecretKey deriveKey(
            final char @NotNull [] password,
            final byte @NotNull [] salt,
            final int keyLength) throws GeneralSecurityException;

    /**
     * Derives a cryptographic key from a password string.
     *
     * <p>This is a convenience method that converts the password string to a
     * character array and delegates to {@link #deriveKey(char[], byte[], int)}.
     * Note that using {@code String} for passwords is less secure because strings
     * are immutable and cannot be cleared from memory.</p>
     *
     * @param password  the password string; must not be {@code null}
     * @param salt      the salt bytes; must not be {@code null};
     *                  must be at least {@link #getMinSaltLength()} bytes
     * @param keyLength the desired key length in bytes
     * @return the derived secret key; never returns {@code null}
     * @throws GeneralSecurityException if key derivation fails
     *
     * @see #deriveKey(char[], byte[], int)
     */
    default @NotNull SecretKey deriveKey(
            final @NotNull String password,
            final byte @NotNull [] salt,
            final int keyLength) throws GeneralSecurityException {
        return deriveKey(password.toCharArray(), salt, keyLength);
    }

    /**
     * Returns the minimum required salt length in bytes.
     *
     * <p>Salts shorter than this value will cause {@link #deriveKey} to throw
     * an {@link IllegalArgumentException}. The minimum is algorithm-dependent
     * but typically at least 8-16 bytes.</p>
     *
     * @return the minimum salt length in bytes; always {@code > 0}
     *
     * @see #generateSalt()
     */
    int getMinSaltLength();

    /**
     * Generates a random salt of the recommended size.
     *
     * <p>The generated salt is cryptographically secure and of the recommended
     * length for this KDF (typically 16-32 bytes, which exceeds the minimum).</p>
     *
     * <p>Each encryption operation should use a unique salt. The salt should
     * be stored alongside the encrypted data to enable decryption.</p>
     *
     * @return a newly generated random salt; never returns {@code null};
     *         length is implementation-dependent but always at least
     *         {@link #getMinSaltLength()} bytes
     */
    byte @NotNull [] generateSalt();

    /**
     * Returns the serialized parameters used for key derivation.
     *
     * <p>The returned bytes encode all parameters needed to recreate this KDF
     * configuration (e.g., memory cost, iterations, parallelism for Argon2).
     * These parameters should be stored alongside the encrypted data to enable
     * decryption with the correct settings.</p>
     *
     * <p>Implementations provide factory methods like
     * {@link Argon2idKeyDerivation#fromParameters(byte[])} to recreate a KDF
     * from serialized parameters.</p>
     *
     * @return the serialized parameter bytes; never returns {@code null}
     *
     * @see Argon2idKeyDerivation#fromParameters(byte[])
     * @see Pbkdf2KeyDerivation#fromParameters(byte[])
     */
    byte @NotNull [] getParameters();

}
