# Chunk Format Specification

The chunk header is a fixed-size 24-byte structure that precedes each data chunk within an entry. Chunks are independently processed blocks that enable streaming, memory efficiency, and per-chunk error isolation.

## Binary Layout

| Offset | Size | Field | Type | Description |
|--------|------|-------|------|-------------|
| 0x00 | 4 | magic | bytes | Magic number "CHNK" (ASCII) |
| 0x04 | 4 | chunkIndex | int32 | Zero-based chunk index within entry |
| 0x08 | 4 | originalSize | int32 | Uncompressed chunk size in bytes |
| 0x0C | 4 | storedSize | int32 | Stored (compressed) size in bytes |
| 0x10 | 4 | checksum | int32 | Checksum of original data |
| 0x14 | 4 | flags | int32 | Chunk flags (bitmask) |

**Total Size: 24 bytes (0x18)**

## Visual Layout

```
Offset  0x00      0x04      0x08      0x0C
        ┌─────────┬─────────┬─────────┬─────────┐
0x00    │  magic  │  chunk  │ original│  stored │
        │ "CHNK"  │  Index  │  Size   │  Size   │
        │   (4)   │   (4)   │   (4)   │   (4)   │
        ├─────────┴─────────┼─────────┴─────────┤
0x10    │     checksum      │      flags        │
        │       (4)         │       (4)         │
        └───────────────────┴───────────────────┘
0x18    End of chunk header, followed by storedSize bytes of data
```

## Field Descriptions

### magic (4 bytes)

The magic number identifies this structure as a chunk header. Must be exactly "CHNK" (bytes: `0x43 0x48 0x4E 0x4B`).

### chunkIndex (4 bytes, int32)

Zero-based index of this chunk within the entry. The first chunk has index 0, the second has index 1, and so on.

**Purpose:**
- Verify chunks are read in the correct order
- Enable random access to specific chunks within an entry
- Detect missing or corrupted chunks

### originalSize (4 bytes, int32)

The uncompressed size of this chunk's data in bytes.

**Constraints:**
- Typically equals the configured chunk size (default: 256 KB)
- The last chunk may be smaller if entry data doesn't evenly divide
- Maximum: Chunk size configured in file header

### storedSize (4 bytes, int32)

The stored size of this chunk's data in bytes after compression and/or encryption.

**Notes:**
- May be smaller than originalSize when compressed
- May be larger than originalSize for incompressible data stored uncompressed
- Includes any encryption overhead (IV, padding, auth tag) if encrypted

### checksum (4 bytes, int32)

Checksum of the **original uncompressed** chunk data. The algorithm is determined by the `checksumAlgorithm` field in the file header.

| Algorithm ID | Algorithm | Notes |
|--------------|-----------|-------|
| 0 | CRC32 | 32-bit, good compatibility |
| 1 | XXH3-64 | Lower 32 bits used |

**Verification Process:**
1. Read and decompress the chunk data
2. Compute checksum over the decompressed data
3. Compare against stored checksum
4. Reject chunk if mismatch detected

### flags (4 bytes, int32)

Bitmask of chunk flags indicating processing status.

| Bit | Mask | Flag | Description |
|-----|------|------|-------------|
| 0 | 0x01 | `CHUNK_FLAG_LAST` | This is the last chunk of the entry |
| 1 | 0x02 | `CHUNK_FLAG_COMPRESSED` | This chunk is compressed |
| 2 | 0x04 | `CHUNK_FLAG_ENCRYPTED` | This chunk is encrypted |
| 3-31 | - | Reserved | Reserved for future use |

## Chunk Flags Details

### CHUNK_FLAG_LAST (0x01)

Indicates this is the final chunk of the entry. When this flag is set:
- No more chunks follow for the current entry
- The reader should proceed to the next entry or trailer
- The `originalSize` may be smaller than the configured chunk size

### CHUNK_FLAG_COMPRESSED (0x02)

Indicates this chunk's data is compressed.

**Per-Chunk Decision:**
Even if the entry has compression enabled, individual chunks may be stored uncompressed if compression would not reduce their size. This handles:
- Already-compressed data (JPEG, PNG, ZIP)
- Random/encrypted data that doesn't compress well
- Small chunks where compression overhead exceeds savings

### CHUNK_FLAG_ENCRYPTED (0x04)

Indicates this chunk's data is encrypted.

When both compressed and encrypted:
- **Writing order**: Compress → Encrypt
- **Reading order**: Decrypt → Decompress

