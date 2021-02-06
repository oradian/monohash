package com.oradian.infra.monohash;

import com.oradian.infra.monohash.param.Algorithm;
import com.oradian.infra.monohash.param.Concurrency;
import com.oradian.infra.monohash.util.Format;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

final class WhiteWalker {
    private final Logger logger;
    private final Algorithm algorithm;
    private final HashPlan hashPlan;
    private final Queue<File> workQueue;

    private final Semaphore workersFinished;
    private final AtomicReference<Exception> workerError;
    private final ExecutorService es;

    private final long startAt;
    private final ConcurrentMap<String, byte[]> pathHashes;

    private final LongAdder currentlyProcessing;
    private final LongAdder filesHashed;
    private final LongAdder bytesHashed;

    private WhiteWalker(
            final Logger logger,
            final Algorithm algorithm,
            final HashPlan hashPlan,
            final Queue<File> workQueue,
            final Semaphore workersFinished,
            final AtomicReference<Exception> workerError,
            final ExecutorService es) {
        this.logger = logger;
        this.algorithm = algorithm;
        this.hashPlan = hashPlan;
        this.workQueue = workQueue;

        // workersFinished is a successful semaphore countdown, workerError is "cancel everything, stop work"
        this.workersFinished = workersFinished;
        this.workerError = workerError;
        this.es = es;

        // not really started, but makes sense to calculate time since initialisation
        this.startAt = System.currentTimeMillis();

        // the results, concurrent map for purpose of putIfAbsent
        this.pathHashes = new ConcurrentHashMap<>();

        // number of workers that are currently active, ensures that no worker quits before all others finished their work,
        // because even though the workQueue may be empty - a currently running worker may produce new items on the queue
        this.currentlyProcessing = new LongAdder();

        // some metrics which don't affect the work (for logging purposes)
        this.filesHashed = new LongAdder();
        this.bytesHashed = new LongAdder();
    }

    private static final byte[] EMPTY = new byte[0];

