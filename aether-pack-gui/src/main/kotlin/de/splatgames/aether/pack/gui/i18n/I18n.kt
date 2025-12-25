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

package de.splatgames.aether.pack.gui.i18n

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.MessageFormat
import java.util.*

/**
 * Internationalization manager for the Aether Pack GUI.
 *
 * Provides localized strings with support for message formatting.
 * Supports loading custom language files from user directory.
 */
class I18n private constructor(
    private val messages: Map<String, String>,
    val locale: Locale
) {
    /**
     * Get a localized message by key.
     *
     * @param key The message key
     * @return The localized message or "[key]" if not found
     */
    operator fun get(key: String): String = messages[key] ?: "[$key]"

    /**
     * Get a localized message with formatting arguments.
     *
     * @param key The message key
     * @param args The formatting arguments
     * @return The formatted localized message
     */
    fun format(key: String, vararg args: Any): String {
        val pattern = messages[key] ?: return "[$key]"
        return try {
            MessageFormat(pattern, locale).format(args)
        } catch (e: Exception) {
            pattern
        }
    }

    /**
     * Check if a message key exists.
     *
     * @param key The message key
     * @return true if the key exists
     */
    fun hasKey(key: String): Boolean = messages.containsKey(key)

    companion object {
        /**
         * Supported locales (bundled with the application).
         */
        val SUPPORTED_LOCALES = listOf(
            Locale.ENGLISH,
            Locale.GERMAN,
            Locale.JAPANESE
        )

        /**
         * Default locale.
         */
        val DEFAULT_LOCALE: Locale = Locale.ENGLISH

        /**
         * Path to custom language files in user home directory.
         */
        private val CUSTOM_LANG_DIR: Path = Path.of(
            System.getProperty("user.home"),
            ".aether-pack",
            "i18n"
        )

        /**
         * Load localization for the given locale.
         *
         * @param locale The locale to load
         * @return The I18n instance
         */
        fun load(locale: Locale): I18n {
            val resolvedLocale = resolveLocale(locale)
            val messages = loadMessages(resolvedLocale)
            return I18n(messages, resolvedLocale)
        }

        /**
         * Resolve the given locale to a supported one.
         */
        private fun resolveLocale(locale: Locale): Locale {
            return SUPPORTED_LOCALES.find { it.language == locale.language }
                ?: DEFAULT_LOCALE
        }

        /**
         * Load messages from resources and custom files.
         */
        private fun loadMessages(locale: Locale): Map<String, String> {
            val messages = mutableMapOf<String, String>()

            // Load from resources
            val suffix = if (locale == Locale.ENGLISH) "" else "_${locale.language}"
            val resourcePath = "/i18n/messages$suffix.properties"

            I18n::class.java.getResourceAsStream(resourcePath)?.use { stream ->
                val props = Properties()
                props.load(InputStreamReader(stream, StandardCharsets.UTF_8))
                props.forEach { key, value ->
                    messages[key.toString()] = value.toString()
                }
            }

            // Load custom language files (override bundled messages)
            loadCustomMessages(locale)?.let { customProps ->
                customProps.forEach { key, value ->
                    messages[key.toString()] = value.toString()
                }
            }

            return messages
        }

        /**
         * Load custom language file from user directory.
         */
        private fun loadCustomMessages(locale: Locale): Properties? {
            val customFile = CUSTOM_LANG_DIR.resolve("messages_${locale.language}.properties")

            if (Files.exists(customFile)) {
                return try {
                    Properties().apply {
                        Files.newBufferedReader(customFile, StandardCharsets.UTF_8).use { reader ->
                            load(reader)
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }

            return null
        }

        /**
         * Get available locales (including custom ones from user directory).
         */
        fun getAvailableLocales(): List<Locale> {
            val locales = SUPPORTED_LOCALES.toMutableList()

            // Check for custom language files
            if (Files.exists(CUSTOM_LANG_DIR)) {
                try {
                    Files.list(CUSTOM_LANG_DIR).use { stream ->
                        stream
                            .filter { it.fileName.toString().matches(Regex("messages_[a-z]{2}\\.properties")) }
                            .forEach { path ->
                                val lang = path.fileName.toString()
                                    .removePrefix("messages_")
                                    .removeSuffix(".properties")
                                val locale = Locale.forLanguageTag(lang)
                                if (!locales.any { it.language == locale.language }) {
                                    locales.add(locale)
                                }
                            }
                    }
                } catch (e: Exception) {
                    // Ignore errors reading custom directory
                }
            }

            return locales.sortedBy { it.displayLanguage }
        }
    }
}
