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
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI command for listing contents of an APACK archive.
 *
 * <p>This command displays the contents of an APACK archive in various formats:
 * simple file names, detailed long format with sizes and flags, or machine-readable
 * JSON output. It's useful for inspecting archives without extracting them.</p>
 *
 * <h2>Command Syntax</h2>
 * <pre>{@code
 * apack list [OPTIONS] <archive>
 * apack l [OPTIONS] <archive>
 * apack ls [OPTIONS] <archive>
 * }</pre>
 *
 * <h2>Options</h2>
 * <table>
 *   <caption>List Command Options</caption>
 *   <tr><th>Option</th><th>Description</th><th>Default</th></tr>
 *   <tr><td>{@code -l, --long}</td><td>Use long listing format with sizes and flags</td><td>false</td></tr>
 *   <tr><td>{@code --json}</td><td>Output in JSON format</td><td>false</td></tr>
 * </table>
 *
 * <h2>Output Formats</h2>
 *
 * <h3>Simple Format (default)</h3>
 * <p>Lists only the file names, one per line:</p>
 * <pre>{@code
 * file1.txt
 * subdir/file2.dat
 * image.png
 * }</pre>
 *
 * <h3>Long Format ({@code -l})</h3>
 * <p>Displays detailed information in a tabular format:</p>
 * <pre>{@code
 * ID       Size       Stored     Ratio Flag Name
 * ----------------------------------------------------------------------
 *        0 1.5 KB     512 B       34%  C    file1.txt
 *        1 2.3 MB     1.8 MB      78%  CE   sensitive.dat
 *        2 100 B      100 B      100%  -    readme.txt
 * ----------------------------------------------------------------------
 *        3 2.3 MB     1.8 MB      78%       (3 entries)
 * }</pre>
 *
 * <p>Flag meanings:</p>
 * <ul>
 *   <li>{@code C} - Compressed</li>
 *   <li>{@code E} - Encrypted</li>
 *   <li>{@code R} - Has Reed-Solomon error correction</li>
 *   <li>{@code -} - No special flags</li>
 * </ul>
 *
 * <h3>JSON Format ({@code --json})</h3>
 * <p>Outputs machine-readable JSON with full entry details:</p>
 * <pre>{@code
 * {
 *   "entries": [
 *     {
 *       "id": 0,
 *       "name": "file1.txt",
 *       "originalSize": 1536,
 *       "storedSize": 512,
 *       ...
 *     }
 *   ],
 *   "totalEntries": 1
 * }
 * }</pre>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * # Simple listing
 * apack list archive.apack
 *
 * # Long format with details
 * apack list -l archive.apack
 *
 * # JSON output for scripting
 * apack list --json archive.apack | jq '.entries[].name'
 *
 * # Using aliases
 * apack l archive.apack
 * apack ls -l archive.apack
 * }</pre>
 *
 * @see InfoCommand
 * @see VerifyCommand
 * @see ApackCli
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Command(
        name = "list",
        aliases = {"l", "ls"},
        description = "List contents of an APACK archive"
)
public final class ListCommand implements Callable<Integer> {

    /**
     * Creates a new ListCommand instance.
     * This constructor is called by picocli during command parsing.
     */
    public ListCommand() {
        // Default constructor for picocli
    }

    /** The archive file to list contents from. */
    @Parameters(index = "0", description = "Archive file to list")
    Path archiveFile;

    /** Whether to use long listing format with detailed information. */
    @Option(names = {"-l", "--long"}, description = "Use long listing format")
    boolean longFormat;

    /** Whether to output in JSON format for scripting. */
    @Option(names = {"--json"}, description = "Output in JSON format")
    boolean jsonFormat;

    /**
     * Executes the list command.
     *
     * <p>This method performs the following steps:</p>
     * <ol>
     *   <li>Validates the archive file exists</li>
     *   <li>Opens the archive for reading</li>
     *   <li>Outputs entries in the selected format (simple, long, or JSON)</li>
     * </ol>
     *
     * @return exit code: 0 for success, 1 for error
     * @throws Exception if listing fails due to I/O errors or archive corruption
     */
    @Override
    public Integer call() throws Exception {
        if (!Files.exists(this.archiveFile)) {
            System.err.println("Error: Archive not found: " + this.archiveFile);
            return 1;
        }

        try (AetherPackReader reader = AetherPackReader.open(this.archiveFile)) {
            if (this.jsonFormat) {
                printJson(reader);
            } else if (this.longFormat) {
                printLong(reader);
            } else {
                printSimple(reader);
            }
        }

        return 0;
    }

