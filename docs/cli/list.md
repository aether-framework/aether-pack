# List Command

The `list` command displays the contents of an APACK archive without extracting files.

## Syntax

```
apack list [OPTIONS] <archive>
apack l [OPTIONS] <archive>
apack ls [OPTIONS] <archive>
```

**Arguments:**
- `<archive>` - Path to the archive file to list

---

## Options

| Option | Description | Default |
|--------|-------------|---------|
| `-l, --long` | Long listing format with details | `false` |
| `--json` | Output in JSON format | `false` |

---

## Output Formats

### Simple Format (Default)

Lists only file names, one per line:

```bash
apack list archive.apack
```

Output:
```
config.json
src/main.java
src/util/Helper.java
assets/logo.png
README.md
```

**Use cases:**
- Pipe to other commands
- Quick overview
- Script processing

### Long Format (`-l`)

Displays detailed information in a table:

```bash
apack list -l archive.apack
```

Output:
```
ID       Size       Stored     Ratio Flag Name
----------------------------------------------------------------------
       0 1.5 KB     512 B       34%  C    config.json
       1 2.3 KB     856 B       37%  C    src/main.java
       2 1.1 KB     402 B       37%  C    src/util/Helper.java
       3 45.2 KB    42.1 KB     93%  -    assets/logo.png
       4 512 B      256 B       50%  C    README.md
----------------------------------------------------------------------
       5 50.6 KB    44.1 KB     87%       (5 entries)
```

**Columns:**

| Column | Description |
|--------|-------------|
| ID | Unique entry identifier |
| Size | Original (uncompressed) size |
| Stored | Size in archive after processing |
| Ratio | Stored size as percentage of original |
| Flag | Processing flags (see below) |
| Name | Entry name/path |

### JSON Format (`--json`)

Machine-readable output for scripting:

```bash
apack list --json archive.apack
```

Output:
```json
{
  "entries": [
    {
      "id": 0,
      "name": "config.json",
      "mimeType": "application/json",
      "originalSize": 1536,
      "storedSize": 512,
      "chunkCount": 1,
      "compressed": true,
      "encrypted": false,
      "hasEcc": false
    },
    {
      "id": 1,
      "name": "src/main.java",
      "mimeType": "text/x-java-source",
      "originalSize": 2355,
      "storedSize": 856,
      "chunkCount": 1,
      "compressed": true,
      "encrypted": false,
      "hasEcc": false
    }
  ],
  "totalEntries": 2
}
```

---

## Flags

The Flag column in long format shows entry properties:

| Flag | Meaning |
|------|---------|
| `C` | Compressed |
| `E` | Encrypted |
| `R` | Reed-Solomon error correction |
| `-` | No flags (uncompressed, unencrypted) |

**Flag combinations:**
- `C` - Compressed only
- `E` - Encrypted only
- `CE` - Compressed and encrypted
- `CER` - Compressed, encrypted, and ECC
- `-` - No processing

---

## Examples

### Basic Listing

```bash
# Simple list
apack list archive.apack

# Using alias
apack l archive.apack
apack ls archive.apack
```

### Long Format

```bash
# Detailed listing
apack list -l archive.apack
```

### JSON Output

```bash
# Full JSON
apack list --json archive.apack

# Extract just file names
apack list --json archive.apack | jq '.entries[].name'

# Count entries
apack list --json archive.apack | jq '.totalEntries'

# Filter by extension
apack list --json archive.apack | jq '.entries[] | select(.name | endswith(".java"))'

# Calculate total size
apack list --json archive.apack | jq '[.entries[].originalSize] | add'
```

### Scripting Examples

```bash
# Check if a file exists in archive
if apack list archive.apack | grep -q "config.json"; then
    echo "Config found"
fi

# Count files
file_count=$(apack list archive.apack | wc -l)
echo "Archive contains $file_count files"

# List only .java files
apack list archive.apack | grep '\.java$'

# Find large files (requires JSON + jq)
apack list --json archive.apack | jq '.entries[] | select(.originalSize > 1000000)'
```

---

## Understanding Compression Ratio

The ratio shows how much storage space is used:

| Ratio | Meaning |
|-------|---------|
| < 50% | Excellent compression (text, source code) |
| 50-80% | Good compression (mixed content) |
| 80-100% | Minimal compression (already compressed files) |
| > 100% | Expansion (incompressible data stored with overhead) |

**Note:** Incompressible data (JPEG, MP3, encrypted) may show 100% or slightly higher ratios because the chunk format adds a small header overhead.

---

## Footer Summary

The long format includes a footer with totals:

```
----------------------------------------------------------------------
       5 50.6 KB    44.1 KB     87%       (5 entries)
```

| Field | Description |
|-------|-------------|
| First number | Total entry count |
| Size | Total original size |
| Stored | Total stored size |
| Ratio | Overall compression ratio |
| (n entries) | Entry count label |

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

## Use Cases

### Verify Contents Before Extraction

```bash
apack list -l archive.apack
# Check the files are what you expect
apack extract archive.apack -o ./output/
```

### Compare Archives

```bash
# List both and compare
apack list archive1.apack > list1.txt
apack list archive2.apack > list2.txt
diff list1.txt list2.txt
```

### Find Specific Files

```bash
# Find all config files
apack list archive.apack | grep -E '\.conf$|\.json$|\.yaml$|\.yml$'
```

### Audit Large Files

```bash
# Find files over 10 MB (using JSON)
apack list --json archive.apack | jq '.entries[] | select(.originalSize > 10485760) | .name'
```

### Export Entry List

```bash
# CSV format
apack list --json archive.apack | jq -r '.entries[] | [.id, .name, .originalSize, .storedSize] | @csv' > entries.csv
```

---

*Next: [Info Command](info.md) | Previous: [Extract Command](extract.md)*
