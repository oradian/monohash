package com.oradian.infra.monohash;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static com.oradian.infra.monohash.Logger.NL;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HashResults extends AbstractCollection<Map.Entry<String, byte[]>> {
    private final Logger logger;
    private final MessageDigest digest;
    private final int lengthInHex;
    private final byte[][] lines;

    public HashResults(final Logger logger, final String algorithm, final Collection<Map.Entry<String, byte[]>> files) throws NoSuchAlgorithmException {
        this.logger = logger;
        this.digest = MessageDigest.getInstance(algorithm);
        this.lengthInHex = digest.getDigestLength() << 1;

        lines = new byte[files.size()][];

        final StringBuilder sb = new StringBuilder();
        int i = 0;
        for (final Map.Entry<String, byte[]> pathHashes : files) {
            sb.setLength(0);
            sb.append(Hex.toHex(pathHashes.getValue()))
                    .append(' ')
                    .append(pathHashes.getKey())
                    .append('\n'); // hardcoded to unix newline for cross-platform compatibility
            lines[i++] = sb.toString().getBytes(UTF_8);
        }
    }

    /**
     * Parses the hashes and relative paths from a file to instantiate a HashResult,
     * used for comparing a previous project HashResult to the current status
     */
    public HashResults(final Logger logger, final String algorithm, final File inFile) throws NoSuchAlgorithmException, IOException {
        this.logger = logger;
        this.digest = MessageDigest.getInstance(algorithm);
        this.lengthInHex = digest.getDigestLength() << 1;

        if (logger.isTraceEnabled()) {
            logger.trace("Parsing [export file]: " + inFile + " ...");
        }
        final long startAt = System.nanoTime();
        try (final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), UTF_8))) {
            final ArrayList<byte[]> result = new ArrayList<>();
            while (true) {
                final String line = br.readLine();
                if (line == null) break;
                if (line.isEmpty()) continue;
                final int breakLine = line.indexOf(' ');
                if (breakLine == -1) {
                    throw new IOException("Could not split hash from path in line: " + line);
                }
                if (breakLine != lengthInHex) {
                    throw new IOException("Expected hash length of " + lengthInHex + ", but found a hash with " + breakLine + " characters instead: '" + line + "'");
                }
                if (!line.matches("[0-9A-Fa-f]{" + lengthInHex + "}.*")) {
                    throw new IOException("Expected hash of " + lengthInHex + " hexadecimal characters at the beginning of line, but got: '" + line + "'");
                }
                result.add(line.concat("\n").getBytes(UTF_8)); // hardcoded to unix newline for cross-platform compatibility
            }
            this.lines = result.toArray(new byte[0][]);
        }
        if (logger.isDebugEnabled()) {
            final long endAt = System.nanoTime();
            logger.debug(String.format("Parsed [export file]: '%s' (in %1.3f ms)", inFile, (endAt - startAt) / 1e6));
        }
    }

    public byte[] totalHash() {
        digest.reset();
        for (final byte[] line : lines) {
            digest.update(line);
        }
        return digest.digest();
    }

    /**
     * Export the hashes and relative paths into a file, returning the summary hash of the newly exported file
     * If outFile was empty, it will perform the export in memory and return the same hash
     */
    public void export(final File outFile) throws IOException {
        try (final OutputStream fos = new BufferedOutputStream(new FileOutputStream(outFile))) {
            for (final byte[] line : lines) {
                fos.write(line);
            }
        }
    }

    @Override
    public Iterator<Map.Entry<String, byte[]>> iterator() {
        return new Iterator<Map.Entry<String, byte[]>>() {
            private int current = 0;

            @Override
            public boolean hasNext() {
                return current < lines.length;
            }

            @Override
            public Map.Entry<String, byte[]> next() {
                if (current >= lines.length) {
                    throw new NoSuchElementException("Seeking past last entry");
                }
                final byte[] line = lines[current++];
                final String path = new String(line, lengthInHex + 1, line.length - lengthInHex - 2, UTF_8);
                final byte[] hash = Hex.fromHex(line, lengthInHex);
                return new AbstractMap.SimpleImmutableEntry<>(path, hash);
            }
        };
    }

    @Override
    public int size() {
        return lines.length;
    }

    private LinkedHashMap<String, byte[]> toMap() {
        final LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        for (final Map.Entry<String, byte[]> entry : this) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static class Diff {
        final Map<String, List<Map.Entry<Integer, String[]>>> addedReverse = new LinkedHashMap<>(); // hash, (index1, (hash, path1Dst, path1Src?)), (index2, (hash, path2Dst, path2Src?)), ...
        final Map<String, byte[][]> changed = new LinkedHashMap<>();                                // path, hashDst, hashSrc
        final Map<String, String> deleted = new LinkedHashMap<>();                                  // pathDst, hash

        public boolean isEmpty() {
            return addedReverse.isEmpty() && changed.isEmpty() && deleted.isEmpty();
        }

        public List<String> toLines() {
            final ArrayList<String> lines = new ArrayList<>();
            final StringBuilder sbx = new StringBuilder();
            if (!addedReverse.isEmpty()) {
                lines.add("Added files:");
                final SortedMap<Integer, String[]> sortedAddsAndRenames = new TreeMap<>(); // index, hash, pathDst, pathSrc?
                for (final List<Map.Entry<Integer, String[]>> addedHashEntry : addedReverse.values()) {
                    for (final Map.Entry<Integer, String[]> addedPathEntry : addedHashEntry) {
                        sortedAddsAndRenames.put(addedPathEntry.getKey(), addedPathEntry.getValue());
                    }
                }
                for (final String[] add : sortedAddsAndRenames.values()) {
                    sbx.setLength(0);
                    sbx.append("+ ").append(add[0]).append(": ").append(add[1]);
                    if (add[2] != null) {
                        sbx.append(" (renamed from: ").append(add[2]).append(")");
                    }
                    lines.add(sbx.toString());
                }
                lines.add("");
            }
            if (!changed.isEmpty()) {
                lines.add("Changed files:");
                for (final Map.Entry<String, byte[][]> change : changed.entrySet()) {
                    sbx.setLength(0);
                    sbx.append("! ").append(Hex.toHex(change.getValue()[0])).append(": ")
                            .append(change.getKey())
                            .append(" (was: ").append(Hex.toHex(change.getValue()[1])).append(")");
                    lines.add(sbx.toString());
                }
                lines.add("");
            }
            if (!deleted.isEmpty()) {
                lines.add("Deleted files:");
                for (final Map.Entry<String, String> delete : deleted.entrySet()) {
                    sbx.setLength(0);
                    sbx.append("- ").append(delete.getValue()).append(": ").append(delete.getKey());
                    lines.add(sbx.toString());
                }
                lines.add("");
            }
            return lines;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            for (final String line : toLines()) {
                sb.append(line).append(NL);
            }
            return sb.toString();
        }
    }

    public static Diff diff(final HashResults src, final HashResults dst) {
        final Map<String, byte[]> srcMap = src.toMap();
        final Map<String, byte[]> dstMap = dst.toMap();

        final Diff diff = new Diff();

        int index = 0;
        for (final Map.Entry<String, byte[]> dstEntry : dstMap.entrySet()) {
            final String dstKey = dstEntry.getKey();
            final byte[] dstValue = dstEntry.getValue();

            final byte[] srcValue = srcMap.remove(dstKey);
            if (srcValue == null) {
                final String reverseKey = Hex.toHex(dstValue);
                diff.addedReverse.computeIfAbsent(reverseKey, unused -> new ArrayList<>())
                        .add(new AbstractMap.SimpleEntry<>(index, new String[]{reverseKey, dstKey, null})); // null is rename marker
            } else if (!Arrays.equals(dstValue, srcValue)) {
                diff.changed.put(dstKey, new byte[][]{dstValue, srcValue});
            }
            index++;
        }

        for (final Map.Entry<String, byte[]> srcEntry : srcMap.entrySet()) {
            final String srcKey = srcEntry.getKey();
            final String srcValue = Hex.toHex(srcEntry.getValue());

            final List<Map.Entry<Integer, String[]>> moved = diff.addedReverse.get(srcValue);
            if (moved == null) {
                diff.deleted.put(srcKey, srcValue);
            } else {
                moved.stream()
                        .filter(entry -> entry.getValue()[2] == null)
                        .limit(1)
                        .forEach(entry -> entry.getValue()[2] = srcKey);
            }
        }

        return diff;
    }
}
