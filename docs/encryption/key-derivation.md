# Key Derivation

Key Derivation Functions (KDFs) convert passwords into cryptographic keys. APACK supports two KDFs for password-based encryption.

## Available KDFs

| KDF | ID | Description |
|-----|-----|-------------|
| Argon2id | 1 | Recommended, memory-hard |
| PBKDF2-SHA256 | 2 | Fallback, widely compatible |

---

## Argon2id (Recommended)

Argon2id is the **recommended** KDF for password-based encryption. It won the Password Hashing Competition (PHC) in 2015.

### Properties

| Property | Value |
|----------|-------|
| ID | `argon2id` |
| Numeric ID | 1 |
| Default Memory | 64 MiB (65536 KiB) |
| Default Iterations | 3 |
| Default Parallelism | 4 |
| Default Salt | 16 bytes |

### Why Argon2id?

1. **Memory-hard** - Requires significant memory, making GPU/ASIC attacks expensive
2. **Hybrid design** - Combines Argon2i (side-channel resistant) and Argon2d (GPU resistant)
3. **Parallelizable** - Efficient on multi-core CPUs
4. **Modern** - Represents current best practice

### Usage

```java
// Default OWASP 2024 parameters
Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();

// Custom parameters for higher security
Argon2idKeyDerivation kdf = new Argon2idKeyDerivation(
    131072,  // 128 MiB memory
    4,       // 4 iterations
    8,       // 8 parallel threads
    32       // 32-byte salt
);

// Derive key
byte[] salt = kdf.generateSalt();
SecretKey key = kdf.deriveKey(password, salt, 32);
```

### Parameter Guidelines

| Use Case | Memory | Iterations | Notes |
|----------|--------|------------|-------|
| Server | 64 MiB | 3 | OWASP 2024 default |
| High-security | 128+ MiB | 4+ | More resistant to attacks |
| Constrained | 32 MiB | 2 | Mobile/embedded devices |

### Serialization

```java
// Get parameters for storage
byte[] params = kdf.getParameters();

// Recreate from stored parameters
Argon2idKeyDerivation restored = Argon2idKeyDerivation.fromParameters(params);
```

---

## PBKDF2-SHA256 (Fallback)

PBKDF2 is provided as a fallback for environments where Argon2 is not available.

### Properties

| Property | Value |
|----------|-------|
| ID | `pbkdf2-sha256` |
| Numeric ID | 2 |
| Default Iterations | 600,000 |
| Default Salt | 32 bytes |
| Minimum Iterations | 100,000 |

### When to Use PBKDF2

- Argon2 library not available
- Strict compatibility requirements
- Legacy system integration

### Limitations

- **Not memory-hard** - Vulnerable to GPU attacks
- **Linear scaling** - Attackers benefit equally from faster hardware
- **Less future-proof** - May require increasing iterations over time

### Usage

```java
// Default parameters
Pbkdf2KeyDerivation kdf = new Pbkdf2KeyDerivation();

// Custom parameters
Pbkdf2KeyDerivation kdf = new Pbkdf2KeyDerivation(
    1000000,  // 1 million iterations
    32        // 32-byte salt
);

// Derive key
byte[] salt = kdf.generateSalt();
SecretKey key = kdf.deriveKey(password, salt, 32);
```

---

## Key Wrapping

APACK uses a two-tier key hierarchy for password-based encryption:

```
Password
    ↓ (KDF)
Key Encryption Key (KEK)
    ↓ (AES Key Wrap)
Data Encryption Key (DEK)
```

### Why Two-Tier?

1. **Password change** - Only rewrap DEK, don't re-encrypt all data
2. **Multiple passwords** - Same data accessible with different passwords
3. **Key escrow** - Backup key can unwrap DEK directly

### Implementation

```java
// Generate content encryption key
EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
SecretKey contentKey = aes.generateKey();

// Wrap with password
Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();
byte[] salt = kdf.generateSalt();
byte[] wrappedKey = KeyWrapper.wrapWithPassword(contentKey, password, salt, kdf);

// Unwrap with password
SecretKey unwrapped = KeyWrapper.unwrapWithPassword(
    wrappedKey, password, salt, kdf, "AES"
);
```

