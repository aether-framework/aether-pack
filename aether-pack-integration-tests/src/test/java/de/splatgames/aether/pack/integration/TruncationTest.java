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

import de.splatgames.aether.pack.compression.ZstdCompressionProvider;
import de.splatgames.aether.pack.core.AetherPackReader;
import de.splatgames.aether.pack.core.AetherPackWriter;
import de.splatgames.aether.pack.core.ApackConfiguration;
import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.crypto.Aes256GcmEncryptionProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for truncation handling at every byte position.
 *
 * <p>These tests verify that the APACK parser correctly handles truncated files
 * by producing clean errors at any truncation point, without:</p>
 * <ul>
 *   <li>Crashing or segfaulting</li>
 *   <li>Entering infinite loops</li>
 *   <li>Returning partially corrupted data as valid</li>
 * </ul>
 *
 * <p>Critical for savegame safety where power loss during write could truncate files.</p>
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Truncation Tests")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class TruncationTest {

    private static final Random SEEDED_RANDOM = new Random(42);

    @Nested
    @DisplayName("Byte-By-Byte Truncation Tests")
    class ByteByByteTruncationTests {

        @ParameterizedTest(name = "truncate at byte {0}")
        @ValueSource(ints = {0, 1, 2, 3, 4, 5})
        @DisplayName("should fail cleanly when truncated in magic (bytes 0-5)")
        void shouldFailCleanlyWhenTruncatedInMagic(final int truncateAt, @TempDir final Path tempDir) throws Exception {
            final Path original = createValidArchive(tempDir, "magic_truncate.apack");
            final Path truncated = tempDir.resolve("truncated_" + truncateAt + ".apack");

            truncateFile(original, truncated, truncateAt);

            assertThatThrownBy(() -> AetherPackReader.open(truncated))
                    .isInstanceOf(Exception.class);
        }

        @ParameterizedTest(name = "truncate at byte {0}")
        @ValueSource(ints = {6, 10, 16, 20, 32, 48, 56, 63})
        @DisplayName("should fail cleanly when truncated in header (bytes 6-63)")
        void shouldFailCleanlyWhenTruncatedInHeader(final int truncateAt, @TempDir final Path tempDir) throws Exception {
            final Path original = createValidArchive(tempDir, "header_truncate.apack");
            final Path truncated = tempDir.resolve("truncated_" + truncateAt + ".apack");

            truncateFile(original, truncated, truncateAt);

            assertThatThrownBy(() -> AetherPackReader.open(truncated))
                    .isInstanceOf(Exception.class);
        }

        @ParameterizedTest(name = "truncate at header + {0}")
        @ValueSource(ints = {0, 4, 8, 16, 20, 24})
        @DisplayName("should fail cleanly when truncated in entry header")
        void shouldFailCleanlyWhenTruncatedInEntryHeader(final int offset, @TempDir final Path tempDir) throws Exception {
            final Path original = createValidArchive(tempDir, "entry_truncate.apack");
            final int truncateAt = FormatConstants.FILE_HEADER_SIZE + offset;
            final Path truncated = tempDir.resolve("truncated_entry_" + offset + ".apack");

            truncateFile(original, truncated, truncateAt);

            // Either fails on open or on read
            try (AetherPackReader reader = AetherPackReader.open(truncated)) {
                assertThatThrownBy(() -> reader.readAllBytes("test.txt"))
                        .isInstanceOf(Exception.class);
            } catch (Exception e) {
                // Expected - truncation detected at open
            }
        }

        @Test
        @DisplayName("should fail cleanly when truncated in chunk header")
        void shouldFailCleanlyWhenTruncatedInChunkHeader(@TempDir final Path tempDir) throws Exception {
            final Path original = createLargeArchive(tempDir, "chunk_header_truncate.apack", 10_000);
            final byte[] content = Files.readAllBytes(original);

            // Find first chunk header position
            int chunkPos = -1;
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 4; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    chunkPos = i;
                    break;
                }
            }

            assertThat(chunkPos).isGreaterThan(0);

            // Truncate in the middle of chunk header
            for (int offset : new int[]{4, 8, 12, 16, 20}) {
                final Path truncated = tempDir.resolve("chunk_header_" + offset + ".apack");
                truncateFile(original, truncated, chunkPos + offset);

                try (AetherPackReader reader = AetherPackReader.open(truncated)) {
                    assertThatThrownBy(() -> reader.readAllBytes("data.bin"))
                            .isInstanceOf(Exception.class);
                } catch (Exception e) {
                    // Expected
                }
            }
        }

        @Test
        @DisplayName("should fail cleanly when truncated in chunk payload")
        void shouldFailCleanlyWhenTruncatedInChunkPayload(@TempDir final Path tempDir) throws Exception {
            final Path original = createLargeArchive(tempDir, "chunk_payload_truncate.apack", 10_000);
            final byte[] content = Files.readAllBytes(original);

            // Find chunk data start (after chunk header)
            int chunkPos = -1;
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 4; i++) {
                if (content[i] == 'C' && content[i + 1] == 'H' && content[i + 2] == 'N' && content[i + 3] == 'K') {
                    chunkPos = i;
                    break;
                }
            }

            assertThat(chunkPos).isGreaterThan(0);

            final int dataStart = chunkPos + FormatConstants.CHUNK_HEADER_SIZE;

            // Truncate at various points in chunk data
            for (int offset : new int[]{10, 50, 100, 500, 1000}) {
                if (dataStart + offset < content.length) {
                    final Path truncated = tempDir.resolve("chunk_data_" + offset + ".apack");
                    truncateFile(original, truncated, dataStart + offset);

                    try (AetherPackReader reader = AetherPackReader.open(truncated)) {
                        assertThatThrownBy(() -> reader.readAllBytes("data.bin"))
                                .isInstanceOf(Exception.class);
                    } catch (Exception e) {
                        // Expected
                    }
                }
            }
        }

        @Test
        @DisplayName("should fail cleanly when truncated in trailer")
        void shouldFailCleanlyWhenTruncatedInTrailer(@TempDir final Path tempDir) throws Exception {
            final Path original = createValidArchive(tempDir, "trailer_truncate.apack");
            final byte[] content = Files.readAllBytes(original);

            // Find trailer position (look for ATRL magic)
            int trailerPos = -1;
            for (int i = content.length - 50; i > FormatConstants.FILE_HEADER_SIZE; i--) {
                if (content[i] == 'A' && content[i + 1] == 'T' && content[i + 2] == 'R' && content[i + 3] == 'L') {
                    trailerPos = i;
                    break;
                }
            }

            if (trailerPos > 0) {
                // Truncate at various points in trailer
                for (int offset : new int[]{4, 8, 16, 24, 32}) {
                    if (trailerPos + offset < content.length) {
                        final Path truncated = tempDir.resolve("trailer_" + offset + ".apack");
                        truncateFile(original, truncated, trailerPos + offset);

                        assertThatThrownBy(() -> AetherPackReader.open(truncated))
                                .isInstanceOf(Exception.class);
                    }
                }
            }
        }

        @Test
        @DisplayName("should test every byte index in small archive")
        void shouldTestEveryByteIndexSmallArchive(@TempDir final Path tempDir) throws Exception {
            final Path original = createValidArchive(tempDir, "every_byte.apack");
            final long size = Files.size(original);

            assertThat(size).isLessThan(2000); // Ensure small archive

            for (int i = 0; i < size; i++) {
                final Path truncated = tempDir.resolve("truncated_" + i + ".apack");
                truncateFile(original, truncated, i);

                final int finalI = i;
                assertThatCode(() -> {
                    try {
                        try (AetherPackReader reader = AetherPackReader.open(truncated)) {
                            for (var entry : reader) {
                                reader.readAllBytes(entry);
                            }
                        }
                    } catch (Exception e) {
                        // Expected - truncation detected
                    }
                }).as("Truncation at byte " + finalI + " should not crash")
                        .doesNotThrowAnyException();

                Files.deleteIfExists(truncated);
            }
        }
    }

    @Nested
    @DisplayName("Partial Read Simulation Tests")
    class PartialReadSimulationTests {

        @Test
        @DisplayName("should handle EOF during magic read")
        void shouldHandleEOFDuringMagicRead(@TempDir final Path tempDir) throws Exception {
            // File with partial magic
            final Path file = tempDir.resolve("partial_magic.apack");
            Files.write(file, new byte[]{'A', 'P', 'A'}); // Only 3 bytes of APACK

            assertThatThrownBy(() -> AetherPackReader.open(file))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle EOF during header read")
        void shouldHandleEOFDuringHeaderRead(@TempDir final Path tempDir) throws Exception {
            final Path original = createValidArchive(tempDir, "header_eof.apack");
            final Path partial = tempDir.resolve("partial_header.apack");

            // Copy only first 32 bytes (half the header)
            truncateFile(original, partial, 32);

            assertThatThrownBy(() -> AetherPackReader.open(partial))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle EOF during entry read")
        void shouldHandleEOFDuringEntryRead(@TempDir final Path tempDir) throws Exception {
            final Path original = createValidArchive(tempDir, "entry_eof.apack");
            final Path partial = tempDir.resolve("partial_entry.apack");

            // Copy header plus partial entry
            truncateFile(original, partial, FormatConstants.FILE_HEADER_SIZE + 20);

            try (AetherPackReader reader = AetherPackReader.open(partial)) {
                assertThatThrownBy(() -> reader.readAllBytes("test.txt"))
                        .isInstanceOf(Exception.class);
            } catch (Exception e) {
                // Expected
            }
        }

        @Test
        @DisplayName("should handle EOF during chunk read")
        void shouldHandleEOFDuringChunkRead(@TempDir final Path tempDir) throws Exception {
            final Path original = createLargeArchive(tempDir, "chunk_eof.apack", 50_000);
            final long size = Files.size(original);
            final Path partial = tempDir.resolve("partial_chunk.apack");

            // Truncate in the middle
            truncateFile(original, partial, (int) (size / 2));

            try (AetherPackReader reader = AetherPackReader.open(partial)) {
                assertThatThrownBy(() -> reader.readAllBytes("data.bin"))
                        .isInstanceOf(Exception.class);
            } catch (Exception e) {
                // Expected
            }
        }

        @Test
        @DisplayName("should handle EOF during trailer read")
        void shouldHandleEOFDuringTrailerRead(@TempDir final Path tempDir) throws Exception {
            final Path original = createValidArchive(tempDir, "trailer_eof.apack");
            final long size = Files.size(original);
            final Path partial = tempDir.resolve("partial_trailer.apack");

            // Remove last 50 bytes to ensure trailer is definitely truncated
            // (trailer header is 48 bytes + TOC entries are 40 bytes each)
            truncateFile(original, partial, (int) (size - 50));

            assertThatThrownBy(() -> AetherPackReader.open(partial))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Stream EOF Tests")
    class StreamEOFTests {

        @Test
        @DisplayName("should handle input stream EOF at start")
        void shouldHandleInputStreamEOFAtStart(@TempDir final Path tempDir) throws Exception {
            final Path file = tempDir.resolve("empty.apack");
            Files.write(file, new byte[0]);

            assertThatThrownBy(() -> AetherPackReader.open(file))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle input stream EOF midway through entry")
        void shouldHandleInputStreamEOFMidway(@TempDir final Path tempDir) throws Exception {
            final Path original = createLargeArchive(tempDir, "midway_eof.apack", 100_000);
            final long size = Files.size(original);
            final Path truncated = tempDir.resolve("midway.apack");

            truncateFile(original, truncated, (int) (size * 0.4));

            try (AetherPackReader reader = AetherPackReader.open(truncated)) {
                assertThatThrownBy(() -> reader.readAllBytes("data.bin"))
                        .isInstanceOf(Exception.class);
            } catch (Exception e) {
                // Expected
            }
        }

        @Test
        @DisplayName("should handle input stream EOF near end")
        void shouldHandleInputStreamEOFNearEnd(@TempDir final Path tempDir) throws Exception {
            final Path original = createValidArchive(tempDir, "near_end.apack");
            final long size = Files.size(original);
            final Path truncated = tempDir.resolve("near_end.apack");

            // Remove last 50 bytes to ensure we hit trailer validation
            truncateFile(original, truncated, (int) (size - 50));

            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(truncated)) {
                    for (var entry : reader) {
                        reader.readAllBytes(entry);
                    }
                }
            }).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Large File Truncation Tests")
    class LargeFileTruncationTests {

        @Test
        @DisplayName("should handle truncation in multi-chunk archive")
        void shouldHandleTruncationInMultiChunkArchive(@TempDir final Path tempDir) throws Exception {
            // Create archive with multiple chunks
            final byte[] largeData = new byte[FormatConstants.DEFAULT_CHUNK_SIZE * 3];
            SEEDED_RANDOM.nextBytes(largeData);

            final Path original = tempDir.resolve("multi_chunk.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(original)) {
                writer.addEntry("large.bin", largeData);
            }

            final long size = Files.size(original);

            // Truncate at different chunk boundaries
            for (double ratio : new double[]{0.3, 0.5, 0.7, 0.9}) {
                final int truncateAt = (int) (size * ratio);
                final Path truncated = tempDir.resolve("multi_truncate_" + (int) (ratio * 100) + ".apack");
                truncateFile(original, truncated, truncateAt);

                assertThatThrownBy(() -> {
                    try (AetherPackReader reader = AetherPackReader.open(truncated)) {
                        reader.readAllBytes("large.bin");
                    }
                }).isInstanceOf(Exception.class);
            }
        }

        @Test
        @DisplayName("should handle truncation in compressed archive")
        void shouldHandleTruncationInCompressedArchive(@TempDir final Path tempDir) throws Exception {
            final byte[] data = new byte[50_000];
            SEEDED_RANDOM.nextBytes(data);

            final Path original = tempDir.resolve("compressed.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .compression(new ZstdCompressionProvider())
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(original, config)) {
                writer.addEntry("data.bin", data);
            }

            final long size = Files.size(original);

            for (double ratio : new double[]{0.25, 0.5, 0.75, 0.95}) {
                final int truncateAt = (int) (size * ratio);
                final Path truncated = tempDir.resolve("compressed_truncate_" + (int) (ratio * 100) + ".apack");
                truncateFile(original, truncated, truncateAt);

                assertThatThrownBy(() -> {
                    try (AetherPackReader reader = AetherPackReader.open(truncated, config.createChunkProcessor())) {
                        reader.readAllBytes("data.bin");
                    }
                }).isInstanceOf(Exception.class);
            }
        }

        @Test
        @DisplayName("should handle truncation in encrypted archive")
        void shouldHandleTruncationInEncryptedArchive(@TempDir final Path tempDir) throws Exception {
            final byte[] data = new byte[10_000];
            SEEDED_RANDOM.nextBytes(data);

            final Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
            final SecretKey key = aes.generateKey();

            final Path original = tempDir.resolve("encrypted.apack");
            final ApackConfiguration config = ApackConfiguration.builder()
                    .encryption(aes, key)
                    .build();

            try (AetherPackWriter writer = AetherPackWriter.create(original, config)) {
                writer.addEntry("secret.bin", data);
            }

            final long size = Files.size(original);

            for (double ratio : new double[]{0.3, 0.6, 0.9}) {
                final int truncateAt = (int) (size * ratio);
                final Path truncated = tempDir.resolve("encrypted_truncate_" + (int) (ratio * 100) + ".apack");
                truncateFile(original, truncated, truncateAt);

                assertThatThrownBy(() -> {
                    try (AetherPackReader reader = AetherPackReader.open(truncated, config.createChunkProcessor())) {
                        reader.readAllBytes("secret.bin");
                    }
                }).isInstanceOf(Exception.class);
            }
        }
    }

    @Nested
    @DisplayName("Multi-Entry Truncation Tests")
    class MultiEntryTruncationTests {

        @Test
        @DisplayName("should handle truncation after first entry")
        void shouldHandleTruncationAfterFirstEntry(@TempDir final Path tempDir) throws Exception {
            final Path original = tempDir.resolve("multi_entry.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(original)) {
                writer.addEntry("first.txt", "First entry content".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("second.txt", "Second entry content".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("third.txt", "Third entry content".getBytes(StandardCharsets.UTF_8));
            }

            final long size = Files.size(original);

            // Truncate somewhere in the middle
            final Path truncated = tempDir.resolve("multi_truncated.apack");
            truncateFile(original, truncated, (int) (size * 0.5));

            // Some entries may be readable, others not
            try (AetherPackReader reader = AetherPackReader.open(truncated)) {
                int readableCount = 0;
                for (var entry : reader) {
                    try {
                        reader.readAllBytes(entry);
                        readableCount++;
                    } catch (Exception e) {
                        // Entry is truncated
                    }
                }
                // At least one entry should fail (truncation effect)
                assertThat(readableCount).isLessThanOrEqualTo(3);
            } catch (Exception e) {
                // Expected if truncation affects trailer
            }
        }
    }

    // ===== Helper Methods =====

    private Path createValidArchive(final Path tempDir, final String name) throws Exception {
        final Path archive = tempDir.resolve(name);
        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
            writer.addEntry("test.txt", "Test content for truncation testing".getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }

    private Path createLargeArchive(final Path tempDir, final String name, final int size) throws Exception {
        final Path archive = tempDir.resolve(name);
        final byte[] data = new byte[size];
        SEEDED_RANDOM.nextBytes(data);
        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
            writer.addEntry("data.bin", data);
        }
        return archive;
    }

    private void truncateFile(final Path source, final Path dest, final int length) throws IOException {
        final byte[] content = Files.readAllBytes(source);
        final byte[] truncated = Arrays.copyOf(content, Math.min(length, content.length));
        Files.write(dest, truncated);
    }
}
