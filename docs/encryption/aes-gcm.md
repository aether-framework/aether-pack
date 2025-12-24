# AES-256-GCM Encryption

AES-256-GCM is the recommended encryption algorithm for APACK archives. It provides authenticated encryption using the Advanced Encryption Standard with Galois/Counter Mode.

## Algorithm Properties

| Property | Value |
|----------|-------|
| ID | `aes-256-gcm` |
| Numeric ID | 1 |
| Key Size | 256 bits (32 bytes) |
| Nonce Size | 96 bits (12 bytes) |
| Tag Size | 128 bits (16 bytes) |
| Block Size | 128 bits (16 bytes) |

---

## Why AES-256-GCM

AES-256-GCM is recommended because:

1. **NIST Standard** - Federal government approved, widely trusted
2. **Hardware acceleration** - Intel AES-NI, ARM Crypto Extensions
3. **AEAD** - Combined encryption and authentication
4. **Proven security** - Extensively analyzed, well understood
5. **Wide support** - Available in all major platforms and libraries

---

## Security Properties

### Confidentiality

Data is encrypted using AES-256 in counter mode, providing:
- 256-bit key security
- No practical brute-force attacks possible
- Each block encrypted independently (parallelizable)

### Integrity

The 128-bit Galois MAC detects:
- Any bit flip in ciphertext
- Truncation or extension
- Ciphertext reordering

### Authenticity

The authentication tag verifies:
- Message was encrypted with the correct key
- Message hasn't been modified
- AAD matches what was provided during encryption

---

## API Usage

### Provider Access

```java
// Via registry (recommended)
EncryptionProvider aes = EncryptionRegistry.aes256Gcm();

// Direct instantiation
Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();
```

### Key Generation

```java
Aes256GcmEncryptionProvider aes = new Aes256GcmEncryptionProvider();

// Generate random key
SecretKey key = aes.generateKey();

// Create from existing bytes
byte[] keyBytes = new byte[32];
SecureRandom.getInstanceStrong().nextBytes(keyBytes);
SecretKey key = Aes256GcmEncryptionProvider.createKey(keyBytes);
```

### Block Encryption

```java
EncryptionProvider aes = EncryptionRegistry.aes256Gcm();
SecretKey key = aes.generateKey();

// Encrypt
byte[] ciphertext = aes.encryptBlock(plaintext, key);
// Output: [12-byte nonce][ciphertext][16-byte tag]

// Decrypt
byte[] decrypted = aes.decryptBlock(ciphertext, key);
```

### With Additional Authenticated Data

```java
// AAD is authenticated but not encrypted
byte[] aad = "metadata".getBytes(StandardCharsets.UTF_8);

// Encrypt with AAD
byte[] ciphertext = aes.encryptBlock(plaintext, key, aad);

// Decrypt with same AAD
byte[] decrypted = aes.decryptBlock(ciphertext, key, aad);

// Decryption fails if AAD doesn't match
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

// With AAD
try (OutputStream out = aes.encrypt(fileOutputStream, key, aad)) {
    out.write(plaintext);
}
```

---

## Ciphertext Format

The encrypted output format:

```
┌─────────────────────────────────────────────────────────────┐
│  Nonce (12 bytes)  │  Ciphertext  │  Auth Tag (16 bytes)   │
└─────────────────────────────────────────────────────────────┘
```

| Field | Size | Description |
|-------|------|-------------|
| Nonce | 12 bytes | Random, unique per encryption |
| Ciphertext | = plaintext | Encrypted data |
| Auth Tag | 16 bytes | GCM authentication code |

**Total overhead:** 28 bytes per encryption operation.

---

## Nonce Handling

### Automatic Generation

Each encryption generates a cryptographically random nonce:

```java
// Nonce is generated automatically
byte[] ciphertext = aes.encryptBlock(plaintext, key);
```

### Uniqueness Requirements

- **Never reuse** a nonce with the same key
- Random 96-bit nonces are safe for ~2^48 encryptions per key
- The implementation uses `SecureRandom` for nonce generation

