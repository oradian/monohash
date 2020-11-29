package com.oradian.infra.monohash;

import com.oradian.infra.monohash.util.Format;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

public final class HashPlan {
    public final String basePath;
    public final List<String> whitelist;
    public final Pattern blacklist;

    /**
     * HashPlan holds instructions on which files and folders will be included in hashing
     *
     * @param basePath  the absolute path with a trailing slash - all reporting and relative path resolution will use this
     * @param whitelist a list of files and directories which must exist, which will be used for hashing
     * @param blacklist a single regex containing all the ignore patterns for skipping files during hashing
     */
    public HashPlan(
            final String basePath,
            final List<String> whitelist,
            final Pattern blacklist) {
        this.basePath = basePath;
        this.whitelist = whitelist;
        this.blacklist = blacklist;
    }

    private static String resolveBasePath(final Logger logger, final File planParent, final List<String> lines) throws IOException {
        final LinkedHashSet<String> basePathOverrides = new LinkedHashSet<>();
        for (final String line : lines) {
            if (line.charAt(0) == '@') {
                basePathOverrides.add(line);
            }
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Found " + basePathOverrides.size() + " distinct base path overrides in hash plan");
        }
        if (basePathOverrides.size() > 1) {
            throw new IllegalArgumentException("There is more than one base path override: '" + String.join("', '", basePathOverrides) + '\'');
        }

        final File file;
        if (basePathOverrides.isEmpty()) {
            file = planParent;
        } else {
            final String line = basePathOverrides.iterator().next();
            final String relBasePath = line.substring(1);
            if (relBasePath.isEmpty()) {
                throw new IllegalArgumentException("base path override cannot be empty");
            }
            if (!relBasePath.endsWith("/") && logger.isWarnEnabled()) {
                logger.warn("Relative base path should end with a trailing slash, adding the slash: '" + line + "/'");
            }
            file = new File(planParent, relBasePath);
        }

        try {
            // consolidate slashes, append trailing slash to base path string
            return file.getCanonicalPath()
                    .replace('\\', '/')
                    .replaceFirst("/*$", "/");
        } catch (final IOException e) {
            throw new IOException("Could not resolve canonical path for [hash plan]'s parent: " + Format.file(file), e);
        }
    }

    private static Pattern compileBlacklist(final Logger logger, final List<String> lines) {
        final LinkedHashSet<String> patterns = new LinkedHashSet<>();
        for (final String line : lines) {
            if (line.startsWith("!")) {
                final String pattern = line.substring(1);
                if (pattern.isEmpty()) {
                    throw new IllegalArgumentException("blacklist pattern cannot be empty");
                }

                final StringBuilder sb = new StringBuilder();
                for (final String part : pattern.split("\\*", -1)) {
                    if (!part.isEmpty()) {
                        sb.append(Pattern.quote(part));
                    }
                    sb.append(".*");
                }
                sb.setLength(sb.length() - 2);
                patterns.add(sb.toString());
            }
        }

        if (patterns.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("No blacklist patterns to compile");
            }
            return null;
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug(Format.lines("Compiling blacklist patterns", patterns));
            }
            return Pattern.compile(String.join("|", patterns));
        }
    }

    private static List<String> extractWhitelist(final Logger logger, final String basePath, final List<String> lines) {
        final LinkedHashSet<String> whitelist = new LinkedHashSet<>();
        for (final String line : lines) {
            final String entry;
            switch (line.charAt(0)) {
                case '@':
                case '!':
                case '#':
                    continue; // control characters
                case '\\':
                    entry = line.substring(1);
                    break; // strip leading backslash to allow escaping control characters in filenames
                default:
                    entry = line;
            }

            final String suffix;
            switch (entry) {
                case ".":
                    if (logger.isWarnEnabled()) {
                        logger.warn("Relative path '.' is a directory - please append a trailing / to this whitelist entry");
                    }
                    suffix = "";
                    break; // falling through here freaks out linters
                case "./":
                    suffix = "";
                    break;
                default:
                    suffix = entry;
            }
            final boolean duplicate = !whitelist.add(basePath + suffix);
            if (duplicate && logger.isWarnEnabled()) {
                logger.warn("Whitelist entry '" + line + "' is a duplicate - please review the [hash plan]");
            }
        }

        if (whitelist.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("No whitelist entries found, adding base path as default");
            }
            whitelist.add(basePath);
        }
        return new ArrayList<>(whitelist);
    }

    static HashPlan apply(final Logger logger, final File planParent, final String plan) throws IOException {
        // split on CR/LF and remove trailing whitespaces from each line
        final ArrayList<String> lines = new ArrayList<>();
        for (final String line : plan.split("[ \t]*([\r\n]+|$)")) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        if (logger.isTraceEnabled()) {
            logger.trace(Format.lines("Read hash plan", lines));
        }

        final String basePath = resolveBasePath(logger, planParent, lines);
        if (logger.isDebugEnabled()) {
            logger.debug("Using base path: '" + basePath + '\'');
        }

        final Pattern blacklist = compileBlacklist(logger, lines);
        final List<String> whitelist = extractWhitelist(logger, basePath, lines);
        return new HashPlan(basePath, whitelist, blacklist);
    }

    static HashPlan apply(final Logger logger, final File planParent, final byte[] plan) throws IOException {
        // Use CharsetDecoder in order to explode on Character decoding issues
        // Vanilla String / Charset functions would silently skip errors and replace them with '�'
        final CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder();
        final String planString = utf8.decode(ByteBuffer.wrap(plan)).toString();
        return apply(logger, planParent, planString);
    }

    static HashPlan apply(final Logger logger, final File plan) throws IOException {
        final File canoPlan;
        try {
            canoPlan = plan.getCanonicalFile();
        } catch (final IOException e) {
            throw new IOException("Could not resolve canonical path for [hash plan]: " + Format.file(plan), e);
        }
        if (canoPlan.isDirectory()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Hash plan was a directory, proceeding with synthetic [hash plan] instead ...");
            }
            return apply(logger, canoPlan, "");
        }
        final File planParent = canoPlan.getParentFile();

        if (logger.isDebugEnabled()) {
            logger.debug("Reading hash plan: " + Format.file(canoPlan) + " ...");
        }
        final byte[] planBytes = Files.readAllBytes(canoPlan.toPath());
        return apply(logger, planParent, planBytes);
    }
}
