# Attributes Specification

Attributes are custom key-value metadata attached to entries. They enable storing application-specific information such as timestamps, permissions, tags, or any arbitrary data.

## Attribute Structure

Each attribute consists of:
1. Key (UTF-8 string)
2. Value type
3. Value (type-dependent encoding)

### Binary Layout (Single Attribute)

| Offset | Size | Field | Type | Description |
|--------|------|-------|------|-------------|
| 0x00 | 2 | keyLength | uint16 | Length of key in bytes |
| 0x02 | 1 | valueType | uint8 | Value type ID |
| 0x03 | 4 | valueLength | int32 | Length of value in bytes |
| 0x07 | N | key | bytes | Key (UTF-8, no null terminator) |
| 0x07+N | M | value | bytes | Value (encoding depends on type) |

**Minimum Size: 7 + keyLength + valueLength bytes**

### Visual Layout

```
Offset  0x00      0x02  0x03      0x07
        ┌─────────┬─────┬─────────┬─────────────────┐
        │ keyLen  │type │valueLen │      key        │
        │  (2)    │ (1) │   (4)   │  (keyLen bytes) │
        ├─────────┴─────┴─────────┼─────────────────┤
        │            value (valueLen bytes)         │
        └───────────────────────────────────────────┘
```

## Value Types

| ID | Type | Encoding | Size |
|----|------|----------|------|
| 0 | String | UTF-8, no null terminator | Variable |
| 1 | Int64 | Little-endian 64-bit signed integer | 8 bytes |
| 2 | Float64 | Little-endian IEEE 754 double | 8 bytes |
| 3 | Boolean | 0x00 (false) or 0x01 (true) | 1 byte |
| 4 | Bytes | Raw byte array | Variable |

### Type Details

#### String (Type 0)

UTF-8 encoded text without null terminator.

```
Key: "author"
Type: 0 (String)
Value: "John Doe" (8 bytes UTF-8)
```

#### Int64 (Type 1)

64-bit signed integer in Little-Endian byte order.

```
Key: "timestamp"
Type: 1 (Int64)
Value: 1703980800000 (8 bytes LE)
```

#### Float64 (Type 2)

IEEE 754 double-precision floating point in Little-Endian byte order.

```
Key: "score"
Type: 2 (Float64)
Value: 0.95 (8 bytes LE)
```

#### Boolean (Type 3)

Single byte: `0x00` for false, `0x01` for true.

```
Key: "readonly"
Type: 3 (Boolean)
Value: 0x01 (1 byte)
```

#### Bytes (Type 4)

Raw byte array. Can store any binary data.

```
Key: "thumbnail"
Type: 4 (Bytes)
Value: <PNG data> (variable bytes)
```

## Attributes in Entry Header

Attributes are stored at the end of the entry header, after the name and MIME type.

```
Entry Header Structure:
┌───────────────────────────────────────┐
│        Fixed Header (48 bytes)        │
├───────────────────────────────────────┤
│          Name (nameLength bytes)      │
├───────────────────────────────────────┤
│       MIME Type (mimeTypeLength)      │
├───────────────────────────────────────┤
│    Attribute 1 (if attrCount > 0)     │
├───────────────────────────────────────┤
│    Attribute 2                        │
├───────────────────────────────────────┤
│           ...                         │
├───────────────────────────────────────┤
│    Attribute N                        │
├───────────────────────────────────────┤
│        Padding (0-7 bytes)            │
└───────────────────────────────────────┘
        Aligned to 8-byte boundary
```

The `attrCount` field in the entry header indicates how many attributes follow.

## Reading Attributes

```java
public List<Attribute> readAttributes(BinaryReader reader, int attrCount)
        throws IOException {
    List<Attribute> attributes = new ArrayList<>();

    for (int i = 0; i < attrCount; i++) {
        int keyLength = reader.readUInt16();
        int valueType = reader.readUInt8();
        int valueLength = reader.readInt32();

        String key = reader.readString(keyLength);
        Object value = readValue(reader, valueType, valueLength);

        attributes.add(new Attribute(key, valueType, value));
    }

    return attributes;
}

private Object readValue(BinaryReader reader, int type, int length)
        throws IOException {
    return switch (type) {
        case 0 -> reader.readString(length);              // String
        case 1 -> reader.readInt64();                     // Int64
        case 2 -> reader.readFloat64();                   // Float64
        case 3 -> reader.readUInt8() != 0;               // Boolean
        case 4 -> reader.readBytes(length);               // Bytes
        default -> throw new FormatException("Unknown attribute type: " + type);
    };
}
```

