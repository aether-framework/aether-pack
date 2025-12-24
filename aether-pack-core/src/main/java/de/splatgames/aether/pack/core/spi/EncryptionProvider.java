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

package de.splatgames.aether.pack.core.spi;

import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

/**
 * Service Provider Interface for encryption algorithms in APACK archives.
 *
 * <p>This interface defines the contract for pluggable encryption providers.
 * All implementations must use Authenticated Encryption with Associated Data
 * (AEAD) to provide both confidentiality and integrity.</p>
 *
 * <h2>Built-in Implementations</h2>
 * <p>The {@code aether-pack-crypto} module provides:</p>
 * <ul>
 *   <li><strong>AES-256-GCM</strong> - Industry standard, hardware-accelerated on modern CPUs</li>
 *   <li><strong>ChaCha20-Poly1305</strong> - High performance in software, constant-time</li>
 * </ul>
 *
 * <h2>AEAD Properties</h2>
 * <p>All encryption providers implement AEAD which provides:</p>
 * <ul>
 *   <li><strong>Confidentiality</strong> - Data is encrypted and unreadable without the key</li>
 *   <li><strong>Integrity</strong> - Any tampering with ciphertext is detected</li>
 *   <li><strong>Authenticity</strong> - Origin of data can be verified</li>
 * </ul>
 *
 * <h2>Implementation Requirements</h2>
 * <ul>
 *   <li>Implementations <strong>must be thread-safe</strong></li>
 *   <li>Implementations <strong>must be stateless</strong></li>
 *   <li>A unique random nonce/IV must be generated for each encryption operation</li>
 *   <li>The nonce must be prepended to the ciphertext output</li>
 *   <li>The authentication tag must be appended to the ciphertext</li>
 * </ul>
 *
 * <h2>Data Format</h2>
 * <p>Encrypted data has the following structure:</p>
 * <pre>
 * +-------+------------+-----+
 * | Nonce | Ciphertext | Tag |
 * +-------+------------+-----+
 * </pre>
 * <p>Where:</p>
 * <ul>
 *   <li>Nonce: 12 bytes (random, unique per encryption)</li>
 *   <li>Ciphertext: Same length as plaintext</li>
 *   <li>Tag: 16 bytes (authentication tag)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Block encryption
 * EncryptionProvider aes = EncryptionRegistry.requireByName("aes-256-gcm");
 * SecretKey key = aes.generateKey();
 *
 * byte[] encrypted = aes.encryptBlock(plaintext, key);
 * byte[] decrypted = aes.decryptBlock(encrypted, key);
 *
 * // With Additional Authenticated Data
 * byte[] aad = "entry-header".getBytes(StandardCharsets.UTF_8);
 * byte[] encryptedWithAad = aes.encryptBlock(plaintext, key, aad);
 * byte[] decryptedWithAad = aes.decryptBlock(encryptedWithAad, key, aad);
 *
 * // Stream encryption
 * try (OutputStream out = aes.encrypt(fileOut, key)) {
 *     out.write(data);
 * }
 * }</pre>
 *
 * @see CompressionProvider
 * @see ChecksumProvider
 * @see de.splatgames.aether.pack.core.io.ChunkProcessor
 * @see de.splatgames.aether.pack.core.format.FormatConstants#ENCRYPTION_AES_256_GCM
 * @see de.splatgames.aether.pack.core.format.FormatConstants#ENCRYPTION_CHACHA20_POLY1305
 * @see de.splatgames.aether.pack.core.format.EncryptionBlock
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface EncryptionProvider {

    /**
     * Returns the unique string identifier for this encryption algorithm.
     *
     * <p>The identifier is a human-readable string that uniquely identifies the
     * encryption algorithm implementation. This identifier is used for configuration,
     * logging, and to look up providers in registries.</p>
     *
     * <p><strong>:</strong></p>
     * <p>Identifiers should be lowercase with hyphens and include key size:</p>
     * <ul>
     *   <li>{@code "aes-256-gcm"} - AES with 256-bit key in GCM mode</li>
     *   <li>{@code "chacha20-poly1305"} - ChaCha20 with Poly1305 MAC</li>
     * </ul>
     *
     * <p><strong>:</strong></p>
     * <p>Each provider implementation must return a unique identifier. Registering
     * multiple providers with the same ID will cause conflicts.</p>
     *
     * @return the unique algorithm identifier string; never {@code null};
     *         typically lowercase with hyphens (e.g., "aes-256-gcm")
     *
     * @see #getNumericId()
     */
    @NotNull String getId();

    /**
     * Returns the numeric identifier for this encryption algorithm.
     *
     * <p>The numeric ID is stored in the binary archive format to identify
     * which encryption algorithm was used. When reading an archive, this ID
     * is used to look up the appropriate provider for decryption.</p>
     *
     * <p><strong>:</strong></p>
     * <table>
     *   <caption>Reserved Encryption Algorithm IDs</caption>
     *   <tr><th>ID</th><th>Algorithm</th><th>Constant</th></tr>
     *   <tr><td>0</td><td>None</td><td>{@link de.splatgames.aether.pack.core.format.FormatConstants#ENCRYPTION_NONE}</td></tr>
     *   <tr><td>1</td><td>AES-256-GCM</td><td>{@link de.splatgames.aether.pack.core.format.FormatConstants#ENCRYPTION_AES_256_GCM}</td></tr>
     *   <tr><td>2</td><td>ChaCha20-Poly1305</td><td>{@link de.splatgames.aether.pack.core.format.FormatConstants#ENCRYPTION_CHACHA20_POLY1305}</td></tr>
     * </table>
     *
     * <p><strong>:</strong></p>
     * <p>Custom implementations should use IDs >= 128 to avoid conflicts with
     * future standard algorithm assignments.</p>
     *
     * @return the numeric algorithm identifier; standard algorithms use values
     *         0-127; custom implementations should use 128+
     *
     * @see #getId()
     * @see de.splatgames.aether.pack.core.format.FormatConstants#ENCRYPTION_AES_256_GCM
     * @see de.splatgames.aether.pack.core.format.FormatConstants#ENCRYPTION_CHACHA20_POLY1305
     */
    int getNumericId();

    /**
     * Returns the required encryption key size in bytes.
     *
     * <p>This is the size of the symmetric key that must be provided for
     * encryption and decryption operations. Keys of incorrect size will
     * cause operations to fail.</p>
     *
     * <p><strong>:</strong></p>
     * <table>
     *   <caption>Key Sizes by Algorithm</caption>
     *   <tr><th>Algorithm</th><th>Key Size</th><th>Security Level</th></tr>
     *   <tr><td>AES-256-GCM</td><td>32 bytes (256 bits)</td><td>256-bit</td></tr>
     *   <tr><td>ChaCha20-Poly1305</td><td>32 bytes (256 bits)</td><td>256-bit</td></tr>
     * </table>
     *
     * <p><strong>:</strong></p>
     * <p>Keys should be derived from passwords using a secure KDF like
     * Argon2id or PBKDF2, not used directly. Use {@link #generateKey()}
     * to create a random key.</p>
     *
     * @return the key size in bytes; typically 32 for 256-bit security
     *
     * @see #generateKey()
     * @see #encryptBlock(byte[], SecretKey)
     */
    int getKeySize();

    /**
     * Returns the nonce (Number used ONCE) size in bytes.
     *
     * <p>The nonce is a unique value that must be used exactly once with any
     * given key. It is prepended to the ciphertext during encryption and
     * read during decryption. Reusing a nonce with the same key is a
     * critical security vulnerability.</p>
     *
     * <p><strong>:</strong></p>
     * <table>
     *   <caption>Nonce Sizes by Algorithm</caption>
     *   <tr><th>Algorithm</th><th>Nonce Size</th><th>Notes</th></tr>
     *   <tr><td>AES-256-GCM</td><td>12 bytes (96 bits)</td><td>Recommended size for GCM</td></tr>
     *   <tr><td>ChaCha20-Poly1305</td><td>12 bytes (96 bits)</td><td>Standard size</td></tr>
     * </table>
     *
     * <p><strong>:</strong></p>
     * <p>Implementations must generate cryptographically random nonces
     * for each encryption operation. With 96-bit random nonces, the
     * collision probability is negligible for practical use.</p>
     *
     * @return the nonce size in bytes; typically 12 (96 bits)
     *
     * @see #encryptBlock(byte[], SecretKey)
     * @see #encryptedSize(int)
     */
    int getNonceSize();

    /**
     * Returns the authentication tag size in bytes.
     *
     * <p>The authentication tag provides integrity and authenticity verification.
     * It is computed during encryption and appended to the ciphertext. During
     * decryption, the tag is verified and any tampering is detected.</p>
     *
     * <p><strong>:</strong></p>
     * <table>
     *   <caption>Tag Sizes by Algorithm</caption>
     *   <tr><th>Algorithm</th><th>Tag Size</th><th>Security</th></tr>
     *   <tr><td>AES-256-GCM</td><td>16 bytes (128 bits)</td><td>Full security</td></tr>
     *   <tr><td>ChaCha20-Poly1305</td><td>16 bytes (128 bits)</td><td>Full security</td></tr>
     * </table>
     *
     * <p><strong>:</strong></p>
     * <p>If the tag verification fails during decryption, a
     * {@link GeneralSecurityException} is thrown. This indicates either
     * data corruption or a tampering attempt.</p>
     *
     * @return the tag size in bytes; typically 16 (128 bits) for full security
     *
     * @see #decryptBlock(byte[], SecretKey)
     * @see #encryptedSize(int)
     */
    int getTagSize();

    /**
     * Creates an encrypting output stream that wraps the given output stream.
     *
     * <p>Data written to the returned stream is encrypted before being written
     * to the underlying output stream. The stream automatically handles nonce
     * generation and tag computation.</p>
     *
     * <p><strong>:</strong></p>
     * <p>The encrypted output has the structure:</p>
     * <pre>
     * [Nonce (12 bytes)] [Ciphertext (n bytes)] [Tag (16 bytes)]
     * </pre>
     *
     * <p><strong>:</strong></p>
     * <ol>
     *   <li>Nonce is written immediately when stream is created</li>
     *   <li>Data is encrypted as it is written</li>
     *   <li>Tag is written when stream is closed</li>
     * </ol>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * try (OutputStream encrypted = provider.encrypt(outputStream, key)) {
     *     encrypted.write(sensitiveData);
     * }  // Tag is written and stream is finalized
     * }</pre>
     *
     * @param output the underlying output stream to write encrypted data to;
     *               must not be {@code null}
     * @param key    the encryption key; must have the correct size as returned
     *               by {@link #getKeySize()}
     * @return a new output stream that encrypts data before writing;
     *         never {@code null}; must be closed to finalize encryption
     * @throws IOException if an I/O error occurs during stream creation
     * @throws GeneralSecurityException if the key is invalid or encryption
     *                                  setup fails
     *
     * @see #encrypt(OutputStream, SecretKey, byte[])
     * @see #decrypt(InputStream, SecretKey)
     */
    @NotNull OutputStream encrypt(
            final @NotNull OutputStream output,
            final @NotNull SecretKey key) throws IOException, GeneralSecurityException;

    /**
     * Creates an encrypting output stream with Additional Authenticated Data (AAD).
     *
     * <p>AAD is data that is authenticated but not encrypted. This is useful for
     * binding the ciphertext to contextual information (like headers, entry IDs,
     * or version numbers) without revealing that information in the ciphertext.</p>
     *
     * <p><strong>:</strong></p>
     * <ul>
     *   <li><strong>Not encrypted</strong> - AAD remains in plaintext</li>
     *   <li><strong>Authenticated</strong> - Any change to AAD causes decryption to fail</li>
     *   <li><strong>Not stored</strong> - AAD must be provided again during decryption</li>
     * </ul>
     *
     * <p><strong>:</strong></p>
     * <ul>
     *   <li>Binding ciphertext to entry headers</li>
     *   <li>Including version or algorithm identifiers</li>
     *   <li>Adding context that must match during decryption</li>
     * </ul>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * byte[] aad = headerBytes;  // Entry header as AAD
     * try (OutputStream encrypted = provider.encrypt(outputStream, key, aad)) {
     *     encrypted.write(entryData);
     * }
     * }</pre>
     *
     * @param output the underlying output stream to write encrypted data to;
     *               must not be {@code null}
     * @param key    the encryption key; must have the correct size
     * @param aad    additional authenticated data; must not be {@code null};
     *               can be empty; must be provided identically during decryption
     * @return a new output stream that encrypts data before writing;
     *         never {@code null}
     * @throws IOException if an I/O error occurs during stream creation
     * @throws GeneralSecurityException if the key is invalid or encryption
     *                                  setup fails
     *
     * @see #encrypt(OutputStream, SecretKey)
     * @see #decrypt(InputStream, SecretKey, byte[])
     */
    @NotNull OutputStream encrypt(
            final @NotNull OutputStream output,
            final @NotNull SecretKey key,
            final byte @NotNull [] aad) throws IOException, GeneralSecurityException;

    /**
     * Creates a decrypting input stream that wraps the given input stream.
     *
     * <p>Data read from the returned stream is decrypted from the encrypted
     * data in the underlying input stream. The stream expects the nonce at
     * the beginning and verifies the tag at the end.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>
     * [Nonce (12 bytes)] [Ciphertext (n bytes)] [Tag (16 bytes)]
     * </pre>
     *
     * <p><strong>:</strong></p>
     * <p>The authentication tag is verified when the stream is fully read
     * or closed. If verification fails, a {@link GeneralSecurityException}
     * is thrown, indicating tampering or corruption.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * try (InputStream decrypted = provider.decrypt(inputStream, key)) {
     *     byte[] plaintext = decrypted.readAllBytes();
     * }
     * }</pre>
     *
     * @param input the underlying input stream containing encrypted data;
     *              must not be {@code null}; must be positioned at the nonce
     * @param key   the decryption key; must match the encryption key
     * @return a new input stream that decrypts data while reading;
     *         never {@code null}
     * @throws IOException if an I/O error occurs during stream creation
     * @throws GeneralSecurityException if the key is invalid or decryption
     *                                  setup fails
     *
     * @see #decrypt(InputStream, SecretKey, byte[])
     * @see #encrypt(OutputStream, SecretKey)
     */
    @NotNull InputStream decrypt(
            final @NotNull InputStream input,
            final @NotNull SecretKey key) throws IOException, GeneralSecurityException;

    /**
     * Creates a decrypting input stream with Additional Authenticated Data (AAD).
     *
     * <p>The AAD provided must exactly match the AAD used during encryption.
     * Any difference will cause authentication to fail and a
     * {@link GeneralSecurityException} to be thrown.</p>
     *
     * <p><strong>:</strong></p>
     * <p>The AAD comparison is done during tag verification:</p>
     * <ul>
     *   <li>Same AAD → Decryption succeeds</li>
     *   <li>Different AAD → Authentication fails</li>
     *   <li>Empty AAD when original had AAD → Authentication fails</li>
     * </ul>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * byte[] aad = headerBytes;  // Must match encryption AAD
     * try (InputStream decrypted = provider.decrypt(inputStream, key, aad)) {
     *     byte[] plaintext = decrypted.readAllBytes();
     * }
     * }</pre>
     *
     * @param input the underlying input stream containing encrypted data;
     *              must not be {@code null}
     * @param key   the decryption key; must match the encryption key
     * @param aad   additional authenticated data; must exactly match the AAD
     *              used during encryption; must not be {@code null}
     * @return a new input stream that decrypts data while reading;
     *         never {@code null}
     * @throws IOException if an I/O error occurs during stream creation
     * @throws GeneralSecurityException if the key is invalid or decryption
     *                                  setup fails
     *
     * @see #decrypt(InputStream, SecretKey)
     * @see #encrypt(OutputStream, SecretKey, byte[])
     */
    @NotNull InputStream decrypt(
            final @NotNull InputStream input,
            final @NotNull SecretKey key,
            final byte @NotNull [] aad) throws IOException, GeneralSecurityException;

    /**
     * Encrypts a byte array in memory.
     *
     * <p>This method performs block encryption, where the entire plaintext is
     * encrypted in a single operation. The result includes the nonce prefix
     * and authentication tag suffix.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>
     * Output: [Nonce (12 bytes)] [Ciphertext (n bytes)] [Tag (16 bytes)]
     * Total size: plaintext.length + {@link #getNonceSize()} + {@link #getTagSize()}
     * </pre>
     *
     * <p><strong>:</strong></p>
     * <p>A new random nonce is generated for each call. Never encrypt two
     * different plaintexts with the same nonce and key.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * byte[] plaintext = "Secret message".getBytes(StandardCharsets.UTF_8);
     * byte[] encrypted = provider.encryptBlock(plaintext, key);
     * // encrypted.length == plaintext.length + 12 + 16 = plaintext.length + 28
     * }</pre>
     *
     * @param plaintext the data to encrypt; must not be {@code null}; can be empty
     * @param key       the encryption key; must have correct size
     * @return a new byte array containing [nonce + ciphertext + tag];
     *         never {@code null}; length equals
     *         {@code plaintext.length + getNonceSize() + getTagSize()}
     * @throws GeneralSecurityException if encryption fails due to invalid key
     *                                  or algorithm error
     *
     * @see #encryptBlock(byte[], SecretKey, byte[])
     * @see #decryptBlock(byte[], SecretKey)
     * @see #encryptedSize(int)
     */
    byte @NotNull [] encryptBlock(
            final byte @NotNull [] plaintext,
            final @NotNull SecretKey key) throws GeneralSecurityException;

    /**
     * Encrypts a byte array in memory with Additional Authenticated Data (AAD).
     *
     * <p>AAD is authenticated but not encrypted. It must be provided identically
     * during decryption or authentication will fail.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>
     * Output: [Nonce (12 bytes)] [Ciphertext (n bytes)] [Tag (16 bytes)]
     * Note: AAD is NOT included in output, only used for authentication
     * </pre>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * byte[] plaintext = sensitiveData;
     * byte[] aad = "entry-id:12345".getBytes(StandardCharsets.UTF_8);
     * byte[] encrypted = provider.encryptBlock(plaintext, key, aad);
     * }</pre>
     *
     * @param plaintext the data to encrypt; must not be {@code null}; can be empty
     * @param key       the encryption key; must have correct size
     * @param aad       additional authenticated data; must not be {@code null};
     *                  can be empty; must be provided during decryption
     * @return a new byte array containing [nonce + ciphertext + tag];
     *         never {@code null}
     * @throws GeneralSecurityException if encryption fails
     *
     * @see #encryptBlock(byte[], SecretKey)
     * @see #decryptBlock(byte[], SecretKey, byte[])
     */
    byte @NotNull [] encryptBlock(
            final byte @NotNull [] plaintext,
            final @NotNull SecretKey key,
            final byte @NotNull [] aad) throws GeneralSecurityException;

    /**
     * Decrypts a byte array in memory.
     *
     * <p>This method decrypts data that was encrypted with
     * {@link #encryptBlock(byte[], SecretKey)}. The input must have the
     * expected structure with nonce prefix and tag suffix.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>
     * Input: [Nonce (12 bytes)] [Ciphertext (n bytes)] [Tag (16 bytes)]
     * Minimum size: {@link #getNonceSize()} + {@link #getTagSize()} = 28 bytes
     * </pre>
     *
     * <p><strong>:</strong></p>
     * <p>The authentication tag is verified before returning the plaintext.
     * If verification fails, a {@link GeneralSecurityException} is thrown
     * and no plaintext data is returned. This prevents oracle attacks.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * try {
     *     byte[] plaintext = provider.decryptBlock(encrypted, key);
     *     // Use decrypted data
     * } catch (GeneralSecurityException e) {
     *     // Authentication failed - data corrupted or tampered
     *     throw new SecurityException("Decryption failed: " + e.getMessage());
     * }
     * }</pre>
     *
     * @param ciphertext the encrypted data containing [nonce + ciphertext + tag];
     *                   must not be {@code null}; minimum size is 28 bytes
     * @param key        the decryption key; must match the encryption key
     * @return a new byte array containing the decrypted plaintext;
     *         never {@code null}; length equals
     *         {@code ciphertext.length - getNonceSize() - getTagSize()}
     * @throws GeneralSecurityException if decryption fails due to invalid key,
     *                                  corrupted data, or authentication failure
     *
     * @see #decryptBlock(byte[], SecretKey, byte[])
     * @see #encryptBlock(byte[], SecretKey)
     */
    byte @NotNull [] decryptBlock(
            final byte @NotNull [] ciphertext,
            final @NotNull SecretKey key) throws GeneralSecurityException;

    /**
     * Decrypts a byte array in memory with Additional Authenticated Data (AAD).
     *
     * <p>The AAD must exactly match what was used during encryption.
     * Any mismatch will cause authentication to fail.</p>
     *
     * <p><strong>:</strong></p>
     * <p>The AAD is included in the authentication tag computation.
     * During decryption, the same AAD must be provided or the tag
     * verification will fail, even if the ciphertext is correct.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * byte[] aad = "entry-id:12345".getBytes(StandardCharsets.UTF_8);
     * try {
     *     byte[] plaintext = provider.decryptBlock(encrypted, key, aad);
     * } catch (GeneralSecurityException e) {
     *     // Wrong key, wrong AAD, or corrupted ciphertext
     * }
     * }</pre>
     *
     * @param ciphertext the encrypted data containing [nonce + ciphertext + tag];
     *                   must not be {@code null}
     * @param key        the decryption key; must match the encryption key
     * @param aad        additional authenticated data; must exactly match the
     *                   AAD used during encryption; must not be {@code null}
     * @return a new byte array containing the decrypted plaintext;
     *         never {@code null}
     * @throws GeneralSecurityException if decryption fails due to invalid key,
     *                                  wrong AAD, corrupted data, or auth failure
     *
     * @see #decryptBlock(byte[], SecretKey)
     * @see #encryptBlock(byte[], SecretKey, byte[])
     */
    byte @NotNull [] decryptBlock(
            final byte @NotNull [] ciphertext,
            final @NotNull SecretKey key,
            final byte @NotNull [] aad) throws GeneralSecurityException;

    /**
     * Calculates the total encrypted size for a given plaintext size.
     *
     * <p>This method computes the exact size of the encrypted output,
     * including the nonce prefix and authentication tag suffix. This is
     * useful for buffer pre-allocation and size calculations.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>
     * encryptedSize = nonceSize + plaintextSize + tagSize
     *               = 12 + plaintextSize + 16
     *               = plaintextSize + 28 bytes
     * </pre>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * int plainSize = originalData.length;
     * int encSize = provider.encryptedSize(plainSize);
     *
     * ByteBuffer buffer = ByteBuffer.allocate(encSize);
     * byte[] encrypted = provider.encryptBlock(originalData, key);
     * assert encrypted.length == encSize;
     * }</pre>
     *
     * @param plaintextSize the size of the plaintext data in bytes; must be
     *                      non-negative
     * @return the total encrypted size including nonce and tag; always
     *         greater than plaintextSize by exactly
     *         ({@link #getNonceSize()} + {@link #getTagSize()}) bytes
     *
     * @see #getNonceSize()
     * @see #getTagSize()
     * @see #encryptBlock(byte[], SecretKey)
     */
    default int encryptedSize(final int plaintextSize) {
        return getNonceSize() + plaintextSize + getTagSize();
    }

    /**
     * Generates a new cryptographically random key suitable for this cipher.
     *
     * <p>This method creates a fresh random key using a secure random number
     * generator. The generated key has the correct size for this encryption
     * algorithm ({@link #getKeySize()} bytes).</p>
     *
     * <p><strong>:</strong></p>
     * <ul>
     *   <li>Keys are generated using {@link java.security.SecureRandom}</li>
     *   <li>Generated keys have full entropy (no password derivation)</li>
     *   <li>Keys should be stored securely (keystore, encrypted file, etc.)</li>
     * </ul>
     *
     * <p><strong>:</strong></p>
     * <p>For keys derived from passwords, use a Key Derivation Function (KDF)
     * like Argon2id or PBKDF2 instead of this method. The KDF output can then
     * be wrapped in a {@link SecretKey} for use with this provider.</p>
     *
     * <p><strong>:</strong></p>
     * <pre>{@code
     * // Generate a new random key
     * SecretKey key = provider.generateKey();
     *
     * // Use for encryption
     * byte[] encrypted = provider.encryptBlock(data, key);
     *
     * // Store key securely for later decryption
     * byte[] keyBytes = key.getEncoded();
     * secureKeyStore.store("mykey", keyBytes);
     * }</pre>
     *
     * @return a new randomly generated secret key with the correct size for
     *         this algorithm; never {@code null}; the key is suitable for
     *         immediate use with encrypt/decrypt methods
     * @throws GeneralSecurityException if key generation fails due to
     *                                  missing algorithm support or random
     *                                  number generator failure
     *
     * @see #getKeySize()
     * @see #encryptBlock(byte[], SecretKey)
     */
    @NotNull SecretKey generateKey() throws GeneralSecurityException;

}
