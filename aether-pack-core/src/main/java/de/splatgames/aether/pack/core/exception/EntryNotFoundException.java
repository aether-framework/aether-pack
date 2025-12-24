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
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when a requested entry is not found in an archive.
 *
 * <p>This exception indicates that a lookup for a specific entry by
 * name or ID did not find a matching entry in the archive. This can
 * occur when using {@code requireEntry()} methods or similar APIs
 * that expect the entry to exist.</p>
 *
 * <h2>Lookup Methods</h2>
 * <p>Entries can be looked up in two ways:</p>
 * <ul>
 *   <li><strong>By name</strong> - Human-readable path or filename</li>
 *   <li><strong>By ID</strong> - Numeric identifier assigned during creation</li>
 * </ul>
 *
 * <h2>Diagnostic Information</h2>
 * <p>The exception provides the search criteria that failed:</p>
 * <ul>
 *   <li>{@link #getEntryName()} - Returns the name if searched by name</li>
 *   <li>{@link #getEntryId()} - Returns the ID if searched by ID</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (AetherPackReader reader = AetherPackReader.open(path)) {
 *     // Safe lookup with Optional
 *     Optional<Entry> entry = reader.getEntry("config.json");
 *     if (entry.isEmpty()) {
 *         System.out.println("config.json not found, using defaults");
 *     }
 *
 *     // Or throwing lookup
 *     try {
 *         Entry required = reader.requireEntry("manifest.json");
 *     } catch (EntryNotFoundException e) {
 *         System.err.println("Missing required entry: " + e.getEntryName());
 *     }
 * }
 * }</pre>
 *
 * <h2>Prevention</h2>
 * <p>To avoid this exception, use the non-throwing lookup methods that
 * return {@link java.util.Optional} and handle the absent case explicitly.</p>
 *
 * @see ApackException
 * @see de.splatgames.aether.pack.core.entry.Entry
 * @see de.splatgames.aether.pack.core.AetherPackReader
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public class EntryNotFoundException extends ApackException {

    /** The name of the entry that was not found, or null if looked up by ID. */
    private final @Nullable String entryName;

    /** The ID of the entry that was not found, or null if looked up by name. */
    private final @Nullable Long entryId;

    /**
     * Creates a new exception for a missing entry looked up by name.
     *
     * <p>This constructor creates an entry-not-found exception when a lookup
     * by entry name fails. The entry name is stored and can be retrieved via
     * {@link #getEntryName()} for diagnostic purposes. The {@link #getEntryId()}
     * method will return {@code null} when this constructor is used.</p>
     *
     * <p>The exception message follows the format:</p>
     * <pre>
     * "Entry not found: {entryName}"
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <p>Entry names in APACK archives are UTF-8 strings that typically
     * represent file paths or resource identifiers. Names are case-sensitive
     * and must match exactly for a lookup to succeed.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * public Entry requireEntry(String name) throws EntryNotFoundException {
     *     Entry entry = findEntry(name);
     *     if (entry == null) {
     *         throw new EntryNotFoundException(name);
     *     }
     *     return entry;
     * }
     * }</pre>
     *
     * @param entryName the name of the entry that was not found in the archive;
     *                  must not be {@code null}; this is the exact name string
     *                  that was used in the lookup operation; the name is stored
     *                  and can be retrieved via {@link #getEntryName()}; the
     *                  exception message will include this name for diagnostic
     *                  purposes
     *
     * @see #EntryNotFoundException(long)
     * @see #getEntryName()
     */
    public EntryNotFoundException(final @NotNull String entryName) {
        super("Entry not found: " + entryName);
        this.entryName = entryName;
        this.entryId = null;
    }

    /**
     * Creates a new exception for a missing entry looked up by numeric ID.
     *
     * <p>This constructor creates an entry-not-found exception when a lookup
     * by entry ID fails. The entry ID is stored and can be retrieved via
     * {@link #getEntryId()} for diagnostic purposes. The {@link #getEntryName()}
     * method will return {@code null} when this constructor is used.</p>
     *
     * <p>The exception message follows the format:</p>
     * <pre>
     * "Entry not found with ID: {entryId}"
     * </pre>
     *
     * <p><strong>\:</strong></p>
     * <p>Entry IDs are numeric identifiers assigned to entries during archive
     * creation. They provide an alternative to name-based lookup and are
     * particularly useful for:</p>
     * <ul>
     *   <li>Internal references between entries</li>
     *   <li>Compact storage when names are not needed</li>
     *   <li>Fast indexed lookup in random-access mode</li>
     * </ul>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * public Entry requireEntry(long id) throws EntryNotFoundException {
     *     if (id < 0 || id >= entries.length) {
     *         throw new EntryNotFoundException(id);
     *     }
     *     return entries[(int) id];
     * }
     * }</pre>
     *
     * @param entryId the numeric ID of the entry that was not found in the
     *                archive; this is the exact ID value that was used in
     *                the lookup operation; the ID is stored as a boxed Long
     *                and can be retrieved via {@link #getEntryId()}; the
     *                exception message will include this ID for diagnostic
     *                purposes
     *
     * @see #EntryNotFoundException(String)
     * @see #getEntryId()
     */
    public EntryNotFoundException(final long entryId) {
        super("Entry not found with ID: " + entryId);
        this.entryName = null;
        this.entryId = entryId;
    }

    /**
     * Returns the name of the entry that was not found, if available.
     *
     * <p>This method returns the entry name when the exception was created
     * using the {@link #EntryNotFoundException(String)} constructor. If the
     * exception was created using the ID-based constructor, this method
     * returns {@code null}.</p>
     *
     * <p>Use this method to provide feedback to users about which entry
     * they requested that could not be found, or to implement fallback
     * logic for missing entries.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * catch (EntryNotFoundException e) {
     *     String name = e.getEntryName();
     *     if (name != null) {
     *         System.err.println("Could not find entry: " + name);
     *         suggestSimilarEntries(name);
     *     } else {
     *         System.err.println("Could not find entry with ID: " + e.getEntryId());
     *     }
     * }
     * }</pre>
     *
     * @return the name of the entry that was not found, as passed to the
     *         {@link #EntryNotFoundException(String)} constructor; returns
     *         {@code null} if this exception was created using the ID-based
     *         constructor {@link #EntryNotFoundException(long)}; when non-null,
     *         this is the exact name string that was used in the failed lookup
     *         operation
     *
     * @see #getEntryId()
     * @see #EntryNotFoundException(String)
     */
    public @Nullable String getEntryName() {
        return this.entryName;
    }

    /**
     * Returns the ID of the entry that was not found, if available.
     *
     * <p>This method returns the entry ID when the exception was created
     * using the {@link #EntryNotFoundException(long)} constructor. If the
     * exception was created using the name-based constructor, this method
     * returns {@code null}.</p>
     *
     * <p>The ID is returned as a boxed {@link Long} rather than a primitive
     * {@code long} to allow {@code null} to indicate that the lookup was
     * performed by name rather than by ID.</p>
     *
     * <p><strong>\:</strong></p>
     * <pre>{@code
     * catch (EntryNotFoundException e) {
     *     Long id = e.getEntryId();
     *     if (id != null) {
     *         System.err.printf("Entry ID %d does not exist%n", id);
     *         System.err.printf("Valid IDs range from 0 to %d%n", maxEntryId);
     *     } else {
     *         System.err.println("Entry not found: " + e.getEntryName());
     *     }
     * }
     * }</pre>
     *
     * @return the ID of the entry that was not found, as passed to the
     *         {@link #EntryNotFoundException(long)} constructor; returns
     *         {@code null} if this exception was created using the name-based
     *         constructor {@link #EntryNotFoundException(String)}; when non-null,
     *         this is the exact ID value that was used in the failed lookup
     *         operation; the boxed type allows distinguishing between "searched
     *         by ID 0" and "searched by name"
     *
     * @see #getEntryName()
     * @see #EntryNotFoundException(long)
     */
    public @Nullable Long getEntryId() {
        return this.entryId;
    }

}
