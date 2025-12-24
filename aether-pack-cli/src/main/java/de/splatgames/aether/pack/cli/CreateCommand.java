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
import de.splatgames.aether.pack.core.AetherPackWriter;
import de.splatgames.aether.pack.core.ApackConfiguration;
import de.splatgames.aether.pack.core.entry.EntryMetadata;
import de.splatgames.aether.pack.core.format.EncryptionBlock;
import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.core.spi.CompressionProvider;
import de.splatgames.aether.pack.crypto.Argon2idKeyDerivation;
import de.splatgames.aether.pack.crypto.EncryptionRegistry;
import de.splatgames.aether.pack.crypto.KeyWrapper;
import de.splatgames.aether.pack.core.spi.EncryptionProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.crypto.SecretKey;
import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * CLI command for creating new APACK archives.
 *
 * <p>This command creates a new APACK archive from one or more input files
 * or directories. It supports optional compression, encryption, and various
 * configuration options for the archive format.</p>
 *
 * <h2>Command Syntax</h2>
 * <pre>{@code
 * apack create [OPTIONS] <output> <input>...
 * apack c [OPTIONS] <output> <input>...
 * }</pre>
 *
 * <h2>Options</h2>
 * <table>
 *   <caption>Create Command Options</caption>
 *   <tr><th>Option</th><th>Description</th><th>Default</th></tr>
 *   <tr><td>{@code -c, --compression}</td><td>Compression algorithm (zstd, lz4, none)</td><td>zstd</td></tr>
 *   <tr><td>{@code -l, --level}</td><td>Compression level</td><td>Algorithm default</td></tr>
 *   <tr><td>{@code -e, --encrypt}</td><td>Encryption algorithm (aes-256-gcm, chacha20-poly1305)</td><td>None</td></tr>
 *   <tr><td>{@code -p, --password}</td><td>Encryption password</td><td>Prompt if -e used</td></tr>
 *   <tr><td>{@code --chunk-size}</td><td>Chunk size in KB</td><td>256</td></tr>
 *   <tr><td>{@code -r, --recursive}</td><td>Recursively add directories</td><td>true</td></tr>
 *   <tr><td>{@code -v, --verbose}</td><td>Verbose output</td><td>false</td></tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * # Create archive with default ZSTD compression
 * apack create archive.apack file1.txt file2.txt
 *
 * # Create archive from directory
 * apack create archive.apack ./mydir/
 *
 * # Create with specific compression level
 * apack create -c zstd -l 9 archive.apack ./data/
 *
 * # Create with LZ4 for speed
 * apack create -c lz4 archive.apack ./data/
 *
 * # Create encrypted archive (will prompt for password)
 * apack create -e aes-256-gcm archive.apack ./sensitive/
 *
 * # Create with both compression and encryption
 * apack create -c zstd -l 6 -e chacha20-poly1305 archive.apack ./data/
 *
 * # Create with larger chunks (better compression for large files)
 * apack create --chunk-size 1024 archive.apack ./largefile.bin
 * }</pre>
 *
 * <h2>Output</h2>
 * <p>On success, prints a summary showing the number of files added,
 * original total size, archive size, and compression ratio.</p>
 *
 * @see ExtractCommand
 * @see ApackCli
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Command(
        name = "create",
        aliases = {"c"},
        description = "Create a new APACK archive"
)
public final class CreateCommand implements Callable<Integer> {

    /**
     * Creates a new CreateCommand instance.
     * This constructor is called by picocli during command parsing.
     */
    public CreateCommand() {
        // Default constructor for picocli
    }

    /** The output archive file path. */
    @Parameters(index = "0", description = "Output archive file")
    Path outputFile;

    /** List of input files and directories to add to the archive. */
    @Parameters(index = "1..*", description = "Files and directories to add")
    List<Path> inputPaths;

    /** Compression algorithm identifier (zstd, lz4, or none). */
    @Option(names = {"-c", "--compression"}, description = "Compression algorithm (zstd, lz4, none)", defaultValue = "zstd")
    String compression;

    /** Compression level (algorithm-specific). */
    @Option(names = {"-l", "--level"}, description = "Compression level")
    Integer compressionLevel;

