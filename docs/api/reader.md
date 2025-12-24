# AetherPackReader API

`AetherPackReader` is the primary class for reading APACK archives. It provides random access to entries by name or ID, iteration over all entries, and streaming data extraction.

## Class Definition

```java
public final class AetherPackReader implements Closeable, Iterable<Entry>
```

## Factory Methods

### open(Path)

Opens an archive file with default settings (no decompression/decryption).

```java
public static AetherPackReader open(Path path) throws IOException, ApackException
```

**Parameters:**
- `path` - File path to the archive

**Returns:** A new reader instance

**Throws:**
- `IOException` - If the file cannot be opened
- `ApackException` - If the format is invalid
- `NoSuchFileException` - If the file doesn't exist

**Example:**
```java
try (AetherPackReader reader = AetherPackReader.open(Path.of("archive.apack"))) {
    System.out.println("Entries: " + reader.getEntryCount());
}
```

### open(Path, ApackConfiguration)

Opens an archive with configuration for decompression/decryption.

```java
public static AetherPackReader open(Path path, ApackConfiguration config)
        throws IOException, ApackException
```

**Parameters:**
- `path` - File path to the archive
- `config` - Configuration with compression/encryption settings

**Example:**
```java
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd())
    .encryption(EncryptionRegistry.aes256Gcm(), secretKey)
    .build();

try (AetherPackReader reader = AetherPackReader.open(path, config)) {
    // Data is automatically decompressed and decrypted
}
```

### open(Path, ChunkProcessor)

Opens an archive with a pre-configured chunk processor.

```java
public static AetherPackReader open(Path path, ChunkProcessor processor)
        throws IOException, ApackException
```

**Parameters:**
- `path` - File path to the archive
- `processor` - Chunk processor for decompression/decryption

**Example:**
```java
ChunkProcessor processor = ChunkProcessor.builder()
    .compression(CompressionRegistry.lz4())
    .build();

try (AetherPackReader reader = AetherPackReader.open(path, processor)) {
    // LZ4 decompression applied
}
```

### open(Path, ChunkProcessor, ChunkSecuritySettings)

Opens with custom security settings for chunk validation.

```java
public static AetherPackReader open(
        Path path,
        ChunkProcessor processor,
        ChunkSecuritySettings securitySettings)
        throws IOException, ApackException
```

**Parameters:**
- `path` - File path to the archive
- `processor` - Chunk processor
- `securitySettings` - Security limits for decompression

**Example:**
```java
ChunkSecuritySettings settings = ChunkSecuritySettings.builder()
    .maxChunkSize(128 * 1024 * 1024)  // 128 MB max
    .maxDecompressionRatio(200)        // 200:1 max ratio
    .build();

try (AetherPackReader reader = AetherPackReader.open(path, processor, settings)) {
    // Custom limits applied
}
```

### open(SeekableByteChannel)

Opens from a seekable channel (advanced usage).

```java
public static AetherPackReader open(SeekableByteChannel channel)
        throws IOException, ApackException
```

**Note:** The reader takes ownership of the channel and will close it.

---

## Query Methods

### getFileHeader()

Returns the archive's file header.

```java
public FileHeader getFileHeader()
```

**Returns:** The file header containing format metadata

**Example:**
```java
FileHeader header = reader.getFileHeader();
System.out.println("Version: " + header.versionMajor() + "." + header.versionMinor());
System.out.println("Chunk size: " + header.chunkSize());
System.out.println("Encrypted: " + header.isEncrypted());
```

### getEncryptionBlock()

Returns encryption metadata (if encrypted).

```java
public @Nullable EncryptionBlock getEncryptionBlock()
```

**Returns:** The encryption block, or `null` if not encrypted

**Example:**
```java
EncryptionBlock block = reader.getEncryptionBlock();
if (block != null) {
    System.out.println("KDF: " + (block.isArgon2id() ? "Argon2id" : "PBKDF2"));
    System.out.println("Cipher: " + (block.isAesGcm() ? "AES-GCM" : "ChaCha20"));
}
```

### getEntryCount()

Returns the number of entries.

```java
public int getEntryCount()
```

**Returns:** Entry count (≥ 0)

### getEntries()

Returns all entries as an unmodifiable list.

```java
public List<? extends Entry> getEntries()
```

**Returns:** Immutable list of entries in archive order

---

## Entry Lookup

### getEntry(long id)

Gets an entry by its unique ID.

```java
public Entry getEntry(long id) throws EntryNotFoundException
```

**Parameters:**
- `id` - The unique entry ID

**Returns:** The entry

