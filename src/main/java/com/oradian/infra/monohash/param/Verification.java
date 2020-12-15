package com.oradian.infra.monohash.param;

import java.util.Locale;

public enum Verification {
    OFF,     // don't perform verification against the previous export file
    WARN,    // perform verification if previous export file exists, log differences as WARN
    REQUIRE, // require previous export file, on mismatch log as ERROR and explode
    ;

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }

    // #################################################################################################################

    public static final Verification DEFAULT;
    static {
        try {
            DEFAULT = parseString(Config.getString("Verification.DEFAULT"));
        } catch (final ParamParseException e) {
            throw new RuntimeException(e);
        }
    }

    static Verification parseString(final String value) throws ParamParseException {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw new ParamParseException("Could not parse Verification: " + value, e);
        }
    }
}
