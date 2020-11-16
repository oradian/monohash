package com.oradian.infra.monohash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.Locale;
import java.util.function.Supplier;

public class Algorithm {
    public final String name;
    public final int lengthInBytes;

    private final ThreadLocal<MessageDigest> digest;
    public MessageDigest init(final Supplier<Long> length) {
        final MessageDigest md = digest.get();
        md.reset();

        if (name.equals("Git")) {
            final byte[] header = ("blob " + length.get() + '\u0000').getBytes(StandardCharsets.ISO_8859_1);
            md.update(header);
        }

        return md;
    }

    public Algorithm(final String algorithm) throws NoSuchAlgorithmException {
        this(algorithm, null);
    }

    public Algorithm(final String algorithm, final Provider provider) throws NoSuchAlgorithmException {
        // The synthetic "Git" MessageDigest is actually "SHA-1" under the hood + a length prefix,
        // so that it's compatible with Git's object IDs: (https://git-scm.com/book/en/v2/Git-Internals-Git-Objects

        final boolean isGit = algorithm.toLowerCase(Locale.ROOT).equals("git");
        final String underlying = isGit ? "SHA-1" : algorithm;

        // Check if it's possible to instantiate the MessageDigest immediately, instead of failing later
        // If the provider has not been provided it will search through all of them by impl. default
        final MessageDigest testDigest = provider == null
                ? MessageDigest.getInstance(underlying)
                : MessageDigest.getInstance(underlying, provider);

        final Provider testProvider = testDigest.getProvider();
        final String testAlgorithm = testDigest.getAlgorithm();

        lengthInBytes = testDigest.getDigestLength();

        name = isGit ? "Git" : testAlgorithm;
        digest = ThreadLocal.withInitial(() -> {
            try {
                // lock the provider for this particular algorithm
                return MessageDigest.getInstance(testAlgorithm, testProvider);
            } catch (final NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
