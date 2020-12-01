package com.oradian.infra.monohash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.*;

public final class Algorithm {
    public static final String SHA_1 = "SHA-1";
    public static final String GIT = "GIT";

    public final String name;
    public final String underlying;
    public final Provider provider;
    public final int lengthInBytes;

    @FunctionalInterface
    public interface LengthSupplier {
        long get() throws IOException;
    }

    private final ThreadLocal<MessageDigest> digest;
    public MessageDigest init(final long length) {
        final MessageDigest md = digest.get();
        md.reset();

        if (name.equals(GIT)) {
            final byte[] header = ("blob " + length + '\u0000').getBytes(StandardCharsets.ISO_8859_1);
            md.update(header);
        }

        return md;
    }

    public MessageDigest init(final LengthSupplier length) throws IOException {
        return init(name.equals(GIT) ? length.get() : 0L);
    }

    public Algorithm(final String algorithm) throws NoSuchAlgorithmException {
        this(algorithm, null);
    }

    public Algorithm(final String algorithm, final Provider provider) throws NoSuchAlgorithmException {
        name = algorithm.toUpperCase(Locale.ROOT);

        // The synthetic "GIT" algorithm is actually "SHA-1" under the hood + a length prefix,
        // so that it's compatible with Git's object IDs: (https://git-scm.com/book/en/v2/Git-Internals-Git-Objects
        underlying = name.equals(GIT) ? SHA_1 : name;

        // Check if it's possible to instantiate the MessageDigest immediately, instead of failing later
        // If the provider was null we'll search through all of them to find the first digest implementation
        final MessageDigest testDigest = provider == null
                ? MessageDigest.getInstance(underlying)
                : MessageDigest.getInstance(underlying, provider);

        this.provider = testDigest.getProvider();
        lengthInBytes = testDigest.getDigestLength();
        digest = ThreadLocal.withInitial(() -> {
            try {
                // lock the provider for this particular algorithm
                return MessageDigest.getInstance(underlying, this.provider);
            } catch (final NoSuchAlgorithmException e) {
                // should not happen - something is very wrong
                throw new RuntimeException("Unable to resolve '" + underlying +
                        "' MessageDigest via provider '" + this.provider.getName() +
                        "', even though this was previously successful", e);
            }
        });
    }

    // =================================================================================================================

    private static String voteForName(final Set<String> algorithms, final SortedSet<String> aliases) {
        if (aliases.contains(SHA_1)) {
            return SHA_1;
        }
        for (final String alias : aliases) {
            if (algorithms.contains(alias)) {
                return alias;
            }
        }
        return aliases.first();
    }

    static SortedMap<String, SortedSet<String>> linkAlgorithms(final Set<String> algorithms, final List<SortedSet<String>> aliasPairs) {
        final TreeMap<String, SortedSet<String>> cache = new TreeMap<>();
        for (final String algorithm : algorithms) {
            final TreeSet<String> self = new TreeSet<>();
            self.add(algorithm);
            cache.put(algorithm, self);
        }
        for (final SortedSet<String> aliasPair : aliasPairs) {
            if (aliasPair.size() != 2) {
                throw new IllegalArgumentException("Expected pairs of aliases, but got: " + aliasPair);
            }
            final String a1 = aliasPair.first();
            final String a2 = aliasPair.last();
            final SortedSet<String> previous1 = cache.get(a1);
            final SortedSet<String> previous2 = cache.get(a2);
            if (previous1 == null && previous2 == null) {
                cache.put(a1, aliasPair);
                cache.put(a2, aliasPair);
            } else if (previous1 == null) {
                previous2.add(a1);
                cache.put(a1, previous2);
            } else if (previous2 == null) {
                previous1.add(a2);
                cache.put(a2, previous1);
            } else {
                previous1.addAll(previous2);
                for (final String k2 : previous2) {
                    cache.put(k2, previous1);
                }
            }
        }
        final TreeMap<String, SortedSet<String>> result = new TreeMap<>();
        for (final SortedSet<String> group : new HashSet<>(cache.values())) { // HashSet to dedup values
            final String key = voteForName(algorithms, group);
            group.remove(key); // separate voted key from rest of aliases
            result.put(key, group);
        }
        return result;
    }

    public static SortedMap<String, SortedSet<String>> getAlgorithms() {
        final Set<String> algorithms = Security.getAlgorithms("MessageDigest");

        final ArrayList<SortedSet<String>> aliasPairs = new ArrayList<>();
        for (final Provider provider : Security.getProviders()) {
            for (final Map.Entry<Object, Object> service : provider.entrySet()) {
                final String description = service.getKey().toString();
                if (description.startsWith("Alg.Alias.MessageDigest.")) {
                    final TreeSet<String> aliasPair = new TreeSet<>();
                    aliasPair.add(description.substring("Alg.Alias.MessageDigest.".length()).toUpperCase(Locale.ROOT));
                    aliasPair.add(service.getValue().toString().toUpperCase(Locale.ROOT));
                    aliasPairs.add(aliasPair);
                }
            }
        }

        final SortedMap<String, SortedSet<String>> result = linkAlgorithms(algorithms, aliasPairs);
        if (result.containsKey(SHA_1)) {
            // synthesize virtual "GIT" algorithm if underlying "SHA-1" is available
            result.put(GIT, Collections.emptySortedSet());
        }
        return result;
    }
}
