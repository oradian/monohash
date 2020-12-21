package com.oradian.infra.monohash.impl;

import com.oradian.infra.monohash.Logger;
import com.oradian.infra.monohash.param.LogLevel;

import java.io.PrintStream;

public final class PrintStreamLogger implements Logger {
    private final PrintStream printStream;
    public final LogLevel logLevel;

    public PrintStreamLogger(final PrintStream printStream, final LogLevel logLevel) {
        this.printStream = printStream;
        this.logLevel = logLevel;
    }

    @Override public boolean isErrorEnabled() { return logLevel.ordinal() >= LogLevel.ERROR.ordinal(); }
    @Override public boolean isWarnEnabled() { return logLevel.ordinal() >= LogLevel.WARN.ordinal(); }
    @Override public boolean isInfoEnabled() { return logLevel.ordinal() >= LogLevel.INFO.ordinal(); }
    @Override public boolean isDebugEnabled() { return logLevel.ordinal() >= LogLevel.DEBUG.ordinal(); }
    @Override public boolean isTraceEnabled() { return logLevel.ordinal() >= LogLevel.TRACE.ordinal(); }

    /** PrintStream always uses the system-dependant newline which cannot be overridden :/ */
    public static String NL = System.lineSeparator();

    private void printLines(final LogLevel logLevel, final String msg) {
        final String header = "[" + logLevel + "] ";
        printStream.println(header + msg.replace("\n", NL + header));
    }

    // These are not meant to be called outside of isXxxEnabled() guards
    @Override public void error(final String msg) { printLines(LogLevel.ERROR, msg); }
    @Override public void warn(final String msg) { printLines(LogLevel.WARN, msg); }
    @Override public void info(final String msg) { printLines(LogLevel.INFO, msg); }
    @Override public void debug(final String msg) { printLines(LogLevel.DEBUG, msg); }
    @Override public void trace(final String msg) { printLines(LogLevel.TRACE, msg); }
}
