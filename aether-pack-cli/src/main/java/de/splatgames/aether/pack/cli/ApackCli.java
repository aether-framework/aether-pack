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

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Main entry point for the APACK command-line interface.
 *
 * <p>This class provides the root command for the APACK CLI tool, which allows
 * users to create, extract, list, verify, and inspect APACK archives from
 * the command line. It uses the Picocli framework for argument parsing and
 * command dispatching.</p>
 *
 * <h2>Available Commands</h2>
 * <table>
 *   <caption>CLI Commands</caption>
 *   <tr><th>Command</th><th>Aliases</th><th>Description</th></tr>
 *   <tr><td>{@code create}</td><td>{@code c}</td><td>Create a new APACK archive</td></tr>
 *   <tr><td>{@code extract}</td><td>{@code x}</td><td>Extract files from an archive</td></tr>
 *   <tr><td>{@code list}</td><td>{@code l}, {@code ls}</td><td>List archive contents</td></tr>
 *   <tr><td>{@code info}</td><td>{@code i}</td><td>Display archive information</td></tr>
 *   <tr><td>{@code verify}</td><td>{@code v}</td><td>Verify archive integrity</td></tr>
 *   <tr><td>{@code help}</td><td>-</td><td>Show help for a command</td></tr>
 * </table>
 *
 * <h2>Global Options</h2>
 * <ul>
 *   <li>{@code -v, --verbose} - Enable verbose output with detailed error messages</li>
 *   <li>{@code -h, --help} - Show help message</li>
 *   <li>{@code -V, --version} - Show version information</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * # Show help
 * apack --help
 *
 * # Create archive with ZSTD compression
 * apack create -c zstd archive.apack files/
 *
 * # Create encrypted archive
 * apack create -c zstd -e aes-256-gcm archive.apack files/
 *
 * # Extract archive
 * apack extract archive.apack -o output/
 *
 * # List contents (long format)
 * apack list -l archive.apack
 *
 * # Verify integrity
 * apack verify archive.apack
 * }</pre>
 *
 * <h2>Exit Codes</h2>
 * <ul>
 *   <li>{@code 0} - Success</li>
 *   <li>{@code 1} - Error (invalid arguments, operation failed)</li>
 *   <li>{@code 2} - Archive error (corruption, invalid format)</li>
 * </ul>
 *
 * @see CreateCommand
 * @see ExtractCommand
 * @see ListCommand
 * @see InfoCommand
 * @see VerifyCommand
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@Command(
        name = "apack",
        description = "Aether Pack file format tool",
        version = "apack 0.1.0",
        mixinStandardHelpOptions = true,
        subcommands = {
                CreateCommand.class,
                ExtractCommand.class,
                ListCommand.class,
                InfoCommand.class,
                VerifyCommand.class,
                CommandLine.HelpCommand.class
        }
)
public final class ApackCli implements Runnable {

    /**
     * Creates a new ApackCli instance.
     *
     * <p>This constructor is called by picocli during command initialization.
     * The instance is created when the main method starts the command-line
     * processing.</p>
     */
    public ApackCli() {
        // Default constructor for picocli
    }

    /**
     * Flag to enable verbose output mode.
     *
     * <p>When enabled, detailed error messages including stack traces
     * are printed to standard error on failures.</p>
     */
    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;

    /**
     * Main entry point for the APACK command-line tool.
     *
     * <p>This method initializes the Picocli command line parser, registers
     * the custom exception handler, executes the command, and exits with
     * the appropriate exit code.</p>
     *
     * @param args the command-line arguments; typically passed from the
     *             JVM startup; must not be {@code null}
     */
    public static void main(final String[] args) {
        final int exitCode = new CommandLine(new ApackCli())
                .setExecutionExceptionHandler(new ExceptionHandler())
                .execute(args);
        System.exit(exitCode);
    }

    /**
     * Executes the root command when no subcommand is specified.
     *
     * <p>When the user runs {@code apack} without any subcommand, this method
     * prints the usage help message to standard output.</p>
     */
    @Override
    public void run() {
        // If no subcommand is specified, print usage
        CommandLine.usage(this, System.out);
    }

    /**
     * Custom exception handler for user-friendly error messages.
     *
     * <p>This handler intercepts exceptions thrown during command execution
     * and formats them for display to the user. In verbose mode, the full
     * stack trace is printed; otherwise, only the error message is shown.</p>
     *
     * <p>All errors result in exit code 1.</p>
     */
    private static final class ExceptionHandler implements CommandLine.IExecutionExceptionHandler {

        /**
         * Handles an exception thrown during command execution.
         *
         * <p>Prints the error message to standard error using the configured
         * color scheme. If verbose mode is enabled, the full stack trace
         * is also printed.</p>
         *
         * @param ex          the exception that was thrown; must not be {@code null}
         * @param commandLine the command line object for accessing configuration;
         *                    must not be {@code null}
         * @param parseResult the result of parsing the command line, used to
         *                    check if verbose mode was requested; must not be {@code null}
         * @return the exit code (always 1 for errors)
         */
        @Override
        public int handleExecutionException(
                final Exception ex,
                final CommandLine commandLine,
                final CommandLine.ParseResult parseResult) {

            commandLine.getErr().println(commandLine.getColorScheme().errorText("Error: " + ex.getMessage()));

            if (parseResult.hasMatchedOption("--verbose") ||
                    parseResult.hasMatchedOption("-v")) {
                ex.printStackTrace(commandLine.getErr());
            }

            return 1;
        }

    }

}
