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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for multi-language Unicode support.
 *
 * <p>These tests verify that the APACK format correctly handles text in
 * various languages and scripts. Critical for international game localizations
 * and player content in different languages.</p>
 *
 * <p>Covers: German, English, Japanese (Hiragana, Katakana, Kanji),
 * Spanish, Chinese (Simplified), Russian (Cyrillic), and mixed content.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@DisplayName("Multi-Language Unicode Tests")
class MultiLanguageUnicodeTest {

    // === German Test Data ===
    private static final String GERMAN_SIMPLE = "Guten Tag! Wie geht es Ihnen?";
    private static final String GERMAN_UMLAUTS = "Größe, Übung, Äpfel, Öl, müssen, können, für";
    private static final String GERMAN_ESZETT = "Straße, Maß, Gruß, Fuß, Floß";
    private static final String GERMAN_PARAGRAPH = """
            Das Spiel speichert automatisch.
            Bitte warten Sie, bis der Speichervorgang abgeschlossen ist.
            Änderungen werden in der nächsten Sitzung übernommen.
            Größere Dateien können länger dauern.""";

    // === English Test Data ===
    private static final String ENGLISH_SIMPLE = "Hello World! Welcome to the game.";
    private static final String ENGLISH_SPECIAL = "Game's over! You've won & earned 100% completion.";
    private static final String ENGLISH_TECHNICAL = "Save file v2.0.1-beta created at 2024-12-23T10:30:00Z";

    // === Japanese Test Data ===
    private static final String JAPANESE_HIRAGANA = "ひらがなのテストです。こんにちは。";
    private static final String JAPANESE_KATAKANA = "カタカナノテストデス。コンニチハ。";
    private static final String JAPANESE_KANJI = "日本語の漢字テスト。保存完了。";
    private static final String JAPANESE_MIXED = "プレイヤー「勇者」がレベル99に到達しました！経験値: 9999999";
    private static final String JAPANESE_FULL = """
            セーブデータ
            プレイヤー名：勇者
            レベル：99
            所持金：1,234,567ゴールド
            プレイ時間：123時間45分
            現在地：魔王城・最深部""";

    // === Spanish Test Data ===
    private static final String SPANISH_SIMPLE = "¡Hola! ¿Cómo estás?";
    private static final String SPANISH_ACCENTS = "Año, España, mañana, señor, niño, corazón";
    private static final String SPANISH_PARAGRAPH = """
            El juego se ha guardado correctamente.
            ¡Felicitaciones! Has completado la misión.
            Continúa tu aventura mañana.""";

    // === Chinese (Simplified) Test Data ===
    private static final String CHINESE_SIMPLE = "你好世界！欢迎来到游戏。";
    private static final String CHINESE_GAMING = "玩家等级：99级 经验值：9999999 金币：1234567";
    private static final String CHINESE_PARAGRAPH = """
            存档已保存
            玩家：英雄
            等级：99
            金币：1,234,567
            游戏时间：123小时45分钟
            当前位置：魔王城""";

    // === Russian (Cyrillic) Test Data ===
    private static final String RUSSIAN_SIMPLE = "Привет мир! Добро пожаловать в игру.";
    private static final String RUSSIAN_GAMING = "Игрок: Герой | Уровень: 99 | Опыт: 9999999";
    private static final String RUSSIAN_PARAGRAPH = """
            Сохранение завершено
            Имя игрока: Герой
            Уровень: 99
            Золото: 1,234,567
            Время игры: 123 часа 45 минут
            Локация: Замок Тьмы""";

    // === Special Unicode Cases ===
    private static final String EMOJI_TEXT = "Player 🎮 won the game! 🏆 Score: 💯";
    private static final String MIXED_SCRIPTS = "Hello こんにちは Привет 你好 ¡Hola! Größe";
    private static final String MATH_SYMBOLS = "∑∏∫∂∇ε∞≈≠≤≥±×÷√";
    private static final String CURRENCY_SYMBOLS = "€£¥₹₽¢$₿";

    @Nested
    @DisplayName("German Language Tests")
    class GermanLanguageTests {

        @Test
        @DisplayName("should handle simple German text")
        void shouldHandleSimpleGermanText(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "german_simple.txt", GERMAN_SIMPLE);
        }

