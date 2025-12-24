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
import de.splatgames.aether.pack.core.entry.Entry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for error recovery and graceful degradation.
 *
 * <p>These tests verify that the APACK format handles error scenarios
 * gracefully and provides meaningful feedback. Critical for savegame
 * reliability where users need to understand what went wrong.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@DisplayName("Error Recovery Tests")
class ErrorRecoveryTest {

    private static final Random RANDOM = new Random(42);

    @Nested
    @DisplayName("File System Error Handling")
    class FileSystemErrorTests {

        @Test
        @DisplayName("should throw for non-existent file")
        void shouldThrowForNonExistentFile(@TempDir final Path tempDir) {
            final Path nonExistent = tempDir.resolve("does_not_exist.apack");

            assertThatThrownBy(() -> AetherPackReader.open(nonExistent))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw for directory instead of file")
        void shouldThrowForDirectoryInsteadOfFile(@TempDir final Path tempDir) {
            assertThatThrownBy(() -> AetherPackReader.open(tempDir))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle read-only target gracefully")
        void shouldHandleReadOnlyTargetGracefully(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("readonly.apack");

            // First create the file
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            // Make it read-only and try to overwrite
            archive.toFile().setReadOnly();

            try {
                assertThatThrownBy(() -> AetherPackWriter.create(archive))
                        .isInstanceOf(Exception.class);
            } finally {
                archive.toFile().setWritable(true);
            }
        }
    }

    @Nested
    @DisplayName("Entry Access Error Handling")
    class EntryAccessErrorTests {

        @Test
        @DisplayName("should return empty optional for missing entry by name")
        void shouldReturnEmptyOptionalForMissingEntryByName(@TempDir final Path tempDir) throws Exception {
            final Path archive = createArchiveWithEntry(tempDir, "existing.apack", "test.txt");

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntry("nonexistent.txt")).isEmpty();
            }
        }

