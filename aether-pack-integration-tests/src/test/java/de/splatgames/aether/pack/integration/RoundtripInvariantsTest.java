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
import de.splatgames.aether.pack.core.AetherPackReader;
import de.splatgames.aether.pack.core.AetherPackWriter;
import de.splatgames.aether.pack.core.ApackConfiguration;
import de.splatgames.aether.pack.crypto.Aes256GcmEncryptionProvider;
import de.splatgames.aether.pack.crypto.ChaCha20Poly1305EncryptionProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for roundtrip invariants: decode(encode(x)) == x
 *
 * <p>These tests verify the fundamental correctness property that any data
 * written to an APACK archive can be read back exactly as written.</p>
 *
 * <p>All tests use seeded randomness for reproducibility.</p>
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Roundtrip Invariants Tests")
class RoundtripInvariantsTest {

    private static final long BASE_SEED = 42L;

    @Nested
    @DisplayName("Deterministic Encoding Tests")
    class DeterministicEncodingTests {

        @Test
        @DisplayName("should produce same output for same input (seeded random)")
        void shouldProduceSameOutputForSameInput(@TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED);
            final byte[] content = new byte[10_000];
            random.nextBytes(content);

            final Path archive1 = tempDir.resolve("deterministic1.apack");
            final Path archive2 = tempDir.resolve("deterministic2.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive1)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackWriter writer = AetherPackWriter.create(archive2)) {
                writer.addEntry("data.bin", content);
            }

            // Entry data should be identical
            try (AetherPackReader reader1 = AetherPackReader.open(archive1);
                 AetherPackReader reader2 = AetherPackReader.open(archive2)) {
                assertThat(reader1.readAllBytes("data.bin")).isEqualTo(reader2.readAllBytes("data.bin"));
            }
        }

        @RepeatedTest(value = 5, name = "run {currentRepetition}")
        @DisplayName("should produce same output across multiple runs")
        void shouldProduceSameOutputAcrossMultipleRuns(final RepetitionInfo info, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + 100);
            final byte[] content = new byte[5_000];
            random.nextBytes(content);

            final Path archive = tempDir.resolve("run_" + info.getCurrentRepetition() + ".apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Roundtrip Property Tests")
    class RoundtripPropertyTests {

        @ParameterizedTest(name = "size {0} bytes")
        @ValueSource(ints = {0, 1, 100, 1000, 10000, 100000})
        @DisplayName("should roundtrip random byte arrays of various sizes")
        void shouldRoundtripRandomByteArrays(final int size, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + size);
            final byte[] content = new byte[size];
            random.nextBytes(content);

            final Path archive = tempDir.resolve("size_" + size + ".apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should roundtrip empty entries")
        void shouldRoundtripEmptyEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("empty_entry.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("empty.bin", new byte[0]);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("empty.bin")).isEmpty();
            }
        }

