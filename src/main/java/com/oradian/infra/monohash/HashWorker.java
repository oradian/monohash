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
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Logger logger;
    private final Algorithm algorithm;
    private final LongAdder bytesHashed;
    private final ExecutorService es;

    private final ByteBuffer buffer0;
    private final ByteBuffer[] largeBuffers = new ByteBuffer[2];

    public HashWorker(
            final Logger logger,
            final Algorithm algorithm,
            final LongAdder bytesHashed,
            final ExecutorService es) {
        this.logger = logger;
        this.algorithm = algorithm;
        this.bytesHashed = bytesHashed;
        this.es = es;

        // allocateDirect consistently wins over allocate in heap (~1% faster on same BUFFER_SIZE)
        // allocateDirect consistently wins over vanilla byte[] (~3% faster on same BUFFER_SIZE)
        buffer0 = ByteBuffer.allocateDirect(BUFFER_SIZE);
    }

    /** Not thread safe, reuses buffer and digest */
    public byte[] hashFile(final File file) throws IOException {
        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel fc = raf.getChannel()) {
            final long size = fc.size();
            final MessageDigest md = algorithm.init(size);

            if (size < BUFFER_SIZE * 8) {
                hashSmall(fc, md);
            } else {
                if (System.getProperty("hashMethod").equals("1")) {
                    hashLarge(fc, md);
                } else if (System.getProperty("hashMethod").equals("2")) {
                    hashLarge2(fc, md);
                } else {
                    throw new RuntimeException("TTETETETETE");
                }
            }

            final byte[] result = md.digest();
            if (logger.isTraceEnabled()) {
                logger.trace("Hashed file " + Format.file(file) + ": " + Format.hex(result) + Format.timeNanos(startAt));
            }
            return result;
        }
    }

    private void hashSmall(final FileChannel fc, final MessageDigest md) throws IOException {
        while (true) {
            buffer0.clear();
            final int read = fc.read(buffer0);
            if (read == -1) {
                break;
            }
            buffer0.flip();
            md.update(buffer0);
            bytesHashed.add(read);
        }
    }

    private void hashLarge(final FileChannel fc, final MessageDigest md) throws IOException {
        final AtomicBoolean finished1 = new AtomicBoolean(false);
        final AtomicBoolean finished2 = new AtomicBoolean(false);

        final Semaphore okToRead1 = new Semaphore(1);
        final Semaphore okToHash1 = new Semaphore(0);
        final Semaphore okToRead2 = new Semaphore(1);
        final Semaphore okToHash2 = new Semaphore(0);

        if (largeBuffers[0] == null) {
            largeBuffers[0] = ByteBuffer.allocateDirect(BUFFER_SIZE * 4);
            largeBuffers[1] = ByteBuffer.allocateDirect(BUFFER_SIZE * 4);
        }

        final ByteBuffer buffer1 = largeBuffers[0];
        final ByteBuffer buffer2 = largeBuffers[1];

        final Future<?> work = es.submit(() -> {
            try {
                while (true) {
                    okToHash1.acquire();
                    if (finished1.get()) {
                        return;
                    }
                    final int remaining1 = buffer1.remaining();
                    md.update(buffer1);
                    bytesHashed.add(remaining1);
                    okToRead1.release();

                    okToHash2.acquire();
                    if (finished2.get()) {
                        return;
                    }
                    final int remaining2 = buffer2.remaining();
                    md.update(buffer2);
                    bytesHashed.add(remaining2);
                    okToRead2.release();
                }
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        try {
            while (true) {
                okToRead1.acquire();
                buffer1.clear();
                final int read1 = fc.read(buffer1);
                if (read1 == -1) {
                    finished1.set(true);
                    okToHash1.release();
                    break;
                }
                buffer1.flip();
                okToHash1.release();

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
            }
        } catch (final InterruptedException e) {
            throw new IOException(e);
        }

        try {
            work.get();
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    private void hashLarge2(final FileChannel fc, final MessageDigest md) throws IOException {
        if (largeBuffers[0] == null) {
            largeBuffers[0] = ByteBuffer.allocateDirect(BUFFER_SIZE * 4);
            largeBuffers[1] = ByteBuffer.allocateDirect(BUFFER_SIZE * 4);
        }

        final ByteBuffer buffer1 = largeBuffers[0];
        final ByteBuffer buffer2 = largeBuffers[1];

        final Runnable run1 = () -> md.update(buffer1);
        final Runnable run2 = () -> md.update(buffer2);

        Future<?> hasher1;
        Future<?> hasher2 = CompletableFuture.completedFuture(null);

        int read1;
        int read2 = 0;

        try {
            while (true) {
                buffer1.clear();
                read1 = fc.read(buffer1);
                buffer1.flip();
                hasher2.get();
                bytesHashed.add(read2);
                if (read1 == -1) {
                    break;
                }
                hasher1 = es.submit(run1);

                buffer2.clear();
                read2 = fc.read(buffer2);
                buffer2.flip();
                hasher1.get();
                bytesHashed.add(read1);
                if (read2 == -1) {
                    break;
                }
                hasher2 = es.submit(run2);
            }
        } catch (final ExecutionException | InterruptedException e) {
            throw new IOException(e);
        }
    }
}
