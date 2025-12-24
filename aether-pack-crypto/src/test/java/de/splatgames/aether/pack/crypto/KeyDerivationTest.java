package de.splatgames.aether.pack.crypto;

import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Key Derivation Functions")
class KeyDerivationTest {

    @Nested
    @DisplayName("Argon2idKeyDerivation")
    class Argon2idTests {

        @Test
        @DisplayName("should return correct ID")
        void shouldReturnCorrectId() {
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();
            assertThat(kdf.getId()).isEqualTo("argon2id");
        }

        @Test
        @DisplayName("should return correct numeric ID")
        void shouldReturnCorrectNumericId() {
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();
            assertThat(kdf.getNumericId()).isEqualTo(FormatConstants.KDF_ARGON2ID);
        }

        @Test
        @DisplayName("should derive key of correct length")
        void shouldDeriveCorrectLength() throws GeneralSecurityException {
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation(
                    8 * 1024, 1, 1, 16); // Low params for fast test
            final byte[] salt = kdf.generateSalt();

            final SecretKey key = kdf.deriveKey("password".toCharArray(), salt, 32);

            assertThat(key.getEncoded()).hasSize(32);
        }

        @Test
        @DisplayName("should produce same key for same password and salt")
        void shouldProduceSameKeyForSameInput() throws GeneralSecurityException {
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation(
                    8 * 1024, 1, 1, 16);
            final byte[] salt = kdf.generateSalt();

            final SecretKey key1 = kdf.deriveKey("password".toCharArray(), salt, 32);
            final SecretKey key2 = kdf.deriveKey("password".toCharArray(), salt, 32);

            assertThat(key1.getEncoded()).isEqualTo(key2.getEncoded());
        }

        @Test
        @DisplayName("should produce different keys for different passwords")
        void shouldProduceDifferentKeysForDifferentPasswords() throws GeneralSecurityException {
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation(
                    8 * 1024, 1, 1, 16);
            final byte[] salt = kdf.generateSalt();

            final SecretKey key1 = kdf.deriveKey("password1".toCharArray(), salt, 32);
            final SecretKey key2 = kdf.deriveKey("password2".toCharArray(), salt, 32);

            assertThat(key1.getEncoded()).isNotEqualTo(key2.getEncoded());
        }

        @Test
        @DisplayName("should produce different keys for different salts")
        void shouldProduceDifferentKeysForDifferentSalts() throws GeneralSecurityException {
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation(
                    8 * 1024, 1, 1, 16);
            final byte[] salt1 = kdf.generateSalt();
            final byte[] salt2 = kdf.generateSalt();

            final SecretKey key1 = kdf.deriveKey("password".toCharArray(), salt1, 32);
            final SecretKey key2 = kdf.deriveKey("password".toCharArray(), salt2, 32);

            assertThat(key1.getEncoded()).isNotEqualTo(key2.getEncoded());
        }

        @Test
        @DisplayName("should generate salt of correct length")
        void shouldGenerateSaltOfCorrectLength() {
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();
            final byte[] salt = kdf.generateSalt();

            assertThat(salt).hasSize(16); // Default salt length
        }

        @Test
        @DisplayName("should generate unique salts")
        void shouldGenerateUniqueSalts() {
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();

            final byte[] salt1 = kdf.generateSalt();
            final byte[] salt2 = kdf.generateSalt();

            assertThat(salt1).isNotEqualTo(salt2);
        }

        @Test
        @DisplayName("should serialize and deserialize parameters")
        void shouldSerializeParameters() {
            final Argon2idKeyDerivation original = new Argon2idKeyDerivation(
                    32 * 1024, 2, 2, 32);

            final byte[] params = original.getParameters();
            final Argon2idKeyDerivation restored = Argon2idKeyDerivation.fromParameters(params);

            assertThat(restored.getMemoryKiB()).isEqualTo(32 * 1024);
            assertThat(restored.getIterations()).isEqualTo(2);
            assertThat(restored.getParallelism()).isEqualTo(2);
        }

        @Test
        @DisplayName("should reject salt that is too short")
        void shouldRejectShortSalt() {
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation(
                    8 * 1024, 1, 1, 16);
            final byte[] shortSalt = new byte[4]; // Too short

            assertThatThrownBy(() -> kdf.deriveKey("password".toCharArray(), shortSalt, 32))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Salt too short");
        }

    }

    @Nested
    @DisplayName("Pbkdf2KeyDerivation")
    class Pbkdf2Tests {

        @Test
        @DisplayName("should return correct ID")
        void shouldReturnCorrectId() {
            final Pbkdf2KeyDerivation kdf = new Pbkdf2KeyDerivation();
            assertThat(kdf.getId()).isEqualTo("pbkdf2-sha256");
        }

        @Test
        @DisplayName("should return correct numeric ID")
        void shouldReturnCorrectNumericId() {
            final Pbkdf2KeyDerivation kdf = new Pbkdf2KeyDerivation();
            assertThat(kdf.getNumericId()).isEqualTo(FormatConstants.KDF_PBKDF2_SHA256);
        }

