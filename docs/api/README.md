# API Documentation

This section provides detailed documentation for the Aether Pack Java API.

## Overview

The APACK API is designed around these core concepts:

- **Reader/Writer pattern** - Separate classes for reading and writing archives
- **Configuration objects** - Immutable settings for format options
- **Provider pattern** - Pluggable compression, encryption, and checksum algorithms
- **Streaming support** - Process large files without loading them entirely into memory

## Package Structure

```
de.splatgames.aether.pack.core
├── AetherPackReader         # Read existing archives
├── AetherPackWriter         # Create new archives
├── ApackConfiguration       # Configuration settings
├── entry/
│   ├── Entry                # Entry interface (read)
│   ├── PackEntry            # Entry implementation
│   └── EntryMetadata        # Entry metadata (write)
├── io/
│   ├── ChunkProcessor       # Chunk processing pipeline
│   ├── ChunkedInputStream   # Streaming decompression/decryption
│   └── ChunkedOutputStream  # Streaming compression/encryption
├── spi/
│   ├── CompressionProvider  # Compression algorithm interface
│   ├── EncryptionProvider   # Encryption algorithm interface
│   └── ChecksumProvider     # Checksum algorithm interface
└── exception/
    └── ApackException       # Exception hierarchy
```

## Quick Reference

### Creating Archives

```java
// Simple archive
try (AetherPackWriter writer = AetherPackWriter.create(Path.of("archive.apack"))) {
    writer.addEntry("file.txt", inputStream);
    writer.addEntry("data.bin", byteArray);
    writer.addEntry("document.pdf", Path.of("/path/to/file.pdf"));
}

// With compression
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd(), 6)
    .build();

try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
    writer.addEntry("data.bin", inputStream);
}

// With encryption
ApackConfiguration config = ApackConfiguration.builder()
    .encryption(EncryptionRegistry.aes256Gcm(), secretKey)
    .build();
```

### Reading Archives

```java
// Simple reading
try (AetherPackReader reader = AetherPackReader.open(Path.of("archive.apack"))) {
    for (Entry entry : reader) {
        byte[] data = reader.readAllBytes(entry);
    }
}

// Random access by name
try (AetherPackReader reader = AetherPackReader.open(path)) {
    Optional<Entry> entry = reader.getEntry("config.json");
    if (entry.isPresent()) {
        try (InputStream input = reader.getInputStream(entry.get())) {
            // Process data
        }
    }
}

// With configured processor
ChunkProcessor processor = ChunkProcessor.builder()
    .compression(CompressionRegistry.zstd())
    .build();

try (AetherPackReader reader = AetherPackReader.open(path, processor)) {
    // Data is automatically decompressed
}
```

## Thread Safety

| Class | Thread-Safe | Notes |
|-------|-------------|-------|
| `AetherPackReader` | No | Single channel, serialize access |
| `AetherPackWriter` | No | Single output, serialize access |
| `ApackConfiguration` | Yes | Immutable record |
| `Entry` / `PackEntry` | Yes | Immutable |
| `EntryMetadata` | No | Mutable during construction |
| `ChunkProcessor` | Yes | Stateless processing |
| Provider classes | Yes | Stateless |

## Resource Management

Both `AetherPackReader` and `AetherPackWriter` implement `Closeable`. Always use try-with-resources:

```java
try (AetherPackReader reader = AetherPackReader.open(path);
     InputStream input = reader.getInputStream(entry)) {
    // Process data
}
// Resources automatically released
```

**Warning:** Failure to close `AetherPackWriter` results in an incomplete archive.

## Error Handling

All operations may throw:

- `IOException` - I/O errors during reading/writing
- `ApackException` - Format errors, validation failures

```java
try {
    reader = AetherPackReader.open(path);
} catch (FormatException e) {
    // Invalid or corrupted archive
} catch (ChecksumException e) {
    // Data integrity failure
} catch (EntryNotFoundException e) {
    // Entry not found
} catch (IOException e) {
    // I/O error
}
```

## API Reference

| Document | Description |
|----------|-------------|
| [Reader](reader.md) | `AetherPackReader` - Opening and reading archives |
| [Writer](writer.md) | `AetherPackWriter` - Creating archives |
| [Configuration](configuration.md) | `ApackConfiguration` - Format settings |
| [Entries](entries.md) | `Entry` and `EntryMetadata` |
| [ChunkProcessor](chunk-processor.md) | Processing pipeline |

---

*Next: [Reader API](reader.md)*
