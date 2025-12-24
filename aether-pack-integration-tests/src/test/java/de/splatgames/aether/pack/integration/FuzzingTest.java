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
import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Fuzzing tests to ensure parser robustness against random/malicious input.
 *
 * <p>These tests verify that the APACK parser:</p>
 * <ul>
 *   <li>Never crashes on any input</li>
 *   <li>Never enters an infinite loop</li>
 *   <li>Never allocates unbounded memory</li>
 *   <li>Always produces a clean error (exception) for invalid input</li>
 * </ul>
 *
 * <p>All tests use seeded randomness for reproducibility. If a test fails,
 * record the seed value for debugging.</p>
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Fuzzing Tests")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class FuzzingTest {

    private static final long BASE_SEED = 42L;

    @Nested
    @DisplayName("Random Bytes Fuzzing Tests")
    class RandomBytesFuzzingTests {

        @Test
        @DisplayName("should not crash on empty input (0 bytes)")
        void shouldNotCrashOnRandomBytes_Length0(@TempDir final Path tempDir) throws Exception {
            final Path file = tempDir.resolve("empty.apack");
            Files.write(file, new byte[0]);

            assertThatThrownBy(() -> AetherPackReader.open(file))
                    .isInstanceOf(Exception.class);
        }

        @RepeatedTest(value = 10, name = "iteration {currentRepetition}")
        @DisplayName("should not crash on random bytes length 1-7")
        void shouldNotCrashOnRandomBytes_Length1to7(final RepetitionInfo info, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + info.getCurrentRepetition());
            final int length = 1 + random.nextInt(7);
            final byte[] data = new byte[length];
            random.nextBytes(data);

            final Path file = tempDir.resolve("random_" + length + ".apack");
            Files.write(file, data);

            assertThatThrownBy(() -> AetherPackReader.open(file))
                    .isInstanceOf(Exception.class);
        }

        @RepeatedTest(value = 10, name = "iteration {currentRepetition}")
        @DisplayName("should not crash on random bytes length 8-63")
        void shouldNotCrashOnRandomBytes_Length8to63(final RepetitionInfo info, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + info.getCurrentRepetition() + 100);
            final int length = 8 + random.nextInt(56);
            final byte[] data = new byte[length];
            random.nextBytes(data);

            final Path file = tempDir.resolve("random_" + length + ".apack");
            Files.write(file, data);

            assertThatCode(() -> {
                try {
                    AetherPackReader.open(file).close();
                } catch (Exception e) {
                    // Expected - invalid format
                }
            }).doesNotThrowAnyException();
        }

        @RepeatedTest(value = 10, name = "iteration {currentRepetition}")
        @DisplayName("should not crash on random bytes at header size (64)")
        void shouldNotCrashOnRandomBytes_Length64(final RepetitionInfo info, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + info.getCurrentRepetition() + 200);
            final byte[] data = new byte[FormatConstants.FILE_HEADER_SIZE];
            random.nextBytes(data);

            final Path file = tempDir.resolve("random_64.apack");
            Files.write(file, data);

            assertThatCode(() -> {
                try {
                    AetherPackReader.open(file).close();
                } catch (Exception e) {
                    // Expected
                }
            }).doesNotThrowAnyException();
        }

        @RepeatedTest(value = 10, name = "iteration {currentRepetition}")
        @DisplayName("should not crash on random bytes length 65-255")
        void shouldNotCrashOnRandomBytes_Length65to255(final RepetitionInfo info, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + info.getCurrentRepetition() + 300);
            final int length = 65 + random.nextInt(191);
            final byte[] data = new byte[length];
            random.nextBytes(data);

            final Path file = tempDir.resolve("random_" + length + ".apack");
            Files.write(file, data);

            assertThatCode(() -> {
                try {
                    AetherPackReader.open(file).close();
                } catch (Exception e) {
                    // Expected
                }
            }).doesNotThrowAnyException();
        }

        @RepeatedTest(value = 5, name = "iteration {currentRepetition}")
        @DisplayName("should not crash on random bytes length 256-1023")
        void shouldNotCrashOnRandomBytes_Length256to1023(final RepetitionInfo info, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + info.getCurrentRepetition() + 400);
            final int length = 256 + random.nextInt(768);
            final byte[] data = new byte[length];
            random.nextBytes(data);

            final Path file = tempDir.resolve("random_" + length + ".apack");
            Files.write(file, data);

            assertThatCode(() -> {
                try {
                    AetherPackReader.open(file).close();
                } catch (Exception e) {
                    // Expected
                }
            }).doesNotThrowAnyException();
        }

        @RepeatedTest(value = 5, name = "iteration {currentRepetition}")
        @DisplayName("should not crash on random bytes length 1024-65535")
        void shouldNotCrashOnRandomBytes_Length1024to65535(final RepetitionInfo info, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + info.getCurrentRepetition() + 500);
            final int length = 1024 + random.nextInt(64512);
            final byte[] data = new byte[length];
            random.nextBytes(data);

            final Path file = tempDir.resolve("random_" + length + ".apack");
            Files.write(file, data);

            assertThatCode(() -> {
                try {
                    AetherPackReader.open(file).close();
                } catch (Exception e) {
                    // Expected
                }
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not crash on random bytes length 65536+")
        void shouldNotCrashOnRandomBytes_Length65536Plus(@TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + 600);
            final byte[] data = new byte[100_000];
            random.nextBytes(data);

            final Path file = tempDir.resolve("random_100000.apack");
            Files.write(file, data);

            assertThatCode(() -> {
                try {
                    AetherPackReader.open(file).close();
                } catch (Exception e) {
                    // Expected
                }
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Structured Fuzzing Tests")
    class StructuredFuzzingTests {

        @RepeatedTest(value = 10, name = "iteration {currentRepetition}")
        @DisplayName("should not crash on valid magic plus random data")
        void shouldNotCrashOnValidMagicPlusRandomData(final RepetitionInfo info, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + info.getCurrentRepetition() + 700);

            // Start with valid magic "APACK\0"
            final int length = 100 + random.nextInt(500);
            final byte[] data = new byte[length];
            random.nextBytes(data);
            System.arraycopy(FormatConstants.MAGIC, 0, data, 0, FormatConstants.MAGIC.length);
            data[5] = 0; // Null terminator

            final Path file = tempDir.resolve("magic_random.apack");
            Files.write(file, data);

            assertThatCode(() -> {
                try {
                    AetherPackReader.open(file).close();
                } catch (Exception e) {
                    // Expected
                }
            }).doesNotThrowAnyException();
        }

        @RepeatedTest(value = 10, name = "iteration {currentRepetition}")
        @DisplayName("should not crash on partial valid header plus random data")
        void shouldNotCrashOnPartialHeaderPlusRandomData(final RepetitionInfo info, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + info.getCurrentRepetition() + 800);

            final byte[] data = new byte[200];
            random.nextBytes(data);

            // Valid magic
            System.arraycopy(FormatConstants.MAGIC, 0, data, 0, 5);
            data[5] = 0;

            // Valid version
            data[6] = (byte) FormatConstants.FORMAT_VERSION_MAJOR;
            data[7] = 0;
            data[8] = (byte) FormatConstants.FORMAT_VERSION_MINOR;
            data[9] = 0;

            final Path file = tempDir.resolve("partial_header.apack");
            Files.write(file, data);

            assertThatCode(() -> {
                try {
                    AetherPackReader.open(file).close();
                } catch (Exception e) {
                    // Expected
                }
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not crash on full valid header plus random chunks")
        void shouldNotCrashOnFullHeaderPlusRandomChunks(@TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + 900);

            final ByteBuffer buffer = ByteBuffer.allocate(500).order(ByteOrder.LITTLE_ENDIAN);

            // File Header (64 bytes)
            buffer.put(FormatConstants.MAGIC);
            buffer.put((byte) 0); // Null
            buffer.putShort((short) FormatConstants.FORMAT_VERSION_MAJOR);
            buffer.putShort((short) FormatConstants.FORMAT_VERSION_MINOR);
            buffer.put((byte) 0); // Flags
            buffer.put((byte) 0); // Reserved
            buffer.putInt(FormatConstants.DEFAULT_CHUNK_SIZE);
            buffer.putInt(1); // Entry count
            buffer.putLong(200); // Trailer offset (pointing into random data)

            // Pad to 64 bytes
            while (buffer.position() < 64) {
                buffer.put((byte) 0);
            }

            // Random data after header
            final byte[] randomData = new byte[500 - 64];
            random.nextBytes(randomData);
            buffer.put(randomData);

            final Path file = tempDir.resolve("header_random.apack");
            Files.write(file, buffer.array());

            assertThatCode(() -> {
                try {
                    AetherPackReader.open(file).close();
                } catch (Exception e) {
                    // Expected
                }
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Timeout Guard Tests")
    class TimeoutGuardTests {

        @RepeatedTest(value = 100, name = "iteration {currentRepetition}")
        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        @DisplayName("should complete within 1 second on random input")
        void shouldCompleteWithin1SecondOnRandomInput(final RepetitionInfo info, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED + info.getCurrentRepetition() + 1000);
            final int length = random.nextInt(10_000);
            final byte[] data = new byte[length];
            random.nextBytes(data);

            final Path file = tempDir.resolve("timeout_test_" + info.getCurrentRepetition() + ".apack");
            Files.write(file, data);

            try {
                try (AetherPackReader reader = AetherPackReader.open(file)) {
                    for (var entry : reader) {
                        reader.readAllBytes(entry);
                    }
                }
            } catch (Exception e) {
                // Expected for random data
            }
            // Test passes if it completes within timeout
        }

        @Test
        @Timeout(value = 2, unit = TimeUnit.SECONDS)
        @DisplayName("should not hang on malicious length fields")
        void shouldNotHangOnMaliciousLengthFields(@TempDir final Path tempDir) throws Exception {
            final ByteBuffer buffer = ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN);

            // Valid file header - must match actual format layout!
            // Offset 0-5: magic + null
            buffer.put(FormatConstants.MAGIC);
            buffer.put((byte) 0);
            // Offset 6-7: version major
            buffer.putShort((short) 1);
            // Offset 8-9: version minor
            buffer.putShort((short) 0);
            // Offset 10-11: version patch
            buffer.putShort((short) 0);
            // Offset 12-13: compat level
            buffer.putShort((short) 0);
            // Offset 14: mode flags (FLAG_RANDOM_ACCESS = 0x08)
            buffer.put((byte) FormatConstants.FLAG_RANDOM_ACCESS);
            // Offset 15: checksum algorithm
            buffer.put((byte) 0);
            // Offset 16-19: chunk size
            buffer.putInt(FormatConstants.DEFAULT_CHUNK_SIZE);
            // Offset 20-23: header checksum
            buffer.putInt(0);
            // Offset 24-31: entry count - MALICIOUS: exceeds 1 million limit
            buffer.putLong(2_000_000L);
            // Offset 32-39: trailer offset
            buffer.putLong(64);
            // Offset 40-47: creation timestamp
            buffer.putLong(0);
            // Offset 48-63: reserved (16 bytes)
            while (buffer.position() < 64) {
                buffer.put((byte) 0);
            }

            // Entry header with malicious name length (won't even be reached due to entry count validation)
            buffer.put(FormatConstants.ENTRY_MAGIC);
            buffer.put((byte) 0); // Flags
            buffer.put((byte) 0); // Checksum algorithm
            buffer.put((byte) 0); // Compression algorithm
            buffer.put((byte) 0); // Encryption algorithm
            buffer.putLong(0); // Entry ID
            buffer.putLong(Long.MAX_VALUE); // Original size - malicious!
            buffer.putLong(Long.MAX_VALUE); // Stored size - malicious!
            buffer.putInt(0); // First chunk index
            buffer.putInt(1); // Chunk count
            buffer.putShort((short) 0xFFFF); // Name length - max!
            buffer.putShort((short) 0); // Mime type length

            final Path file = tempDir.resolve("malicious_lengths.apack");
            Files.write(file, buffer.array());

            // Should fail fast, not try to allocate based on malicious values
            assertThatThrownBy(() -> AetherPackReader.open(file))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @Timeout(value = 2, unit = TimeUnit.SECONDS)
        @DisplayName("should not allocate unbounded memory")
        void shouldNotAllocateUnboundedMemory(@TempDir final Path tempDir) throws Exception {
            final long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // Create file claiming huge sizes
            final ByteBuffer buffer = ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(FormatConstants.MAGIC);
            buffer.put((byte) 0);
            buffer.putShort((short) 1);
            buffer.putShort((short) 0);
            buffer.putShort((short) 0);
            buffer.putInt(Integer.MAX_VALUE); // Huge chunk size
            buffer.putInt(Integer.MAX_VALUE); // Huge entry count

            while (buffer.position() < 200) {
                buffer.put((byte) 0);
            }

            final Path file = tempDir.resolve("huge_claims.apack");
            Files.write(file, buffer.array());

            try {
                AetherPackReader.open(file).close();
            } catch (Exception e) {
                // Expected
            }

            System.gc();
            final long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // Should not have allocated gigabytes
            assertThat(finalMemory - initialMemory).isLessThan(100L * 1024L * 1024L);
        }
    }

    @Nested
    @DisplayName("Fuzzing Iterations Tests")
    class FuzzingIterationsTests {

        @ParameterizedTest(name = "seed {0}")
        @ValueSource(longs = {1, 42, 123, 456, 789, 1000, 2000, 3000, 4000, 5000})
        @DisplayName("should survive with different random seeds")
        void shouldSurviveWithDifferentSeeds(final long seed, @TempDir final Path tempDir) throws Exception {
            final Random random = new Random(seed);

            for (int i = 0; i < 100; i++) {
                final int length = random.nextInt(1000);
                final byte[] data = new byte[length];
                random.nextBytes(data);

                final Path file = tempDir.resolve("fuzz_" + seed + "_" + i + ".apack");
                Files.write(file, data);

                try {
                    try (AetherPackReader reader = AetherPackReader.open(file)) {
                        for (var entry : reader) {
                            reader.readAllBytes(entry);
                        }
                    }
                } catch (Exception e) {
                    // Expected for random data - record seed for debugging if unexpected
                }
            }
        }

        @Test
        @DisplayName("should survive 1000 random inputs")
        void shouldSurvive1000RandomInputs(@TempDir final Path tempDir) throws Exception {
            final Random random = new Random(BASE_SEED);
            int successCount = 0;
            int errorCount = 0;

            for (int i = 0; i < 1000; i++) {
                final int length = random.nextInt(5000);
                final byte[] data = new byte[length];
                random.nextBytes(data);

                final Path file = tempDir.resolve("mass_fuzz_" + i + ".apack");
                Files.write(file, data);

                try {
                    try (AetherPackReader reader = AetherPackReader.open(file)) {
                        for (var entry : reader) {
                            reader.readAllBytes(entry);
                        }
                    }
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                }

                // Clean up to avoid filling temp directory
                Files.deleteIfExists(file);
            }

            // Almost all random inputs should fail (very unlikely to be valid)
            assertThat(errorCount).isGreaterThan(990);
        }
    }

    @Nested
    @DisplayName("Edge Case Fuzzing Tests")
    class EdgeCaseFuzzingTests {

        @Test
        @DisplayName("should handle file with only magic bytes")
        void shouldHandleFileWithOnlyMagic(@TempDir final Path tempDir) throws Exception {
            final Path file = tempDir.resolve("only_magic.apack");
            Files.write(file, FormatConstants.MAGIC);

            assertThatThrownBy(() -> AetherPackReader.open(file))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle file with repeated magic bytes")
        void shouldHandleFileWithRepeatedMagic(@TempDir final Path tempDir) throws Exception {
            final byte[] data = new byte[500];
            for (int i = 0; i < data.length - 5; i += 5) {
                System.arraycopy(FormatConstants.MAGIC, 0, data, i, 5);
            }

            final Path file = tempDir.resolve("repeated_magic.apack");
            Files.write(file, data);

            assertThatCode(() -> {
                try {
                    AetherPackReader.open(file).close();
                } catch (Exception e) {
                    // Expected
                }
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle file with all zeros")
        void shouldHandleFileWithAllZeros(@TempDir final Path tempDir) throws Exception {
            final byte[] data = new byte[1000];

            final Path file = tempDir.resolve("all_zeros.apack");
            Files.write(file, data);

            assertThatThrownBy(() -> AetherPackReader.open(file))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle file with all 0xFF bytes")
        void shouldHandleFileWithAllFF(@TempDir final Path tempDir) throws Exception {
            final byte[] data = new byte[1000];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) 0xFF;
            }

            final Path file = tempDir.resolve("all_ff.apack");
            Files.write(file, data);

            assertThatThrownBy(() -> AetherPackReader.open(file))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle file with alternating bytes")
        void shouldHandleFileWithAlternatingBytes(@TempDir final Path tempDir) throws Exception {
            final byte[] data = new byte[1000];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (i % 2 == 0 ? 0x00 : 0xFF);
            }

            final Path file = tempDir.resolve("alternating.apack");
            Files.write(file, data);

            assertThatThrownBy(() -> AetherPackReader.open(file))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle file with ascending byte values")
        void shouldHandleFileWithAscendingBytes(@TempDir final Path tempDir) throws Exception {
            final byte[] data = new byte[256];
            for (int i = 0; i < 256; i++) {
                data[i] = (byte) i;
            }

            final Path file = tempDir.resolve("ascending.apack");
            Files.write(file, data);

            assertThatThrownBy(() -> AetherPackReader.open(file))
                    .isInstanceOf(Exception.class);
        }
    }
}
