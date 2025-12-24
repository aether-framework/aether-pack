# AetherPackWriter API

`AetherPackWriter` is the primary class for creating APACK archives. It supports adding entries from streams, files, and byte arrays with optional compression and encryption.

## Class Definition

```java
public final class AetherPackWriter implements Closeable
```

## Factory Methods

### create(OutputStream)

Creates a writer to an output stream with default settings.

```java
public static AetherPackWriter create(OutputStream output)
```

**Parameters:**
- `output` - The output stream to write to

**Returns:** A new writer instance

**Note:** Stream-based writers cannot update the file header after closing, limiting random access support.

**Example:**
```java
try (AetherPackWriter writer = AetherPackWriter.create(outputStream)) {
    writer.addEntry("file.txt", data);
}
```

### create(OutputStream, ApackConfiguration)

Creates a writer with custom configuration.

```java
public static AetherPackWriter create(OutputStream output, ApackConfiguration config)
```

**Parameters:**
- `output` - The output stream
- `config` - Configuration settings

### create(Path)

Creates a writer to a file with default settings.

```java
public static AetherPackWriter create(Path path) throws IOException
```

**Parameters:**
- `path` - File path to create

**Returns:** A new writer instance

**Throws:**
- `IOException` - If file cannot be created
- `SecurityException` - If access is denied

**Note:** File-based writers can update the header with entry count and trailer offset, enabling full random access.

**Example:**
```java
try (AetherPackWriter writer = AetherPackWriter.create(Path.of("archive.apack"))) {
    writer.addEntry("readme.txt", "Hello, World!".getBytes());
}
```

### create(Path, ApackConfiguration)

Creates a writer to a file with custom configuration.

```java
public static AetherPackWriter create(Path path, ApackConfiguration config)
        throws IOException
```

**Parameters:**
- `path` - File path to create
- `config` - Configuration settings

**Returns:** A new writer instance

**Example:**
```java
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd(), 6)
    .chunkSize(128 * 1024)
    .build();

try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
    writer.addEntry("large-file.dat", inputStream);
}
```

---

## Adding Entries

### addEntry(String, InputStream)

Adds an entry from an input stream.

```java
public void addEntry(String name, InputStream input) throws IOException
```

**Parameters:**
- `name` - Entry name (path within archive)
- `input` - Data source (read until EOF)

**Throws:** `IOException` if I/O error or writer is closed

**Note:** The input stream is NOT closed by this method.

**Example:**
```java
try (InputStream fileInput = Files.newInputStream(sourcePath)) {
    writer.addEntry("data/file.bin", fileInput);
}
```

### addEntry(EntryMetadata, InputStream)

Adds an entry with full metadata control.

```java
public void addEntry(EntryMetadata metadata, InputStream input) throws IOException
```

**Parameters:**
- `metadata` - Entry metadata including name, MIME type, attributes
- `input` - Data source

**Example:**
```java
EntryMetadata metadata = EntryMetadata.builder()
    .name("document.pdf")
    .mimeType("application/pdf")
    .attribute("author", "John Doe")
    .attribute("created", System.currentTimeMillis())
    .build();

try (InputStream pdfStream = Files.newInputStream(pdfPath)) {
    writer.addEntry(metadata, pdfStream);
}
```

### addEntry(String, Path)

Adds an entry from a file.

```java
public void addEntry(String name, Path path) throws IOException
```

**Parameters:**
- `name` - Entry name (can differ from file name)
- `path` - Source file path

**Throws:**
- `IOException` - If file cannot be read
- `NoSuchFileException` - If file doesn't exist

**Example:**
```java
// Store with different name
writer.addEntry("config/settings.json", Path.of("/etc/myapp/config.json"));

// Store with same name
Path file = Path.of("data.bin");
writer.addEntry(file.getFileName().toString(), file);
```

### addEntry(String, byte[])

Adds an entry from a byte array.

```java
public void addEntry(String name, byte[] data) throws IOException
```

**Parameters:**
- `name` - Entry name
- `data` - Entry content

**Example:**
```java
String json = "{\"version\": 1}";
writer.addEntry("metadata.json", json.getBytes(StandardCharsets.UTF_8));
```

---

## Query Methods

### getEntryCount()

Returns the number of entries written so far.

```java
public int getEntryCount()
```

**Returns:** Entry count (≥ 0)

---

## Resource Management

### close()

Closes the writer and finalizes the archive.

```java
public void close() throws IOException
```

**Operations performed:**
1. Writes file header (if not already written)
2. Writes trailer with table of contents
3. Flushes buffered data
4. Updates file header (if writing to file)
5. Closes underlying stream