        @Test
        @DisplayName("should derive key of correct length")
        void shouldDeriveCorrectLength() throws GeneralSecurityException {
            final Pbkdf2KeyDerivation kdf = new Pbkdf2KeyDerivation(100000, 32);
            final byte[] salt = kdf.generateSalt();

            final SecretKey key = kdf.deriveKey("password".toCharArray(), salt, 32);

            assertThat(key.getEncoded()).hasSize(32);
        }

        @Test
        @DisplayName("should produce same key for same password and salt")
        void shouldProduceSameKeyForSameInput() throws GeneralSecurityException {
            final Pbkdf2KeyDerivation kdf = new Pbkdf2KeyDerivation(100000, 32);
            final byte[] salt = kdf.generateSalt();

            final SecretKey key1 = kdf.deriveKey("password".toCharArray(), salt, 32);
            final SecretKey key2 = kdf.deriveKey("password".toCharArray(), salt, 32);

            assertThat(key1.getEncoded()).isEqualTo(key2.getEncoded());
        }

        @Test
        @DisplayName("should produce different keys for different passwords")
        void shouldProduceDifferentKeysForDifferentPasswords() throws GeneralSecurityException {
            final Pbkdf2KeyDerivation kdf = new Pbkdf2KeyDerivation(100000, 32);
            final byte[] salt = kdf.generateSalt();

            final SecretKey key1 = kdf.deriveKey("password1".toCharArray(), salt, 32);
            final SecretKey key2 = kdf.deriveKey("password2".toCharArray(), salt, 32);

            assertThat(key1.getEncoded()).isNotEqualTo(key2.getEncoded());
        }

        @Test
        @DisplayName("should serialize and deserialize parameters")
        void shouldSerializeParameters() {
            final Pbkdf2KeyDerivation original = new Pbkdf2KeyDerivation(200000, 64);

            final byte[] params = original.getParameters();
            final Pbkdf2KeyDerivation restored = Pbkdf2KeyDerivation.fromParameters(params);

            assertThat(restored.getIterations()).isEqualTo(200000);
            assertThat(restored.getSaltLength()).isEqualTo(64);
        }

        @Test
        @DisplayName("should reject iterations below minimum")
        void shouldRejectLowIterations() {
            assertThatThrownBy(() -> new Pbkdf2KeyDerivation(1000, 32))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Iterations must be at least");
        }

    }

    @Nested
    @DisplayName("KeyWrapper")
    class KeyWrapperTests {

        @Test
        @DisplayName("should wrap and unwrap key correctly")
        void shouldWrapAndUnwrap() throws GeneralSecurityException {
            final SecretKey cek = KeyWrapper.generateAes256Key();
            final SecretKey kek = KeyWrapper.generateAes256Key();

            final byte[] wrapped = KeyWrapper.wrap(cek, kek);
            final SecretKey unwrapped = KeyWrapper.unwrapAes(wrapped, kek);

            assertThat(unwrapped.getEncoded()).isEqualTo(cek.getEncoded());
        }

        @Test
        @DisplayName("should fail unwrap with wrong key")
        void shouldFailWithWrongKey() throws GeneralSecurityException {
            final SecretKey cek = KeyWrapper.generateAes256Key();
            final SecretKey kek = KeyWrapper.generateAes256Key();
            final SecretKey wrongKek = KeyWrapper.generateAes256Key();

            final byte[] wrapped = KeyWrapper.wrap(cek, kek);

            assertThatThrownBy(() -> KeyWrapper.unwrapAes(wrapped, wrongKek))
                    .isInstanceOf(GeneralSecurityException.class);
        }

        @Test
        @DisplayName("should add correct overhead")
        void shouldAddCorrectOverhead() throws GeneralSecurityException {
            final SecretKey cek = KeyWrapper.generateAes256Key();
            final SecretKey kek = KeyWrapper.generateAes256Key();

            final byte[] wrapped = KeyWrapper.wrap(cek, kek);

            assertThat(wrapped.length).isEqualTo(cek.getEncoded().length + KeyWrapper.WRAP_OVERHEAD);
        }

        @Test
        @DisplayName("should wrap with password")
        void shouldWrapWithPassword() throws GeneralSecurityException {
            final SecretKey cek = KeyWrapper.generateAes256Key();
            final Argon2idKeyDerivation kdf = new Argon2idKeyDerivation(
                    8 * 1024, 1, 1, 16);
            final byte[] salt = kdf.generateSalt();
            final char[] password = "testPassword123".toCharArray();

            final byte[] wrapped = KeyWrapper.wrapWithPassword(cek, password, salt, kdf);
            final SecretKey unwrapped = KeyWrapper.unwrapWithPassword(
                    wrapped, password, salt, kdf, "AES");

            assertThat(unwrapped.getEncoded()).isEqualTo(cek.getEncoded());
        }

    }

}
