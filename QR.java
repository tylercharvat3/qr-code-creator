package qrcode;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.io.File;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

public class QR {
    private static final int SIZE = 21; // Version 1 is always 21x21
    private static boolean[][] matrix;
    private static boolean[][] isFunction; // Track which modules are function patterns

    // Galois Field (GF) arithmetic for Reed-Solomon - FIXED
    private static final int[] GF_LOG = new int[256];
    private static final int[] GF_EXP = new int[256];

    static {
        // Initialize Galois Field lookup tables for GF(256) - CORRECTED
        int x = 1;
        for (int i = 0; i < 255; i++) {
            GF_EXP[i] = x;
            GF_LOG[x] = i;
            // Direct multiplication instead of using gfMultiply
            x <<= 1; // x = x * 2
            if (x > 255) {
                x ^= 0x11d; // x = x XOR 285 (primitive polynomial)
            }
        }
        GF_EXP[255] = 1; // For convenience
        GF_LOG[0] = 0; // Undefined, but we'll handle this in gfMultiply
    }

    public static File GenerateQRCode(String data) {
        int charCount = data.length();
        String totalString = "";
        String modeIndicator = "0100"; // bytes
        totalString += modeIndicator;
        String byteCharCount = Integer.toBinaryString(charCount);
        byte[] iso88591bytes = data.getBytes(StandardCharsets.ISO_8859_1);
        String[] binaryData = new String[iso88591bytes.length];

        for (int i = 0; i < iso88591bytes.length; i++) {
            binaryData[i] = padByteString(Integer.toBinaryString(iso88591bytes[i] & 0xFF), 8);
        }

        // FIXED: pad to 8 bits for Version 1, not 9
        byteCharCount = padByteString(byteCharCount, 8);
        totalString += byteCharCount;
        System.out.print(modeIndicator + " " + byteCharCount);

        for (int i = 0; i < binaryData.length; i++) {
            System.out.print(" " + binaryData[i]);
            totalString += binaryData[i];
        }

        // 152 bits required
        String terminator = "0000";
        totalString += terminator;

        // FIXED: Only add padding if needed
        int remainder = totalString.length() % 8;
        if (remainder != 0) {
            int charsToGo = 8 - remainder;
            for (int i = 0; i < charsToGo; i++) {
                totalString += "0";
            }
        }

        System.out.println("\nIs string divisible by 8: " + (totalString.length() % 8 == 0));
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
        String errorCorrectionBits = getErrorCorrectionBits(rawDataBits);

        System.out.println("Data bits: " + rawDataBits);
        System.out.println("Error bits: " + errorCorrectionBits);

        // Debug the decoded content
        debugDataContent(rawDataBits);

        // === MATRIX PLACEMENT AND RENDERING ===

        // Initialize matrices
        matrix = new boolean[SIZE][SIZE];
        isFunction = new boolean[SIZE][SIZE];

        // Step 1: Add finder patterns
        addFinderPatterns();

        // Step 2: Add separators
        addSeparators();

        // Step 3: Add timing patterns
        addTimingPatterns();

        // Step 4: Add dark module
        addDarkModule();

        // Step 5: Reserve format information areas
        reserveFormatInformation();

        // Step 6: Place data bits
        String allBits = rawDataBits + errorCorrectionBits;
        placeDataBits(allBits);

        // Step 7: Apply masking and find best mask
        int bestMask = findBestMask();
        applyMask(bestMask);

        // Step 8: Add format information
        addFormatInformation(bestMask);

        // Step 9: Create and save image
        File outputFile = createAndSaveImage(data);

        System.out.println("QR Code generated successfully with mask pattern: " + bestMask);
        return outputFile;
    }

    private static void addFinderPatterns() {
        // Top-left (0,0), Top-right (14,0), Bottom-left (0,14)
        addFinderPattern(0, 0);
        addFinderPattern(14, 0);
        addFinderPattern(0, 14);
    }

