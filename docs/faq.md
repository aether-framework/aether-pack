# Frequently Asked Questions

## General Questions

### What is APACK?

APACK (Aether Pack) is a binary archive format library for Java that provides:
- Chunked storage for efficient random access
- Built-in compression (ZSTD, LZ4)
- Strong encryption (AES-256-GCM, ChaCha20-Poly1305)
- Data integrity verification (checksums)
- Optional error correction (Reed-Solomon)

### What Java version is required?

APACK requires **Java 17 or later**.

### Is APACK open source?

Yes, APACK is released under the MIT License.

### Can I use APACK in commercial projects?

Yes, the MIT License permits commercial use, modification, and distribution.

---

## Archive Operations

### How do I create a simple archive?

```java
try (AetherPackWriter writer = AetherPackWriter.create(Paths.get("archive.apack"))) {
    writer.addEntry("file.txt", Paths.get("input/file.txt"));
    writer.addEntry("data.bin", inputStream);
}
```

### How do I read an archive?

```java
try (AetherPackReader reader = AetherPackReader.open(Paths.get("archive.apack"))) {
    for (Entry entry : reader) {
        System.out.println(entry.getName());
    }

    // Read specific entry
    byte[] data = reader.readAllBytes("file.txt");
}
```

### Can I add files to an existing archive?

No, APACK archives are write-once. To add files, create a new archive with all content.

### Can I delete entries from an archive?

No, entries cannot be deleted. Create a new archive without the unwanted entries.

### How do I update an archive?

Create a new archive:

```java
// Read existing entries
List<Entry> existing;
try (AetherPackReader reader = AetherPackReader.open(oldArchive)) {
    existing = reader.getEntries();
}

// Create new archive with modified content
try (AetherPackWriter writer = AetherPackWriter.create(newArchive)) {
    for (Entry entry : existing) {
        if (!entry.getName().equals("update-this.txt")) {
            // Copy existing entry
            writer.addEntry(entry.getName(), reader.openEntry(entry));
        }
    }
    // Add updated entry
    writer.addEntry("update-this.txt", updatedContent);
}
```

---

## Compression

### Which compression algorithm should I use?

| Use Case | Recommendation |
|----------|----------------|
| General purpose | ZSTD level 3 (default) |
| Maximum speed | LZ4 |
| Maximum compression | ZSTD level 19-22 |
| Already compressed data | None |

### How do I disable compression?

```java
ApackConfiguration config = ApackConfiguration.builder()
    .noCompression()
    .build();
```

### Why is my archive larger than expected?

Possible causes:
1. **Already compressed data** - Disable compression for pre-compressed files
2. **Small chunk size** - Overhead per chunk reduces ratio
3. **Low compression level** - Try higher ZSTD levels

### Can I use different compression for different entries?

Currently, compression is archive-wide. All entries use the same configuration.

---

## Encryption

### How do I encrypt an archive with a password?

```java
// Derive key from password
Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();
byte[] salt = kdf.generateSalt();
SecretKey contentKey = kdf.deriveKey(password, salt, 32);

// Configure encryption
ApackConfiguration config = ApackConfiguration.builder()
    .encryption(EncryptionRegistry.aes256Gcm(), contentKey, encryptionBlock)
    .build();

try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
    writer.addEntry("secret.txt", secretData);
}
```

### How do I decrypt an archive?

```java
// Derive same key from password
SecretKey key = kdf.deriveKey(password, salt, 32);

try (AetherPackReader reader = AetherPackReader.open(path, key)) {
    byte[] data = reader.readAllBytes("secret.txt");
}
```

### Which encryption algorithm is more secure?

Both AES-256-GCM and ChaCha20-Poly1305 provide equivalent 256-bit security. Choose based on:
- **AES-256-GCM**: Faster with hardware acceleration (AES-NI)
- **ChaCha20-Poly1305**: Faster in software, constant-time

### What happens if I use the wrong password?

Decryption will fail with an `EncryptionException`. AEAD algorithms detect authentication failures.

### Can I change the password of an encrypted archive?

No, you must create a new archive with the new password.

---

## Performance

### How can I improve compression speed?

1. Lower compression level (ZSTD 1-3)
2. Use LZ4 instead of ZSTD
3. Use larger chunks

### How can I improve compression ratio?

