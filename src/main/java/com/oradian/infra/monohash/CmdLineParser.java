package com.oradian.infra.monohash;

import com.oradian.infra.monohash.Logger.Level;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class CmdLineParser {
    private static final String STOP_PARSING_FLAG = "--";

    private enum Option {
        LOG_LEVEL   ("-l", "log level",    "info", () -> ", allowed values: " + printSupportedLogLevels()),
        ALGORITHM   ("-a", "algorithm",    Algorithm.SHA_1, () -> ", some allowed values: " + printSupportedAlgorithms(true)),
        CONCURRENCY ("-c", "concurrency",  String.valueOf(getCurrentDefaultConcurrency()), () -> " - taken from number of CPUs, unless specified here"),
        VERIFICATION("-v", "verification", "off", () -> ", allowed values: " + printSupportedVerifications()),
        ;

        final String flag;
        final String name;
        final String defaultValue;
        final Supplier<String> description;

        Option(
                final String flag,
                final String name,
                final String defaultValue,
                final Supplier<String> description) {
            this.flag = flag;
            this.name = name;
            this.defaultValue = defaultValue;
            this.description = description;
        }

        @Override
        public String toString() {
            return "  " + flag + " <" + name + "> (default: " + defaultValue + description.get() + ")";
        }
    }

    private static ExitException buildExitWithHelp(final String extraMessage, final int exitCode) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);

        pw.println("Usage: java -jar monohash.jar [hash plan file] [export file (optional)]");
        pw.println();
        pw.println("Additional options:");
        for (final Option option : Option.values()) {
            pw.println(option.toString());
        }
        pw.println("  " + STOP_PARSING_FLAG + " stops processing arguments to allow for filenames which may conflict with options above");
        pw.println();

        if (extraMessage != null) {
            pw.println(extraMessage);
            pw.println();
        }

        return new ExitException(sw.toString(), exitCode);
    }

    // -----------------------------------------------------------------------------------------

    final Level logLevel;
    final Algorithm algorithm;
    final int concurrency;
    final Verification verification;

    final Logger logger;
    final String hashPlanPath;
    final String exportPath;

    // -----------------------------------------------------------------------------------------

    private static String seekOption(final List<String> args, final Option option) throws ExitException {
        for (final Iterator<String> i = args.iterator(); i.hasNext(); ) {
            final String arg = i.next();
            if (arg.equals(STOP_PARSING_FLAG)) {
                return null;
            }
            if (arg.startsWith(option.flag)) {
                i.remove();

                String value = arg.substring(option.flag.length());
                if (value.isEmpty()) {
                    if (!i.hasNext()) {
                        throw buildExitWithHelp("Missing value for " + option.name + ", last argument was an alone '" + option.flag + '\'', ExitException.INVALID_ARGUMENT_GENERIC);
                    }

                    value = i.next();
                    i.remove();
                    if (value.equals(STOP_PARSING_FLAG)) {
                        throw buildExitWithHelp("Missing value for " + option.name + ", next argument was the stop flag '" + STOP_PARSING_FLAG + '\'', ExitException.INVALID_ARGUMENT_GENERIC);
                    }
                    if (value.isEmpty()) {
                        throw buildExitWithHelp("Empty value provided for " + option.name, ExitException.INVALID_ARGUMENT_GENERIC);
                    }
                }

                // Seek if value was overridden later, return the last one
                final String override = seekOption(args, option);
                return override != null ? override : value;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------------------------

    private static List<String> getSupportedLogLevels() {
        final List<String> logLevels = new ArrayList<>();
        for (final Level level : Level.values()) {
            logLevels.add(level.name().toLowerCase(Locale.ROOT));
        }
        return logLevels;
    }

    private static String printSupportedLogLevels() {
        return String.join(", ", getSupportedLogLevels());
    }

    private static Level parseLogLevel(final List<String> args) throws ExitException {
        String value = seekOption(args, Option.LOG_LEVEL);
        if (value == null) {
            value = Option.LOG_LEVEL.defaultValue;
        }

        try {
            return Level.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw buildExitWithHelp("Unknown log level: '" + value + "', supported log levels are: " +
                    printSupportedLogLevels(), ExitException.INVALID_ARGUMENT_LOG_LEVEL);
        }
    }

    // -----------------------------------------------------------------------------------------

    private static boolean isAlgorithmNameHumanReadable(final String algorithm) {
        return !algorithm.matches("(?:OID|\\d).*");
    }

    private static String printSupportedAlgorithms(final boolean humanReadable) {
        final SortedMap<String, SortedSet<String>> algorithms = Algorithm.getAlgorithms();
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, SortedSet<String>> entry : algorithms.entrySet()) {
            final String algorithm = entry.getKey();
            if (!humanReadable || isAlgorithmNameHumanReadable(algorithm)) {
                sb.append(entry.getKey());
                final List<String> aliases = entry.getValue().stream()
                        .filter(name -> !humanReadable || isAlgorithmNameHumanReadable(name))
                        .collect(Collectors.toList());
                if (!aliases.isEmpty()) {
                    sb.append(" (aliases: ").append(String.join(", ", aliases)).append(")");
                }
                sb.append(", ");
            }
        }
        if (sb.length() == 0) {
            return "<none>";
        }
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    private static Algorithm parseAlgorithm(final List<String> args) throws ExitException {
        String value = seekOption(args, Option.ALGORITHM);
        if (value == null) {
            value = Option.ALGORITHM.defaultValue;
        }

        try {
            return new Algorithm(value, null);
        } catch (final NoSuchAlgorithmException e) {
            throw buildExitWithHelp("Algorithm '" + value + "' is not supported. Supported algorithms: " +
                    printSupportedAlgorithms(false), ExitException.INVALID_ARGUMENT_ALGORITHM);
        }
    }

    // -----------------------------------------------------------------------------------------

    /** If concurrency is null, it will be sampled in runtime before beginning of work using this method */
    static int getCurrentDefaultConcurrency() {
        return Runtime.getRuntime().availableProcessors();
    }

    private static int parseConcurrency(final List<String> args) throws ExitException {
        final String value = seekOption(args, Option.CONCURRENCY);
        if (value == null) {
            return getCurrentDefaultConcurrency();
        }

        try {
            final int concurrency = Integer.parseInt(value);
            if (concurrency < 1) {
                throw buildExitWithHelp("Concurrency must be a positive integer, got: '" + value + '\'', ExitException.INVALID_ARGUMENT_CONCURRENCY);
            }
            return concurrency;
        } catch (final NumberFormatException e) {
            throw buildExitWithHelp("Invalid concurrency setting: '" + value + "', expecting a positive integer", ExitException.INVALID_ARGUMENT_CONCURRENCY);
        }
    }

    // -----------------------------------------------------------------------------------------

    private static List<String> getSupportedVerifications() {
        final List<String> verifications = new ArrayList<>();
        for (final Verification v : Verification.values()) {
            verifications.add(v.name().toLowerCase(Locale.ROOT));
        }
        return verifications;
    }

    private static String printSupportedVerifications() {
        return String.join(", ", getSupportedVerifications());
    }

    private static Verification parseVerification(final List<String> args) throws ExitException {
        String value = seekOption(args, Option.VERIFICATION);
        if (value == null) {
            value = Option.VERIFICATION.defaultValue;
        }

        try {
            return Verification.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw buildExitWithHelp("Unknown verification: '" + value + "', supported verifications are: " +
                    printSupportedVerifications(), ExitException.INVALID_ARGUMENT_VERIFICATION);
        }
    }

    // -----------------------------------------------------------------------------------------

    CmdLineParser(final String[] args, final Function<Level, Logger> loggerFactory) throws ExitException {
        final List<String> remainingArgs = new ArrayList<>(Arrays.asList(args));

        logLevel = parseLogLevel(remainingArgs);
        logger = loggerFactory.apply(logLevel);

        if (logger.isDebugEnabled()) {
            logger.debug("Parsing arguments:\n" +
                    String.join("\n", args));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Using log level: " + logLevel.name().toLowerCase(Locale.ROOT));
        }

        algorithm = parseAlgorithm(remainingArgs);
        if (logger.isDebugEnabled()) {
            logger.debug("Using algorithm: " + algorithm.name);
        }

        concurrency = parseConcurrency(remainingArgs);
        if (logger.isDebugEnabled()) {
            logger.debug("Using concurrency: " + concurrency);
        }

        verification = parseVerification(remainingArgs);
        if (logger.isDebugEnabled()) {
            logger.debug("Using verification: " + verification.name().toLowerCase(Locale.ROOT));
        }

        if (!remainingArgs.isEmpty() && remainingArgs.get(0).equals(STOP_PARSING_FLAG)) {
            remainingArgs.remove(0);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Remaining arguments after processing additional options:\n" +
                    String.join("\n", remainingArgs));
        }

        if (remainingArgs.isEmpty()) {
            throw buildExitWithHelp("You did not specify the [hash plan file]", ExitException.HASH_PLAN_FILE_MISSING);
        }

        hashPlanPath = remainingArgs.remove(0);
        if (hashPlanPath.isEmpty()) {
            throw buildExitWithHelp("Provided [hash plan file] was an empty string", ExitException.INVALID_ARGUMENT_GENERIC);
        }
        if (remainingArgs.isEmpty()) {
            if (verification == Verification.REQUIRE) {
                throw buildExitWithHelp("[verification] is set to 'require', but [export file] was not provided", ExitException.EXPORT_FILE_REQUIRED_BUT_NOT_PROVIDED);
            }
            exportPath = null;
        } else {
            exportPath = remainingArgs.remove(0);
            if (exportPath.isEmpty()) {
                throw buildExitWithHelp("Provided [export file] was an empty string", ExitException.INVALID_ARGUMENT_GENERIC);
            }
        }

        if (!remainingArgs.isEmpty()) {
            throw buildExitWithHelp("There are too many arguments provided after [hash plan file] and [export file], first was: '" + remainingArgs.get(0) + '\'', ExitException.INVALID_ARGUMENT_TOO_MANY);
        }
    }
}
