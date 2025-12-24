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
import de.splatgames.aether.pack.core.exception.ApackException;
import de.splatgames.aether.pack.core.exception.EntryNotFoundException;
import de.splatgames.aether.pack.core.io.ChunkProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AetherPackReader}.
 *
 * <p>Tests the main API for reading APACK archives including various
 * open modes, entry access, data reading, and lifecycle management.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@DisplayName("AetherPackReader")
class AetherPackReaderTest {

    /** Reproducible random for test data generation. */
    private static final Random RANDOM = new Random(42);

    /**
     * Creates a test archive with the specified entries.
     *
     * @param archivePath the path to create the archive at
     * @param entries     map of entry name to content
     * @throws IOException if an I/O error occurs
     */
    private void createTestArchive(final Path archivePath, final String... entries) throws IOException {
        try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
            for (int i = 0; i < entries.length; i += 2) {
                writer.addEntry(entries[i], entries[i + 1].getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Nested
    @DisplayName("Open Tests")
    class OpenTests {

        @Test
        @DisplayName("should open valid archive")
        void shouldOpenValidArchive(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "file.txt", "content");

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                // Then
                assertThat(reader).isNotNull();
                assertThat(reader.getEntryCount()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("should open with ChunkProcessor")
        void shouldOpenWithChunkProcessor(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "file.txt", "content");
            final ChunkProcessor processor = ChunkProcessor.passThrough();

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath, processor)) {
                // Then
                assertThat(reader).isNotNull();
                assertThat(reader.getEntryCount()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("should open with ApackConfiguration")
        void shouldOpenWithConfiguration(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "file.txt", "content");
            final ApackConfiguration config = ApackConfiguration.DEFAULT;

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath, config)) {
                // Then
                assertThat(reader).isNotNull();
            }
        }

        @Test
        @DisplayName("should throw NoSuchFileException for non-existent file")
        void shouldThrowForNonExistentFile(@TempDir final Path tempDir) {
            // Given
            final Path nonExistent = tempDir.resolve("does_not_exist.apack");

            // When/Then
            assertThatThrownBy(() -> AetherPackReader.open(nonExistent))
                    .isInstanceOf(NoSuchFileException.class);
        }

        @Test
        @DisplayName("should throw FormatException for invalid magic")
        void shouldThrowFormatExceptionForInvalidMagic(@TempDir final Path tempDir) throws IOException {
            // Given
            final Path invalidFile = tempDir.resolve("invalid.apack");
            Files.write(invalidFile, "This is not an APACK file".getBytes(StandardCharsets.UTF_8));

            // When/Then
            assertThatThrownBy(() -> AetherPackReader.open(invalidFile))
                    .isInstanceOf(ApackException.class);
        }

        @Test
        @DisplayName("should return file header")
        void shouldReturnFileHeader(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "file.txt", "content");

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                // Then
                assertThat(reader.getFileHeader()).isNotNull();
                assertThat(reader.getFileHeader().versionMajor()).isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Nested
    @DisplayName("Get Entry Tests")
    class GetEntryTests {

        @Test
        @DisplayName("should find entry by ID")
        void shouldFindEntryById(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "file.txt", "content");

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final Entry entry = reader.getEntry(1);

                // Then
                assertThat(entry).isNotNull();
                assertThat(entry.getId()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("should throw EntryNotFoundException for invalid ID")
        void shouldThrowForInvalidId(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "file.txt", "content");

            // When/Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThatThrownBy(() -> reader.getEntry(999))
                        .isInstanceOf(EntryNotFoundException.class);
            }
        }

        @Test
        @DisplayName("should find entry by name")
        void shouldFindEntryByName(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "myfile.txt", "content");

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final Optional<Entry> entry = reader.getEntry("myfile.txt");

                // Then
                assertThat(entry).isPresent();
                assertThat(entry.get().getName()).isEqualTo("myfile.txt");
            }
        }

        @Test
        @DisplayName("should return empty Optional for missing name")
        void shouldReturnEmptyOptionalForMissingName(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "exists.txt", "content");

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final Optional<Entry> entry = reader.getEntry("does_not_exist.txt");

