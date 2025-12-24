# Core Concepts

This document explains the fundamental concepts behind the Aether Pack (APACK) format and library.

## What is APACK?

APACK (Aether Pack) is a binary container format designed to store multiple data entries efficiently. Think of it as a modern alternative to formats like ZIP or TAR, but with a focus on:

- **Streaming access** - Read and write without loading entire files into memory
- **Random access** - Jump directly to any entry without scanning the entire archive
- **Data integrity** - Per-chunk checksums detect corruption early
- **Security** - Authenticated encryption protects both data and metadata
- **Extensibility** - Pluggable algorithms via Service Provider Interfaces

## Entry Concept

An **entry** is the fundamental unit of storage in an APACK archive. Each entry represents a logical piece of data (similar to a file) with associated metadata.

```
┌─────────────────────────────────────────────────────────────────┐
│                            Entry                                │
├─────────────────────────────────────────────────────────────────┤
│  Metadata                                                       │
│  ├── ID: Unique identifier (64-bit)                            │
│  ├── Name: Path within archive ("assets/config.json")          │
│  ├── MIME Type: Content type hint ("application/json")         │
│  ├── Original Size: Uncompressed size in bytes                 │
│  ├── Stored Size: Size after compression/encryption            │
│  ├── Flags: Compression, encryption, ECC status                │
│  └── Attributes: Custom key-value metadata                     │
├─────────────────────────────────────────────────────────────────┤
│  Data (split into chunks)                                       │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐         ┌─────────┐       │
│  │ Chunk 1 │ │ Chunk 2 │ │ Chunk 3 │  ...    │ Chunk N │       │
│  │ (256KB) │ │ (256KB) │ │ (256KB) │         │ (<256KB)│       │
│  └─────────┘ └─────────┘ └─────────┘         └─────────┘       │
└─────────────────────────────────────────────────────────────────┘
```

### Entry Properties

| Property | Description |
|----------|-------------|
| **ID** | Unique 64-bit identifier within the archive |
| **Name** | UTF-8 encoded path (max 65,535 bytes) |
| **MIME Type** | Optional content type hint |
| **Original Size** | Size before compression |
| **Stored Size** | Size in archive (after processing) |
| **Attributes** | Custom key-value metadata |

## Chunking Model

APACK splits entry data into **chunks** - fixed-size blocks that are processed independently. This design provides several benefits:

### Why Chunks?

1. **Memory Efficiency** - Process large files without loading them entirely into memory
2. **Streaming** - Start processing before the entire file is available
3. **Error Isolation** - Corruption affects only specific chunks
4. **Random Access** - Seek to specific portions of an entry
5. **Parallel Processing** - Chunks can be processed concurrently

### Chunk Structure

```
┌──────────────────────────────────────────────────────────────┐
│                       Chunk Header (24 bytes)                │
│  Index │ Original Size │ Stored Size │ Checksum │ Flags     │
├──────────────────────────────────────────────────────────────┤
│                       Chunk Data                             │
│             (compressed and/or encrypted bytes)              │
└──────────────────────────────────────────────────────────────┘
```

### Default Chunk Size

- **Default**: 256 KB
- **Minimum**: 1 KB
- **Maximum**: 64 MB

Larger chunks improve compression ratio but require more memory. Smaller chunks reduce memory usage but may decrease compression efficiency.

## Processing Pipeline

When data is written to an APACK archive, it passes through a processing pipeline:

```
                         WRITING
    ┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐
    │ Original  │ -> │ Compress  │ -> │  Encrypt  │ -> │  Stored   │
    │   Data    │    │  (ZSTD)   │    │ (AES-GCM) │    │   Data    │
    └───────────┘    └───────────┘    └───────────┘    └───────────┘
          │                │                │                │
          ▼                ▼                ▼                ▼
       Checksum         Smaller          Secure          On Disk
       computed          data          ciphertext

                         READING
    ┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐
    │  Stored   │ -> │  Decrypt  │ -> │Decompress │ -> │ Original  │
    │   Data    │    │ (AES-GCM) │    │  (ZSTD)   │    │   Data    │
    └───────────┘    └───────────┘    └───────────┘    └───────────┘
                                             │
                                             ▼
                                         Checksum
                                         verified
```

### Processing Order

**Writing**: Checksum → Compress → Encrypt

**Reading**: Decrypt → Decompress → Verify Checksum

The checksum is computed on the **original uncompressed data**, ensuring data integrity even if compression or encryption has bugs.

## Container Mode vs Stream Mode

APACK supports two operational modes:

### Container Mode (Default)

- Multiple entries with a table of contents (TOC)
- Random access to any entry by name or ID
- Entry count and sizes known upfront
- TOC at end of file (Trailer)

```
┌─────────────┐
│ File Header │
├─────────────┤
│   Entry 1   │
├─────────────┤
│   Entry 2   │
├─────────────┤
│     ...     │
├─────────────┤
│   Entry N   │
├─────────────┤
│   Trailer   │  <- Table of Contents
└─────────────┘
```

### Stream Mode

