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

package de.splatgames.aether.pack.core;

import de.splatgames.aether.pack.core.entry.Entry;
import de.splatgames.aether.pack.core.entry.EntryMetadata;
import de.splatgames.aether.pack.core.exception.ApackException;
import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AetherPackWriter}.
 *
 * <p>Tests the main API for creating APACK archives including various
 * creation modes, entry addition, and lifecycle management.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@DisplayName("AetherPackWriter")
class AetherPackWriterTest {

    /** Reproducible random for test data generation. */
    private static final Random RANDOM = new Random(42);

    @Nested
    @DisplayName("Create Tests")
    class CreateTests {

        @Test
        @DisplayName("should create writer with OutputStream")
        void shouldCreateWithOutputStream() throws IOException {
            // Given
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(baos)) {
                // Then
                assertThat(writer).isNotNull();
                assertThat(writer.getEntryCount()).isZero();
            }

            // Verify output was written
            assertThat(baos.size()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should create writer with file path")
        void shouldCreateWithFilePath(@TempDir final Path tempDir) throws IOException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                assertThat(writer).isNotNull();
                assertThat(writer.getEntryCount()).isZero();
            }

            // Then
            assertThat(Files.exists(archivePath)).isTrue();
            assertThat(Files.size(archivePath)).isGreaterThan(0);
        }

