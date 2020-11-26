package com.oradian.infra.monohash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.*;
import java.util.function.Supplier;

public class Algorithm {
    public static final String SHA_1 = "SHA-1";
    public static final String GIT = "GIT";

    public final String name;
    public final String underlying;
    public final Provider provider;
    public final int lengthInBytes;

    private final ThreadLocal<MessageDigest> digest;
    public MessageDigest init(final Supplier<Long> length) {
        final MessageDigest md = digest.get();
        md.reset();

        if (name.equals(GIT)) {
            final byte[] header = ("blob " + length.get() + '\u0000').getBytes(StandardCharsets.ISO_8859_1);
            md.update(header);
        }

        return md;
    }

    public Algorithm(final String algorithm) throws NoSuchAlgorithmException {
        this(algorithm, null);
    }

    public Algorithm(final String algorithm, final Provider provider) throws NoSuchAlgorithmException {
        name = algorithm.toUpperCase(Locale.ROOT);

        // The synthetic "GIT" MessageDigest is actually "SHA-1" under the hood + a length prefix,
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

    private static Map<String, Set<String>> getAliases() {
        final Map<String, Set<String>> aliases = new TreeMap<>();
        for (final String algorithm : Security.getAlgorithms("MessageDigest")) {
            final Set<String> selfAlias = new TreeSet<>();
            selfAlias.add(algorithm);
            aliases.put(algorithm, selfAlias);
        }
        for (final Provider provider : Security.getProviders()) {
            for (final Map.Entry<Object, Object> service : provider.entrySet()) {
                final String description = service.getKey().toString();
                if (description.startsWith("Alg.Alias.MessageDigest.")) {
                    final String alias = description.substring("Alg.Alias.MessageDigest.".length()).toUpperCase(Locale.ROOT);
                    final String algorithm = service.getValue().toString().toUpperCase(Locale.ROOT);
                    aliases.computeIfAbsent(alias, x -> new HashSet<>()).add(algorithm);
                }
            }
        }
        return aliases;
    }

    private static String voteForName(final SortedSet<String> algorithms) {
        return algorithms.contains(SHA_1) ? SHA_1 : algorithms.first();
    }

    private static Map<String, String> linkAlgorithms(final Map<String, Set<String>> aliases) {
        final Map<String, SortedSet<String>> mergers = new HashMap<>();
        for (final Set<String> names : aliases.values()) {
            final SortedSet<String> expansion = new TreeSet<>();
            for (final String v : names) {
                expansion.add(v);
                final Set<String> previous = mergers.get(v);
                if (previous != null) {
                    expansion.addAll(previous);
                }
            }
            for (final String v : expansion) {
                mergers.put(v, expansion);
            }
        }

        final Map<String, String> selection = new HashMap<>();
        for (final String key : mergers.keySet()) {
            selection.put(key, voteForName(mergers.get(key)));
        }
        return selection;
    }

    public static SortedMap<String, SortedSet<String>> getAlgorithms() {
        final Map<String, Set<String>> aliases = getAliases();
        final Map<String, String> selection = linkAlgorithms(aliases);

        final SortedMap<String, SortedSet<String>> algorithms = new TreeMap<>();
        for (final Map.Entry<String, Set<String>> aliasEntry : aliases.entrySet()) {
            final String aliasName = aliasEntry.getKey();
            for (final String algorithm : aliasEntry.getValue()) {
                final String selectedName = selection.get(algorithm);
                final SortedSet<String> onlyAliases =
                        algorithms.computeIfAbsent(selectedName, x -> new TreeSet<>());
                if (!aliasName.equals(selectedName)) {
                    onlyAliases.add(aliasName);
                }
            }
        }

        // synthesize virtual "GIT" algorithm if underlying "SHA-1" is available
        if (aliases.containsKey(SHA_1)) {
            algorithms.put(GIT, Collections.emptySortedSet());
        }
        return algorithms;
    }
}
