# System Architecture

This document provides a comprehensive overview of the Aether Pack (APACK) library architecture, including module structure, package organization, and data flow.

## Module Structure

APACK is organized as a Maven multi-module project with clear separation of concerns:

```
aether-pack/                           (Parent POM)
│
├── aether-pack-core/                  (Core Module)
│   │   Format definitions, I/O, SPI interfaces
│   │   No external dependencies for algorithms
│   │
│   └── Dependencies: JetBrains Annotations
│
├── aether-pack-compression/           (Compression Module)
│   │   ZSTD and LZ4 providers
│   │   Implements CompressionProvider SPI
│   │
│   └── Dependencies: core, zstd-jni, lz4-java
│
├── aether-pack-crypto/                (Crypto Module)
│   │   AES-GCM, ChaCha20, Argon2id, PBKDF2
│   │   Implements EncryptionProvider SPI
│   │
│   └── Dependencies: core, BouncyCastle
│
└── aether-pack-cli/                   (CLI Module)
        Command-line interface
        Depends on all other modules

        Dependencies: core, compression, crypto, Picocli
```

## Module Dependency Graph

```
                    ┌──────────────────┐
                    │  aether-pack-cli │
                    │   (Application)  │
                    └────────┬─────────┘
                             │
            ┌────────────────┼────────────────┐
            │                │                │
            ▼                ▼                ▼
    ┌───────────────┐ ┌───────────────┐ ┌───────────────┐
    │  compression  │ │    crypto     │ │     core      │
    │    (ZSTD,LZ4) │ │ (AES,ChaCha)  │ │   (Format)    │
    └───────┬───────┘ └───────┬───────┘ └───────────────┘
            │                 │                 ▲
            └─────────────────┴─────────────────┘
                        depends on core
```

## Core Module Architecture

The core module contains the fundamental APACK functionality:

```
de.splatgames.aether.pack.core/
│
├── AetherPackReader.java          # Main reader class
├── AetherPackWriter.java          # Main writer class
├── ApackConfiguration.java        # Configuration record
│
├── format/                        # Binary format definitions
│   ├── FormatConstants.java       # Magic numbers, IDs, flags
│   ├── FileHeader.java            # 64-byte file header
│   ├── EntryHeader.java           # Variable-size entry header
│   ├── ChunkHeader.java           # 24-byte chunk header
│   ├── Trailer.java               # Container trailer
│   ├── StreamTrailer.java         # Stream mode trailer
│   ├── TocEntry.java              # TOC entry (40 bytes)
│   ├── EncryptionBlock.java       # Encryption metadata
│   └── Attribute.java             # Custom attributes
│
├── io/                            # Binary I/O operations
│   ├── BinaryReader.java          # Little-endian reader
│   ├── BinaryWriter.java          # Little-endian writer
│   ├── HeaderIO.java              # Header read/write
│   ├── TrailerIO.java             # Trailer read/write
│   ├── ChunkedInputStream.java    # Chunked reading
│   ├── ChunkedOutputStream.java   # Chunked writing
│   ├── ChunkProcessor.java        # Compression/encryption
│   └── ChunkSecuritySettings.java # Validation settings
│
├── entry/                         # Entry abstraction
│   ├── Entry.java                 # Entry interface
│   ├── PackEntry.java             # Immutable read entry
│   └── EntryMetadata.java         # Mutable write entry
│
├── spi/                           # Service Provider Interfaces
│   ├── CompressionProvider.java   # Compression SPI
│   ├── EncryptionProvider.java    # Encryption SPI
│   └── ChecksumProvider.java      # Checksum SPI
│
├── checksum/                      # Checksum implementations
│   ├── ChecksumRegistry.java      # Provider registry
│   ├── Crc32Checksum.java         # CRC-32 implementation
│   └── XxHash3Checksum.java       # XXH3-64 implementation
│
├── ecc/                           # Error correction
│   ├── ErrorCorrectionCodec.java  # ECC interface
│   ├── ReedSolomonCodec.java      # Reed-Solomon implementation
│   ├── EccConfiguration.java      # ECC configuration
│   └── GaloisField.java           # GF(2^8) arithmetic
│
├── pipeline/                      # Processing pipeline
│   ├── ProcessingPipeline.java    # Pipeline builder
│   ├── PipelineStage.java         # Stage interface
│   ├── PipelineContext.java       # Shared state
│   ├── CompressionStage.java      # Compression stage
│   ├── EncryptionStage.java       # Encryption stage
│   └── ChecksumStage.java         # Checksum stage
│
└── exception/                     # Exception hierarchy
    ├── ApackException.java        # Base exception
    ├── FormatException.java       # Format errors
    ├── ChecksumException.java     # Checksum failures
    ├── CompressionException.java  # Compression errors
    ├── EncryptionException.java   # Encryption errors
    └── EntryNotFoundException.java # Entry not found
```

