package com.oradian.infra.monohash.param;

import com.oradian.infra.monohash.ExitException;
import com.oradian.infra.monohash.Logger;
import com.oradian.infra.monohash.MonoHash;
import com.oradian.infra.monohash.MonoHashBuilder;
import com.oradian.infra.monohash.util.Format;

import java.io.File;
import java.util.*;
import java.util.function.Function;

public abstract class CmdLineParser {
    private CmdLineParser() {}

    private static final String STOP_PARSING_FLAG = "--";

    private enum Option {
        LOG_LEVEL   ("-l", "log level",    LogLevel.DEFAULT, ", allowed values: " + formatSupportedLogLevels()),
        ALGORITHM   ("-a", "algorithm",    Algorithm.DEFAULT.name, ", some allowed values: " + formatSupportedAlgorithms(false)),
        CONCURRENCY ("-c", "concurrency",  Concurrency.DEFAULT.getConcurrency(), " - taken from number of CPUs"),
        VERIFICATION("-v", "verification", Verification.DEFAULT, ", allowed values: " + formatSupportedVerifications()),
        ;

        final String flag;
        final String name;
        final Object defaultValue;
        final String description;

        Option(
                final String flag,
                final String name,
                final Object defaultValue,
                final String description) {
            this.flag = flag;
            this.name = name;
            this.defaultValue = defaultValue;
            this.description = description;
        }
    }

