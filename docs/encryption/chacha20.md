# ChaCha20-Poly1305 Encryption

ChaCha20-Poly1305 is an alternative to AES-GCM that excels in software implementations. It provides authenticated encryption using the ChaCha20 stream cipher and Poly1305 MAC.

## Algorithm Properties

| Property | Value |
|----------|-------|
| ID | `chacha20-poly1305` |
| Numeric ID | 2 |
| Key Size | 256 bits (32 bytes) |
| Nonce Size | 96 bits (12 bytes) |
| Tag Size | 128 bits (16 bytes) |

---

## Why ChaCha20-Poly1305

ChaCha20-Poly1305 is advantageous when:

1. **Software-only environments** - Faster than AES without AES-NI
2. **ARM/mobile devices** - Often faster than software AES
3. **Constant-time** - No timing side-channels
4. **Modern adoption** - TLS 1.3, WireGuard, SSH
5. **Simple implementation** - Fewer failure modes than AES-GCM

---

## Security Properties

### Confidentiality

ChaCha20 provides:
- 256-bit key security
- Quarter-round ARX operations (Add, Rotate, XOR)
- 512-bit state blocks
- High diffusion per round

### Integrity

Poly1305 provides:
- 128-bit authentication tag
- Universal hash with one-time key
- Information-theoretically secure

### Design Philosophy

- Created by Daniel J. Bernstein
- Conservative, well-analyzed design
- No S-boxes (unlike AES)
- All operations are constant-time

---

## API Usage

### Provider Access

```java
// Via registry (recommended)
EncryptionProvider chacha = EncryptionRegistry.chaCha20Poly1305();

// Direct instantiation
ChaCha20Poly1305EncryptionProvider chacha = new ChaCha20Poly1305EncryptionProvider();
```

### Key Generation

```java
ChaCha20Poly1305EncryptionProvider chacha = new ChaCha20Poly1305EncryptionProvider();

// Generate random key
SecretKey key = chacha.generateKey();

// Create from existing bytes
byte[] keyBytes = new byte[32];
SecureRandom.getInstanceStrong().nextBytes(keyBytes);
SecretKey key = ChaCha20Poly1305EncryptionProvider.createKey(keyBytes);
```

### Block Encryption

```java
EncryptionProvider chacha = EncryptionRegistry.chaCha20Poly1305();
SecretKey key = chacha.generateKey();

// Encrypt
byte[] ciphertext = chacha.encryptBlock(plaintext, key);
// Output: [12-byte nonce][ciphertext][16-byte tag]

// Decrypt
byte[] decrypted = chacha.decryptBlock(ciphertext, key);
```

### With Additional Authenticated Data

```java
// AAD is authenticated but not encrypted
byte[] aad = "context".getBytes(StandardCharsets.UTF_8);

// Encrypt with AAD
byte[] ciphertext = chacha.encryptBlock(plaintext, key, aad);

// Decrypt with same AAD (must match exactly)
byte[] decrypted = chacha.decryptBlock(ciphertext, key, aad);
```

### Stream Encryption

```java
// Encrypt to stream
try (OutputStream out = chacha.encrypt(fileOutputStream, key)) {
    out.write(plaintext);
}

// Decrypt from stream
try (InputStream in = chacha.decrypt(fileInputStream, key)) {
    byte[] plaintext = in.readAllBytes();
}
```

---

## Ciphertext Format

Identical to AES-GCM for interoperability:

```
┌─────────────────────────────────────────────────────────────┐
│  Nonce (12 bytes)  │  Ciphertext  │  Auth Tag (16 bytes)   │
└─────────────────────────────────────────────────────────────┘
```

| Field | Size | Description |
|-------|------|-------------|
| Nonce | 12 bytes | Random, unique per encryption |
| Ciphertext | = plaintext | Encrypted data |
| Auth Tag | 16 bytes | Poly1305 authentication code |

**Total overhead:** 28 bytes per encryption operation.

---

## Performance

### Software Performance

Without hardware acceleration, ChaCha20 is typically faster:

| Algorithm | Speed |
|-----------|-------|
| ChaCha20-Poly1305 | ~300-500 MB/s |
| AES-GCM (software) | ~100-200 MB/s |

