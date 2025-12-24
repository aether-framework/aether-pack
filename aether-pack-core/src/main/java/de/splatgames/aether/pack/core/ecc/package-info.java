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
 * Error Correction Code (ECC) implementations for data recovery.
 *
 * <p>This package provides Reed-Solomon error correction capabilities for APACK
 * archives. Unlike checksums which only detect errors, error correction codes
 * can both detect and automatically correct a limited number of byte-level
 * errors, making archives resilient against storage media degradation,
 * transmission errors, and minor file corruption.</p>
 *
 * <h2>Reed-Solomon Algorithm</h2>
 * <p>Reed-Solomon is a widely-deployed error correction algorithm used in:</p>
 * <ul>
 *   <li>QR codes and barcodes</li>
 *   <li>CDs, DVDs, and Blu-ray discs</li>
 *   <li>RAID-6 storage systems</li>
 *   <li>Deep-space communication (Voyager, Mars rovers)</li>
 *   <li>Digital television (DVB)</li>
 * </ul>
 *
 * <h2>How It Works</h2>
 * <pre>
 *                            ENCODING
 *    ┌───────────────────────────────────────────────────────────────┐
 *    │                                                               │
 *    │   Original Data (n bytes)                                     │
 *    │   ┌─────────────────────────────────────────────────────┐     │
 *    │   │  D₁  │  D₂  │  D₃  │  ...  │  Dₙ                    │     │
 *    │   └─────────────────────────────────────────────────────┘     │
 *    │                          │                                    │
 *    │                          ▼ Polynomial division in GF(2⁸)      │
 *    │                                                               │
 *    │   Encoded Data (n + p bytes)                                  │
 *    │   ┌─────────────────────────────────────────────────────────┐ │
 *    │   │  D₁  │  D₂  │  ...  │  Dₙ  ║  P₁  │  P₂  │  ...  │  Pₚ │ │
 *    │   └────────────────────────────╨──────────────────────────────┘
 *    │          Original Data         │        Parity Bytes          │
 *    │          (preserved)           │     (error correction)       │
 *    └───────────────────────────────────────────────────────────────┘
 *
 *                            DECODING
 *    ┌───────────────────────────────────────────────────────────────┐
 *    │                                                               │
 *    │   Received Data (possibly corrupted)                          │
 *    │   ┌─────────────────────────────────────────────────────────┐ │
 *    │   │  D₁  │  X   │  D₃  │  ...  │  Dₙ  ║  P₁  │  X   │  Pₚ  │ │
 *    │   └─────────────────────────────────────────────────────────┘ │
 *    │              ▲                             ▲                  │
 *    │              │ Error                       │ Error            │
 *    │              │                             │                  │
 *    │                          │                                    │
 *    │   1. Calculate syndromes ▼                                    │
 *    │   2. Berlekamp-Massey: Find error locator polynomial          │
 *    │   3. Chien Search: Find error positions                       │
 *    │   4. Forney Algorithm: Calculate error magnitudes             │
 *    │   5. Apply corrections                                        │
 *    │                          │                                    │
 *    │                          ▼                                    │
 *    │   Recovered Original Data                                     │
 *    │   ┌─────────────────────────────────────────────────────┐     │
 *    │   │  D₁  │  D₂  │  D₃  │  ...  │  Dₙ                    │     │
 *    │   └─────────────────────────────────────────────────────┘     │
 *    └───────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link de.splatgames.aether.pack.core.ecc.ReedSolomonCodec} - Main codec
 *       for encoding and decoding with configurable parity</li>
 *   <li>{@link de.splatgames.aether.pack.core.ecc.EccConfiguration} - Configuration
 *       presets for different protection levels</li>
 *   <li>{@link de.splatgames.aether.pack.core.ecc.GaloisField} - GF(2⁸) arithmetic
 *       operations for the algorithm</li>
 *   <li>{@link de.splatgames.aether.pack.core.ecc.ErrorCorrectionCodec} - SPI
 *       interface for custom ECC implementations</li>
 * </ul>
 *
 * <h2>Configuration Presets</h2>
 * <table>
 *   <caption>ECC Configuration Options</caption>
 *   <tr><th>Preset</th><th>Parity</th><th>Max Errors</th><th>Overhead</th><th>Use Case</th></tr>
 *   <tr>
 *     <td>LOW_OVERHEAD</td><td>8 bytes</td><td>4</td><td>~3.3%</td>
 *     <td>Fast storage, minor corruption</td>
 *   </tr>
 *   <tr>
 *     <td>DEFAULT</td><td>16 bytes</td><td>8</td><td>~6.7%</td>
 *     <td>Balanced protection</td>
 *   </tr>
 *   <tr>
 *     <td>HIGH_REDUNDANCY</td><td>32 bytes</td><td>16</td><td>~14.3%</td>
 *     <td>Long-term archival, unreliable media</td>
 *   </tr>
 * </table>
 *
 * <h2>Limits and Constraints</h2>
 * <ul>
 *   <li><strong>Max codeword size:</strong> 255 bytes (GF(2⁸) field size - 1)</li>
 *   <li><strong>Max data per block:</strong> 255 - parityBytes</li>
 *   <li><strong>Max correctable errors:</strong> parityBytes / 2</li>
 *   <li><strong>Error detection:</strong> Can detect up to parityBytes errors</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Encoding and Decoding</h3>
 * <pre>{@code
 * // Create codec with 16 parity bytes (can correct up to 8 errors)
 * ReedSolomonCodec codec = new ReedSolomonCodec(16);
 *
 * // Encode data
 * byte[] original = "Critical data that must survive corruption".getBytes();
 * byte[] encoded = codec.encode(original);
 * // encoded.length == original.length + 16
 *
 * // Simulate corruption (3 random byte errors)
 * encoded[5] ^= 0x55;
 * encoded[20] ^= 0xAA;
 * encoded[35] ^= 0xFF;
 *
 * // Decode and recover original data
 * try {
 *     byte[] recovered = codec.decode(encoded);
 *     assert Arrays.equals(original, recovered);  // Success!
 * } catch (EccException e) {
 *     // More than 8 errors - cannot recover
 *     System.err.println("Too many errors: " + e.getMessage());
 * }
 * }</pre>
 *
 * <h3>Verification Without Full Decode</h3>
 * <pre>{@code
 * // Quick integrity check (faster than full decode)
 * if (codec.verify(encoded)) {
 *     System.out.println("Data is intact");
 * } else {
 *     System.out.println("Data has errors (attempting recovery...)");
 *     byte[] recovered = codec.decode(encoded);
 * }
 * }</pre>
 *
 * <h3>Using Configuration Presets</h3>
 * <pre>{@code
 * // High redundancy for archival
 * ReedSolomonCodec archivalCodec = new ReedSolomonCodec(
 *     EccConfiguration.HIGH_REDUNDANCY);
 *
 * // Low overhead for performance
 * ReedSolomonCodec fastCodec = new ReedSolomonCodec(
 *     EccConfiguration.LOW_OVERHEAD);
 *
 * // Custom configuration
 * EccConfiguration custom = EccConfiguration.builder()
 *     .parityBytes(24)  // Correct up to 12 errors
 *     .build();
 * ReedSolomonCodec customCodec = new ReedSolomonCodec(custom);
 * }</pre>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li><strong>Encoding:</strong> O(n × p) where n = data size, p = parity bytes</li>
 *   <li><strong>Decoding (no errors):</strong> O(n × p) - syndrome calculation only</li>
 *   <li><strong>Decoding (with errors):</strong> O(n × p + p³) - includes error location</li>
 *   <li>GF(2⁸) operations use precomputed log/exp tables for speed</li>
 * </ul>
 *
 * <h2>Integration with APACK</h2>
 * <p>When ECC is enabled for an entry, each data chunk is protected separately:</p>
 * <ol>
 *   <li>Chunk data is split into 239-byte blocks (with 16-byte parity)</li>
 *   <li>Each block is encoded with Reed-Solomon</li>
 *   <li>On read, blocks are decoded and errors are corrected automatically</li>
 *   <li>Entries with ECC have the {@link de.splatgames.aether.pack.core.format.FormatConstants#ENTRY_FLAG_HAS_ECC}
 *       flag set in their header</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>{@link de.splatgames.aether.pack.core.ecc.ReedSolomonCodec} instances are
 * thread-safe. The generator polynomial is computed once at construction, and
 * all encoding/decoding operations are stateless.</p>
 *
 * @see de.splatgames.aether.pack.core.ecc.ReedSolomonCodec
 * @see de.splatgames.aether.pack.core.ecc.EccConfiguration
 * @see de.splatgames.aether.pack.core.ecc.ErrorCorrectionCodec
 * @see de.splatgames.aether.pack.core.ecc.GaloisField
 * @see de.splatgames.aether.pack.core.format.FormatConstants#ENTRY_FLAG_HAS_ECC
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.splatgames.aether.pack.core.ecc;
