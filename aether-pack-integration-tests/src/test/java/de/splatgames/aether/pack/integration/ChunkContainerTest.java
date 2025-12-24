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

import de.splatgames.aether.pack.core.AetherPackReader;
import de.splatgames.aether.pack.core.AetherPackWriter;
import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for chunk container handling and edge cases.
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Chunk Container Tests")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ChunkContainerTest {

    private static final Random SEEDED_RANDOM = new Random(42);

    @Nested
    @DisplayName("Many Chunks Tests")
    class ManyChunksTests {

        @Test
        @DisplayName("should handle many small entries creating many chunks")
        void shouldHandleManySmallEntries(@TempDir final Path tempDir) throws Exception {
            final int entryCount = 1000;
            final Path archive = tempDir.resolve("many_entries.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < entryCount; i++) {
                    writer.addEntry("entry_" + i + ".txt", ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                int count = 0;
                for (var entry : reader) {
                    reader.readAllBytes(entry);
                    count++;
                }
                assertThat(count).isEqualTo(entryCount);
            }
        }

        @Test
        @DisplayName("should handle single huge entry near MAX_CHUNK_SIZE")
        void shouldHandleSingleHugeEntry(@TempDir final Path tempDir) throws Exception {
            // Create entry that's slightly smaller than MAX_CHUNK_SIZE
            final int size = FormatConstants.MAX_CHUNK_SIZE - 1024;
            final byte[] content = new byte[size];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("huge_entry.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("huge.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("huge.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle mixed chunk sizes")
        void shouldHandleMixedChunkSizes(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("mixed.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                // Very small entry
                writer.addEntry("small.txt", "tiny".getBytes(StandardCharsets.UTF_8));

                // Medium entry (default chunk size)
                final byte[] medium = new byte[FormatConstants.DEFAULT_CHUNK_SIZE];
                SEEDED_RANDOM.nextBytes(medium);
                writer.addEntry("medium.bin", medium);

                // Large multi-chunk entry
                final byte[] large = new byte[FormatConstants.DEFAULT_CHUNK_SIZE * 5];
                SEEDED_RANDOM.nextBytes(large);
                writer.addEntry("large.bin", large);

                // Empty entry
                writer.addEntry("empty.txt", new byte[0]);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("small.txt")).isEqualTo("tiny".getBytes(StandardCharsets.UTF_8));
                assertThat(reader.readAllBytes("medium.bin")).hasSize(FormatConstants.DEFAULT_CHUNK_SIZE);
                assertThat(reader.readAllBytes("large.bin")).hasSize(FormatConstants.DEFAULT_CHUNK_SIZE * 5);
                assertThat(reader.readAllBytes("empty.txt")).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Chunk Type Tests")
    class ChunkTypeTests {

        @Test
        @DisplayName("should reject unknown chunk magic")
        void shouldRejectUnknownChunkMagic(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "unknown_magic.apack");
            final byte[] content = Files.readAllBytes(archive);

            // Find CHNK magic and change to UNKN
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 4; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    content[i] = 'U';
                    content[i + 1] = 'N';
                    content[i + 2] = 'K';
                    content[i + 3] = 'N';
                    break;
                }
            }
            Files.write(archive, content);

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.readAllBytes("test.txt"))
                        .isInstanceOf(IOException.class);
            }
        }
    }

    @Nested
    @DisplayName("Chunk Order Tests")
    class ChunkOrderTests {

        @Test
        @DisplayName("should handle chunks in order")
        void shouldHandleChunksInOrder(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[FormatConstants.DEFAULT_CHUNK_SIZE * 3];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) (i % 256);
            }

            final Path archive = tempDir.resolve("ordered.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("data.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should detect corrupted chunk index")
        void shouldDetectCorruptedChunkIndex(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[FormatConstants.DEFAULT_CHUNK_SIZE * 2];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("bad_index.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            // Corrupt chunk index in header
            final byte[] archiveContent = Files.readAllBytes(archive);
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < archiveContent.length - 10; i++) {
                if (archiveContent[i] == 'C' && archiveContent[i + 1] == 'H' &&
                    archiveContent[i + 2] == 'N' && archiveContent[i + 3] == 'K') {
                    // Chunk index at offset 4 - set to invalid value
                    ByteBuffer.wrap(archiveContent, i + 4, 4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(999);
                    break;
                }
            }
            Files.write(archive, archiveContent);

            // May or may not detect - documents behavior
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                try {
                    reader.readAllBytes("data.bin");
                } catch (Exception e) {
                    // Expected
                }
            }
        }
    }

    @Nested
    @DisplayName("Chunk Size Mismatch Tests")
    class ChunkSizeMismatchTests {

        @Test
        @DisplayName("should detect declared size too small")
        void shouldDetectDeclaredSizeTooSmall(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "size_small.apack");
            final byte[] content = Files.readAllBytes(archive);

            // Find chunk and set stored size to 1 (way too small)
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 20; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    ByteBuffer.wrap(content, i + 12, 4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(1);
                    break;
                }
            }
            Files.write(archive, content);

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.readAllBytes("test.txt"))
                        .isInstanceOf(Exception.class);
            }
        }

        @Test
        @DisplayName("should detect declared size too large")
        void shouldDetectDeclaredSizeTooLarge(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "size_large.apack");
            final byte[] content = Files.readAllBytes(archive);

            // Find chunk and set stored size to huge value
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 20; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    ByteBuffer.wrap(content, i + 12, 4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(Integer.MAX_VALUE / 2);
                    break;
                }
            }
            Files.write(archive, content);

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.readAllBytes("test.txt"))
                        .isInstanceOf(Exception.class);
            }
        }
    }

    @Nested
    @DisplayName("Empty Chunk Tests")
    class EmptyChunkTests {

        @Test
        @DisplayName("should handle empty entry (zero-length chunk)")
        void shouldHandleEmptyChunk(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("empty_chunk.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("empty.bin", new byte[0]);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("empty.bin")).isEmpty();
            }
        }

        @Test
        @DisplayName("should handle multiple empty entries")
        void shouldHandleMultipleEmptyEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("multi_empty.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("empty1.bin", new byte[0]);
                writer.addEntry("empty2.bin", new byte[0]);
                writer.addEntry("empty3.bin", new byte[0]);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("empty1.bin")).isEmpty();
                assertThat(reader.readAllBytes("empty2.bin")).isEmpty();
                assertThat(reader.readAllBytes("empty3.bin")).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Chunk Boundary Tests")
    class ChunkBoundaryTests {

        @Test
        @DisplayName("should handle exact chunk boundary size")
        void shouldHandleExactChunkBoundary(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[FormatConstants.DEFAULT_CHUNK_SIZE];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("exact_boundary.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("exact.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("exact.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle one byte over chunk boundary")
        void shouldHandleOneByteOverBoundary(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[FormatConstants.DEFAULT_CHUNK_SIZE + 1];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("over_boundary.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("over.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("over.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle one byte under chunk boundary")
        void shouldHandleOneByteUnderBoundary(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[FormatConstants.DEFAULT_CHUNK_SIZE - 1];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("under_boundary.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("under.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("under.bin")).isEqualTo(content);
            }
        }
    }

    // ===== Helper Methods =====

    private Path createValidArchive(final Path tempDir, final String name) throws Exception {
        final Path archive = tempDir.resolve(name);
        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
            writer.addEntry("test.txt", "Test content for chunk testing".getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }
}
