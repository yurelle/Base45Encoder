package io.yurelle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Java implementation of the Base45 Standard outlined here:
 * https://datatracker.ietf.org/doc/html/rfc9285
 *
 * The corresponding Unit Test implements all 4 examples
 * provided in the spec, along with several others.
 */
public class Base45 {
    public final static char[] ALPHANUM_STANDARD = new char[] {
            // 0 - 9
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',

            // A - Z
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
            'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
            'U', 'V', 'W', 'X', 'Y', 'Z',

            // Symbols
            ' ', '$', '%', '*', '+', '-', '.', '/', ':'
    };
    public final static int[] ALPHANUM_REVERSE_LOOKUP = new int[] {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 0x00 - 0x0f
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 0x10 - 0x1f
            36, -1, -1, -1, 37, 38, -1, -1, -1, -1, 39, 40, -1, 41, 42, 43,  // 0x20 - 0x2f
            0,   1,  2,  3,  4,  5,  6,  7,  8,  9, 44, -1, -1, -1, -1, -1,  // 0x30 - 0x3f
            -1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,  // 0x40 - 0x4f
            25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, -1, -1, -1, -1, -1,  // 0x50 - 0x5f
    };

    public static String encode(final byte[] inputData) throws IOException {
        return encode(new ByteArrayInputStream(inputData));
    }

    public static String encode(final InputStream in) throws IOException {
        final StringBuilder strOut = new StringBuilder();
        while(in.available() > 0) {
            //First Byte
            int a = in.read();

            //Odd-Even Check
            if (in.available() > 0) {//Two Source Bytes
                //Second Byte
                int b = in.read();

                //Unified Bytes
                int n = (a * 256) + b;

                //e
                int e           = n / (45 * 45);
                int e_remainder = n % (45 * 45);

                //d & c
                int d = e_remainder / 45;
                int c = e_remainder % 45;

                //Output
                strOut.append(ALPHANUM_STANDARD[c]);
                strOut.append(ALPHANUM_STANDARD[d]);
                strOut.append(ALPHANUM_STANDARD[e]);

            } else {//Only One Source Byte
                int c = a % 45;
                int d = a / 45;

                //Output
                strOut.append(ALPHANUM_STANDARD[c]);
                strOut.append(ALPHANUM_STANDARD[d]);
            }
        }
        return strOut.toString();
    }

    public static byte[] decode(final String inputStr) throws IOException {
        //Prep for InputStream
        //
        //The Encoding Specified in the Standard; See: Section 4, Paragraph 2
        //https://datatracker.ietf.org/doc/html/rfc9285
        final byte[] buf = inputStr.getBytes("US-ASCII");

        return decode(new ByteArrayInputStream(buf));
    }

    public static byte[] decode(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        while(in.available() > 0) {
            //First byte
            //
            //Read in next char and translate back through lookup table
            int c = ALPHANUM_REVERSE_LOOKUP[in.read()];

            //Check for expected value
            //
            //There must be a minimum of 2 bytes, if not, then it's an error.
            if (in.available() < 0) {
                throw new IllegalArgumentException("Unexpected end of input! Parser expected at least one more byte.");
            }

            //Second byte
            int d = ALPHANUM_REVERSE_LOOKUP[in.read()];

            //Check for 3rd byte
            boolean hasThirdByte = in.available() > 0;
            int e = 0;
            if (hasThirdByte) {//3rd Byte Exists
                //Third byte
                e = ALPHANUM_REVERSE_LOOKUP[in.read()];
            }

            //Combine Bytes
            int accumulator = c + (d * 45) + (e * 45 * 45);

            //b
            byte b = (byte) accumulator;

            //Drop first byte
            accumulator /= 256;

            //a
            byte a = (byte) accumulator;

            //Write Bytes
            if (hasThirdByte) {
                out.write(a);
            }
            out.write(b);
        }
        return out.toByteArray();
    }
}
