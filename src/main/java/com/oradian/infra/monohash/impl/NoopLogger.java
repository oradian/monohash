package com.oradian.infra.monohash.impl;

import com.oradian.infra.monohash.Logger;

public enum NoopLogger implements Logger {
    INSTANCE;

    @Override public boolean isErrorEnabled() { return false; }
    @Override public boolean isWarnEnabled() { return false; }
    @Override public boolean isInfoEnabled() { return false; }
    @Override public boolean isDebugEnabled() { return false; }
    @Override public boolean isTraceEnabled() { return false; }

    @Override public void error(final String msg) {}
    @Override public void warn(final String msg) {}
    @Override public void info(final String msg) {}
    @Override public void debug(final String msg) {}
    @Override public void trace(final String msg) {}

    @Override
    public String toString() {
        return "NoopLogger";
    }
}
