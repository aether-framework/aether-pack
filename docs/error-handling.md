# Error Handling

This document describes the exception hierarchy in APACK and provides guidance on handling errors during archive operations.

## Exception Hierarchy

APACK uses a well-defined exception hierarchy rooted at `ApackException`:

```
Exception
    │
ApackException (base exception for all APACK errors)
    │
    ├── FormatException (invalid or corrupted format)
    │   └── UnsupportedVersionException (version compatibility)
    │
    ├── ChecksumException (integrity verification failure)
    │
    ├── CompressionException (compression/decompression error)
    │
    ├── EncryptionException (encryption/decryption error)
    │
    └── EntryNotFoundException (entry lookup failure)
```

All APACK exceptions are **checked exceptions** that extend `Exception`, ensuring callers explicitly handle potential errors.

---

## ApackException

The base class for all APACK-related exceptions.

### Purpose

Provides a common ancestor for all APACK errors, allowing catch-all handling:

```java
try (AetherPackReader reader = AetherPackReader.open(path)) {
    // Process archive...
} catch (ApackException e) {
    // Handle any APACK error
    log.error("APACK operation failed: {}", e.getMessage());
}
```

### Constructors

| Constructor | Description |
|------------|-------------|
| `ApackException(String message)` | Creates exception with message |
| `ApackException(String message, Throwable cause)` | Creates exception with message and cause |

---

## FormatException

Thrown when the APACK file format is invalid or corrupted.

### Common Causes

| Cause | Description |
|-------|-------------|
| Invalid magic number | File doesn't start with "APACK" |
| Truncated file | Unexpected end of file during parsing |
| Invalid header values | Sizes, offsets, or counts out of valid range |
| Missing trailer | Container mode file without valid trailer |
| Corrupted structure | Internal pointers don't match |

### Example Handling

```java
try (AetherPackReader reader = AetherPackReader.open(path)) {
    // Process archive...
} catch (FormatException e) {
    if (e instanceof UnsupportedVersionException ve) {
        System.err.printf("Upgrade required: file needs version %d, " +
            "reader is version %d%n",
            ve.getRequiredVersion(), ve.getCurrentVersion());
    } else {
        System.err.println("Invalid archive format: " + e.getMessage());
    }
}
```

### Recovery

Format exceptions are generally not recoverable. The file is either not a valid APACK archive or has been corrupted beyond repair.

---

## UnsupportedVersionException

A specialized `FormatException` for version compatibility issues.

### Properties

| Method | Description |
|--------|-------------|
| `getRequiredVersion()` | Minimum version the file requires |
| `getCurrentVersion()` | Version of the current reader |

### Example

```java
try (AetherPackReader reader = AetherPackReader.open(path)) {
    // ...
} catch (UnsupportedVersionException e) {
    System.err.printf(
        "Cannot read archive:%n" +
        "  File requires: version %d%n" +
        "  Reader version: %d%n" +
        "Please update to a newer version of aether-pack.%n",
        e.getRequiredVersion(),
        e.getCurrentVersion()
    );
}
```

### Forward Compatibility

APACK is designed with forward compatibility in mind. Unknown flags and fields are generally ignored, allowing older readers to process newer archives that don't use incompatible features. This exception is only thrown for breaking format changes.

---

## ChecksumException

Thrown when checksum verification fails, indicating data corruption.

### Properties

| Method | Description |
|--------|-------------|
| `getExpected()` | The stored checksum value |
| `getActual()` | The computed checksum value |

### Checksum Locations

APACK uses checksums in multiple locations:

| Location | Purpose |
|----------|---------|
| Chunk checksums | Per-chunk data integrity |
| Header checksums | File header integrity |
| TOC checksums | Table of contents integrity |

### Example Handling

```java
try {
    byte[] data = reader.readAllBytes("data.bin");
} catch (ChecksumException e) {
    System.err.println("Data corruption detected!");
    System.err.printf("  Expected: 0x%016X%n", e.getExpected());
    System.err.printf("  Actual:   0x%016X%n", e.getActual());

    // Analyze corruption severity
    long diff = e.getExpected() ^ e.getActual();
    int bitErrors = Long.bitCount(diff);
    System.err.printf("  Bit differences: %d%n", bitErrors);
}
```

