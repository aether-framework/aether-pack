# Compression Overview

APACK supports pluggable compression algorithms through the `CompressionProvider` SPI. The library includes two built-in providers: ZSTD and LZ4.

## Available Algorithms

| Algorithm | ID | Speed | Ratio | Use Case |
|-----------|-----|-------|-------|----------|
| ZSTD | `zstd` | Fast | Excellent | General purpose (recommended) |
| LZ4 | `lz4` | Very Fast | Good | Real-time, streaming |

---

## Choosing an Algorithm

### Use ZSTD When

- **Best ratio matters** - ZSTD achieves compression ratios comparable to LZMA
- **Decompression speed is critical** - ZSTD decompresses at ~1000+ MB/s regardless of level
- **General-purpose archiving** - Default choice for most applications
- **You have CPU budget** - Higher levels trade CPU time for better ratio

### Use LZ4 When

- **Speed is paramount** - LZ4 is one of the fastest compressors available
- **Real-time processing** - Sub-millisecond latency per chunk
- **CPU constrained** - Minimal CPU usage compared to other algorithms
- **Temporary storage** - Caching, IPC, or ephemeral data
- **High-throughput streaming** - Network data, logs, telemetry

---

## Compression Levels

Both algorithms support configurable compression levels:

### ZSTD Levels (1-22)

| Level | Speed | Ratio | Use Case |
|-------|-------|-------|----------|
| 1-3 | Very fast | Good | Real-time, default (3) |
| 4-9 | Fast | Better | General archiving |
| 10-15 | Moderate | Very good | Backup storage |
| 16-19 | Slow | Excellent | Long-term archival |
| 20-22 | Very slow | Maximum | Distribution packages |

### LZ4 Levels (0-17)

| Level | Mode | Speed | Ratio | Use Case |
|-------|------|-------|-------|----------|
| 0 | Fast | Extremely fast | Moderate | Default, streaming |
| 1-9 | HC | Fast | Better | General purpose |
| 10-17 | HC High | Moderate | Good | Best LZ4 ratio |

---

## API Usage

### Via CompressionRegistry

```java
// Get providers
CompressionProvider zstd = CompressionRegistry.zstd();
CompressionProvider lz4 = CompressionRegistry.lz4();

// By name
Optional<CompressionProvider> provider = CompressionRegistry.get("zstd");

// By numeric ID
Optional<CompressionProvider> provider = CompressionRegistry.getById(1);
```

### With ApackConfiguration

```java
// ZSTD compression
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd(), 6)
    .build();

// LZ4 fast mode
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.lz4(), 0)
    .build();
```

### With ChunkProcessor

```java
ChunkProcessor processor = ChunkProcessor.builder()
    .compression(CompressionRegistry.zstd(), 9)
    .build();

// Compress
ProcessedChunk result = processor.processForWrite(data, data.length);
if (result.compressed()) {
    // Compression was beneficial
}

// Decompress
byte[] original = processor.processForRead(
    result.data(),
    result.originalSize(),
    result.compressed(),
    false
);
```

---

## Compression Behavior

### Adaptive Compression

APACK only uses compressed data if it's smaller than the original:

```java
ProcessedChunk result = processor.processForWrite(jpegData, jpegData.length);
// result.compressed() = false for incompressible data
// The original data is stored to avoid "negative compression"
```

This prevents expansion for already-compressed data like JPEG, MP3, or encrypted content.

### Per-Chunk Compression

Each chunk is compressed independently, enabling:
- Random access without decompressing entire files
- Parallel decompression
- Resilience (corruption affects only one chunk)

---

## Performance Comparison

Approximate performance on modern hardware (MB/s):

| Operation | ZSTD L3 | ZSTD L9 | LZ4 L0 | LZ4 L9 |
|-----------|---------|---------|--------|--------|
| Compress | 300-500 | 50-100 | 500-800 | 50-100 |
| Decompress | 1000+ | 1000+ | 2000+ | 2000+ |

**Note:** Decompression speed is nearly constant regardless of compression level for both algorithms.

---

## Memory Usage

### ZSTD

- Compression: Scales with level (8 KB to several MB)
- Decompression: ~128 KB typical

### LZ4

- Compression: ~16 KB (fast mode), ~256 KB (HC mode)
- Decompression: ~16 KB

---

## Dependencies

| Algorithm | Library | Maven Coordinate |
|-----------|---------|------------------|
| ZSTD | zstd-jni | `com.github.luben:zstd-jni` |
| LZ4 | lz4-java | `org.lz4:lz4-java` |

Both libraries use native code for optimal performance and include prebuilt binaries for common platforms.

---

## Thread Safety

Both compression providers are stateless and thread-safe. A single provider instance can be shared across multiple threads without synchronization.

```java
// Safe to share
private static final CompressionProvider ZSTD = CompressionRegistry.zstd();

// Use from multiple threads
executor.submit(() -> {
    byte[] compressed = ZSTD.compressBlock(data, 6);
});
```

---

*Next: [ZSTD](zstd.md) | Back to: [Documentation](../README.md)*
