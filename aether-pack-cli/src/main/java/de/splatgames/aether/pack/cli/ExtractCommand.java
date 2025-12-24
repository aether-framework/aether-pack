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
import de.splatgames.aether.pack.core.format.EncryptionBlock;
import de.splatgames.aether.pack.core.format.FormatConstants;
import de.splatgames.aether.pack.core.io.ChunkProcessor;
import de.splatgames.aether.pack.core.spi.CompressionProvider;
import de.splatgames.aether.pack.core.spi.EncryptionProvider;
import de.splatgames.aether.pack.crypto.Argon2idKeyDerivation;
import de.splatgames.aether.pack.crypto.EncryptionRegistry;
import de.splatgames.aether.pack.crypto.KeyDerivation;
import de.splatgames.aether.pack.crypto.KeyWrapper;
import de.splatgames.aether.pack.crypto.Pbkdf2KeyDerivation;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.crypto.SecretKey;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * CLI command for extracting files from APACK archives.
 *
 * <p>This command extracts all files from an APACK archive to a specified
 * output directory. It supports password-protected archives, progress output,
 * and dry-run mode for previewing extraction.</p>
 *
 * <h2>Command Syntax</h2>
 * <pre>{@code
 * apack extract [OPTIONS] <archive>
 * apack x [OPTIONS] <archive>
 * }</pre>
 *
 * <h2>Options</h2>
 * <table>
 *   <caption>Extract Command Options</caption>
 *   <tr><th>Option</th><th>Description</th><th>Default</th></tr>
 *   <tr><td>{@code -o, --output}</td><td>Output directory</td><td>Current directory</td></tr>
 *   <tr><td>{@code -p, --password}</td><td>Decryption password</td><td>Prompt if needed</td></tr>
 *   <tr><td>{@code --overwrite}</td><td>Overwrite existing files</td><td>false</td></tr>
 *   <tr><td>{@code -v, --verbose}</td><td>Verbose output</td><td>false</td></tr>
 *   <tr><td>{@code --dry-run}</td><td>Preview without extracting</td><td>false</td></tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
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
 *
 * # Extract encrypted archive
 * apack extract -p archive.apack -o ./output/
 *
 * # Overwrite existing files
 * apack extract --overwrite archive.apack -o ./output/
 * }</pre>
 *
 * <h2>Output</h2>
 * <p>On success, prints a summary showing the number of files extracted
 * and total bytes written.</p>
 *
 * @see CreateCommand
 * @see ApackCli
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Command(
        name = "extract",
        aliases = {"x"},
        description = "Extract files from an APACK archive"
)
public final class ExtractCommand implements Callable<Integer> {

    /**
     * Creates a new ExtractCommand instance.
     * This constructor is called by picocli during command parsing.
     */
    public ExtractCommand() {
        // Default constructor for picocli
    }

    /** The archive file to extract. */
    @Parameters(index = "0", description = "Archive file to extract")
    Path archiveFile;

    /** The output directory for extracted files. */
    @Option(names = {"-o", "--output"}, description = "Output directory (default: current directory)")
    Path outputDir;

    /** Decryption password for encrypted archives. */
    @Option(names = {"-p", "--password"}, description = "Decryption password (will prompt if needed)", interactive = true)
    char[] password;

    /** Whether to overwrite existing files during extraction. */
    @Option(names = {"--overwrite"}, description = "Overwrite existing files", defaultValue = "false")
    boolean overwrite;

    /** Whether to print verbose output during extraction. */
    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    boolean verbose;

    /** Whether to only preview extraction without writing files. */
    @Option(names = {"--dry-run"}, description = "Show what would be extracted without extracting")
    boolean dryRun;

