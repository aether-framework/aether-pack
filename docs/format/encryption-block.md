# Encryption Block Specification

The encryption block is a variable-size structure that appears immediately after the file header when encryption is enabled. It contains all parameters needed to derive the encryption key from a password.

## Presence Condition

The encryption block is present only when the `FLAG_ENCRYPTED` (0x02) bit is set in the file header's `modeFlags` field.

```
┌──────────────────────────────┐
│     File Header (64 bytes)   │
├──────────────────────────────┤
│ Encryption Block (if FLAG_ENCRYPTED set)
├──────────────────────────────┤
│         Entry 1...           │
└──────────────────────────────┘
```

## Encryption Architecture

APACK uses a two-tier key hierarchy:

```
Password (user-provided)
        │
        ▼
┌───────────────────────────────────┐
│ Key Derivation Function (KDF)     │
│ - Argon2id or PBKDF2             │
│ - Uses salt, iterations, memory   │
└───────────────────┬───────────────┘
                    │
                    ▼
    Key Encryption Key (KEK) - 256 bits
                    │
                    ▼
┌───────────────────────────────────┐
│ Unwrap Wrapped Key                │
│ - Decrypt wrapped DEK with KEK   │
│ - Verify authentication tag       │
└───────────────────┬───────────────┘
                    │
                    ▼
    Data Encryption Key (DEK) - 256 bits
                    │
                    ▼
    Used to encrypt/decrypt chunks
```

**Benefits of this architecture:**
- Password can be changed without re-encrypting all data
- Random DEK provides stronger security than password-derived key
- Wrapped key verification detects wrong passwords early

## Binary Layout

| Offset | Size | Field | Type | Description |
|--------|------|-------|------|-------------|
| 0x00 | 4 | magic | bytes | Magic number "ENCR" (ASCII) |
| 0x04 | 1 | kdfAlgorithmId | uint8 | KDF algorithm (0=Argon2id, 1=PBKDF2) |
| 0x05 | 1 | cipherAlgorithmId | uint8 | Cipher (1=AES-256-GCM, 2=ChaCha20) |
| 0x06 | 2 | reserved | uint16 | Reserved for future use |
| 0x08 | 4 | kdfIterations | int32 | Iterations/time cost |
| 0x0C | 4 | kdfMemory | int32 | Memory cost in KB (Argon2 only) |
| 0x10 | 4 | kdfParallelism | int32 | Parallelism factor (Argon2 only) |
| 0x14 | 2 | saltLength | uint16 | Length of salt in bytes |
| 0x16 | 2 | wrappedKeyLength | uint16 | Length of wrapped key in bytes |
| 0x18 | N | salt | bytes | KDF salt (typically 32 bytes) |
| 0x18+N | 32 | wrappedKey | bytes | Encrypted DEK (32 bytes) |
| ... | 16 | wrappedKeyTag | bytes | AEAD authentication tag |

**Minimum Size: 24 + 32 (salt) + 32 (wrapped key) + 16 (tag) = 104 bytes**

## Visual Layout

```
Offset  0x00      0x04      0x08      0x0C
        ┌─────────┬─────────┬─────────┬─────────┐
0x00    │  magic  │kdf│ciph│    kdfIterations  │
        │ "ENCR"  │ id│ id │                   │
        │   (4)   │(1)│(1) │        (4)        │
        │         │reserved │                   │
        │         │   (2)   │                   │
        ├─────────┴─────────┼───────────────────┤
0x0C    │     kdfMemory     │   kdfParallelism  │
        │        (4)        │        (4)        │
        ├───────────────────┼───────────────────┤
0x14    │ saltLen │wrappedLen│                  │
        │   (2)   │   (2)   │                   │
        ├─────────┴─────────┴───────────────────┤
0x18    │               salt                    │
        │          (saltLength bytes)           │
        ├───────────────────────────────────────┤
        │            wrappedKey                 │
        │             (32 bytes)                │
        ├───────────────────────────────────────┤
        │          wrappedKeyTag                │
        │             (16 bytes)                │
        └───────────────────────────────────────┘
```

