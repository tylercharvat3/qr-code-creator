package qrcode;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class QR {

    // Generate generator polynomial for n error correction codewords
    private static int[] generateGeneratorPolynomial(int n) {
        int[] generator = { 1 }; // Start with polynomial "1"

        // Multiply by (x - α^i) for i = 0 to n-1
        for (int i = 0; i < n; i++) {
            int[] newGenerator = new int[generator.length + 1];

            // Multiply generator by (x - α^i)
            // This is equivalent to multiplying by x and then subtracting α^i * generator
            for (int j = 0; j < generator.length; j++) {
                newGenerator[j] = Galois.add(newGenerator[j], Galois.multiply(generator[j], Galois.alphaPower(i)));
                newGenerator[j + 1] = Galois.add(newGenerator[j + 1], generator[j]);
            }
            generator = newGenerator;
        }
        return generator;
    }

    public static File GenerateQRCode(String data) {
        String rawDataBits = GetRawDataBits(data);
        String eccBits = GetECCDataBits(rawDataBits);

        return new File("C:/");
    }

    public static int[] generateErrorCorrectionCodewords(int[] dataCodewords, int numEccCodewords) {
        // Step 1: Create the message polynomial by padding with zeros
        int[] messagePolynomial = new int[dataCodewords.length + numEccCodewords];
        System.arraycopy(dataCodewords, 0, messagePolynomial, 0, dataCodewords.length);
        // The remaining positions are already 0

        // Step 2: Get the generator polynomial
        int[] generatorPolynomial = generateGeneratorPolynomial(numEccCodewords);

        // Step 3: Perform polynomial division using Reed-Solomon algorithm
        for (int i = 0; i < dataCodewords.length; i++) {
            int leadCoeff = messagePolynomial[i];

            if (leadCoeff != 0) {
                // Multiply generator polynomial by lead coefficient and XOR with message
                for (int j = 0; j < generatorPolynomial.length; j++) {
                    messagePolynomial[i + j] = Galois.add(messagePolynomial[i + j],
                            Galois.multiply(generatorPolynomial[j], leadCoeff));
                }
            }
        }

        // Step 4: Extract the remainder (error correction codewords)
        int[] eccCodewords = new int[numEccCodewords];
        System.arraycopy(messagePolynomial, dataCodewords.length, eccCodewords, 0, numEccCodewords);

        return eccCodewords;
    }

    public static String GetRawDataBits(String data) {
        int charCount = data.length();
        String totalString = "";
        String modeIndicator = "0100"; // bytes mode
        totalString += modeIndicator;

        String byteCharCount = Integer.toBinaryString(charCount);
        byte[] iso88591bytes = data.getBytes(StandardCharsets.ISO_8859_1);
        String[] binaryData = new String[iso88591bytes.length];

        for (int i = 0; i < iso88591bytes.length; i++) {
            binaryData[i] = padByteString(Integer.toBinaryString(iso88591bytes[i] & 0xFF), 8);
        }

        // Pad character count to 9 bits
        byteCharCount = padByteString(byteCharCount, 9);
        totalString += byteCharCount;

        for (String binaryDatum : binaryData) {
            totalString += binaryDatum;
        }

        // Pad to required length (152 bits for your version)
        int bitsToGo = 152 - totalString.length();
        if (bitsToGo > 4) {
            totalString += "0000";
            bitsToGo -= 4;
        } else if (bitsToGo > 0) {
            for (int i = 0; i < bitsToGo; i++) {
                totalString += "0";
            }
            bitsToGo = 0;
        }

        // Pad to byte boundary
        while (totalString.length() % 8 != 0) {
            totalString += "0";
        }

        // Add pad bytes
        int padBytesToGo = (152 - totalString.length()) / 8;
        for (int i = 0; i < padBytesToGo; i++) {
            if (i % 2 == 0) {
                totalString += "11101100"; // 236 in binary
            } else {
                totalString += "00010001"; // 17 in binary
            }
        }

        return totalString;
    }

    public static String GetECCDataBits(String rawDataBits) {
        // Convert rawDataBits to data codewords
        int numDataCodewords = rawDataBits.length() / 8;
        int[] dataCodewords = new int[numDataCodewords];

        for (int i = 0; i < numDataCodewords; i++) {
            String byteString = rawDataBits.substring(i * 8, (i + 1) * 8);
            dataCodewords[i] = StringBinaryToDecimal(byteString);
        }

        // Generate ECC codewords
        int[] eccCodewords = generateErrorCorrectionCodewords(dataCodewords, 19);

        // Convert back to binary string
        StringBuilder eccBits = new StringBuilder();
        for (int codeword : eccCodewords) {
            eccBits.append(padByteString(Integer.toBinaryString(codeword), 8));
        }

        return eccBits.toString();
    }

    public static String padByteString(String bytes, int expectedSize) {
        while (bytes.length() < expectedSize) {
            bytes = "0" + bytes;
        }
        return bytes;
    }

    public static int StringBinaryToDecimal(String s) {
        int ans = 0;
        int power = 0;

        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) == '1') {
                ans += (1 << power); // More efficient than Math.pow(2, power)
            }
            power++;
        }
        return ans;
    }
}