# Getting Started with APACK

This guide will help you get up and running with the Aether Pack (APACK) library quickly.

## Prerequisites

- **Java 17** or later
- **Maven** or **Gradle** for dependency management

## Installation

### Maven

Add the following dependencies to your `pom.xml`:

```xml
<!-- Core library (required) -->
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-core</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>

<!-- Compression support (optional) -->
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-compression</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>

<!-- Encryption support (optional) -->
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-crypto</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

### Gradle

Add the following to your `build.gradle`:

```groovy
dependencies {
    // Core library (required)
    implementation 'de.splatgames.aether.pack:aether-pack-core:0.1.0'

    // Compression support (optional)
    implementation 'de.splatgames.aether.pack:aether-pack-compression:0.1.0'

    // Encryption support (optional)
    implementation 'de.splatgames.aether.pack:aether-pack-crypto:0.1.0'
}
```

## Creating Your First Archive

Here's a simple example that creates an APACK archive with a few files:

```java
import de.splatgames.aether.pack.core.AetherPackWriter;
import de.splatgames.aether.pack.core.entry.EntryMetadata;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class CreateArchiveExample {
    public static void main(String[] args) throws Exception {
        Path archivePath = Path.of("my-archive.apack");

        try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
            // Add a text file from a string
            String content = "Hello, APACK!";
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            writer.addEntry("hello.txt", new ByteArrayInputStream(bytes));

            // Add a file from disk
            writer.addEntry("config.json", Path.of("/path/to/config.json"));

            // Add with custom metadata
            EntryMetadata metadata = EntryMetadata.builder()
                .name("data/document.pdf")
                .mimeType("application/pdf")
                .attribute("author", "John Doe")
                .attribute("version", 1L)
                .build();
            writer.addEntry(metadata, new ByteArrayInputStream(pdfBytes));
        }

        System.out.println("Archive created successfully!");
    }
}
```

## Reading an Archive

Reading entries from an APACK archive is straightforward:

```java
import de.splatgames.aether.pack.core.AetherPackReader;
import de.splatgames.aether.pack.core.entry.Entry;

import java.io.InputStream;
import java.nio.file.Path;

public class ReadArchiveExample {
    public static void main(String[] args) throws Exception {
        Path archivePath = Path.of("my-archive.apack");

        try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
            // List all entries
            System.out.println("Archive contains " + reader.getEntryCount() + " entries:");

            for (Entry entry : reader) {
                System.out.printf("  %s (%d bytes)%n",
                    entry.getName(),
                    entry.getOriginalSize());
            }

            // Read a specific entry by name
            Entry entry = reader.getEntry("hello.txt")
                .orElseThrow(() -> new RuntimeException("Entry not found"));

            try (InputStream input = reader.getInputStream(entry)) {
                String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("Content: " + content);
            }

            // Or read directly to byte array
            byte[] data = reader.readAllBytes("config.json");
        }
    }
}
```

## Using Compression

Enable compression to reduce archive size:

```java
import de.splatgames.aether.pack.core.AetherPackWriter;
import de.splatgames.aether.pack.core.ApackConfiguration;
import de.splatgames.aether.pack.compression.CompressionRegistry;

public class CompressedArchiveExample {
    public static void main(String[] args) throws Exception {
        // Configure with ZSTD compression at level 6
        ApackConfiguration config = ApackConfiguration.builder()
            .compression(CompressionRegistry.zstd(), 6)
            .build();

        try (AetherPackWriter writer = AetherPackWriter.create(
                Path.of("compressed.apack"), config)) {
            writer.addEntry("large-file.dat", Path.of("/path/to/large-file.dat"));
        }
    }
}
```

### Compression Levels

| Algorithm | Level Range | Recommended |
|-----------|-------------|-------------|
| ZSTD | 1-22 | 3-6 for general use, 19+ for archival |
| LZ4 | 0-17 | 0 (fast mode) for speed, 9+ for better ratio |

## Using Encryption

Encrypt your archives for security:

```java
import de.splatgames.aether.pack.core.AetherPackWriter;
import de.splatgames.aether.pack.core.AetherPackReader;
import de.splatgames.aether.pack.core.ApackConfiguration;
import de.splatgames.aether.pack.crypto.EncryptionRegistry;
import de.splatgames.aether.pack.crypto.kdf.Argon2idKeyDerivation;
import de.splatgames.aether.pack.crypto.KeyWrapper;

import javax.crypto.SecretKey;

