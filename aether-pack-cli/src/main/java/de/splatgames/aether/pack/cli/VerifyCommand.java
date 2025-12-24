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

package de.splatgames.aether.pack.cli;

import de.splatgames.aether.pack.compression.CompressionRegistry;
import de.splatgames.aether.pack.core.AetherPackReader;
import de.splatgames.aether.pack.core.entry.Entry;
import de.splatgames.aether.pack.core.io.ChunkProcessor;
import de.splatgames.aether.pack.core.spi.CompressionProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI command for verifying the integrity of an APACK archive.
 *
 * <p>This command validates an archive by checking its structure and optionally
 * reading all entry data to verify checksums. It can detect corruption, truncation,
 * and other integrity issues. This is essential for validating backups, verifying
 * transfers, and ensuring data integrity.</p>
 *
 * <h2>Command Syntax</h2>
 * <pre>{@code
 * apack verify [OPTIONS] <archive>
 * apack v [OPTIONS] <archive>
 * }</pre>
 *
 * <h2>Options</h2>
 * <table>
 *   <caption>Verify Command Options</caption>
 *   <tr><th>Option</th><th>Description</th><th>Default</th></tr>
 *   <tr><td>{@code -v, --verbose}</td><td>Show details for each entry</td><td>false</td></tr>
 *   <tr><td>{@code --quick}</td><td>Quick check (headers only, no data verification)</td><td>false</td></tr>
 * </table>
 *
 * <h2>Verification Modes</h2>
 *
 * <h3>Full Verification (default)</h3>
 * <p>Reads all entry data and verifies checksums. This catches:</p>
 * <ul>
 *   <li>Checksum mismatches indicating data corruption</li>
 *   <li>Decompression errors from corrupted compressed data</li>
 *   <li>Truncated or incomplete entries</li>
 *   <li>Invalid entry headers</li>
 * </ul>
 *
 * <h3>Quick Verification ({@code --quick})</h3>
 * <p>Only validates headers without reading entry data. Faster but less thorough.
 * Catches structural issues but not data corruption within chunks.</p>
 *
 * <h2>Exit Codes</h2>
 * <table>
 *   <caption>Exit Code Meanings</caption>
 *   <tr><th>Code</th><th>Meaning</th></tr>
 *   <tr><td>0</td><td>All entries verified successfully</td></tr>
 *   <tr><td>1</td><td>One or more entries failed verification</td></tr>
 *   <tr><td>2</td><td>Archive could not be read (format error, file not found)</td></tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * # Full verification
 * apack verify archive.apack
 *
 * # Verbose output showing each file
 * apack verify -v archive.apack
 *
 * # Quick header-only check
 * apack verify --quick archive.apack
 *
 * # Verify in script (check exit code)
 * if apack verify archive.apack; then
 *     echo "Archive is valid"
 * else
 *     echo "Archive is corrupted"
 * fi
 * }</pre>
 *
 * <h2>Example Output</h2>
 *
 * <h3>Successful Verification (verbose)</h3>
 * <pre>{@code
 * Verifying: archive.apack
 *   Format version: 1.0.0
 *   Entries: 42
 *   Chunk size: 256 KB
 *   Random access: yes
 *   Encrypted: no
 *
 *   OK: file1.txt (1.5 KB)
 *   OK: subdir/file2.dat (2.3 MB)
 *   OK: image.png (512 KB)
 *
 * OK - 42 entries verified (4.3 MB)
 * }</pre>
 *
 * <h3>Failed Verification</h3>
 * <pre>{@code
 * Verifying: corrupted.apack
 *   FAIL: damaged.bin - Checksum mismatch
 *
 * FAILED - 1 of 42 entries failed verification
 * }</pre>
 *
 * @see ListCommand
 * @see InfoCommand
 * @see ApackCli
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Command(
        name = "verify",
        aliases = {"v"},
        description = "Verify integrity of an APACK archive"
)
public final class VerifyCommand implements Callable<Integer> {

    /**
     * Creates a new VerifyCommand instance.
     * This constructor is called by picocli during command parsing.
     */
    public VerifyCommand() {
        // Default constructor for picocli
    }

    /** The archive file to verify. */
    @Parameters(index = "0", description = "Archive file to verify")
    Path archiveFile;