---

## EncryptionBlock

The `EncryptionBlock` stores KDF parameters and the wrapped key in the archive:

```java
EncryptionBlock block = EncryptionBlock.builder()
    .kdfAlgorithmId(FormatConstants.KDF_ARGON2ID)
    .cipherAlgorithmId(FormatConstants.ENCRYPTION_AES_256_GCM)
    .kdfIterations(3)
    .kdfMemory(65536)
    .kdfParallelism(4)
    .salt(salt)
    .wrappedKey(wrappedKey)
    .wrappedKeyTag(new byte[0])
    .build();

// Use with configuration
ApackConfiguration config = ApackConfiguration.builder()
    .encryption(aes, contentKey, block)
    .build();
```

---

## Password Best Practices

### Minimum Requirements

| Aspect | Recommendation |
|--------|----------------|
| Length | 12+ characters |
| Entropy | 60+ bits |
| Character set | Mixed case, numbers, symbols |

### Password Handling

```java
// Read password securely
char[] password = System.console().readPassword("Password: ");

try {
    // Derive key
    SecretKey key = kdf.deriveKey(password, salt, 32);

    // Use key...
} finally {
    // Clear password from memory
    Arrays.fill(password, '\0');
}
```

### What NOT to Do

```java
// BAD: Password as String (immutable, lingers in memory)
String password = "secret";

// BAD: Short/weak password
char[] password = "abc".toCharArray();

// BAD: No memory clearing
char[] password = getPassword();
// ... use password ...
// password never cleared!
```

---

## Comparison: Argon2id vs PBKDF2

| Aspect | Argon2id | PBKDF2-SHA256 |
|--------|----------|---------------|
| Memory-hard | Yes | No |
| GPU resistant | Yes | Limited |
| ASIC resistant | Yes | No |
| Side-channel resistant | Partially | N/A |
| Iterations (typical) | 3 | 600,000 |
| Derivation time | ~100ms | ~100ms |
| Recommended | Yes | Fallback only |

### Attack Resistance

| Attack Type | Argon2id | PBKDF2 |
|-------------|----------|--------|
| CPU brute-force | Slow | Slow |
| GPU brute-force | Very slow | Fast |
| ASIC attack | Very slow | Fast |
| Memory trade-off | Resistant | N/A |

---

## Complete Example

```java
public SecretKey createEncryptedArchive(
        Path archivePath,
        char[] password,
        Path... files) throws Exception {

    // Setup encryption
    EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
    Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();

    // Generate keys
    byte[] salt = kdf.generateSalt();
    SecretKey contentKey = aes.generateKey();

    // Wrap content key with password
    byte[] wrappedKey = KeyWrapper.wrapWithPassword(
        contentKey, password, salt, kdf
    );

    // Build encryption block
    EncryptionBlock block = EncryptionBlock.builder()
        .kdfAlgorithmId(FormatConstants.KDF_ARGON2ID)
        .cipherAlgorithmId(aes.getNumericId())
        .kdfIterations(kdf.getIterations())
        .kdfMemory(kdf.getMemoryKiB())
        .kdfParallelism(kdf.getParallelism())
        .salt(salt)
        .wrappedKey(wrappedKey)
        .wrappedKeyTag(new byte[0])
        .build();

    // Configure and create archive
    ApackConfiguration config = ApackConfiguration.builder()
        .compression(CompressionRegistry.zstd(), 6)
        .encryption(aes, contentKey, block)
        .build();

    try (AetherPackWriter writer = AetherPackWriter.create(archivePath, config)) {
        for (Path file : files) {
            writer.addEntry(file.getFileName().toString(), file);
        }
    }

    return contentKey;
}
```

---

*Next: [Security Considerations](security-considerations.md) | Previous: [ChaCha20-Poly1305](chacha20.md)*
