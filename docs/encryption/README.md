# Encryption Overview

APACK supports authenticated encryption using industry-standard AEAD (Authenticated Encryption with Associated Data) algorithms. Encryption protects data confidentiality and integrity.

## Available Algorithms

| Algorithm | ID | Key Size | Description |
|-----------|-----|----------|-------------|
| AES-256-GCM | `aes-256-gcm` | 256-bit | NIST standard, hardware accelerated |
| ChaCha20-Poly1305 | `chacha20-poly1305` | 256-bit | Fast in software, no timing attacks |

Both algorithms provide:
- **Confidentiality** - Data cannot be read without the key
- **Integrity** - Tampering is detected
- **Authenticity** - Verifies the message origin

---

## Choosing an Algorithm

### Use AES-256-GCM When

- **Hardware AES available** - Intel AES-NI, ARM Crypto Extensions
- **NIST compliance required** - Government/enterprise environments
- **Maximum compatibility** - Widest platform support

### Use ChaCha20-Poly1305 When

- **Software-only environments** - No hardware AES acceleration
- **ARM/mobile devices** - Often faster than software AES
- **Timing attack concerns** - Constant-time by design
- **Modern systems** - Growing adoption (TLS 1.3, WireGuard)

---

## Encryption Parameters

Both algorithms use identical security parameters:

| Parameter | Value | Description |
|-----------|-------|-------------|
| Key Size | 256 bits (32 bytes) | Secret key length |
| Nonce Size | 96 bits (12 bytes) | Unique per encryption |
| Tag Size | 128 bits (16 bytes) | Authentication tag |

---

## Key Derivation

For password-based encryption, keys are derived using:

| KDF | ID | Description |
|-----|-----|-------------|
| Argon2id | 1 | Recommended, memory-hard |
| PBKDF2-SHA256 | 2 | Fallback, widely supported |

See [Key Derivation](key-derivation.md) for details.

---

## API Usage

### Via EncryptionRegistry

```java
// Get providers
EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
EncryptionProvider chacha = EncryptionRegistry.chaCha20Poly1305();

// By name
Optional<EncryptionProvider> provider = EncryptionRegistry.get("aes-256-gcm");

// By numeric ID
Optional<EncryptionProvider> provider = EncryptionRegistry.getById(1);
```

### Key Generation

```java
EncryptionProvider aes = EncryptionRegistry.aes256Gcm();

// Generate random key
SecretKey key = aes.generateKey();

// Create key from bytes
byte[] keyBytes = /* from KDF or secure storage */;
SecretKey key = Aes256GcmEncryptionProvider.createKey(keyBytes);
```

### Block Encryption

```java
EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
SecretKey key = aes.generateKey();

// Encrypt
byte[] ciphertext = aes.encryptBlock(plaintext, key);

// Decrypt
byte[] decrypted = aes.decryptBlock(ciphertext, key);
```

### Stream Encryption

```java
// Encrypt to stream
try (OutputStream out = aes.encrypt(fileOutputStream, key)) {
    out.write(plaintext);
}

// Decrypt from stream
try (InputStream in = aes.decrypt(fileInputStream, key)) {
    byte[] plaintext = in.readAllBytes();
}
```

### With ApackConfiguration

```java
// Generate or derive key
SecretKey key = aes.generateKey();

// Configure encryption
ApackConfiguration config = ApackConfiguration.builder()
    .encryption(EncryptionRegistry.aes256Gcm(), key)
    .build();

// With compression
ApackConfiguration config = ApackConfiguration.builder()
    .compression(CompressionRegistry.zstd(), 6)
    .encryption(EncryptionRegistry.aes256Gcm(), key)
    .build();
```

---

## Ciphertext Format

Both algorithms produce output in the same format:

```
[12-byte nonce][ciphertext][16-byte authentication tag]
```

- **Nonce**: Random, unique per encryption
- **Ciphertext**: Encrypted data (same length as plaintext)
- **Tag**: Authentication code for integrity verification

---

## Processing Order

APACK applies transformations in this order:

### Writing (Encryption)

```
Original Data → Compress → Encrypt → Store
```

Compression is applied first because encrypted data is incompressible.

### Reading (Decryption)

```
Stored Data → Decrypt → Decompress → Original Data
```

---

## Password-Based Encryption

For password-protected archives:

```java
// Create KDF and derive key
Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();
byte[] salt = kdf.generateSalt();

SecretKey contentKey = aes.generateKey();
byte[] wrappedKey = KeyWrapper.wrapWithPassword(contentKey, password, salt, kdf);

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

// Configure with encryption block
ApackConfiguration config = ApackConfiguration.builder()
    .encryption(aes, contentKey, block)
    .build();
```

---

## Security Considerations

### Nonce Uniqueness

- Each encryption uses a unique random nonce
- Never reuse nonces with the same key
- Safe for approximately 2^48 encryptions per key

### Key Management

- Store keys securely (HSM, key vault)
- Clear keys from memory when done
- Use strong key derivation for passwords

### Authentication

- Always verify the authentication tag
- Failed verification indicates tampering or wrong key
- Never use data from failed decryption

See [Security Considerations](security-considerations.md) for comprehensive guidance.

---

## Thread Safety

Both encryption providers are stateless and thread-safe:

```java
// Shared instance
private static final EncryptionProvider AES = EncryptionRegistry.aes256Gcm();

// Safe concurrent use
parallelStream().forEach(chunk -> {
    byte[] encrypted = AES.encryptBlock(chunk, sharedKey);
});
```

---

## Dependencies

| Algorithm | Library |
|-----------|---------|
| AES-256-GCM | JCA (built-in) |
| ChaCha20-Poly1305 | BouncyCastle |

```xml
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78</version>
</dependency>
```

---

*Next: [AES-256-GCM](aes-gcm.md) | Back to: [Documentation](../README.md)*