- Optimized for single-entry streaming
- No random access (sequential only)
- Entry count unknown until end
- Lighter-weight trailer

```
┌─────────────┐
│ File Header │
├─────────────┤
│ Entry Data  │
│  (chunked)  │
├─────────────┤
│Stream Trailer│
└─────────────┘
```

Use **Container Mode** when you need multiple entries or random access. Use **Stream Mode** for single-entry streaming scenarios.

## Random Access via TOC

In Container Mode, the **Table of Contents (TOC)** enables O(1) entry lookup:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Trailer                                  │
├─────────────────────────────────────────────────────────────────┤
│  TOC Entry 1: ID=1, NameHash=0x1234, Offset=64, Size=1024      │
│  TOC Entry 2: ID=2, NameHash=0x5678, Offset=1088, Size=512     │
│  TOC Entry 3: ID=3, NameHash=0x9ABC, Offset=1600, Size=2048    │
│  ...                                                            │
└─────────────────────────────────────────────────────────────────┘
```

### Lookup Process

1. **By ID**: Direct HashMap lookup - O(1)
2. **By Name**:
   - Compute XXH3 hash of name
   - Lookup by hash - O(1) average
   - Verify name matches (handle collisions)

## Checksums

APACK uses checksums at multiple levels for data integrity:

| Location | Algorithm | Purpose |
|----------|-----------|---------|
| File Header | CRC32 | Validate header integrity |
| Chunk Data | XXH3-64 or CRC32 | Detect data corruption |
| TOC | CRC32 | Verify TOC integrity |
| Trailer | CRC32 | Validate trailer |

### Checksum Algorithms

| Algorithm | Size | Speed | Use Case |
|-----------|------|-------|----------|
| CRC32 | 32-bit | Fast | Headers, legacy compatibility |
| XXH3-64 | 64-bit | Very Fast | Chunk data (recommended) |

## Encryption

APACK uses **Authenticated Encryption with Associated Data (AEAD)**, which provides:

- **Confidentiality** - Data is encrypted
- **Integrity** - Tampering is detected
- **Authenticity** - Origin is verified

### Supported Algorithms

| Algorithm | Key Size | Nonce Size | Tag Size |
|-----------|----------|------------|----------|
| AES-256-GCM | 256-bit | 96-bit | 128-bit |
| ChaCha20-Poly1305 | 256-bit | 96-bit | 128-bit |

### Key Derivation

Passwords are converted to encryption keys using Key Derivation Functions (KDFs):

| KDF | Security Level | Recommendation |
|-----|----------------|----------------|
| Argon2id | High (memory-hard) | Recommended |
| PBKDF2-SHA256 | Medium | Fallback only |

## Compression

APACK supports two compression algorithms:

### ZSTD (Recommended)

- Excellent compression ratio
- Very fast decompression
- Levels 1-22 (higher = better ratio, slower compression)

### LZ4

- Fastest compression and decompression
- Lower compression ratio than ZSTD
- Levels 0 (fast) and 1-17 (high compression)

### Compression Decision

Per-chunk compression is automatic:

1. Compress the chunk
2. If compressed size >= original size, store uncompressed
3. Set chunk flag to indicate compression status

This prevents "negative compression" for incompressible data.

## Error Correction (ECC)

Optional **Reed-Solomon error correction** can recover from byte-level corruption:

### How It Works

1. Data is split into blocks (max 239 bytes with 16-byte parity)
2. Parity bytes are computed and appended
3. On read, errors are detected and corrected automatically

### Configuration

| Preset | Parity Bytes | Max Errors | Overhead |
|--------|--------------|------------|----------|
| LOW_OVERHEAD | 8 | 4 | ~3.3% |
| DEFAULT | 16 | 8 | ~6.7% |
| HIGH_REDUNDANCY | 32 | 16 | ~14.3% |

Enable ECC for:
- Long-term archival storage
- Unreliable storage media
- Critical data that must survive corruption

## Byte Order

All multi-byte integers in APACK are stored in **Little-Endian** byte order. This matches the native byte order of x86/x64 processors, minimizing conversion overhead on common platforms.

```
Example: 32-bit integer 0x12345678

Memory layout (Little-Endian):
  Address:    N      N+1    N+2    N+3
  Value:    0x78   0x56   0x34   0x12
            ────────────────────────────>
            Least significant  ->  Most significant
```

## Thread Safety

APACK classes have the following thread safety characteristics:

| Class | Thread-Safe? | Notes |
|-------|--------------|-------|
| AetherPackReader | No | Single underlying channel |
| AetherPackWriter | No | Concurrent writes corrupt output |
| ApackConfiguration | Yes | Immutable |
| Entry / PackEntry | Yes | Immutable |
| EntryMetadata | No | Mutable during writing |
| ChunkProcessor | Yes | Stateless |
| Provider classes | Yes | Stateless |

For multi-threaded scenarios:
- Use separate Reader/Writer instances per thread
- Share only immutable objects (configs, entries)

---

*Next: [Architecture Overview](architecture.md) or [Getting Started](getting-started.md)*
