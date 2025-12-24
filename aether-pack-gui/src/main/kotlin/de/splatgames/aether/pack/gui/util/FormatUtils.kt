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

package de.splatgames.aether.pack.gui.util

import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Utility functions for formatting values.
 */
object FormatUtils {
    private val sizeFormat = DecimalFormat("#,##0.#")
    private val percentFormat = DecimalFormat("0.0")
    private val dateFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    private const val KB = 1024L
    private const val MB = KB * 1024
    private const val GB = MB * 1024
    private const val TB = GB * 1024

    /**
     * Format a byte size to a human-readable string.
     *
     * @param bytes The size in bytes
     * @return Formatted string like "1.5 MB"
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= TB -> "${sizeFormat.format(bytes.toDouble() / TB)} TB"
            bytes >= GB -> "${sizeFormat.format(bytes.toDouble() / GB)} GB"
            bytes >= MB -> "${sizeFormat.format(bytes.toDouble() / MB)} MB"
            bytes >= KB -> "${sizeFormat.format(bytes.toDouble() / KB)} KB"
            else -> "$bytes B"
        }
    }

    /**
     * Format a compression ratio as percentage.
     *
     * @param ratio The compression ratio (0.0 to 1.0+)
     * @return Formatted string like "45.2%"
     */
    fun formatRatio(ratio: Double): String {
        return "${percentFormat.format(ratio * 100)}%"
    }

    /**
     * Format saved percentage (1 - ratio).
     *
     * @param ratio The compression ratio
     * @return Formatted string like "54.8% saved"
     */
    fun formatSaved(ratio: Double): String {
        val saved = (1.0 - ratio) * 100
        return if (saved >= 0) {
            "${percentFormat.format(saved)}% saved"
        } else {
            "${percentFormat.format(-saved)}% larger"
        }
    }

    /**
     * Format a timestamp as a localized date/time string.
     *
     * @param instant The instant to format
     * @return Formatted date/time string
     */
    fun formatDateTime(instant: Instant): String {
        return dateFormatter.format(instant)
    }

    /**
     * Format a timestamp (milliseconds since epoch) as a localized date/time string.
     *
     * @param timestamp Milliseconds since Unix epoch
     * @return Formatted date/time string
     */
    fun formatDateTime(timestamp: Long): String {
        return formatDateTime(Instant.ofEpochMilli(timestamp))
    }

    /**
     * Format a chunk size in KB or MB.
     *
     * @param bytes The chunk size in bytes
     * @return Formatted string like "256 KB" or "1 MB"
     */
    fun formatChunkSize(bytes: Int): String {
        return when {
            bytes >= MB -> "${bytes / MB} MB"
            else -> "${bytes / KB} KB"
        }
    }

    /**
     * Get the checksum algorithm name by ID.
     *
     * @param id The checksum algorithm ID
     * @return Algorithm name
     */
    fun getChecksumName(id: Int): String {
        return when (id) {
            0 -> "CRC-32"
            1 -> "XXH3-64"
            2 -> "XXH3-128"
            else -> "Unknown ($id)"
        }
    }

    /**
     * Get the compression algorithm name by ID.
     *
     * @param id The compression algorithm ID
     * @return Algorithm name
     */
    fun getCompressionName(id: Int): String {
        return when (id) {
            0 -> "None"
            1 -> "ZSTD"
            2 -> "LZ4"
            else -> "Unknown ($id)"
        }
    }

    /**
     * Get the encryption algorithm name by ID.
     *
     * @param id The encryption algorithm ID
     * @return Algorithm name
     */
    fun getEncryptionName(id: Int): String {
        return when (id) {
            0 -> "None"
            1 -> "AES-256-GCM"
            2 -> "ChaCha20-Poly1305"
            else -> "Unknown ($id)"
        }
    }
}
