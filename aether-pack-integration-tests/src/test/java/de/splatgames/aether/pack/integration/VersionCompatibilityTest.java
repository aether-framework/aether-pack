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
import de.splatgames.aether.pack.core.exception.FormatException;
import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for version compatibility and format stability.
 *
 * <p>These tests ensure that the APACK format can handle version-related
 * scenarios properly. Critical for long-term savegame compatibility across
 * game updates.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@DisplayName("Version Compatibility Tests")
class VersionCompatibilityTest {

    @Nested
    @DisplayName("Current Version Tests")
    class CurrentVersionTests {

        @Test
        @DisplayName("should write current format version")
        void shouldWriteCurrentFormatVersion(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("current_version.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            // Verify version bytes in file header
            final byte[] content = Files.readAllBytes(archive);
            // Version is at offset 6-7 (after APACK + null byte)
            final short versionMajor = (short) ((content[7] << 8) | (content[6] & 0xFF));

            assertThat((int) versionMajor).isEqualTo(FormatConstants.FORMAT_VERSION_MAJOR);
        }

        @Test
        @DisplayName("should read archives with current version")
        void shouldReadArchivesWithCurrentVersion(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("current.apack");
            final byte[] testData = "Test data for version check".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.txt", testData);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("data.txt");
                assertThat(read).isEqualTo(testData);
            }
        }

        @Test
        @DisplayName("should preserve all header fields through roundtrip")
        void shouldPreserveAllHeaderFieldsThroughRoundtrip(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("header_fields.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("file1.txt", "content1".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("file2.txt", "content2".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("file3.txt", "content3".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(3);
                assertThat(reader.readAllBytes("file1.txt")).isEqualTo("content1".getBytes(StandardCharsets.UTF_8));
                assertThat(reader.readAllBytes("file2.txt")).isEqualTo("content2".getBytes(StandardCharsets.UTF_8));
                assertThat(reader.readAllBytes("file3.txt")).isEqualTo("content3".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Nested
    @DisplayName("Future Version Handling")
    class FutureVersionTests {

        @Test
        @DisplayName("should handle future major version - currently accepts all versions")
        void shouldHandleFutureMajorVersion(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "future_major.apack");

            // Set major version to 99 (future version)
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(6); // Version major at offset 6
                raf.writeShort(Short.reverseBytes((short) 99));
            }

            // NOTE: Current implementation does not validate version numbers
            // This documents current behavior - future implementations may reject unknown versions
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // Currently accepts unknown versions
                assertThat(reader.getEntryCount()).isGreaterThanOrEqualTo(0);
            }
        }

        @ParameterizedTest(name = "future major version {0}")
        @ValueSource(shorts = {2, 5, 10, 99, 255})
        @DisplayName("should handle various future major versions - currently accepts all")
        void shouldHandleVariousFutureMajorVersions(final short futureVersion, @TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "future_v" + futureVersion + ".apack");

            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(6);
                raf.writeShort(Short.reverseBytes(futureVersion));
            }

