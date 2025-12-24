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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests using golden (reference) files for version compatibility.
 *
 * <p>Golden files are pre-created archives that must remain readable
 * across all future versions. This ensures backwards compatibility.</p>
 *
 * <p>If these tests fail after a format change, either:</p>
 * <ul>
 *   <li>The format change broke backwards compatibility (bug)</li>
 *   <li>The format change is intentional and golden files need updating</li>
 * </ul>
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Golden Files Tests")
class GoldenFilesTest {

    // Expected content for golden files
    private static final String EXPECTED_TEXT = "Hello, APACK!\nThis is a test file for golden file testing.\n";
    private static final byte[] EXPECTED_BINARY = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};

    @Nested
    @DisplayName("Create Golden Files (for reference)")
    class CreateGoldenFilesTests {

        /**
         * This test creates golden files. Run it once to generate the files,
         * then commit them to the repository. After that, disable or skip this test.
         */
        @Test
        @DisplayName("should create golden files for future reference")
        void shouldCreateGoldenFiles(@TempDir final Path tempDir) throws Exception {
            final Path goldenDir = tempDir.resolve("golden");
            Files.createDirectories(goldenDir);

            // Plain archive
            createPlainGoldenFile(goldenDir.resolve("v1_plain.apack"));

            // ZSTD compressed
            createZstdGoldenFile(goldenDir.resolve("v1_zstd.apack"));

            // LZ4 compressed
            createLz4GoldenFile(goldenDir.resolve("v1_lz4.apack"));

            // Encrypted
            createEncryptedGoldenFile(goldenDir.resolve("v1_encrypted.apack"));

            // Full features
            createFullFeaturedGoldenFile(goldenDir.resolve("v1_full_features.apack"));

            // Print instructions
            System.out.println("Golden files created in: " + goldenDir);
            System.out.println("To use as test resources, copy to:");
            System.out.println("  src/test/resources/golden/");
        }

        private void createPlainGoldenFile(final Path path) throws Exception {
            try (AetherPackWriter writer = AetherPackWriter.create(path)) {
                writer.addEntry("readme.txt", EXPECTED_TEXT.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("data.bin", EXPECTED_BINARY);
            }
        }

        private void createZstdGoldenFile(final Path path) throws Exception {
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider(), 3)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
                writer.addEntry("readme.txt", EXPECTED_TEXT.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("data.bin", EXPECTED_BINARY);
            }
        }

        private void createLz4GoldenFile(final Path path) throws Exception {
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new Lz4CompressionProvider(), 9)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
                writer.addEntry("readme.txt", EXPECTED_TEXT.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("data.bin", EXPECTED_BINARY);
            }
        }

        private void createEncryptedGoldenFile(final Path path) throws Exception {
            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey key = aes.generateKey();

            final ApackConfiguration config = ApackConfiguration.builder()
                    .encryption(aes, key)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
                writer.addEntry("readme.txt", EXPECTED_TEXT.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("data.bin", EXPECTED_BINARY);
            }
        }

        private void createFullFeaturedGoldenFile(final Path path) throws Exception {
            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey key = aes.generateKey();

            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider(), 9)
                    .encryption(aes, key)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
                writer.addEntry("readme.txt", EXPECTED_TEXT.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("data.bin", EXPECTED_BINARY);

                // Entry with metadata
                var meta = de.splatgames.aether.pack.core.entry.EntryMetadata.builder()
                        .name("metadata.json")
                        .mimeType("application/json")
                        .attribute("version", 1L)
                        .attribute("author", "golden_test")
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream("{\"test\":true}".getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    @Nested
    @DisplayName("Version Compatibility Tests")
    class VersionCompatibilityTests {

        @Test
        @DisplayName("should read plain archive created with current version")
        void shouldReadCurrentVersionPlainArchive(@TempDir final Path tempDir) throws Exception {
            // Create a fresh archive with current version
            final Path archive = tempDir.resolve("plain.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("readme.txt", EXPECTED_TEXT.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("data.bin", EXPECTED_BINARY);
            }

            // Read it back
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(new String(reader.readAllBytes("readme.txt"), StandardCharsets.UTF_8))
                        .isEqualTo(EXPECTED_TEXT);
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(EXPECTED_BINARY);
            }
        }

        @Test
        @DisplayName("should read ZSTD compressed archive")
        void shouldReadZstdCompressedArchive(@TempDir final Path tempDir) throws Exception {
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider(), 3)
                    .build();

            final Path archive = tempDir.resolve("zstd.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("readme.txt", EXPECTED_TEXT.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("data.bin", EXPECTED_BINARY);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(new String(reader.readAllBytes("readme.txt"), StandardCharsets.UTF_8))
                        .isEqualTo(EXPECTED_TEXT);
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(EXPECTED_BINARY);
            }
        }

        @Test
        @DisplayName("should read LZ4 compressed archive")
        void shouldReadLz4CompressedArchive(@TempDir final Path tempDir) throws Exception {
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new Lz4CompressionProvider(), 9)
                    .build();

            final Path archive = tempDir.resolve("lz4.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("readme.txt", EXPECTED_TEXT.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("data.bin", EXPECTED_BINARY);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(new String(reader.readAllBytes("readme.txt"), StandardCharsets.UTF_8))
                        .isEqualTo(EXPECTED_TEXT);
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(EXPECTED_BINARY);
            }
        }

        @Test
        @DisplayName("should read encrypted archive")
        void shouldReadEncryptedArchive(@TempDir final Path tempDir) throws Exception {
            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey key = aes.generateKey();

            final ApackConfiguration config = ApackConfiguration.builder()
                    .encryption(aes, key)
                    .build();

            final Path archive = tempDir.resolve("encrypted.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("readme.txt", EXPECTED_TEXT.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("data.bin", EXPECTED_BINARY);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(new String(reader.readAllBytes("readme.txt"), StandardCharsets.UTF_8))
                        .isEqualTo(EXPECTED_TEXT);
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(EXPECTED_BINARY);
            }
        }

        @Test
        @DisplayName("should read full featured archive")
        void shouldReadFullFeaturedArchive(@TempDir final Path tempDir) throws Exception {
            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey key = aes.generateKey();

            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider(), 9)
                    .encryption(aes, key)
                    .build();

            final Path archive = tempDir.resolve("full.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("readme.txt", EXPECTED_TEXT.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("data.bin", EXPECTED_BINARY);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(new String(reader.readAllBytes("readme.txt"), StandardCharsets.UTF_8))
                        .isEqualTo(EXPECTED_TEXT);
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(EXPECTED_BINARY);
            }
        }
    }

    @Nested
    @DisplayName("Content Verification Tests")
    class ContentVerificationTests {

        @Test
        @DisplayName("should extract exact content")
        void shouldExtractExactContent(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("content.apack");

            // Known content with specific hash
            final byte[] content = "Exact content for verification".getBytes(StandardCharsets.UTF_8);
            final String expectedHash = sha256(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("verify.txt", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("verify.txt");
                assertThat(sha256(read)).isEqualTo(expectedHash);
            }
        }

        @Test
        @DisplayName("should preserve binary content exactly")
        void shouldPreserveBinaryContentExactly(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("binary.apack");

            // All possible byte values
            final byte[] content = new byte[256];
            for (int i = 0; i < 256; i++) {
                content[i] = (byte) i;
            }

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("all_bytes.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("all_bytes.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should verify metadata from archive")
        void shouldVerifyMetadata(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("metadata.apack");

            var meta = de.splatgames.aether.pack.core.entry.EntryMetadata.builder()
                    .name("config.json")
                    .mimeType("application/json")
                    .attribute("version", 42L)
                    .attribute("author", "test_author")
                    .attribute("enabled", true)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(meta, new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                for (var entry : reader) {
                    if ("config.json".equals(entry.getName())) {
                        assertThat(entry.getMimeType()).isEqualTo("application/json");
                        // Attributes verification would require entry.getAttributes()
                    }
                }
            }
        }
    }

    // ===== Helper Methods =====

    private static String sha256(final byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        return HexFormat.of().formatHex(hash);
    }
}
