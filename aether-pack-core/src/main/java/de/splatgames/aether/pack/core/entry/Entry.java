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

package de.splatgames.aether.pack.core.entry;

import de.splatgames.aether.pack.core.format.Attribute;
import de.splatgames.aether.pack.core.format.FormatConstants;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Represents an entry in an APACK archive.
 *
 * <p>An entry is a logical unit of data stored within an APACK container.
 * Each entry has metadata (name, MIME type, attributes) and content data
 * that may be compressed, encrypted, and protected with error correction.</p>
 *
 * <h2>Entry Properties</h2>
 * <ul>
 *   <li><strong>ID</strong> - Unique identifier within the container</li>
 *   <li><strong>Name</strong> - Human-readable name (max 65535 bytes UTF-8)</li>
 *   <li><strong>MIME Type</strong> - Content type hint (optional)</li>
 *   <li><strong>Sizes</strong> - Original and stored (after compression/encryption) sizes</li>
 *   <li><strong>Chunks</strong> - Number of data chunks for this entry</li>
 *   <li><strong>Attributes</strong> - Custom key-value metadata</li>
 * </ul>
 *
 * <h2>Processing Flags</h2>
 * <p>Entries can have various processing applied:</p>
 * <ul>
 *   <li><strong>Compression</strong> - ZSTD, LZ4, or other algorithms</li>
 *   <li><strong>Encryption</strong> - AES-256-GCM, ChaCha20-Poly1305</li>
 *   <li><strong>ECC</strong> - Reed-Solomon error correction</li>
 * </ul>
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link PackEntry} - Immutable entry read from an archive</li>
 *   <li>{@link EntryMetadata} - Mutable metadata for writing entries</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Reading entries from an archive
 * try (AetherPackReader reader = AetherPackReader.open(path)) {
 *     for (Entry entry : reader.getEntries()) {
 *         System.out.println(entry.getName() + " - " + entry.getOriginalSize() + " bytes");
 *
 *         if (entry.isCompressed()) {
 *             System.out.println("  Compression: " + entry.getCompressionId());
 *             double ratio = (double) entry.getStoredSize() / entry.getOriginalSize();
 *             System.out.printf("  Ratio: %.1f%%%n", ratio * 100);
 *         }
 *
 *         // Read custom attributes
 *         entry.getStringAttribute("author")
 *              .ifPresent(author -> System.out.println("  Author: " + author));
 *     }
 * }
 *
 * // Creating entry metadata for writing
 * EntryMetadata meta = EntryMetadata.builder()
 *     .name("document.pdf")
 *     .mimeType("application/pdf")
 *     .attribute("author", "John Doe")
 *     .attribute("version", 2L)
 *     .build();
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations should document their thread safety guarantees.
 * {@link PackEntry} is immutable and thread-safe; {@link EntryMetadata}
 * has mutable size fields updated during writing.</p>
 *
 * @see PackEntry
 * @see EntryMetadata
 * @see Attribute
 * @see de.splatgames.aether.pack.core.format.EntryHeader
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public interface Entry {

    /**
     * Returns the unique identifier of this entry within the archive.
     *
     * <p>The entry ID is a unique value assigned when the entry is created.
     * It can be used to identify entries independently of their name, which
     * is useful when entries are renamed or when names contain non-ASCII
     * characters that may cause issues in some systems.</p>
     *
     * <p>For entries being written ({@link EntryMetadata}), an ID of 0 indicates
     * that the writer will auto-assign an ID during the write process.</p>
     *
     * @return the unique entry identifier within the archive; always a
     *         non-negative value; 0 may indicate an unassigned ID for
     *         entries being written
     *
     * @see de.splatgames.aether.pack.core.format.TocEntry#entryId()
     */
    long getId();

    /**
     * Returns the name of this entry.
     *
     * <p>The entry name typically represents a relative path or filename within
     * the archive. Forward slashes ({@code /}) are used as path separators,
     * regardless of the operating system. Names are encoded as UTF-8 and can
     * contain any valid UTF-8 characters.</p>
     *
     * <p>Name constraints:</p>
     * <ul>
     *   <li>Maximum length: {@link FormatConstants#MAX_ENTRY_NAME_LENGTH} bytes (65535)</li>
     *   <li>Empty names are not allowed when writing</li>
     *   <li>Recommended: Use forward slashes for paths (portable)</li>
     * </ul>
     *
     * @return the entry name as a non-null, non-empty string; typically a
     *         relative path like "assets/config.json" or a simple filename
     *
     * @see FormatConstants#MAX_ENTRY_NAME_LENGTH
     */
    @NotNull String getName();

    /**
     * Returns the MIME type of this entry's content.
     *
     * <p>The MIME type provides a hint about the content type, helping
     * applications handle the entry appropriately without guessing based
     * on the file extension. Common examples include:</p>
     * <ul>
     *   <li>{@code "application/json"} - JSON data</li>
     *   <li>{@code "image/png"} - PNG image</li>
     *   <li>{@code "text/plain"} - Plain text</li>
     *   <li>{@code "application/octet-stream"} - Binary data</li>
     * </ul>
     *
     * <p>The MIME type is optional and may be an empty string if not specified
     * when the entry was created.</p>
     *
     * @return the MIME type string, or an empty string if no MIME type was
     *         specified; never {@code null}
     */
    @NotNull String getMimeType();

    /**
     * Returns the original (uncompressed) size of the entry content in bytes.
     *
     * <p>This is the size of the entry data before any compression or encryption
     * was applied. It represents the actual amount of data that will be available
     * when the entry is extracted.</p>
     *
     * <p>For entries being written ({@link EntryMetadata}), this value is 0
     * until the entry data has been fully written.</p>
     *
     * @return the original uncompressed size in bytes; always a non-negative
     *         value; 0 for empty entries or unwritten entries
     *
     * @see #getStoredSize()
     */
    long getOriginalSize();

    /**
     * Returns the stored (compressed and/or encrypted) size in bytes.
     *
     * <p>This is the size of the entry data as stored in the archive, after
     * compression and encryption have been applied. Comparing this to
     * {@link #getOriginalSize()} gives the compression ratio:</p>
     * <pre>{@code
     * double ratio = (double) entry.getStoredSize() / entry.getOriginalSize();
     * System.out.printf("Compressed to %.1f%% of original%n", ratio * 100);
     * }</pre>
     *
     * <p>For entries being written ({@link EntryMetadata}), this value is 0
     * until the entry data has been fully written.</p>
     *
     * @return the stored size in bytes including chunk overhead; always a
     *         non-negative value; may be larger than original size if
     *         compression was ineffective (for incompressible data)
     *
     * @see #getOriginalSize()
     */
    long getStoredSize();

    /**
     * Returns the number of data chunks this entry consists of.
     *
     * <p>Large entries are split into multiple chunks for processing. Each
     * chunk is independently compressed, encrypted, and checksummed, allowing
     * for:</p>
     * <ul>
     *   <li>Streaming processing without loading entire entries into memory</li>
     *   <li>Random access to specific parts of the entry</li>
     *   <li>Error isolation (corruption affects only specific chunks)</li>
     * </ul>
     *
     * <p>Chunk size is configured at archive creation time via
     * {@link de.splatgames.aether.pack.core.ApackConfiguration}.</p>
     *
     * @return the total number of data chunks; always a positive integer for
     *         valid entries; 0 for entries being written before any data is written
     *
     * @see de.splatgames.aether.pack.core.format.FormatConstants#DEFAULT_CHUNK_SIZE
     */
    int getChunkCount();

    /**
     * Checks whether this entry's data was compressed.
     *
     * <p>Compression reduces storage size for compressible data. When enabled,
     * the compression algorithm is applied during writing and the inverse
     * decompression is applied during reading.</p>
     *
     * <p>Note that per-chunk compression decisions may vary: if compression
     * would increase chunk size (for incompressible data), individual chunks
     * may be stored uncompressed even when compression is enabled.</p>
     *
     * @return {@code true} if compression was enabled for this entry;
     *         {@code false} if data is stored uncompressed
     *
     * @see #getCompressionId()
     */
    boolean isCompressed();

    /**
     * Checks whether this entry's data is encrypted.
     *
     * <p>When encryption is enabled, all chunk data is encrypted using the
     * configured algorithm and key. Decryption requires providing the correct
     * key when reading the entry.</p>
     *
     * @return {@code true} if this entry is encrypted; {@code false} if data
     *         is stored in plaintext
     *
     * @see #getEncryptionId()
     */
    boolean isEncrypted();

    /**
     * Checks whether this entry has error correction data.
     *
     * <p>When ECC (Error Correction Codes) is enabled, Reed-Solomon parity
     * data is stored alongside the chunk data, allowing detection and
     * correction of byte-level corruption.</p>
     *
     * @return {@code true} if Reed-Solomon error correction is present;
     *         {@code false} if no ECC data is stored
     *
     * @see de.splatgames.aether.pack.core.ecc.ReedSolomonCodec
     */
    boolean hasEcc();

    /**
     * Returns the compression algorithm identifier.
     *
     * <p>Known compression algorithm IDs:</p>
     * <ul>
     *   <li>{@link FormatConstants#COMPRESSION_NONE} (0) - No compression</li>
     *   <li>{@link FormatConstants#COMPRESSION_ZSTD} (1) - Zstandard</li>
     *   <li>{@link FormatConstants#COMPRESSION_LZ4} (2) - LZ4</li>
     * </ul>
     *
     * @return the compression algorithm ID; 0 if not compressed; use with
     *         compression provider registries to get the actual provider
     *
     * @see #isCompressed()
     * @see FormatConstants#COMPRESSION_NONE
     * @see FormatConstants#COMPRESSION_ZSTD
     */
    int getCompressionId();

    /**
     * Returns the encryption algorithm identifier.
     *
     * <p>Known encryption algorithm IDs:</p>
     * <ul>
     *   <li>{@link FormatConstants#ENCRYPTION_NONE} (0) - No encryption</li>
     *   <li>{@link FormatConstants#ENCRYPTION_AES_256_GCM} (1) - AES-256-GCM</li>
     *   <li>{@link FormatConstants#ENCRYPTION_CHACHA20_POLY1305} (2) - ChaCha20-Poly1305</li>
     * </ul>
     *
     * @return the encryption algorithm ID; 0 if not encrypted; use with
     *         encryption provider registries to get the actual provider
     *
     * @see #isEncrypted()
     * @see FormatConstants#ENCRYPTION_NONE
     * @see FormatConstants#ENCRYPTION_AES_256_GCM
     */
    int getEncryptionId();

    /**
     * Returns all custom attributes attached to this entry.
     *
     * <p>Attributes are key-value pairs that store additional metadata about
     * the entry. They can be used to store application-specific information
     * like author, version, creation date, permissions, etc.</p>
     *
     * <p>Supported attribute value types:</p>
     * <ul>
     *   <li>String - Text values</li>
     *   <li>Long - Integer values (64-bit)</li>
     *   <li>Boolean - True/false values</li>
     *   <li>Bytes - Raw binary data</li>
     * </ul>
     *
     * @return an unmodifiable list of all attributes; never {@code null};
     *         may be empty if no attributes were set
     *
     * @see Attribute
     * @see #getAttribute(String)
     */
    @NotNull List<Attribute> getAttributes();

    /**
     * Returns the attribute with the specified key.
     *
     * <p>This method searches the entry's attribute list for an attribute
     * matching the given key. Attribute keys are case-sensitive and must
     * match exactly. If multiple attributes with the same key exist (which
     * is not recommended), only the first one is returned.</p>
     *
     * <p>This method returns the raw {@link Attribute} object, which contains
     * both the value and type information. For convenience, use the typed
     * accessor methods like {@link #getStringAttribute(String)},
     * {@link #getLongAttribute(String)}, or {@link #getBooleanAttribute(String)}
     * to retrieve values of specific types directly.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Get raw attribute to inspect type
     * Optional<Attribute> attr = entry.getAttribute("version");
     * attr.ifPresent(a -> {
     *     System.out.println("Type: " + a.valueType());
     *     System.out.println("Key: " + a.key());
     * });
     *
     * // Check if attribute exists
     * if (entry.getAttribute("checksum").isPresent()) {
     *     // Process entry with checksum
     * }
     * }</pre>
     *
     * @param key the attribute key to search for; must not be {@code null};
     *            keys are case-sensitive and must match exactly; commonly
     *            used keys include "author", "version", "created", etc.
     * @return an {@link Optional} containing the attribute if found, or an
     *         empty {@link Optional} if no attribute with the specified key
     *         exists; never returns {@code null}
     *
     * @see #getAttributes()
     * @see #getStringAttribute(String)
     * @see #getLongAttribute(String)
     * @see #getBooleanAttribute(String)
     * @see Attribute
     */
    default @NotNull Optional<Attribute> getAttribute(final @NotNull String key) {
        return getAttributes().stream()
                .filter(attr -> attr.key().equals(key))
                .findFirst();
    }

    /**
     * Returns a string attribute value.
     *
     * <p>This convenience method retrieves a string-typed attribute value
     * directly. It first looks up the attribute by key, then verifies that
     * the attribute's type is {@link FormatConstants#ATTR_TYPE_STRING}
     * before returning the value. If the attribute doesn't exist or has
     * a different type, an empty {@link Optional} is returned.</p>
     *
     * <p>String attributes are commonly used for:</p>
     * <ul>
     *   <li>Textual metadata like author names, descriptions, comments</li>
     *   <li>File paths and identifiers</li>
     *   <li>Version strings and timestamps in human-readable format</li>
     *   <li>MIME types and content type hints</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Get author attribute with default value
     * String author = entry.getStringAttribute("author")
     *     .orElse("Unknown");
     *
     * // Process if attribute exists
     * entry.getStringAttribute("description")
     *     .ifPresent(desc -> System.out.println("Description: " + desc));
     *
     * // Check and use attribute
     * if (entry.getStringAttribute("license").isPresent()) {
     *     String license = entry.getStringAttribute("license").get();
     *     validateLicense(license);
     * }
     * }</pre>
     *
     * @param key the attribute key to search for; must not be {@code null};
     *            keys are case-sensitive and must match exactly
     * @return an {@link Optional} containing the string value if an attribute
     *         with the specified key exists and has type
     *         {@link FormatConstants#ATTR_TYPE_STRING}; an empty
     *         {@link Optional} if the attribute doesn't exist or has a
     *         different type (e.g., Long, Boolean, or Bytes); never
     *         returns {@code null}
     *
     * @see #getAttribute(String)
     * @see #getLongAttribute(String)
     * @see #getBooleanAttribute(String)
     * @see Attribute#asString()
     * @see FormatConstants#ATTR_TYPE_STRING
     */
    default @NotNull Optional<String> getStringAttribute(final @NotNull String key) {
        return getAttribute(key)
                .filter(attr -> attr.valueType() == de.splatgames.aether.pack.core.format.FormatConstants.ATTR_TYPE_STRING)
                .map(Attribute::asString);
    }

    /**
     * Returns a long attribute value.
     *
     * <p>This convenience method retrieves a 64-bit integer attribute value
     * directly. It first looks up the attribute by key, then verifies that
     * the attribute's type is {@link FormatConstants#ATTR_TYPE_INT64}
     * before returning the value. If the attribute doesn't exist or has
     * a different type, an empty {@link Optional} is returned.</p>
     *
     * <p>Long attributes are commonly used for:</p>
     * <ul>
     *   <li>Numeric version numbers and revision counters</li>
     *   <li>Timestamps as Unix epoch milliseconds or seconds</li>
     *   <li>File sizes, offsets, and other byte counts</li>
     *   <li>Numeric identifiers and hash values</li>
     *   <li>Permission flags and bit masks</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Get creation timestamp
     * long createdAt = entry.getLongAttribute("created")
     *     .orElse(0L);
     * Instant created = Instant.ofEpochMilli(createdAt);
     *
     * // Get version with validation
     * entry.getLongAttribute("version")
     *     .filter(v -> v >= 1)
     *     .ifPresent(version -> System.out.println("Version: " + version));
     *
     * // Calculate age based on timestamp attribute
     * entry.getLongAttribute("modified")
     *     .ifPresent(timestamp -> {
     *         long ageMs = System.currentTimeMillis() - timestamp;
     *         System.out.println("Age: " + Duration.ofMillis(ageMs));
     *     });
     * }</pre>
     *
     * @param key the attribute key to search for; must not be {@code null};
     *            keys are case-sensitive and must match exactly
     * @return an {@link Optional} containing the long value if an attribute
     *         with the specified key exists and has type
     *         {@link FormatConstants#ATTR_TYPE_INT64}; an empty
     *         {@link Optional} if the attribute doesn't exist or has a
     *         different type (e.g., String, Boolean, or Bytes); never
     *         returns {@code null}
     *
     * @see #getAttribute(String)
     * @see #getStringAttribute(String)
     * @see #getBooleanAttribute(String)
     * @see Attribute#asLong()
     * @see FormatConstants#ATTR_TYPE_INT64
     */
    default @NotNull Optional<Long> getLongAttribute(final @NotNull String key) {
        return getAttribute(key)
                .filter(attr -> attr.valueType() == de.splatgames.aether.pack.core.format.FormatConstants.ATTR_TYPE_INT64)
                .map(Attribute::asLong);
    }

    /**
     * Returns a boolean attribute value.
     *
     * <p>This convenience method retrieves a boolean attribute value
     * directly. It first looks up the attribute by key, then verifies that
     * the attribute's type is {@link FormatConstants#ATTR_TYPE_BOOLEAN}
     * before returning the value. If the attribute doesn't exist or has
     * a different type, an empty {@link Optional} is returned.</p>
     *
     * <p>Boolean attributes are commonly used for:</p>
     * <ul>
     *   <li>Feature flags and capability indicators</li>
     *   <li>Read-only or hidden file markers</li>
     *   <li>Validity and verification status</li>
     *   <li>Processing hints and optimization flags</li>
     *   <li>Optional feature enablement</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Check if entry is read-only with default
     * boolean readOnly = entry.getBooleanAttribute("readonly")
     *     .orElse(false);
     *
     * // Conditional processing based on flag
     * entry.getBooleanAttribute("verified")
     *     .filter(verified -> verified)
     *     .ifPresent(v -> System.out.println("Entry has been verified"));
     *
     * // Check hidden status
     * if (entry.getBooleanAttribute("hidden").orElse(false)) {
     *     // Skip hidden entries in listing
     *     return;
     * }
     * }</pre>
     *
     * @param key the attribute key to search for; must not be {@code null};
     *            keys are case-sensitive and must match exactly
     * @return an {@link Optional} containing the boolean value if an attribute
     *         with the specified key exists and has type
     *         {@link FormatConstants#ATTR_TYPE_BOOLEAN}; an empty
     *         {@link Optional} if the attribute doesn't exist or has a
     *         different type (e.g., String, Long, or Bytes); never
     *         returns {@code null}
     *
     * @see #getAttribute(String)
     * @see #getStringAttribute(String)
     * @see #getLongAttribute(String)
     * @see Attribute#asBoolean()
     * @see FormatConstants#ATTR_TYPE_BOOLEAN
     */
    default @NotNull Optional<Boolean> getBooleanAttribute(final @NotNull String key) {
        return getAttribute(key)
                .filter(attr -> attr.valueType() == de.splatgames.aether.pack.core.format.FormatConstants.ATTR_TYPE_BOOLEAN)
                .map(Attribute::asBoolean);
    }

}
