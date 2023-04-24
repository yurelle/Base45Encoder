import io.yurelle.Base45;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Implements all 4 examples provided in the Base45 spec, here:
 * https://datatracker.ietf.org/doc/html/rfc9285
 *
 * Along with other tests.
 */
public class Base45Test {
    @Test
    public void stringEncodingTest() throws IOException {
        //Init test data
        final String testStr = "Some cool input data! !@#$%^&*()_+";

        //Encode
        final String encodedStr = Base45.encode(testStr.getBytes(StandardCharsets.US_ASCII));

        //Decode
        final byte[] decodedBytes = Base45.decode(encodedStr);
        final String decodedStr = new String(decodedBytes, StandardCharsets.US_ASCII);

        //Output
        final boolean matches = testStr.equals(decodedStr);

        //Signal Test Result Assertion
        assertTrue(matches);

        //Log
        System.out.println("Passed!");
    }

    @Test
    public void encodeAllUniqueBase45SingleDigits() throws IOException {
        //See: Section 4.2, "Table 1: The Base45 Alphabet" of https://datatracker.ietf.org/doc/html/rfc9285
        //
        //Trailing zero is to fill out the rest of byte. 1 byte is more than a single b45 digit.
        //According to Section 4, Paragraph 6 of the standard, 1 byte is encoded into 2 base45 digits.
        final char[] expectedDigits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:".toCharArray();

        final int maxBytes = 45;
        for (int x = 0; x < maxBytes; x++) {
            final byte[] inputArray = new byte[1];
            inputArray[0] = (byte) x;
            final String encodedStr = Base45.encode(inputArray);
            System.out.print("x: " + x + "\tencoded: " + encodedStr);

            //Check
            assertEquals(expectedDigits[x] + "0", encodedStr);
            System.out.println("\tPassed!");
        }
    }

    private void testEncodeString(final String INPUT_STR, final String EXPECTED_STR) throws IOException {
        //Process
        final String encodedStr = Base45.encode(INPUT_STR.getBytes(StandardCharsets.US_ASCII));
        System.out.print("\"" + INPUT_STR + "\":\tencoded: \"" + encodedStr + "\"");

        //Test
        assertEquals(EXPECTED_STR, encodedStr);
        System.out.println("\tPassed!");
    }

    /**
     * Implements the Encoding & Decoding examples from the official standard.
     *
     * See: Section 4, of https://datatracker.ietf.org/doc/html/rfc9285
     */
    @Test
    public void binaryStandardTest() throws IOException {
        //
        // Encoding
        //
        System.out.println("\n--------\nEncoding\n--------\n");

        {//"AB" - [65, 66] // Section 4.3; Example 1
            final String INPUT_STR = "AB";
            final String EXPECTED_STR = "BB8";

            //String Conversion
            testEncodeString(INPUT_STR, EXPECTED_STR);

            {//Manual Binary Array
                final byte[] inputArray = new byte[2];
                inputArray[0] = (byte) 65;
                inputArray[1] = (byte) 66;

                //Process
                final String encodedStr = Base45.encode(inputArray);
                System.out.print(printByteArray(inputArray) + ":\tencoded: \"" + encodedStr + "\"");

                //Test
                assertEquals(EXPECTED_STR, encodedStr);
                System.out.println("\tPassed!");
            }
        }

        // "Hello!!" // Section 4.3; Example 2
        testEncodeString("Hello!!", "%69 VD92EX0");

        // "Hello!!" // Section 4.3; Example 3
        testEncodeString("base-45", "UJCLQE7W581");

        //
        // Decoding
        //
        System.out.println("\n--------\nDecoding\n--------\n");
        {// "Hello!!" // Section 4.4; Example 1
            final String INPUT_STR = "QED8WEX0";
            final String EXPECTED_STR = "ietf!";

            //Process
            byte[] decodedBytes = Base45.decode(INPUT_STR);
            final String decodedString = new String(decodedBytes, StandardCharsets.US_ASCII);
            System.out.print("\"" + INPUT_STR + "\":\tdecoded:\t\"" + decodedString + "\" " + printByteArray(decodedBytes));

            //Test
            assertEquals(EXPECTED_STR, decodedString);
            System.out.println("\tPassed!");
        }
    }

    private String printByteArray(final byte[] arr) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (byte b : arr) {
            //Separator
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }

