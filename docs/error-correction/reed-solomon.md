# Reed-Solomon Error Correction

Reed-Solomon codes are the error correction mechanism used by APACK. They provide powerful error detection and correction capabilities.

## Algorithm Overview

Reed-Solomon is a type of error-correcting code that works on symbols (typically bytes) rather than individual bits.

### Key Properties

| Property | Value |
|----------|-------|
| Symbol size | 8 bits (1 byte) |
| Max block size | 255 bytes |
| Error correction | Parity bytes / 2 |
| Error detection | Any errors ≤ parity bytes |

### Mathematical Foundation

Reed-Solomon codes are based on polynomial arithmetic over finite fields (Galois fields). Each codeword is a polynomial evaluated at specific points.

```
Data: d₀, d₁, d₂, ..., dₙ₋₁
      ↓
Polynomial: P(x) = d₀ + d₁x + d₂x² + ... + dₙ₋₁xⁿ⁻¹
      ↓
Parity: P(α⁰), P(α¹), ..., P(α^(k-1))
```

---

## Correction Capability

### Error Limits

Given `p` parity bytes:
- Can **correct** up to `p/2` byte errors
- Can **detect** up to `p` byte errors

| Parity | Correct | Detect |
|--------|---------|--------|
| 8 bytes | 4 bytes | 8 bytes |
| 16 bytes | 8 bytes | 16 bytes |
| 32 bytes | 16 bytes | 32 bytes |

### Erasure Correction

When error positions are known (erasures), correction capacity doubles:
- Can correct up to `p` erasures
- Combined: `2E + C ≤ p` (E = erasures, C = unknown errors)

---

## APACK Integration

### Chunk-Level ECC

ECC is applied per chunk:

```
┌─────────────────────────────────────────────────────┐
│ Chunk Header │ Chunk Data │ ECC Parity │           │
│   (24 bytes) │  (N bytes) │ (P bytes)  │ Checksum  │
└─────────────────────────────────────────────────────┘
```

Benefits:
- Parallel encoding/decoding
- Localized error recovery
- Random access not affected

### Entry-Level Flag

The `hasEcc` flag in entry headers indicates ECC protection:

```java
EntryMetadata.builder()
    .name("protected.bin")
    .hasEcc(true)
    .build();
```

---

## Implementation Details

### Galois Field

Reed-Solomon uses GF(2^8) - the Galois field with 256 elements:
- Each element is an 8-bit byte
- Addition = XOR
- Multiplication via lookup tables or polynomial reduction

### Generator Polynomial

For `p` parity bytes, the generator polynomial is:

```
g(x) = (x - α⁰)(x - α¹)(x - α²)...(x - α^(p-1))
```

Where α is a primitive element of GF(2^8).

### Encoding Process

1. Represent data as polynomial D(x)
2. Multiply by x^p (shift left by p positions)
3. Divide by generator polynomial g(x)
4. Append remainder as parity bytes

```java
// Conceptual encoding
byte[] parity = reedSolomon.encode(dataBytes, parityCount);
byte[] codeword = concatenate(dataBytes, parity);
```

### Decoding Process

1. Compute syndromes from received codeword
2. If all syndromes zero: no errors
3. Otherwise: locate and correct errors using Berlekamp-Massey or Euclidean algorithm

---

## Storage Overhead

### Overhead Calculation

```
Overhead = Parity Bytes / Data Bytes × 100%
```

| Data Size | Parity | Total | Overhead |
|-----------|--------|-------|----------|
| 255 bytes | 16 bytes | 271 bytes | 6.3% |
| 223 bytes | 32 bytes | 255 bytes | 14.3% |
| 239 bytes | 16 bytes | 255 bytes | 6.7% |

### APACK Default

APACK uses a balanced configuration:
- Data: 223-239 bytes per RS block
- Parity: 16-32 bytes
- Typical overhead: 6-15%

---

## Performance Characteristics

### Encoding Speed

| Operation | Speed (approx) |
|-----------|----------------|
| Encode | 100-500 MB/s |
| Decode (no errors) | 100-500 MB/s |
| Decode (with errors) | 50-200 MB/s |

Error correction is slower due to:
- Syndrome calculation
- Error locator polynomial solving
- Chien search for error positions
- Forney algorithm for error values

### Optimization Techniques

1. **Lookup tables** - Precomputed GF(2^8) operations
2. **SIMD** - Parallel processing of multiple bytes
3. **Lazy decoding** - Only decode if checksum fails

---

## Error Detection Flow

```
Read Chunk
    ↓
Verify Checksum
    ↓
┌───────────┴───────────┐
│                       │
Checksum OK        Checksum Failed
    ↓                   ↓
Use Data          Has ECC?
                        ↓
                 ┌──────┴──────┐
                 │             │
               Yes            No
                 ↓             ↓
            Attempt       Report Error
            Correction
                 ↓
          ┌──────┴──────┐
          │             │
       Success       Failed
          ↓             ↓
       Use Data    Report Error
```

---

## Comparison with Other Schemes

### vs. Parity (RAID-3/4)

| Aspect | Reed-Solomon | Simple Parity |
|--------|--------------|---------------|
| Correction | Multiple bytes | Single bit |
| Detection | Multiple bytes | Single bit |
| Overhead | Variable | Fixed (1 bit/byte) |
| Complexity | Higher | Lower |

### vs. Checksums

| Aspect | Reed-Solomon | Checksum Only |
|--------|--------------|---------------|
| Correction | Yes | No |
| Detection | Yes | Yes |
| Overhead | Higher | Lower |
| Purpose | Recovery | Detection |

### vs. Hamming Codes

| Aspect | Reed-Solomon | Hamming |
|--------|--------------|---------|
| Symbol size | Byte | Bit |
| Correction | Multiple symbols | Single bit |
| Burst errors | Good | Poor |
| Complexity | Higher | Lower |

---

## Best Practices

### When to Use More Parity

- Long-term archival (years)
- Unreliable storage media
- Critical data without backups
- High error rate environments

### When to Use Less Parity

- Short-term storage
- Reliable storage (enterprise SSDs)
- Multiple backup copies exist
- Storage space constrained

### Recommended Configurations

| Use Case | Data | Parity | Correction |
|----------|------|--------|------------|
| Standard | 239 | 16 | 8 bytes |
| Archival | 223 | 32 | 16 bytes |
| Maximum | 191 | 64 | 32 bytes |

---

## Limitations

### Block Size Limit

RS codes have a maximum block size of 255 bytes (for GF(2^8)). Larger data requires:
- Interleaving multiple RS blocks
- Higher-order Galois fields

APACK handles this automatically by chunking.

### Not a Replacement For

| Protection | Purpose |
|------------|---------|
| Backups | Complete data loss |
| RAID | Hardware failure |
| Checksums | Fast detection |
| Encryption | Confidentiality |

---

## Implementation Status

Current implementation status in APACK:

- [x] GF(2^8) arithmetic
- [x] RS encoding
- [x] RS decoding
- [x] Error detection
- [ ] Automatic chunk integration (planned)
- [ ] Configurable parity levels (planned)

---

*Previous: [ECC Overview](README.md) | Back to: [Documentation](../README.md)*
