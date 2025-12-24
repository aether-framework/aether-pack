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

package de.splatgames.aether.pack.core.io;

import de.splatgames.aether.pack.core.exception.FormatException;
import de.splatgames.aether.pack.core.exception.UnsupportedVersionException;
import de.splatgames.aether.pack.core.format.Attribute;
import de.splatgames.aether.pack.core.format.EncryptionBlock;
import de.splatgames.aether.pack.core.format.EntryHeader;
import de.splatgames.aether.pack.core.format.FileHeader;
import de.splatgames.aether.pack.core.format.FormatConstants;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Utility class for serializing and deserializing APACK headers.
 *
 * <p>This class provides static methods for reading and writing the binary
 * representations of {@link FileHeader} and {@link EntryHeader} structures.
 * It handles all the low-level details of byte order, field encoding, and
 * checksum computation.</p>
 *
 * <h2>Supported Headers</h2>
 * <ul>
 *   <li>{@link FileHeader} - The 64-byte file header at the start of every archive</li>
 *   <li>{@link EntryHeader} - Variable-length headers preceding each entry's data</li>
 * </ul>
 *
 * <h2>Validation</h2>
 * <p>When reading headers, this class performs the following validations:</p>
 * <ul>
 *   <li>Magic number verification (e.g., "APACK", "ENTR")</li>
 *   <li>Version compatibility checking</li>
 *   <li>Format structure validation</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Writing a file header
 * FileHeader header = FileHeader.builder()
 *     .chunkSize(256 * 1024)
 *     .compressed(true)
 *     .build();
 * HeaderIO.writeFileHeader(writer, header);
 *
 * // Reading a file header
 * FileHeader readHeader = HeaderIO.readFileHeader(reader);
 * if (readHeader.isCompressed()) {
 *     // Handle compressed archive
 * }
 *
 * // Writing an entry header
 * EntryHeader entry = EntryHeader.builder()
 *     .name("data/file.txt")
 *     .originalSize(1024)
 *     .build();
 * HeaderIO.writeEntryHeader(writer, entry);
 *
 * // Reading an entry header
 * EntryHeader readEntry = HeaderIO.readEntryHeader(reader);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All methods in this class are stateless and thread-safe. However, the
 * {@link BinaryReader} and {@link BinaryWriter} instances passed to these
 * methods are not thread-safe.</p>
 *
 * @see FileHeader
 * @see EntryHeader
 * @see BinaryReader
 * @see BinaryWriter
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class HeaderIO {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private HeaderIO() {
        // Utility class
    }

    // ==================== File Header ====================

    /**
     * Writes a file header to the output stream in binary format.
     *
     * <p>This method serializes a {@link FileHeader} to its binary representation
     * according to the APACK format specification. The file header is always
     * exactly {@link FormatConstants#FILE_HEADER_SIZE} (64) bytes and includes:</p>
     * <ul>
     *   <li>Magic number ("APACK" + null byte)</li>
     *   <li>Version information (major, minor, patch)</li>
     *   <li>Compatibility level</li>
     *   <li>Mode flags and checksum algorithm</li>
     *   <li>Chunk size configuration</li>
     *   <li>Header checksum (automatically computed)</li>
     *   <li>Entry count and trailer offset</li>
     *   <li>Creation timestamp</li>
     *   <li>Reserved bytes for future use</li>
     * </ul>
     *
     * <p>The header checksum is computed automatically over the first 20 bytes
     * of the header to enable corruption detection during reading.</p>
     *
     * @param writer the binary writer to write the header to; must not be
     *               {@code null}; must be positioned at the start of where
     *               the header should be written
     * @param header the file header to serialize; must not be {@code null};
     *               all fields will be written in Little-Endian byte order
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     * @throws NullPointerException if writer or header is {@code null}
     *
     * @see #readFileHeader(BinaryReader)
     * @see FileHeader
     * @see FormatConstants#FILE_HEADER_SIZE
     */
    public static void writeFileHeader(
            final @NotNull BinaryWriter writer,
            final @NotNull FileHeader header) throws IOException {

        // Magic + null byte (6 bytes)
        writer.writeMagic();

        // Version (6 bytes)
        writer.writeUInt16(header.versionMajor());
        writer.writeUInt16(header.versionMinor());
        writer.writeUInt16(header.versionPatch());

        // Compat level (2 bytes)
        writer.writeUInt16(header.compatLevel());

        // Flags and checksum algo (2 bytes)
        writer.writeByte(header.modeFlags());
        writer.writeByte(header.checksumAlgorithm());

        // Chunk size (4 bytes)
        writer.writeInt32(header.chunkSize());

        // Compute header checksum (CRC32 of bytes 0x00-0x13 = first 20 bytes)
        // We need to compute this separately
        final int headerChecksum = computeHeaderChecksum(header);
        writer.writeInt32(headerChecksum);

        // Entry count (8 bytes)
        writer.writeInt64(header.entryCount());

        // Trailer offset (8 bytes)
        writer.writeInt64(header.trailerOffset());

        // Creation timestamp (8 bytes)
        writer.writeInt64(header.creationTimestamp());

        // Reserved (16 bytes)
        writer.writeZeros(16);
    }

    /**
     * Reads and deserializes a file header from the input stream.
     *
     * <p>This method reads {@link FormatConstants#FILE_HEADER_SIZE} (64) bytes
     * from the input and parses them into a {@link FileHeader} structure. The
     * method performs several validations during parsing:</p>
     * <ul>
     *   <li>Magic number verification ("APACK" + null byte)</li>
     *   <li>Compatibility level checking against the current format version</li>
     *   <li>Reserved bytes are skipped but not validated</li>
     * </ul>
     *
     * <p>If the archive's compatibility level is higher than the current
     * format major version, an {@link UnsupportedVersionException} is thrown
     * to prevent reading archives created by newer, incompatible versions.</p>
     *
     * @param reader the binary reader to read the header from; must not be
     *               {@code null}; must be positioned at the start of the
     *               file header (typically at offset 0)
     * @return the parsed file header containing all archive metadata; never
     *         {@code null}; the returned header is a complete representation
     *         of the binary data read from the stream
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, if end of stream is reached
     *                     before all header bytes can be read, or if the
     *                     reader has been closed
     * @throws FormatException if the magic number is invalid, indicating
     *                         that the file is not a valid APACK archive
     * @throws UnsupportedVersionException if the archive's compatibility
     *                                     level is higher than the current
     *                                     format major version
     * @throws NullPointerException if reader is {@code null}
     *
     * @see #writeFileHeader(BinaryWriter, FileHeader)
     * @see FileHeader
     * @see FormatConstants#FILE_HEADER_SIZE
     */
    public static @NotNull FileHeader readFileHeader(
            final @NotNull BinaryReader reader) throws IOException, FormatException {

        // Magic + null byte
        reader.readAndValidateMagic();

        // Version
        final int versionMajor = reader.readUInt16();
        final int versionMinor = reader.readUInt16();
        final int versionPatch = reader.readUInt16();

        // Compat level
        final int compatLevel = reader.readUInt16();

        // Check compatibility
        if (compatLevel > FormatConstants.FORMAT_VERSION_MAJOR) {
            throw new UnsupportedVersionException(compatLevel, FormatConstants.FORMAT_VERSION_MAJOR);
        }

        // Flags and checksum algo
        final int modeFlags = reader.readByte();
        final int checksumAlgorithm = reader.readByte();

        // Chunk size
        final int chunkSize = reader.readInt32();

        // Validate chunk size to prevent OOM attacks
        if (chunkSize < FormatConstants.MIN_CHUNK_SIZE || chunkSize > FormatConstants.MAX_CHUNK_SIZE) {
            throw new FormatException("Invalid chunk size: " + chunkSize +
                    " (must be " + FormatConstants.MIN_CHUNK_SIZE + "-" + FormatConstants.MAX_CHUNK_SIZE + ")");
        }

        // Header checksum (we skip validation for now, could add later)
        final int headerChecksum = reader.readInt32();

        // Entry count
        final long entryCount = reader.readInt64();

        // Validate entry count to prevent resource exhaustion attacks
        if (entryCount < 0) {
            throw new FormatException("Invalid negative entry count: " + entryCount);
        }
        // Reasonable maximum: 1 million entries (prevents malicious allocation)
        final long MAX_ENTRY_COUNT = 1_000_000;
        if (entryCount > MAX_ENTRY_COUNT) {
            throw new FormatException("Entry count exceeds maximum: " + entryCount +
                    " (max: " + MAX_ENTRY_COUNT + ")");
        }

        // Trailer offset
        final long trailerOffset = reader.readInt64();

        // Creation timestamp
        final long creationTimestamp = reader.readInt64();

        // Skip reserved bytes
        reader.skip(16);

        return new FileHeader(
                versionMajor,
                versionMinor,
                versionPatch,
                compatLevel,
                modeFlags,
                checksumAlgorithm,
                chunkSize,
                headerChecksum,
                entryCount,
                trailerOffset,
                creationTimestamp
        );
    }

    /**
     * Computes the CRC32 checksum of the header fields.
     *
     * <p>The checksum is computed over the first 20 bytes of the file header,
     * which includes the magic number, version information, compatibility level,
     * flags, and chunk size.</p>
     *
     * @param header the file header to compute the checksum for
     * @return the CRC32 checksum value
     */
    private static int computeHeaderChecksum(final @NotNull FileHeader header) {
        final CRC32 crc = new CRC32();
        final byte[] data = new byte[20];

        // Magic
        System.arraycopy(FormatConstants.MAGIC, 0, data, 0, 5);
        data[5] = 0; // null byte

        // Version (little-endian)
        writeUInt16LE(data, 6, header.versionMajor());
        writeUInt16LE(data, 8, header.versionMinor());
        writeUInt16LE(data, 10, header.versionPatch());

        // Compat level
        writeUInt16LE(data, 12, header.compatLevel());

        // Flags
        data[14] = (byte) header.modeFlags();
        data[15] = (byte) header.checksumAlgorithm();

        // Chunk size
        writeInt32LE(data, 16, header.chunkSize());

        crc.update(data);
        return (int) crc.getValue();
    }

    // ==================== Encryption Block ====================

    /**
     * Writes an encryption block to the output stream in binary format.
     *
     * <p>The encryption block is written immediately after the file header
     * when encryption is enabled. It contains all information needed to
     * derive the encryption key from a password and decrypt the archive.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>
     * Offset  Size    Field              Description
     * ──────────────────────────────────────────────────────────────────
     * 0x00    4       magic              "ENCR" (ASCII)
     * 0x04    1       kdfAlgorithmId     KDF algorithm (0=Argon2id, 1=PBKDF2)
     * 0x05    1       cipherAlgorithmId  Cipher (1=AES-256-GCM, 2=ChaCha20)
     * 0x06    2       reserved           Reserved for future use
     * 0x08    4       kdfIterations      Iterations/time cost
     * 0x0C    4       kdfMemory          Memory cost in KB (Argon2 only)
     * 0x10    4       kdfParallelism     Parallelism factor (Argon2 only)
     * 0x14    2       saltLength         Length of salt in bytes
     * 0x16    2       wrappedKeyLength   Length of wrapped key in bytes
     * 0x18    N       salt               KDF salt (typically 32 bytes)
     * 0x18+N  M       wrappedKey         Wrapped DEK + auth tag
     * </pre>
     *
     * @param writer the binary writer to write the block to; must not be {@code null}
     * @param block  the encryption block to serialize; must not be {@code null}
     * @throws IOException if an I/O error occurs while writing
     *
     * @see #readEncryptionBlock(BinaryReader)
     * @see EncryptionBlock
     */
    public static void writeEncryptionBlock(
            final @NotNull BinaryWriter writer,
            final @NotNull EncryptionBlock block) throws IOException {

        // Magic "ENCR" (4 bytes)
        writer.writeBytes(FormatConstants.ENCRYPTION_MAGIC);

        // KDF algorithm ID (1 byte)
        writer.writeByte(block.kdfAlgorithmId());

        // Cipher algorithm ID (1 byte)
        writer.writeByte(block.cipherAlgorithmId());

        // Reserved (2 bytes)
        writer.writeZeros(2);

        // KDF iterations (4 bytes)
        writer.writeInt32(block.kdfIterations());

        // KDF memory in KB (4 bytes)
        writer.writeInt32(block.kdfMemory());

        // KDF parallelism (4 bytes)
        writer.writeInt32(block.kdfParallelism());

        // Salt length (2 bytes)
        final byte[] salt = block.salt();
        writer.writeUInt16(salt.length);

        // Wrapped key length (includes auth tag) (2 bytes)
        final byte[] wrappedKey = block.wrappedKey();
        final byte[] wrappedKeyTag = block.wrappedKeyTag();
        writer.writeUInt16(wrappedKey.length + wrappedKeyTag.length);

        // Salt
        writer.writeBytes(salt);

        // Wrapped key + auth tag
        writer.writeBytes(wrappedKey);
        writer.writeBytes(wrappedKeyTag);

        // Padding to 8-byte alignment
        writer.writePadding(8);
    }

    /**
     * Reads and deserializes an encryption block from the input stream.
     *
     * <p>This method reads the encryption block that follows the file header
     * in encrypted archives. It validates the magic number and extracts all
     * key derivation and wrapped key parameters.</p>
     *
     * @param reader the binary reader to read the block from; must not be {@code null}
     * @return the parsed encryption block; never {@code null}
     * @throws IOException if an I/O error occurs while reading
     * @throws FormatException if the encryption block magic is invalid
     *
     * @see #writeEncryptionBlock(BinaryWriter, EncryptionBlock)
     * @see EncryptionBlock
     */
    public static @NotNull EncryptionBlock readEncryptionBlock(
            final @NotNull BinaryReader reader) throws IOException, FormatException {

        // Read and validate magic "ENCR"
        final byte[] magic = reader.readBytes(4);
        if (!java.util.Arrays.equals(magic, FormatConstants.ENCRYPTION_MAGIC)) {
            throw new FormatException("Invalid encryption block magic: expected 'ENCR'");
        }

        // KDF algorithm ID (1 byte)
        final int kdfAlgorithmId = reader.readByte();

        // Cipher algorithm ID (1 byte)
        final int cipherAlgorithmId = reader.readByte();

        // Reserved (2 bytes)
        reader.skip(2);

        // KDF iterations (4 bytes)
        final int kdfIterations = reader.readInt32();

        // KDF memory in KB (4 bytes)
        final int kdfMemory = reader.readInt32();

        // KDF parallelism (4 bytes)
        final int kdfParallelism = reader.readInt32();

        // Salt length (2 bytes)
        final int saltLength = reader.readUInt16();
        if (saltLength < 0 || saltLength > 256) {
            throw new FormatException("Invalid salt length: " + saltLength);
        }

        // Wrapped key length (total length including any tag) (2 bytes)
        final int wrappedKeyTotalLength = reader.readUInt16();
        if (wrappedKeyTotalLength < 8) {
            throw new FormatException("Invalid wrapped key length: " + wrappedKeyTotalLength);
        }

        // Salt
        final byte[] salt = reader.readBytes(saltLength);

        // Read the full wrapped key data
        // For AES Key Wrap: the full output is the wrapped key (includes 8-byte ICV)
        // For other methods: might include a separate auth tag
        // We store the full wrapped output and an optional separate tag
        final byte[] fullWrappedData = reader.readBytes(wrappedKeyTotalLength);

        // Split into wrappedKey and wrappedKeyTag
        // AES Key Wrap produces: key_length + 8 bytes (ICV embedded)
        // We store the full output in wrappedKey, tag can be empty
        final byte[] wrappedKey;
        final byte[] wrappedKeyTag;

        // Heuristic: if total length is exactly key_size + 8 (AES Key Wrap), treat all as wrappedKey
        // Otherwise, assume last AUTH_TAG_SIZE bytes are the tag
        if (wrappedKeyTotalLength == 40 || wrappedKeyTotalLength == 24 || wrappedKeyTotalLength == 32) {
            // Looks like AES Key Wrap output (32+8=40, 24+8=32, 16+8=24)
            wrappedKey = fullWrappedData;
            wrappedKeyTag = new byte[0];
        } else if (wrappedKeyTotalLength > FormatConstants.AUTH_TAG_SIZE) {
            // Has room for a separate auth tag
            wrappedKey = java.util.Arrays.copyOfRange(fullWrappedData, 0, wrappedKeyTotalLength - FormatConstants.AUTH_TAG_SIZE);
            wrappedKeyTag = java.util.Arrays.copyOfRange(fullWrappedData, wrappedKeyTotalLength - FormatConstants.AUTH_TAG_SIZE, wrappedKeyTotalLength);
        } else {
            // No separate tag, everything is the wrapped key
            wrappedKey = fullWrappedData;
            wrappedKeyTag = new byte[0];
        }

        // Skip padding
        reader.skipPadding(8);

        return new EncryptionBlock(
                kdfAlgorithmId,
                cipherAlgorithmId,
                kdfIterations,
                kdfMemory,
                kdfParallelism,
                salt,
                wrappedKey,
                wrappedKeyTag
        );
    }

    // ==================== Entry Header ====================

    /**
     * Writes an entry header to the output stream in binary format.
     *
     * <p>This method serializes an {@link EntryHeader} to its binary representation
     * according to the APACK format specification. Unlike the fixed-size file header,
     * entry headers are variable-length due to the entry name, MIME type, and
     * custom attributes. The entry header includes:</p>
     * <ul>
     *   <li>Magic number ("ENTR")</li>
     *   <li>Header version and flags</li>
     *   <li>Entry ID for random access lookup</li>
     *   <li>Original and stored sizes</li>
     *   <li>Chunk count and algorithm IDs</li>
     *   <li>Variable-length name and MIME type</li>
     *   <li>Custom attributes (if any)</li>
     *   <li>Padding to 8-byte alignment</li>
     * </ul>
     *
     * <p>The method automatically adds padding bytes at the end to ensure
     * the next structure starts on an 8-byte boundary for efficient access.</p>
     *
     * @param writer the binary writer to write the header to; must not be
     *               {@code null}; must be positioned where the entry header
     *               should begin
     * @param header the entry header to serialize; must not be {@code null};
     *               all fields including name, MIME type, and attributes
     *               will be written
     * @throws IOException if an I/O error occurs while writing to the
     *                     underlying stream, or if the writer has been closed
     * @throws NullPointerException if writer or header is {@code null}
     *
     * @see #readEntryHeader(BinaryReader)
     * @see EntryHeader
     * @see FormatConstants#ENTRY_MAGIC
     * @see FormatConstants#ENTRY_ALIGNMENT
     */
    public static void writeEntryHeader(
            final @NotNull BinaryWriter writer,
            final @NotNull EntryHeader header) throws IOException {

        // Magic (4 bytes)
        writer.writeBytes(FormatConstants.ENTRY_MAGIC);

        // Header version (2 bytes)
        writer.writeUInt16(header.headerVersion());

        // Flags (2 bytes)
        writer.writeUInt16(header.flags());

        // Entry ID (8 bytes)
        writer.writeInt64(header.entryId());

        // Original size (8 bytes)
        writer.writeInt64(header.originalSize());

        // Stored size (8 bytes)
        writer.writeInt64(header.storedSize());

        // Chunk count (4 bytes)
        writer.writeInt32(header.chunkCount());

        // Compression ID (4 bytes)
        writer.writeInt32(header.compressionId());

        // Encryption ID (4 bytes)
        writer.writeInt32(header.encryptionId());

        // Name length (2 bytes)
        final byte[] nameBytes = header.name().getBytes(FormatConstants.STRING_CHARSET);
        writer.writeUInt16(nameBytes.length);

        // MIME type length (2 bytes)
        final byte[] mimeBytes = header.mimeType().getBytes(FormatConstants.STRING_CHARSET);
        writer.writeUInt16(mimeBytes.length);

        // Attribute count (4 bytes)
        writer.writeInt32(header.attributes().size());

        // Header checksum (4 bytes) - placeholder, should be computed
        writer.writeInt32(header.headerChecksum());

        // Name
        writer.writeBytes(nameBytes);

        // MIME type
        writer.writeBytes(mimeBytes);

        // Attributes
        for (final Attribute attr : header.attributes()) {
            writeAttribute(writer, attr);
        }

        // Padding to 8-byte alignment
        writer.writePadding(FormatConstants.ENTRY_ALIGNMENT);
    }

    /**
     * Reads and deserializes an entry header from the input stream.
     *
     * <p>This method reads a variable-length entry header from the input and
     * parses it into an {@link EntryHeader} structure. The method reads:</p>
     * <ul>
     *   <li>Magic number ("ENTR") with validation</li>
     *   <li>Header version and flags</li>
     *   <li>Entry metadata (ID, sizes, chunk count)</li>
     *   <li>Algorithm IDs (compression, encryption)</li>
     *   <li>Variable-length name and MIME type</li>
     *   <li>Custom attributes (if present)</li>
     *   <li>Padding bytes to reach 8-byte alignment</li>
     * </ul>
     *
     * <p>After reading, the reader is positioned at the start of the entry's
     * first chunk header, ready for data reading.</p>
     *
     * @param reader the binary reader to read the header from; must not be
     *               {@code null}; must be positioned at the start of an
     *               entry header (at the "ENTR" magic bytes)
     * @return the parsed entry header containing all entry metadata including
     *         name, sizes, algorithm IDs, and attributes; never {@code null}
     * @throws IOException if an I/O error occurs while reading from the
     *                     underlying stream, if end of stream is reached
     *                     before all header bytes can be read, or if the
     *                     reader has been closed
     * @throws FormatException if the entry magic number is invalid (not "ENTR"),
     *                         indicating a corrupted archive or incorrect
     *                         stream position
     * @throws NullPointerException if reader is {@code null}
     *
     * @see #writeEntryHeader(BinaryWriter, EntryHeader)
     * @see EntryHeader
     * @see FormatConstants#ENTRY_MAGIC
     */
    public static @NotNull EntryHeader readEntryHeader(
            final @NotNull BinaryReader reader) throws IOException, FormatException {

        // Read and validate magic
        final byte[] magic = reader.readBytes(4);
        if (!java.util.Arrays.equals(magic, FormatConstants.ENTRY_MAGIC)) {
            throw new FormatException("Invalid entry magic");
        }

        // Header version
        final int headerVersion = reader.readUInt16();

        // Flags
        final int flags = reader.readUInt16();

        // Entry ID
        final long entryId = reader.readInt64();

        // Sizes
        final long originalSize = reader.readInt64();
        final long storedSize = reader.readInt64();

        // Validate entry sizes to prevent OOM attacks
        if (originalSize < 0) {
            throw new FormatException("Invalid negative original size: " + originalSize);
        }
        if (storedSize < 0) {
            throw new FormatException("Invalid negative stored size: " + storedSize);
        }
        // Reasonable maximum: 1 TB per entry
        final long MAX_ENTRY_SIZE = 1024L * 1024L * 1024L * 1024L;
        if (originalSize > MAX_ENTRY_SIZE || storedSize > MAX_ENTRY_SIZE) {
            throw new FormatException("Entry size exceeds maximum: original=" + originalSize +
                    ", stored=" + storedSize + " (max: " + MAX_ENTRY_SIZE + ")");
        }

        // Chunk count
        final int chunkCount = reader.readInt32();

        // Algorithm IDs
        final int compressionId = reader.readInt32();
        final int encryptionId = reader.readInt32();

        // Lengths
        final int nameLength = reader.readUInt16();
        final int mimeTypeLength = reader.readUInt16();
        final int attributeCount = reader.readInt32();

        // Header checksum
        final int headerChecksum = reader.readInt32();

        // Name
        final String name = reader.readString(nameLength);

        // MIME type
        final String mimeType = reader.readString(mimeTypeLength);

        // Attributes
        final List<Attribute> attributes = new ArrayList<>(attributeCount);
        for (int i = 0; i < attributeCount; i++) {
            attributes.add(readAttribute(reader));
        }

        // Skip padding
        reader.skipPadding(FormatConstants.ENTRY_ALIGNMENT);

        return new EntryHeader(
                headerVersion,
                flags,
                entryId,
                originalSize,
                storedSize,
                chunkCount,
                compressionId,
                encryptionId,
                headerChecksum,
                name,
                mimeType,
                attributes
        );
    }

    // ==================== Attributes ====================

    /**
     * Writes an attribute to the output.
     *
     * <p>An attribute consists of a key-value pair with type information.
     * The binary format is:</p>
     * <ul>
     *   <li>Key length (2 bytes)</li>
     *   <li>Value type (1 byte)</li>
     *   <li>Reserved (1 byte)</li>
     *   <li>Value length (4 bytes)</li>
     *   <li>Key data (UTF-8 encoded)</li>
     *   <li>Value data (raw bytes)</li>
     * </ul>
     *
     * @param writer the binary writer
     * @param attr   the attribute to write
     * @throws IOException if an I/O error occurs
     */
    private static void writeAttribute(
            final @NotNull BinaryWriter writer,
            final @NotNull Attribute attr) throws IOException {

        final byte[] keyBytes = attr.key().getBytes(FormatConstants.STRING_CHARSET);
        final byte[] valueBytes = attr.value();

        // Key length (2 bytes)
        writer.writeUInt16(keyBytes.length);

        // Value type (1 byte)
        writer.writeByte(attr.valueType());

        // Reserved (1 byte)
        writer.writeByte(0);

        // Value length (4 bytes)
        writer.writeInt32(valueBytes.length);

        // Key
        writer.writeBytes(keyBytes);

        // Value
        writer.writeBytes(valueBytes);
    }

    /**
     * Reads an attribute from the input.
     *
     * <p>Parses the attribute structure from the binary format, reading
     * the key, value type, and value data.</p>
     *
     * @param reader the binary reader
     * @return the parsed attribute
     * @throws IOException if an I/O error occurs
     */
    private static @NotNull Attribute readAttribute(
            final @NotNull BinaryReader reader) throws IOException {

        // Key length
        final int keyLength = reader.readUInt16();

        // Value type
        final int valueType = reader.readByte();

        // Reserved
        reader.readByte();

        // Value length
        final int valueLength = reader.readInt32();

        // Validate attribute value length to prevent OOM attacks
        if (valueLength < 0 || valueLength > FormatConstants.MAX_CHUNK_SIZE) {
            throw new IOException("Invalid attribute value length: " + valueLength);
        }

        // Key
        final String key = reader.readString(keyLength);

        // Value
        final byte[] value = reader.readBytes(valueLength);

        return new Attribute(key, valueType, value);
    }

    // ==================== Helper Methods ====================

    /**
     * Writes a 16-bit unsigned integer in Little-Endian format to a byte array.
     *
     * @param data   the byte array to write to
     * @param offset the offset in the array
     * @param value  the value to write
     */
    private static void writeUInt16LE(final byte[] data, final int offset, final int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
    }

    /**
     * Writes a 32-bit signed integer in Little-Endian format to a byte array.
     *
     * @param data   the byte array to write to
     * @param offset the offset in the array
     * @param value  the value to write
     */
    private static void writeInt32LE(final byte[] data, final int offset, final int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
        data[offset + 3] = (byte) (value >> 24);
    }

}
