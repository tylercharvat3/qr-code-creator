package qrcode;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class QR {
    public static File GenerateQRCode(String data) {
        int charCount = data.length();
        String totalString = "";
        String modeIndicator = "0100"; // bytes
        totalString += modeIndicator;
        String byteCharCount = Integer.toBinaryString(charCount);
        byte[] iso88591bytes = data.getBytes(StandardCharsets.ISO_8859_1);
        String[] binaryData = new String[iso88591bytes.length];
        for (int i = 0; i < iso88591bytes.length; i++) {
            binaryData[i] = padByteString(Integer.toBinaryString(iso88591bytes[i]), 8);
        }
        // pad to 9 bits
        byteCharCount = padByteString(byteCharCount, 9);
        totalString += byteCharCount;
        System.out.print(modeIndicator + " " + byteCharCount);
        for (int i = 0; i < binaryData.length; i++) {
            System.out.print(" " + binaryData[i]);
            totalString += binaryData[i];
        }
        // at this point binaryData contains mode indicator, character count, and data
        // encoded as iso88591
        // use list of 0s and 1s to store data
        // first 4 bits: 0100
        // next 9 bits: charCount in binary

        // 152 bits required
        String terminator = "0000";
        totalString += terminator;
        int charsToGo = 8 - (totalString.length() % 8);
        for (int i = 0; i < charsToGo; i++) {
            totalString += "0";
        }
        System.out.println("Is string divisible by 8:" + (totalString.length() % 8));
        int bitsToAdd = (152 - totalString.length()) / 8;
        // add filler at end
        for (int i = 0; i < bitsToAdd; i++) {
            if (i % 2 == 0) {
                totalString += "11101100";
            } else {
                totalString += "00010001";
            }
        }
        System.out.println("Length: " + totalString.length());
        // raw data bits all done
        String rawDataBits = totalString;

        return new File("C:/");
    }

    public static byte[] GetDataBytes(String data) {
        return new byte[] {};
    }

    public static String padByteString(String bytes, int expectedSize) {
        while (bytes.length() < expectedSize) {
            bytes = "0" + bytes;
        }
        return bytes;
    }
}