        @Test
        @DisplayName("should throw for readAllBytes on missing entry")
        void shouldThrowForReadAllBytesOnMissingEntry(@TempDir final Path tempDir) throws Exception {
            final Path archive = createArchiveWithEntry(tempDir, "test.apack", "test.txt");

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.readAllBytes("missing.txt"))
                        .isInstanceOf(Exception.class);
            }
        }

        @Test
        @DisplayName("should handle null entry name gracefully")
        void shouldHandleNullEntryNameGracefully(@TempDir final Path tempDir) throws Exception {
            final Path archive = createArchiveWithEntry(tempDir, "test.apack", "test.txt");

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.getEntry(null))
                        .isInstanceOf(Exception.class);
            }
        }
    }

    @Nested
    @DisplayName("Resource Cleanup Tests")
    class ResourceCleanupTests {

        @Test
        @DisplayName("should close reader properly after use")
        void shouldCloseReaderProperlyAfterUse(@TempDir final Path tempDir) throws Exception {
            final Path archive = createArchiveWithEntry(tempDir, "cleanup.apack", "test.txt");
            AetherPackReader reader = AetherPackReader.open(archive);

            // Read something
            reader.readAllBytes("test.txt");

            // Close
            reader.close();

            // Operations after close should fail
            assertThatThrownBy(() -> reader.readAllBytes("test.txt"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should close writer properly after use")
        void shouldCloseWriterProperlyAfterUse(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("writer_cleanup.apack");
            AetherPackWriter writer = AetherPackWriter.create(archive);

            writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8));
            writer.close();

            // Verify file was written correctly
            assertThat(Files.exists(archive)).isTrue();
            assertThat(Files.size(archive)).isGreaterThan(0);

            // Should be readable
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("test.txt"))
                        .isEqualTo("content".getBytes(StandardCharsets.UTF_8));
            }
        }

        @Test
        @DisplayName("should handle double close gracefully")
        void shouldHandleDoubleCloseGracefully(@TempDir final Path tempDir) throws Exception {
            final Path archive = createArchiveWithEntry(tempDir, "double_close.apack", "test.txt");

            AetherPackReader reader = AetherPackReader.open(archive);
            reader.close();

            // Second close should not throw
            assertThatCode(() -> reader.close()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should release file handle after close")
        void shouldReleaseFileHandleAfterClose(@TempDir final Path tempDir) throws Exception {
            final Path archive = createArchiveWithEntry(tempDir, "file_handle.apack", "test.txt");

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                reader.readAllBytes("test.txt");
            }

            // File should be deletable after reader is closed (on most OSes)
            // Note: This may fail on Windows if handles aren't properly released
            assertThatCode(() -> Files.delete(archive)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Partial Read Recovery Tests")
    class PartialReadRecoveryTests {

        @Test
        @DisplayName("should allow reading valid entries before invalid access")
        void shouldAllowReadingValidEntriesBeforeInvalidAccess(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("multi_entry.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("valid1.txt", "content1".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("valid2.txt", "content2".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // Read valid entries first
                assertThat(reader.readAllBytes("valid1.txt"))
                        .isEqualTo("content1".getBytes(StandardCharsets.UTF_8));

                // Invalid access
                assertThatThrownBy(() -> reader.readAllBytes("invalid.txt"))
                        .isInstanceOf(Exception.class);

                // Should still be able to read other valid entries
                assertThat(reader.readAllBytes("valid2.txt"))
                        .isEqualTo("content2".getBytes(StandardCharsets.UTF_8));
            }
        }

        @Test
        @DisplayName("should allow re-reading same entry multiple times")
        void shouldAllowReReadingSameEntryMultipleTimes(@TempDir final Path tempDir) throws Exception {
            final Path archive = createArchiveWithEntry(tempDir, "reread.apack", "test.txt");

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] expected = "Test content".getBytes(StandardCharsets.UTF_8);

                // Read multiple times
                assertThat(reader.readAllBytes("test.txt")).isEqualTo(expected);
                assertThat(reader.readAllBytes("test.txt")).isEqualTo(expected);
                assertThat(reader.readAllBytes("test.txt")).isEqualTo(expected);
            }
        }

        @Test
        @DisplayName("should handle partial stream read and close")
        void shouldHandlePartialStreamReadAndClose(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("partial_stream.apack");
            final byte[] largeContent = new byte[10000];
            RANDOM.nextBytes(largeContent);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("large.bin", largeContent);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("large.bin").orElseThrow();

                // Read only part of the stream
                try (InputStream is = reader.getInputStream(entry)) {
                    byte[] partial = new byte[100];
                    int read = is.read(partial);
                    assertThat(read).isEqualTo(100);
                    // Close without reading the rest
                }

                // Should still be able to read the full content
                assertThat(reader.readAllBytes("large.bin")).isEqualTo(largeContent);
            }
        }
    }

    @Nested
    @DisplayName("Entry Enumeration Recovery Tests")
    class EntryEnumerationRecoveryTests {

        @Test
        @DisplayName("should enumerate all entries with iterator")
        void shouldEnumerateAllEntriesWithIterator(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("enumerate.apack");
            final int entryCount = 10;

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < entryCount; i++) {
                    writer.addEntry("entry_" + i + ".txt",
                            ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                List<String> names = new ArrayList<>();
                for (Entry entry : reader) {
                    names.add(entry.getName());
                }
                assertThat(names).hasSize(entryCount);
            }
        }

        @Test
        @DisplayName("should support stream operations on entries")
        void shouldSupportStreamOperationsOnEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("stream_ops.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("a.txt", "a".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("b.txt", "b".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("c.txt", "c".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                List<String> names = reader.stream()
                        .map(Entry::getName)
                        .sorted()
                        .toList();

                assertThat(names).containsExactly("a.txt", "b.txt", "c.txt");
            }
        }
    }

    @Nested
    @DisplayName("Error Message Quality Tests")
    class ErrorMessageQualityTests {

        @Test
        @DisplayName("should provide informative error for corrupt magic")
        void shouldProvideInformativeErrorForCorruptMagic(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("bad_magic.apack");
            Files.write(archive, "NOT_APACK_FILE".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(Exception.class)
                    .satisfies(e -> {
                        assertThat(e.getMessage()).isNotNull();
                        assertThat(e.getMessage().toLowerCase()).containsAnyOf("magic", "invalid", "format");
                    });
        }

        @Test
        @DisplayName("should include file context in errors when possible")
        void shouldIncludeFileContextInErrorsWhenPossible(@TempDir final Path tempDir) throws Exception {
            final Path archive = createArchiveWithEntry(tempDir, "context.apack", "test.txt");

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Throwable thrown = catchThrowable(() -> reader.readAllBytes("missing_entry.txt"));

                assertThat(thrown).isNotNull();
                assertThat(thrown.getMessage()).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Concurrent Access Tests")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("should allow sequential reads from same reader")
        void shouldAllowSequentialReadsFromSameReader(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("sequential.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < 5; i++) {
                    writer.addEntry("file" + i + ".txt",
                            ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // Read entries in random order
                assertThat(reader.readAllBytes("file3.txt"))
                        .isEqualTo("Content 3".getBytes(StandardCharsets.UTF_8));
                assertThat(reader.readAllBytes("file0.txt"))
                        .isEqualTo("Content 0".getBytes(StandardCharsets.UTF_8));
                assertThat(reader.readAllBytes("file4.txt"))
                        .isEqualTo("Content 4".getBytes(StandardCharsets.UTF_8));
                assertThat(reader.readAllBytes("file1.txt"))
                        .isEqualTo("Content 1".getBytes(StandardCharsets.UTF_8));
                assertThat(reader.readAllBytes("file2.txt"))
                        .isEqualTo("Content 2".getBytes(StandardCharsets.UTF_8));
            }
        }

        @Test
        @DisplayName("should allow multiple independent readers on same file")
        void shouldAllowMultipleIndependentReadersOnSameFile(@TempDir final Path tempDir) throws Exception {
            final Path archive = createArchiveWithEntry(tempDir, "multi_reader.apack", "test.txt");

            try (AetherPackReader reader1 = AetherPackReader.open(archive);
                 AetherPackReader reader2 = AetherPackReader.open(archive)) {

                final byte[] expected = "Test content".getBytes(StandardCharsets.UTF_8);

                assertThat(reader1.readAllBytes("test.txt")).isEqualTo(expected);
                assertThat(reader2.readAllBytes("test.txt")).isEqualTo(expected);
            }
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("should reject empty entry name")
        void shouldRejectEmptyEntryName(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("empty_name.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                assertThatThrownBy(() -> writer.addEntry("", "content".getBytes(StandardCharsets.UTF_8)))
                        .isInstanceOf(Exception.class);
            }
        }

        @Test
        @DisplayName("should reject null content")
        void shouldRejectNullContent(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("null_content.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                assertThatThrownBy(() -> writer.addEntry("test.txt", (byte[]) null))
                        .isInstanceOf(Exception.class);
            }
        }

        @Test
        @DisplayName("should handle very long entry names")
        void shouldHandleVeryLongEntryNames(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("long_name.apack");
            final StringBuilder longName = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                longName.append("a");
            }
            longName.append(".txt");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(longName.toString(), "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(longName.toString()))
                        .isEqualTo("content".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Nested
    @DisplayName("Savegame Scenario Tests")
    class SavegameScenarioTests {

        @Test
        @DisplayName("should handle typical savegame structure")
        void shouldHandleTypicalSavegameStructure(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("savegame.apack");

            // Simulate a typical game save structure
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("meta.json", """
                        {"version": "1.0", "timestamp": "2024-12-23T10:00:00Z"}
                        """.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("player/stats.json", """
                        {"level": 99, "exp": 9999999, "gold": 1234567}
                        """.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("player/inventory.json", """
                        {"items": ["sword", "shield", "potion"]}
                        """.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("world/state.bin", new byte[1000]);
                writer.addEntry("world/npcs.json", """
                        {"npcs": [{"id": 1, "alive": true}]}
                        """.getBytes(StandardCharsets.UTF_8));
            }

            // Read back and verify structure
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(5);
                assertThat(reader.getEntry("meta.json")).isPresent();
                assertThat(reader.getEntry("player/stats.json")).isPresent();
                assertThat(reader.getEntry("player/inventory.json")).isPresent();
                assertThat(reader.getEntry("world/state.bin")).isPresent();
                assertThat(reader.getEntry("world/npcs.json")).isPresent();
            }
        }

        @Test
        @DisplayName("should support atomic save pattern")
        void shouldSupportAtomicSavePattern(@TempDir final Path tempDir) throws Exception {
            final Path finalPath = tempDir.resolve("save.apack");
            final Path tempPath = tempDir.resolve("save.apack.tmp");
            final byte[] saveData = "Critical save data".getBytes(StandardCharsets.UTF_8);

            // Write to temporary file first
            try (AetherPackWriter writer = AetherPackWriter.create(tempPath)) {
                writer.addEntry("data.txt", saveData);
            }

            // Verify temp file is valid before renaming
            try (AetherPackReader reader = AetherPackReader.open(tempPath)) {
                assertThat(reader.readAllBytes("data.txt")).isEqualTo(saveData);
            }

            // Atomic rename
            Files.move(tempPath, finalPath);

            // Final file should be valid
            try (AetherPackReader reader = AetherPackReader.open(finalPath)) {
                assertThat(reader.readAllBytes("data.txt")).isEqualTo(saveData);
            }
        }

        @Test
        @DisplayName("should handle backup and restore pattern")
        void shouldHandleBackupAndRestorePattern(@TempDir final Path tempDir) throws Exception {
            final Path original = tempDir.resolve("save.apack");
            final Path backup = tempDir.resolve("save.apack.bak");
            final byte[] originalData = "Original save".getBytes(StandardCharsets.UTF_8);
            final byte[] newData = "New save".getBytes(StandardCharsets.UTF_8);

            // Create original save
            try (AetherPackWriter writer = AetherPackWriter.create(original)) {
                writer.addEntry("data.txt", originalData);
            }

            // Backup before modifying
            Files.copy(original, backup);

            // Create new save
            try (AetherPackWriter writer = AetherPackWriter.create(original)) {
                writer.addEntry("data.txt", newData);
            }

            // Verify new save
            try (AetherPackReader reader = AetherPackReader.open(original)) {
                assertThat(reader.readAllBytes("data.txt")).isEqualTo(newData);
            }

            // Restore from backup
            Files.delete(original);
            Files.move(backup, original);

            // Verify restored save
            try (AetherPackReader reader = AetherPackReader.open(original)) {
                assertThat(reader.readAllBytes("data.txt")).isEqualTo(originalData);
            }
        }
    }

    // Helper methods

    private Path createArchiveWithEntry(final Path tempDir, final String archiveName,
                                         final String entryName) throws Exception {
        final Path archive = tempDir.resolve(archiveName);
        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
            writer.addEntry(entryName, "Test content".getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }
}
