# Info Command

The `info` command displays detailed information about an APACK archive's format, configuration, and content statistics.

## Syntax

```
apack info [OPTIONS] <archive>
apack i [OPTIONS] <archive>
```

**Arguments:**
- `<archive>` - Path to the archive file to inspect

---

## Options

| Option | Description | Default |
|--------|-------------|---------|
| `--json` | Output in JSON format | `false` |

---

## Information Sections

### File Section

Basic file information:

| Field | Description |
|-------|-------------|
| Path | Full path to the archive file |
| Size | Archive file size on disk |

### Format Section

Archive format details:

| Field | Description |
|-------|-------------|
| Version | Format version (major.minor.patch) |
| Compat Level | Minimum version required to read |
| Mode | Container or Stream |
| Chunk Size | Size of data chunks |
| Checksum | Checksum algorithm used |

### Flags Section

Archive capability flags:

| Field | Description |
|-------|-------------|
| Random Access | Table of contents present |
| Encrypted | Archive-level encryption enabled |
| Compressed | Archive-level compression enabled |

### Content Section

Entry statistics:

| Field | Description |
|-------|-------------|
| Entries | Total number of entries |
| Original Size | Total uncompressed size |
| Stored Size | Total size in archive |
| Compression Ratio | Stored/Original percentage |
| Compressed Entries | Count with compression |
| Encrypted Entries | Count with encryption |
| ECC Entries | Count with error correction |

---

## Examples

### Basic Information

```bash
apack info archive.apack
```

Output:
```
APACK Archive Information
========================================

File:
  Path: archive.apack
  Size: 3.8 MB

Format:
  Version: 1.0.0
  Compat Level: 1
  Mode: Container
  Chunk Size: 256 KB
  Checksum: XXH3-64

Flags:
  Random Access: Yes
  Encrypted: No
  Compressed: Yes

Content:
  Entries: 42
  Original Size: 15.2 MB
  Stored Size: 3.8 MB
  Compression Ratio: 25.0%
  Compressed Entries: 42
  Encrypted Entries: 0
  ECC Entries: 0
```

### JSON Output

```bash
apack info --json archive.apack
```

Output:
```json
{
  "file": "archive.apack",
  "fileSize": 3984560,
  "format": {
    "versionMajor": 1,
    "versionMinor": 0,
    "versionPatch": 0,
    "compatLevel": 1,
    "streamMode": false,
    "chunkSize": 262144,
    "checksumAlgorithm": 2,
    "randomAccess": true,
    "encrypted": false,
    "compressed": true
  },
  "content": {
    "entryCount": 42,
    "originalSize": 15938560,
    "storedSize": 3984560,
    "compressedEntries": 42,
    "encryptedEntries": 0,
    "eccEntries": 0
  }
}
```

---

## Version Information

### Version Fields

| Field | Description |
|-------|-------------|
| Major | Breaking changes |
| Minor | New features (backward compatible) |
| Patch | Bug fixes |
| Compat Level | Minimum reader version required |

### Compatibility

```
Archive Version: 1.2.3
Compat Level: 1

Readers with version >= 1.0.0 can read this archive
```

---

## Mode Types

### Container Mode

Standard archive with table of contents:
- Random access to entries by name
- Full metadata available
- Suitable for most use cases

### Stream Mode

Optimized for streaming:
- No table of contents
- Sequential reading only
- Smaller footer
- Suitable for pipes and streaming

---

## Checksum Algorithms

| ID | Name | Description |
|----|------|-------------|
| 1 | CRC32 | Standard CRC-32 |
| 2 | XXH3-64 | Fast 64-bit hash (default) |
| 3 | XXH3-128 | Fast 128-bit hash |

---

## Scripting Examples

### Extract Specific Information

```bash
# Get version
apack info --json archive.apack | jq '.format.versionMajor'

# Get entry count
apack info --json archive.apack | jq '.content.entryCount'

# Get compression ratio
apack info --json archive.apack | jq '(.content.storedSize / .content.originalSize * 100 | floor)'

# Check if encrypted
apack info --json archive.apack | jq '.format.encrypted'
```

### Compare Archives

```bash
# Compare entry counts
echo "Archive 1: $(apack info --json a1.apack | jq '.content.entryCount') entries"
echo "Archive 2: $(apack info --json a2.apack | jq '.content.entryCount') entries"
```

### Validation Scripts

```bash
#!/bin/bash
# Check archive properties

archive=$1
info=$(apack info --json "$archive")

if echo "$info" | jq -e '.format.encrypted' > /dev/null; then
    echo "Archive is encrypted"
else
    echo "Archive is NOT encrypted"
fi

entries=$(echo "$info" | jq '.content.entryCount')
if [ "$entries" -eq 0 ]; then
    echo "Warning: Archive is empty"
fi
```

### Report Generation

```bash
#!/bin/bash
# Generate archive report

for archive in *.apack; do
    info=$(apack info --json "$archive")
    entries=$(echo "$info" | jq '.content.entryCount')
    size=$(echo "$info" | jq '.fileSize')
    echo "$archive: $entries entries, $size bytes"
done
```

---

## Use Cases

### Pre-Extraction Check

```bash
# Check if you need a password
apack info archive.apack | grep "Encrypted:"

# Check available space needed
apack info archive.apack | grep "Original Size:"
```

### Archive Comparison

```bash
# Quick summary comparison
echo "=== Original ==="
apack info original.apack | grep -E "(Entries|Original Size|Stored Size)"

echo "=== Updated ==="
apack info updated.apack | grep -E "(Entries|Original Size|Stored Size)"
```

### Compression Analysis

```bash
# Check compression effectiveness
apack info archive.apack | grep "Compression Ratio:"

# Find if all entries are compressed
compressed=$(apack info --json archive.apack | jq '.content.compressedEntries')
total=$(apack info --json archive.apack | jq '.content.entryCount')
echo "$compressed of $total entries are compressed"
```

### Format Version Check

```bash
# Check compatibility before processing
version=$(apack info --json archive.apack | jq -r '.format.versionMajor')
if [ "$version" -gt 1 ]; then
    echo "Warning: Archive uses newer format version"
fi
```

---

## Error Handling

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "Archive not found" | File doesn't exist | Check the path |
| Format error | Invalid archive | Verify with `apack verify` |

### Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Error (file not found, format error) |

---

*Next: [Verify Command](verify.md) | Previous: [List Command](list.md)*