                // Then
                assertThat(entry).isEmpty();
            }
        }

        @Test
        @DisplayName("should check entry existence with hasEntry")
        void shouldCheckEntryExistence(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "exists.txt", "content");

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                // Then
                assertThat(reader.hasEntry("exists.txt")).isTrue();
                assertThat(reader.hasEntry("does_not_exist.txt")).isFalse();
            }
        }

        @Test
        @DisplayName("should return unmodifiable entries list")
        void shouldReturnUnmodifiableEntriesList(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "file.txt", "content");

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final List<? extends Entry> entries = reader.getEntries();

                // Then
                assertThatThrownBy(() -> ((List<Entry>) entries).clear())
                        .isInstanceOf(UnsupportedOperationException.class);
            }
        }

        @Test
        @DisplayName("should find multiple entries by name")
        void shouldFindMultipleEntriesByName(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath,
                    "alpha.txt", "content A",
                    "beta.txt", "content B",
                    "gamma.txt", "content C"
            );

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                // Then
                assertThat(reader.getEntry("alpha.txt")).isPresent();
                assertThat(reader.getEntry("beta.txt")).isPresent();
                assertThat(reader.getEntry("gamma.txt")).isPresent();
            }
        }
    }

    @Nested
    @DisplayName("Read Data Tests")
    class ReadDataTests {

        @Test
        @DisplayName("should return InputStream for entry")
        void shouldReturnInputStreamForEntry(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "file.txt", "Hello, World!");

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final Entry entry = reader.getEntry("file.txt").orElseThrow();

                try (InputStream input = reader.getInputStream(entry)) {
                    // Then
                    assertThat(input).isNotNull();
                    final byte[] data = input.readAllBytes();
                    assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("Hello, World!");
                }
            }
        }

        @Test
        @DisplayName("should read all bytes from entry")
        void shouldReadAllBytesFromEntry(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            final byte[] originalContent = new byte[1024];
            RANDOM.nextBytes(originalContent);

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("binary.dat", originalContent);
            }

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final Entry entry = reader.getEntry("binary.dat").orElseThrow();
                final byte[] readContent = reader.readAllBytes(entry);

                // Then
                assertThat(readContent).isEqualTo(originalContent);
            }
        }

        @Test
        @DisplayName("should read all bytes by entry name")
        void shouldReadAllBytesByName(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            final String content = "Test content for reading";
            createTestArchive(archivePath, "test.txt", content);

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final byte[] readContent = reader.readAllBytes("test.txt");

                // Then
                assertThat(new String(readContent, StandardCharsets.UTF_8)).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should throw EntryNotFoundException when reading non-existent entry by name")
        void shouldThrowWhenReadingNonExistentEntry(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "exists.txt", "content");

            // When/Then
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                assertThatThrownBy(() -> reader.readAllBytes("does_not_exist.txt"))
                        .isInstanceOf(EntryNotFoundException.class);
            }
        }

        @Test
        @DisplayName("should read correct data for each entry")
        void shouldReadCorrectDataForEachEntry(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath,
                    "first.txt", "First content",
                    "second.txt", "Second content",
                    "third.txt", "Third content"
            );

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                // Then
                assertThat(new String(reader.readAllBytes("first.txt"), StandardCharsets.UTF_8))
                        .isEqualTo("First content");
                assertThat(new String(reader.readAllBytes("second.txt"), StandardCharsets.UTF_8))
                        .isEqualTo("Second content");
                assertThat(new String(reader.readAllBytes("third.txt"), StandardCharsets.UTF_8))
                        .isEqualTo("Third content");
            }
        }
    }

    @Nested
    @DisplayName("Iteration Tests")
    class IterationTests {

        @Test
        @DisplayName("should iterate over all entries")
        void shouldIterateOverAllEntries(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath,
                    "a.txt", "A",
                    "b.txt", "B",
                    "c.txt", "C"
            );

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final List<String> names = new ArrayList<>();
                for (final Entry entry : reader) {
                    names.add(entry.getName());
                }

                // Then
                assertThat(names).containsExactlyInAnyOrder("a.txt", "b.txt", "c.txt");
            }
        }

        @Test
        @DisplayName("should stream all entries")
        void shouldStreamAllEntries(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath,
                    "file1.txt", "content1",
                    "file2.txt", "content2",
                    "file3.txt", "content3"
            );

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final List<String> names = reader.stream()
                        .map(Entry::getName)
                        .collect(Collectors.toList());

                // Then
                assertThat(names).containsExactlyInAnyOrder("file1.txt", "file2.txt", "file3.txt");
            }
        }

        @Test
        @DisplayName("should support filtering with stream")
        void shouldSupportFilteringWithStream(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath,
                    "data.json", "{}",
                    "config.json", "{}",
                    "readme.txt", "Hello"
            );

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final long jsonCount = reader.stream()
                        .filter(e -> e.getName().endsWith(".json"))
                        .count();

                // Then
                assertThat(jsonCount).isEqualTo(2);
            }
        }

        @Test
        @DisplayName("should return empty iterator for empty archive")
        void shouldReturnEmptyIteratorForEmptyArchive(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("empty.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                // Don't add entries
            }

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                // Then
                assertThat(reader.iterator().hasNext()).isFalse();
                assertThat(reader.stream().count()).isZero();
            }
        }
    }

    @Nested
    @DisplayName("Close Tests")
    class CloseTests {

        @Test
        @DisplayName("should close underlying channel")
        void shouldCloseUnderlyingChannel(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "file.txt", "content");

            final AetherPackReader reader = AetherPackReader.open(archivePath);

            // When
            reader.close();

            // Then - reading should fail
            assertThatThrownBy(() -> reader.readAllBytes("file.txt"))
                    .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("close should be idempotent")
        void closeShouldBeIdempotent(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "file.txt", "content");

            final AetherPackReader reader = AetherPackReader.open(archivePath);

            // When/Then - should not throw
            reader.close();
            reader.close();
            reader.close();
        }

        @Test
        @DisplayName("should throw IOException on getInputStream after close")
        void shouldThrowIOExceptionOnGetInputStreamAfterClose(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            createTestArchive(archivePath, "file.txt", "content");

            final AetherPackReader reader = AetherPackReader.open(archivePath);
            final Entry entry = reader.getEntry("file.txt").orElseThrow();
            reader.close();

            // When/Then
            assertThatThrownBy(() -> reader.getInputStream(entry))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle Unicode entry names")
        void shouldHandleUnicodeEntryNames(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("unicode.apack");
            final String unicodeName = "文件/данные/αρχείο.txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry(unicodeName, "Unicode content".getBytes(StandardCharsets.UTF_8));
            }

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                // Then
                assertThat(reader.hasEntry(unicodeName)).isTrue();
                assertThat(reader.getEntry(unicodeName)).isPresent();
            }
        }

        @Test
        @DisplayName("should handle empty entries")
        void shouldHandleEmptyEntries(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("empty_content.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("empty.txt", new byte[0]);
            }

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final byte[] content = reader.readAllBytes("empty.txt");

                // Then
                assertThat(content).isEmpty();
            }
        }

        @Test
        @DisplayName("should handle large entries")
        void shouldHandleLargeEntries(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("large.apack");
            final byte[] largeContent = new byte[512 * 1024]; // 512 KB
            RANDOM.nextBytes(largeContent);

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("large.bin", largeContent);
            }

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final byte[] readContent = reader.readAllBytes("large.bin");

                // Then
                assertThat(readContent).isEqualTo(largeContent);
            }
        }

        @Test
        @DisplayName("should provide entry metadata")
        void shouldProvideEntryMetadata(@TempDir final Path tempDir) throws IOException, ApackException {
            // Given
            final Path archivePath = tempDir.resolve("test.apack");
            final byte[] content = "Test content here".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
                writer.addEntry("test.txt", content);
            }

            // When
            try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
                final Entry entry = reader.getEntry("test.txt").orElseThrow();

                // Then
                assertThat(entry.getName()).isEqualTo("test.txt");
                assertThat(entry.getId()).isPositive();
            }
        }
    }
}
