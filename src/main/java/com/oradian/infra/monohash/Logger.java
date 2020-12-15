package com.oradian.infra.monohash;

public interface Logger {
    boolean isErrorEnabled();
    boolean isWarnEnabled();
    boolean isInfoEnabled();
    boolean isDebugEnabled();
    boolean isTraceEnabled();

    void error(final String msg);
    void warn(final String msg);
    void info(final String msg);
    void debug(final String msg);
    void trace(final String msg);
}
