package com.oradian.infra.monohash.impl;

import com.oradian.infra.monohash.Logger;

import java.io.PrintStream;

public final class PrintStreamLogger implements Logger {
    private final PrintStream printStream;
    private final Level level;

    public PrintStreamLogger(final PrintStream printStream, final Level level) {
        this.printStream = printStream;
        this.level = level;
    }

    @Override public boolean isErrorEnabled() { return level.ordinal() >= Level.ERROR.ordinal(); }
    @Override public boolean isWarnEnabled() { return level.ordinal() >= Level.WARN.ordinal(); }
    @Override public boolean isInfoEnabled() { return level.ordinal() >= Level.INFO.ordinal(); }
    @Override public boolean isDebugEnabled() { return level.ordinal() >= Level.DEBUG.ordinal(); }
    @Override public boolean isTraceEnabled() { return level.ordinal() >= Level.TRACE.ordinal(); }

    /** PrintStream always uses the system-dependant newline which cannot be overridden :/ */
    public static String NL = System.lineSeparator();

    private void printLines(final String header, final String msg) {
        printStream.println(header + msg.replace("\n", NL + header));
    }

    // These are not meant to be called outside of isXxxEnabled() guards
    @Override public void error(final String msg) { printLines("[error] ", msg); }
    @Override public void warn(final String msg) { printLines("[warn] ", msg); }
    @Override public void info(final String msg) { printLines("[info] ", msg); }
    @Override public void debug(final String msg) { printLines("[debug] ", msg); }
    @Override public void trace(final String msg) { printLines("[trace] ", msg); }
}
