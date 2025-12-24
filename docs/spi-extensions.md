# SPI Extensions

This guide explains how to create custom providers for compression, encryption, and checksums using the APACK Service Provider Interface (SPI).

## Overview

APACK uses the Java ServiceLoader mechanism to discover and register providers. You can extend APACK by implementing one of the SPI interfaces:

| Interface | Purpose | Module |
|-----------|---------|--------|
| `CompressionProvider` | Custom compression algorithms | core |
| `EncryptionProvider` | Custom encryption algorithms | core |
| `ChecksumProvider` | Custom checksum algorithms | core |

---

## Creating a Custom CompressionProvider

### Interface Overview

```java
public interface CompressionProvider {
    String getId();                 // Unique string identifier
    int getNumericId();             // Binary format ID
    int getDefaultLevel();          // Default compression level
    int getMinLevel();              // Minimum supported level
    int getMaxLevel();              // Maximum supported level
    boolean supportsLevel(int level);  // Level validation

    // Streaming operations
    OutputStream compress(OutputStream output, int level) throws IOException;
    InputStream decompress(InputStream input) throws IOException;

    // Block operations
    byte[] compressBlock(byte[] data, int level) throws IOException;
    byte[] decompressBlock(byte[] compressedData, int originalSize) throws IOException;

    // Size estimation
    int maxCompressedSize(int inputSize);
}
```

### Example: Deflate Compression Provider

```java
package com.example.apack.compression;

import de.splatgames.aether.pack.core.spi.CompressionProvider;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class DeflateCompressionProvider implements CompressionProvider {

    @Override
    public @NotNull String getId() {
        return "deflate";
    }

    @Override
    public int getNumericId() {
        return 128;  // Custom IDs should be >= 128
    }

    @Override
    public int getDefaultLevel() {
        return Deflater.DEFAULT_COMPRESSION;  // -1
    }

    @Override
    public int getMinLevel() {
        return 0;  // NO_COMPRESSION
    }

    @Override
    public int getMaxLevel() {
        return 9;  // BEST_COMPRESSION
    }

    @Override
    public @NotNull OutputStream compress(
            @NotNull OutputStream output,
            int level) throws IOException {
        Deflater deflater = new Deflater(level);
        return new DeflaterOutputStream(output, deflater);
    }

    @Override
    public @NotNull InputStream decompress(
            @NotNull InputStream input) throws IOException {
        return new InflaterInputStream(input, new Inflater());
    }

    @Override
    public byte @NotNull [] compressBlock(
            byte @NotNull [] data,
            int level) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStream out = compress(baos, level)) {
            out.write(data);
        }
        return baos.toByteArray();
    }

    @Override
    public byte @NotNull [] decompressBlock(
            byte @NotNull [] compressedData,
            int originalSize) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        try (InputStream in = decompress(bais)) {
            return in.readAllBytes();
        }
    }

    @Override
    public int maxCompressedSize(int inputSize) {
        // Deflate adds overhead for incompressible data
        return inputSize + (inputSize / 10) + 12;
    }
}
```

### Registration

Create a service file at:
```
src/main/resources/META-INF/services/de.splatgames.aether.pack.core.spi.CompressionProvider
```

Contents:
```
com.example.apack.compression.DeflateCompressionProvider
```

---

## Creating a Custom EncryptionProvider

### Interface Overview

```java
public interface EncryptionProvider {
    String getId();           // Unique string identifier
    int getNumericId();       // Binary format ID
    int getKeySize();         // Key size in bytes
    int getNonceSize();       // Nonce size in bytes
    int getTagSize();         // Auth tag size in bytes

    // Streaming operations
    OutputStream encrypt(OutputStream output, SecretKey key) throws IOException, GeneralSecurityException;
    OutputStream encrypt(OutputStream output, SecretKey key, byte[] aad) throws IOException, GeneralSecurityException;
    InputStream decrypt(InputStream input, SecretKey key) throws IOException, GeneralSecurityException;
    InputStream decrypt(InputStream input, SecretKey key, byte[] aad) throws IOException, GeneralSecurityException;

    // Block operations
    byte[] encryptBlock(byte[] plaintext, SecretKey key) throws GeneralSecurityException;
    byte[] encryptBlock(byte[] plaintext, SecretKey key, byte[] aad) throws GeneralSecurityException;
    byte[] decryptBlock(byte[] ciphertext, SecretKey key) throws GeneralSecurityException;
    byte[] decryptBlock(byte[] ciphertext, SecretKey key, byte[] aad) throws GeneralSecurityException;

    // Utilities
    int encryptedSize(int plaintextSize);
    SecretKey generateKey() throws GeneralSecurityException;
}
```

