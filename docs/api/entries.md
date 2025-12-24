# Entry and EntryMetadata API

This document covers the `Entry` interface for reading archive entries and the `EntryMetadata` class for creating new entries.

## Entry Interface

`Entry` is the read-only interface representing an entry in an APACK archive.

### Interface Definition

```java
public interface Entry {
    long getId();
    String getName();
    String getMimeType();
    long getOriginalSize();
    long getStoredSize();
    int getChunkCount();
    boolean isCompressed();
    boolean isEncrypted();
    boolean hasEcc();
    int getCompressionId();
    int getEncryptionId();
    List<Attribute> getAttributes();
}
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `id` | `long` | Unique identifier within archive |
| `name` | `String` | Entry name/path (max 65,535 bytes UTF-8) |
| `mimeType` | `String` | Content type hint (optional) |
| `originalSize` | `long` | Uncompressed size in bytes |
| `storedSize` | `long` | Stored size after processing |
| `chunkCount` | `int` | Number of data chunks |
| `compressed` | `boolean` | Whether compression is applied |
| `encrypted` | `boolean` | Whether encryption is applied |
| `hasEcc` | `boolean` | Whether error correction is present |
| `compressionId` | `int` | Compression algorithm ID |
| `encryptionId` | `int` | Encryption algorithm ID |
| `attributes` | `List<Attribute>` | Custom key-value metadata |

### Basic Methods

#### getId()

```java
long getId()
```

Returns the unique entry ID. Entry IDs are assigned during archive creation.

#### getName()

```java
String getName()
```

Returns the entry name (path within archive).

**Conventions:**
- Forward slashes (`/`) as path separators
- Case-sensitive
- UTF-8 encoded

**Examples:** `config.json`, `assets/images/logo.png`

#### getMimeType()

```java
String getMimeType()
```

Returns the MIME type hint, or empty string if not set.

**Examples:** `application/json`, `image/png`, `text/plain`

#### getOriginalSize() / getStoredSize()

```java
long getOriginalSize()
long getStoredSize()
```

Returns sizes before/after processing (compression, encryption).

**Calculate compression ratio:**
```java
double ratio = (double) entry.getStoredSize() / entry.getOriginalSize() * 100;
System.out.printf("Compressed to %.1f%% of original%n", ratio);
```

### Attribute Methods

#### getAttributes()

```java
List<Attribute> getAttributes()
```

Returns all custom attributes as an unmodifiable list.

#### getAttribute(String key)

```java
Optional<Attribute> getAttribute(String key)
```

Gets an attribute by key.

```java
entry.getAttribute("author").ifPresent(attr -> {
    System.out.println("Type: " + attr.valueType());
    System.out.println("Value: " + attr.value());
});
```

#### getStringAttribute(String key)

```java
Optional<String> getStringAttribute(String key)
```

Gets a string-typed attribute value.

```java
String author = entry.getStringAttribute("author").orElse("Unknown");
```

#### getLongAttribute(String key)

```java
Optional<Long> getLongAttribute(String key)
```

Gets a long-typed attribute value.

```java
long created = entry.getLongAttribute("created").orElse(0L);
Instant timestamp = Instant.ofEpochMilli(created);
```

#### getBooleanAttribute(String key)

```java
Optional<Boolean> getBooleanAttribute(String key)
```

Gets a boolean-typed attribute value.

```java
boolean readonly = entry.getBooleanAttribute("readonly").orElse(false);
```

---

## EntryMetadata Class

`EntryMetadata` is a mutable class for creating entry metadata when writing archives.

### Class Definition

```java
public final class EntryMetadata implements Entry
```

### Factory Methods

#### of(String name)

Creates metadata with just a name.

```java
EntryMetadata meta = EntryMetadata.of("readme.txt");
```

#### of(String name, String mimeType)

Creates metadata with name and MIME type.

```java
EntryMetadata meta = EntryMetadata.of("image.png", "image/png");
```

#### builder()

Creates a full-featured builder.

```java
EntryMetadata meta = EntryMetadata.builder()
    .name("document.pdf")
    .mimeType("application/pdf")
    .attribute("author", "John Doe")
    .build();
```

---

## Builder

The `EntryMetadata.Builder` provides a fluent API for constructing entry metadata.

### Basic Properties

#### name(String)

Sets the entry name (required).

```java
builder.name("config/settings.json")
```

**Constraints:**
- Must not be empty
- Max 65,535 UTF-8 bytes
- Use forward slashes for paths

#### id(long)

Sets a specific entry ID (optional).

```java
builder.id(42)
```

If not set (or set to 0), the writer auto-assigns an ID.

#### mimeType(String)

Sets the MIME type hint.

```java
builder.mimeType("application/json")
```

### Attributes

#### attribute(String key, String value)

Adds a string attribute.

```java
builder.attribute("author", "Jane Doe")
       .attribute("version", "1.0.0")
```

#### attribute(String key, long value)

Adds a long attribute.

```java
builder.attribute("created", System.currentTimeMillis())
       .attribute("version", 3L)
