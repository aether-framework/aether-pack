# APACK Binary Format Specification

This section provides a complete specification of the Aether Pack (APACK) binary file format.

## Format Overview

APACK is a binary container format designed for efficient storage of multiple data entries with optional compression, encryption, and error correction. The format uses a chunked storage model where large entries are split into independently-processed blocks.

## Design Goals

1. **Streaming Support** - Write entries without knowing total size upfront
2. **Random Access** - Jump directly to any entry via table of contents
3. **Data Integrity** - Checksums at multiple levels detect corruption
4. **Security** - Authenticated encryption protects data and metadata
5. **Extensibility** - Reserved fields and algorithm IDs allow future growth
6. **Efficiency** - Little-endian encoding matches common processor architectures

## File Layout

### Container Mode (Default)

```
┌─────────────────────────────────────────────────────────────────┐
│                     File Header (64 bytes)                      │
│   Magic "APACK" │ Version │ Flags │ Chunk Size │ Entry Count   │
├─────────────────────────────────────────────────────────────────┤
│                   Encryption Block (optional)                   │
│              KDF Parameters │ Salt │ Wrapped Key                │
├─────────────────────────────────────────────────────────────────┤
│                          Entry 1                                │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │ Entry Header: Name, MIME Type, Sizes, Attributes          │ │
│  ├───────────────────────────────────────────────────────────┤ │
│  │ Chunk 1: Header (24 bytes) + Data                         │ │
│  │ Chunk 2: Header (24 bytes) + Data                         │ │
│  │ ...                                                       │ │
│  │ Chunk N: Header (24 bytes) + Data (LAST_CHUNK flag)       │ │
│  └───────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                          Entry 2                                │
│                            ...                                  │
├─────────────────────────────────────────────────────────────────┤
│                          Entry N                                │
├─────────────────────────────────────────────────────────────────┤
│                     Trailer (48+ bytes)                         │
│        Table of Contents │ Statistics │ Integrity Check         │
└─────────────────────────────────────────────────────────────────┘
```

### Stream Mode

```
┌─────────────────────────────────────────────────────────────────┐
│                     File Header (64 bytes)                      │
├─────────────────────────────────────────────────────────────────┤
│                   Encryption Block (optional)                   │
├─────────────────────────────────────────────────────────────────┤
│                       Entry Header                              │
├─────────────────────────────────────────────────────────────────┤
│                       Chunk 1...N                               │
├─────────────────────────────────────────────────────────────────┤
│                   Stream Trailer (32 bytes)                     │
└─────────────────────────────────────────────────────────────────┘
```

## Byte Order

All multi-byte integer values are stored in **Little-Endian** byte order.

```
Example: 32-bit integer 0x12345678

Byte offset:    0      1      2      3
Byte value:   0x78   0x56   0x34   0x12
              └─ LSB              MSB ─┘
```

## String Encoding

All strings are encoded as **UTF-8** without null terminators. String lengths are stored as separate length-prefix fields.

## Alignment

Entry headers are aligned to **8-byte boundaries** using zero-padding after the last attribute.

## Version Information

| Field | Current Value |
|-------|---------------|
| Format Major Version | 1 |
| Format Minor Version | 0 |
| Format Patch Version | 0 |
| Compatibility Level | 1 |

The compatibility level indicates the minimum reader version required to properly interpret the file.

## Structure Reference

| Structure | Size | Description |
|-----------|------|-------------|
| [File Header](file-header.md) | 64 bytes | Archive identification and global settings |
| [Encryption Block](encryption-block.md) | ~100 bytes | Key derivation and encryption parameters |
| [Entry Header](entry-header.md) | Variable | Entry metadata (name, MIME, sizes, attributes) |
| [Chunk Header](chunk-format.md) | 24 bytes | Chunk metadata and checksum |
| [Trailer](trailer.md) | 48+ bytes | Table of contents (Container mode) |
| [Stream Trailer](trailer.md#stream-trailer) | 32 bytes | Summary (Stream mode) |
| [TOC Entry](trailer.md#toc-entry) | 40 bytes | Single entry in table of contents |
| [Attribute](attributes.md) | Variable | Custom key-value metadata |

## Constants Reference

See [Constants Reference](constants.md) for:
- Magic numbers
- Algorithm IDs (compression, encryption, checksum, KDF)
- Flag definitions (file, entry, chunk)
- Size limits

## Processing Order

### Writing

```
Original Data
     │
     ▼
Compute Checksum (on original data)
     │
     ▼
Compress (if enabled)
     │
     ▼
Encrypt (if enabled)
     │
     ▼
Write Chunk Header + Data
```

### Reading

```
Read Chunk Header + Data
     │
     ▼
Decrypt (if encrypted)
     │
     ▼
Decompress (if compressed)
     │
     ▼
Verify Checksum (against original data)
     │
     ▼
Return Original Data
```

## Checksums

| Location | Algorithm | Coverage |
|----------|-----------|----------|
| File Header | CRC32 | Bytes 0x00-0x0F |
| Entry Header | CRC32 | Entire header |
| Chunk Data | Configurable | Uncompressed chunk data |
| TOC | CRC32 | All TOC entries |
| Trailer | CRC32 | Trailer header |

## Example: Minimal Archive

A minimal APACK archive with one small entry:

```
Offset    Hex                                              ASCII
────────────────────────────────────────────────────────────────
0x0000    41 50 41 43 4B 01 00 00  01 04 01 00 00 00 04 00  APACK...........
0x0010    XX XX XX XX 01 00 00 00  00 00 00 00 40 00 00 00  ............@...
0x0020    00 00 00 00 XX XX XX XX  XX XX XX XX 00 00 00 00  ................
0x0030    00 00 00 00 00 00 00 00  00 00 00 00 00 00 00 00  ................

0x0040    45 4E 54 52 01 00 00 00  01 00 00 00 00 00 00 00  ENTR............
0x0050    0D 00 00 00 00 00 00 00  0D 00 00 00 00 00 00 00  ................
0x0060    01 00 00 00 00 00 08 00  00 00 XX XX XX XX 68 65  ..............he
0x0070    6C 6C 6F 2E 74 78 74 00  00 00 00 00 00 00 00 00  llo.txt.........

0x0080    43 48 4E 4B 00 00 00 00  0D 00 00 00 0D 00 00 00  CHNK............
0x0090    XX XX XX XX 01 00 00 00  48 65 6C 6C 6F 2C 20 57  ........Hello, W
0x00A0    6F 72 6C 64 21 00 00 00                           orld!...

0x00A8    41 54 52 4C 01 00 00 00  ...                      ATRL....
         [Trailer continues...]
```

---

*Continue to: [File Header](file-header.md)*
