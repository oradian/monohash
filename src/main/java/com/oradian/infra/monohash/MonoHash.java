package com.oradian.infra.monohash;

import com.oradian.infra.monohash.impl.PrintStreamLogger;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.Locale;

public class MonoHash {
    public static void main(final String[] args) {
        final int exitCode = main(args, System.out, System.err);
        System.exit(exitCode);
    }

    static int main(final String[] args, final PrintStream out, final PrintStream err) {
        try {
            final CmdLineParser parser = new CmdLineParser(args, logLevel -> new PrintStreamLogger(err, logLevel));
            final byte[] totalHash = new MonoHash(parser.logger).run(parser).totalHash();
            out.println(Hex.toHex(totalHash));
            return ExitException.SUCCESS;
        } catch (final ExitException e) {
            err.println(e.getMessage());
            return e.exitCode;
        } catch (final Throwable t) {
            t.printStackTrace(err);
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

        return run(hashPlanFile, exportFile, parser.algorithm, parser.envelope, parser.concurrency, parser.verification);
    }

    public HashResults run(final File hashPlan, final File export, final String algorithm, final Envelope envelope, final int concurrency, final Verification verification) throws Exception {
        final File planFile = hashPlan.getCanonicalFile();
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

        HashResults previousResults = null;
        final File exportFile;
        if (export == null) {
            if (verification == Verification.REQUIRE) {
                throw new ExitException("[verification] is set to 'require', but [export file] was not provided", ExitException.EXPORT_FILE_REQUIRED_BUT_NOT_PROVIDED);
            }
            exportFile = null;
            if (logger.isDebugEnabled()) {
                logger.debug("Export file was not defined, skipping export ...");
            }
        } else {
            exportFile = export.getCanonicalFile();
            if (exportFile.isFile()) {
                if (verification != Verification.OFF) {
                    try {
                        previousResults = new HashResults(logger, algorithm, exportFile);
                    } catch(final IOException e) {
                        if (verification == Verification.REQUIRE) {
                            throw e;
                        }
                        if (logger.isWarnEnabled()) {
                            logger.warn("Could not parse the previous [export file]: " + e.getMessage());
                        }
                    }
                }
            } else if (exportFile.exists()) {
                throw new ExitException("[export file] is not a file: " + exportFile, ExitException.EXPORT_FILE_IS_NOT_A_FILE);
            } else if (verification == Verification.REQUIRE) {
                throw new ExitException("[verification] is set to 'require', but previous [export file] was not found: " + exportFile, ExitException.EXPORT_FILE_REQUIRED_BUT_NOT_FOUND);
            }
            if (logger.isInfoEnabled()) {
                logger.info("Using [export file]: " + exportFile);
            }
        }

        final long parseStartAt = System.currentTimeMillis();
        final HashPlan plan = HashPlan.apply(logger, planFile);
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Parsed hash plan in %1.3f sec", (System.currentTimeMillis() - parseStartAt) / 1e3));
        }

        final long executeStartAt = System.currentTimeMillis();
        final HashResults hashResults = WhiteWalker.apply(logger, plan, algorithm, envelope, concurrency);
        if (logger.isInfoEnabled()) {
            logger.info(String.format("Executed hash plan by hashing %s files in %1.3f sec", nf(hashResults.size()), (System.currentTimeMillis() - executeStartAt) / 1e3));
        }

        if (previousResults != null) {
            final long diffStartAt = System.currentTimeMillis();
            final HashResults.Diff diff = HashResults.diff(previousResults, hashResults);
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Exported hash results to export file in %1.3f sec", (System.currentTimeMillis() - diffStartAt) / 1e3));
            }
            if (!diff.isEmpty()) {
                if (verification == Verification.WARN) {
                    if (logger.isWarnEnabled()) {
                        for (final String diffLine : diff.toLines()) {
                            logger.warn(diffLine); // logging loop
                        }
                    }
                } else if (verification == Verification.REQUIRE && logger.isErrorEnabled()) {
                    if (logger.isErrorEnabled()) {
                        for (final String diffLine : diff.toLines()) {
                            logger.error(diffLine); // logging loop
                        }
                    }
                    throw new ExitException("[verification] was set to 'require', but there was a difference in export results", ExitException.EXPORT_FILE_VERIFICATION_MISMATCH);
                }
            }
        }

        final long exportStartAt = System.currentTimeMillis();
        if (exportFile != null) {
            hashResults.export(exportFile);
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Exported hash results to export file in %1.3f sec", (System.currentTimeMillis() - exportStartAt) / 1e3));
            }
        }

        return hashResults;
    }

    private static final NumberFormat thousandsSeparatorFormat = NumberFormat.getIntegerInstance(Locale.ROOT);
    static String nf(final long value) {
        return thousandsSeparatorFormat.format(value);
    }
}