```

#### attribute(String key, boolean value)

Adds a boolean attribute.

```java
builder.attribute("verified", true)
       .attribute("readonly", false)
```

#### attribute(Attribute)

Adds a pre-constructed attribute.

```java
Attribute hash = Attribute.ofBytes("checksum", hashBytes);
builder.attribute(hash)
```

#### attributes(List<Attribute>)

Sets all attributes, replacing existing ones.

```java
builder.attributes(existingEntry.getAttributes())
```

### Processing Flags

#### compressionId(int)

Sets the compression algorithm ID.

```java
builder.compressionId(FormatConstants.COMPRESSION_ZSTD)
```

| ID | Constant | Algorithm |
|----|----------|-----------|
| 0 | `COMPRESSION_NONE` | None |
| 1 | `COMPRESSION_ZSTD` | Zstandard |
| 2 | `COMPRESSION_LZ4` | LZ4 |

**Note:** Actual compression is determined by the writer's configuration.

#### encryptionId(int)

Sets the encryption algorithm ID.

```java
builder.encryptionId(FormatConstants.ENCRYPTION_AES_256_GCM)
```

| ID | Constant | Algorithm |
|----|----------|-----------|
| 0 | `ENCRYPTION_NONE` | None |
| 1 | `ENCRYPTION_AES_256_GCM` | AES-256-GCM |
| 2 | `ENCRYPTION_CHACHA20_POLY1305` | ChaCha20-Poly1305 |

#### hasEcc(boolean)

Enables Reed-Solomon error correction.

```java
builder.hasEcc(true)
```

### build()

Validates and builds the metadata.

```java
EntryMetadata meta = builder.build();
```

**Throws:**
- `IllegalStateException` if name is empty
- `IllegalArgumentException` if name exceeds maximum length

---

## Mutable Size Fields

After writing, `EntryMetadata` has its size fields updated:

```java
EntryMetadata meta = EntryMetadata.of("data.bin");

// Before writing: sizes are 0
System.out.println(meta.getOriginalSize());  // 0
System.out.println(meta.getStoredSize());    // 0

// Add to writer
try (InputStream input = Files.newInputStream(sourcePath)) {
    writer.addEntry(meta, input);
}

// After writing: sizes are populated
System.out.println(meta.getOriginalSize());  // e.g., 102400
System.out.println(meta.getStoredSize());    // e.g., 45000
System.out.println(meta.getChunkCount());    // e.g., 1
```

---

## PackEntry Class

`PackEntry` is the immutable implementation returned when reading archives.

```java
try (AetherPackReader reader = AetherPackReader.open(path)) {
    for (Entry entry : reader) {
        // entry is actually a PackEntry instance
        PackEntry packEntry = (PackEntry) entry;

        // PackEntry provides additional method
        long offset = packEntry.getOffset();  // File offset to entry header
    }
}
```

---

## Complete Examples

### Creating Entries with Metadata

```java
// Simple entry
EntryMetadata simple = EntryMetadata.of("data.txt");

// Entry with MIME type
EntryMetadata typed = EntryMetadata.of("config.json", "application/json");

// Full-featured entry
EntryMetadata full = EntryMetadata.builder()
    .name("assets/document.pdf")
    .mimeType("application/pdf")
    .attribute("author", "John Smith")
    .attribute("created", System.currentTimeMillis())
    .attribute("version", 2L)
    .attribute("draft", false)
    .build();

// Use with writer
try (AetherPackWriter writer = AetherPackWriter.create(archivePath)) {
    writer.addEntry(full, pdfInputStream);
}
```

### Reading Entry Attributes

```java
try (AetherPackReader reader = AetherPackReader.open(archivePath)) {
    for (Entry entry : reader) {
        System.out.println("Name: " + entry.getName());
        System.out.println("Size: " + entry.getOriginalSize() + " bytes");

        // Check MIME type
        if (!entry.getMimeType().isEmpty()) {
            System.out.println("Type: " + entry.getMimeType());
        }

        // Read attributes
        entry.getStringAttribute("author")
            .ifPresent(a -> System.out.println("Author: " + a));

        entry.getLongAttribute("created")
            .map(Instant::ofEpochMilli)
            .ifPresent(t -> System.out.println("Created: " + t));

        // Processing info
        if (entry.isCompressed()) {
            double ratio = (double) entry.getStoredSize() / entry.getOriginalSize();
            System.out.printf("Compressed to %.1f%%%n", ratio * 100);
        }
    }
}
```

### Copying Entry Metadata

```java
// Copy metadata from existing entry
Entry source = reader.getEntry("template.txt").orElseThrow();

EntryMetadata copy = EntryMetadata.builder()
    .name("copy.txt")
    .mimeType(source.getMimeType())
    .attributes(source.getAttributes())
    .build();
```

---

*Next: [ChunkProcessor](chunk-processor.md) | Previous: [Configuration](configuration.md)*
