package qrcode;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class QR {
    public static File GenerateQRCode(String data) {
        String rawDataBits = GetRawDataBits(data);
        for (int i = 0; i < rawDataBits.length(); i++) {
            System.out.print(rawDataBits.substring(i, i + 1));
            if (i % 8 == 7) {
                System.out.print(" ");
            }
        }
        return new File("C:/");
    }

    public static byte[] GetDataBytes(String data) {
        return new byte[] {};
    }

    public static String GetRawDataBits(String data) { // Working for now
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
        for (int i = 0; i < binaryData.length; i++) {
            totalString += binaryData[i];
        }
        // at this point binaryData contains mode indicator, character count, and data
        // encoded as iso88591
        // use list of 0s and 1s to store data
        // first 4 bits: 0100
        // next 9 bits: charCount in binary

        // 152 bits required
        int bitsToGo = 152 - totalString.length();
        if (bitsToGo > 4) {
            totalString += "0000";
        } else if (bitsToGo < 4) {
            for (int i = 0; i < bitsToGo; i++) {
                totalString += "0";
            }
        }
        while (totalString.length() % 8 != 0) {
            totalString += "0";
        }
        int padBytesToGo = (152 - totalString.length()) / 8;
        for (int i = 0; i < padBytesToGo; i++) {
            if (i % 2 == 0) {
                totalString += "11101110";
            } else {
                totalString += "00010001";
            }
        }
        return (totalString);
    }

    public static String padByteString(String bytes, int expectedSize) {
        while (bytes.length() < expectedSize) {
            bytes = "0" + bytes;
        }
        return bytes;
    }
}