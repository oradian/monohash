package com.oradian.infra.monohash;

import com.oradian.infra.monohash.diff.Diff;
import com.oradian.infra.monohash.impl.PrintStreamLogger;
import com.oradian.infra.monohash.param.*;
import com.oradian.infra.monohash.util.Format;
import com.oradian.infra.monohash.util.Hex;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public final class MonoHash {
    public static void main(final String[] args) {
        final int exitCode = main(args, System.out, System.err);
        System.exit(exitCode);
    }

    static int main(final String[] argsx, final PrintStream out, final PrintStream err) {
//        System.setProperty("BUFFER_SIZE", "65536");
//        System.setProperty("hashMethod", "parallelViaSemaphores");
//        final String[] args = { "-ldebug", "s:\\w2" }; //\\linux-5.11-rc6\\lib\\zstd\\" };
//        final String[] args = { "-ltrace", "s:\\w2\\linux-5.11-rc6\\lib\\zstd\\" };
        final String[] args = argsx;

        try {
            final Function<LogLevel, Logger> loggerFactory = logLevel -> new PrintStreamLogger(err, logLevel);
            final byte[] hash = CmdLineParser.parse(Arrays.asList(args), loggerFactory).run().hash();
            out.println(Hex.toHex(hash));
            return ExitException.SUCCESS;
        } catch (final ExitException e) {
            err.println(e.getMessage().replace("\n", PrintStreamLogger.NL));
            final Throwable cause = e.getCause();
            if (cause != null) {
                cause.printStackTrace(err);
            }
            return e.exitCode;
        } catch (final Throwable t) {
            t.printStackTrace(err);
            return ExitException.ERROR_GENERIC;
        }
    }

    private MonoHash() {}

    public static MonoHashBuilder withLogger(final Logger logger) {
        return MonoHashBuilder.DEFAULT.withLogger(logger);
    }

    public static MonoHashBuilder withAlgorithm(final Algorithm algorithm) {
        return MonoHashBuilder.DEFAULT.withAlgorithm(algorithm);
    }

    public static MonoHashBuilder withConcurrency(final Concurrency concurrency) {
        return MonoHashBuilder.DEFAULT.withConcurrency(concurrency);
    }

    public static MonoHashBuilder withVerification(final Verification verification) {
        return MonoHashBuilder.DEFAULT.withVerification(verification);
    }

    public static MonoHashBuilder.Ready withHashPlan(final File hashPlan) {
        return MonoHashBuilder.DEFAULT.withHashPlan(hashPlan);
    }

    public static MonoHashBuilder withExport(final File export) {
        return MonoHashBuilder.DEFAULT.withExport(export);
    }

    private static File resolvePlanFile(final Logger logger, final File hashPlan) throws ExitException {
        final File planFile;
        try {
            planFile = hashPlan.getCanonicalFile();
        } catch (final IOException e) {
            throw new ExitException("Could not resolve canonical path of [hash plan file]: " + Format.file(hashPlan),
                    ExitException.HASH_PLAN_FILE_CANONICAL_ERROR);
        }
        if (!planFile.exists()) {
            throw new ExitException("[hash plan file] must point to an existing file or directory, got: " + Format.file(hashPlan),
                    ExitException.HASH_PLAN_FILE_NOT_FOUND);
        }
        if (logger.isInfoEnabled()) {
            if (planFile.isDirectory()) {
                logger.info("Using [hash plan directory]: " + Format.dir(planFile) + " ...");
            } else {
                logger.info("Using [hash plan file]: " + Format.file(planFile) + " ...");
            }
        }
        return planFile;
    }

    private static File resolveExportFile(final Logger logger, final File export, final Verification verification) throws ExitException {
        if (export == null) {
            if (verification == Verification.REQUIRE) {
                throw new ExitException("[verification] is set to 'require', but [export file] was not provided",
                        ExitException.EXPORT_FILE_REQUIRED_BUT_NOT_PROVIDED);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Export file was not defined, skipping export ...");
            }
            return null;
        }
        try {
            final File exportFile = export.getCanonicalFile();
            if (logger.isInfoEnabled()) {
                logger.info("Using [export file]: " + Format.file(exportFile));
            }
            return exportFile;
        } catch (final IOException e) {
            throw new ExitException("Could not resolve canonical path of [export file]: " + Format.file(export),
                    ExitException.EXPORT_FILE_CANONICAL_ERROR, e);
        }
    }

    private static HashResults readPreviousExport(final Logger logger, final File exportFile, final Algorithm algorithm, final Verification verification) throws ExitException {
        if (exportFile == null) {
            return null;
        }
        if (!exportFile.exists()) {
            if (verification == Verification.REQUIRE) {
                throw new ExitException("[verification] is set to 'require', but previous [export file] was not found: " +
                        Format.file(exportFile), ExitException.EXPORT_FILE_REQUIRED_BUT_NOT_FOUND);
            }
            return null;
        }
        if (!exportFile.isFile()) {
            throw new ExitException("[export file] is not a file: " + Format.dir(exportFile),
                    ExitException.EXPORT_FILE_IS_NOT_A_FILE);
        }
        try {
            final long startAt = System.nanoTime();
            final HashResults previousResults = HashResults.apply(logger, algorithm, Files.readAllBytes(exportFile.toPath()));
            if (logger.isTraceEnabled()) {
                logger.trace("Read previous [export file]: " + Format.file(exportFile) + Format.timeNanos(startAt));
            }
            return previousResults;
        } catch (final IOException e) {
            if (verification == Verification.REQUIRE) {
                throw new ExitException("[verification] is set to 'require', but previous [export file] could not be read: " +
                        Format.file(exportFile), ExitException.EXPORT_FILE_REQUIRED_BUT_CANNOT_READ, e);
            }
            if (verification == Verification.WARN && logger.isWarnEnabled()) {
                logger.warn("Could not read the previous [export file]: " + e.getMessage());
            }
            return null;
        }
    }

    private static HashPlan parseHashPlan(final Logger logger, final File planFile) throws ExitException {
        final long startAt = System.nanoTime();
        try {
            final HashPlan plan = HashPlan.apply(logger, planFile);
            if (logger.isDebugEnabled()) {
                logger.debug("Parsed hash plan" + Format.timeNanos(startAt));
            }
            return plan;
        } catch (final IOException e) {
            throw new ExitException("Error reading [hash plan] file: " + Format.file(planFile),
                    ExitException.HASH_PLAN_CANNOT_READ, e);
        }
    }

    private static HashResults executeHashPlan(final Logger logger, final HashPlan plan, final Algorithm algorithm, final Concurrency concurrency) throws ExitException {
        final long startAt = System.currentTimeMillis();
        try {
            final HashResults hashResults = WhiteWalker.apply(logger, plan, algorithm, concurrency);
            if (logger.isInfoEnabled()) {
                logger.info("Executed hash plan by hashing " + Format.i(hashResults.size()) + " files: " +
                        Format.hex(hashResults.hash()) + Format.timeMillis(startAt));
            }
            return hashResults;
        } catch (final Exception e) {
            throw new ExitException("Error executing [hash plan]: '" + plan.basePath + '\'',
                    ExitException.MONOHASH_EXECUTION_ERROR, e);
        }
    }

    private static void logDiff(final Logger logger, final HashResults previousResults, final HashResults newResults, final Verification verification) {
        final boolean logWarn = verification == Verification.WARN && logger.isWarnEnabled();
        final boolean logError = verification == Verification.REQUIRE && logger.isErrorEnabled();

        if (logWarn || logError) {
            String msg;
            try {
                if (logger.isTraceEnabled()) {
                    logger.trace("Diffing against previous export ...");
                }
                final long startAt = System.nanoTime();
                final Map<String, byte[]> previousMap = previousResults == null
                        ? Collections.emptyMap()
                        : previousResults.toMap();
                final Diff diff = Diff.apply(previousMap, newResults.toMap());
                if (logger.isDebugEnabled()) {
                    logger.debug("Diffed against previous export" + Format.timeNanos(startAt));
                }
                if (diff.isEmpty()) {
                    msg = previousResults != null
                            ? "Running diff against previous [export file] produced no differences, but the exports were not identical"
                            : "Previous [export file] were not read and there were no entries in current run to build a diff from";
                } else {
                    msg = diff.toString();
                }
            } catch (final ExportParsingException e) {
                msg = "Could not diff against the previous [export file]: " + e.getMessage();
            }
            if (logWarn) {
                logger.warn(msg);
            } else {
                logger.error(msg);
            }
        }
    }

    private static void exportResults(
            final Logger logger,
            final File exportFile,
            final HashResults previousResults,
            final HashResults newResults,
            final Verification verification) throws ExitException {

        if (exportFile == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Skipping export ...");
            }
            return;
        }

        if (previousResults == null) {
            // should not happen with REQUIRE as it should have short-circuited
            logDiff(logger, null, newResults, verification);
        } else {
            if (newResults.equals(previousResults)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Previous hash result was identical, no need to update the [export file]: " +
                            Format.file(exportFile));
                }
                return;
            } else {
                logDiff(logger, previousResults, newResults, verification);
                if (verification == Verification.REQUIRE) {
                    throw new ExitException("[verification] was set to 'require', but there was a difference in export results",
                            ExitException.EXPORT_FILE_VERIFICATION_MISMATCH);
                }
            }
        }
        try {
            newResults.export(exportFile);
        } catch (final IOException e) {
            throw new ExitException("Error occurred while writing to [export file]: " + Format.file(exportFile),
                    ExitException.EXPORT_FILE_CANNOT_WRITE, e);
        }
    }

    public static HashResults run(
            final Logger logger,
            final Algorithm algorithm,
            final Concurrency concurrency,
            final Verification verification,
            final File hashPlan,
            final File export) throws ExitException {
        final File planFile = resolvePlanFile(logger, hashPlan);
        final File exportFile = resolveExportFile(logger, export, verification);
        final HashResults previousResults = readPreviousExport(logger, exportFile, algorithm, verification);

        final HashPlan plan = parseHashPlan(logger, planFile);
        final HashResults hashResults = executeHashPlan(logger, plan, algorithm, concurrency);

        exportResults(logger, exportFile, previousResults, hashResults, verification);
        return hashResults;
    }
}
