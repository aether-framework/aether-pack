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
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.jetbrains.annotations.NotNull;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Argon2id key derivation function implementation.
 *
 * <p>Argon2id is the <strong>recommended</strong> key derivation function for
 * password-based encryption. It was the winner of the Password Hashing Competition
 * (PHC) in 2015 and combines the best properties of the Argon2d and Argon2i variants:</p>
 *
 * <ul>
 *   <li><strong>Memory-hard:</strong> Requires significant memory, making GPU/ASIC
 *       attacks expensive</li>
 *   <li><strong>Side-channel resistant:</strong> First half uses data-independent
 *       memory access (Argon2i), second half uses data-dependent access (Argon2d)</li>
 *   <li><strong>Parallelizable:</strong> Can use multiple CPU cores efficiently</li>
 * </ul>
 *
 * <h2>Default Parameters (OWASP 2024)</h2>
 * <table>
 *   <caption>Argon2id Default Configuration</caption>
 *   <tr><th>Parameter</th><th>Default Value</th><th>Description</th></tr>
 *   <tr><td>Memory</td><td>64 MiB (65536 KiB)</td><td>Memory cost in kilobytes</td></tr>
 *   <tr><td>Iterations</td><td>3</td><td>Time cost (number of passes)</td></tr>
 *   <tr><td>Parallelism</td><td>4</td><td>Number of parallel threads</td></tr>
 *   <tr><td>Salt Length</td><td>16 bytes</td><td>Random salt size</td></tr>
 * </table>
 *
 * <p>These defaults follow the OWASP recommendations for 2024 and provide a good
 * balance between security and performance on modern hardware.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create with default parameters
 * Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();
 *
 * // Or with custom parameters for higher security
 * Argon2idKeyDerivation kdf = new Argon2idKeyDerivation(
 *     131072,  // 128 MiB memory
 *     4,       // 4 iterations
 *     8,       // 8 parallel threads
 *     32       // 32-byte salt
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
 * <h2>Choosing Parameters</h2>
 * <p>Parameters should be chosen to make key derivation take at least 100ms on
 * target hardware:</p>
 * <ul>
 *   <li><strong>Server applications:</strong> 64 MiB memory, 3 iterations (default)</li>
 *   <li><strong>High-security:</strong> 128+ MiB memory, 4+ iterations</li>
 *   <li><strong>Constrained devices:</strong> 32 MiB memory, 2 iterations</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is immutable and thread-safe. The same instance can be used
 * concurrently from multiple threads.</p>
 *
 * @see KeyDerivation
 * @see Pbkdf2KeyDerivation
 * @see KeyWrapper
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class Argon2idKeyDerivation implements KeyDerivation {

    /**
     * Algorithm identifier string for Argon2id.
     * This value is used for configuration and logging purposes.
     */
    public static final String ID = "argon2id";

    /**
     * Default memory cost in KiB (64 MiB = 65536 KiB).
     * This is the OWASP-recommended value for 2024.
     */
    public static final int DEFAULT_MEMORY_KIB = 65536;

    /**
     * Default number of iterations (time cost).
     * Higher values increase computation time linearly.
     */
    public static final int DEFAULT_ITERATIONS = 3;

    /**
     * Default parallelism factor (number of lanes/threads).
     * Should match the number of available CPU cores.
     */
    public static final int DEFAULT_PARALLELISM = 4;

    /**
     * Default salt length in bytes (128 bits).
     * Provides sufficient uniqueness for most use cases.
     */
    public static final int DEFAULT_SALT_LENGTH = 16;

    /**
     * Minimum allowed salt length in bytes (64 bits).
     * Shorter salts are rejected for security reasons.
     */
    public static final int MIN_SALT_LENGTH = 8;

    /** Shared secure random instance for salt generation. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Memory cost in KiB for this instance. */
    private final int memoryKiB;

    /** Number of iterations (time cost) for this instance. */
    private final int iterations;

    /** Parallelism factor for this instance. */
    private final int parallelism;

    /** Salt length in bytes for this instance. */
    private final int saltLength;

    /**
     * Creates an Argon2id KDF with default OWASP 2024 parameters.
     *
     * <p>Default values:</p>
     * <ul>
     *   <li>Memory: {@value #DEFAULT_MEMORY_KIB} KiB (64 MiB)</li>
     *   <li>Iterations: {@value #DEFAULT_ITERATIONS}</li>
     *   <li>Parallelism: {@value #DEFAULT_PARALLELISM}</li>
     *   <li>Salt Length: {@value #DEFAULT_SALT_LENGTH} bytes</li>
     * </ul>
     *
     * @see #Argon2idKeyDerivation(int, int, int, int)
     */
    public Argon2idKeyDerivation() {
        this(DEFAULT_MEMORY_KIB, DEFAULT_ITERATIONS, DEFAULT_PARALLELISM, DEFAULT_SALT_LENGTH);
    }

    /**
     * Creates an Argon2id KDF with custom parameters.
     *
     * <p>Use this constructor when the default parameters don't meet your
     * security or performance requirements. Higher values provide better
     * security but increase computation time and memory usage.</p>
     *
     * @param memoryKiB   memory cost in KiB; must be at least 8 KiB;
     *                    typical values: 32768 (32 MiB) to 262144 (256 MiB)
     * @param iterations  number of iterations (time cost); must be at least 1;
     *                    higher values increase computation time linearly
     * @param parallelism parallelism factor (number of lanes); must be at least 1;
     *                    typically matches the number of CPU cores
     * @param saltLength  salt length in bytes; must be at least {@value #MIN_SALT_LENGTH};
     *                    16-32 bytes recommended
     * @throws IllegalArgumentException if any parameter is below its minimum value
     *
     * @see #Argon2idKeyDerivation()
     */
    public Argon2idKeyDerivation(
            final int memoryKiB,
            final int iterations,
            final int parallelism,
            final int saltLength) {

        if (memoryKiB < 8) {
            throw new IllegalArgumentException("Memory must be at least 8 KiB");
        }
        if (iterations < 1) {
            throw new IllegalArgumentException("Iterations must be at least 1");
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("Parallelism must be at least 1");
        }
        if (saltLength < MIN_SALT_LENGTH) {
            throw new IllegalArgumentException("Salt must be at least " + MIN_SALT_LENGTH + " bytes");
        }

        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.saltLength = saltLength;
    }

    /**
     * Recreates an Argon2id KDF from serialized parameters.
     *
     * <p>This factory method deserializes parameters previously obtained from
     * {@link #getParameters()}. It is used when decrypting data to recreate
     * the exact KDF configuration used during encryption.</p>
     *
     * <p>Parameter format (16 bytes, big-endian):</p>
     * <pre>
     * [0-3]   memoryKiB (int)
     * [4-7]   iterations (int)
     * [8-11]  parallelism (int)
     * [12-15] saltLength (int)
     * </pre>
     *
     * @param parameters the serialized parameters; must not be {@code null};
     *                   must be at least 16 bytes
     * @return a new KDF instance with the deserialized configuration;
     *         never returns {@code null}
     * @throws IllegalArgumentException if the parameters array is too short
     *         or contains invalid values
     *
     * @see #getParameters()
     */
    public static @NotNull Argon2idKeyDerivation fromParameters(final byte @NotNull [] parameters) {
        if (parameters.length < 16) {
            throw new IllegalArgumentException("Invalid parameters length");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(parameters);
        final int memoryKiB = buffer.getInt();
        final int iterations = buffer.getInt();
        final int parallelism = buffer.getInt();
        final int saltLength = buffer.getInt();
        return new Argon2idKeyDerivation(memoryKiB, iterations, parallelism, saltLength);
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
     * @return {@link FormatConstants#KDF_ARGON2ID}
     */
    @Override
    public int getNumericId() {
        return FormatConstants.KDF_ARGON2ID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation uses BouncyCastle's Argon2 generator with the
     * parameters configured for this instance. The password is converted to
     * UTF-8 bytes internally and cleared after key derivation.</p>
     *
     * @param password  the password; cleared from memory after key derivation
     * @param salt      the salt; must be at least {@value #MIN_SALT_LENGTH} bytes
     * @param keyLength the desired key length in bytes
     * @return the derived key with algorithm name "RAW"
     * @throws GeneralSecurityException if key derivation fails
     * @throws IllegalArgumentException if salt is too short
     */
    @Override
    public @NotNull SecretKey deriveKey(
            final char @NotNull [] password,
            final byte @NotNull [] salt,
            final int keyLength) throws GeneralSecurityException {

        if (salt.length < MIN_SALT_LENGTH) {
            throw new IllegalArgumentException("Salt too short: minimum " + MIN_SALT_LENGTH + " bytes");
        }

        // Convert password to bytes
        final byte[] passwordBytes = toBytes(password);

        try {
            // Build Argon2 parameters
            final Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withSalt(salt)
                    .withMemoryAsKB(this.memoryKiB)
                    .withIterations(this.iterations)
                    .withParallelism(this.parallelism);

            final Argon2Parameters params = builder.build();

            // Generate key
            final Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(params);

            final byte[] keyBytes = new byte[keyLength];
            generator.generateBytes(passwordBytes, keyBytes);

            return new SecretKeySpec(keyBytes, "RAW");
        } finally {
            // Clear password bytes
            Arrays.fill(passwordBytes, (byte) 0);
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
     * <p>Returns a 16-byte array containing all Argon2id parameters in
     * big-endian format: memoryKiB, iterations, parallelism, saltLength.</p>
     *
     * @return serialized parameters (16 bytes)
     *
     * @see #fromParameters(byte[])
     */
    @Override
    public byte @NotNull [] getParameters() {
        final ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putInt(this.memoryKiB);
        buffer.putInt(this.iterations);
        buffer.putInt(this.parallelism);
        buffer.putInt(this.saltLength);
        return buffer.array();
    }

    /**
     * Returns the memory cost in KiB configured for this instance.
     *
     * @return memory cost in kilobytes
     */
    public int getMemoryKiB() {
        return this.memoryKiB;
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
     * Returns the parallelism factor configured for this instance.
     *
     * @return the parallelism (number of lanes/threads)
     */
    public int getParallelism() {
        return this.parallelism;
    }

    /**
     * Converts a character array to UTF-8 bytes.
     *
     * <p>This method is used to convert the password to bytes for the
     * Argon2 algorithm. The resulting byte array should be cleared
     * after use.</p>
     *
     * @param chars the characters to convert; must not be {@code null}
     * @return UTF-8 encoded bytes; never returns {@code null}
     */
    private static byte @NotNull [] toBytes(final char @NotNull [] chars) {
        final CharBuffer charBuffer = CharBuffer.wrap(chars);
        final ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        final byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        // Clear the buffer
        byteBuffer.clear();
        while (byteBuffer.hasRemaining()) {
            byteBuffer.put((byte) 0);
        }
        return bytes;
    }

}
