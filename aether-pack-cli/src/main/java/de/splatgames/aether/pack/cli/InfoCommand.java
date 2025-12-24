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

import de.splatgames.aether.pack.core.AetherPackReader;
import de.splatgames.aether.pack.core.entry.Entry;
import de.splatgames.aether.pack.core.format.FileHeader;
import de.splatgames.aether.pack.core.format.FormatConstants;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI command for displaying detailed information about an APACK archive.
 *
 * <p>This command provides comprehensive information about an archive's
 * structure, format version, configuration settings, and content statistics.
 * It's useful for diagnosing issues, understanding archive capabilities,
 * and gathering metadata for processing.</p>
 *
 * <h2>Command Syntax</h2>
 * <pre>{@code
 * apack info [OPTIONS] <archive>
 * apack i [OPTIONS] <archive>
 * }</pre>
 *
 * <h2>Options</h2>
 * <table>
 *   <caption>Info Command Options</caption>
 *   <tr><th>Option</th><th>Description</th><th>Default</th></tr>
 *   <tr><td>{@code --json}</td><td>Output in JSON format</td><td>false</td></tr>
 * </table>
 *
 * <h2>Information Displayed</h2>
 *
 * <h3>File Section</h3>
 * <ul>
 *   <li>Path to the archive file</li>
 *   <li>File size on disk</li>
 * </ul>
 *
 * <h3>Format Section</h3>
 * <ul>
 *   <li>Version number (major.minor.patch)</li>
 *   <li>Compatibility level</li>
 *   <li>Mode (Stream or Container)</li>
 *   <li>Chunk size configuration</li>
 *   <li>Checksum algorithm used</li>
 * </ul>
 *
 * <h3>Flags Section</h3>
 * <ul>
 *   <li>Random access support</li>
 *   <li>Encryption status</li>
 *   <li>Compression status</li>
 * </ul>
 *
 * <h3>Content Section</h3>
 * <ul>
 *   <li>Entry count</li>
 *   <li>Original and stored sizes</li>
 *   <li>Compression ratio</li>
 *   <li>Counts of compressed, encrypted, and ECC-protected entries</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * # Display archive information
 * apack info archive.apack
 *
 * # JSON output for scripting
 * apack info --json archive.apack
 *
 * # Check version compatibility
 * apack i archive.apack | grep "Compat Level"
 *
 * # Extract specific field with JSON
 * apack info --json archive.apack | jq '.format.versionMajor'
 * }</pre>
 *
 * <h2>Example Output</h2>
 * <pre>{@code
 * APACK Archive Information
 * ========================================
 *
 * File:
 *   Path: archive.apack
 *   Size: 1.5 MB
 *
 * Format:
 *   Version: 1.0.0
 *   Compat Level: 1
 *   Mode: Container
 *   Chunk Size: 256 KB
 *   Checksum: XXH3-64
 *
 * Flags:
 *   Random Access: Yes
 *   Encrypted: No
 *   Compressed: Yes
 *
 * Content:
 *   Entries: 42
 *   Original Size: 5.2 MB
 *   Stored Size: 1.5 MB
 *   Compression Ratio: 28.8%
 *   Compressed Entries: 42
 *   Encrypted Entries: 0
 *   ECC Entries: 0
 * }</pre>
 *
 * @see ListCommand
 * @see VerifyCommand
 * @see ApackCli
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Command(
        name = "info",
        aliases = {"i"},
        description = "Display information about an APACK archive"
)
public final class InfoCommand implements Callable<Integer> {

    /**
     * Creates a new InfoCommand instance.
     * This constructor is called by picocli during command parsing.
     */
    public InfoCommand() {
        // Default constructor for picocli
    }

    /** The archive file to inspect. */
    @Parameters(index = "0", description = "Archive file to inspect")
    Path archiveFile;

    /** Whether to output in JSON format for scripting. */
    @Option(names = {"--json"}, description = "Output in JSON format")
    boolean jsonFormat;

    /**
     * Executes the info command.
     *
     * <p>This method performs the following steps:</p>
     * <ol>
     *   <li>Validates the archive file exists</li>
     *   <li>Opens the archive and reads the file header</li>
     *   <li>Calculates content statistics by iterating all entries</li>
     *   <li>Outputs information in the selected format (text or JSON)</li>
     * </ol>
     *
     * @return exit code: 0 for success, 1 for error
     * @throws Exception if reading the archive fails
     */
    @Override
    public Integer call() throws Exception {
        if (!Files.exists(this.archiveFile)) {
            System.err.println("Error: Archive not found: " + this.archiveFile);
            return 1;
        }

        try (AetherPackReader reader = AetherPackReader.open(this.archiveFile)) {
            final FileHeader header = reader.getFileHeader();

            if (this.jsonFormat) {
                printJson(reader, header);
            } else {
                printInfo(reader, header);
            }
        }

        return 0;
    }