## Class Hierarchy

### Entry Hierarchy

```
                    <<interface>>
                       Entry
                         │
           ┌─────────────┴─────────────┐
           │                           │
      PackEntry                   EntryMetadata
    (immutable)                    (mutable)
    For reading                   For writing
```

### Exception Hierarchy

```
                    Exception
                        │
                  ApackException
                        │
        ┌───────┬───────┼───────┬───────────┐
        │       │       │       │           │
   Format   Checksum Compression Encryption EntryNot
  Exception Exception Exception  Exception  Found
        │                                  Exception
        │
  Unsupported
   Version
  Exception
```

### Provider Hierarchy

```
<<interface>>               <<interface>>              <<interface>>
CompressionProvider         EncryptionProvider         ChecksumProvider
       │                           │                          │
       ├── ZstdProvider           ├── Aes256GcmProvider      ├── Crc32Checksum
       └── Lz4Provider            └── ChaCha20Provider       └── XxHash3Checksum
```

## Data Flow: Writing

The following diagram shows the data flow when writing an entry to an APACK archive:

```
┌─────────────┐
│ Application │
│   Data      │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│                    AetherPackWriter                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   addEntry()                             │   │
│  │  1. Create EntryMetadata                                 │   │
│  │  2. Write EntryHeader                                    │   │
│  │  3. Create ChunkedOutputStream                           │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           │                                     │
│  ┌────────────────────────▼────────────────────────────────┐   │
│  │               ChunkedOutputStream                        │   │
│  │  For each chunk:                                         │   │
│  │  1. Buffer chunk data (up to chunkSize)                  │   │
│  │  2. Compute checksum on raw data                         │   │
│  │  3. Pass to ChunkProcessor                               │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           │                                     │
│  ┌────────────────────────▼────────────────────────────────┐   │
│  │                 ChunkProcessor                           │   │
│  │  1. Compress (if enabled)                                │   │
│  │     - Skip if compressed >= original                     │   │
│  │  2. Encrypt (if enabled)                                 │   │
│  │     - Generate nonce                                     │   │
│  │     - Encrypt with AEAD                                  │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           │                                     │
│  ┌────────────────────────▼────────────────────────────────┐   │
│  │               HeaderIO.writeChunkHeader()                │   │
│  │  Write 24-byte chunk header:                             │   │
│  │  - Index, original size, stored size                     │   │
│  │  - Checksum, flags                                       │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           │                                     │
└───────────────────────────┼─────────────────────────────────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │  Output Stream  │
                   │   (File/etc)    │
                   └─────────────────┘
```

## Data Flow: Reading

The following diagram shows the data flow when reading an entry from an APACK archive:

```
┌─────────────────┐
│ SeekableChannel │
│   (File/etc)    │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    AetherPackReader                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   open()                                 │   │
│  │  1. Read & validate FileHeader                           │   │
│  │  2. Read EncryptionBlock (if encrypted)                  │   │
│  │  3. Seek to trailerOffset                                │   │
│  │  4. Read Trailer & TOC                                   │   │
│  │  5. Build entry lookup maps                              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │               getInputStream(entry)                      │   │
│  │  1. Seek to entry offset                                 │   │
│  │  2. Create ChunkedInputStream                            │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           │                                     │
│  ┌────────────────────────▼────────────────────────────────┐   │
│  │               ChunkedInputStream                         │   │
│  │  For each chunk:                                         │   │
│  │  1. Read ChunkHeader (24 bytes)                          │   │
│  │  2. Read chunk data (storedSize bytes)                   │   │
│  │  3. Pass to ChunkProcessor                               │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           │                                     │
│  ┌────────────────────────▼────────────────────────────────┐   │
│  │                 ChunkProcessor                           │   │
│  │  1. Decrypt (if encrypted)                               │   │
│  │     - Extract nonce                                      │   │
│  │     - Decrypt with AEAD                                  │   │
│  │     - Verify auth tag                                    │   │
│  │  2. Decompress (if compressed)                           │   │
│  │  3. Verify checksum                                      │   │
│  └────────────────────────┬────────────────────────────────┘   │
│                           │                                     │
└───────────────────────────┼─────────────────────────────────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │   Application   │
                   │      Data       │
                   └─────────────────┘
```

