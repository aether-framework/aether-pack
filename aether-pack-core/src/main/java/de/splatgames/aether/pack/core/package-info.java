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
 * Core module of the Aether Pack (APACK) binary file format library.
 *
 * <p>This package provides the primary API for creating and reading APACK archives,
 * a modern binary container format designed for efficient storage, streaming access,
 * and data integrity. APACK supports optional compression, encryption, and error
 * correction, making it suitable for a wide range of applications from game assets
 * to secure data archives.</p>
 *
 * <h2>Architecture Overview</h2>
 * <pre>
 *                           ┌─────────────────────────────────────────┐
 *                           │           Application Layer             │
 *                           └──────────────────┬──────────────────────┘
 *                                              │
 *               ┌──────────────────────────────┴──────────────────────────────┐
 *               │                      Core API                               │
 *               │  ┌──────────────────┐          ┌──────────────────┐         │
 *               │  │ AetherPackWriter │          │ AetherPackReader │         │
 *               │  │   (write only)   │          │   (read only)    │         │
 *               │  └────────┬─────────┘          └────────┬─────────┘         │
 *               │           │                             │                   │
 *               │           └──────────────┬──────────────┘                   │
 *               │                          │                                  │
 *               │              ┌───────────┴───────────┐                      │
 *               │              │   ApackConfiguration  │                      │
 *               │              │  (compression, crypto,│                      │
 *               │              │   chunk size, etc.)   │                      │
 *               │              └───────────────────────┘                      │
 *               └─────────────────────────────────────────────────────────────┘
 *                                              │
 *          ┌────────────────┬─────────────────┬┴───────────────┬──────────────────┐
 *          ▼                ▼                 ▼                ▼                  ▼
 *     ┌─────────┐     ┌──────────┐     ┌───────────┐     ┌──────────┐     ┌────────────┐
 *     │ format  │     │    io    │     │  pipeline │     │   spi    │     │  checksum  │
 *     │ (types) │     │ (binary) │     │ (stages)  │     │(plugins) │     │ (integrity)│
 *     └─────────┘     └──────────┘     └───────────┘     └──────────┘     └────────────┘
 * </pre>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link de.splatgames.aether.pack.core.AetherPackWriter} - Creates new APACK archives
 *       with support for compression, encryption, and random access</li>
 *   <li>{@link de.splatgames.aether.pack.core.AetherPackReader} - Reads existing archives
 *       with streaming or random access to entries</li>
 *   <li>{@link de.splatgames.aether.pack.core.ApackConfiguration} - Configures archive
 *       parameters including chunk size, compression, and encryption</li>
 * </ul>
 *
 * <h2>Subpackages</h2>
 * <table>
 *   <caption>Core Module Package Structure</caption>
 *   <tr><th>Package</th><th>Description</th></tr>
 *   <tr><td>{@link de.splatgames.aether.pack.core.format}</td>
 *       <td>Format constants and binary structure records (headers, trailers)</td></tr>
 *   <tr><td>{@link de.splatgames.aether.pack.core.io}</td>
 *       <td>Binary I/O primitives and chunked stream processing</td></tr>
 *   <tr><td>{@link de.splatgames.aether.pack.core.pipeline}</td>
 *       <td>Composable processing stages for compression, encryption, checksums</td></tr>
 *   <tr><td>{@link de.splatgames.aether.pack.core.spi}</td>
 *       <td>Service Provider Interfaces for pluggable algorithms</td></tr>
 *   <tr><td>{@link de.splatgames.aether.pack.core.checksum}</td>
 *       <td>Checksum algorithm implementations (CRC32, XXH3)</td></tr>
 *   <tr><td>{@link de.splatgames.aether.pack.core.ecc}</td>
 *       <td>Error correction using Reed-Solomon codes</td></tr>
 *   <tr><td>{@link de.splatgames.aether.pack.core.entry}</td>
 *       <td>Entry abstraction and metadata handling</td></tr>
 *   <tr><td>{@link de.splatgames.aether.pack.core.exception}</td>
 *       <td>Exception hierarchy for APACK operations</td></tr>
 * </table>
 *
 * <h2>Quick Start: Creating an Archive</h2>
 * <pre>{@code
 * // Simple archive without compression
 * try (AetherPackWriter writer = AetherPackWriter.create(Path.of("data.apack"))) {
 *     writer.addEntry("config.json", configStream);
 *     writer.addEntry("data.bin", Path.of("/path/to/data.bin"));
 * }
 *
 * // With compression and custom chunk size
 * ApackConfiguration config = ApackConfiguration.builder()
 *     .compression(CompressionRegistry.zstd(), 6)
 *     .chunkSize(128 * 1024)  // 128 KB chunks
 *     .build();
 *
 * try (AetherPackWriter writer = AetherPackWriter.create(path, config)) {
 *     writer.addEntry("large-file.dat", inputStream);
 * }
 * }</pre>
 *
 * <h2>Quick Start: Reading an Archive</h2>
 * <pre>{@code
 * // List and extract entries
 * try (AetherPackReader reader = AetherPackReader.open(Path.of("data.apack"))) {
 *     for (Entry entry : reader) {
 *         System.out.printf("%s (%d bytes)%n", entry.getName(), entry.getOriginalSize());
 *
 *         // Extract entry data
 *         byte[] data = reader.readAllBytes(entry);
 *
 *         // Or stream the data
 *         try (InputStream in = reader.getInputStream(entry)) {
 *             processData(in);
 *         }
 *     }
 *
 *     // Random access by name
 *     Optional&lt;Entry&gt; entry = reader.getEntry("config.json");
 * }
 * }</pre>
 *
 * <h2>Binary Format</h2>
 * <p>An APACK file has the following high-level structure:</p>
 * <pre>
 * ┌───────────────────────────────────────────────────────────────┐
 * │                    File Header (64 bytes)                     │
 * │  Magic "APACK" │ Version │ Flags │ Chunk Size │ Entry Count  │
 * ├───────────────────────────────────────────────────────────────┤
 * │                    Encryption Block (optional)                │
 * │           Salt │ KDF Parameters │ Wrapped Key                 │
 * ├───────────────────────────────────────────────────────────────┤
 * │                         Entry 1                               │
 * │  ┌───────────────────────────────────────────────────────┐   │
 * │  │ Entry Header (variable): Name, MIME, Attributes       │   │
 * │  ├───────────────────────────────────────────────────────┤   │
 * │  │ Chunk 1: Header (24 bytes) + Data (compressed/encrypted)│ │
 * │  │ Chunk 2: Header (24 bytes) + Data                     │   │
 * │  │ ...                                                   │   │
 * │  │ Chunk N: Header (24 bytes) + Data (last chunk flag)   │   │
 * │  └───────────────────────────────────────────────────────┘   │
 * ├───────────────────────────────────────────────────────────────┤
 * │                         Entry 2...N                           │
 * ├───────────────────────────────────────────────────────────────┤
 * │                    Trailer (48+ bytes)                        │
 * │        Table of Contents │ Statistics │ Integrity Check       │
 * └───────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>The core reader and writer classes are <strong>not thread-safe</strong>.
 * A single archive should be accessed by one thread at a time, or external
 * synchronization must be provided. Provider implementations (compression,
 * encryption, checksum) are typically thread-safe and stateless.</p>
 *
 * <h2>Byte Order</h2>
 * <p>All multi-byte integer values in the binary format are stored in
 * <strong>Little-Endian</strong> byte order, matching the native byte order
 * of x86/x64 processors for optimal performance.</p>
 *
 * @see de.splatgames.aether.pack.core.AetherPackWriter
 * @see de.splatgames.aether.pack.core.AetherPackReader
 * @see de.splatgames.aether.pack.core.ApackConfiguration
 * @see de.splatgames.aether.pack.core.format.FormatConstants
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
package de.splatgames.aether.pack.core;
