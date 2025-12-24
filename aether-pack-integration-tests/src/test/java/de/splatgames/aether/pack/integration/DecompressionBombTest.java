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

import de.splatgames.aether.pack.compression.Lz4CompressionProvider;
import de.splatgames.aether.pack.compression.ZstdCompressionProvider;
import de.splatgames.aether.pack.core.AetherPackReader;
import de.splatgames.aether.pack.core.AetherPackWriter;
import de.splatgames.aether.pack.core.ApackConfiguration;
import de.splatgames.aether.pack.core.io.ChunkProcessor;
import de.splatgames.aether.pack.core.exception.CompressionException;
import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for decompression bomb protection and compression safety.
 *
 * <p>These tests verify that the APACK format correctly handles malicious
 * compressed data that could cause:</p>
 * <ul>
 *   <li>Memory exhaustion through decompression bombs</li>
 *   <li>CPU exhaustion through malformed compressed streams</li>
 *   <li>Data corruption through compression flag mismatches</li>
 * </ul>
 *
 * <p>Critical for savegame security where untrusted data may be loaded.</p>
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Decompression Bomb Tests")
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class DecompressionBombTest {

    private static final Random SEEDED_RANDOM = new Random(42);

    @Nested
    @DisplayName("Compression Bomb Tests")
    class CompressionBombTests {

        @Test
        @DisplayName("should reject ZSTD bomb with small input claiming huge original size")
        void shouldRejectZstdBombSmallInputHugeOriginalSize(@TempDir final Path tempDir) throws Exception {
            final Path archive = createCompressedArchive(tempDir, "zstd_bomb.apack", "zstd");
            final byte[] content = Files.readAllBytes(archive);

            // Find chunk header and inflate original size claim
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 20; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    // Original size is at offset 8 from chunk magic (4 bytes, little-endian int)
                    ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                    bb.putInt(1024 * 1024 * 1024); // Claim 1 GB
                    System.arraycopy(bb.array(), 0, content, i + 8, 4);
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

        @Test
        @DisplayName("should reject LZ4 bomb with small input claiming huge original size")
        void shouldRejectLz4BombSmallInputHugeOriginalSize(@TempDir final Path tempDir) throws Exception {
            final Path archive = createCompressedArchive(tempDir, "lz4_bomb.apack", "lz4");
            final byte[] content = Files.readAllBytes(archive);

            // Find chunk header and inflate original size claim
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 20; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                    bb.putInt(1024 * 1024 * 1024); // Claim 1 GB
                    System.arraycopy(bb.array(), 0, content, i + 8, 4);
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

        @Test
        @DisplayName("should enforce MAX_CHUNK_SIZE limit on decompression")
        void shouldEnforceMaxChunkSizeLimit(@TempDir final Path tempDir) throws Exception {
            final Path archive = createCompressedArchive(tempDir, "max_chunk_bomb.apack", "zstd");
            final byte[] content = Files.readAllBytes(archive);

            // Claim original size just over MAX_CHUNK_SIZE
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 20; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                    bb.putInt(FormatConstants.MAX_CHUNK_SIZE + 1);
                    System.arraycopy(bb.array(), 0, content, i + 8, 4);
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

        @ParameterizedTest(name = "should not allocate {0}MB without data")
        @ValueSource(ints = {100, 256, 512, 1024})
        @DisplayName("should not allocate claimed size without verifying data")
        void shouldNotAllocateClaimedSizeWithoutData(final int claimedMB, @TempDir final Path tempDir) throws Exception {
            final Path archive = createCompressedArchive(tempDir, "claimed_" + claimedMB + "mb.apack", "zstd");
            final byte[] content = Files.readAllBytes(archive);

            final long claimedBytes = (long) claimedMB * 1024 * 1024;

            // Find chunk and set original size
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 20; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                    bb.putInt((int) Math.min(claimedBytes, Integer.MAX_VALUE));
                    System.arraycopy(bb.array(), 0, content, i + 8, 4);
                    break;
                }
            }
            Files.write(archive, content);

            final long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            try {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("data.bin");
                }
            } catch (Exception e) {
                // Expected
            }

            System.gc();
            final long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // Should not have allocated the claimed amount
            assertThat(memAfter - memBefore).isLessThan(claimedBytes / 2);
        }
    }

    @Nested
    @DisplayName("Compression Mismatch Tests")
    class CompressionMismatchTests {

        @Test
        @DisplayName("should detect uncompressed data with compressed flag")
        void shouldDetectUncompressedDataWithCompressedFlag(@TempDir final Path tempDir) throws Exception {
            // Create uncompressed archive
            final Path archive = tempDir.resolve("uncompressed.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", "Test content".getBytes(StandardCharsets.UTF_8));
            }

            final byte[] content = Files.readAllBytes(archive);

            // Set compression flag in file header
            content[10] |= FormatConstants.FLAG_COMPRESSED;

            // Also set chunk compression flag
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 10; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    content[i + 4] |= FormatConstants.CHUNK_FLAG_COMPRESSED;
                    break;
                }
            }
            Files.write(archive, content);

            // Reading with compression flag set but uncompressed data should fail
            assertThatThrownBy(() -> {
                final ChunkProcessor processor = ChunkProcessor.builder()
                        .compression(new ZstdCompressionProvider())
                        .build();
                try (AetherPackReader reader = AetherPackReader.open(archive, processor)) {
                    reader.readAllBytes("test.txt");
                }
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle compressed data without compressed flag")
        void shouldHandleCompressedDataWithoutCompressedFlag(@TempDir final Path tempDir) throws Exception {
            // Create compressed archive
            final Path archive = createCompressedArchive(tempDir, "compressed.apack", "zstd");
            final byte[] content = Files.readAllBytes(archive);

            // Clear compression flags
            content[10] &= ~FormatConstants.FLAG_COMPRESSED;

            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 10; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    content[i + 4] &= ~FormatConstants.CHUNK_FLAG_COMPRESSED;
                }
            }
            Files.write(archive, content);

            // Reading compressed data as raw should produce wrong data
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] data = reader.readAllBytes("data.bin");
                // Data won't be decompressed, so it should be different from original
                // or potentially corrupted/unreadable
                assertThat(data).isNotNull(); // Just verify no crash
            }
        }

        @Test
        @DisplayName("should detect truncated compressed stream")
        void shouldDetectTruncatedCompressedStream(@TempDir final Path tempDir) throws Exception {
            final Path archive = createCompressedArchive(tempDir, "truncated_zstd.apack", "zstd");
            final long originalSize = Files.size(archive);

            // Truncate significantly - to 60% of original size to ensure we hit compressed data
            // (not just the trailer/TOC which is typically ~90 bytes)
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.setLength((long) (originalSize * 0.6));
            }

            assertThatThrownBy(() -> {
                final ChunkProcessor processor = ChunkProcessor.builder()
                        .compression(new ZstdCompressionProvider())
                        .build();
                try (AetherPackReader reader = AetherPackReader.open(archive, processor)) {
                    reader.readAllBytes("data.bin");
                }
            }).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Corrupt Compressed Data Tests")
    class CorruptCompressedDataTests {

        @Test
        @DisplayName("should detect corrupt ZSTD stream")
        void shouldDetectCorruptZstdStream(@TempDir final Path tempDir) throws Exception {
            final Path archive = createCompressedArchive(tempDir, "corrupt_zstd.apack", "zstd");
            final byte[] content = Files.readAllBytes(archive);

            // Find chunk data and corrupt it
            boolean foundChunk = false;
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 30; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    // Chunk data starts after 24-byte header
                    final int dataOffset = i + FormatConstants.CHUNK_HEADER_SIZE;
                    if (dataOffset + 20 < content.length) {
                        // Corrupt some bytes in the compressed data
                        for (int j = 0; j < 10; j++) {
                            content[dataOffset + j] ^= 0xFF;
                        }
                        foundChunk = true;
                    }
                    break;
                }
            }

            assertThat(foundChunk).isTrue();
            Files.write(archive, content);

            assertThatThrownBy(() -> {
                final ChunkProcessor processor = ChunkProcessor.builder()
                        .compression(new ZstdCompressionProvider())
                        .build();
                try (AetherPackReader reader = AetherPackReader.open(archive, processor)) {
                    reader.readAllBytes("data.bin");
                }
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should detect corrupt LZ4 stream")
        void shouldDetectCorruptLz4Stream(@TempDir final Path tempDir) throws Exception {
            final Path archive = createCompressedArchive(tempDir, "corrupt_lz4.apack", "lz4");
            final byte[] content = Files.readAllBytes(archive);

            // Find chunk data and corrupt it
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 30; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    final int dataOffset = i + FormatConstants.CHUNK_HEADER_SIZE;
                    if (dataOffset + 20 < content.length) {
                        for (int j = 0; j < 10; j++) {
                            content[dataOffset + j] ^= 0xFF;
                        }
                    }
                    break;
                }
            }
            Files.write(archive, content);

            assertThatThrownBy(() -> {
                final ChunkProcessor processor = ChunkProcessor.builder()
                        .compression(new Lz4CompressionProvider())
                        .build();
                try (AetherPackReader reader = AetherPackReader.open(archive, processor)) {
                    reader.readAllBytes("data.bin");
                }
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should provide CompressionException for decompression failures")
        void shouldProvideCompressionException(@TempDir final Path tempDir) throws Exception {
            // Create a file that looks like compressed data but isn't
            final Path archive = tempDir.resolve("fake_compressed.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", "Not really compressed data".getBytes(StandardCharsets.UTF_8));
            }

            final byte[] content = Files.readAllBytes(archive);

            // Set compression flags
            content[10] |= FormatConstants.FLAG_COMPRESSED;

            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 10; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    content[i + 4] |= FormatConstants.CHUNK_FLAG_COMPRESSED;
                    break;
                }
            }
            Files.write(archive, content);

            assertThatThrownBy(() -> {
                final ChunkProcessor processor = ChunkProcessor.builder()
                        .compression(new ZstdCompressionProvider())
                        .build();
                try (AetherPackReader reader = AetherPackReader.open(archive, processor)) {
                    reader.readAllBytes("test.txt");
                }
            }).isInstanceOfAny(CompressionException.class, IOException.class);
        }

        @ParameterizedTest(name = "should handle random corruption at position {0}")
        @ValueSource(ints = {5, 10, 15, 20, 25})
        @DisplayName("should handle corruption at various positions in compressed data")
        void shouldHandleCorruptionAtVariousPositions(final int corruptionOffset, @TempDir final Path tempDir) throws Exception {
            final Path archive = createCompressedArchive(tempDir, "corrupt_pos_" + corruptionOffset + ".apack", "zstd");
            final byte[] content = Files.readAllBytes(archive);

            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 30; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    final int dataOffset = i + FormatConstants.CHUNK_HEADER_SIZE + corruptionOffset;
                    if (dataOffset < content.length) {
                        content[dataOffset] ^= 0xFF;
                    }
                    break;
                }
            }
            Files.write(archive, content);

            assertThatThrownBy(() -> {
                final ChunkProcessor processor = ChunkProcessor.builder()
                        .compression(new ZstdCompressionProvider())
                        .build();
                try (AetherPackReader reader = AetherPackReader.open(archive, processor)) {
                    reader.readAllBytes("data.bin");
                }
            }).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Compression Ratio Guard Tests")
    class CompressionRatioGuardTests {

        @Test
        @DisplayName("should allow reasonable compression ratio")
        void shouldAllowReasonableCompressionRatio(@TempDir final Path tempDir) throws Exception {
            // Highly compressible data (all zeros)
            final byte[] compressible = new byte[100_000];

            final Path archive = tempDir.resolve("compressible.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider(), 19) // Maximum compression
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("zeros.bin", compressible);
            }

            // Should decompress successfully
            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                final byte[] read = reader.readAllBytes("zeros.bin");
                assertThat(read).isEqualTo(compressible);
            }
        }

        @Test
        @DisplayName("should handle incompressible data")
        void shouldHandleIncompressibleData(@TempDir final Path tempDir) throws Exception {
            // Random data is incompressible
            final byte[] incompressible = new byte[10_000];
            SEEDED_RANDOM.nextBytes(incompressible);

            final Path archive = tempDir.resolve("incompressible.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider())
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("random.bin", incompressible);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                final byte[] read = reader.readAllBytes("random.bin");
                assertThat(read).isEqualTo(incompressible);
            }
        }

        @Test
        @DisplayName("should handle data with mixed compressibility")
        void shouldHandleMixedCompressibility(@TempDir final Path tempDir) throws Exception {
            // Half zeros, half random
            final byte[] mixed = new byte[50_000];
            SEEDED_RANDOM.nextBytes(mixed);
            for (int i = 0; i < 25_000; i++) {
                mixed[i] = 0;
            }

            final Path archive = tempDir.resolve("mixed.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider())
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("mixed.bin", mixed);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                final byte[] read = reader.readAllBytes("mixed.bin");
                assertThat(read).isEqualTo(mixed);
            }
        }
    }

    @Nested
    @DisplayName("Multi-Chunk Compression Tests")
    class MultiChunkCompressionTests {

        @Test
        @DisplayName("should handle compression bomb in middle chunk")
        void shouldHandleCompressionBombInMiddleChunk(@TempDir final Path tempDir) throws Exception {
            // Create multi-chunk archive
            final byte[] largeData = new byte[FormatConstants.DEFAULT_CHUNK_SIZE * 3];
            SEEDED_RANDOM.nextBytes(largeData);

            final Path archive = tempDir.resolve("multi_chunk.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider())
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
                writer.addEntry("large.bin", largeData);
            }

            final byte[] content = Files.readAllBytes(archive);

            // Find second chunk and corrupt its original size
            int chunkCount = 0;
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 20; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    chunkCount++;
                    if (chunkCount == 2) { // Second chunk
                        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                        bb.putInt(Integer.MAX_VALUE);
                        System.arraycopy(bb.array(), 0, content, i + 8, 4);
                        break;
                    }
                }
            }
            Files.write(archive, content);

            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive, config.createChunkProcessor())) {
                    reader.readAllBytes("large.bin");
                }
            }).isInstanceOf(Exception.class);
        }
    }

    // ===== Helper Methods =====

    private Path createCompressedArchive(final Path tempDir, final String name, final String algorithm) throws Exception {
        final Path archive = tempDir.resolve(name);
        final byte[] data = new byte[10_000];
        SEEDED_RANDOM.nextBytes(data);

        final ApackConfiguration config;
        if ("zstd".equals(algorithm)) {
            config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider(), 3)
                    .build();
        } else if ("lz4".equals(algorithm)) {
            config = ApackConfiguration.builder()
                    .compression(new Lz4CompressionProvider(), 9)
                    .build();
        } else {
            config = ApackConfiguration.DEFAULT;
        }

        try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
            writer.addEntry("data.bin", data);
        }
        return archive;
    }
}