            //Value
            sb.append((int) b);
        }
        sb.append("]");
        return sb.toString();
    }

    @Test
    public void embeddedZeroTest() throws IOException {
        System.out.println("Embedded Zero\n------------");

        //Index of the embedded zero
        //
        //Each iteration, it moves the zero one notch down the array.
        final int numTest = 32;
        for (int x=0; x<numTest; x++) {
            byte[] inputBuf = new byte[numTest];

            //Init array values
            for (int y=0; y<inputBuf.length; y++) {
                if (y == x) {
                    inputBuf[y] = 0;
                } else {
                    inputBuf[y] = (byte) (y+1);
                }
            }
            System.out.print("X: " + printByteArray(inputBuf) + "\t");
            final String encodedStr = Base45.encode(inputBuf);
            final byte[] decodedBuf = Base45.decode(encodedStr);

            //Test
            assertEquals(inputBuf.length, decodedBuf.length);
            for (int y=0; y<inputBuf.length; y++) {
                assertEquals(inputBuf[y], decodedBuf[y]);
            }

            //Log
            System.out.println("Passed!");
        }/**/



        //Zero Byte Test
        final byte[] embededZero = new byte[] {-32, 10, 118, -119, 0, -83, -96, 45, 32, -120, -4, 37, -21, -49};
        System.out.print("Manual Embedded Zero (Known Case): " + printByteArray(embededZero) + "\t");
        final String encodedZero = Base45.encode(embededZero);
        final byte[] decodedZero = Base45.decode(encodedZero);

        //Test
        assertEquals(embededZero.length, decodedZero.length);
        for (int x=0; x<embededZero.length; x++) {
            assertEquals(embededZero[x], decodedZero[x]);
        }

        //Log
        System.out.println("Passed!");
    }

    @Test
    public void binaryEncodingAccuracyTest() throws IOException {
        //Init test data
        final int maxBytes = 5_000;
        for (int x=1; x<=maxBytes; x++) {
            System.out.print("x: " + x + "\t");

            //Encode
            final byte[] inputArray = getTestBytes(x);
            final String encodedStr = Base45.encode(inputArray);

            //Decode
            final byte[] decodedBytes = Base45.decode(encodedStr);

            //Output
            for (int y=0; y<x; y++) {
                //Signal Test Result Assertion
                assertEquals(inputArray[y], decodedBytes[y]);
            }

            //Log
            System.out.println("Passed!");
        }
    }

    @Test
    public void binaryEncodingEfficiencyTest() throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        //Retrieve ZXING Classes
        Class bitArrayClass;
        Class encoderClass;
        try {
            bitArrayClass = Class.forName("com.google.zxing.common.BitArray");
            encoderClass = Class.forName("com.google.zxing.qrcode.encoder.Encoder");
        } catch (ClassNotFoundException e) {
            //ZXING Not on classpath. See commented out dependency in POM.xml
            System.out.println("ZXing not in classpath. Skipping this test.");
            return;
        }

        //Init test data
        final byte[] inputData = new byte[4096];
        new Random(1234).nextBytes(inputData);
        System.out.println("bytes:\n" + printByteArray(inputData) + "\n");

        //Encode
        final String encodedStr = Base45.encode(inputData);
        System.out.println("encoded String:\n" + encodedStr + "\n");

        //Write to QR Code Encoder // Have to use Reflection to force access, since the function is not public.
        Object qrCode = bitArrayClass.newInstance();
        final Method appendAlphanumericBytes = encoderClass.getDeclaredMethod("appendAlphanumericBytes", CharSequence.class, bitArrayClass);
        appendAlphanumericBytes.setAccessible(true);
        appendAlphanumericBytes.invoke(null, encodedStr, qrCode);

        //Get Handle to size function
        Method getSizeInBytes = bitArrayClass.getMethod("getSizeInBytes");

        //Output
        final int origSize = inputData.length;
        final int qrSize = (int) getSizeInBytes.invoke(qrCode);
        System.out.println("Raw Binary Size:\t\t" + origSize + "\nEncoded String Size:\t" + encodedStr.length() + "\nQR Code Alphanum Size:\t" + qrSize);

        //Calculate Storage Efficiency Loss
        final int delta = origSize - qrSize;
        final double efficiency = ((double) delta) / origSize;
        final double efficiencyPercentage = efficiency * 100;
        System.out.println("Storage Efficiency Loss: " + String.format("%.3f", efficiencyPercentage) + "%");

        //Signal Test Result Assertion
        assertTrue("Binary encoding efficiency dropped below acceptable threshold!", (Math.abs(efficiencyPercentage) < 10) );
    }

    private static final Random rand = new Random(1234);
    public static byte[] getTestBytes(int numBytes) {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int x=0; x<numBytes; x++) {
            //bos.write(255);// -1 (byte) = 255 (int) = 1111 1111

            byte b = (byte) rand.nextInt();
            bos.write(b);
        }
        return bos.toByteArray();
    }
}
