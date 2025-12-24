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

import de.splatgames.aether.pack.core.entry.Entry
import de.splatgames.aether.pack.core.format.EncryptionBlock
import de.splatgames.aether.pack.core.format.FileHeader
import java.nio.file.Path

/**
 * Represents the state of a currently opened archive.
 *
 * @param path Path to the archive file
 * @param fileHeader The archive file header
 * @param encryptionBlock Encryption block if the archive is encrypted
 * @param entries List of entries in the archive
 * @param selectedEntryIds Set of currently selected entry IDs
 */
data class ArchiveState(
    val path: Path,
    val fileHeader: FileHeader,
    val encryptionBlock: EncryptionBlock?,
    val entries: List<Entry>,
    val selectedEntryIds: Set<Long> = emptySet()
) {
    /**
     * Whether the archive is encrypted.
     */
    val isEncrypted: Boolean
        get() = fileHeader.isEncrypted

    /**
     * Whether the archive uses compression.
     */
    val isCompressed: Boolean
        get() = fileHeader.isCompressed

    /**
     * Whether random access is enabled.
     */
    val hasRandomAccess: Boolean
        get() = fileHeader.hasRandomAccess()

    /**
     * Whether this is a stream mode archive.
     */
    val isStreamMode: Boolean
        get() = fileHeader.isStreamMode

    /**
     * Total original size of all entries.
     */
    val totalOriginalSize: Long
        get() = entries.sumOf { it.originalSize }

    /**
     * Total stored size of all entries.
     */
    val totalStoredSize: Long
        get() = entries.sumOf { it.storedSize }

    /**
     * Overall compression ratio (0.0 to 1.0+).
     */
    val compressionRatio: Double
        get() = if (totalOriginalSize > 0) {
            totalStoredSize.toDouble() / totalOriginalSize
        } else {
            1.0
        }

    /**
     * Compression percentage saved (negative if expanded).
     */
    val savedPercentage: Double
        get() = (1.0 - compressionRatio) * 100.0

    /**
     * Number of compressed entries.
     */
    val compressedCount: Int
        get() = entries.count { it.isCompressed }

    /**
     * Number of encrypted entries.
     */
    val encryptedCount: Int
        get() = entries.count { it.isEncrypted }

    /**
     * Number of ECC-protected entries.
     */
    val eccCount: Int
        get() = entries.count { it.hasEcc() }

    /**
     * Currently selected entries.
     */
    val selectedEntries: List<Entry>
        get() = entries.filter { it.id in selectedEntryIds }

    /**
     * Archive file name.
     */
    val fileName: String
        get() = path.fileName.toString()

    /**
     * Format version string.
     */
    val formatVersion: String
        get() = "${fileHeader.versionMajor()}.${fileHeader.versionMinor()}.${fileHeader.versionPatch()}"

    /**
     * Create a copy with updated selection.
     */
    fun withSelection(selectedIds: Set<Long>): ArchiveState {
        return copy(selectedEntryIds = selectedIds)
    }

    /**
     * Toggle selection of a single entry.
     */
    fun toggleSelection(entryId: Long): ArchiveState {
        val newSelection = if (entryId in selectedEntryIds) {
            selectedEntryIds - entryId
        } else {
            selectedEntryIds + entryId
        }
        return copy(selectedEntryIds = newSelection)
    }

    /**
     * Select all entries.
     */
    fun selectAll(): ArchiveState {
        return copy(selectedEntryIds = entries.map { it.id }.toSet())
    }

    /**
     * Clear selection.
     */
    fun clearSelection(): ArchiveState {
        return copy(selectedEntryIds = emptySet())
    }
}
