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
 * Command-line interface for the APACK file format.
 *
 * <p>This package provides a comprehensive command-line tool for working with
 * APACK archives. It uses the Picocli framework for argument parsing and offers
 * commands for creating, extracting, listing, inspecting, and verifying archives.</p>
 *
 * <h2>Package Overview</h2>
 *
 * <table>
 *   <caption>CLI Components</caption>
 *   <tr><th>Class</th><th>Command</th><th>Aliases</th><th>Description</th></tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.cli.ApackCli}</td>
 *     <td>{@code apack}</td>
 *     <td>-</td>
 *     <td>Main entry point and root command</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.cli.CreateCommand}</td>
 *     <td>{@code create}</td>
 *     <td>{@code c}</td>
 *     <td>Create new archives with compression/encryption</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.cli.ExtractCommand}</td>
 *     <td>{@code extract}</td>
 *     <td>{@code x}</td>
 *     <td>Extract files from archives</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.cli.ListCommand}</td>
 *     <td>{@code list}</td>
 *     <td>{@code l}, {@code ls}</td>
 *     <td>List archive contents in various formats</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.cli.InfoCommand}</td>
 *     <td>{@code info}</td>
 *     <td>{@code i}</td>
 *     <td>Display archive metadata and statistics</td>
 *   </tr>
 *   <tr>
 *     <td>{@link de.splatgames.aether.pack.cli.VerifyCommand}</td>
 *     <td>{@code verify}</td>
 *     <td>{@code v}</td>
 *     <td>Verify archive integrity</td>
 *   </tr>
 * </table>
 *
 * <h2>Quick Start</h2>
 *
 * <h3>Creating Archives</h3>
 * <pre>{@code
 * # Basic archive creation
 * apack create archive.apack file1.txt file2.txt
 *
 * # Archive a directory with ZSTD compression
 * apack create -c zstd archive.apack ./mydir/
 *
 * # Create encrypted archive
 * apack create -c zstd -e aes-256-gcm archive.apack ./sensitive/
 *
 * # High compression (slower)
 * apack create -c zstd -l 19 archive.apack ./data/
 *
 * # Fast compression with LZ4
 * apack create -c lz4 archive.apack ./data/
 * }</pre>
 *
 * <h3>Extracting Archives</h3>
 * <pre>{@code
 * # Extract to current directory
 * apack extract archive.apack
 *
 * # Extract to specific directory
 * apack extract archive.apack -o ./output/
 *
 * # Extract with verbose output
 * apack extract -v archive.apack -o ./output/
 *
 * # Preview extraction (dry run)
 * apack extract --dry-run archive.apack
 * }</pre>
 *
 * <h3>Inspecting Archives</h3>
 * <pre>{@code
 * # List contents
 * apack list archive.apack
 *
 * # Detailed listing with sizes
 * apack list -l archive.apack
 *
 * # JSON output for scripting
 * apack list --json archive.apack
 *
 * # Show archive information
 * apack info archive.apack
 *
 * # Verify integrity
 * apack verify archive.apack
 *
 * # Quick verification (headers only)
 * apack verify --quick archive.apack
 * }</pre>
 *
 * <h2>Global Options</h2>
 *
 * <p>These options are available for all commands:</p>
 * <ul>
 *   <li>{@code -h, --help} - Show help message</li>
 *   <li>{@code -V, --version} - Show version information</li>
 *   <li>{@code -v, --verbose} - Enable verbose output (command-specific)</li>
 * </ul>
 *
 * <h2>Exit Codes</h2>
 *
 * <table>
 *   <caption>Exit Code Reference</caption>
 *   <tr><th>Code</th><th>Meaning</th></tr>
 *   <tr><td>0</td><td>Success</td></tr>
 *   <tr><td>1</td><td>General error (invalid arguments, operation failed)</td></tr>
 *   <tr><td>2</td><td>Archive error (corruption, invalid format)</td></tr>
 * </table>
 *
 * <h2>Compression Algorithms</h2>
 *
 * <table>
 *   <caption>Supported Compression Algorithms</caption>
 *   <tr><th>ID</th><th>Levels</th><th>Default</th><th>Recommendation</th></tr>
 *   <tr><td>{@code zstd}</td><td>1-22</td><td>3</td><td>Best overall balance (recommended)</td></tr>
 *   <tr><td>{@code lz4}</td><td>0-17</td><td>0</td><td>Fastest, for speed-critical use</td></tr>
 *   <tr><td>{@code none}</td><td>-</td><td>-</td><td>Store only, no compression</td></tr>
 * </table>
 *
 * <h2>Encryption Algorithms</h2>
 *
 * <table>
 *   <caption>Supported Encryption Algorithms</caption>
 *   <tr><th>ID</th><th>Key Size</th><th>Recommendation</th></tr>
 *   <tr><td>{@code aes-256-gcm}</td><td>256-bit</td><td>Best compatibility</td></tr>
 *   <tr><td>{@code chacha20-poly1305}</td><td>256-bit</td><td>Best for software-only implementations</td></tr>
 * </table>
 *
 * <h2>Dependencies</h2>
 *
 * <ul>
 *   <li><strong>Picocli:</strong> Command-line parsing framework</li>
 *   <li><strong>aether-pack-core:</strong> Core archive reading/writing</li>
 *   <li><strong>aether-pack-compression:</strong> ZSTD and LZ4 providers</li>
 *   <li><strong>aether-pack-crypto:</strong> Encryption providers</li>
 * </ul>
 *
 * <h2>Building the CLI</h2>
 *
 * <p>The CLI module produces a fat JAR containing all dependencies:</p>
 * <pre>{@code
 * mvn clean package -pl aether-pack-cli -am
 *
 * # Run the CLI
 * java -jar aether-pack-cli/target/aether-pack-cli-*-fat.jar --help
 * }</pre>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 *
 * @see de.splatgames.aether.pack.cli.ApackCli
 * @see de.splatgames.aether.pack.cli.CreateCommand
 * @see de.splatgames.aether.pack.cli.ExtractCommand
 * @see de.splatgames.aether.pack.cli.ListCommand
 * @see de.splatgames.aether.pack.cli.InfoCommand
 * @see de.splatgames.aether.pack.cli.VerifyCommand
 */
package de.splatgames.aether.pack.cli;
