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

/**
 * Entry abstraction and metadata handling for APACK archives.
 *
 * <p>This package provides the abstraction layer for entries within an APACK
 * archive. An entry represents a logical unit of data (similar to a file)
 * with associated metadata such as name, MIME type, size information, and
 * custom attributes. Entries are the primary unit of interaction when
 * reading from or writing to archives.</p>
 *
 * <h2>Entry Concept</h2>
 * <p>Each entry in an APACK archive consists of:</p>
 * <pre>
 * ┌───────────────────────────────────────────────────────────────────────┐
 * │                              Entry                                    │
 * ├───────────────────────────────────────────────────────────────────────┤
 * │  ┌─────────────────────────────────────────────────────────────────┐ │
 * │  │                         Metadata                                │ │
 * │  │  • ID: Unique identifier (long)                                 │ │
 * │  │  • Name: Path within archive ("assets/config.json")            │ │
 * │  │  • MIME Type: Content type hint ("application/json")           │ │
 * │  │  • Original Size: Uncompressed size in bytes                   │ │
 * │  │  • Stored Size: Compressed/encrypted size in bytes             │ │
 * │  │  • Chunk Count: Number of data chunks                          │ │
 * │  │  • Flags: Compression, encryption, ECC status                  │ │
 * │  │  • Attributes: Custom key-value metadata                       │ │
 * │  └─────────────────────────────────────────────────────────────────┘ │
 * │  ┌─────────────────────────────────────────────────────────────────┐ │
 * │  │                      Data Chunks                                │ │
 * │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐       ┌─────────┐         │ │
 * │  │  │ Chunk 1 │ │ Chunk 2 │ │ Chunk 3 │  ...  │ Chunk N │         │ │
 * │  │  │ (256KB) │ │ (256KB) │ │ (256KB) │       │(&lt;256KB) │        │ │
 * │  │  └─────────┘ └─────────┘ └─────────┘       └─────────┘         │ │
 * │  │       Each chunk: compressed, encrypted, checksummed            │ │
 * │  └─────────────────────────────────────────────────────────────────┘ │
 * └───────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Class Hierarchy</h2>
 * <pre>
 *                    ┌───────────────────┐
 *                    │      Entry        │  (interface)
 *                    │  - getId()        │
 *                    │  - getName()      │
 *                    │  - getMimeType()  │
 *                    │  - getAttributes()│
 *                    │  - isCompressed() │
 *                    │  - isEncrypted()  │
 *                    └─────────┬─────────┘
 *                              │
 *           ┌──────────────────┴──────────────────┐
 *           │                                     │
 *  ┌────────┴────────┐               ┌────────────┴────────────┐
 *  │    PackEntry    │               │     EntryMetadata       │
 *  │   (immutable)   │               │       (mutable)         │
 *  │                 │               │                         │
 *  │  Read from      │               │  Used for writing       │
 *  │  existing       │               │  new entries            │
 *  │  archive        │               │                         │
 *  │                 │               │  Sizes updated during   │
 *  │  Includes file  │               │  write operation        │
 *  │  offset for     │               │                         │
 *  │  random access  │               │  Builder pattern        │
 *  └─────────────────┘               └─────────────────────────┘
 * </pre>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link de.splatgames.aether.pack.core.entry.Entry} - Common interface
 *       for all entry types, defines metadata accessors and attribute helpers</li>
 *   <li>{@link de.splatgames.aether.pack.core.entry.PackEntry} - Immutable
 *       implementation for entries read from an archive</li>
 *   <li>{@link de.splatgames.aether.pack.core.entry.EntryMetadata} - Mutable
 *       metadata holder used when writing entries to an archive</li>
 * </ul>
 *
 * <h2>Custom Attributes</h2>
 * <p>Entries support custom key-value attributes for application-specific
 * metadata. Supported value types:</p>
 * <table>
 *   <caption>Attribute Value Types</caption>
 *   <tr><th>Type</th><th>ID</th><th>Java Type</th><th>Example Use</th></tr>
 *   <tr><td>String</td><td>0</td><td>{@code String}</td><td>Author, description</td></tr>
 *   <tr><td>Int64</td><td>1</td><td>{@code long}</td><td>Timestamp, version</td></tr>
 *   <tr><td>Float64</td><td>2</td><td>{@code double}</td><td>Coordinates, ratings</td></tr>
 *   <tr><td>Boolean</td><td>3</td><td>{@code boolean}</td><td>Flags, markers</td></tr>
 *   <tr><td>Bytes</td><td>4</td><td>{@code byte[]}</td><td>Thumbnails, hashes</td></tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Reading Entry Metadata</h3>
 * <pre>{@code
 * try (AetherPackReader reader = AetherPackReader.open(path)) {
 *     for (Entry entry : reader) {
 *         // Basic metadata
 *         System.out.printf("Name: %s%n", entry.getName());
 *         System.out.printf("Size: %d bytes (stored: %d)%n",
 *             entry.getOriginalSize(), entry.getStoredSize());
 *         System.out.printf("MIME: %s%n", entry.getMimeType());
 *
 *         // Compression ratio
 *         if (entry.isCompressed()) {
 *             double ratio = 100.0 * entry.getStoredSize() / entry.getOriginalSize();
 *             System.out.printf("Compressed to %.1f%%%n", ratio);
 *         }
 *
 *         // Custom attributes
 *         entry.getStringAttribute("author")
 *              .ifPresent(author -> System.out.println("Author: " + author));
 *
 *         entry.getLongAttribute("created")
 *              .map(Instant::ofEpochMilli)
 *              .ifPresent(ts -> System.out.println("Created: " + ts));
 *
 *         entry.getBooleanAttribute("readonly")
 *              .filter(ro -> ro)
 *              .ifPresent(ro -> System.out.println("Read-only entry"));
 *     }
 * }
 * }</pre>
 *
 * <h3>Creating Entry Metadata for Writing</h3>
 * <pre>{@code
 * // Simple entry with just a name
 * EntryMetadata simple = EntryMetadata.of("data.bin");
 *
 * // Entry with MIME type
 * EntryMetadata withMime = EntryMetadata.of("document.pdf", "application/pdf");
 *
 * // Full-featured entry with builder
 * EntryMetadata rich = EntryMetadata.builder()
 *     .name("assets/textures/hero.png")
 *     .mimeType("image/png")
 *     .attribute("author", "John Doe")
 *     .attribute("created", System.currentTimeMillis())
 *     .attribute("version", 3L)
 *     .attribute("draft", false)
 *     .build();
 *
 * // Write to archive
 * try (AetherPackWriter writer = AetherPackWriter.create(path)) {
 *     writer.addEntry(rich, inputStream);
 * }
 * }</pre>
 *
 * <h3>Accessing All Attributes</h3>
 * <pre>{@code
 * // Iterate over all attributes
 * for (Attribute attr : entry.getAttributes()) {
 *     System.out.printf("  %s (%s): ", attr.key(), attr.valueType());
 *     switch (attr.valueType()) {
 *         case FormatConstants.ATTR_TYPE_STRING:
 *             System.out.println(attr.asString());
 *             break;
 *         case FormatConstants.ATTR_TYPE_INT64:
 *             System.out.println(attr.asLong());
 *             break;
 *         case FormatConstants.ATTR_TYPE_BOOLEAN:
 *             System.out.println(attr.asBoolean());
 *             break;
 *         default:
 *             System.out.println("[binary data]");
 *     }
 * }
 * }</pre>
 *
 * <h2>Entry Names</h2>
 * <p>Entry names follow these conventions:</p>
 * <ul>
 *   <li>UTF-8 encoded, max 65535 bytes</li>
 *   <li>Forward slashes ({@code /}) as path separators (portable)</li>
 *   <li>No leading slash (relative paths)</li>
 *   <li>Case-sensitive matching</li>
 *   <li>No restrictions on characters (except null bytes)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *   <li>{@link de.splatgames.aether.pack.core.entry.PackEntry} - Immutable and
 *       thread-safe, can be shared across threads</li>
 *   <li>{@link de.splatgames.aether.pack.core.entry.EntryMetadata} - Mutable
 *       size fields are updated during writing; not thread-safe</li>
 * </ul>
 *
 * @see de.splatgames.aether.pack.core.entry.Entry
 * @see de.splatgames.aether.pack.core.entry.PackEntry
 * @see de.splatgames.aether.pack.core.entry.EntryMetadata
 * @see de.splatgames.aether.pack.core.format.Attribute
 * @see de.splatgames.aether.pack.core.format.EntryHeader
 * @see de.splatgames.aether.pack.core.AetherPackReader
 * @see de.splatgames.aether.pack.core.AetherPackWriter
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.splatgames.aether.pack.core.entry;