**Throws:** `EntryNotFoundException` if not found

**Performance:** O(1) using HashMap

### getEntry(String name)

Gets an entry by name.

```java
public Optional<Entry> getEntry(String name)
```

**Parameters:**
- `name` - Entry name (case-sensitive, exact match)

**Returns:** Optional containing the entry, or empty if not found

**Performance:** O(1) average using XXH3 hash index

**Example:**
```java
reader.getEntry("config/settings.json").ifPresent(entry -> {
    System.out.println("Found: " + entry.getOriginalSize() + " bytes");
});
```

### hasEntry(String name)

Checks if an entry exists.

```java
public boolean hasEntry(String name)
```

**Parameters:**
- `name` - Entry name to check

**Returns:** `true` if exists, `false` otherwise

---

## Reading Data

### getInputStream(Entry)

Gets a stream for reading entry data.

```java
public InputStream getInputStream(Entry entry) throws IOException
```

**Parameters:**
- `entry` - The entry to read (must be from this reader)

**Returns:** InputStream with decompressed/decrypted data

**Throws:**
- `IOException` - If reader is closed or I/O error
- `IllegalArgumentException` - If entry is from another reader

**Important:**
- Only one entry can be read at a time (shared channel)
- Stream does not close the reader when closed

**Example:**
```java
Entry entry = reader.getEntry("data.bin").orElseThrow();
try (InputStream input = reader.getInputStream(entry)) {
    byte[] buffer = new byte[8192];
    int read;
    while ((read = input.read(buffer)) != -1) {
        // Process chunk
    }
}
```

### readAllBytes(Entry)

Reads entire entry into a byte array.

```java
public byte[] readAllBytes(Entry entry) throws IOException
```

**Parameters:**
- `entry` - The entry to read

**Returns:** Complete entry data

**Throws:** `OutOfMemoryError` if entry is too large

**Example:**
```java
Entry entry = reader.getEntry("config.json").orElseThrow();
byte[] data = reader.readAllBytes(entry);
String json = new String(data, StandardCharsets.UTF_8);
```

### readAllBytes(String name)

Reads entry by name into a byte array.

```java
public byte[] readAllBytes(String name) throws IOException, EntryNotFoundException
```

**Parameters:**
- `name` - Entry name

**Returns:** Complete entry data

**Throws:** `EntryNotFoundException` if not found

**Example:**
```java
byte[] data = reader.readAllBytes("assets/logo.png");
```

---

## Iteration

### iterator()

Returns an iterator over all entries.

```java
public Iterator<Entry> iterator()
```

**Returns:** Iterator in archive order

**Example:**
```java
for (Entry entry : reader) {
    System.out.println(entry.getName() + ": " + entry.getOriginalSize() + " bytes");
}
```

### stream()

Returns a sequential stream of entries.

```java
public Stream<Entry> stream()
```

**Returns:** Sequential stream (not parallel due to shared channel)

**Example:**
```java
long totalSize = reader.stream()
    .mapToLong(Entry::getOriginalSize)
    .sum();

List<Entry> jsonFiles = reader.stream()
    .filter(e -> e.getName().endsWith(".json"))
    .collect(Collectors.toList());
```

---

## Resource Management

### close()

Closes the reader and releases resources.

```java
public void close() throws IOException
```

**Note:** Always use try-with-resources to ensure proper cleanup.

---

## Complete Example

```java
public void extractArchive(Path archivePath, Path outputDir) throws IOException {
    // Configure decompression
    ChunkProcessor processor = ChunkProcessor.builder()
        .compression(CompressionRegistry.zstd())
        .build();

    try (AetherPackReader reader = AetherPackReader.open(archivePath, processor)) {
        // Print archive info
        FileHeader header = reader.getFileHeader();
        System.out.printf("Archive: %d entries, created %s%n",
            reader.getEntryCount(),
            Instant.ofEpochMilli(header.creationTimestamp()));

        // Extract each entry
        for (Entry entry : reader) {
            Path outputPath = outputDir.resolve(entry.getName());

            // Create parent directories
            Files.createDirectories(outputPath.getParent());

            // Extract with progress
            try (InputStream input = reader.getInputStream(entry);
                 OutputStream output = Files.newOutputStream(outputPath)) {

                byte[] buffer = new byte[8192];
                long written = 0;
                int read;

                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    written += read;
                }

                System.out.printf("Extracted: %s (%d bytes)%n",
                    entry.getName(), written);
            }
        }
    }
}
```

---

*Next: [Writer API](writer.md) | Previous: [API Overview](README.md)*
