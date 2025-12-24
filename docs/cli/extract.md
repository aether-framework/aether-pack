# Extract Command

The `extract` command extracts files from an APACK archive to a specified directory.

## Syntax

```
apack extract [OPTIONS] <archive>
apack x [OPTIONS] <archive>
```

**Arguments:**
- `<archive>` - Path to the archive file to extract

---

## Options

| Option | Description | Default |
|--------|-------------|---------|
| `-o, --output <dir>` | Output directory | Current directory |
| `-p, --password <pwd>` | Decryption password | Interactive prompt |
| `--overwrite` | Overwrite existing files | `false` |
| `-v, --verbose` | Show each file as extracted | `false` |
| `--dry-run` | Preview extraction without writing | `false` |

---

## Examples

### Basic Extraction

```bash
# Extract to current directory
apack extract archive.apack

# Extract to specific directory
apack extract -o ./output/ archive.apack

# Using alias
apack x archive.apack -o ./extracted/
```

### Encrypted Archives

```bash
# Interactive password prompt
apack extract archive.apack -o ./output/

# Password on command line (use with caution)
apack extract -p mypassword archive.apack -o ./output/
```

### Overwrite Control

```bash
# Default: skip existing files with warning
apack extract archive.apack -o ./output/

# Overwrite existing files
apack extract --overwrite archive.apack -o ./output/
```

### Verbose Output

```bash
apack extract -v archive.apack -o ./output/
```

Output:
```
Extracting: src/main.java (2.3 KB)
Extracting: src/util/Helper.java (1.1 KB)
Extracting: README.md (512 B)

Extracted 3 file(s), 3.9 KB
```

### Dry Run (Preview)

```bash
apack extract --dry-run archive.apack
```

Output:
```
Would extract: src/main.java (2.3 KB)
Would extract: src/util/Helper.java (1.1 KB)
Would extract: README.md (512 B)

Would extract 3 file(s), 3.9 KB
```

---

## Password Handling

### Interactive Prompt

When extracting an encrypted archive without `-p`:

```
Enter password: ********
```

The password is read securely (not echoed) when a console is available.

### Command-Line Password

```bash
apack extract -p "mypassword" archive.apack
```

**Security Warning:** Command-line passwords are visible in:
- Process listings (`ps`)
- Shell history
- System logs

Use interactive prompts for sensitive data.

### Wrong Password

```
Error: Failed to derive decryption key (wrong password?)
```

The decryption will fail if the password doesn't match. The archive is not modified.

---

## Directory Structure

### Automatic Directory Creation

Parent directories are created automatically:

```
Archive contents:
  data/config/settings.json
  data/logs/app.log

Extraction creates:
  ./output/data/config/settings.json
  ./output/data/logs/app.log
```

### Output Directory

If the output directory doesn't exist, it's created:

```bash
# Creates ./new-folder/ if it doesn't exist
apack extract -o ./new-folder/ archive.apack
```

---

## Existing File Handling

### Default Behavior

Without `--overwrite`, existing files are skipped:

```
Warning: Skipping existing file: ./output/config.json
```

### Overwrite Mode

With `--overwrite`, existing files are replaced:

```bash
apack extract --overwrite archive.apack -o ./output/
```

---

## Compression Detection

The extract command automatically detects the compression algorithm used:

1. Scans entry headers for compression IDs
2. Loads the appropriate decompression provider
3. Decompresses data transparently

**Supported algorithms:**
- ZSTD (ID: 1)
- LZ4 (ID: 2)

---

## Encryption Detection

The extract command detects encryption from the file header:

1. Checks the file header encryption flag
2. Reads the encryption block for KDF parameters
3. Prompts for password if needed
4. Derives the decryption key using Argon2id or PBKDF2
5. Unwraps the content encryption key
6. Decrypts data transparently

**Supported algorithms:**
- AES-256-GCM (ID: 1)
- ChaCha20-Poly1305 (ID: 2)

**Supported KDFs:**
- Argon2id (ID: 1)
- PBKDF2-SHA256 (ID: 2)

---

## Error Handling

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "Archive not found" | File doesn't exist | Check the path |
| "Password required for encrypted archive" | No password provided | Use `-p` or interactive prompt |
| "Failed to derive decryption key" | Wrong password | Re-enter correct password |
| "Skipping existing file" | File exists | Use `--overwrite` or delete file |
| "Archive is encrypted but encryption metadata is missing" | Old archive format | Original key required |

### Encryption Block Missing

Archives created before encryption block support cannot be decrypted with a password:

```
Error: Archive is encrypted but encryption metadata is missing.
This archive may have been created before encryption block support was added.
Such archives cannot be decrypted with a password - the original key is required.
```

---

## Output Summary

### Successful Extraction

```
Extracted 42 file(s), 15.2 MB
```

### Dry Run

```
Would extract 42 file(s), 15.2 MB
```

### With Skipped Files

```
Warning: Skipping existing file: ./output/config.json
Warning: Skipping existing file: ./output/data.bin

Extracted 40 file(s), 14.8 MB
```

---

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success (all files extracted) |
| `1` | Error (password error, I/O error) |

---

## Best Practices

1. **Always verify first:**
   ```bash
   apack verify archive.apack
   apack extract archive.apack -o ./output/
   ```

2. **Use dry-run for preview:**
   ```bash
   apack extract --dry-run archive.apack
   ```

3. **Secure password handling:**
   - Use interactive prompts
   - Avoid `-p` on shared systems

4. **Check disk space:**
   - Use `apack info` to see original sizes
   - Ensure sufficient space in output directory

5. **Backup before overwrite:**
   ```bash
   # Instead of overwriting directly
   apack extract archive.apack -o ./new-output/
   # Then compare and merge as needed
   ```

---

*Next: [List Command](list.md) | Previous: [Create Command](create.md)*
