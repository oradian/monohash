package com.oradian.infra.monohash;

import com.oradian.infra.monohash.Logger.Level;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.oradian.infra.monohash.Logger.NL;

class CmdLineParser {
    private static final String STOP_PARSING_FLAG = "--";

    private enum Option {
        LOG_LEVEL   ("-l", "log level",    "info", () -> ", allowed values: " + printSupportedLogLevels()),
        ALGORITHM   ("-a", "algorithm",    "SHA-1", () -> ", some allowed values: " + printSupportedAlgorithms(true)),
        CONCURRENCY ("-c", "concurrency",  String.valueOf(getCurrentDefaultConcurrency()), () -> " - taken from number of CPUs, unless specified here"),
        VERIFICATION("-v", "verification", "warn", () -> ", allowed values: " + printSupportedVerifications()),
        ;

        final String flag;
        final String name;
        final String defaultValue;
        final Supplier<String> description;

        Option(final String flag, final String name, final String defaultValue, final Supplier<String> description) {
            this.flag = flag;
            this.name = name;
            this.defaultValue = defaultValue;
            this.description = description;
        }

        @Override public String toString() {
            return "  " + flag + " <" + name + "> (default: " + defaultValue + description.get() + ")";
        }
    }

    @SuppressWarnings("serial")
    static class ExitException extends Exception {
        ExitException(final String help) {
            super(help);
        }
    }

