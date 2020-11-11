package com.oradian.infra.monohash;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class HashWorker {
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Logger logger;
    private final MessageDigest digest;
    private final Envelope envelope;
    private final ByteBuffer buffer;

    public final int lengthInBytes;

    public HashWorker(final Logger logger, final MessageDigest digest, final Envelope envelope) {
        this.logger = logger;
        this.digest = digest;
        this.envelope = envelope;
        buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        lengthInBytes = digest.getDigestLength();
    }

    /** Not thread safe, reuses buffer and digest */
    public byte[] hashFile(final File file) throws IOException {
        if (logger.isTraceEnabled()) {
            logger.trace("Starting hash of '" + file + "' ...");
        }
        final long startAt = System.nanoTime();
        try (final FileInputStream fis = new FileInputStream(file)) {
            final FileChannel fc = fis.getChannel();
            digest.reset();
            if (envelope == Envelope.GIT) {
                final byte[] header = ("blob " + fc.size() + '\u0000').getBytes(StandardCharsets.ISO_8859_1);
                digest.update(header);
            }

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
                final long endAt = System.nanoTime();
                logger.trace(String.format("Hashed file '%s': %s (in %1.3f ms)", file, Hex.toHex(result), (endAt - startAt) / 1e6));
            }
            return result;
        }
    }
}