        @Test
        @DisplayName("should create writer with configuration")
        void shouldCreateWithConfiguration(@TempDir final Path tempDir) throws IOException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .chunkSize(64 * 1024)
                    .enableRandomAccess(true)
                    .build();

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath, config)) {
                assertThat(writer).isNotNull();
            }

            // Then
            assertThat(Files.exists(archivePath)).isTrue();
        }

        @Test
        @DisplayName("should use default configuration when not specified")
        void shouldUseDefaultConfiguration(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("test.txt", "Hello".getBytes(StandardCharsets.UTF_8));
            }

            // Then - verify archive can be read
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getEntryCount()).isEqualTo(1);
                assertThat(reader.getFileHeader().chunkSize())
                        .isEqualTo(FormatConstants.DEFAULT_CHUNK_SIZE);
            }
        }
    }

    @Nested
    @DisplayName("Add Entry Tests")
    class AddEntryTests {

        @Test
        @DisplayName("should add simple entry with name and InputStream")
        void shouldAddSimpleEntry(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            final byte[] content = "Hello, World!".getBytes(StandardCharsets.UTF_8);

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("hello.txt", new ByteArrayInputStream(content));
            }

            // Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getEntryCount()).isEqualTo(1);
                assertThat(reader.hasEntry("hello.txt")).isTrue();
            }
        }

        @Test
        @DisplayName("should add entry with full metadata")
        void shouldAddEntryWithMetadata(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            final byte[] content = "JSON content".getBytes(StandardCharsets.UTF_8);
            final EntryMetadata metadata = EntryMetadata.builder()
                    .name("config.json")
                    .mimeType("application/json")
                    .attribute("version", 1L)
                    .attribute("author", "Test")
                    .build();

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry(metadata, new ByteArrayInputStream(content));
            }

            // Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final Entry entry = reader.getEntry("config.json").orElseThrow();
                assertThat(entry.getName()).isEqualTo("config.json");
                assertThat(entry.getMimeType()).isEqualTo("application/json");
            }
        }

        @Test
        @DisplayName("should add entry from byte array")
        void shouldAddEntryFromByteArray(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            final byte[] content = new byte[1024];
            RANDOM.nextBytes(content);

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("data.bin", content);
            }

            // Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.hasEntry("data.bin")).isTrue();
                final byte[] readContent = reader.readAllBytes("data.bin");
                assertThat(readContent).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should add entry from file path")
        void shouldAddEntryFromFilePath(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            final Path sourceFile = tempDir.resolve("source.txt");
            final byte[] content = "File content".getBytes(StandardCharsets.UTF_8);
            Files.write(sourceFile, content);

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("renamed.txt", sourceFile);
            }

            // Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.hasEntry("renamed.txt")).isTrue();
                final byte[] readContent = reader.readAllBytes("renamed.txt");
                assertThat(readContent).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should increment entry count for each added entry")
        void shouldIncrementEntryCount(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                assertThat(writer.getEntryCount()).isZero();

                writer.addEntry("file1.txt", "Content 1".getBytes(StandardCharsets.UTF_8));
                assertThat(writer.getEntryCount()).isEqualTo(1);

                writer.addEntry("file2.txt", "Content 2".getBytes(StandardCharsets.UTF_8));
                assertThat(writer.getEntryCount()).isEqualTo(2);

                writer.addEntry("file3.txt", "Content 3".getBytes(StandardCharsets.UTF_8));
                assertThat(writer.getEntryCount()).isEqualTo(3);
            }

            // Then verify on read
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getEntryCount()).isEqualTo(3);
            }
        }

        @Test
        @DisplayName("should not close input stream after adding entry")
        void shouldNotCloseInputStream(@TempDir final Path tempDir) throws IOException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            final TrackingInputStream input = new TrackingInputStream(
                    new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)));

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("test.txt", input);
            }

            // Then - input stream should not have been closed by the writer
            // (it's the caller's responsibility)
            assertThat(input.wasClosed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Close Tests")
    class CloseTests {

        @Test
        @DisplayName("should write trailer on close")
        void shouldWriteTrailerOnClose(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("entry.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            // Then - verify trailer was written (random access enabled)
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getFileHeader().hasRandomAccess()).isTrue();
                assertThat(reader.getFileHeader().trailerOffset()).isGreaterThan(0);
            }
        }

        @Test
        @DisplayName("should update file header on close when writing to file")
        void shouldUpdateFileHeaderOnClose(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("file1.txt", "content1".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("file2.txt", "content2".getBytes(StandardCharsets.UTF_8));
            }

            // Then - file header should have correct entry count
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getFileHeader().entryCount()).isEqualTo(2);
            }
        }

        @Test
        @DisplayName("close should be idempotent")
        void shouldBeIdempotent(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            final AetherPackWriter writer = AetherPackWriter.create(archivePath);
            writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8));

            // When - close multiple times
            writer.close();
            writer.close();
            writer.close();

            // Then - should not throw and file should be valid
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getEntryCount()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("should throw IOException when adding entry after close")
        void shouldThrowIOExceptionAfterClose(@TempDir final Path tempDir) throws IOException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            final AetherPackWriter writer = AetherPackWriter.create(archivePath);
            writer.close();

            // When/Then
            assertThatThrownBy(() -> writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should create empty archive")
        void shouldCreateEmptyArchive(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("empty.apack");

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                // Don't add any entries
            }

            // Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getEntryCount()).isZero();
            }
        }

        @Test
        @DisplayName("should handle many entries")
        void shouldHandleManyEntries(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("many.apack");
            final int entryCount = 100;

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                for (int i = 0; i < entryCount; i++) {
                    writer.addEntry("entry_" + i + ".txt",
                            ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            // Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.getEntryCount()).isEqualTo(entryCount);
                for (int i = 0; i < entryCount; i++) {
                    assertThat(reader.hasEntry("entry_" + i + ".txt")).isTrue();
                }
            }
        }

        @Test
        @DisplayName("should handle entry with Unicode name")
        void shouldHandleEntryWithUnicodeName(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("unicode.apack");
            final String unicodeName = "données/日本語/emoji_\uD83D\uDE00.txt";
            final byte[] content = "Unicode content".getBytes(StandardCharsets.UTF_8);

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry(unicodeName, content);
            }

            // Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.hasEntry(unicodeName)).isTrue();
                final byte[] readContent = reader.readAllBytes(unicodeName);
                assertThat(readContent).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle empty entry")
        void shouldHandleEmptyEntry(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("empty_entry.apack");
            final byte[] emptyContent = new byte[0];

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("empty.txt", emptyContent);
            }

            // Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.hasEntry("empty.txt")).isTrue();
                final byte[] readContent = reader.readAllBytes("empty.txt");
                assertThat(readContent).isEmpty();
            }
        }

        @Test
        @DisplayName("should handle large entry spanning multiple chunks")
        void shouldHandleLargeEntry(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("large.apack");
            final byte[] largeContent = new byte[512 * 1024]; // 512 KB (larger than default chunk size)
            RANDOM.nextBytes(largeContent);

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("large.bin", largeContent);
            }

            // Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final byte[] readContent = reader.readAllBytes("large.bin");
                assertThat(readContent).isEqualTo(largeContent);
            }
        }

        @Test
        @DisplayName("should handle entry names with slashes")
        void shouldHandleEntryNamesWithSlashes(@TempDir final Path tempDir) throws Exception {
            // Given
            final Path archivePath = tempDir.resolve("paths.apack");

            // When
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("a/b/c/d/file.txt", "deep".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("root.txt", "root".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("folder/file.txt", "folder".getBytes(StandardCharsets.UTF_8));
            }

            // Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThat(reader.hasEntry("a/b/c/d/file.txt")).isTrue();
                assertThat(reader.hasEntry("root.txt")).isTrue();
                assertThat(reader.hasEntry("folder/file.txt")).isTrue();
            }
        }
    }

    /**
     * Test helper: InputStream that tracks whether it was closed.
     */
    private static class TrackingInputStream extends InputStream {
        private final InputStream delegate;
        private boolean closed = false;

        TrackingInputStream(final InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return this.delegate.read();
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            return this.delegate.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            this.closed = true;
            this.delegate.close();
        }

        boolean wasClosed() {
            return this.closed;
        }
    }
}
