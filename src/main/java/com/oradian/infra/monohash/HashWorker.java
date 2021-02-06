package com.oradian.infra.monohash;

import com.oradian.infra.monohash.param.Algorithm;
import com.oradian.infra.monohash.util.Format;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public final class HashWorker {
    /** Default size was benched across different workloads.
      * There are consistent, but marginal differences for 16KiB, 32KiB, 128KiB (~0.5% slower)
      * This may become configurable in the future, but it's overkill for now. */
    private static final int BUFFER_SIZE;
    static {
        BUFFER_SIZE = Integer.parseInt(System.getProperty("BUFFER_SIZE"));
    }

    private final Logger logger;
    private final Algorithm algorithm;
    private final LongAdder bytesHashed;

    private final ByteBuffer buffer;
    private final ByteBuffer buffer2;
    private static final int method;

    static {
        final String hashMethod = System.getProperty("hashMethod");
        if (hashMethod.equals("read")) method = 0;
        else if (hashMethod.equals("baseline")) method = 1;
        else if (hashMethod.equals("parallelViaExecutor")) method = 2;
        else if (hashMethod.equals("parallelViaAtomicSpinlock")) method = 3;
        else if (hashMethod.equals("parallelViaSemaphores")) method = 4;
        else throw new RuntimeException("NO METHOD");
    }

    final ExecutorService es;

    public HashWorker(
            final Logger logger,
            final Algorithm algorithm,
            final LongAdder bytesHashed) {
        this.logger = logger;
        this.algorithm = algorithm;
        this.bytesHashed = bytesHashed;
        // allocateDirect consistently wins over allocate in heap (~1% faster on same BUFFER_SIZE)
        // allocateDirect consistently wins over vanilla byte[] (~3% faster on same BUFFER_SIZE)
        buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        buffer2 = ByteBuffer.allocateDirect(BUFFER_SIZE);
        es = Executors.newSingleThreadExecutor();
    }

    /** Not thread safe, reuses buffer and digest */
    public byte[] hashFile(final File file) throws IOException {
        final long length = file.length();
        if (logger.isTraceEnabled()) {
            logger.trace("Starting to hash file " + Format.file(file) + "(" + file.length() + " bytes)");
        }
        if (length < BUFFER_SIZE * 2) {
            return hashFile_baseline(file);
        }
        try {
            switch (method) {
//                case 0:
//                    return hashFile_read(file);
                case 1:
                    return hashFile_baseline(file);
                case 2:
                    return hashFile_parallelViaExecutor(file);
                case 3:
                    return hashFile_parallelViaAtomicSpinlock(file);
                case 4:
                    return hashFile_parallelViaSemaphores(file);
                default: return null;
            }
        } catch(final Throwable t){
            throw new IOException(t);
        }
    }

    /** Not thread safe, reuses buffer and digest */
    public byte[] hashFile_read(final File file) throws IOException {
        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel fc = raf.getChannel()) {
            final MessageDigest md = algorithm.init(fc::size);

            while (true) {
                buffer.clear();
                final int read = fc.read(buffer);
                if (read == -1) {
                    break;
                }
                buffer.flip();
                bytesHashed.add(read);
            }
            final byte[] result = md.digest();
            if (logger.isTraceEnabled()) {
                logger.trace("Hashed file " + Format.file(file) + "(" + fc.size() + " bytes): " + Format.hex(result) + Format.timeNanos(startAt));
            }
            return result;
        }
    }

    /** Not thread safe, reuses buffer and digest */
    public byte[] hashFile_baseline(final File file) throws IOException {
        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel fc = raf.getChannel()) {
            final MessageDigest md = algorithm.init(fc::size);

            while (true) {
                buffer.clear();
                final int read = fc.read(buffer);
                if (read == -1) {
                    break;
                }
                buffer.flip();
                md.update(buffer);
                bytesHashed.add(read);
            }
            final byte[] result = md.digest();
            if (logger.isTraceEnabled()) {
                logger.trace("Hashed file " + Format.file(file) + ": " + Format.hex(result) + Format.timeNanos(startAt));
            }
            return result;
        }
    }

    public byte[] hashFile_parallelViaExecutor(final File file) throws Throwable {
        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel fc = raf.getChannel()) {
            final MessageDigest md = algorithm.init(fc::size);

            Future<?> hasher1 = null;
            Future<?> hasher2 = CompletableFuture.completedFuture(null);

            final Runnable run1 = () -> md.update(buffer);
            final Runnable run2 = () -> md.update(buffer2);

            while (true) {
                buffer.clear();
                final int read = fc.read(buffer);
                if (read == -1) {
                    break;
                }
                buffer.flip();
                md.update(buffer);
                bytesHashed.add(read);
            }

            while (true) {
                buffer.clear();
                final int read1 = fc.read(buffer);
                if (read1 == -1) {
                    hasher2.get();
                    break;
                }
                buffer.flip();
                hasher2.get();
                hasher1 = es.submit(run1);
                bytesHashed.add(read1);

                buffer2.clear();
                final int read2 = fc.read(buffer2);
                if (read2 == -1) {
                    hasher1.get();
                    break;
                }
                buffer2.flip();

                hasher1.get();
                hasher2 = es.submit(run2);
                bytesHashed.add(read2);
            }

            final byte[] result = md.digest();
            if (logger.isTraceEnabled()) {
                logger.trace("Hashed file " + Format.file(file) + ": " + Format.hex(result) + Format.timeNanos(startAt));
            }
            return result;
        }
    }

    /** Not thread safe, reuses buffer and digest */
    public byte[] hashFile_parallelViaAtomicSpinlock(final File file) throws Throwable {
        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel fc = raf.getChannel()) {
            final MessageDigest md = algorithm.init(fc::size);

            final AtomicBoolean finished1 = new AtomicBoolean(false);
            final AtomicBoolean finished2 = new AtomicBoolean(false);
            final AtomicBoolean okToRead1 = new AtomicBoolean(true);
            final AtomicBoolean okToHash1 = new AtomicBoolean(false);
            final AtomicBoolean okToRead2 = new AtomicBoolean(true);
            final AtomicBoolean okToHash2 = new AtomicBoolean(false);

            final Future<?> work = es.submit(() -> {
                while (true) {
                    while (!okToHash1.get()) {}
                    if (finished1.get()) {
                        return;
                    }
                    md.update(buffer);
                    okToRead1.set(true);
                    okToHash1.set(false);

                    while (!okToHash2.get()) {}
                    if (finished2.get()) {
                        return;
                    }
                    md.update(buffer2);
                    okToRead2.set(true);
                    okToHash2.set(false);
                }
            });

            while (true) {
                while (!okToRead1.get()) {}
                buffer.clear();
                final int read1 = fc.read(buffer);
                if (read1 == -1) {
                    finished1.set(true);
                    okToHash1.set(true);
                    break;
                }
                buffer.flip();
                okToRead1.set(false);
                okToHash1.set(true);
                bytesHashed.add(read1);

                while (!okToRead2.get()) {}
                buffer2.clear();
                final int read2 = fc.read(buffer2);
                if (read2 == -1) {
                    finished2.set(true);
                    okToHash2.set(true);
                    break;
                }
                buffer2.flip();
                okToRead2.set(false);
                okToHash2.set(true);
                bytesHashed.add(read2);
            }

            work.get();

            final byte[] result = md.digest();
            if (logger.isTraceEnabled()) {
                logger.trace("Hashed file " + Format.file(file) + ": " + Format.hex(result) + Format.timeNanos(startAt));
            }
            return result;
        }
    }

    public byte[] hashFile_parallelViaSemaphores(final File file) throws Throwable {
        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel fc = raf.getChannel()) {
            final MessageDigest md = algorithm.init(fc::size);

            final AtomicBoolean finished1 = new AtomicBoolean(false);
            final AtomicBoolean finished2 = new AtomicBoolean(false);
            final Semaphore okToRead1 = new Semaphore(1);
            final Semaphore okToHash1 = new Semaphore(0);
            final Semaphore okToRead2 = new Semaphore(1);
            final Semaphore okToHash2 = new Semaphore(0);

            final Future<?> work = es.submit(() -> {
                try {
                    while (true) {
                        okToHash1.acquire();
                        if (finished1.get()) {
                            return;
                        }
                        md.update(buffer);
                        okToRead1.release();

                        okToHash2.acquire();
                        if (finished2.get()) {
                            return;
                        }
                        md.update(buffer2);
                        okToRead2.release();
                    }
                } catch (final InterruptedException e) {};
            });

            while (true) {
                okToRead1.acquire();
                buffer.clear();
                final int read1 = fc.read(buffer);
                if (read1 == -1) {
                    finished1.set(true);
                    okToHash1.release();
                    break;
                }
                buffer.flip();
                okToHash1.release();
                bytesHashed.add(read1);

                okToRead2.acquire();
                buffer2.clear();
                final int read2 = fc.read(buffer2);
                if (read2 == -1) {
                    finished2.set(true);
                    okToHash2.release();
                    break;
                }
                buffer2.flip();
                okToHash2.release();
                bytesHashed.add(read2);
            }

            work.get();

            final byte[] result = md.digest();
            if (logger.isTraceEnabled()) {
                logger.trace("Hashed file " + Format.file(file) + ": " + Format.hex(result) + Format.timeNanos(startAt));
            }
            return result;
        }
    }
}
