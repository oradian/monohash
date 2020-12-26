package com.oradian.infra.monohash.util;

import java.io.File;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Locale;

public final class Format {
    private Format() {}

    public static String file(final File file) {
        return file == null ? "<none>" : '\'' + file.getPath().replace('\\', '/') + '\'';
    }
    public static String dir(final File directory) {
        return directory == null ? "<none>" : '\'' + directory.getPath().replace('\\', '/') + "/'";
    }

    public static String lines(final String header, final Collection<String> lines) {
        final StringBuilder sb = new StringBuilder(header).append(':');
        if (lines.isEmpty()) {
            sb.append(" <none>");
        }
        for (final String line : lines) {
            sb.append("\n  ").append(line);
        }
        return sb.toString();
    }

    public static String hex(final byte[] hash) {
        return '[' + Hex.toHex(hash) + ']';
    }

    private static final NumberFormat integerFormat = NumberFormat.getIntegerInstance(Locale.ROOT);
    public static String i(final long value) {
        return integerFormat.format(value);
    }

    private static final NumberFormat decimalFormat;
    static {
        decimalFormat = NumberFormat.getInstance(Locale.ROOT);
        decimalFormat.setMinimumFractionDigits(3);
    }
    public static String f(final float value) {
        return decimalFormat.format(value);
    }

    public static String timeMillis(final long startAt) {
        final long tookMs = System.currentTimeMillis() - startAt;
        return " (in " + f(tookMs / 1e3f) + " sec)";
    }

    public static String timeNanos(final long startAt) {
        final long tookNs = System.nanoTime() - startAt;
        return " (in " + f(tookNs / 1e6f) + " ms)";
    }
}
