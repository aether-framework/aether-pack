# ApackConfiguration API

`ApackConfiguration` is an immutable record that encapsulates all settings for APACK archive operations. It defines chunk size, compression, encryption, checksums, and format options.

## Class Definition

```java
public record ApackConfiguration(
    int chunkSize,
    ChecksumProvider checksumProvider,
    CompressionProvider compressionProvider,
    int compressionLevel,
    EncryptionProvider encryptionProvider,
    SecretKey encryptionKey,
    EncryptionBlock encryptionBlock,
    boolean enableRandomAccess,
    boolean streamMode
)
```

## Default Configuration

```java
public static final ApackConfiguration DEFAULT
```

The default configuration provides:

| Setting | Default Value |
|---------|---------------|
| Chunk Size | 256 KB |
| Checksum | XXH3-64 |
| Compression | None |
| Encryption | None |
| Random Access | Enabled |
| Stream Mode | Disabled |

## Factory Method

### builder()

Creates a new configuration builder.

```java
public static Builder builder()
```

**Returns:** A new builder with default values

**Example:**
```java
ApackConfiguration config = ApackConfiguration.builder()
    .chunkSize(128 * 1024)
    .compression(CompressionRegistry.zstd(), 6)
    .build();
```

---

## Record Accessors

### chunkSize()

```java
public int chunkSize()
```

Returns the chunk size in bytes. Must be between `MIN_CHUNK_SIZE` (1 KB) and `MAX_CHUNK_SIZE` (64 MB).

### checksumProvider()

```java
public ChecksumProvider checksumProvider()
```

Returns the checksum provider for data integrity verification.

### compressionProvider()

```java
public @Nullable CompressionProvider compressionProvider()
```

Returns the compression provider, or `null` if compression is disabled.

### compressionLevel()

```java
public int compressionLevel()
```

Returns the compression level. Interpretation depends on the provider.

### encryptionProvider()

```java
public @Nullable EncryptionProvider encryptionProvider()
```

Returns the encryption provider, or `null` if encryption is disabled.

### encryptionKey()

```java
public @Nullable SecretKey encryptionKey()
```

Returns the encryption key, or `null` if encryption is disabled.

### encryptionBlock()

```java
public @Nullable EncryptionBlock encryptionBlock()
```

Returns the encryption metadata block containing KDF parameters.

### enableRandomAccess()

```java
public boolean enableRandomAccess()
```

Returns `true` if random access (TOC) is enabled.

### streamMode()

```java
public boolean streamMode()
```

Returns `true` if stream mode is enabled.

---

## Utility Methods

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

### createChunkProcessor()

```java
public ChunkProcessor createChunkProcessor()
```

Creates a `ChunkProcessor` based on this configuration's compression and encryption settings.

**Example:**
```java
ChunkProcessor processor = config.createChunkProcessor();
// Use processor for reading/writing chunks
```

---

## Builder

The `Builder` class provides a fluent API for constructing configurations.

### chunkSize(int)

Sets the chunk size in bytes.

```java
public Builder chunkSize(int chunkSize)
```

**Parameters:**
- `chunkSize` - Size in bytes (1 KB to 64 MB)

**Example:**
```java
.chunkSize(512 * 1024)  // 512 KB
```

**Guidelines:**
| Use Case | Recommended Size |
|----------|------------------|
| Random access priority | 16-64 KB |
| Balanced | 256 KB (default) |
| Compression priority | 512 KB - 1 MB |
| Large files | 1-4 MB |

### checksumProvider(ChecksumProvider)

Sets the checksum provider.

```java
public Builder checksumProvider(ChecksumProvider provider)
```

**Parameters:**
- `provider` - Checksum implementation

**Example:**
```java
.checksumProvider(ChecksumRegistry.crc32())
```

### checksumAlgorithm(String)

Sets the checksum by algorithm name.

```java
public Builder checksumAlgorithm(String algorithm)
```

**Parameters:**
- `algorithm` - Algorithm name ("xxh3-64", "crc32")

**Throws:** `NoSuchElementException` if algorithm not found

### compression(CompressionProvider)

Enables compression with default level.

```java
public Builder compression(CompressionProvider provider)
```

**Parameters:**
- `provider` - Compression implementation

**Example:**
```java
.compression(CompressionRegistry.zstd())
```

### compression(CompressionProvider, int)

Enables compression with specific level.

```java
public Builder compression(CompressionProvider provider, int level)
```

**Parameters:**
- `provider` - Compression implementation
- `level` - Compression level

**Example:**
```java
.compression(CompressionRegistry.zstd(), 9)  // High compression
.compression(CompressionRegistry.lz4(), 0)   // Fast mode
```

**Level Guidelines:**