    private static ExitException buildExitWithHelp(final String extraMessage) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);

        pw.println("Usage: java -jar monohash.jar [hash plan file] [export file (optional)]");
        pw.println();
        pw.println("Additional options:");
        for (final Option option: Option.values()) {
            pw.println(option.toString());
        }
        pw.println("  " + STOP_PARSING_FLAG + " stops processing arguments to allow for filenames which may conflict with options above");
        pw.println();

        if (extraMessage != null) {
            pw.println(extraMessage);
            pw.println();
        }

        return new ExitException(sw.toString());
    }

    // -----------------------------------------------------------------------------------------

    final Level logLevel;
    final String algorithm;
    final int concurrency;
    final Verification verification;

    final Logger logger;
    final File hashPlanFile;
    final File exportFile;

    // -----------------------------------------------------------------------------------------

    private static String seekOption(final List<String> args, final Option option) throws ExitException {
        for (final Iterator<String> i = args.iterator(); i.hasNext();) {
            final String arg = i.next();
            if (arg.equals(STOP_PARSING_FLAG)) {
                return null;
            }
            if (arg.startsWith(option.flag)) {
                i.remove();

                String value = arg.substring(option.flag.length());
                if (value.isEmpty()) {
                    if (!i.hasNext()) {
                        throw buildExitWithHelp("Missing value for " + option.name + ", last argument was an alone '" + option.flag + "'");
                    }

                    value = i.next();
                    i.remove();
                    if (value.equals(STOP_PARSING_FLAG)) {
                        throw buildExitWithHelp("Missing value for " + option.name + ", next argument was the stop flag '" + STOP_PARSING_FLAG + "'");
                    }
                    if (value.isEmpty()) {
                        throw buildExitWithHelp("Empty value provided for " + option.name);
                    }
                }

                // Seek if value was overridden later, return the last one
                final String override = seekOption(args, option);
                return override != null ? override : value;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------------------------------------------------

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
            throw buildExitWithHelp("Unknown log level: '" + value + "', allowed log levels are " + printSupportedLogLevels());
        }
    }

    // -----------------------------------------------------------------------------------------

    private static SortedMap<String, SortedSet<String>> getSupportedAlgorithms() {
        final SortedMap<String, SortedSet<String>> algorithms = new TreeMap<>();
        for (final Provider provider : Security.getProviders()) {
            for (final Provider.Service service : provider.getServices()) {
                if (service.getType().equals("MessageDigest")) {
                    algorithms.putIfAbsent(service.getAlgorithm(), new TreeSet<>());
                }
            }
            for (final Map.Entry<Object, Object> service : provider.entrySet()) {
                final String description = service.getKey().toString();
                if (description.startsWith("Alg.Alias.MessageDigest.")) {
                    final String alias = description.substring("Alg.Alias.MessageDigest.".length());
                    final String key = service.getValue().toString();
                    algorithms.get(key).add(alias);
                }
            }
        }
        return algorithms;
    }

    private static boolean isAlgorithmNameHumanReadible(final String algorithm) {
        return !algorithm.matches("(?:OID|\\d).*");
    }

    private static String printSupportedAlgorithms(final boolean humanReadable) {
        final SortedMap<String, SortedSet<String>> algorithms = getSupportedAlgorithms();
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, SortedSet<String>> entry : algorithms.entrySet()) {
            final String algorithm = entry.getKey();
            if (!humanReadable || isAlgorithmNameHumanReadible(algorithm)) {
                sb.append(entry.getKey());
                final List<String> aliases = entry.getValue().stream()
                        .filter(name -> !humanReadable || isAlgorithmNameHumanReadible(name))
                        .collect(Collectors.toList());
                if (!aliases.isEmpty()) {
                    sb.append(" (aliases: ").append(String.join(", ", aliases)).append(")");
                }
                sb.append(", ");
            }
        }
        if (sb.length() == 0) {
            return "";
        }
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    private static String parseAlgorithm(final List<String> args) throws ExitException {
        String value = seekOption(args, Option.ALGORITHM);
        if (value == null) {
            value = Option.ALGORITHM.defaultValue;
        }

        try {
            MessageDigest.getInstance(value);
            return value;
        } catch (final NoSuchAlgorithmException e) {
            throw buildExitWithHelp("Algorithm '" + value + "' is not supported. Supported algorithms: " + printSupportedAlgorithms(false));
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
                throw buildExitWithHelp("Concurrency must be a positive integer, got: '" + value + "'");
            }
            return concurrency;
        } catch (final NumberFormatException e) {
            throw buildExitWithHelp("Invalid concurrency setting: '" + value + "', expecting a positive integer");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

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
            throw buildExitWithHelp("Unknown verification: '" + value + "', allowed verifications are " + printSupportedLogLevels());
        }
    }

    // -----------------------------------------------------------------------------------------

    CmdLineParser(final String[] args, final Function<Level, Logger> loggerFactory) throws ExitException {
        final List<String> remainingArgs = new ArrayList<>(Arrays.asList(args));

        logLevel = parseLogLevel(remainingArgs);
        logger = loggerFactory.apply(logLevel);

        if (logger.isDebugEnabled()) {
            logger.debug("Parsing arguments:" + NL + String.join(NL, args));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Using log level: " + logLevel.name().toLowerCase(Locale.ROOT));
        }

        algorithm = parseAlgorithm(remainingArgs);
        if (logger.isDebugEnabled()) {
            logger.debug("Using algorithm: " + algorithm);
        }

        concurrency = parseConcurrency(remainingArgs);
        if (logger.isDebugEnabled()) {
            logger.debug("Using concurrency: " + concurrency);
        }

        verification = parseVerification(remainingArgs);
        if (logger.isDebugEnabled()) {
            logger.debug("Using verification: " + verification);
        }

        if (!remainingArgs.isEmpty() && remainingArgs.get(0).equals(STOP_PARSING_FLAG)) {
            remainingArgs.remove(0);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Remaining arguments after processing additional options:" + NL + String.join(NL, remainingArgs));
        }

        if (remainingArgs.isEmpty()) {
            throw buildExitWithHelp("You did not specify the [hash plan file]!");
        }

        final String planPath = remainingArgs.remove(0);
        if (planPath.isEmpty()) {
            throw buildExitWithHelp("Provided [hash plan file] was an empty string!");
        }
        if (planPath.endsWith("/") && logger.isWarnEnabled()) {
            logger.warn( "The hash plan file should not end with a slash: '" + planPath + "' - ignoring the ending slash");
        }
        hashPlanFile = new File(planPath);
        if (remainingArgs.isEmpty()) {
            exportFile = null;
        } else {
            final String exportPath = remainingArgs.remove(0);
            if (exportPath.isEmpty()) {
                throw buildExitWithHelp("Provided [export file] was an empty string!");
            }
            exportFile = new File(exportPath);
        }

        if (!remainingArgs.isEmpty()) {
            throw buildExitWithHelp("There are too many arguments provided after [hash plan file] and [export file], first was: '" + remainingArgs.get(0) + "'");
        }
    }
}
