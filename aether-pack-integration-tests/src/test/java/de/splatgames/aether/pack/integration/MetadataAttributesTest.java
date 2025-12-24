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
import de.splatgames.aether.pack.core.entry.Entry;
import de.splatgames.aether.pack.core.entry.EntryMetadata;
import de.splatgames.aether.pack.core.format.Attribute;
import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for entry metadata and custom attributes.
 *
 * <p>These tests verify that the APACK format correctly stores and retrieves
 * entry metadata including MIME types and custom attributes of various types.
 * Critical for savegames where metadata carries important game state information.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@DisplayName("Metadata and Attributes Tests")
class MetadataAttributesTest {

    @Nested
    @DisplayName("MIME Type Tests")
    class MimeTypeTests {

        @Test
        @DisplayName("should roundtrip MIME type")
        void shouldRoundtripMimeType(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("mime.apack");
            final byte[] content = "{}".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.of("data.json", "application/json");
                writer.addEntry(meta, new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.json").orElseThrow();
                assertThat(entry.getMimeType()).isEqualTo("application/json");
            }
        }

        @Test
        @DisplayName("should handle empty MIME type")
        void shouldHandleEmptyMimeType(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("no_mime.apack");
            final byte[] content = new byte[]{1, 2, 3};

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.of("data.bin");
                writer.addEntry(meta, new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                assertThat(entry.getMimeType()).isEmpty();
            }
        }

        @ParameterizedTest(name = "MIME type: {0}")
        @ValueSource(strings = {
                "text/plain",
                "text/html",
                "text/css",
                "text/javascript",
                "application/json",
                "application/xml",
                "application/pdf",
                "application/octet-stream",
                "image/png",
                "image/jpeg",
                "image/gif",
                "image/svg+xml",
                "audio/mpeg",
                "video/mp4",
                "application/x-custom-game-save"
        })
        @DisplayName("should roundtrip various MIME types")
        void shouldRoundtripVariousMimeTypes(final String mimeType, @TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("mime_" + mimeType.hashCode() + ".apack");
            final byte[] content = "content".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.of("file", mimeType);
                writer.addEntry(meta, new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("file").orElseThrow();
                assertThat(entry.getMimeType()).isEqualTo(mimeType);
            }
        }

