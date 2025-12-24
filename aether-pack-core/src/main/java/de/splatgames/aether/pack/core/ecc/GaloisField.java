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

package de.splatgames.aether.pack.core.ecc;

/**
 * Galois Field GF(2^8) arithmetic for Reed-Solomon encoding.
 *
 * <p>This class provides arithmetic operations over the finite field GF(2^8),
 * which is essential for Reed-Solomon error correction. Each element in the
 * field is a byte (0-255), and operations are performed using lookup tables
 * for efficiency.</p>
 *
 * <h2>Field Properties</h2>
 * <ul>
 *   <li><strong>Size:</strong> 256 elements (2^8)</li>
 *   <li><strong>Primitive polynomial:</strong> x^8 + x^4 + x^3 + x^2 + 1 (0x11D)</li>
 *   <li><strong>Generator:</strong> α = 2</li>
 * </ul>
 *
 * <h2>Arithmetic Rules</h2>
 * <ul>
 *   <li><strong>Addition/Subtraction:</strong> XOR operation (a ⊕ b)</li>
 *   <li><strong>Multiplication:</strong> Via log/exp tables: a × b = exp(log(a) + log(b))</li>
 *   <li><strong>Division:</strong> Via log/exp tables: a ÷ b = exp(log(a) - log(b))</li>
 * </ul>
 *
 * <h2>Primitive Polynomial</h2>
 * <p>The primitive polynomial 0x11D is widely used in standards including:</p>
 * <ul>
 *   <li>QR codes</li>
 *   <li>Data Matrix</li>
 *   <li>RAID-6</li>
 *   <li>Various storage systems</li>
 * </ul>
 *
 * <h2>Lookup Tables</h2>
 * <p>For performance, this implementation uses precomputed lookup tables:</p>
 * <ul>
 *   <li><strong>LOG table:</strong> log[x] = discrete log of x (base α)</li>
 *   <li><strong>EXP table:</strong> exp[i] = α^i (with wraparound for easy modulo)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is stateless and thread-safe. All methods are pure functions
 * that only read from immutable lookup tables.</p>
 *
 * @see ReedSolomonCodec
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
final class GaloisField {

    /**
     * Primitive polynomial for GF(2^8): x^8 + x^4 + x^3 + x^2 + 1.
     */
    private static final int PRIMITIVE_POLYNOMIAL = 0x11D;

    /**
     * Field size: 2^8 = 256.
     */
    static final int FIELD_SIZE = 256;

    /**
     * Logarithm table: log[x] = discrete log of x (base alpha).
     */
    private static final int[] LOG = new int[FIELD_SIZE];

    /**
     * Exponent table: exp[i] = alpha^i.
     */
    private static final int[] EXP = new int[FIELD_SIZE * 2];

    static {
        // Build log and exp tables
        int x = 1;
        for (int i = 0; i < FIELD_SIZE - 1; i++) {
            EXP[i] = x;
            EXP[i + FIELD_SIZE - 1] = x; // For easy modulo
            LOG[x] = i;
            x = multiply(x, 2); // x = x * alpha (alpha = 2)
        }
        LOG[0] = -1; // log(0) is undefined
    }

    /**
     * Private constructor to prevent instantiation.
     * <p>
     * This is a utility class with only static methods.
     * </p>
     */
    private GaloisField() {
        // Utility class
    }

    /**
     * Multiplies two field elements without using tables.
     * <p>
     * This method uses the Russian peasant multiplication algorithm
     * with polynomial reduction. It is used only during static initialization
     * to build the logarithm and exponent tables.
     * </p>
     *
     * @param a first field element
     * @param b second field element
     * @return a × b in GF(2^8)
     */
    private static int multiply(int a, int b) {
        int result = 0;
        while (b > 0) {
            if ((b & 1) != 0) {
                result ^= a;
            }
            a <<= 1;
            if ((a & 0x100) != 0) {
                a ^= PRIMITIVE_POLYNOMIAL;
            }
            b >>= 1;
        }
        return result;
    }

    /**
     * Adds two field elements.
     * In GF(2^8), addition is XOR.
     *
     * @param a first element
     * @param b second element
     * @return a + b
     */
    static int add(final int a, final int b) {
        return a ^ b;
    }

    /**
     * Subtracts two field elements.
     * In GF(2^8), subtraction is the same as addition (XOR).
     *
     * @param a first element
     * @param b second element
     * @return a - b
     */
    static int sub(final int a, final int b) {
        return a ^ b;
    }

    /**
     * Multiplies two field elements using lookup tables.
     *
     * @param a first element
     * @param b second element
     * @return a * b
     */
    static int mul(final int a, final int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return EXP[LOG[a] + LOG[b]];
    }

    /**
     * Divides two field elements.
     *
     * @param a dividend
     * @param b divisor (must not be 0)
     * @return a / b
     */
    static int div(final int a, final int b) {
        if (b == 0) {
            throw new ArithmeticException("Division by zero in GF(2^8)");
        }
        if (a == 0) {
            return 0;
        }
        return EXP[LOG[a] - LOG[b] + FIELD_SIZE - 1];
    }

    /**
     * Raises alpha to the given power.
     *
     * @param power the exponent
     * @return alpha^power
     */
    static int exp(final int power) {
        return EXP[power % (FIELD_SIZE - 1)];
    }

    /**
     * Returns the discrete logarithm of a field element.
     *
     * @param x the element (must not be 0)
     * @return log(x)
     */
    static int log(final int x) {
        if (x == 0) {
            throw new ArithmeticException("Logarithm of zero in GF(2^8)");
        }
        return LOG[x];
    }

    /**
     * Computes the multiplicative inverse of a field element.
     *
     * @param x the element (must not be 0)
     * @return x^(-1)
     */
    static int inverse(final int x) {
        if (x == 0) {
            throw new ArithmeticException("Inverse of zero in GF(2^8)");
        }
        return EXP[FIELD_SIZE - 1 - LOG[x]];
    }

    /**
     * Evaluates a polynomial at a given point.
     *
     * @param poly   polynomial coefficients (lowest degree first)
     * @param length number of coefficients
     * @param x      the point to evaluate at
     * @return poly(x)
     */
    static int polyEval(final int[] poly, final int length, final int x) {
        int result = 0;
        for (int i = length - 1; i >= 0; i--) {
            result = add(mul(result, x), poly[i]);
        }
        return result;
    }

}
