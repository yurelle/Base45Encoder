import io.yurelle.Base45Encoder;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Base45EncoderTest {

    @Test
    public void stringEncodingTest() throws IOException {
        //Init test data
        final String testStr = "Some cool input data! !@#$%^&*()_+";

        //Encode
        final String encodedStr = Base45Encoder.encodeToBase45QrPayload(testStr.getBytes("UTF-8"));

        //Decode
        final byte[] decodedBytes = Base45Encoder.decodeBase45QrPayload(encodedStr);
        final String decodedStr = new String(decodedBytes, "UTF-8");

        //Output
        final boolean matches = testStr.equals(decodedStr);

        //Signal Test Result Assertion
        assertTrue(matches);

        //Log
        System.out.println("They match!");
    }

    @Test
    public void binaryEncodingAccuracyTest() throws IOException {
        //Init test data
        final int maxBytes = 5_000;
        for (int x=1; x<=maxBytes; x++) {
            System.out.print("x: " + x + "\t");

            //Encode
            final byte[] inputArray = getTestBytes(x);
            final String encodedStr = Base45Encoder.encodeToBase45QrPayload(inputArray);

            //Decode
            final byte[] decodedBytes = Base45Encoder.decodeBase45QrPayload(encodedStr);

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
        //Init test data
        final byte[] inputData = new byte[4096];
        new Random().nextBytes(inputData);

        //Encode
        final String encodedStr = Base45Encoder.encodeToBase45QrPayload(inputData);

        //Retrieve ZXING Classes
        Class bitArrayClass;
        Class encoderClass;
        try {
            bitArrayClass = Class.forName("com.google.zxing.common.BitArray");
            encoderClass = Class.forName("com.google.zxing.qrcode.encoder.Encoder");
        } catch (ClassNotFoundException e) {
            //ZXING Not on classpath
            return;
        }

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

    public static byte[] getTestBytes(int numBytes) {
        final Random rand = new Random();
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int x=0; x<numBytes; x++) {
            //bos.write(255);// -1 (byte) = 255 (int) = 1111 1111

            byte b = (byte) rand.nextInt();
            bos.write(b);
        }
        return bos.toByteArray();
    }
}
