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
 * Checksum algorithm implementations for data integrity verification.
 *
 * <p>This package provides checksum algorithm implementations used throughout
 * the APACK format for verifying data integrity. Checksums are computed for
 * individual chunks, entry headers, and the table of contents to detect
 * accidental corruption or tampering.</p>
 *
 * <h2>Purpose and Design</h2>
 * <p>Checksums in APACK serve multiple purposes:</p>
 * <ul>
 *   <li><strong>Chunk Integrity</strong> - Each data chunk includes a checksum
 *       for detecting corruption during storage or transmission</li>
 *   <li><strong>Header Validation</strong> - File and entry headers use checksums
 *       to verify structural integrity before processing</li>
 *   <li><strong>Fast Lookup</strong> - Name hashes enable O(1) entry lookup
 *       without string comparison</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │                         ChecksumRegistry                                │
 * │  ┌─────────────────────────────────────────────────────────────────┐   │
 * │  │  getById(int) ──▶ Provider lookup by numeric ID (for reading)   │   │
 * │  │  getByName(String) ──▶ Provider lookup by name (for config)     │   │
 * │  │  getDefault() ──▶ XXH3-64 (recommended default)                 │   │
 * │  │  register(provider) ──▶ Add custom implementations              │   │
 * │  └─────────────────────────────────────────────────────────────────┘   │
 * │                                  │                                      │
 * │                                  ▼                                      │
 * │         ┌────────────────────────┴────────────────────────┐            │
 * │         │              ChecksumProvider (SPI)             │            │
 * │         └────────────────────────┬────────────────────────┘            │
 * │                    ┌─────────────┴─────────────┐                       │
 * │                    ▼                           ▼                       │
 * │           ┌──────────────────┐       ┌──────────────────┐              │
 * │           │  Crc32Checksum   │       │  XxHash3Checksum │              │
 * │           │  ID: 0 (32-bit)  │       │  ID: 1 (64-bit)  │              │
 * │           │  Compatible with │       │  High-performance│              │
 * │           │  java.util.zip   │       │  SIMD-optimized  │              │
 * │           └──────────────────┘       └──────────────────┘              │
 * └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Built-in Algorithms</h2>
 * <table>
 *   <caption>Supported Checksum Algorithms</caption>
 *   <tr><th>Algorithm</th><th>ID</th><th>Size</th><th>Speed</th><th>Use Case</th></tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.checksum.Crc32Checksum CRC-32}</td>
 *     <td>0</td><td>32-bit</td><td>Fast</td>
 *     <td>Compatibility with legacy systems</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.core.checksum.XxHash3Checksum XXH3-64}</td>
 *     <td>1</td><td>64-bit</td><td>Very Fast</td>
 *     <td>Default, recommended for new archives</td>
 *   </tr>
 * </table>
 *
 * <h2>Performance Characteristics</h2>
 * <p>XXH3-64 is the recommended default due to its exceptional performance:</p>
 * <ul>
 *   <li><strong>Speed:</strong> 10+ GB/s on modern CPUs with SIMD support</li>
 *   <li><strong>Quality:</strong> Excellent bit distribution and collision resistance</li>
 *   <li><strong>Portability:</strong> Consistent results across platforms</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>One-Shot Computation</h3>
 * <pre>{@code
 * // Get default provider (XXH3-64)
 * ChecksumProvider provider = ChecksumRegistry.getDefault();
 *
 * // Compute checksum of entire byte array
 * byte[] data = Files.readAllBytes(path);
 * long checksum = provider.compute(data);
 * System.out.printf("Checksum: 0x%016X%n", checksum);
 *
 * // Compute checksum of portion of array
 * long partialChecksum = provider.compute(buffer, offset, length);
 * }</pre>
 *
 * <h3>Incremental Computation</h3>
 * <pre>{@code
 * // Create a reusable calculator
 * ChecksumProvider.Checksum calc = provider.createChecksum();
 *
 * // Process data in chunks
 * byte[] buffer = new byte[8192];
 * int bytesRead;
 * while ((bytesRead = input.read(buffer)) != -1) {
 *     calc.update(buffer, 0, bytesRead);
 * }
 *
 * // Get result
 * long checksum = calc.getValue();
 * int checksum32 = calc.getValueAsInt();  // For 32-bit storage
 *
 * // Reuse for another computation
 * calc.reset();
 * calc.update(otherData);
 * long otherChecksum = calc.getValue();
 * }</pre>
 *
 * <h3>Provider Lookup</h3>
 * <pre>{@code
 * // By name (case-insensitive)
 * ChecksumProvider crc = ChecksumRegistry.requireByName("crc32");
 * ChecksumProvider xxh3 = ChecksumRegistry.requireByName("xxh3-64");
 *
 * // By numeric ID (when reading from file format)
 * int algorithmId = fileHeader.checksumAlgorithm();
 * ChecksumProvider provider = ChecksumRegistry.requireById(algorithmId);
 *
 * // Optional lookup (no exception)
 * Optional&lt;ChecksumProvider&gt; maybe = ChecksumRegistry.getByName("custom");
 * }</pre>
 *
 * <h3>Custom Provider Registration</h3>
 * <pre>{@code
 * // Register a custom checksum implementation
 * public class Sha256Checksum implements ChecksumProvider {
 *     public String getId() { return "sha256"; }
 *     public int getNumericId() { return 128; }  // Custom IDs >= 128
 *     public int getChecksumSize() { return 32; }
 *     // ... implementation
 * }
 *
 * ChecksumRegistry.register(new Sha256Checksum());
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *   <li>{@link de.splatgames.aether.pack.core.checksum.ChecksumRegistry} - Thread-safe,
 *       uses concurrent collections</li>
 *   <li>{@link de.splatgames.aether.pack.core.spi.ChecksumProvider} - Thread-safe,
 *       implementations must be stateless</li>
 *   <li>{@link de.splatgames.aether.pack.core.spi.ChecksumProvider.Checksum} -
 *       <strong>NOT</strong> thread-safe, each thread needs its own instance</li>
 * </ul>
 *
 * <h2>Integration with APACK Format</h2>
 * <p>Checksums are stored in the binary format at multiple levels:</p>
 * <ul>
 *   <li><strong>File Header:</strong> CRC-32 of the first 16 header bytes</li>
 *   <li><strong>Chunk Header:</strong> Checksum of uncompressed chunk data</li>
 *   <li><strong>TOC Entry:</strong> Checksum for each entry's complete data</li>
 * </ul>
 *
 * @see de.splatgames.aether.pack.core.checksum.ChecksumRegistry
 * @see de.splatgames.aether.pack.core.checksum.Crc32Checksum
 * @see de.splatgames.aether.pack.core.checksum.XxHash3Checksum
 * @see de.splatgames.aether.pack.core.spi.ChecksumProvider
 * @see de.splatgames.aether.pack.core.format.FormatConstants#CHECKSUM_CRC32
 * @see de.splatgames.aether.pack.core.format.FormatConstants#CHECKSUM_XXH3_64
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.splatgames.aether.pack.core.checksum;
