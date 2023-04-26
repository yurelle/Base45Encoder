package io.yurelle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static io.yurelle.SecureLookupWrapper.doLookup;
import static io.yurelle.SecureLookupWrapper.doReverseLookup;

/**
 * Java implementation of the Base45 Standard outlined here:
 * https://datatracker.ietf.org/doc/html/rfc9285
 *
 * The corresponding Unit Test implements all 4 examples
 * provided in the spec, along with several others.
 */
public class Base45 {
    public static String encode(final byte[] inputData) throws IOException {
        return encode(new ByteArrayInputStream(inputData));
    }

    //
    //Variable names match those used in the encoding examples provided in the standard.
    //See: Section 4(.0)
    //
    public static String encode(final InputStream in) throws IOException {
        final StringBuilder strOut = new StringBuilder();

        int bytesRead = 0;
        while(in.available() > 0) {
            //First Byte
            int a = in.read();
            bytesRead++;

            //Odd-Even Check
            if (in.available() > 0) {//Two Source Bytes
                //Second Byte
                int b = in.read();
                bytesRead++;

                //Unified Bytes
                int n = (a * 256) + b;

                //e
                int e           = n / (45 * 45);
                int e_remainder = n % (45 * 45);

                //d & c
                int d = e_remainder / 45;
                int c = e_remainder % 45;

                //Output
                strOut.append(doLookup(c, bytesRead));
                strOut.append(doLookup(d, bytesRead));
                strOut.append(doLookup(e, bytesRead));

            } else {//Only One Source Byte
                int c = a % 45;
                int d = a / 45;

                //Output
                strOut.append(doLookup(c, bytesRead));
                strOut.append(doLookup(d, bytesRead));
            }
        }
        return strOut.toString();
    }

    public static byte[] decode(final String inputStr) throws IOException, IllegalArgumentException {
        //Prep for InputStream
        //
        //The Encoding Specified in the Standard; See: Section 4, Paragraph 2
        //https://datatracker.ietf.org/doc/html/rfc9285
        final byte[] buf = inputStr.getBytes("US-ASCII");

        return decode(new ByteArrayInputStream(buf));
    }

    //
    //Variable names match those used in the decoding examples provided in the standard.
    //See: Section 4(.0)
    //
    public static byte[] decode(final InputStream in) throws IOException, IllegalArgumentException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        int bytesRead = 0;
        while(in.available() > 0) {
            //First byte
            //
            //Read in next char and translate back through lookup table
            int c = doReverseLookup(in.read(), bytesRead);
            bytesRead++;

            //Check for expected value
            //
            //There must be a minimum of 2 bytes, if not, then it's an error.
            //See: Section 4, Paragraphs 6 & 7
            if (in.available() < 0) {
                throw new IllegalArgumentException("Unexpected end of input at index '" + bytesRead + "'! Parser expected at least one more byte.");
            }

            //Second byte
            int d = doReverseLookup(in.read(), bytesRead);
            bytesRead++;

            //Check for 3rd byte
            boolean hasThirdByte = in.available() > 0;
            int e = 0;
            if (hasThirdByte) {//3rd Byte Exists
                //Third byte
                e = doReverseLookup(in.read(), bytesRead);
                bytesRead++;
            }

            //Combine Bytes
            int accumulator = c + (d * 45) + (e * 45 * 45);

            //Security check outlined in the standard; See: Section 6, Paragraph 5.
            //
            //Base45 triplets exceeding an accumulated value of 64K (i.e. 65,535; or 0xFFFF)
            //must be rejected.
            if (accumulator > 0xFFFF) {
                throw new IllegalArgumentException("Parsing Failed! Parsed value '" + accumulator + "' of Base45 chunk at index '" + (hasThirdByte ? bytesRead-3 : bytesRead-2) + "' exceeds double-byte max value of 64K (i.e. 65,535; or 0xFFFF).");
            }

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
