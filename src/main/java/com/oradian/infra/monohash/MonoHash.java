package com.oradian.infra.monohash;

import com.oradian.infra.monohash.diff.Diff;
import com.oradian.infra.monohash.impl.PrintStreamLogger;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public class MonoHash {
    public static void main(final String[] args) {
        final int exitCode = main(args, System.out, System.err);
        System.exit(exitCode);
    }

    static int main(final String[] args, final PrintStream out, final PrintStream err) {
        try {
            final CmdLineParser parser = new CmdLineParser(args, logLevel -> new PrintStreamLogger(err, logLevel));
            final byte[] hash = new MonoHash(parser.logger).run(parser).hash();
            out.println(Hex.toHex(hash));
            return ExitException.SUCCESS;
        } catch (final ExitException e) {
            err.println(e.getMessage());
            final Throwable cause = e.getCause();
            if (cause != null) {
                cause.printStackTrace(err);
            }
            return e.exitCode;
        } catch (final Throwable t) {
            t.printStackTrace(err);
            t.printStackTrace(System.out);
            return ExitException.ERROR_GENERIC;
        }
    }

    private final Logger logger;

    public MonoHash(final Logger logger) {
        this.logger = logger;
    }

    public HashResults run(final CmdLineParser parser) throws Exception {
        final String hashPlanPath = parser.hashPlanPath.replaceFirst("[\\\\/]+$", "");
        final File hashPlanFile = new File(hashPlanPath);
        if (hashPlanFile.isFile() && !hashPlanPath.equals(parser.hashPlanPath)) {
            throw new ExitException("The [hash plan file] must not end with a slash: " + parser.hashPlanPath, ExitException.HASH_PLAN_FILE_ENDS_WITH_SLASH);
        }

        final File exportFile;
        if (parser.exportPath != null) {
            final String exportPath = parser.exportPath.replaceFirst("[\\\\/]+$", "");
            if (!exportPath.equals(parser.exportPath)) {
                throw new ExitException("The [export file] must not end with a slash: " + parser.exportPath, ExitException.EXPORT_FILE_ENDS_WITH_SLASH);
            }
            exportFile = new File(parser.exportPath);
        } else {
            exportFile = null;
        }

        return run(hashPlanFile, exportFile, parser.algorithm, parser.concurrency, parser.verification);
    }

    private File resolvePlanFile(final File hashPlan) throws ExitException {
        final File planFile;
        try {
            planFile = hashPlan.getCanonicalFile();
        } catch (final IOException e) {
            throw new ExitException("Could not resolve canonical path of [hash plan file]: " + hashPlan, ExitException.HASH_PLAN_FILE_CANONICAL_ERROR);
        }
        if (!planFile.exists()) {
            throw new ExitException("[hash plan file] must point to an existing file or directory, got: " + hashPlan, ExitException.HASH_PLAN_FILE_NOT_FOUND);
        }
        if (logger.isInfoEnabled()) {
            if (planFile.isDirectory()) {
                logger.info("Using [hash plan directory]: " + planFile + " ...");
            } else {
                logger.info("Using [hash plan file]: " + planFile + " ...");
            }
        }
        return planFile;
    }

    private File resolveExportFile(final File export, final Verification verification) throws ExitException {
        if (export == null) {
            if (verification == Verification.REQUIRE) {
                throw new ExitException("[verification] is set to 'require', but [export file] was not provided", ExitException.EXPORT_FILE_REQUIRED_BUT_NOT_PROVIDED);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Export file was not defined, skipping export ...");
            }
            return null;
        }
        try {
            final File exportFile = export.getCanonicalFile();
            if (logger.isInfoEnabled()) {
                logger.info("Using [export file]: " + exportFile);
            }
            return exportFile;
        } catch (final IOException e) {
            throw new ExitException("Could not resolve canonical path of [export file]: " + export, ExitException.EXPORT_FILE_CANONICAL_ERROR, e);
        }
    }

    private HashResults readPreviousExport(final File exportFile, final Algorithm algorithm, final Verification verification) throws ExitException {
        if (exportFile == null) {
            return null;
        }
        if (!exportFile.exists()) {
            if (verification == Verification.REQUIRE) {
                throw new ExitException("[verification] is set to 'require', but previous [export file] was not found: " + exportFile, ExitException.EXPORT_FILE_REQUIRED_BUT_NOT_FOUND);
            }
            return null;
        }
        if (!exportFile.isFile()) {
            throw new ExitException("[export file] is not a file: " + exportFile, ExitException.EXPORT_FILE_IS_NOT_A_FILE);
        }
        try {
            final long startAt = System.nanoTime();
            final HashResults previousResults = HashResults.apply(logger, algorithm, Files.readAllBytes(exportFile.toPath()));
            if (logger.isTraceEnabled()) {
                final long endAt = System.nanoTime();
                logger.trace(String.format("Read previous [export file] '%s' (in %1.3f ms)", exportFile, (endAt - startAt) / 1e6));
            }
            return previousResults;
        } catch (final IOException e) {
            if (verification == Verification.REQUIRE) {
                throw new ExitException("[verification] is set to 'require', but previous [export file] could not be read: " + exportFile, ExitException.EXPORT_FILE_REQUIRED_BUT_CANNOT_READ, e);
            }
            if (verification == Verification.WARN && logger.isWarnEnabled()) {
                logger.warn("Could not read the previous [export file]: " + e.getMessage());
            }
            return null;
        }
    }

    private HashPlan parseHashPlan(final File planFile) throws ExitException {
        final long parseStartAt = System.currentTimeMillis();
        try {
            final HashPlan plan = HashPlan.apply(logger, planFile);
            if (logger.isDebugEnabled()) {
                final long parseEndAt = System.currentTimeMillis();
                logger.debug(String.format("Parsed hash plan in %1.3f sec", (parseEndAt - parseStartAt) / 1e3));
            }
            return plan;
        } catch (final IOException e) {
            throw new ExitException("Error reading [hash plan] file: " + planFile, ExitException.HASH_PLAN_CANNOT_READ, e);
        }
    }

    private HashResults executeHashPlan(final HashPlan plan, final Algorithm algorithm, final int concurrency) throws ExitException {
        final long executeStartAt = System.currentTimeMillis();
        try {
            final HashResults hashResults = WhiteWalker.apply(logger, plan, algorithm, concurrency);
            if (logger.isInfoEnabled()) {
                final long executeEndAt = System.currentTimeMillis();
                logger.info(String.format("Executed hash plan by hashing %s files in %1.3f sec", nf(hashResults.size()), (executeEndAt - executeStartAt) / 1e3));
            }
            return hashResults;
        } catch (final Exception e) {
            throw new ExitException("Error executing [hash plan]: " + plan, ExitException.MONOHASH_EXECUTION_ERROR, e);
        }
    }

    private void logDiff(final HashResults previousResults, final HashResults newResults, final Verification verification) {
        final boolean logWarn = verification == Verification.WARN && logger.isWarnEnabled();
        final boolean logError = verification == Verification.REQUIRE && logger.isErrorEnabled();

        if (logWarn || logError) {
            String msg = "";
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
                    final long endAt = System.nanoTime();
                    logger.debug(String.format("Diffed against previous export (in %1.3f ms)", (endAt - startAt) / 1e6));
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

    private void exportResults(
            final File exportFile,
            final HashResults previousResults,
            final HashResults newResults,
            final Verification verification) throws ExitException {

        if (exportFile == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Skipping export");
            }
            return;
        }

        if (previousResults == null) {
            // should not happen with REQUIRE as it should have short-circuited
            logDiff(null, newResults, verification);
        } else {
            if (newResults.equals(previousResults)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Previous hash result was identical, no need to update the [export file]: " + exportFile);
                }
                return;
            } else {
                logDiff(previousResults, newResults, verification);
                if (verification == Verification.REQUIRE) {
                    throw new ExitException("[verification] was set to 'require', but there was a difference in export results", ExitException.EXPORT_FILE_VERIFICATION_MISMATCH);
                }
            }
        }
        try {
            newResults.export(exportFile);
        } catch (final IOException e) {
            throw new ExitException("Error occurred while writing to [export file]: " + exportFile, ExitException.EXPORT_FILE_CANNOT_WRITE, e);
        }
    }

    public HashResults run(final File hashPlan, final File export, final Algorithm algorithm, final int concurrency, final Verification verification) throws ExitException {
        final File planFile = resolvePlanFile(hashPlan);
        final File exportFile = resolveExportFile(export, verification);
        final HashResults previousResults = readPreviousExport(exportFile, algorithm, verification);

        final HashPlan plan = parseHashPlan(planFile);
        final HashResults hashResults = executeHashPlan(plan, algorithm, concurrency);

        exportResults(exportFile, previousResults, hashResults, verification);
        return hashResults;
    }

    private static final NumberFormat thousandsSeparatorFormat = NumberFormat.getIntegerInstance(Locale.ROOT);
    static String nf(final long value) {
        return thousandsSeparatorFormat.format(value);
    }
}