    /**
     * Prints archive information in human-readable text format.
     *
     * <p>Outputs sections for file info, format details, flags, and content
     * statistics. Each section is clearly labeled and formatted for readability.</p>
     *
     * @param reader the archive reader for accessing entries
     * @param header the file header containing format information
     * @throws Exception if an error occurs while iterating entries
     */
    private void printInfo(final AetherPackReader reader, final FileHeader header) throws Exception {
        System.out.println("APACK Archive Information");
        System.out.println("=".repeat(40));
        System.out.println();

        // File info
        System.out.println("File:");
        System.out.printf("  Path: %s%n", this.archiveFile);
        System.out.printf("  Size: %s%n", formatSize(Files.size(this.archiveFile)));
        System.out.println();

        // Format info
        System.out.println("Format:");
        System.out.printf("  Version: %d.%d.%d%n",
                header.versionMajor(), header.versionMinor(), header.versionPatch());
        System.out.printf("  Compat Level: %d%n", header.compatLevel());
        System.out.printf("  Mode: %s%n", header.isStreamMode() ? "Stream" : "Container");
        System.out.printf("  Chunk Size: %s%n", formatSize(header.chunkSize()));
        System.out.printf("  Checksum: %s%n", getChecksumName(header.checksumAlgorithm()));
        System.out.println();

        // Flags
        System.out.println("Flags:");
        System.out.printf("  Random Access: %s%n", header.hasRandomAccess() ? "Yes" : "No");
        System.out.printf("  Encrypted: %s%n", header.isEncrypted() ? "Yes" : "No");
        System.out.printf("  Compressed: %s%n", header.isCompressed() ? "Yes" : "No");
        System.out.println();

        // Content stats
        long totalOriginal = 0;
        long totalStored = 0;
        int compressedCount = 0;
        int encryptedCount = 0;
        int eccCount = 0;

        for (final Entry entry : reader) {
            totalOriginal += entry.getOriginalSize();
            totalStored += entry.getStoredSize();
            if (entry.isCompressed()) compressedCount++;
            if (entry.isEncrypted()) encryptedCount++;
            if (entry.hasEcc()) eccCount++;
        }

        System.out.println("Content:");
        System.out.printf("  Entries: %d%n", reader.getEntryCount());
        System.out.printf("  Original Size: %s%n", formatSize(totalOriginal));
        System.out.printf("  Stored Size: %s%n", formatSize(totalStored));
        if (totalOriginal > 0) {
            System.out.printf("  Compression Ratio: %.1f%%%n", (totalStored * 100.0) / totalOriginal);
        }
        System.out.printf("  Compressed Entries: %d%n", compressedCount);
        System.out.printf("  Encrypted Entries: %d%n", encryptedCount);
        System.out.printf("  ECC Entries: %d%n", eccCount);
    }

    /**
     * Prints archive information in JSON format for machine processing.
     *
     * <p>Outputs a JSON object with nested structures for file info,
     * format details, and content statistics.</p>
     *
     * @param reader the archive reader for accessing entries
     * @param header the file header containing format information
     * @throws Exception if an error occurs while iterating entries
     */
    private void printJson(final AetherPackReader reader, final FileHeader header) throws Exception {
        long totalOriginal = 0;
        long totalStored = 0;
        int compressedCount = 0;
        int encryptedCount = 0;
        int eccCount = 0;

        for (final Entry entry : reader) {
            totalOriginal += entry.getOriginalSize();
            totalStored += entry.getStoredSize();
            if (entry.isCompressed()) compressedCount++;
            if (entry.isEncrypted()) encryptedCount++;
            if (entry.hasEcc()) eccCount++;
        }

        System.out.println("{");
        System.out.printf("  \"file\": \"%s\",%n", escapeJson(this.archiveFile.toString()));
        System.out.printf("  \"fileSize\": %d,%n", Files.size(this.archiveFile));
        System.out.println("  \"format\": {");
        System.out.printf("    \"versionMajor\": %d,%n", header.versionMajor());
        System.out.printf("    \"versionMinor\": %d,%n", header.versionMinor());
        System.out.printf("    \"versionPatch\": %d,%n", header.versionPatch());
        System.out.printf("    \"compatLevel\": %d,%n", header.compatLevel());
        System.out.printf("    \"streamMode\": %b,%n", header.isStreamMode());
        System.out.printf("    \"chunkSize\": %d,%n", header.chunkSize());
        System.out.printf("    \"checksumAlgorithm\": %d,%n", header.checksumAlgorithm());
        System.out.printf("    \"randomAccess\": %b,%n", header.hasRandomAccess());
        System.out.printf("    \"encrypted\": %b,%n", header.isEncrypted());
        System.out.printf("    \"compressed\": %b%n", header.isCompressed());
        System.out.println("  },");
        System.out.println("  \"content\": {");
        System.out.printf("    \"entryCount\": %d,%n", reader.getEntryCount());
        System.out.printf("    \"originalSize\": %d,%n", totalOriginal);
        System.out.printf("    \"storedSize\": %d,%n", totalStored);
        System.out.printf("    \"compressedEntries\": %d,%n", compressedCount);
        System.out.printf("    \"encryptedEntries\": %d,%n", encryptedCount);
        System.out.printf("    \"eccEntries\": %d%n", eccCount);
        System.out.println("  }");
        System.out.println("}");
    }

    /**
     * Returns the human-readable name of a checksum algorithm.
     *
     * @param algorithmId the numeric checksum algorithm identifier
     * @return the algorithm name (e.g., "CRC32", "XXH3-64"), or "Unknown (id)" if not recognized
     */
    private static String getChecksumName(final int algorithmId) {
        return switch (algorithmId) {
            case FormatConstants.CHECKSUM_CRC32 -> "CRC32";
            case FormatConstants.CHECKSUM_XXH3_64 -> "XXH3-64";
            case FormatConstants.CHECKSUM_XXH3_128 -> "XXH3-128";
            default -> "Unknown (" + algorithmId + ")";
        };
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

    /**
     * Escapes a string for safe inclusion in JSON output.
     *
     * <p>Handles backslashes, quotes, and control characters.</p>
     *
     * @param str the string to escape; may be {@code null}
     * @return the escaped string, or empty string if input is {@code null}
     */
    private static String escapeJson(final String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

}