## Provider Registration

APACK uses the Java ServiceLoader mechanism for provider discovery:

```
┌─────────────────────────────────────────────────────────────────┐
│                      Registry (e.g., ChecksumRegistry)          │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │  Static Initialization                                     │ │
│  │  1. Register built-in providers                            │ │
│  │  2. ServiceLoader.load(ChecksumProvider.class)             │ │
│  │  3. Register discovered providers                          │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │  Lookup Maps                                               │ │
│  │  ┌─────────────────┐    ┌─────────────────┐               │ │
│  │  │   BY_ID (int)   │    │ BY_NAME (String)│               │ │
│  │  ├─────────────────┤    ├─────────────────┤               │ │
│  │  │ 0 -> CRC32      │    │ crc32 -> CRC32  │               │ │
│  │  │ 1 -> XXH3-64    │    │ xxh3-64 -> XXH3 │               │ │
│  │  └─────────────────┘    └─────────────────┘               │ │
│  └───────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### ServiceLoader Configuration

To register a custom provider, create a file in `META-INF/services/`:

```
META-INF/
└── services/
    ├── de.splatgames.aether.pack.core.spi.CompressionProvider
    ├── de.splatgames.aether.pack.core.spi.EncryptionProvider
    └── de.splatgames.aether.pack.core.spi.ChecksumProvider
```

Each file contains fully-qualified class names of implementations:

```
# de.splatgames.aether.pack.core.spi.CompressionProvider
de.splatgames.aether.pack.compression.ZstdCompressionProvider
de.splatgames.aether.pack.compression.Lz4CompressionProvider
```

## Processing Pipeline

The `ProcessingPipeline` provides a flexible, composable approach to data transformation:

```
┌─────────────────────────────────────────────────────────────────┐
│                    ProcessingPipeline                           │
│                                                                 │
│  Stages (ordered by priority):                                  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Priority 100: ChecksumStage                             │   │
│  │  - Computes checksum on raw data                         │   │
│  │  - Stores result in PipelineContext                      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                           │                                     │
│                           ▼                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Priority 200: CompressionStage                          │   │
│  │  - Wraps output with compression stream                  │   │
│  │  - Skips if compressed >= original                       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                           │                                     │
│                           ▼                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Priority 300: EncryptionStage                           │   │
│  │  - Wraps output with encryption stream                   │   │
│  │  - Generates nonce, appends auth tag                     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Memory Management

APACK is designed for efficient memory usage:

### Chunked Processing

- Only one chunk is held in memory at a time
- Chunk size is configurable (default 256 KB)
- Large files processed without loading entirely

### Buffer Reuse

- Internal buffers are reused across operations
- Reduces garbage collection pressure

### Stream-Based API

- Reader provides `InputStream` for streaming reads
- Writer accepts `InputStream` for streaming writes
- No need to materialize entire entries

## Thread Safety Considerations

### Safe for Sharing

- `ApackConfiguration` - Immutable record
- `Entry` / `PackEntry` - Immutable
- Provider implementations - Stateless
- `ChunkProcessor` - Stateless after construction

### Not Thread-Safe

- `AetherPackReader` - Single underlying channel
- `AetherPackWriter` - Shared output state
- `EntryMetadata` - Mutable size fields
- `Checksum` calculators - Stateful

### Recommended Patterns

```java
// Good: Each thread has its own reader
ExecutorService executor = Executors.newFixedThreadPool(4);
for (int i = 0; i < 4; i++) {
    executor.submit(() -> {
        try (AetherPackReader reader = AetherPackReader.open(path)) {
            // Process entries
        }
    });
}

// Bad: Sharing reader across threads
AetherPackReader sharedReader = AetherPackReader.open(path);
executor.submit(() -> sharedReader.readAllBytes("a.txt")); // Race condition!
executor.submit(() -> sharedReader.readAllBytes("b.txt")); // Race condition!
```

## External Dependencies

### Core Module
- **JetBrains Annotations** - @NotNull, @Nullable

### Compression Module
- **zstd-jni** (1.5.5-11) - ZSTD native bindings
- **lz4-java** (1.8.0) - LZ4 implementation

### Crypto Module
- **BouncyCastle** (bcprov-jdk18on) - ChaCha20, Argon2id

### CLI Module
- **Picocli** (4.7.5) - Command-line parsing

---

*Next: [Format Specification](format/README.md) or [API Reference](api/README.md)*
