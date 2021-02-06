package com.oradian.infra.monohash.param;

import java.util.Locale;

public enum InputMode {
    HASH_PLAN("hash-plan"), // input to MonoHash is a valid HashPlan or a directory
    SINGLE   ("single"),    // input to MonoHash is a singular file to process
    ;
    
    public final String input;
    
    InputMode(final String input) {
        this.input = input;        
    }

    @Override
    public String toString() {
        return input;
    }

    // #################################################################################################################

    public static final InputMode DEFAULT;
    static {
        try {
            DEFAULT = parseString(Config.getString("Input.DEFAULT"));
        } catch (final ParamParseException e) {
            throw new RuntimeException(e);
        }
    }

    static InputMode parseString(final String value) throws ParamParseException {
        final String lowerValue = value.toLowerCase(Locale.ROOT);
        if (lowerValue.equals(HASH_PLAN.input)) {
            return HASH_PLAN;
        } else if (lowerValue.equals(SINGLE.input)) {
            return SINGLE;
        }
        throw new ParamParseException("Could not parse Input: " + value);
    }
}
