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
 * Compression providers for APACK archives.
 *
 * <p>This package provides high-performance compression implementations for
 * APACK archives. It includes two compression algorithms optimized for different
 * use cases: ZSTD for maximum compression ratio and LZ4 for maximum speed.</p>
 *
 * <h2>Package Overview</h2>
 *
 * <table>
 *   <caption>Compression Components</caption>
 *   <tr><th>Component</th><th>Purpose</th><th>Recommendation</th></tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.compression.CompressionRegistry}</td>
 *     <td>Central registry for compression algorithms</td>
 *     <td>Entry point for providers</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.compression.ZstdCompressionProvider}</td>
 *     <td>Zstandard compression</td>
 *     <td><strong>Recommended</strong> for most use cases</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.compression.Lz4CompressionProvider}</td>
 *     <td>LZ4 compression</td>
 *     <td>Best for real-time/speed-critical</td>
 *   </tr>
 * </table>
 *
 * <h2>Algorithm Comparison</h2>
 *
 * <table>
 *   <caption>ZSTD vs LZ4 Comparison</caption>
 *   <tr><th>Property</th><th>ZSTD</th><th>LZ4</th></tr>
 *   <tr><td>Compression ratio</td><td>Excellent</td><td>Moderate</td></tr>
 *   <tr><td>Compression speed</td><td>Good (level-dependent)</td><td>Extremely fast</td></tr>
 *   <tr><td>Decompression speed</td><td>Very fast</td><td>Extremely fast</td></tr>
 *   <tr><td>Levels</td><td>1-22</td><td>0-17</td></tr>
 *   <tr><td>Default level</td><td>3</td><td>0 (fast mode)</td></tr>
 *   <tr><td>Memory usage</td><td>Scales with level</td><td>Constant, low</td></tr>
 * </table>
 *
 * <h2>Choosing an Algorithm</h2>
 *
 * <h3>Use ZSTD when:</h3>
 * <ul>
 *   <li>Storage space or bandwidth is limited</li>
 *   <li>Creating archives for distribution or backup</li>
 *   <li>Decompression speed matters more than compression speed</li>
 *   <li>You want the best overall balance of ratio and speed</li>
 * </ul>
 *
 * <h3>Use LZ4 when:</h3>
 * <ul>
 *   <li>Speed is the top priority</li>
 *   <li>Processing real-time or streaming data</li>
 *   <li>CPU resources are constrained</li>
 *   <li>Temporary storage or caching</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Compression</h3>
 * <pre>{@code
 * // Get providers from registry
 * CompressionProvider zstd = CompressionRegistry.zstd();
 * CompressionProvider lz4 = CompressionRegistry.lz4();
 *
 * // Or by ID
 * CompressionProvider provider = CompressionRegistry.require("zstd");
 *
 * // Block compression
 * byte[] compressed = provider.compressBlock(data, provider.getDefaultLevel());
 * byte[] decompressed = provider.decompressBlock(compressed, data.length);
 * }</pre>
 *
 * <h3>Stream Compression</h3>
 * <pre>{@code
 * CompressionProvider zstd = CompressionRegistry.zstd();
 *
 * // Compress to file
 * try (OutputStream out = zstd.compress(new FileOutputStream("data.zst"), 6)) {
 *     out.write(data);
 * }
 *
 * // Decompress from file
 * try (InputStream in = zstd.decompress(new FileInputStream("data.zst"))) {
 *     byte[] data = in.readAllBytes();
 * }
 * }</pre>
 *
 * <h3>Choosing Compression Level</h3>
 * <pre>{@code
 * CompressionProvider zstd = CompressionRegistry.zstd();
 *
 * // Fast compression (level 1-3)
 * byte[] fast = zstd.compressBlock(data, 1);
 *
 * // Balanced (level 6-9)
 * byte[] balanced = zstd.compressBlock(data, 6);
 *
 * // Maximum compression (level 19-22)
 * byte[] max = zstd.compressBlock(data, 19);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>All classes in this package are thread-safe. Compression providers are
 * stateless and can be shared across threads. The {@link de.splatgames.aether.pack.compression.CompressionRegistry}
 * uses concurrent collections for safe provider registration and lookup.</p>
 *
 * <h2>Dependencies</h2>
 *
 * <ul>
 *   <li><strong>zstd-jni:</strong> Native ZSTD bindings (com.github.luben:zstd-jni)</li>
 *   <li><strong>lz4-java:</strong> LZ4 implementation (org.lz4:lz4-java)</li>
 * </ul>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 *
 * @see de.splatgames.aether.pack.compression.CompressionRegistry
 * @see de.splatgames.aether.pack.compression.ZstdCompressionProvider
 * @see de.splatgames.aether.pack.compression.Lz4CompressionProvider
 * @see de.splatgames.aether.pack.core.spi.CompressionProvider
 */
package de.splatgames.aether.pack.compression;
