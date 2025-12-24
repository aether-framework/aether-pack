# Constants Reference

This document provides a complete reference of all magic numbers, algorithm IDs, flags, and size constants defined in the APACK format.

## Magic Numbers

Magic numbers identify structure types within the archive.

| Structure | Magic | ASCII | Bytes |
|-----------|-------|-------|-------|
| File Header | APACK | "APACK" | `41 50 41 43 4B` |
| Entry Header | ENTR | "ENTR" | `45 4E 54 52` |
| Chunk Header | CHNK | "CHNK" | `43 48 4E 4B` |
| Container Trailer | ATRL | "ATRL" | `41 54 52 4C` |
| Stream Trailer | STRL | "STRL" | `53 54 52 4C` |
| Encryption Block | ENCR | "ENCR" | `45 4E 43 52` |

## Size Constants

Fixed sizes for various structures.

| Constant | Value | Description |
|----------|-------|-------------|
| `FILE_HEADER_SIZE` | 64 bytes | Complete file header |
| `CHUNK_HEADER_SIZE` | 24 bytes | Chunk header (fixed) |
| `TOC_ENTRY_SIZE` | 40 bytes | Single TOC entry |
| `TRAILER_HEADER_SIZE` | 64 bytes | Container trailer header (excluding TOC) |
| `STREAM_TRAILER_SIZE` | 32 bytes | Stream trailer (fixed) |
| `ENTRY_HEADER_MIN_SIZE` | 56 bytes | Minimum entry header (no name/attributes) |
| `ENTRY_ALIGNMENT` | 8 bytes | Entry header alignment |

## Chunk Size Limits

| Constant | Value | Description |
|----------|-------|-------------|
| `DEFAULT_CHUNK_SIZE` | 262,144 (256 KB) | Default chunk size |
| `MIN_CHUNK_SIZE` | 1,024 (1 KB) | Minimum allowed chunk size |
| `MAX_CHUNK_SIZE` | 67,108,864 (64 MB) | Maximum allowed chunk size |

## Entry Limits

| Constant | Value | Description |
|----------|-------|-------------|
| `MAX_ENTRY_NAME_LENGTH` | 65,535 bytes | Maximum entry name length |
| `MAX_MIME_TYPE_LENGTH` | 255 bytes | Maximum MIME type length |
| `MAX_ATTRIBUTE_COUNT` | 65,535 | Maximum attributes per entry |

---

## Algorithm IDs

### Checksum Algorithms

| ID | Constant | Algorithm | Output Size |
|----|----------|-----------|-------------|
| 0 | `CHECKSUM_CRC32` | CRC32 | 32 bits |
| 1 | `CHECKSUM_XXH3_64` | XXH3-64 | 64 bits |
| 2 | `CHECKSUM_XXH3_128` | XXH3-128 | 128 bits |

**Recommended:** XXH3-64 for chunk data (fast, high quality).

### Compression Algorithms

| ID | Constant | Algorithm | Notes |
|----|----------|-----------|-------|
| 0 | `COMPRESSION_NONE` | None | No compression |
| 1 | `COMPRESSION_ZSTD` | Zstandard | Best ratio, levels 1-22 |
| 2 | `COMPRESSION_LZ4` | LZ4 | Fastest, levels 0-17 |

### Encryption Algorithms

| ID | Constant | Algorithm | Key Size | Nonce Size |
|----|----------|-----------|----------|------------|
| 0 | `ENCRYPTION_NONE` | None | - | - |
| 1 | `ENCRYPTION_AES_256_GCM` | AES-256-GCM | 256 bits | 96 bits |
| 2 | `ENCRYPTION_CHACHA20_POLY1305` | ChaCha20-Poly1305 | 256 bits | 96 bits |

### Key Derivation Functions (KDF)

| ID | Constant | Algorithm | Notes |
|----|----------|-----------|-------|
| 0 | `KDF_ARGON2ID` | Argon2id | Memory-hard, recommended |
| 1 | `KDF_PBKDF2_SHA256` | PBKDF2-HMAC-SHA256 | Widely compatible |