    /**
     * Executes the extract command.
     *
     * <p>This method performs the following steps:</p>
     * <ol>
     *   <li>Validates the archive file exists</li>
     *   <li>Creates the output directory if needed</li>
     *   <li>Detects compression from archive entries</li>
     *   <li>Prompts for password if archive is encrypted</li>
     *   <li>Extracts all entries to the output directory</li>
     *   <li>Prints a summary of the operation</li>
     * </ol>
     *
     * @return exit code: 0 for success, 1 for error
     * @throws Exception if extraction fails
     */
    @Override
    public Integer call() throws Exception {
        if (!Files.exists(this.archiveFile)) {
            System.err.println("Error: Archive not found: " + this.archiveFile);
            return 1;
        }

        // Determine output directory
        final Path outDir = this.outputDir != null ? this.outputDir : Path.of(".");
        if (!this.dryRun) {
            Files.createDirectories(outDir);
        }

        long extractedFiles = 0;
        long extractedBytes = 0;

        // First pass: detect compression and encryption from archive
        CompressionProvider compressionProvider = null;
        EncryptionProvider encryptionProvider = null;
        SecretKey decryptionKey = null;
        EncryptionBlock encryptionBlock = null;

        try (AetherPackReader scanReader = AetherPackReader.open(this.archiveFile)) {
            if (this.verbose) {
                System.out.println("File header encrypted flag: " + scanReader.getFileHeader().isEncrypted());
            }

            // Detect compression
            for (final Entry entry : scanReader) {
                if (entry.isCompressed() && entry.getCompressionId() > 0) {
                    compressionProvider = CompressionRegistry.getById(entry.getCompressionId())
                            .orElse(null);
                    if (compressionProvider != null) {
                        if (this.verbose) {
                            System.out.println("Detected compression: " + compressionProvider.getId());
                        }
                        break;
                    }
                }
            }

            // Check for encryption
            if (scanReader.getFileHeader().isEncrypted()) {
                encryptionBlock = scanReader.getEncryptionBlock();
                if (this.verbose) {
                    System.out.println("Encryption block present: " + (encryptionBlock != null));
                }
                if (encryptionBlock == null) {
                    System.err.println("Error: Archive is encrypted but encryption metadata is missing.");
                    System.err.println("This archive may have been created before encryption block support was added.");
                    System.err.println("Such archives cannot be decrypted with a password - the original key is required.");
                    return 1;
                }

                // Get password
                char[] pwd = this.password;
                if (pwd == null || pwd.length == 0) {
                    pwd = readPassword("Enter password: ");
                    if (pwd == null || pwd.length == 0) {
                        System.err.println("Error: Password required for encrypted archive");
                        return 1;
                    }
                }

                try {
                    // Create final reference for lambda
                    final EncryptionBlock block = encryptionBlock;

                    // Resolve encryption provider
                    encryptionProvider = EncryptionRegistry.getById(block.cipherAlgorithmId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Unknown encryption algorithm: " + block.cipherAlgorithmId()));

                    // Create KDF based on algorithm ID
                    final KeyDerivation kdf;
                    if (block.kdfAlgorithmId() == FormatConstants.KDF_ARGON2ID) {
                        // Constructor: memoryKiB, iterations, parallelism, saltLength
                        kdf = new Argon2idKeyDerivation(
                                block.kdfMemory(),
                                block.kdfIterations(),
                                block.kdfParallelism(),
                                block.salt().length
                        );
                    } else if (block.kdfAlgorithmId() == FormatConstants.KDF_PBKDF2_SHA256) {
                        // Constructor: iterations, saltLength
                        kdf = new Pbkdf2KeyDerivation(
                                block.kdfIterations(),
                                block.salt().length
                        );
                    } else {
                        System.err.println("Error: Unknown KDF algorithm: " + block.kdfAlgorithmId());
                        return 1;
                    }

                    // Get wrapped key (may include tag appended)
                    byte[] wrappedKeyData = block.wrappedKey();
                    final byte[] wrappedKeyTag = block.wrappedKeyTag();
                    if (wrappedKeyTag.length > 0) {
                        // Concatenate wrappedKey and wrappedKeyTag for unwrapping
                        final byte[] combined = new byte[wrappedKeyData.length + wrappedKeyTag.length];
                        System.arraycopy(wrappedKeyData, 0, combined, 0, wrappedKeyData.length);
                        System.arraycopy(wrappedKeyTag, 0, combined, wrappedKeyData.length, wrappedKeyTag.length);
                        wrappedKeyData = combined;
                    }

                    // Unwrap the content encryption key
                    decryptionKey = KeyWrapper.unwrapWithPassword(
                            wrappedKeyData,
                            pwd,
                            block.salt(),
                            kdf,
                            "AES"
                    );

                    if (this.verbose) {
                        System.out.println("Decryption key derived successfully");
                    }
                } catch (final GeneralSecurityException e) {
                    System.err.println("Error: Failed to derive decryption key (wrong password?)");
                    if (this.verbose) {
                        e.printStackTrace();
                    }
                    return 1;
                } finally {
                    Arrays.fill(pwd, '\0');
                }
            }
        }

        // Build chunk processor with compression and/or encryption
        final ChunkProcessor.Builder processorBuilder = ChunkProcessor.builder();
        if (compressionProvider != null) {
            processorBuilder.compression(compressionProvider);
        }
        if (encryptionProvider != null && decryptionKey != null) {
            processorBuilder.encryption(encryptionProvider, decryptionKey);
            if (this.verbose) {
                System.out.println("ChunkProcessor configured with encryption: " + encryptionProvider.getId());
            }
        } else if (this.verbose) {
            System.out.println("ChunkProcessor configured without encryption");
        }
        final ChunkProcessor chunkProcessor = processorBuilder.build();

        try (AetherPackReader reader = AetherPackReader.open(this.archiveFile, chunkProcessor)) {

            for (final Entry entry : reader) {
                final Path targetPath = outDir.resolve(entry.getName());

                if (this.verbose || this.dryRun) {
                    System.out.printf("%s %s (%s)%n",
                            this.dryRun ? "Would extract:" : "Extracting:",
                            entry.getName(),
                            formatSize(entry.getOriginalSize()));
                }

                if (!this.dryRun) {
                    // Create parent directories
                    final Path parent = targetPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    // Check for existing file
                    if (Files.exists(targetPath) && !this.overwrite) {
                        System.err.println("Warning: Skipping existing file: " + targetPath);
                        continue;
                    }

                    // Extract file
                    try (InputStream input = reader.getInputStream(entry);
                         OutputStream output = Files.newOutputStream(targetPath)) {

                        final byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                    }

                    extractedBytes += entry.getOriginalSize();
                }

                extractedFiles++;
            }
        }

        // Output summary
        if (this.dryRun) {
            System.out.printf("%nWould extract %d file(s), %s%n",
                    extractedFiles, formatSize(extractedBytes));
        } else {
            System.out.printf("%nExtracted %d file(s), %s%n",
                    extractedFiles, formatSize(extractedBytes));
        }

        return 0;
    }

    /**
     * Reads a password from the console or standard input.
     *
     * <p>Uses the system console for secure password reading if available.
     * Falls back to standard input for non-console environments.</p>
     *
     * @param prompt the prompt to display to the user
     * @return the password as a character array; may be empty if reading fails
     */
    private static char[] readPassword(final String prompt) {
        final Console console = System.console();
        if (console != null) {
            return console.readPassword(prompt);
        }
        System.out.print(prompt);
        try {
            final String line = new java.util.Scanner(System.in).nextLine();
            return line.toCharArray();
        } catch (final Exception e) {
            return new char[0];
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
