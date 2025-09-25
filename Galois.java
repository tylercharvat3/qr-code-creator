package qrcode;

public class Galois {
    private static final int MOD = 0x11D;
    private static final int SIZE = 256;

    private static final int[] expTable = new int[512]; // doubled for easy modulo
    private static final int[] logTable = new int[256];

    // Static initializer to build log and exponent tables
    static {
        int x = 1;
        for (int i = 0; i < SIZE - 1; i++) {
            expTable[i] = x;
            logTable[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= MOD;
            }
        }
        // duplicate for easy modulo 255 arithmetic
        for (int i = 255; i < 512; i++) {
            expTable[i] = expTable[i - 255];
        }
        logTable[0] = -1; // log(0) is undefined
    }

    // Addition in GF(256)
    public static int add(int a, int b) {
        return a ^ b;
    }

    public static int subtract(int a, int b) {
        return a ^ b;
    }

    // Multiplication in GF(256) using log/exp tables
    public static int multiply(int a, int b) {
        if (a == 0 || b == 0)
            return 0;
        int logA = logTable[a & 0xFF];
        int logB = logTable[b & 0xFF];
        return expTable[logA + logB];
    }

    // Division in GF(256) using log/exp tables
    public static int divide(int a, int b) {
        if (b == 0)
            throw new ArithmeticException("Divide by zero in GF(256)");
        if (a == 0)
            return 0;
        int logA = logTable[a & 0xFF];
        int logB = logTable[b & 0xFF];
        int logResult = (logA - logB + 255) % 255; // ensure positive
        return expTable[logResult];
    }

    // Inversion in GF(256)
    public static int inverse(int a) {
        if (a == 0)
            throw new ArithmeticException("Zero has no inverse in GF(256)");
        int logA = logTable[a & 0xFF];
        int logInv = 255 - logA;
        int inv = expTable[logInv];
        // verify
        System.out.println("Is result inverse: " + (multiply(a, inv) == 1));
        return inv;
    }

    public static int alphaPower(int n) {
        return expTable[n % 255]; // wrap around modulo 255
    }

    public static void main(String[] args) {
        int a = 16;
        int b = 32;
        int val = 72;

        int sum = add(a, b);
        int product = multiply(a, b);
        int inv = inverse(a);
        int quotient = divide(b, a);

        System.out.println("Add: " + sum);
        System.out.println("Mul: " + product);
        System.out.println("Inv: " + inv);
        System.out.println("Div: " + quotient);
        System.out.println("Alpha Power: " + alphaPower(val));
    }
}
