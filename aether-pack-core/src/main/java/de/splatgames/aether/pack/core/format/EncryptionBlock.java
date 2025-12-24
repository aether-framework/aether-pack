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

package de.splatgames.aether.pack.core.format;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Represents the encryption metadata block in an encrypted APACK archive.
 *
 * <p>When an APACK archive has encryption enabled (indicated by
 * {@link FileHeader#isEncrypted()}), an encryption block is written
 * immediately after the file header. This block contains all the
 * information needed to derive the encryption key from a password
 * and decrypt the archive data.</p>
 *
 * <h2>Encryption Architecture</h2>
 * <p>APACK uses a two-tier key hierarchy:</p>
 * <ol>
 *   <li><strong>Key Encryption Key (KEK)</strong>: Derived from password using KDF</li>
 *   <li><strong>Data Encryption Key (DEK)</strong>: Random key wrapped with KEK</li>
 * </ol>
 * <p>This allows password changes without re-encrypting all data.</p>
 *
 * <h2>Binary Layout</h2>
 * <p>The encryption block has the following structure (Little-Endian byte order):</p>
 * <pre>
 * Offset  Size    Field              Description
 * ──────────────────────────────────────────────────────────────────
 * 0x00    4       magic              "ENCR" (ASCII)
 * 0x04    1       kdfAlgorithmId     KDF algorithm (0=Argon2id, 1=PBKDF2)
 * 0x05    1       cipherAlgorithmId  Cipher (1=AES-256-GCM, 2=ChaCha20)
 * 0x06    2       reserved           Reserved for future use
 * 0x08    4       kdfIterations      Iterations/time cost
 * 0x0C    4       kdfMemory          Memory cost in KB (Argon2 only)
 * 0x10    4       kdfParallelism     Parallelism factor (Argon2 only)
 * 0x14    2       saltLength         Length of salt in bytes
 * 0x16    2       wrappedKeyLength   Length of wrapped key in bytes
 * 0x18    N       salt               KDF salt (typically 32 bytes)
 * 0x18+N  32      wrappedKey         Wrapped DEK (32 bytes for 256-bit key)
 * ...     16      wrappedKeyTag      AEAD authentication tag
 * ──────────────────────────────────────────────────────────────────
 * </pre>
 *
 * <h2>Supported Algorithms</h2>
 * <h3>Key Derivation Functions (KDF)</h3>
 * <ul>
 *   <li>{@link FormatConstants#KDF_ARGON2ID} (0) - Argon2id (recommended)</li>
 *   <li>{@link FormatConstants#KDF_PBKDF2_SHA256} (1) - PBKDF2-HMAC-SHA256</li>
 * </ul>
 *
 * <h3>Cipher Algorithms</h3>
 * <ul>
 *   <li>{@link FormatConstants#ENCRYPTION_AES_256_GCM} (1) - AES-256-GCM</li>
 *   <li>{@link FormatConstants#ENCRYPTION_CHACHA20_POLY1305} (2) - ChaCha20-Poly1305</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create an encryption block for a new archive
 * EncryptionBlock block = EncryptionBlock.builder()
 *     .kdfAlgorithmId(FormatConstants.KDF_ARGON2ID)
 *     .cipherAlgorithmId(FormatConstants.ENCRYPTION_AES_256_GCM)
 *     .kdfIterations(3)
 *     .kdfMemory(65536)  // 64 MB
 *     .kdfParallelism(4)
 *     .salt(generateRandomSalt())
 *     .wrappedKey(encryptedDek)
 *     .wrappedKeyTag(authTag)
 *     .build();
 *
 * // Check algorithm used
 * if (block.isArgon2id()) {
 *     System.out.println("Using Argon2id with " + block.kdfMemory() + " KB memory");
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This record creates defensive copies of all byte arrays during construction
 * and when returning values, ensuring immutability. Instances are thread-safe
 * and can be freely shared between threads.</p>
 *
 * @param kdfAlgorithmId    the KDF algorithm ID; one of {@link FormatConstants#KDF_ARGON2ID}
 *                          or {@link FormatConstants#KDF_PBKDF2_SHA256}
 * @param cipherAlgorithmId the cipher algorithm ID; one of
 *                          {@link FormatConstants#ENCRYPTION_AES_256_GCM} or
 *                          {@link FormatConstants#ENCRYPTION_CHACHA20_POLY1305}
 * @param kdfIterations     the number of iterations (PBKDF2) or time cost (Argon2id)
 * @param kdfMemory         the memory cost in KB for Argon2id; ignored for PBKDF2
 * @param kdfParallelism    the parallelism factor for Argon2id; ignored for PBKDF2
 * @param salt              the random salt used for key derivation; typically 32 bytes
 * @param wrappedKey        the encrypted Data Encryption Key (DEK)
 * @param wrappedKeyTag     the AEAD authentication tag for the wrapped key
 *
 * @see FileHeader#isEncrypted()
 * @see FormatConstants#ENCRYPTION_MAGIC
 * @see FormatConstants#KEY_SIZE
 * @see FormatConstants#DEFAULT_SALT_SIZE
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record EncryptionBlock(
        int kdfAlgorithmId,
        int cipherAlgorithmId,
        int kdfIterations,
        int kdfMemory,
        int kdfParallelism,
        byte @NotNull [] salt,
        byte @NotNull [] wrappedKey,
        byte @NotNull [] wrappedKeyTag
) {

    /**
     * Compact constructor that creates defensive copies of all byte arrays.
     *
     * <p>This ensures that the encryption block is truly immutable and that
     * external modifications to the original arrays do not affect this record.</p>
     */
    public EncryptionBlock {
        salt = salt.clone();
        wrappedKey = wrappedKey.clone();
        wrappedKeyTag = wrappedKeyTag.clone();
    }

    /**
     * Returns a defensive copy of the KDF salt.
     *
     * <p>The returned array is a copy; modifications to it will not affect
     * this encryption block.</p>
     *
     * @return a copy of the salt bytes; never {@code null}
     */
    @Override
    public byte @NotNull [] salt() {
        return this.salt.clone();
    }

    /**
     * Returns a defensive copy of the wrapped Data Encryption Key.
     *
     * <p>The returned array is a copy; modifications to it will not affect
     * this encryption block. The wrapped key must be decrypted with the
     * Key Encryption Key derived from the password to obtain the actual DEK.</p>
     *
     * @return a copy of the wrapped key bytes; never {@code null}
     */
    @Override
    public byte @NotNull [] wrappedKey() {
        return this.wrappedKey.clone();
    }

    /**
     * Returns a defensive copy of the wrapped key authentication tag.
     *
     * <p>The returned array is a copy; modifications to it will not affect
     * this encryption block. This tag is used to verify the integrity of
     * the wrapped key during decryption.</p>
     *
     * @return a copy of the authentication tag bytes; never {@code null}
     */
    @Override
    public byte @NotNull [] wrappedKeyTag() {
        return this.wrappedKeyTag.clone();
    }

    /**
     * Creates a new encryption block builder initialized with default values.
     *
     * <p>The builder is pre-configured with:</p>
     * <ul>
     *   <li>KDF: Argon2id</li>
     *   <li>Cipher: AES-256-GCM</li>
     *   <li>Iterations: 3</li>
     *   <li>Memory: 64 MB</li>
     *   <li>Parallelism: 4</li>
     * </ul>
     *
     * @return a new builder instance; never {@code null}
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Checks if the Key Derivation Function is Argon2id.
     *
     * <p>Argon2id is a memory-hard KDF that provides strong resistance against
     * GPU-based password cracking attacks. It is the recommended KDF for APACK.</p>
     *
     * @return {@code true} if this block uses Argon2id, {@code false} otherwise
     *
     * @see FormatConstants#KDF_ARGON2ID
     */
    public boolean isArgon2id() {
        return this.kdfAlgorithmId == FormatConstants.KDF_ARGON2ID;
    }

    /**
     * Checks if the Key Derivation Function is PBKDF2-HMAC-SHA256.
     *
     * <p>PBKDF2 is a widely-supported KDF with lower memory requirements than
     * Argon2id, making it suitable for constrained environments.</p>
     *
     * @return {@code true} if this block uses PBKDF2, {@code false} otherwise
     *
     * @see FormatConstants#KDF_PBKDF2_SHA256
     */
    public boolean isPbkdf2() {
        return this.kdfAlgorithmId == FormatConstants.KDF_PBKDF2_SHA256;
    }

    /**
     * Checks if the cipher algorithm is AES-256-GCM.
     *
     * <p>AES-256-GCM provides authenticated encryption with 256-bit security
     * and is widely supported in hardware on modern processors.</p>
     *
     * @return {@code true} if this block uses AES-256-GCM, {@code false} otherwise
     *
     * @see FormatConstants#ENCRYPTION_AES_256_GCM
     */
    public boolean isAesGcm() {
        return this.cipherAlgorithmId == FormatConstants.ENCRYPTION_AES_256_GCM;
    }

    /**
     * Checks if the cipher algorithm is ChaCha20-Poly1305.
     *
     * <p>ChaCha20-Poly1305 provides authenticated encryption with excellent
     * software performance on systems without AES hardware acceleration.</p>
     *
     * @return {@code true} if this block uses ChaCha20-Poly1305, {@code false} otherwise
     *
     * @see FormatConstants#ENCRYPTION_CHACHA20_POLY1305
     */
    public boolean isChaCha20Poly1305() {
        return this.cipherAlgorithmId == FormatConstants.ENCRYPTION_CHACHA20_POLY1305;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof EncryptionBlock that)) return false;
        return this.kdfAlgorithmId == that.kdfAlgorithmId
                && this.cipherAlgorithmId == that.cipherAlgorithmId
                && this.kdfIterations == that.kdfIterations
                && this.kdfMemory == that.kdfMemory
                && this.kdfParallelism == that.kdfParallelism
                && Arrays.equals(this.salt, that.salt)
                && Arrays.equals(this.wrappedKey, that.wrappedKey)
                && Arrays.equals(this.wrappedKeyTag, that.wrappedKeyTag);
    }

    @Override
    public int hashCode() {
        int result = this.kdfAlgorithmId;
        result = 31 * result + this.cipherAlgorithmId;
        result = 31 * result + this.kdfIterations;
        result = 31 * result + this.kdfMemory;
        result = 31 * result + this.kdfParallelism;
        result = 31 * result + Arrays.hashCode(this.salt);
        result = 31 * result + Arrays.hashCode(this.wrappedKey);
        result = 31 * result + Arrays.hashCode(this.wrappedKeyTag);
        return result;
    }

    @Override
    public @NotNull String toString() {
        return "EncryptionBlock[kdf=" + this.kdfAlgorithmId
                + ", cipher=" + this.cipherAlgorithmId
                + ", saltLen=" + this.salt.length
                + ", wrappedKeyLen=" + this.wrappedKey.length + "]";
    }

    /**
     * A fluent builder for creating {@link EncryptionBlock} instances.
     *
     * <p>This builder provides a convenient way to construct encryption blocks
     * with customized settings. All setter methods return the builder instance
     * for method chaining.</p>
     *
     * <h2>Default Values</h2>
     * <p>The builder is initialized with secure defaults:</p>
     * <ul>
     *   <li>KDF algorithm: Argon2id</li>
     *   <li>Cipher algorithm: AES-256-GCM</li>
     *   <li>KDF iterations: 3 (Argon2id time cost)</li>
     *   <li>KDF memory: 65536 KB (64 MB)</li>
     *   <li>KDF parallelism: 4</li>
     *   <li>Salt, wrapped key, and tag: Empty arrays (must be set)</li>
     * </ul>
     *
     * <h2>Usage Example</h2>
     * <pre>{@code
     * byte[] salt = new byte[32];
     * SecureRandom.getInstanceStrong().nextBytes(salt);
     *
     * EncryptionBlock block = EncryptionBlock.builder()
     *     .kdfAlgorithmId(FormatConstants.KDF_ARGON2ID)
     *     .cipherAlgorithmId(FormatConstants.ENCRYPTION_AES_256_GCM)
     *     .kdfIterations(3)
     *     .kdfMemory(65536)
     *     .kdfParallelism(4)
     *     .salt(salt)
     *     .wrappedKey(encryptedKey)
     *     .wrappedKeyTag(tag)
     *     .build();
     * }</pre>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public static final class Builder {

        private int kdfAlgorithmId = FormatConstants.KDF_ARGON2ID;
        private int cipherAlgorithmId = FormatConstants.ENCRYPTION_AES_256_GCM;
        private int kdfIterations = 3;
        private int kdfMemory = 65536; // 64 MB
        private int kdfParallelism = 4;
        private byte @NotNull [] salt = new byte[0];
        private byte @NotNull [] wrappedKey = new byte[0];
        private byte @NotNull [] wrappedKeyTag = new byte[0];

        private Builder() {
        }

        /**
         * Sets the Key Derivation Function (KDF) algorithm identifier.
         *
         * <p>The KDF algorithm determines how the encryption key is derived from
         * the user's password. This is a critical security parameter that affects
         * the resistance to password cracking attacks. The following algorithms
         * are supported:</p>
         * <ul>
         *   <li>{@link FormatConstants#KDF_ARGON2ID} (0): Argon2id - memory-hard,
         *       provides strong resistance to GPU-based attacks (recommended)</li>
         *   <li>{@link FormatConstants#KDF_PBKDF2_SHA256} (1): PBKDF2-HMAC-SHA256 -
         *       widely compatible, suitable for constrained environments</li>
         * </ul>
         *
         * <p>Argon2id is the recommended choice for new archives due to its
         * memory-hard properties that make parallel cracking attacks expensive.</p>
         *
         * @param kdfAlgorithmId the KDF algorithm identifier; must be one of the
         *                       {@code KDF_*} constants defined in {@link FormatConstants};
         *                       determines which key derivation function will be used
         * @return this builder instance to allow fluent method chaining for setting
         *         additional encryption block properties
         *
         * @see FormatConstants#KDF_ARGON2ID
         * @see FormatConstants#KDF_PBKDF2_SHA256
         * @see EncryptionBlock#isArgon2id()
         * @see EncryptionBlock#isPbkdf2()
         */
        public @NotNull Builder kdfAlgorithmId(final int kdfAlgorithmId) {
            this.kdfAlgorithmId = kdfAlgorithmId;
            return this;
        }

        /**
         * Sets the cipher algorithm identifier for data encryption.
         *
         * <p>The cipher algorithm determines how archive data is encrypted and
         * authenticated. Both supported algorithms provide authenticated encryption
         * (AEAD), ensuring both confidentiality and integrity. The following
         * algorithms are supported:</p>
         * <ul>
         *   <li>{@link FormatConstants#ENCRYPTION_AES_256_GCM} (1): AES-256 in GCM mode -
         *       widely supported, hardware-accelerated on modern CPUs</li>
         *   <li>{@link FormatConstants#ENCRYPTION_CHACHA20_POLY1305} (2): ChaCha20-Poly1305 -
         *       excellent software performance, no timing side channels</li>
         * </ul>
         *
         * <p>Both algorithms provide 256-bit security and are suitable for
         * protecting sensitive data.</p>
         *
         * @param cipherAlgorithmId the cipher algorithm identifier; must be one of
         *                          the {@code ENCRYPTION_*} constants defined in
         *                          {@link FormatConstants}
         * @return this builder instance to allow fluent method chaining for setting
         *         additional encryption block properties
         *
         * @see FormatConstants#ENCRYPTION_AES_256_GCM
         * @see FormatConstants#ENCRYPTION_CHACHA20_POLY1305
         * @see EncryptionBlock#isAesGcm()
         * @see EncryptionBlock#isChaCha20Poly1305()
         */
        public @NotNull Builder cipherAlgorithmId(final int cipherAlgorithmId) {
            this.cipherAlgorithmId = cipherAlgorithmId;
            return this;
        }

        /**
         * Sets the KDF iteration count (PBKDF2) or time cost parameter (Argon2id).
         *
         * <p>This parameter controls the computational cost of key derivation:</p>
         * <ul>
         *   <li><strong>For PBKDF2:</strong> The number of HMAC iterations to perform.
         *       Higher values increase security but slow down key derivation.
         *       Recommended: 100,000 or more for sensitive data.</li>
         *   <li><strong>For Argon2id:</strong> The time cost parameter (t). Each
         *       iteration performs one pass over the memory. Recommended: 3 or more
         *       for interactive use, higher for offline processing.</li>
         * </ul>
         *
         * <p>The value should be chosen to balance security against acceptable
         * delay during password verification. Higher values provide better
         * resistance to brute-force attacks.</p>
         *
         * @param kdfIterations the iteration count for PBKDF2 or time cost for
         *                      Argon2id; must be a positive integer; higher values
         *                      increase security but slow down key derivation
         * @return this builder instance to allow fluent method chaining for setting
         *         additional encryption block properties
         *
         * @see EncryptionBlock#kdfIterations()
         */
        public @NotNull Builder kdfIterations(final int kdfIterations) {
            this.kdfIterations = kdfIterations;
            return this;
        }

        /**
         * Sets the memory cost in kilobytes for Argon2id key derivation.
         *
         * <p>The memory cost parameter (m) determines how much memory Argon2id
         * uses during key derivation. Higher values make GPU-based parallel
         * attacks more expensive by requiring more memory per attempt.</p>
         *
         * <p>Recommended values:</p>
         * <ul>
         *   <li>65536 KB (64 MB): Good balance for most applications</li>
         *   <li>131072 KB (128 MB) or higher: For high-security applications</li>
         *   <li>16384 KB (16 MB) or lower: For memory-constrained environments</li>
         * </ul>
         *
         * <p><strong>Note:</strong> This parameter is only used with Argon2id.
         * It is ignored when PBKDF2 is selected as the KDF algorithm.</p>
         *
         * @param kdfMemory the memory cost in kilobytes for Argon2id; must be a
         *                  positive integer; higher values increase security but
         *                  require more memory during key derivation
         * @return this builder instance to allow fluent method chaining for setting
         *         additional encryption block properties
         *
         * @see EncryptionBlock#kdfMemory()
         */
        public @NotNull Builder kdfMemory(final int kdfMemory) {
            this.kdfMemory = kdfMemory;
            return this;
        }

        /**
         * Sets the parallelism factor for Argon2id key derivation.
         *
         * <p>The parallelism parameter (p) determines how many independent
         * computational lanes Argon2id uses. This should typically be set to
         * the number of available CPU cores for optimal performance.</p>
         *
         * <p>Recommended values:</p>
         * <ul>
         *   <li>4: Good default for most systems</li>
         *   <li>1-2: For single-core or mobile devices</li>
         *   <li>8+: For servers with many cores</li>
         * </ul>
         *
         * <p><strong>Note:</strong> This parameter is only used with Argon2id.
         * It is ignored when PBKDF2 is selected as the KDF algorithm.</p>
         *
         * @param kdfParallelism the parallelism factor for Argon2id; must be a
         *                       positive integer; typically set to the number of
         *                       available CPU cores
         * @return this builder instance to allow fluent method chaining for setting
         *         additional encryption block properties
         *
         * @see EncryptionBlock#kdfParallelism()
         */
        public @NotNull Builder kdfParallelism(final int kdfParallelism) {
            this.kdfParallelism = kdfParallelism;
            return this;
        }

        /**
         * Sets the random salt used for key derivation.
         *
         * <p>The salt is a random value that is combined with the password during
         * key derivation. Using a unique salt for each archive ensures that the
         * same password produces different encryption keys, preventing precomputed
         * rainbow table attacks.</p>
         *
         * <p>The salt should be:</p>
         * <ul>
         *   <li>Generated using a cryptographically secure random number generator</li>
         *   <li>At least {@link FormatConstants#DEFAULT_SALT_SIZE} (32) bytes long</li>
         *   <li>Unique for each archive (never reused)</li>
         * </ul>
         *
         * <p>The provided byte array is defensively copied to ensure immutability
         * of the resulting encryption block.</p>
         *
         * @param salt the salt bytes for key derivation; must not be {@code null};
         *             should be at least {@link FormatConstants#DEFAULT_SALT_SIZE}
         *             bytes; generated using a secure random number generator
         * @return this builder instance to allow fluent method chaining for setting
         *         additional encryption block properties
         *
         * @see FormatConstants#DEFAULT_SALT_SIZE
         * @see EncryptionBlock#salt()
         */
        public @NotNull Builder salt(final byte @NotNull [] salt) {
            this.salt = salt;
            return this;
        }

        /**
         * Sets the wrapped (encrypted) Data Encryption Key (DEK).
         *
         * <p>The wrapped key is the actual data encryption key that has been
         * encrypted using the Key Encryption Key (KEK) derived from the password.
         * This two-tier key hierarchy allows password changes without re-encrypting
         * all archive data.</p>
         *
         * <p>The wrapping process:</p>
         * <ol>
         *   <li>Generate a random DEK (32 bytes for 256-bit encryption)</li>
         *   <li>Derive a KEK from the password using the configured KDF</li>
         *   <li>Encrypt the DEK using the KEK with an AEAD cipher</li>
         *   <li>Store the encrypted DEK (wrapped key) in the encryption block</li>
         * </ol>
         *
         * <p>The provided byte array is defensively copied to ensure immutability
         * of the resulting encryption block.</p>
         *
         * @param wrappedKey the encrypted DEK bytes; must not be {@code null};
         *                   typically {@link FormatConstants#KEY_SIZE} (32) bytes
         *                   for the encrypted key material
         * @return this builder instance to allow fluent method chaining for setting
         *         additional encryption block properties
         *
         * @see FormatConstants#KEY_SIZE
         * @see EncryptionBlock#wrappedKey()
         */
        public @NotNull Builder wrappedKey(final byte @NotNull [] wrappedKey) {
            this.wrappedKey = wrappedKey;
            return this;
        }

        /**
         * Sets the AEAD authentication tag for the wrapped key.
         *
         * <p>The authentication tag is produced by the AEAD cipher when encrypting
         * the DEK and is used to verify the integrity and authenticity of the
         * wrapped key during decryption. If the tag verification fails, it indicates
         * either data corruption or an incorrect password.</p>
         *
         * <p>The tag size is {@link FormatConstants#AUTH_TAG_SIZE} (16) bytes for
         * both AES-GCM and ChaCha20-Poly1305 ciphers.</p>
         *
         * <p>The provided byte array is defensively copied to ensure immutability
         * of the resulting encryption block.</p>
         *
         * @param wrappedKeyTag the AEAD authentication tag bytes; must not be
         *                      {@code null}; must be exactly
         *                      {@link FormatConstants#AUTH_TAG_SIZE} (16) bytes
         * @return this builder instance to allow fluent method chaining for setting
         *         additional encryption block properties
         *
         * @see FormatConstants#AUTH_TAG_SIZE
         * @see EncryptionBlock#wrappedKeyTag()
         */
        public @NotNull Builder wrappedKeyTag(final byte @NotNull [] wrappedKeyTag) {
            this.wrappedKeyTag = wrappedKeyTag;
            return this;
        }

        /**
         * Builds and returns a new immutable {@link EncryptionBlock} instance.
         *
         * <p>This method constructs a new encryption block using all the values
         * that have been set on this builder. Any values not explicitly set will
         * use their default values as established when the builder was created.</p>
         *
         * <p>The resulting encryption block is immutable and thread-safe. All
         * byte arrays (salt, wrapped key, tag) are defensively copied to ensure
         * that modifications to the original arrays do not affect the created block.</p>
         *
         * <p>The builder can be reused after calling this method to create
         * additional encryption blocks with different or modified values.</p>
         *
         * @return a new immutable {@link EncryptionBlock} instance containing all
         *         the configured values; never {@code null}
         *
         * @see EncryptionBlock
         */
        public @NotNull EncryptionBlock build() {
            return new EncryptionBlock(
                    this.kdfAlgorithmId,
                    this.cipherAlgorithmId,
                    this.kdfIterations,
                    this.kdfMemory,
                    this.kdfParallelism,
                    this.salt,
                    this.wrappedKey,
                    this.wrappedKeyTag
            );
        }

    }

}
