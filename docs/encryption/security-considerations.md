# Security Considerations

This document covers security best practices for using APACK encryption.

## Threat Model

APACK encryption protects against:

| Threat | Protection |
|--------|------------|
| Unauthorized access | 256-bit encryption |
| Data tampering | AEAD authentication |
| Password brute-force | Memory-hard KDF |
| Side-channel attacks | ChaCha20 constant-time |

APACK does **not** protect against:

| Threat | Mitigation |
|--------|------------|
| Key compromise | Proper key management |
| Weak passwords | Password policies |
| Malware on system | System security |
| Physical access | Full-disk encryption |

---

## Key Management

### Key Generation

Always use cryptographically secure random generation:

```java
// GOOD: Cryptographic random
SecretKey key = aes.generateKey();

// GOOD: Secure random for raw bytes
byte[] keyBytes = new byte[32];
SecureRandom.getInstanceStrong().nextBytes(keyBytes);
```

**Never** use:
- Predictable values
- Timestamps
- User-supplied strings directly
- Weak random generators

### Key Storage

| Storage | Recommendation |
|---------|----------------|
| In memory | Clear when done |
| On disk | Never store plaintext |
| In config | Use password + KDF |
| Production | Use HSM or key vault |

### Key Lifetime

```java
// Clear keys when done
try {
    byte[] ciphertext = aes.encryptBlock(data, key);
} finally {
    // Clear key bytes
    byte[] encoded = key.getEncoded();
    if (encoded != null) {
        Arrays.fill(encoded, (byte) 0);
    }
}
```

---

## Password Security

### Password Requirements

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| Length | 12 chars | 16+ chars |
| Entropy | 60 bits | 80+ bits |
| Character classes | 3 | 4 (upper, lower, digit, symbol) |

### Password Handling

```java
// Secure password reading
Console console = System.console();
if (console != null) {
    char[] password = console.readPassword("Password: ");
    try {
        // Use password
    } finally {
        Arrays.fill(password, '\0');
    }
}
```

### Password Confirmation

```java
char[] password = readPassword("Enter password: ");
char[] confirm = readPassword("Confirm password: ");

try {
    if (!Arrays.equals(password, confirm)) {
        throw new SecurityException("Passwords do not match");
    }
    // Proceed with password
} finally {
    Arrays.fill(password, '\0');
    Arrays.fill(confirm, '\0');
}
```

### What to Avoid

```java
// BAD: Password as String (can't clear, may be interned)
String password = scanner.nextLine();

// BAD: Logging passwords
log.debug("Password: " + password);

// BAD: Password in command line
// Visible in process list, shell history
apack create -p "secret" archive.apack
```

---

## Nonce Management

### Uniqueness Requirements

Nonces must be unique per key:
- APACK generates random 96-bit nonces automatically
- Safe for approximately 2^48 encryptions per key

### Nonce Reuse Consequences

If a nonce is reused with the same key:
- XOR of plaintexts is revealed
- Authentication key may be recovered
- Complete security break

APACK prevents this by using `SecureRandom` for each encryption.

### Key Rotation

For long-lived keys, consider rotation:

```java
// Track encryption count
long encryptionCount = counter.incrementAndGet();

// Rotate key before 2^40 uses (safety margin)
if (encryptionCount > (1L << 40)) {
    key = aes.generateKey();
    counter.set(0);
}
```

---

## KDF Configuration

### Argon2id Parameters

OWASP 2024 recommendations:

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Memory | 64 MiB | GPU cost |
| Iterations | 3 | Time cost |
| Parallelism | 4 | CPU utilization |

### Tuning Guidelines

Target: **100-500ms** derivation time on target hardware.

```java
// Measure derivation time
long start = System.nanoTime();
kdf.deriveKey(testPassword, salt, 32);
long elapsed = System.nanoTime() - start;

// Adjust if too fast (< 100ms) or too slow (> 500ms)
```

### PBKDF2 Considerations

If using PBKDF2:
- Minimum 600,000 iterations (OWASP 2024)
- Consider higher for sensitive data
- Be aware of GPU vulnerability

---

## Authentication Failures

