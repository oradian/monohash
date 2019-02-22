package com.oradian.infra.monohash;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static com.oradian.infra.monohash.Logger.NL;

public class HashPlan {
    public final File plan;
    public final String basePath;
    public final List<String> whitelist;
    public final Pattern blacklist;

    /**
     * HashPlan holds instructions on which files and folders will be included in hashing
     *
     * @param plan      the file from which instructions were read
     * @param basePath  the absolute path with a trailing slash - all reporting and relative path resolution will use this
     * @param whitelist a list of files and directories which must exist, which will be used for hashing
     * @param blacklist a single regex containing all the ignore patterns for skipping files during hashing
     */
    public HashPlan(
            final File plan,
            final String basePath,
            final List<String> whitelist,
            final Pattern blacklist) {
        this.plan = plan;
        this.basePath = basePath;
        this.whitelist = whitelist;
        this.blacklist = blacklist;
    }

    private static String resolveBasePath(final Logger logger, final File planParent, final List<String> lines) {
        final List<String> basePathOverrides = lines.stream().filter(line -> line.startsWith("@")).distinct().collect(Collectors.toList());

        if (logger.isTraceEnabled()) {
            logger.trace("Found " + basePathOverrides.size() + " distinct base path overrides in hash plan");
        }
        if (basePathOverrides.size() > 1) {
            throw new IllegalArgumentException("There is more than one base path override: '" + String.join("', '", basePathOverrides) + "'");
        }

        final File file;
        if (basePathOverrides.isEmpty()) {
            file = planParent;
        } else {
            final String line = basePathOverrides.get(0);
            final String relBasePath = line.substring(1);
            if (relBasePath.isEmpty()) {
                throw new IllegalArgumentException("base path override cannot be empty!");
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
            throw new RuntimeException("Could not resolve canonical path for: " + file, e);
        }
    }

    private static Pattern compileBlacklist(final Logger logger, final List<String> lines) {
        final LinkedHashSet<String> patterns = new LinkedHashSet<>();
        for (final String line : lines) {
            if (line.startsWith("!")) {
                final String pattern = line.substring(1);
                if (pattern.isEmpty()) {
                    throw new IllegalArgumentException("blacklist pattern cannot be empty!");
                }
                final String regex = Arrays.stream(pattern.split("\\*", -1))
                        .map(Pattern::quote)
                        .collect(Collectors.joining(".*"))
                        .replace("\\Q\\E", "");
                patterns.add(regex);
            }
        }

        if (patterns.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("No blacklist patterns to compile!");
            }
            return null;
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Compiling blacklist patterns:" + Logger.NL + String.join(Logger.NL, patterns));
            }
            return Pattern.compile(String.join("|", patterns));
        }
    }

    private static List<String> extractWhitelist(final Logger logger, final String basePath, final List<String> lines) {
        final LinkedHashSet<String> whitelist = new LinkedHashSet<>();
        for (final String line : lines) {
            switch (line.charAt(0)) {
                case '@':
                case '!':
                case '#':
                    continue; // control characters
                case '\\':
                    whitelist.add(line.substring(1));
                    break; // strip leading backslash to allow escaping control characters in filenames
                default:
                    whitelist.add(line);
            }
        }

        final LinkedHashSet<String> dotPatch = new LinkedHashSet<>();
        for (final String line : whitelist) {
            switch (line) {
                case ".":
                    if (logger.isWarnEnabled()) {
                        logger.warn("Relative path '.' is a directory - please append a trailing / to this whitelist entry");
                    }
                case "./":
                    dotPatch.add(basePath);
                    break;
                default:
                    dotPatch.add(basePath + line);
            }
        }

        if (dotPatch.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("No whitelist entries found, adding base path as default");
            }
            dotPatch.add(basePath);
        }

        return new ArrayList<>(dotPatch);
    }

    static HashPlan apply(final Logger logger, final File plan) throws IOException {
        final File canoPlan = plan.getCanonicalFile();
        final File planParent = canoPlan.getParentFile();

        if (logger.isDebugEnabled()) {
            logger.debug("Reading hash plan: '" + canoPlan + "' ...");
        }
        final String body = new String(Files.readAllBytes(canoPlan.toPath()), UTF_8);

        // split on CR/LF and remove trailing whitespaces from each line
        final List<String> lines = Arrays.stream(body.split("[ \t]*([\r\n]+|$)"))
                .filter(line -> !line.isEmpty()).collect(Collectors.toList());
        if (logger.isTraceEnabled()) {
            logger.trace("Read hash plan:" + NL + String.join(NL, lines));
        }

        final String basePath = resolveBasePath(logger, planParent, lines);
        if (logger.isDebugEnabled()) {
            logger.debug("Using base path: '" + basePath + "'");
        }

        final Pattern blacklist = compileBlacklist(logger, lines);
        final List<String> whitelist = extractWhitelist(logger, basePath, lines);
        return new HashPlan(canoPlan, basePath, whitelist, blacklist);
    }
}
