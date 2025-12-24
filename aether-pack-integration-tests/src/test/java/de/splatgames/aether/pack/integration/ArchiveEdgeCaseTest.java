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
import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.crypto.Aes256GcmEncryptionProvider;
import de.splatgames.aether.pack.crypto.ChaCha20Poly1305EncryptionProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive edge-case tests for APACK archive creation and extraction.
 *
 * <p>These tests cover boundary conditions, unusual inputs, and stress scenarios
 * to ensure the format handles all cases correctly.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@DisplayName("Archive Edge Case Tests")
class ArchiveEdgeCaseTest {

    /** Reproducible random for test data generation. */
    private static final Random RANDOM = new Random(42);

    /** Default chunk size for reference. */
    private static final int DEFAULT_CHUNK_SIZE = FormatConstants.DEFAULT_CHUNK_SIZE;

    @Nested
    @DisplayName("File Size Boundary Tests")
    class FileSizeBoundaryTests {

        @Test
        @DisplayName("should handle empty file (0 bytes)")
        void shouldHandleEmptyFile(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("empty.apack");
            final byte[] content = new byte[0];

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("empty.txt", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(1);
                final byte[] read = reader.readAllBytes("empty.txt");
                assertThat(read).isEmpty();
            }
        }

        @Test
        @DisplayName("should handle single byte file")
        void shouldHandleSingleByteFile(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("single.apack");
            final byte[] content = new byte[]{0x42};

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("single.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("single.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @ParameterizedTest(name = "size = {0} bytes")
        @ValueSource(ints = {1, 2, 7, 8, 15, 16, 31, 32, 63, 64, 127, 128, 255, 256, 511, 512, 1023, 1024})
        @DisplayName("should handle small file sizes")
        void shouldHandleSmallFileSizes(final int size, @TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("small_" + size + ".apack");
            final byte[] content = new byte[size];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("data.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle file exactly at chunk size")
        void shouldHandleFileExactlyAtChunkSize(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("exact_chunk.apack");
            final byte[] content = new byte[DEFAULT_CHUNK_SIZE];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("exact.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("exact.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle file one byte below chunk size")
        void shouldHandleFileBelowChunkSize(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("below_chunk.apack");
            final byte[] content = new byte[DEFAULT_CHUNK_SIZE - 1];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("below.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("below.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle file one byte above chunk size")
        void shouldHandleFileAboveChunkSize(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("above_chunk.apack");
            final byte[] content = new byte[DEFAULT_CHUNK_SIZE + 1];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("above.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("above.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle file exactly at two chunks")
        void shouldHandleFileTwoChunks(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("two_chunks.apack");
            final byte[] content = new byte[DEFAULT_CHUNK_SIZE * 2];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("two.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("two.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle file exactly at three chunks")
        void shouldHandleFileThreeChunks(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("three_chunks.apack");
            final byte[] content = new byte[DEFAULT_CHUNK_SIZE * 3];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("three.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("three.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @ParameterizedTest(name = "chunks = {0}")
        @ValueSource(ints = {1, 2, 3, 4, 5, 10})
        @DisplayName("should handle various chunk multiples")
        void shouldHandleChunkMultiples(final int chunks, @TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("chunks_" + chunks + ".apack");
            final byte[] content = new byte[DEFAULT_CHUNK_SIZE * chunks];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("data.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle 1 MB file")
        void shouldHandleOneMegabyteFile(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("1mb.apack");
            final byte[] content = new byte[1024 * 1024];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("1mb.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("1mb.bin");
                assertThat(read).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Multiple Entry Tests")
    class MultipleEntryTests {

        @Test
        @DisplayName("should handle two entries")
        void shouldHandleTwoEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("two.apack");
            final byte[] content1 = "First entry content".getBytes(StandardCharsets.UTF_8);
            final byte[] content2 = "Second entry content".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("first.txt", content1);
                writer.addEntry("second.txt", content2);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(2);
                assertThat(reader.readAllBytes("first.txt")).isEqualTo(content1);
                assertThat(reader.readAllBytes("second.txt")).isEqualTo(content2);
            }
        }

        @Test
        @DisplayName("should handle 10 entries")
        void shouldHandleTenEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("ten.apack");
            final List<byte[]> contents = new ArrayList<>();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < 10; i++) {
                    final byte[] content = ("Content " + i).getBytes(StandardCharsets.UTF_8);
                    contents.add(content);
                    writer.addEntry("entry_" + i + ".txt", content);
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(10);
                for (int i = 0; i < 10; i++) {
                    assertThat(reader.readAllBytes("entry_" + i + ".txt")).isEqualTo(contents.get(i));
                }
            }
        }

        @Test
        @DisplayName("should handle 100 entries")
        void shouldHandleHundredEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("hundred.apack");
            final List<byte[]> contents = new ArrayList<>();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < 100; i++) {
                    final byte[] content = new byte[100 + i];
                    RANDOM.nextBytes(content);
                    contents.add(content);
                    writer.addEntry("entry_" + String.format("%03d", i) + ".bin", content);
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(100);
                for (int i = 0; i < 100; i++) {
                    final byte[] read = reader.readAllBytes("entry_" + String.format("%03d", i) + ".bin");
                    assertThat(read).isEqualTo(contents.get(i));
                }
            }
        }

        @Test
        @DisplayName("should handle mixed size entries")
        void shouldHandleMixedSizeEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("mixed.apack");
            final byte[] tiny = new byte[1];
            final byte[] small = new byte[100];
            final byte[] medium = new byte[10_000];
            final byte[] large = new byte[DEFAULT_CHUNK_SIZE + 1000];

            RANDOM.nextBytes(tiny);
            RANDOM.nextBytes(small);
            RANDOM.nextBytes(medium);
            RANDOM.nextBytes(large);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("tiny.bin", tiny);
                writer.addEntry("small.bin", small);
                writer.addEntry("medium.bin", medium);
                writer.addEntry("large.bin", large);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(4);
                assertThat(reader.readAllBytes("tiny.bin")).isEqualTo(tiny);
                assertThat(reader.readAllBytes("small.bin")).isEqualTo(small);
                assertThat(reader.readAllBytes("medium.bin")).isEqualTo(medium);
                assertThat(reader.readAllBytes("large.bin")).isEqualTo(large);
            }
        }

        @Test
        @DisplayName("should read entries in reverse order")
        void shouldReadEntriesInReverseOrder(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("reverse.apack");
            final List<byte[]> contents = new ArrayList<>();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < 5; i++) {
                    final byte[] content = ("Content " + i).getBytes(StandardCharsets.UTF_8);
                    contents.add(content);
                    writer.addEntry("entry_" + i + ".txt", content);
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // Read in reverse order
                for (int i = 4; i >= 0; i--) {
                    assertThat(reader.readAllBytes("entry_" + i + ".txt")).isEqualTo(contents.get(i));
                }
            }
        }

        @Test
        @DisplayName("should read same entry multiple times")
        void shouldReadSameEntryMultipleTimes(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("repeat.apack");
            final byte[] content = "Repeated content".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.txt", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                for (int i = 0; i < 10; i++) {
                    assertThat(reader.readAllBytes("data.txt")).isEqualTo(content);
                }
            }
        }

        @Test
        @DisplayName("should handle entries with empty content interspersed")
        void shouldHandleEmptyEntriesInterspersed(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("empty_interspersed.apack");
            final byte[] empty = new byte[0];
            final byte[] data = "Some data".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("empty1.txt", empty);
                writer.addEntry("data1.txt", data);
                writer.addEntry("empty2.txt", empty);
                writer.addEntry("data2.txt", data);
                writer.addEntry("empty3.txt", empty);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(5);
                assertThat(reader.readAllBytes("empty1.txt")).isEmpty();
                assertThat(reader.readAllBytes("data1.txt")).isEqualTo(data);
                assertThat(reader.readAllBytes("empty2.txt")).isEmpty();
                assertThat(reader.readAllBytes("data2.txt")).isEqualTo(data);
                assertThat(reader.readAllBytes("empty3.txt")).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Entry Name Tests")
    class EntryNameTests {

        @Test
        @DisplayName("should handle simple filename")
        void shouldHandleSimpleFilename(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("simple.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("file.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.hasEntry("file.txt")).isTrue();
            }
        }

        @Test
        @DisplayName("should handle path with single directory")
        void shouldHandleSingleDirectory(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("single_dir.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("folder/file.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.hasEntry("folder/file.txt")).isTrue();
            }
        }

        @Test
        @DisplayName("should handle deep nested path")
        void shouldHandleDeepNestedPath(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("deep.apack");
            final String deepPath = "a/b/c/d/e/f/g/h/i/j/file.txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(deepPath, "deep content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.hasEntry(deepPath)).isTrue();
            }
        }

        @Test
        @DisplayName("should handle Unicode filename")
        void shouldHandleUnicodeFilename(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("unicode.apack");
            final String unicodeName = "日本語/ファイル.txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(unicodeName, "Unicode content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.hasEntry(unicodeName)).isTrue();
                assertThat(reader.readAllBytes(unicodeName))
                        .isEqualTo("Unicode content".getBytes(StandardCharsets.UTF_8));
            }
        }

        @Test
        @DisplayName("should handle emoji in filename")
        void shouldHandleEmojiFilename(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("emoji.apack");
            final String emojiName = "folder/\uD83D\uDE00_smile.txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(emojiName, "Emoji content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.hasEntry(emojiName)).isTrue();
            }
        }

