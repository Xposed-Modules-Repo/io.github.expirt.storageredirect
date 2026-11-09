package io.github.expirt.storageredirect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Path-to-Path redirection engine.
 *
 * Handles:
 *  - Boundary-aware recursive matching for files and directories
 *  - Longest-prefix priority (more specific rules take precedence)
 *  - Deterministic overlapping rule resolution
 *  - Cyclic and recursive mapping detection and prevention
 *  - Safe destination directory and intermediate parent creation
 *  - Robust path normalization (aliases, trailing separators, ., .., {pkg})
 *  - Diagnostic tracking for operations
 */
public class PathEngine {

    public interface DiagnosticLogger {
        void log(String op, String original, String resolved, String rule, boolean success, String error);
    }

    private static final String[] STORAGE_ALIASES = {
            "/sdcard",
            "/storage/emulated/0",
            "/storage/self/primary",
            "/mnt/sdcard"
    };

    public static class PathRule implements Comparable<PathRule> {
        public final String originalSource;
        public final String originalDestination;
        public final String source;        // canonical source
        public final String destination;   // canonical destination template
        public final Glob glob;
        public final boolean isLiteral;
        public final int specificity;
        public final int order;            // original position for stable tie-breaking
        public final boolean isDirectoryRule;

        public PathRule(String source, String dest, int order, String origSource, String origDest) {
            this.source = source;
            this.destination = dest;
            this.order = order;
            this.originalSource = origSource;
            this.originalDestination = origDest;
            this.glob = new Glob(source, true);
            this.isLiteral = (source.indexOf('*') < 0);

            // Determine if this rule targets a directory or an individual file
            boolean isDir = false;
            if (origSource.endsWith("/") || origDest.endsWith("/")) {
                isDir = true;
            } else {
                File dFile = new File(dest);
                File sFile = new File(source);
                if (dFile.isDirectory() || sFile.isDirectory()) {
                    isDir = true;
                } else if (dFile.isFile() || sFile.isFile()) {
                    isDir = false;
                } else {
                    // Path does not exist yet. Infer from file extension on the final segment.
                    String name = dFile.getName();
                    int dot = name.lastIndexOf('.');
                    isDir = (dot <= 0 || dot == name.length() - 1);
                }
            }
            this.isDirectoryRule = isDir;

            // Compute specificity for longest-prefix sorting
            if (isLiteral) {
                int segs = countSegments(source);
                this.specificity = segs * 1000 + source.length();
            } else {
                int firstWildcard = source.indexOf('*');
                int slashBefore = source.lastIndexOf('/', firstWildcard);
                String prefix = slashBefore <= 0 ? "/" : source.substring(0, slashBefore);
                int segs = countSegments(prefix);
                this.specificity = segs * 1000 + prefix.length() - (glob.groups * 10);
            }
        }

        private static int countSegments(String p) {
            if (p == null || p.isEmpty() || p.equals("/")) return 0;
            int count = 0;
            for (int i = 0; i < p.length(); i++) {
                if (p.charAt(i) == '/') count++;
            }
            return count;
        }

        @Override
        public int compareTo(PathRule o) {
            // 1. Higher specificity first
            if (this.specificity != o.specificity) {
                return Integer.compare(o.specificity, this.specificity);
            }
            // 2. Literal rules before wildcard rules
            if (this.isLiteral != o.isLiteral) {
                return this.isLiteral ? -1 : 1;
            }
            // 3. Stable tie-break by original order
            return Integer.compare(this.order, o.order);
        }

        @Override
        public String toString() {
            return source + " => " + destination + (isDirectoryRule ? " [DIR]" : " [FILE]");
        }
    }

    private final String sd;
    private final String pkg;
    private final String dataDir;
    private final List<PathRule> rules = new ArrayList<>();
    private final Set<String> destinationPrefixes = new HashSet<>();
    private boolean debug = false;
    private DiagnosticLogger logger = null;

