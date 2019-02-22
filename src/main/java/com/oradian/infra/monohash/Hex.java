package com.oradian.infra.monohash;

// It's mind-boggling that there is no decent hex utility in the JRE
class Hex {
    private static final char[] TO_LC_HEX = "0123456789abcdef".toCharArray();
    static String toHex(final byte[] binary) {
        final char[] hex = new char[binary.length << 1];
        int readIndex = 0;
        for (int i = 0; i < hex.length;) {
            final int b = binary[readIndex++];
            hex[i++] = TO_LC_HEX[(b >> 4) & 0xf];
            hex[i++] = TO_LC_HEX[b & 0xf];
        }
        return new String(hex);
    }

    /** Hexadecimal character to integer value mapping used in parser.
     *  Invalid characters are marked with a value of `-1`. */
    private static final int[] FROM_HEX = new int['f' + 1];
    static {
        for (int i = 0; i < FROM_HEX.length; i ++) {
            FROM_HEX[i] = i >= '0' && i <= '9' ? i - '0' :
                          i >= 'A' && i <= 'F' ? i - 'A' + 10 :
                          i >= 'a' /* && i <= 'f' */ ? i - 'a' + 10 : -1;
        }
    }
    static byte[] fromHex(final byte[] hex, final int length) {
        final byte[] binary = new byte[length >> 1];
        int readIndex = 0;
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) ((FROM_HEX[(int) hex[readIndex++]] << 4) +
                                (FROM_HEX[(int) hex[readIndex++]]));
        }
        return binary;
    }
}
