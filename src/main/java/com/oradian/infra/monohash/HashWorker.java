package com.oradian.infra.monohash;

import com.oradian.infra.monohash.param.Algorithm;
import com.oradian.infra.monohash.util.Format;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.concurrent.atomic.LongAdder;

public final class HashWorker {
    /** Default size was benched across different workloads.
      * There are consistent, but marginal differences for 16KiB, 32KiB, 128KiB (~0.5% slower)
      * This may become configurable in the future, but it's overkill for now. */
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Logger logger;
    private final Algorithm algorithm;
    private final LongAdder bytesHashed;

    private final ByteBuffer buffer;

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
    }

    /** Not thread safe, reuses buffer and digest */
    public byte[] hashFile(final File file) throws IOException {
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
}
