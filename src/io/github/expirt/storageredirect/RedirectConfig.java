package io.github.expirt.storageredirect;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Settings shared between the UI (writer) and the hook (reader, inside the target app).
 *
 * Fixed location:
 *   /storage/emulated/0/Android/media/uninviting/config.properties        defaults for every app
 *   /storage/emulated/0/Android/media/uninviting/redirect.properties      compatibility mirror
 *   /storage/emulated/0/Android/media/uninviting/.config-location         location pointer
 *   /storage/emulated/0/Android/media/uninviting/apps/<package>.properties  per-app override
 */
final class RedirectConfig {

    public static final String MODULE_PACKAGE = "io.github.expirt.storageredirect";
    public static final String DEF_INTERNAL_CONFIG_DIR = "/data/data/" + MODULE_PACKAGE + "/files/storageredirect";
    static final String DEF_CONFIG_DIR = "Android/media/uninviting";
    private static final String[] KNOWN = {"config.properties", "redirect.properties", "apps", "backup", ".config-location"};

    private static volatile Context sContext;

    public final long sourceMtime;
    public final String sourcePath;

    boolean internal = false;    // /data/data/<pkg> - default off to protect hooked apps on startup
    boolean prefs = true;        // shared_prefs (only meaningful with internal)
    boolean external = true;     // /Android/data/<pkg> + /Android/obb/<pkg>
    boolean storage = false;     // main storage /storage/emulated/0 (virtual sdcard per app)
    boolean rawPaths = false;    // rewrite hardcoded path strings
    boolean fakePaths = false;   // the app keeps seeing the standard paths; I/O is redirected underneath
    boolean debug = false;       // detailed diagnostic logging to logcat

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

    public RedirectConfig() {
        this.sourceMtime = 0L;
        this.sourcePath = null;
    }

    public RedirectConfig(long sourceMtime, String sourcePath) {
        this.sourceMtime = sourceMtime;
        this.sourcePath = sourcePath;
    }

    public static void init(Context ctx) {
        if (ctx != null) {
            sContext = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        }
    }

    public static Context getContext() {
        return sContext;
    }

    /**
     * Checks if a path represents the default configuration directory
     * (e.g. "Android/media/uninviting" or "android/media/uninviting").
     */
    public static boolean isDefaultConfigDir(String path, String sd) {
        if (path == null) return true;
        String t = path.trim();
        if (t.isEmpty()) return true;
        if (t.equalsIgnoreCase(DEF_CONFIG_DIR) || t.equalsIgnoreCase("android/media/uninviting")
                || t.equalsIgnoreCase("Android/media/uninviting")) return true;
        if (sd != null) {
            String abs1 = sd + "/Android/media/uninviting";
            String abs2 = sd + "/android/media/uninviting";
            if (t.equalsIgnoreCase(abs1) || t.equalsIgnoreCase(abs2)) return true;
        }
        return false;
    }

