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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for savegame stability scenarios.
 *
 * <p>These tests verify behavior in scenarios common to game savegames:</p>
 * <ul>
 *   <li>Atomic write operations</li>
 *   <li>Crash recovery</li>
 *   <li>Concurrent access</li>
 * </ul>
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Savegame Stability Tests")
class SavegameStabilityTest {

    private static final Random SEEDED_RANDOM = new Random(42);

    @Nested
    @DisplayName("Atomic Write Tests")
    class AtomicWriteTests {

        @Test
        @DisplayName("should support write-to-temp-then-rename pattern")
        void shouldSupportWriteToTempThenRename(@TempDir final Path tempDir) throws Exception {
            final Path target = tempDir.resolve("savegame.apack");
            final Path temp = tempDir.resolve("savegame.apack.tmp");
            final byte[] content = "Game state data".getBytes(StandardCharsets.UTF_8);

            // Write to temp file
            try (AetherPackWriter writer = AetherPackWriter.create(temp)) {
                writer.addEntry("state.bin", content);
            }

            // Atomic rename
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            // Verify result
            try (AetherPackReader reader = AetherPackReader.open(target)) {
                assertThat(reader.readAllBytes("state.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should not corrupt existing file on write failure simulation")
        void shouldNotCorruptOnWriteFailure(@TempDir final Path tempDir) throws Exception {
            final Path target = tempDir.resolve("savegame.apack");
            final byte[] originalContent = "Original game state".getBytes(StandardCharsets.UTF_8);

            // Create original savegame
            try (AetherPackWriter writer = AetherPackWriter.create(target)) {
                writer.addEntry("state.bin", originalContent);
            }

            final long originalSize = Files.size(target);

            // Simulate a failed write by writing to temp and not renaming
            final Path temp = tempDir.resolve("savegame.apack.tmp");
            try (AetherPackWriter writer = AetherPackWriter.create(temp)) {
                writer.addEntry("state.bin", "New but failed state".getBytes(StandardCharsets.UTF_8));
            }
            // Don't rename - simulating crash after write

            // Original should be intact
            assertThat(Files.size(target)).isEqualTo(originalSize);
            try (AetherPackReader reader = AetherPackReader.open(target)) {
                assertThat(reader.readAllBytes("state.bin")).isEqualTo(originalContent);
            }
        }
    }

    @Nested
    @DisplayName("Crash Scenario Tests")
    class CrashScenarioTests {

        @Test
        @DisplayName("should handle only temp file exists scenario")
        void shouldHandleOnlyTempFileExists(@TempDir final Path tempDir) throws Exception {
            final Path target = tempDir.resolve("savegame.apack");
            final Path temp = tempDir.resolve("savegame.apack.tmp");
            final byte[] content = "Temp content".getBytes(StandardCharsets.UTF_8);

            // Only temp exists (simulating crash during write before rename)
            try (AetherPackWriter writer = AetherPackWriter.create(temp)) {
                writer.addEntry("state.bin", content);
            }

            // Recovery logic would check for temp file
            assertThat(Files.exists(target)).isFalse();
            assertThat(Files.exists(temp)).isTrue();

            // Application can recover by using temp or deleting it
            try (AetherPackReader reader = AetherPackReader.open(temp)) {
                assertThat(reader.readAllBytes("state.bin")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should detect half-written target file")
        void shouldDetectHalfWrittenTargetFile(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("half_written.apack");
            final byte[] content = new byte[50_000];
            SEEDED_RANDOM.nextBytes(content);

            // Create valid archive
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            // Simulate crash by truncating
            final long originalSize = Files.size(archive);
            try (RandomAccessFile raf = new RandomAccessFile(archive.toFile(), "rw")) {
                raf.setLength(originalSize / 2);
            }

            // Should detect corruption
            assertThatThrownBy(() -> {
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("data.bin");
                }
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should provide recovery guidance through exception message")
        void shouldProvideRecoveryGuidance(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("corrupted.apack");

            // Create truncated/corrupted file
            Files.write(archive, new byte[]{0x41, 0x50, 0x41, 0x43, 0x4B, 0x00}); // Just magic

            assertThatThrownBy(() -> AetherPackReader.open(archive))
                    .isInstanceOf(Exception.class)
                    .satisfies(e -> {
                        assertThat(e.getMessage()).isNotNull();
                        assertThat(e.getMessage()).isNotEmpty();
                    });
        }
    }

    @Nested
    @DisplayName("Concurrent Access Tests")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("should handle read while another instance writes to different file")
        void shouldHandleReadWhileWritingDifferentFile(@TempDir final Path tempDir) throws Exception {
            final Path readArchive = tempDir.resolve("read.apack");
            final Path writeArchive = tempDir.resolve("write.apack");
            final byte[] readContent = "Read content".getBytes(StandardCharsets.UTF_8);
            final byte[] writeContent = "Write content".getBytes(StandardCharsets.UTF_8);

            // Create read archive
            try (AetherPackWriter writer = AetherPackWriter.create(readArchive)) {
                writer.addEntry("data.bin", readContent);
            }

            // Concurrent read and write to different files
            try (AetherPackReader reader = AetherPackReader.open(readArchive);
                 AetherPackWriter writer = AetherPackWriter.create(writeArchive)) {

                assertThat(reader.readAllBytes("data.bin")).isEqualTo(readContent);
                writer.addEntry("data.bin", writeContent);
            }

            // Both operations succeeded
            try (AetherPackReader reader = AetherPackReader.open(writeArchive)) {
                assertThat(reader.readAllBytes("data.bin")).isEqualTo(writeContent);
            }
        }

        @Test
        @DisplayName("should handle multiple concurrent readers")
        void shouldHandleMultipleReaders(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("multi_read.apack");
            final byte[] content = new byte[10_000];
            SEEDED_RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            final int readerCount = 10;
            final ExecutorService executor = Executors.newFixedThreadPool(readerCount);
            final CountDownLatch latch = new CountDownLatch(readerCount);
            final AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < readerCount; i++) {
                executor.submit(() -> {
                    try (AetherPackReader reader = AetherPackReader.open(archive)) {
                        byte[] read = reader.readAllBytes("data.bin");
                        if (java.util.Arrays.equals(read, content)) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Failed
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(readerCount);
        }
    }

    @Nested
    @DisplayName("Long-Term Stability Tests")
    class LongTermStabilityTests {

        @Test
        @DisplayName("should read archive with all supported features")
        void shouldReadFullFeaturedArchive(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("full_featured.apack");

            // Create archive with various content types
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                // Text content
                writer.addEntry("save/player.json", "{\"name\":\"Hero\",\"level\":50}".getBytes(StandardCharsets.UTF_8));

                // Binary content
                byte[] binary = new byte[1000];
                SEEDED_RANDOM.nextBytes(binary);
                writer.addEntry("save/world.dat", binary);

                // Empty file
                writer.addEntry("save/markers.txt", new byte[0]);

                // Unicode name
                writer.addEntry("save/\u30C7\u30FC\u30BF.bin", "Japanese data".getBytes(StandardCharsets.UTF_8));
            }

            // Verify all entries readable
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(new String(reader.readAllBytes("save/player.json"), StandardCharsets.UTF_8))
                        .contains("Hero");
                assertThat(reader.readAllBytes("save/world.dat")).hasSize(1000);
                assertThat(reader.readAllBytes("save/markers.txt")).isEmpty();
                assertThat(reader.readAllBytes("save/\u30C7\u30FC\u30BF.bin")).isNotNull();
            }
        }

        @Test
        @DisplayName("should handle repeated save/load cycles")
        void shouldHandleRepeatedSaveLoadCycles(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("repeated.apack");
            byte[] content = "Initial state".getBytes(StandardCharsets.UTF_8);

            for (int cycle = 0; cycle < 100; cycle++) {
                // Save
                try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                    writer.addEntry("state.bin", content);
                }

                // Load and modify
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    byte[] loaded = reader.readAllBytes("state.bin");
                    assertThat(loaded).isEqualTo(content);

                    // Prepare next state
                    content = ("State after cycle " + (cycle + 1)).getBytes(StandardCharsets.UTF_8);
                }
            }
        }
    }

    @Nested
    @DisplayName("Resource Cleanup Tests")
    class ResourceCleanupTests {

        @Test
        @DisplayName("should release file handles on close")
        void shouldReleaseFileHandlesOnClose(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("handles.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            // Open and close reader
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                reader.readAllBytes("test.txt");
            }

            // File should be deletable (handles released)
            assertThat(Files.deleteIfExists(archive)).isTrue();
        }

        @Test
        @DisplayName("should release handles even on exception")
        void shouldReleaseHandlesOnException(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("exception.apack");

            // Create invalid archive
            Files.write(archive, new byte[100]);

            try {
                AetherPackReader.open(archive).close();
            } catch (Exception e) {
                // Expected
            }

            // File should be deletable
            assertThat(Files.deleteIfExists(archive)).isTrue();
        }
    }
}
