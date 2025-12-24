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
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for garbage/extra bytes handling at file boundaries.
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Garbage Handling Tests")
class GarbageHandlingTest {

    private static final Random SEEDED_RANDOM = new Random(42);

    @Nested
    @DisplayName("Trailing Garbage Tests")
    class TrailingGarbageTests {

        @Test
        @DisplayName("should handle trailing garbage (1 byte) - documents behavior")
        void shouldHandleTrailingGarbage1Byte(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "trailing_1.apack");
            final byte[] originalContent = "Test content".getBytes(StandardCharsets.UTF_8);

            // Append 1 byte of garbage
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(raf.length());
                raf.write(0x42);
            }

            // Current implementation ignores trailing garbage
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("test.txt")).isEqualTo(originalContent);
            }
        }

        @Test
        @DisplayName("should handle trailing garbage (1 KB) - documents behavior")
        void shouldHandleTrailingGarbage1KB(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "trailing_1kb.apack");
            final byte[] originalContent = "Test content".getBytes(StandardCharsets.UTF_8);

            // Append 1KB of garbage
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(raf.length());
                byte[] garbage = new byte[1024];
                SEEDED_RANDOM.nextBytes(garbage);
                raf.write(garbage);
            }

            // Current implementation ignores trailing garbage
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("test.txt")).isEqualTo(originalContent);
            }
        }

        @Test
        @DisplayName("should handle trailing garbage (1 MB) - documents behavior")
        void shouldHandleTrailingGarbage1MB(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "trailing_1mb.apack");
            final byte[] originalContent = "Test content".getBytes(StandardCharsets.UTF_8);

            // Append 1MB of garbage
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(raf.length());
                byte[] garbage = new byte[1024 * 1024];
                SEEDED_RANDOM.nextBytes(garbage);
                raf.write(garbage);
            }

            // Current implementation ignores trailing garbage
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("test.txt")).isEqualTo(originalContent);
            }
        }

        @Test
        @DisplayName("should document actual trailing garbage behavior")
        void shouldDocumentActualBehavior(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "trailing_doc.apack");
            final long originalSize = Files.size(archive);

            // Append garbage
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(raf.length());
                raf.write(new byte[]{0x00, 0x01, 0x02, 0x03});
            }

            // Document that trailing bytes are ignored
            assertThat(Files.size(archive)).isEqualTo(originalSize + 4);

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // Archive is still readable - trailing garbage is ignored
                assertThat(reader.readAllBytes("test.txt")).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Leading Garbage Tests")
    class LeadingGarbageTests {

        @Test
        @DisplayName("should reject leading garbage (magic not at start)")
        void shouldRejectLeadingGarbage(@TempDir final Path tempDir) throws Exception {
            final Path original = createValidArchive(tempDir, "original.apack");
            final byte[] validContent = Files.readAllBytes(original);

            // Create file with garbage prefix
            final Path archive = tempDir.resolve("leading_garbage.apack");
            byte[] garbage = new byte[100];
            SEEDED_RANDOM.nextBytes(garbage);

            byte[] combined = new byte[garbage.length + validContent.length];
            System.arraycopy(garbage, 0, combined, 0, garbage.length);
            System.arraycopy(validContent, 0, combined, garbage.length, validContent.length);
            Files.write(archive, combined);

            // Magic is not at position 0, should fail
            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Internal Garbage Tests")
    class InternalGarbageTests {

        @Test
        @DisplayName("should detect garbage between chunks")
        void shouldDetectGarbageBetweenChunks(@TempDir final Path tempDir) throws Exception {
            // Create multi-chunk archive
            final byte[] content = new byte[FormatConstants.DEFAULT_CHUNK_SIZE * 2];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("internal_garbage.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            final byte[] archiveContent = Files.readAllBytes(archive);

            // Find second CHNK magic and insert garbage before it
            int secondChunk = -1;
            int chunkCount = 0;
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < archiveContent.length - 4; i++) {
                if (archiveContent[i] == 'C' && archiveContent[i + 1] == 'H' &&
                    archiveContent[i + 2] == 'N' && archiveContent[i + 3] == 'K') {
                    chunkCount++;
                    if (chunkCount == 2) {
                        secondChunk = i;
                        break;
                    }
                }
            }

            if (secondChunk > 0) {
                // Insert garbage between chunks
                byte[] newContent = new byte[archiveContent.length + 50];
                System.arraycopy(archiveContent, 0, newContent, 0, secondChunk);
                byte[] garbage = new byte[50];
                SEEDED_RANDOM.nextBytes(garbage);
                System.arraycopy(garbage, 0, newContent, secondChunk, 50);
                System.arraycopy(archiveContent, secondChunk, newContent, secondChunk + 50,
                        archiveContent.length - secondChunk);
                Files.write(archive, newContent);

                // Garbage between chunks should cause an error - either during open or during read
                assertThatThrownBy(() -> {
                    try (AetherPackReader reader = AetherPackReader.open(archive)) {
                        reader.readAllBytes("data.bin");
                    }
                }).isInstanceOf(Exception.class);
            }
        }
    }

    // ===== Helper Methods =====

    private Path createValidArchive(final Path tempDir, final String name) throws Exception {
        final Path archive = tempDir.resolve(name);
        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
            writer.addEntry("test.txt", "Test content".getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }
}
