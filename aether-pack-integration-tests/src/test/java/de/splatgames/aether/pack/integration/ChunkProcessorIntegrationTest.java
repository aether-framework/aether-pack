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

package de.splatgames.aether.pack.integration;

import de.splatgames.aether.pack.compression.Lz4CompressionProvider;
import de.splatgames.aether.pack.compression.ZstdCompressionProvider;
import de.splatgames.aether.pack.core.io.ChunkProcessor;
import de.splatgames.aether.pack.core.spi.CompressionProvider;
import de.splatgames.aether.pack.core.spi.EncryptionProvider;
import de.splatgames.aether.pack.crypto.Aes256GcmEncryptionProvider;
import de.splatgames.aether.pack.crypto.Argon2idKeyDerivation;
import de.splatgames.aether.pack.crypto.ChaCha20Poly1305EncryptionProvider;
import de.splatgames.aether.pack.crypto.KeyWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the full write/read cycle using ChunkProcessor
 * with actual compression and encryption providers.
 */
@DisplayName("ChunkProcessor Integration")
class ChunkProcessorIntegrationTest {

    private static final Random RANDOM = new Random(42);

    // ==================== Provider Combinations ====================

    static Stream<Arguments> compressionProviders() {
        return Stream.of(
                Arguments.of("ZSTD", new ZstdCompressionProvider()),
                Arguments.of("LZ4", new Lz4CompressionProvider())
        );
    }

    static Stream<Arguments> encryptionProviders() throws GeneralSecurityException {
        final Aes256GcmEncryptionProvider aesProvider = new Aes256GcmEncryptionProvider();
        final ChaCha20Poly1305EncryptionProvider chachaProvider = new ChaCha20Poly1305EncryptionProvider();

        return Stream.of(
                Arguments.of("AES-256-GCM", aesProvider, aesProvider.generateKey()),
                Arguments.of("ChaCha20-Poly1305", chachaProvider, chachaProvider.generateKey())
        );
    }

    static Stream<Arguments> allCombinations() throws GeneralSecurityException {
        final Aes256GcmEncryptionProvider aesProvider = new Aes256GcmEncryptionProvider();
        final ChaCha20Poly1305EncryptionProvider chachaProvider = new ChaCha20Poly1305EncryptionProvider();

        return Stream.of(
                // Compression only
                Arguments.of("ZSTD only", new ZstdCompressionProvider(), null, null),
                Arguments.of("LZ4 only", new Lz4CompressionProvider(), null, null),
                // Encryption only
                Arguments.of("AES-256-GCM only", null, aesProvider, aesProvider.generateKey()),
                Arguments.of("ChaCha20 only", null, chachaProvider, chachaProvider.generateKey()),
                // Both
                Arguments.of("ZSTD + AES-256-GCM", new ZstdCompressionProvider(), aesProvider, aesProvider.generateKey()),
                Arguments.of("ZSTD + ChaCha20", new ZstdCompressionProvider(), chachaProvider, chachaProvider.generateKey()),
                Arguments.of("LZ4 + AES-256-GCM", new Lz4CompressionProvider(), aesProvider, aesProvider.generateKey()),
                Arguments.of("LZ4 + ChaCha20", new Lz4CompressionProvider(), chachaProvider, chachaProvider.generateKey())
        );
    }

    // ==================== Compression Only Tests ====================

    @Nested
    @DisplayName("Compression Only")
    class CompressionOnlyTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.splatgames.aether.pack.integration.ChunkProcessorIntegrationTest#compressionProviders")
        @DisplayName("should round-trip text data")
        void shouldRoundTripTextData(final String name, final CompressionProvider provider) throws IOException {
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .compression(provider)
                    .build();

            final byte[] original = "Hello, World! This is a test of compression. ".repeat(100)
                    .getBytes(StandardCharsets.UTF_8);

            final ChunkProcessor.ProcessedChunk written = processor.processForWrite(original, original.length);
            final byte[] read = processor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted());

            assertThat(read).isEqualTo(original);
            assertThat(written.compressed()).isTrue();
            assertThat(written.encrypted()).isFalse();
            assertThat(written.storedSize()).isLessThan(original.length);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.splatgames.aether.pack.integration.ChunkProcessorIntegrationTest#compressionProviders")
        @DisplayName("should round-trip random data")
        void shouldRoundTripRandomData(final String name, final CompressionProvider provider) throws IOException {
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .compression(provider)
                    .build();

            final byte[] original = randomBytes(64 * 1024); // 64 KB

            final ChunkProcessor.ProcessedChunk written = processor.processForWrite(original, original.length);
            final byte[] read = processor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted());

