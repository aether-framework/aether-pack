# Performance Tuning

This guide covers performance optimization strategies for APACK archives, including chunk size tuning, compression level selection, and memory management.

## Quick Reference

| Goal | Recommendation |
|------|----------------|
| Maximum speed | LZ4, small chunks (64 KB), no encryption |
| Maximum compression | ZSTD level 19-22, larger chunks (1 MB) |
| Balanced | ZSTD level 3-6, 256 KB chunks |
| Low memory | Smaller chunks (64-128 KB) |
| Random access | Smaller chunks for finer granularity |

---

## Chunk Size Tuning

### Default Chunk Size

APACK uses **256 KB (262,144 bytes)** as the default chunk size, providing a balanced trade-off between compression ratio and random access granularity.

### Chunk Size Impact

| Factor | Smaller Chunks | Larger Chunks |
|--------|---------------|---------------|
| Compression ratio | Lower | Higher |
| Random access | Finer granularity | Coarser granularity |
| Memory usage | Lower | Higher |
| Overhead | Higher (more headers) | Lower |
| Parallelism | Better | Worse |

### Recommended Chunk Sizes

| Use Case | Chunk Size | Rationale |
|----------|------------|-----------|
| Random access | 64-128 KB | Fine-grained seeking |
| Streaming read | 256-512 KB | Balanced performance |
| Maximum compression | 1-4 MB | Better ratio |
| Low memory | 64 KB | Minimal buffers |
| Network transfer | 256 KB | Match TCP window |

### Configuration

```java
ApackConfiguration config = ApackConfiguration.builder()
    .chunkSize(128 * 1024)  // 128 KB for random access
    .compression(CompressionRegistry.zstd(), 6)
    .build();

try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
    // Write entries...
}
```

### Chunk Size Formula

For random access with target seek granularity `G`:

```
Recommended chunk size = G / 2
```

For example, if you need to seek to any 100 KB position efficiently, use 50 KB chunks.

---

## Compression Level Selection

### ZSTD Levels

| Level | Use Case | Speed | Ratio |
|-------|----------|-------|-------|
| 1-3 | Real-time, high throughput | Very fast | Good |
| 4-6 | General purpose | Fast | Better |
| 7-12 | Archival, storage | Medium | Very good |
| 13-19 | Maximum compression | Slow | Excellent |
| 20-22 | Ultra (diminishing returns) | Very slow | Maximum |

### ZSTD Speed vs Ratio

```
Level 1:  ~500 MB/s compression,  2.0x ratio
Level 3:  ~350 MB/s compression,  2.5x ratio (default)
Level 6:  ~200 MB/s compression,  2.8x ratio
Level 12: ~50 MB/s compression,   3.0x ratio
Level 19: ~10 MB/s compression,   3.2x ratio
```

Decompression speed is relatively constant (~800+ MB/s) regardless of compression level.

### LZ4 Levels

| Mode | Level | Speed | Ratio |
|------|-------|-------|-------|
| Fast | 0 | Extremely fast | Lower |
| Fast | 1-9 | Very fast | Moderate |
| HC | 10-17 | Fast | Better |

### LZ4 Speed vs Ratio

```
Fast mode:  ~700+ MB/s compression, 1.8x ratio
HC mode:    ~100 MB/s compression,  2.2x ratio
```

Decompression: ~2+ GB/s regardless of compression mode.

### Choosing Between ZSTD and LZ4

| Criterion | ZSTD | LZ4 |
|-----------|------|-----|
| Compression ratio | Better | Good |
| Compression speed | Good | Excellent |
| Decompression speed | Excellent | Excellent |
| Memory usage | Moderate | Low |
| Best for | Archival, storage | Real-time, streaming |

---

## Encryption Performance

### Algorithm Comparison

| Algorithm | With AES-NI | Without AES-NI |
|-----------|-------------|----------------|
| AES-256-GCM | 4-8 GB/s | 100-200 MB/s |
| ChaCha20-Poly1305 | 300-500 MB/s | 300-500 MB/s |

### Recommendations

```
AES-NI available?
├── Yes → Use AES-256-GCM (hardware accelerated)
└── No
    ├── Speed critical? → Use ChaCha20-Poly1305
    └── NIST compliance needed? → Use AES-256-GCM
```

### Checking AES-NI Availability

```java
// Simple heuristic - test actual performance
long start = System.nanoTime();
byte[] test = new byte[1024 * 1024];
EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
SecretKey key = aes.generateKey();

for (int i = 0; i < 10; i++) {
    aes.encryptBlock(test, key);
}

long elapsed = System.nanoTime() - start;
double mbps = (10.0 * test.length) / (elapsed / 1_000_000_000.0) / (1024 * 1024);

boolean hasAesNi = mbps > 500;  // Threshold
System.out.printf("AES speed: %.1f MB/s, AES-NI likely: %s%n", mbps, hasAesNi);
```

---

## Memory Management

### Memory Usage by Operation

| Operation | Memory Per Chunk |
|-----------|------------------|
| Uncompressed read | chunkSize |
| Compressed read | chunkSize + compressed size |
| Encrypted read | chunkSize + encrypted size |
| Full pipeline | ~2-3x chunkSize |

### Reducing Memory Usage

1. **Use smaller chunks:**
   ```java
   .chunkSize(64 * 1024)  // 64 KB instead of 256 KB
   ```

2. **Stream processing:**
   ```java
   // Don't buffer entire entry
   try (InputStream in = reader.openEntry(entry)) {
       byte[] buffer = new byte[8192];
       int read;
       while ((read = in.read(buffer)) != -1) {
           process(buffer, 0, read);
       }
   }
   ```

