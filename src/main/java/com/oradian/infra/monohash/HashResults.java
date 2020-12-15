package com.oradian.infra.monohash;

import com.oradian.infra.monohash.param.Algorithm;
import com.oradian.infra.monohash.util.Format;
import com.oradian.infra.monohash.util.Hex;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

public final class HashResults {
    private final Logger logger;
    public final Algorithm algorithm;
    private final byte[] lines;

    HashResults(
            final Logger logger,
            final Algorithm algorithm,
            final byte[] lines,
            final int[] newlines) {
        this.logger = logger;
        this.algorithm = algorithm;
        this.lines = lines;
        this.newlinesCache = newlines;
    }

    private byte[] hashCache;
    public byte[] hash() {
        if (hashCache == null) {
            final long startAt = System.nanoTime();
            final MessageDigest md = algorithm.init(lines.length);
            hashCache = md.digest(lines);
            if (logger.isTraceEnabled()) {
                logger.trace("Calculated total hash: " + Format.hex(hashCache) + Format.timeNanos(startAt));
            }
        }
        return hashCache.clone();
    }

    private int[] newlinesCache;
    private int[] newlines() {
        if (newlinesCache == null) {
            int[] buffer = new int[lines.length >>> 7]; // guesstimate on avg. line length
            int newlineCount = 0;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i] == '\n') {
                    newlineCount++;
                    if (buffer.length < newlineCount) {
                        buffer = Arrays.copyOf(buffer, newlineCount << 1);
                    }
                    buffer[newlineCount - 1] = i + 1;
                }
            }
            // in case we're reading some corrupted export file, it may not end with a '\n'
            // we need to patch the newline counter since otherwise the last line will be lost
            if (lines.length > 0 && lines[lines.length - 1] != '\n') {
                newlineCount++;
                if (buffer.length != newlineCount) {
                    buffer = Arrays.copyOf(buffer, newlineCount);
                }
                buffer[newlineCount - 1] = lines.length + 1;
            }
            if (buffer.length != newlineCount) {
                buffer = Arrays.copyOf(buffer, newlineCount);
            }
            newlinesCache = buffer;
        }
        return newlinesCache;
    }

    /** Export the hashes and relative paths into a file, returning the summary hash of the newly exported file
     * If outFile was empty, it will perform the export in memory and return the same hash */
    public void export(final File outFile) throws IOException {
        final long startAt = System.nanoTime();
        Files.write(outFile.toPath(), lines);
        if (logger.isTraceEnabled()) {
            logger.trace("Wrote to [export file]: " + Format.file(outFile) + Format.timeNanos(startAt));
        }
    }

    public int size() {
        return newlines().length;
    }

    public LinkedHashMap<String, byte[]> toMap() throws ExportParsingException {
        final LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        final int[] nls = newlines();
        final CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder();

        for (int index = 0; index < nls.length; index++) {
            final int lastEnd = index == 0 ? 0 : nls[index - 1];
            final int lineLength = nls[index] - lastEnd;

            final byte[] lineHash;
            try {
                lineHash = Hex.fromHex(lines, lastEnd, algorithm.lengthInBytes << 1);
            } catch (final NumberFormatException e) {
                final String line = new String(lines, lastEnd, lineLength - 1, StandardCharsets.UTF_8);
                throw new ExportParsingException("Cannot parse export line #" + (index + 1) + ": " + line, e);
            }

            final int pathOffset = lastEnd + (algorithm.lengthInBytes << 1) + 1;
            if (lines[pathOffset - 1] != ' ') {
                final String line = new String(lines, lastEnd, lineLength - 1, StandardCharsets.UTF_8);
                throw new ExportParsingException("Could not split hash from path in export line #" + (index + 1) + ": " + line);
            }

            final int pathByteLength = lineLength - (algorithm.lengthInBytes << 1) - 2;
            final ByteBuffer bb = ByteBuffer.wrap(lines, pathOffset, pathByteLength);
            final String path;
            try {
                path = utf8.decode(bb).toString();
            } catch (final CharacterCodingException e) {
                final String line = new String(lines, lastEnd, lineLength - 1, StandardCharsets.UTF_8);
                throw new ExportParsingException("Could not decode export line #" + (index + 1) + " using UTF-8: " + line, e);
            }
            if (path.isEmpty()) {
                final String line = new String(lines, lastEnd, lineLength - 1, StandardCharsets.UTF_8);
                throw new ExportParsingException("Path was empty on line #" + (index + 1) + ": " + line);
            }
            if (result.put(path, lineHash) != null) {
                throw new ExportParsingException("At least two export lines found with identical paths '" + path + '\'');
            }
        }

        return result;
    }

    @Override
    public int hashCode() {
        return lines.length;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof HashResults)) {
            return false;
        }
        final HashResults that = (HashResults) obj;
        return Arrays.equals(lines, that.lines);
    }

    public static HashResults apply(final Logger logger, final Algorithm algorithm, final byte[] lines) {
        return new HashResults(logger, algorithm, lines, null);
    }

    public static HashResults apply(final Logger logger, final Algorithm algorithm, final Collection<Map.Entry<String, byte[]>> entries) {
        final ArrayList<byte[]> lineBuffer = new ArrayList<>();
        int totalLength = 0;
        for (final Map.Entry<String, byte[]> pathHashes : entries) {
            final byte[] hash = pathHashes.getValue();
            lineBuffer.add(hash);
            final byte[] path = pathHashes.getKey().getBytes(StandardCharsets.UTF_8);
            lineBuffer.add(path);
            totalLength += (hash.length << 1) + 1 + path.length + 1;
        }

        final byte[] lines = new byte[totalLength];
        final int[] newlines = new int[lineBuffer.size() >>> 1];

        int pos = 0;
        for (int i = 0; i < newlines.length; i ++) {
            final byte[] hash = lineBuffer.get(i << 1);
            Hex.toHex(hash, 0, hash.length, lines, pos);
            pos += hash.length << 1;
            lines[pos++] = ' ';

            final byte[] path = lineBuffer.get((i << 1) + 1);
            System.arraycopy(path, 0, lines, pos, path.length);
            pos += path.length;
            lines[pos++] = '\n';

            newlines[i] = pos;
        }
        return new HashResults(logger, algorithm, lines, newlines);
    }
}