### AEAD Requirement

All encryption providers **must** implement Authenticated Encryption with Associated Data (AEAD). This provides:

- **Confidentiality**: Data is encrypted
- **Integrity**: Any tampering is detected
- **Authenticity**: Origin verification

### Data Format

Encrypted output must follow this structure:

```
[Nonce (getNonceSize() bytes)] [Ciphertext] [Tag (getTagSize() bytes)]
```

### Example: AES-128-GCM Provider

```java
package com.example.apack.crypto;

import de.splatgames.aether.pack.core.spi.EncryptionProvider;
import org.jetbrains.annotations.NotNull;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.*;

public class Aes128GcmEncryptionProvider implements EncryptionProvider {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 16;      // 128 bits
    private static final int NONCE_SIZE = 12;    // 96 bits
    private static final int TAG_SIZE = 16;      // 128 bits

    @Override
    public @NotNull String getId() {
        return "aes-128-gcm";
    }

    @Override
    public int getNumericId() {
        return 129;  // Custom IDs should be >= 128
    }

    @Override
    public int getKeySize() {
        return KEY_SIZE;
    }

    @Override
    public int getNonceSize() {
        return NONCE_SIZE;
    }

    @Override
    public int getTagSize() {
        return TAG_SIZE;
    }

    @Override
    public byte @NotNull [] encryptBlock(
            byte @NotNull [] plaintext,
            @NotNull SecretKey key) throws GeneralSecurityException {
        return encryptBlock(plaintext, key, new byte[0]);
    }

    @Override
    public byte @NotNull [] encryptBlock(
            byte @NotNull [] plaintext,
            @NotNull SecretKey key,
            byte @NotNull [] aad) throws GeneralSecurityException {

        // Generate random nonce
        byte[] nonce = new byte[NONCE_SIZE];
        SecureRandom.getInstanceStrong().nextBytes(nonce);

        // Initialize cipher
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE * 8, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        // Add AAD if provided
        if (aad.length > 0) {
            cipher.updateAAD(aad);
        }

        // Encrypt
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Combine: nonce + ciphertext (includes tag)
        byte[] result = new byte[NONCE_SIZE + ciphertext.length];
        System.arraycopy(nonce, 0, result, 0, NONCE_SIZE);
        System.arraycopy(ciphertext, 0, result, NONCE_SIZE, ciphertext.length);

        return result;
    }

    @Override
    public byte @NotNull [] decryptBlock(
            byte @NotNull [] ciphertext,
            @NotNull SecretKey key) throws GeneralSecurityException {
        return decryptBlock(ciphertext, key, new byte[0]);
    }

    @Override
    public byte @NotNull [] decryptBlock(
            byte @NotNull [] ciphertext,
            @NotNull SecretKey key,
            byte @NotNull [] aad) throws GeneralSecurityException {

        if (ciphertext.length < NONCE_SIZE + TAG_SIZE) {
            throw new IllegalArgumentException("Ciphertext too short");
        }

        // Extract nonce
        byte[] nonce = new byte[NONCE_SIZE];
        System.arraycopy(ciphertext, 0, nonce, 0, NONCE_SIZE);

        // Initialize cipher
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE * 8, nonce);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        // Add AAD if provided
        if (aad.length > 0) {
            cipher.updateAAD(aad);
        }

        // Decrypt (cipher.doFinal verifies tag)
        return cipher.doFinal(ciphertext, NONCE_SIZE, ciphertext.length - NONCE_SIZE);
    }

    @Override
    public @NotNull OutputStream encrypt(
            @NotNull OutputStream output,
            @NotNull SecretKey key) throws IOException, GeneralSecurityException {
        return encrypt(output, key, new byte[0]);
    }

    @Override
    public @NotNull OutputStream encrypt(
            @NotNull OutputStream output,
            @NotNull SecretKey key,
            byte @NotNull [] aad) throws IOException, GeneralSecurityException {
        // Implementation: buffer data, encrypt on close, write nonce+ciphertext+tag
        return new BufferedEncryptingOutputStream(output, key, aad);
    }

    @Override
    public @NotNull InputStream decrypt(
            @NotNull InputStream input,
            @NotNull SecretKey key) throws IOException, GeneralSecurityException {
        return decrypt(input, key, new byte[0]);
    }

    @Override
    public @NotNull InputStream decrypt(
            @NotNull InputStream input,
            @NotNull SecretKey key,
            byte @NotNull [] aad) throws IOException, GeneralSecurityException {
        // Implementation: read all, verify tag, return plaintext stream
        return new BufferedDecryptingInputStream(input, key, aad);
    }

    @Override
    public @NotNull SecretKey generateKey() throws GeneralSecurityException {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE * 8, SecureRandom.getInstanceStrong());
        return keyGen.generateKey();
    }

    // Helper method to create key from bytes
    public static SecretKey createKey(byte[] keyBytes) {
        if (keyBytes.length != KEY_SIZE) {
            throw new IllegalArgumentException(
                "Key must be " + KEY_SIZE + " bytes");
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    // Inner classes for streaming (simplified)
    private class BufferedEncryptingOutputStream extends FilterOutputStream {
        // ... implementation
    }

    private class BufferedDecryptingInputStream extends FilterInputStream {
        // ... implementation
    }
}
```

