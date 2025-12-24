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

import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BinaryWriter}.
 */
@DisplayName("BinaryWriter")
class BinaryWriterTest {

    // ==================== Write Byte Tests ====================

    @Nested
    @DisplayName("writeByte()")
    class WriteByteTests {

        @Test
        @DisplayName("should write single byte")
        void shouldWriteSingleByte() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeByte(0x42);
            }
            assertThat(out.toByteArray()).containsExactly(0x42);
        }

        @Test
        @DisplayName("should truncate to lower 8 bits")
        void shouldTruncateToLowerEightBits() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeByte(0x1FF); // 511, should become 0xFF
            }
            assertThat(out.toByteArray()).containsExactly((byte) 0xFF);
        }

        @Test
        @DisplayName("should increment bytes written counter")
        void shouldIncrementBytesWrittenCounter() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                assertThat(writer.getBytesWritten()).isZero();
                writer.writeByte(0x01);
                assertThat(writer.getBytesWritten()).isEqualTo(1);
                writer.writeByte(0x02);
                assertThat(writer.getBytesWritten()).isEqualTo(2);
            }
        }
    }

    // ==================== Write Primitives Tests ====================

    @Nested
    @DisplayName("writeUInt16()")
    class WriteUInt16Tests {

        @Test
        @DisplayName("should write little-endian uint16")
        void shouldWriteLittleEndianUInt16() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeUInt16(0x1234);
            }
            // Little-endian: low byte first
            assertThat(out.toByteArray()).containsExactly(0x34, 0x12);
        }

        @Test
        @DisplayName("should handle max value 65535")
        void shouldHandleMaxValue() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeUInt16(65535);
            }
            assertThat(out.toByteArray()).containsExactly((byte) 0xFF, (byte) 0xFF);
        }
    }

    @Nested
    @DisplayName("writeInt32()")
    class WriteInt32Tests {

        @Test
        @DisplayName("should write little-endian int32")
        void shouldWriteLittleEndianInt32() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeInt32(0x12345678);
            }
            // Little-endian: 0x78 0x56 0x34 0x12
            assertThat(out.toByteArray()).containsExactly(0x78, 0x56, 0x34, 0x12);
        }

        @Test
        @DisplayName("should handle negative values")
        void shouldHandleNegativeValues() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeInt32(-1);
            }
            assertThat(out.toByteArray()).containsExactly(
                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
        }

        @Test
        @DisplayName("should handle min and max values")
        void shouldHandleMinAndMaxValues() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeInt32(Integer.MAX_VALUE);
                writer.writeInt32(Integer.MIN_VALUE);
            }

            final ByteBuffer expected = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            expected.putInt(Integer.MAX_VALUE);
            expected.putInt(Integer.MIN_VALUE);

            assertThat(out.toByteArray()).isEqualTo(expected.array());
        }
    }

    @Nested
    @DisplayName("writeUInt32()")
    class WriteUInt32Tests {

        @Test
        @DisplayName("should write large unsigned values")
        void shouldWriteLargeUnsignedValues() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeUInt32(4294967295L); // 0xFFFFFFFF
            }
            assertThat(out.toByteArray()).containsExactly(
                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
        }
    }

    @Nested
    @DisplayName("writeInt64()")
    class WriteInt64Tests {

        @Test
        @DisplayName("should write little-endian int64")
        void shouldWriteLittleEndianInt64() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeInt64(0x123456789ABCDEF0L);
            }

            final ByteBuffer expected = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            expected.putLong(0x123456789ABCDEF0L);

            assertThat(out.toByteArray()).isEqualTo(expected.array());
        }

        @Test
        @DisplayName("should handle large values")
        void shouldHandleLargeValues() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeInt64(Long.MAX_VALUE);
            }

            final ByteBuffer expected = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            expected.putLong(Long.MAX_VALUE);

            assertThat(out.toByteArray()).isEqualTo(expected.array());
        }
    }

    // ==================== Write String Tests ====================

    @Nested
    @DisplayName("writeString()")
    class WriteStringTests {

        @Test
        @DisplayName("should write UTF-8 string")
        void shouldWriteUtf8String() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeString("Hello");
            }
            assertThat(out.toByteArray()).isEqualTo("Hello".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("should handle unicode characters")
        void shouldHandleUnicodeCharacters() throws IOException {
            final String unicodeStr = "日本語";
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeString(unicodeStr);
            }
            assertThat(out.toByteArray()).isEqualTo(unicodeStr.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmptyString() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeString("");
            }
            assertThat(out.toByteArray()).isEmpty();
        }
    }

    @Nested
    @DisplayName("writeLengthPrefixedString16()")
    class WriteLengthPrefixedString16Tests {

        @Test
        @DisplayName("should write length-prefixed string")
        void shouldWriteLengthPrefixedString() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeLengthPrefixedString16("Hello");
            }
            // Length = 5 (little-endian: 0x05 0x00) + "Hello"
            assertThat(out.toByteArray()).containsExactly(0x05, 0x00, 'H', 'e', 'l', 'l', 'o');
        }

        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmptyString() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeLengthPrefixedString16("");
            }
            assertThat(out.toByteArray()).containsExactly(0x00, 0x00);
        }

        @Test
        @DisplayName("should throw for too long string")
        void shouldThrowForTooLongString() {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            // Create a string longer than 65535 bytes
            final String longStr = "x".repeat(70000);
            try (BinaryWriter writer = new BinaryWriter(out)) {
                assertThatThrownBy(() -> writer.writeLengthPrefixedString16(longStr))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("too long");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("should handle unicode in length-prefixed string")
        void shouldHandleUnicodeInLengthPrefixedString() throws IOException {
            final String unicodeStr = "日本語";
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeLengthPrefixedString16(unicodeStr);
            }

            final byte[] strBytes = unicodeStr.getBytes(StandardCharsets.UTF_8);
            final ByteBuffer expected = ByteBuffer.allocate(2 + strBytes.length).order(ByteOrder.LITTLE_ENDIAN);
            expected.putShort((short) strBytes.length);
            expected.put(strBytes);

            assertThat(out.toByteArray()).isEqualTo(expected.array());
        }
    }

    // ==================== Padding Tests ====================

    @Nested
    @DisplayName("writePadding()")
    class WritePaddingTests {

        @Test
        @DisplayName("should write zeros to alignment")
        void shouldWriteZerosToAlignment() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeByte(0x42); // position = 1
                writer.writePadding(8); // should write 7 zeros to reach position 8
            }
            assertThat(out.toByteArray()).hasSize(8);
            assertThat(out.toByteArray()[0]).isEqualTo((byte) 0x42);
            for (int i = 1; i < 8; i++) {
                assertThat(out.toByteArray()[i]).isZero();
            }
        }

        @Test
        @DisplayName("should not write when already aligned")
        void shouldNotWriteWhenAlreadyAligned() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeBytes(new byte[8]); // position = 8 (aligned to 8)
                final long before = writer.getBytesWritten();
                writer.writePadding(8); // should write 0 bytes
                assertThat(writer.getBytesWritten()).isEqualTo(before);
            }
        }

        @Test
        @DisplayName("should align to 4-byte boundary")
        void shouldAlignToFourByteBoundary() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeByte(0x01);
                writer.writeByte(0x02);
                writer.writeByte(0x03); // position = 3
                writer.writePadding(4); // should write 1 zero to reach position 4
            }
            assertThat(out.toByteArray()).hasSize(4);
            assertThat(out.toByteArray()).containsExactly(0x01, 0x02, 0x03, 0x00);
        }
    }

    @Nested
    @DisplayName("writeZeros()")
    class WriteZerosTests {

        @Test
        @DisplayName("should write specified number of zeros")
        void shouldWriteSpecifiedZeros() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeZeros(5);
            }
            assertThat(out.toByteArray()).containsExactly(0, 0, 0, 0, 0);
        }

        @Test
        @DisplayName("should write nothing for zero count")
        void shouldWriteNothingForZeroCount() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeZeros(0);
            }
            assertThat(out.toByteArray()).isEmpty();
        }
    }

    // ==================== Magic Tests ====================

    @Nested
    @DisplayName("writeMagic()")
    class WriteMagicTests {

        @Test
        @DisplayName("should write APACK magic with null byte")
        void shouldWriteApackMagicWithNullByte() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeMagic();
            }

            final byte[] expected = new byte[6];
            System.arraycopy(FormatConstants.MAGIC, 0, expected, 0, 5);
            expected[5] = 0;

            assertThat(out.toByteArray()).isEqualTo(expected);
        }
    }

    // ==================== Buffering Tests ====================

    @Nested
    @DisplayName("Buffering")
    class BufferingTests {

        @Test
        @DisplayName("should flush buffer when full")
        void shouldFlushBufferWhenFull() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            // Use a small buffer to test flushing
            try (BinaryWriter writer = new BinaryWriter(out, 16)) {
                // Write more than buffer size
                writer.writeBytes(new byte[20]);
            }
            assertThat(out.toByteArray()).hasSize(20);
        }

        @Test
        @DisplayName("should flush on close")
        void shouldFlushOnClose() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final BinaryWriter writer = new BinaryWriter(out);
            writer.writeByte(0x42);
            // Data should be in buffer, not flushed yet
            writer.close();
            // After close, should be flushed
            assertThat(out.toByteArray()).containsExactly(0x42);
        }

        @Test
        @DisplayName("should flush on explicit flush call")
        void shouldFlushOnExplicitFlushCall() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeByte(0x42);
                writer.flush();
                assertThat(out.toByteArray()).containsExactly(0x42);
            }
        }
    }

    // ==================== Lifecycle Tests ====================

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("should track total bytes written")
        void shouldTrackTotalBytesWritten() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeByte(0x01);          // 1
                writer.writeUInt16(0x1234);      // 2
                writer.writeInt32(0x12345678);   // 4
                writer.writeInt64(0x123456789AL); // 8
                writer.writeBytes(new byte[10]); // 10
                writer.writeString("Hello");     // 5
                assertThat(writer.getBytesWritten()).isEqualTo(1 + 2 + 4 + 8 + 10 + 5);
            }
        }

        @Test
        @DisplayName("should throw IOException after close")
        void shouldThrowIOExceptionAfterClose() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final BinaryWriter writer = new BinaryWriter(out);
            writer.close();

            assertThatThrownBy(() -> writer.writeByte(0x42))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        @DisplayName("should be idempotent on close")
        void shouldBeIdempotentOnClose() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final BinaryWriter writer = new BinaryWriter(out);
            writer.close();
            writer.close(); // should not throw
        }
    }

    // ==================== Write Bytes Tests ====================

    @Nested
    @DisplayName("writeBytes()")
    class WriteBytesTests {

        @Test
        @DisplayName("should write entire array")
        void shouldWriteEntireArray() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeBytes(new byte[]{0x01, 0x02, 0x03});
            }
            assertThat(out.toByteArray()).containsExactly(0x01, 0x02, 0x03);
        }

        @Test
        @DisplayName("should write partial array")
        void shouldWritePartialArray() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeBytes(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05}, 1, 3);
            }
            assertThat(out.toByteArray()).containsExactly(0x02, 0x03, 0x04);
        }

        @Test
        @DisplayName("should handle empty array")
        void shouldHandleEmptyArray() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeBytes(new byte[0]);
            }
            assertThat(out.toByteArray()).isEmpty();
        }
    }

    // ==================== Round-trip Tests ====================

    @Nested
    @DisplayName("Round-trip with BinaryReader")
    class RoundtripTests {

        @Test
        @DisplayName("should round-trip all primitive types")
        void shouldRoundtripAllPrimitiveTypes() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeByte(0x42);
                writer.writeUInt16(0x1234);
                writer.writeInt32(0x12345678);
                writer.writeInt64(0x123456789ABCDEF0L);
                writer.writeLengthPrefixedString16("Hello World");
            }

            try (BinaryReader reader = new BinaryReader(new java.io.ByteArrayInputStream(out.toByteArray()))) {
                assertThat(reader.readByte()).isEqualTo(0x42);
                assertThat(reader.readUInt16()).isEqualTo(0x1234);
                assertThat(reader.readInt32()).isEqualTo(0x12345678);
                assertThat(reader.readInt64()).isEqualTo(0x123456789ABCDEF0L);
                assertThat(reader.readLengthPrefixedString16()).isEqualTo("Hello World");
            }
        }

        @Test
        @DisplayName("should round-trip magic number")
        void shouldRoundtripMagicNumber() throws Exception {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (BinaryWriter writer = new BinaryWriter(out)) {
                writer.writeMagic();
            }

            try (BinaryReader reader = new BinaryReader(new java.io.ByteArrayInputStream(out.toByteArray()))) {
                reader.readAndValidateMagic(); // should not throw
            }
        }
    }
}
