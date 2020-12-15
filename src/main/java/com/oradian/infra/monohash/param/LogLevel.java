package com.oradian.infra.monohash.param;

import com.oradian.infra.monohash.ExitException;
import com.oradian.infra.monohash.Logger;

import java.util.List;
import java.util.Locale;

public enum LogLevel {
    OFF,   // completely squelch output, even on errors
    ERROR, // only output errors and stack trace on exit
    WARN,  // show hashing warnings and output verification warnings
    INFO,  // show successful results and timing summary
    DEBUG, // show individual timings for parts of execution
    TRACE, // debug level timings and logging
    ;

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }

    // #################################################################################################################

    public static final LogLevel DEFAULT;
    static {
        try {
            DEFAULT = parseString(Config.getString("LogLevel.DEFAULT"));
        } catch (final ParamParseException e) {
            throw new RuntimeException(e);
        }
    }

    static LogLevel parseString(final String value) throws ParamParseException {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw new ParamParseException("Could not parse LogLevel: " + value, e);
        }
    }
}
