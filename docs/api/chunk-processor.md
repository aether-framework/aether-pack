# ChunkProcessor API

`ChunkProcessor` implements the data transformation pipeline for individual chunks. It handles compression and encryption in the correct order for both writing and reading.

## Class Definition

```java
public final class ChunkProcessor
```

## Processing Pipeline

### Writing Order

```
Original Data
     │
     ▼
┌─────────────┐
│  Compress   │  (if enabled and reduces size)
└─────────────┘
     │
     ▼
┌─────────────┐
│   Encrypt   │  (if enabled)
└─────────────┘
     │
     ▼
 Stored Data
```

### Reading Order

```
 Stored Data
     │
     ▼
┌─────────────┐
│   Decrypt   │  (if chunk is encrypted)
└─────────────┘
     │
     ▼
┌─────────────┐
│ Decompress  │  (if chunk is compressed)
└─────────────┘
     │
     ▼
Original Data
```

---

## Factory Methods

### builder()

Creates a new builder for configuration.

```java
public static Builder builder()
```

**Example:**
```java
ChunkProcessor processor = ChunkProcessor.builder()
    .compression(zstdProvider, 6)
    .encryption(aesProvider, secretKey)
    .build();
```

### passThrough()

Creates a processor that applies no transformations.

```java
public static ChunkProcessor passThrough()
```

**Example:**
```java
// For uncompressed, unencrypted archives
ChunkProcessor processor = ChunkProcessor.passThrough();
```

---

## Query Methods

### isCompressionEnabled()

```java
public boolean isCompressionEnabled()
```

Returns `true` if a compression provider is configured.

### isEncryptionEnabled()

```java
public boolean isEncryptionEnabled()
```

Returns `true` if both encryption provider and key are configured.

### getCompressionProvider()

```java
public @Nullable CompressionProvider getCompressionProvider()
```

Returns the compression provider, or `null` if disabled.

### getEncryptionProvider()

```java
public @Nullable EncryptionProvider getEncryptionProvider()
```

Returns the encryption provider, or `null` if disabled.

---

## Processing Methods

### processForWrite(byte[], int)

Processes data for writing (compress → encrypt).

```java
public ProcessedChunk processForWrite(byte[] data, int originalSize)
        throws IOException
```

**Parameters:**
- `data` - Original chunk data
- `originalSize` - Number of valid bytes in data array

**Returns:** `ProcessedChunk` with transformed data and metadata

**Behavior:**
- Compression only applied if it reduces size
- Returns flags indicating which transformations were applied

**Example:**
```java
byte[] chunkData = new byte[256 * 1024];  // 256 KB chunk
int bytesRead = input.read(chunkData);

ProcessedChunk result = processor.processForWrite(chunkData, bytesRead);

// Write chunk header with flags
ChunkHeader header = ChunkHeader.builder()
    .originalSize(result.originalSize())
    .storedSize(result.storedSize())
    .compressed(result.compressed())
    .encrypted(result.encrypted())
    .build();

// Write processed data
output.write(result.data());
```

### processForRead(byte[], int, boolean, boolean)

Processes data for reading (decrypt → decompress).

```java
public byte[] processForRead(
        byte[] data,
        int originalSize,
        boolean compressed,
        boolean encrypted)
        throws IOException
```

**Parameters:**
- `data` - Stored chunk data
- `originalSize` - Expected decompressed size
- `compressed` - Whether chunk is compressed
- `encrypted` - Whether chunk is encrypted

**Returns:** Original unprocessed data

**Throws:**
- `IOException` if decryption fails (wrong key, corrupted data)
- `IOException` if decompression fails (corrupted data)
- `IOException` if processor lacks required provider

**Example:**
```java
// Read chunk header
ChunkHeader header = readChunkHeader(input);

// Read stored data
byte[] storedData = new byte[header.storedSize()];
input.readFully(storedData);

// Process (decrypt → decompress)
byte[] originalData = processor.processForRead(
    storedData,
    header.originalSize(),
    header.isCompressed(),
    header.isEncrypted()
);
```

---

## ProcessedChunk Record

Result of `processForWrite()`.

```java
public record ProcessedChunk(
    byte[] data,          // Transformed data
    int originalSize,     // Original unprocessed size
    int storedSize,       // Size of transformed data
    boolean compressed,   // Whether compression was applied
    boolean encrypted     // Whether encryption was applied
)
```

**Usage:**
```java
ProcessedChunk result = processor.processForWrite(data, data.length);

System.out.println("Original: " + result.originalSize() + " bytes");
System.out.println("Stored: " + result.storedSize() + " bytes");
System.out.println("Compressed: " + result.compressed());
System.out.println("Encrypted: " + result.encrypted());
```

---

## Builder

The `ChunkProcessor.Builder` configures compression and encryption.