    /** Encryption algorithm identifier (aes-256-gcm or chacha20-poly1305). */
    @Option(names = {"-e", "--encrypt"}, description = "Encryption algorithm (aes-256-gcm, chacha20-poly1305)")
    String encryption;

    /** Encryption password (prompted interactively if not specified). */
    @Option(names = {"-p", "--password"}, description = "Encryption password (will prompt if not specified)", interactive = true)
    char[] password;

    /** Chunk size in kilobytes for splitting large entries. */
    @Option(names = {"--chunk-size"}, description = "Chunk size in KB (default: 256)", defaultValue = "256")
    int chunkSizeKb;

    /** Whether to recursively add directory contents. */
    @Option(names = {"-r", "--recursive"}, description = "Recursively add directories", defaultValue = "true")
    boolean recursive;

    /** Whether to print verbose output during archive creation. */
    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    boolean verbose;

    /**
     * Executes the create command.
     *
     * <p>This method performs the following steps:</p>
     * <ol>
     *   <li>Validates input parameters</li>
     *   <li>Resolves compression and encryption providers</li>
     *   <li>If encryption is requested, prompts for password and derives keys</li>
     *   <li>Creates the archive and adds all input files</li>
     *   <li>Prints a summary of the operation</li>
     * </ol>
     *
     * @return exit code: 0 for success, 1 for error
     * @throws Exception if archive creation fails due to I/O errors,
     *         invalid parameters, or cryptographic errors
     */
    @Override
    public Integer call() throws Exception {
        if (this.inputPaths == null || this.inputPaths.isEmpty()) {
            System.err.println("Error: No input files specified");
            return 1;
        }

        // Resolve compression
        CompressionProvider compressionProvider = null;
        int compLevel = 0;
        if (this.compression != null && !this.compression.equalsIgnoreCase("none")) {
            compressionProvider = CompressionRegistry.get(this.compression)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown compression: " + this.compression));
            compLevel = this.compressionLevel != null
                    ? this.compressionLevel
                    : compressionProvider.getDefaultLevel();
        }

        // Resolve encryption
        EncryptionProvider encryptionProvider = null;
        SecretKey encryptionKey = null;
        EncryptionBlock encryptionBlock = null;

        if (this.encryption != null) {
            encryptionProvider = EncryptionRegistry.get(this.encryption)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown encryption: " + this.encryption));

            // Get password
            char[] pwd = this.password;
            if (pwd == null || pwd.length == 0) {
                pwd = readPassword("Enter password: ");
                final char[] confirm = readPassword("Confirm password: ");
                if (!Arrays.equals(pwd, confirm)) {
                    System.err.println("Error: Passwords do not match");
                    Arrays.fill(pwd, '\0');
                    Arrays.fill(confirm, '\0');
                    return 1;
                }
                Arrays.fill(confirm, '\0');
            }

            // Derive key and wrap content key
            final var kdf = new Argon2idKeyDerivation();
            final byte[] salt = kdf.generateSalt();
            encryptionKey = encryptionProvider.generateKey();

            try {
                final byte[] wrappedKey = KeyWrapper.wrapWithPassword(encryptionKey, pwd, salt, kdf);

                // Build EncryptionBlock with KDF parameters and wrapped key
                encryptionBlock = EncryptionBlock.builder()
                        .kdfAlgorithmId(FormatConstants.KDF_ARGON2ID)
                        .cipherAlgorithmId(encryptionProvider.getNumericId())
                        .kdfIterations(kdf.getIterations())
                        .kdfMemory(kdf.getMemoryKiB())
                        .kdfParallelism(kdf.getParallelism())
                        .salt(salt)
                        .wrappedKey(wrappedKey)
                        .wrappedKeyTag(new byte[0]) // AES Key Wrap has ICV embedded
                        .build();
            } finally {
                Arrays.fill(pwd, '\0');
            }
        }

        // Build configuration
        final var configBuilder = ApackConfiguration.builder()
                .chunkSize(this.chunkSizeKb * 1024)
                .enableRandomAccess(true);

        if (compressionProvider != null) {
            configBuilder.compression(compressionProvider, compLevel);
        }

        if (encryptionProvider != null && encryptionKey != null && encryptionBlock != null) {
            configBuilder.encryption(encryptionProvider, encryptionKey, encryptionBlock);
        }

        final ApackConfiguration config = configBuilder.build();

        // Create archive
        long totalFiles = 0;
        long totalBytes = 0;

        try (AetherPackWriter writer = AetherPackWriter.create(this.outputFile, config)) {
            for (final Path inputPath : this.inputPaths) {
                if (Files.isDirectory(inputPath)) {
                    final int maxDepth = this.recursive ? Integer.MAX_VALUE : 1;
                    try (Stream<Path> files = Files.walk(inputPath, maxDepth)) {
                        for (final Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                            addFile(writer, inputPath, file, compressionProvider, compLevel);
                            totalFiles++;
                            totalBytes += Files.size(file);
                        }
                    }
                } else if (Files.isRegularFile(inputPath)) {
                    addFile(writer, inputPath.getParent(), inputPath, compressionProvider, compLevel);
                    totalFiles++;
                    totalBytes += Files.size(inputPath);
                } else {
                    System.err.println("Warning: Skipping " + inputPath + " (not a file or directory)");
                }
            }
        }

        // Output summary
        final long archiveSize = Files.size(this.outputFile);
        System.out.printf("Created %s%n", this.outputFile);
        System.out.printf("  Files: %d%n", totalFiles);
        System.out.printf("  Original size: %s%n", formatSize(totalBytes));
        System.out.printf("  Archive size: %s%n", formatSize(archiveSize));
        if (totalBytes > 0) {
            System.out.printf("  Ratio: %.1f%%%n", (archiveSize * 100.0) / totalBytes);
        }

        return 0;
    }

