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

package de.splatgames.aether.pack.core.format;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents a custom key-value attribute that can be attached to archive entries.
 *
 * <p>Attributes provide a flexible mechanism for storing arbitrary metadata with
 * entries, such as author information, timestamps, permissions, or application-specific
 * data. Each attribute consists of a UTF-8 encoded key and a typed value.</p>
 *
 * <h2>Supported Value Types</h2>
 * <p>The following value types are supported:</p>
 * <table>
 *   <caption>Attribute Value Types</caption>
 *   <tr><th>Type ID</th><th>Constant</th><th>Description</th><th>Size</th></tr>
 *   <tr><td>0</td><td>{@link FormatConstants#ATTR_TYPE_STRING}</td><td>UTF-8 String</td><td>Variable</td></tr>
 *   <tr><td>1</td><td>{@link FormatConstants#ATTR_TYPE_INT64}</td><td>64-bit signed integer</td><td>8 bytes</td></tr>
 *   <tr><td>2</td><td>{@link FormatConstants#ATTR_TYPE_FLOAT64}</td><td>64-bit floating point</td><td>8 bytes</td></tr>
 *   <tr><td>3</td><td>{@link FormatConstants#ATTR_TYPE_BOOLEAN}</td><td>Boolean</td><td>1 byte</td></tr>
 *   <tr><td>4</td><td>{@link FormatConstants#ATTR_TYPE_BYTES}</td><td>Raw bytes</td><td>Variable</td></tr>
 * </table>
 *
 * <h2>Binary Encoding</h2>
 * <p>In the binary format, attributes are encoded as:</p>
 * <pre>
 * Offset  Size    Field       Description
 * ──────────────────────────────────────────────────────────────────
 * 0x00    2       keyLength   Length of key in bytes
 * 0x02    1       valueType   Value type ID (0-4)
 * 0x03    4       valueLength Length of value in bytes
 * 0x07    N       key         UTF-8 encoded key (no null terminator)
 * 0x07+N  M       value       Raw value bytes
 * ──────────────────────────────────────────────────────────────────
 * </pre>
 *
 * <h2>Factory Methods</h2>
 * <p>Use the static factory methods for convenient attribute creation:</p>
 * <pre>{@code
 * // Create typed attributes
 * Attribute author = Attribute.ofString("author", "John Doe");
 * Attribute timestamp = Attribute.ofLong("created", System.currentTimeMillis());
 * Attribute rating = Attribute.ofDouble("rating", 4.5);
 * Attribute approved = Attribute.ofBoolean("approved", true);
 * Attribute hash = Attribute.ofBytes("sha256", hashBytes);
 * }</pre>
 *
 * <h2>Type-Safe Accessors</h2>
 * <p>Use the type-safe accessor methods to retrieve values:</p>
 * <pre>{@code
 * String authorName = author.asString();
 * long createdTime = timestamp.asLong();
 * double ratingValue = rating.asDouble();
 * boolean isApproved = approved.asBoolean();
 * byte[] hashValue = hash.asBytes();
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This record creates defensive copies of byte arrays during construction
 * and when returning values, ensuring immutability. Instances are thread-safe
 * and can be freely shared between threads.</p>
 *
 * @param key       the attribute key encoded in UTF-8; must not be {@code null}
 *                  or empty
 * @param valueType the value type ID; must be one of the
 *                  {@code ATTR_TYPE_*} constants in {@link FormatConstants}
 * @param value     the raw value bytes; interpretation depends on valueType;
 *                  never {@code null}
 *
 * @see EntryHeader#attributes()
 * @see FormatConstants#ATTR_TYPE_STRING
 * @see FormatConstants#ATTR_TYPE_INT64
 * @see FormatConstants#ATTR_TYPE_FLOAT64
 * @see FormatConstants#ATTR_TYPE_BOOLEAN
 * @see FormatConstants#ATTR_TYPE_BYTES
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public record Attribute(
        @NotNull String key,
        int valueType,
        byte @NotNull [] value
) {

    /**
     * Compact constructor that creates a defensive copy of the value bytes.
     *
     * <p>This ensures that the attribute is truly immutable and that external
     * modifications to the original byte array do not affect this record.</p>
     */
    public Attribute {
        value = value.clone();
    }

    /**
     * Returns a defensive copy of the raw value bytes.
     *
     * <p>The returned array is a copy; modifications to it will not affect
     * this attribute.</p>
     *
     * @return a copy of the value bytes; never {@code null}
     */
    @Override
    public byte @NotNull [] value() {
        return this.value.clone();
    }

    /**
     * Creates a string attribute with UTF-8 encoding.
     *
     * <p>The string value is encoded using UTF-8 and stored in the
     * attribute's value bytes.</p>
     *
     * @param key   the attribute key; must not be {@code null}
     * @param value the string value; must not be {@code null}
     * @return a new string attribute; never {@code null}
     *
     * @see #asString()
     */
    public static @NotNull Attribute ofString(final @NotNull String key, final @NotNull String value) {
        return new Attribute(key, FormatConstants.ATTR_TYPE_STRING, value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a 64-bit signed integer attribute.
     *
     * <p>The long value is stored in Little-Endian byte order.</p>
     *
     * @param key   the attribute key; must not be {@code null}
     * @param value the 64-bit signed integer value
     * @return a new integer attribute; never {@code null}
     *
     * @see #asLong()
     */
    public static @NotNull Attribute ofLong(final @NotNull String key, final long value) {
        final byte[] bytes = new byte[8];
        bytes[0] = (byte) value;
        bytes[1] = (byte) (value >> 8);
        bytes[2] = (byte) (value >> 16);
        bytes[3] = (byte) (value >> 24);
        bytes[4] = (byte) (value >> 32);
        bytes[5] = (byte) (value >> 40);
        bytes[6] = (byte) (value >> 48);
        bytes[7] = (byte) (value >> 56);
        return new Attribute(key, FormatConstants.ATTR_TYPE_INT64, bytes);
    }

    /**
     * Creates a 64-bit floating point (double) attribute.
     *
     * <p>The double value is converted to its IEEE 754 bit representation
     * and stored in Little-Endian byte order.</p>
     *
     * @param key   the attribute key; must not be {@code null}
     * @param value the double-precision floating point value
     * @return a new double attribute; never {@code null}
     *
     * @see #asDouble()
     */
    public static @NotNull Attribute ofDouble(final @NotNull String key, final double value) {
        return ofLong(key, Double.doubleToRawLongBits(value));
    }

    /**
     * Creates a boolean attribute.
     *
     * <p>The boolean is stored as a single byte: 0x00 for {@code false},
     * 0x01 for {@code true}.</p>
     *
     * @param key   the attribute key; must not be {@code null}
     * @param value the boolean value
     * @return a new boolean attribute; never {@code null}
     *
     * @see #asBoolean()
     */
    public static @NotNull Attribute ofBoolean(final @NotNull String key, final boolean value) {
        return new Attribute(key, FormatConstants.ATTR_TYPE_BOOLEAN, new byte[]{(byte) (value ? 1 : 0)});
    }

    /**
     * Creates a raw byte array attribute.
     *
     * <p>The byte array is stored as-is with no transformation. A defensive
     * copy is made during construction.</p>
     *
     * @param key   the attribute key; must not be {@code null}
     * @param value the byte array value; must not be {@code null}
     * @return a new bytes attribute; never {@code null}
     *
     * @see #asBytes()
     */
    public static @NotNull Attribute ofBytes(final @NotNull String key, final byte @NotNull [] value) {
        return new Attribute(key, FormatConstants.ATTR_TYPE_BYTES, value);
    }

    /**
     * Returns the value as a UTF-8 decoded string.
     *
     * <p>This method is only valid for attributes created with
     * {@link #ofString(String, String)} or with value type
     * {@link FormatConstants#ATTR_TYPE_STRING}.</p>
     *
     * @return the string value; never {@code null}
     * @throws IllegalStateException if this attribute's value type is not STRING
     *
     * @see #ofString(String, String)
     */
    public @NotNull String asString() {
        if (this.valueType != FormatConstants.ATTR_TYPE_STRING) {
            throw new IllegalStateException("Attribute is not a string type");
        }
        return new String(this.value, StandardCharsets.UTF_8);
    }

    /**
     * Returns the value as a 64-bit signed integer.
     *
     * <p>This method is only valid for attributes created with
     * {@link #ofLong(String, long)} or with value type
     * {@link FormatConstants#ATTR_TYPE_INT64}.</p>
     *
     * @return the long value
     * @throws IllegalStateException if this attribute's value type is not INT64
     *
     * @see #ofLong(String, long)
     */
    public long asLong() {
        if (this.valueType != FormatConstants.ATTR_TYPE_INT64) {
            throw new IllegalStateException("Attribute is not an int64 type");
        }
        return (this.value[0] & 0xFFL)
                | ((this.value[1] & 0xFFL) << 8)
                | ((this.value[2] & 0xFFL) << 16)
                | ((this.value[3] & 0xFFL) << 24)
                | ((this.value[4] & 0xFFL) << 32)
                | ((this.value[5] & 0xFFL) << 40)
                | ((this.value[6] & 0xFFL) << 48)
                | ((this.value[7] & 0xFFL) << 56);
    }

    /**
     * Returns the value as a 64-bit floating point number.
     *
     * <p>This method is only valid for attributes created with
     * {@link #ofDouble(String, double)} or with value type
     * {@link FormatConstants#ATTR_TYPE_FLOAT64}.</p>
     *
     * @return the double value
     * @throws IllegalStateException if this attribute's value type is not FLOAT64
     *
     * @see #ofDouble(String, double)
     */
    public double asDouble() {
        if (this.valueType != FormatConstants.ATTR_TYPE_FLOAT64) {
            throw new IllegalStateException("Attribute is not a float64 type");
        }
        return Double.longBitsToDouble(asLongInternal());
    }

    /**
     * Returns the value as a boolean.
     *
     * <p>This method is only valid for attributes created with
     * {@link #ofBoolean(String, boolean)} or with value type
     * {@link FormatConstants#ATTR_TYPE_BOOLEAN}. The value is considered
     * {@code true} if the first byte is non-zero.</p>
     *
     * @return the boolean value
     * @throws IllegalStateException if this attribute's value type is not BOOLEAN
     *
     * @see #ofBoolean(String, boolean)
     */
    public boolean asBoolean() {
        if (this.valueType != FormatConstants.ATTR_TYPE_BOOLEAN) {
            throw new IllegalStateException("Attribute is not a boolean type");
        }
        return this.value[0] != 0;
    }

    /**
     * Returns the value as a raw byte array.
     *
     * <p>This method is only valid for attributes created with
     * {@link #ofBytes(String, byte[])} or with value type
     * {@link FormatConstants#ATTR_TYPE_BYTES}. A defensive copy is
     * returned to preserve immutability.</p>
     *
     * @return a copy of the byte array value; never {@code null}
     * @throws IllegalStateException if this attribute's value type is not BYTES
     *
     * @see #ofBytes(String, byte[])
     */
    public byte @NotNull [] asBytes() {
        if (this.valueType != FormatConstants.ATTR_TYPE_BYTES) {
            throw new IllegalStateException("Attribute is not a bytes type");
        }
        return this.value.clone();
    }

    /**
     * Internal helper to decode the value bytes as a 64-bit integer.
     *
     * @return the decoded long value in Little-Endian byte order
     */
    private long asLongInternal() {
        return (this.value[0] & 0xFFL)
                | ((this.value[1] & 0xFFL) << 8)
                | ((this.value[2] & 0xFFL) << 16)
                | ((this.value[3] & 0xFFL) << 24)
                | ((this.value[4] & 0xFFL) << 32)
                | ((this.value[5] & 0xFFL) << 40)
                | ((this.value[6] & 0xFFL) << 48)
                | ((this.value[7] & 0xFFL) << 56);
    }

    /**
     * Compares this attribute to another object for equality.
     *
     * <p>Two attributes are equal if they have the same key, value type,
     * and the same value bytes (compared using {@link Arrays#equals(byte[], byte[])}).</p>
     *
     * @param o the object to compare with
     * @return {@code true} if the objects are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Attribute that)) return false;
        return this.valueType == that.valueType
                && this.key.equals(that.key)
                && Arrays.equals(this.value, that.value);
    }

    /**
     * Computes a hash code for this attribute.
     *
     * <p>The hash code is computed from the key, value type, and value bytes,
     * ensuring that equal attributes have equal hash codes.</p>
     *
     * @return the hash code value
     */
    @Override
    public int hashCode() {
        int result = this.key.hashCode();
        result = 31 * result + this.valueType;
        result = 31 * result + Arrays.hashCode(this.value);
        return result;
    }

    /**
     * Returns a string representation of this attribute.
     *
     * <p>The string includes the key, value type ID, and value size in bytes.
     * The actual value bytes are not included to avoid excessively long output.</p>
     *
     * @return a string representation of this attribute; never {@code null}
     */
    @Override
    public @NotNull String toString() {
        return "Attribute[key=" + this.key + ", type=" + this.valueType + ", size=" + this.value.length + "]";
    }

}