### compression(CompressionProvider)

Enables compression with provider's default level.

```java
builder.compression(CompressionRegistry.zstd())
```

### compression(CompressionProvider, int)

Enables compression with specific level.

```java
builder.compression(CompressionRegistry.zstd(), 6)
```

**Level Guidelines:**

| Provider | Level Range | Low | Medium | High |
|----------|-------------|-----|--------|------|
| ZSTD | -7 to 22 | 1-3 | 4-6 | 7-22 |
| LZ4 | 0-16 | 0-1 | 2-9 | 10-16 |

### encryption(EncryptionProvider, SecretKey)

Enables encryption.

```java
SecretKey key = aesProvider.generateKey();
builder.encryption(EncryptionRegistry.aes256Gcm(), key)
```

### build()

Creates the processor.

```java
ChunkProcessor processor = builder.build();
```

---

## Configuration Examples

### Compression Only

```java
ChunkProcessor processor = ChunkProcessor.builder()
    .compression(CompressionRegistry.zstd(), 6)
    .build();

// Processing
ProcessedChunk result = processor.processForWrite(data, data.length);
// result.compressed() may be true or false (depends on compressibility)
// result.encrypted() is always false
```

### Encryption Only

```java
SecretKey key = EncryptionRegistry.aes256Gcm().generateKey();

ChunkProcessor processor = ChunkProcessor.builder()
    .encryption(EncryptionRegistry.aes256Gcm(), key)
    .build();

// Processing
ProcessedChunk result = processor.processForWrite(data, data.length);
// result.compressed() is always false
// result.encrypted() is always true
```

### Both Compression and Encryption

```java
ChunkProcessor processor = ChunkProcessor.builder()
    .compression(CompressionRegistry.zstd(), 3)
    .encryption(EncryptionRegistry.aes256Gcm(), secretKey)
    .build();

// Writing: compress → encrypt
ProcessedChunk result = processor.processForWrite(data, data.length);

// Reading: decrypt → decompress
byte[] original = processor.processForRead(
    result.data(),
    result.originalSize(),
    result.compressed(),
    result.encrypted()
);
```

### Fast Compression (Streaming)

```java
ChunkProcessor processor = ChunkProcessor.builder()
    .compression(CompressionRegistry.lz4(), 0)  // Fast mode
    .build();
```

### High Compression (Archival)

```java
ChunkProcessor processor = ChunkProcessor.builder()
    .compression(CompressionRegistry.zstd(), 19)  // High compression
    .build();
```

---

## Integration with ApackConfiguration

`ChunkProcessor` is typically created from `ApackConfiguration`:

```java
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd(), 6)
    .encryption(EncryptionRegistry.aes256Gcm(), secretKey)
    .build();

// Create processor from configuration
ChunkProcessor processor = config.createChunkProcessor();

// Use with reader/writer
try (AetherPackReader reader = AetherPackReader.open(path, processor)) {
    // ...
}
```

---

## Compression Behavior

The processor only uses compressed data if it's smaller:

```java
// If compression increases size (e.g., JPEG, encrypted data)
ProcessedChunk result = processor.processForWrite(jpegData, jpegData.length);
System.out.println(result.compressed());  // false - original data stored

// If compression reduces size
ProcessedChunk result2 = processor.processForWrite(textData, textData.length);
System.out.println(result2.compressed()); // true - compressed data stored
```

This prevents "negative compression" for incompressible data.

---

## Error Handling

### Missing Encryption Key

```java
// Processor without encryption
ChunkProcessor processor = ChunkProcessor.passThrough();

// Attempt to read encrypted chunk
try {
    processor.processForRead(data, originalSize, false, true);
} catch (IOException e) {
    // "Data is encrypted but no encryption key provided"
}
```

### Wrong Encryption Key

```java
// Processor with wrong key
try {
    processor.processForRead(encryptedData, originalSize, false, true);
} catch (IOException e) {
    // "Decryption failed" - wraps GeneralSecurityException
}
```

### Corrupted Compressed Data

```java
try {
    processor.processForRead(corruptedData, originalSize, true, false);
} catch (IOException e) {
    // Decompression failure from provider
}
```

---

## Thread Safety

- `ChunkProcessor` instances are immutable and thread-safe
- Can be shared across multiple threads
- Underlying providers may have their own threading requirements

```java
// Safe to share across threads
ChunkProcessor processor = ChunkProcessor.builder()
    .compression(zstdProvider, 6)
    .build();

// Use from multiple threads
ExecutorService executor = Executors.newFixedThreadPool(4);
for (int i = 0; i < 100; i++) {
    executor.submit(() -> {
        ProcessedChunk result = processor.processForWrite(data, data.length);
        // ...
    });
}
```

---

*Previous: [Entries](entries.md) | Back to: [API Overview](README.md)*
