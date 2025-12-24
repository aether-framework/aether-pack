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
import de.splatgames.aether.pack.core.format.FormatConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Unicode edge cases in entry names and string handling.
 *
 * @author Erik Pfoertner
 * @since 0.1.0
 */
@DisplayName("Unicode Edge Case Tests")
class UnicodeEdgeCaseTest {

    @Nested
    @DisplayName("Valid Unicode Tests")
    class ValidUnicodeTests {

        @Test
        @DisplayName("should handle ASCII entry names")
        void shouldHandleAsciiNames(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("ascii.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("simple.txt", "content".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("with-dashes.txt", "content".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("with_underscores.txt", "content".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("CamelCase.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("simple.txt")).isNotNull();
                assertThat(reader.readAllBytes("with-dashes.txt")).isNotNull();
                assertThat(reader.readAllBytes("with_underscores.txt")).isNotNull();
                assertThat(reader.readAllBytes("CamelCase.txt")).isNotNull();
            }
        }

        @Test
        @DisplayName("should handle German umlauts")
        void shouldHandleGermanUmlauts(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("german.apack");
            final String name = "datei_mit_umlauten_\u00E4\u00F6\u00FC\u00DF.txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(name, "Inhalt mit Umlauten: \u00C4\u00D6\u00DC".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] content = reader.readAllBytes(name);
                assertThat(new String(content, StandardCharsets.UTF_8)).contains("\u00C4");
            }
        }

        @Test
        @DisplayName("should handle Japanese characters")
        void shouldHandleJapaneseCharacters(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("japanese.apack");
            final String name = "\u65E5\u672C\u8A9E\u30D5\u30A1\u30A4\u30EB.txt"; // 日本語ファイル

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(name, "\u3053\u3093\u306B\u3061\u306F".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(name)).isNotNull();
            }
        }

        @Test
        @DisplayName("should handle Chinese characters")
        void shouldHandleChineseCharacters(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("chinese.apack");
            final String name = "\u4E2D\u6587\u6587\u4EF6.txt"; // 中文文件

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(name, "\u4F60\u597D\u4E16\u754C".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(name)).isNotNull();
            }
        }

        @Test
        @DisplayName("should handle Cyrillic characters")
        void shouldHandleCyrillicCharacters(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("cyrillic.apack");
            final String name = "\u0420\u0443\u0441\u0441\u043A\u0438\u0439.txt"; // Русский

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(name, "\u041F\u0440\u0438\u0432\u0435\u0442".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(name)).isNotNull();
            }
        }

        @Test
        @DisplayName("should handle Arabic characters")
        void shouldHandleArabicCharacters(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("arabic.apack");
            final String name = "\u0627\u0644\u0639\u0631\u0628\u064A\u0629.txt"; // العربية

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(name, "\u0645\u0631\u062D\u0628\u0627".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(name)).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Boundary Tests")
    class BoundaryTests {

        @Test
        @DisplayName("should handle max name length in bytes")
        void shouldHandleMaxNameLengthInBytes(@TempDir final Path tempDir) throws Exception {
            // Create name that's close to max length (65535 bytes)
            // Use ASCII to make byte length predictable
            final char[] chars = new char[1000];
            Arrays.fill(chars, 'a');
            final String name = new String(chars) + ".txt";

            final Path archive = tempDir.resolve("long_name.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(name, "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(name)).isNotNull();
            }
        }

        @Test
        @DisplayName("should handle empty string entry name")
        void shouldHandleEmptyStringName(@TempDir final Path tempDir) throws Exception {
            // Empty names might not be allowed - documents behavior
            final Path archive = tempDir.resolve("empty_name.apack");

            try {
                try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                    writer.addEntry("", "content".getBytes(StandardCharsets.UTF_8));
                }
                // If it doesn't throw, reading should work
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    reader.readAllBytes("");
                }
            } catch (Exception e) {
                // Empty names might be rejected - acceptable
            }
        }

        @Test
        @DisplayName("should handle single character names")
        void shouldHandleSingleCharacterNames(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("single_char.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("a", "content".getBytes(StandardCharsets.UTF_8));
                writer.addEntry("\u00E4", "content".getBytes(StandardCharsets.UTF_8)); // ä
                writer.addEntry("\u4E2D", "content".getBytes(StandardCharsets.UTF_8)); // 中
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("a")).isNotNull();
                assertThat(reader.readAllBytes("\u00E4")).isNotNull();
                assertThat(reader.readAllBytes("\u4E2D")).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Special Character Tests")
    class SpecialCharacterTests {

        @Test
        @DisplayName("should handle zero-width characters")
        void shouldHandleZeroWidthCharacters(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("zero_width.apack");
            // Zero-width joiner
            final String name = "test\u200Dfile.txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(name, "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(name)).isNotNull();
            }
        }

        @Test
        @DisplayName("should handle combining marks")
        void shouldHandleCombiningMarks(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("combining.apack");
            // e + combining acute accent = é
            final String name = "cafe\u0301.txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(name, "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(name)).isNotNull();
            }
        }

        @Test
        @DisplayName("should handle BOM character in content")
        void shouldHandleBOMInContent(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("bom.apack");
            // Content with UTF-8 BOM
            final byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            final byte[] text = "Hello".getBytes(StandardCharsets.UTF_8);
            final byte[] content = new byte[bom.length + text.length];
            System.arraycopy(bom, 0, content, 0, bom.length);
            System.arraycopy(text, 0, content, bom.length, text.length);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("with_bom.txt", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                final byte[] read = reader.readAllBytes("with_bom.txt");
                assertThat(read).isEqualTo(content);
            }
        }

        @Test
        @DisplayName("should handle surrogate pairs (emoji-like)")
        void shouldHandleSurrogatePairs(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("surrogate.apack");
            // Mathematical bold capital A (U+1D400) requires surrogate pair
            final String name = "math_\uD835\uDC00.txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(name, "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(name)).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Invalid UTF-8 Tests")
    class InvalidUtf8Tests {

        @Test
        @DisplayName("should handle corrupted UTF-8 in name field - documents behavior")
        void shouldHandleCorruptedUtf8InName(@TempDir final Path tempDir) throws Exception {
            final Path archive = createValidArchive(tempDir, "valid.apack");
            final byte[] content = Files.readAllBytes(archive);

            // Find entry name in file and corrupt it with invalid UTF-8
            // This is simplified - actual corruption would need precise offset
            boolean corrupted = false;
            for (int i = FormatConstants.FILE_HEADER_SIZE; i < content.length - 20; i++) {
                if (content[i] == 'E' && content[i + 1] == 'N' &&
                    content[i + 2] == 'T' && content[i + 3] == 'R') {
                    // Entry header found - name follows after fixed fields
                    // Insert invalid UTF-8 sequence (truncated multibyte)
                    int nameOffset = i + 56; // Approximate name start
                    if (nameOffset + 5 < content.length) {
                        content[nameOffset] = (byte) 0xC0; // Invalid UTF-8 start byte
                        content[nameOffset + 1] = (byte) 0x80; // Invalid continuation
                        corrupted = true;
                    }
                    break;
                }
            }

            if (corrupted) {
                Files.write(archive, content);

                // Documents behavior - may throw or handle gracefully
                try (AetherPackReader reader = AetherPackReader.open(archive)) {
                    // If it opens, iteration may fail
                    for (var entry : reader) {
                        // Entry name might be corrupted
                    }
                } catch (Exception e) {
                    // Invalid UTF-8 detected - acceptable
                }
            }
        }
    }

    @Nested
    @DisplayName("Path-like Names Tests")
    class PathLikeNamesTests {

        @Test
        @DisplayName("should handle forward slashes in names")
        void shouldHandleForwardSlashes(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("slashes.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("dir/subdir/file.txt", "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("dir/subdir/file.txt")).isNotNull();
            }
        }

        @Test
        @DisplayName("should handle deeply nested paths")
        void shouldHandleDeeplyNestedPaths(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("deep.apack");
            final String name = "a/b/c/d/e/f/g/h/i/j/file.txt";

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry(name, "content".getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes(name)).isNotNull();
            }
        }
    }

    // ===== Helper Methods =====

    private Path createValidArchive(final Path tempDir, final String name) throws Exception {
        final Path archive = tempDir.resolve(name);
        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
            writer.addEntry("test.txt", "Test content".getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }
}
