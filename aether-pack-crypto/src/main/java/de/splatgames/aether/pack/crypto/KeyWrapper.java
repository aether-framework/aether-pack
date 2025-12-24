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

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Key wrapping utilities for protecting content encryption keys (CEKs).
 *
 * <p>This utility class implements AES Key Wrap (RFC 3394) to encrypt content
 * encryption keys using a key encryption key (KEK) derived from a password.
 * Key wrapping provides an additional layer of security by separating the
 * content encryption key from the password-derived key.</p>
 *
 * <h2>Purpose</h2>
 * <p>Key wrapping solves the problem of securely storing encryption keys.
 * Instead of using the password-derived key directly for content encryption,
 * a random content encryption key (CEK) is generated and then "wrapped"
 * (encrypted) using the password-derived key encryption key (KEK). This
 * approach has several advantages:</p>
 * <ul>
 *   <li><strong>Key separation:</strong> The CEK is independent of the password;
 *       changing the password only requires re-wrapping, not re-encrypting
 *       all content</li>
 *   <li><strong>Integrity protection:</strong> AES Key Wrap includes built-in
 *       integrity verification; unwrapping fails if the key was tampered with</li>
 *   <li><strong>Multi-recipient:</strong> The same CEK can be wrapped with
 *       multiple KEKs (different passwords) for shared access</li>
 * </ul>
 *
 * <h2>Typical Workflow</h2>
 * <pre>{@code
 * // Encryption (creating archive)
 * SecretKey cek = KeyWrapper.generateAes256Key();
 * byte[] salt = kdf.generateSalt();
 * byte[] wrappedKey = KeyWrapper.wrapWithPassword(cek, password, salt, kdf);
 * // Store: wrappedKey, salt, kdfParameters
 * // Use CEK to encrypt content
 *
 * // Decryption (reading archive)
 * SecretKey cek = KeyWrapper.unwrapWithPassword(wrappedKey, password, salt, kdf, "AES");
 * // Use CEK to decrypt content
 * }</pre>
 *
 * <h2>Security Properties</h2>
 * <table>
 *   <caption>AES Key Wrap (RFC 3394) Properties</caption>
 *   <tr><th>Property</th><th>Value</th><th>Description</th></tr>
 *   <tr><td>Algorithm</td><td>AES Key Wrap</td><td>NIST SP 800-38F / RFC 3394</td></tr>
 *   <tr><td>Integrity</td><td>64-bit IV check</td><td>Detects tampering or wrong password</td></tr>
 *   <tr><td>Overhead</td><td>8 bytes</td><td>Wrapped key is 8 bytes larger than CEK</td></tr>
 *   <tr><td>Key sizes</td><td>128, 192, 256 bits</td><td>Supports all standard AES key sizes</td></tr>
 * </table>
 *
 * <h2>Thread Safety</h2>
 * <p>All methods in this class are stateless and thread-safe. Multiple threads
 * can wrap and unwrap keys concurrently.</p>
 *
 * @see KeyDerivation
 * @see Argon2idKeyDerivation
 * @see Pbkdf2KeyDerivation
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class KeyWrapper {

    /** JCA algorithm name for AES Key Wrap (RFC 3394). */
    private static final String WRAP_ALGORITHM = "AESWrap";

    /** JCA algorithm name for AES keys. */
    private static final String KEY_ALGORITHM = "AES";

    /** Shared secure random instance for key generation. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Overhead added by AES Key Wrap (one 64-bit block).
     *
     * <p>AES Key Wrap adds 8 bytes of overhead to the wrapped key. This overhead
     * contains the integrity check value (ICV) that is verified during unwrap.
     * A 32-byte CEK becomes 40 bytes when wrapped.</p>
     */
    public static final int WRAP_OVERHEAD = 8;

    /**
     * Private constructor to prevent instantiation.
     *
     * <p>This is a utility class with only static methods and should not be instantiated.</p>
     */
    private KeyWrapper() {
    }

    /**
     * Wraps a content encryption key using a key encryption key.
     *
     * <p>This method encrypts the CEK using AES Key Wrap (RFC 3394), which
     * provides both confidentiality and integrity protection. The wrapped
     * key is {@value #WRAP_OVERHEAD} bytes larger than the original CEK.</p>
     *
     * <p><strong>Note:</strong> The KEK must be a valid AES key (128, 192, or
     * 256 bits). Use {@link #createAesKey(byte[])} to convert raw bytes to
     * an AES key if needed.</p>
     *
     * @param cek the content encryption key to wrap; must not be {@code null};
     *            the key length must be a multiple of 8 bytes (64 bits)
     * @param kek the key encryption key used to wrap the CEK; must not be
     *            {@code null}; must be a valid AES key (128, 192, or 256 bits)
     * @return the wrapped key bytes; length is {@code cek.length + 8};
     *         never returns {@code null}
     * @throws GeneralSecurityException if wrapping fails due to invalid key
     *         sizes or algorithm unavailability
     *
     * @see #unwrap(byte[], SecretKey, String)
     * @see #wrapWithPassword(SecretKey, char[], byte[], KeyDerivation)
     */
    public static byte @NotNull [] wrap(
            final @NotNull SecretKey cek,
            final @NotNull SecretKey kek) throws GeneralSecurityException {

        final Cipher cipher = Cipher.getInstance(WRAP_ALGORITHM);
        cipher.init(Cipher.WRAP_MODE, kek);
        return cipher.wrap(cek);
    }

    /**
     * Unwraps a content encryption key using a key encryption key.
     *
     * <p>This method decrypts a wrapped CEK and verifies its integrity. If the
     * KEK is incorrect (wrong password) or the wrapped key was tampered with,
     * a {@link GeneralSecurityException} is thrown. This integrity check is a
     * security feature of AES Key Wrap.</p>
     *
     * <p><strong>Security Note:</strong> The exception message does not reveal
     * whether the failure was due to a wrong password or data corruption, which
     * is intentional to prevent password guessing attacks.</p>
     *
     * @param wrappedKey the wrapped key bytes; must not be {@code null};
     *                   length should be original key length + {@value #WRAP_OVERHEAD}
     * @param kek        the key encryption key used for unwrapping; must not be
     *                   {@code null}; must be the same key used for wrapping
     * @param algorithm  the algorithm name for the unwrapped key; must not be
     *                   {@code null}; common values: "AES", "ChaCha20", "RAW"
     * @return the unwrapped content encryption key; never returns {@code null}
     * @throws GeneralSecurityException if unwrapping fails due to wrong password,
     *         data corruption, invalid wrapped key format, or algorithm unavailability
     *
     * @see #wrap(SecretKey, SecretKey)
     * @see #unwrapAes(byte[], SecretKey)
     * @see #unwrapWithPassword(byte[], char[], byte[], KeyDerivation, String)
     */
    public static @NotNull SecretKey unwrap(
            final byte @NotNull [] wrappedKey,
            final @NotNull SecretKey kek,
            final @NotNull String algorithm) throws GeneralSecurityException {

        final Cipher cipher = Cipher.getInstance(WRAP_ALGORITHM);
        cipher.init(Cipher.UNWRAP_MODE, kek);
        return (SecretKey) cipher.unwrap(wrappedKey, algorithm, Cipher.SECRET_KEY);
    }

    /**
     * Unwraps a content encryption key for AES.
     *
     * <p>This is a convenience method equivalent to
     * {@code unwrap(wrappedKey, kek, "AES")}. Use this when you know the
     * wrapped key is an AES key.</p>
     *
     * @param wrappedKey the wrapped key bytes; must not be {@code null}
     * @param kek        the key encryption key; must not be {@code null}
     * @return the unwrapped AES key; never returns {@code null}
     * @throws GeneralSecurityException if unwrapping fails due to wrong
     *         password or data corruption
     *
     * @see #unwrap(byte[], SecretKey, String)
     */
    public static @NotNull SecretKey unwrapAes(
            final byte @NotNull [] wrappedKey,
            final @NotNull SecretKey kek) throws GeneralSecurityException {

        return unwrap(wrappedKey, kek, KEY_ALGORITHM);
    }

    /**
     * Generates a random content encryption key for AES-256.
     *
     * <p>This method generates a cryptographically secure random 256-bit
     * (32-byte) key suitable for AES-256 encryption. The generated key
     * should be wrapped using {@link #wrap(SecretKey, SecretKey)} before
     * storage.</p>
     *
     * @return a random 256-bit AES key; never returns {@code null}
     *
     * @see #createAesKey(byte[])
     * @see #wrap(SecretKey, SecretKey)
     */
    public static @NotNull SecretKey generateAes256Key() {
        final byte[] keyBytes = new byte[32];
        SECURE_RANDOM.nextBytes(keyBytes);
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }

    /**
     * Creates an AES key from raw bytes.
     *
     * <p>This method wraps raw key bytes into a {@link SecretKey} object
     * with the AES algorithm identifier. Use this when you have key bytes
     * from a key derivation function or other source.</p>
     *
     * @param keyBytes the raw key bytes; must not be {@code null};
     *                 length should be 16 (AES-128), 24 (AES-192),
     *                 or 32 (AES-256) bytes
     * @return an AES secret key; never returns {@code null}
     *
     * @see #generateAes256Key()
     */
    public static @NotNull SecretKey createAesKey(final byte @NotNull [] keyBytes) {
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }

    /**
     * Calculates the wrapped key size for a given key size.
     *
     * <p>AES Key Wrap adds {@value #WRAP_OVERHEAD} bytes of overhead to any
     * wrapped key. Use this method to allocate the correct buffer size
     * for storing wrapped keys.</p>
     *
     * @param keySize the original key size in bytes
     * @return the wrapped key size in bytes ({@code keySize + 8})
     */
    public static int wrappedSize(final int keySize) {
        return keySize + WRAP_OVERHEAD;
    }

    /**
     * Derives a key encryption key from a password and wraps a content key.
     *
     * <p>This is the primary convenience method for password-based key wrapping.
     * It performs the complete workflow:</p>
     * <ol>
     *   <li>Derives a 256-bit KEK from the password using the provided KDF</li>
     *   <li>Wraps the CEK using AES Key Wrap</li>
     *   <li>Securely clears the KEK from memory</li>
     * </ol>
     *
     * <p><strong>Important:</strong> The caller is responsible for storing the
     * salt and KDF parameters alongside the wrapped key to enable later
     * unwrapping.</p>
     *
     * @param cek      the content encryption key to wrap; must not be {@code null}
     * @param password the password as a character array; must not be {@code null};
     *                 should be cleared by the caller after this call returns
     * @param salt     the salt for key derivation; must not be {@code null};
     *                 must meet the KDF's minimum salt length requirement
     * @param kdf      the key derivation function to use; must not be {@code null};
     *                 typically {@link Argon2idKeyDerivation} or {@link Pbkdf2KeyDerivation}
     * @return the wrapped key bytes; never returns {@code null}
     * @throws GeneralSecurityException if key derivation or wrapping fails
     *
     * @see #unwrapWithPassword(byte[], char[], byte[], KeyDerivation, String)
     */
    public static byte @NotNull [] wrapWithPassword(
            final @NotNull SecretKey cek,
            final char @NotNull [] password,
            final byte @NotNull [] salt,
            final @NotNull KeyDerivation kdf) throws GeneralSecurityException {

        // Derive KEK from password (256-bit for AES-256)
        final SecretKey derivedKey = kdf.deriveKey(password, salt, 32);
        // Convert to AES key (KDF may return key with generic algorithm)
        final SecretKey kek = createAesKey(derivedKey.getEncoded());
        try {
            return wrap(cek, kek);
        } finally {
            // Clear the KEK
            clearKey(kek);
            clearKey(derivedKey);
        }
    }

    /**
     * Unwraps a content key using a password-derived key.
     *
     * <p>This is the primary convenience method for password-based key unwrapping.
     * It performs the complete workflow:</p>
     * <ol>
     *   <li>Derives the KEK from the password using the provided KDF</li>
     *   <li>Unwraps the CEK using AES Key Wrap</li>
     *   <li>Securely clears the KEK from memory</li>
     * </ol>
     *
     * <p><strong>Security Note:</strong> If the password is incorrect, the
     * unwrap operation will fail with a {@link GeneralSecurityException}.
     * The exception does not indicate whether the failure was due to a wrong
     * password or data corruption.</p>
     *
     * @param wrappedKey the wrapped key bytes; must not be {@code null}
     * @param password   the password as a character array; must not be {@code null};
     *                   should be cleared by the caller after this call returns
     * @param salt       the salt that was used during wrapping; must not be {@code null}
     * @param kdf        the key derivation function (must match wrapping configuration);
     *                   must not be {@code null}
     * @param algorithm  the algorithm name for the unwrapped key; must not be {@code null};
     *                   common values: "AES", "ChaCha20", "RAW"
     * @return the unwrapped content encryption key; never returns {@code null}
     * @throws GeneralSecurityException if key derivation or unwrapping fails
     *         (wrong password, corrupted data, or algorithm unavailability)
     *
     * @see #wrapWithPassword(SecretKey, char[], byte[], KeyDerivation)
     */
    public static @NotNull SecretKey unwrapWithPassword(
            final byte @NotNull [] wrappedKey,
            final char @NotNull [] password,
            final byte @NotNull [] salt,
            final @NotNull KeyDerivation kdf,
            final @NotNull String algorithm) throws GeneralSecurityException {

        // Derive KEK from password
        final SecretKey derivedKey = kdf.deriveKey(password, salt, 32);
        // Convert to AES key (KDF may return key with generic algorithm)
        final SecretKey kek = createAesKey(derivedKey.getEncoded());
        try {
            return unwrap(wrappedKey, kek, algorithm);
        } finally {
            // Clear the KEK
            clearKey(kek);
            clearKey(derivedKey);
        }
    }

    /**
     * Attempts to clear sensitive key material from memory.
     *
     * <p>This method makes a best-effort attempt to zero out the key's
     * encoded bytes. Not all key implementations support this operation,
     * so failures are silently ignored.</p>
     *
     * <p><strong>Note:</strong> Due to Java's memory model and garbage
     * collection, complete erasure cannot be guaranteed. The key bytes
     * may still exist in memory until overwritten.</p>
     *
     * @param key the key to clear; must not be {@code null}
     */
    private static void clearKey(final @NotNull SecretKey key) {
        try {
            final byte[] encoded = key.getEncoded();
            if (encoded != null) {
                Arrays.fill(encoded, (byte) 0);
            }
        } catch (final Exception ignored) {
            // Some key implementations may not support this
        }
    }

}
