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

package de.splatgames.aether.pack.core.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FileHeader}.
 *
 * <p>Tests the file header record including builder functionality,
 * flag methods, and equality semantics.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@DisplayName("FileHeader")
class FileHeaderTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("should build with defaults")
        void shouldBuildWithDefaults() {
            // When
            final FileHeader header = FileHeader.builder().build();

            // Then
            assertThat(header.versionMajor()).isEqualTo(FormatConstants.FORMAT_VERSION_MAJOR);
            assertThat(header.versionMinor()).isEqualTo(FormatConstants.FORMAT_VERSION_MINOR);
            assertThat(header.versionPatch()).isEqualTo(FormatConstants.FORMAT_VERSION_PATCH);
            assertThat(header.compatLevel()).isEqualTo(FormatConstants.COMPAT_LEVEL);
            assertThat(header.chunkSize()).isEqualTo(FormatConstants.DEFAULT_CHUNK_SIZE);
            assertThat(header.checksumAlgorithm()).isEqualTo(FormatConstants.CHECKSUM_XXH3_64);
            assertThat(header.modeFlags()).isZero();
            assertThat(header.entryCount()).isZero();
            assertThat(header.trailerOffset()).isZero();
        }

        @Test
        @DisplayName("should set all fields")
        void shouldSetAllFields() {
            // Given
            final long timestamp = System.currentTimeMillis();

            // When
            final FileHeader header = FileHeader.builder()
                    .versionMajor(2)
                    .versionMinor(1)
                    .versionPatch(3)
                    .compatLevel(5)
                    .modeFlags(0xFF)
                    .checksumAlgorithm(FormatConstants.CHECKSUM_CRC32)
                    .chunkSize(128 * 1024)
                    .headerChecksum(12345)
                    .entryCount(42)
                    .trailerOffset(98765)
                    .creationTimestamp(timestamp)
                    .build();

            // Then
            assertThat(header.versionMajor()).isEqualTo(2);
            assertThat(header.versionMinor()).isEqualTo(1);
            assertThat(header.versionPatch()).isEqualTo(3);
            assertThat(header.compatLevel()).isEqualTo(5);
            assertThat(header.modeFlags()).isEqualTo(0xFF);
            assertThat(header.checksumAlgorithm()).isEqualTo(FormatConstants.CHECKSUM_CRC32);
            assertThat(header.chunkSize()).isEqualTo(128 * 1024);
            assertThat(header.headerChecksum()).isEqualTo(12345);
            assertThat(header.entryCount()).isEqualTo(42);
            assertThat(header.trailerOffset()).isEqualTo(98765);
            assertThat(header.creationTimestamp()).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("should set mode flags via boolean methods")
        void shouldSetModeFlagsViaBooleanMethods() {
            // When
            final FileHeader header = FileHeader.builder()
                    .streamMode(true)
                    .encrypted(true)
                    .compressed(true)
                    .randomAccess(true)
                    .build();

            // Then
            assertThat(header.isStreamMode()).isTrue();
            assertThat(header.isEncrypted()).isTrue();
            assertThat(header.isCompressed()).isTrue();
            assertThat(header.hasRandomAccess()).isTrue();
        }

        @Test
        @DisplayName("should clear mode flags via boolean methods")
        void shouldClearModeFlagsViaBooleanMethods() {
            // Given - set all flags first
            final FileHeader.Builder builder = FileHeader.builder()
                    .streamMode(true)
                    .encrypted(true)
                    .compressed(true)
                    .randomAccess(true);

            // When - clear them all
            final FileHeader header = builder
                    .streamMode(false)
                    .encrypted(false)
                    .compressed(false)
                    .randomAccess(false)
                    .build();

            // Then
            assertThat(header.modeFlags()).isZero();
            assertThat(header.isStreamMode()).isFalse();
            assertThat(header.isEncrypted()).isFalse();
            assertThat(header.isCompressed()).isFalse();
            assertThat(header.hasRandomAccess()).isFalse();
        }

        @Test
        @DisplayName("should set creation timestamp to current time by default")
        void shouldSetCreationTimestampByDefault() {
            // Given
            final long before = System.currentTimeMillis();

            // When
            final FileHeader header = FileHeader.builder().build();

            // Then
            final long after = System.currentTimeMillis();
            assertThat(header.creationTimestamp())
                    .isGreaterThanOrEqualTo(before)
                    .isLessThanOrEqualTo(after);
        }
    }

    @Nested
    @DisplayName("Flag Method Tests")
    class FlagMethodTests {

        @Test
        @DisplayName("isStreamMode should return correctly")
        void isStreamModeShouldReturnCorrectly() {
            // Given
            final FileHeader withFlag = FileHeader.builder()
                    .modeFlags(FormatConstants.FLAG_STREAM_MODE)
                    .build();
            final FileHeader withoutFlag = FileHeader.builder()
                    .modeFlags(0)
                    .build();

            // Then
            assertThat(withFlag.isStreamMode()).isTrue();
            assertThat(withoutFlag.isStreamMode()).isFalse();
        }

        @Test
        @DisplayName("isCompressed should return correctly")
        void isCompressedShouldReturnCorrectly() {
            // Given
            final FileHeader withFlag = FileHeader.builder()
                    .modeFlags(FormatConstants.FLAG_COMPRESSED)
                    .build();
            final FileHeader withoutFlag = FileHeader.builder()
                    .modeFlags(0)
                    .build();

            // Then
            assertThat(withFlag.isCompressed()).isTrue();
            assertThat(withoutFlag.isCompressed()).isFalse();
        }

        @Test
        @DisplayName("isEncrypted should return correctly")
        void isEncryptedShouldReturnCorrectly() {
            // Given
            final FileHeader withFlag = FileHeader.builder()
                    .modeFlags(FormatConstants.FLAG_ENCRYPTED)
                    .build();
            final FileHeader withoutFlag = FileHeader.builder()
                    .modeFlags(0)
                    .build();

            // Then
            assertThat(withFlag.isEncrypted()).isTrue();
            assertThat(withoutFlag.isEncrypted()).isFalse();
        }

        @Test
        @DisplayName("hasRandomAccess should return correctly")
        void hasRandomAccessShouldReturnCorrectly() {
            // Given
            final FileHeader withFlag = FileHeader.builder()
                    .modeFlags(FormatConstants.FLAG_RANDOM_ACCESS)
                    .build();
            final FileHeader withoutFlag = FileHeader.builder()
                    .modeFlags(0)
                    .build();

            // Then
            assertThat(withFlag.hasRandomAccess()).isTrue();
            assertThat(withoutFlag.hasRandomAccess()).isFalse();
        }

        @Test
        @DisplayName("should handle combined flags")
        void shouldHandleCombinedFlags() {
            // Given - all flags set
            final int allFlags = FormatConstants.FLAG_STREAM_MODE
                    | FormatConstants.FLAG_ENCRYPTED
                    | FormatConstants.FLAG_COMPRESSED
                    | FormatConstants.FLAG_RANDOM_ACCESS;
            final FileHeader header = FileHeader.builder()
                    .modeFlags(allFlags)
                    .build();

            // Then
            assertThat(header.isStreamMode()).isTrue();
            assertThat(header.isEncrypted()).isTrue();
            assertThat(header.isCompressed()).isTrue();
            assertThat(header.hasRandomAccess()).isTrue();
        }

        @Test
        @DisplayName("should detect only specific flags from combined bitmask")
        void shouldDetectOnlySpecificFlags() {
            // Given - only compression and random access
            final int flags = FormatConstants.FLAG_COMPRESSED | FormatConstants.FLAG_RANDOM_ACCESS;
            final FileHeader header = FileHeader.builder()
                    .modeFlags(flags)
                    .build();

            // Then
            assertThat(header.isStreamMode()).isFalse();
            assertThat(header.isEncrypted()).isFalse();
            assertThat(header.isCompressed()).isTrue();
            assertThat(header.hasRandomAccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("should be equal with same values")
        void shouldBeEqualWithSameValues() {
            // Given
            final long timestamp = 1234567890L;
            final FileHeader header1 = FileHeader.builder()
                    .versionMajor(1)
                    .versionMinor(2)
                    .versionPatch(3)
                    .compatLevel(1)
                    .modeFlags(0)
                    .checksumAlgorithm(1)
                    .chunkSize(256 * 1024)
                    .headerChecksum(0)
                    .entryCount(10)
                    .trailerOffset(1000)
                    .creationTimestamp(timestamp)
                    .build();

            final FileHeader header2 = FileHeader.builder()
                    .versionMajor(1)
                    .versionMinor(2)
                    .versionPatch(3)
                    .compatLevel(1)
                    .modeFlags(0)
                    .checksumAlgorithm(1)
                    .chunkSize(256 * 1024)
                    .headerChecksum(0)
                    .entryCount(10)
                    .trailerOffset(1000)
                    .creationTimestamp(timestamp)
                    .build();

            // Then
            assertThat(header1).isEqualTo(header2);
        }

        @Test
        @DisplayName("should have consistent hashCode")
        void shouldHaveConsistentHashCode() {
            // Given
            final long timestamp = 9876543210L;
            final FileHeader header1 = FileHeader.builder()
                    .versionMajor(1)
                    .chunkSize(64 * 1024)
                    .entryCount(5)
                    .creationTimestamp(timestamp)
                    .build();

            final FileHeader header2 = FileHeader.builder()
                    .versionMajor(1)
                    .chunkSize(64 * 1024)
                    .entryCount(5)
                    .creationTimestamp(timestamp)
                    .build();

            // Then
            assertThat(header1.hashCode()).isEqualTo(header2.hashCode());
        }

        @Test
        @DisplayName("should not be equal with different values")
        void shouldNotBeEqualWithDifferentValues() {
            // Given
            final FileHeader header1 = FileHeader.builder()
                    .entryCount(10)
                    .build();

            final FileHeader header2 = FileHeader.builder()
                    .entryCount(20)
                    .build();

            // Then
            assertThat(header1).isNotEqualTo(header2);
        }
    }

    @Nested
    @DisplayName("Version Tests")
    class VersionTests {

        @Test
        @DisplayName("should use current format version by default")
        void shouldUseCurrentFormatVersion() {
            // When
            final FileHeader header = FileHeader.builder().build();

            // Then
            assertThat(header.versionMajor()).isEqualTo(FormatConstants.FORMAT_VERSION_MAJOR);
            assertThat(header.versionMinor()).isEqualTo(FormatConstants.FORMAT_VERSION_MINOR);
            assertThat(header.versionPatch()).isEqualTo(FormatConstants.FORMAT_VERSION_PATCH);
        }

        @Test
        @DisplayName("should allow setting custom version")
        void shouldAllowSettingCustomVersion() {
            // When
            final FileHeader header = FileHeader.builder()
                    .versionMajor(2)
                    .versionMinor(5)
                    .versionPatch(10)
                    .build();

            // Then
            assertThat(header.versionMajor()).isEqualTo(2);
            assertThat(header.versionMinor()).isEqualTo(5);
            assertThat(header.versionPatch()).isEqualTo(10);
        }
    }
}