    /**
     * Resolves the config root directory.
     * Fixed location: /storage/emulated/0/Android/media/uninviting
     * Contains config.properties (defaults), redirect.properties (compatibility mirror),
     * .config-location (pointer file), and apps/ (per-app overrides).
     */
    public static File resolveConfigRoot(Context context, String sd) {
        if (context == null) context = sContext;
        if (context == null) {
            try {
                context = de.robv.android.xposed.AndroidAppHelper.currentApplication();
            } catch (Throwable ignored) {
            }
        }
        if (sd == null) {
            try {
                sd = Environment.getExternalStorageDirectory().getAbsolutePath();
            } catch (Throwable t) {
                sd = "/storage/emulated/0";
            }
        }

        // Fixed location: /storage/emulated/0/Android/media/uninviting
        File fixedRoot = new File(sd, DEF_CONFIG_DIR);
        if (!fixedRoot.exists()) {
            File alt = new File(sd, "android/media/uninviting");
            if (alt.exists()) {
                fixedRoot = alt;
            } else {
                try {
                    fixedRoot.mkdirs();
                } catch (Throwable ignored) {
                }
            }
        }

        // Pointer file in the fixed location
        File ptr = new File(fixedRoot, ".config-location");
        if (ptr.isFile()) {
            String custom = readLine(ptr);
            if (custom != null && custom.trim().length() > 0 && !isDefaultConfigDir(custom, sd)) {
                File customDir = resolveConfigPath(sd, custom.trim());
                if (customDir != null && (customDir.isDirectory() || customDir.exists())) {
                    return customDir;
                }
            }
        }

        // Migrate any legacy pointer from previous directories (CustomData, Android/data)
        String[] legacyPtrPaths = {"CustomData/.config-location", "Android/data/uninviting/.config-location", "Android/data/.config-location"};
        for (String lp : legacyPtrPaths) {
            File legacyPtr = new File(sd, lp);
            if (legacyPtr.isFile()) {
                String custom = readLine(legacyPtr);
                legacyPtr.delete();
                if (custom != null && custom.trim().length() > 0 && !isDefaultConfigDir(custom, sd)
                        && !custom.trim().equals("CustomData/.config")) {
                    File customDir = resolveConfigPath(sd, custom.trim());
                    if (customDir != null && (customDir.isDirectory() || customDir.exists())) {
                        try {
                            writeLine(ptr, custom.trim());
                        } catch (Throwable ignored) {
                        }
                        return customDir;
                    }
                }
            }
        }

        // Migration: If fixedRoot has no configs yet, migrate from legacy locations!
        boolean hasConfig = new File(fixedRoot, "config.properties").isFile()
                || new File(fixedRoot, "redirect.properties").isFile()
                || new File(fixedRoot, "apps").isDirectory();
        if (!hasConfig) {
            String[] legacyDirs = {"Android/data/uninviting", "CustomData/.config"};
            for (String ld : legacyDirs) {
                File legacyDir = new File(sd, ld);
                if (legacyDir.isDirectory()) {
                    try {
                        copyTree(legacyDir, fixedRoot);
                    } catch (Throwable ignored) {
                    }
                }
            }
            File moduleInternal = new File("/data/data/" + MODULE_PACKAGE + "/files/storageredirect");
            if (moduleInternal.isDirectory()) {
                try {
                    copyTree(moduleInternal, fixedRoot);
                } catch (Throwable ignored) {
                }
            }
        }

        // Ensure config.properties and redirect.properties are kept in sync
        File cfgFile = new File(fixedRoot, "config.properties");
        File redirFile = new File(fixedRoot, "redirect.properties");
        if (!cfgFile.isFile() && redirFile.isFile()) {
            try {
                copyFile(redirFile, cfgFile);
                makeWorldReadable(cfgFile);
            } catch (Throwable ignored) {
            }
        } else if (cfgFile.isFile() && !redirFile.isFile()) {
            try {
                copyFile(cfgFile, redirFile);
                makeWorldReadable(redirFile);
            } catch (Throwable ignored) {
            }
        }

        // Ensure .config-location exists in fixedRoot pointing to DEF_CONFIG_DIR
        if (!ptr.exists()) {
            try {
                writeLine(ptr, DEF_CONFIG_DIR);
                makeWorldReadable(ptr);
            } catch (Throwable ignored) {
            }
        }

        return fixedRoot;
    }

    /** The exclusion list as clean entries (no empty lines, no duplicates). */
    List<String> excludeList() {
        List<String> out = new ArrayList<>();
        for (String l : excludePaths.split("\n")) {
            String n = l.trim();
            if (n.length() > 0 && !out.contains(n)) out.add(n);
        }
        return out;
    }

    /** The valid rules as [from, to] pairs (blank lines and '#' comments dropped). */
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
            if (!from.isEmpty() && !to.isEmpty()) out.add(new String[]{from, to});
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

    /** Pointer file storing custom location; lives in the same directory as config.properties. */
    static File pointerFile(String sd) {
        if (sd == null) sd = "/storage/emulated/0";
        File dir = new File(sd, DEF_CONFIG_DIR);
        return new File(dir, ".config-location");
    }