## Processing Pipeline

### Writing a Chunk

```
Original Data (up to chunkSize bytes)
        │
        ▼
┌───────────────────┐
│ Compute Checksum  │  ← Checksum of original data
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│    Compress?      │  ← Only if compression reduces size
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│    Encrypt?       │  ← If encryption enabled
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│ Write ChunkHeader │  ← 24 bytes
├───────────────────┤
│ Write Chunk Data  │  ← storedSize bytes
└───────────────────┘
```

### Reading a Chunk

```
┌───────────────────┐
│ Read ChunkHeader  │  ← 24 bytes
├───────────────────┤
│ Read Chunk Data   │  ← storedSize bytes
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│    Decrypt?       │  ← If CHUNK_FLAG_ENCRYPTED set
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│   Decompress?     │  ← If CHUNK_FLAG_COMPRESSED set
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│ Verify Checksum   │  ← Compare against stored checksum
└────────┬──────────┘
         │
         ▼
    Original Data
```

## Reading Example

```java
public ChunkData readChunk(BinaryReader reader, ChunkProcessor processor)
        throws ApackException {
    // Validate magic
    byte[] magic = reader.readBytes(4);
    if (!Arrays.equals(magic, "CHNK".getBytes())) {
        throw new FormatException("Invalid chunk magic");
    }

    int chunkIndex = reader.readInt32();
    int originalSize = reader.readInt32();
    int storedSize = reader.readInt32();
    int checksum = reader.readInt32();
    int flags = reader.readInt32();

    // Read chunk data
    byte[] storedData = reader.readBytes(storedSize);

    // Process: decrypt → decompress → verify
    byte[] originalData = processor.process(storedData, flags);

    // Verify checksum
    int computedChecksum = computeChecksum(originalData);
    if (computedChecksum != checksum) {
        throw new ChecksumException(
            "Chunk " + chunkIndex + " checksum mismatch");
    }

    return new ChunkData(chunkIndex, originalData, isLast(flags));
}

private boolean isLast(int flags) {
    return (flags & 0x01) != 0;
}
```

## Writing Example

```java
public void writeChunk(BinaryWriter writer, byte[] data,
        int chunkIndex, boolean isLast, ChunkProcessor processor)
        throws IOException {
    // Compute checksum of original data
    int checksum = computeChecksum(data);

    // Process: compress → encrypt
    ProcessResult result = processor.process(data);

    // Build flags
    int flags = 0;
    if (isLast) flags |= 0x01;
    if (result.isCompressed()) flags |= 0x02;
    if (result.isEncrypted()) flags |= 0x04;

    // Write chunk header
    writer.writeBytes("CHNK".getBytes());
    writer.writeInt32(chunkIndex);
    writer.writeInt32(data.length);           // originalSize
    writer.writeInt32(result.data().length);  // storedSize
    writer.writeInt32(checksum);
    writer.writeInt32(flags);

    // Write chunk data
    writer.writeBytes(result.data());
}
```

## Validation Rules

1. **Magic** must be exactly "CHNK"
2. **Chunk index** must be sequential (0, 1, 2, ...)
3. **Original size** must be positive and ≤ max chunk size
4. **Stored size** must be positive
5. **Flags** reserved bits must be zero
6. **Last chunk** flag must be set on exactly one chunk per entry
7. **Checksum** must match computed value after decompression

## Size Calculations

### Chunk Data Size

```java
// Size of chunk data in archive
int dataSize = header.storedSize();
```

### Total Chunk Size

```java
// Total size including header
int totalSize = CHUNK_HEADER_SIZE + header.storedSize();
// totalSize = 24 + storedSize
```

### Entry Chunk Count

```java
// Number of chunks for an entry
int chunkCount = (int) Math.ceil((double) originalSize / chunkSize);
```

## Encryption Overhead

When encryption is enabled, the stored size includes encryption overhead:

| Algorithm | Overhead |
|-----------|----------|
| AES-256-GCM | 12 (nonce) + 16 (tag) = 28 bytes |
| ChaCha20-Poly1305 | 12 (nonce) + 16 (tag) = 28 bytes |

```java
// Approximate stored size for encrypted chunk
int overhead = 28; // nonce + auth tag
int encryptedSize = compressedSize + overhead;
```

---

*Next: [Trailer Format](trailer.md) | Previous: [Entry Header](entry-header.md)*