public class EncryptedArchiveExample {
    public static void main(String[] args) throws Exception {
        // Password-based encryption
        char[] password = "my-secure-password".toCharArray();

        // Derive encryption key from password using Argon2id
        Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();
        byte[] salt = kdf.generateSalt();
        SecretKey key = kdf.deriveKey(password, salt, 32);

        // Create encryption block with wrapped key
        var encryptionBlock = KeyWrapper.createEncryptionBlock(
            password,
            EncryptionRegistry.aes256Gcm(),
            kdf
        );

        // Configure encryption
        ApackConfiguration config = ApackConfiguration.builder()
            .encryption(EncryptionRegistry.aes256Gcm(), key, encryptionBlock)
            .compression(CompressionRegistry.zstd(), 3)
            .build();

        // Create encrypted archive
        try (AetherPackWriter writer = AetherPackWriter.create(
                Path.of("encrypted.apack"), config)) {
            writer.addEntry("secret.txt", secretDataStream);
        }
    }
}
```

### Reading Encrypted Archives

```java
// The reader will automatically detect encryption from the archive
// You need to provide the decryption key
ApackConfiguration config = ApackConfiguration.builder()
    .encryption(EncryptionRegistry.aes256Gcm(), decryptionKey)
    .build();

try (AetherPackReader reader = AetherPackReader.open(
        Path.of("encrypted.apack"), config)) {
    byte[] data = reader.readAllBytes("secret.txt");
}
```

## Using the CLI

The APACK command-line tool provides quick access to archive operations.

### Create an Archive

```bash
# Simple archive
apack create archive.apack file1.txt file2.txt directory/

# With ZSTD compression
apack create -c zstd -l 6 archive.apack files/

# With encryption
apack create -c zstd -e aes-256-gcm archive.apack files/
```

### Extract an Archive

```bash
# Extract to current directory
apack extract archive.apack

# Extract to specific directory
apack extract -o output/ archive.apack

# Extract encrypted archive (will prompt for password)
apack extract -o output/ encrypted.apack
```

### List Contents

```bash
# Simple listing
apack list archive.apack

# Detailed listing
apack list -l archive.apack

# JSON output for scripting
apack list --json archive.apack
```

### Verify Integrity

```bash
# Full verification (reads all data)
apack verify archive.apack

# Quick verification (headers only)
apack verify --quick archive.apack
```

### Display Archive Info

```bash
apack info archive.apack
```

## Working with Entry Attributes

APACK supports custom key-value attributes on entries:

```java
// Creating entries with attributes
EntryMetadata metadata = EntryMetadata.builder()
    .name("document.pdf")
    .mimeType("application/pdf")
    .attribute("author", "Jane Smith")           // String
    .attribute("created", System.currentTimeMillis())  // Long
    .attribute("version", 3L)                    // Long
    .attribute("draft", false)                   // Boolean
    .build();

// Reading attributes
Entry entry = reader.getEntry("document.pdf").orElseThrow();

String author = entry.getStringAttribute("author").orElse("Unknown");
long created = entry.getLongAttribute("created").orElse(0L);
boolean isDraft = entry.getBooleanAttribute("draft").orElse(false);

// Iterate all attributes
for (Attribute attr : entry.getAttributes()) {
    System.out.printf("%s = %s%n", attr.key(), attr.value());
}
```

## Error Handling

APACK uses checked exceptions for error handling:

```java
try (AetherPackReader reader = AetherPackReader.open(path)) {
    byte[] data = reader.readAllBytes("config.json");

} catch (EntryNotFoundException e) {
    System.err.println("Entry not found: " + e.getMessage());

} catch (ChecksumException e) {
    System.err.println("Data corruption detected!");
    System.err.printf("Expected: 0x%08X, Actual: 0x%08X%n",
        e.getExpected(), e.getActual());

} catch (EncryptionException e) {
    System.err.println("Decryption failed - wrong password?");

} catch (FormatException e) {
    System.err.println("Invalid archive format: " + e.getMessage());

} catch (ApackException e) {
    System.err.println("APACK error: " + e.getMessage());
}
```

## Next Steps

Now that you have the basics, explore these topics:

- [**Core Concepts**](concepts.md) - Understand how APACK works internally
- [**API Reference**](api/README.md) - Detailed API documentation
- [**Performance Tuning**](performance.md) - Optimize for your use case
- [**Security Considerations**](encryption/security-considerations.md) - Best practices for encryption

---

*This guide covers APACK version 0.1.0*
