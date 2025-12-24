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

package de.splatgames.aether.pack.core.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChunkProcessor")
class ChunkProcessorTest {

    private static final Random RANDOM = new Random(42);

    @Nested
    @DisplayName("passThrough()")
    class PassThroughTests {

        @Test
        @DisplayName("should return unmodified data for write")
        void shouldReturnUnmodifiedDataForWrite() throws IOException {
            final ChunkProcessor processor = ChunkProcessor.passThrough();
            final byte[] data = randomBytes(1024);

            final ChunkProcessor.ProcessedChunk result = processor.processForWrite(data, data.length);

            assertThat(result.data()).isEqualTo(data);
            assertThat(result.originalSize()).isEqualTo(1024);
            assertThat(result.storedSize()).isEqualTo(1024);
            assertThat(result.compressed()).isFalse();
            assertThat(result.encrypted()).isFalse();
        }

        @Test
        @DisplayName("should return unmodified data for read")
        void shouldReturnUnmodifiedDataForRead() throws IOException {
            final ChunkProcessor processor = ChunkProcessor.passThrough();
            final byte[] data = randomBytes(1024);

            final byte[] result = processor.processForRead(data, data.length, false, false);

            assertThat(result).isEqualTo(data);
        }

        @Test
        @DisplayName("should report compression disabled")
        void shouldReportCompressionDisabled() {
            final ChunkProcessor processor = ChunkProcessor.passThrough();

            assertThat(processor.isCompressionEnabled()).isFalse();
            assertThat(processor.getCompressionProvider()).isNull();
        }

        @Test
        @DisplayName("should report encryption disabled")
        void shouldReportEncryptionDisabled() {
            final ChunkProcessor processor = ChunkProcessor.passThrough();

            assertThat(processor.isEncryptionEnabled()).isFalse();
            assertThat(processor.getEncryptionProvider()).isNull();
        }

    }

    @Nested
    @DisplayName("builder()")
    class BuilderTests {

        @Test
        @DisplayName("should create processor with no options")
        void shouldCreateProcessorWithNoOptions() {
            final ChunkProcessor processor = ChunkProcessor.builder().build();

            assertThat(processor.isCompressionEnabled()).isFalse();
            assertThat(processor.isEncryptionEnabled()).isFalse();
        }

    }

    @Nested
    @DisplayName("processForWrite() with partial buffer")
    class PartialBufferTests {

        @Test
        @DisplayName("should handle partial buffer correctly")
        void shouldHandlePartialBuffer() throws IOException {
            final ChunkProcessor processor = ChunkProcessor.passThrough();
            final byte[] buffer = new byte[1024];
            // Only use first 512 bytes
            final byte[] expected = new byte[512];
            System.arraycopy(randomBytes(512), 0, buffer, 0, 512);
            System.arraycopy(buffer, 0, expected, 0, 512);

            final ChunkProcessor.ProcessedChunk result = processor.processForWrite(buffer, 512);

            assertThat(result.originalSize()).isEqualTo(512);
            assertThat(result.storedSize()).isEqualTo(512);
            assertThat(result.data()).hasSize(512);
        }

    }

    private static byte[] randomBytes(final int length) {
        final byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

}
