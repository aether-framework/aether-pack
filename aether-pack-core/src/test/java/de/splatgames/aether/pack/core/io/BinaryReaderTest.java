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

import de.splatgames.aether.pack.core.exception.FormatException;
import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BinaryReader}.
 */
@DisplayName("BinaryReader")
class BinaryReaderTest {

    // ==================== Read Byte Tests ====================

    @Nested
    @DisplayName("readByte()")
    class ReadByteTests {

        @Test
        @DisplayName("should read single byte")
        void shouldReadSingleByte() throws IOException {
            final byte[] data = {0x42};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readByte()).isEqualTo(0x42);
            }
        }

        @Test
        @DisplayName("should return unsigned value for high bytes")
        void shouldReturnUnsignedValueForHighBytes() throws IOException {
            final byte[] data = {(byte) 0xFF, (byte) 0x80, (byte) 0xFE};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readByte()).isEqualTo(255);
                assertThat(reader.readByte()).isEqualTo(128);
                assertThat(reader.readByte()).isEqualTo(254);
            }
        }

        @Test
        @DisplayName("should throw EOFException on empty stream")
        void shouldThrowEOFExceptionOnEmptyStream() {
            final byte[] data = {};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThatThrownBy(reader::readByte)
                        .isInstanceOf(EOFException.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("should increment bytes read counter")
        void shouldIncrementBytesReadCounter() throws IOException {
            final byte[] data = {0x01, 0x02, 0x03};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.getBytesRead()).isZero();
                reader.readByte();
                assertThat(reader.getBytesRead()).isEqualTo(1);
                reader.readByte();
                assertThat(reader.getBytesRead()).isEqualTo(2);
                reader.readByte();
                assertThat(reader.getBytesRead()).isEqualTo(3);
            }
        }
    }

    // ==================== Read Primitives Tests ====================

    @Nested
    @DisplayName("readUInt16()")
    class ReadUInt16Tests {

        @Test
        @DisplayName("should read little-endian uint16")
        void shouldReadLittleEndianUInt16() throws IOException {
            // 0x1234 in little-endian: 0x34 0x12
            final byte[] data = {0x34, 0x12};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readUInt16()).isEqualTo(0x1234);
            }
        }

        @Test
        @DisplayName("should handle max value 65535")
        void shouldHandleMaxValue() throws IOException {
            final byte[] data = {(byte) 0xFF, (byte) 0xFF};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readUInt16()).isEqualTo(65535);
            }
        }

        @Test
        @DisplayName("should throw EOFException when only one byte available")
        void shouldThrowEOFExceptionWhenOnlyOneByte() {
            final byte[] data = {0x34};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThatThrownBy(reader::readUInt16)
                        .isInstanceOf(EOFException.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nested
    @DisplayName("readInt32()")
    class ReadInt32Tests {

        @Test
        @DisplayName("should read little-endian int32")
        void shouldReadLittleEndianInt32() throws IOException {
            // 0x12345678 in little-endian: 0x78 0x56 0x34 0x12
            final byte[] data = {0x78, 0x56, 0x34, 0x12};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readInt32()).isEqualTo(0x12345678);
            }
        }

        @Test
        @DisplayName("should handle negative values")
        void shouldHandleNegativeValues() throws IOException {
            // -1 in little-endian: 0xFF 0xFF 0xFF 0xFF
            final byte[] data = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readInt32()).isEqualTo(-1);
            }
        }

        @Test
        @DisplayName("should handle min and max values")
        void shouldHandleMinAndMaxValues() throws IOException {
            final ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(Integer.MAX_VALUE);
            buf.putInt(Integer.MIN_VALUE);
            final byte[] data = buf.array();

            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readInt32()).isEqualTo(Integer.MAX_VALUE);
                assertThat(reader.readInt32()).isEqualTo(Integer.MIN_VALUE);
            }
        }
    }

    @Nested
    @DisplayName("readUInt32()")
    class ReadUInt32Tests {

        @Test
        @DisplayName("should return long for large values")
        void shouldReturnLongForLargeValues() throws IOException {
            // 0xFFFFFFFF in little-endian
            final byte[] data = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readUInt32()).isEqualTo(4294967295L);
            }
        }

        @Test
        @DisplayName("should handle values above Integer.MAX_VALUE")
        void shouldHandleValuesAboveIntMaxValue() throws IOException {
            // 0x80000000 = 2147483648 (Integer.MAX_VALUE + 1)
            final byte[] data = {0x00, 0x00, 0x00, (byte) 0x80};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readUInt32()).isEqualTo(2147483648L);
            }
        }
    }

    @Nested
    @DisplayName("readInt64()")
    class ReadInt64Tests {

        @Test
        @DisplayName("should read little-endian int64")
        void shouldReadLittleEndianInt64() throws IOException {
            final ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(0x123456789ABCDEF0L);
            final byte[] data = buf.array();

            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readInt64()).isEqualTo(0x123456789ABCDEF0L);
            }
        }

        @Test
        @DisplayName("should handle negative values")
        void shouldHandleNegativeValues() throws IOException {
            final ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(-1L);
            final byte[] data = buf.array();

            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readInt64()).isEqualTo(-1L);
            }
        }

        @Test
        @DisplayName("should handle large values")
        void shouldHandleLargeValues() throws IOException {
            final ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(Long.MAX_VALUE);
            final byte[] data = buf.array();

            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readInt64()).isEqualTo(Long.MAX_VALUE);
            }
        }
    }

    // ==================== Read String Tests ====================

    @Nested
    @DisplayName("readString()")
    class ReadStringTests {

        @Test
        @DisplayName("should read UTF-8 string")
        void shouldReadUtf8String() throws IOException {
            final byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readString(5)).isEqualTo("Hello");
            }
        }

        @Test
        @DisplayName("should handle unicode characters")
        void shouldHandleUnicodeCharacters() throws IOException {
            final String unicodeStr = "Hëllo Wörld 日本語";
            final byte[] data = unicodeStr.getBytes(StandardCharsets.UTF_8);
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readString(data.length)).isEqualTo(unicodeStr);
            }
        }

        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmptyString() throws IOException {
            final byte[] data = {};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readString(0)).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("readLengthPrefixedString16()")
    class ReadLengthPrefixedString16Tests {

        @Test
        @DisplayName("should read length-prefixed string")
        void shouldReadLengthPrefixedString() throws IOException {
            // Length = 5 (little-endian: 0x05 0x00) + "Hello"
            final byte[] data = {0x05, 0x00, 'H', 'e', 'l', 'l', 'o'};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readLengthPrefixedString16()).isEqualTo("Hello");
            }
        }

        @Test
        @DisplayName("should handle empty string with zero length prefix")
        void shouldHandleEmptyStringWithZeroLengthPrefix() throws IOException {
            final byte[] data = {0x00, 0x00};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readLengthPrefixedString16()).isEmpty();
            }
        }

        @Test
        @DisplayName("should handle unicode in length-prefixed string")
        void shouldHandleUnicodeInLengthPrefixedString() throws IOException {
            final String unicodeStr = "日本語";
            final byte[] strBytes = unicodeStr.getBytes(StandardCharsets.UTF_8);
            final ByteBuffer buf = ByteBuffer.allocate(2 + strBytes.length).order(ByteOrder.LITTLE_ENDIAN);
            buf.putShort((short) strBytes.length);
            buf.put(strBytes);
            final byte[] data = buf.array();

            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThat(reader.readLengthPrefixedString16()).isEqualTo(unicodeStr);
            }
        }
    }

    // ==================== Skip and Padding Tests ====================

    @Nested
    @DisplayName("skip() and skipPadding()")
    class SkipAndPaddingTests {

        @Test
        @DisplayName("should skip exact bytes")
        void shouldSkipExactBytes() throws IOException {
            final byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                reader.skip(3);
                assertThat(reader.getBytesRead()).isEqualTo(3);
                assertThat(reader.readByte()).isEqualTo(0x04);
            }
        }

        @Test
        @DisplayName("should skip to alignment")
        void shouldSkipToAlignment() throws IOException {
            final byte[] data = new byte[16];
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                reader.readByte(); // position = 1
                reader.skipPadding(8); // should skip 7 bytes to reach position 8
                assertThat(reader.getBytesRead()).isEqualTo(8);
            }
        }

        @Test
        @DisplayName("should not skip when already aligned")
        void shouldNotSkipWhenAlreadyAligned() throws IOException {
            final byte[] data = new byte[16];
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                reader.readBytes(8); // position = 8 (aligned to 8)
                reader.skipPadding(8); // should skip 0 bytes
                assertThat(reader.getBytesRead()).isEqualTo(8);
            }
        }

        @Test
        @DisplayName("should throw EOFException when cannot skip enough bytes")
        void shouldThrowEOFExceptionWhenCannotSkip() {
            final byte[] data = {0x01, 0x02};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThatThrownBy(() -> reader.skip(10))
                        .isInstanceOf(EOFException.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ==================== Magic Tests ====================

    @Nested
    @DisplayName("readAndValidateMagic()")
    class MagicTests {

        @Test
        @DisplayName("should accept valid magic")
        void shouldAcceptValidMagic() throws Exception {
            final byte[] data = new byte[6];
            System.arraycopy(FormatConstants.MAGIC, 0, data, 0, 5);
            data[5] = 0; // null byte

            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                reader.readAndValidateMagic(); // should not throw
                assertThat(reader.getBytesRead()).isEqualTo(6);
            }
        }

        @Test
        @DisplayName("should throw FormatException for invalid magic")
        void shouldThrowFormatExceptionForInvalidMagic() {
            final byte[] data = {'W', 'R', 'O', 'N', 'G', 0};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThatThrownBy(reader::readAndValidateMagic)
                        .isInstanceOf(FormatException.class)
                        .hasMessageContaining("Invalid magic");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("should throw FormatException for missing null byte")
        void shouldThrowFormatExceptionForMissingNullByte() {
            final byte[] data = new byte[6];
            System.arraycopy(FormatConstants.MAGIC, 0, data, 0, 5);
            data[5] = 0x42; // not null

            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThatThrownBy(reader::readAndValidateMagic)
                        .isInstanceOf(FormatException.class)
                        .hasMessageContaining("null byte");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ==================== Lifecycle Tests ====================

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("should track total bytes read")
        void shouldTrackTotalBytesRead() throws IOException {
            final byte[] data = new byte[100];
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                reader.readByte();          // 1
                reader.readUInt16();        // 2
                reader.readInt32();         // 4
                reader.readInt64();         // 8
                reader.readBytes(10);       // 10
                reader.readString(5);       // 5
                assertThat(reader.getBytesRead()).isEqualTo(1 + 2 + 4 + 8 + 10 + 5);
            }
        }

        @Test
        @DisplayName("should throw IOException after close")
        void shouldThrowIOExceptionAfterClose() throws IOException {
            final byte[] data = {0x01, 0x02, 0x03, 0x04};
            final BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));
            reader.close();

            assertThatThrownBy(reader::readByte)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        @DisplayName("should be idempotent on close")
        void shouldBeIdempotentOnClose() throws IOException {
            final byte[] data = {0x01};
            final BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));
            reader.close();
            reader.close(); // should not throw
        }
    }

    // ==================== Read Bytes Tests ====================

    @Nested
    @DisplayName("readBytes()")
    class ReadBytesTests {

        @Test
        @DisplayName("should read exact byte count")
        void shouldReadExactByteCount() throws IOException {
            final byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                final byte[] read = reader.readBytes(3);
                assertThat(read).containsExactly(0x01, 0x02, 0x03);
            }
        }

        @Test
        @DisplayName("should return empty array for zero length")
        void shouldReturnEmptyArrayForZeroLength() throws IOException {
            final byte[] data = {0x01};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                final byte[] read = reader.readBytes(0);
                assertThat(read).isEmpty();
            }
        }

        @Test
        @DisplayName("should throw EOFException when not enough bytes")
        void shouldThrowEOFExceptionWhenNotEnoughBytes() {
            final byte[] data = {0x01, 0x02};
            try (BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data))) {
                assertThatThrownBy(() -> reader.readBytes(10))
                        .isInstanceOf(EOFException.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
