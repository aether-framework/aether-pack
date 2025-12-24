# Trailer Specification

The trailer is located at the end of an APACK archive and provides archive summary information. APACK supports two trailer types depending on the archive mode.

## Trailer Types

| Mode | Trailer | Size | Purpose |
|------|---------|------|---------|
| Container | `Trailer` | 64 + N×40 bytes | Full TOC for random access |
| Stream | `StreamTrailer` | 32 bytes | Summary only, no TOC |

---

## Container Trailer

The container trailer contains the Table of Contents (TOC) enabling O(1) entry lookup by ID or name hash.

### Binary Layout

| Offset | Size | Field | Type | Description |
|--------|------|-------|------|-------------|
| 0x00 | 4 | magic | bytes | Magic number "ATRL" (ASCII) |
| 0x04 | 4 | trailerVersion | int32 | Trailer format version (current: 1) |
| 0x08 | 8 | tocOffset | int64 | Offset to TOC relative to trailer start |
| 0x10 | 8 | tocSize | int64 | Size of TOC in bytes |
| 0x18 | 8 | entryCount | int64 | Number of entries in TOC |
| 0x20 | 8 | totalOriginalSize | int64 | Sum of all entry original sizes |
| 0x28 | 8 | totalStoredSize | int64 | Sum of all entry stored sizes |
| 0x30 | 4 | tocChecksum | uint32 | CRC32 of entire TOC |
| 0x34 | 4 | trailerChecksum | uint32 | CRC32 of trailer header (bytes 0x00-0x33) |
| 0x38 | 8 | fileSize | int64 | Total file size for verification |
| 0x40 | N×40 | tocEntries | TocEntry[] | Table of Contents entries |

**Header Size: 64 bytes (0x40)**
**Total Size: 64 + (entryCount × 40) bytes**

### Visual Layout

```
Offset  0x00      0x04      0x08      0x0C
        ┌─────────┬─────────┬─────────┬─────────┐
0x00    │  magic  │ trailer │       tocOffset   │
        │ "ATRL"  │ Version │                   │
        │   (4)   │   (4)   │        (8)        │
        ├─────────┴─────────┼───────────────────┤
0x10    │      tocSize      │    entryCount     │
        │        (8)        │        (8)        │
        ├───────────────────┼───────────────────┤
0x20    │ totalOriginalSize │  totalStoredSize  │
        │        (8)        │        (8)        │
        ├───────────────────┼───────────────────┤
0x30    │ toc │trailer│       fileSize          │
        │ csum│ csum  │         (8)             │
        │ (4) │  (4)  │                         │
        ├─────┴───────┴─────────────────────────┤
0x40    │           TOC Entry 1 (40 bytes)      │
        ├───────────────────────────────────────┤
0x68    │           TOC Entry 2 (40 bytes)      │
        ├───────────────────────────────────────┤
        │                  ...                  │
        └───────────────────────────────────────┘
```

### Field Descriptions

#### magic (4 bytes)
Must be exactly "ATRL" (bytes: `0x41 0x54 0x52 0x4C`).

#### trailerVersion (4 bytes, int32)
Version of the trailer format. Current version is 1.

#### tocOffset (8 bytes, int64)
Byte offset from the start of the trailer to the first TOC entry. Typically equals the header size (64 bytes).

#### tocSize (8 bytes, int64)
Total size of all TOC entries in bytes.
```java
tocSize = entryCount * 40;
```

#### entryCount (8 bytes, int64)
Number of entries in the archive and TOC.

#### totalOriginalSize (8 bytes, int64)
Sum of all entry original (uncompressed) sizes. Used for overall compression ratio:
```java
double ratio = (double) totalStoredSize / totalOriginalSize * 100;
```

#### totalStoredSize (8 bytes, int64)
Sum of all entry stored (compressed) sizes.

#### tocChecksum (4 bytes, uint32)
CRC32 checksum of the entire TOC (all TOC entries).

