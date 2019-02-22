package com.oradian.infra.monohash;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashWorker {
    private final Logger logger;
    final MessageDigest worker;
    private final ByteBuffer buffer;

    public final String algorithm;
    public final int lengthInBytes;

    public HashWorker(final Logger logger, final String algorithm) {
        this.logger = logger;
        try {
            worker = MessageDigest.getInstance(algorithm);
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        buffer = ByteBuffer.allocateDirect(64 * 1024);

        this.algorithm = algorithm;
        lengthInBytes = worker.getDigestLength();
    }

    /** Not thread safe, reuses buffer and worker */
    public byte[] hashFile(final File file) throws IOException {
        final long startAt = System.nanoTime();
        if (logger.isTraceEnabled()) {
            logger.trace("Starting hash of '" + file + "' ...");
        }
        try (final FileInputStream fis = new FileInputStream(file)) {
            final FileChannel fc = fis.getChannel();
            worker.reset();
            while (true) {
                buffer.clear();
                final int read = fc.read(buffer);
                if (read == -1) {
                    break;
                }
                buffer.flip();
                worker.update(buffer);
            }
            final byte[] result = worker.digest();
            if (logger.isTraceEnabled()) {
                logger.trace(String.format(
                        "Hashed file '%s': %s (in %1.3f ms)",
                        file,
                        Hex.toHex(result),
                        (System.nanoTime() - startAt) / 1000000.0));
            }
            return result;
        }
    }
}