**Important:**
- Failure to close results in an incomplete archive
- Method is idempotent (safe to call multiple times)
- Empty archives are valid (header + trailer only)

---

## Configuration Examples

### Compression Only

```java
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd(), 6)  // ZSTD level 6
    .build();

try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
    writer.addEntry("data.bin", largeInputStream);
}
```

### Encryption Only

```java
// Generate encryption key
EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
SecretKey key = aes.generateKey();

ApackConfiguration config = ApackConfiguration.builder()
    .encryption(aes, key)
    .build();

try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
    writer.addEntry("secrets.dat", sensitiveData);
}
```

### Compression + Encryption

```java
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd(), 3)
    .encryption(EncryptionRegistry.aes256Gcm(), secretKey)
    .chunkSize(64 * 1024)  // 64 KB chunks
    .build();

try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
    // Data is compressed, then encrypted
    writer.addEntry("data.bin", inputStream);
}
```

### Password-Based Encryption

```java
// Derive key from password using Argon2id
char[] password = getPassword();
byte[] salt = generateSalt(32);

byte[] keyBytes = Argon2id.derive(
    password, salt,
    3,        // time cost
    65536,    // memory (64 MB)
    4,        // parallelism
    32        // key length
);
SecretKey dek = new SecretKeySpec(keyBytes, "AES");

// Create encryption block with KDF parameters
EncryptionBlock encBlock = EncryptionBlock.builder()
    .kdfAlgorithmId(FormatConstants.KDF_ARGON2ID)
    .cipherAlgorithmId(FormatConstants.ENCRYPTION_AES_256_GCM)
    .kdfIterations(3)
    .kdfMemory(65536)
    .kdfParallelism(4)
    .salt(salt)
    .wrappedKey(wrapKey(dek))  // Encrypt DEK with KEK
    .wrappedKeyTag(tag)
    .build();

ApackConfiguration config = ApackConfiguration.builder()
    .encryption(EncryptionRegistry.aes256Gcm(), dek, encBlock)
    .build();

try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
    writer.addEntry("data.bin", inputStream);
}
```

### Custom Chunk Size

```java
ApackConfiguration config = ApackConfiguration.builder()
    .chunkSize(1024 * 1024)  // 1 MB chunks
    .compression(CompressionRegistry.zstd(), 9)  // Higher compression
    .build();
```

### Stream Mode

```java
ApackConfiguration config = ApackConfiguration.builder()
    .streamMode(true)
    .compression(CompressionRegistry.lz4())
    .build();

try (AetherPackWriter writer = AetherPackWriter.create(pipeOutputStream, config)) {
    writer.addEntry("stream.dat", inputStream);
}
```

---

## Complete Example

```java
public void createArchive(Path archivePath, Path sourceDir) throws IOException {
    // Configure compression
    ApackConfiguration config = ApackConfiguration.builder()
        .compression(CompressionRegistry.zstd(), 6)
        .chunkSize(256 * 1024)
        .build();

    try (AetherPackWriter writer = AetherPackWriter.create(archivePath, config)) {
        // Walk directory and add files
        Files.walk(sourceDir)
            .filter(Files::isRegularFile)
            .forEach(file -> {
                try {
                    // Compute relative path for archive entry name
                    String entryName = sourceDir.relativize(file)
                        .toString()
                        .replace('\\', '/');  // Normalize separators

                    // Detect MIME type
                    String mimeType = Files.probeContentType(file);

                    // Build metadata
                    EntryMetadata metadata = EntryMetadata.builder()
                        .name(entryName)
                        .mimeType(mimeType != null ? mimeType : "application/octet-stream")
                        .attribute("lastModified", Files.getLastModifiedTime(file).toMillis())
                        .build();

                    // Add entry
                    try (InputStream input = Files.newInputStream(file)) {
                        writer.addEntry(metadata, input);
                    }

                    System.out.println("Added: " + entryName);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

        System.out.println("Total entries: " + writer.getEntryCount());
    }
}
```

---

## Best Practices

1. **Always use try-with-resources** to ensure proper closing
2. **Choose appropriate chunk size:**
   - Smaller (16-64 KB) for random access
   - Larger (256 KB - 1 MB) for better compression
3. **Match compression level to use case:**
   - Low levels (1-3) for speed
   - High levels (6-9) for ratio
4. **Use file paths** when possible for full random-access support
5. **Close input streams** after calling `addEntry()` (not closed automatically)

---

*Next: [Configuration](configuration.md) | Previous: [Reader API](reader.md)*