| Provider | Level Range | Speed | Ratio |
|----------|-------------|-------|-------|
| ZSTD | 1-3 | Fast | Good |
| ZSTD | 4-6 | Balanced | Better |
| ZSTD | 7-22 | Slow | Best |
| LZ4 | 0 | Fastest | Lower |
| LZ4 | 1-17 | Slower | Better |

### compressionLevel(int)

Sets compression level without changing provider.

```java
public Builder compressionLevel(int level)
```

### encryption(EncryptionProvider, SecretKey)

Enables encryption with provider and key.

```java
public Builder encryption(EncryptionProvider provider, SecretKey key)
```

**Parameters:**
- `provider` - Encryption implementation
- `key` - Secret key for encryption

**Example:**
```java
SecretKey key = aesProvider.generateKey();
.encryption(EncryptionRegistry.aes256Gcm(), key)
```

### encryption(EncryptionProvider, SecretKey, EncryptionBlock)

Enables encryption with full metadata.

```java
public Builder encryption(
    EncryptionProvider provider,
    SecretKey key,
    EncryptionBlock block)
```

**Parameters:**
- `provider` - Encryption implementation
- `key` - Secret key (DEK)
- `block` - Encryption metadata with KDF parameters

**Example:**
```java
.encryption(aesProvider, dek, encryptionBlock)
```

### encryptionBlock(EncryptionBlock)

Sets the encryption block separately.

```java
public Builder encryptionBlock(EncryptionBlock block)
```

### enableRandomAccess(boolean)

Enables or disables table of contents.

```java
public Builder enableRandomAccess(boolean enable)
```

**Parameters:**
- `enable` - `true` for random access support

**Note:** Disabling reduces archive size slightly but prevents entry lookup by name.

### streamMode(boolean)

Enables or disables stream mode.

```java
public Builder streamMode(boolean streamMode)
```

**Parameters:**
- `streamMode` - `true` for stream mode

**Stream Mode:**
- Optimized for single-entry streaming
- Simplified trailer
- No random access

### build()

Creates the configuration.

```java
public ApackConfiguration build()
```

**Returns:** Immutable configuration instance

**Throws:** `IllegalArgumentException` if configuration is invalid

---

## Configuration Examples

### Basic Configuration

```java
ApackConfiguration config = ApackConfiguration.builder()
    .build();
// Uses all defaults
```

### Compression Only

```java
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd(), 6)
    .build();
```

### Encryption Only

```java
EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
SecretKey key = aes.generateKey();

ApackConfiguration config = ApackConfiguration.builder()
    .encryption(aes, key)
    .build();
```

### Compression + Encryption

```java
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd(), 3)
    .encryption(EncryptionRegistry.aes256Gcm(), secretKey)
    .build();
```

### High Compression

```java
ApackConfiguration config = ApackConfiguration.builder()
    .chunkSize(1024 * 1024)  // 1 MB for better ratio
    .compression(CompressionRegistry.zstd(), 19)
    .checksumAlgorithm("crc32")  // Simpler checksum
    .build();
```

### Fast Compression

```java
ApackConfiguration config = ApackConfiguration.builder()
    .chunkSize(64 * 1024)  // Smaller chunks
    .compression(CompressionRegistry.lz4(), 0)
    .build();
```

### Streaming

```java
ApackConfiguration config = ApackConfiguration.builder()
    .streamMode(true)
    .compression(CompressionRegistry.lz4())
    .enableRandomAccess(false)
    .build();
```

### Full-Featured

```java
// Create encryption block
byte[] salt = new byte[32];
SecureRandom.getInstanceStrong().nextBytes(salt);

EncryptionBlock block = EncryptionBlock.builder()
    .kdfAlgorithmId(FormatConstants.KDF_ARGON2ID)
    .cipherAlgorithmId(FormatConstants.ENCRYPTION_AES_256_GCM)
    .kdfIterations(3)
    .kdfMemory(65536)
    .kdfParallelism(4)
    .salt(salt)
    .wrappedKey(encryptedDek)
    .wrappedKeyTag(authTag)
    .build();

ApackConfiguration config = ApackConfiguration.builder()
    .chunkSize(256 * 1024)
    .checksumProvider(ChecksumRegistry.xxh3_64())
    .compression(CompressionRegistry.zstd(), 6)
    .encryption(EncryptionRegistry.aes256Gcm(), dek, block)
    .enableRandomAccess(true)
    .streamMode(false)
    .build();
```

---

## Validation

The configuration validates:

1. **Chunk size** must be in valid range
2. **Encryption key required** when provider is set
3. **Checksum provider** must not be null

```java
// This throws IllegalArgumentException
ApackConfiguration.builder()
    .chunkSize(100)  // Too small (< 1024)
    .build();

// This throws IllegalArgumentException
ApackConfiguration.builder()
    .encryption(aesProvider, null)  // Key required
    .build();
```

---

*Next: [Entries](entries.md) | Previous: [Writer API](writer.md)*