    /** Where legacy shared-storage settings lived prior to migration. */
    static synchronized File legacyDir(String sd) {
        File defaultDir = new File(sd, "CustomData/.config");
        File ptr = new File(defaultDir, ".config-location");
        File legacyPtr = new File(sd, "CustomData/.config-location");
        File d = defaultDir;
        if (ptr.isFile()) {
            String v = readLine(ptr);
            if (v != null && v.trim().length() > 0) d = resolveConfigPath(sd, v.trim());
        } else if (legacyPtr.isFile()) {
            String v = readLine(legacyPtr);
            if (v != null && v.trim().length() > 0) d = resolveConfigPath(sd, v.trim());
        }
        return d;
    }

    /**
     * One-time atomic migration from legacy /sdcard/CustomData/.config into internal storage.
     */
    private static synchronized void checkAndMigrate(File internalDir, String sd) {
        if (internalDir.exists()) return;

        File legDir = legacyDir(sd);
        if (!legDir.exists() || !legDir.isDirectory()) {
            internalDir.mkdirs();
            return;
        }

        File defProps = new File(legDir, "redirect.properties");
        File appsDir = new File(legDir, "apps");
        if (!defProps.isFile() && !appsDir.isDirectory()) {
            internalDir.mkdirs();
            return;
        }

        File parent = internalDir.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        File tempDir = new File(parent, "storageredirect_migrating_" + System.currentTimeMillis());
        if (tempDir.exists()) deleteTree(tempDir);
        if (!tempDir.mkdirs()) {
            internalDir.mkdirs();
            return;
        }

        try {
            copyTree(legDir, tempDir);
            if (verifyTree(legDir, tempDir)) {
                if (tempDir.renameTo(internalDir)) {
                    logDiag("One-time config migration verified and completed: " + legDir.getAbsolutePath()
                            + " -> " + internalDir.getAbsolutePath());
                    File oldPtr = new File(sd, "CustomData/.config-location");
                    if (oldPtr.exists()) oldPtr.delete();
                } else if (internalDir.exists()) {
                    deleteTree(tempDir);
                    File oldPtr = new File(sd, "CustomData/.config-location");
                    if (oldPtr.exists()) oldPtr.delete();
                } else {
                    internalDir.mkdirs();
                    copyTree(tempDir, internalDir);
                    deleteTree(tempDir);
                    logDiag("One-time config migration copied: " + legDir.getAbsolutePath()
                            + " -> " + internalDir.getAbsolutePath());
                    File oldPtr = new File(sd, "CustomData/.config-location");
                    if (oldPtr.exists()) oldPtr.delete();
                }
            } else {
                logDiag("Config migration verification failed between " + legDir.getAbsolutePath()
                        + " and " + tempDir.getAbsolutePath());
                deleteTree(tempDir);
                internalDir.mkdirs();
            }
        } catch (Throwable t) {
            logDiag("Error during config migration: " + t.getMessage());
            deleteTree(tempDir);
            internalDir.mkdirs();
        }
    }

