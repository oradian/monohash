package com.oradian.infra.monohash.param;

@SuppressWarnings("serial")
public class ParamParseException extends Exception {
    public ParamParseException(final String msg) {
        super(msg);
    }

    public ParamParseException(final String msg, final Throwable cause) {
        super(msg, cause);
    }
}
