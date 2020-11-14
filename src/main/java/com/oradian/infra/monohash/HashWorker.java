package com.oradian.infra.monohash;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.LongAdder;

public class HashWorker {
    /** Default size was benched across different workloads.
      * There are consistent, but marginal differences for 16KiB, 32KiB, 128KiB (~0.5% slower)
      * This may become configurable in the future, but it's overkill for now. */
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Logger logger;
    private final MessageDigest digest;
    private final Envelope envelope;
    private final LongAdder bytesHashed;

    private final ByteBuffer buffer;
    public final int lengthInBytes;

    public HashWorker(
            final Logger logger,
            final MessageDigest digest,
            final Envelope envelope,
            final LongAdder bytesHashed) {
        this.logger = logger;
        this.digest = digest;
        this.envelope = envelope;
        this.bytesHashed = bytesHashed;
        // allocateDirect consistently wins over allocate in heap (~1% faster on same BUFFER_SIZE)
        // allocateDirect consistently wins over vanilla byte[] (~3% faster on same BUFFER_SIZE)
        buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        lengthInBytes = digest.getDigestLength();
    }

    /** Not thread safe, reuses buffer and digest */
    public byte[] hashFile(final File file) throws IOException {
        if (logger.isTraceEnabled()) {
            logger.trace("Starting hash of '" + file + "' ...");
        }

        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            final FileChannel fc = raf.getChannel();
            digest.reset();
            if (envelope == Envelope.GIT) {
                final byte[] header = ("blob " + fc.size() + '\u0000').getBytes(StandardCharsets.ISO_8859_1);
                digest.update(header);
            }

            while (true) {
                buffer.clear();
                final int read = fc.read(buffer);
                if (read > 0) {
                    buffer.flip();
                    digest.update(buffer);
                    bytesHashed.add(read);
                } else if (read == -1) {
                    break;
                }
            }
            final byte[] result = digest.digest();
            if (logger.isTraceEnabled()) {
                final long endAt = System.nanoTime();
                logger.trace(String.format("Hashed file '%s': %s (in %1.3f ms)", file, Hex.toHex(result), (endAt - startAt) / 1e6));
            }
            return result;
        }
    }
}
