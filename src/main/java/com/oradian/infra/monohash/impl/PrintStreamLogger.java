package com.oradian.infra.monohash.impl;

import com.oradian.infra.monohash.Logger;

import java.io.PrintStream;

public class PrintStreamLogger implements Logger {
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

  // These are not meant to be called outside of isXxxEnabled() guards
  @Override public void error(final String msg) { printStream.println("[error] " + msg); }
  @Override public void warn(final String msg) { printStream.println("[warn] " + msg); }
  @Override public void info(final String msg) { printStream.println("[info] " + msg); }
  @Override public void debug(final String msg) { printStream.println("[debug] " + msg); }
  @Override public void trace(final String msg) { printStream.println("[trace] " + msg); }
}
