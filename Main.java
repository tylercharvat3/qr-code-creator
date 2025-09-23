package qrcode;

public class Main {
    public static void main(String[] args) {
        System.out.println("Working");
        System.out.println(QR.GenerateQRCode("Hello, world!")); // expected 1011
    }
}