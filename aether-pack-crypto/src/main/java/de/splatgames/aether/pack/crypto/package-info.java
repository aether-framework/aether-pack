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
 * Encryption providers and cryptographic utilities for APACK archives.
 *
 * <p>This package provides a complete cryptographic subsystem for protecting
 * archive contents with password-based encryption. It implements industry-standard
 * algorithms following current best practices (OWASP 2024, NIST recommendations).</p>
 *
 * <h2>Package Overview</h2>
 *
 * <table>
 *   <caption>Cryptographic Components</caption>
 *   <tr><th>Component</th><th>Purpose</th><th>Recommended</th></tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.crypto.EncryptionRegistry}</td>
 *     <td>Central registry for encryption algorithms</td>
 *     <td>Entry point for providers</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.crypto.Aes256GcmEncryptionProvider}</td>
 *     <td>AES-256-GCM authenticated encryption</td>
 *     <td>Hardware-accelerated systems</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.crypto.ChaCha20Poly1305EncryptionProvider}</td>
 *     <td>ChaCha20-Poly1305 authenticated encryption</td>
 *     <td>Software-only systems</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.crypto.Argon2idKeyDerivation}</td>
 *     <td>Password-based key derivation</td>
 *     <td><strong>Preferred</strong> for all new archives</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.crypto.Pbkdf2KeyDerivation}</td>
 *     <td>Password-based key derivation</td>
 *     <td>Fallback when Argon2 unavailable</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.crypto.KeyWrapper}</td>
 *     <td>AES Key Wrap (RFC 3394)</td>
 *     <td>Protects content encryption keys</td>
 *   </tr>
 * </table>
 *
 * <h2>Encryption Algorithms</h2>
 *
 * <p>Both encryption providers implement AEAD (Authenticated Encryption with
 * Associated Data), which provides both confidentiality and integrity protection.
 * Any tampering with the ciphertext is detected during decryption.</p>
 *
 * <h3>AES-256-GCM</h3>
 * <p>The industry-standard choice, using hardware acceleration (AES-NI) when
 * available. Provides excellent performance on modern CPUs. Use
 * {@link de.splatgames.aether.pack.crypto.EncryptionRegistry#aes256Gcm()}.</p>
 *
 * <h3>ChaCha20-Poly1305</h3>
 * <p>A modern alternative that is faster than AES in pure software implementations.
 * Ideal for systems without hardware AES acceleration. Use
 * {@link de.splatgames.aether.pack.crypto.EncryptionRegistry#chaCha20Poly1305()}.</p>
 *
 * <h2>Key Derivation Functions</h2>
 *
 * <p>Key derivation functions transform low-entropy passwords into high-entropy
 * cryptographic keys. They are intentionally slow to resist brute-force attacks.</p>
 *
 * <h3>Argon2id (Recommended)</h3>
 * <p>Winner of the Password Hashing Competition (2015). Memory-hard design
 * makes GPU/ASIC attacks expensive. Default parameters follow OWASP 2024
 * recommendations: 64 MiB memory, 3 iterations, 4 threads.</p>
 *
 * <h3>PBKDF2-HMAC-SHA256 (Fallback)</h3>
 * <p>Widely-supported fallback for environments where Argon2 is unavailable.
 * Default 600,000 iterations per OWASP 2024 recommendations. Not memory-hard,
 * so more vulnerable to GPU attacks than Argon2.</p>
 *
 * <h2>Password-Based Encryption Workflow</h2>
 *
 * <p>The recommended workflow separates the content encryption key (CEK) from
 * the password-derived key encryption key (KEK):</p>
 *
 * <h3>Encryption</h3>
 * <pre>{@code
 * // 1. Setup key derivation
 * KeyDerivation kdf = new Argon2idKeyDerivation();
 * byte[] salt = kdf.generateSalt();
 * byte[] kdfParams = kdf.getParameters();
 *
 * // 2. Generate and wrap content encryption key
 * SecretKey cek = KeyWrapper.generateAes256Key();
 * byte[] wrappedKey = KeyWrapper.wrapWithPassword(cek, password, salt, kdf);
 *
 * // 3. Encrypt content
 * EncryptionProvider provider = EncryptionRegistry.aes256Gcm();
 * OutputStream encrypted = provider.encrypt(output, cek);
 * encrypted.write(plaintext);
 * encrypted.close();
 *
 * // 4. Store: salt, kdfParams, wrappedKey, encryptedContent
 * }</pre>
 *
 * <h3>Decryption</h3>
 * <pre>{@code
 * // 1. Recreate KDF from stored parameters
 * KeyDerivation kdf = Argon2idKeyDerivation.fromParameters(kdfParams);
 *
 * // 2. Unwrap content encryption key
 * SecretKey cek = KeyWrapper.unwrapWithPassword(wrappedKey, password, salt, kdf, "AES");
 *
 * // 3. Decrypt content
 * EncryptionProvider provider = EncryptionRegistry.aes256Gcm();
 * InputStream decrypted = provider.decrypt(input, cek);
 * byte[] plaintext = decrypted.readAllBytes();
 * }</pre>
 *
 * <h2>Security Considerations</h2>
 *
 * <ul>
 *   <li><strong>Password handling:</strong> Use {@code char[]} instead of
 *       {@code String} and clear arrays after use with {@code Arrays.fill()}</li>
 *   <li><strong>Nonce uniqueness:</strong> Never reuse a nonce with the same key.
 *       The encryption providers handle nonce generation automatically.</li>
 *   <li><strong>Salt storage:</strong> Salts are not secret but must be stored
 *       with the encrypted data for decryption.</li>
 *   <li><strong>Algorithm selection:</strong> Store the algorithm identifier
 *       (string or numeric ID) with encrypted data to ensure correct decryption.</li>
 *   <li><strong>KDF parameters:</strong> Store KDF parameters to enable future
 *       decryption. Don't hardcode parameters.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>All classes in this package are thread-safe. Encryption providers and
 * KDF instances can be shared across threads. The {@link de.splatgames.aether.pack.crypto.EncryptionRegistry}
 * uses concurrent collections for safe provider registration and lookup.</p>
 *
 * <h2>Dependencies</h2>
 *
 * <ul>
 *   <li><strong>BouncyCastle:</strong> Required for ChaCha20-Poly1305 and Argon2id</li>
 *   <li><strong>JCA:</strong> AES-GCM and PBKDF2 use standard Java Cryptography Architecture</li>
 * </ul>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 *
 * @see de.splatgames.aether.pack.crypto.EncryptionRegistry
 * @see de.splatgames.aether.pack.crypto.KeyDerivation
 * @see de.splatgames.aether.pack.crypto.KeyWrapper
 * @see de.splatgames.aether.pack.core.spi.EncryptionProvider
 */
package de.splatgames.aether.pack.crypto;