        @Test
        @DisplayName("should handle German umlauts (ä, ö, ü, Ä, Ö, Ü)")
        void shouldHandleGermanUmlauts(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "german_umlauts.txt", GERMAN_UMLAUTS);
        }

        @Test
        @DisplayName("should handle German Eszett (ß)")
        void shouldHandleGermanEszett(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "german_eszett.txt", GERMAN_ESZETT);
        }

        @Test
        @DisplayName("should handle German paragraph with mixed characters")
        void shouldHandleGermanParagraph(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "german_paragraph.txt", GERMAN_PARAGRAPH);
        }

        @Test
        @DisplayName("should handle German entry names with umlauts")
        void shouldHandleGermanEntryNamesWithUmlauts(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("german_names.apack");
            final byte[] content = "Inhalt".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("Größe.txt", content);
                writer.addEntry("Übung/datei.txt", content);
                writer.addEntry("Äpfel_und_Öl.txt", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("Größe.txt")).isEqualTo(content);
                assertThat(reader.readAllBytes("Übung/datei.txt")).isEqualTo(content);
                assertThat(reader.readAllBytes("Äpfel_und_Öl.txt")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("English Language Tests")
    class EnglishLanguageTests {

        @Test
        @DisplayName("should handle simple English text")
        void shouldHandleSimpleEnglishText(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "english_simple.txt", ENGLISH_SIMPLE);
        }

        @Test
        @DisplayName("should handle English with special characters")
        void shouldHandleEnglishWithSpecialCharacters(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "english_special.txt", ENGLISH_SPECIAL);
        }

        @Test
        @DisplayName("should handle technical English content")
        void shouldHandleTechnicalEnglishContent(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "english_technical.txt", ENGLISH_TECHNICAL);
        }
    }

    @Nested
    @DisplayName("Japanese Language Tests")
    class JapaneseLanguageTests {

        @Test
        @DisplayName("should handle Hiragana text")
        void shouldHandleHiraganaText(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "japanese_hiragana.txt", JAPANESE_HIRAGANA);
        }

        @Test
        @DisplayName("should handle Katakana text")
        void shouldHandleKatakanaText(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "japanese_katakana.txt", JAPANESE_KATAKANA);
        }

        @Test
        @DisplayName("should handle Kanji text")
        void shouldHandleKanjiText(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "japanese_kanji.txt", JAPANESE_KANJI);
        }

        @Test
        @DisplayName("should handle mixed Japanese scripts")
        void shouldHandleMixedJapaneseScripts(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "japanese_mixed.txt", JAPANESE_MIXED);
        }

        @Test
        @DisplayName("should handle full Japanese save data")
        void shouldHandleFullJapaneseSaveData(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "japanese_full.txt", JAPANESE_FULL);
        }

        @Test
        @DisplayName("should handle Japanese entry names")
        void shouldHandleJapaneseEntryNames(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("japanese_names.apack");
            final byte[] content = "データ".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("セーブデータ.txt", content);
                writer.addEntry("プレイヤー/勇者.dat", content);
                writer.addEntry("設定/ゲーム設定.json", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("セーブデータ.txt")).isEqualTo(content);
                assertThat(reader.readAllBytes("プレイヤー/勇者.dat")).isEqualTo(content);
                assertThat(reader.readAllBytes("設定/ゲーム設定.json")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Spanish Language Tests")
    class SpanishLanguageTests {

        @Test
        @DisplayName("should handle Spanish inverted punctuation")
        void shouldHandleSpanishInvertedPunctuation(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "spanish_simple.txt", SPANISH_SIMPLE);
        }

        @Test
        @DisplayName("should handle Spanish accented characters")
        void shouldHandleSpanishAccentedCharacters(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "spanish_accents.txt", SPANISH_ACCENTS);
        }

        @Test
        @DisplayName("should handle Spanish paragraph")
        void shouldHandleSpanishParagraph(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "spanish_paragraph.txt", SPANISH_PARAGRAPH);
        }

        @Test
        @DisplayName("should handle Spanish entry names with ñ and accents")
        void shouldHandleSpanishEntryNames(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("spanish_names.apack");
            final byte[] content = "Contenido".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("Año.txt", content);
                writer.addEntry("mañana/datos.txt", content);
                writer.addEntry("España/configuración.json", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("Año.txt")).isEqualTo(content);
                assertThat(reader.readAllBytes("mañana/datos.txt")).isEqualTo(content);
                assertThat(reader.readAllBytes("España/configuración.json")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Chinese Language Tests")
    class ChineseLanguageTests {

        @Test
        @DisplayName("should handle simple Chinese text")
        void shouldHandleSimpleChineseText(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "chinese_simple.txt", CHINESE_SIMPLE);
        }

        @Test
        @DisplayName("should handle Chinese gaming terms")
        void shouldHandleChineseGamingTerms(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "chinese_gaming.txt", CHINESE_GAMING);
        }

        @Test
        @DisplayName("should handle Chinese save data paragraph")
        void shouldHandleChineseSaveDataParagraph(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "chinese_paragraph.txt", CHINESE_PARAGRAPH);
        }

        @Test
        @DisplayName("should handle Chinese entry names")
        void shouldHandleChineseEntryNames(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("chinese_names.apack");
            final byte[] content = "内容".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("存档.txt", content);
                writer.addEntry("玩家/英雄.dat", content);
                writer.addEntry("设置/游戏设置.json", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("存档.txt")).isEqualTo(content);
                assertThat(reader.readAllBytes("玩家/英雄.dat")).isEqualTo(content);
                assertThat(reader.readAllBytes("设置/游戏设置.json")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Russian Language Tests")
    class RussianLanguageTests {

        @Test
        @DisplayName("should handle simple Russian text")
        void shouldHandleSimpleRussianText(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "russian_simple.txt", RUSSIAN_SIMPLE);
        }

        @Test
        @DisplayName("should handle Russian gaming terms")
        void shouldHandleRussianGamingTerms(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "russian_gaming.txt", RUSSIAN_GAMING);
        }

        @Test
        @DisplayName("should handle Russian save data paragraph")
        void shouldHandleRussianSaveDataParagraph(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "russian_paragraph.txt", RUSSIAN_PARAGRAPH);
        }

        @Test
        @DisplayName("should handle Russian entry names")
        void shouldHandleRussianEntryNames(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("russian_names.apack");
            final byte[] content = "Содержимое".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("Сохранение.txt", content);
                writer.addEntry("Игрок/Герой.dat", content);
                writer.addEntry("Настройки/игра.json", content);
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.readAllBytes("Сохранение.txt")).isEqualTo(content);
                assertThat(reader.readAllBytes("Игрок/Герой.dat")).isEqualTo(content);
                assertThat(reader.readAllBytes("Настройки/игра.json")).isEqualTo(content);
            }
        }
    }

    @Nested
    @DisplayName("Special Unicode Tests")
    class SpecialUnicodeTests {

        @Test
        @DisplayName("should handle emoji in content")
        void shouldHandleEmojiInContent(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "emoji.txt", EMOJI_TEXT);
        }

        @Test
        @DisplayName("should handle mixed scripts in single file")
        void shouldHandleMixedScriptsInSingleFile(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "mixed_scripts.txt", MIXED_SCRIPTS);
        }

        @Test
        @DisplayName("should handle mathematical symbols")
        void shouldHandleMathematicalSymbols(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "math_symbols.txt", MATH_SYMBOLS);
        }

        @Test
        @DisplayName("should handle currency symbols")
        void shouldHandleCurrencySymbols(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "currency.txt", CURRENCY_SYMBOLS);
        }

        @Test
        @DisplayName("should handle zero-width characters")
        void shouldHandleZeroWidthCharacters(@TempDir final Path tempDir) throws Exception {
            final String zeroWidth = "Hello\u200BWorld\u200CTest\u200DEnd\uFEFF";
            verifyRoundtrip(tempDir, "zero_width.txt", zeroWidth);
        }

        @Test
        @DisplayName("should handle combining characters")
        void shouldHandleCombiningCharacters(@TempDir final Path tempDir) throws Exception {
            // e + combining acute accent = é (different from precomposed é)
            final String combining = "e\u0301 vs é";
            verifyRoundtrip(tempDir, "combining.txt", combining);
        }

        @Test
        @DisplayName("should handle right-to-left text")
        void shouldHandleRightToLeftText(@TempDir final Path tempDir) throws Exception {
            final String hebrew = "שָׁלוֹם עוֹלָם"; // Shalom Olam
            final String arabic = "مرحبا بالعالم"; // Hello World
            verifyRoundtrip(tempDir, "rtl_hebrew.txt", hebrew);
            verifyRoundtrip(tempDir, "rtl_arabic.txt", arabic);
        }

        @Test
        @DisplayName("should handle surrogate pairs (emoji, rare CJK)")
        void shouldHandleSurrogatePairs(@TempDir final Path tempDir) throws Exception {
            // These require surrogate pairs in UTF-16
            final String surrogatePairs = "𝄞 𠀀 🎮 🎯 🏆";
            verifyRoundtrip(tempDir, "surrogate_pairs.txt", surrogatePairs);
        }
    }

    @Nested
    @DisplayName("Multi-Language Archive Tests")
    class MultiLanguageArchiveTests {

        @Test
        @DisplayName("should handle all languages in single archive")
        void shouldHandleAllLanguagesInSingleArchive(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("all_languages.apack");

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("de/text.txt", GERMAN_PARAGRAPH.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("en/text.txt", ENGLISH_SIMPLE.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("ja/text.txt", JAPANESE_FULL.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("es/text.txt", SPANISH_PARAGRAPH.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("zh/text.txt", CHINESE_PARAGRAPH.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("ru/text.txt", RUSSIAN_PARAGRAPH.getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(6);
                assertThat(new String(reader.readAllBytes("de/text.txt"), StandardCharsets.UTF_8))
                        .isEqualTo(GERMAN_PARAGRAPH);
                assertThat(new String(reader.readAllBytes("en/text.txt"), StandardCharsets.UTF_8))
                        .isEqualTo(ENGLISH_SIMPLE);
                assertThat(new String(reader.readAllBytes("ja/text.txt"), StandardCharsets.UTF_8))
                        .isEqualTo(JAPANESE_FULL);
                assertThat(new String(reader.readAllBytes("es/text.txt"), StandardCharsets.UTF_8))
                        .isEqualTo(SPANISH_PARAGRAPH);
                assertThat(new String(reader.readAllBytes("zh/text.txt"), StandardCharsets.UTF_8))
                        .isEqualTo(CHINESE_PARAGRAPH);
                assertThat(new String(reader.readAllBytes("ru/text.txt"), StandardCharsets.UTF_8))
                        .isEqualTo(RUSSIAN_PARAGRAPH);
            }
        }

        @Test
        @DisplayName("should handle localized entry names in single archive")
        void shouldHandleLocalizedEntryNamesInSingleArchive(@TempDir final Path tempDir) throws Exception {
            final Path archive = tempDir.resolve("localized_names.apack");
            final byte[] content = "content".getBytes(StandardCharsets.UTF_8);

            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("Datei.txt", content);           // German
                writer.addEntry("ファイル.txt", content);          // Japanese
                writer.addEntry("Archivo.txt", content);         // Spanish
                writer.addEntry("文件.txt", content);             // Chinese
                writer.addEntry("Файл.txt", content);            // Russian
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                assertThat(reader.getEntryCount()).isEqualTo(5);
                assertThat(reader.readAllBytes("Datei.txt")).isEqualTo(content);
                assertThat(reader.readAllBytes("ファイル.txt")).isEqualTo(content);
                assertThat(reader.readAllBytes("Archivo.txt")).isEqualTo(content);
                assertThat(reader.readAllBytes("文件.txt")).isEqualTo(content);
                assertThat(reader.readAllBytes("Файл.txt")).isEqualTo(content);
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.splatgames.aether.pack.integration.MultiLanguageUnicodeTest#languageTestCases")
        @DisplayName("should roundtrip language content")
        void shouldRoundtripLanguageContent(final String language, final String content,
                                            @TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, language + ".txt", content);
        }
    }

    @Nested
    @DisplayName("Edge Cases with Unicode")
    class UnicodeEdgeCases {

        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmptyString(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "empty.txt", "");
        }

        @Test
        @DisplayName("should handle single Unicode character")
        void shouldHandleSingleUnicodeCharacter(@TempDir final Path tempDir) throws Exception {
            verifyRoundtrip(tempDir, "single_char.txt", "日");
        }

        @Test
        @DisplayName("should handle very long Unicode string")
        void shouldHandleVeryLongUnicodeString(@TempDir final Path tempDir) throws Exception {
            final String base = "日本語テスト";
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append(base).append(i);
            }
            verifyRoundtrip(tempDir, "long_unicode.txt", sb.toString());
        }

        @Test
        @DisplayName("should handle Unicode null character")
        void shouldHandleUnicodeNullCharacter(@TempDir final Path tempDir) throws Exception {
            final String withNull = "before\0after";
            verifyRoundtrip(tempDir, "null_char.txt", withNull);
        }

        @Test
        @DisplayName("should handle all BMP code points sample")
        void shouldHandleAllBmpCodePointsSample(@TempDir final Path tempDir) throws Exception {
            final StringBuilder sb = new StringBuilder();
            // Sample from various BMP ranges (excluding surrogates and reserved)
            for (int i = 0x0021; i < 0x0100; i++) {
                sb.appendCodePoint(i);
            }
            for (int i = 0x0400; i < 0x0500; i++) { // Cyrillic
                sb.appendCodePoint(i);
            }
            for (int i = 0x3040; i < 0x30A0; i++) { // Hiragana
                sb.appendCodePoint(i);
            }
            for (int i = 0x4E00; i < 0x4E50; i++) { // CJK sample
                sb.appendCodePoint(i);
            }

            verifyRoundtrip(tempDir, "bmp_sample.txt", sb.toString());
        }

        @Test
        @DisplayName("should preserve byte order mark if present")
        void shouldPreserveByteOrderMarkIfPresent(@TempDir final Path tempDir) throws Exception {
            final String withBom = "\uFEFFContent after BOM";
            verifyRoundtrip(tempDir, "with_bom.txt", withBom);
        }

        @Test
        @DisplayName("should handle normalization forms consistently")
        void shouldHandleNormalizationFormsConsistently(@TempDir final Path tempDir) throws Exception {
            // NFC (composed) vs NFD (decomposed)
            final String nfc = "\u00E9"; // é as single code point
            final String nfd = "e\u0301"; // e + combining acute

            verifyRoundtrip(tempDir, "nfc.txt", nfc);
            verifyRoundtrip(tempDir, "nfd.txt", nfd);

            // Verify they are preserved exactly (not normalized)
            final Path archive = tempDir.resolve("normalization.apack");
            try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
                writer.addEntry("nfc.txt", nfc.getBytes(StandardCharsets.UTF_8));
                writer.addEntry("nfd.txt", nfd.getBytes(StandardCharsets.UTF_8));
            }

            try (AetherPackReader reader = AetherPackReader.open(archive)) {
                // Bytes should be different (not normalized to same form)
                final byte[] nfcBytes = reader.readAllBytes("nfc.txt");
                final byte[] nfdBytes = reader.readAllBytes("nfd.txt");
                assertThat(nfcBytes).isNotEqualTo(nfdBytes);
            }
        }
    }

    // Helper methods

    private void verifyRoundtrip(final Path tempDir, final String entryName, final String content) throws Exception {
        final Path archive = tempDir.resolve("roundtrip_" + entryName.hashCode() + ".apack");
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
            writer.addEntry(entryName, bytes);
        }

        try (AetherPackReader reader = AetherPackReader.open(archive)) {
            final byte[] read = reader.readAllBytes(entryName);
            assertThat(read).isEqualTo(bytes);
            assertThat(new String(read, StandardCharsets.UTF_8)).isEqualTo(content);
        }
    }

    // Provider for parameterized tests
    static Stream<Arguments> languageTestCases() {
        return Stream.of(
                Arguments.of("german", GERMAN_PARAGRAPH),
                Arguments.of("english", ENGLISH_SIMPLE),
                Arguments.of("japanese", JAPANESE_FULL),
                Arguments.of("spanish", SPANISH_PARAGRAPH),
                Arguments.of("chinese", CHINESE_PARAGRAPH),
                Arguments.of("russian", RUSSIAN_PARAGRAPH),
                Arguments.of("emoji", EMOJI_TEXT),
                Arguments.of("mixed", MIXED_SCRIPTS)
        );
    }
}
