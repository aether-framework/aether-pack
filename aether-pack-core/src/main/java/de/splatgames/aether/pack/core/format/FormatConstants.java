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

package de.splatgames.aether.pack.core.format;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Constants defining the APACK binary format specification.
 *
 * <p>This class contains all magic numbers, size constants, algorithm identifiers,
 * and flag definitions that make up the APACK file format. These constants ensure
 * consistent interpretation of archive data across all implementations.</p>
 *
 * <h2>Format Overview</h2>
 * <p>An APACK file consists of:</p>
 * <pre>
 * +----------------+
 * |  File Header   |  (64 bytes, fixed)
 * +----------------+
 * |  Entry 1       |
 * |  - Entry Header|
 * |  - Chunk 1...N |
 * +----------------+
 * |  Entry 2...    |
 * +----------------+
 * |  ...           |
 * +----------------+
 * |  Trailer       |  (Table of Contents + metadata)
 * +----------------+
 * </pre>
 *
 * <h2>Byte Order</h2>
 * <p>All multi-byte integers in the format are stored in <strong>Little-Endian</strong>
 * byte order. This matches the native byte order of x86/x64 processors and ensures
 * efficient processing on most modern systems.</p>
 *
 * <h2>String Encoding</h2>
 * <p>All strings (entry names, MIME types, attribute keys) are encoded using
 * {@link #STRING_CHARSET UTF-8}.</p>
 *
 * <h2>Alignment</h2>
 * <p>Entry headers are aligned to {@link #ENTRY_ALIGNMENT 8-byte} boundaries
 * for efficient memory-mapped access.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class FormatConstants {

    /**
     * Private constructor to prevent instantiation.
     */
    private FormatConstants() {
        // Utility class - not instantiable
    }

    // ===== Magic Numbers =====

    /**
     * File magic number: "APACK" (5 bytes).
     */
    public static final byte @NotNull [] MAGIC = {'A', 'P', 'A', 'C', 'K'};

    /**
     * Entry header magic: "ENTR" (4 bytes).
     */
    public static final byte @NotNull [] ENTRY_MAGIC = {'E', 'N', 'T', 'R'};

    /**
     * Chunk header magic: "CHNK" (4 bytes).
     */
    public static final byte @NotNull [] CHUNK_MAGIC = {'C', 'H', 'N', 'K'};

    /**
     * Container trailer magic: "ATRL" (4 bytes).
     */
    public static final byte @NotNull [] TRAILER_MAGIC = {'A', 'T', 'R', 'L'};

    /**
     * Stream trailer magic: "STRL" (4 bytes).
     */
    public static final byte @NotNull [] STREAM_TRAILER_MAGIC = {'S', 'T', 'R', 'L'};

    /**
     * Encryption block magic: "ENCR" (4 bytes).
     */
    public static final byte @NotNull [] ENCRYPTION_MAGIC = {'E', 'N', 'C', 'R'};

    // ===== Size Constants =====

    /**
     * File header size in bytes (fixed).
     */
    public static final int FILE_HEADER_SIZE = 64;

    /**
     * Chunk header size in bytes (fixed).
     */
    public static final int CHUNK_HEADER_SIZE = 24;

    /**
     * TOC entry size in bytes (fixed).
     */
    public static final int TOC_ENTRY_SIZE = 40;

    /**
     * Container trailer header size in bytes (fixed, excluding TOC).
     */
    public static final int TRAILER_HEADER_SIZE = 48;

    /**
     * Stream trailer size in bytes (fixed).
     */
    public static final int STREAM_TRAILER_SIZE = 32;

    /**
     * Minimum entry header size in bytes (excluding variable-length fields).
     */
    public static final int ENTRY_HEADER_MIN_SIZE = 56;

    // ===== Version Constants =====

    /**
     * Current format major version.
     */
    public static final int FORMAT_VERSION_MAJOR = 1;

    /**
     * Current format minor version.
     */
    public static final int FORMAT_VERSION_MINOR = 0;

    /**
     * Current format patch version.
     */
    public static final int FORMAT_VERSION_PATCH = 0;

    /**
     * Minimum reader version required to read files created with this version.
     */
    public static final int COMPAT_LEVEL = 1;

    // ===== Default Values =====

    /**
     * Default chunk size: 256 KB.
     */
    public static final int DEFAULT_CHUNK_SIZE = 256 * 1024;

    /**
     * Minimum allowed chunk size: 1 KB.
     */
    public static final int MIN_CHUNK_SIZE = 1024;

    /**
     * Maximum allowed chunk size: 64 MB.
     */
    public static final int MAX_CHUNK_SIZE = 64 * 1024 * 1024;

    /**
     * Maximum entry name length in bytes.
     */
    public static final int MAX_ENTRY_NAME_LENGTH = 65535;

    /**
     * Byte alignment for entry headers.
     */
    public static final int ENTRY_ALIGNMENT = 8;

    // ===== Checksum Algorithm IDs =====

    /**
     * Checksum algorithm ID: CRC32.
     */
    public static final int CHECKSUM_CRC32 = 0;

    /**
     * Checksum algorithm ID: XXH3-64.
     */
    public static final int CHECKSUM_XXH3_64 = 1;

    /**
     * Checksum algorithm ID: XXH3-128.
     */
    public static final int CHECKSUM_XXH3_128 = 2;

    // ===== Compression Algorithm IDs =====

    /**
     * Compression algorithm ID: None.
     */
    public static final int COMPRESSION_NONE = 0;

    /**
     * Compression algorithm ID: ZSTD.
     */
    public static final int COMPRESSION_ZSTD = 1;

    /**
     * Compression algorithm ID: LZ4.
     */
    public static final int COMPRESSION_LZ4 = 2;

    // ===== Encryption Algorithm IDs =====

    /**
     * Encryption algorithm ID: None.
     */
    public static final int ENCRYPTION_NONE = 0;

    /**
     * Encryption algorithm ID: AES-256-GCM.
     */
    public static final int ENCRYPTION_AES_256_GCM = 1;

    /**
     * Encryption algorithm ID: ChaCha20-Poly1305.
     */
    public static final int ENCRYPTION_CHACHA20_POLY1305 = 2;

    // ===== KDF Algorithm IDs =====

    /**
     * KDF algorithm ID: Argon2id.
     */
    public static final int KDF_ARGON2ID = 0;

    /**
     * KDF algorithm ID: PBKDF2-SHA256.
     */
    public static final int KDF_PBKDF2_SHA256 = 1;

    // ===== Mode Flags (Bit positions) =====

    /**
     * Mode flag bit 0: Container mode (0) vs Stream mode (1).
     */
    public static final int FLAG_STREAM_MODE = 0x01;

    /**
     * Mode flag bit 1: Encryption enabled.
     */
    public static final int FLAG_ENCRYPTED = 0x02;

    /**
     * Mode flag bit 2: Compression enabled.
     */
    public static final int FLAG_COMPRESSED = 0x04;

    /**
     * Mode flag bit 3: Random access TOC present.
     */
    public static final int FLAG_RANDOM_ACCESS = 0x08;

    // ===== Entry Flags (Bit positions) =====

    /**
     * Entry flag bit 0: Has custom attributes.
     */
    public static final int ENTRY_FLAG_HAS_ATTRIBUTES = 0x01;

    /**
     * Entry flag bit 1: Entry is compressed.
     */
    public static final int ENTRY_FLAG_COMPRESSED = 0x02;

    /**
     * Entry flag bit 2: Entry is encrypted.
     */
    public static final int ENTRY_FLAG_ENCRYPTED = 0x04;

    /**
     * Entry flag bit 3: Has Reed-Solomon error correction.
     */
    public static final int ENTRY_FLAG_HAS_ECC = 0x08;

    // ===== Chunk Flags (Bit positions) =====

    /**
     * Chunk flag bit 0: Is last chunk of entry.
     */
    public static final int CHUNK_FLAG_LAST = 0x01;

    /**
     * Chunk flag bit 1: Chunk is compressed.
     */
    public static final int CHUNK_FLAG_COMPRESSED = 0x02;

    /**
     * Chunk flag bit 2: Chunk is encrypted.
     */
    public static final int CHUNK_FLAG_ENCRYPTED = 0x04;

    // ===== Attribute Value Types =====

    /**
     * Attribute value type: UTF-8 String.
     */
    public static final int ATTR_TYPE_STRING = 0;

    /**
     * Attribute value type: 64-bit signed integer.
     */
    public static final int ATTR_TYPE_INT64 = 1;

    /**
     * Attribute value type: 64-bit floating point.
     */
    public static final int ATTR_TYPE_FLOAT64 = 2;

    /**
     * Attribute value type: Boolean (1 byte, 0x00 or 0x01).
     */
    public static final int ATTR_TYPE_BOOLEAN = 3;

    /**
     * Attribute value type: Raw bytes.
     */
    public static final int ATTR_TYPE_BYTES = 4;

    // ===== Encoding =====

    /**
     * Character encoding for all strings in the format.
     */
    public static final @NotNull Charset STRING_CHARSET = StandardCharsets.UTF_8;

    // ===== Crypto Constants =====

    /**
     * Nonce/IV size for AES-GCM and ChaCha20-Poly1305 in bytes.
     */
    public static final int NONCE_SIZE = 12;

    /**
     * Authentication tag size for AEAD ciphers in bytes.
     */
    public static final int AUTH_TAG_SIZE = 16;

    /**
     * Key size for AES-256 and ChaCha20 in bytes.
     */
    public static final int KEY_SIZE = 32;

    /**
     * Default salt size for KDF in bytes.
     */
    public static final int DEFAULT_SALT_SIZE = 32;

}