            assertThat(read).isEqualTo(original);
            // Random data may not compress well
            assertThat(written.encrypted()).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.splatgames.aether.pack.integration.ChunkProcessorIntegrationTest#compressionProviders")
        @DisplayName("should skip compression when data expands")
        void shouldSkipCompressionWhenDataExpands(final String name, final CompressionProvider provider) throws IOException {
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .compression(provider)
                    .build();

            // Already compressed or random data that won't compress
            final byte[] original = randomBytes(100);

            final ChunkProcessor.ProcessedChunk written = processor.processForWrite(original, original.length);
            final byte[] read = processor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted());

            assertThat(read).isEqualTo(original);
            // If compression wasn't beneficial, compressed flag should be false
            if (!written.compressed()) {
                assertThat(written.storedSize()).isEqualTo(original.length);
            }
        }

    }

    // ==================== Encryption Only Tests ====================

    @Nested
    @DisplayName("Encryption Only")
    class EncryptionOnlyTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.splatgames.aether.pack.integration.ChunkProcessorIntegrationTest#encryptionProviders")
        @DisplayName("should round-trip text data")
        void shouldRoundTripTextData(final String name, final EncryptionProvider provider, final SecretKey key)
                throws IOException {

            final ChunkProcessor processor = ChunkProcessor.builder()
                    .encryption(provider, key)
                    .build();

            final byte[] original = "Secret message that needs encryption!"
                    .getBytes(StandardCharsets.UTF_8);

            final ChunkProcessor.ProcessedChunk written = processor.processForWrite(original, original.length);
            final byte[] read = processor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted());

            assertThat(read).isEqualTo(original);
            assertThat(written.compressed()).isFalse();
            assertThat(written.encrypted()).isTrue();
            // Encrypted data includes nonce + tag
            assertThat(written.storedSize()).isGreaterThan(original.length);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.splatgames.aether.pack.integration.ChunkProcessorIntegrationTest#encryptionProviders")
        @DisplayName("should round-trip large data")
        void shouldRoundTripLargeData(final String name, final EncryptionProvider provider, final SecretKey key)
                throws IOException {

            final ChunkProcessor processor = ChunkProcessor.builder()
                    .encryption(provider, key)
                    .build();

            final byte[] original = randomBytes(1024 * 1024); // 1 MB

            final ChunkProcessor.ProcessedChunk written = processor.processForWrite(original, original.length);
            final byte[] read = processor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted());

            assertThat(read).isEqualTo(original);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.splatgames.aether.pack.integration.ChunkProcessorIntegrationTest#encryptionProviders")
        @DisplayName("should fail decryption with wrong key")
        void shouldFailWithWrongKey(final String name, final EncryptionProvider provider, final SecretKey key)
                throws IOException, GeneralSecurityException {

            final ChunkProcessor writeProcessor = ChunkProcessor.builder()
                    .encryption(provider, key)
                    .build();

            // Create a different key for reading
            final SecretKey wrongKey = provider.generateKey();
            final ChunkProcessor readProcessor = ChunkProcessor.builder()
                    .encryption(provider, wrongKey)
                    .build();

            final byte[] original = "Secret message".getBytes(StandardCharsets.UTF_8);
            final ChunkProcessor.ProcessedChunk written = writeProcessor.processForWrite(original, original.length);

            assertThatThrownBy(() -> readProcessor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Decryption failed");
        }

    }

    // ==================== Combined Compression + Encryption Tests ====================

    @Nested
    @DisplayName("Compression + Encryption")
    class CombinedTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.splatgames.aether.pack.integration.ChunkProcessorIntegrationTest#allCombinations")
        @DisplayName("should round-trip data correctly")
        void shouldRoundTripData(
                final String name,
                final CompressionProvider compressionProvider,
                final EncryptionProvider encryptionProvider,
                final SecretKey key) throws IOException {

            final ChunkProcessor.Builder builder = ChunkProcessor.builder();
            if (compressionProvider != null) {
                builder.compression(compressionProvider);
            }
            if (encryptionProvider != null && key != null) {
                builder.encryption(encryptionProvider, key);
            }
            final ChunkProcessor processor = builder.build();

            final byte[] original = "Test data for combined processing. ".repeat(50)
                    .getBytes(StandardCharsets.UTF_8);

            final ChunkProcessor.ProcessedChunk written = processor.processForWrite(original, original.length);
            final byte[] read = processor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted());

            assertThat(read).isEqualTo(original);
        }

        @Test
        @DisplayName("should compress then encrypt in correct order")
        void shouldCompressThenEncrypt() throws IOException, GeneralSecurityException {
            final ZstdCompressionProvider compression = new ZstdCompressionProvider();
            final Aes256GcmEncryptionProvider encryption = new Aes256GcmEncryptionProvider();
            final SecretKey key = encryption.generateKey();

            final ChunkProcessor processor = ChunkProcessor.builder()
                    .compression(compression)
                    .encryption(encryption, key)
                    .build();

            final byte[] original = "Highly compressible data! ".repeat(1000)
                    .getBytes(StandardCharsets.UTF_8);

            final ChunkProcessor.ProcessedChunk written = processor.processForWrite(original, original.length);

            // Data should be compressed then encrypted
            assertThat(written.compressed()).isTrue();
            assertThat(written.encrypted()).isTrue();

            // Verify we can read it back
            final byte[] read = processor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted());
            assertThat(read).isEqualTo(original);
        }

    }

    // ==================== Key Derivation Integration Tests ====================

    @Nested
    @DisplayName("Key Derivation Integration")
    class KeyDerivationIntegrationTests {

        @Test
        @DisplayName("should encrypt with password-derived key using Argon2id")
        void shouldEncryptWithArgon2idDerivedKey() throws IOException, GeneralSecurityException {
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation(
                    8 * 1024, 1, 1, 16); // Low params for fast test
            final byte[] salt = kdf.generateSalt();
            final char[] password = "securePassword123!".toCharArray();

            // Derive key from password and convert to AES key
            final SecretKey derivedKey = kdf.deriveKey(password, salt, 32);
            final SecretKey key = Aes256GcmEncryptionProvider.createKey(derivedKey.getEncoded());

            final Aes256GcmEncryptionProvider encryption = new Aes256GcmEncryptionProvider();
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .encryption(encryption, key)
                    .build();

            final byte[] original = "Password-protected secret data".getBytes(StandardCharsets.UTF_8);
            final ChunkProcessor.ProcessedChunk written = processor.processForWrite(original, original.length);

            // Derive the same key again to decrypt
            final SecretKey sameDerivedKey = kdf.deriveKey(password, salt, 32);
            final SecretKey sameKey = Aes256GcmEncryptionProvider.createKey(sameDerivedKey.getEncoded());
            final ChunkProcessor readProcessor = ChunkProcessor.builder()
                    .encryption(encryption, sameKey)
                    .build();

            final byte[] read = readProcessor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted());

            assertThat(read).isEqualTo(original);
        }

        @Test
        @DisplayName("should fail with wrong password")
        void shouldFailWithWrongPassword() throws IOException, GeneralSecurityException {
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation(
                    8 * 1024, 1, 1, 16);
            final byte[] salt = kdf.generateSalt();

            // Encrypt with correct password
            final SecretKey derivedCorrectKey = kdf.deriveKey("correct".toCharArray(), salt, 32);
            final SecretKey correctKey = Aes256GcmEncryptionProvider.createKey(derivedCorrectKey.getEncoded());
            final Aes256GcmEncryptionProvider encryption = new Aes256GcmEncryptionProvider();
            final ChunkProcessor writeProcessor = ChunkProcessor.builder()
                    .encryption(encryption, correctKey)
                    .build();

            final byte[] original = "Secret".getBytes(StandardCharsets.UTF_8);
            final ChunkProcessor.ProcessedChunk written = writeProcessor.processForWrite(original, original.length);

            // Try to decrypt with wrong password
            final SecretKey derivedWrongKey = kdf.deriveKey("wrong".toCharArray(), salt, 32);
            final SecretKey wrongKey = Aes256GcmEncryptionProvider.createKey(derivedWrongKey.getEncoded());
            final ChunkProcessor readProcessor = ChunkProcessor.builder()
                    .encryption(encryption, wrongKey)
                    .build();

            assertThatThrownBy(() -> readProcessor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted()))
                    .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("should work with KeyWrapper for wrapped keys")
        void shouldWorkWithWrappedKeys() throws IOException, GeneralSecurityException {
            // Generate content encryption key (CEK)
            final SecretKey cek = KeyWrapper.generateAes256Key();

            // Derive key encryption key (KEK) from password
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation(
                    8 * 1024, 1, 1, 16);
            final byte[] salt = kdf.generateSalt();

            // Wrap the CEK using the convenience method that handles key conversion
            final byte[] wrappedCek = KeyWrapper.wrapWithPassword(cek, "password".toCharArray(), salt, kdf);

            // Use CEK for encryption
            final Aes256GcmEncryptionProvider encryption = new Aes256GcmEncryptionProvider();
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .compression(new ZstdCompressionProvider())
                    .encryption(encryption, cek)
                    .build();

            final byte[] original = "Data encrypted with wrapped key. ".repeat(100)
                    .getBytes(StandardCharsets.UTF_8);
            final ChunkProcessor.ProcessedChunk written = processor.processForWrite(original, original.length);

            // Unwrap the CEK and decrypt using the convenience method
            final SecretKey unwrappedCek = KeyWrapper.unwrapWithPassword(
                    wrappedCek, "password".toCharArray(), salt, kdf, "AES");

            final ChunkProcessor readProcessor = ChunkProcessor.builder()
                    .compression(new ZstdCompressionProvider())
                    .encryption(encryption, unwrappedCek)
                    .build();

            final byte[] read = readProcessor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted());

            assertThat(read).isEqualTo(original);
        }

    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty data with compression + encryption")
        void shouldHandleEmptyData() throws IOException, GeneralSecurityException {
            final Aes256GcmEncryptionProvider encryption = new Aes256GcmEncryptionProvider();
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .compression(new ZstdCompressionProvider())
                    .encryption(encryption, encryption.generateKey())
                    .build();

            final byte[] original = new byte[0];

            final ChunkProcessor.ProcessedChunk written = processor.processForWrite(original, 0);
            final byte[] read = processor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted());

            assertThat(read).isEmpty();
        }

        @Test
        @DisplayName("should handle single byte with encryption")
        void shouldHandleSingleByte() throws IOException, GeneralSecurityException {
            final ChaCha20Poly1305EncryptionProvider encryption = new ChaCha20Poly1305EncryptionProvider();
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .encryption(encryption, encryption.generateKey())
                    .build();

            final byte[] original = new byte[]{0x42};

            final ChunkProcessor.ProcessedChunk written = processor.processForWrite(original, 1);
            final byte[] read = processor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), written.encrypted());

            assertThat(read).isEqualTo(original);
        }

        @Test
        @DisplayName("should fail when reading encrypted data without key")
        void shouldFailReadingEncryptedDataWithoutKey() throws IOException, GeneralSecurityException {
            final Aes256GcmEncryptionProvider encryption = new Aes256GcmEncryptionProvider();
            final ChunkProcessor writeProcessor = ChunkProcessor.builder()
                    .encryption(encryption, encryption.generateKey())
                    .build();

            final byte[] original = "Encrypted data".getBytes(StandardCharsets.UTF_8);
            final ChunkProcessor.ProcessedChunk written = writeProcessor.processForWrite(original, original.length);

            // Try to read without encryption configured
            final ChunkProcessor readProcessor = ChunkProcessor.passThrough();

            assertThatThrownBy(() -> readProcessor.processForRead(
                    written.data(), written.originalSize(), written.compressed(), true))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("no encryption key provided");
        }

        @Test
        @DisplayName("should fail when reading compressed data without provider")
        void shouldFailReadingCompressedDataWithoutProvider() throws IOException {
            final ChunkProcessor writeProcessor = ChunkProcessor.builder()
                    .compression(new ZstdCompressionProvider())
                    .build();

            final byte[] original = "Compressible data! ".repeat(50).getBytes(StandardCharsets.UTF_8);
            final ChunkProcessor.ProcessedChunk written = writeProcessor.processForWrite(original, original.length);

            // Try to read without compression configured
            final ChunkProcessor readProcessor = ChunkProcessor.passThrough();

            assertThatThrownBy(() -> readProcessor.processForRead(
                    written.data(), written.originalSize(), true, written.encrypted()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("no compression provider");
        }

    }

    // ==================== Performance Characteristics ====================

    @Nested
    @DisplayName("Performance Characteristics")
    class PerformanceTests {

        @Test
        @DisplayName("ZSTD should achieve better compression than LZ4 at high levels")
        void zstdShouldCompressBetterThanLz4() throws IOException {
            final ZstdCompressionProvider zstd = new ZstdCompressionProvider();
            final Lz4CompressionProvider lz4 = new Lz4CompressionProvider();

            // Compressible data
            final byte[] original = "The quick brown fox jumps over the lazy dog. ".repeat(1000)
                    .getBytes(StandardCharsets.UTF_8);

            final ChunkProcessor zstdProcessor = ChunkProcessor.builder()
                    .compression(zstd, 19) // High compression
                    .build();

            final ChunkProcessor lz4Processor = ChunkProcessor.builder()
                    .compression(lz4, 17) // Max HC level
                    .build();

            final ChunkProcessor.ProcessedChunk zstdResult = zstdProcessor.processForWrite(original, original.length);
            final ChunkProcessor.ProcessedChunk lz4Result = lz4Processor.processForWrite(original, original.length);

            // ZSTD at high levels typically compresses better
            assertThat(zstdResult.storedSize()).isLessThan(lz4Result.storedSize());
        }

    }

    // ==================== Utilities ====================

    private static byte[] randomBytes(final int length) {
        final byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

}
