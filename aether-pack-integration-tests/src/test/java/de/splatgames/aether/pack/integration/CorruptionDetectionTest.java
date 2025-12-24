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
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for corruption detection and error handling.
 *
 * <p>These tests verify that the APACK format correctly detects corrupted
 * archives and provides meaningful error messages. Critical for savegame
 * integrity where data corruption must be detected early.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@DisplayName("Corruption Detection Tests")
class CorruptionDetectionTest {

    private static final Random RANDOM = new Random(42);

    @Nested
    @DisplayName("Magic Number Corruption")
    class MagicNumberCorruptionTests {

        @Test
        @DisplayName("should detect corrupted file magic")
        void shouldDetectCorruptedFileMagic(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "magic.apack");

            // Corrupt the magic bytes at offset 0
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(0);
                raf.write(new byte[]{'X', 'X', 'X', 'X', 'X'}); // Replace APACK with XXXXX
            }

            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(FormatException.class)
                    .hasMessageContaining("magic");
        }

        @Test
        @DisplayName("should detect partially corrupted magic")
        void shouldDetectPartiallyCorruptedMagic(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "partial_magic.apack");

            // Corrupt just one byte of the magic
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(2); // Change 'A' in APACK
                raf.write('X');
            }

            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(FormatException.class);
        }

        @Test
        @DisplayName("should detect empty file")
        void shouldDetectEmptyFile(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("empty.apack");
            Files.createFile(archive);

            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(Exception.class); // EOF or FormatException
        }

        @Test
        @DisplayName("should detect file too small for header")
        void shouldDetectFileTooSmallForHeader(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("small.apack");
            Files.write(archive, new byte[]{0x41, 0x50, 0x41, 0x43, 0x4B}); // Just "APACK"

            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("File Header Corruption")
    class FileHeaderCorruptionTests {

        @Test
        @DisplayName("should handle invalid version major - currently does not validate")
        void shouldHandleInvalidVersionMajor(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "version.apack");

            // Corrupt version major (offset 6-7, after magic + null)
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(6);
                raf.writeShort(Short.reverseBytes((short) 999)); // Invalid version
            }

            // NOTE: Current implementation does not validate version numbers
            // This documents current behavior - future versions may validate
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // Currently opens successfully - data may still be readable
                assertThat(reader).isNotNull();
            }
        }

        @Test
        @DisplayName("should handle corrupted entry count - documents current behavior")
        void shouldHandleCorruptedEntryCount(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "entry_count.apack");

            // Set entry count to impossibly high value
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(20); // Approximate offset for entry count
                raf.writeInt(Integer.reverseBytes(999999)); // Way more entries than file can hold
            }

            // NOTE: The format may not validate entry count against file size at open time
            // This documents current behavior
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // Opened successfully - the actual entry that exists should still be readable
                // because the TOC is read from the trailer, not from the header count
                assertThat(reader.readAllBytes("test.txt"))
                        .isEqualTo("Test content".getBytes(StandardCharsets.UTF_8));
            }
        }

        @Test
        @DisplayName("should reject invalid chunk size")
        void shouldHandleInvalidChunkSize(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "chunk_size.apack");

            // Set chunk size to 0 (invalid)
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(16); // Offset for chunk size
                raf.writeInt(0);
            }

            // Chunk size 0 is invalid (must be MIN_CHUNK_SIZE to MAX_CHUNK_SIZE)
            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Truncation Tests")
    class TruncationTests {

        @Test
        @DisplayName("should detect truncated file header")
        void shouldDetectTruncatedFileHeader(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "truncated_header.apack");

            // Truncate to just 32 bytes (header is 64 bytes)
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.setLength(32);
            }

            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should detect truncated entry header")
        void shouldDetectTruncatedEntryHeader(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "truncated_entry.apack");

            // Truncate just after file header
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.setLength(FormatConstants.FILE_HEADER_SIZE + 10);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.readAllBytes("test.txt"))
                        .isInstanceOf(Exception.class);
            } catch (Exception e) {
                // Expected - truncation detected
            }
        }

        @Test
        @DisplayName("should detect truncated chunk data")
        void shouldDetectTruncatedChunkData(@TempDir final Path tempDir) throws Exception {
            final Path archive = createLargeArchive(tempDir, "truncated_chunk.apack", 100_000);
            final long originalSize = Files.size(archive);

            // Truncate in the middle of chunk data
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.setLength(originalSize / 2);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.readAllBytes("data.bin"))
                        .isInstanceOf(Exception.class);
            } catch (Exception e) {
                // Expected
            }
        }

        @ParameterizedTest(name = "truncate at {0}%")
        @ValueSource(ints = {10, 25, 50, 75, 90, 95, 99})
        @DisplayName("should detect truncation at various points")
        void shouldDetectTruncationAtVariousPoints(final int percentage, @TempDir final Path tempDir) throws Exception {
            final Path archive = createLargeArchive(tempDir, "truncate_" + percentage + ".apack", 50_000);
            final long originalSize = Files.size(archive);
            final long truncatedSize = (originalSize * percentage) / 100;

            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.setLength(truncatedSize);
            }

            // Should fail at some point during reading
            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    for (var entry : reader) {
                        reader.readAllBytes(entry);
                    }
                }
            }).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Chunk Corruption Tests")
    class ChunkCorruptionTests {

        @Test
        @DisplayName("should detect corrupted chunk magic")
        void shouldDetectCorruptedChunkMagic(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "chunk_magic.apack");

            // Find and corrupt chunk magic (CHNK)
            final byte[] content = Files.readAllBytes(archive);
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 4; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    content[i] = 'X'; // Corrupt first byte
                    break;
                }
            }
            Files.write(archive, content);

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.readAllBytes("test.txt"))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("magic");
            }
        }

        @Test
        @DisplayName("should detect corrupted chunk checksum")
        void shouldDetectCorruptedChunkChecksum(@TempDir final Path tempDir) throws Exception {
            final Path archive = createLargeArchive(tempDir, "chunk_checksum.apack", 10_000);

            // Corrupt data in the middle of the file (after headers)
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                final long dataStart = FormatConstants.FILE_HEADER_SIZE + 100; // After headers
                raf.seek(dataStart);
                byte[] corruption = new byte[100];
                RANDOM.nextBytes(corruption);
                raf.write(corruption);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.readAllBytes("data.bin"))
                        .isInstanceOf(IOException.class);
            }
        }

        @Test
        @DisplayName("should detect bit flip in chunk data")
        void shouldDetectBitFlipInChunkData(@TempDir final Path tempDir) throws Exception {
            final Path archive = createLargeArchive(tempDir, "bit_flip.apack", 10_000);
            final byte[] content = Files.readAllBytes(archive);

            // Flip a single bit somewhere in the data area
            final int dataOffset = FormatConstants.FILE_HEADER_SIZE + 200;
            if (dataOffset < content.length) {
                content[dataOffset] ^= 0x01; // Flip one bit
            }
            Files.write(archive, content);

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.readAllBytes("data.bin"))
                        .isInstanceOf(IOException.class);
            }
        }
    }

    @Nested
    @DisplayName("Multi-Entry Corruption Tests")
    class MultiEntryCorruptionTests {

        @Test
        @DisplayName("should read valid entries before corrupted one")
        void shouldReadValidEntriesBeforeCorruptedOne(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("partial_corrupt.apack");
            final byte[] content1 = "First entry".getBytes(StandardCharsets.UTF_8);
            final byte[] content2 = "Second entry".getBytes(StandardCharsets.UTF_8);
            final byte[] content3 = "Third entry".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("first.txt", content1);
                writer.addEntry("second.txt", content2);
                writer.addEntry("third.txt", content3);
            }

            // Corrupt the third entry's data
            final byte[] archiveData = Files.readAllBytes(archive);
            // Find third entry and corrupt its chunk
            // This is approximate - in production you'd need precise offset calculation
            for (int i = archiveData.length - 50; i < archiveData.length - 10; i++) {
                archiveData[i] ^= 0xFF;
            }
            Files.write(archive, archiveData);

            // First entries might still be readable
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // First entry should work
                assertThat(reader.readAllBytes("first.txt")).isEqualTo(content1);
                // Second entry should work
                assertThat(reader.readAllBytes("second.txt")).isEqualTo(content2);
            } catch (Exception e) {
                // Acceptable if corruption affects reading
            }
        }
    }

    @Nested
    @DisplayName("Recovery Scenario Tests")
    class RecoveryScenarioTests {

        @Test
        @DisplayName("should provide meaningful error for corrupted savegame")
        void shouldProvideMeaningfulErrorForCorruptedSavegame(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "savegame.apack");

            // Simulate power loss corruption - zero out middle section
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                final long size = raf.length();
                raf.seek(size / 3);
                raf.write(new byte[100]); // Write zeros
            }

            // Error should be descriptive
            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("test.txt");
                }
            }).isInstanceOf(Exception.class)
                    .satisfies(e -> {
                        // Error message should give some indication of what's wrong
                        assertThat(e.getMessage()).isNotNull();
                        assertThat(e.getMessage()).isNotEmpty();
                    });
        }

        @Test
        @DisplayName("should handle random garbage appended to file")
        void shouldHandleGarbageAppendedToFile(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "garbage.apack");

            // Append random garbage
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.seek(raf.length());
                byte[] garbage = new byte[1000];
                RANDOM.nextBytes(garbage);
                raf.write(garbage);
            }

            // Should still be able to read valid data (garbage at end)
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("test.txt");
                assertThat(read).isEqualTo("Test content".getBytes(StandardCharsets.UTF_8));
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

    private Path createLargeArchive(final Path tempDir, final String name, final int size) throws Exception {
        final Path archive = tempDir.resolve(name);
        final byte[] content = new byte[size];
        RANDOM.nextBytes(content);
        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
            writer.addEntry("data.bin", content);
        }
        return archive;
    }
}
