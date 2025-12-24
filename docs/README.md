# Aether Pack (APACK) Documentation

Welcome to the official documentation for **Aether Pack (APACK)** - a modern binary container format library for Java, designed for efficient storage, streaming access, and data integrity.

## Overview

APACK is a high-performance archive format that provides:

- **Chunked Storage** - Large files are split into manageable chunks for streaming and random access
- **Compression** - Optional ZSTD or LZ4 compression with configurable levels
- **Encryption** - AES-256-GCM or ChaCha20-Poly1305 authenticated encryption
- **Error Correction** - Optional Reed-Solomon error correction for data recovery
- **Integrity Verification** - Per-chunk checksums (CRC32 or XXH3-64)
- **Custom Metadata** - Arbitrary key-value attributes per entry
- **Random Access** - O(1) entry lookup via table of contents

## Quick Links

### Getting Started

- [**Getting Started Guide**](getting-started.md) - Installation and first steps
- [**Core Concepts**](concepts.md) - Understanding APACK fundamentals
- [**Architecture Overview**](architecture.md) - System design and module structure

### Binary Format Specification

- [**Format Overview**](format/README.md) - High-level format structure
- [**File Header**](format/file-header.md) - 64-byte file header specification
- [**Entry Header**](format/entry-header.md) - Variable-size entry metadata
- [**Chunk Format**](format/chunk-format.md) - 24-byte chunk headers and data
- [**Trailer & TOC**](format/trailer.md) - Table of contents for random access
- [**Encryption Block**](format/encryption-block.md) - Key derivation and encryption metadata
- [**Attributes**](format/attributes.md) - Custom key-value attribute encoding
- [**Constants Reference**](format/constants.md) - Magic numbers, IDs, and flags

### Java API Reference

- [**API Overview**](api/README.md) - Design philosophy and thread safety
- [**AetherPackReader**](api/reader.md) - Reading archives
- [**AetherPackWriter**](api/writer.md) - Creating archives
- [**ApackConfiguration**](api/configuration.md) - Configuration options
- [**Entry & EntryMetadata**](api/entries.md) - Working with entries
- [**ChunkProcessor**](api/chunk-processor.md) - Data transformation pipeline

### Command Line Interface

- [**CLI Overview**](cli/README.md) - Installation and global options
- [**create**](cli/create.md) - Create archives
- [**extract**](cli/extract.md) - Extract files from archives
- [**list**](cli/list.md) - List archive contents
- [**info**](cli/info.md) - Display archive information
- [**verify**](cli/verify.md) - Verify archive integrity

### Algorithm Guides

#### Compression
- [**Compression Overview**](compression/README.md) - Algorithm comparison
- [**ZSTD**](compression/zstd.md) - Zstandard compression
- [**LZ4**](compression/lz4.md) - LZ4 compression

#### Encryption
- [**Encryption Overview**](encryption/README.md) - Security concepts
- [**AES-256-GCM**](encryption/aes-gcm.md) - AES encryption
- [**ChaCha20-Poly1305**](encryption/chacha20.md) - ChaCha20 encryption
- [**Key Derivation**](encryption/key-derivation.md) - Argon2id and PBKDF2
- [**Security Considerations**](encryption/security-considerations.md) - Best practices

#### Error Correction
- [**ECC Overview**](error-correction/README.md) - Error correction concepts
- [**Reed-Solomon**](error-correction/reed-solomon.md) - Implementation details

### Additional Guides

- [**Error Handling**](error-handling.md) - Exception hierarchy and recovery
- [**Performance Tuning**](performance.md) - Optimization guide
- [**SPI Extensions**](spi-extensions.md) - Creating custom providers
- [**FAQ**](faq.md) - Frequently asked questions

## Version Information

| Component | Version |
|-----------|---------|
| APACK Format | 1.0.0 |
| Library | 0.1.0 |
| Java | 17+ |

## Module Structure

```
aether-pack/
├── aether-pack-core/           # Core format, I/O, SPI interfaces
├── aether-pack-compression/    # ZSTD and LZ4 providers
├── aether-pack-crypto/         # AES-GCM, ChaCha20, Argon2id, PBKDF2
└── aether-pack-cli/            # Command-line tools
```

## Maven Coordinates

```xml
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-core</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- Optional: Compression support -->
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-compression</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- Optional: Encryption support -->
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-crypto</artifactId>
    <version>0.1.0</version>
</dependency>
```

## License

APACK is released under the [MIT License](https://opensource.org/licenses/MIT).

```
Copyright (c) 2025 Splatgames.de Software and Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

## Contributing

Contributions are welcome! Please see the project repository for contribution guidelines.

---

*This documentation covers APACK version 0.1.0*