        @Test
        @DisplayName("should handle filename with spaces")
        void shouldHandleFilenameWithSpaces(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("spaces.apack");
            final String spaceName = "folder with spaces/file with spaces.txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(spaceName, "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.hasEntry(spaceName)).isTrue();
            }
        }

        @Test
        @DisplayName("should handle filename with special characters")
        void shouldHandleSpecialCharacters(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("special.apack");
            final String specialName = "folder/file-name_v1.2.3+build.txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(specialName, "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.hasEntry(specialName)).isTrue();
            }
        }

        @Test
        @DisplayName("should handle very long filename")
        void shouldHandleLongFilename(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("long.apack");
            final String longName = "a".repeat(200) + ".txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(longName, "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.hasEntry(longName)).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Content Type Tests")
    class ContentTypeTests {

        @Test
        @DisplayName("should handle all zero bytes")
        void shouldHandleAllZeroBytes(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("zeros.apack");
            final byte[] zeros = new byte[10_000];
            Arrays.fill(zeros, (byte) 0);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("zeros.bin", zeros);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("zeros.bin")).isEqualTo(zeros);
            }
        }

        @Test
        @DisplayName("should handle all 0xFF bytes")
        void shouldHandleAllFFBytes(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("ff.apack");
            final byte[] ffs = new byte[10_000];
            Arrays.fill(ffs, (byte) 0xFF);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("ff.bin", ffs);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("ff.bin")).isEqualTo(ffs);
            }
        }

        @Test
        @DisplayName("should handle repeating pattern")
        void shouldHandleRepeatingPattern(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("pattern.apack");
            final byte[] pattern = new byte[10_000];
            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = (byte) (i % 256);
            }

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("pattern.bin", pattern);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("pattern.bin")).isEqualTo(pattern);
            }
        }

        @Test
        @DisplayName("should handle random binary data")
        void shouldHandleRandomBinaryData(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("random.apack");
            final byte[] random = new byte[50_000];
            new SecureRandom().nextBytes(random);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("random.bin", random);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("random.bin")).isEqualTo(random);
            }
        }

        @Test
        @DisplayName("should handle UTF-8 text content")
        void shouldHandleUtf8TextContent(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("utf8.apack");
            final String text = "Hello, World! 日本語 Ümläuts \uD83D\uDE00";
            final byte[] content = text.getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("text.txt", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("text.txt");
                assertThat(new String(read, StandardCharsets.UTF_8)).isEqualTo(text);
            }
        }

        @Test
        @DisplayName("should handle binary data with null bytes")
        void shouldHandleNullBytes(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("nulls.apack");
            final byte[] content = new byte[]{0x00, 0x01, 0x00, 0x02, 0x00, 0x00, 0x03};

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("nulls.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("nulls.bin")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Compression Tests")
    class CompressionTests {

        @Test
        @DisplayName("should roundtrip with ZSTD compression level 1")
        void shouldRoundtripZstdLevel1(@TempDir final Path tempDir) throws Exception {
            testCompressionRoundtrip(tempDir, "zstd1.apack",
                    new ZstdCompressionProvider(), 1);
        }

        @Test
        @DisplayName("should roundtrip with ZSTD compression level 9")
        void shouldRoundtripZstdLevel9(@TempDir final Path tempDir) throws Exception {
            testCompressionRoundtrip(tempDir, "zstd9.apack",
                    new ZstdCompressionProvider(), 9);
        }

        @Test
        @DisplayName("should roundtrip with ZSTD compression level 19")
        void shouldRoundtripZstdLevel19(@TempDir final Path tempDir) throws Exception {
            testCompressionRoundtrip(tempDir, "zstd19.apack",
                    new ZstdCompressionProvider(), 19);
        }

        @Test
        @DisplayName("should roundtrip with LZ4 compression level 1")
        void shouldRoundtripLz4Level1(@TempDir final Path tempDir) throws Exception {
            testCompressionRoundtrip(tempDir, "lz4_1.apack",
                    new Lz4CompressionProvider(), 1);
        }

        @Test
        @DisplayName("should roundtrip with LZ4 compression level 9")
        void shouldRoundtripLz4Level9(@TempDir final Path tempDir) throws Exception {
            testCompressionRoundtrip(tempDir, "lz4_9.apack",
                    new Lz4CompressionProvider(), 9);
        }

        @Test
        @DisplayName("should handle incompressible data")
        void shouldHandleIncompressibleData(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("incompressible.apack");
            final byte[] random = new byte[50_000];
            new SecureRandom().nextBytes(random);

            final ZstdCompressionProvider zstd = new ZstdCompressionProvider();
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(zstd, 3)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("random.bin", random);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.readAllBytes("random.bin")).isEqualTo(random);
            }
        }

        @Test
        @DisplayName("should handle highly compressible data")
        void shouldHandleHighlyCompressibleData(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("compressible.apack");
            final byte[] zeros = new byte[100_000];

            final ZstdCompressionProvider zstd = new ZstdCompressionProvider();
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(zstd, 3)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("zeros.bin", zeros);
            }

            // Verify the archive is smaller than the original
            assertThat(Files.size(archive)).isLessThan(zeros.length);

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.readAllBytes("zeros.bin")).isEqualTo(zeros);
            }
        }

        private void testCompressionRoundtrip(final Path tempDir, final String filename,
                                              final de.splatgames.aether.pack.core.spi.CompressionProvider provider,
                                              final int level) throws Exception {
            final Path archive = tempDir.resolve(filename);
            final byte[] content = new byte[DEFAULT_CHUNK_SIZE + 1000];
            RANDOM.nextBytes(content);

            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(provider, level)
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
    @DisplayName("Encryption Tests")
    class EncryptionTests {

        @Test
        @DisplayName("should roundtrip with AES-GCM encryption")
        void shouldRoundtripAesGcm(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("aes.apack");
            final byte[] content = new byte[10_000];
            RANDOM.nextBytes(content);

            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey key = aes.generateKey();

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
        @DisplayName("should roundtrip with ChaCha20-Poly1305 encryption")
        void shouldRoundtripChaCha20(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("chacha.apack");
            final byte[] content = new byte[10_000];
            RANDOM.nextBytes(content);

            final ChaCha20Poly1305EncryptionProvider chacha = new ChaCha20Poly1305EncryptionProvider();
            final SecretKey key = chacha.generateKey();

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
        @DisplayName("should roundtrip with compression and encryption")
        void shouldRoundtripCompressionAndEncryption(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("combined.apack");
            final byte[] content = new byte[50_000];
            RANDOM.nextBytes(content);

            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey key = aes.generateKey();

            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider(), 3)
                    .encryption(aes, key)
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
    @DisplayName("Custom Chunk Size Tests")
    class CustomChunkSizeTests {

        @Test
        @DisplayName("should handle minimum chunk size (4 KB)")
        void shouldHandleMinimumChunkSize(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("min_chunk.apack");
            final int minChunkSize = FormatConstants.MIN_CHUNK_SIZE;
            final byte[] content = new byte[minChunkSize * 3]; // Will create multiple chunks
            RANDOM.nextBytes(content);

            final ApackConfiguration config = ApackConfiguration.builder()
                    .chunkSize(minChunkSize)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle 64 KB chunk size")
        void shouldHandle64KBChunkSize(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("64kb_chunk.apack");
            final int chunkSize = 64 * 1024;
            final byte[] content = new byte[chunkSize * 3];
            RANDOM.nextBytes(content);

            final ApackConfiguration config = ApackConfiguration.builder()
                    .chunkSize(chunkSize)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle 1 MB chunk size")
        void shouldHandle1MBChunkSize(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("1mb_chunk.apack");
            final int chunkSize = 1024 * 1024;
            final byte[] content = new byte[chunkSize * 2];
            RANDOM.nextBytes(content);

            final ApackConfiguration config = ApackConfiguration.builder()
                    .chunkSize(chunkSize)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Stream Reading Tests")
    class StreamReadingTests {

        @Test
        @DisplayName("should read entry via InputStream")
        void shouldReadEntryViaInputStream(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("stream.apack");
            final byte[] content = new byte[10_000];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final Entry entry = reader.getEntry("data.bin").orElseThrow();
                try (InputStream is = reader.getInputStream(entry)) {
                    final byte[] read = is.readAllBytes();
                    assertThat(read).isEqualTo(content);
                }
            }
        }

        @Test
        @DisplayName("should read entry in small chunks")
        void shouldReadEntryInSmallChunks(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("chunks.apack");
            final byte[] content = new byte[10_000];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final Entry entry = reader.getEntry("data.bin").orElseThrow();
                try (InputStream is = reader.getInputStream(entry)) {
                    final byte[] result = new byte[content.length];
                    int offset = 0;
                    final byte[] buffer = new byte[100]; // Small buffer
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        System.arraycopy(buffer, 0, result, offset, bytesRead);
                        offset += bytesRead;
                    }
                    assertThat(result).isEqualTo(content);
                }
            }
        }

        @Test
        @DisplayName("should read multiple entries via InputStream sequentially")
        void shouldReadMultipleEntriesViaInputStream(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("multi_stream.apack");
            final byte[] content1 = new byte[5_000];
            final byte[] content2 = new byte[7_000];
            final byte[] content3 = new byte[3_000];
            RANDOM.nextBytes(content1);
            RANDOM.nextBytes(content2);
            RANDOM.nextBytes(content3);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("file1.bin", content1);
                writer.addEntry("file2.bin", content2);
                writer.addEntry("file3.bin", content3);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // Read first entry
                Entry entry1 = reader.getEntry("file1.bin").orElseThrow();
                try (InputStream is1 = reader.getInputStream(entry1)) {
                    assertThat(is1.readAllBytes()).isEqualTo(content1);
                }

                // Read second entry
                Entry entry2 = reader.getEntry("file2.bin").orElseThrow();
                try (InputStream is2 = reader.getInputStream(entry2)) {
                    assertThat(is2.readAllBytes()).isEqualTo(content2);
                }

                // Read third entry
                Entry entry3 = reader.getEntry("file3.bin").orElseThrow();
                try (InputStream is3 = reader.getInputStream(entry3)) {
                    assertThat(is3.readAllBytes()).isEqualTo(content3);
                }
            }
        }
    }

    @Nested
    @DisplayName("Iterator Tests")
    class IteratorTests {

        @Test
        @DisplayName("should iterate over all entries")
        void shouldIterateOverAllEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("iterate.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < 10; i++) {
                    writer.addEntry("entry_" + i + ".txt",
                            ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                int count = 0;
                for (Entry entry : reader) {
                    assertThat(entry.getName()).startsWith("entry_");
                    count++;
                }
                assertThat(count).isEqualTo(10);
            }
        }

        @Test
        @DisplayName("should stream all entries")
        void shouldStreamAllEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("stream_all.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < 10; i++) {
                    writer.addEntry("entry_" + i + ".txt",
                            ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final long count = reader.stream().count();
                assertThat(count).isEqualTo(10);
            }
        }
    }

    @Nested
    @DisplayName("Write from InputStream Tests")
    class WriteFromInputStreamTests {

        @Test
        @DisplayName("should add entry from InputStream")
        void shouldAddEntryFromInputStream(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("from_stream.apack");
            final byte[] content = new byte[10_000];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should add large entry from InputStream")
        void shouldAddLargeEntryFromInputStream(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("large_stream.apack");
            final byte[] content = new byte[DEFAULT_CHUNK_SIZE * 3];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("large.bin", new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("large.bin")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("File-based Write Tests")
    class FileBasedWriteTests {

        @Test
        @DisplayName("should add entry from file path")
        void shouldAddEntryFromFilePath(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("from_file.apack");
            final Path sourceFile = tempDir.resolve("source.txt");
            final byte[] content = "File content".getBytes(StandardCharsets.UTF_8);
            Files.write(sourceFile, content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.txt", sourceFile);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("data.txt")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should add large file from path")
        void shouldAddLargeFileFromPath(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("large_file.apack");
            final Path sourceFile = tempDir.resolve("large.bin");
            final byte[] content = new byte[DEFAULT_CHUNK_SIZE * 2];
            RANDOM.nextBytes(content);
            Files.write(sourceFile, content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("large.bin", sourceFile);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("large.bin")).isEqualTo(content);
            }
        }
    }
}