## Field Descriptions

### magic (4 bytes)

Must be exactly "ENCR" (bytes: `0x45 0x4E 0x43 0x52`).

### kdfAlgorithmId (1 byte)

Identifies the Key Derivation Function used to derive the KEK from the password.

| ID | Algorithm | Description |
|----|-----------|-------------|
| 0 | Argon2id | Memory-hard, recommended for new archives |
| 1 | PBKDF2-HMAC-SHA256 | Widely compatible fallback |

### cipherAlgorithmId (1 byte)

Identifies the AEAD cipher used for encryption.

| ID | Algorithm | Description |
|----|-----------|-------------|
| 1 | AES-256-GCM | Hardware-accelerated on modern CPUs |
| 2 | ChaCha20-Poly1305 | Excellent software performance |

### reserved (2 bytes)

Reserved for future use. Must be set to zero when writing, ignored when reading.

### kdfIterations (4 bytes, int32)

**For Argon2id:** Time cost parameter (t). Number of passes over memory.
- Default: 3
- Minimum recommended: 1

**For PBKDF2:** Number of HMAC iterations.
- Default: 100,000
- Minimum recommended: 100,000

### kdfMemory (4 bytes, int32)

**For Argon2id only:** Memory cost in kilobytes.
- Default: 65,536 KB (64 MB)
- Minimum recommended: 16,384 KB (16 MB)

**For PBKDF2:** Ignored (set to 0).

### kdfParallelism (4 bytes, int32)

**For Argon2id only:** Parallelism factor (p). Number of parallel lanes.
- Default: 4
- Typically: Number of CPU cores

**For PBKDF2:** Ignored (set to 0).

### saltLength (2 bytes, uint16)

Length of the salt in bytes. Typically 32 bytes.

### wrappedKeyLength (2 bytes, uint16)

Length of the wrapped (encrypted) DEK. Typically 32 bytes for a 256-bit key.

### salt (variable)

Random salt used in key derivation. Must be:
- Generated using a cryptographically secure random number generator
- At least 16 bytes (32 bytes recommended)
- Unique for each archive

### wrappedKey (32 bytes)

The Data Encryption Key (DEK) encrypted with the Key Encryption Key (KEK).

### wrappedKeyTag (16 bytes)

AEAD authentication tag for the wrapped key. Verifies integrity and authenticity.

## KDF Configuration

### Argon2id Parameters

| Parameter | Field | Description | Recommended |
|-----------|-------|-------------|-------------|
| Time (t) | kdfIterations | Number of passes | 3+ |
| Memory (m) | kdfMemory | Memory in KB | 65,536 (64 MB) |
| Parallelism (p) | kdfParallelism | Thread count | 4 |
| Salt | salt | Random bytes | 32 bytes |
| Output | - | KEK length | 32 bytes |

**Security Levels:**

| Level | Time | Memory | Use Case |
|-------|------|--------|----------|
| Interactive | 1 | 64 MB | Quick password checks |
| Standard | 3 | 64 MB | General use (default) |
| Sensitive | 4 | 128 MB | High-security archives |

### PBKDF2 Parameters

| Parameter | Field | Description | Recommended |
|-----------|-------|-------------|-------------|
| Iterations | kdfIterations | HMAC iterations | 100,000+ |
| Salt | salt | Random bytes | 32 bytes |
| Hash | - | SHA-256 (fixed) | - |
| Output | - | KEK length | 32 bytes |

**Note:** PBKDF2 ignores `kdfMemory` and `kdfParallelism`.

## Reading Example