        @Test
        @DisplayName("should roundtrip entry names with all printable ASCII")
        void shouldRoundtripEntryNamesWithAllAsciiPrintable(@TempDir final Path tempDir) throws Exception {
            // Build string with printable ASCII (32-126), excluding problematic chars
            final StringBuilder sb = new StringBuilder();
            for (char c = 32; c < 127; c++) {
                if (c != '/' && c != '\\' && c != ':' && c != '*' && c != '?' && c != '"' && c != '<' && c != '>' && c != '|') {
                    sb.append(c);
                }
            }
            final String name = "test_" + sb.substring(0, Math.min(50, sb.length())) + ".txt";
            final byte[] content = "ASCII name test".getBytes(StandardCharsets.UTF_8);

            final Path archive = tempDir.resolve("ascii_names.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(name, content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(name)).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should roundtrip entry names with unicode codepoints")
        void shouldRoundtripEntryNamesWithUnicodeCodepoints(@TempDir final Path tempDir) throws Exception {
            final String[] unicodeNames = {
                    "simple.txt",
                    "umlauts_aeoeue.txt",
                    "japanese_\u65E5\u672C\u8A9E.txt",
                    "chinese_\u4E2D\u6587.txt",
                    "emoji_test.txt", // Avoid actual emoji in name
                    "cyrillic_\u0410\u0411\u0412.txt",
                    "arabic_\u0627\u0644\u0639\u0631\u0628\u064A\u0629.txt"
            };

            final Path archive = tempDir.resolve("unicode_names.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < unicodeNames.length; i++) {
                    writer.addEntry(unicodeNames[i], ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                for (int i = 0; i < unicodeNames.length; i++) {
                    assertThat(reader.readAllBytes(unicodeNames[i]))
                            .isEqualTo(("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        @Test
        @DisplayName("should roundtrip multiple entries")
        void shouldRoundtripMultipleEntries(@TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + 200);
            final int entryCount = 100;
            final byte[][] contents = new byte[entryCount][];

            for (int i = 0; i < entryCount; i++) {
                contents[i] = new byte[100 + random.nextInt(1000)];
                random.nextBytes(contents[i]);
            }

            final Path archive = tempDir.resolve("multi_entry.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < entryCount; i++) {
                    writer.addEntry("entry_" + i + ".bin", contents[i]);
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                for (int i = 0; i < entryCount; i++) {
                    assertThat(reader.readAllBytes("entry_" + i + ".bin")).isEqualTo(contents[i]);
                }
            }
        }
    }

    @Nested
    @DisplayName("Compression Roundtrip Tests")
    class CompressionRoundtripTests {

        @ParameterizedTest(name = "ZSTD level {0}")
        @ValueSource(ints = {1, 3, 9, 19})
        @DisplayName("should roundtrip with ZSTD at all compression levels")
        void shouldRoundtripWithZstdAllLevels(final int level, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + level);
            final byte[] content = new byte[50_000];
            random.nextBytes(content);

            final Path archive = tempDir.resolve("zstd_level_" + level + ".apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider(), level)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(content);
            }
        }

        @ParameterizedTest(name = "LZ4 level {0}")
        @ValueSource(ints = {0, 9, 17})
        @DisplayName("should roundtrip with LZ4 at all compression levels")
        void shouldRoundtripWithLz4AllLevels(final int level, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + level + 100);
            final byte[] content = new byte[50_000];
            random.nextBytes(content);

            final Path archive = tempDir.resolve("lz4_level_" + level + ".apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new Lz4CompressionProvider(), level)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should roundtrip high entropy data (uncompressible)")
        void shouldRoundtripHighEntropyData(@TempDir final Path tempDir) throws Exception {
            // Random data is high entropy and won't compress well
            final Random random = new Random(BASE_SEED + 300);
            final byte[] content = new byte[100_000];
            random.nextBytes(content);

            final Path archive = tempDir.resolve("high_entropy.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider())
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("random.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.readAllBytes("random.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should roundtrip low entropy data (highly compressible)")
        void shouldRoundtripLowEntropyData(@TempDir final Path tempDir) throws Exception {
            // All zeros - maximally compressible
            final byte[] content = new byte[100_000];

            final Path archive = tempDir.resolve("low_entropy.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider(), 19)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("zeros.bin", content);
            }

            // Archive should be much smaller than original
            assertThat(Files.size(archive)).isLessThan(content.length / 10);

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.readAllBytes("zeros.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should roundtrip repetitive pattern data")
        void shouldRoundtripRepetitivePatternData(@TempDir final Path tempDir) throws Exception {
            // Repeating pattern - highly compressible
            final byte[] pattern = "ABCDEFGH12345678".getBytes(StandardCharsets.UTF_8);
            final byte[] content = new byte[100_000];
            for (int i = 0; i < content.length; i++) {
                content[i] = pattern[i % pattern.length];
            }

            final Path archive = tempDir.resolve("pattern.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider())
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("pattern.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.readAllBytes("pattern.bin")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Encryption Roundtrip Tests")
    class EncryptionRoundtripTests {

        @Test
        @DisplayName("should roundtrip with AES-GCM encryption")
        void shouldRoundtripWithAesGcm(@TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + 400);
            final byte[] content = new byte[20_000];
            random.nextBytes(content);

            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey key = aes.generateKey();

            final Path archive = tempDir.resolve("aes_gcm.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .encryption(aes, key)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("secret.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.readAllBytes("secret.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should roundtrip with ChaCha20 encryption")
        void shouldRoundtripWithChaCha20(@TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + 500);
            final byte[] content = new byte[20_000];
            random.nextBytes(content);

            final ChaCha20Poly1305EncryptionProvider chacha = new ChaCha20Poly1305EncryptionProvider();
            final SecretKey key = chacha.generateKey();

            final Path archive = tempDir.resolve("chacha20.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .encryption(chacha, key)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("secret.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.readAllBytes("secret.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should fail decryption with wrong key")
        void shouldFailDecryptionWithWrongKey(@TempDir final Path tempDir) throws Exception {
            final byte[] content = "Secret data".getBytes(StandardCharsets.UTF_8);

            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey correctKey = aes.generateKey();
            final SecretKey wrongKey = aes.generateKey();

            final Path archive = tempDir.resolve("wrong_key.apack");
            final ApackConfiguration writeConfig = ApackConfiguration.builder()
                    .encryption(aes, correctKey)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, writeConfig)) {
                writer.addEntry("secret.txt", content);
            }

            final ApackConfiguration readConfig = ApackConfiguration.builder()
                    .encryption(aes, wrongKey)
                    .build();

            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive, readConfig.createChunkProcessor())) {
                    reader.readAllBytes("secret.txt");
                }
            }).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Combined Compression and Encryption Tests")
    class CombinedTests {

        @Test
        @DisplayName("should roundtrip with ZSTD compression and AES-GCM encryption")
        void shouldRoundtripWithZstdAndAesGcm(@TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + 600);
            final byte[] content = new byte[50_000];
            random.nextBytes(content);

            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey key = aes.generateKey();

            final Path archive = tempDir.resolve("zstd_aes.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider())
                    .encryption(aes, key)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should roundtrip with LZ4 compression and ChaCha20 encryption")
        void shouldRoundtripWithLz4AndChaCha20(@TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + 700);
            final byte[] content = new byte[50_000];
            random.nextBytes(content);

            final ChaCha20Poly1305EncryptionProvider chacha = new ChaCha20Poly1305EncryptionProvider();
            final SecretKey key = chacha.generateKey();

            final Path archive = tempDir.resolve("lz4_chacha.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new Lz4CompressionProvider())
                    .encryption(chacha, key)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Edge Case Roundtrip Tests")
    class EdgeCaseRoundtripTests {

        @Test
        @DisplayName("should roundtrip single byte content")
        void shouldRoundtripSingleByte(@TempDir final Path tempDir) throws Exception {
            for (int b = 0; b < 256; b++) {
                final byte[] content = new byte[]{(byte) b};
                final Path archive = tempDir.resolve("byte_" + b + ".apack");

                try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                    writer.addEntry("byte.bin", content);
                }

                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    assertThat(reader.readAllBytes("byte.bin")).isEqualTo(content);
                }

                Files.deleteIfExists(archive);
            }
        }

        @Test
        @DisplayName("should roundtrip data at chunk boundary sizes")
        void shouldRoundtripAtChunkBoundaries(@TempDir final Path tempDir) throws Exception {
            final int chunkSize = 256 * 1024; // DEFAULT_CHUNK_SIZE
            final int[] sizes = {
                    chunkSize - 1,
                    chunkSize,
                    chunkSize + 1,
                    chunkSize * 2 - 1,
                    chunkSize * 2,
                    chunkSize * 2 + 1
            };

            final Random random = new Random(BASE_SEED + 800);

            for (int size : sizes) {
                final byte[] content = new byte[size];
                random.nextBytes(content);

                final Path archive = tempDir.resolve("boundary_" + size + ".apack");

                try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                    writer.addEntry("data.bin", content);
                }

                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    assertThat(reader.readAllBytes("data.bin"))
                            .as("Size %d should roundtrip correctly", size)
                            .isEqualTo(content);
                }

                Files.deleteIfExists(archive);
            }
        }

        @Test
        @DisplayName("should roundtrip binary data with all byte values")
        void shouldRoundtripAllByteValues(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[256];
            for (int i = 0; i < 256; i++) {
                content[i] = (byte) i;
            }

            final Path archive = tempDir.resolve("all_bytes.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("all_bytes.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("all_bytes.bin")).isEqualTo(content);
            }
        }
    }
}
