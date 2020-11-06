package com.oradian.infra.monohash;

import com.oradian.infra.monohash.impl.PrintStreamLogger;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

public class MonoHash {
    public static void main(final String[] args) {
        try {
            final CmdLineParser parser = new CmdLineParser(args, logLevel -> new PrintStreamLogger(System.err, logLevel));
            final byte[] totalHash = new MonoHash(parser.logger)
                    .run(parser.hashPlanFile, parser.exportFile, parser.algorithm, parser.concurrency, parser.verification)
                    .totalHash();
            System.out.println(Hex.toHex(totalHash));
            System.exit(0);
        } catch (final CmdLineParser.ExitException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (final Throwable t) {
            t.printStackTrace(System.err);
            System.exit(2);
        }
    }

    private final Logger logger;

    public MonoHash(final Logger logger) {
        this.logger = logger;
    }

    public HashResults run(final File hashPlan, final File export, final String algorithm, final int concurrency, final Verification verification) throws Exception {
        final File planFile = hashPlan.getCanonicalFile();
        if (!planFile.isFile()) {
            throw new IOException("[hash plan file] must point to an existing file, got: " + hashPlan);
        }
        if (logger.isInfoEnabled()) {
            logger.info("Using [hash plan file]: " + planFile + " ...");
        }

        final File exportFile;
        if (export == null) {
            exportFile = null;
            if (logger.isDebugEnabled()) {
                logger.debug("Export file was not defined, skipping export ...");
            }
        } else {
            exportFile = export.getCanonicalFile();
            if (exportFile.isFile()) {
                if (!exportFile.delete()) {
                    throw new IOException("Could not delete previous [export file]: " + exportFile);
                }
            } else {
                if (exportFile.exists()) {
                    throw new IOException("[export file] is not a file: " + exportFile);
                }
            }
            if (logger.isInfoEnabled()) {
                logger.info("Using [export file]: " + exportFile);
            }
        }

        final long parseStartAt = System.currentTimeMillis();
        final HashPlan plan = HashPlan.apply(logger, planFile);
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Parsed hash plan in %1.3f sec", (System.currentTimeMillis() - parseStartAt) / 1000.0));
        }

        final long executeStartAt = System.currentTimeMillis();
        final HashResults hashResults = WhiteWalker.apply(logger, algorithm, plan, concurrency);
        if (logger.isInfoEnabled()) {
            logger.info(String.format("Executed hash plan by hashing %s files in %1.3f sec", nf(hashResults.size()), (System.currentTimeMillis() - executeStartAt) / 1000.0));
        }

        final long exportStartAt = System.currentTimeMillis();
        if (exportFile != null) {
            hashResults.export(exportFile);
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Exported hash results to export file in %1.3f sec", (System.currentTimeMillis() - exportStartAt) / 1000.0));
            }
        }

        return hashResults;
    }

    private static final NumberFormat thousandsSeparatorFormat = NumberFormat.getIntegerInstance(Locale.ROOT);
    static String nf(final long value) {
        return thousandsSeparatorFormat.format(value);
    }
}
