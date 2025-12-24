![License](https://img.shields.io/badge/license-MIT-red)
![Maven Central](https://img.shields.io/maven-central/v/de.splatgames.aether.pack/aether-pack-core)
![Version](https://img.shields.io/badge/version-0.1.0-orange)

# Aether Pack 📦

**Aether Pack** is a modern **binary archive format** for the JVM.
It provides **chunked storage** with built-in support for **compression**, **encryption**, and **error correction** —
designed for game assets, save files, and any application requiring efficient, secure, and reliable data storage.

---

## ✨ Features (v0.1.0)

- ✅ **Chunked Storage** — Large files split into configurable chunks (default 256KB) for streaming and random access
- ✅ **Compression** — ZSTD (levels 1-22) and LZ4 via pluggable `CompressionProvider` SPI
- ✅ **Encryption** — AES-256-GCM and ChaCha20-Poly1305 with AEAD for confidentiality and integrity
- ✅ **Key Derivation** — Argon2id and PBKDF2 for secure password-based encryption
- ✅ **Error Correction** — Reed-Solomon ECC for data recovery from corruption
- ✅ **Checksums** — XXH3-64 (default) and CRC-32 for integrity verification
- ✅ **Container Mode** — Table of Contents (TOC) for random access to entries
- ✅ **Stream Mode** — Sequential writing for real-time archiving
- ✅ **Custom Attributes** — Key-value metadata per entry
- ✅ **CLI Tool** — Command-line interface for all archive operations
- ✅ **JDK 17+** — Built and tested on modern LTS JVMs

---

## 📦 Modules

- **aether-pack-core** — Core library with format definitions, I/O, and SPI interfaces
- **aether-pack-compression** — ZSTD and LZ4 compression providers
- **aether-pack-crypto** — AES-GCM, ChaCha20-Poly1305, Argon2id, and PBKDF2 providers
- **aether-pack-cli** — Command-line tool for archive operations

---

## 🚀 Quickstart

### 1) Create an Archive

```java
ApackConfiguration config = ApackConfiguration.builder()
    .compressionProvider("zstd")
    .compressionLevel(6)
    .checksumProvider("xxh3-64")
    .build();

try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
    writer.addEntry(EntryMetadata.of("data/config.json"), configBytes);
    writer.addEntry(EntryMetadata.of("assets/texture.png"), textureBytes);
}
```

### 2) Read an Archive

```java
try (AetherPackReader reader = AetherPackReader.open(path)) {
    for (Entry entry : reader.getEntries()) {
        System.out.printf("%s (%d bytes)%n", entry.getName(), entry.getOriginalSize());
    }

    try (InputStream in = reader.openEntry("data/config.json")) {
        byte[] data = in.readAllBytes();
    }
}
```

### 3) Encrypted Archive

```java
char[] password = "secret".toCharArray();

// Create encrypted archive
try (AetherPackWriter writer = AetherPackWriter.createEncrypted(path, config, password)) {
    writer.addEntry(EntryMetadata.of("secret.dat"), sensitiveData);
}

// Read encrypted archive
try (AetherPackReader reader = AetherPackReader.openEncrypted(path, password)) {
    // Read entries as normal...
}
```

---

## 📚 Installation

**Maven**

```xml
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**Gradle (Groovy)**

```groovy
dependencies {
    implementation 'de.splatgames.aether.pack:aether-pack-core:0.1.0-SNAPSHOT'
}
```

**Gradle (Kotlin)**

```kotlin
dependencies {
    implementation("de.splatgames.aether.pack:aether-pack-core:0.1.0-SNAPSHOT")
}
```

> Add `aether-pack-compression` for ZSTD/LZ4 and `aether-pack-crypto` for encryption support.

---

## 🖥️ CLI Usage

The CLI tool provides commands for creating, extracting, listing, and verifying archives.

### Commands

| Command   | Aliases    | Description                      |
|-----------|------------|----------------------------------|
| `create`  | `c`        | Create a new APACK archive       |
| `extract` | `x`        | Extract files from an archive    |
| `list`    | `l`, `ls`  | List archive contents            |
| `info`    | `i`        | Display archive information      |
| `verify`  | `v`        | Verify archive integrity         |

### Examples

```bash
# Create archive with ZSTD compression
apack create -c zstd archive.apack files/

# Create encrypted archive
apack create -c zstd -e aes-256-gcm archive.apack files/

# Extract archive
apack extract archive.apack -o output/

# List contents (long format)
apack list -l archive.apack

# Show archive information
apack info archive.apack

# Verify integrity
apack verify archive.apack
```

---

## 🔑 Key Concepts

| Concept            | Description                                                                 |
|--------------------|-----------------------------------------------------------------------------|
| **Entry**          | A named file within the archive with metadata and chunked data              |
| **Chunk**          | A fixed-size block (default 256KB) that is independently processed          |
| **Pipeline**       | Processing stages: Checksum → Compress → Encrypt                            |
| **Container Mode** | Archive with TOC trailer for random access to entries                       |
| **Stream Mode**    | Sequential archive without TOC, suitable for streaming writes               |
| **AEAD**           | Authenticated Encryption with Associated Data (AES-GCM, ChaCha20-Poly1305)  |
| **ECC**            | Error Correction Code using Reed-Solomon for data recovery                  |

---

## 📊 File Format

```
+------------------+
|   File Header    |  64 bytes (magic, version, flags, configuration)
+------------------+
|  Entry Header 1  |  Variable (name, sizes, flags, attributes)
|    Chunk 1.1     |  24-byte header + compressed/encrypted data
|    Chunk 1.2     |
|    ...           |
+------------------+
|  Entry Header 2  |
|    Chunks...     |
+------------------+
|      ...         |
+------------------+
|     Trailer      |  48+ bytes (TOC for random access, checksums)
+------------------+
```

**Magic Number:** `APACK` (hex: `41 50 41 43 4B`)

---

## 🔧 Algorithms

### Compression

| Algorithm | ID | Levels | Description                              |
|-----------|----|--------|------------------------------------------|
| ZSTD      | 1  | 1-22   | High compression ratio, fast decompression |
| LZ4       | 2  | 1-12   | Extremely fast compression/decompression |

### Encryption

| Algorithm          | ID | Key Size | Description                          |
|--------------------|----|----------|--------------------------------------|
| AES-256-GCM        | 1  | 32 bytes | Industry standard, hardware-accelerated |
| ChaCha20-Poly1305  | 2  | 32 bytes | High software performance, constant-time |

### Key Derivation

| Algorithm     | ID | Description                         |
|---------------|----|-------------------------------------|
| Argon2id      | 1  | Memory-hard, recommended for passwords |
| PBKDF2-SHA256 | 2  | Widely compatible fallback          |

---

## 🛠️ Building

```bash
# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run tests
mvn test

# Generate JavaDoc
mvn javadoc:aggregate
```

---

## 📖 Documentation

Comprehensive documentation is available in the `docs/` folder:

- [Getting Started](docs/getting-started.md)
- [Core Concepts](docs/concepts.md)
- [File Format Specification](docs/format/)
- [API Reference](docs/api/)
- [CLI Reference](docs/cli/)
- [Security Guide](docs/encryption/security-considerations.md)

---

## 🗺️ Roadmap

- **v0.1.0** (current)
    - Core format implementation
    - ZSTD and LZ4 compression
    - AES-256-GCM and ChaCha20-Poly1305 encryption
    - Argon2id and PBKDF2 key derivation
    - Reed-Solomon error correction
    - CLI tool with create, extract, list, info, verify

- **v0.2.0**
    - Graphical User Interface (GUI) for archive management

- **v1.0.0**
    - Stable API surface
    - Production-ready release

---

## 🤝 Contributing

Contributions welcome! Please open issues/PRs with clear repros or targeted patches.

---

## 📄 License

MIT © Splatgames.de Software and Contributors