### Recovery

If the archive was created with error correction (ECC), recovery may be possible. Without ECC, checksum failures indicate unrecoverable data corruption.

---

## CompressionException

Thrown when compression or decompression fails.

### Common Causes

| Cause | Description |
|-------|-------------|
| Invalid compressed data | Corrupted or truncated stream |
| Unknown algorithm | Compression algorithm ID not recognized |
| Algorithm unavailable | Required codec not on classpath |
| Buffer overflow | Decompressed data exceeds expected size |
| Invalid level | Compression level out of range |

### Example Handling

```java
try {
    byte[] data = reader.readAllBytes("compressed-entry.bin");
} catch (CompressionException e) {
    System.err.println("Decompression failed: " + e.getMessage());

    if (e.getCause() != null) {
        System.err.println("Underlying cause: " + e.getCause().getMessage());
    }

    // Check if it's a missing provider issue
    if (e.getMessage().contains("not found")) {
        System.err.println("Ensure aether-pack-compression is on the classpath");
    }
}
```

### Required Dependencies

```xml
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-compression</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

---

## EncryptionException

Thrown when encryption or decryption fails.

### Common Causes

| Cause | Description |
|-------|-------------|
| Wrong password | Key derived from incorrect password |
| Wrong key | Incorrect encryption key provided |
| Corrupted ciphertext | Encrypted data modified |
| Authentication failure | AEAD tag verification failed |
| Algorithm unavailable | Cipher not available in JCE |
| Invalid key length | Key doesn't match algorithm requirements |

### Security Considerations

For security reasons, the exception message intentionally does not distinguish between "wrong password" and "corrupted data" to prevent information leakage.

### Example Handling

```java
try {
    SecretKey key = deriveKey(password);
    try (AetherPackReader reader = AetherPackReader.open(path, key)) {
        // Read encrypted entries...
    }
} catch (EncryptionException e) {
    // Don't reveal specifics to end users
    System.err.println("Decryption failed. Please verify your password.");

    // For debugging, check the cause
    if (e.getCause() instanceof AEADBadTagException) {
        log.debug("Authentication tag mismatch - wrong key or corrupted data");
    }
}
```

### Required Dependencies

```xml
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-crypto</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

---

## EntryNotFoundException

Thrown when a requested entry is not found in the archive.

### Properties

| Method | Description |
|--------|-------------|
| `getEntryName()` | Name used in lookup (if searched by name) |
| `getEntryId()` | ID used in lookup (if searched by ID) |

### Lookup Methods

| Method | Behavior |
|--------|----------|
| `getEntry(name)` | Returns `Optional`, no exception |
| `requireEntry(name)` | Throws `EntryNotFoundException` |
| `getEntry(id)` | Returns `Optional`, no exception |
| `requireEntry(id)` | Throws `EntryNotFoundException` |

### Example Handling

```java
try (AetherPackReader reader = AetherPackReader.open(path)) {
    // Safe approach: use Optional
    Optional<Entry> config = reader.getEntry("config.json");
    if (config.isEmpty()) {
        System.out.println("config.json not found, using defaults");
        return getDefaultConfig();
    }

    // Throwing approach: for required entries
    try {
        Entry manifest = reader.requireEntry("manifest.json");
    } catch (EntryNotFoundException e) {
        if (e.getEntryName() != null) {
            System.err.println("Missing required entry: " + e.getEntryName());
        } else {
            System.err.println("Missing entry with ID: " + e.getEntryId());
        }
    }
}
```

### Prevention

Use non-throwing lookup methods to handle missing entries gracefully:

```java
// List available entries first
for (Entry entry : reader) {
    System.out.println("Available: " + entry.getName());
}

// Check existence before requiring
if (reader.hasEntry("data.bin")) {
    Entry entry = reader.requireEntry("data.bin");
}
```

---

## Error Handling Patterns

### Pattern 1: Specific Exception Handling

Handle each exception type differently for precise error reporting:

```java
try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
    byte[] data = reader.readAllBytes(entryName);
    // Process data...

} catch (UnsupportedVersionException e) {
    System.err.println("Please upgrade to version " + e.getRequiredVersion());

} catch (FormatException e) {
    System.err.println("Invalid archive format: " + e.getMessage());

} catch (ChecksumException e) {
    System.err.printf("Data corruption at 0x%016X%n", e.getExpected());

} catch (CompressionException e) {
    System.err.println("Decompression error: " + e.getMessage());

} catch (EncryptionException e) {
    System.err.println("Decryption failed - check password");

} catch (EntryNotFoundException e) {
    System.err.println("Entry not found: " + e.getEntryName());

} catch (ApackException e) {
    System.err.println("Unexpected APACK error: " + e.getMessage());
}
```

### Pattern 2: Category-Based Handling

Group related exceptions for simpler handling:

```java
try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
    // Operations...

} catch (FormatException e) {
    // File structure issues (includes version problems)
    handleInvalidFormat(e);

} catch (ChecksumException | CompressionException e) {
    // Data integrity issues
    handleDataCorruption(e);

} catch (EncryptionException e) {
    // Security issues
    handleDecryptionFailure(e);

} catch (EntryNotFoundException e) {
    // Lookup issues
    handleMissingEntry(e);
}
```

### Pattern 3: Retry with Recovery

Attempt recovery for recoverable errors:

```java
public byte[] readWithRetry(AetherPackReader reader, String entryName)
        throws ApackException {
    int maxRetries = 3;

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            return reader.readAllBytes(entryName);

        } catch (ChecksumException e) {
            if (attempt < maxRetries && hasEcc(reader, entryName)) {
                log.warn("Checksum error on attempt {}, trying recovery", attempt);
                attemptEccRecovery(reader, entryName);
            } else {
                throw e;
            }
        }
    }

    throw new ApackException("Max retries exceeded");
}
```

---

## Logging Recommendations

### What to Log

| Event | Level | Example |
|-------|-------|---------|
| Archive opened | INFO | `Opened archive: {path}` |
| Entry read | DEBUG | `Read entry: {name} ({size} bytes)` |
| Checksum mismatch | WARN | `Checksum mismatch in chunk {index}` |
| Decryption failure | WARN | `Decryption failed for archive: {path}` |
| Format error | ERROR | `Invalid format: {message}` |

### What NOT to Log

| Data | Reason |
|------|--------|
| Passwords | Security sensitive |
| Encryption keys | Security sensitive |
| Full exception stacks (in production) | Information leakage |
| Entry contents | May contain sensitive data |

### Example Logging

```java
private static final Logger log = LoggerFactory.getLogger(ArchiveProcessor.class);

public void processArchive(Path path) {
    log.info("Processing archive: {}", path);

    try (AetherPackReader reader = AetherPackReader.open(path)) {
        log.debug("Archive contains {} entries", reader.getEntryCount());

        for (Entry entry : reader) {
            log.trace("Reading entry: {}", entry.getName());
            processEntry(reader, entry);
        }

        log.info("Successfully processed archive: {}", path);

    } catch (ChecksumException e) {
        log.warn("Checksum failure in archive {}: expected 0x{}, actual 0x{}",
            path, Long.toHexString(e.getExpected()), Long.toHexString(e.getActual()));
        throw e;

    } catch (ApackException e) {
        log.error("Failed to process archive {}: {}", path, e.getMessage());
        throw e;
    }
}
```

---

## Exception Chaining

APACK exceptions preserve cause chains for debugging:

```java
try {
    // Operation that wraps lower-level exception
} catch (ApackException e) {
    // Walk the exception chain
    Throwable current = e;
    while (current != null) {
        System.err.println("Caused by: " + current.getClass().getName());
        System.err.println("  Message: " + current.getMessage());
        current = current.getCause();
    }
}
```

### Common Cause Types

| Exception | Common Causes |
|-----------|--------------|
| `CompressionException` | Native library errors, `IOException` |
| `EncryptionException` | `AEADBadTagException`, `InvalidKeyException` |
| `FormatException` | `IOException`, `BufferUnderflowException` |

---

*Back to: [Documentation](README.md) | Related: [API Reference](api/README.md)*