3. **Close resources promptly:**
   ```java
   // Release buffers after each entry
   for (Entry entry : reader) {
       try (InputStream in = reader.openEntry(entry)) {
           processEntry(in);
       }  // InputStream closed, buffers released
   }
   ```

### Buffer Sizing

| Scenario | Buffer Size | Rationale |
|----------|-------------|-----------|
| Small files | 8 KB | Reduce overhead |
| Streaming | 64 KB | Balance memory/throughput |
| Large files | 256 KB | Match chunk size |

---

## Parallel Processing

### Reading Multiple Entries

Each `AetherPackReader` instance is NOT thread-safe. For parallel processing, open multiple readers:

```java
List<String> entryNames = getEntryNames(archivePath);

// Process entries in parallel with separate readers
entryNames.parallelStream().forEach(name -> {
    try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
        byte[] data = reader.readAllBytes(name);
        processEntry(name, data);
    } catch (ApackException | IOException e) {
        throw new RuntimeException(e);
    }
});
```

### Writing Multiple Archives

Writers can be parallelized across different output files:

```java
List<Path> inputDirs = getInputDirectories();

// Create archives in parallel
inputDirs.parallelStream().forEach(dir -> {
    Path archive = dir.resolveSibling(dir.getFileName() + ".apack");
    try (AetherPackWriter writer = AetherPackWriter.create(archive)) {
        addDirectoryContents(writer, dir);
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
});
```

### Parallel Compression

Compression providers are thread-safe and can be shared:

```java
CompressionProvider zstd = CompressionRegistry.zstd();

// Compress chunks in parallel
byte[][] compressed = chunks.parallelStream()
    .map(chunk -> {
        try {
            return zstd.compressBlock(chunk, 6);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    })
    .toArray(byte[][]::new);
```

---

## Benchmarking

### Simple Benchmark

```java
public void benchmark(Path archivePath, int iterations) {
    // Warm up
    for (int i = 0; i < 3; i++) {
        readArchive(archivePath);
    }

    // Measure
    long totalBytes = 0;
    long start = System.nanoTime();

    for (int i = 0; i < iterations; i++) {
        totalBytes += readArchive(archivePath);
    }

    long elapsed = System.nanoTime() - start;
    double seconds = elapsed / 1_000_000_000.0;
    double mbps = (totalBytes / (1024.0 * 1024.0)) / seconds;

    System.out.printf("Throughput: %.2f MB/s%n", mbps);
}

private long readArchive(Path path) throws Exception {
    long bytes = 0;
    try (AetherPackReader reader = AetherPackReader.open(path)) {
        for (Entry entry : reader) {
            bytes += reader.readAllBytes(entry.getName()).length;
        }
    }
    return bytes;
}
```

### Compression Ratio Analysis

```java
public void analyzeCompression(Path inputDir, int level) throws Exception {
    CompressionProvider zstd = CompressionRegistry.zstd();

    long totalOriginal = 0;
    long totalCompressed = 0;

    for (Path file : Files.list(inputDir).toList()) {
        byte[] original = Files.readAllBytes(file);
        byte[] compressed = zstd.compressBlock(original, level);

        totalOriginal += original.length;
        totalCompressed += compressed.length;

        double ratio = (double) original.length / compressed.length;
        System.out.printf("%s: %.2fx compression%n", file.getFileName(), ratio);
    }

    double overallRatio = (double) totalOriginal / totalCompressed;
    System.out.printf("Overall: %.2fx compression (level %d)%n", overallRatio, level);
}
```

---

## Optimization Checklist

### For Write Performance

- [ ] Choose appropriate compression level (lower = faster)
- [ ] Consider LZ4 for maximum speed
- [ ] Use larger chunks for better compression ratio
- [ ] Disable encryption if not needed
- [ ] Use streaming writes for large files

### For Read Performance

- [ ] Use random access (TOC) for selective reading
- [ ] Stream large entries instead of buffering
- [ ] Close readers promptly to release resources
- [ ] Use parallel readers for concurrent access

### For Storage Efficiency

- [ ] Use ZSTD level 6+ for better compression
- [ ] Use larger chunks (512 KB - 1 MB)
- [ ] Consider entry-level compression decisions
- [ ] Measure actual compression ratios for your data

### For Low Memory Environments

- [ ] Use smaller chunks (64-128 KB)
- [ ] Stream instead of buffering
- [ ] Process entries sequentially
- [ ] Close resources immediately after use

---

## Common Performance Issues

### Issue: Slow Compression

| Symptom | Cause | Solution |
|---------|-------|----------|
| High CPU, slow write | Level too high | Reduce ZSTD level |
| Slow small files | Per-file overhead | Batch into single archive |

### Issue: Slow Decompression

| Symptom | Cause | Solution |
|---------|-------|----------|
| Slow random access | Large chunks | Use smaller chunks |
| Memory pressure | Chunk too large | Reduce chunk size |

### Issue: High Memory Usage

| Symptom | Cause | Solution |
|---------|-------|----------|
| OOM errors | Large chunks | Reduce chunk size |
| GC pressure | Buffering entries | Stream instead |

### Issue: Poor Compression Ratio

| Symptom | Cause | Solution |
|---------|-------|----------|
| Low ratio | Already compressed data | Disable compression |
| Low ratio | Small chunks | Increase chunk size |
| Low ratio | Level too low | Increase compression level |

---

*Back to: [Documentation](README.md) | Related: [Compression](compression/README.md) | [Encryption](encryption/README.md)*