    /**
     * Prints entries in simple format (names only).
     *
     * <p>Outputs one file name per line without any additional information.</p>
     *
     * @param reader the archive reader to iterate over entries
     */
    private void printSimple(final AetherPackReader reader) {
        for (final Entry entry : reader) {
            System.out.println(entry.getName());
        }
    }

    /**
     * Prints entries in long format with detailed information.
     *
     * <p>Displays a table with columns for ID, original size, stored size,
     * compression ratio, flags, and file name. Includes a header row and
     * a summary footer with totals.</p>
     *
     * @param reader the archive reader to iterate over entries
     */
    private void printLong(final AetherPackReader reader) {
        // Print header
        System.out.printf("%-8s %-10s %-10s %-5s %-4s %s%n",
                "ID", "Size", "Stored", "Ratio", "Flag", "Name");
        System.out.println("-".repeat(70));

        long totalOriginal = 0;
        long totalStored = 0;

        for (final Entry entry : reader) {
            final String flags = buildFlags(entry);
            final double ratio = entry.getOriginalSize() > 0
                    ? (entry.getStoredSize() * 100.0) / entry.getOriginalSize()
                    : 100.0;

            System.out.printf("%8d %-10s %-10s %4.0f%% %-4s %s%n",
                    entry.getId(),
                    formatSize(entry.getOriginalSize()),
                    formatSize(entry.getStoredSize()),
                    ratio,
                    flags,
                    entry.getName());

            totalOriginal += entry.getOriginalSize();
            totalStored += entry.getStoredSize();
        }

        // Print footer
        System.out.println("-".repeat(70));
        final double totalRatio = totalOriginal > 0
                ? (totalStored * 100.0) / totalOriginal
                : 100.0;
        System.out.printf("%8d %-10s %-10s %4.0f%%      (%d entries)%n",
                reader.getEntryCount(),
                formatSize(totalOriginal),
                formatSize(totalStored),
                totalRatio,
                reader.getEntryCount());
    }

    /**
     * Prints entries in JSON format for machine processing.
     *
     * <p>Outputs a JSON object with an "entries" array containing full
     * details for each entry, followed by a "totalEntries" count.</p>
     *
     * @param reader the archive reader to iterate over entries
     */
    private void printJson(final AetherPackReader reader) {
        System.out.println("{");
        System.out.println("  \"entries\": [");

        boolean first = true;
        for (final Entry entry : reader) {
            if (!first) {
                System.out.println(",");
            }
            first = false;

            System.out.printf("    {%n");
            System.out.printf("      \"id\": %d,%n", entry.getId());
            System.out.printf("      \"name\": \"%s\",%n", escapeJson(entry.getName()));
            System.out.printf("      \"mimeType\": \"%s\",%n", escapeJson(entry.getMimeType()));
            System.out.printf("      \"originalSize\": %d,%n", entry.getOriginalSize());
            System.out.printf("      \"storedSize\": %d,%n", entry.getStoredSize());
            System.out.printf("      \"chunkCount\": %d,%n", entry.getChunkCount());
            System.out.printf("      \"compressed\": %b,%n", entry.isCompressed());
            System.out.printf("      \"encrypted\": %b,%n", entry.isEncrypted());
            System.out.printf("      \"hasEcc\": %b%n", entry.hasEcc());
            System.out.print("    }");
        }

        System.out.println();
        System.out.println("  ],");
        System.out.printf("  \"totalEntries\": %d%n", reader.getEntryCount());
        System.out.println("}");
    }

    /**
     * Builds a flag string indicating entry properties.
     *
     * <p>Returns a string containing flag characters:</p>
     * <ul>
     *   <li>{@code C} - Entry is compressed</li>
     *   <li>{@code E} - Entry is encrypted</li>
     *   <li>{@code R} - Entry has Reed-Solomon error correction</li>
     * </ul>
     *
     * @param entry the entry to build flags for
     * @return the flag string, or "-" if no flags are set
     */
    private static String buildFlags(final Entry entry) {
        final StringBuilder sb = new StringBuilder();
        if (entry.isCompressed()) {
            sb.append('C');
        }
        if (entry.isEncrypted()) {
            sb.append('E');
        }
        if (entry.hasEcc()) {
            sb.append('R');
        }
        return sb.length() > 0 ? sb.toString() : "-";
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
