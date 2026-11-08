package io.github.expirt.storageredirect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Settings shared between the UI (writer) and the hook (reader, inside the target app).
 *
 *   <sdcard>/CustomData/.config/redirect.properties        defaults for every app
 *   <sdcard>/CustomData/.config/apps/<package>.properties  per-app override (file exists = override on)
 *
 * Files are the only channel that works in rootless LSPatch (no XSharedPreferences / no root).
 */
final class RedirectConfig {

    boolean internal = true;     // /data/data/<pkg>
    boolean prefs = true;        // shared_prefs (only meaningful with internal)
    boolean external = true;     // /Android/data/<pkg> + /Android/obb/<pkg>
    boolean storage = false;     // main storage /storage/emulated/0 (virtual sdcard per app)
    boolean rawPaths = false;    // rewrite hardcoded path strings
    boolean fakePaths = false;   // the app keeps seeing the standard paths; I/O is redirected underneath

    static final String DEF_INTERNAL = "CustomData/{pkg}/internal";
    static final String DEF_EXTERNAL = "CustomData/{pkg}/external";
    static final String DEF_STORAGE = "CustomData/{pkg}/storage";

    // Destination folder of each redirect. Absolute, or relative to the storage root.
    // "{pkg}" is replaced by the app's package name.
    String internalPath = DEF_INTERNAL;
    String externalPath = DEF_EXTERNAL;
    String storagePath = DEF_STORAGE;

    // Paths that may never be created (one per line). Absolute, or relative to the storage root;
    // "{pkg}" is replaced by the app's package name. Exact match on the whole path.
    String blockPaths = "";

    // Path-to-path redirects, one per line: "<full source path> => <full destination path>".
    // Both sides must be absolute; "{pkg}" is replaced by the app's package name.
    String pathRules = "";

    // Paths that are never redirected by any rule or folder redirect (one per line, wildcards allowed).
    String excludePaths = "";

    /** The exclusion list as clean entries (no empty lines, no duplicates). */
    List<String> excludeList() {
        List<String> out = new ArrayList<>();
        for (String l : excludePaths.split("\n")) {
            String n = l.trim();
            if (n.length() > 0 && !out.contains(n)) out.add(n);
        }
        return out;
    }

    /** The valid rules as [from, to] pairs (blank lines, '#' comments and non-absolute sides dropped). */
    List<String[]> ruleList() {
        List<String[]> out = new ArrayList<>();
        for (String l : pathRules.split("\n")) {
            String t = l.trim();
            if (t.length() == 0 || t.startsWith("#")) continue;
            int i = t.indexOf("=>");
            if (i < 0) i = t.indexOf("->");
            if (i < 0) continue;
            String from = t.substring(0, i).trim();
            String to = t.substring(i + 2).trim();
            if (from.startsWith("/") && to.startsWith("/")) out.add(new String[]{from, to});
        }
        return out;
    }

    /** The block list as clean entries (no empty lines, no duplicates). */
    List<String> blockList() {
        List<String> out = new ArrayList<>();
        for (String l : blockPaths.split("\n")) {
            String n = l.trim();
            if (n.length() > 0 && !out.contains(n)) out.add(n);
        }
        return out;
    }

    /** Resolves a configured path for one app to an absolute folder. */
    static File resolve(String sd, String pkg, String path) {
        String p = path.replace("{pkg}", pkg == null ? "<package>" : pkg).trim();
        return p.startsWith("/") ? new File(p) : new File(sd, p);
    }

    static final String DEF_CONFIG_DIR = "CustomData/.config";
    private static final String[] KNOWN = {"redirect.properties", "apps", "backup"};

    /** Fixed, well-known file that says where the (user-chosen) configuration folder is. */
    private static File pointerFile(String sd) {
        return new File(sd, "CustomData/.config-location");
    }

    private static String cacheSd;
    private static long cacheStamp = -2;
    private static File cacheDir;

