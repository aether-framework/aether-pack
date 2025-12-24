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
import de.splatgames.aether.pack.core.entry.Entry;
import de.splatgames.aether.pack.core.entry.EntryMetadata;
import de.splatgames.aether.pack.core.io.ChunkProcessor;
import de.splatgames.aether.pack.core.spi.CompressionProvider;
import de.splatgames.aether.pack.core.spi.EncryptionProvider;
import de.splatgames.aether.pack.crypto.Aes256GcmEncryptionProvider;
import de.splatgames.aether.pack.crypto.ChaCha20Poly1305EncryptionProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the full archive write/read cycle.
 *
 * <p>These tests validate that archives created with {@link AetherPackWriter}
 * can be correctly read back with {@link AetherPackReader}, including all
 * combinations of compression and encryption.</p>
 */
@DisplayName("Roundtrip Integration")
class RoundtripIntegrationTest {

    private static final Random RANDOM = new Random(42);

    @TempDir
    Path tempDir;

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
        final ZstdCompressionProvider zstd = new ZstdCompressionProvider();
        final Lz4CompressionProvider lz4 = new Lz4CompressionProvider();
        final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
        final ChaCha20Poly1305EncryptionProvider chacha = new ChaCha20Poly1305EncryptionProvider();

        return Stream.of(
                // Compression only
                Arguments.of("ZSTD only", zstd, null, null),
                Arguments.of("LZ4 only", lz4, null, null),
                // Encryption only
                Arguments.of("AES-256-GCM only", null, aes, aes.generateKey()),
                Arguments.of("ChaCha20 only", null, chacha, chacha.generateKey()),
                // Both compression + encryption
                Arguments.of("ZSTD + AES-256-GCM", zstd, aes, aes.generateKey()),
                Arguments.of("ZSTD + ChaCha20", zstd, chacha, chacha.generateKey()),
                Arguments.of("LZ4 + AES-256-GCM", lz4, aes, aes.generateKey()),
                Arguments.of("LZ4 + ChaCha20", lz4, chacha, chacha.generateKey())
        );
    }

    // ==================== Plain Archive Tests ====================

    @Nested
    @DisplayName("Plain Archives (no compression/encryption)")
    class PlainArchiveTests {

        @Test
        @DisplayName("should roundtrip single text entry")
        void shouldRoundtripSingleTextEntry() throws Exception {
            final Path archivePath = tempDir.resolve("single_text.apack");
            final String entryName = "hello.txt";
            final byte[] content = "Hello, World!".getBytes(StandardCharsets.UTF_8);

            // Write archive
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry(entryName, content);
            }

            // Read archive
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getEntryCount()).isEqualTo(1);

                final Optional<Entry> entry = reader.getEntry(entryName);
                assertThat(entry).isPresent();
                assertThat(entry.get().getName()).isEqualTo(entryName);

                final byte[] readContent = reader.readAllBytes(entry.get());
                assertThat(readContent).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should roundtrip single binary entry")
        void shouldRoundtripSingleBinaryEntry() throws Exception {
            final Path archivePath = tempDir.resolve("single_binary.apack");
            final String entryName = "data.bin";
            final byte[] content = randomBytes(1024);

            // Write archive
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry(entryName, content);
            }

            // Read archive
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getEntryCount()).isEqualTo(1);

                final byte[] readContent = reader.readAllBytes(entryName);
                assertThat(readContent).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should roundtrip multiple entries")
        void shouldRoundtripMultipleEntries() throws Exception {
            final Path archivePath = tempDir.resolve("multiple.apack");

            final String[] names = {"file1.txt", "dir/file2.txt", "dir/subdir/file3.bin"};
            final byte[][] contents = {
                    "Content 1".getBytes(StandardCharsets.UTF_8),
                    "Content 2".getBytes(StandardCharsets.UTF_8),
                    randomBytes(512)
            };

            // Write archive
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                for (int i = 0; i < names.length; i++) {
                    writer.addEntry(names[i], contents[i]);
                }
            }

            // Read archive
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getEntryCount()).isEqualTo(names.length);

                for (int i = 0; i < names.length; i++) {
                    final byte[] readContent = reader.readAllBytes(names[i]);
                    assertThat(readContent).isEqualTo(contents[i]);
                }
            }
        }

        @Test
        @DisplayName("should roundtrip empty archive")
        void shouldRoundtripEmptyArchive() throws Exception {
            final Path archivePath = tempDir.resolve("empty.apack");

            // Write empty archive
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                // Don't add any entries
            }

            // Read empty archive
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getEntryCount()).isZero();
                assertThat(reader.getEntries()).isEmpty();
            }
        }

        @Test
        @DisplayName("should roundtrip entry with MIME type")
        void shouldRoundtripEntryWithMimeType() throws Exception {
            final Path archivePath = tempDir.resolve("with_mime.apack");
            final String entryName = "document.json";
            final String mimeType = "application/json";
            final byte[] content = "{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8);

            // Write archive with MIME type
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                final EntryMetadata metadata = EntryMetadata.builder()
                        .name(entryName)
                        .mimeType(mimeType)
                        .build();
                writer.addEntry(metadata, new ByteArrayInputStream(content));
            }

            // Read archive
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final Optional<Entry> entry = reader.getEntry(entryName);
                assertThat(entry).isPresent();
                assertThat(entry.get().getMimeType()).isEqualTo(mimeType);

                final byte[] readContent = reader.readAllBytes(entry.get());
                assertThat(readContent).isEqualTo(content);
            }
        }
    }

    // ==================== Compressed Archive Tests ====================

    @Nested
    @DisplayName("Compressed Archives")
    class CompressedArchiveTests {

        @ParameterizedTest(name = "ZSTD level {0}")
        @ValueSource(ints = {1, 3, 9, 19})
        @DisplayName("should roundtrip with ZSTD compression")
        void shouldRoundtripWithZstdCompression(final int level) throws Exception {
            final Path archivePath = tempDir.resolve("zstd_level_" + level + ".apack");
            final ZstdCompressionProvider zstd = new ZstdCompressionProvider();

            // Compressible content (repetitive text)
            final byte[] content = "Compress me! ".repeat(1000).getBytes(StandardCharsets.UTF_8);

            // Build configuration
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(zstd, level)
                    .build();

            // Write archive
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath, config)) {
                writer.addEntry("data.txt", content);
            }

            // Build chunk processor for reading
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .compression(zstd)
                    .build();

            // Read archive
            try (AetherPackReader reader = AetherPackReader.open(archivePath, processor)) {
                final byte[] readContent = reader.readAllBytes("data.txt");
                assertThat(readContent).isEqualTo(content);
            }
        }

        @ParameterizedTest(name = "LZ4 level {0}")
        @ValueSource(ints = {0, 9, 17})
        @DisplayName("should roundtrip with LZ4 compression")
        void shouldRoundtripWithLz4Compression(final int level) throws Exception {
            final Path archivePath = tempDir.resolve("lz4_level_" + level + ".apack");
            final Lz4CompressionProvider lz4 = new Lz4CompressionProvider();

            // Compressible content
            final byte[] content = "LZ4 compress me! ".repeat(1000).getBytes(StandardCharsets.UTF_8);

            // Build configuration
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(lz4, level)
                    .build();

            // Write archive
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath, config)) {
                writer.addEntry("data.txt", content);
            }

            // Build chunk processor for reading
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .compression(lz4)
                    .build();

            // Read archive
            try (AetherPackReader reader = AetherPackReader.open(archivePath, processor)) {
                final byte[] readContent = reader.readAllBytes("data.txt");
                assertThat(readContent).isEqualTo(content);
            }
        }
    }

    // ==================== Encrypted Archive Tests ====================

    @Nested
    @DisplayName("Encrypted Archives")
    class EncryptedArchiveTests {

        @Test
        @DisplayName("should roundtrip with AES-GCM encryption")
        void shouldRoundtripWithAesGcmEncryption() throws Exception {
            final Path archivePath = tempDir.resolve("aes_gcm.apack");
            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey key = aes.generateKey();

            final byte[] content = "Secret data".getBytes(StandardCharsets.UTF_8);

            // Build configuration
            final ApackConfiguration config = ApackConfiguration.builder()
                    .encryption(aes, key)
                    .build();

            // Write archive
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath, config)) {
                writer.addEntry("secret.txt", content);
            }

            // Build chunk processor for reading
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .encryption(aes, key)
                    .build();

            // Read archive
            try (AetherPackReader reader = AetherPackReader.open(archivePath, processor)) {
                final byte[] readContent = reader.readAllBytes("secret.txt");
                assertThat(readContent).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should roundtrip with ChaCha20-Poly1305 encryption")
        void shouldRoundtripWithChaCha20Encryption() throws Exception {
            final Path archivePath = tempDir.resolve("chacha20.apack");
            final ChaCha20Poly1305EncryptionProvider chacha = new ChaCha20Poly1305EncryptionProvider();
            final SecretKey key = chacha.generateKey();

            final byte[] content = "ChaCha20 secret data".getBytes(StandardCharsets.UTF_8);

            // Build configuration
            final ApackConfiguration config = ApackConfiguration.builder()
                    .encryption(chacha, key)
                    .build();

            // Write archive
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath, config)) {
                writer.addEntry("secret.txt", content);
            }

            // Build chunk processor for reading
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .encryption(chacha, key)
                    .build();

            // Read archive
            try (AetherPackReader reader = AetherPackReader.open(archivePath, processor)) {
                final byte[] readContent = reader.readAllBytes("secret.txt");
                assertThat(readContent).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should fail decryption with wrong key")
        void shouldFailDecryptionWithWrongKey() throws Exception {
            final Path archivePath = tempDir.resolve("wrong_key.apack");
            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey correctKey = aes.generateKey();
            final SecretKey wrongKey = aes.generateKey();

            final byte[] content = "Secret data".getBytes(StandardCharsets.UTF_8);

            // Build configuration with correct key
            final ApackConfiguration config = ApackConfiguration.builder()
                    .encryption(aes, correctKey)
                    .build();

            // Write archive
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath, config)) {
                writer.addEntry("secret.txt", content);
            }

            // Build chunk processor with wrong key
            final ChunkProcessor processor = ChunkProcessor.builder()
                    .encryption(aes, wrongKey)
                    .build();

            // Read archive - should fail
            try (AetherPackReader reader = AetherPackReader.open(archivePath, processor)) {
                assertThatThrownBy(() -> reader.readAllBytes("secret.txt"))
                        .isInstanceOf(IOException.class);
            }
        }
    }

    // ==================== Combined Compression + Encryption Tests ====================

    @Nested
    @DisplayName("Combined Compression and Encryption")
    class CombinedTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.splatgames.aether.pack.integration.RoundtripIntegrationTest#allCombinations")
        @DisplayName("should roundtrip data correctly")
        void shouldRoundtripData(
                final String name,
                final CompressionProvider compressionProvider,
                final EncryptionProvider encryptionProvider,
                final SecretKey key) throws Exception {

            final Path archivePath = tempDir.resolve("combined_" + name.replace(" ", "_") + ".apack");

            // Compressible content
            final byte[] content = "Test data for combined processing! ".repeat(100)
                    .getBytes(StandardCharsets.UTF_8);

            // Build write configuration
            final ApackConfiguration.Builder configBuilder = ApackConfiguration.builder();
            if (compressionProvider != null) {
                configBuilder.compression(compressionProvider, compressionProvider.getDefaultLevel());
            }
            if (encryptionProvider != null && key != null) {
                configBuilder.encryption(encryptionProvider, key);
            }
            final ApackConfiguration config = configBuilder.build();

            // Write archive
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath, config)) {
                writer.addEntry("data.txt", content);
            }

            // Build read processor
            final ChunkProcessor.Builder processorBuilder = ChunkProcessor.builder();
            if (compressionProvider != null) {
                processorBuilder.compression(compressionProvider);
            }
            if (encryptionProvider != null && key != null) {
                processorBuilder.encryption(encryptionProvider, key);
            }
            final ChunkProcessor processor = processorBuilder.build();

            // Read archive
            try (AetherPackReader reader = AetherPackReader.open(archivePath, processor)) {
                final byte[] readContent = reader.readAllBytes("data.txt");
                assertThat(readContent).isEqualTo(content);
            }
        }
    }

    // ==================== Entry Access Tests ====================

    @Nested
    @DisplayName("Entry Access Patterns")
    class EntryAccessTests {

        @Test
        @DisplayName("should access entries by name")
        void shouldAccessEntriesByName() throws Exception {
            final Path archivePath = tempDir.resolve("by_name.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("first.txt", "First".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("second.txt", "Second".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("third.txt", "Third".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.hasEntry("first.txt")).isTrue();
                assertThat(reader.hasEntry("second.txt")).isTrue();
                assertThat(reader.hasEntry("third.txt")).isTrue();
                assertThat(reader.hasEntry("nonexistent.txt")).isFalse();

                assertThat(reader.getEntry("second.txt")).isPresent();
                assertThat(reader.getEntry("nonexistent.txt")).isEmpty();
            }
        }

        @Test
        @DisplayName("should access entries by ID")
        void shouldAccessEntriesById() throws Exception {
            final Path archivePath = tempDir.resolve("by_id.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("a.txt", "A".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("b.txt", "B".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                // First entry should have ID 1, second should have ID 2
                final Entry entry1 = reader.getEntry(1);
                assertThat(entry1.getName()).isEqualTo("a.txt");

                final Entry entry2 = reader.getEntry(2);
                assertThat(entry2.getName()).isEqualTo("b.txt");
            }
        }

        @Test
        @DisplayName("should iterate over entries")
        void shouldIterateOverEntries() throws Exception {
            final Path archivePath = tempDir.resolve("iterate.apack");

            final String[] names = {"one.txt", "two.txt", "three.txt"};

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                for (final String name : names) {
                    writer.addEntry(name, name.getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                int count = 0;
                for (final Entry entry : reader) {
                    assertThat(entry.getName()).isEqualTo(names[count]);
                    count++;
                }
                assertThat(count).isEqualTo(names.length);
            }
        }

        @Test
        @DisplayName("should stream entries")
        void shouldStreamEntries() throws Exception {
            final Path archivePath = tempDir.resolve("stream.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("a.json", "{}".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("b.txt", "text".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("c.json", "[]".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final List<String> jsonFiles = reader.stream()
                        .filter(e -> e.getName().endsWith(".json"))
                        .map(Entry::getName)
                        .collect(Collectors.toList());

                assertThat(jsonFiles).containsExactly("a.json", "c.json");
            }
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty entry")
        void shouldHandleEmptyEntry() throws Exception {
            final Path archivePath = tempDir.resolve("empty_entry.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("empty.txt", new byte[0]);
            }

            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final byte[] content = reader.readAllBytes("empty.txt");
                assertThat(content).isEmpty();
            }
        }

        @Test
        @DisplayName("should handle single byte entry")
        void shouldHandleSingleByteEntry() throws Exception {
            final Path archivePath = tempDir.resolve("single_byte.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("single.bin", new byte[]{0x42});
            }

            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final byte[] content = reader.readAllBytes("single.bin");
                assertThat(content).containsExactly(0x42);
            }
        }

        @Test
        @DisplayName("should handle entry spanning multiple chunks")
        void shouldHandleMultiChunkEntry() throws Exception {
            final Path archivePath = tempDir.resolve("multi_chunk.apack");

            // Create data larger than default chunk size (256 KB)
            final byte[] content = randomBytes(512 * 1024); // 512 KB

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("large.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final byte[] readContent = reader.readAllBytes("large.bin");
                assertThat(readContent).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle entry names with slashes")
        void shouldHandleEntryNamesWithSlashes() throws Exception {
            final Path archivePath = tempDir.resolve("slashes.apack");

            final String entryName = "path/to/nested/file.txt";
            final byte[] content = "Nested file".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry(entryName, content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.hasEntry(entryName)).isTrue();
                assertThat(reader.readAllBytes(entryName)).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle entry names with unicode")
        void shouldHandleEntryNamesWithUnicode() throws Exception {
            final Path archivePath = tempDir.resolve("unicode.apack");

            final String entryName = "日本語/文件名.txt";
            final byte[] content = "Unicode content: 你好世界".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry(entryName, content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.hasEntry(entryName)).isTrue();
                assertThat(reader.readAllBytes(entryName)).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle many small entries")
        void shouldHandleManySmallEntries() throws Exception {
            final Path archivePath = tempDir.resolve("many_entries.apack");
            final int entryCount = 100;

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                for (int i = 0; i < entryCount; i++) {
                    writer.addEntry("entry_" + i + ".txt", ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getEntryCount()).isEqualTo(entryCount);

                for (int i = 0; i < entryCount; i++) {
                    final String name = "entry_" + i + ".txt";
                    assertThat(reader.hasEntry(name)).isTrue();
                    assertThat(reader.readAllBytes(name))
                            .isEqualTo(("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    // ==================== Configuration Tests ====================

    @Nested
    @DisplayName("Configuration Options")
    class ConfigurationTests {

        @Test
        @DisplayName("should respect custom chunk size")
        void shouldRespectCustomChunkSize() throws Exception {
            final Path archivePath = tempDir.resolve("custom_chunk.apack");

            // Use a small chunk size to force multiple chunks
            final int chunkSize = 1024; // 1 KB
            final ApackConfiguration config = ApackConfiguration.builder()
                    .chunkSize(chunkSize)
                    .build();

            // Create data larger than chunk size
            final byte[] content = randomBytes(5 * chunkSize); // 5 chunks

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath, config)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                // Verify chunk size in file header
                assertThat(reader.getFileHeader().chunkSize()).isEqualTo(chunkSize);

                // Verify data integrity
                final byte[] readContent = reader.readAllBytes("data.bin");
                assertThat(readContent).isEqualTo(content);
            }
        }
    }

    // ==================== Utilities ====================

    private static byte[] randomBytes(final int length) {
        final byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
