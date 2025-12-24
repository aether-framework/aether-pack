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

package de.splatgames.aether.pack.core.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Exception thrown when an archive requires a newer reader version.
 *
 * <p>This exception is a specialized {@link FormatException} that indicates
 * a version compatibility issue. The archive was created with a newer version
 * of the APACK library that uses features not supported by the current reader.</p>
 *
 * <h2>Version Compatibility</h2>
 * <p>The APACK file header contains a {@code readerVersionMin} field that
 * specifies the minimum reader version required to parse the archive correctly.
 * When opening an archive, the reader compares this value against its own
 * version and throws this exception if the file requires a newer version.</p>
 *
 * <h2>Version Information</h2>
 * <p>The exception provides version details for diagnostics:</p>
 * <ul>
 *   <li>{@link #getRequiredVersion()} - Minimum version required by the file</li>
 *   <li>{@link #getCurrentVersion()} - Version of the current reader</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (AetherPackReader reader = AetherPackReader.open(path)) {
 *     // Process archive...
 * } catch (UnsupportedVersionException e) {
 *     System.err.printf("Cannot read archive: requires version %d, " +
 *         "but this library is version %d%n",
 *         e.getRequiredVersion(), e.getCurrentVersion());
 *     System.err.println("Please update to the latest aether-pack library.");
 * }
 * }</pre>
 *
 * <h2>Forward Compatibility</h2>
 * <p>The APACK format is designed with forward compatibility in mind.
 * Unknown flags and fields are generally ignored, allowing older readers
 * to process newer archives that don't use incompatible features. This
 * exception is only thrown when a breaking format change is detected.</p>
 *
 * @see FormatException
 * @see ApackException
 * @see de.splatgames.aether.pack.core.format.FileHeader#compatLevel()
 * @see de.splatgames.aether.pack.core.format.FormatConstants#COMPAT_LEVEL
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class UnsupportedVersionException extends FormatException {

    /** The minimum reader version required by the file. */
    private final int requiredVersion;

    /** The version of the current reader. */
    private final int currentVersion;

    /**
     * Creates a new unsupported version exception with the specified version information.
     *
     * <p>This constructor creates a version exception that captures both the
     * version required by the file and the version of the current reader.
     * This information is stored for diagnostic purposes and can be retrieved
     * via {@link #getRequiredVersion()} and {@link #getCurrentVersion()}.</p>
     *
     * <p>The exception message follows the format:</p>
     * <pre>
     * "File requires reader version {requiredVersion}, but this reader is version {currentVersion}"
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <p>The version number represents the APACK format version, not the
     * library version. Format version changes indicate breaking changes in
     * the file structure that require updated parsing logic:</p>
     * <ul>
     *   <li><strong>Version 1</strong> - Initial format specification</li>
     *   <li>Higher versions may add new header fields, change sizes, or
     *       introduce new encoding schemes</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * if (header.readerVersionMin() > FormatConstants.CURRENT_VERSION) {
     *     throw new UnsupportedVersionException(
     *         header.readerVersionMin(),
     *         FormatConstants.CURRENT_VERSION
     *     );
     * }
     * }</pre>
     *
     * @param requiredVersion the minimum version required by the file to be
     *                        read correctly; this value comes from the file
     *                        header's {@code readerVersionMin} field; the value
     *                        indicates the lowest format version that can parse
     *                        all structures in the file; stored and retrievable
     *                        via {@link #getRequiredVersion()}
     * @param currentVersion  the version of this reader, typically obtained from
     *                        {@link de.splatgames.aether.pack.core.format.FormatConstants#COMPAT_LEVEL};
     *                        this represents the highest format version that the
     *                        current library implementation can handle; stored
     *                        and retrievable via {@link #getCurrentVersion()}
     *
     * @see #getRequiredVersion()
     * @see #getCurrentVersion()
     * @see de.splatgames.aether.pack.core.format.FileHeader#compatLevel()
     */
    public UnsupportedVersionException(final int requiredVersion, final int currentVersion) {
        super(String.format(
                "File requires reader version %d, but this reader is version %d",
                requiredVersion, currentVersion
        ));
        this.requiredVersion = requiredVersion;
        this.currentVersion = currentVersion;
    }

    /**
     * Returns the minimum version required to read the file correctly.
     *
     * <p>This method returns the version number from the file's header that
     * specifies the minimum reader version required to correctly parse and
     * process the archive. If the current reader's version is less than this
     * value, the archive may contain structures or features that the reader
     * cannot handle.</p>
     *
     * <p>The required version is determined by the writer at creation time
     * based on which format features were used. For example:</p>
     * <ul>
     *   <li>A file using only basic features might require version 1</li>
     *   <li>A file using a new compression algorithm might require a higher version</li>
     *   <li>A file using an updated header structure might require the version
     *       that introduced that structure</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * catch (UnsupportedVersionException e) {
     *     int required = e.getRequiredVersion();
     *     int current = e.getCurrentVersion();
     *     System.err.printf(
     *         "Please upgrade to aether-pack version %d.x or newer%n" +
     *         "(current library supports format version %d)%n",
     *         required, current
     *     );
     * }
     * }</pre>
     *
     * @return the minimum version required by the file; this is the value from
     *         the file header's {@code readerVersionMin} field; the value will
     *         always be greater than the value returned by {@link #getCurrentVersion()}
     *         (otherwise this exception would not have been thrown); the value
     *         is a positive integer representing the format version number
     *
     * @see #getCurrentVersion()
     * @see de.splatgames.aether.pack.core.format.FileHeader#compatLevel()
     */
    public int getRequiredVersion() {
        return this.requiredVersion;
    }

    /**
     * Returns the version of the current reader implementation.
     *
     * <p>This method returns the format version that the current library
     * implementation supports. This value typically comes from
     * {@link de.splatgames.aether.pack.core.format.FormatConstants#COMPAT_LEVEL}
     * and represents the highest format version the reader can handle.</p>
     *
     * <p>When this value is less than the value returned by
     * {@link #getRequiredVersion()}, the reader cannot safely parse the
     * archive because it may encounter unknown structures or encodings.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * catch (UnsupportedVersionException e) {
     *     int versionGap = e.getRequiredVersion() - e.getCurrentVersion();
     *     if (versionGap == 1) {
     *         System.err.println("Only one version behind - minor update needed");
     *     } else {
     *         System.err.printf("Multiple versions behind (%d) - major update needed%n",
     *             versionGap);
     *     }
     * }
     * }</pre>
     *
     * @return the version of the current reader; this is typically the value of
     *         {@link de.splatgames.aether.pack.core.format.FormatConstants#COMPAT_LEVEL};
     *         the value represents the highest format version this library can
     *         read; the value will always be less than the value returned by
     *         {@link #getRequiredVersion()} (otherwise this exception would not
     *         have been thrown); the value is a positive integer representing
     *         the format version number
     *
     * @see #getRequiredVersion()
     * @see de.splatgames.aether.pack.core.format.FormatConstants#COMPAT_LEVEL
     */
    public int getCurrentVersion() {
        return this.currentVersion;
    }

}
