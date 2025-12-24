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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for thread safety and concurrent access.
 *
 * <p>These tests verify that the APACK library can be safely used
 * from multiple threads without data races or corruption.</p>
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Thread Safety Tests")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ThreadSafetyTest {

    private static final Random SEEDED_RANDOM = new Random(42);

    @Nested
    @DisplayName("Parallel Decoding Tests")
    class ParallelDecodingTests {

        @Test
        @DisplayName("should decode in parallel without race condition")
        void shouldDecodeInParallelWithoutRaceCondition(@TempDir final Path tempDir) throws Exception {
            // Create multiple archives
            final int archiveCount = 10;
            final List<Path> archives = new ArrayList<>();
            final byte[][] expectedContents = new byte[archiveCount][];

            for (int i = 0; i < archiveCount; i++) {
                final Path archive = tempDir.resolve("archive_" + i + ".apack");
                expectedContents[i] = ("Content for archive " + i).getBytes(StandardCharsets.UTF_8);

                try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                    writer.addEntry("data.txt", expectedContents[i]);
                }
                archives.add(archive);
            }

            // Read all archives in parallel
            final ExecutorService executor = Executors.newFixedThreadPool(archiveCount);
            final CountDownLatch latch = new CountDownLatch(archiveCount);
            final AtomicInteger successCount = new AtomicInteger(0);
            final AtomicBoolean failed = new AtomicBoolean(false);

            for (int i = 0; i < archiveCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try (AetherPackReader reader = AetherPackReader.open(archives.get(index))) {
                        byte[] content = reader.readAllBytes("data.txt");
                        if (Arrays.equals(content, expectedContents[index])) {
                            successCount.incrementAndGet();
                        } else {
                            failed.set(true);
                        }
                    } catch (Exception e) {
                        failed.set(true);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertThat(failed.get()).isFalse();
            assertThat(successCount.get()).isEqualTo(archiveCount);
        }

        @Test
        @DisplayName("should encode in parallel without race condition")
        void shouldEncodeInParallelWithoutRaceCondition(@TempDir final Path tempDir) throws Exception {
            final int writerCount = 10;
            final ExecutorService executor = Executors.newFixedThreadPool(writerCount);
            final CountDownLatch latch = new CountDownLatch(writerCount);
            final AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < writerCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        final Path archive = tempDir.resolve("parallel_write_" + index + ".apack");
                        final byte[] content = new byte[1000];
                        new Random(42 + index).nextBytes(content);

                        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                            writer.addEntry("data.bin", content);
                        }

                        // Verify
                        try (AetherPackReader reader = AetherPackReader.open(archive)) {
                            byte[] read = reader.readAllBytes("data.bin");
                            if (Arrays.equals(read, content)) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        // Failed
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(writerCount);
        }

        @Test
        @DisplayName("should handle concurrent reads from same archive")
        void shouldHandleConcurrentReadsFromSameArchive(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("shared.apack");
            final byte[] content = new byte[10_000];
            SEEDED_RANDOM.nextBytes(content);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("data.bin", content);
            }

            // Multiple readers reading same file
            final int readerCount = 20;
            final ExecutorService executor = Executors.newFixedThreadPool(readerCount);
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(readerCount);
            final AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < readerCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        try (AetherPackReader reader = AetherPackReader.open(archive)) {
                            byte[] read = reader.readAllBytes("data.bin");
                            if (Arrays.equals(read, content)) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        // Failed
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // Start all threads
            doneLatch.await();
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(readerCount);
        }
    }

    @Nested
    @DisplayName("Shared State Tests")
    class SharedStateTests {

        @Test
        @DisplayName("should not share mutable state between readers")
        void shouldNotShareMutableState(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("state.apack");

            // Create archive with multiple entries
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                for (int i = 0; i < 10; i++) {
                    writer.addEntry("entry_" + i + ".txt", ("Content " + i).getBytes(StandardCharsets.UTF_8));
                }
            }

            // Open multiple readers and iterate independently
            final int readerCount = 5;
            final ExecutorService executor = Executors.newFixedThreadPool(readerCount);
            final CountDownLatch latch = new CountDownLatch(readerCount);
            final AtomicInteger successCount = new AtomicInteger(0);

            for (int r = 0; r < readerCount; r++) {
                executor.submit(() -> {
                    try (AetherPackReader reader = AetherPackReader.open(archive)) {
                        int entryCount = 0;
                        for (var entry : reader) {
                            reader.readAllBytes(entry);
                            entryCount++;
                        }
                        if (entryCount == 10) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Failed
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(readerCount);
        }

        @Test
        @DisplayName("should isolate reader instances")
        void shouldIsolateReaderInstances(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("isolated.apack");
            final byte[] content = "Test content".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", content);
            }

            // Open two readers, close one, other should still work
            try (AetherPackReader reader1 = AetherPackReader.open(archive)) {
                try (AetherPackReader reader2 = AetherPackReader.open(archive)) {
                    assertThat(reader1.readAllBytes("test.txt")).isEqualTo(content);
                    assertThat(reader2.readAllBytes("test.txt")).isEqualTo(content);
                }
                // reader2 is closed, reader1 should still work
                assertThat(reader1.readAllBytes("test.txt")).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should isolate writer instances")
        void shouldIsolateWriterInstances(@TempDir final Path tempDir) throws Exception {
            final Path archive1 = tempDir.resolve("isolated1.apack");
            final Path archive2 = tempDir.resolve("isolated2.apack");

            // Two writers writing simultaneously
            try (AetherPackWriter writer1 = AetherPackWriter.create(archive1);
                 AetherPackWriter writer2 = AetherPackWriter.create(archive2)) {

                writer1.addEntry("test.txt", "Content 1".getBytes(StandardCharsets.UTF_8));
                writer2.addEntry("test.txt", "Content 2".getBytes(StandardCharsets.UTF_8));
            }

            // Verify each has correct content
            try (AetherPackReader reader1 = AetherPackReader.open(archive1);
                 AetherPackReader reader2 = AetherPackReader.open(archive2)) {

                assertThat(new String(reader1.readAllBytes("test.txt"), StandardCharsets.UTF_8))
                        .isEqualTo("Content 1");
                assertThat(new String(reader2.readAllBytes("test.txt"), StandardCharsets.UTF_8))
                        .isEqualTo("Content 2");
            }
        }
    }

    @Nested
    @DisplayName("Stress Tests")
    class StressTests {

        @Test
        @DisplayName("should survive 1000 parallel operations")
        void shouldSurvive1000ParallelOperations(@TempDir final Path tempDir) throws Exception {
            final int operationCount = 1000;
            final ExecutorService executor = Executors.newFixedThreadPool(50);
            final CountDownLatch latch = new CountDownLatch(operationCount);
            final AtomicInteger successCount = new AtomicInteger(0);
            final AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < operationCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        final Path archive = tempDir.resolve("stress_" + index + ".apack");
                        final byte[] content = new byte[100 + (index % 1000)];
                        new Random(42 + index).nextBytes(content);

                        // Write
                        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                            writer.addEntry("data.bin", content);
                        }

                        // Read and verify
                        try (AetherPackReader reader = AetherPackReader.open(archive)) {
                            byte[] read = reader.readAllBytes("data.bin");
                            if (Arrays.equals(read, content)) {
                                successCount.incrementAndGet();
                            } else {
                                errorCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(120, TimeUnit.SECONDS);
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            assertThat(errorCount.get()).as("Error count should be 0").isEqualTo(0);
            assertThat(successCount.get()).as("Success count").isEqualTo(operationCount);
        }

        @Test
        @DisplayName("should not deadlock under contention")
        void shouldNotDeadlock(@TempDir final Path tempDir) throws Exception {
            final int threadCount = 20;
            final int iterationsPerThread = 50;
            final Path sharedArchive = tempDir.resolve("contention.apack");

            // Create initial archive
            try (AetherPackWriter writer = AetherPackWriter.create(sharedArchive)) {
                writer.addEntry("data.txt", "Initial".getBytes(StandardCharsets.UTF_8));
            }

            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);
            final AtomicBoolean deadlockDetected = new AtomicBoolean(false);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            // Alternate between reading and writing to different files
                            if (i % 2 == 0) {
                                try (AetherPackReader reader = AetherPackReader.open(sharedArchive)) {
                                    reader.readAllBytes("data.txt");
                                }
                            } else {
                                Path threadArchive = tempDir.resolve("thread_" + threadId + "_" + i + ".apack");
                                try (AetherPackWriter writer = AetherPackWriter.create(threadArchive)) {
                                    writer.addEntry("data.txt", ("Thread " + threadId).getBytes(StandardCharsets.UTF_8));
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Error but not deadlock
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();

            // Wait with timeout to detect deadlock
            boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
            executor.shutdownNow();

            assertThat(completed).as("Should complete without deadlock").isTrue();
        }
    }

    @Nested
    @DisplayName("Memory Safety Tests")
    class MemorySafetyTests {

        @Test
        @DisplayName("should not leak memory in parallel operations")
        void shouldNotLeakMemoryInParallelOperations(@TempDir final Path tempDir) throws Exception {
            // Force GC and get baseline
            System.gc();
            Thread.sleep(100);
            final long baselineMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            final int iterations = 100;
            final ExecutorService executor = Executors.newFixedThreadPool(10);
            final CountDownLatch latch = new CountDownLatch(iterations);

            for (int i = 0; i < iterations; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        final Path archive = tempDir.resolve("memory_" + index + ".apack");
                        final byte[] content = new byte[10_000];

                        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                            writer.addEntry("data.bin", content);
                        }

                        try (AetherPackReader reader = AetherPackReader.open(archive)) {
                            reader.readAllBytes("data.bin");
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // Force GC and check memory
            System.gc();
            Thread.sleep(100);
            final long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // Memory increase should be reasonable (not leaking)
            final long memoryIncrease = finalMemory - baselineMemory;
            assertThat(memoryIncrease).isLessThan(50L * 1024 * 1024); // Less than 50MB increase
        }
    }
}
