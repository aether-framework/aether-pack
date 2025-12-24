# Create Command

The `create` command creates a new APACK archive from files and directories.

## Syntax

```
apack create [OPTIONS] <output> <input>...
apack c [OPTIONS] <output> <input>...
```

**Arguments:**
- `<output>` - Path to the output archive file
- `<input>...` - One or more files or directories to add

---

## Options

| Option | Description | Default |
|--------|-------------|---------|
| `-c, --compression <alg>` | Compression algorithm | `zstd` |
| `-l, --level <n>` | Compression level | Provider default |
| `-e, --encrypt <alg>` | Encryption algorithm | None |
| `-p, --password <pwd>` | Encryption password | Interactive prompt |
| `--chunk-size <kb>` | Chunk size in KB | `256` |
| `-r, --recursive` | Recursively add directories | `true` |
| `-v, --verbose` | Show each file as it's added | `false` |

---

## Compression Options

### Algorithm Selection

| Value | Algorithm | Description |
|-------|-----------|-------------|
| `zstd` | Zstandard | High ratio, good speed (default) |
| `lz4` | LZ4 | Very fast, lower ratio |
| `none` | None | No compression |

### Compression Levels

**ZSTD Levels:**

| Level | Speed | Ratio | Use Case |
|-------|-------|-------|----------|
| 1-3 | Fast | Good | Real-time compression |
| 4-6 | Balanced | Better | General purpose (default ~3) |
| 7-12 | Slow | High | Archival storage |
| 13-22 | Very slow | Maximum | Maximum compression |

**LZ4 Levels:**

| Level | Speed | Ratio | Use Case |
|-------|-------|-------|----------|
| 0 | Fastest | Lower | Streaming, real-time |
| 1-9 | Slower | Better | General purpose |
| 10-17 | Slow | Best | High compression mode |

---

## Encryption Options

### Algorithm Selection

| Value | Algorithm | Key Size | Description |
|-------|-----------|----------|-------------|
| `aes-256-gcm` | AES-256-GCM | 256-bit | NIST standard, hardware accelerated |
| `chacha20-poly1305` | ChaCha20-Poly1305 | 256-bit | Fast on software, ARM devices |

### Password Handling

When encryption is enabled:

1. **Interactive prompt** (recommended): Omit `-p` to be prompted securely
2. **Command-line**: Use `-p password` (visible in process list - use with caution)

The password is used with Argon2id key derivation:
- **Iterations**: 3
- **Memory**: 64 MB
- **Parallelism**: 4 threads

---

## Examples

### Basic Archive Creation

```bash
# Create archive with default settings
apack create archive.apack file.txt

# Create from multiple files
apack create archive.apack file1.txt file2.txt data.bin

# Create from directory
apack create archive.apack ./mydata/
```

### Compression Examples

```bash
# ZSTD with default level
apack create -c zstd archive.apack ./data/

# ZSTD with high compression
apack create -c zstd -l 15 archive.apack ./data/

# LZ4 for fast compression
apack create -c lz4 archive.apack ./data/

# LZ4 with higher compression
apack create -c lz4 -l 9 archive.apack ./data/

# No compression (store only)
apack create -c none archive.apack ./data/
```

### Encryption Examples

```bash
# AES-256-GCM encryption (will prompt for password)
apack create -e aes-256-gcm archive.apack ./sensitive/

# ChaCha20-Poly1305 encryption
apack create -e chacha20-poly1305 archive.apack ./sensitive/

# Compression + encryption
apack create -c zstd -l 6 -e aes-256-gcm archive.apack ./data/
```

### Chunk Size Examples

```bash
# Default chunk size (256 KB)
apack create archive.apack ./data/

# Larger chunks for better compression
apack create --chunk-size 1024 archive.apack ./largefile.bin

# Smaller chunks for random access
apack create --chunk-size 64 archive.apack ./database/
```

### Verbose Output

```bash
# Show each file as it's added
apack create -v archive.apack ./myproject/
```

Output:
```
Adding: src/main.java
Adding: src/util/Helper.java
Adding: README.md
Created archive.apack
  Files: 3
  Original size: 45.2 KB
  Archive size: 12.8 KB
  Ratio: 28.3%
```

---

## Directory Handling

### Recursive Addition

By default (`-r true`), directories are added recursively:

```bash
# Adds all files in ./project/ and subdirectories
apack create archive.apack ./project/
```

### Non-Recursive Addition

```bash
# Only add files directly in ./project/, not subdirectories
apack create -r false archive.apack ./project/
```

### Entry Names

Files are stored with relative paths from the input base:

```
Input: ./project/src/main.java
Entry: src/main.java

Input: /absolute/path/file.txt  (single file)
Entry: file.txt
```

---

## MIME Type Detection

The CLI automatically detects MIME types for common file extensions:

| Extension | MIME Type |
|-----------|-----------|
| `.txt` | `text/plain` |
| `.json` | `application/json` |
| `.xml` | `application/xml` |
| `.html` | `text/html` |
| `.png` | `image/png` |
| `.jpg` | `image/jpeg` |
| `.pdf` | `application/pdf` |
| ... | (uses system detection) |

---

## Output Summary

After successful creation, a summary is displayed:

```
Created archive.apack
  Files: 42
  Original size: 15.2 MB
  Archive size: 3.8 MB
  Ratio: 25.0%
```

---

## Error Handling

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "No input files specified" | No files provided | Add file/directory arguments |
| "Unknown compression: xyz" | Invalid compression name | Use `zstd`, `lz4`, or `none` |
| "Unknown encryption: xyz" | Invalid encryption name | Use `aes-256-gcm` or `chacha20-poly1305` |
| "Passwords do not match" | Confirmation failed | Re-enter matching passwords |
| "Skipping (not a file or directory)" | Invalid input path | Check path exists |

### Verbose Mode

Use `-v` or `--verbose` with the global option to see detailed error messages including stack traces:

```bash
apack -v create archive.apack ./data/
```

---

## Best Practices

1. **Choose compression wisely:**
   - Use ZSTD for general-purpose archives
   - Use LZ4 for speed-critical applications
   - Skip compression for already-compressed files (JPEG, MP3, etc.)

2. **Chunk size considerations:**
   - Larger chunks = better compression ratio
   - Smaller chunks = better random access performance
   - Default (256 KB) is a good balance

3. **Security:**
   - Always use interactive password prompts
   - Prefer `aes-256-gcm` for hardware-accelerated systems
   - Use `chacha20-poly1305` on ARM/mobile devices

4. **Large archives:**
   - Increase chunk size for files > 100 MB
   - Use higher compression levels for archival storage

---

*Next: [Extract Command](extract.md) | Previous: [CLI Overview](README.md)*
