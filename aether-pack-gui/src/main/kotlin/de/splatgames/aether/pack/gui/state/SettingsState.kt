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

package de.splatgames.aether.pack.gui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Represents user settings for the application.
 *
 * Settings are persisted to the user's home directory.
 */
class SettingsState {
    /**
     * Whether to use system theme (true) or manual theme selection (false).
     */
    var useSystemTheme by mutableStateOf(true)

    /**
     * Dark theme preference (only used when useSystemTheme is false).
     */
    var isDarkTheme by mutableStateOf(false)

    /**
     * Current locale for the application.
     */
    var locale: Locale by mutableStateOf(Locale.getDefault())

    /**
     * Default output directory for extraction operations.
     */
    var defaultOutputDir: Path by mutableStateOf(Path.of(System.getProperty("user.home")))

    /**
     * Default compression algorithm.
     */
    var defaultCompression: String by mutableStateOf("zstd")

    /**
     * Default compression level.
     */
    var defaultCompressionLevel: Int by mutableStateOf(6)

    /**
     * Default chunk size in KB.
     */
    var defaultChunkSizeKb: Int by mutableStateOf(256)

    /**
     * Maximum number of recent files to remember.
     */
    var maxRecentFiles: Int by mutableStateOf(10)

    /**
     * Whether to show file extensions in entry names.
     */
    var showExtensions: Boolean by mutableStateOf(true)

    /**
     * Whether to confirm before overwriting files.
     */
    var confirmOverwrite: Boolean by mutableStateOf(true)

    companion object {
        private val SETTINGS_DIR: Path = Path.of(
            System.getProperty("user.home"),
            ".aether-pack"
        )
        private val SETTINGS_FILE: Path = SETTINGS_DIR.resolve("settings.properties")

        /**
         * Load settings from disk.
         */
        fun load(): SettingsState {
            val state = SettingsState()

            if (Files.exists(SETTINGS_FILE)) {
                try {
                    val props = Properties()
                    Files.newBufferedReader(SETTINGS_FILE).use { reader ->
                        props.load(reader)
                    }

                    props.getProperty("useSystemTheme")?.toBooleanStrictOrNull()?.let {
                        state.useSystemTheme = it
                    }
                    props.getProperty("isDarkTheme")?.toBooleanStrictOrNull()?.let {
                        state.isDarkTheme = it
                    }
                    props.getProperty("locale")?.let {
                        state.locale = Locale.forLanguageTag(it)
                    }
                    props.getProperty("defaultOutputDir")?.let {
                        val path = Path.of(it)
                        if (Files.isDirectory(path)) {
                            state.defaultOutputDir = path
                        }
                    }
                    props.getProperty("defaultCompression")?.let {
                        state.defaultCompression = it
                    }
                    props.getProperty("defaultCompressionLevel")?.toIntOrNull()?.let {
                        state.defaultCompressionLevel = it
                    }
                    props.getProperty("defaultChunkSizeKb")?.toIntOrNull()?.let {
                        state.defaultChunkSizeKb = it
                    }
                    props.getProperty("maxRecentFiles")?.toIntOrNull()?.let {
                        state.maxRecentFiles = it
                    }
                    props.getProperty("showExtensions")?.toBooleanStrictOrNull()?.let {
                        state.showExtensions = it
                    }
                    props.getProperty("confirmOverwrite")?.toBooleanStrictOrNull()?.let {
                        state.confirmOverwrite = it
                    }
                } catch (e: Exception) {
                    // Use defaults if loading fails
                }
            }

            return state
        }
    }

    /**
     * Save settings to disk.
     */
    fun save() {
        try {
            Files.createDirectories(SETTINGS_DIR)

            val props = Properties()
            props.setProperty("useSystemTheme", useSystemTheme.toString())
            props.setProperty("isDarkTheme", isDarkTheme.toString())
            props.setProperty("locale", locale.toLanguageTag())
            props.setProperty("defaultOutputDir", defaultOutputDir.toString())
            props.setProperty("defaultCompression", defaultCompression)
            props.setProperty("defaultCompressionLevel", defaultCompressionLevel.toString())
            props.setProperty("defaultChunkSizeKb", defaultChunkSizeKb.toString())
            props.setProperty("maxRecentFiles", maxRecentFiles.toString())
            props.setProperty("showExtensions", showExtensions.toString())
            props.setProperty("confirmOverwrite", confirmOverwrite.toString())

            Files.newBufferedWriter(SETTINGS_FILE).use { writer ->
                props.store(writer, "Aether Pack GUI Settings")
            }
        } catch (e: Exception) {
            // Ignore save errors
        }
    }
}