#### trailerChecksum (4 bytes, uint32)
CRC32 checksum of the trailer header (bytes 0x00-0x33, excluding the checksum itself).

#### fileSize (8 bytes, int64)
Total archive file size. Used to verify the archive is complete and not truncated.

---

## TOC Entry

Each TOC entry is a fixed 40-byte structure enabling random access to entries.

### Binary Layout

| Offset | Size | Field | Type | Description |
|--------|------|-------|------|-------------|
| 0x00 | 8 | entryId | int64 | Unique entry identifier |
| 0x08 | 8 | entryOffset | int64 | Absolute file offset to entry header |
| 0x10 | 8 | originalSize | int64 | Original (uncompressed) size |
| 0x18 | 8 | storedSize | int64 | Stored (compressed) size |
| 0x20 | 4 | nameHash | int32 | XXH3-32 hash of entry name |
| 0x24 | 4 | entryChecksum | int32 | CRC32 of entry header |

**Total Size: 40 bytes (0x28)**

### Visual Layout

```
Offset  0x00      0x04      0x08      0x0C
        ┌─────────┬─────────┬─────────┬─────────┐
0x00    │       entryId     │     entryOffset   │
        │         (8)       │        (8)        │
        ├───────────────────┼───────────────────┤
0x10    │    originalSize   │    storedSize     │
        │         (8)       │        (8)        │
        ├───────────────────┼───────────────────┤
0x20    │    nameHash       │  entryChecksum    │
        │       (4)         │       (4)         │
        └───────────────────┴───────────────────┘
0x28    End of TOC entry
```

### Field Descriptions

#### entryId (8 bytes, int64)
Unique identifier for this entry. Matches `EntryHeader.entryId`.

#### entryOffset (8 bytes, int64)
Absolute file offset to the entry header. Seek to this position to read the entry.

#### originalSize (8 bytes, int64)
Uncompressed size of entry data. Matches `EntryHeader.originalSize`.

#### storedSize (8 bytes, int64)
Compressed/encrypted size of entry data. Matches `EntryHeader.storedSize`.

#### nameHash (4 bytes, int32)
XXH3-32 hash of the entry name (UTF-8 bytes). Enables fast name-based lookup without reading entry headers.

**Lookup Process:**
1. Compute XXH3-32 of desired entry name
2. Scan TOC entries for matching `nameHash`
3. For matches, read actual entry name from `entryOffset`
4. Compare names to confirm (handles hash collisions)

#### entryChecksum (4 bytes, int32)
CRC32 of the entry header. Used for early integrity validation.

---

## Stream Trailer

The stream trailer is a simplified 32-byte structure for stream-mode archives (single entry, no random access).

### Binary Layout

| Offset | Size | Field | Type | Description |
|--------|------|-------|------|-------------|
| 0x00 | 4 | magic | bytes | Magic number "STRL" (ASCII) |
| 0x04 | 4 | reserved | int32 | Reserved for future use |
| 0x08 | 8 | originalSize | int64 | Original (uncompressed) size |
| 0x10 | 8 | storedSize | int64 | Stored (compressed) size |
| 0x18 | 4 | chunkCount | int32 | Total number of chunks |
| 0x1C | 4 | trailerChecksum | uint32 | CRC32 of trailer (bytes 0x00-0x1B) |

**Total Size: 32 bytes (0x20)**

### Visual Layout

```
Offset  0x00      0x04      0x08      0x0C
        ┌─────────┬─────────┬─────────┬─────────┐
0x00    │  magic  │reserved │    originalSize   │
        │ "STRL"  │   (4)   │        (8)        │
        │   (4)   │         │                   │
        ├─────────┴─────────┼───────────────────┤
0x10    │    storedSize     │ chunk │ trailer   │
        │        (8)        │ Count │ Checksum  │
        │                   │  (4)  │    (4)    │
        └───────────────────┴───────┴───────────┘
0x20    End of stream trailer
```

