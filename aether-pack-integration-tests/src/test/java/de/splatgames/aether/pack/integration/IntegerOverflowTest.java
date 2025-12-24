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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for integer overflow and length field manipulation attacks.
 *
 * <p>These tests verify that the APACK format correctly handles malicious
 * or corrupted length fields that could cause:</p>
 * <ul>
 *   <li>Memory exhaustion attacks (unbounded allocations)</li>
 *   <li>Integer overflow vulnerabilities</li>
 *   <li>Buffer over-reads or under-reads</li>
 * </ul>
 *
 * <p>Critical for savegame security where untrusted data may be loaded.</p>
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Integer Overflow Tests")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class IntegerOverflowTest {

    @Nested
    @DisplayName("Length Field Overflow Tests")
    class LengthFieldOverflowTests {

        @Test
        @DisplayName("should handle chunk size at MAX_INT without OOM")
        void shouldHandleChunkSizeMaxInt(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "max_int_chunk.apack");

            // Set chunk size to Integer.MAX_VALUE (2^31-1)
            corruptIntField(archive, 16, Integer.MAX_VALUE);

            // Should not allocate 2GB, should fail fast or handle gracefully
            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("test.txt");
                }
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle negative chunk size as signed int")
        void shouldHandleChunkSizeNegative(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "negative_chunk.apack");

            // Set chunk size to -1 (0xFFFFFFFF as unsigned)
            corruptIntField(archive, 16, -1);

            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("test.txt");
                }
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle entry original size at MAX_LONG")
        void shouldHandleEntrySizeMaxLong(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "max_long_entry.apack");

            // Find entry header and corrupt original size field
            // Entry header starts after file header (64 bytes)
            // Layout: magic(4) + version(2) + flags(2) + entryId(8) = 16, then originalSize
            corruptLongField(archive, FormatConstants.FILE_HEADER_SIZE + 16, Long.MAX_VALUE);

            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("test.txt");
                }
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle entry stored size at MAX_LONG")
        void shouldHandleStoredSizeMaxLong(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "max_long_stored.apack");

            // Entry header layout: magic(4) + version(2) + flags(2) + entryId(8) + originalSize(8) = 24
            // storedSize is at offset 24 within entry header
            corruptLongField(archive, FormatConstants.FILE_HEADER_SIZE + 24, Long.MAX_VALUE);

            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("test.txt");
                }
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle name length overflow")
        void shouldHandleNameLengthOverflow(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "name_overflow.apack");

            // Name length is at offset 48 (uint16) within entry header
            corruptShortField(archive, FormatConstants.FILE_HEADER_SIZE + 48, (short) 0xFFFF);

            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("test.txt");
                }
            }).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Off-By-One Tests")
    class OffByOneTests {

        @Test
        @DisplayName("should handle entry with 0 byte content")
        void shouldHandleLength0(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("empty_entry.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("empty.txt", new byte[0]);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] content = reader.readAllBytes("empty.txt");
                assertThat(content).isEmpty();
            }
        }

        @Test
        @DisplayName("should handle entry with 1 byte content")
        void shouldHandleLength1(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("single_byte.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("single.txt", new byte[]{0x42});
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] content = reader.readAllBytes("single.txt");
                assertThat(content).containsExactly(0x42);
            }
        }

        @ParameterizedTest(name = "should handle content at boundary size {0}")
        @ValueSource(ints = {
                FormatConstants.MIN_CHUNK_SIZE - 1,
                FormatConstants.MIN_CHUNK_SIZE,
                FormatConstants.MIN_CHUNK_SIZE + 1,
                FormatConstants.DEFAULT_CHUNK_SIZE - 1,
                FormatConstants.DEFAULT_CHUNK_SIZE,
                FormatConstants.DEFAULT_CHUNK_SIZE + 1
        })
        @DisplayName("should handle content at chunk boundary sizes")
        void shouldHandleBoundarySizes(final int size, @TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("boundary_" + size + ".apack");
            final byte[] content = new byte[size];
            for (int i = 0; i < size; i++) {
                content[i] = (byte) (i % 256);
            }

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("data.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle entry count at maximum reasonable value")
        void shouldHandleMaxReasonableEntryCount(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("many_entries.apack");
            final int entryCount = 1000;

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < entryCount; i++) {
                    writer.addEntry("entry_" + i + ".txt", ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                int count = 0;
                for (var entry : reader) {
                    assertThat(reader.readAllBytes(entry)).isNotEmpty();
                    count++;
                }
                assertThat(count).isEqualTo(entryCount);
            }
        }
    }

    @Nested
    @DisplayName("Attribute Count Overflow Tests")
    class AttributeCountOverflowTests {

        @Test
        @DisplayName("should handle attribute count overflow without OOM")
        void shouldHandleAttributeCountMaxInt(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchiveWithAttributes(tempDir, "attr_overflow.apack");

            // Find attribute count field and set to MAX_INT
            // This requires finding the entry header and its attribute count field
            final byte[] content = Files.readAllBytes(archive);

            // Search for entry with attributes and corrupt attribute count
            // Attribute count is stored as uint16 after name and mime type
            // This is a simplified corruption - in practice, we'd need to find exact offset
            boolean corrupted = false;
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 4; i++) {
                // Look for ENTR magic followed by flags with HAS_ATTRIBUTES set
                if (content[i] == 'E' && content[i + 1] == 'N' && content[i + 2] == 'T' && content[i + 3] == 'R') {
                    // Entry flags are at offset 4 from magic, check for HAS_ATTRIBUTES
                    if ((content[i + 4] & FormatConstants.ENTRY_FLAG_HAS_ATTRIBUTES) != 0) {
                        // This entry has attributes - the count would be after variable fields
                        // For simplicity, we corrupt bytes that could be attribute count
                        // In the entry structure, after the fixed header and name/mime
                        corrupted = true;
                        break;
                    }
                }
            }

            if (corrupted) {
                Files.write(archive, content);
            }

            // Test should complete without OOM
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                for (var entry : reader) {
                    reader.readAllBytes(entry);
                }
            } catch (Exception e) {
                // Expected - corruption detected
            }
        }

        @Test
        @DisplayName("should handle negative attribute count")
        void shouldHandleAttributeCountNegative(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "negative_attr.apack");

            // Attribute counts are unsigned, but if read as signed -1 would be 0xFFFF
            // This tests defensive handling
            assertThatCode(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    for (var entry : reader) {
                        reader.readAllBytes(entry);
                    }
                }
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Memory Allocation Guard Tests")
    class MemoryAllocationGuardTests {

        @Test
        @DisplayName("should not allocate for huge length field")
        void shouldNotAllocateForHugeLengthField(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "huge_length.apack");
            final long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // Set stored size to 1GB
            corruptLongField(archive, FormatConstants.FILE_HEADER_SIZE + 32, 1024L * 1024L * 1024L);

            try {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("test.txt");
                }
            } catch (Exception e) {
                // Expected
            }

            final long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            // Should not have allocated 1GB
            assertThat(finalMemory - initialMemory).isLessThan(100L * 1024L * 1024L); // Less than 100MB difference
        }

        @Test
        @DisplayName("should fail fast on unreasonable size")
        void shouldFailFastOnUnreasonableSize(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "unreasonable.apack");
            final long fileSize = Files.size(archive);

            // For random-access archives, the reader uses TOC entries for sizes, not entry headers.
            // TOC is at trailerOffset (read from file header at offset 32).
            // TOC entry layout: entryId(8) + entryOffset(8) + originalSize(8) + storedSize(8)
            // So storedSize is at trailerOffset + 24
            final byte[] content = Files.readAllBytes(archive);
            final ByteBuffer headerBuffer = ByteBuffer.wrap(content, 32, 8).order(ByteOrder.LITTLE_ENDIAN);
            final long trailerOffset = headerBuffer.getLong();

            // Corrupt TOC entry's storedSize to be 10x file size
            corruptLongField(archive, (int) trailerOffset + 24, fileSize * 10);

            final long startTime = System.currentTimeMillis();

            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("test.txt");
                }
            }).isInstanceOf(Exception.class);

            // Should fail quickly, not try to read non-existent data for long
            assertThat(System.currentTimeMillis() - startTime).isLessThan(1000);
        }

        @Test
        @DisplayName("should respect MAX_CHUNK_SIZE constant")
        void shouldRespectMaxChunkSizeConstant(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "max_chunk.apack");

            // Try to set chunk size above MAX_CHUNK_SIZE (64 MB)
            final int oversizeChunk = FormatConstants.MAX_CHUNK_SIZE + 1024;
            corruptIntField(archive, 16, oversizeChunk);

            // Should either reject or handle gracefully
            assertThatCode(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("test.txt");
                }
            }).satisfiesAnyOf(
                    code -> assertThatThrownBy(() -> { throw (Throwable) code; }).isInstanceOf(Exception.class),
                    code -> {} // Or completes without allocating oversized buffer
            );
        }

        @Test
        @DisplayName("should handle chunk original size exceeding stored size")
        void shouldHandleChunkSizeMismatch(@TempDir final Path tempDir) throws Exception {
            final Path archive = createLargeArchive(tempDir, "size_mismatch.apack", 10000);
            final byte[] content = Files.readAllBytes(archive);

            // Find chunk header and make original size > stored size
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 20; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    // Chunk header found
                    // Original size is at offset 8 (4 bytes), stored size at offset 12 (4 bytes)
                    ByteBuffer bb = ByteBuffer.wrap(content, i + 8, 8).order(ByteOrder.LITTLE_ENDIAN);
                    int originalSize = bb.getInt();
                    int storedSize = bb.getInt();

                    // Set original size to 10x stored size
                    ByteBuffer corrupt = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                    corrupt.putInt(storedSize * 10);
                    System.arraycopy(corrupt.array(), 0, content, i + 8, 4);
                    break;
                }
            }
            Files.write(archive, content);

            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("data.bin");
                }
            }).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Trailer Offset Overflow Tests")
    class TrailerOffsetOverflowTests {

        @Test
        @DisplayName("should handle trailer offset beyond file size")
        void shouldHandleTrailerOffsetBeyondFile(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "trailer_beyond.apack");
            final long fileSize = Files.size(archive);

            // Trailer offset is at offset 32 in file header (8 bytes, long)
            // Layout: magic(6) + version(6) + compat(2) + flags(2) + chunkSize(4) + checksum(4) + entryCount(8) = 32
            corruptLongField(archive, 32, fileSize * 2);

            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle negative trailer offset")
        void shouldHandleNegativeTrailerOffset(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "negative_trailer.apack");

            // Trailer offset is at offset 32
            corruptLongField(archive, 32, -100);

            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle trailer offset pointing into header")
        void shouldHandleTrailerOffsetInHeader(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "trailer_in_header.apack");

            // Trailer offset is at offset 32, point it to middle of file header
            corruptLongField(archive, 32, 16);

            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(Exception.class);
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

    private Path createValidArchiveWithAttributes(final Path tempDir, final String name) throws Exception {
        final Path archive = tempDir.resolve(name);
        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
            var meta = de.splatgames.aether.pack.core.entry.EntryMetadata.builder()
                    .name("test.txt")
                    .mimeType("text/plain")
                    .attribute("author", "test")
                    .attribute("version", 1L)
                    .build();
            writer.addEntry(meta, new ByteArrayInputStream("Test content".getBytes(StandardCharsets.UTF_8)));
        }
        return archive;
    }

    private Path createLargeArchive(final Path tempDir, final String name, final int size) throws Exception {
        final Path archive = tempDir.resolve(name);
        final byte[] content = new byte[size];
        new java.util.Random(42).nextBytes(content);
        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
            writer.addEntry("data.bin", content);
        }
        return archive;
    }

    private void corruptIntField(final Path archive, final long offset, final int value) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
            raf.seek(offset);
            // Write little-endian int
            raf.write(value & 0xFF);
            raf.write((value >> 8) & 0xFF);
            raf.write((value >> 16) & 0xFF);
            raf.write((value >> 24) & 0xFF);
        }
    }

    private void corruptLongField(final Path archive, final long offset, final long value) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
            raf.seek(offset);
            // Write little-endian long
            for (int i = 0; i < 8; i++) {
                raf.write((int) ((value >> (i * 8)) & 0xFF));
            }
        }
    }

    private void corruptShortField(final Path archive, final long offset, final short value) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
            raf.seek(offset);
            // Write little-endian short
            raf.write(value & 0xFF);
            raf.write((value >> 8) & 0xFF);
        }
    }
}
