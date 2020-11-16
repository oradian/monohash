package com.oradian.infra.monohash;

@SuppressWarnings("serial")
public class ExitException extends Exception {
    public static final int SUCCESS                               =    0;
    public static final int ERROR_GENERIC                         =    1;

    public static final int INVALID_ARGUMENT_GENERIC              = 1000;
    public static final int INVALID_ARGUMENT_LOG_LEVEL            = 1010;
    public static final int INVALID_ARGUMENT_ALGORITHM            = 1020;
    public static final int INVALID_ARGUMENT_CONCURRENCY          = 1040;
    public static final int INVALID_ARGUMENT_VERIFICATION         = 1050;
    public static final int INVALID_ARGUMENT_TOO_MANY             = 1060;

    public static final int HASH_PLAN_FILE_MISSING                = 2000;
    public static final int HASH_PLAN_FILE_ENDS_WITH_SLASH        = 2010;
    public static final int HASH_PLAN_FILE_NOT_FOUND              = 2020;
    public static final int HASH_PLAN_FILE_CANONICAL_ERROR        = 2030;
    public static final int HASH_PLAN_ERROR_READING               = 2040;

    public static final int EXPORT_FILE_REQUIRED_BUT_NOT_PROVIDED = 3000;
    public static final int EXPORT_FILE_ENDS_WITH_SLASH           = 3010;
    public static final int EXPORT_FILE_IS_NOT_A_FILE             = 3020;
    public static final int EXPORT_FILE_REQUIRED_BUT_NOT_FOUND    = 3030;
    public static final int EXPORT_FILE_VERIFICATION_MISMATCH     = 3040;
    public static final int EXPORT_FILE_CANONICAL_ERROR           = 3050;
    public static final int EXPORT_FILE_REQUIRED_BUT_CANNOT_READ  = 3060;
    public static final int EXPORT_FILE_ERROR_WRITING             = 3070;

    public static final int MONOHASH_EXECUTION_ERROR              = 4000;

    public final int exitCode;

    ExitException(final String msg, final int exitCode) {
        super(msg);
        this.exitCode = exitCode;
    }

    ExitException(final String msg, final int exitCode, final Exception cause) {
        super(msg, cause);
        this.exitCode = exitCode;
    }
}