### Field Descriptions

#### magic (4 bytes)
Must be exactly "STRL" (bytes: `0x53 0x54 0x52 0x4C`).

#### reserved (4 bytes)
Reserved for future use. Must be zero.

#### originalSize (8 bytes, int64)
Total uncompressed size of the stream data.

#### storedSize (8 bytes, int64)
Total compressed/encrypted size of the stream data.

#### chunkCount (4 bytes, int32)
Total number of chunks written to the stream.

#### trailerChecksum (4 bytes, uint32)
CRC32 of the trailer (bytes 0x00-0x1B, excluding the checksum itself).

---

## Reading Container Trailer

```java
public Trailer readTrailer(SeekableByteChannel channel) throws ApackException {
    // Seek to trailer (offset from file header)
    channel.position(fileHeader.trailerOffset());

    BinaryReader reader = new BinaryReader(channel);

    // Validate magic
    byte[] magic = reader.readBytes(4);
    if (!Arrays.equals(magic, "ATRL".getBytes())) {
        throw new FormatException("Invalid trailer magic");
    }

    int trailerVersion = reader.readInt32();
    long tocOffset = reader.readInt64();
    long tocSize = reader.readInt64();
    long entryCount = reader.readInt64();
    long totalOriginalSize = reader.readInt64();
    long totalStoredSize = reader.readInt64();
    int tocChecksum = reader.readInt32();
    int trailerChecksum = reader.readInt32();
    long fileSize = reader.readInt64();

    // Read TOC entries
    List<TocEntry> tocEntries = new ArrayList<>();
    for (int i = 0; i < entryCount; i++) {
        tocEntries.add(readTocEntry(reader));
    }

    // Verify TOC checksum
    int computedTocChecksum = computeTocChecksum(tocEntries);
    if (computedTocChecksum != tocChecksum) {
        throw new ChecksumException("TOC checksum mismatch");
    }

    return new Trailer(
        trailerVersion, tocOffset, tocSize, entryCount,
        totalOriginalSize, totalStoredSize,
        tocChecksum, trailerChecksum, fileSize, tocEntries
    );
}

private TocEntry readTocEntry(BinaryReader reader) throws IOException {
    return TocEntry.builder()
        .entryId(reader.readInt64())
        .entryOffset(reader.readInt64())
        .originalSize(reader.readInt64())
        .storedSize(reader.readInt64())
        .nameHash(reader.readInt32())
        .entryChecksum(reader.readInt32())
        .build();
}
```

## Entry Lookup by Name

```java
public Optional<PackEntry> findEntry(String name) {
    // Compute name hash
    int targetHash = computeXxh3Hash(name);

    // Search TOC
    for (TocEntry tocEntry : trailer.tocEntries()) {
        if (tocEntry.nameHash() == targetHash) {
            // Read actual entry header to confirm name
            EntryHeader header = readEntryHeaderAt(tocEntry.entryOffset());
            if (header.name().equals(name)) {
                return Optional.of(new PackEntry(header, tocEntry));
            }
        }
    }
    return Optional.empty();
}
```

## Validation Rules

### Container Trailer
1. **Magic** must be exactly "ATRL"
2. **Trailer version** must be supported (≤ current version)
3. **TOC size** must equal `entryCount × 40`
4. **TOC checksum** must match computed CRC32
5. **Trailer checksum** must match computed CRC32
6. **File size** must match actual file size
7. **Entry offsets** must be within file bounds

### Stream Trailer
1. **Magic** must be exactly "STRL"
2. **Reserved** field must be zero
3. **Chunk count** must be positive for non-empty streams
4. **Trailer checksum** must match computed CRC32
5. **Original size** must be non-negative
6. **Stored size** must be non-negative

---

*Next: [Encryption Block](encryption-block.md) | Previous: [Chunk Format](chunk-format.md)*
