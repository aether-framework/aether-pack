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

package de.splatgames.aether.pack.compression;

import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ZstdCompressionProvider")
class ZstdCompressionProviderTest {

    private ZstdCompressionProvider provider;

    @BeforeEach
    void setUp() {
        this.provider = new ZstdCompressionProvider();
    }

    @Nested
    @DisplayName("metadata")
    class MetadataTests {

        @Test
        @DisplayName("should return correct ID")
        void shouldReturnCorrectId() {
            assertThat(provider.getId()).isEqualTo("zstd");
        }

        @Test
        @DisplayName("should return correct numeric ID")
        void shouldReturnCorrectNumericId() {
            assertThat(provider.getNumericId()).isEqualTo(FormatConstants.COMPRESSION_ZSTD);
        }

        @Test
        @DisplayName("should have valid compression level range")
        void shouldHaveValidLevelRange() {
            assertThat(provider.getMinLevel()).isEqualTo(1);
            assertThat(provider.getMaxLevel()).isEqualTo(22);
            assertThat(provider.getDefaultLevel()).isEqualTo(3);
            assertThat(provider.getDefaultLevel()).isBetween(provider.getMinLevel(), provider.getMaxLevel());
        }

        @Test
        @DisplayName("should support all levels in range")
        void shouldSupportAllLevelsInRange() {
            for (int level = provider.getMinLevel(); level <= provider.getMaxLevel(); level++) {
                assertThat(provider.supportsLevel(level)).isTrue();
            }
        }

        @Test
        @DisplayName("should not support levels outside range")
        void shouldNotSupportLevelsOutsideRange() {
            assertThat(provider.supportsLevel(provider.getMinLevel() - 1)).isFalse();
            assertThat(provider.supportsLevel(provider.getMaxLevel() + 1)).isFalse();
        }

    }

    @Nested
    @DisplayName("compressBlock/decompressBlock")
    class BlockCompressionTests {

        @Test
        @DisplayName("should round-trip data correctly")
        void shouldRoundTripData() throws IOException {
            final byte[] original = "Hello, World! This is a test of ZSTD compression.".getBytes(StandardCharsets.UTF_8);

            final byte[] compressed = provider.compressBlock(original, provider.getDefaultLevel());
            final byte[] decompressed = provider.decompressBlock(compressed, original.length);

            assertThat(decompressed).isEqualTo(original);
        }

        @Test
        @DisplayName("should compress repetitive data effectively")
        void shouldCompressRepetitiveData() throws IOException {
            final byte[] original = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".repeat(100)
                    .getBytes(StandardCharsets.UTF_8);

            final byte[] compressed = provider.compressBlock(original, provider.getDefaultLevel());

            assertThat(compressed.length).isLessThan(original.length);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 3, 9, 19, 22})
        @DisplayName("should work at different compression levels")
        void shouldWorkAtDifferentLevels(final int level) throws IOException {
            final byte[] original = randomBytes(10000);

            final byte[] compressed = provider.compressBlock(original, level);
            final byte[] decompressed = provider.decompressBlock(compressed, original.length);

            assertThat(decompressed).isEqualTo(original);
        }

        @Test
        @DisplayName("should handle empty data")
        void shouldHandleEmptyData() throws IOException {
            final byte[] original = new byte[0];

            final byte[] compressed = provider.compressBlock(original, provider.getDefaultLevel());
            final byte[] decompressed = provider.decompressBlock(compressed, 0);

            assertThat(decompressed).isEmpty();
        }

        @Test
        @DisplayName("should handle large data")
        void shouldHandleLargeData() throws IOException {
            final byte[] original = randomBytes(1024 * 1024); // 1 MB

            final byte[] compressed = provider.compressBlock(original, provider.getDefaultLevel());
            final byte[] decompressed = provider.decompressBlock(compressed, original.length);

            assertThat(decompressed).isEqualTo(original);
        }

    }

    @Nested
    @DisplayName("compress/decompress streams")
    class StreamCompressionTests {

        @Test
        @DisplayName("should round-trip data through streams")
        void shouldRoundTripThroughStreams() throws IOException {
            final byte[] original = "Stream compression test data. ".repeat(1000).getBytes(StandardCharsets.UTF_8);

            // Compress
            final ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
            try (OutputStream zstd = provider.compress(compressedOut, provider.getDefaultLevel())) {
                zstd.write(original);
            }
            final byte[] compressed = compressedOut.toByteArray();

            // Decompress
            final ByteArrayOutputStream decompressedOut = new ByteArrayOutputStream();
            try (InputStream zstd = provider.decompress(new ByteArrayInputStream(compressed))) {
                zstd.transferTo(decompressedOut);
            }

            assertThat(decompressedOut.toByteArray()).isEqualTo(original);
        }

    }

    @Nested
    @DisplayName("maxCompressedSize")
    class MaxCompressedSizeTests {

        @Test
        @DisplayName("should return size larger than input")
        void shouldReturnSizeLargerThanInput() {
            final int inputSize = 10000;
            final int maxSize = provider.maxCompressedSize(inputSize);

            assertThat(maxSize).isGreaterThan(inputSize);
        }

        @Test
        @DisplayName("should be sufficient for incompressible data")
        void shouldBeSufficientForIncompressibleData() throws IOException {
            final byte[] original = randomBytes(10000);
            final int maxSize = provider.maxCompressedSize(original.length);

            final byte[] compressed = provider.compressBlock(original, provider.getDefaultLevel());

            assertThat(compressed.length).isLessThanOrEqualTo(maxSize);
        }

    }

    private static byte[] randomBytes(final int length) {
        final byte[] bytes = new byte[length];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

}
