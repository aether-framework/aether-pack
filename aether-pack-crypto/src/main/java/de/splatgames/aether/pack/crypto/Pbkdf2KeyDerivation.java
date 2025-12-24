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
import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

/**
 * PBKDF2-HMAC-SHA256 key derivation function implementation.
 *
 * <p>PBKDF2 (Password-Based Key Derivation Function 2) is a widely-used
 * key derivation function defined in RFC 8018. It applies a pseudorandom
 * function (in this case HMAC-SHA256) to the password along with a salt
 * value and repeats the process many times to produce the derived key.</p>
 *
 * <h2>When to Use PBKDF2</h2>
 * <p>This implementation is provided as a <strong>fallback</strong> for environments
 * where Argon2 is not available. For new applications, prefer
 * {@link Argon2idKeyDerivation} because:</p>
 * <ul>
 *   <li><strong>Memory-hardness:</strong> Argon2 requires significant memory,
 *       making GPU/ASIC attacks expensive; PBKDF2 is not memory-hard</li>
 *   <li><strong>Modern design:</strong> Argon2 won the Password Hashing Competition
 *       in 2015 and represents the current best practice</li>
 *   <li><strong>Better resistance:</strong> Argon2 provides better resistance
 *       against time-memory trade-off attacks</li>
 * </ul>
 *
 * <h2>Default Parameters (OWASP 2024)</h2>
 * <table>
 *   <caption>PBKDF2-HMAC-SHA256 Default Configuration</caption>
 *   <tr><th>Parameter</th><th>Default Value</th><th>Description</th></tr>
 *   <tr><td>Iterations</td><td>600,000</td><td>Number of HMAC operations</td></tr>
 *   <tr><td>Salt Length</td><td>32 bytes</td><td>Random salt size</td></tr>
 * </table>
 *
 * <p>These defaults follow the OWASP recommendations for 2024 when using
 * HMAC-SHA256 as the pseudorandom function.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create with default parameters
 * Pbkdf2KeyDerivation kdf = new Pbkdf2KeyDerivation();
 *
 * // Or with custom parameters for higher security
 * Pbkdf2KeyDerivation kdf = new Pbkdf2KeyDerivation(
 *     1000000,  // 1 million iterations
 *     32        // 32-byte salt
 * );
 *
 * // Generate salt and derive key
 * byte[] salt = kdf.generateSalt();
 * SecretKey key = kdf.deriveKey("user-password".toCharArray(), salt, 32);
 *
 * // Store parameters and salt for decryption
 * byte[] params = kdf.getParameters();
 * }</pre>
 *
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li><strong>Iteration count:</strong> Should make key derivation take at
 *       least 100ms on target hardware. 600,000 is the OWASP minimum for
 *       HMAC-SHA256.</li>
 *   <li><strong>GPU attacks:</strong> PBKDF2 is vulnerable to GPU acceleration;
 *       attackers can test many passwords in parallel</li>
 *   <li><strong>Salt uniqueness:</strong> Always use a unique random salt for
 *       each password to prevent rainbow table attacks</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is immutable and thread-safe. The same instance can be used
 * concurrently from multiple threads.</p>
 *
 * @see KeyDerivation
 * @see Argon2idKeyDerivation
 * @see KeyWrapper
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class Pbkdf2KeyDerivation implements KeyDerivation {

    /**
     * Algorithm identifier string for PBKDF2 with HMAC-SHA256.
     * This value is used for configuration and logging purposes.
     */
    public static final String ID = "pbkdf2-sha256";

    /**
     * Default number of iterations (OWASP 2024 recommendation for HMAC-SHA256).
     *
     * <p>This value (600,000) is chosen to make key derivation take approximately
     * 100ms on modern hardware when using HMAC-SHA256. Lower iteration counts
     * make brute-force attacks faster.</p>
     */
    public static final int DEFAULT_ITERATIONS = 600000;

    /**
     * Default salt length in bytes (256 bits).
     *
     * <p>A 32-byte salt provides sufficient uniqueness to prevent rainbow table
     * attacks even for very large password databases.</p>
     */
    public static final int DEFAULT_SALT_LENGTH = 32;

    /**
     * Minimum allowed salt length in bytes (128 bits).
     *
     * <p>Shorter salts are rejected for security reasons. A minimum of 16 bytes
     * ensures reasonable uniqueness across password databases.</p>
     */
    public static final int MIN_SALT_LENGTH = 16;

    /**
     * Minimum allowed iteration count.
     *
     * <p>Iteration counts below 100,000 are rejected as they provide insufficient
     * resistance against brute-force attacks on modern hardware.</p>
     */
    public static final int MIN_ITERATIONS = 100000;

    /** JCA algorithm name for PBKDF2 with HMAC-SHA256. */
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** Shared secure random instance for salt generation. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Number of iterations (time cost) for this instance. */
    private final int iterations;

    /** Salt length in bytes for this instance. */
    private final int saltLength;

    /**
     * Creates a PBKDF2 KDF with default OWASP 2024 parameters.
     *
     * <p>Default values:</p>
     * <ul>
     *   <li>Iterations: {@value #DEFAULT_ITERATIONS}</li>
     *   <li>Salt Length: {@value #DEFAULT_SALT_LENGTH} bytes</li>
     * </ul>
     *
     * @see #Pbkdf2KeyDerivation(int, int)
     */
    public Pbkdf2KeyDerivation() {
        this(DEFAULT_ITERATIONS, DEFAULT_SALT_LENGTH);
    }

    /**
     * Creates a PBKDF2 KDF with custom parameters.
     *
     * <p>Use this constructor when the default parameters don't meet your
     * security or performance requirements. Higher iteration counts provide
     * better security but increase computation time linearly.</p>
     *
     * @param iterations the number of HMAC iterations (time cost); must be at least
     *                   {@value #MIN_ITERATIONS}; higher values make brute-force
     *                   attacks slower but also slow down legitimate key derivation
     * @param saltLength the salt length in bytes; must be at least {@value #MIN_SALT_LENGTH};
     *                   16-32 bytes recommended
     * @throws IllegalArgumentException if iterations is less than {@value #MIN_ITERATIONS}
     *         or saltLength is less than {@value #MIN_SALT_LENGTH}
     *
     * @see #Pbkdf2KeyDerivation()
     */
    public Pbkdf2KeyDerivation(final int iterations, final int saltLength) {
        if (iterations < MIN_ITERATIONS) {
            throw new IllegalArgumentException(
                    "Iterations must be at least " + MIN_ITERATIONS);
        }
        if (saltLength < MIN_SALT_LENGTH) {
            throw new IllegalArgumentException(
                    "Salt must be at least " + MIN_SALT_LENGTH + " bytes");
        }

        this.iterations = iterations;
        this.saltLength = saltLength;
    }

    /**
     * Recreates a PBKDF2 KDF from serialized parameters.
     *
     * <p>This factory method deserializes parameters previously obtained from
     * {@link #getParameters()}. It is used when decrypting data to recreate
     * the exact KDF configuration used during encryption.</p>
     *
     * <p>Parameter format (8 bytes, big-endian):</p>
     * <pre>
     * [0-3]  iterations (int)
     * [4-7]  saltLength (int)
     * </pre>
     *
     * @param parameters the serialized parameters; must not be {@code null};
     *                   must be at least 8 bytes
     * @return a new KDF instance with the deserialized configuration;
     *         never returns {@code null}
     * @throws IllegalArgumentException if the parameters array is too short
     *         or contains invalid values (iterations/salt below minimums)
     *
     * @see #getParameters()
     */
    public static @NotNull Pbkdf2KeyDerivation fromParameters(final byte @NotNull [] parameters) {
        if (parameters.length < 8) {
            throw new IllegalArgumentException("Invalid parameters length");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(parameters);
        final int iterations = buffer.getInt();
        final int saltLength = buffer.getInt();
        return new Pbkdf2KeyDerivation(iterations, saltLength);
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #ID}
     */
    @Override
    public @NotNull String getId() {
        return ID;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link de.splatgames.aether.pack.core.format.FormatConstants#KDF_PBKDF2_SHA256}
     */
    @Override
    public int getNumericId() {
        return FormatConstants.KDF_PBKDF2_SHA256;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation uses the JCA {@code SecretKeyFactory} with the
     * {@code PBKDF2WithHmacSHA256} algorithm. The password is passed to the
     * underlying PBKDF2 implementation and cleared after key derivation
     * (if supported by the JCA provider).</p>
     *
     * <p><strong>Performance Note:</strong> Key derivation with 600,000 iterations
     * takes approximately 100ms on modern hardware. This is intentional to
     * resist brute-force attacks.</p>
     *
     * @param password  the password; the implementation attempts to clear
     *                  the internal copy after key derivation
     * @param salt      the salt; must be at least {@value #MIN_SALT_LENGTH} bytes
     * @param keyLength the desired key length in bytes
     * @return the derived key with algorithm name "RAW"
     * @throws GeneralSecurityException if key derivation fails due to
     *         algorithm unavailability (should not happen with standard JDK)
     * @throws IllegalArgumentException if salt is too short
     */
    @Override
    public @NotNull SecretKey deriveKey(
            final char @NotNull [] password,
            final byte @NotNull [] salt,
            final int keyLength) throws GeneralSecurityException {

        if (salt.length < MIN_SALT_LENGTH) {
            throw new IllegalArgumentException(
                    "Salt too short: minimum " + MIN_SALT_LENGTH + " bytes");
        }

        final KeySpec spec = new PBEKeySpec(password, salt, this.iterations, keyLength * 8);
        try {
            final SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            final SecretKey derived = factory.generateSecret(spec);
            return new SecretKeySpec(derived.getEncoded(), "RAW");
        } finally {
            // PBEKeySpec doesn't have a clearPassword method that works reliably,
            // but we should try to clear the spec if possible
            try {
                ((PBEKeySpec) spec).clearPassword();
            } catch (final Exception ignored) {
                // Ignore - some implementations don't support this
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #MIN_SALT_LENGTH} bytes
     */
    @Override
    public int getMinSaltLength() {
        return MIN_SALT_LENGTH;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Generates a salt of the length configured for this instance
     * (default: {@value #DEFAULT_SALT_LENGTH} bytes).</p>
     *
     * @return a new random salt
     */
    @Override
    public byte @NotNull [] generateSalt() {
        final byte[] salt = new byte[this.saltLength];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns an 8-byte array containing the PBKDF2 parameters in
     * big-endian format: iterations, saltLength.</p>
     *
     * @return serialized parameters (8 bytes)
     *
     * @see #fromParameters(byte[])
     */
    @Override
    public byte @NotNull [] getParameters() {
        final ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(this.iterations);
        buffer.putInt(this.saltLength);
        return buffer.array();
    }

    /**
     * Returns the number of iterations configured for this instance.
     *
     * @return the iteration count (time cost)
     */
    public int getIterations() {
        return this.iterations;
    }

    /**
     * Returns the salt length configured for this instance.
     *
     * @return the salt length in bytes
     */
    public int getSaltLength() {
        return this.saltLength;
    }

}