    private static void addFinderPattern(int x, int y) {
        // 7x7 black border
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                matrix[y + i][x + j] = true;
                isFunction[y + i][x + j] = true;
            }
        }

        // 5x5 white interior
        for (int i = 1; i < 6; i++) {
            for (int j = 1; j < 6; j++) {
                matrix[y + i][x + j] = false;
            }
        }

        // 3x3 black center
        for (int i = 2; i < 5; i++) {
            for (int j = 2; j < 5; j++) {
                matrix[y + i][x + j] = true;
            }
        }
    }

    private static void addSeparators() {
        // Top-left separators
        for (int i = 0; i < 8; i++) {
            if (i < SIZE) {
                matrix[7][i] = false;
                isFunction[7][i] = true;
                matrix[i][7] = false;
                isFunction[i][7] = true;
            }
        }

        // Top-right separators
        for (int i = 13; i < SIZE; i++) {
            matrix[7][i] = false;
            isFunction[7][i] = true;
        }
        for (int i = 0; i < 8; i++) {
            matrix[i][13] = false;
            isFunction[i][13] = true;
        }

        // Bottom-left separators
        for (int i = 0; i < 8; i++) {
            matrix[13][i] = false;
            isFunction[13][i] = true;
        }
        for (int i = 13; i < SIZE; i++) {
            matrix[i][7] = false;
            isFunction[i][7] = true;
        }
    }

    private static void addTimingPatterns() {
        // Horizontal timing pattern (row 6) - spans entire width between separators
        for (int x = 8; x < SIZE - 8; x++) {
            matrix[6][x] = (x % 2 == 0);
            isFunction[6][x] = true;
        }

        // Vertical timing pattern (column 6) - spans entire height between separators
        for (int y = 8; y < SIZE - 8; y++) {
            matrix[y][6] = (y % 2 == 0);
            isFunction[y][6] = true;
        }
    }

    private static void addDarkModule() {
        // Always at (8, 13) for version 1
        matrix[13][8] = true;
        isFunction[13][8] = true;
    }

    private static void reserveFormatInformation() {
        // Reserve format info areas

        // Horizontal strip near top-left finder
        for (int x = 0; x < 9; x++) {
            isFunction[8][x] = true;
        }

        // Vertical strip near top-left finder
        for (int y = 0; y < 8; y++) {
            isFunction[y][8] = true;
        }

        // Strip near top-right finder
        for (int x = 13; x < SIZE; x++) {
            isFunction[8][x] = true;
        }

        // Strip near bottom-left finder
        for (int y = 13; y < SIZE; y++) {
            isFunction[y][8] = true;
        }
    }

    private static void placeDataBits(String bits) {
        int bitIndex = 0;
        boolean up = true;

        // Start from bottom-right, move in 2-module wide columns
        for (int x = SIZE - 1; x > 0; x -= 2) {
            if (x == 6)
                x--; // Skip vertical timing pattern

            for (int y = 0; y < SIZE; y++) {
                for (int col = 0; col < 2; col++) {
                    int currentX = x - col;
                    int currentY = up ? (SIZE - 1 - y) : y;

                    // Place bit if this module is available
                    if (currentX >= 0 && currentY >= 0 &&
                            !isFunction[currentY][currentX] && bitIndex < bits.length()) {
                        matrix[currentY][currentX] = (bits.charAt(bitIndex) == '1');
                        bitIndex++;
                    }
                }
            }
            up = !up; // Change direction
        }

        System.out.println("Placed " + bitIndex + " data bits out of " + bits.length());
    }

    private static int findBestMask() {
        int bestMask = 0;
        int lowestPenalty = Integer.MAX_VALUE;

        for (int maskPattern = 0; maskPattern < 8; maskPattern++) {
            // Create a copy of the matrix to test this mask
            boolean[][] testMatrix = new boolean[SIZE][SIZE];
            for (int i = 0; i < SIZE; i++) {
                System.arraycopy(matrix[i], 0, testMatrix[i], 0, SIZE);
            }

            // Apply mask to test matrix
            applyMaskToMatrix(testMatrix, maskPattern);

            // Calculate penalty
            int penalty = calculateMaskPenalty(testMatrix);

            if (penalty < lowestPenalty) {
                lowestPenalty = penalty;
                bestMask = maskPattern;
            }

            System.out.println("Mask " + maskPattern + " penalty: " + penalty);
        }

        System.out.println("Best mask: " + bestMask + " with penalty: " + lowestPenalty);
        return bestMask;
    }

    private static void applyMask(int maskPattern) {
        applyMaskToMatrix(matrix, maskPattern);
    }

    private static void applyMaskToMatrix(boolean[][] targetMatrix, int maskPattern) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!isFunction[y][x]) {
                    boolean mask = false;
                    switch (maskPattern) {
                        case 0:
                            mask = ((x + y) % 2 == 0);
                            break;
                        case 1:
                            mask = (y % 2 == 0);
                            break;
                        case 2:
                            mask = (x % 3 == 0);
                            break;
                        case 3:
                            mask = ((x + y) % 3 == 0);
                            break;
                        case 4:
                            mask = (((y / 2) + (x / 3)) % 2 == 0);
                            break;
                        case 5:
                            mask = (((x * y) % 2) + ((x * y) % 3) == 0);
                            break;
                        case 6:
                            mask = ((((x * y) % 2) + ((x * y) % 3)) % 2 == 0);
                            break;
                        case 7:
                            mask = ((((x + y) % 2) + ((x * y) % 3)) % 2 == 0);
                            break;
                    }
                    if (mask) {
                        targetMatrix[y][x] = !targetMatrix[y][x];
                    }
                }
            }
        }
    }

    private static int calculateMaskPenalty(boolean[][] testMatrix) {
        int penalty = 0;

        // Rule 1: Consecutive modules of the same color
        for (int y = 0; y < SIZE; y++) {
            int consecutive = 1;
            for (int x = 1; x < SIZE; x++) {
                if (testMatrix[y][x] == testMatrix[y][x - 1]) {
                    consecutive++;
                } else {
                    if (consecutive >= 5) {
                        penalty += consecutive - 2;
                    }
                    consecutive = 1;
                }
            }
            if (consecutive >= 5) {
                penalty += consecutive - 2;
            }
        }

        // Vertical consecutive
        for (int x = 0; x < SIZE; x++) {
            int consecutive = 1;
            for (int y = 1; y < SIZE; y++) {
                if (testMatrix[y][x] == testMatrix[y - 1][x]) {
                    consecutive++;
                } else {
                    if (consecutive >= 5) {
                        penalty += consecutive - 2;
                    }
                    consecutive = 1;
                }
            }
            if (consecutive >= 5) {
                penalty += consecutive - 2;
            }
        }

        // Rule 2: 2x2 blocks of same color
        for (int y = 0; y < SIZE - 1; y++) {
            for (int x = 0; x < SIZE - 1; x++) {
                boolean color = testMatrix[y][x];
                if (testMatrix[y][x + 1] == color &&
                        testMatrix[y + 1][x] == color &&
                        testMatrix[y + 1][x + 1] == color) {
                    penalty += 3;
                }
            }
        }

        return penalty;
    }

    private static void addFormatInformation(int maskPattern) {
        // Format strings for L error correction with each mask pattern
        String[] formatStrings = {
                "111011111000100", // Mask 0
                "111001011110011", // Mask 1
                "111110110101010", // Mask 2
                "111100010011101", // Mask 3
                "110011000101111", // Mask 4
                "110001100011000", // Mask 5
                "110110001000001", // Mask 6
                "110100101110110" // Mask 7
        };

        String formatBits = formatStrings[maskPattern];

        // Place format bits around top-left finder pattern
        int bitIndex = 0;

        // Horizontal placement
        for (int x = 0; x < 6; x++) {
            matrix[8][x] = (formatBits.charAt(bitIndex++) == '1');
        }
        matrix[8][7] = (formatBits.charAt(bitIndex++) == '1');
        matrix[8][8] = (formatBits.charAt(bitIndex++) == '1');

        // Vertical placement
        matrix[7][8] = (formatBits.charAt(bitIndex++) == '1');
        for (int y = 5; y >= 0; y--) {
            matrix[y][8] = (formatBits.charAt(bitIndex++) == '1');
        }

        // Place format bits around bottom-left and top-right
        bitIndex = 0;
        for (int y = SIZE - 1; y >= SIZE - 7; y--) {
            matrix[y][8] = (formatBits.charAt(bitIndex++) == '1');
        }
        for (int x = SIZE - 8; x < SIZE; x++) {
            matrix[8][x] = (formatBits.charAt(bitIndex++) == '1');
        }
    }

    private static File createAndSaveImage(String originalData) {
        int moduleSize = 10; // pixels per module
        BufferedImage image = new BufferedImage(
                SIZE * moduleSize, SIZE * moduleSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // White background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, SIZE * moduleSize, SIZE * moduleSize);

        // Black modules
        g.setColor(Color.BLACK);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (matrix[y][x]) {
                    g.fillRect(x * moduleSize, y * moduleSize, moduleSize, moduleSize);
                }
            }
        }

        g.dispose();

        // Save image
        String filename = "qrcode_" + originalData.replaceAll("[^a-zA-Z0-9]", "_") + ".png";
        File outputFile = new File(filename);

        try {
            ImageIO.write(image, "PNG", outputFile);
            System.out.println("QR code saved as: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error saving image: " + e.getMessage());
            e.printStackTrace();
        }

        return outputFile;
    }

    public static void debugDataContent(String dataBits) {
        System.out.println("\n=== DEBUGGING DATA CONTENT ===");
        String mode = dataBits.substring(0, 4);
        System.out.println("Mode: " + mode + " (should be 0100 for byte)");
        String countBits = dataBits.substring(4, 12);
        int count = Integer.parseInt(countBits, 2);
        System.out.println("Character count: " + countBits + " = " + count);
        System.out.println("Data bytes:");
        StringBuilder decoded = new StringBuilder();
        for (int i = 0; i < count; i++) {
            int startPos = 12 + (i * 8);
            String byteBits = dataBits.substring(startPos, startPos + 8);
            int byteValue = Integer.parseInt(byteBits, 2);
            char character = (char) byteValue;
            decoded.append(character);
            System.out.printf("  Byte %d: %s = %d = '%c'\n", i + 1, byteBits, byteValue, character);
        }
        System.out.println("Decoded text: \"" + decoded.toString() + "\"");
    }

    public static String getErrorCorrectionBits(String dataBits) {
        // Convert bit string to bytes (19 bytes for Version 1, L error correction)
        byte[] dataBytes = new byte[19];
        for (int i = 0; i < 19; i++) {
            String byteStr = dataBits.substring(i * 8, (i + 1) * 8);
            dataBytes[i] = (byte) Integer.parseInt(byteStr, 2);
        }

        // Generate 7 error correction bytes using Reed-Solomon
        byte[] errorBytes = generateReedSolomonBytes(dataBytes, 7);

        // Convert error bytes back to bit string
        StringBuilder errorBits = new StringBuilder();
        for (byte b : errorBytes) {
            String bits = Integer.toBinaryString(b & 0xFF);
            errorBits.append(padByteString(bits, 8));
        }

        return errorBits.toString();
    }

    private static int gfMultiply(int a, int b) {
        if (a == 0 || b == 0)
            return 0;
        return GF_EXP[(GF_LOG[a] + GF_LOG[b]) % 255];
    }

    private static byte[] generateReedSolomonBytes(byte[] data, int numErrorBytes) {
        // Generator polynomial for 7 error correction bytes (Version 1, L)
        int[] generator = { 21, 102, 238, 149, 146, 229, 87, 1 };

        // Convert data to integers for calculation
        int[] dataInts = new int[data.length + numErrorBytes];
        for (int i = 0; i < data.length; i++) {
            dataInts[i] = data[i] & 0xFF;
        }

        // Polynomial division
        for (int i = 0; i < data.length; i++) {
            int coeff = dataInts[i];
            if (coeff != 0) {
                for (int j = 0; j < generator.length; j++) {
                    dataInts[i + j] ^= gfMultiply(generator[j], coeff);
                }
            }
        }

        // Extract error correction bytes
        byte[] errorBytes = new byte[numErrorBytes];
        for (int i = 0; i < numErrorBytes; i++) {
            errorBytes[i] = (byte) dataInts[data.length + i];
        }

        return errorBytes;
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

    // Helper method to visualize the process
    public static void debugErrorCorrection(String dataBits) {
        System.out.println("Data bits: " + dataBits);
        System.out.println("Data length: " + dataBits.length());

        // Show data as hex for easier debugging
        StringBuilder hexData = new StringBuilder();
        for (int i = 0; i < dataBits.length(); i += 8) {
            String byteStr = dataBits.substring(i, i + 8);
            int byteVal = Integer.parseInt(byteStr, 2);
            hexData.append(String.format("%02X ", byteVal));
        }
        System.out.println("Data as hex: " + hexData.toString());

        String errorBits = getErrorCorrectionBits(dataBits);
        System.out.println("Error bits: " + errorBits);
        System.out.println("Error length: " + errorBits.length());

        // Show error correction as hex
        StringBuilder hexError = new StringBuilder();
        for (int i = 0; i < errorBits.length(); i += 8) {
            String byteStr = errorBits.substring(i, i + 8);
            int byteVal = Integer.parseInt(byteStr, 2);
            hexError.append(String.format("%02X ", byteVal));
        }
        System.out.println("Error as hex: " + hexError.toString());
    }

    // Test method
    public static void main(String[] args) {
        System.out.println("Generating QR code for 'Hello, world!'");
        GenerateQRCode("Hello, world!");

        System.out.println("\n" + "=".repeat(50));
        System.out.println("Generating QR code for 'Test'");
        GenerateQRCode("Test");
    }
}