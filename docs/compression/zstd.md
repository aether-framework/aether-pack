# ZSTD Compression

Zstandard (ZSTD) is the recommended compression algorithm for APACK archives. Developed by Facebook/Meta, it provides an excellent balance between compression ratio and speed.

## Algorithm Properties

| Property | Value |
|----------|-------|
| ID | `zstd` |
| Numeric ID | 1 |
| Level Range | 1-22 |
| Default Level | 3 |

---

## Why Choose ZSTD

ZSTD is recommended because:

1. **Excellent ratio** - Comparable to LZMA/xz at high levels
2. **Fast decompression** - ~1000-1500 MB/s regardless of compression level
3. **Flexible levels** - 22 levels for fine-tuned ratio/speed tradeoffs
4. **Streaming support** - Efficient with any data size
5. **Modern design** - Incorporates years of compression research

---

## Compression Levels

### Level Guide

| Level | Speed | Ratio | Memory | Use Case |
|-------|-------|-------|--------|----------|
| 1 | ~500 MB/s | Good | Low | Real-time |
| 2 | ~400 MB/s | Good | Low | Fast compression |
| **3** | ~300 MB/s | Good | Low | **Default** |
| 4-6 | ~150 MB/s | Better | Medium | Balanced |
| 7-9 | ~80 MB/s | Very Good | Medium | General archiving |
| 10-15 | ~30 MB/s | Excellent | High | Long-term storage |
| 16-19 | ~10 MB/s | Near-maximum | Very High | Archival |
| 20-22 | ~5 MB/s | Maximum | Extreme | Distribution packages |

### Level Recommendations

| Use Case | Level | Rationale |
|----------|-------|-----------|
| Real-time processing | 1-3 | Speed priority |
| General purpose | 3-6 | Balanced |
| Backup storage | 6-9 | Better ratio |
| Distribution | 15-19 | Maximum practical |
| Extreme compression | 20-22 | When size is critical |

---

## API Usage

### Provider Access

```java
// Via registry (recommended)
CompressionProvider zstd = CompressionRegistry.zstd();

// Direct instantiation
ZstdCompressionProvider zstd = new ZstdCompressionProvider();
```

### Block Compression

```java
ZstdCompressionProvider zstd = new ZstdCompressionProvider();

// Compress with default level (3)
byte[] compressed = zstd.compressBlock(data, zstd.getDefaultLevel());

// Compress with specific level
byte[] highCompressed = zstd.compressBlock(data, 15);

// Decompress (size must be known)
byte[] decompressed = zstd.decompressBlock(compressed, originalSize);
```

### Stream Compression

```java
// Compress to stream
try (OutputStream out = zstd.compress(fileOutputStream, 6)) {
    out.write(data);
}

// Decompress from stream
try (InputStream in = zstd.decompress(fileInputStream)) {
    byte[] data = in.readAllBytes();
}
```

### With ApackConfiguration

```java
// Default level (3)
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd())
    .build();

// High compression
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd(), 15)
    .build();

// Fast compression
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd(), 1)
    .build();
```

---

## Performance Characteristics

### Compression Speed

Approximate compression speeds on modern hardware:

| Level | Speed | Ratio (Calgary) |
|-------|-------|-----------------|
| 1 | 500 MB/s | 2.5:1 |
| 3 | 300 MB/s | 2.8:1 |
| 6 | 100 MB/s | 3.0:1 |
| 9 | 50 MB/s | 3.1:1 |
| 15 | 15 MB/s | 3.3:1 |
| 19 | 5 MB/s | 3.4:1 |

### Decompression Speed

Decompression is nearly constant at ~1000-1500 MB/s regardless of compression level. This is a key advantage of ZSTD: you can use high compression levels without impacting read performance.

---

## Memory Usage

Memory requirements vary by compression level:

| Level Range | Compression Memory | Decompression Memory |
|-------------|-------------------|---------------------|
| 1-3 | 8 KB - 256 KB | ~128 KB |
| 4-9 | 256 KB - 2 MB | ~128 KB |
| 10-15 | 2 MB - 16 MB | ~128 KB |
| 16-19 | 16 MB - 64 MB | ~128 KB |
| 20-22 | 64 MB - 256 MB | ~128 KB |

**Note:** Decompression memory is constant and low regardless of the compression level used.

---

## Maximum Compressed Size

ZSTD's worst-case expansion is approximately:

```
maxSize = inputSize + (inputSize / 128) + 512
```

This means a 1 MB file could expand to at most ~1.008 MB. APACK handles this by storing uncompressed data if compression doesn't reduce size.

---

## Best Practices

### 1. Match Level to Use Case

```java
// Real-time streaming
.compression(CompressionRegistry.zstd(), 1)

// General purpose
.compression(CompressionRegistry.zstd(), 3)

// Archival storage
.compression(CompressionRegistry.zstd(), 15)
```

### 2. Consider Chunk Size

Larger chunks generally compress better:

```java
// Better compression for large files
ApackConfiguration.builder()
    .chunkSize(1024 * 1024)  // 1 MB
    .compression(CompressionRegistry.zstd(), 9)
    .build();
```

### 3. Skip Incompressible Data

APACK automatically skips compression when it increases size, but for known-incompressible data (JPEG, MP3, etc.), consider no compression:

```java
// For media files
ApackConfiguration.builder()
    .compression(null)  // No compression
    .build();
```

---

## Comparison with LZ4

| Aspect | ZSTD | LZ4 |
|--------|------|-----|
| Compression Speed | Fast | Very Fast |
| Decompression Speed | Very Fast | Extremely Fast |
| Compression Ratio | Excellent | Good |
| Level Range | 1-22 | 0-17 |
| Memory Usage | Higher | Lower |
| Best For | General archiving | Real-time streaming |

**Rule of thumb:** Use ZSTD unless you need LZ4's speed.

---

## Dependencies

```xml
<dependency>
    <groupId>com.github.luben</groupId>
    <artifactId>zstd-jni</artifactId>
    <version>1.5.x</version>
</dependency>
```

The `zstd-jni` library includes:
- Native JNI bindings
- Prebuilt binaries for Windows, Linux, macOS
- ARM and x86 support

---

## Thread Safety

`ZstdCompressionProvider` is stateless and thread-safe. A single instance can be shared across multiple threads:

```java
// Shared instance
private static final CompressionProvider ZSTD = CompressionRegistry.zstd();

// Safe concurrent use
parallelStream().forEach(chunk -> {
    byte[] compressed = ZSTD.compressBlock(chunk, 6);
});
```

---

*Next: [LZ4](lz4.md) | Previous: [Compression Overview](README.md)*
