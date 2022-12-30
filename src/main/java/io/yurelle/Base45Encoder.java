package io.yurelle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * For some reason none of the Java QR Code libraries support binary payloads. At least, none that
 * I could find anyway. The commonly suggested workaround for this is to use Base64 encoding.
 * However, this results in a 33% payload size inflation. If your payload is already near the size
 * limit of QR codes, this is a lot.
 *
 * This class implements an encoder which takes advantage of a built-in compression optimization
 * of the ZXING QR Code library, to enable the storage of Binary data into a QR Code, with a
 * storage efficiency loss of only -8%.
 *
 * The built-in optimization is this: ZXING will automatically detect if your String payload is
 * purely AlphaNumeric (by their own definition), and if so, it will automatically compress 2
 * AlphaNumeric characters into 11 bits.
 *
 *
 * ----------------------
 *
 *
 * The included ALPHANUMERIC_TABLE is the conversion table used by the ZXING library as a reverse
 * index for determining if a given input data should be classified as alphanumeric.
 *
 * See:
 *
 *      com.google.zxing.qrcode.encoder.Encoder.chooseMode(String content, String encoding)
 *
 * which scans through the input string one character at a time and passes them to:
 *
 *      getAlphanumericCode(int code)
 *
 * in the same class, which uses that character as a numeric index into the the
 * ALPHANUMERIC_TABLE.
 *
 * If you examine the values, you'll notice that it ignores / disqualifies certain values, and
 * effectively converts the input into base 45 (0 -> 44; -1 is interpreted by the calling code
 * to mean a failure). This is confirmed in the function:
 *
 *      appendAlphanumericBytes(CharSequence content, BitArray bits)
 *
 * where they pack 2 of these base 45 digits into 11 bits. This presents us with an opportunity.
 * If we can take our data, and convert it into a compatible base 45 alphanumeric representation,
 * then the QR Encoder will automatically pack that data into sub-byte chunks.
 *
 * 2 digits in base 45 is 2,025 possible values. 11 bits has a maximum storage capacity of 2,048
 * possible states. This is only a loss of 1.1% in storage efficiency behind raw binary.
 *
 *      45 ^ 2 = 2,025
 *      2 ^ 11 = 2,048
 *      2,048 - 2,025 = 23
 *      23 / 2,048 = 0.01123046875 = 1.123%
 *
 * However, this is the ideal / theoretical efficiency. This implementation processes data in
 * chunks, using a Long as a computational buffer. However, since Java Long's are singed, we
 * can only use the lower 7 bytes. The conversion code requires continuously positive values;
 * using the highest 8th byte would contaminate the sign bit and randomly produce negative
 * values.
 *
 *
 *
 * Real-World Test:
 *
 * Using a 7 byte Long to encode a 2KB buffer of random bytes, we get the following results.
 *
 *      Raw Binary Size:        2,048
 *      Encoded String Size:    3,218
 *      QR Code Alphanum Size:  2,213 (after the QR Code compresses 2 base45 digits to 11 bits)
 *
 * This is a real-world storage efficiency loss of only 8%.
 *
 *      2,213 - 2,048 = 165
 *      165 / 2,048 = 0.08056640625 = 8.0566%
 */
public class Base45Encoder {
    public final static int[] ALPHANUMERIC_TABLE;

