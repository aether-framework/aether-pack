# File Header Specification

The file header is a fixed 64-byte structure that appears at the beginning of every APACK file. It contains essential metadata for archive identification, version compatibility, and format configuration.

## Binary Layout

| Offset | Size | Field | Type | Description |
|--------|------|-------|------|-------------|
| 0x00 | 5 | magic | bytes | Magic number "APACK" (ASCII) |
| 0x05 | 1 | versionMajor | uint8 | Format major version |
| 0x06 | 1 | versionMinor | uint8 | Format minor version |
| 0x07 | 1 | versionPatch | uint8 | Format patch version |
| 0x08 | 1 | compatLevel | uint8 | Minimum reader version required |
| 0x09 | 1 | modeFlags | uint8 | Mode flags (bitmask) |
| 0x0A | 1 | checksumAlgorithm | uint8 | Checksum algorithm ID |
| 0x0B | 1 | reserved | uint8 | Reserved for future use |
| 0x0C | 4 | chunkSize | int32 | Default chunk size in bytes |
| 0x10 | 4 | headerChecksum | uint32 | CRC32 of bytes 0x00-0x0F |
| 0x14 | 8 | entryCount | int64 | Number of entries |
| 0x1C | 8 | trailerOffset | int64 | Absolute file offset to trailer |
| 0x24 | 8 | creationTimestamp | int64 | Creation time (ms since epoch) |
| 0x2C | 20 | reserved | bytes | Reserved for future use |

**Total Size: 64 bytes (0x40)**

## Visual Layout

```
Offset  0x00      0x04      0x08      0x0C
        ┌─────────┬─────────┬─────────┬─────────┐
0x00    │  magic  │ version │compat/  │ chunk   │
        │ "APACK" │ maj/min │mode/chk │  size   │
        │  (5)    │ patch   │ rsv     │  (4)    │
        ├─────────┴─────────┼─────────┴─────────┤
0x10    │   headerChecksum  │    entryCount     │
        │       (4)         │       (8)         │
        ├───────────────────┴───────────────────┤
0x1C    │            trailerOffset              │
        │                (8)                    │
        ├───────────────────────────────────────┤
0x24    │          creationTimestamp            │
        │                (8)                    │
        ├───────────────────────────────────────┤
0x2C    │              reserved                 │
        │               (20)                    │
        └───────────────────────────────────────┘
0x40    End of file header
```

## Field Descriptions

### magic (5 bytes)

The magic number identifies the file as an APACK archive. It must be exactly the ASCII string "APACK" (bytes: `0x41 0x50 0x41 0x43 0x4B`).

If the magic number does not match, the file is not a valid APACK archive and should be rejected with a `FormatException`.

### versionMajor, versionMinor, versionPatch (1 byte each)

The format version encoded as semantic versioning (major.minor.patch).

| Version | Value | Notes |
|---------|-------|-------|
| Current | 1.0.0 | Initial release |

**Compatibility Rules:**
- Major version change: Breaking changes, incompatible format
- Minor version change: New features, backward compatible
- Patch version change: Bug fixes, no format changes

### compatLevel (1 byte)

The minimum reader version required to properly read this archive. A reader with version less than `compatLevel` should refuse to open the file.

This allows writers to use new features while maintaining backward compatibility information.

### modeFlags (1 byte)

Bitmask of mode flags indicating archive capabilities:

| Bit | Mask | Flag | Description |
|-----|------|------|-------------|
| 0 | 0x01 | `FLAG_STREAM_MODE` | Stream mode (single entry, no TOC) |
| 1 | 0x02 | `FLAG_ENCRYPTED` | Encryption is enabled |
| 2 | 0x04 | `FLAG_COMPRESSED` | Compression is enabled (globally) |
| 3 | 0x08 | `FLAG_RANDOM_ACCESS` | Table of contents present |
| 4-7 | - | Reserved | Reserved for future use |

**Notes:**
- `FLAG_STREAM_MODE` and `FLAG_RANDOM_ACCESS` are mutually exclusive
- `FLAG_ENCRYPTED` indicates an encryption block follows the file header
- `FLAG_COMPRESSED` is a hint; individual chunks may still be uncompressed

### checksumAlgorithm (1 byte)

The checksum algorithm used for chunk data integrity verification.

| ID | Algorithm | Output Size |
|----|-----------|-------------|
| 0 | CRC32 | 32 bits |
| 1 | XXH3-64 | 64 bits |

The recommended default is XXH3-64 (ID: 1) for its excellent performance and hash quality.

### chunkSize (4 bytes, int32)

The default chunk size in bytes for splitting entry data. All entries in the archive use this chunk size.