---

## Flag Definitions

### File Mode Flags

Stored in `FileHeader.modeFlags` (1 byte bitmask).

| Bit | Mask | Constant | Description |
|-----|------|----------|-------------|
| 0 | 0x01 | `FLAG_STREAM_MODE` | Stream mode (single entry, no TOC) |
| 1 | 0x02 | `FLAG_ENCRYPTED` | Archive has encryption enabled |
| 2 | 0x04 | `FLAG_COMPRESSED` | Archive has compression enabled |
| 3 | 0x08 | `FLAG_RANDOM_ACCESS` | TOC present for random access |
| 4-7 | - | Reserved | Reserved for future use |

**Notes:**
- `FLAG_STREAM_MODE` and `FLAG_RANDOM_ACCESS` are mutually exclusive
- `FLAG_ENCRYPTED` indicates encryption block follows file header

### Entry Flags

Stored in `EntryHeader.flags` (1 byte bitmask).

| Bit | Mask | Constant | Description |
|-----|------|----------|-------------|
| 0 | 0x01 | `ENTRY_FLAG_HAS_ATTRIBUTES` | Entry has custom attributes |
| 1 | 0x02 | `ENTRY_FLAG_COMPRESSED` | Entry data is compressed |
| 2 | 0x04 | `ENTRY_FLAG_ENCRYPTED` | Entry data is encrypted |
| 3 | 0x08 | `ENTRY_FLAG_HAS_ECC` | Reed-Solomon error correction present |
| 4-7 | - | Reserved | Reserved for future use |

### Chunk Flags

Stored in `ChunkHeader.flags` (4 bytes, but only lower bits used).

| Bit | Mask | Constant | Description |
|-----|------|----------|-------------|
| 0 | 0x01 | `CHUNK_FLAG_LAST` | Last chunk of entry |
| 1 | 0x02 | `CHUNK_FLAG_COMPRESSED` | Chunk data is compressed |
| 2 | 0x04 | `CHUNK_FLAG_ENCRYPTED` | Chunk data is encrypted |
| 3-31 | - | Reserved | Reserved for future use |

---

## Attribute Value Types

| ID | Constant | Type | Encoding |
|----|----------|------|----------|
| 0 | `ATTR_TYPE_STRING` | String | UTF-8, no null terminator |
| 1 | `ATTR_TYPE_INT64` | Int64 | Little-endian 64-bit signed |
| 2 | `ATTR_TYPE_FLOAT64` | Float64 | Little-endian IEEE 754 double |
| 3 | `ATTR_TYPE_BOOLEAN` | Boolean | 0x00 (false) or 0x01 (true) |
| 4 | `ATTR_TYPE_BYTES` | Bytes | Raw byte array |

---

## Cryptographic Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `NONCE_SIZE` | 12 bytes | Nonce/IV for AES-GCM and ChaCha20 |
| `AUTH_TAG_SIZE` | 16 bytes | AEAD authentication tag |
| `KEY_SIZE` | 32 bytes | Encryption key (256 bits) |
| `DEFAULT_SALT_SIZE` | 32 bytes | KDF salt length |

---

## Version Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `FORMAT_VERSION_MAJOR` | 1 | Current format major version |
| `FORMAT_VERSION_MINOR` | 0 | Current format minor version |
| `FORMAT_VERSION_PATCH` | 0 | Current format patch version |
| `COMPAT_LEVEL` | 1 | Minimum reader version required |
| `TRAILER_CURRENT_VERSION` | 1 | Current trailer format version |

---

## Encoding

| Constant | Value | Description |
|----------|-------|-------------|
| `STRING_CHARSET` | UTF-8 | Encoding for all strings |
| Byte Order | Little-Endian | All multi-byte integers |

---

## Java Constants Class Reference

All constants are defined in `de.splatgames.aether.pack.core.format.FormatConstants`:

```java
public final class FormatConstants {
    // Magic numbers
    public static final byte[] MAGIC = {'A', 'P', 'A', 'C', 'K'};
    public static final byte[] ENTRY_MAGIC = {'E', 'N', 'T', 'R'};
    public static final byte[] CHUNK_MAGIC = {'C', 'H', 'N', 'K'};
    public static final byte[] TRAILER_MAGIC = {'A', 'T', 'R', 'L'};
    public static final byte[] STREAM_TRAILER_MAGIC = {'S', 'T', 'R', 'L'};
    public static final byte[] ENCRYPTION_MAGIC = {'E', 'N', 'C', 'R'};

    // Sizes
    public static final int FILE_HEADER_SIZE = 64;
    public static final int CHUNK_HEADER_SIZE = 24;
    public static final int TOC_ENTRY_SIZE = 40;
    public static final int TRAILER_HEADER_SIZE = 48;
    public static final int STREAM_TRAILER_SIZE = 32;
    public static final int ENTRY_HEADER_MIN_SIZE = 56;
    public static final int ENTRY_ALIGNMENT = 8;

    // Chunk sizes
    public static final int DEFAULT_CHUNK_SIZE = 256 * 1024;
    public static final int MIN_CHUNK_SIZE = 1024;
    public static final int MAX_CHUNK_SIZE = 64 * 1024 * 1024;
    public static final int MAX_ENTRY_NAME_LENGTH = 65535;

    // Checksum algorithms
    public static final int CHECKSUM_CRC32 = 0;
    public static final int CHECKSUM_XXH3_64 = 1;
    public static final int CHECKSUM_XXH3_128 = 2;

    // Compression algorithms
    public static final int COMPRESSION_NONE = 0;
    public static final int COMPRESSION_ZSTD = 1;
    public static final int COMPRESSION_LZ4 = 2;

    // Encryption algorithms
    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_AES_256_GCM = 1;
    public static final int ENCRYPTION_CHACHA20_POLY1305 = 2;

    // KDF algorithms
    public static final int KDF_ARGON2ID = 0;
    public static final int KDF_PBKDF2_SHA256 = 1;

    // Mode flags
    public static final int FLAG_STREAM_MODE = 0x01;
    public static final int FLAG_ENCRYPTED = 0x02;
    public static final int FLAG_COMPRESSED = 0x04;
    public static final int FLAG_RANDOM_ACCESS = 0x08;

    // Entry flags
    public static final int ENTRY_FLAG_HAS_ATTRIBUTES = 0x01;
    public static final int ENTRY_FLAG_COMPRESSED = 0x02;
    public static final int ENTRY_FLAG_ENCRYPTED = 0x04;
    public static final int ENTRY_FLAG_HAS_ECC = 0x08;

    // Chunk flags
    public static final int CHUNK_FLAG_LAST = 0x01;
    public static final int CHUNK_FLAG_COMPRESSED = 0x02;
    public static final int CHUNK_FLAG_ENCRYPTED = 0x04;

    // Attribute types
    public static final int ATTR_TYPE_STRING = 0;
    public static final int ATTR_TYPE_INT64 = 1;
    public static final int ATTR_TYPE_FLOAT64 = 2;
    public static final int ATTR_TYPE_BOOLEAN = 3;
    public static final int ATTR_TYPE_BYTES = 4;

    // Crypto constants
    public static final int NONCE_SIZE = 12;
    public static final int AUTH_TAG_SIZE = 16;
    public static final int KEY_SIZE = 32;
    public static final int DEFAULT_SALT_SIZE = 32;

    // Version
    public static final int FORMAT_VERSION_MAJOR = 1;
    public static final int FORMAT_VERSION_MINOR = 0;
    public static final int FORMAT_VERSION_PATCH = 0;
    public static final int COMPAT_LEVEL = 1;

    // Encoding
    public static final Charset STRING_CHARSET = StandardCharsets.UTF_8;
}
```

---

*Previous: [Attributes](attributes.md) | Back to: [Format Overview](README.md)*