    /*
     * By default, attempt to retrieve the internal table definition from the ZXING library, in case they
     * change it in future versions. If this fails, or if the ZXING library is not on the classpath, then
     * revert to a manual table definition.
     */
    static {
        final Field SOURCE_ALPHANUMERIC_TABLE;
        int[] tmp;

        //Copy lookup table from ZXING Encoder class
        //
        //Default to cloning whatever array the current version of zxing is using.
        try {
            SOURCE_ALPHANUMERIC_TABLE = Class.forName("com.google.zxing.qrcode.encoder.Encoder").getDeclaredField("ALPHANUMERIC_TABLE");
            SOURCE_ALPHANUMERIC_TABLE.setAccessible(true);
            tmp = (int[]) SOURCE_ALPHANUMERIC_TABLE.get(null);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();//Shouldn't happen - Unless you don't have xzing on the classpath
            tmp = null;
        } catch (NoSuchFieldException e) {
            e.printStackTrace();//Shouldn't happen
            tmp = null;
        } catch (IllegalAccessException e) {
            e.printStackTrace();//Shouldn't happen
            tmp = null;
        }

        //Null Check - Fallback
        if (tmp == null) {
            System.err.println(
                    "\n\n----------\n\n" +
                    "Failed to retrieve ZXING's internal AlphaNum table definition! Falling back to manual definition." +
                    "\n\n----------\n\n"
            );
            tmp = new int[] {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 0x00-0x0f
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,  // 0x10-0x1f
                    36, -1, -1, -1, 37, 38, -1, -1, -1, -1, 39, 40, -1, 41, 42, 43,  // 0x20-0x2f
                    0,   1,  2,  3,  4,  5,  6,  7,  8,  9, 44, -1, -1, -1, -1, -1,  // 0x30-0x3f
                    -1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,  // 0x40-0x4f
                    25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, -1, -1, -1, -1, -1,  // 0x50-0x5f
            };
        }

        //Store
        ALPHANUMERIC_TABLE = tmp;
    }

    public static final int NUM_DISTINCT_ALPHANUM_VALUES = 45;
    public static final char[] alphaNumReverseIndex = new char[NUM_DISTINCT_ALPHANUM_VALUES];

    static {
        //Build AlphaNum Index
        final int len = ALPHANUMERIC_TABLE.length;
        for (int x = 0; x < len; x++) {
            // The base45 result which the alphanum lookup table produces.
            // i.e. the base45 digit value which String characters are
            // converted into.
            //
            // We use this value to build a reverse lookup table to find
            // the String character we have to send to the encoder, to
            // make it produce the given base45 digit value.
            final int base45DigitValue = ALPHANUMERIC_TABLE[x];

            //Ignore the -1 records
            if (base45DigitValue > -1) {
                //The index into the lookup table which produces the given base45 digit value.
                //
                //i.e. to produce a base45 digit with the numeric value in base45DigitValue, we need
                //to send the Encoder a String character with the numeric value in x.
                alphaNumReverseIndex[base45DigitValue] = (char) x;
            }
        }
    }

    /*
     * The storage capacity of one digit in the number system; i.e. the maximum
     * possible number of distinct values which can be stored in 1 logical digit
     */
    public static final int QR_PAYLOAD_NUMERIC_BASE = NUM_DISTINCT_ALPHANUM_VALUES;

    /*
     * We can't use all 8 bytes, because the Long is signed, and the conversion math
     * requires consistently positive values. If we populated all 8 bytes, then the
     * last byte has the potential to contaminate the sign bit, and break the
     * conversion math. So, we only use the lower 7 bytes, and avoid this problem.
     */
    public static final int LONG_USABLE_BYTES = Long.BYTES - 1;

    //The following mapping was determined by brute-forcing -1 Long (all bits 1), and compressing to base45 until it hit zero.
    public static final int[] BINARY_TO_BASE45_DIGIT_COUNT_CONVERSION = new int[] {0,2,3,5,6,8,9,11,12};
    public static final int NUM_BASE45_DIGITS_PER_LONG = BINARY_TO_BASE45_DIGIT_COUNT_CONVERSION[LONG_USABLE_BYTES];
    public static final Map<Integer, Integer> BASE45_TO_BINARY_DIGIT_COUNT_CONVERSION = new HashMap<>();

    static {
        //Build Reverse Lookup
        int len = BINARY_TO_BASE45_DIGIT_COUNT_CONVERSION.length;
        for (int x=0; x<len; x++) {
            int numB45Digits = BINARY_TO_BASE45_DIGIT_COUNT_CONVERSION[x];
            BASE45_TO_BINARY_DIGIT_COUNT_CONVERSION.put(numB45Digits, x);
        }
    }

    public static String encodeToBase45QrPayload(final byte[] inputData) throws IOException {
        return encodeToBase45QrPayload(new ByteArrayInputStream(inputData));
    }

