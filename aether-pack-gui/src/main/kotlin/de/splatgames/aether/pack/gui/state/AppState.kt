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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*

/**
 * Global application state.
 *
 * Contains all shared state for the Aether Pack GUI application.
 */
class AppState {
    /**
     * Application-level coroutine scope for background tasks.
     * Uses SupervisorJob so child failures don't cancel siblings.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * User settings.
     */
    val settings: SettingsState = SettingsState.load()

    /**
     * Currently opened archive, if any.
     */
    var archiveState by mutableStateOf<ArchiveState?>(null)

    /**
     * Background task state.
     */
    val taskState = TaskState()

    /**
     * Create Archive Wizard state (persistent across navigation).
     */
    val createWizardState = CreateWizardState(settings, applicationScope)

    /**
     * Recently opened files.
     */
    val recentFiles = mutableStateListOf<RecentFile>()

    init {
        loadRecentFiles()
    }

    /**
     * Add a file to the recent files list.
     *
     * @param path Path to the file
     * @param entryCount Number of entries in the archive
     */
    fun addRecentFile(path: Path, entryCount: Int = 0) {
        // Remove existing entry for this path
        recentFiles.removeAll { it.path == path }

        // Add to the beginning
        recentFiles.add(0, RecentFile.of(path, entryCount))

        // Trim to max size
        while (recentFiles.size > settings.maxRecentFiles) {
            recentFiles.removeLast()
        }

        // Save immediately
        saveRecentFiles()
    }

    /**
     * Remove a file from the recent files list.
     *
     * @param path Path to remove
     */
    fun removeRecentFile(path: Path) {
        recentFiles.removeAll { it.path == path }
        saveRecentFiles()
    }

    /**
     * Clear all recent files.
     */
    fun clearRecentFiles() {
        recentFiles.clear()
        saveRecentFiles()
    }

    /**
     * Load recent files from disk.
     */
    private fun loadRecentFiles() {
        val file = RECENT_FILES_FILE

        if (Files.exists(file)) {
            try {
                Files.readAllLines(file).forEach { line ->
                    val parts = line.split("|")
                    if (parts.size >= 2) {
                        val path = Path.of(parts[0])
                        val timestamp = Instant.parse(parts[1])
                        val entryCount = parts.getOrNull(2)?.toIntOrNull() ?: 0
                        val fileSize = parts.getOrNull(3)?.toLongOrNull() ?: 0L

                        if (Files.exists(path)) {
                            recentFiles.add(
                                RecentFile(path, timestamp, entryCount, fileSize)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore errors loading recent files
            }
        }
    }

    /**
     * Save recent files to disk.
     */
    private fun saveRecentFiles() {
        try {
            Files.createDirectories(SETTINGS_DIR)

            val lines = recentFiles.map { file ->
                "${file.path}|${file.lastOpened}|${file.entryCount}|${file.fileSize}"
            }

            Files.write(RECENT_FILES_FILE, lines)
        } catch (e: Exception) {
            // Ignore errors saving recent files
        }
    }

    /**
     * Save all settings.
     */
    fun saveSettings() {
        settings.save()
    }

    companion object {
        private val SETTINGS_DIR: Path = Path.of(
            System.getProperty("user.home"),
            ".aether-pack"
        )
        private val RECENT_FILES_FILE: Path = SETTINGS_DIR.resolve("recent_files.txt")
    }
}