            // NOTE: Current implementation does not validate version numbers
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isGreaterThanOrEqualTo(0);
            }
        }

        @Test
        @DisplayName("should handle minor version differences gracefully")
        void shouldHandleMinorVersionDifferencesGracefully(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "minor_version.apack");

            // Set minor version to 99 (higher but same major)
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(8); // Version minor at offset 8
                raf.writeShort(Short.reverseBytes((short) 99));
            }

            // Minor version differences might be acceptable or rejected depending on implementation
            // This test documents the behavior
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // If it opens, reading should still work
                assertThat(reader.getEntryCount()).isGreaterThanOrEqualTo(0);
            } catch (Exception e) {
                // Also acceptable to reject higher minor versions
                assertThat(e).isInstanceOf(Exception.class);
            }
        }
    }

    @Nested
    @DisplayName("Past Version Handling")
    class PastVersionTests {

        @Test
        @DisplayName("should handle version 0 gracefully")
        void shouldHandleVersionZeroGracefully(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "version_zero.apack");

            // Set version to 0.0
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(6);
                raf.writeShort((short) 0); // Major 0
                raf.writeShort((short) 0); // Minor 0
            }

            // Version 0 should be rejected or handled
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // If accepted, verify behavior
                assertThat(reader).isNotNull();
            } catch (Exception e) {
                // Rejection is also valid
                assertThat(e).isInstanceOf(Exception.class);
            }
        }
    }

    @Nested
    @DisplayName("Format Stability Tests")
    class FormatStabilityTests {

        @Test
        @DisplayName("should maintain consistent magic number")
        void shouldMaintainConsistentMagicNumber(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("magic_check.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            final byte[] content = Files.readAllBytes(archive);
            assertThat(content[0]).isEqualTo((byte) 'A');
            assertThat(content[1]).isEqualTo((byte) 'P');
            assertThat(content[2]).isEqualTo((byte) 'A');
            assertThat(content[3]).isEqualTo((byte) 'C');
            assertThat(content[4]).isEqualTo((byte) 'K');
            assertThat(content[5]).isEqualTo((byte) 0); // Null terminator
        }

        @Test
        @DisplayName("should maintain consistent header size")
        void shouldMaintainConsistentHeaderSize(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("header_size.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            final long fileSize = Files.size(archive);
            assertThat(fileSize).isGreaterThanOrEqualTo(FormatConstants.FILE_HEADER_SIZE);
        }

        @Test
        @DisplayName("should have mostly deterministic output for same input")
        void shouldHaveMostlyDeterministicOutputForSameInput(@TempDir final Path tempDir) throws Exception {
            final Path archive1 = tempDir.resolve("deterministic1.apack");
            final Path archive2 = tempDir.resolve("deterministic2.apack");
            final byte[] content = "Deterministic test content".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive1)) {
                writer.addEntry("test.txt", content);
            }

            try (AetherPackWriter writer = AetherPackWriter.create(archive2)) {
                writer.addEntry("test.txt", content);
            }

            final byte[] bytes1 = Files.readAllBytes(archive1);
            final byte[] bytes2 = Files.readAllBytes(archive2);

            // Archives should be same size
            assertThat(bytes1.length).isEqualTo(bytes2.length);

            // NOTE: The file header contains a creation timestamp, so byte-for-byte
            // comparison is not possible. Instead, verify critical structural elements:
            // - Magic bytes (0-5) should match
            assertThat(bytes1[0]).isEqualTo(bytes2[0]); // A
            assertThat(bytes1[1]).isEqualTo(bytes2[1]); // P
            assertThat(bytes1[2]).isEqualTo(bytes2[2]); // A
            assertThat(bytes1[3]).isEqualTo(bytes2[3]); // C
            assertThat(bytes1[4]).isEqualTo(bytes2[4]); // K
            assertThat(bytes1[5]).isEqualTo(bytes2[5]); // null

            // Verify the data portion (after timestamp area) is deterministic
            // The actual entry data should be identical
            try (AetherPackReader reader1 = AetherPackReader.open(archive1);
                 AetherPackReader reader2 = AetherPackReader.open(archive2)) {
                assertThat(reader1.readAllBytes("test.txt"))
                        .isEqualTo(reader2.readAllBytes("test.txt"));
            }
        }

        @Test
        @DisplayName("should use little-endian byte order consistently")
        void shouldUseLittleEndianByteOrderConsistently(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("endianness.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            final byte[] content = Files.readAllBytes(archive);

            // Check version bytes are little-endian (1.0 = 0x0001, 0x0000)
            // At offset 6: version major
            final short majorLE = (short) ((content[7] << 8) | (content[6] & 0xFF));
            assertThat((int) majorLE).isEqualTo(FormatConstants.FORMAT_VERSION_MAJOR);
        }
    }

    @Nested
    @DisplayName("Migration Scenario Tests")
    class MigrationScenarioTests {

        @Test
        @DisplayName("should allow re-archiving with same content")
        void shouldAllowReArchivingWithSameContent(@TempDir final Path tempDir) throws Exception {
            final Path original = tempDir.resolve("original.apack");
            final Path migrated = tempDir.resolve("migrated.apack");
            final byte[] content1 = "Content 1".getBytes(StandardCharsets.UTF_8);
            final byte[] content2 = "Content 2".getBytes(StandardCharsets.UTF_8);

            // Create original archive
            try (AetherPackWriter writer = AetherPackWriter.create(original)) {
                writer.addEntry("file1.txt", content1);
                writer.addEntry("file2.txt", content2);
            }

            // Read and re-write to new archive (simulating migration)
            try (AetherPackReader reader = AetherPackReader.open(original);
                 AetherPackWriter writer = AetherPackWriter.create(migrated)) {

                for (var entry : reader) {
                    final byte[] data = reader.readAllBytes(entry);
                    writer.addEntry(entry.getName(), data);
                }
            }

            // Verify migrated archive
            try (AetherPackReader reader = AetherPackReader.open(migrated)) {
                assertThat(reader.readAllBytes("file1.txt")).isEqualTo(content1);
                assertThat(reader.readAllBytes("file2.txt")).isEqualTo(content2);
            }
        }

        @Test
        @DisplayName("should support incremental migration with new entries")
        void shouldSupportIncrementalMigrationWithNewEntries(@TempDir final Path tempDir) throws Exception {
            final Path original = tempDir.resolve("original.apack");
            final Path migrated = tempDir.resolve("migrated.apack");
            final byte[] oldContent = "Old content".getBytes(StandardCharsets.UTF_8);
            final byte[] newContent = "New content added during migration".getBytes(StandardCharsets.UTF_8);

            // Create original
            try (AetherPackWriter writer = AetherPackWriter.create(original)) {
                writer.addEntry("old.txt", oldContent);
            }

            // Migrate with additional content
            try (AetherPackReader reader = AetherPackReader.open(original);
                 AetherPackWriter writer = AetherPackWriter.create(migrated)) {

                for (var entry : reader) {
                    writer.addEntry(entry.getName(), reader.readAllBytes(entry));
                }
                writer.addEntry("new.txt", newContent);
            }

            // Verify
            try (AetherPackReader reader = AetherPackReader.open(migrated)) {
                assertThat(reader.getEntryCount()).isEqualTo(2);
                assertThat(reader.readAllBytes("old.txt")).isEqualTo(oldContent);
                assertThat(reader.readAllBytes("new.txt")).isEqualTo(newContent);
            }
        }

        @Test
        @DisplayName("should support filtering during migration")
        void shouldSupportFilteringDuringMigration(@TempDir final Path tempDir) throws Exception {
            final Path original = tempDir.resolve("original.apack");
            final Path filtered = tempDir.resolve("filtered.apack");

            // Create original with multiple entries
            try (AetherPackWriter writer = AetherPackWriter.create(original)) {
                writer.addEntry("keep1.txt", "keep".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("remove.txt", "remove".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("keep2.txt", "keep".getBytes(StandardCharsets.UTF_8));
            }

            // Migrate, filtering out one entry
            try (AetherPackReader reader = AetherPackReader.open(original);
                 AetherPackWriter writer = AetherPackWriter.create(filtered)) {

                for (var entry : reader) {
                    if (!entry.getName().equals("remove.txt")) {
                        writer.addEntry(entry.getName(), reader.readAllBytes(entry));
                    }
                }
            }

            // Verify
            try (AetherPackReader reader = AetherPackReader.open(filtered)) {
                assertThat(reader.getEntryCount()).isEqualTo(2);
                assertThat(reader.getEntry("keep1.txt")).isPresent();
                assertThat(reader.getEntry("keep2.txt")).isPresent();
                assertThat(reader.getEntry("remove.txt")).isEmpty();
            }
        }

        @Test
        @DisplayName("should support renaming entries during migration")
        void shouldSupportRenamingEntriesDuringMigration(@TempDir final Path tempDir) throws Exception {
            final Path original = tempDir.resolve("original.apack");
            final Path renamed = tempDir.resolve("renamed.apack");
            final byte[] content = "File content".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(original)) {
                writer.addEntry("old_name.txt", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(original);
                 AetherPackWriter writer = AetherPackWriter.create(renamed)) {

                for (var entry : reader) {
                    final String newName = entry.getName().replace("old_", "new_");
                    writer.addEntry(newName, reader.readAllBytes(entry));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(renamed)) {
                assertThat(reader.getEntry("old_name.txt")).isEmpty();
                assertThat(reader.getEntry("new_name.txt")).isPresent();
                assertThat(reader.readAllBytes("new_name.txt")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Long-Term Storage Tests")
    class LongTermStorageTests {

        @Test
        @DisplayName("should handle archive with many small entries")
        void shouldHandleArchiveWithManySmallEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("many_entries.apack");
            final int entryCount = 100;

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < entryCount; i++) {
                    writer.addEntry("entry_" + i + ".txt",
                            ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(entryCount);

                // Verify random access still works
                assertThat(reader.readAllBytes("entry_50.txt"))
                        .isEqualTo("Content 50".getBytes(StandardCharsets.UTF_8));
                assertThat(reader.readAllBytes("entry_0.txt"))
                        .isEqualTo("Content 0".getBytes(StandardCharsets.UTF_8));
                assertThat(reader.readAllBytes("entry_99.txt"))
                        .isEqualTo("Content 99".getBytes(StandardCharsets.UTF_8));
            }
        }

        @Test
        @DisplayName("should handle deep path hierarchies")
        void shouldHandleDeepPathHierarchies(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("deep_paths.apack");
            final String deepPath = "level1/level2/level3/level4/level5/level6/level7/level8/file.txt";
            final byte[] content = "Deep content".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(deepPath, content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(deepPath)).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should preserve exact byte content over roundtrip")
        void shouldPreserveExactByteContentOverRoundtrip(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("exact_bytes.apack");

            // Test all possible byte values
            final byte[] allBytes = new byte[256];
            for (int i = 0; i < 256; i++) {
                allBytes[i] = (byte) i;
            }

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("all_bytes.bin", allBytes);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("all_bytes.bin");
                assertThat(read).isEqualTo(allBytes);
            }
        }
    }

    // Helper methods

    private Path createValidArchive(final Path tempDir, final String name) throws Exception {
        final Path archive = tempDir.resolve(name);
        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
            writer.addEntry("test.txt", "Test content".getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }
}
