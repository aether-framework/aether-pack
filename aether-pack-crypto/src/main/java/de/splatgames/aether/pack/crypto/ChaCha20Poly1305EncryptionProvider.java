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
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.ChaCha7539Engine;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.jetbrains.annotations.NotNull;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * ChaCha20-Poly1305 encryption provider using BouncyCastle.
 *
 * <p>This class implements the {@link EncryptionProvider} interface using
 * ChaCha20-Poly1305, an Authenticated Encryption with Associated Data (AEAD)
 * algorithm that combines the ChaCha20 stream cipher with the Poly1305 MAC.
 * It provides an alternative to AES-GCM that is particularly efficient in
 * software implementations without hardware AES acceleration.</p>
 *
 * <h2>Algorithm Properties</h2>
 * <table>
 *   <caption>ChaCha20-Poly1305 Parameters</caption>
 *   <tr><th>Property</th><th>Value</th><th>Description</th></tr>
 *   <tr><td>Key Size</td><td>256 bits (32 bytes)</td><td>Secret key length</td></tr>
 *   <tr><td>Nonce Size</td><td>96 bits (12 bytes)</td><td>Unique per-message nonce</td></tr>
 *   <tr><td>Tag Size</td><td>128 bits (16 bytes)</td><td>Poly1305 authentication tag</td></tr>
 * </table>
 *
 * <h2>Security Properties</h2>
 * <ul>
 *   <li><strong>Confidentiality:</strong> Data is encrypted using ChaCha20 stream cipher</li>
 *   <li><strong>Integrity:</strong> Tampering is detected via the 128-bit Poly1305 tag</li>
 *   <li><strong>Authenticity:</strong> The authentication tag verifies the message origin</li>
 *   <li><strong>AAD Support:</strong> Additional data can be authenticated without encryption</li>
 * </ul>
 *
 * <h2>Why Choose ChaCha20-Poly1305?</h2>
 * <ul>
 *   <li><strong>Software Performance:</strong> Faster than AES-GCM on CPUs without AES-NI</li>
 *   <li><strong>Constant Time:</strong> No timing side-channels (unlike some AES implementations)</li>
 *   <li><strong>Simplicity:</strong> Fewer failure modes than AES-GCM</li>
 *   <li><strong>Modern Design:</strong> Developed by Daniel J. Bernstein</li>
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
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ChaCha20Poly1305EncryptionProvider chacha = new ChaCha20Poly1305EncryptionProvider();
 *
 * // Generate a random key
 * SecretKey key = chacha.generateKey();
 *
 * // Encrypt data (streaming)
 * try (OutputStream out = chacha.encrypt(fileOutputStream, key)) {
 *     out.write(plaintext);
 * }
 *
 * // Decrypt data (streaming)
 * try (InputStream in = chacha.decrypt(fileInputStream, key)) {
 *     byte[] decrypted = in.readAllBytes();
 * }
 * }</pre>
 *
 * <h2>Implementation Note</h2>
 * <p>This implementation uses BouncyCastle's ChaCha20-Poly1305 implementation
 * rather than the JCA provider, as BouncyCastle provides streaming support
 * and better control over AEAD parameters.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is stateless and thread-safe. The shared {@link SecureRandom}
 * instance is also thread-safe.</p>
 *
 * @see EncryptionProvider
 * @see EncryptionRegistry#chaCha20Poly1305()
 * @see Aes256GcmEncryptionProvider
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ChaCha20Poly1305EncryptionProvider implements EncryptionProvider {

    /**
     * Algorithm identifier string for ChaCha20-Poly1305.
     * This value is used for registry lookup and configuration purposes.
     */
    public static final String ID = "chacha20-poly1305";

    /**
     * Key size in bytes (256 bits = 32 bytes).
     * ChaCha20 requires exactly 32 bytes of key material.
     */
    public static final int KEY_SIZE = 32;

    /**
     * Nonce size in bytes (96 bits = 12 bytes).
     * The IETF variant of ChaCha20 uses a 96-bit nonce.
     */
    public static final int NONCE_SIZE = 12;

    /**
     * Authentication tag size in bytes (128 bits = 16 bytes).
     * Poly1305 always produces a 16-byte tag.
     */
    public static final int TAG_SIZE = 16;

    /** JCA algorithm name for ChaCha20 keys. */
    private static final String KEY_ALGORITHM = "ChaCha20";

    /** Shared secure random instance for nonce and key generation. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Creates a new ChaCha20-Poly1305 encryption provider instance.
     *
     * <p>The provider is stateless and can be safely shared across multiple
     * threads and operations. Consider using the singleton instance from
     * {@link EncryptionRegistry#chaCha20Poly1305()} for convenience.</p>
     *
     * @see EncryptionRegistry#chaCha20Poly1305()
     */
    public ChaCha20Poly1305EncryptionProvider() {
    }

    /**
     * {@inheritDoc}
     *
     * @return the string identifier {@value #ID}
     */
    @Override
    public @NotNull String getId() {
        return ID;
    }

    /**
     * {@inheritDoc}
     *
     * @return the numeric identifier {@link FormatConstants#ENCRYPTION_CHACHA20_POLY1305}
     */
    @Override
    public int getNumericId() {
        return FormatConstants.ENCRYPTION_CHACHA20_POLY1305;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #KEY_SIZE} bytes (256 bits)
     */
    @Override
    public int getKeySize() {
        return KEY_SIZE;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #NONCE_SIZE} bytes (96 bits)
     */
    @Override
    public int getNonceSize() {
        return NONCE_SIZE;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #TAG_SIZE} bytes (128 bits)
     */
    @Override
    public int getTagSize() {
        return TAG_SIZE;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Wraps the output stream with ChaCha20-Poly1305 encryption.
     * A random 12-byte nonce is generated and written first.</p>
     *
     * @param output the underlying output stream; must not be {@code null}
     * @param key    the secret key; must be 32 bytes
     * @return an encrypting output stream; never returns {@code null}
     * @throws IOException if an I/O error occurs
     * @throws GeneralSecurityException if cipher initialization fails
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
     * <p>Wraps the output stream with ChaCha20-Poly1305 encryption with AAD.
     * Output format: {@code [12-byte nonce][ciphertext][16-byte tag]}</p>
     *
     * @param output the underlying output stream; must not be {@code null}
     * @param key    the secret key; must be 32 bytes
     * @param aad    additional authenticated data; may be {@code null}
     * @return an encrypting output stream; never returns {@code null}
     * @throws IOException if an I/O error occurs
     * @throws GeneralSecurityException if cipher initialization fails
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

        // Create cipher
        final ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        final KeyParameter keyParam = new KeyParameter(key.getEncoded());
        final AEADParameters params = new AEADParameters(keyParam, TAG_SIZE * 8, nonce, aad);
        cipher.init(true, params);

        return new ChaCha20Poly1305OutputStream(output, cipher);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Wraps the input stream with ChaCha20-Poly1305 decryption.
     * The 12-byte nonce is read from the beginning of the input.</p>
     *
     * @param input the underlying input stream; must not be {@code null}
     * @param key   the secret key; must be the same key used for encryption
     * @return a decrypting input stream; never returns {@code null}
     * @throws IOException if an I/O error occurs reading the nonce
     * @throws GeneralSecurityException if cipher initialization fails
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
     * <p>Wraps the input stream with ChaCha20-Poly1305 decryption with AAD.
     * The AAD must match what was provided during encryption.</p>
     *
     * @param input the underlying input stream; must not be {@code null}
     * @param key   the secret key; must be the same key used for encryption
     * @param aad   additional authenticated data; must match encryption
     * @return a decrypting input stream; never returns {@code null}
     * @throws IOException if an I/O error occurs reading the nonce
     * @throws GeneralSecurityException if cipher initialization fails
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

        // Create cipher
        final ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        final KeyParameter keyParam = new KeyParameter(key.getEncoded());
        final AEADParameters params = new AEADParameters(keyParam, TAG_SIZE * 8, nonce, aad);
        cipher.init(false, params);

        return new ChaCha20Poly1305InputStream(input, cipher);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Encrypts a byte array in a single operation.</p>
     *
     * @param plaintext the data to encrypt; must not be {@code null}
     * @param key       the secret key; must be 32 bytes
     * @return encrypted data including nonce and tag; never returns {@code null}
     * @throws GeneralSecurityException if encryption fails
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
     * <p>Encrypts a byte array with AAD in a single operation.
     * Output format: {@code [12-byte nonce][ciphertext][16-byte tag]}</p>
     *
     * @param plaintext the data to encrypt; must not be {@code null}
     * @param key       the secret key; must be 32 bytes
     * @param aad       additional authenticated data; may be {@code null}
     * @return encrypted data including nonce and tag; never returns {@code null}
     * @throws GeneralSecurityException if encryption fails
     */
    @Override
    public byte @NotNull [] encryptBlock(
            final byte @NotNull [] plaintext,
            final @NotNull SecretKey key,
            final byte @NotNull [] aad) throws GeneralSecurityException {

        // Generate random nonce
        final byte[] nonce = new byte[NONCE_SIZE];
        SECURE_RANDOM.nextBytes(nonce);

        // Create cipher
        final ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        final KeyParameter keyParam = new KeyParameter(key.getEncoded());
        final AEADParameters params = new AEADParameters(keyParam, TAG_SIZE * 8, nonce, aad);
        cipher.init(true, params);

        // Encrypt
        final byte[] ciphertext = new byte[cipher.getOutputSize(plaintext.length)];
        int len = cipher.processBytes(plaintext, 0, plaintext.length, ciphertext, 0);
        try {
            len += cipher.doFinal(ciphertext, len);
        } catch (final InvalidCipherTextException e) {
            throw new GeneralSecurityException("Encryption failed", e);
        }

        // Combine: nonce + ciphertext
        final byte[] result = new byte[NONCE_SIZE + len];
        System.arraycopy(nonce, 0, result, 0, NONCE_SIZE);
        System.arraycopy(ciphertext, 0, result, NONCE_SIZE, len);

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Decrypts a byte array in a single operation.</p>
     *
     * @param ciphertext encrypted data including nonce and tag
     * @param key        the secret key; must be the same key used for encryption
     * @return decrypted plaintext; never returns {@code null}
     * @throws GeneralSecurityException if decryption or authentication fails
     * @throws IllegalArgumentException if ciphertext is too short
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
     * <p>Decrypts a byte array with AAD verification in a single operation.</p>
     *
     * @param ciphertext encrypted data including nonce and tag
     * @param key        the secret key; must be the same key used for encryption
     * @param aad        additional authenticated data; must match encryption
     * @return decrypted plaintext; never returns {@code null}
     * @throws GeneralSecurityException if decryption or authentication fails
     * @throws IllegalArgumentException if ciphertext is too short
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

        // Create cipher
        final ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        final KeyParameter keyParam = new KeyParameter(key.getEncoded());
        final AEADParameters params = new AEADParameters(keyParam, TAG_SIZE * 8, nonce, aad);
        cipher.init(false, params);

        // Decrypt
        final int encryptedLen = ciphertext.length - NONCE_SIZE;
        final byte[] plaintext = new byte[cipher.getOutputSize(encryptedLen)];
        int len = cipher.processBytes(ciphertext, NONCE_SIZE, encryptedLen, plaintext, 0);
        try {
            len += cipher.doFinal(plaintext, len);
        } catch (final InvalidCipherTextException e) {
            throw new GeneralSecurityException("Decryption failed: authentication tag mismatch", e);
        }

        // Return exact size
        if (len == plaintext.length) {
            return plaintext;
        }
        final byte[] result = new byte[len];
        System.arraycopy(plaintext, 0, result, 0, len);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Generates a cryptographically secure random 256-bit key.</p>
     *
     * @return a new randomly generated secret key; never returns {@code null}
     * @throws GeneralSecurityException should not occur
     */
    @Override
    public @NotNull SecretKey generateKey() throws GeneralSecurityException {
        final byte[] keyBytes = new byte[KEY_SIZE];
        SECURE_RANDOM.nextBytes(keyBytes);
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }

    /**
     * Creates a secret key from raw key bytes.
     *
     * <p>This method wraps existing key material into a {@link SecretKey}
     * object suitable for use with this provider.</p>
     *
     * @param keyBytes the raw key bytes; must not be {@code null};
     *                 must be exactly 32 bytes (256 bits)
     * @return a new secret key wrapping the provided bytes; never returns {@code null}
     * @throws IllegalArgumentException if {@code keyBytes.length != 32}
     *
     * @see #generateKey()
     */
    public static @NotNull SecretKey createKey(final byte @NotNull [] keyBytes) {
        if (keyBytes.length != KEY_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid key size: expected " + KEY_SIZE + ", got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }

    /**
     * Reads exactly {@code buffer.length} bytes from the input stream.
     *
     * @param input  the input stream to read from
     * @param buffer the buffer to fill
     * @return the number of bytes actually read
     * @throws IOException if an I/O error occurs
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

    /**
     * Output stream that encrypts data using ChaCha20-Poly1305.
     *
     * <p>This stream buffers plaintext, encrypts it incrementally, and writes
     * ciphertext to the delegate. The authentication tag is written when
     * the stream is closed.</p>
     */
    private static final class ChaCha20Poly1305OutputStream extends OutputStream {

        /** The underlying output stream to write encrypted data to. */
        private final @NotNull OutputStream delegate;

        /** The BouncyCastle ChaCha20-Poly1305 AEAD cipher. */
        private final @NotNull ChaCha20Poly1305 cipher;

        /** Single-byte buffer for the write(int) method. */
        private final byte @NotNull [] singleByte = new byte[1];

        /** Whether this stream has been closed. */
        private boolean closed;

        ChaCha20Poly1305OutputStream(
                final @NotNull OutputStream delegate,
                final @NotNull ChaCha20Poly1305 cipher) {
            this.delegate = delegate;
            this.cipher = cipher;
            this.closed = false;
        }

        @Override
        public void write(final int b) throws IOException {
            this.singleByte[0] = (byte) b;
            write(this.singleByte, 0, 1);
        }

        @Override
        public void write(final byte @NotNull [] b, final int off, final int len) throws IOException {
            if (this.closed) {
                throw new IOException("Stream closed");
            }
            final byte[] output = new byte[this.cipher.getUpdateOutputSize(len)];
            final int outputLen = this.cipher.processBytes(b, off, len, output, 0);
            if (outputLen > 0) {
                this.delegate.write(output, 0, outputLen);
            }
        }

        @Override
        public void flush() throws IOException {
            this.delegate.flush();
        }

        @Override
        public void close() throws IOException {
            if (this.closed) {
                return;
            }
            this.closed = true;

            try {
                final byte[] finalOutput = new byte[this.cipher.getOutputSize(0)];
                final int len = this.cipher.doFinal(finalOutput, 0);
                if (len > 0) {
                    this.delegate.write(finalOutput, 0, len);
                }
            } catch (final InvalidCipherTextException e) {
                throw new IOException("Encryption finalization failed", e);
            } finally {
                this.delegate.close();
            }
        }

    }

    /**
     * Input stream that decrypts data using ChaCha20-Poly1305.
     *
     * <p>Due to AEAD requirements, this stream must buffer the entire
     * ciphertext before the authentication tag can be verified. For
     * large data, use chunked encryption instead.</p>
     *
     * <p><strong>Note:</strong> The entire ciphertext is read and decrypted
     * on the first read operation. This is necessary because AEAD authentication
     * cannot be verified until all data is processed.</p>
     */
    private static final class ChaCha20Poly1305InputStream extends InputStream {

        /** The underlying input stream containing encrypted data. */
        private final @NotNull InputStream delegate;

        /** The BouncyCastle ChaCha20-Poly1305 AEAD cipher. */
        private final @NotNull ChaCha20Poly1305 cipher;

        /** Buffer containing decrypted plaintext after finalization. */
        private byte @NotNull [] plaintext;

        /** Current read position within the plaintext buffer. */
        private int position;

        /** Whether decryption has been performed. */
        private boolean finalized;

        ChaCha20Poly1305InputStream(
                final @NotNull InputStream delegate,
                final @NotNull ChaCha20Poly1305 cipher) {
            this.delegate = delegate;
            this.cipher = cipher;
            this.plaintext = new byte[0];
            this.position = 0;
            this.finalized = false;
        }

        @Override
        public int read() throws IOException {
            ensureDecrypted();
            if (this.position >= this.plaintext.length) {
                return -1;
            }
            return this.plaintext[this.position++] & 0xFF;
        }

        @Override
        public int read(final byte @NotNull [] b, final int off, final int len) throws IOException {
            ensureDecrypted();
            if (this.position >= this.plaintext.length) {
                return -1;
            }
            final int available = this.plaintext.length - this.position;
            final int toRead = Math.min(len, available);
            System.arraycopy(this.plaintext, this.position, b, off, toRead);
            this.position += toRead;
            return toRead;
        }

        @Override
        public int available() throws IOException {
            ensureDecrypted();
            return this.plaintext.length - this.position;
        }

        @Override
        public void close() throws IOException {
            this.delegate.close();
        }

        private void ensureDecrypted() throws IOException {
            if (this.finalized) {
                return;
            }
            this.finalized = true;

            // Read all remaining data
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = this.delegate.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            final byte[] ciphertext = baos.toByteArray();

            // Decrypt
            final byte[] output = new byte[this.cipher.getOutputSize(ciphertext.length)];
            int len = this.cipher.processBytes(ciphertext, 0, ciphertext.length, output, 0);
            try {
                len += this.cipher.doFinal(output, len);
            } catch (final InvalidCipherTextException e) {
                throw new IOException("Decryption failed: authentication tag mismatch", e);
            }

            // Store plaintext
            if (len == output.length) {
                this.plaintext = output;
            } else {
                this.plaintext = new byte[len];
                System.arraycopy(output, 0, this.plaintext, 0, len);
            }
        }

    }

}