### With AES-NI

When hardware AES is available, AES-GCM is faster:

| Algorithm | Speed |
|-----------|-------|
| AES-GCM (AES-NI) | ~4-8 GB/s |
| ChaCha20-Poly1305 | ~300-500 MB/s |

### Recommendation

```java
// Check for hardware AES (simplified)
boolean useAes = Runtime.getRuntime().availableProcessors() >= 1; // Heuristic

EncryptionProvider provider = useAes
    ? EncryptionRegistry.aes256Gcm()
    : EncryptionRegistry.chaCha20Poly1305();
```

---

## Constant-Time Properties

ChaCha20-Poly1305 is constant-time by design:

- **No table lookups** - ARX operations only
- **No branches on secret data** - Deterministic execution path
- **Cache-timing resistant** - No data-dependent memory access

This makes it resistant to timing attacks that can affect software AES implementations.

---

## Stream Implementation Note

The ChaCha20-Poly1305 input stream must buffer the entire ciphertext before verification:

```java
// InputStream implementation buffers all data
try (InputStream in = chacha.decrypt(source, key)) {
    // All data is read and verified before first byte returns
    byte[] plaintext = in.readAllBytes();
}
```

This is because AEAD authentication cannot be verified until all data is processed. For large data, APACK's chunked encryption avoids this issue by encrypting each chunk independently.

---

## Use Cases

### Ideal For

- **Mobile applications** - Fast on ARM without crypto extensions
- **Cross-platform software** - Consistent performance everywhere
- **Security-critical systems** - No timing attack concerns
- **Embedded systems** - Low memory, no special instructions

### Consider AES-GCM Instead For

- **Servers with AES-NI** - Faster with hardware
- **NIST compliance** - Government requirements
- **Existing AES infrastructure** - Compatibility

---

## Best Practices

### 1. Nonce Uniqueness

```java
// Nonces are generated automatically and uniquely
byte[] ciphertext = chacha.encryptBlock(plaintext, key);
// Each call uses a fresh random nonce
```

### 2. Key Management

```java
try {
    byte[] ciphertext = chacha.encryptBlock(data, key);
} finally {
    // Clear sensitive key material
    Arrays.fill(key.getEncoded(), (byte) 0);
}
```

### 3. Authentication Failure Handling

```java
try {
    byte[] plaintext = chacha.decryptBlock(ciphertext, key);
} catch (GeneralSecurityException e) {
    // "Decryption failed: authentication tag mismatch"
    // Data was tampered with or wrong key
    log.error("Decryption failed: possible tampering");
    throw e;
}
```

---

## Comparison with AES-256-GCM

| Aspect | ChaCha20-Poly1305 | AES-256-GCM |
|--------|-------------------|-------------|
| Software Speed | Faster | Slower |
| Hardware Speed | N/A | Much Faster |
| Timing Attacks | Resistant | Possible |
| NIST Approved | No | Yes |
| Key Size | 256-bit | 256-bit |
| Nonce Size | 96-bit | 96-bit |
| Tag Size | 128-bit | 128-bit |

### Decision Tree

```
Is hardware AES available (AES-NI)?
├── Yes → Use AES-256-GCM
└── No
    ├── Timing attacks a concern? → Use ChaCha20-Poly1305
    ├── ARM/mobile device? → Use ChaCha20-Poly1305
    └── Otherwise → Either works, ChaCha20 slightly preferred
```

---

## Dependencies

ChaCha20-Poly1305 uses BouncyCastle:

```xml
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78</version>
</dependency>
```

---

## Thread Safety

`ChaCha20Poly1305EncryptionProvider` is stateless and thread-safe:

```java
// Shared instance
private static final EncryptionProvider CHACHA = EncryptionRegistry.chaCha20Poly1305();

// Safe concurrent use
parallelStream().forEach(chunk -> {
    byte[] encrypted = CHACHA.encryptBlock(chunk, key);
});
```

---

*Next: [Key Derivation](key-derivation.md) | Previous: [AES-256-GCM](aes-gcm.md)*
