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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link EntryHeader}.
 *
 * <p>Tests the entry header record including builder functionality,
 * flag methods, attribute handling, and equality semantics.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@DisplayName("EntryHeader")
class EntryHeaderTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("should build with defaults")
        void shouldBuildWithDefaults() {
            // When
            final EntryHeader header = EntryHeader.builder().build();

            // Then
            assertThat(header.headerVersion()).isEqualTo(EntryHeader.CURRENT_VERSION);
            assertThat(header.flags()).isZero();
            assertThat(header.entryId()).isZero();
            assertThat(header.originalSize()).isZero();
            assertThat(header.storedSize()).isZero();
            assertThat(header.chunkCount()).isZero();
            assertThat(header.compressionId()).isEqualTo(FormatConstants.COMPRESSION_NONE);
            assertThat(header.encryptionId()).isEqualTo(FormatConstants.ENCRYPTION_NONE);
            assertThat(header.headerChecksum()).isZero();
            assertThat(header.name()).isEmpty();
            assertThat(header.mimeType()).isEmpty();
            assertThat(header.attributes()).isEmpty();
        }

        @Test
        @DisplayName("should set all fields")
        void shouldSetAllFields() {
            // Given
            final List<Attribute> attributes = List.of(
                    Attribute.ofString("author", "Test"),
                    Attribute.ofLong("version", 1L)
            );

            // When
            final EntryHeader header = EntryHeader.builder()
                    .headerVersion(2)
                    .flags(0xFF)
                    .entryId(42)
                    .originalSize(10000)
                    .storedSize(5000)
                    .chunkCount(3)
                    .compressionId(FormatConstants.COMPRESSION_ZSTD)
                    .encryptionId(FormatConstants.ENCRYPTION_AES_256_GCM)
                    .headerChecksum(12345)
                    .name("test/file.txt")
                    .mimeType("text/plain")
                    .attributes(attributes)
                    .build();

            // Then
            assertThat(header.headerVersion()).isEqualTo(2);
            assertThat(header.flags()).isEqualTo(0xFF);
            assertThat(header.entryId()).isEqualTo(42);
            assertThat(header.originalSize()).isEqualTo(10000);
            assertThat(header.storedSize()).isEqualTo(5000);
            assertThat(header.chunkCount()).isEqualTo(3);
            assertThat(header.compressionId()).isEqualTo(FormatConstants.COMPRESSION_ZSTD);
            assertThat(header.encryptionId()).isEqualTo(FormatConstants.ENCRYPTION_AES_256_GCM);
            assertThat(header.headerChecksum()).isEqualTo(12345);
            assertThat(header.name()).isEqualTo("test/file.txt");
            assertThat(header.mimeType()).isEqualTo("text/plain");
            assertThat(header.attributes()).hasSize(2);
        }

        @Test
        @DisplayName("should set flags via boolean methods")
        void shouldSetFlagsViaBooleanMethods() {
            // When
            final EntryHeader header = EntryHeader.builder()
                    .compressed(true)
                    .encrypted(true)
                    .hasEcc(true)
                    .build();

            // Then
            assertThat(header.isCompressed()).isTrue();
            assertThat(header.isEncrypted()).isTrue();
            assertThat(header.hasEcc()).isTrue();
        }

        @Test
        @DisplayName("should clear flags via boolean methods")
        void shouldClearFlagsViaBooleanMethods() {
            // Given - set all flags first
            final EntryHeader.Builder builder = EntryHeader.builder()
                    .compressed(true)
                    .encrypted(true)
                    .hasEcc(true);

            // When - clear them all
            final EntryHeader header = builder
                    .compressed(false)
                    .encrypted(false)
                    .hasEcc(false)
                    .build();

            // Then
            assertThat(header.isCompressed()).isFalse();
            assertThat(header.isEncrypted()).isFalse();
            assertThat(header.hasEcc()).isFalse();
        }

        @Test
        @DisplayName("should auto-set hasAttributes flag when attributes added")
        void shouldAutoSetHasAttributesFlagWhenAttributesAdded() {
            // When
            final EntryHeader header = EntryHeader.builder()
                    .attributes(List.of(Attribute.ofString("key", "value")))
                    .build();

            // Then
            assertThat(header.hasAttributes()).isTrue();
            assertThat(header.flags() & FormatConstants.ENTRY_FLAG_HAS_ATTRIBUTES).isNotZero();
        }

        @Test
        @DisplayName("should not set hasAttributes flag for empty attributes list")
        void shouldNotSetHasAttributesFlagForEmptyList() {
            // When
            final EntryHeader header = EntryHeader.builder()
                    .attributes(List.of())
                    .build();

            // Then
            assertThat(header.hasAttributes()).isFalse();
        }
    }

    @Nested
    @DisplayName("Flag Method Tests")
    class FlagMethodTests {

        @Test
        @DisplayName("isCompressed should return correctly")
        void isCompressedShouldReturnCorrectly() {
            // Given
            final EntryHeader withFlag = EntryHeader.builder()
                    .flags(FormatConstants.ENTRY_FLAG_COMPRESSED)
                    .build();
            final EntryHeader withoutFlag = EntryHeader.builder()
                    .flags(0)
                    .build();

            // Then
            assertThat(withFlag.isCompressed()).isTrue();
            assertThat(withoutFlag.isCompressed()).isFalse();
        }

        @Test
        @DisplayName("isEncrypted should return correctly")
        void isEncryptedShouldReturnCorrectly() {
            // Given
            final EntryHeader withFlag = EntryHeader.builder()
                    .flags(FormatConstants.ENTRY_FLAG_ENCRYPTED)
                    .build();
            final EntryHeader withoutFlag = EntryHeader.builder()
                    .flags(0)
                    .build();

            // Then
            assertThat(withFlag.isEncrypted()).isTrue();
            assertThat(withoutFlag.isEncrypted()).isFalse();
        }

        @Test
        @DisplayName("hasEcc should return correctly")
        void hasEccShouldReturnCorrectly() {
            // Given
            final EntryHeader withFlag = EntryHeader.builder()
                    .flags(FormatConstants.ENTRY_FLAG_HAS_ECC)
                    .build();
            final EntryHeader withoutFlag = EntryHeader.builder()
                    .flags(0)
                    .build();

            // Then
            assertThat(withFlag.hasEcc()).isTrue();
            assertThat(withoutFlag.hasEcc()).isFalse();
        }

        @Test
        @DisplayName("hasAttributes should return correctly")
        void hasAttributesShouldReturnCorrectly() {
            // Given
            final EntryHeader withFlag = EntryHeader.builder()
                    .flags(FormatConstants.ENTRY_FLAG_HAS_ATTRIBUTES)
                    .build();
            final EntryHeader withoutFlag = EntryHeader.builder()
                    .flags(0)
                    .build();

            // Then
            assertThat(withFlag.hasAttributes()).isTrue();
            assertThat(withoutFlag.hasAttributes()).isFalse();
        }

        @Test
        @DisplayName("should handle combined flags")
        void shouldHandleCombinedFlags() {
            // Given - all flags set
            final int allFlags = FormatConstants.ENTRY_FLAG_HAS_ATTRIBUTES
                    | FormatConstants.ENTRY_FLAG_COMPRESSED
                    | FormatConstants.ENTRY_FLAG_ENCRYPTED
                    | FormatConstants.ENTRY_FLAG_HAS_ECC;
            final EntryHeader header = EntryHeader.builder()
                    .flags(allFlags)
                    .build();

            // Then
            assertThat(header.hasAttributes()).isTrue();
            assertThat(header.isCompressed()).isTrue();
            assertThat(header.isEncrypted()).isTrue();
            assertThat(header.hasEcc()).isTrue();
        }
    }

    @Nested
    @DisplayName("Attribute Tests")
    class AttributeTests {

        @Test
        @DisplayName("should return immutable attributes list")
        void shouldReturnImmutableAttributesList() {
            // Given
            final EntryHeader header = EntryHeader.builder()
                    .attributes(List.of(Attribute.ofString("key", "value")))
                    .build();

            // When/Then
            assertThatThrownBy(() -> header.attributes().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should defensively copy attributes")
        void shouldDefensivelyCopyAttributes() {
            // Given
            final List<Attribute> mutableList = new ArrayList<>();
            mutableList.add(Attribute.ofString("original", "value"));

            // When
            final EntryHeader header = EntryHeader.builder()
                    .attributes(mutableList)
                    .build();

            // Modify original list
            mutableList.add(Attribute.ofString("added", "later"));

            // Then - header should not be affected
            assertThat(header.attributes()).hasSize(1);
            assertThat(header.attributes().get(0).key()).isEqualTo("original");
        }

        @Test
        @DisplayName("should store multiple attributes")
        void shouldStoreMultipleAttributes() {
            // Given
            final List<Attribute> attributes = List.of(
                    Attribute.ofString("name", "John"),
                    Attribute.ofLong("age", 30L),
                    Attribute.ofBoolean("active", true)
            );

            // When
            final EntryHeader header = EntryHeader.builder()
                    .attributes(attributes)
                    .build();

            // Then
            assertThat(header.attributes()).hasSize(3);
        }

        @Test
        @DisplayName("should handle empty attributes list")
        void shouldHandleEmptyAttributesList() {
            // When
            final EntryHeader header = EntryHeader.builder()
                    .attributes(List.of())
                    .build();

            // Then
            assertThat(header.attributes()).isEmpty();
            assertThat(header.hasAttributes()).isFalse();
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("should be equal with same values")
        void shouldBeEqualWithSameValues() {
            // Given
            final EntryHeader header1 = EntryHeader.builder()
                    .headerVersion(1)
                    .entryId(42)
                    .name("test.txt")
                    .originalSize(1000)
                    .storedSize(500)
                    .build();

            final EntryHeader header2 = EntryHeader.builder()
                    .headerVersion(1)
                    .entryId(42)
                    .name("test.txt")
                    .originalSize(1000)
                    .storedSize(500)
                    .build();

            // Then
            assertThat(header1).isEqualTo(header2);
        }

        @Test
        @DisplayName("should have consistent hashCode")
        void shouldHaveConsistentHashCode() {
            // Given
            final EntryHeader header1 = EntryHeader.builder()
                    .name("file.bin")
                    .entryId(10)
                    .originalSize(2048)
                    .build();

            final EntryHeader header2 = EntryHeader.builder()
                    .name("file.bin")
                    .entryId(10)
                    .originalSize(2048)
                    .build();

            // Then
            assertThat(header1.hashCode()).isEqualTo(header2.hashCode());
        }

        @Test
        @DisplayName("should not be equal with different values")
        void shouldNotBeEqualWithDifferentValues() {
            // Given
            final EntryHeader header1 = EntryHeader.builder()
                    .name("file1.txt")
                    .build();

            final EntryHeader header2 = EntryHeader.builder()
                    .name("file2.txt")
                    .build();

            // Then
            assertThat(header1).isNotEqualTo(header2);
        }

        @Test
        @DisplayName("should not be equal with different attributes")
        void shouldNotBeEqualWithDifferentAttributes() {
            // Given
            final EntryHeader header1 = EntryHeader.builder()
                    .name("test.txt")
                    .attributes(List.of(Attribute.ofString("key", "value1")))
                    .build();

            final EntryHeader header2 = EntryHeader.builder()
                    .name("test.txt")
                    .attributes(List.of(Attribute.ofString("key", "value2")))
                    .build();

            // Then
            assertThat(header1).isNotEqualTo(header2);
        }
    }

    @Nested
    @DisplayName("Size Tests")
    class SizeTests {

        @Test
        @DisplayName("should track original and stored sizes")
        void shouldTrackOriginalAndStoredSizes() {
            // Given
            final EntryHeader header = EntryHeader.builder()
                    .originalSize(10000)
                    .storedSize(3500)
                    .compressed(true)
                    .build();

            // Then
            assertThat(header.originalSize()).isEqualTo(10000);
            assertThat(header.storedSize()).isEqualTo(3500);
        }

        @Test
        @DisplayName("should track chunk count")
        void shouldTrackChunkCount() {
            // Given
            final EntryHeader header = EntryHeader.builder()
                    .chunkCount(5)
                    .build();

            // Then
            assertThat(header.chunkCount()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Current Version Tests")
    class CurrentVersionTests {

        @Test
        @DisplayName("should use current version by default")
        void shouldUseCurrentVersionByDefault() {
            // When
            final EntryHeader header = EntryHeader.builder().build();

            // Then
            assertThat(header.headerVersion()).isEqualTo(EntryHeader.CURRENT_VERSION);
        }

        @Test
        @DisplayName("CURRENT_VERSION constant should be defined")
        void currentVersionConstantShouldBeDefined() {
            // Then
            assertThat(EntryHeader.CURRENT_VERSION).isPositive();
        }
    }

    @Nested
    @DisplayName("Name and MIME Type Tests")
    class NameAndMimeTypeTests {

        @Test
        @DisplayName("should store entry name")
        void shouldStoreEntryName() {
            // Given
            final EntryHeader header = EntryHeader.builder()
                    .name("path/to/file.txt")
                    .build();

            // Then
            assertThat(header.name()).isEqualTo("path/to/file.txt");
        }

        @Test
        @DisplayName("should store MIME type")
        void shouldStoreMimeType() {
            // Given
            final EntryHeader header = EntryHeader.builder()
                    .mimeType("application/json")
                    .build();

            // Then
            assertThat(header.mimeType()).isEqualTo("application/json");
        }

        @Test
        @DisplayName("should handle Unicode names")
        void shouldHandleUnicodeNames() {
            // Given
            final String unicodeName = "文件/données/αρχείο.txt";
            final EntryHeader header = EntryHeader.builder()
                    .name(unicodeName)
                    .build();

            // Then
            assertThat(header.name()).isEqualTo(unicodeName);
        }

        @Test
        @DisplayName("should handle empty MIME type")
        void shouldHandleEmptyMimeType() {
            // Given
            final EntryHeader header = EntryHeader.builder()
                    .name("file.bin")
                    .mimeType("")
                    .build();

            // Then
            assertThat(header.mimeType()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Compression and Encryption ID Tests")
    class CompressionAndEncryptionIdTests {

        @Test
        @DisplayName("should store compression ID")
        void shouldStoreCompressionId() {
            // Given
            final EntryHeader header = EntryHeader.builder()
                    .compressionId(FormatConstants.COMPRESSION_ZSTD)
                    .compressed(true)
                    .build();

            // Then
            assertThat(header.compressionId()).isEqualTo(FormatConstants.COMPRESSION_ZSTD);
            assertThat(header.isCompressed()).isTrue();
        }

        @Test
        @DisplayName("should store encryption ID")
        void shouldStoreEncryptionId() {
            // Given
            final EntryHeader header = EntryHeader.builder()
                    .encryptionId(FormatConstants.ENCRYPTION_AES_256_GCM)
                    .encrypted(true)
                    .build();

            // Then
            assertThat(header.encryptionId()).isEqualTo(FormatConstants.ENCRYPTION_AES_256_GCM);
            assertThat(header.isEncrypted()).isTrue();
        }

        @Test
        @DisplayName("should handle LZ4 compression")
        void shouldHandleLz4Compression() {
            // Given
            final EntryHeader header = EntryHeader.builder()
                    .compressionId(FormatConstants.COMPRESSION_LZ4)
                    .compressed(true)
                    .build();

            // Then
            assertThat(header.compressionId()).isEqualTo(FormatConstants.COMPRESSION_LZ4);
        }

        @Test
        @DisplayName("should handle ChaCha20 encryption")
        void shouldHandleChaCha20Encryption() {
            // Given
            final EntryHeader header = EntryHeader.builder()
                    .encryptionId(FormatConstants.ENCRYPTION_CHACHA20_POLY1305)
                    .encrypted(true)
                    .build();

            // Then
            assertThat(header.encryptionId()).isEqualTo(FormatConstants.ENCRYPTION_CHACHA20_POLY1305);
        }
    }
}
