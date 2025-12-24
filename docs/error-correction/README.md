# Error Correction Overview

APACK supports optional error correction using Reed-Solomon codes. Error Correction Codes (ECC) can detect and recover from data corruption.

## Purpose

Error correction protects against:
- Bit flips in storage media
- Partial sector failures
- Transmission errors
- Silent data corruption (bit rot)

---

## When to Use ECC

### Recommended For

| Use Case | Rationale |
|----------|-----------|
| Long-term archival | Media degrades over time |
| Critical backups | Cannot afford data loss |
| Unreliable storage | Flash media, optical discs |
| Network transfers | Packet corruption possible |

### Not Needed For

| Use Case | Rationale |
|----------|-----------|
| Short-term storage | Low corruption risk |
| Verified transfers | TCP provides checksums |
| Redundant storage | RAID provides protection |
| Re-creatable data | Can regenerate if lost |

---

## How It Works

### Reed-Solomon Codes

APACK uses Reed-Solomon error correction:

1. **Encoding**: Parity data is added to each chunk
2. **Storage**: Data + parity stored together
3. **Detection**: Checksum identifies corruption
4. **Recovery**: Parity data reconstructs original

```
Original Chunk (N bytes)
       ↓
┌──────────────────────────────────────────────┐
│  Data (N bytes)  │  Parity (P bytes)         │
└──────────────────────────────────────────────┘
       ↓
If corruption detected, parity reconstructs data
```

### Correction Capability

| Parity Bytes | Max Correctable | Overhead |
|--------------|-----------------|----------|
| 8 | 4 bytes | 3.1% |
| 16 | 8 bytes | 6.3% |
| 32 | 16 bytes | 12.5% |
| 64 | 32 bytes | 25% |

**Rule**: Can correct up to `parity_bytes / 2` byte errors.

---

## API Usage

### Enabling ECC

```java
EntryMetadata metadata = EntryMetadata.builder()
    .name("critical-data.bin")
    .hasEcc(true)
    .build();

try (AetherPackWriter writer = AetherPackWriter.create(path)) {
    writer.addEntry(metadata, dataStream);
}
```

### Checking ECC Status

```java
try (AetherPackReader reader = AetherPackReader.open(path)) {
    for (Entry entry : reader) {
        if (entry.hasEcc()) {
            System.out.println(entry.getName() + " has ECC protection");
        }
    }
}
```

---

## Trade-offs

### Benefits

- Automatic corruption recovery
- Silent repair without user intervention
- Protection against bit rot
- Works with any storage medium

### Costs

| Cost | Impact |
|------|--------|
| Storage | 3-25% increase (configurable) |
| CPU | Encoding/decoding overhead |
| Complexity | Additional processing |

---

## Integration with Other Features

### ECC + Compression

Processing order:
```
Original → Compress → Add ECC → Store
Read → Verify ECC → Decompress → Original
```

ECC protects the compressed data. If compression produces smaller output, ECC overhead on compressed data is less than on original.

### ECC + Encryption

Processing order:
```
Original → Compress → Encrypt → Add ECC → Store
Read → Verify ECC → Decrypt → Decompress → Original
```

ECC protects the encrypted data. This ensures:
- Corruption detected before decryption attempted
- Corrupted ciphertext can be repaired
- Authentication tag protected by ECC

---

## Verification

The `verify` command checks ECC-protected entries:

```bash
apack verify archive.apack

# Output:
# Verifying: archive.apack
#   OK: data.bin (with ECC, 0 corrections)
#   OK: image.png (no ECC)
# OK - 2 entries verified
```

If corruption is found and repaired:
```
#   OK: data.bin (with ECC, 2 bytes corrected)
```

---

## Limitations

### Cannot Recover From

| Scenario | Reason |
|----------|--------|
| Total data loss | No data to correct |
| Corruption > parity capacity | Too many errors |
| Parity section corrupted | Recovery data damaged |
| Hardware failure | Media inaccessible |

### Defense in Depth

ECC should be one layer of protection:
- Use reliable storage media
- Maintain multiple copies
- Verify archives periodically
- Consider RAID for critical data

---

## Implementation Status

The ECC flag is defined in the format, but full Reed-Solomon implementation is planned for a future release. Current functionality:
- [x] ECC flag in entry headers
- [x] ECC detection in reader
- [ ] Automatic ECC encoding (planned)
- [ ] Automatic ECC correction (planned)

---

*Next: [Reed-Solomon](reed-solomon.md) | Back to: [Documentation](../README.md)*