    private static final ThreadLocal<Boolean> sInDirectoryCreation = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    public PathEngine(String sd, String pkg, String dataDir) {
        this.sd = (sd != null && !sd.isEmpty()) ? sd : "/storage/emulated/0";
        this.pkg = pkg;
        this.dataDir = (dataDir != null && !dataDir.isEmpty()) ? dataDir : "/data/user/0/" + pkg;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setLogger(DiagnosticLogger logger) {
        this.logger = logger;
    }

    public List<PathRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * Canonicalizes and normalizes an absolute path.
     * - Resolves ".", "..", "//"
     * - Strips trailing slash unless root "/"
     * - Normalizes storage aliases (/sdcard, /storage/self/primary, etc.) to the canonical storage root
     * - Normalizes /data/data/<pkg> to dataDir
     * - Returns null for relative or invalid paths
     */
    public String canon(String p) {
        return canonPath(p, this.sd, this.dataDir, this.pkg);
    }

    public static String canonPath(String p, String sdRoot, String appDataDir, String pkgName) {
        if (p == null || p.isEmpty() || p.charAt(0) != '/') return null;

        // Tokenize and collapse ., ..
        List<String> segments = new ArrayList<>();
        int len = p.length();
        int start = 0;
        for (int i = 0; i <= len; i++) {
            if (i == len || p.charAt(i) == '/') {
                if (i > start) {
                    String seg = p.substring(start, i);
                    if (!seg.isEmpty() && !seg.equals(".")) {
                        if (seg.equals("..")) {
                            if (!segments.isEmpty()) segments.remove(segments.size() - 1);
                        } else {
                            segments.add(seg);
                        }
                    }
                }
                start = i + 1;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String s : segments) {
            sb.append('/').append(s);
        }
        String q = sb.length() == 0 ? "/" : sb.toString();

        // Swap storage aliases
        if (sdRoot != null) {
            for (String alias : STORAGE_ALIASES) {
                String swapped = swapRoot(q, alias, sdRoot);
                if (swapped != null) {
                    q = swapped;
                    break;
                }
            }
        }

        // Swap data directory aliases
        if (appDataDir != null && pkgName != null) {
            String[] dataRoots = {"/data/data/" + pkgName, "/data/user/0/" + pkgName};
            for (String r : dataRoots) {
                String swapped = swapRoot(q, r, appDataDir);
                if (swapped != null) {
                    q = swapped;
                    break;
                }
            }
        }

        return q;
    }

    private static String swapRoot(String path, String fromRoot, String toRoot) {
        if (path.equals(fromRoot)) return toRoot;
        if (path.startsWith(fromRoot + "/")) {
            return toRoot + path.substring(fromRoot.length());
        }
        return null;
    }

    /**
     * Builds and compiles rules from raw entries.
     * Preserves longest-prefix priority and prepares destination directory creation safely.
     */
    public void buildRules(List<String[]> entries) {
        rules.clear();
        destinationPrefixes.clear();
        if (entries == null || entries.isEmpty()) return;

        List<PathRule> parsed = new ArrayList<>();
        Set<String> seenSources = new HashSet<>();
        int order = 0;

        for (String[] entry : entries) {
            if (entry == null || entry.length < 2) continue;
            String rawFrom = entry[0] == null ? "" : entry[0].trim();
            String rawTo = entry[1] == null ? "" : entry[1].trim();
            if (rawFrom.isEmpty() || rawTo.isEmpty()) continue;

            String resolvedFrom = pkg != null ? rawFrom.replace("{pkg}", pkg) : rawFrom;
            String resolvedTo = pkg != null ? rawTo.replace("{pkg}", pkg) : rawTo;

            // When a custom path starts without '/', default is /storage/emulated/0/*path*.
            // When it starts with '/', use the full path.
            if (!resolvedFrom.startsWith("/")) {
                resolvedFrom = (this.sd != null ? this.sd : "/storage/emulated/0") + "/" + resolvedFrom;
            }
            if (!resolvedTo.startsWith("/")) {
                resolvedTo = (this.sd != null ? this.sd : "/storage/emulated/0") + "/" + resolvedTo;
            }

            String f = canon(resolvedFrom);
            String t = canon(resolvedTo);

            if (f == null || t == null || f.equals("/") || f.equals(t)) {
                if (debug && logger != null) {
                    logger.log("BUILD_RULE", rawFrom, rawTo, null, false, "Invalid or identical path");
                }
                continue;
            }

            // Check wildcard index validity
            Glob g = new Glob(f, true);
            Matcher m = Pattern.compile("\\{(\\d+)\\}").matcher(t);
            boolean badRef = false;
            while (m.find()) {
                int ref = Integer.parseInt(m.group(1));
                if (ref < 1 || ref > g.groups) {
                    badRef = true;
                    break;
                }
            }
            if (badRef) {
                if (debug && logger != null) {
                    logger.log("BUILD_RULE", rawFrom, rawTo, null, false, "Invalid {n} capture group");
                }
                continue;
            }

            if (seenSources.contains(f)) {
                // If same source appears twice, the earlier one defined retains priority
                continue;
            }
            seenSources.add(f);

            PathRule rule = new PathRule(f, t, order++, rawFrom, rawTo);
            parsed.add(rule);

            // Record destination prefix for re-redirect guards
            String destGuard = guardOf(t);
            if (destGuard != null) {
                destinationPrefixes.add(destGuard);
            }
        }

        // Sort rules by specificity (longest prefix first)
        Collections.sort(parsed);
        rules.addAll(parsed);

        // Safely initialize destinations where possible without dropping rules on failure
        initializeDestinations();
    }

    private void initializeDestinations() {
        for (PathRule rule : rules) {
            try {
                if (!rule.destination.contains("{")) {
                    File dest = new File(rule.destination);
                    if (rule.isDirectoryRule) {
                        ensureDirInternal(dest);
                    } else {
                        File parent = dest.getParentFile();
                        if (parent != null) {
                            ensureDirInternal(parent);
                        }
                    }
                } else {
                    String prefix = guardOf(rule.destination);
                    if (prefix != null) {
                        ensureDirInternal(new File(prefix));
                    }
                }
            } catch (Throwable t) {
                // NEVER discard rules because destination cannot yet be created
                if (debug && logger != null) {
                    logger.log("INIT_DEST", rule.source, rule.destination, rule.toString(), false, t.getMessage());
                }
            }
        }
    }

    private static String guardOf(String to) {
        if (to == null) return null;
        int i = to.indexOf('{');
        if (i < 0) return to;
        int s = to.lastIndexOf('/', i);
        return s <= 0 ? null : to.substring(0, s);
    }

    /**
     * Resolves a path through the redirection rules.
     * Supports chaining up to a max depth and detects cycles safely.
     * Returns the translated path, or null if no rule matches.
     */
    public String resolvePath(String path) {
        if (path == null || path.isEmpty() || rules.isEmpty()) return null;
        String current = canon(path);
        if (current == null) return null;

        Set<String> visited = new HashSet<>();
        String result = null;
        int hops = 0;

        while (hops < 5) {
            visited.add(current);
            String next = matchSingle(current);
            if (next == null || next.equals(current)) break;

            result = next;
            String nextCanon = canon(next);
            if (nextCanon == null || visited.contains(nextCanon)) {
                // Cyclic redirection detected! Stop chaining immediately.
                if (debug && logger != null) {
                    logger.log("CYCLE_DETECTED", path, next, null, false, "Redirection loop broken");
                }
                break;
            }
            current = nextCanon;
            hops++;
        }

        return result;
    }

    /**
     * Matches a single path against the sorted rules (longest-prefix wins).
     */
    private String matchSingle(String c) {
        for (PathRule rule : rules) {
            // Guard: don't redirect if path is already at or inside this rule's destination
            if (!rule.destination.contains("{")) {
                if (c.equals(rule.destination) || c.startsWith(rule.destination + "/")) {
                    continue;
                }
            }

            String[] caps = rule.glob.match(c);
            if (caps == null) continue;

            String dest = rule.destination;
            for (int k = 0; k < caps.length - 1; k++) {
                dest = dest.replace("{" + (k + 1) + "}", caps[k]);
            }
            String remainder = caps[caps.length - 1];
            return dest + remainder;
        }
        return null;
    }

    /**
     * Finds the matching rule for a path, if any.
     */
    public PathRule findMatchingRule(String path) {
        String c = canon(path);
        if (c == null) return null;
        for (PathRule rule : rules) {
            if (!rule.destination.contains("{")) {
                if (c.equals(rule.destination) || c.startsWith(rule.destination + "/")) {
                    continue;
                }
            }
            if (rule.glob.match(c) != null) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Safely ensures destination directory or parent directories exist for a redirected operation.
     * Protected against recursive hook interception.
     */
    public void ensureTargetParents(String redirectedPath, boolean isDir) {
        if (redirectedPath == null || redirectedPath.isEmpty()) return;
        File f = new File(redirectedPath);
        if (isDir) {
            ensureDirInternal(f);
        } else {
            File p = f.getParentFile();
            if (p != null) {
                ensureDirInternal(p);
            }
        }
    }

    /**
     * Creates a directory and intermediate parents safely without reentering hooks.
     */
    public static File ensureDirInternal(File dir) {
        if (dir == null) return null;
        if (Boolean.TRUE.equals(sInDirectoryCreation.get())) {
            try {
                if (dir.exists() || dir.mkdirs() || dir.exists()) return dir;
            } catch (Throwable ignored) {}
            return null;
        }
        sInDirectoryCreation.set(Boolean.TRUE);
        try {
            if (dir.exists() || dir.mkdirs() || dir.exists()) return dir;
        } catch (Throwable ignored) {
        } finally {
            sInDirectoryCreation.set(Boolean.FALSE);
        }
        return null;
    }

    public void logDiag(String op, String original, String resolved, String rule, boolean success, String error) {
        if (debug && logger != null) {
            logger.log(op, original, resolved, rule, success, error);
        }
    }
}
