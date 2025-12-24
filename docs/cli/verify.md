# Verify Command

The `verify` command validates the integrity of an APACK archive by checking its structure and data checksums.

## Syntax

```
apack verify [OPTIONS] <archive>
apack v [OPTIONS] <archive>
```

**Arguments:**
- `<archive>` - Path to the archive file to verify

---

## Options

| Option | Description | Default |
|--------|-------------|---------|
| `-v, --verbose` | Show details for each entry | `false` |
| `--quick` | Quick check (headers only) | `false` |

---

## Verification Modes

### Full Verification (Default)

Reads all data and verifies checksums:

```bash
apack verify archive.apack
```

**What it checks:**
- File header validity
- Entry header structure
- Chunk header integrity
- Data checksums (reads all data)
- Decompression validity
- Trailer structure

**Detects:**
- Checksum mismatches
- Corrupted compressed data
- Truncated entries
- Invalid chunk sequences
- Format violations

### Quick Verification (`--quick`)

Validates headers without reading data:

```bash
apack verify --quick archive.apack
```

**What it checks:**
- File header validity
- Entry header structure
- Entry metadata
- Trailer structure

**Does NOT check:**
- Data checksums
- Chunk data integrity
- Decompression validity

**Use when:**
- Time is critical
- Checking for structural issues only
- Initial quick scan before full verification

---

## Examples

### Basic Verification

```bash
# Full verification
apack verify archive.apack
```

Output (success):
```
Verifying: archive.apack

OK - 42 entries verified (15.2 MB)
```

Output (failure):
```
Verifying: corrupted.apack
  FAIL: data/file.bin - Checksum mismatch

FAILED - 1 of 42 entries failed verification
```

### Verbose Mode

```bash
apack verify -v archive.apack
```

Output:
```
Verifying: archive.apack
  Format version: 1.0.0
  Entries: 42
  Chunk size: 256 KB
  Random access: yes
  Encrypted: no

  OK: config.json (1.5 KB)
  OK: src/main.java (2.3 KB)
  OK: src/util/Helper.java (1.1 KB)
  OK: assets/logo.png (45.2 KB)
  OK: README.md (512 B)

OK - 5 entries verified (50.6 KB)
```

### Quick Mode

```bash
apack verify --quick archive.apack
```

Output:
```
Verifying: archive.apack

OK - 42 entries verified
```

Note: Quick mode doesn't show data size since data isn't read.

---

## Exit Codes

| Code | Meaning | Description |
|------|---------|-------------|
| `0` | Success | All entries verified |
| `1` | Verification Failed | One or more entries failed |
| `2` | Archive Error | Could not read archive |

### Using in Scripts

```bash
#!/bin/bash

if apack verify archive.apack; then
    echo "Archive is valid"
else
    echo "Archive verification failed"
    exit 1
fi
```

```bash
# Check exit code explicitly
apack verify archive.apack
case $? in
    0) echo "Valid" ;;
    1) echo "Corrupted" ;;
    2) echo "Cannot read archive" ;;
esac
```

---

## Failure Types

### Checksum Mismatch

```
FAIL: data/file.bin - Checksum mismatch
```

**Cause:** Data corruption in storage or transmission.

**Solution:**
- Restore from backup
- Re-download if transferred
- Check storage medium

### Decompression Error

```
FAIL: data/compressed.bin - Decompression failed
```

**Cause:** Corrupted compressed data.

**Solution:**
- Data is likely unrecoverable
- Restore from backup
- If ECC enabled, try recovery

### Truncated Entry

```
FAIL: large/file.bin - Unexpected end of data
```

**Cause:** Archive file is truncated.

**Solution:**
- Check for incomplete copy
- Verify disk space during creation
- Re-create archive

### Header Error

```
Error reading archive: Invalid entry header
```

**Cause:** Structural corruption.

**Solution:**
- Archive may be severely damaged
- Restore from backup

---

## Verbose Output Details

### Archive Header Information

```
  Format version: 1.0.0    # Format version
  Entries: 42              # Total entry count
  Chunk size: 256 KB       # Chunk size setting
  Random access: yes       # TOC present
  Encrypted: no            # Encryption status
```

### Entry Status

```
  OK: filename.txt (1.5 KB)     # Entry verified
  FAIL: broken.bin - Error msg   # Entry failed
```

### Summary

```
OK - 42 entries verified (15.2 MB)     # All passed
FAILED - 1 of 42 entries failed        # Some failed
```

---

## Use Cases

### Backup Verification

```bash
#!/bin/bash
# Verify backups after creation

backup_dir="/backups"

for archive in "$backup_dir"/*.apack; do
    echo "Verifying: $archive"
    if apack verify "$archive"; then
        echo "OK"
    else
        echo "FAILED: $archive" >> /var/log/backup-errors.log
    fi
done
```

### Transfer Verification

```bash
# After copying or downloading
scp server:/data/archive.apack ./

if apack verify archive.apack; then
    echo "Transfer successful"
else
    echo "Transfer corrupted, retrying..."
    scp server:/data/archive.apack ./
fi
```

### Pre-Extraction Check

```bash
# Verify before extracting
if apack verify archive.apack; then
    apack extract archive.apack -o ./output/
else
    echo "Archive is corrupted, cannot extract"
    exit 1
fi
```

### Periodic Health Check

```bash
#!/bin/bash
# Weekly archive verification

find /archives -name "*.apack" -print0 | while IFS= read -r -d '' archive; do
    if ! apack verify --quick "$archive" 2>/dev/null; then
        echo "$(date): Verification failed: $archive" >> /var/log/archive-health.log
    fi
done
```

### CI/CD Pipeline

```yaml
# GitHub Actions example
steps:
  - name: Create Archive
    run: apack create build.apack ./dist/

  - name: Verify Archive
    run: apack verify build.apack

  - name: Upload if Valid
    if: success()
    run: upload-to-storage build.apack
```

---

## Compression Detection

The verify command automatically detects compression:

1. Scans entries for compression algorithm ID
2. Loads appropriate decompression provider
3. Decompresses and verifies each chunk

**Note:** Verification of encrypted archives requires the decryption key. Use the extract command with password for encrypted archive verification.

---

## Performance Considerations

### Full Verification

- Reads all data from disk
- CPU usage for decompression
- Time proportional to archive size
- Recommended for critical verification

### Quick Verification

- Reads only headers
- Minimal I/O and CPU
- Very fast for large archives
- Good for initial checks

### Recommendations

| Archive Size | Recommendation |
|--------------|----------------|
| < 100 MB | Always use full verification |
| 100 MB - 1 GB | Full for important archives |
| > 1 GB | Quick first, full for backups |

---

## Error Handling

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "Archive not found" | File doesn't exist | Check the path |
| "Error reading archive" | Format/I/O error | Archive may be corrupted |
| "Unknown compression" | Missing provider | Install compression module |

### Verbose Debugging

```bash
# Use global verbose flag for stack traces
apack -v verify archive.apack
```

This shows full exception details for diagnosis.

---

*Previous: [Info Command](info.md) | Back to: [CLI Overview](README.md)*
