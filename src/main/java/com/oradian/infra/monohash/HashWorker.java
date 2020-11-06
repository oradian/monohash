package com.oradian.infra.monohash;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;

public class HashWorker {
    private final Logger logger;
    private final MessageDigest digest;
    private final ByteBuffer buffer;

    public final int lengthInBytes;

    public HashWorker(final Logger logger, final MessageDigest digest) {
        this.logger = logger;
        this.digest = digest;
        buffer = ByteBuffer.allocateDirect(64 * 1024);
        lengthInBytes = digest.getDigestLength();
    }

    /** Not thread safe, reuses buffer and digest */
    public byte[] hashFile(final File file) throws IOException {
        final long startAt = System.nanoTime();
        if (logger.isTraceEnabled()) {
            logger.trace("Starting hash of '" + file + "' ...");
        }
        try (final FileInputStream fis = new FileInputStream(file)) {
            final FileChannel fc = fis.getChannel();
            digest.reset();
            while (true) {
                buffer.clear();
                final int read = fc.read(buffer);
                if (read == -1) {
                    break;
                }
                buffer.flip();
                digest.update(buffer);
            }
            final byte[] result = digest.digest();
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
