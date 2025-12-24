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
 * Binary I/O primitives and chunked stream processing.
 *
 * <p>This package provides low-level binary I/O operations for reading and
 * writing APACK archive structures, as well as high-level chunked stream
 * processing that handles automatic chunking, compression, encryption,
 * and checksum verification transparently.</p>
 *
 * <h2>Architecture Overview</h2>
 * <pre>
 *                              APPLICATION LAYER
 *                    ┌───────────────────────────────────┐
 *                    │  AetherPackReader/Writer          │
 *                    └─────────────────┬─────────────────┘
 *                                      │
 *     ┌────────────────────────────────┴────────────────────────────────┐
 *     │                         I/O PACKAGE                             │
 *     │                                                                 │
 *     │  ┌─────────────────────────────────────────────────────────────┐│
 *     │  │              CHUNKED STREAM LAYER                          ││
 *     │  │  ┌────────────────────┐    ┌────────────────────┐          ││
 *     │  │  │ ChunkedOutputStream│    │ ChunkedInputStream │          ││
 *     │  │  │  • Auto-chunking   │    │  • Chunk assembly  │          ││
 *     │  │  │  • Checksum calc   │    │  • Checksum verify │          ││
 *     │  │  │  • Size tracking   │    │  • Decompression   │          ││
 *     │  │  └─────────┬──────────┘    └─────────┬──────────┘          ││
 *     │  │            │                         │                      ││
 *     │  │            └──────────┬──────────────┘                      ││
 *     │  │                       │                                     ││
 *     │  │           ┌───────────┴───────────┐                         ││
 *     │  │           │    ChunkProcessor     │                         ││
 *     │  │           │  • Compression        │                         ││
 *     │  │           │  • Encryption         │                         ││
 *     │  │           │  • ECC encoding       │                         ││
 *     │  │           └───────────────────────┘                         ││
 *     │  └─────────────────────────────────────────────────────────────┘│
 *     │                                                                 │
 *     │  ┌─────────────────────────────────────────────────────────────┐│
 *     │  │               HEADER I/O LAYER                             ││
 *     │  │  ┌────────────┐  ┌────────────┐  ┌────────────┐            ││
 *     │  │  │  HeaderIO  │  │ TrailerIO  │  │ Encryption │            ││
 *     │  │  │            │  │            │  │  BlockIO   │            ││
 *     │  │  └────────────┘  └────────────┘  └────────────┘            ││
 *     │  └─────────────────────────────────────────────────────────────┘│
 *     │                                                                 │
 *     │  ┌─────────────────────────────────────────────────────────────┐│
 *     │  │              BINARY PRIMITIVES LAYER                       ││
 *     │  │  ┌────────────────────┐    ┌────────────────────┐          ││
 *     │  │  │   BinaryReader     │    │   BinaryWriter     │          ││
 *     │  │  │  • Little-Endian   │    │  • Little-Endian   │          ││
 *     │  │  │  • Type reading    │    │  • Type writing    │          ││
 *     │  │  │  • Byte tracking   │    │  • Padding support │          ││
 *     │  │  └────────────────────┘    └────────────────────┘          ││
 *     │  └─────────────────────────────────────────────────────────────┘│
 *     └─────────────────────────────────────────────────────────────────┘
 *                                      │
 *                    ┌─────────────────┴─────────────────┐
 *                    │      java.io InputStream/         │
 *                    │         OutputStream              │
 *                    └───────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Classes</h2>
 *
 * <h3>Binary Primitives</h3>
 * <ul>
 *   <li>{@link de.splatgames.aether.pack.core.io.BinaryReader} - Reads
 *       Little-Endian encoded primitives from an input stream</li>
 *   <li>{@link de.splatgames.aether.pack.core.io.BinaryWriter} - Writes
 *       Little-Endian encoded primitives to an output stream</li>
 * </ul>
 *
 * <h3>Header I/O</h3>
 * <ul>
 *   <li>{@link de.splatgames.aether.pack.core.io.HeaderIO} - Reads and writes
 *       file headers, entry headers, and chunk headers</li>
 *   <li>{@link de.splatgames.aether.pack.core.io.TrailerIO} - Reads and writes
 *       the table of contents trailer for random access</li>
 * </ul>
 *
 * <h3>Chunked Streams</h3>
 * <ul>
 *   <li>{@link de.splatgames.aether.pack.core.io.ChunkedOutputStream} - Splits
 *       data into chunks, applies processing, writes headers</li>
 *   <li>{@link de.splatgames.aether.pack.core.io.ChunkedInputStream} - Reads
 *       chunks, verifies checksums, reassembles original data</li>
 *   <li>{@link de.splatgames.aether.pack.core.io.ChunkProcessor} - Applies
 *       compression, encryption, and ECC to chunk data</li>
 *   <li>{@link de.splatgames.aether.pack.core.io.ChunkSecuritySettings} -
 *       Security validation settings for chunk processing</li>
 * </ul>
 *
 * <h2>Byte Order</h2>
 * <p>All binary I/O operations use <strong>Little-Endian</strong> byte order,
 * as specified by the APACK format. This is the native byte order for x86/x64
 * processors, minimizing conversion overhead on common platforms.</p>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Binary Reader</h3>
 * <pre>{@code
 * try (BinaryReader reader = new BinaryReader(inputStream)) {
 *     // Validate magic number
 *     reader.readAndValidateMagic();  // Throws if not "APACK"
 *
 *     // Read header fields
 *     int majorVersion = reader.readUInt8();
 *     int minorVersion = reader.readUInt8();
 *     int patchVersion = reader.readUInt8();
 *     int chunkSize = reader.readInt32();
 *     long entryCount = reader.readInt64();
 *
 *     // Read strings
 *     String name = reader.readLengthPrefixedString16();  // 2-byte length prefix
 *     String mime = reader.readLengthPrefixedString8();   // 1-byte length prefix
 *
 *     // Skip padding to alignment boundary
 *     reader.skipPadding(8);  // Skip to next 8-byte boundary
 *
 *     // Track position
 *     long bytesRead = reader.getBytesRead();
 * }
 * }</pre>
 *
 * <h3>Binary Writer</h3>
 * <pre>{@code
 * try (BinaryWriter writer = new BinaryWriter(outputStream)) {
 *     // Write magic number
 *     writer.writeMagic();  // Writes "APACK\0"
 *
 *     // Write header fields
 *     writer.writeUInt8(1);   // Major version
 *     writer.writeUInt8(0);   // Minor version
 *     writer.writeUInt8(0);   // Patch version
 *     writer.writeInt32(chunkSize);
 *     writer.writeInt64(entryCount);
 *
 *     // Write strings
 *     writer.writeLengthPrefixedString16(name);
 *     writer.writeLengthPrefixedString8(mimeType);
 *
 *     // Add padding to alignment boundary
 *     writer.writePadding(8);  // Pad to next 8-byte boundary
 *
 *     // Track position
 *     long bytesWritten = writer.getBytesWritten();
 * }
 * }</pre>
 *
 * <h3>Reading Headers</h3>
 * <pre>{@code
 * try (BinaryReader reader = new BinaryReader(inputStream)) {
 *     // Read file header
 *     FileHeader fileHeader = HeaderIO.readFileHeader(reader);
 *     System.out.printf("APACK v%d.%d.%d%n",
 *         fileHeader.versionMajor(),
 *         fileHeader.versionMinor(),
 *         fileHeader.versionPatch());
 *
 *     // Read entry header
 *     EntryHeader entryHeader = HeaderIO.readEntryHeader(reader);
 *     System.out.println("Entry: " + entryHeader.name());
 *
 *     // Read chunk header
 *     ChunkHeader chunkHeader = HeaderIO.readChunkHeader(reader);
 *     System.out.printf("Chunk %d: %d bytes%n",
 *         chunkHeader.index(), chunkHeader.storedSize());
 * }
 * }</pre>
 *
 * <h3>Chunked Output Stream</h3>
 * <pre>{@code
 * ChunkProcessor processor = new ChunkProcessor(
 *     compressionProvider,
 *     compressionLevel,
 *     checksumProvider
 * );
 *
 * try (ChunkedOutputStream out = new ChunkedOutputStream(
 *         outputStream,
 *         processor,
 *         chunkSize)) {
 *
 *     // Write data - automatically chunked
 *     out.write(largeData);
 *
 *     // Finalize (writes last chunk with LAST_CHUNK flag)
 *     out.finish();
 *
 *     // Get statistics
 *     long originalSize = out.getOriginalSize();
 *     long storedSize = out.getStoredSize();
 *     int chunkCount = out.getChunkCount();
 * }
 * }</pre>
 *
 * <h3>Chunked Input Stream</h3>
 * <pre>{@code
 * ChunkSecuritySettings security = ChunkSecuritySettings.builder()
 *     .maxChunkSize(16 * 1024 * 1024)  // 16 MB max
 *     .maxExpansionRatio(10.0)          // Max 10x size increase
 *     .build();
 *
 * try (ChunkedInputStream in = new ChunkedInputStream(
 *         inputStream,
 *         checksumProvider,
 *         decompressor,
 *         expectedChunkCount,
 *         security)) {
 *
 *     // Read data - chunks are automatically assembled
 *     byte[] data = in.readAllBytes();
 *
 *     // Checksums are verified automatically
 *     // ChecksumException thrown if verification fails
 * }
 * }</pre>
 *
 * <h2>Security Considerations</h2>
 * <p>{@link de.splatgames.aether.pack.core.io.ChunkSecuritySettings} protects
 * against malicious archives:</p>
 * <ul>
 *   <li><strong>Max Chunk Size:</strong> Prevents memory exhaustion from huge chunks</li>
 *   <li><strong>Max Expansion Ratio:</strong> Limits decompression bomb attacks</li>
 *   <li><strong>Chunk Count Validation:</strong> Ensures expected number of chunks</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All stream classes in this package are <strong>NOT thread-safe</strong>.
 * A single stream instance should only be accessed by one thread at a time.
 * For parallel processing, each thread should use its own stream instance.</p>
 *
 * @see de.splatgames.aether.pack.core.io.BinaryReader
 * @see de.splatgames.aether.pack.core.io.BinaryWriter
 * @see de.splatgames.aether.pack.core.io.HeaderIO
 * @see de.splatgames.aether.pack.core.io.TrailerIO
 * @see de.splatgames.aether.pack.core.io.ChunkedInputStream
 * @see de.splatgames.aether.pack.core.io.ChunkedOutputStream
 * @see de.splatgames.aether.pack.core.io.ChunkProcessor
 * @see de.splatgames.aether.pack.core.io.ChunkSecuritySettings
 * @see de.splatgames.aether.pack.core.format.FormatConstants
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.splatgames.aether.pack.core.io;
