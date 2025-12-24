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

package de.splatgames.aether.pack.core.pipeline;

import de.splatgames.aether.pack.core.spi.EncryptionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;

/**
 * Configuration for the {@link EncryptionStage} pipeline stage.
 *
 * <p>This record holds the encryption settings used when processing
 * data through the pipeline. It specifies which encryption algorithm
 * to use, the encryption key, and optionally Additional Authenticated
 * Data (AAD) for AEAD ciphers.</p>
 *
 * <h2>Supported Algorithms</h2>
 * <table>
 *   <caption>Encryption Algorithms</caption>
 *   <tr><th>Algorithm</th><th>Key Size</th><th>Nonce</th><th>Tag</th></tr>
 *   <tr><td>AES-256-GCM</td><td>32 bytes</td><td>12 bytes</td><td>16 bytes</td></tr>
 *   <tr><td>ChaCha20-Poly1305</td><td>32 bytes</td><td>12 bytes</td><td>16 bytes</td></tr>
 * </table>
 *
 * <h2>Additional Authenticated Data (AAD)</h2>
 * <p>AAD is data that is authenticated but not encrypted. It's useful
 * for binding ciphertext to context (e.g., entry name, chunk index).
 * Any modification to AAD will cause decryption to fail.</p>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Disable encryption
 * EncryptionConfig disabled = EncryptionConfig.DISABLED;
 *
 * // Basic encryption
 * EncryptionProvider aes = EncryptionRegistry.requireByName("aes-256-gcm");
 * SecretKey key = aes.generateKey();
 * EncryptionConfig config = EncryptionConfig.of(aes, key);
 *
 * // With AAD (binds ciphertext to entry name)
 * byte[] aad = "entry:config.json".getBytes(StandardCharsets.UTF_8);
 * EncryptionConfig withAad = EncryptionConfig.of(aes, key, aad);
 * }</pre>
 *
 * @param provider the encryption provider (null to disable encryption)
 * @param key      the encryption key (null to disable encryption)
 * @param aad      additional authenticated data (optional, may be null)
 *
 * @see EncryptionStage
 * @see EncryptionProvider
 * @see de.splatgames.aether.pack.core.format.FormatConstants#ENCRYPTION_AES_256_GCM
 * @see de.splatgames.aether.pack.core.format.FormatConstants#ENCRYPTION_CHACHA20_POLY1305
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record EncryptionConfig(
        @Nullable EncryptionProvider provider,
        @Nullable SecretKey key,
        byte @Nullable [] aad
) {

    /**
     * Pre-defined configuration that disables encryption.
     *
     * <p>Use this constant when creating entries that should not be encrypted.
     * This is more efficient than creating a new instance with null values.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Public metadata doesn't need encryption
     * if (entry.getName().equals("manifest.json")) {
     *     pipeline.addStage(new EncryptionStage(), EncryptionConfig.DISABLED);
     * }
     * }</pre>
     */
    public static final EncryptionConfig DISABLED = new EncryptionConfig(null, null, null);

    /**
     * Creates an encryption configuration without Additional Authenticated Data (AAD).
     *
     * <p>This factory method creates a basic encryption configuration that
     * encrypts data without binding it to additional context. The ciphertext
     * will be authenticated (tamper-proof) but not bound to any external data.</p>
     *
     * <p><strong>\:</strong></p>
     * <p>For maximum security, consider using {@link #of(EncryptionProvider, SecretKey, byte[])}
     * with AAD to bind the ciphertext to its context (e.g., entry name, chunk index).
     * This prevents ciphertext from being moved between different contexts.</p>
     *
     * @param provider the encryption provider to use; must not be {@code null};
     *                 determines the encryption algorithm (AES-256-GCM or
     *                 ChaCha20-Poly1305); the provider's key size requirements
     *                 must match the provided key
     * @param key      the encryption key; must not be {@code null}; must have
     *                 the correct length for the selected algorithm (32 bytes
     *                 for both AES-256-GCM and ChaCha20-Poly1305); the key
     *                 should be derived from a password using Argon2id or
     *                 PBKDF2, or generated securely
     * @return a new encryption configuration without AAD; never returns
     *         {@code null}; the returned configuration will have
     *         {@link #isEnabled()} return {@code true} and
     *         {@link #hasAad()} return {@code false}
     *
     * @see #of(EncryptionProvider, SecretKey, byte[])
     * @see EncryptionProvider#generateKey()
     */
    public static @NotNull EncryptionConfig of(
            final @NotNull EncryptionProvider provider,
            final @NotNull SecretKey key) {
        return new EncryptionConfig(provider, key, null);
    }

    /**
     * Creates an encryption configuration with Additional Authenticated Data (AAD).
     *
     * <p>This factory method creates an encryption configuration that binds
     * the ciphertext to additional context data. The AAD is authenticated
     * but not encrypted - any modification to the AAD will cause decryption
     * to fail with an authentication error.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li><strong>Entry binding</strong>: Include entry name to prevent
     *       moving encrypted data between entries</li>
     *   <li><strong>Chunk binding</strong>: Include chunk index to prevent
     *       reordering or swapping chunks</li>
     *   <li><strong>Version binding</strong>: Include format version to detect
     *       version mismatches</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Bind ciphertext to entry name
     * String entryName = "config.json";
     * byte[] aad = ("entry:" + entryName).getBytes(StandardCharsets.UTF_8);
     * EncryptionConfig config = EncryptionConfig.of(provider, key, aad);
     * }</pre>
     *
     * @param provider the encryption provider to use; must not be {@code null};
     *                 determines the encryption algorithm (AES-256-GCM or
     *                 ChaCha20-Poly1305); must support AEAD mode
     * @param key      the encryption key; must not be {@code null}; must have
     *                 the correct length for the selected algorithm (32 bytes
     *                 for both supported algorithms)
     * @param aad      the Additional Authenticated Data; must not be {@code null};
     *                 can be any byte array that provides context for the encryption;
     *                 this data is authenticated but NOT encrypted; the same AAD
     *                 must be provided during decryption or authentication will fail
     * @return a new encryption configuration with AAD; never returns {@code null};
     *         the returned configuration will have {@link #isEnabled()} return
     *         {@code true} and {@link #hasAad()} return {@code true}
     *
     * @see #of(EncryptionProvider, SecretKey)
     */
    public static @NotNull EncryptionConfig of(
            final @NotNull EncryptionProvider provider,
            final @NotNull SecretKey key,
            final byte @NotNull [] aad) {
        return new EncryptionConfig(provider, key, aad);
    }

    /**
     * Checks if encryption is enabled in this configuration.
     *
     * <p>Encryption is considered enabled when both a provider and a key
     * are specified (not null). When this method returns {@code false}, the
     * {@link EncryptionStage} will pass data through unchanged.</p>
     *
     * <p>This method is called by {@link EncryptionStage#isEnabled} to
     * determine whether the stage should be applied to the data stream.</p>
     *
     * @return {@code true} if both an encryption provider and key are configured
     *         and encryption should be applied to data flowing through the
     *         pipeline; {@code false} if either the provider or key is
     *         {@code null} and data should pass through unencrypted
     *
     * @see EncryptionStage#isEnabled(EncryptionConfig)
     * @see #DISABLED
     */
    public boolean isEnabled() {
        return this.provider != null && this.key != null;
    }

    /**
     * Checks if Additional Authenticated Data (AAD) is provided.
     *
     * <p>AAD is optional context data that is authenticated but not encrypted.
     * When AAD is present, the same AAD must be provided during both encryption
     * and decryption, or the decryption will fail with an authentication error.</p>
     *
     * <p>This method is used by the {@link EncryptionStage} to determine
     * whether to call the AAD-enabled encryption/decryption methods.</p>
     *
     * @return {@code true} if AAD is configured and has at least one byte;
     *         {@code false} if AAD is {@code null} or an empty array; when
     *         {@code true}, the AAD will be used during encryption/decryption
     *
     * @see #of(EncryptionProvider, SecretKey, byte[])
     */
    public boolean hasAad() {
        return this.aad != null && this.aad.length > 0;
    }

}