    /** Whether to show details for each entry during verification. */
    @Option(names = {"-v", "--verbose"}, description = "Show details for each entry")
    boolean verbose;

    /** Whether to perform quick verification (headers only, no data). */
    @Option(names = {"--quick"}, description = "Quick check (headers only, no data)")
    boolean quick;

    /**
     * Executes the verify command.
     *
     * <p>This method performs the following steps:</p>
     * <ol>
     *   <li>Validates the archive file exists</li>
     *   <li>Detects compression from entry headers</li>
     *   <li>Opens the archive with the appropriate chunk processor</li>
     *   <li>Optionally displays archive header information (verbose mode)</li>
     *   <li>Iterates all entries, reading data to verify checksums (full mode)
     *       or just validating headers (quick mode)</li>
     *   <li>Reports verification results</li>
     * </ol>
     *
     * @return exit code: 0 if all entries verified, 1 if any entry failed,
     *         2 if the archive could not be read
     * @throws Exception if a fatal error occurs during verification
     */
    @Override
    public Integer call() throws Exception {
        if (!Files.exists(this.archiveFile)) {
            System.err.println("Error: Archive not found: " + this.archiveFile);
            return 1;
        }

        System.out.println("Verifying: " + this.archiveFile);

        int totalEntries = 0;
        int verifiedEntries = 0;
        int failedEntries = 0;
        long totalBytes = 0;

        // First pass: detect compression from entries
        ChunkProcessor chunkProcessor = ChunkProcessor.passThrough();
        try (AetherPackReader scanReader = AetherPackReader.open(this.archiveFile)) {
            for (final Entry entry : scanReader) {
                if (entry.isCompressed() && entry.getCompressionId() > 0) {
                    final CompressionProvider compProvider = CompressionRegistry.getById(entry.getCompressionId())
                            .orElse(null);
                    if (compProvider != null) {
                        chunkProcessor = ChunkProcessor.builder()
                                .compression(compProvider)
                                .build();
                        break;
                    }
                }
            }
        }

        try (AetherPackReader reader = AetherPackReader.open(this.archiveFile, chunkProcessor)) {
            // Check file header
            final var header = reader.getFileHeader();
            if (this.verbose) {
                System.out.printf("  Format version: %d.%d.%d%n",
                        header.versionMajor(), header.versionMinor(), header.versionPatch());
                System.out.printf("  Entries: %d%n", reader.getEntryCount());
                System.out.printf("  Chunk size: %d KB%n", header.chunkSize() / 1024);
                System.out.printf("  Random access: %s%n", header.hasRandomAccess() ? "yes" : "no");
                System.out.printf("  Encrypted: %s%n", header.isEncrypted() ? "yes" : "no");
                System.out.println();
            }

            for (final Entry entry : reader) {
                totalEntries++;
                final String name = entry.getName();

                try {
                    if (!this.quick) {
                        // Full verification: read all data to verify checksums
                        try (InputStream input = reader.getInputStream(entry)) {
                            final byte[] buffer = new byte[8192];
                            long entryBytes = 0;
                            while (true) {
                                final int read = input.read(buffer);
                                if (read == -1) {
                                    break;
                                }
                                entryBytes += read;
                            }
                            totalBytes += entryBytes;
                        }
                    }

                    verifiedEntries++;
                    if (this.verbose) {
                        System.out.printf("  OK: %s (%s)%n", name, formatSize(entry.getOriginalSize()));
                    }

                } catch (final Exception e) {
                    failedEntries++;
                    System.err.printf("  FAIL: %s - %s%n", name, e.getMessage());
                }
            }

        } catch (final Exception e) {
            System.err.println("Error reading archive: " + e.getMessage());
            return 2;
        }

        // Print summary
        System.out.println();
        if (failedEntries == 0) {
            System.out.printf("OK - %d entries verified", verifiedEntries);
            if (!this.quick) {
                System.out.printf(" (%s)", formatSize(totalBytes));
            }
            System.out.println();
            return 0;
        } else {
            System.out.printf("FAILED - %d of %d entries failed verification%n",
                    failedEntries, totalEntries);
            return 1;
        }
    }

    /**
     * Formats a byte count as a human-readable string.
     *
     * @param bytes the number of bytes
     * @return a formatted string (e.g., "1.5 MB", "256 B")
     */
    private static String formatSize(final long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

}