### What Happens on Nonce Reuse?

Reusing a nonce with the same key:
- Reveals XOR of plaintexts
- May allow authentication key recovery
- Completely breaks security

APACK generates random nonces to prevent this.

---

## Hardware Acceleration

AES-GCM benefits from hardware instructions:

### Intel AES-NI

Available on most modern Intel/AMD processors:
- ~2-5x faster than software AES
- Constant-time (no timing attacks)
- Enabled automatically by JVM

### ARM Crypto Extensions

Available on ARMv8 and later:
- Similar speedup to AES-NI
- Common on modern smartphones/tablets
- Enabled automatically

### Checking Acceleration

The JVM automatically uses hardware acceleration when available. No configuration needed.

---

## Performance

### With Hardware Acceleration (AES-NI)

| Operation | Speed |
|-----------|-------|
| Encryption | ~4-8 GB/s |
| Decryption | ~4-8 GB/s |

### Without Hardware Acceleration

| Operation | Speed |
|-----------|-------|
| Encryption | ~100-200 MB/s |
| Decryption | ~100-200 MB/s |

---

## Best Practices

### 1. Use Strong Keys

```java
// Generate random key
SecretKey key = aes.generateKey();

// Or derive from password with strong KDF
Argon2idKeyDerivation kdf = new Argon2idKeyDerivation();
SecretKey key = kdf.deriveKey(password, salt, 32);
```

### 2. Clear Keys When Done

```java
try {
    // Use key
    byte[] ciphertext = aes.encryptBlock(data, key);
} finally {
    // Clear key from memory
    Arrays.fill(key.getEncoded(), (byte) 0);
}
```

### 3. Handle Authentication Failures

```java
try {
    byte[] plaintext = aes.decryptBlock(ciphertext, key);
} catch (GeneralSecurityException e) {
    // Authentication failed - tampering or wrong key
    // Do NOT use any partial data
    throw new SecurityException("Decryption failed", e);
}
```

### 4. Use AAD for Metadata

```java
// Include context in authentication
byte[] aad = ByteBuffer.allocate(12)
    .putInt(chunkIndex)
    .putLong(timestamp)
    .array();

byte[] ciphertext = aes.encryptBlock(data, key, aad);
```

---

## Error Handling

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `AEADBadTagException` | Wrong key or tampering | Verify key, check for corruption |
| `InvalidKeyException` | Key wrong size | Ensure exactly 32 bytes |
| `Ciphertext too short` | Truncated data | Check data integrity |

### Example

```java
try {
    byte[] plaintext = aes.decryptBlock(ciphertext, key);
} catch (AEADBadTagException e) {
    // Authentication failed
    throw new IOException("Decryption failed: data corrupted or wrong key");
} catch (IllegalArgumentException e) {
    // Invalid input
    throw new IOException("Invalid ciphertext format");
}
```

---

## Comparison with ChaCha20-Poly1305

| Aspect | AES-256-GCM | ChaCha20-Poly1305 |
|--------|-------------|-------------------|
| Hardware Accel | Yes (AES-NI) | No |
| Software Speed | Slower | Faster |
| NIST Approved | Yes | No |
| Timing Attacks | Possible (software) | Resistant |
| Key Size | 256-bit | 256-bit |
| Tag Size | 128-bit | 128-bit |

**Choose AES-GCM** when hardware acceleration is available.
**Choose ChaCha20** for software-only or ARM environments.

---

## Thread Safety

`Aes256GcmEncryptionProvider` is stateless and thread-safe:

```java
// Shared instance
private static final EncryptionProvider AES = EncryptionRegistry.aes256Gcm();

// Safe concurrent use
parallelStream().forEach(chunk -> {
    byte[] encrypted = AES.encryptBlock(chunk, key);
});
```

---

*Next: [ChaCha20-Poly1305](chacha20.md) | Previous: [Encryption Overview](README.md)*
