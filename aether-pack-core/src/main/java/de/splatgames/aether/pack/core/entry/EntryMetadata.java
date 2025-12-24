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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Mutable metadata for an entry being written to an APACK archive.
 *
 * <p>This class is used when creating new archive entries. It holds the
 * entry's metadata (name, MIME type, attributes, processing flags) and
 * tracks size information that is updated during the write process.</p>
 *
 * <h2>Immutable vs Mutable Fields</h2>
 * <p>The class has two categories of fields:</p>
 * <ul>
 *   <li><strong>Immutable</strong> - Set at construction: id, name, mimeType,
 *       attributes, compressionId, encryptionId, hasEcc</li>
 *   <li><strong>Mutable</strong> - Updated during writing: originalSize,
 *       storedSize, chunkCount (via setter methods)</li>
 * </ul>
 *
 * <h2>Creation</h2>
 * <p>Instances are created using the builder pattern or factory methods:</p>
 * <pre>{@code
 * // Simple entry with just a name
 * EntryMetadata simple = EntryMetadata.of("readme.txt");
 *
 * // Entry with MIME type
 * EntryMetadata withMime = EntryMetadata.of("image.png", "image/png");
 *
 * // Full builder usage
 * EntryMetadata full = EntryMetadata.builder()
 *     .id(1)
 *     .name("document.pdf")
 *     .mimeType("application/pdf")
 *     .attribute("author", "Jane Smith")
 *     .attribute("version", 3L)
 *     .attribute("draft", false)
 *     .compressionId(FormatConstants.COMPRESSION_ZSTD)
 *     .encryptionId(FormatConstants.ENCRYPTION_AES_256_GCM)
 *     .hasEcc(true)
 *     .build();
 * }</pre>
 *
 * <h2>Usage with AetherPackWriter</h2>
 * <pre>{@code
 * try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
 *     // Create metadata
 *     EntryMetadata meta = EntryMetadata.builder()
 *         .name("data/config.json")
 *         .mimeType("application/json")
 *         .attribute("schema-version", 2L)
 *         .build();
 *
 *     // Write entry content
 *     try (OutputStream out = writer.addEntry(meta)) {
 *         out.write(jsonBytes);
 *     }
 *     // After closing, meta.getOriginalSize() and meta.getStoredSize() are set
 *
 *     System.out.println("Written: " + meta.getOriginalSize() + " bytes");
 *     System.out.println("Stored: " + meta.getStoredSize() + " bytes");
 *     System.out.println("Chunks: " + meta.getChunkCount());
 * }
 * }</pre>
 *
 * <h2>Processing Flags</h2>
 * <p>The compression and encryption IDs determine how the entry data is processed:</p>
 * <table>
 *   <caption>Processing Algorithm IDs</caption>
 *   <tr><th>Type</th><th>ID</th><th>Constant</th></tr>
 *   <tr><td>No compression</td><td>0</td><td>{@link FormatConstants#COMPRESSION_NONE}</td></tr>
 *   <tr><td>ZSTD</td><td>1</td><td>{@link FormatConstants#COMPRESSION_ZSTD}</td></tr>
 *   <tr><td>LZ4</td><td>2</td><td>{@link FormatConstants#COMPRESSION_LZ4}</td></tr>
 *   <tr><td>No encryption</td><td>0</td><td>{@link FormatConstants#ENCRYPTION_NONE}</td></tr>
 *   <tr><td>AES-256-GCM</td><td>1</td><td>{@link FormatConstants#ENCRYPTION_AES_256_GCM}</td></tr>
 *   <tr><td>ChaCha20-Poly1305</td><td>2</td><td>{@link FormatConstants#ENCRYPTION_CHACHA20_POLY1305}</td></tr>
 * </table>
 *
 * <h2>Validation</h2>
 * <p>The builder enforces the following constraints:</p>
 * <ul>
 *   <li>Name is required (non-empty)</li>
 *   <li>Name length must not exceed {@link FormatConstants#MAX_ENTRY_NAME_LENGTH} bytes</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is <strong>NOT thread-safe</strong>. The mutable size fields
 * are updated by the writer during the write process. Access from multiple
 * threads requires external synchronization.</p>
 *
 * @see Entry
 * @see PackEntry
 * @see de.splatgames.aether.pack.core.AetherPackWriter
 * @see Attribute
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class EntryMetadata implements Entry {

    /** The unique entry identifier (0 for auto-assignment). */
    private final long id;

    /** The entry name (typically a relative path or filename). */
    private final @NotNull String name;

    /** The MIME type hint (empty string if not set). */
    private final @NotNull String mimeType;

    /** Immutable list of custom key-value attributes. */
    private final @NotNull List<Attribute> attributes;

    /** The compression algorithm ID (0 = none). */
    private final int compressionId;

    /** The encryption algorithm ID (0 = none). */
    private final int encryptionId;

    /** Whether Reed-Solomon error correction is enabled. */
    private final boolean hasEcc;

    // Mutable fields set during writing

    /** The original uncompressed size in bytes (set by writer). */
    private long originalSize;

    /** The stored size after processing in bytes (set by writer). */
    private long storedSize;

    /** The number of data chunks (set by writer). */
    private int chunkCount;

    /**
     * Private constructor called by the builder.
     *
     * <p>Creates an entry metadata instance with the specified immutable properties.
     * The mutable size fields (originalSize, storedSize, chunkCount) are initialized
     * to zero and will be set later by the writer.</p>
     *
     * @param id            the entry ID (0 for auto-assignment)
     * @param name          the entry name (required, non-empty)
     * @param mimeType      the MIME type (empty string if not set)
     * @param attributes    the list of custom attributes (will be copied)
     * @param compressionId the compression algorithm ID
     * @param encryptionId  the encryption algorithm ID
     * @param hasEcc        whether Reed-Solomon error correction is enabled
     */
    private EntryMetadata(
            final long id,
            final @NotNull String name,
            final @NotNull String mimeType,
            final @NotNull List<Attribute> attributes,
            final int compressionId,
            final int encryptionId,
            final boolean hasEcc) {

        this.id = id;
        this.name = name;
        this.mimeType = mimeType;
        this.attributes = List.copyOf(attributes);
        this.compressionId = compressionId;
        this.encryptionId = encryptionId;
        this.hasEcc = hasEcc;
        this.originalSize = 0;
        this.storedSize = 0;
        this.chunkCount = 0;
    }

    /**
     * Creates a new builder for constructing {@link EntryMetadata} instances.
     *
     * <p>The builder provides a fluent API for setting all entry properties.
     * Only the entry name is required; all other properties have sensible
     * defaults:</p>
     * <ul>
     *   <li><strong>id:</strong> 0 (auto-assigned by the writer)</li>
     *   <li><strong>mimeType:</strong> empty string (no MIME type)</li>
     *   <li><strong>attributes:</strong> empty list</li>
     *   <li><strong>compressionId:</strong> {@link FormatConstants#COMPRESSION_NONE}</li>
     *   <li><strong>encryptionId:</strong> {@link FormatConstants#ENCRYPTION_NONE}</li>
     *   <li><strong>hasEcc:</strong> false</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * EntryMetadata meta = EntryMetadata.builder()
     *     .name("documents/report.pdf")
     *     .mimeType("application/pdf")
     *     .attribute("author", "John Doe")
     *     .attribute("pages", 42L)
     *     .compressionId(FormatConstants.COMPRESSION_ZSTD)
     *     .build();
     * }</pre>
     *
     * @return a new builder instance initialized with default values; never
     *         {@code null}; the builder is mutable and should not be reused
     *         after calling {@link Builder#build()}
     *
     * @see Builder
     * @see #of(String)
     * @see #of(String, String)
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Creates a simple entry metadata with just a name.
     *
     * <p>This factory method is a convenience shortcut for creating metadata
     * with minimal configuration. It is equivalent to:</p>
     * <pre>{@code
     * EntryMetadata.builder().name(name).build()
     * }</pre>
     *
     * <p>The resulting metadata will have all default values:</p>
     * <ul>
     *   <li>No MIME type (empty string)</li>
     *   <li>No attributes</li>
     *   <li>No compression ({@link FormatConstants#COMPRESSION_NONE})</li>
     *   <li>No encryption ({@link FormatConstants#ENCRYPTION_NONE})</li>
     *   <li>No error correction (hasEcc = false)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Simple text file entry
     * EntryMetadata meta = EntryMetadata.of("readme.txt");
     *
     * // Use with writer
     * try (OutputStream out = writer.addEntry(meta)) {
     *     out.write(content.getBytes(StandardCharsets.UTF_8));
     * }
     * }</pre>
     *
     * @param name the entry name; must not be {@code null} or empty; typically
     *             a relative path like "data/config.json" or a simple filename;
     *             must not exceed {@link FormatConstants#MAX_ENTRY_NAME_LENGTH}
     *             bytes when encoded as UTF-8
     * @return a new entry metadata instance with the specified name and all
     *         other properties set to their default values; never {@code null}
     * @throws IllegalStateException if name is empty
     * @throws IllegalArgumentException if name exceeds maximum length
     *
     * @see #of(String, String)
     * @see #builder()
     */
    public static @NotNull EntryMetadata of(final @NotNull String name) {
        return builder().name(name).build();
    }

    /**
     * Creates entry metadata with a name and MIME type.
     *
     * <p>This factory method is a convenience shortcut for creating metadata
     * with a name and content type hint. It is equivalent to:</p>
     * <pre>{@code
     * EntryMetadata.builder().name(name).mimeType(mimeType).build()
     * }</pre>
     *
     * <p>The MIME type helps applications handle the entry content appropriately
     * without guessing based on file extensions. Common MIME types include:</p>
     * <ul>
     *   <li>{@code "application/json"} - JSON data</li>
     *   <li>{@code "application/pdf"} - PDF documents</li>
     *   <li>{@code "image/png"} - PNG images</li>
     *   <li>{@code "text/plain"} - Plain text</li>
     *   <li>{@code "application/octet-stream"} - Generic binary data</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * // Image entry with MIME type
     * EntryMetadata meta = EntryMetadata.of("images/logo.png", "image/png");
     *
     * // JSON configuration
     * EntryMetadata config = EntryMetadata.of("config.json", "application/json");
     * }</pre>
     *
     * @param name     the entry name; must not be {@code null} or empty; typically
     *                 a relative path or filename; must not exceed
     *                 {@link FormatConstants#MAX_ENTRY_NAME_LENGTH} bytes UTF-8
     * @param mimeType the MIME type string indicating the content type; must not
     *                 be {@code null}; can be empty if MIME type is unknown
     * @return a new entry metadata instance with the specified name and MIME type,
     *         and all other properties set to their default values; never {@code null}
     * @throws IllegalStateException if name is empty
     * @throws IllegalArgumentException if name exceeds maximum length
     *
     * @see #of(String)
     * @see #builder()
     */
    public static @NotNull EntryMetadata of(final @NotNull String name, final @NotNull String mimeType) {
        return builder().name(name).mimeType(mimeType).build();
    }

    /**
     * {@inheritDoc}
     *
     * @return the entry ID, or 0 if not assigned yet
     */
    @Override
    public long getId() {
        return this.id;
    }

    /**
     * {@inheritDoc}
     *
     * @return the entry name
     */
    @Override
    public @NotNull String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     *
     * @return the MIME type, or empty string if not set
     */
    @Override
    public @NotNull String getMimeType() {
        return this.mimeType;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This value is 0 until set by the writer after entry data has been written.</p>
     *
     * @return the original (uncompressed) size in bytes
     */
    @Override
    public long getOriginalSize() {
        return this.originalSize;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This value is 0 until set by the writer after entry data has been written.</p>
     *
     * @return the stored (compressed/encrypted) size in bytes
     */
    @Override
    public long getStoredSize() {
        return this.storedSize;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This value is 0 until set by the writer after entry data has been written.</p>
     *
     * @return the number of chunks
     */
    @Override
    public int getChunkCount() {
        return this.chunkCount;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code true} if compressionId is not {@link FormatConstants#COMPRESSION_NONE}
     */
    @Override
    public boolean isCompressed() {
        return this.compressionId != FormatConstants.COMPRESSION_NONE;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code true} if encryptionId is not {@link FormatConstants#ENCRYPTION_NONE}
     */
    @Override
    public boolean isEncrypted() {
        return this.encryptionId != FormatConstants.ENCRYPTION_NONE;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code true} if Reed-Solomon error correction is enabled
     */
    @Override
    public boolean hasEcc() {
        return this.hasEcc;
    }

    /**
     * {@inheritDoc}
     *
     * @return the compression algorithm ID
     */
    @Override
    public int getCompressionId() {
        return this.compressionId;
    }

    /**
     * {@inheritDoc}
     *
     * @return the encryption algorithm ID
     */
    @Override
    public int getEncryptionId() {
        return this.encryptionId;
    }

    /**
     * {@inheritDoc}
     *
     * @return an unmodifiable list of custom attributes
     */
    @Override
    public @NotNull List<Attribute> getAttributes() {
        return this.attributes;
    }

    /**
     * Sets the original (uncompressed) size of the entry content.
     *
     * <p><strong>Internal use only.</strong> This method is called by the
     * {@link de.splatgames.aether.pack.core.AetherPackWriter} after all entry
     * data has been written. It records the total number of uncompressed bytes
     * that were written to the entry.</p>
     *
     * <p>The original size is important for:</p>
     * <ul>
     *   <li>Allocating buffers for decompression during reading</li>
     *   <li>Calculating compression ratios</li>
     *   <li>Progress reporting during extraction</li>
     *   <li>Storage in the entry header for archive metadata</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <p>This method is not thread-safe. It should only be called by the
     * writer thread that is actively writing the entry.</p>
     *
     * @param originalSize the original (uncompressed) size in bytes; must be
     *                     a non-negative value; represents the total number
     *                     of bytes written to the entry stream before any
     *                     compression or encryption is applied
     *
     * @see #getOriginalSize()
     * @see #setStoredSize(long)
     * @see de.splatgames.aether.pack.core.AetherPackWriter
     */
    public void setOriginalSize(final long originalSize) {
        this.originalSize = originalSize;
    }

    /**
     * Sets the stored (compressed/encrypted) size of the entry content.
     *
     * <p><strong>Internal use only.</strong> This method is called by the
     * {@link de.splatgames.aether.pack.core.AetherPackWriter} after all entry
     * data has been written. It records the total number of bytes actually
     * stored in the archive, including any overhead from chunk headers,
     * compression, encryption, and error correction.</p>
     *
     * <p>The stored size may be:</p>
     * <ul>
     *   <li><strong>Smaller than original</strong> - If compression was effective</li>
     *   <li><strong>Larger than original</strong> - If data is incompressible or
     *       encryption/ECC overhead exceeds compression savings</li>
     *   <li><strong>Equal to original</strong> - For uncompressed, unencrypted entries</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <p>The compression ratio can be calculated as:</p>
     * <pre>{@code
     * double ratio = (double) meta.getStoredSize() / meta.getOriginalSize();
     * System.out.printf("Compressed to %.1f%% of original%n", ratio * 100);
     * }</pre>
     *
     * <p><strong>\:</strong></p>
     * <p>This method is not thread-safe. It should only be called by the
     * writer thread that is actively writing the entry.</p>
     *
     * @param storedSize the stored size in bytes including all overhead; must
     *                   be a non-negative value; represents the total bytes
     *                   written to the archive file for this entry's chunks
     *
     * @see #getStoredSize()
     * @see #setOriginalSize(long)
     * @see de.splatgames.aether.pack.core.AetherPackWriter
     */
    public void setStoredSize(final long storedSize) {
        this.storedSize = storedSize;
    }

    /**
     * Sets the number of data chunks for this entry.
     *
     * <p><strong>Internal use only.</strong> This method is called by the
     * {@link de.splatgames.aether.pack.core.AetherPackWriter} after all entry
     * data has been written. It records the total number of chunks that were
     * created during the writing process.</p>
     *
     * <p>The chunk count depends on:</p>
     * <ul>
     *   <li><strong>Original size</strong> - Larger entries have more chunks</li>
     *   <li><strong>Chunk size configuration</strong> - Configured via
     *       {@link de.splatgames.aether.pack.core.ApackConfiguration}</li>
     * </ul>
     *
     * <p>The relationship is approximately:</p>
     * <pre>
     * chunkCount ≈ ceil(originalSize / chunkSize)
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <p>Each chunk is processed independently, allowing:</p>
     * <ul>
     *   <li>Streaming without loading entire entry into memory</li>
     *   <li>Random access to specific parts of the entry</li>
     *   <li>Error isolation (corruption affects only specific chunks)</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <p>This method is not thread-safe. It should only be called by the
     * writer thread that is actively writing the entry.</p>
     *
     * @param chunkCount the total number of data chunks for this entry; must
     *                   be a non-negative value; 0 for empty entries, positive
     *                   for entries with data
     *
     * @see #getChunkCount()
     * @see de.splatgames.aether.pack.core.ApackConfiguration#chunkSize()
     * @see de.splatgames.aether.pack.core.format.FormatConstants#DEFAULT_CHUNK_SIZE
     */
    public void setChunkCount(final int chunkCount) {
        this.chunkCount = chunkCount;
    }

    /**
     * Builder for {@link EntryMetadata} instances.
     *
     * <p>Provides a fluent API for constructing entry metadata with all
     * necessary properties. The only required field is the entry name;
     * all other fields have sensible defaults.</p>
     *
     * <p><strong>\:</strong></p>
     * <ul>
     *   <li>id: 0 (auto-assigned by writer)</li>
     *   <li>mimeType: "" (empty string)</li>
     *   <li>attributes: empty list</li>
     *   <li>compressionId: {@link FormatConstants#COMPRESSION_NONE}</li>
     *   <li>encryptionId: {@link FormatConstants#ENCRYPTION_NONE}</li>
     *   <li>hasEcc: false</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * EntryMetadata meta = EntryMetadata.builder()
     *     .name("assets/logo.svg")
     *     .mimeType("image/svg+xml")
     *     .attribute("designer", "Acme Corp")
     *     .build();
     * }</pre>
     *
     * @author Erik Pförtner
     * @since 0.1.0
     */
    public static final class Builder {

        /** Entry ID (0 = auto-assign). */
        private long id = 0;

        /** Entry name (required, must be non-empty). */
        private @NotNull String name = "";

        /** MIME type hint (empty string if not set). */
        private @NotNull String mimeType = "";

        /** Mutable list of custom attributes. */
        private @NotNull List<Attribute> attributes = new ArrayList<>();

        /** Compression algorithm ID (default: none). */
        private int compressionId = FormatConstants.COMPRESSION_NONE;

        /** Encryption algorithm ID (default: none). */
        private int encryptionId = FormatConstants.ENCRYPTION_NONE;

        /** Whether to enable Reed-Solomon error correction (default: false). */
        private boolean hasEcc = false;

        /**
         * Private constructor to enforce use of {@link EntryMetadata#builder()}.
         */
        private Builder() {
        }

        /**
         * Sets the unique entry ID for this entry.
         *
         * <p>The entry ID is a unique identifier that can be used to reference
         * entries independently of their names. This is useful when entry names
         * may change or when a stable numeric identifier is needed for indexing
         * or cross-referencing.</p>
         *
         * <p>If not explicitly set (default is 0), the writer will automatically
         * assign a unique ID during the write process. Auto-assigned IDs are
         * guaranteed to be unique within the archive and are typically assigned
         * sequentially starting from 1.</p>
         *
         * <p><strong>\:</strong></p>
         * <ul>
         *   <li>When migrating entries between archives and preserving references</li>
         *   <li>When external systems reference entries by ID</li>
         *   <li>When implementing custom entry ordering or grouping</li>
         * </ul>
         *
         * @param id the entry ID; typically a positive value; 0 means the
         *           writer will auto-assign an ID; negative values are allowed
         *           but not recommended
         * @return this builder instance for fluent method chaining; never
         *         {@code null}
         *
         * @see EntryMetadata#getId()
         */
        public @NotNull Builder id(final long id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the entry name (required field).
         *
         * <p>The entry name is the primary identifier for the entry within
         * the archive. It typically represents a relative path or filename
         * and is used to locate entries during extraction.</p>
         *
         * <p>Naming conventions:</p>
         * <ul>
         *   <li>Use forward slashes ({@code /}) as path separators for portability</li>
         *   <li>Avoid leading slashes (prefer "data/file.txt" over "/data/file.txt")</li>
         *   <li>Names are case-sensitive on most platforms</li>
         *   <li>UTF-8 characters are fully supported</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <p>The name is validated when {@link #build()} is called:</p>
         * <ul>
         *   <li>Must not be empty</li>
         *   <li>Must not exceed {@link FormatConstants#MAX_ENTRY_NAME_LENGTH} bytes
         *       when encoded as UTF-8</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * builder.name("config.json");              // Simple filename
         * builder.name("assets/images/logo.png");   // Path with directories
         * builder.name("documents/résumé.pdf");     // UTF-8 characters
         * }</pre>
         *
         * @param name the entry name; must not be {@code null} or empty; must
         *             not exceed {@link FormatConstants#MAX_ENTRY_NAME_LENGTH}
         *             bytes when encoded as UTF-8
         * @return this builder instance for fluent method chaining; never
         *         {@code null}
         *
         * @see EntryMetadata#getName()
         * @see FormatConstants#MAX_ENTRY_NAME_LENGTH
         */
        public @NotNull Builder name(final @NotNull String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the MIME type for the entry content.
         *
         * <p>The MIME type provides a hint about the content type, helping
         * applications handle the entry appropriately without guessing based
         * on the file extension. This is optional metadata; if not set, an
         * empty string is stored.</p>
         *
         * <p><strong>\:</strong></p>
         * <table>
         *   <caption>Frequently Used MIME Types</caption>
         *   <tr><th>Type</th><th>Description</th></tr>
         *   <tr><td>{@code application/json}</td><td>JSON data</td></tr>
         *   <tr><td>{@code application/pdf}</td><td>PDF documents</td></tr>
         *   <tr><td>{@code text/plain}</td><td>Plain text files</td></tr>
         *   <tr><td>{@code image/png}</td><td>PNG images</td></tr>
         *   <tr><td>{@code image/jpeg}</td><td>JPEG images</td></tr>
         *   <tr><td>{@code application/octet-stream}</td><td>Generic binary</td></tr>
         * </table>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * builder.name("data.json")
         *        .mimeType("application/json");
         * }</pre>
         *
         * @param mimeType the MIME type string; must not be {@code null}; can
         *                 be empty if the content type is unknown or not
         *                 applicable
         * @return this builder instance for fluent method chaining; never
         *         {@code null}
         *
         * @see EntryMetadata#getMimeType()
         */
        public @NotNull Builder mimeType(final @NotNull String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        /**
         * Adds a pre-constructed attribute to the entry.
         *
         * <p>This method allows adding an {@link Attribute} object that was
         * created using the {@code Attribute} factory methods. It provides
         * full control over the attribute type and value.</p>
         *
         * <p>For convenience, use the overloaded {@code attribute()} methods
         * that accept key-value pairs directly for common types (String, long,
         * boolean).</p>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * // Add binary attribute
         * byte[] hash = computeHash(data);
         * Attribute hashAttr = Attribute.ofBytes("checksum", hash);
         * builder.attribute(hashAttr);
         *
         * // Add string attribute using factory
         * builder.attribute(Attribute.ofString("format", "v2"));
         * }</pre>
         *
         * @param attribute the attribute to add; must not be {@code null};
         *                  duplicate keys are allowed but not recommended
         * @return this builder instance for fluent method chaining; never
         *         {@code null}
         *
         * @see #attribute(String, String)
         * @see #attribute(String, long)
         * @see #attribute(String, boolean)
         * @see Attribute
         */
        public @NotNull Builder attribute(final @NotNull Attribute attribute) {
            this.attributes.add(attribute);
            return this;
        }

        /**
         * Adds a string attribute to the entry.
         *
         * <p>This convenience method creates a string-typed attribute and adds
         * it to the entry's attribute list. It is equivalent to:</p>
         * <pre>{@code
         * builder.attribute(Attribute.ofString(key, value))
         * }</pre>
         *
         * <p>String attributes are commonly used for:</p>
         * <ul>
         *   <li>Author and creator information</li>
         *   <li>Descriptions and comments</li>
         *   <li>Version strings</li>
         *   <li>Identifiers and references</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * builder.attribute("author", "Jane Doe")
         *        .attribute("version", "1.0.0")
         *        .attribute("description", "Configuration file");
         * }</pre>
         *
         * @param key   the attribute key; must not be {@code null}; keys are
         *              case-sensitive; duplicate keys are allowed but not
         *              recommended
         * @param value the string value; must not be {@code null}; can be empty
         * @return this builder instance for fluent method chaining; never
         *         {@code null}
         *
         * @see #attribute(String, long)
         * @see #attribute(String, boolean)
         * @see Entry#getStringAttribute(String)
         */
        public @NotNull Builder attribute(final @NotNull String key, final @NotNull String value) {
            this.attributes.add(Attribute.ofString(key, value));
            return this;
        }

        /**
         * Adds a long (64-bit integer) attribute to the entry.
         *
         * <p>This convenience method creates a long-typed attribute and adds
         * it to the entry's attribute list. It is equivalent to:</p>
         * <pre>{@code
         * builder.attribute(Attribute.ofLong(key, value))
         * }</pre>
         *
         * <p>Long attributes are commonly used for:</p>
         * <ul>
         *   <li>Timestamps (Unix epoch milliseconds)</li>
         *   <li>Numeric version numbers</li>
         *   <li>File sizes and offsets</li>
         *   <li>Counters and identifiers</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * builder.attribute("created", System.currentTimeMillis())
         *        .attribute("version", 3L)
         *        .attribute("priority", 100L);
         * }</pre>
         *
         * @param key   the attribute key; must not be {@code null}; keys are
         *              case-sensitive; duplicate keys are allowed but not
         *              recommended
         * @param value the long value; can be any 64-bit integer value
         * @return this builder instance for fluent method chaining; never
         *         {@code null}
         *
         * @see #attribute(String, String)
         * @see #attribute(String, boolean)
         * @see Entry#getLongAttribute(String)
         */
        public @NotNull Builder attribute(final @NotNull String key, final long value) {
            this.attributes.add(Attribute.ofLong(key, value));
            return this;
        }

        /**
         * Adds a boolean attribute to the entry.
         *
         * <p>This convenience method creates a boolean-typed attribute and adds
         * it to the entry's attribute list. It is equivalent to:</p>
         * <pre>{@code
         * builder.attribute(Attribute.ofBoolean(key, value))
         * }</pre>
         *
         * <p>Boolean attributes are commonly used for:</p>
         * <ul>
         *   <li>Feature flags and toggles</li>
         *   <li>Verification status indicators</li>
         *   <li>Read-only or hidden markers</li>
         *   <li>Processing hints</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * builder.attribute("verified", true)
         *        .attribute("hidden", false)
         *        .attribute("readonly", true);
         * }</pre>
         *
         * @param key   the attribute key; must not be {@code null}; keys are
         *              case-sensitive; duplicate keys are allowed but not
         *              recommended
         * @param value the boolean value; {@code true} or {@code false}
         * @return this builder instance for fluent method chaining; never
         *         {@code null}
         *
         * @see #attribute(String, String)
         * @see #attribute(String, long)
         * @see Entry#getBooleanAttribute(String)
         */
        public @NotNull Builder attribute(final @NotNull String key, final boolean value) {
            this.attributes.add(Attribute.ofBoolean(key, value));
            return this;
        }

        /**
         * Sets all attributes, replacing any previously added attributes.
         *
         * <p>This method replaces the entire attribute list with the provided
         * list. Any attributes previously added via {@link #attribute} methods
         * will be discarded.</p>
         *
         * <p>The provided list is copied defensively; modifications to the
         * original list after calling this method will not affect the builder.</p>
         *
         * <p><strong>\:</strong></p>
         * <ul>
         *   <li>Copying attributes from an existing entry</li>
         *   <li>Setting a pre-built attribute collection</li>
         *   <li>Resetting attributes after previous configuration</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * // Copy attributes from existing entry
         * Entry existing = reader.getEntry("template.txt").orElseThrow();
         * builder.attributes(existing.getAttributes());
         *
         * // Set from pre-built list
         * List<Attribute> attrs = List.of(
         *     Attribute.ofString("author", "System"),
         *     Attribute.ofLong("version", 1L)
         * );
         * builder.attributes(attrs);
         * }</pre>
         *
         * @param attributes the list of attributes to set; must not be
         *                   {@code null}; the list is copied; can be empty
         *                   to clear all attributes
         * @return this builder instance for fluent method chaining; never
         *         {@code null}
         *
         * @see #attribute(Attribute)
         * @see Entry#getAttributes()
         */
        public @NotNull Builder attributes(final @NotNull List<Attribute> attributes) {
            this.attributes = new ArrayList<>(attributes);
            return this;
        }

        /**
         * Sets the compression algorithm ID for this entry.
         *
         * <p>The compression ID determines which compression algorithm will
         * be used to compress the entry's data chunks. Setting this to a
         * non-zero value enables compression; the actual compression is
         * performed by the writer using the appropriate provider.</p>
         *
         * <p><strong>\:</strong></p>
         * <table>
         *   <caption>Compression Algorithm Constants</caption>
         *   <tr><th>ID</th><th>Constant</th><th>Description</th></tr>
         *   <tr><td>0</td><td>{@link FormatConstants#COMPRESSION_NONE}</td><td>No compression</td></tr>
         *   <tr><td>1</td><td>{@link FormatConstants#COMPRESSION_ZSTD}</td><td>Zstandard (recommended)</td></tr>
         *   <tr><td>2</td><td>{@link FormatConstants#COMPRESSION_LZ4}</td><td>LZ4 (faster, less ratio)</td></tr>
         * </table>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * // Enable ZSTD compression
         * builder.compressionId(FormatConstants.COMPRESSION_ZSTD);
         *
         * // Disable compression
         * builder.compressionId(FormatConstants.COMPRESSION_NONE);
         * }</pre>
         *
         * @param compressionId the compression algorithm ID; 0 for no compression;
         *                      use constants from {@link FormatConstants}; the
         *                      writer must have a matching compression provider
         *                      registered
         * @return this builder instance for fluent method chaining; never
         *         {@code null}
         *
         * @see EntryMetadata#getCompressionId()
         * @see EntryMetadata#isCompressed()
         * @see FormatConstants#COMPRESSION_ZSTD
         * @see FormatConstants#COMPRESSION_LZ4
         */
        public @NotNull Builder compressionId(final int compressionId) {
            this.compressionId = compressionId;
            return this;
        }

        /**
         * Sets the encryption algorithm ID for this entry.
         *
         * <p>The encryption ID determines which encryption algorithm will
         * be used to encrypt the entry's data chunks. Setting this to a
         * non-zero value enables encryption; the actual encryption is
         * performed by the writer using the appropriate provider and key.</p>
         *
         * <p><strong>\:</strong></p>
         * <table>
         *   <caption>Encryption Algorithm Constants</caption>
         *   <tr><th>ID</th><th>Constant</th><th>Description</th></tr>
         *   <tr><td>0</td><td>{@link FormatConstants#ENCRYPTION_NONE}</td><td>No encryption</td></tr>
         *   <tr><td>1</td><td>{@link FormatConstants#ENCRYPTION_AES_256_GCM}</td><td>AES-256 in GCM mode</td></tr>
         *   <tr><td>2</td><td>{@link FormatConstants#ENCRYPTION_CHACHA20_POLY1305}</td><td>ChaCha20-Poly1305</td></tr>
         * </table>
         *
         * <p><strong>\:</strong></p>
         * <p>When encryption is enabled, the writer must be configured with
         * an appropriate encryption key. The key is typically derived from
         * a password using a KDF like Argon2id or PBKDF2.</p>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * // Enable AES-256-GCM encryption
         * builder.encryptionId(FormatConstants.ENCRYPTION_AES_256_GCM);
         *
         * // Enable ChaCha20-Poly1305 (better for systems without AES-NI)
         * builder.encryptionId(FormatConstants.ENCRYPTION_CHACHA20_POLY1305);
         * }</pre>
         *
         * @param encryptionId the encryption algorithm ID; 0 for no encryption;
         *                     use constants from {@link FormatConstants}; the
         *                     writer must have a matching encryption provider
         *                     registered and be configured with a key
         * @return this builder instance for fluent method chaining; never
         *         {@code null}
         *
         * @see EntryMetadata#getEncryptionId()
         * @see EntryMetadata#isEncrypted()
         * @see FormatConstants#ENCRYPTION_AES_256_GCM
         * @see FormatConstants#ENCRYPTION_CHACHA20_POLY1305
         */
        public @NotNull Builder encryptionId(final int encryptionId) {
            this.encryptionId = encryptionId;
            return this;
        }

        /**
         * Enables or disables Reed-Solomon error correction for this entry.
         *
         * <p>When enabled, Reed-Solomon parity data is computed and stored
         * alongside each data chunk, allowing detection and correction of
         * byte-level corruption. This provides resilience against storage
         * media degradation, transmission errors, and bit flips.</p>
         *
         * <p><strong>\:</strong></p>
         * <ul>
         *   <li><strong>Detection</strong> - Identifies corrupted chunks</li>
         *   <li><strong>Correction</strong> - Recovers original data without backup</li>
         *   <li><strong>Verification</strong> - Validates data integrity during read</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <p>ECC adds storage overhead depending on the parity configuration.
         * The default configuration adds approximately 6% overhead and can
         * correct up to 8 byte errors per ECC block.</p>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * // Enable ECC for archival data
         * builder.hasEcc(true);
         *
         * // Disable ECC for frequently updated data
         * builder.hasEcc(false);
         * }</pre>
         *
         * @param hasEcc {@code true} to enable Reed-Solomon error correction;
         *               {@code false} to disable (default)
         * @return this builder instance for fluent method chaining; never
         *         {@code null}
         *
         * @see EntryMetadata#hasEcc()
         * @see de.splatgames.aether.pack.core.ecc.ReedSolomonCodec
         * @see de.splatgames.aether.pack.core.ecc.EccConfiguration
         */
        public @NotNull Builder hasEcc(final boolean hasEcc) {
            this.hasEcc = hasEcc;
            return this;
        }

        /**
         * Builds and returns a new immutable {@link EntryMetadata} instance.
         *
         * <p>This method validates all configured values and constructs a new
         * {@link EntryMetadata} object. The resulting metadata has its mutable
         * size fields (originalSize, storedSize, chunkCount) initialized to 0;
         * these will be set by the writer during the write process.</p>
         *
         * <p><strong>\:</strong></p>
         * <ul>
         *   <li>Entry name must be set (non-empty)</li>
         *   <li>Entry name must not exceed {@link FormatConstants#MAX_ENTRY_NAME_LENGTH}
         *       bytes when encoded as UTF-8</li>
         * </ul>
         *
         * <p><strong>\:</strong></p>
         * <p>The builder can technically be reused after calling {@code build()},
         * but this is not recommended. Create a new builder for each metadata
         * instance to avoid accidental state sharing.</p>
         *
         * <p><strong>\:</strong></p>
         * <pre>{@code
         * EntryMetadata meta = EntryMetadata.builder()
         *     .name("document.pdf")
         *     .mimeType("application/pdf")
         *     .attribute("author", "John Doe")
         *     .compressionId(FormatConstants.COMPRESSION_ZSTD)
         *     .build();  // Validates and creates the metadata
         *
         * // Use with writer
         * try (OutputStream out = writer.addEntry(meta)) {
         *     out.write(pdfContent);
         * }
         * }</pre>
         *
         * @return a new {@link EntryMetadata} instance with all configured
         *         properties; never {@code null}; the returned metadata is
         *         independent of this builder
         * @throws IllegalStateException if the entry name is not set (empty)
         * @throws IllegalArgumentException if the entry name exceeds the
         *                                  maximum allowed length
         *
         * @see EntryMetadata
         * @see FormatConstants#MAX_ENTRY_NAME_LENGTH
         */
        public @NotNull EntryMetadata build() {
            if (this.name.isEmpty()) {
                throw new IllegalStateException("Entry name is required");
            }
            if (this.name.length() > FormatConstants.MAX_ENTRY_NAME_LENGTH) {
                throw new IllegalArgumentException("Entry name too long: " + this.name.length());
            }
            return new EntryMetadata(
                    this.id,
                    this.name,
                    this.mimeType,
                    this.attributes,
                    this.compressionId,
                    this.encryptionId,
                    this.hasEcc
            );
        }

    }

}