### Handling Decryption Errors

```java
try {
    byte[] plaintext = aes.decryptBlock(ciphertext, key);
} catch (AEADBadTagException e) {
    // Authentication failed
    // Could be: wrong key, data tampering, corruption

    // DO NOT:
    // - Reveal which error occurred
    // - Return partial data
    // - Retry with variations

    throw new SecurityException("Decryption failed");
}
```

### Timing Attacks

Don't reveal information through timing:

```java
// BAD: Early exit on error
if (!checkHeader(data)) {
    throw new Exception("Invalid header");
}
if (!checkMac(data)) {
    throw new Exception("Invalid MAC");
}

// BETTER: Constant-time comparison
boolean valid = constantTimeEquals(computedMac, expectedMac);
```

---

## Data at Rest

### Archive Protection

| Scenario | Protection |
|----------|------------|
| Archive on disk | APACK encryption |
| Extracted files | Not protected |
| Temporary files | May leak data |

### Secure Deletion

```java
// After extraction, consider secure deletion
try {
    // Use archive
} finally {
    // Secure delete extracted files if sensitive
    secureDelete(extractedPath);
}
```

### Disk Considerations

- SSD wear leveling may retain deleted data
- Consider full-disk encryption for sensitive systems
- Swap files may contain decrypted data

---

## Network Considerations

### Transmission

APACK encryption protects data confidentiality, but consider:
- Use TLS for transmission (defense in depth)
- Verify archive integrity after transfer
- Consider signature for authenticity

### Verification

```java
// After receiving archive
if (!apackVerify(archive)) {
    throw new SecurityException("Archive verification failed");
}

// Then decrypt
```

---

## Implementation Security

### Dependency Management

- Keep dependencies updated
- Monitor for CVEs in BouncyCastle, zstd-jni, lz4-java
- Use dependency scanning tools

### Memory Protection

```java
// Clear sensitive data
Arrays.fill(keyBytes, (byte) 0);
Arrays.fill(password, '\0');

// Note: Java GC may copy objects, clearing is best-effort
```

### Error Messages

```java
// BAD: Detailed error reveals information
throw new Exception("Decryption failed: key mismatch for entry 'secret.txt'");

// GOOD: Generic error
throw new SecurityException("Decryption failed");
```

---

## Audit Logging

### What to Log

| Event | Log Level |
|-------|-----------|
| Archive created (encrypted) | INFO |
| Archive opened | INFO |
| Decryption failure | WARN |
| Multiple failures | ERROR |

### What NOT to Log

- Passwords
- Decryption keys
- Plaintext data
- Salt values
- Specific entry names (in sensitive contexts)

```java
// GOOD logging
log.info("Created encrypted archive: {}", archivePath);
log.warn("Decryption failed for archive: {}", archivePath);

// BAD logging
log.debug("Password used: {}", password);
log.info("Decrypting entry: {}", entryName);
```

---

## Compliance Considerations

### Algorithm Approval

| Standard | AES-256-GCM | ChaCha20-Poly1305 | Argon2id |
|----------|-------------|-------------------|----------|
| NIST | Approved | Not approved | Not approved |
| FIPS 140-2/3 | Approved | Not approved | Not approved |
| Common Criteria | Varies | Varies | Varies |

### Recommendations

- **Government/regulated**: Use AES-256-GCM
- **Commercial**: Either algorithm acceptable
- **Open source**: ChaCha20 often preferred

---

## Security Checklist

Before deploying encrypted APACK archives:

- [ ] Using AES-256-GCM or ChaCha20-Poly1305
- [ ] Using Argon2id for password-based encryption
- [ ] KDF parameters tuned for target hardware
- [ ] Passwords meet minimum requirements
- [ ] Keys cleared from memory after use
- [ ] Authentication failures handled securely
- [ ] Logging doesn't expose sensitive data
- [ ] Dependencies are up to date
- [ ] Tested with wrong passwords (fails gracefully)
- [ ] Tested with corrupted data (fails gracefully)

---

*Previous: [Key Derivation](key-derivation.md) | Back to: [Encryption Overview](README.md)*
