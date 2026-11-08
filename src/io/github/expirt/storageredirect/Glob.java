package io.github.expirt.storageredirect;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A path pattern. "*" matches any characters inside one path segment, "**" matches anything,
 * including "/". A pattern without wildcards is compared as plain text. With {@code below} the
 * pattern also covers everything underneath what it matches (path A also covers A/x/y).
 */
final class Glob {
    private final String literal;      // set when there are no wildcards
    private final Pattern pat;         // otherwise
    private final boolean below;
    final int groups;                  // number of wildcards = captures

    Glob(String pattern, boolean below) {
        this.below = below;
        if (pattern.indexOf('*') < 0) {
            literal = pattern;
            pat = null;
            groups = 0;
            return;
        }
        literal = null;
        StringBuilder sb = new StringBuilder("^");
        StringBuilder lit = new StringBuilder();
        int n = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch != '*') {
                lit.append(ch);
                continue;
            }
            if (lit.length() > 0) {
                sb.append(Pattern.quote(lit.toString()));
                lit.setLength(0);
            }
            if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                sb.append("(.*)");
                i++;
            } else {
                sb.append("([^/]*)");
            }
            n++;
        }
        if (lit.length() > 0) sb.append(Pattern.quote(lit.toString()));
        if (below) sb.append("(/.*)?");
        sb.append('$');
        pat = Pattern.compile(sb.toString(), Pattern.DOTALL);
        groups = n;
    }

    static boolean hasWildcard(String s) {
        return s.indexOf('*') >= 0;
    }

    /**
     * Null when c (a canonical absolute path) doesn't match. Otherwise an array whose first
     * {@code groups} items are what each wildcard matched and whose last item is the part below
     * the match ("" or "/x/y"; always "" when this pattern doesn't cover what's below).
     */
    String[] match(String c) {
        if (literal != null) {
            if (c.equals(literal)) return new String[]{""};
            if (below && c.startsWith(literal + "/")) return new String[]{c.substring(literal.length())};
            return null;
        }
        Matcher m = pat.matcher(c);
        if (!m.matches()) return null;
        String[] out = new String[groups + 1];
        for (int i = 0; i < groups; i++) out[i] = m.group(i + 1);
        String t = below ? m.group(groups + 1) : null;
        out[groups] = t == null ? "" : t;
        return out;
    }
}