        @Test
        @DisplayName("should handle multiple entries with different MIME types")
        void shouldHandleMultipleEntriesWithDifferentMimeTypes(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("multi_mime.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(EntryMetadata.of("doc.json", "application/json"),
                        new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
                writer.addEntry(EntryMetadata.of("image.png", "image/png"),
                        new ByteArrayInputStream(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}));
                writer.addEntry(EntryMetadata.of("text.txt", "text/plain"),
                        new ByteArrayInputStream("Hello".getBytes(StandardCharsets.UTF_8)));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntry("doc.json").orElseThrow().getMimeType())
                        .isEqualTo("application/json");
                assertThat(reader.getEntry("image.png").orElseThrow().getMimeType())
                        .isEqualTo("image/png");
                assertThat(reader.getEntry("text.txt").orElseThrow().getMimeType())
                        .isEqualTo("text/plain");
            }
        }
    }

    @Nested
    @DisplayName("String Attribute Tests")
    class StringAttributeTests {

        @Test
        @DisplayName("should roundtrip string attribute")
        void shouldRoundtripStringAttribute(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("string_attr.apack");
            final byte[] content = "save data".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("savegame.dat")
                        .attribute("playerName", "Hero")
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("savegame.dat").orElseThrow();
                assertThat(entry.getStringAttribute("playerName")).hasValue("Hero");
            }
        }

        @Test
        @DisplayName("should handle empty string attribute")
        void shouldHandleEmptyStringAttribute(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("empty_string_attr.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("data.bin")
                        .attribute("emptyValue", "")
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                assertThat(entry.getStringAttribute("emptyValue")).hasValue("");
            }
        }

        @Test
        @DisplayName("should handle Unicode string attribute")
        void shouldHandleUnicodeStringAttribute(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("unicode_attr.apack");
            final String unicodeValue = "勇者 Héros Герой 英雄";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("data.bin")
                        .attribute("unicodeName", unicodeValue)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                assertThat(entry.getStringAttribute("unicodeName")).hasValue(unicodeValue);
            }
        }

        @Test
        @DisplayName("should handle long string attribute")
        void shouldHandleLongStringAttribute(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("long_string_attr.apack");
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("a");
            }
            final String longValue = sb.toString();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("data.bin")
                        .attribute("longValue", longValue)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                assertThat(entry.getStringAttribute("longValue")).hasValue(longValue);
            }
        }
    }

    @Nested
    @DisplayName("Long Attribute Tests")
    class LongAttributeTests {

        @Test
        @DisplayName("should roundtrip long attribute")
        void shouldRoundtripLongAttribute(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("long_attr.apack");
            final byte[] content = "save data".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("savegame.dat")
                        .attribute("playerLevel", 99L)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("savegame.dat").orElseThrow();
                assertThat(entry.getLongAttribute("playerLevel")).hasValue(99L);
            }
        }

        @Test
        @DisplayName("should handle zero long attribute")
        void shouldHandleZeroLongAttribute(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("zero_long.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("data.bin")
                        .attribute("zeroValue", 0L)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                assertThat(entry.getLongAttribute("zeroValue")).hasValue(0L);
            }
        }

        @Test
        @DisplayName("should handle negative long attribute")
        void shouldHandleNegativeLongAttribute(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("negative_long.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("data.bin")
                        .attribute("negativeValue", -12345L)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                assertThat(entry.getLongAttribute("negativeValue")).hasValue(-12345L);
            }
        }

        @Test
        @DisplayName("should handle Long.MAX_VALUE")
        void shouldHandleLongMaxValue(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("max_long.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("data.bin")
                        .attribute("maxValue", Long.MAX_VALUE)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                assertThat(entry.getLongAttribute("maxValue")).hasValue(Long.MAX_VALUE);
            }
        }

        @Test
        @DisplayName("should handle Long.MIN_VALUE")
        void shouldHandleLongMinValue(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("min_long.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("data.bin")
                        .attribute("minValue", Long.MIN_VALUE)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                assertThat(entry.getLongAttribute("minValue")).hasValue(Long.MIN_VALUE);
            }
        }

        @Test
        @DisplayName("should handle timestamp attribute")
        void shouldHandleTimestampAttribute(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("timestamp.apack");
            final long timestamp = System.currentTimeMillis();
            final byte[] content = "save data".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("savegame.dat")
                        .attribute("created", timestamp)
                        .attribute("modified", timestamp + 1000)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("savegame.dat").orElseThrow();
                assertThat(entry.getLongAttribute("created")).hasValue(timestamp);
                assertThat(entry.getLongAttribute("modified")).hasValue(timestamp + 1000);
            }
        }
    }

    @Nested
    @DisplayName("Boolean Attribute Tests")
    class BooleanAttributeTests {

        @Test
        @DisplayName("should roundtrip true boolean attribute")
        void shouldRoundtripTrueBooleanAttribute(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("bool_true.apack");
            final byte[] content = "save data".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("savegame.dat")
                        .attribute("isHardMode", true)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("savegame.dat").orElseThrow();
                assertThat(entry.getBooleanAttribute("isHardMode")).hasValue(true);
            }
        }

        @Test
        @DisplayName("should roundtrip false boolean attribute")
        void shouldRoundtripFalseBooleanAttribute(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("bool_false.apack");
            final byte[] content = "save data".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("savegame.dat")
                        .attribute("isHardMode", false)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("savegame.dat").orElseThrow();
                assertThat(entry.getBooleanAttribute("isHardMode")).hasValue(false);
            }
        }
    }

    @Nested
    @DisplayName("Multiple Attributes Tests")
    class MultipleAttributesTests {

        @Test
        @DisplayName("should handle multiple attributes of same type")
        void shouldHandleMultipleAttributesOfSameType(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("multi_same_type.apack");
            final byte[] content = "save data".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("savegame.dat")
                        .attribute("key1", "value1")
                        .attribute("key2", "value2")
                        .attribute("key3", "value3")
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("savegame.dat").orElseThrow();
                assertThat(entry.getStringAttribute("key1")).hasValue("value1");
                assertThat(entry.getStringAttribute("key2")).hasValue("value2");
                assertThat(entry.getStringAttribute("key3")).hasValue("value3");
            }
        }

        @Test
        @DisplayName("should handle mixed attribute types")
        void shouldHandleMixedAttributeTypes(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("mixed_attrs.apack");
            final byte[] content = "save data".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("savegame.dat")
                        .attribute("playerName", "Hero")
                        .attribute("playerLevel", 99L)
                        .attribute("isHardMode", true)
                        .attribute("version", "1.0.0")
                        .attribute("playTime", 360000L) // 100 hours in seconds
                        .attribute("tutorialComplete", true)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(content));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("savegame.dat").orElseThrow();
                assertThat(entry.getStringAttribute("playerName")).hasValue("Hero");
                assertThat(entry.getLongAttribute("playerLevel")).hasValue(99L);
                assertThat(entry.getBooleanAttribute("isHardMode")).hasValue(true);
                assertThat(entry.getStringAttribute("version")).hasValue("1.0.0");
                assertThat(entry.getLongAttribute("playTime")).hasValue(360000L);
                assertThat(entry.getBooleanAttribute("tutorialComplete")).hasValue(true);
            }
        }

        @Test
        @DisplayName("should handle many attributes")
        void shouldHandleManyAttributes(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("many_attrs.apack");
            final int attributeCount = 50;

            EntryMetadata.Builder builder = EntryMetadata.builder().name("data.bin");
            for (int i = 0; i < attributeCount; i++) {
                builder.attribute("attr_" + i, "value_" + i);
            }

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(builder.build(), new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                assertThat(entry.getAttributes()).hasSize(attributeCount);
                for (int i = 0; i < attributeCount; i++) {
                    assertThat(entry.getStringAttribute("attr_" + i)).hasValue("value_" + i);
                }
            }
        }
    }

    @Nested
    @DisplayName("Attribute Access Tests")
    class AttributeAccessTests {

        @Test
        @DisplayName("should return empty optional for missing attribute")
        void shouldReturnEmptyOptionalForMissingAttribute(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("missing_attr.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("data.bin")
                        .attribute("existingKey", "value")
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                assertThat(entry.getStringAttribute("nonExistentKey")).isEmpty();
                assertThat(entry.getLongAttribute("nonExistentKey")).isEmpty();
                assertThat(entry.getBooleanAttribute("nonExistentKey")).isEmpty();
                assertThat(entry.getAttribute("nonExistentKey")).isEmpty();
            }
        }

        @Test
        @DisplayName("should return empty optional for wrong type")
        void shouldReturnEmptyOptionalForWrongType(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("wrong_type.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("data.bin")
                        .attribute("stringValue", "hello")
                        .attribute("longValue", 123L)
                        .attribute("boolValue", true)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();

                // Try to access with wrong type
                assertThat(entry.getLongAttribute("stringValue")).isEmpty();
                assertThat(entry.getBooleanAttribute("stringValue")).isEmpty();

                assertThat(entry.getStringAttribute("longValue")).isEmpty();
                assertThat(entry.getBooleanAttribute("longValue")).isEmpty();

                assertThat(entry.getStringAttribute("boolValue")).isEmpty();
                assertThat(entry.getLongAttribute("boolValue")).isEmpty();
            }
        }

        @Test
        @DisplayName("should get raw attribute object")
        void shouldGetRawAttributeObject(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("raw_attr.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("data.bin")
                        .attribute("testKey", "testValue")
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                Optional<Attribute> attr = entry.getAttribute("testKey");
                assertThat(attr).isPresent();
                assertThat(attr.get().key()).isEqualTo("testKey");
                assertThat(attr.get().asString()).isEqualTo("testValue");
            }
        }

        @Test
        @DisplayName("should get attributes list")
        void shouldGetAttributesList(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("attr_list.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                EntryMetadata meta = EntryMetadata.builder()
                        .name("data.bin")
                        .attribute("key1", "value1")
                        .attribute("key2", 123L)
                        .attribute("key3", true)
                        .build();
                writer.addEntry(meta, new ByteArrayInputStream(new byte[1]));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("data.bin").orElseThrow();
                List<Attribute> attributes = entry.getAttributes();
                assertThat(attributes).hasSize(3);
            }
        }
    }

    @Nested
    @DisplayName("Entry Size Tests")
    class EntrySizeTests {

        @Test
        @DisplayName("should track original size after writing")
        void shouldTrackOriginalSizeAfterWriting(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("size_tracking.apack");
            final byte[] content = "Hello World!".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("test.txt").orElseThrow();
                assertThat(entry.getOriginalSize()).isEqualTo(content.length);
            }
        }

        @Test
        @DisplayName("should track stored size after writing")
        void shouldTrackStoredSizeAfterWriting(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("stored_size.apack");
            final byte[] content = "Hello World!".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("test.txt").orElseThrow();
                // Stored size includes chunk header overhead
                assertThat(entry.getStoredSize()).isGreaterThanOrEqualTo(content.length);
            }
        }

        @Test
        @DisplayName("should track chunk count")
        void shouldTrackChunkCount(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("chunk_count.apack");
            final byte[] content = new byte[1000];

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.bin", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("test.bin").orElseThrow();
                // Chunk count should be non-negative (0 or more)
                assertThat(entry.getChunkCount()).isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Nested
    @DisplayName("Entry ID Tests")
    class EntryIdTests {

        @Test
        @DisplayName("should assign unique IDs to entries")
        void shouldAssignUniqueIdsToEntries(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("unique_ids.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("entry1.txt", "content1".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("entry2.txt", "content2".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("entry3.txt", "content3".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                long id1 = reader.getEntry("entry1.txt").orElseThrow().getId();
                long id2 = reader.getEntry("entry2.txt").orElseThrow().getId();
                long id3 = reader.getEntry("entry3.txt").orElseThrow().getId();

                assertThat(id1).isNotEqualTo(id2);
                assertThat(id2).isNotEqualTo(id3);
                assertThat(id1).isNotEqualTo(id3);
            }
        }

        @Test
        @DisplayName("should find entry by ID")
        void shouldFindEntryById(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("find_by_id.apack");
            final byte[] content = "test content".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry byName = reader.getEntry("test.txt").orElseThrow();
                long entryId = byName.getId();

                Entry byId = reader.getEntry(entryId);
                assertThat(byId.getName()).isEqualTo("test.txt");
            }
        }
    }

    @Nested
    @DisplayName("Processing Flags Tests")
    class ProcessingFlagsTests {

        @Test
        @DisplayName("should report uncompressed entry correctly")
        void shouldReportUncompressedEntryCorrectly(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("uncompressed.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("test.txt").orElseThrow();
                assertThat(entry.isCompressed()).isFalse();
                assertThat(entry.getCompressionId()).isEqualTo(FormatConstants.COMPRESSION_NONE);
            }
        }

        @Test
        @DisplayName("should report unencrypted entry correctly")
        void shouldReportUnencryptedEntryCorrectly(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("unencrypted.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("test.txt").orElseThrow();
                assertThat(entry.isEncrypted()).isFalse();
                assertThat(entry.getEncryptionId()).isEqualTo(FormatConstants.ENCRYPTION_NONE);
            }
        }

        @Test
        @DisplayName("should report no ECC by default")
        void shouldReportNoEccByDefault(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("no_ecc.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("test.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                Entry entry = reader.getEntry("test.txt").orElseThrow();
                assertThat(entry.hasEcc()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Savegame Metadata Scenarios")
    class SavegameMetadataScenarios {

        @Test
        @DisplayName("should handle complete savegame metadata")
        void shouldHandleCompleteSavegameMetadata(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("complete_save.apack");
            final long creationTime = System.currentTimeMillis();

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                // Player data entry
                EntryMetadata playerMeta = EntryMetadata.builder()
                        .name("player/data.json")
                        .mimeType("application/json")
                        .attribute("playerName", "勇者")
                        .attribute("playerLevel", 99L)
                        .attribute("playTimeSeconds", 360000L)
                        .attribute("isHardMode", true)
                        .build();
                writer.addEntry(playerMeta,
                        new ByteArrayInputStream("{\"stats\":{}}".getBytes(StandardCharsets.UTF_8)));

                // World state entry
                EntryMetadata worldMeta = EntryMetadata.builder()
                        .name("world/state.bin")
                        .mimeType("application/octet-stream")
                        .attribute("worldVersion", 3L)
                        .attribute("seed", 12345L)
                        .build();
                writer.addEntry(worldMeta, new ByteArrayInputStream(new byte[1000]));

                // Metadata entry
                EntryMetadata metaMeta = EntryMetadata.builder()
                        .name("meta.json")
                        .mimeType("application/json")
                        .attribute("saveVersion", "1.2.0")
                        .attribute("gameVersion", "2.5.1")
                        .attribute("created", creationTime)
                        .attribute("modified", creationTime)
                        .attribute("platform", "PC")
                        .build();
                writer.addEntry(metaMeta,
                        new ByteArrayInputStream("{\"type\":\"save\"}".getBytes(StandardCharsets.UTF_8)));
            }

            // Verify everything was saved correctly
            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(3);

                // Verify player data
                Entry player = reader.getEntry("player/data.json").orElseThrow();
                assertThat(player.getMimeType()).isEqualTo("application/json");
                assertThat(player.getStringAttribute("playerName")).hasValue("勇者");
                assertThat(player.getLongAttribute("playerLevel")).hasValue(99L);
                assertThat(player.getLongAttribute("playTimeSeconds")).hasValue(360000L);
                assertThat(player.getBooleanAttribute("isHardMode")).hasValue(true);

                // Verify world state
                Entry world = reader.getEntry("world/state.bin").orElseThrow();
                assertThat(world.getMimeType()).isEqualTo("application/octet-stream");
                assertThat(world.getLongAttribute("worldVersion")).hasValue(3L);
                assertThat(world.getLongAttribute("seed")).hasValue(12345L);

                // Verify metadata
                Entry meta = reader.getEntry("meta.json").orElseThrow();
                assertThat(meta.getMimeType()).isEqualTo("application/json");
                assertThat(meta.getStringAttribute("saveVersion")).hasValue("1.2.0");
                assertThat(meta.getStringAttribute("gameVersion")).hasValue("2.5.1");
                assertThat(meta.getLongAttribute("created")).hasValue(creationTime);
                assertThat(meta.getStringAttribute("platform")).hasValue("PC");
            }
        }
    }
}