```java
public EncryptionBlock readEncryptionBlock(BinaryReader reader)
        throws ApackException {
    // Validate magic
    byte[] magic = reader.readBytes(4);
    if (!Arrays.equals(magic, "ENCR".getBytes())) {
        throw new FormatException("Invalid encryption block magic");
    }

    int kdfAlgorithmId = reader.readUInt8();
    int cipherAlgorithmId = reader.readUInt8();
    reader.skipBytes(2); // reserved
    int kdfIterations = reader.readInt32();
    int kdfMemory = reader.readInt32();
    int kdfParallelism = reader.readInt32();
    int saltLength = reader.readUInt16();
    int wrappedKeyLength = reader.readUInt16();

    byte[] salt = reader.readBytes(saltLength);
    byte[] wrappedKey = reader.readBytes(wrappedKeyLength);
    byte[] wrappedKeyTag = reader.readBytes(16); // AUTH_TAG_SIZE

    return EncryptionBlock.builder()
        .kdfAlgorithmId(kdfAlgorithmId)
        .cipherAlgorithmId(cipherAlgorithmId)
        .kdfIterations(kdfIterations)
        .kdfMemory(kdfMemory)
        .kdfParallelism(kdfParallelism)
        .salt(salt)
        .wrappedKey(wrappedKey)
        .wrappedKeyTag(wrappedKeyTag)
        .build();
}
```

## Key Derivation Example

```java
public byte[] deriveKeyEncryptionKey(EncryptionBlock block, char[] password) {
    if (block.isArgon2id()) {
        return Argon2id.derive(
            password,
            block.salt(),
            block.kdfIterations(),    // time cost
            block.kdfMemory(),        // memory in KB
            block.kdfParallelism(),   // parallelism
            32                        // output length (256 bits)
        );
    } else if (block.isPbkdf2()) {
        return PBKDF2.derive(
            password,
            block.salt(),
            block.kdfIterations(),
            32                        // output length (256 bits)
        );
    } else {
        throw new UnsupportedAlgorithmException(
            "Unknown KDF: " + block.kdfAlgorithmId());
    }
}
```

## DEK Unwrapping Example

```java
public byte[] unwrapDataEncryptionKey(EncryptionBlock block, byte[] kek)
        throws DecryptionException {
    // Nonce for key wrapping (typically derived or fixed)
    byte[] nonce = deriveKeyWrapNonce(block.salt());

    // Decrypt wrapped key
    try {
        if (block.isAesGcm()) {
            return AesGcm.decrypt(
                kek, nonce,
                block.wrappedKey(),
                block.wrappedKeyTag(),
                null  // no additional data
            );
        } else if (block.isChaCha20Poly1305()) {
            return ChaCha20Poly1305.decrypt(
                kek, nonce,
                block.wrappedKey(),
                block.wrappedKeyTag(),
                null  // no additional data
            );
        } else {
            throw new UnsupportedAlgorithmException(
                "Unknown cipher: " + block.cipherAlgorithmId());
        }
    } catch (AEADBadTagException e) {
        throw new DecryptionException("Wrong password or corrupted key");
    }
}
```

## Validation Rules

1. **Magic** must be exactly "ENCR"
2. **KDF algorithm ID** must be known (0 or 1)
3. **Cipher algorithm ID** must be known (1 or 2)
4. **Salt length** must be at least 16 bytes
5. **Wrapped key length** must be 32 bytes (for 256-bit keys)
6. **KDF iterations** must be positive
7. **KDF memory** must be positive for Argon2id
8. **KDF parallelism** must be positive for Argon2id

## Security Considerations

### Salt Requirements
- Must be cryptographically random
- Must be unique per archive
- Stored in plaintext (not secret)

### Password Handling
- Never store passwords in memory longer than necessary
- Use `char[]` instead of `String` (can be zeroed)
- Zero password memory after use

### Wrong Password Detection
- Auth tag verification fails with wrong password
- Throw specific exception for user feedback
- Do not reveal which step failed (KEK derivation vs DEK unwrapping)

---

*Next: [Attributes](attributes.md) | Previous: [Trailer](trailer.md)*
