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

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Represents a recently opened archive file.
 *
 * @param path Path to the archive file
 * @param lastOpened Timestamp when the file was last opened
 * @param entryCount Number of entries in the archive (cached)
 * @param fileSize Size of the archive file in bytes (cached)
 */
data class RecentFile(
    val path: Path,
    val lastOpened: Instant,
    val entryCount: Int = 0,
    val fileSize: Long = 0
) {
    /**
     * File name without path.
     */
    val fileName: String
        get() = path.fileName.toString()

    /**
     * Parent directory path.
     */
    val directory: Path
        get() = path.parent ?: path

    /**
     * Whether the file still exists.
     */
    val exists: Boolean
        get() = Files.exists(path)

    /**
     * Create from a path with current timestamp.
     */
    companion object {
        fun of(path: Path, entryCount: Int = 0): RecentFile {
            val fileSize = try {
                Files.size(path)
            } catch (e: Exception) {
                0L
            }

            return RecentFile(
                path = path,
                lastOpened = Instant.now(),
                entryCount = entryCount,
                fileSize = fileSize
            )
        }
    }
}