    public static String encodeToBase45QrPayload(final InputStream in) throws IOException {
        //Init conversion state vars
        final StringBuilder strOut = new StringBuilder();
        int data;
        long buf = 0;

        // Process all input data in chunks of size LONG.BYTES, this allows for economies of scale
        // so we can process more digits of arbitrary size before we hit the wall of the binary
        // chunk size in a power of 2, and have to transmit a sub-optimal chunk of the "crumbs"
        // left over; i.e. the slack space between where the multiples of QR_PAYLOAD_NUMERIC_BASE
        // and the powers of 2 don't quite line up.
        while(in.available() > 0) {
            //Fill buffer
            int numBytesStored = 0;
            while (numBytesStored < LONG_USABLE_BYTES && in.available() > 0) {
                //Read next byte
                data = in.read();

                //Push byte into buffer
                buf = (buf << 8) | data; //8 bits per byte

                //Increment
                numBytesStored++;
            }

            //Write out in lower base
            final StringBuilder outputChunkBuffer = new StringBuilder();
            final int numBase45Digits = BINARY_TO_BASE45_DIGIT_COUNT_CONVERSION[numBytesStored];
            int numB45DigitsProcessed = 0;
            while(numB45DigitsProcessed < numBase45Digits) {
                //Chunk out a digit
                final byte digit = (byte) (buf % QR_PAYLOAD_NUMERIC_BASE);

                //Drop digit data from buffer
                buf = buf / QR_PAYLOAD_NUMERIC_BASE;

                //Write Digit
                outputChunkBuffer.append(alphaNumReverseIndex[(int) digit]);

                //Track output digits
                numB45DigitsProcessed++;
            }

            /*
             * The way this code works, the processing output results in a First-In-Last-Out digit
             * reversal. So, we need to buffer the chunk output, and feed it to the OutputStream
             * backwards to correct this.
             *
             * We could probably get away with writing the bytes out in inverted order, and then
             * flipping them back on the decode side, but just to be safe, I'm always keeping
             * them in the proper order.
             */
            strOut.append(outputChunkBuffer.reverse().toString());
        }

        //Return
        return strOut.toString();
    }

    public static byte[] decodeBase45QrPayload(final String inputStr) throws IOException {
        //Prep for InputStream
        final byte[] buf = inputStr.getBytes();//Use the default encoding (the same encoding that the 'char' primitive uses)

        return decodeBase45QrPayload(new ByteArrayInputStream(buf));
    }

    public static byte[] decodeBase45QrPayload(final InputStream in) throws IOException {
        //Init conversion state vars
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int data;
        long buf = 0;
        int x=0;

        // Process all input data in chunks of size LONG.BYTES, this allows for economies of scale
        // so we can process more digits of arbitrary size before we hit the wall of the binary
        // chunk size in a power of 2, and have to transmit a sub-optimal chunk of the "crumbs"
        // left over; i.e. the slack space between where the multiples of QR_PAYLOAD_NUMERIC_BASE
        // and the powers of 2 don't quite line up.
        while(in.available() > 0) {
            //Convert & Fill Buffer
            int numB45Digits = 0;
            while (numB45Digits < NUM_BASE45_DIGITS_PER_LONG && in.available() > 0) {
                //Read in next char
                char c = (char) in.read();

                //Translate back through lookup table
                int digit = ALPHANUMERIC_TABLE[(int) c];

                //Shift buffer up one digit to make room
                buf *= QR_PAYLOAD_NUMERIC_BASE;

                //Append next digit
                buf += digit;

                //Increment
                numB45Digits++;
            }

            //Write out in higher base
            final LinkedList<Byte> outputChunkBuffer = new LinkedList<>();
            final int numBytes = BASE45_TO_BINARY_DIGIT_COUNT_CONVERSION.get(numB45Digits);
            int numBytesProcessed = 0;
            while(numBytesProcessed < numBytes) {
                //Chunk out 1 byte
                final byte chunk = (byte) buf;

                //Shift buffer to next byte
                buf = buf >> 8; //8 bits per byte

                //Write byte to output
                //
                //Again, we need to invert the order of the bytes, so as we chunk them off, push
                //them onto a FILO stack; inverting their order.
                outputChunkBuffer.push(chunk);

                //Increment
                numBytesProcessed++;
            }

            //Write chunk buffer to output stream (in reverse order)
            while (outputChunkBuffer.size() > 0) {
                out.write(outputChunkBuffer.pop());
            }
        }

        //Return
        out.flush();
        out.close();
        return out.toByteArray();
    }
}