    private void processWorkInQueue(final Logger logger, final String workerId) throws IOException {
        if (logger.isTraceEnabled()) {
            logger.trace("Started worker " + workerId + " ...");
        }
        final HashWorker hasher = new HashWorker(logger, algorithm, bytesHashed, es);
        while (true) {
            if (workerError.get() != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug(workerId + " is shutting down due to error in another worker");
                }
                return;
            }

            final File file;
            synchronized (workQueue) {
                file = workQueue.poll();
                if (file != null) {
                    // work needs to increment inside the synchronization block
                    currentlyProcessing.increment();
                }
            }

            if (file == null) {
                if (currentlyProcessing.longValue() == 0) {
                    if (logger.isTraceEnabled()) {
                        logger.trace(workerId + " is finished");
                    }
                    return;
                }
                continue; // spinlock until new work is available
            }

            final String relativePath = relativise(hashPlan.basePath, file);
            // use the empty array as a marker to reserve this relative path against other
            // concurrent workers who might be about to begin hashing on the same relative path
            if (pathHashes.putIfAbsent(relativePath, EMPTY) == null
                    && (hashPlan.blacklist == null || verify(relativePath, hashPlan.blacklist))) {
                // in case of a directory or a blacklisted path, the empty array will remain as a marker
                // which will be filtered before returning the results
                if (file.isDirectory()) {
                    final File[] children = file.listFiles();
                    if (children == null) {
                        throw new IOException("Could not list children for path: " + Format.dir(file));
                    }
                    synchronized (workQueue) {
                        workQueue.addAll(Arrays.asList(children));
                        currentlyProcessing.decrement();
                    }
                } else {
                    // in case this is one of the last files to process, we need to let the other workers know
                    // immediately unlock to minimise spinlocking and notify that there is no more work for them
                    currentlyProcessing.decrement();

                    final byte[] hash = hasher.hashFile(file);
                    // replace the empty path with the real hash
                    pathHashes.put(relativePath, hash);

                    // increase counters
                    filesHashed.increment();
                }
            }
        }
    }

    private static final int LOGGING_INTERVAL_MS = 1000;

    private void logUntilFinished() {
        // logging takes some time, measure how long it takes to flush to logger and adjust for better precision
        long lastTookMs = 0L;
        long msAdjustment = 0L;

        long lastFiles = -1L;
        boolean finished = false;
        while (!finished) {
            try {
                if (workersFinished.tryAcquire(1, LOGGING_INTERVAL_MS - msAdjustment, TimeUnit.MILLISECONDS)) {
                    finished = true;
                }
                final long filesCount = filesHashed.longValue();
                // don't log the same timing message twice if logger is exiting, only if it has changed in the meantime
                if (!finished || filesCount != lastFiles) {
                    lastFiles = filesCount;
                    if (logger.isInfoEnabled()) {
                        final long bytesHashed = this.bytesHashed.longValue();
                        final long tookMs = System.currentTimeMillis() - startAt;
                        msAdjustment += tookMs - LOGGING_INTERVAL_MS - lastTookMs;
                        lastTookMs = tookMs;
                        final float seconds = tookMs / 1e3f;
                        final int filesSpeed = seconds > 0.0f ? (int) (filesCount / seconds) : 0;
                        final int hashingSpeed = seconds > 0.0f ? (int) (bytesHashed / seconds / (1 << 20)) : 0;
                        final String errorNotice = workerError.get() == null ? "" : " [stopping early due to errors]";
                        logger.info("Hashed " + Format.i(filesCount) +
                                " files with a total of " + Format.i(bytesHashed) +
                                " bytes in " + Format.f(seconds) +
                                " sec (average speed: " + Format.i(filesSpeed) +
                                " files/sec, " + Format.i(hashingSpeed) +
                                " MiB/sec)" + errorNotice);
                    }
                }
            } catch (final InterruptedException e) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Logging thread was interrupted (logger is exiting): " + e.getMessage());
                }
                break;
            }
        }
    }

    /**
     * Pattern matches against the blacklist to figure out if we should process the file or walk into the folder
     */
    private boolean verify(final String relativePath, final Pattern blacklist) {
        final boolean result = !blacklist.matcher(relativePath).matches();
        if (!result || !relativePath.endsWith("/")) {
            return result;
        }

        final String alternativePath = relativePath.substring(0, relativePath.length() - 1);
        final boolean alterResult = !blacklist.matcher(alternativePath).matches();
        if (!alterResult && logger.isWarnEnabled()) {
            logger.warn("Relative path '" + alternativePath +
                    "' is a directory - please append a trailing / to this blacklist pattern");
        }
        return alterResult;
    }

    /**
     * Subtracts the basePath from the child file path.
     * In case of a folder, a superfluous '/' will be added to the end of the directory name, to make pattern-matching more explicit.
     */
    private static String relativise(final String basePath, final File file) {
        final String path = file.getPath().replace('\\', '/') + (file.isDirectory() ? "/" : "");
        if (!path.startsWith(basePath)) {
            throw new IllegalArgumentException("Child path '" + path + "' does not start with '" + basePath + '\'');
        }
        return path.substring(basePath.length());
    }

    public static HashResults apply(final Logger logger, final HashPlan hashPlan, final Algorithm algorithm, final Concurrency concurrency) throws Exception {
        final Queue<File> workQueue = new ArrayDeque<>();
        for (final String relativePath : hashPlan.whitelist) {
            final File file = new File(relativePath);
            if (file.isDirectory() && !relativePath.endsWith("/") && logger.isWarnEnabled()) {
                logger.warn("Relative path '" + relativePath +
                        "' is a directory - please append a trailing / in the [hash plan]");
            }
            workQueue.add(file);
        }

        final int threads = concurrency.getConcurrency();

        final Semaphore workersFinished = new Semaphore(1 - threads);
        final AtomicReference<Exception> workerError = new AtomicReference<>();
        final ExecutorService es = Executors.newCachedThreadPool();
        final WhiteWalker ww = new WhiteWalker(logger, algorithm, hashPlan, workQueue, workersFinished, workerError, es);

        final List<Future<?>> workers = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                final String workerId = "Worker #" + (i + 1);
                workers.add(es.submit(() -> {
                    try {
                        ww.processWorkInQueue(logger, workerId);
                    } catch (final Exception t) {
                        workerError.set(t);
                        if (logger.isErrorEnabled()) {
                            logger.error(workerId + " experienced an exception, shutting down other workers ...");
                        }
                    } finally {
                        workersFinished.release();
                    }
                }));
            }

            ww.logUntilFinished();
            for (final Future<?> worker : workers) {
                try {
                    worker.get();
                } catch (final InterruptedException e) {
                    throw new IOException(e);
                }
            }
            if (workerError.get() != null) {
                throw workerError.get();
            }
        } finally {
            es.shutdown();
        }

        // sort the entries by their relative paths
        // also filter out the directories or the blacklisted entries which retained the empty byte array marker
        final TreeMap<String, byte[]> sortedDigests = new TreeMap<>();
        for (final Map.Entry<String, byte[]> pathHashes : ww.pathHashes.entrySet()) {
            final byte[] hash = pathHashes.getValue();
            if (hash != EMPTY) {
                sortedDigests.put(pathHashes.getKey(), hash);
            }
        }

        return HashResults.apply(logger, algorithm, sortedDigests.entrySet());
    }
}
