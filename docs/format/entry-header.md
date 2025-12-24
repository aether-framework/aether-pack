# Entry Header Specification

The entry header is a variable-size structure that describes an individual entry within an APACK archive. It contains metadata about the entry including name, sizes, processing flags, and custom attributes.

## Binary Layout

### Fixed Header (48 bytes minimum)

| Offset | Size | Field | Type | Description |
|--------|------|-------|------|-------------|
| 0x00 | 4 | magic | bytes | Magic number "ENTR" (ASCII) |
| 0x04 | 1 | headerVersion | uint8 | Entry header version (current: 1) |
| 0x05 | 1 | flags | uint8 | Entry flags (bitmask) |
| 0x06 | 2 | reserved | uint16 | Reserved for future use |
| 0x08 | 8 | entryId | int64 | Unique entry identifier |
| 0x10 | 8 | originalSize | int64 | Uncompressed size in bytes |
| 0x18 | 8 | storedSize | int64 | Stored (compressed) size in bytes |
| 0x20 | 4 | chunkCount | int32 | Number of data chunks |
| 0x24 | 1 | compressionId | uint8 | Compression algorithm ID |
| 0x25 | 1 | encryptionId | uint8 | Encryption algorithm ID |
| 0x26 | 2 | nameLength | uint16 | Entry name length in bytes |
| 0x28 | 2 | mimeTypeLength | uint16 | MIME type length in bytes |
| 0x2A | 2 | attrCount | uint16 | Number of custom attributes |
| 0x2C | 4 | headerChecksum | uint32 | CRC32 of header (excluding this field) |

### Variable-Length Fields

| Field | Size | Description |
|-------|------|-------------|
| name | nameLength | Entry name (UTF-8, no null terminator) |
| mimeType | mimeTypeLength | MIME type (UTF-8, no null terminator) |
| attributes | variable | Encoded attributes (see below) |
| padding | 0-7 | Zero padding to 8-byte boundary |

**Total Size: 48 + nameLength + mimeTypeLength + attributesSize + padding**

## Visual Layout

```
Offset  0x00      0x04      0x08      0x0C
        ┌─────────┬─────────┬─────────┬─────────┐
0x00    │  magic  │ ver/flg │  entry  │         │
        │ "ENTR"  │ rsv(2)  │   Id    │ (cont)  │
        │   (4)   │         │   (8)   │         │
        ├─────────┴─────────┼─────────┴─────────┤
0x10    │    originalSize   │    storedSize     │
        │        (8)        │       (8)         │
        ├───────────────────┼───────────────────┤
0x20    │    chunkCount     │ comp/enc│name/mime│
        │       (4)         │  (1+1)  │len (2+2)│
        ├───────────────────┼─────────┴─────────┤
0x28    │ attrCount │  hdrChecksum │            │
        │    (2)    │     (4)      │            │
        ├───────────┴──────────────┴────────────┤
0x30    │                 name                  │
        │            (nameLength bytes)         │
        ├───────────────────────────────────────┤
        │               mimeType                │
        │          (mimeTypeLength bytes)       │
        ├───────────────────────────────────────┤
        │              attributes               │
        │              (variable)               │
        ├───────────────────────────────────────┤
        │          padding (0-7 bytes)          │
        └───────────────────────────────────────┘
        Aligned to 8-byte boundary
```

## Field Descriptions

### magic (4 bytes)

The magic number identifies this structure as an entry header. Must be exactly "ENTR" (bytes: `0x45 0x4E 0x54 0x52`).

### headerVersion (1 byte)

Version of the entry header format. Current version is 1.

This allows future extensions to the entry header while maintaining backward compatibility.

### flags (1 byte)

Bitmask of entry flags:

| Bit | Mask | Flag | Description |
|-----|------|------|-------------|
| 0 | 0x01 | `ENTRY_FLAG_HAS_ATTRIBUTES` | Entry has custom attributes |
| 1 | 0x02 | `ENTRY_FLAG_COMPRESSED` | Entry data is compressed |
| 2 | 0x04 | `ENTRY_FLAG_ENCRYPTED` | Entry data is encrypted |
| 3 | 0x08 | `ENTRY_FLAG_HAS_ECC` | Error correction data present |
| 4-7 | - | Reserved | Reserved for future use |

### entryId (8 bytes, int64)

Unique identifier for this entry within the archive. Entry IDs:
- Must be unique within the archive
- Are used for O(1) lookup by ID
- Typically assigned sequentially starting from 1

### originalSize (8 bytes, int64)

The uncompressed size of the entry data in bytes. This is the size of data that will be returned when reading the entry.

### storedSize (8 bytes, int64)

