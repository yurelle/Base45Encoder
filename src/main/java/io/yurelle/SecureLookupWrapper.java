package io.yurelle;

import java.util.Arrays;

/**
 * This wrapper class has been created around the lookup tables, to ensure
 * the enforcement of the security checks outlined in the standard. It has
 * been exported into its own file, rather than being an innerclass of the
 * Base45 class, because parent classes have special privileges to access
 * even private fields of their innerclasses. So, this was moved out into
 * its own file, to ensure that Base45 has no direct access to these
 * lookup tables, to prevent any accidental direct references which would
 * bypass the security checks.
 *
 * See Section 6 of:
 * https://datatracker.ietf.org/doc/html/rfc9285
 */
public class SecureLookupWrapper {
    private final static char[] ALPHANUM_STANDARD = new char[] {
            // 0 - 9
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',

            // A - Z
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
            'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
            'U', 'V', 'W', 'X', 'Y', 'Z',

            // Symbols
            ' ', '$', '%', '*', '+', '-', '.', '/', ':'
    };

    private final static int[] ALPHANUM_REVERSE_LOOKUP;
    static {
        //Init Array
        ALPHANUM_REVERSE_LOOKUP = new int[256];

        //Default Value of -1
        Arrays.fill(ALPHANUM_REVERSE_LOOKUP, -1);

        //Fill in Base45 entries
        for (int x=0; x<ALPHANUM_STANDARD.length; x++) {
            char c = ALPHANUM_STANDARD[x];
            ALPHANUM_REVERSE_LOOKUP[c] = x;
        }
    }

    /**
     * Security check outlined in the standard; See: Section 6, Paragraph 1.
     *
     * If somehow the input data is manipulated, or a bug is introduced into the
     * encoder, such that the encoder attempts to output a digit outside of the
     * Base45 value space, this bounds check should catch it.
     *
     * @param lookupIndex
     * @return
     */
    public static char doLookup(final int lookupIndex, final int sourceByteLocation) {
        //The JVM should already do this bounds check, and throw an ArrayIndexOutOfBoundsException.
        //But just to be safe, and to make the failure exceptions consistent, we'll catch it ourselves
        //and throw an IllegalArgumentException.
        if (lookupIndex >= ALPHANUM_STANDARD.length || lookupIndex < 0) {
            throw new IllegalArgumentException("Encoding Failed at index '" + sourceByteLocation + "' - Invalid Encoding! Digit value '" + lookupIndex + "' outside the bounds of Base45 single-digit value space: 0 - 44.");
        }
        return ALPHANUM_STANDARD[lookupIndex];
    }

    /**
     * Security check outlined in the standard; See: Section 6, Paragraph 4.
     *
     * The lookup table returns -1 for any character which does not map to
     * the base45 character set. This function checks the return of that
     * lookup, and throws an exception if the returned value is negative.
     *
     * @return The decoded character, if & only if the char value is > 0.
     * @throws IllegalAccessException If the result of the decode lookup
     * is negative; representing a invalid character mapping.
     */
    public static int doReverseLookup(final int lookupIndex, final int sourceByteLocation) throws IllegalArgumentException {
        //The JVM should already do this bounds check, and throw an ArrayIndexOutOfBoundsException.
        //But just to be safe, and to make the failure exceptions consistent, we'll catch it ourselves
        //and throw an IllegalArgumentException.
        if (lookupIndex >= ALPHANUM_REVERSE_LOOKUP.length || lookupIndex < 0) {
            throw new IllegalArgumentException("Decoding Failed at index '" + sourceByteLocation + "' - Invalid Encoding! Digit value '" + lookupIndex + "' outside the bounds of single-byte value space: 0 - 255 (i.e. 0x00 - 0xFF).");
        }

        //Do Lookup
        final int retVal = ALPHANUM_REVERSE_LOOKUP[lookupIndex];

        //Ensure Positive & Base45 Capped
        if (retVal < 0 || retVal > 44) {
            throw new IllegalArgumentException("Decoding Failed at index '" + sourceByteLocation + "'! Unrecognized character '" + ((char) lookupIndex) + "'.");
        } else {
            return retVal;
        }
    }
}