    private static ExitException buildExitWithHelp(final String message, final int exitCode) {
        final StringBuilder sb = new StringBuilder(
                "Usage: java -jar monohash.jar <options> [hash plan file] [export file (optional)]\n\n" +
                "Options:\n");

        for (final Option option : Option.values()) {
            sb.append("  ").append(option.flag).append(" <").append(option.name)
                    .append("> (default: ").append(option.defaultValue)
                    .append(option.description).append(")\n");
        }
        sb.append("  ")
                .append(STOP_PARSING_FLAG)
                .append(" stops parsing options to allow for filenames which may conflict with options above\n\n")
                .append(message).append('\n');
        return new ExitException(sb.toString(), exitCode);
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static String seekOption(final Queue<String> remainingArgs, final Option option) throws ExitException {
        for (final Iterator<String> i = remainingArgs.iterator(); i.hasNext(); ) {
            final String arg = i.next();
            if (arg.equals(STOP_PARSING_FLAG)) {
                return null;
            }
            if (arg.startsWith(option.flag)) {
                i.remove();

                String value = arg.substring(option.flag.length());
                if (value.isEmpty()) {
                    if (!i.hasNext()) {
                        throw buildExitWithHelp("Missing value for " + option.name +
                                ", last argument was an alone '" + option.flag + '\'',
                                ExitException.INVALID_ARGUMENT_GENERIC);
                    }

                    value = i.next();
                    i.remove();
                    if (value.equals(STOP_PARSING_FLAG)) {
                        throw buildExitWithHelp("Missing value for " + option.name +
                                ", next argument was the stop flag '" + STOP_PARSING_FLAG + '\'',
                                ExitException.INVALID_ARGUMENT_GENERIC);
                    }
                    if (value.isEmpty()) {
                        throw buildExitWithHelp("Empty value provided for " + option.name,
                                ExitException.INVALID_ARGUMENT_GENERIC);
                    }
                }

                // Seek if value was overridden later, return the last one
                final String override = seekOption(remainingArgs, option);
                return override != null ? override : value;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static String formatSupportedLogLevels() {
        final StringBuilder sb = new StringBuilder();
        for (final LogLevel logLevel : LogLevel.values()) {
            sb.append(logLevel).append(", ");
        }
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    private static LogLevel parseLogLevel(final Queue<String> remainingArgs) throws ExitException {
        final String value = seekOption(remainingArgs, Option.LOG_LEVEL);
        try {
            return value == null ? null : LogLevel.parseString(value);
        } catch (final ParamParseException e) {
            throw buildExitWithHelp("Unknown log level: '" + value + "', supported log levels are: " +
                    formatSupportedLogLevels(), ExitException.INVALID_ARGUMENT_LOG_LEVEL);
        }
    }

    private static Logger buildLogger(final Queue<String> remainingArgs, final Function<LogLevel, Logger> loggerFactory) throws ExitException {
        final LogLevel logLevel = parseLogLevel(remainingArgs);
        final Logger logger = loggerFactory.apply(logLevel == null ? LogLevel.DEFAULT : logLevel);
        if (logLevel != null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Parsed log level: " + logLevel);
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Using log level: " + logLevel);
        }
        return logger;
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static String formatSupportedAlgorithms(final boolean showAliases) {
        final SortedMap<String, SortedSet<String>> algorithms = Algorithm.getAlgorithms();
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, SortedSet<String>> entry : algorithms.entrySet()) {
            sb.append(entry.getKey());
            final SortedSet<String> aliases = entry.getValue();
            if (showAliases && !aliases.isEmpty()) {
                sb.append(" (aliases: ").append(String.join(", ", aliases)).append(")");
            }
            sb.append(", ");
        }
        if (sb.length() == 0) {
            return "<none>";
        }
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    private static Algorithm parseAlgorithm(final Queue<String> remainingArgs, final Logger logger) throws ExitException {
        final String algorithm = seekOption(remainingArgs, Option.ALGORITHM);
        try {
            final Algorithm result;
            if (algorithm == null) {
                result = Algorithm.DEFAULT;
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("Parsed algorithm: " + algorithm);
                }
                result = Algorithm.parseString(algorithm);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Using algorithm: " + result.name);
            }
            return result;
        } catch (final ParamParseException e) {
            throw buildExitWithHelp("Algorithm '" + algorithm + "' is not supported. Supported algorithms: " +
                    formatSupportedAlgorithms(true), ExitException.INVALID_ARGUMENT_ALGORITHM);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static Concurrency parseConcurrency(final Queue<String> remainingArgs, final Logger logger) throws ExitException {
        final String concurrency = seekOption(remainingArgs, Option.CONCURRENCY);
        try {
            final Concurrency result;
            if (concurrency == null) {
                result = Concurrency.DEFAULT;
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("Parsed concurrency: " + concurrency);
                }
                result = Concurrency.parseString(concurrency);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Using concurrency: " + result.getConcurrency());
            }
            return result;
        } catch (final ParamParseException e) {
            throw buildExitWithHelp(e.getMessage(), ExitException.INVALID_ARGUMENT_CONCURRENCY);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static String formatSupportedVerifications() {
        final StringBuilder sb = new StringBuilder();
        for (final Verification verification : Verification.values()) {
            sb.append(verification).append(", ");
        }
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    private static Verification parseVerification(final Queue<String> remainingArgs, final Logger logger) throws ExitException {
        final String verification = seekOption(remainingArgs, Option.VERIFICATION);
        try {
            final Verification result;
            if (verification == null) {
                result = Verification.DEFAULT;
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("Parsed verification: " + verification);
                }
                result = Verification.parseString(verification);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Using verification: " + result);
            }
            return result;
        } catch (final ParamParseException e) {
            throw buildExitWithHelp("Unknown verification: '" + verification + "', supported verifications are: " +
                    formatSupportedVerifications(), ExitException.INVALID_ARGUMENT_VERIFICATION);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static void skipStopFlag(final Queue<String> remainingArgs, final Logger logger) {
        if (!remainingArgs.isEmpty() && remainingArgs.peek().equals(STOP_PARSING_FLAG)) {
            if (logger.isTraceEnabled()) {
                logger.trace("Skipping stop flag ...");
            }
            remainingArgs.remove();
        }
    }

    private static File parseHashPlan(final Queue<String> remainingArgs, final Logger logger) throws ExitException {
        final String hashPlanPath = remainingArgs.poll();
        if (hashPlanPath == null) {
            throw buildExitWithHelp("You did not specify the [hash plan file]",
                    ExitException.HASH_PLAN_FILE_MISSING);
        }

        if (hashPlanPath.isEmpty()) {
            throw buildExitWithHelp("Provided [hash plan file] was an empty string",
                    ExitException.INVALID_ARGUMENT_GENERIC);
        }

        final String hashPlanPathTrimmed = hashPlanPath.replaceFirst("[\\\\/]+$", "");
        final File hashPlanFile = new File(hashPlanPathTrimmed);
        if (hashPlanFile.isFile() && !hashPlanPathTrimmed.equals(hashPlanPath)) {
            throw new ExitException("The [hash plan file] must not end with a slash: '" + hashPlanPath + '\'',
                    ExitException.HASH_PLAN_FILE_ENDS_WITH_SLASH);
        }
        return hashPlanFile;
    }

    private static File parseExport(final Queue<String> remainingArgs, final Logger logger, final Verification verification) throws ExitException {
        final String exportPath = remainingArgs.poll();
        if (exportPath == null) {
            if (verification == Verification.REQUIRE) {
                throw buildExitWithHelp("[verification] is set to 'require', but [export file] was not provided",
                        ExitException.EXPORT_FILE_REQUIRED_BUT_NOT_PROVIDED);
            }
            return null;
        }

        if (exportPath.isEmpty()) {
            throw buildExitWithHelp("Provided [export file] was an empty string",
                    ExitException.INVALID_ARGUMENT_GENERIC);
        }

        final String exportPathTrimmed = exportPath.replaceFirst("[\\\\/]+$", "");
        if (!exportPath.equals(exportPathTrimmed)) {
            throw new ExitException("The [export file] must not end with a slash: '" + exportPath + '\'',
                    ExitException.EXPORT_FILE_ENDS_WITH_SLASH);
        }
        return new File(exportPath);
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static void checkForSuperfluousArgs(final Queue<String> remainingArgs) throws ExitException {
        if (!remainingArgs.isEmpty()) {
            throw buildExitWithHelp("There are too many arguments provided after [hash plan file] and " +
                    "[export file], first was: '" + remainingArgs.peek() + '\'',
                    ExitException.INVALID_ARGUMENT_TOO_MANY);
        }
    }

    public static MonoHashBuilder.Ready parse(final List<String> args, final Function<LogLevel, Logger> loggerFactory) throws ExitException {
        final ArrayDeque<String> remainingArgs = new ArrayDeque<>(args);
        final Logger logger = buildLogger(remainingArgs, loggerFactory);

        if (logger.isDebugEnabled()) {
            logger.debug(Format.lines("Parsing arguments", args));
        }
        final Algorithm algorithm = parseAlgorithm(remainingArgs, logger);
        final Concurrency concurrency = parseConcurrency(remainingArgs, logger);
        final Verification verification = parseVerification(remainingArgs, logger);

        skipStopFlag(remainingArgs, logger);
        if (logger.isTraceEnabled()) {
            logger.trace(Format.lines("Remaining arguments after processing options", remainingArgs));
        }
        final File hashPlan = parseHashPlan(remainingArgs, logger);
        final File export = parseExport(remainingArgs, logger, verification);
        checkForSuperfluousArgs(remainingArgs);

        return MonoHash
                .withLogger(logger)
                .withAlgorithm(algorithm)
                .withConcurrency(concurrency)
                .withVerification(verification)
                .withHashPlan(hashPlan)
                .withExport(export);
    }
}