| Constraint | Value |
|------------|-------|
| Default | 262,144 (256 KB) |
| Minimum | 1,024 (1 KB) |
| Maximum | 67,108,864 (64 MB) |

Larger chunk sizes improve compression ratio but require more memory. Smaller chunk sizes reduce memory usage but may decrease compression efficiency.

### headerChecksum (4 bytes, uint32)

CRC32 checksum of the header bytes from offset 0x00 to 0x0F (the first 16 bytes). This provides early detection of header corruption.

**Calculation:**
```java
byte[] headerStart = new byte[16];
// ... read bytes 0x00-0x0F
CRC32 crc = new CRC32();
crc.update(headerStart);
int checksum = (int) crc.getValue();
```

### entryCount (8 bytes, int64)

The total number of entries in the archive.

**Special values:**
- `0` in stream mode (entry count unknown until trailer)
- Actual count in container mode

### trailerOffset (8 bytes, int64)

Absolute file offset to the trailer structure.

**Special values:**
- `0` if no trailer is present yet (writing in progress)
- Actual offset in finalized archives

Readers can seek to this offset for O(1) access to the table of contents.

### creationTimestamp (8 bytes, int64)

Archive creation timestamp as milliseconds since the Unix epoch (January 1, 1970, 00:00:00 UTC).

Use `System.currentTimeMillis()` in Java to generate this value.

### reserved (20 bytes)

Reserved for future use. Must be set to zeros when writing and ignored when reading.

## Reading Example

```java
public FileHeader readFileHeader(BinaryReader reader) throws ApackException {
    // Validate magic number
    byte[] magic = reader.readBytes(5);
    if (!Arrays.equals(magic, "APACK".getBytes(StandardCharsets.US_ASCII))) {
        throw new FormatException("Invalid magic number");
    }

    int versionMajor = reader.readUInt8();
    int versionMinor = reader.readUInt8();
    int versionPatch = reader.readUInt8();
    int compatLevel = reader.readUInt8();
    int modeFlags = reader.readUInt8();
    int checksumAlgorithm = reader.readUInt8();
    int reserved1 = reader.readUInt8();
    int chunkSize = reader.readInt32();
    int headerChecksum = reader.readInt32();
    long entryCount = reader.readInt64();
    long trailerOffset = reader.readInt64();
    long creationTimestamp = reader.readInt64();
    reader.skipBytes(20); // reserved

    return new FileHeader(
        versionMajor, versionMinor, versionPatch,
        compatLevel, modeFlags, checksumAlgorithm,
        chunkSize, headerChecksum, entryCount,
        trailerOffset, creationTimestamp
    );
}
```

## Writing Example

```java
public void writeFileHeader(BinaryWriter writer, FileHeader header) throws IOException {
    // Magic number
    writer.writeBytes("APACK".getBytes(StandardCharsets.US_ASCII));

    // Version
    writer.writeUInt8(header.versionMajor());
    writer.writeUInt8(header.versionMinor());
    writer.writeUInt8(header.versionPatch());

    // Configuration
    writer.writeUInt8(header.compatLevel());
    writer.writeUInt8(header.modeFlags());
    writer.writeUInt8(header.checksumAlgorithm());
    writer.writeUInt8(0); // reserved
    writer.writeInt32(header.chunkSize());

    // Checksum (computed over bytes 0x00-0x0F)
    writer.writeInt32(header.headerChecksum());

    // Counts and offsets
    writer.writeInt64(header.entryCount());
    writer.writeInt64(header.trailerOffset());
    writer.writeInt64(header.creationTimestamp());

    // Reserved
    writer.writeZeros(20);
}
```

## Validation Rules

When reading a file header, validate:

1. **Magic number** must be exactly "APACK"
2. **Version compatibility**: `compatLevel <= readerVersion`
3. **Chunk size** must be within valid range
4. **Header checksum** must match computed CRC32
5. **Flags** must not have conflicting bits set

```java
public void validateHeader(FileHeader header) throws FormatException {
    // Check version compatibility
    if (header.compatLevel() > CURRENT_VERSION_MAJOR) {
        throw new UnsupportedVersionException(
            "Archive requires version " + header.compatLevel());
    }

    // Check chunk size
    if (header.chunkSize() < MIN_CHUNK_SIZE ||
        header.chunkSize() > MAX_CHUNK_SIZE) {
        throw new FormatException("Invalid chunk size: " + header.chunkSize());
    }

    // Check mutually exclusive flags
    if ((header.modeFlags() & FLAG_STREAM_MODE) != 0 &&
        (header.modeFlags() & FLAG_RANDOM_ACCESS) != 0) {
        throw new FormatException("Conflicting mode flags");
    }
}
```

---

*Next: [Entry Header](entry-header.md) | Previous: [Format Overview](README.md)*
