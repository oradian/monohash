package com.oradian.infra.monohash;

public interface Logger {
    enum Level { OFF, ERROR, WARN, INFO, DEBUG, TRACE }

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
