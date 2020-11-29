package com.oradian.infra.monohash.util;

import java.nio.charset.StandardCharsets;

// It's mind-boggling that there is no decent hex utility in the JRE
public final class Hex {
    private Hex() {}

    private static final byte[] TO_LC_HEX = "0123456789abcdef".getBytes(StandardCharsets.ISO_8859_1);

    public static String toHex(final byte[] binary) {
        final byte[] buffer = new byte[binary.length << 1];
        toHex(binary, 0, binary.length, buffer, 0);
        return new String(buffer, StandardCharsets.ISO_8859_1);
    }

    public static void toHex(final byte[] src, final int srcOff, final int srcLen, final byte[] dst, final int dstOff) {
        int writeIndex = dstOff;
        int readIndex = srcOff;
        while (readIndex < srcLen - 4) {
            final int b1 = src[readIndex];
            final int b2 = src[readIndex + 1];
            final int b3 = src[readIndex + 2];
            final int b4 = src[readIndex + 3];
            dst[writeIndex] = TO_LC_HEX[(b1 >>> 4) & 0xf];
            dst[writeIndex + 1] = TO_LC_HEX[b1 & 0xf];
            dst[writeIndex + 2] = TO_LC_HEX[(b2 >>> 4) & 0xf];
            dst[writeIndex + 3] = TO_LC_HEX[b2 & 0xf];
            dst[writeIndex + 4] = TO_LC_HEX[(b3 >>> 4) & 0xf];
            dst[writeIndex + 5] = TO_LC_HEX[b3 & 0xf];
            dst[writeIndex + 6] = TO_LC_HEX[(b4 >>> 4) & 0xf];
            dst[writeIndex + 7] = TO_LC_HEX[b4 & 0xf];
            readIndex += 4;
            writeIndex += 8;
        }
        while (readIndex < srcLen) {
            final int b = src[readIndex++];
            dst[writeIndex++] = TO_LC_HEX[(b >>> 4) & 0xf];
            dst[writeIndex++] = TO_LC_HEX[b & 0xf];
        }
    }

    /** Hexadecimal character to integer value mapping used in parser.
      * Invalid characters are marked with a value of `-1`. */
    private static final int[] FROM_HEX = new int[0x100];
    static {
        for (int i = 0; i < FROM_HEX.length; i++) {
            FROM_HEX[i] = i >= '0' && i <= '9' ? i - '0' :
                          i >= 'a' && i <= 'f' ? i - 'a' + 10 : -1;
        }
    }

    public static byte[] fromHex(final byte[] hex) {
        return fromHex(hex, 0, hex.length);
    }

    public static byte[] fromHex(final byte[] hex, final int offset, final int length) {
        if ((length & 1) == 1) {
            throw new IllegalArgumentException("Length must be an even number, got: " + length);
        }
        final byte[] binary = new byte[length >>> 1];
        int readIndex = offset;
        for (int i = 0; i < binary.length; i++) {
            final int hi = FROM_HEX[hex[readIndex++] & 0xff];
            final int lo = FROM_HEX[hex[readIndex++] & 0xff];
            if ((hi | lo) < 0) {
                final int index = readIndex - (hi < 0 ? 2 : 1);
                throw new NumberFormatException("Cannot parse hex digit at index " + index + " - expected a lowercase hexadecimal digit [0-9, a-f] but got: '" + (char) hex[index] + '\'');
            }
            binary[i] = (byte) ((hi << 4) + lo);
        }
        return binary;
    }
}