### Registration

Create a service file at:
```
src/main/resources/META-INF/services/de.splatgames.aether.pack.core.spi.EncryptionProvider
```

Contents:
```
com.example.apack.crypto.Aes128GcmEncryptionProvider
```

---

## Creating a Custom ChecksumProvider

### Interface Overview

```java
public interface ChecksumProvider {
    String getId();           // Unique string identifier
    int getNumericId();       // Binary format ID
    int getChecksumSize();    // Output size in bytes

    // Factory method
    Checksum createChecksum();

    // Convenience methods
    long compute(byte[] data);
    long compute(byte[] data, int offset, int length);

    // Inner interface for incremental computation
    interface Checksum {
        void update(int b);
        void update(byte[] data);
        void update(byte[] data, int offset, int length);
        long getValue();
        int getValueAsInt();
        void reset();
        byte[] getBytes();
    }
}
```

### Example: Adler-32 Provider

```java
package com.example.apack.checksum;

import de.splatgames.aether.pack.core.spi.ChecksumProvider;
import org.jetbrains.annotations.NotNull;

public class Adler32ChecksumProvider implements ChecksumProvider {

    @Override
    public @NotNull String getId() {
        return "adler32";
    }

    @Override
    public int getNumericId() {
        return 128;  // Custom IDs should be >= 128
    }

    @Override
    public int getChecksumSize() {
        return 4;  // 32 bits
    }

    @Override
    public @NotNull Checksum createChecksum() {
        return new Adler32Checksum();
    }

    private static class Adler32Checksum implements Checksum {
        private final java.util.zip.Adler32 adler = new java.util.zip.Adler32();

        @Override
        public void update(int b) {
            adler.update(b);
        }

        @Override
        public void update(byte @NotNull [] data, int offset, int length) {
            adler.update(data, offset, length);
        }

        @Override
        public long getValue() {
            return adler.getValue();
        }

        @Override
        public void reset() {
            adler.reset();
        }
    }
}
```

### Registration

Create a service file at:
```
src/main/resources/META-INF/services/de.splatgames.aether.pack.core.spi.ChecksumProvider
```

Contents:
```
com.example.apack.checksum.Adler32ChecksumProvider
```

---

## ID Assignment

### Reserved ID Ranges

| Range | Usage |
|-------|-------|
| 0-127 | Standard algorithms (reserved) |
| 128-255 | Custom implementations |

### Standard IDs

| Type | ID | Algorithm |
|------|----|-----------|
| Compression | 0 | None |
| Compression | 1 | ZSTD |
| Compression | 2 | LZ4 |
| Encryption | 0 | None |
| Encryption | 1 | AES-256-GCM |
| Encryption | 2 | ChaCha20-Poly1305 |
| Checksum | 0 | CRC-32 |
| Checksum | 1 | XXH3-64 |
| KDF | 1 | Argon2id |
| KDF | 2 | PBKDF2-SHA256 |

---

## Implementation Requirements

### Thread Safety

All providers **must be thread-safe** and **stateless**:

```java
// GOOD: Stateless provider
public class MyProvider implements CompressionProvider {
    @Override
    public byte[] compressBlock(byte[] data, int level) {
        // Create new compressor for each call
        Compressor c = new Compressor(level);
        return c.compress(data);
    }
}

// BAD: Stateful provider
public class BadProvider implements CompressionProvider {
    private Compressor compressor;  // Shared state!

    @Override
    public byte[] compressBlock(byte[] data, int level) {
        return compressor.compress(data);  // NOT thread-safe!
    }
}
```

### Error Handling

Providers should throw appropriate exceptions:

```java
@Override
public byte[] compressBlock(byte[] data, int level) throws IOException {
    if (!supportsLevel(level)) {
        throw new IllegalArgumentException(
            "Compression level " + level + " not supported. " +
            "Valid range: " + getMinLevel() + "-" + getMaxLevel());
    }

    try {
        return doCompress(data, level);
    } catch (NativeLibraryException e) {
        throw new IOException("Compression failed: " + e.getMessage(), e);
    }
}
```

### Resource Management

Clean up resources properly:

```java
@Override
public OutputStream compress(OutputStream output, int level) {
    return new FilterOutputStream(output) {
        private final Compressor compressor = createCompressor(level);

        @Override
        public void close() throws IOException {
            try {
                compressor.finish();
                super.close();
            } finally {
                compressor.release();  // Always release native resources
            }
        }
    };
}
```

---

## Testing Custom Providers

### Basic Tests

```java
@Test
void testRoundTrip() throws Exception {
    CompressionProvider provider = new MyCompressionProvider();
    byte[] original = "Test data for compression".getBytes();

    byte[] compressed = provider.compressBlock(original, provider.getDefaultLevel());
    byte[] decompressed = provider.decompressBlock(compressed, original.length);

    assertArrayEquals(original, decompressed);
}

@Test
void testAllLevels() throws Exception {
    CompressionProvider provider = new MyCompressionProvider();
    byte[] data = new byte[10000];
    new Random().nextBytes(data);

    for (int level = provider.getMinLevel(); level <= provider.getMaxLevel(); level++) {
        byte[] compressed = provider.compressBlock(data, level);
        byte[] decompressed = provider.decompressBlock(compressed, data.length);
        assertArrayEquals(data, decompressed, "Failed at level " + level);
    }
}

@Test
void testStreamRoundTrip() throws Exception {
    CompressionProvider provider = new MyCompressionProvider();
    byte[] original = new byte[100000];
    new Random().nextBytes(original);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (OutputStream out = provider.compress(baos, 6)) {
        out.write(original);
    }

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    try (InputStream in = provider.decompress(bais)) {
        byte[] decompressed = in.readAllBytes();
        assertArrayEquals(original, decompressed);
    }
}
```

### Integration Test

```java
@Test
void testWithApack() throws Exception {
    // Register custom provider
    CompressionRegistry.register(new MyCompressionProvider());

    Path archive = tempDir.resolve("test.apack");
    byte[] testData = "Hello, custom compression!".getBytes();

    // Create archive with custom compression
    ApackConfiguration config = ApackConfiguration.builder()
        .compression(CompressionRegistry.requireByName("my-algorithm"), 6)
        .build();

    try (AetherPackWriter writer = AetherPackWriter.create(archive, config)) {
        writer.addEntry("test.txt", new ByteArrayInputStream(testData));
    }

    // Read back
    try (AetherPackReader reader = AetherPackReader.open(archive)) {
        byte[] read = reader.readAllBytes("test.txt");
        assertArrayEquals(testData, read);
    }
}
```

---

## Maven Configuration

### Module Dependencies

```xml
<dependency>
    <groupId>de.splatgames.aether.pack</groupId>
    <artifactId>aether-pack-core</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

### Annotations (Optional)

```xml
<dependency>
    <groupId>org.jetbrains</groupId>
    <artifactId>annotations</artifactId>
    <version>24.0.0</version>
    <scope>provided</scope>
</dependency>
```

---

## Best Practices

1. **Use IDs >= 128** for custom implementations
2. **Be stateless** - don't store mutable state
3. **Be thread-safe** - multiple threads may call concurrently
4. **Handle errors gracefully** - wrap native exceptions
5. **Document limitations** - specify supported data sizes, etc.
6. **Test thoroughly** - roundtrip, edge cases, thread safety
7. **Clean up resources** - especially native memory

---

*Back to: [Documentation](README.md) | Related: [Architecture](architecture.md) | [API](api/README.md)*