    /**
     * Adds a single file to the archive.
     *
     * <p>Computes the entry name as a relative path from the base directory,
     * detects the MIME type, and writes the file content to the archive.</p>
     *
     * @param writer           the archive writer; must not be {@code null}
     * @param basePath         the base path for computing relative entry names;
     *                         may be {@code null} for single files
     * @param file             the file to add; must exist and be readable
     * @param compression      the compression provider; may be {@code null} for no compression
     * @param compressionLevel the compression level to use
     * @throws IOException if the file cannot be read or written to the archive
     */
    private void addFile(
            final AetherPackWriter writer,
            final Path basePath,
            final Path file,
            final CompressionProvider compression,
            final int compressionLevel) throws IOException {

        // Compute entry name (relative path)
        final String entryName = basePath != null
                ? basePath.relativize(file).toString().replace('\\', '/')
                : file.getFileName().toString();

        if (this.verbose) {
            System.out.println("Adding: " + entryName);
        }

        // Build metadata
        final var metadataBuilder = EntryMetadata.builder()
                .name(entryName);

        // Add MIME type based on file extension
        final String mimeType = guessMimeType(file);
        if (mimeType != null) {
            metadataBuilder.mimeType(mimeType);
        }

        // Set compression
        if (compression != null) {
            metadataBuilder.compressionId(compression.getNumericId());
        }

        writer.addEntry(metadataBuilder.build(), Files.newInputStream(file));
    }

    /**
     * Reads a password from the console or standard input.
     *
     * <p>Uses the system console for secure password reading if available.
     * Falls back to standard input for non-console environments (e.g., IDEs).</p>
     *
     * @param prompt the prompt to display to the user
     * @return the password as a character array; may be empty if reading fails
     */
    private static char[] readPassword(final String prompt) {
        final Console console = System.console();
        if (console != null) {
            return console.readPassword(prompt);
        }
        // Fallback for IDE/non-console environments
        System.out.print(prompt);
        try {
            final String line = new java.util.Scanner(System.in).nextLine();
            return line.toCharArray();
        } catch (final Exception e) {
            return new char[0];
        }
    }

    /**
     * Attempts to determine the MIME type of a file.
     *
     * <p>Uses the system's file type detection mechanism, which typically
     * relies on file extensions.</p>
     *
     * @param file the file to examine
     * @return the detected MIME type, or {@code null} if detection fails
     */
    private static String guessMimeType(final Path file) {
        try {
            return Files.probeContentType(file);
        } catch (final IOException e) {
            return null;
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
