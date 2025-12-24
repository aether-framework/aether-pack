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
 * Binary format constants, record types, and structure definitions.
 *
 * <p>This package defines the APACK binary file format specification, including
 * all magic numbers, version constants, header structures, and flag definitions.
 * It provides the foundation for reading and writing APACK archives in a
 * platform-independent manner.</p>
 *
 * <h2>Format Overview</h2>
 * <p>The APACK format is a modern binary container designed for:</p>
 * <ul>
 *   <li><strong>Streaming:</strong> Entries can be written and read sequentially</li>
 *   <li><strong>Random Access:</strong> Optional trailer enables O(1) entry lookup</li>
 *   <li><strong>Security:</strong> AEAD encryption with authenticated headers</li>
 *   <li><strong>Integrity:</strong> Per-chunk checksums and optional ECC</li>
 *   <li><strong>Efficiency:</strong> Chunked processing for large files</li>
 * </ul>
 *
 * <h2>File Structure</h2>
 * <pre>
 * Offset      Size    Structure           Description
 * ════════════════════════════════════════════════════════════════════════════
 *
 * 0x0000      64      {@link de.splatgames.aether.pack.core.format.FileHeader FileHeader}           Magic, version, flags, chunk size
 *                     ┌────────────────────────────────────────────────────┐
 *                     │ "APACK" │ Ver │ Flags │ ChunkSize │ EntryCount    │
 *                     │  5 bytes│  3  │   2   │     4     │      8        │
 *                     │         │     │       │           │               │
 *                     │ TrailerOffset │ Timestamp │ Reserved              │
 *                     │      8        │     8     │    20                 │
 *                     └────────────────────────────────────────────────────┘
 *
 * [if encrypted]
 * 0x0040      var     {@link de.splatgames.aether.pack.core.format.EncryptionBlock EncryptionBlock}      Salt, KDF params, wrapped key
 *                     ┌────────────────────────────────────────────────────┐
 *                     │ KDF ID │ Salt │ Iterations │ Memory │ Parallelism │
 *                     │   1    │  32  │     4      │   4    │      1      │
 *                     │                                                    │
 *                     │ Encryption ID │ Nonce │ Wrapped Key                │
 *                     │      1        │  12   │    48                      │
 *                     └────────────────────────────────────────────────────┘
 *
 * ────────────────────────────────────────────────────────────────────────────
 *                         ENTRIES (repeated for each entry)
 * ────────────────────────────────────────────────────────────────────────────
 *
 * var         var     {@link de.splatgames.aether.pack.core.format.EntryHeader EntryHeader}          Entry metadata (name, MIME, attrs)
 *                     ┌────────────────────────────────────────────────────┐
 *                     │ "ENTR" │ Flags │ Sizes │ NameLen │ Name │ Attrs  │
 *                     │   4    │   2   │  16   │    2    │ var  │  var   │
 *                     └────────────────────────────────────────────────────┘
 *
 * var         var     Chunk 1
 *                     ┌────────────────────────────────────────────────────┐
 *                     │ {@link de.splatgames.aether.pack.core.format.ChunkHeader ChunkHeader} (24 bytes)                              │
 *                     │   Index │ OrigSize │ StoredSize │ Checksum │ Flags│
 *                     │    4    │    4     │     4      │    8     │   4  │
 *                     ├────────────────────────────────────────────────────┤
 *                     │ Data (compressed/encrypted)                       │
 *                     │   [StoredSize bytes]                              │
 *                     └────────────────────────────────────────────────────┘
 *
 * var         var     Chunk 2...N (last chunk has LAST_CHUNK flag)
 *
 * ────────────────────────────────────────────────────────────────────────────
 *
 * [if random access enabled]
 * TrailerOff  var     {@link de.splatgames.aether.pack.core.format.Trailer Trailer}              Table of contents for random access
 *                     ┌────────────────────────────────────────────────────┐
 *                     │ "TRLR" │ EntryCount │ TOC Entries │ Checksum      │
 *                     │   4    │     8      │    var      │     8         │
 *                     │                                                    │
 *                     │ {@link de.splatgames.aether.pack.core.format.TocEntry TocEntry}[]: ID │ NameHash │ Offset │ Size          │
 *                     └────────────────────────────────────────────────────┘
 *
 * ════════════════════════════════════════════════════════════════════════════
 * </pre>
 *
 * <h2>Key Records and Classes</h2>
 * <table>
 *   <caption>Format Components</caption>
 *   <tr><th>Class</th><th>Size</th><th>Purpose</th></tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.format.FileHeader}</td>
 *     <td>64 bytes</td>
 *     <td>Archive identification, version, global settings</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.format.EntryHeader}</td>
 *     <td>Variable</td>
 *     <td>Entry metadata: name, MIME type, sizes, attributes</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.format.ChunkHeader}</td>
 *     <td>24 bytes</td>
 *     <td>Chunk metadata: index, sizes, checksum, flags</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.format.Trailer}</td>
 *     <td>48+ bytes</td>
 *     <td>Table of contents for random access</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.format.TocEntry}</td>
 *     <td>32 bytes</td>
 *     <td>Single TOC entry with offset and size</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.format.EncryptionBlock}</td>
 *     <td>~100 bytes</td>
 *     <td>Key derivation and encryption parameters</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.format.Attribute}</td>
 *     <td>Variable</td>
 *     <td>Custom key-value metadata for entries</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.format.FormatConstants}</td>
 *     <td>-</td>
 *     <td>Magic numbers, flags, algorithm IDs, limits</td>
 *   </tr>
 * </table>
 *
 * <h2>Format Constants</h2>
 * <p>{@link de.splatgames.aether.pack.core.format.FormatConstants} defines:</p>
 *
 * <h3>Magic Numbers</h3>
 * <ul>
 *   <li>{@code MAGIC_FILE} - "APACK" (file header)</li>
 *   <li>{@code MAGIC_ENTRY} - "ENTR" (entry header)</li>
 *   <li>{@code MAGIC_TRAILER} - "TRLR" (trailer)</li>
 * </ul>
 *
 * <h3>Algorithm IDs</h3>
 * <ul>
 *   <li><strong>Compression:</strong> NONE=0, ZSTD=1, LZ4=2</li>
 *   <li><strong>Encryption:</strong> NONE=0, AES-256-GCM=1, ChaCha20-Poly1305=2</li>
 *   <li><strong>Checksum:</strong> CRC32=0, XXH3-64=1</li>
 *   <li><strong>KDF:</strong> PBKDF2=0, Argon2id=1</li>
 * </ul>
 *
 * <h3>Flags</h3>
 * <ul>
 *   <li><strong>File Flags:</strong> STREAM_MODE, ENCRYPTED, COMPRESSED, RANDOM_ACCESS</li>
 *   <li><strong>Entry Flags:</strong> HAS_ECC, ENCRYPTED, COMPRESSED</li>
 *   <li><strong>Chunk Flags:</strong> LAST_CHUNK, UNCOMPRESSED, HAS_ECC</li>
 * </ul>
 *
 * <h2>Byte Order</h2>
 * <p>All multi-byte integers are stored in <strong>Little-Endian</strong> byte
 * order throughout the format. This matches the native byte order of x86/x64
 * processors, minimizing conversion overhead on common platforms.</p>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Checking Format Version</h3>
 * <pre>{@code
 * FileHeader header = reader.getFileHeader();
 *
 * // Check compatibility
 * if (header.versionMajor() > FormatConstants.FORMAT_VERSION_MAJOR) {
 *     throw new UnsupportedVersionException(
 *         "Archive requires newer format version");
 * }
 *
 * // Check compat level
 * if (header.compatLevel() > FormatConstants.FORMAT_VERSION_MAJOR) {
 *     throw new UnsupportedVersionException(
 *         "Archive requires minimum compat level: " + header.compatLevel());
 * }
 * }</pre>
 *
 * <h3>Inspecting Flags</h3>
 * <pre>{@code
 * FileHeader header = reader.getFileHeader();
 *
 * if (header.isEncrypted()) {
 *     System.out.println("Archive is encrypted");
 * }
 * if (header.isCompressed()) {
 *     System.out.println("Compression enabled");
 * }
 * if (header.hasRandomAccess()) {
 *     System.out.println("Random access via TOC");
 * }
 * }</pre>
 *
 * <h3>Working with Entry Headers</h3>
 * <pre>{@code
 * EntryHeader eh = entry.getHeader();
 *
 * System.out.printf("Entry: %s%n", eh.name());
 * System.out.printf("Original size: %d bytes%n", eh.originalSize());
 * System.out.printf("Stored size: %d bytes%n", eh.storedSize());
 * System.out.printf("Chunks: %d%n", eh.chunkCount());
 *
 * if ((eh.flags() & FormatConstants.ENTRY_FLAG_COMPRESSED) != 0) {
 *     System.out.println("Entry is compressed with algorithm: " +
 *         eh.compressionId());
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All record types in this package are immutable and thread-safe.
 * {@link de.splatgames.aether.pack.core.format.FormatConstants} contains
 * only static final fields and is inherently thread-safe.</p>
 *
 * @see de.splatgames.aether.pack.core.format.FormatConstants
 * @see de.splatgames.aether.pack.core.format.FileHeader
 * @see de.splatgames.aether.pack.core.format.EntryHeader
 * @see de.splatgames.aether.pack.core.format.ChunkHeader
 * @see de.splatgames.aether.pack.core.format.Trailer
 * @see de.splatgames.aether.pack.core.format.TocEntry
 * @see de.splatgames.aether.pack.core.format.EncryptionBlock
 * @see de.splatgames.aether.pack.core.format.Attribute
 * @see de.splatgames.aether.pack.core.io.HeaderIO
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.splatgames.aether.pack.core.format;