## Writing Attributes

```java
public void writeAttributes(BinaryWriter writer, List<Attribute> attributes)
        throws IOException {
    for (Attribute attr : attributes) {
        byte[] keyBytes = attr.key().getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = encodeValue(attr.type(), attr.value());

        writer.writeUInt16(keyBytes.length);
        writer.writeUInt8(attr.type());
        writer.writeInt32(valueBytes.length);
        writer.writeBytes(keyBytes);
        writer.writeBytes(valueBytes);
    }
}

private byte[] encodeValue(int type, Object value) {
    return switch (type) {
        case 0 -> ((String) value).getBytes(StandardCharsets.UTF_8);
        case 1 -> encodeLittleEndianInt64((Long) value);
        case 2 -> encodeLittleEndianFloat64((Double) value);
        case 3 -> new byte[] { (byte) ((Boolean) value ? 1 : 0) };
        case 4 -> (byte[]) value;
        default -> throw new IllegalArgumentException("Unknown type: " + type);
    };
}
```

## Calculating Attributes Size

```java
public int calculateAttributesSize(List<Attribute> attributes) {
    int size = 0;
    for (Attribute attr : attributes) {
        int keyLength = attr.key().getBytes(StandardCharsets.UTF_8).length;
        int valueLength = getValueSize(attr.type(), attr.value());

        size += 2;  // keyLength (uint16)
        size += 1;  // valueType (uint8)
        size += 4;  // valueLength (int32)
        size += keyLength;
        size += valueLength;
    }
    return size;
}

private int getValueSize(int type, Object value) {
    return switch (type) {
        case 0 -> ((String) value).getBytes(StandardCharsets.UTF_8).length;
        case 1 -> 8;  // Int64
        case 2 -> 8;  // Float64
        case 3 -> 1;  // Boolean
        case 4 -> ((byte[]) value).length;
        default -> throw new IllegalArgumentException("Unknown type: " + type);
    };
}
```

## Usage Examples

### Common Attribute Patterns

```java
// File metadata
EntryMetadata.builder()
    .name("document.pdf")
    .attribute("created", 1703980800000L)      // Int64 timestamp
    .attribute("modified", 1704067200000L)     // Int64 timestamp
    .attribute("readonly", true)               // Boolean
    .attribute("author", "John Doe")           // String
    .build();

// Game save data
EntryMetadata.builder()
    .name("save001.dat")
    .attribute("level", 42L)                   // Int64
    .attribute("score", 98765.5)               // Float64
    .attribute("username", "Player1")          // String
    .attribute("completed", false)             // Boolean
    .build();

// Image with thumbnail
EntryMetadata.builder()
    .name("photo.jpg")
    .mimeType("image/jpeg")
    .attribute("width", 1920L)                 // Int64
    .attribute("height", 1080L)                // Int64
    .attribute("thumbnail", thumbnailBytes)    // Bytes
    .build();
```

### Accessing Attributes

```java
PackEntry entry = reader.getEntry("document.pdf");

// Get typed attributes
Optional<Long> created = entry.getAttribute("created", Long.class);
Optional<String> author = entry.getAttribute("author", String.class);
Optional<Boolean> readonly = entry.getAttribute("readonly", Boolean.class);

// Iterate all attributes
for (Attribute attr : entry.getAttributes()) {
    System.out.println(attr.key() + " = " + attr.value());
}
```

## Constraints

| Constraint | Limit |
|------------|-------|
| Maximum key length | 65,535 bytes |
| Maximum value length | 2,147,483,647 bytes (int32 max) |
| Maximum attributes per entry | 65,535 (limited by attrCount field) |
| Key encoding | UTF-8, no null terminator |
| Key uniqueness | Keys should be unique within an entry |

## Validation Rules

1. **Key length** must be greater than 0
2. **Value type** must be a known type ID (0-4)
3. **Value length** must match actual value size
4. **Fixed-size types** (Int64, Float64, Boolean) must have correct value length
5. **String values** must be valid UTF-8
6. **Boolean values** must be 0x00 or 0x01

## Reserved Attribute Names

The following attribute names are reserved for future use by the format:

| Name | Purpose |
|------|---------|
| `apack.version` | Entry format version |
| `apack.ctime` | Creation timestamp |
| `apack.mtime` | Modification timestamp |
| `apack.permissions` | POSIX permissions |
| `apack.owner` | Owner name |
| `apack.group` | Group name |

Applications should avoid using the `apack.` prefix for custom attributes.

---

*Next: [Constants Reference](constants.md) | Previous: [Encryption Block](encryption-block.md)*
