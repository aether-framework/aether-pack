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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

/**
 * Pipeline stage for data encryption and decryption.
 *
 * <p>This stage wraps I/O streams with encryption (on write) or
 * decryption (on read) using a pluggable {@link EncryptionProvider}.
 * It operates at priority 300, making it the outermost layer in the
 * standard pipeline configuration.</p>
 *
 * <h2>Processing Order</h2>
 * <pre>
 * Write: Raw Data → [Checksum] → [Compress] → [Encrypt] → Output
 * Read:  Input → [Decrypt] → [Decompress] → [Checksum] → Raw Data
 * </pre>
 *
 * <p>Encryption is applied last during writing, ensuring that
 * the compressed data (if compression is enabled) is encrypted.
 * This provides the most efficient and secure processing order.</p>
 *
 * <h2>Context Values</h2>
 * <p>This stage updates the following context values:</p>
 * <ul>
 *   <li>{@link PipelineContext#ENCRYPTION_ID} - The algorithm ID used</li>
 *   <li>{@link PipelineContext#ENCRYPTED_SIZE} - Total encrypted bytes written</li>
 * </ul>
 *
 * <h2>AEAD Properties</h2>
 * <p>All supported algorithms use Authenticated Encryption with
 * Associated Data (AEAD), providing:</p>
 * <ul>
 *   <li><strong>Confidentiality</strong> - Data is encrypted</li>
 *   <li><strong>Integrity</strong> - Tampering is detected</li>
 *   <li><strong>Authenticity</strong> - Origin is verified</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * EncryptionProvider aes = EncryptionRegistry.requireByName("aes-256-gcm");
 * SecretKey key = aes.generateKey();
 * EncryptionConfig config = EncryptionConfig.of(aes, key);
 *
 * ProcessingPipeline pipeline = ProcessingPipeline.builder()
 *     .addStage(new EncryptionStage(), config)
 *     .build();
 * }</pre>
 *
 * <h2>Key Validation</h2>
 * <p>The {@link #validateConfig} method ensures the provided key
 * has the correct length for the selected algorithm (32 bytes for
 * both AES-256-GCM and ChaCha20-Poly1305).</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is stateless and thread-safe. Each stream wrapping
 * operation creates new independent cipher instances.</p>
 *
 * @see EncryptionConfig
 * @see EncryptionProvider
 * @see PipelineStage
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class EncryptionStage implements PipelineStage<EncryptionConfig> {

    /** Stage order priority (300 = outermost layer). */
    public static final int ORDER = 300;

    /** Stage identifier. */
    public static final String ID = "encryption";

    /**
     * Creates a new encryption stage instance.
     *
     * <p>The encryption stage is stateless and can be safely shared across
     * multiple pipelines. Each invocation of {@link #wrapOutput} or
     * {@link #wrapInput} creates independent cipher instances with fresh
     * nonces, so a single stage instance can be used concurrently.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * EncryptionStage stage = new EncryptionStage();
     *
     * // Create a key for the session
     * EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
     * SecretKey key = aes.generateKey();
     *
     * ProcessingPipeline pipeline = ProcessingPipeline.builder()
     *     .addStage(stage, EncryptionConfig.of(aes, key))
     *     .build();
     * }</pre>
     *
     * @see EncryptionConfig
     * @see ProcessingPipeline.Builder#addStage(PipelineStage, Object)
     */
    public EncryptionStage() {
    }

    /**
     * {@inheritDoc}
     *
     * @return the stage identifier "{@value #ID}"; this ID is used to
     *         prevent duplicate stage registration in a pipeline and
     *         for debugging/logging purposes
     */
    @Override
    public @NotNull String getId() {
        return ID;
    }

    /**
     * {@inheritDoc}
     *
     * @return the order priority {@value #ORDER}; this places the encryption
     *         stage as the outermost layer, ensuring data is encrypted after
     *         both checksum computation and compression
     */
    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The encryption stage is enabled when both an encryption provider and
     * a key are configured in the {@link EncryptionConfig}. When disabled,
     * this stage passes data through unchanged without any encryption.</p>
     *
     * @param config the encryption configuration to check; must not be {@code null}
     * @return {@code true} if both a provider and key are configured,
     *         {@code false} if either is {@code null}
     */
    @Override
    public boolean isEnabled(final @NotNull EncryptionConfig config) {
        return config.isEnabled();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method wraps the output stream with an encrypting stream from
     * the configured provider. Data written to the returned stream is
     * encrypted before being passed to the underlying stream.</p>
     *
     * <p>The encryption algorithm ID is stored in the pipeline context under
     * {@link PipelineContext#ENCRYPTION_ID}, and the encrypted byte count
     * is stored under {@link PipelineContext#ENCRYPTED_SIZE} when the stream
     * is closed.</p>
     *
     * <p>If Additional Authenticated Data (AAD) is configured, it is passed
     * to the cipher for authentication. The AAD is not encrypted but is
     * protected against modification.</p>
     *
     * @param output  the underlying output stream to wrap; must not be {@code null};
     *                encrypted data will be written to this stream
     * @param config  the encryption configuration containing the provider, key,
     *                and optional AAD; must not be {@code null}; if the provider
     *                or key is {@code null}, the output stream is returned unchanged
     * @param context the pipeline context where encryption metadata will be
     *                stored; must not be {@code null}; after the stream is closed,
     *                {@link PipelineContext#ENCRYPTED_SIZE} will contain the
     *                ciphertext byte count
     * @return a wrapped output stream that encrypts data, or the original
     *         stream if encryption is not configured; never returns {@code null}
     * @throws IOException if cipher initialization fails or an I/O error occurs;
     *         the cause may be a {@link GeneralSecurityException} from the
     *         underlying cryptographic operations
     */
    @Override
    public @NotNull OutputStream wrapOutput(
            final @NotNull OutputStream output,
            final @NotNull EncryptionConfig config,
            final @NotNull PipelineContext context) throws IOException {

        final EncryptionProvider provider = config.provider();
        if (provider == null || config.key() == null) {
            return output;
        }

        // Store encryption info in context
        context.set(PipelineContext.ENCRYPTION_ID, provider.getNumericId());

        try {
            final OutputStream encryptedOutput;
            if (config.hasAad()) {
                encryptedOutput = provider.encrypt(output, config.key(), config.aad());
            } else {
                encryptedOutput = provider.encrypt(output, config.key());
            }
            return new CountingEncryptionOutputStream(encryptedOutput, context);
        } catch (final GeneralSecurityException e) {
            throw new IOException("Encryption setup failed", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method wraps the input stream with a decrypting stream from
     * the configured provider. Data read from the returned stream is
     * automatically decrypted and authenticated.</p>
     *
     * <p>If Additional Authenticated Data (AAD) is configured, it must match
     * the AAD used during encryption, or decryption will fail with an
     * authentication error.</p>
     *
     * <p>All supported algorithms use AEAD (Authenticated Encryption with
     * Associated Data), so any tampering with the ciphertext will be
     * detected and cause an exception during decryption.</p>
     *
     * @param input   the underlying input stream to wrap; must not be {@code null};
     *                encrypted data will be read from this stream
     * @param config  the encryption configuration containing the provider, key,
     *                and optional AAD; must not be {@code null}; the key and
     *                AAD must match those used during encryption
     * @param context the pipeline context for sharing state; must not be {@code null};
     *                not currently used by this method but required by the interface
     * @return a wrapped input stream that decrypts data, or the original
     *         stream if encryption is not configured; never returns {@code null}
     * @throws IOException if cipher initialization fails or an I/O error occurs;
     *         the cause may be a {@link GeneralSecurityException} from the
     *         underlying cryptographic operations
     */
    @Override
    public @NotNull InputStream wrapInput(
            final @NotNull InputStream input,
            final @NotNull EncryptionConfig config,
            final @NotNull PipelineContext context) throws IOException {

        final EncryptionProvider provider = config.provider();
        if (provider == null || config.key() == null) {
            return input;
        }

        try {
            if (config.hasAad()) {
                return provider.decrypt(input, config.key(), config.aad());
            } else {
                return provider.decrypt(input, config.key());
            }
        } catch (final GeneralSecurityException e) {
            throw new IOException("Decryption setup failed", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method validates that the provided encryption key has the
     * correct size for the configured provider. Both AES-256-GCM and
     * ChaCha20-Poly1305 require 32-byte (256-bit) keys.</p>
     *
     * <p>Key validation is performed early (at pipeline construction time)
     * to fail fast rather than encountering cryptographic errors during
     * actual data processing.</p>
     *
     * @param config the encryption configuration to validate; must not be
     *               {@code null}; validation is only performed if both a
     *               provider and key are configured
     * @throws IllegalArgumentException if the key size doesn't match the
     *         provider's requirements; the exception message includes both
     *         the expected and actual key sizes
     */
    @Override
    public void validateConfig(final @NotNull EncryptionConfig config) {
        if (config.provider() != null && config.key() != null) {
            final int expectedKeySize = config.provider().getKeySize();
            final int actualKeySize = config.key().getEncoded().length;
            if (actualKeySize != expectedKeySize) {
                throw new IllegalArgumentException(
                        "Invalid key size for " + config.provider().getId() +
                                ": expected " + expectedKeySize + " bytes, got " + actualKeySize);
            }
        }
    }

    /**
     * Output stream wrapper that tracks encrypted byte count for the pipeline context.
     *
     * <p>This internal class wraps an encryption stream and counts the bytes
     * written (before encryption). This provides the plaintext size, which
     * can be useful for progress reporting and size verification.</p>
     *
     * <p>The byte count represents the data passed to the encryption stream,
     * not the ciphertext output size. The ciphertext will typically be
     * slightly larger due to authentication tags and nonces.</p>
     */
    private static final class CountingEncryptionOutputStream extends OutputStream {

        /** The underlying encryption stream to write data to. */
        private final @NotNull OutputStream delegate;

        /** The pipeline context for storing the encrypted byte count. */
        private final @NotNull PipelineContext context;

        /** Counter tracking bytes written before encryption. */
        private long bytesWritten;

        /**
         * Constructs a new counting encryption output stream.
         *
         * <p>The stream wraps an encryption stream and tracks all bytes written
         * to it. When closed, the total byte count is stored in the pipeline
         * context.</p>
         *
         * @param delegate the underlying encryption stream to write data to;
         *                 must not be {@code null}; this should be a stream
         *                 returned by {@link EncryptionProvider#encrypt}
         * @param context  the pipeline context where the byte count will be
         *                 stored when the stream is closed; must not be {@code null};
         *                 the count is stored under {@link PipelineContext#ENCRYPTED_SIZE}
         */
        CountingEncryptionOutputStream(
                final @NotNull OutputStream delegate,
                final @NotNull PipelineContext context) {
            this.delegate = delegate;
            this.context = context;
            this.bytesWritten = 0;
        }

        /**
         * Writes a single byte and increments the byte counter.
         *
         * @param b the byte to write
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void write(final int b) throws IOException {
            this.delegate.write(b);
            this.bytesWritten++;
        }

        /**
         * Writes a byte array and updates the byte counter.
         *
         * @param b   the byte array
         * @param off the start offset in the array
         * @param len the number of bytes to write
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void write(final byte @NotNull [] b, final int off, final int len) throws IOException {
            this.delegate.write(b, off, len);
            this.bytesWritten += len;
        }

        /**
         * Flushes the underlying stream.
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void flush() throws IOException {
            this.delegate.flush();
        }

        /**
         * Closes the stream and stores the encrypted size in the context.
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void close() throws IOException {
            this.delegate.close();
            // Update context with encrypted size
            this.context.set(PipelineContext.ENCRYPTED_SIZE, this.bytesWritten);
        }

    }

}
