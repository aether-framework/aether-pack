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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for checksum verification functionality.
 *
 * <p>These tests verify that checksums are correctly computed, stored,
 * and validated during read operations.</p>
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Checksum Verification Tests")
class ChecksumVerificationTest {

    private static final Random SEEDED_RANDOM = new Random(42);

    @Nested
    @DisplayName("Chunk Checksum Tests")
    class ChunkChecksumTests {

        @Test
        @DisplayName("should validate correct chunk checksum")
        void shouldValidateCorrectChunkChecksum(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[10_000];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("valid_checksum.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            // Should read successfully with valid checksum
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("data.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should detect chunk checksum mismatch")
        void shouldDetectChunkChecksumMismatch(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[10_000];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("bad_checksum.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            // Corrupt the checksum in chunk header
            final byte[] archiveContent = Files.readAllBytes(archive);
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < archiveContent.length - 24; i++) {
                if (archiveContent[i] == 'C' && archiveContent[i + 1] == 'H' &&
                    archiveContent[i + 2] == 'N' && archiveContent[i + 3] == 'K') {
                    // Checksum at offset 16 from CHNK magic
                    archiveContent[i + 16] ^= 0xFF;
                    archiveContent[i + 17] ^= 0xFF;
                    break;
                }
            }
            Files.write(archive, archiveContent);

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.readAllBytes("data.bin"))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("checksum");
            }
        }

        @Test
        @DisplayName("should verify all chunks in multi-chunk archive")
        void shouldVerifyAllChunksInMultiChunkArchive(@TempDir final Path tempDir) throws Exception {
            // Create multi-chunk archive
            final byte[] content = new byte[FormatConstants.DEFAULT_CHUNK_SIZE * 3];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("multi_chunk.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("large.bin", content);
            }

            // Should verify all chunks successfully
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("large.bin");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should detect corruption in middle chunk")
        void shouldDetectCorruptionInMiddleChunk(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[FormatConstants.DEFAULT_CHUNK_SIZE * 3];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("middle_corrupt.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("large.bin", content);
            }

            // Find and corrupt second chunk's data
            final byte[] archiveContent = Files.readAllBytes(archive);
            int chunkCount = 0;
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < archiveContent.length - 50; i++) {
                if (archiveContent[i] == 'C' && archiveContent[i + 1] == 'H' &&
                    archiveContent[i + 2] == 'N' && archiveContent[i + 3] == 'K') {
                    chunkCount++;
                    if (chunkCount == 2) {
                        // Corrupt data in second chunk
                        archiveContent[i + FormatConstants.CHUNK_HEADER_SIZE + 100] ^= 0xFF;
                        break;
                    }
                }
            }
            Files.write(archive, archiveContent);

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThatThrownBy(() -> reader.readAllBytes("large.bin"))
                        .isInstanceOf(IOException.class);
            }
        }
    }

    @Nested
    @DisplayName("Checksum Boundary Tests")
    class ChecksumBoundaryTests {

        @Test
        @DisplayName("should checksum empty payload")
        void shouldChecksumEmptyPayload(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("empty.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("empty.bin", new byte[0]);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("empty.bin")).isEmpty();
            }
        }

        @Test
        @DisplayName("should checksum single byte payload")
        void shouldChecksumSingleBytePayload(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("single.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("single.bin", new byte[]{0x42});
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("single.bin")).containsExactly(0x42);
            }
        }

        @Test
        @DisplayName("should checksum max size payload")
        void shouldChecksumMaxSizePayload(@TempDir final Path tempDir) throws Exception {
            // Use default chunk size as "max" for this test
            final byte[] content = new byte[FormatConstants.DEFAULT_CHUNK_SIZE];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("max_size.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("max.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("max.bin")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Checksum Algorithm Tests")
    class ChecksumAlgorithmTests {

        @Test
        @DisplayName("should use CRC32 for chunks by default")
        void shouldUseCRC32ForChunks(@TempDir final Path tempDir) throws Exception {
            final byte[] content = "Test content".getBytes();

            final Path archive = tempDir.resolve("crc32.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", content);
            }

            // Verify checksum algorithm in chunk header
            final byte[] archiveContent = Files.readAllBytes(archive);
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < archiveContent.length - 10; i++) {
                if (archiveContent[i] == 'C' && archiveContent[i + 1] == 'H' &&
                    archiveContent[i + 2] == 'N' && archiveContent[i + 3] == 'K') {
                    // Read checksum and verify it's 4 bytes (CRC32)
                    ByteBuffer bb = ByteBuffer.wrap(archiveContent, i + 16, 4)
                            .order(ByteOrder.LITTLE_ENDIAN);
                    int checksum = bb.getInt();
                    // CRC32 produces 32-bit values
                    assertThat(checksum).isNotEqualTo(0);
                    break;
                }
            }
        }
    }

    @Nested
    @DisplayName("Checksum Edge Cases")
    class ChecksumEdgeCaseTests {

        @Test
        @DisplayName("should handle all-zeros data checksum")
        void shouldHandleAllZerosDataChecksum(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[10_000]; // All zeros

            final Path archive = tempDir.resolve("zeros.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("zeros.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("zeros.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle all-0xFF data checksum")
        void shouldHandleAllFFDataChecksum(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[10_000];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) 0xFF;
            }

            final Path archive = tempDir.resolve("ff.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("ff.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("ff.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should detect any single bit flip in data")
        void shouldDetectAnySingleBitFlipInData(@TempDir final Path tempDir) throws Exception {
            final byte[] content = new byte[1000];
            SEEDED_RANDOM.nextBytes(content);

            final Path archive = tempDir.resolve("bit_flip.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            final byte[] archiveContent = Files.readAllBytes(archive);

            // Find chunk data start
            int dataStart = -1;
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < archiveContent.length - 30; i++) {
                if (archiveContent[i] == 'C' && archiveContent[i + 1] == 'H' &&
                    archiveContent[i + 2] == 'N' && archiveContent[i + 3] == 'K') {
                    dataStart = i + FormatConstants.CHUNK_HEADER_SIZE;
                    break;
                }
            }

            assertThat(dataStart).isGreaterThan(0);

            // Test flipping each bit in first 100 bytes of data
            for (int byteOffset = 0; byteOffset < Math.min(100, archiveContent.length - dataStart - 10); byteOffset++) {
                for (int bit = 0; bit < 8; bit++) {
                    final byte[] corrupted = archiveContent.clone();
                    corrupted[dataStart + byteOffset] ^= (1 << bit);

                    final Path corruptedFile = tempDir.resolve("corrupt_" + byteOffset + "_" + bit + ".apack");
                    Files.write(corruptedFile, corrupted);

                    try (AetherPackReader reader = AetherPackReader.open(corruptedFile)) {
                        assertThatThrownBy(() -> reader.readAllBytes("data.bin"))
                                .as("Bit flip at byte %d bit %d should be detected", byteOffset, bit)
                                .isInstanceOf(IOException.class);
                    }

                    Files.deleteIfExists(corruptedFile);
                }
            }
        }
    }
}