The stored size of the entry data in bytes. This includes:
- Chunk headers (24 bytes per chunk)
- Compressed/encrypted chunk data
- Any ECC parity data

### chunkCount (4 bytes, int32)

The total number of data chunks for this entry. Entries are split into chunks based on the archive's chunk size.

### compressionId (1 byte)

The compression algorithm used for this entry:

| ID | Algorithm |
|----|-----------|
| 0 | None (uncompressed) |
| 1 | ZSTD |
| 2 | LZ4 |

### encryptionId (1 byte)

The encryption algorithm used for this entry:

| ID | Algorithm |
|----|-----------|
| 0 | None (plaintext) |
| 1 | AES-256-GCM |
| 2 | ChaCha20-Poly1305 |

### nameLength (2 bytes, uint16)

Length of the entry name in bytes (UTF-8 encoded).

**Constraints:**
- Minimum: 1 (empty names not allowed)
- Maximum: 65,535 bytes

### mimeTypeLength (2 bytes, uint16)

Length of the MIME type string in bytes (UTF-8 encoded).

**Constraints:**
- Minimum: 0 (MIME type is optional)
- Maximum: 255 bytes

### attrCount (2 bytes, uint16)

Number of custom attributes attached to this entry.

### headerChecksum (4 bytes, uint32)

CRC32 checksum of the entry header, computed over all bytes except the checksum field itself.

### name (variable)

Entry name encoded as UTF-8. This typically represents a path within the archive.

**Conventions:**
- Use forward slashes (`/`) as path separators
- No leading slash (relative paths)
- Case-sensitive
- No null terminator

**Examples:**
- `config.json`
- `assets/images/logo.png`
- `data/level-001/enemies.bin`

### mimeType (variable)

MIME type string encoded as UTF-8. Provides a hint about the content type.

**Examples:**
- `application/json`
- `image/png`
- `application/octet-stream`

An empty MIME type (length = 0) indicates no type hint is provided.

### attributes (variable)

Custom key-value attributes. See [Attributes](attributes.md) for the encoding format.

### padding (0-7 bytes)

Zero bytes to align the end of the entry header to an 8-byte boundary. This ensures the following chunk data starts at an aligned offset.

**Padding calculation:**
```java
int totalSize = 48 + nameLength + mimeTypeLength + attributesSize;
int padding = (8 - (totalSize % 8)) % 8;
```

## Reading Example

```java
public EntryHeader readEntryHeader(BinaryReader reader) throws ApackException {
    // Validate magic
    byte[] magic = reader.readBytes(4);
    if (!Arrays.equals(magic, "ENTR".getBytes())) {
        throw new FormatException("Invalid entry magic");
    }

    int headerVersion = reader.readUInt8();
    int flags = reader.readUInt8();
    reader.skipBytes(2); // reserved

    long entryId = reader.readInt64();
    long originalSize = reader.readInt64();
    long storedSize = reader.readInt64();
    int chunkCount = reader.readInt32();
    int compressionId = reader.readUInt8();
    int encryptionId = reader.readUInt8();
    int nameLength = reader.readUInt16();
    int mimeTypeLength = reader.readUInt16();
    int attrCount = reader.readUInt16();
    int headerChecksum = reader.readInt32();

    String name = reader.readString(nameLength);
    String mimeType = reader.readString(mimeTypeLength);

    List<Attribute> attributes = new ArrayList<>();
    for (int i = 0; i < attrCount; i++) {
        attributes.add(readAttribute(reader));
    }

    // Skip padding to 8-byte boundary
    reader.skipPadding(8);

    return new EntryHeader(
        headerVersion, flags, entryId,
        originalSize, storedSize, chunkCount,
        compressionId, encryptionId,
        name, mimeType, attributes
    );
}
```

## Validation Rules

1. **Magic** must be exactly "ENTR"
2. **Header version** must be supported (<= current version)
3. **Entry ID** must be positive (> 0)
4. **Original size** must be non-negative
5. **Stored size** must be non-negative
6. **Chunk count** must be positive (> 0) for non-empty entries
7. **Name length** must be > 0
8. **Algorithm IDs** must be known values
9. **Checksum** must match computed value

## Size Calculations

### Header Size

```java
int baseSize = 48;
int variableSize = nameLength + mimeTypeLength + attributesSize;
int padding = (8 - ((baseSize + variableSize) % 8)) % 8;
int totalHeaderSize = baseSize + variableSize + padding;
```

### Entry Total Size

```java
long totalSize = entryHeaderSize + storedSize;
```

---

*Next: [Chunk Format](chunk-format.md) | Previous: [File Header](file-header.md)*
