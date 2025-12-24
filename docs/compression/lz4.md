# LZ4 Compression

LZ4 is an extremely fast compression algorithm prioritizing speed over compression ratio. It's ideal for real-time applications, caching, and latency-sensitive use cases.

## Algorithm Properties

| Property | Value |
|----------|-------|
| ID | `lz4` |
| Numeric ID | 2 |
| Level Range | 0-17 |
| Default Level | 0 (fast mode) |

---

## Why Choose LZ4

LZ4 is the best choice when:

1. **Speed is critical** - One of the fastest compressors available
2. **Real-time processing** - Sub-millisecond latency per operation
3. **CPU constrained** - Minimal CPU usage
4. **Temporary storage** - Caching, IPC, ephemeral data
5. **High throughput** - Network data, logs, streaming

---

## Compression Modes

LZ4 has two distinct modes:

### Fast Mode (Level 0)

Uses the standard LZ4 algorithm optimized for speed:
- **Compression:** ~500-800 MB/s
- **Decompression:** ~2000+ MB/s
- **Ratio:** Moderate (typically 2:1 to 2.5:1)

This is the default and recommended mode for most LZ4 use cases.

### High Compression Mode (Levels 1-17)

Uses LZ4HC (High Compression) for better ratios:
- **Compression:** ~50-100 MB/s (depends on level)
- **Decompression:** ~2000+ MB/s (same as fast mode)
- **Ratio:** Better than fast mode

**Note:** Even at maximum HC level, LZ4's ratio is typically lower than ZSTD level 1.

---

## Level Guide

| Level | Mode | Speed | Ratio | Use Case |
|-------|------|-------|-------|----------|
| **0** | Fast | ~700 MB/s | Moderate | **Default, streaming** |
| 1-4 | HC Low | ~100 MB/s | Better | Slightly better ratio |
| 5-9 | HC Mid | ~60 MB/s | Good | Balanced HC |
| 10-12 | HC High | ~30 MB/s | Very Good | Better ratio |
| 13-17 | HC Max | ~15 MB/s | Best | Maximum LZ4 ratio |

---

## API Usage

### Provider Access

```java
// Via registry (recommended)
CompressionProvider lz4 = CompressionRegistry.lz4();

// Direct instantiation
Lz4CompressionProvider lz4 = new Lz4CompressionProvider();
```

### Block Compression

```java
Lz4CompressionProvider lz4 = new Lz4CompressionProvider();

// Fast mode (level 0) - recommended
byte[] compressed = lz4.compressBlock(data, 0);

// High compression mode
byte[] hcCompressed = lz4.compressBlock(data, 9);

// Decompress (size must be known)
byte[] decompressed = lz4.decompressBlock(compressed, originalSize);
```

### Stream Compression

```java
// Compress to stream (64 KB blocks)
try (OutputStream out = lz4.compress(fileOutputStream, 0)) {
    out.write(data);
}

// Decompress from stream
try (InputStream in = lz4.decompress(fileInputStream)) {
    byte[] data = in.readAllBytes();
}
```

### With ApackConfiguration

```java
// Fast mode (recommended)
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.lz4(), 0)
    .build();

// High compression mode
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.lz4(), 9)
    .build();
```

---

## Performance Characteristics

### Speed Comparison

| Level | Compression | Decompression |
|-------|-------------|---------------|
| 0 (Fast) | 700 MB/s | 2500+ MB/s |
| 4 (HC) | 100 MB/s | 2500+ MB/s |
| 9 (HC) | 60 MB/s | 2500+ MB/s |
| 17 (HC Max) | 15 MB/s | 2500+ MB/s |

**Key insight:** Decompression speed is constant regardless of compression level.

### Why So Fast?

LZ4's design prioritizes speed through:
- Simple algorithm with minimal branches
- Cache-friendly memory access patterns
- No entropy coding (unlike ZSTD/gzip)
- Optimized for modern CPUs

---

## Memory Usage

LZ4 uses minimal memory:

| Mode | Compression | Decompression |
|------|-------------|---------------|
| Fast | ~16 KB | ~16 KB |
| HC | ~256 KB | ~16 KB |

This makes LZ4 ideal for memory-constrained environments.

---

## Block Format

LZ4 uses a simple block format:

```
[Block Size (4 bytes)][Compressed Data]
```

The stream API uses 64 KB blocks by default, enabling efficient streaming.

---

## Maximum Compressed Size

LZ4's worst-case expansion is calculated by the library:

```java
int maxSize = lz4.maxCompressedSize(inputSize);
```

Typically this is approximately `inputSize + (inputSize / 255) + 16`.

---

## Best Practices

### 1. Use Fast Mode by Default

```java
// Fast mode for most use cases
.compression(CompressionRegistry.lz4(), 0)
```

Fast mode provides the best speed and reasonable compression. Use HC mode only when you specifically need better ratios.

### 2. Consider Data Characteristics

LZ4 works best with:
- Text and structured data
- Game assets
- Database files
- Log files

For highly compressible data, ZSTD may be worth the extra CPU.

### 3. Match Block Size to Use Case

```java
// Smaller blocks for random access
ApackConfiguration.builder()
    .chunkSize(64 * 1024)  // 64 KB
    .compression(CompressionRegistry.lz4(), 0)
    .build();

// Larger blocks for throughput
ApackConfiguration.builder()
    .chunkSize(256 * 1024)  // 256 KB
    .compression(CompressionRegistry.lz4(), 0)
    .build();
```

---

## Comparison with ZSTD

| Aspect | LZ4 | ZSTD |
|--------|-----|------|
| Compression Speed | Extremely Fast | Fast |
| Decompression Speed | Extremely Fast | Very Fast |
| Compression Ratio | Good | Excellent |
| Memory Usage | Very Low | Higher |
| Best For | Real-time, streaming | Archiving |

### When to Choose LZ4

- Real-time game data
- Network protocol compression
- In-memory caching
- Temporary files
- High-throughput logging

### When to Choose ZSTD

- File archiving
- Long-term storage
- Distribution packages
- Size-sensitive transfers

---

## Native Implementation

LZ4-Java uses native code for optimal performance:

```java
// Uses fastest available implementation
LZ4Factory factory = LZ4Factory.fastestInstance();
```

This automatically selects:
1. JNI-based native implementation (fastest)
2. Pure Java implementation (fallback)

---

## Dependencies

```xml
<dependency>
    <groupId>org.lz4</groupId>
    <artifactId>lz4-java</artifactId>
    <version>1.8.x</version>
</dependency>
```

The `lz4-java` library includes:
- JNI native bindings
- Pure Java fallback
- Prebuilt binaries for common platforms

---

## Thread Safety

`Lz4CompressionProvider` is stateless and thread-safe:

```java
// Shared instance
private static final CompressionProvider LZ4 = CompressionRegistry.lz4();

// Safe concurrent use
parallelStream().forEach(chunk -> {
    byte[] compressed = LZ4.compressBlock(chunk, 0);
});
```

The underlying `LZ4Factory` is also thread-safe.

---

*Previous: [ZSTD](zstd.md) | Back to: [Compression Overview](README.md)*
