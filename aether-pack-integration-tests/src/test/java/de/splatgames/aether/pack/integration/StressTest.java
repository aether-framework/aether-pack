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

import de.splatgames.aether.pack.compression.ZstdCompressionProvider;
import de.splatgames.aether.pack.core.AetherPackReader;
import de.splatgames.aether.pack.core.AetherPackWriter;
import de.splatgames.aether.pack.core.ApackConfiguration;
import de.splatgames.aether.pack.core.entry.Entry;
import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Stress tests for APACK format.
 *
 * <p>These tests verify the format handles large archives, many entries,
 * and extreme scenarios that might occur in production savegame usage.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@DisplayName("Stress Tests")
class StressTest {

    private static final Random RANDOM = new Random(42);

    @Nested
    @DisplayName("Many Entries Tests")
    class ManyEntriesTests {

        @Test
        @DisplayName("should handle 500 entries")
        void shouldHandle500Entries(@TempDir final Path tempDir) throws Exception {
            testManyEntries(tempDir, 500);
        }

        @Test
        @DisplayName("should handle 1000 entries")
        void shouldHandle1000Entries(@TempDir final Path tempDir) throws Exception {
            testManyEntries(tempDir, 1000);
        }

        @Test
        @DisplayName("should handle 2000 entries")
        void shouldHandle2000Entries(@TempDir final Path tempDir) throws Exception {
            testManyEntries(tempDir, 2000);
        }

        private void testManyEntries(final Path tempDir, final int count) throws Exception {
            final Path archive = tempDir.resolve("many_" + count + ".apack");
            final Map<String, byte[]> entries = new HashMap<>();

            // Create archive with many entries
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < count; i++) {
                    final String name = String.format("entry_%05d.dat", i);
                    final byte[] content = new byte[100 + (i % 500)];
                    RANDOM.nextBytes(content);
                    entries.put(name, content);
                    writer.addEntry(name, content);
                }
            }

