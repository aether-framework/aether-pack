package de.splatgames.aether.pack.crypto;

import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChaCha20Poly1305EncryptionProvider")
class ChaCha20Poly1305EncryptionProviderTest {

    private ChaCha20Poly1305EncryptionProvider provider;
    private SecretKey key;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        this.provider = new ChaCha20Poly1305EncryptionProvider();
        this.key = this.provider.generateKey();
    }

    @Nested
    @DisplayName("metadata")
    class MetadataTests {

        @Test
        @DisplayName("should return correct ID")
        void shouldReturnCorrectId() {
            assertThat(provider.getId()).isEqualTo("chacha20-poly1305");
        }

        @Test
        @DisplayName("should return correct numeric ID")
        void shouldReturnCorrectNumericId() {
            assertThat(provider.getNumericId()).isEqualTo(FormatConstants.ENCRYPTION_CHACHA20_POLY1305);
        }

        @Test
        @DisplayName("should have correct sizes")
        void shouldHaveCorrectSizes() {
            assertThat(provider.getKeySize()).isEqualTo(32);   // 256 bits
            assertThat(provider.getNonceSize()).isEqualTo(12); // 96 bits
            assertThat(provider.getTagSize()).isEqualTo(16);   // 128 bits
        }

    }

    @Nested
    @DisplayName("generateKey()")
    class GenerateKeyTests {

        @Test
        @DisplayName("should generate key of correct size")
        void shouldGenerateCorrectSize() throws GeneralSecurityException {
            final SecretKey generatedKey = provider.generateKey();

            assertThat(generatedKey.getEncoded()).hasSize(32);
        }

        @Test
        @DisplayName("should generate unique keys")
        void shouldGenerateUniqueKeys() throws GeneralSecurityException {
            final SecretKey key1 = provider.generateKey();
            final SecretKey key2 = provider.generateKey();

            assertThat(key1.getEncoded()).isNotEqualTo(key2.getEncoded());
        }

    }

    @Nested
    @DisplayName("encryptBlock/decryptBlock")
    class BlockEncryptionTests {

        @Test
        @DisplayName("should round-trip data correctly")
        void shouldRoundTripData() throws GeneralSecurityException {
            final byte[] plaintext = "Hello, World! This is a test of ChaCha20-Poly1305 encryption."
                    .getBytes(StandardCharsets.UTF_8);

            final byte[] ciphertext = provider.encryptBlock(plaintext, key);
            final byte[] decrypted = provider.decryptBlock(ciphertext, key);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should produce different ciphertext each time (random nonce)")
        void shouldProduceDifferentCiphertext() throws GeneralSecurityException {
            final byte[] plaintext = "Same plaintext".getBytes(StandardCharsets.UTF_8);

            final byte[] ciphertext1 = provider.encryptBlock(plaintext, key);
            final byte[] ciphertext2 = provider.encryptBlock(plaintext, key);

            assertThat(ciphertext1).isNotEqualTo(ciphertext2);
        }

        @Test
        @DisplayName("should include nonce and tag in ciphertext")
        void shouldIncludeNonceAndTag() throws GeneralSecurityException {
            final byte[] plaintext = "Test".getBytes(StandardCharsets.UTF_8);

            final byte[] ciphertext = provider.encryptBlock(plaintext, key);

            // Ciphertext should be: nonce (12) + encrypted data (4) + tag (16) = 32 bytes
            assertThat(ciphertext.length).isEqualTo(
                    provider.getNonceSize() + plaintext.length + provider.getTagSize());
        }

        @Test
        @DisplayName("should fail decryption with wrong key")
        void shouldFailWithWrongKey() throws GeneralSecurityException {
            final byte[] plaintext = "Secret data".getBytes(StandardCharsets.UTF_8);
            final SecretKey wrongKey = provider.generateKey();

            final byte[] ciphertext = provider.encryptBlock(plaintext, key);

            assertThatThrownBy(() -> provider.decryptBlock(ciphertext, wrongKey))
                    .isInstanceOf(GeneralSecurityException.class);
        }

        @Test
        @DisplayName("should fail decryption with tampered ciphertext")
        void shouldFailWithTamperedCiphertext() throws GeneralSecurityException {
            final byte[] plaintext = "Secret data".getBytes(StandardCharsets.UTF_8);

            final byte[] ciphertext = provider.encryptBlock(plaintext, key);
            // Tamper with the ciphertext
            ciphertext[ciphertext.length - 1] ^= 0xFF;

            assertThatThrownBy(() -> provider.decryptBlock(ciphertext, key))
                    .isInstanceOf(GeneralSecurityException.class)
                    .hasMessageContaining("authentication tag mismatch");
        }

        @Test
        @DisplayName("should handle empty plaintext")
        void shouldHandleEmptyPlaintext() throws GeneralSecurityException {
            final byte[] plaintext = new byte[0];

            final byte[] ciphertext = provider.encryptBlock(plaintext, key);
            final byte[] decrypted = provider.decryptBlock(ciphertext, key);

            assertThat(decrypted).isEmpty();
        }

        @Test
        @DisplayName("should handle large data")
        void shouldHandleLargeData() throws GeneralSecurityException {
            final byte[] plaintext = randomBytes(1024 * 1024); // 1 MB

            final byte[] ciphertext = provider.encryptBlock(plaintext, key);
            final byte[] decrypted = provider.decryptBlock(ciphertext, key);

            assertThat(decrypted).isEqualTo(plaintext);
        }

    }

    @Nested
    @DisplayName("AAD (Additional Authenticated Data)")
    class AadTests {

        @Test
        @DisplayName("should authenticate AAD")
        void shouldAuthenticateAad() throws GeneralSecurityException {
            final byte[] plaintext = "Secret data".getBytes(StandardCharsets.UTF_8);
            final byte[] aad = "Associated data".getBytes(StandardCharsets.UTF_8);

            final byte[] ciphertext = provider.encryptBlock(plaintext, key, aad);
            final byte[] decrypted = provider.decryptBlock(ciphertext, key, aad);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should fail with wrong AAD")
        void shouldFailWithWrongAad() throws GeneralSecurityException {
            final byte[] plaintext = "Secret data".getBytes(StandardCharsets.UTF_8);
            final byte[] aad = "Associated data".getBytes(StandardCharsets.UTF_8);
            final byte[] wrongAad = "Wrong data".getBytes(StandardCharsets.UTF_8);

            final byte[] ciphertext = provider.encryptBlock(plaintext, key, aad);

            assertThatThrownBy(() -> provider.decryptBlock(ciphertext, key, wrongAad))
                    .isInstanceOf(GeneralSecurityException.class);
        }

    }

    @Nested
    @DisplayName("createKey()")
    class CreateKeyTests {

        @Test
        @DisplayName("should create key from bytes")
        void shouldCreateKeyFromBytes() {
            final byte[] keyBytes = randomBytes(32);
            final SecretKey createdKey = ChaCha20Poly1305EncryptionProvider.createKey(keyBytes);

            assertThat(createdKey.getEncoded()).isEqualTo(keyBytes);
        }

        @Test
        @DisplayName("should reject wrong key size")
        void shouldRejectWrongKeySize() {
            final byte[] wrongSize = randomBytes(16);

            assertThatThrownBy(() -> ChaCha20Poly1305EncryptionProvider.createKey(wrongSize))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid key size");
        }

    }

    private static byte[] randomBytes(final int length) {
        final byte[] bytes = new byte[length];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

}