    /** Where the settings live: the folder named in the pointer file, or the default one. */
    static synchronized File dir(String sd) {
        File ptr = pointerFile(sd);
        long stamp = ptr.isFile() ? ptr.lastModified() * 31 + ptr.length() : -1;
        if (sd.equals(cacheSd) && stamp == cacheStamp && cacheDir != null) return cacheDir;
        File d = new File(sd, DEF_CONFIG_DIR);
        if (stamp != -1) {
            String v = readLine(ptr);
            if (v != null && v.trim().length() > 0) d = resolveConfigPath(sd, v.trim());
        }
        cacheSd = sd;
        cacheStamp = stamp;
        cacheDir = d;
        return d;
    }

    static File resolveConfigPath(String sd, String text) {
        return text.startsWith("/") ? new File(text) : new File(sd, text);
    }

    /** The configuration folder as the user typed it (default if never changed). */
    static String configSetting(String sd) {
        File ptr = pointerFile(sd);
        String v = ptr.isFile() ? readLine(ptr) : null;
        return (v == null || v.trim().length() == 0) ? DEF_CONFIG_DIR : v.trim();
    }

    /**
     * Moves the settings (defaults, per-app overrides, backup settings) to another folder and
     * remembers it. Returns null on success, otherwise a short error message. Nothing is removed
     * from the old place unless everything was copied.
     */
    static synchronized String moveConfigDir(String sd, String newText) {
        String text = (newText == null || newText.trim().length() == 0) ? DEF_CONFIG_DIR : newText.trim();
        File from = dir(sd);
        File to = resolveConfigPath(sd, text);
        cacheSd = null;
        try {
            String f = from.getCanonicalPath(), t = to.getCanonicalPath();
            boolean same = f.equals(t);
            if (!same) {
                if (t.startsWith(f + "/") || f.startsWith(t + "/"))
                    return "The new folder can\u2019t be inside the current one, or contain it";
                if (!to.exists() && !to.mkdirs() && !to.exists()) return "Can\u2019t create " + to;
                for (String n : KNOWN) copyTree(new File(from, n), new File(to, n));
            }
            File ptr = pointerFile(sd);
            boolean isDefault = t.equals(new File(sd, DEF_CONFIG_DIR).getCanonicalPath());
            if (isDefault) {
                if (ptr.exists() && !ptr.delete()) return "Can\u2019t update the location file";
            } else {
                File pd = ptr.getParentFile();
                if (!pd.exists() && !pd.mkdirs() && !pd.exists()) return "Can\u2019t create " + pd;
                writeLine(ptr, text);
            }
            if (!same) {                                   // everything copied: remove the old copy
                for (String n : KNOWN) deleteTree(new File(from, n));
                from.delete();                             // only succeeds if now empty
            }
            return null;
        } catch (IOException e) {
            return e.getMessage() == null ? "Failed" : e.getMessage();
        } finally {
            cacheSd = null;
        }
    }

