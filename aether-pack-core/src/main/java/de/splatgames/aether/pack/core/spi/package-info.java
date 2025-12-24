/*
 * Copyright (c) 2025 Splatgames.de Software and Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/**
 * Service Provider Interfaces (SPI) for pluggable algorithm implementations.
 *
 * <p>This package defines the extension points for the APACK library, allowing
 * custom implementations of compression, encryption, and checksum algorithms
 * to be plugged in without modifying the core library. This follows the
 * standard Java SPI pattern using {@link java.util.ServiceLoader}.</p>
 *
 * <h2>Plugin Architecture</h2>
 * <pre>
 *                           ┌─────────────────────────────────────────┐
 *                           │              APACK Core                 │
 *                           │                                         │
 *                           │  ┌─────────────────────────────────────┐│
 *                           │  │         SPI Interfaces              ││
 *                           │  │  ┌─────────────┐ ┌───────────────┐  ││
 *                           │  │  │ Compression │ │  Encryption   │  ││
 *                           │  │  │  Provider   │ │   Provider    │  ││
 *                           │  │  └──────┬──────┘ └───────┬───────┘  ││
 *                           │  │         │                │          ││
 *                           │  │  ┌──────┴────────────────┴───────┐  ││
 *                           │  │  │       ChecksumProvider        │  ││
 *                           │  │  └───────────────────────────────┘  ││
 *                           │  └─────────────────────────────────────┘│
 *                           │                     │                   │
 *                           │              ServiceLoader              │
 *                           └─────────────────────┬───────────────────┘
 *                                                 │
 *     ┌───────────────────────────────────────────┼───────────────────────────────────────────┐
 *     │                                           │                                           │
 *     ▼                                           ▼                                           ▼
 * ┌────────────────────────┐        ┌──────────────────────────┐        ┌────────────────────────┐
 * │ aether-pack-compression│        │   aether-pack-crypto     │        │    Custom Module       │
 * │                        │        │                          │        │                        │
 * │ ┌────────────────────┐ │        │ ┌──────────────────────┐ │        │ ┌────────────────────┐ │
 * │ │  ZstdCompression   │ │        │ │   AesGcmEncryption   │ │        │ │  MyCustomAlgorithm │ │
 * │ │   Provider         │ │        │ │     Provider         │ │        │ │     Provider       │ │
 * │ └────────────────────┘ │        │ └──────────────────────┘ │        │ └────────────────────┘ │
 * │ ┌────────────────────┐ │        │ ┌──────────────────────┐ │        │                        │
 * │ │  Lz4Compression    │ │        │ │ ChaCha20Poly1305     │ │        │ META-INF/services/     │
 * │ │   Provider         │ │        │ │  Encryption Provider │ │        │  ...Provider           │
 * │ └────────────────────┘ │        │ └──────────────────────┘ │        │                        │
 * └────────────────────────┘        └──────────────────────────┘        └────────────────────────┘
 * </pre>
 *
 * <h2>SPI Interfaces</h2>
 * <table>
 *   <caption>Available Service Provider Interfaces</caption>
 *   <tr><th>Interface</th><th>Purpose</th><th>Built-in Implementations</th></tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.spi.CompressionProvider}</td>
 *     <td>Compress and decompress data</td>
 *     <td>ZSTD, LZ4 (in aether-pack-compression)</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.spi.EncryptionProvider}</td>
 *     <td>AEAD encryption/decryption</td>
 *     <td>AES-256-GCM, ChaCha20-Poly1305 (in aether-pack-crypto)</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.spi.ChecksumProvider}</td>
 *     <td>Data integrity verification</td>
 *     <td>CRC-32, XXH3-64 (in aether-pack-core)</td>
 *   </tr>
 * </table>
 *
 * <h2>Provider Identification</h2>
 * <p>Each provider has two identifiers:</p>
 * <ul>
 *   <li><strong>String ID:</strong> Human-readable name for configuration
 *       (e.g., "zstd", "aes-256-gcm", "xxh3-64")</li>
 *   <li><strong>Numeric ID:</strong> Compact integer stored in binary format
 *       (e.g., 1 for ZSTD, 1 for AES-256-GCM, 1 for XXH3-64)</li>
 * </ul>
 *
 * <h2>Implementation Requirements</h2>
 * <p>All provider implementations must follow these requirements:</p>
 * <ul>
 *   <li><strong>Thread-Safety:</strong> Providers must be stateless and thread-safe</li>
 *   <li><strong>No-Arg Constructor:</strong> Required for ServiceLoader discovery</li>
 *   <li><strong>Unique IDs:</strong> Both string and numeric IDs must be unique</li>
 *   <li><strong>Custom IDs:</strong> Use numeric IDs &gt;= 128 to avoid conflicts</li>
 * </ul>
 *
 * <h2>Creating a Custom Provider</h2>
 *
 * <h3>1. Implement the Interface</h3>
 * <pre>{@code
 * public class SnappyCompressionProvider implements CompressionProvider {
 *
 *     @Override
 *     public String getId() {
 *         return "snappy";
 *     }
 *
 *     @Override
 *     public int getNumericId() {
 *         return 128;  // Custom IDs >= 128
 *     }
 *
 *     @Override
 *     public OutputStream compress(OutputStream output, int level) throws IOException {
 *         return new SnappyOutputStream(output);
 *     }
 *
 *     @Override
 *     public InputStream decompress(InputStream input) throws IOException {
 *         return new SnappyInputStream(input);
 *     }
 *
 *     @Override
 *     public byte[] compressBlock(byte[] data, int level) {
 *         return Snappy.compress(data);
 *     }
 *
 *     @Override
 *     public byte[] decompressBlock(byte[] data, int originalSize) throws IOException {
 *         return Snappy.uncompress(data);
 *     }
 * }
 * }</pre>
 *
 * <h3>2. Create Service File</h3>
 * <pre>
 * META-INF/services/de.splatgames.aether.pack.core.spi.CompressionProvider
 * </pre>
 * <p>Contents:</p>
 * <pre>
 * com.example.SnappyCompressionProvider
 * </pre>
 *
 * <h3>3. Register and Use</h3>
 * <pre>{@code
 * // Automatic discovery via ServiceLoader
 * // Provider is automatically registered when JAR is on classpath
 *
 * // Manual registration (if not using ServiceLoader)
 * CompressionRegistry.register(new SnappyCompressionProvider());
 *
 * // Use the provider
 * CompressionProvider snappy = CompressionRegistry.requireByName("snappy");
 * byte[] compressed = snappy.compressBlock(data, 3);
 * }</pre>
 *
 * <h2>Provider Registries</h2>
 * <p>Each SPI has a corresponding registry for lookup:</p>
 * <ul>
 *   <li>{@link de.splatgames.aether.pack.core.checksum.ChecksumRegistry} -
 *       Checksum algorithm lookup</li>
 *   <li>{@code CompressionRegistry} (in aether-pack-compression) -
 *       Compression algorithm lookup</li>
 *   <li>{@code EncryptionRegistry} (in aether-pack-crypto) -
 *       Encryption algorithm lookup</li>
 * </ul>
 *
 * <h2>Encryption Provider Details</h2>
 * <p>{@link de.splatgames.aether.pack.core.spi.EncryptionProvider} requires
 * Authenticated Encryption with Associated Data (AEAD):</p>
 * <ul>
 *   <li><strong>Confidentiality:</strong> Data is encrypted</li>
 *   <li><strong>Integrity:</strong> Tampering is detected</li>
 *   <li><strong>Authenticity:</strong> Origin is verified</li>
 * </ul>
 *
 * <p>Encrypted data format:</p>
 * <pre>
 * ┌─────────────────┬──────────────────────────┬─────────────────┐
 * │  Nonce (12 B)   │   Ciphertext (n bytes)   │   Tag (16 B)    │
 * └─────────────────┴──────────────────────────┴─────────────────┘
 * </pre>
 *
 * <h2>Compression Provider Details</h2>
 * <p>{@link de.splatgames.aether.pack.core.spi.CompressionProvider} supports:</p>
 * <ul>
 *   <li><strong>Streaming:</strong> Wrap streams for incremental processing</li>
 *   <li><strong>Block:</strong> Compress/decompress entire byte arrays</li>
 *   <li><strong>Levels:</strong> Configurable compression level (0-22 for ZSTD)</li>
 * </ul>
 *
 * <h2>Checksum Provider Details</h2>
 * <p>{@link de.splatgames.aether.pack.core.spi.ChecksumProvider} supports:</p>
 * <ul>
 *   <li><strong>One-shot:</strong> Compute checksum of entire byte array</li>
 *   <li><strong>Incremental:</strong> Update checksum with multiple data chunks</li>
 *   <li><strong>Reusable:</strong> Reset and reuse calculator instances</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *   <li><strong>Providers:</strong> Must be thread-safe and stateless</li>
 *   <li><strong>Registries:</strong> Thread-safe, use concurrent collections</li>
 *   <li><strong>Streams:</strong> Individual streams are NOT thread-safe</li>
 *   <li><strong>Checksum calculators:</strong> NOT thread-safe (use one per thread)</li>
 * </ul>
 *
 * @see de.splatgames.aether.pack.core.spi.CompressionProvider
 * @see de.splatgames.aether.pack.core.spi.EncryptionProvider
 * @see de.splatgames.aether.pack.core.spi.ChecksumProvider
 * @see de.splatgames.aether.pack.core.checksum.ChecksumRegistry
 * @see java.util.ServiceLoader
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.splatgames.aether.pack.core.spi;