            // Verify all entries
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(count);

                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    final byte[] read = reader.readAllBytes(entry.getKey());
                    assertThat(read).isEqualTo(entry.getValue());
                }
            }
        }

        @Test
        @DisplayName("should iterate over 1000 entries efficiently")
        void shouldIterateOver1000Entries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("iterate_1000.apack");
            final int count = 1000;

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < count; i++) {
                    writer.addEntry("entry_" + i + ".txt",
                            ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                int iteratedCount = 0;
                for (Entry entry : reader) {
                    assertThat(entry.getName()).startsWith("entry_");
                    iteratedCount++;
                }
                assertThat(iteratedCount).isEqualTo(count);
            }
        }

        @Test
        @DisplayName("should stream 1000 entries efficiently")
        void shouldStream1000Entries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("stream_1000.apack");
            final int count = 1000;

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < count; i++) {
                    writer.addEntry("entry_" + i + ".txt",
                            ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final long streamCount = reader.stream()
                        .filter(e -> e.getName().startsWith("entry_"))
                        .count();
                assertThat(streamCount).isEqualTo(count);
            }
        }
    }

    @Nested
    @DisplayName("Large Entry Tests")
    class LargeEntryTests {

        @Test
        @DisplayName("should handle 1 MB entry")
        void shouldHandle1MBEntry(@TempDir final Path tempDir) throws Exception {
            testLargeEntry(tempDir, 1024 * 1024, "1mb.apack");
        }

        @Test
        @DisplayName("should handle 5 MB entry")
        void shouldHandle5MBEntry(@TempDir final Path tempDir) throws Exception {
            testLargeEntry(tempDir, 5 * 1024 * 1024, "5mb.apack");
        }

        @Test
        @DisplayName("should handle 10 MB entry")
        void shouldHandle10MBEntry(@TempDir final Path tempDir) throws Exception {
            testLargeEntry(tempDir, 10 * 1024 * 1024, "10mb.apack");
        }

        private void testLargeEntry(final Path tempDir, final int size, final String name) throws Exception {
            final Path archive = tempDir.resolve(name);
            final byte[] content = new byte[size];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("large.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("large.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle multiple large entries")
        void shouldHandleMultipleLargeEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("multi_large.apack");
            final int entrySize = 2 * 1024 * 1024; // 2 MB each
            final int entryCount = 5;
            final List<byte[]> contents = new ArrayList<>();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < entryCount; i++) {
                    final byte[] content = new byte[entrySize];
                    RANDOM.nextBytes(content);
                    contents.add(content);
                    writer.addEntry("large_" + i + ".bin", content);
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(entryCount);
                for (int i = 0; i < entryCount; i++) {
                    final byte[] read = reader.readAllBytes("large_" + i + ".bin");
                    assertThat(read).isEqualTo(contents.get(i));
                }
            }
        }
    }

    @Nested
    @DisplayName("Mixed Size Tests")
    class MixedSizeTests {

        @Test
        @DisplayName("should handle wildly mixed entry sizes")
        void shouldHandleMixedEntrySizes(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("mixed.apack");
            final Map<String, byte[]> entries = new HashMap<>();

            // Create entries of various sizes: 1B, 10B, 100B, 1KB, 10KB, 100KB, 1MB
            final int[] sizes = {1, 10, 100, 1024, 10240, 102400, 1024 * 1024};

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < sizes.length; i++) {
                    final String name = "size_" + sizes[i] + ".bin";
                    final byte[] content = new byte[sizes[i]];
                    RANDOM.nextBytes(content);
                    entries.put(name, content);
                    writer.addEntry(name, content);
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    final byte[] read = reader.readAllBytes(entry.getKey());
                    assertThat(read).isEqualTo(entry.getValue());
                }
            }
        }

        @Test
        @DisplayName("should handle alternating tiny and huge entries")
        void shouldHandleAlternatingTinyAndHuge(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("alternating.apack");
            final Map<String, byte[]> entries = new HashMap<>();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < 20; i++) {
                    final int size = (i % 2 == 0) ? 1 : 500_000; // 1 byte or 500 KB
                    final String name = "entry_" + i + ".bin";
                    final byte[] content = new byte[size];
                    RANDOM.nextBytes(content);
                    entries.put(name, content);
                    writer.addEntry(name, content);
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    final byte[] read = reader.readAllBytes(entry.getKey());
                    assertThat(read).isEqualTo(entry.getValue());
                }
            }
        }
    }

    @Nested
    @DisplayName("Chunk Boundary Tests")
    class ChunkBoundaryTests {

        @ParameterizedTest(name = "entries of size {0} bytes")
        @ValueSource(ints = {
                FormatConstants.DEFAULT_CHUNK_SIZE - 100,
                FormatConstants.DEFAULT_CHUNK_SIZE - 1,
                FormatConstants.DEFAULT_CHUNK_SIZE,
                FormatConstants.DEFAULT_CHUNK_SIZE + 1,
                FormatConstants.DEFAULT_CHUNK_SIZE + 100,
                FormatConstants.DEFAULT_CHUNK_SIZE * 2 - 1,
                FormatConstants.DEFAULT_CHUNK_SIZE * 2,
                FormatConstants.DEFAULT_CHUNK_SIZE * 2 + 1
        })
        @DisplayName("should handle entries at chunk boundaries")
        void shouldHandleEntriesAtChunkBoundaries(final int size, @TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("boundary_" + size + ".apack");
            final byte[] content = new byte[size];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("boundary.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("boundary.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle many entries exactly at chunk size")
        void shouldHandleManyExactChunkSizeEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("exact_chunks.apack");
            final int entryCount = 10;
            final List<byte[]> contents = new ArrayList<>();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < entryCount; i++) {
                    final byte[] content = new byte[FormatConstants.DEFAULT_CHUNK_SIZE];
                    RANDOM.nextBytes(content);
                    contents.add(content);
                    writer.addEntry("chunk_" + i + ".bin", content);
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                for (int i = 0; i < entryCount; i++) {
                    final byte[] read = reader.readAllBytes("chunk_" + i + ".bin");
                    assertThat(read).isEqualTo(contents.get(i));
                }
            }
        }
    }

    @Nested
    @DisplayName("Compression Stress Tests")
    class CompressionStressTests {

        @Test
        @DisplayName("should compress 1000 small entries efficiently")
        void shouldCompress1000SmallEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("compressed_1000.apack");
            final int count = 1000;

            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider(), 3)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                for (int i = 0; i < count; i++) {
                    // Compressible content (repeated text)
                    final String content = "Entry " + i + " content. ".repeat(10);
                    writer.addEntry("entry_" + i + ".txt", content.getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                assertThat(reader.getEntryCount()).isEqualTo(count);

                // Verify a sample of entries
                for (int i = 0; i < count; i += 100) {
                    final String expected = "Entry " + i + " content. ".repeat(10);
                    final byte[] read = reader.readAllBytes("entry_" + i + ".txt");
                    assertThat(new String(read, StandardCharsets.UTF_8)).isEqualTo(expected);
                }
            }
        }

        @Test
        @DisplayName("should handle highly compressible large entry")
        void shouldHandleHighlyCompressibleLargeEntry(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("compressible_large.apack");
            final byte[] content = new byte[5 * 1024 * 1024]; // 5 MB of zeros

            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider(), 3)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("zeros.bin", content);
            }

            // Archive should be much smaller than 5 MB
            final long archiveSize = Files.size(archive);
            assertThat(archiveSize).isLessThan(1024 * 1024); // Should be < 1 MB

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                final byte[] read = reader.readAllBytes("zeros.bin");
                assertThat(read).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Deep Path Tests")
    class DeepPathTests {

        @Test
        @DisplayName("should handle 20-level deep paths")
        void shouldHandle20LevelDeepPaths(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("deep_20.apack");
            final StringBuilder pathBuilder = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                pathBuilder.append("level").append(i).append("/");
            }
            pathBuilder.append("file.txt");
            final String deepPath = pathBuilder.toString();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(deepPath, "Deep content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.hasEntry(deepPath)).isTrue();
                final byte[] read = reader.readAllBytes(deepPath);
                assertThat(new String(read, StandardCharsets.UTF_8)).isEqualTo("Deep content");
            }
        }

        @Test
        @DisplayName("should handle 50 entries with deep paths")
        void shouldHandle50EntriesWithDeepPaths(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("deep_many.apack");
            final Map<String, byte[]> entries = new HashMap<>();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < 50; i++) {
                    final String path = "a/b/c/d/e/f/g/h/i/j/file_" + i + ".dat";
                    final byte[] content = ("Content " + i).getBytes(StandardCharsets.UTF_8);
                    entries.put(path, content);
                    writer.addEntry(path, content);
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    assertThat(reader.readAllBytes(entry.getKey())).isEqualTo(entry.getValue());
                }
            }
        }
    }

    @Nested
    @DisplayName("Random Access Pattern Tests")
    class RandomAccessPatternTests {

        @Test
        @DisplayName("should handle random access to 100 entries")
        void shouldHandleRandomAccessTo100Entries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("random_access.apack");
            final int count = 100;
            final Map<String, byte[]> entries = new HashMap<>();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < count; i++) {
                    final String name = "entry_" + i + ".bin";
                    final byte[] content = new byte[1000 + i];
                    RANDOM.nextBytes(content);
                    entries.put(name, content);
                    writer.addEntry(name, content);
                }
            }

            // Access entries in random order multiple times
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final Random accessRandom = new Random(123);
                for (int i = 0; i < 200; i++) {
                    final int index = accessRandom.nextInt(count);
                    final String name = "entry_" + index + ".bin";
                    final byte[] read = reader.readAllBytes(name);
                    assertThat(read).isEqualTo(entries.get(name));
                }
            }
        }

        @Test
        @DisplayName("should handle repeated access to same entry")
        void shouldHandleRepeatedAccessToSameEntry(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("repeated.apack");
            final byte[] content = new byte[10_000];
            RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("target.bin", content);
                // Add some other entries
                for (int i = 0; i < 10; i++) {
                    writer.addEntry("other_" + i + ".bin", new byte[100]);
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // Read the same entry 100 times
                for (int i = 0; i < 100; i++) {
                    final byte[] read = reader.readAllBytes("target.bin");
                    assertThat(read).isEqualTo(content);
                }
            }
        }
    }
}