    private static String readLine(File f) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            return r.readLine();
        } catch (Throwable t) {
            return null;
        } finally {
            if (r != null) try { r.close(); } catch (Throwable ignored) { }
        }
    }

    private static void writeLine(File f, String line) throws IOException {
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try { w.write(line); w.write('\n'); } finally { w.close(); }
    }

    private static void copyTree(File src, File dst) throws IOException {
        if (!src.exists()) return;
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs() && !dst.exists()) throw new IOException("Can\u2019t create " + dst);
            File[] fs = src.listFiles();
            if (fs != null) for (File c : fs) copyTree(c, new File(dst, c.getName()));
            return;
        }
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(dst);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            in.close();
            if (out != null) out.close();
        }
        dst.setLastModified(src.lastModified());
    }

    private static void deleteTree(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) deleteTree(c);
        f.delete();
    }

    /** pkg == null -> the global defaults file. */
    static File file(String sd, String pkg) {
        if (pkg == null) return new File(dir(sd), "redirect.properties");
        return new File(new File(dir(sd), "apps"), pkg + ".properties");
    }

    static boolean hasOverride(String sd, String pkg) {
        return pkg != null && file(sd, pkg).isFile();
    }

    /** Effective config for a package: its override if present, otherwise the defaults. */
    static RedirectConfig load(String sd, String pkg) {
        if (hasOverride(sd, pkg)) return read(file(sd, pkg));
        return read(file(sd, null));
    }

    static boolean deleteOverride(String sd, String pkg) {
        File f = file(sd, pkg);
        return !f.exists() || f.delete();
    }

    /** Packages that currently have an override file. */
    static Set<String> overriddenPackages(String sd) {
        Set<String> out = new HashSet<>();
        try {
            File[] fs = new File(dir(sd), "apps").listFiles();
            if (fs == null) return out;
            for (File f : fs) {
                String n = f.getName();
                if (n.endsWith(".properties"))
                    out.add(n.substring(0, n.length() - ".properties".length()));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    RedirectConfig copy() {
        RedirectConfig c = new RedirectConfig();
        c.internal = internal;
        c.prefs = prefs;
        c.external = external;
        c.storage = storage;
        c.rawPaths = rawPaths;
        c.fakePaths = fakePaths;
        c.internalPath = internalPath;
        c.externalPath = externalPath;
        c.storagePath = storagePath;
        c.blockPaths = blockPaths;
        c.pathRules = pathRules;
        c.excludePaths = excludePaths;
        return c;
    }

    private static RedirectConfig read(File f) {
        RedirectConfig c = new RedirectConfig();
        FileInputStream in = null;
        try {
            if (!f.isFile()) return c;
            in = new FileInputStream(f);
            Properties p = new Properties();
            p.load(in);
            c.internal = bool(p, "internal", c.internal);
            c.prefs = bool(p, "prefs", c.prefs);
            c.external = bool(p, "external", c.external);
            c.storage = bool(p, "storage", c.storage);
            c.rawPaths = bool(p, "rawPaths", c.rawPaths);
            c.fakePaths = bool(p, "fakePaths", c.fakePaths);
            // legacy single "root" key (v2.0/v2.1)
            String r = p.getProperty("root");
            if (r != null && r.trim().length() > 0) {
                r = r.trim();
                c.internalPath = r + "/{pkg}/internal";
                c.externalPath = r + "/{pkg}/external";
                c.storagePath = r + "/{pkg}/storage";
            }
            c.internalPath = str(p, "internalPath", c.internalPath);
            c.externalPath = str(p, "externalPath", c.externalPath);
            c.storagePath = str(p, "storagePath", c.storagePath);
            // v2.9 stored plain names under "blockNames"; names are no longer supported, so that
            // key is ignored.
            String bp = p.getProperty("blockPaths");
            if (bp != null) c.blockPaths = bp;
            String pr = p.getProperty("pathRules");
            if (pr != null) c.pathRules = pr;
            String ex = p.getProperty("excludePaths");
            if (ex != null) c.excludePaths = ex;
        } catch (Throwable ignored) {
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
        return c;
    }

    boolean save(String sd, String pkg) {
        FileOutputStream out = null;
        try {
            File f = file(sd, pkg);
            File d = f.getParentFile();
            if (!d.exists() && !d.mkdirs() && !d.exists()) return false;
            Properties p = new Properties();
            p.setProperty("internal", String.valueOf(internal));
            p.setProperty("prefs", String.valueOf(prefs));
            p.setProperty("external", String.valueOf(external));
            p.setProperty("storage", String.valueOf(storage));
            p.setProperty("rawPaths", String.valueOf(rawPaths));
            p.setProperty("fakePaths", String.valueOf(fakePaths));
            p.setProperty("internalPath", internalPath);
            p.setProperty("externalPath", externalPath);
            p.setProperty("storagePath", storagePath);
            p.setProperty("blockPaths", blockPaths);
            p.setProperty("pathRules", pathRules);
            p.setProperty("excludePaths", excludePaths);
            out = new FileOutputStream(f);
            p.store(out, "Storage Redirect" + (pkg == null ? " (defaults)" : " - " + pkg));
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (out != null) try { out.close(); } catch (Throwable ignored) { }
        }
    }

    private static String str(Properties p, String k, String def) {
        String v = p.getProperty(k);
        return (v == null || v.trim().length() == 0) ? def : v.trim();
    }

    private static boolean bool(Properties p, String k, boolean def) {
        String v = p.getProperty(k);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }
}
