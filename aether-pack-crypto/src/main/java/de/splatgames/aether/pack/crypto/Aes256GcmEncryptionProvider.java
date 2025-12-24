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

import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.core.spi.EncryptionProvider;
import org.jetbrains.annotations.NotNull;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * AES-256-GCM encryption provider implementation.
 *
 * <p>This class implements the {@link EncryptionProvider} interface using
 * AES-256 in Galois/Counter Mode (GCM). AES-256-GCM is an Authenticated
 * Encryption with Associated Data (AEAD) algorithm that provides both
 * confidentiality and integrity protection in a single operation.</p>
 *
 * <h2>Algorithm Properties</h2>
 * <table>
 *   <caption>AES-256-GCM Parameters</caption>
 *   <tr><th>Property</th><th>Value</th><th>Description</th></tr>
 *   <tr><td>Key Size</td><td>256 bits (32 bytes)</td><td>Secret key length</td></tr>
 *   <tr><td>Nonce Size</td><td>96 bits (12 bytes)</td><td>Initialization vector (recommended for GCM)</td></tr>
 *   <tr><td>Tag Size</td><td>128 bits (16 bytes)</td><td>Authentication tag length</td></tr>
 *   <tr><td>Block Size</td><td>128 bits (16 bytes)</td><td>AES block size</td></tr>
 * </table>
 *
 * <h2>Security Properties</h2>
 * <ul>
 *   <li><strong>Confidentiality:</strong> Data is encrypted using AES-256 in counter mode</li>
 *   <li><strong>Integrity:</strong> Tampering is detected via the 128-bit authentication tag</li>
 *   <li><strong>Authenticity:</strong> The authentication tag verifies the message origin</li>
 *   <li><strong>AAD Support:</strong> Additional data can be authenticated without encryption</li>
 * </ul>
 *
 * <h2>Nonce Handling</h2>
 * <p>This implementation generates a cryptographically secure random nonce
 * for each encryption operation and prepends it to the ciphertext. The format
 * of encrypted output is:</p>
 * <pre>
 * [12-byte nonce][ciphertext][16-byte authentication tag]
 * </pre>
 *
 * <p><strong>Warning:</strong> Never reuse a nonce with the same key. This
 * implementation uses {@link SecureRandom} to generate unique nonces, which
 * is safe for up to approximately 2^48 encryptions with the same key.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
 *
 * // Generate a random key
 * SecretKey key = aes.generateKey();
 *
 * // Encrypt data (streaming)
 * try (OutputStream out = aes.encrypt(fileOutputStream, key)) {
 *     out.write(plaintext);
 * }
 *
 * // Decrypt data (streaming)
 * try (InputStream in = aes.decrypt(fileInputStream, key)) {
 *     byte[] decrypted = in.readAllBytes();
 * }
 *
 * // Block-based encryption
 * byte[] ciphertext = aes.encryptBlock(plaintext, key);
 * byte[] decrypted = aes.decryptBlock(ciphertext, key);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is stateless and thread-safe. The shared {@link SecureRandom}
 * instance is also thread-safe. Multiple threads can use the same provider
 * instance concurrently without synchronization.</p>
 *
 * @see EncryptionProvider
 * @see EncryptionRegistry#aes256Gcm()
 * @see ChaCha20Poly1305EncryptionProvider
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class Aes256GcmEncryptionProvider implements EncryptionProvider {

    /**
     * Algorithm identifier string for AES-256-GCM.
     * This value is used for registry lookup and configuration purposes.
     */
    public static final String ID = "aes-256-gcm";

    /**
     * Key size in bytes (256 bits = 32 bytes).
     * AES-256 requires exactly 32 bytes of key material.
     */
    public static final int KEY_SIZE = 32;

    /**
     * Nonce/IV size in bytes (96 bits = 12 bytes).
     * This is the NIST-recommended nonce size for GCM mode.
     */
    public static final int NONCE_SIZE = 12;

    /**
     * Authentication tag size in bytes (128 bits = 16 bytes).
     * The maximum tag size for GCM, providing optimal security.
     */
    public static final int TAG_SIZE = 16;

    /** GCM tag length in bits (128 bits). */
    private static final int TAG_LENGTH_BITS = TAG_SIZE * 8;

    /** JCA algorithm name for AES. */
    private static final String ALGORITHM = "AES";

    /** JCA transformation string for AES-GCM with no padding. */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** Shared secure random instance for nonce and key generation. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Creates a new AES-256-GCM encryption provider instance.
     *
     * <p>The provider is stateless and can be safely shared across multiple
     * threads and operations. Consider using the singleton instance from
     * {@link EncryptionRegistry#aes256Gcm()} for convenience.</p>
     *
     * @see EncryptionRegistry#aes256Gcm()
     */
    public Aes256GcmEncryptionProvider() {
    }

    /**
     * {@inheritDoc}
     *
     * @return the string identifier {@value #ID}; this value is used for
     *         registry lookup and is case-insensitive
     */
    @Override
    public @NotNull String getId() {
        return ID;
    }

    /**
     * {@inheritDoc}
     *
     * @return the numeric identifier {@link FormatConstants#ENCRYPTION_AES_256_GCM};
     *         this value is stored in the binary format to identify the algorithm
     */
    @Override
    public int getNumericId() {
        return FormatConstants.ENCRYPTION_AES_256_GCM;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #KEY_SIZE} bytes (256 bits); keys must be exactly this size
     */
    @Override
    public int getKeySize() {
        return KEY_SIZE;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #NONCE_SIZE} bytes (96 bits); this is the NIST-recommended
     *         nonce size for GCM mode
     */
    @Override
    public int getNonceSize() {
        return NONCE_SIZE;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #TAG_SIZE} bytes (128 bits); this is the maximum tag size
     *         for GCM, providing optimal security
     */
    @Override
    public int getTagSize() {
        return TAG_SIZE;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method wraps the output stream with an encrypting stream that applies
     * AES-256-GCM encryption. A random 12-byte nonce is generated and written to
     * the output before any encrypted data. The authentication tag is appended
     * when the stream is closed.</p>
     *
     * @param output the underlying output stream to wrap; must not be {@code null};
     *               encrypted data (including nonce and tag) will be written here
     * @param key    the secret key for encryption; must not be {@code null};
     *               must be exactly 32 bytes (256 bits)
     * @return an encrypting output stream that wraps the provided stream;
     *         never returns {@code null}; closing this stream finalizes
     *         encryption and writes the authentication tag
     * @throws IOException if an I/O error occurs while writing the nonce
     * @throws GeneralSecurityException if cipher initialization fails due to
     *         an invalid key or unavailable algorithm
     *
     * @see #encrypt(OutputStream, SecretKey, byte[])
     */
    @Override
    public @NotNull OutputStream encrypt(
            final @NotNull OutputStream output,
            final @NotNull SecretKey key) throws IOException, GeneralSecurityException {

        return encrypt(output, key, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method wraps the output stream with an encrypting stream that applies
     * AES-256-GCM encryption with additional authenticated data. The AAD is
     * authenticated but not encrypted, allowing metadata to be verified during
     * decryption without being hidden.</p>
     *
     * <p>The output format is: {@code [12-byte nonce][encrypted data][16-byte tag]}</p>
     *
     * @param output the underlying output stream to wrap; must not be {@code null};
     *               encrypted data will be written here
     * @param key    the secret key for encryption; must not be {@code null};
     *               must be exactly 32 bytes (256 bits)
     * @param aad    additional authenticated data; may be {@code null} or empty;
     *               this data is authenticated but not encrypted; must match
     *               exactly during decryption or authentication will fail
     * @return an encrypting output stream; never returns {@code null}
     * @throws IOException if an I/O error occurs while writing the nonce
     * @throws GeneralSecurityException if cipher initialization fails
     *
     * @see #decrypt(InputStream, SecretKey, byte[])
     */
    @Override
    public @NotNull OutputStream encrypt(
            final @NotNull OutputStream output,
            final @NotNull SecretKey key,
            final byte @NotNull [] aad) throws IOException, GeneralSecurityException {

        // Generate random nonce
        final byte[] nonce = new byte[NONCE_SIZE];
        SECURE_RANDOM.nextBytes(nonce);

        // Write nonce first
        output.write(nonce);

        // Initialize cipher
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        final GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }

        return new CipherOutputStream(output, cipher);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method wraps the input stream with a decrypting stream that applies
     * AES-256-GCM decryption. The 12-byte nonce is read from the beginning of
     * the input stream, and the authentication tag is verified when the stream
     * reaches the end of the encrypted data.</p>
     *
     * @param input the underlying input stream to wrap; must not be {@code null};
     *              must contain encrypted data in the format produced by
     *              {@link #encrypt(OutputStream, SecretKey)}
     * @param key   the secret key for decryption; must not be {@code null};
     *              must be the same key used for encryption
     * @return a decrypting input stream that wraps the provided stream;
     *         never returns {@code null}; reading from this stream returns
     *         decrypted plaintext
     * @throws IOException if an I/O error occurs while reading the nonce,
     *         or if the nonce cannot be fully read (truncated input)
     * @throws GeneralSecurityException if cipher initialization fails
     *
     * @see #decrypt(InputStream, SecretKey, byte[])
     */
    @Override
    public @NotNull InputStream decrypt(
            final @NotNull InputStream input,
            final @NotNull SecretKey key) throws IOException, GeneralSecurityException {

        return decrypt(input, key, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method wraps the input stream with a decrypting stream that applies
     * AES-256-GCM decryption with AAD verification. The AAD must match exactly
     * what was provided during encryption, or the authentication tag verification
     * will fail.</p>
     *
     * @param input the underlying input stream containing encrypted data
     * @param key   the secret key for decryption
     * @param aad   additional authenticated data that must match the encryption AAD;
     *              may be {@code null} if no AAD was used during encryption
     * @return a decrypting input stream; never returns {@code null}
     * @throws IOException if an I/O error occurs while reading the nonce
     * @throws GeneralSecurityException if cipher initialization fails
     *
     * @see #encrypt(OutputStream, SecretKey, byte[])
     */
    @Override
    public @NotNull InputStream decrypt(
            final @NotNull InputStream input,
            final @NotNull SecretKey key,
            final byte @NotNull [] aad) throws IOException, GeneralSecurityException {

        // Read nonce
        final byte[] nonce = new byte[NONCE_SIZE];
        if (readFully(input, nonce) != NONCE_SIZE) {
            throw new IOException("Failed to read nonce");
        }

        // Initialize cipher
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        final GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, nonce);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }

        return new CipherInputStream(input, cipher);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method encrypts a complete byte array in a single operation.
     * It generates a random nonce, encrypts the data, and returns the
     * combined result. This is more efficient than streaming for small data.</p>
     *
     * @param plaintext the data to encrypt; must not be {@code null}; may be empty
     * @param key       the secret key for encryption; must be 32 bytes
     * @return the encrypted data including nonce and tag; the length is
     *         {@code plaintext.length + NONCE_SIZE + TAG_SIZE}; never returns {@code null}
     * @throws GeneralSecurityException if encryption fails
     *
     * @see #encryptBlock(byte[], SecretKey, byte[])
     * @see #decryptBlock(byte[], SecretKey)
     */
    @Override
    public byte @NotNull [] encryptBlock(
            final byte @NotNull [] plaintext,
            final @NotNull SecretKey key) throws GeneralSecurityException {

        return encryptBlock(plaintext, key, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method encrypts a complete byte array with additional authenticated
     * data in a single operation. The output format is:
     * {@code [12-byte nonce][encrypted data][16-byte tag]}</p>
     *
     * @param plaintext the data to encrypt; must not be {@code null}
     * @param key       the secret key for encryption; must be 32 bytes
     * @param aad       additional authenticated data; may be {@code null}
     * @return the encrypted data including nonce and tag; never returns {@code null}
     * @throws GeneralSecurityException if encryption fails
     *
     * @see #decryptBlock(byte[], SecretKey, byte[])
     */
    @Override
    public byte @NotNull [] encryptBlock(
            final byte @NotNull [] plaintext,
            final @NotNull SecretKey key,
            final byte @NotNull [] aad) throws GeneralSecurityException {

        // Generate random nonce
        final byte[] nonce = new byte[NONCE_SIZE];
        SECURE_RANDOM.nextBytes(nonce);

        // Initialize cipher
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        final GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }

        // Encrypt
        final byte[] ciphertext = cipher.doFinal(plaintext);

        // Combine: nonce + ciphertext (includes tag)
        final byte[] result = new byte[NONCE_SIZE + ciphertext.length];
        System.arraycopy(nonce, 0, result, 0, NONCE_SIZE);
        System.arraycopy(ciphertext, 0, result, NONCE_SIZE, ciphertext.length);

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method decrypts a complete byte array in a single operation.
     * It extracts the nonce from the beginning of the ciphertext, decrypts
     * the data, and verifies the authentication tag.</p>
     *
     * @param ciphertext the encrypted data including nonce and tag; must not be
     *                   {@code null}; must be at least {@code NONCE_SIZE + TAG_SIZE}
     *                   bytes (28 bytes minimum)
     * @param key        the secret key for decryption; must be the same key
     *                   used for encryption
     * @return the decrypted plaintext; never returns {@code null}
     * @throws GeneralSecurityException if decryption fails or authentication
     *         tag verification fails (indicating tampering or wrong key)
     * @throws IllegalArgumentException if the ciphertext is too short
     *
     * @see #decryptBlock(byte[], SecretKey, byte[])
     * @see #encryptBlock(byte[], SecretKey)
     */
    @Override
    public byte @NotNull [] decryptBlock(
            final byte @NotNull [] ciphertext,
            final @NotNull SecretKey key) throws GeneralSecurityException {

        return decryptBlock(ciphertext, key, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method decrypts a complete byte array with AAD verification
     * in a single operation. The AAD must match exactly what was provided
     * during encryption.</p>
     *
     * @param ciphertext the encrypted data including nonce and tag
     * @param key        the secret key for decryption
     * @param aad        additional authenticated data that must match encryption;
     *                   may be {@code null} if no AAD was used
     * @return the decrypted plaintext; never returns {@code null}
     * @throws GeneralSecurityException if decryption or authentication fails
     * @throws IllegalArgumentException if the ciphertext is too short
     *
     * @see #encryptBlock(byte[], SecretKey, byte[])
     */
    @Override
    public byte @NotNull [] decryptBlock(
            final byte @NotNull [] ciphertext,
            final @NotNull SecretKey key,
            final byte @NotNull [] aad) throws GeneralSecurityException {

        if (ciphertext.length < NONCE_SIZE + TAG_SIZE) {
            throw new IllegalArgumentException("Ciphertext too short");
        }

        // Extract nonce
        final byte[] nonce = new byte[NONCE_SIZE];
        System.arraycopy(ciphertext, 0, nonce, 0, NONCE_SIZE);

        // Initialize cipher
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        final GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, nonce);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }

        // Decrypt (includes tag verification)
        return cipher.doFinal(ciphertext, NONCE_SIZE, ciphertext.length - NONCE_SIZE);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method generates a cryptographically secure random 256-bit
     * AES key using the JCA {@link KeyGenerator}. The key is suitable
     * for use with all encryption methods in this class.</p>
     *
     * @return a new randomly generated 256-bit AES secret key;
     *         never returns {@code null}
     * @throws GeneralSecurityException if the AES algorithm is not available
     *         (should not occur on standard JVMs)
     *
     * @see #createKey(byte[])
     */
    @Override
    public @NotNull SecretKey generateKey() throws GeneralSecurityException {
        final KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE * 8, SECURE_RANDOM);
        return keyGen.generateKey();
    }

    /**
     * Creates a secret key from raw key bytes.
     *
     * <p>This method wraps existing key material into a {@link SecretKey}
     * object suitable for use with this provider. The key bytes must be
     * exactly 32 bytes (256 bits).</p>
     *
     * <p><strong>\:</strong></p>
     * <p>The key bytes should come from a secure source such as:</p>
     * <ul>
     *   <li>A key derivation function (e.g., {@link Argon2idKeyDerivation})</li>
     *   <li>A secure random number generator</li>
     *   <li>A hardware security module</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // From a KDF
     * byte[] keyBytes = kdf.deriveKey(password, salt, 32).getEncoded();
     * SecretKey key = Aes256GcmEncryptionProvider.createKey(keyBytes);
     *
     * // From secure random
     * byte[] keyBytes = new byte[32];
     * SecureRandom.getInstanceStrong().nextBytes(keyBytes);
     * SecretKey key = Aes256GcmEncryptionProvider.createKey(keyBytes);
     * }</pre>
     *
     * @param keyBytes the raw key bytes; must not be {@code null};
     *                 must be exactly 32 bytes (256 bits)
     * @return a new secret key wrapping the provided bytes; never returns {@code null}
     * @throws IllegalArgumentException if {@code keyBytes.length != 32}
     * @throws NullPointerException if {@code keyBytes} is {@code null}
     *
     * @see #generateKey()
     */
    public static @NotNull SecretKey createKey(final byte @NotNull [] keyBytes) {
        if (keyBytes.length != KEY_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid key size: expected " + KEY_SIZE + ", got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Reads exactly {@code buffer.length} bytes from the input stream.
     *
     * <p>This method blocks until the buffer is completely filled or
     * end-of-stream is reached. It handles partial reads by looping
     * until all requested bytes are read.</p>
     *
     * @param input  the input stream to read from; must not be {@code null}
     * @param buffer the buffer to fill with data; must not be {@code null};
     *               determines the number of bytes to read
     * @return the total number of bytes read; may be less than
     *         {@code buffer.length} if end-of-stream is reached
     * @throws IOException if an I/O error occurs during reading
     */
    private static int readFully(
            final @NotNull InputStream input,
            final byte @NotNull [] buffer) throws IOException {

        int offset = 0;
        int remaining = buffer.length;
        while (remaining > 0) {
            final int read = input.read(buffer, offset, remaining);
            if (read == -1) {
                return offset;
            }
            offset += read;
            remaining -= read;
        }
        return buffer.length;
    }

}