1. Higher compression level (ZSTD 6+)
2. Use larger chunks
3. Don't compress pre-compressed data

### Is APACK thread-safe?

- **Providers**: Yes, compression/encryption providers are thread-safe
- **Readers**: No, each thread should use its own reader instance
- **Writers**: No, writers are not thread-safe

### How much memory does APACK use?

Memory usage is approximately `2-3x chunk size` per operation. Default chunk size is 256 KB.

---

## Format

### What is the maximum archive size?

Theoretically unlimited. File offsets use 64-bit integers (up to 16 EB).

### What is the maximum entry size?

Theoretically unlimited. Entry sizes use 64-bit integers.

### What is the maximum number of entries?

The header supports 32-bit entry counts (up to ~4 billion entries).

### Can I store empty files?

Yes, entries with zero bytes are supported.

### How are entry names stored?

Entry names are stored as UTF-8 strings with a 16-bit length prefix (max 65,535 bytes).

### Is APACK compatible with ZIP?

No, APACK uses a completely different binary format. It cannot read or write ZIP files.

---

## CLI

### How do I install the CLI?

Download `aether-pack-cli-x.x.x-fat.jar` and run:

```bash
java -jar aether-pack-cli-0.1.0-fat.jar <command>
```

### How do I create an encrypted archive from CLI?

```bash
java -jar aether-pack-cli.jar create -e aes-256-gcm archive.apack files/
```

You'll be prompted for a password.

### How do I list entries without extracting?

```bash
java -jar aether-pack-cli.jar list archive.apack
java -jar aether-pack-cli.jar list -l archive.apack  # Long format
```

### How do I verify an archive's integrity?

```bash
java -jar aether-pack-cli.jar verify archive.apack
```

---

## Troubleshooting

### "Unknown compression algorithm ID"

The archive uses a compression algorithm not available on your classpath. Ensure `aether-pack-compression` is included:

```xml
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-compression</artifactId>
    <version>0.1.0</version>
</dependency>
```

### "Unknown encryption algorithm ID"

The archive uses an encryption algorithm not available. Add `aether-pack-crypto`:

```xml
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-crypto</artifactId>
    <version>0.1.0</version>
</dependency>
```

### "Checksum mismatch"

The data is corrupted. If the archive has ECC, recovery may be possible. Without ECC, the data cannot be recovered.

### "Decryption failed"

Possible causes:
1. Wrong password
2. Corrupted ciphertext
3. Wrong encryption algorithm

### "Invalid magic number"

The file is not a valid APACK archive. Check:
1. File extension is correct
2. File was not truncated
3. File is not a different format (ZIP, etc.)

### OutOfMemoryError

Reduce chunk size or process entries as streams:

```java
ApackConfiguration config = ApackConfiguration.builder()
    .chunkSize(64 * 1024)  // Smaller chunks
    .build();
```

---

## Comparison

### APACK vs ZIP

| Feature | APACK | ZIP |
|---------|-------|-----|
| Compression | ZSTD, LZ4 | Deflate |
| Encryption | AES-256-GCM, ChaCha20 | ZipCrypto (weak), AES |
| Chunking | Yes | No |
| Random access | TOC-based | Central directory |
| Error correction | Optional (ECC) | No |

### APACK vs TAR

| Feature | APACK | TAR |
|---------|-------|-----|
| Compression | Built-in | External (gzip, etc.) |
| Encryption | Built-in | External |
| Random access | Yes (TOC) | No (sequential) |
| Seeking | Efficient | Requires decompression |

### When should I use APACK?

- Need random access to compressed data
- Require strong encryption
- Want integrated checksums
- Need better compression ratios (ZSTD)
- Building game assets, data archives, or backups

### When should I NOT use APACK?

- Require ZIP compatibility for interchange
- Need to incrementally update archives
- File count exceeds billions
- Targeting pre-Java 17 environments

---

## Development

### How do I contribute?

Visit the GitHub repository to:
1. Report issues
2. Submit pull requests
3. Suggest features

### How do I build from source?

```bash
git clone https://github.com/splatgames/aether-pack.git
cd aether-pack
mvn clean install
```

### How do I run the tests?

```bash
mvn test
```

### How do I create custom providers?

See the [SPI Extensions Guide](spi-extensions.md) for detailed instructions on creating custom compression, encryption, and checksum providers.

---

*Back to: [Documentation](README.md)*