    private static boolean verifyTree(File src, File dst) {
        if (!src.exists()) return true;
        if (src.isDirectory()) {
            if (!dst.exists() || !dst.isDirectory()) return false;
            File[] files = src.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!verifyTree(f, new File(dst, f.getName()))) return false;
                }
            }
            return true;
        } else {
            return dst.isFile() && dst.length() == src.length();
        }
    }

    static void logDiag(String msg) {
        try {
            de.robv.android.xposed.XposedBridge.log("[StorageRedirect] " + msg);
        } catch (Throwable t) {
            android.util.Log.i("StorageRedirect", msg);
        }
    }

    /** Where the settings live: resolves internal root or custom configured location. */
    static File dir(Context ctx, String sd) {
        return resolveConfigRoot(ctx, sd);
    }

    static File dir(String sd) {
        return resolveConfigRoot(sContext, sd);
    }

    static File resolveConfigPath(String sd, String text) {
        if (sd == null) sd = "/storage/emulated/0";
        if (text == null || text.trim().isEmpty()) {
            return new File(sd, DEF_CONFIG_DIR);
        }
        String t = text.trim();
        return t.startsWith("/") ? new File(t) : new File(sd, t);
    }

    /** The configuration folder as currently configured. */
    static String configSetting(String sd) {
        File ptr = pointerFile(sd);
        String v = ptr.isFile() ? readLine(ptr) : null;
        return (v == null || v.trim().length() == 0) ? DEF_CONFIG_DIR : v.trim();
    }

    /**
     * Moves configuration to another folder.
     */
    static synchronized String moveConfigDir(String sd, String newText) {
        String text = (newText == null || newText.trim().length() == 0) ? DEF_CONFIG_DIR : newText.trim();
        File from = dir(sd);
        File to = resolveConfigPath(sd, text);
        try {
            String f = from.getCanonicalPath(), t = to.getCanonicalPath();
            boolean same = f.equals(t);
            if (!same) {
                if (t.startsWith(f + "/") || f.startsWith(t + "/"))
                    return "The new folder can\u2019t be inside the current one, or contain it";
                if (!to.exists() && !to.mkdirs() && !to.exists()) return "Can\u2019t create " + to;
                for (String n : KNOWN) copyTree(new File(from, n), new File(to, n));
                for (String n : KNOWN) deleteTree(new File(from, n));
            }
            File defaultDir = new File(sd, DEF_CONFIG_DIR);
            File defaultPtr = new File(defaultDir, ".config-location");
            File toPtr = new File(to, ".config-location");
            boolean isDefault = isDefaultConfigDir(text, sd) || t.equals(defaultDir.getCanonicalPath());
            if (isDefault) {
                if (!defaultDir.exists()) defaultDir.mkdirs();
                writeLine(defaultPtr, DEF_CONFIG_DIR);
                makeWorldReadable(defaultPtr);
            } else {
                if (!defaultDir.exists()) defaultDir.mkdirs();
                writeLine(defaultPtr, text);
                writeLine(toPtr, text);
                makeWorldReadable(defaultPtr);
                makeWorldReadable(toPtr);
            }
            File loosePtr = new File(sd, "CustomData/.config-location");
            if (loosePtr.exists()) loosePtr.delete();
            return null;
        } catch (IOException e) {
            return e.getMessage() == null ? "Failed" : e.getMessage();
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
        copyFile(src, dst);
    }

    private static void copyFile(File src, File dst) throws IOException {
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
        if (!f.exists()) return;
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) deleteTree(c);
        f.delete();
    }

    /** pkg == null -> the global defaults file. Checks config.properties first, then redirect.properties. */
    static File file(Context ctx, String sd, String pkg) {
        File d = resolveConfigRoot(ctx, sd);
        if (pkg == null) {
            File cfg = new File(d, "config.properties");
            if (cfg.isFile()) return cfg;
            File redir = new File(d, "redirect.properties");
            if (redir.isFile()) return redir;
            return cfg;
        }
        return new File(new File(d, "apps"), pkg + ".properties");
    }

    static File file(String sd, String pkg) {
        return file(sContext, sd, pkg);
    }

    static boolean hasOverride(Context ctx, String sd, String pkg) {
        return pkg != null && file(ctx, sd, pkg).isFile();
    }

    static boolean hasOverride(String sd, String pkg) {
        return hasOverride(sContext, sd, pkg);
    }

    /** Effective config for a package: its override if present, otherwise the defaults. */
    static RedirectConfig load(Context ctx, String sd, String pkg) {
        if (hasOverride(ctx, sd, pkg)) return read(file(ctx, sd, pkg));
        return read(file(ctx, sd, null));
    }

    static RedirectConfig load(String sd, String pkg) {
        return load(sContext, sd, pkg);
    }

    static boolean deleteOverride(Context ctx, String sd, String pkg) {
        File f = file(ctx, sd, pkg);
        return !f.exists() || f.delete();
    }

    static boolean deleteOverride(String sd, String pkg) {
        return deleteOverride(sContext, sd, pkg);
    }

    /** Packages that currently have an override file. */
    static Set<String> overriddenPackages(Context ctx, String sd) {
        Set<String> out = new HashSet<>();
        try {
            File[] fs = new File(dir(ctx, sd), "apps").listFiles();
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

    static Set<String> overriddenPackages(String sd) {
        return overriddenPackages(sContext, sd);
    }

    RedirectConfig copy() {
        RedirectConfig c = new RedirectConfig(sourceMtime, sourcePath);
        c.internal = internal;
        c.prefs = prefs;
        c.external = external;
        c.storage = storage;
        c.rawPaths = rawPaths;
        c.fakePaths = fakePaths;
        c.debug = debug;
        c.internalPath = internalPath;
        c.externalPath = externalPath;
        c.storagePath = storagePath;
        c.blockPaths = blockPaths;
        c.pathRules = pathRules;
        c.excludePaths = excludePaths;
        return c;
    }

    static RedirectConfig read(File f) {
        long mtime = f != null && f.isFile() ? f.lastModified() : 0L;
        String path = f != null ? f.getAbsolutePath() : null;
        RedirectConfig c = new RedirectConfig(mtime, path);
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
            c.debug = bool(p, "debug", c.debug);
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

    boolean save(Context ctx, String sd, String pkg) {
        FileOutputStream out = null;
        try {
            File configDir = dir(ctx, sd);
            File f;
            if (pkg == null) {
                f = new File(configDir, "config.properties");
            } else {
                f = new File(new File(configDir, "apps"), pkg + ".properties");
            }
            File d = f.getParentFile();
            if (!d.exists() && !d.mkdirs() && !d.exists()) return false;
            Properties p = new Properties();
            p.setProperty("internal", String.valueOf(internal));
            p.setProperty("prefs", String.valueOf(prefs));
            p.setProperty("external", String.valueOf(external));
            p.setProperty("storage", String.valueOf(storage));
            p.setProperty("rawPaths", String.valueOf(rawPaths));
            p.setProperty("fakePaths", String.valueOf(fakePaths));
            p.setProperty("debug", String.valueOf(debug));
            p.setProperty("internalPath", internalPath);
            p.setProperty("externalPath", externalPath);
            p.setProperty("storagePath", storagePath);
            p.setProperty("blockPaths", blockPaths);
            p.setProperty("pathRules", pathRules);
            p.setProperty("excludePaths", excludePaths);
            out = new FileOutputStream(f);
            p.store(out, "Storage Redirect" + (pkg == null ? " (defaults)" : " - " + pkg));
            out.close();
            out = null;
            makeWorldReadable(f);

            if (pkg == null) {
                // Mirror to redirect.properties for backwards compatibility
                try {
                    File mirror = new File(configDir, "redirect.properties");
                    copyFile(f, mirror);
                    makeWorldReadable(mirror);
                } catch (Throwable ignored) {
                }
            }

            File locFile = new File(configDir, ".config-location");
            if (!locFile.exists()) {
                writeLine(locFile, configSetting(sd));
                makeWorldReadable(locFile);
            }
            File loosePtr = new File(sd, "CustomData/.config-location");
            if (loosePtr.exists()) loosePtr.delete();

            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (out != null) try { out.close(); } catch (Throwable ignored) { }
        }
    }

    static void makeWorldReadable(File f) {
        if (f == null || !f.exists()) return;
        try {
            f.setReadable(true, false);
            if (f.isDirectory()) {
                f.setExecutable(true, false);
                File parent = f.getParentFile();
                if (parent != null) {
                    parent.setReadable(true, false);
                    parent.setExecutable(true, false);
                    File grandParent = parent.getParentFile();
                    if (grandParent != null) {
                        grandParent.setExecutable(true, false);
                    }
                }
                File[] children = f.listFiles();
                if (children != null) {
                    for (File c : children) makeWorldReadable(c);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    boolean save(String sd, String pkg) {
        return save(sContext, sd, pkg);
    }

    // ------------------------------------------------ Module Settings Backup & Restore
    /**
     * Backs up the entire module configuration folder (defaults, app overrides, backup settings)
     * into a zip archive at the specified destination file.
     * Returns null on success, or an error message on failure.
     */
    public static String backupModuleConfig(Context context, String sd, File destZip) {
        File configDir = resolveConfigRoot(context, sd);
        if (!configDir.exists() || !configDir.isDirectory()) {
            return "Configuration directory does not exist: " + configDir.getAbsolutePath();
        }
        File parent = destZip.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            return "Cannot create backup destination directory: " + parent.getAbsolutePath();
        }
        File tempZip = new File(destZip.getParentFile(), destZip.getName() + ".tmp." + System.currentTimeMillis());
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(tempZip)));
            zipSubtree(configDir, "", zos);
            zos.finish();
            zos.close();
            zos = null;
            if (destZip.exists() && !destZip.delete()) {
                tempZip.delete();
                return "Cannot replace existing backup file: " + destZip.getAbsolutePath();
            }
            if (!tempZip.renameTo(destZip)) {
                copyFile(tempZip, destZip);
                tempZip.delete();
            }
            logDiag("Module configuration backed up to " + destZip.getAbsolutePath());
            return null;
        } catch (Throwable t) {
            if (tempZip.exists()) tempZip.delete();
            return t.getMessage() != null ? t.getMessage() : "Module backup failed";
        } finally {
            if (zos != null) try { zos.close(); } catch (Throwable ignored) {}
        }
    }

    private static void zipSubtree(File dir, String base, ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        byte[] buf = new byte[16 * 1024];
        for (File f : files) {
            String entryName = base.isEmpty() ? f.getName() : base + "/" + f.getName();
            if (f.isDirectory()) {
                ZipEntry ze = new ZipEntry(entryName + "/");
                ze.setTime(f.lastModified());
                zos.putNextEntry(ze);
                zos.closeEntry();
                zipSubtree(f, entryName, zos);
            } else if (f.isFile()) {
                ZipEntry ze = new ZipEntry(entryName);
                ze.setTime(f.lastModified());
                zos.putNextEntry(ze);
                FileInputStream fis = new FileInputStream(f);
                try {
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        zos.write(buf, 0, len);
                    }
                } finally {
                    fis.close();
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * Restores module configuration from a zip archive into the active configuration directory.
     * Returns null on success, or an error message on failure.
     */
    public static String restoreModuleConfig(Context context, String sd, File srcZip) {
        if (!srcZip.isFile()) {
            return "Backup file does not exist: " + srcZip.getAbsolutePath();
        }
        File configDir = resolveConfigRoot(context, sd);
        File parent = configDir.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            return "Cannot create configuration directory parent: " + parent.getAbsolutePath();
        }
        File tempDir = new File(parent, "storageredirect_restore_tmp_" + System.currentTimeMillis());
        if (tempDir.exists()) deleteTree(tempDir);
        if (!tempDir.mkdirs()) {
            return "Cannot create temporary directory for restore";
        }

        ZipFile zf = null;
        try {
            zf = new ZipFile(srcZip);
            java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
            byte[] buf = new byte[16 * 1024];
            boolean foundAny = false;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.contains("..")) continue; // Avoid zip slip
                File target = new File(tempDir, name);
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    File p = target.getParentFile();
                    if (p != null && !p.exists()) p.mkdirs();
                    InputStream is = zf.getInputStream(entry);
                    FileOutputStream fos = new FileOutputStream(target);
                    try {
                        int len;
                        while ((len = is.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                        }
                    } finally {
                        is.close();
                        fos.close();
                    }
                    target.setLastModified(entry.getTime());
                    foundAny = true;
                }
            }
            zf.close();
            zf = null;

            if (!foundAny) {
                deleteTree(tempDir);
                return "Backup archive contains no valid configuration files";
            }

            if (!configDir.exists()) configDir.mkdirs();
            copyTree(tempDir, configDir);
            deleteTree(tempDir);
            logDiag("Module configuration restored from " + srcZip.getAbsolutePath() + " to " + configDir.getAbsolutePath());
            return null;
        } catch (Throwable t) {
            deleteTree(tempDir);
            return t.getMessage() != null ? t.getMessage() : "Module restore failed";
        } finally {
            if (zf != null) try { zf.close(); } catch (Throwable ignored) {}
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
