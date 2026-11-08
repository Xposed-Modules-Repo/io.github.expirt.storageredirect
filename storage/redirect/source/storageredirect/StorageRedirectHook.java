package io.github.expirt.storageredirect;

import android.os.Environment;
import android.system.ErrnoException;
import android.system.OsConstants;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Redirects the hooked app's storage to the folders configured for internal data, app external
 * data and main storage (each fully customizable, "{pkg}" = package name).
 *
 * Two modes (per app, set in the module's UI):
 *  - normal : the app is handed the custom folders (paths it sees are the real custom paths).
 *  - fake   : the app keeps seeing the STANDARD paths (/data/user/0/<pkg>, /storage/emulated/0/...,
 *             /storage/emulated/0/Android/data/<pkg>) and every Java-level file operation on them is
 *             redirected underneath (libcore Os / UnixFileSystem / SQLite hooks).
 *
 * Every hook is a "before" hook that sets a result only if the custom dir exists or could be
 * created; on any failure the original method runs, so the app never crashes because of this module.
 */
public class StorageRedirectHook implements IXposedHookLoadPackage {

    /** getDir() names left in the real private dir (WebView needs a real private dir). */
    private static final Set<String> KEEP_PRIVATE_DIRS = new HashSet<>(Arrays.asList("webview"));
    /** Sub-paths of the private data dir that are never redirected (dex/odex, native libs). */
    private static final String[] KEEP_PRIVATE_SUBPATHS = {"/code_cache", "/app_webview", "/lib"};
    private static final String TAG = "[StorageRedirect] ";

    /** libcore.io.Linux methods that take file paths (hooked in fake mode). */
    private static final Set<String> LINUX_PATH_METHODS = new HashSet<>(Arrays.asList(
            "access", "chmod", "chown", "lchown", "lstat", "stat", "mkdir", "mkfifo", "open",
            "readlink", "realpath", "remove", "rename", "statvfs", "symlink", "link",
            "utimensat", "setxattr", "getxattr", "removexattr", "listxattr"));

    private static String sPkg;
    private static String sSd;          // real storage root, e.g. /storage/emulated/0
    private static String sDataDir;     // real private data dir, e.g. /data/user/0/<pkg>
    private static File sInternalRoot;
    private static File sExternalRoot;
    private static File sStorageRoot;
    private static boolean sFake;
    private static Glob[] sBlocked = new Glob[0];      // canonical path patterns that may never be created
    private static Glob[] sRuleFrom = new Glob[0];     // path-to-path redirects, checked top to bottom
    private static String[] sRuleTo = new String[0];   // destination templates ({1}, {2}.. = wildcard captures)
    private static String[] sRuleGuard = new String[0]; // destination prefix: paths below it are never redirected again
    private static Glob[] sExclude = new Glob[0];      // paths that are never redirected
    private static String[] sScope = new String[0];       // real folders of the enabled redirects
    private static String[] sOwn = new String[0];     // our destination dirs: never rewritten again
    private static String[] sFrom = new String[0];
    private static String[] sTo = new String[0];
    private static int[] sKind = new int[0];
    private static final int K_INT = 0, K_EXT = 1, K_OBB = 2, K_STO = 3;
    private static volatile boolean sWarned;

    private enum Kind { DIR, DIRS, FILE }   // DIRS = File[] result, FILE = ensure parent only

    // resolver codes
    private static final int FILES = 1, CACHE = 2, NOBACKUP = 3, GETDIR = 4, DBPATH = 5,
            DBDIR = 6, PREFS = 7, EXT_FILES = 8, EXT_CACHE = 9, OBB = 10, MEDIA = 11,
            ENV_DATA = 12, ENV_FILES = 13, ENV_CACHE = 14, ENV_OBB = 15, ENV_MEDIA = 16,
            STORAGE_ROOT = 17, STORAGE_PUBLIC = 18;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        // Applies to whatever app this module is embedded in / scoped to.
        if (lpparam.packageName == null || "android".equals(lpparam.packageName)) return;
        sPkg = lpparam.packageName;
        try {
            // Everything that needs the REAL storage root is computed before hooking.
            sSd = Environment.getExternalStorageDirectory().getAbsolutePath();
            RedirectConfig cfg = RedirectConfig.load(sSd, sPkg);   // per-app override, else defaults
            List<String> blockEntries = cfg.blockList();
            boolean anyRedirect = cfg.internal || cfg.external || cfg.storage;
            List<String[]> ruleEntries = cfg.ruleList();
            if (!anyRedirect && blockEntries.isEmpty() && ruleEntries.isEmpty()) {
                XposedBridge.log(TAG + "all redirects disabled for " + sPkg);
                return;
            }
            sFake = cfg.fakePaths;
            sDataDir = (lpparam.appInfo != null && lpparam.appInfo.dataDir != null)
                    ? lpparam.appInfo.dataDir : "/data/user/0/" + sPkg;

            sInternalRoot = RedirectConfig.resolve(sSd, sPkg, cfg.internalPath);
            sExternalRoot = RedirectConfig.resolve(sSd, sPkg, cfg.externalPath);
            sStorageRoot = RedirectConfig.resolve(sSd, sPkg, cfg.storagePath);
            sOwn = new String[]{sInternalRoot.getAbsolutePath(), sExternalRoot.getAbsolutePath(),
                    sStorageRoot.getAbsolutePath()};
            // Block list: resolve each entry like a destination folder, then normalise it.
            sBlocked = compile(blockEntries, false);
            sExclude = compile(cfg.excludeList(), true);
            buildRules(ruleEntries);
            List<String> scope = new ArrayList<>();
            if (cfg.internal) scope.add(sOwn[0]);
            if (cfg.external) scope.add(sOwn[1]);
            if (cfg.storage) scope.add(sOwn[2]);
            sScope = scope.toArray(new String[0]);
            if (cfg.internal && cfg.external && sOwn[0].equals(sOwn[1]))
                XposedBridge.log(TAG + "WARNING: internal and external use the same folder");

            if (cfg.internal)
                for (String n : new String[]{"files", "cache", "databases", "shared_prefs", "no_backup"})
                    if (!isBlocked(new File(sInternalRoot, n).getPath())) ensure(new File(sInternalRoot, n));
            if (cfg.external) {
                if (!isBlocked(new File(sExternalRoot, "files").getPath()))
                    ensure(new File(sExternalRoot, "files"));
                if (!isBlocked(new File(sExternalRoot, "cache").getPath()))
                    ensure(new File(sExternalRoot, "cache"));
            }
            if (cfg.storage) ensure(sStorageRoot);

            ClassLoader cl = lpparam.classLoader;
            if (cfg.internal || cfg.external) {
                Class<?> ctx = XposedHelpers.findClassIfExists("android.app.ContextImpl", cl);
                if (ctx == null) XposedBridge.log(TAG + "ContextImpl not found");
                else {
                    if (cfg.internal) hookInternal(ctx, cfg.prefs);
                    if (cfg.external) hookExternal(ctx);
                }
            }
            if (!sFake) {                       // fake mode leaves Environment untouched
                if (cfg.external) hookEnvironmentAppDirs(cl);
                if (cfg.storage) hookMainStorage(cl);
            }

            boolean layer = anyRedirect && (sFake || cfg.rawPaths || cfg.storage);
            if (layer) buildRewriteTable(cfg);
            if (layer && !sFake) hookFileConstructors();
            // path-to-path redirects always work at the I/O level, in normal and fake mode
            if ((layer && sFake) || sRuleFrom.length > 0) hookSinks(cl);
            if (sBlocked.length > 0) hookBlock(cl);
            XposedBridge.log(TAG + "active for " + sPkg + (sFake ? " [fake paths]" : "")
                    + " internal=" + (cfg.internal ? sOwn[0] : "off")
                    + " external=" + (cfg.external ? sOwn[1] : "off")
                    + " storage=" + (cfg.storage ? sOwn[2] : "off")
                    + (sBlocked.length > 0 ? " blocked=" + sBlocked.length : "")
                    + (sRuleFrom.length > 0 ? " path-redirects=" + sRuleFrom.length : ""));
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    // ------------------------------------------------------------ hook groups
    // getCodeCacheDir() is deliberately NOT hooked: dex/odex output must stay private.
    private static void hookInternal(Class<?> ctx, boolean prefs) {
        hook(ctx, "getFilesDir", Kind.DIR, FILES);
        hook(ctx, "getCacheDir", Kind.DIR, CACHE);
        hook(ctx, "getNoBackupFilesDir", Kind.DIR, NOBACKUP);
        hook(ctx, "getDir", Kind.DIR, GETDIR);
        hook(ctx, "getDatabasePath", Kind.FILE, DBPATH);
        // private helpers used by openOrCreateDatabase / deleteDatabase / databaseList
        hook(ctx, "getDatabasesDir", Kind.DIR, DBDIR);
        if (prefs) hook(ctx, "getPreferencesDir", Kind.DIR, PREFS);
    }

    private static void hookExternal(Class<?> ctx) {
        hook(ctx, "getExternalFilesDir", Kind.DIR, EXT_FILES);
        hook(ctx, "getExternalFilesDirs", Kind.DIRS, EXT_FILES);
        hook(ctx, "getExternalCacheDir", Kind.DIR, EXT_CACHE);
        hook(ctx, "getExternalCacheDirs", Kind.DIRS, EXT_CACHE);
        hook(ctx, "getObbDir", Kind.DIR, OBB);
        hook(ctx, "getObbDirs", Kind.DIRS, OBB);
        hook(ctx, "getExternalMediaDirs", Kind.DIRS, MEDIA);
    }

    /** Best effort: hidden static builders some framework paths still use. */
    private static void hookEnvironmentAppDirs(ClassLoader cl) {
        Class<?> env = XposedHelpers.findClassIfExists("android.os.Environment", cl);
        if (env == null) return;
        hook(env, "buildExternalStorageAppDataDirs", Kind.DIRS, ENV_DATA);
        hook(env, "buildExternalStorageAppFilesDirs", Kind.DIRS, ENV_FILES);
        hook(env, "buildExternalStorageAppCacheDirs", Kind.DIRS, ENV_CACHE);
        hook(env, "buildExternalStorageAppObbDirs", Kind.DIRS, ENV_OBB);
        hook(env, "buildExternalStorageAppMediaDirs", Kind.DIRS, ENV_MEDIA);
    }

    /** Normal mode, main storage: the app is handed a private virtual sdcard. */
    private static void hookMainStorage(ClassLoader cl) {
        Class<?> env = XposedHelpers.findClassIfExists("android.os.Environment", cl);
        if (env == null) return;
        hook(env, "getExternalStorageDirectory", Kind.DIR, STORAGE_ROOT);
        hook(env, "getExternalStoragePublicDirectory", Kind.DIR, STORAGE_PUBLIC);
    }

    // -------------------------------------------------------------- resolver
    /** The REAL (custom) folder for a hooked call, or null to leave the call alone. */
    private static File resolve(int code, XC_MethodHook.MethodHookParam p) {
        Object a0 = (p.args != null && p.args.length > 0) ? p.args[0] : null;
        switch (code) {
            case FILES:    return new File(sInternalRoot, "files");
            case CACHE:    return new File(sInternalRoot, "cache");
            case NOBACKUP: return new File(sInternalRoot, "no_backup");
            case DBDIR:    return new File(sInternalRoot, "databases");
            case PREFS:    return new File(sInternalRoot, "shared_prefs");
            case GETDIR: {
                String n = (String) a0;
                if (n == null || KEEP_PRIVATE_DIRS.contains(n)) return null;
                return new File(sInternalRoot, "app_" + n);
            }
            case DBPATH: {   // relative names only; absolute/invalid left to the original
                String n = (String) a0;
                if (n == null || n.isEmpty() || n.indexOf('/') >= 0) return null;
                return new File(new File(sInternalRoot, "databases"), n);
            }
            case EXT_FILES: {
                File b = new File(sExternalRoot, "files");
                String t = (String) a0;
                return (t == null || t.isEmpty()) ? b : new File(b, t);
            }
            case EXT_CACHE: return new File(sExternalRoot, "cache");
            case OBB:       return new File(sExternalRoot, "obb");
            case MEDIA:     return new File(sExternalRoot, "media");
            case ENV_DATA:  return envDir(a0, p, null);
            case ENV_FILES: return envDir(a0, p, "files");
            case ENV_CACHE: return envDir(a0, p, "cache");
            case ENV_OBB:   return envDir(a0, p, "obb");
            case ENV_MEDIA: return envDir(a0, p, "media");
            case STORAGE_ROOT: return sStorageRoot;
            case STORAGE_PUBLIC: {
                String t = (String) a0;
                return (t == null || t.isEmpty()) ? sStorageRoot : new File(sStorageRoot, t);
            }
            default: return null;
        }
    }

    private static File envDir(Object pkg, XC_MethodHook.MethodHookParam p, String sub) {
        if (p.args.length != 1 || !sPkg.equals(pkg)) return null;
        return sub == null ? sExternalRoot : new File(sExternalRoot, sub);
    }

    // --------------------------------------------------------------- helpers
    private static void hook(Class<?> cls, String method, final Kind kind, final int code) {
        try {
            Set<XC_MethodHook.Unhook> hooked = XposedBridge.hookAllMethods(cls, method,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                File f = resolve(code, param);
                                if (f == null) return;
                                File ok = ensure(kind == Kind.FILE ? f.getParentFile() : f);
                                if (ok == null) return;          // fall back to the original
                                // fake mode: the app is shown the standard path, I/O goes to f
                                File shown = sFake ? toVirtual(f) : f;
                                param.setResult(kind == Kind.DIRS ? new File[]{shown} : shown);
                            } catch (Throwable t) {
                                XposedBridge.log(t);             // original runs
                            }
                        }
                    });
            if (hooked.isEmpty())
                XposedBridge.log(TAG + "not found: " + cls.getSimpleName() + "." + method);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static File ensure(File dir) {
        if (dir == null) return null;
        try {
            if (dir.exists() || dir.mkdirs() || dir.exists()) return dir;
        } catch (Throwable ignored) {
        }
        if (!sWarned) {
            sWarned = true;
            XposedBridge.log(TAG + "cannot create " + dir
                    + " (storage permission?) - falling back to default path");
        }
        return null;
    }

    // -------------------------------------------- fake mode: real -> standard path
    /** The standard-looking path an app expects for a folder inside one of our destinations. */
    static File toVirtual(File real) {
        String p = real.getAbsolutePath();
        String rest = under(p, sOwn[0]);
        if (rest != null) return new File(sDataDir + rest);
        rest = under(p, sOwn[1]);
        if (rest != null) {
            if (rest.equals("/obb") || rest.startsWith("/obb/"))
                return new File(sSd + "/Android/obb/" + sPkg + rest.substring(4));
            return new File(sSd + "/Android/data/" + sPkg + rest);
        }
        rest = under(p, sOwn[2]);
        if (rest != null) return new File(sSd + rest);
        return real;
    }

    private static String under(String p, String base) {
        if (p.equals(base)) return "";
        return p.startsWith(base + "/") ? p.substring(base.length()) : null;
    }

    // ------------------------------------------- standard path -> real (rewrite table)
    private static void buildRewriteTable(RedirectConfig cfg) {
        List<String> from = new ArrayList<>();
        List<String> to = new ArrayList<>();
        List<Integer> kinds = new ArrayList<>();
        String in = sInternalRoot.getAbsolutePath();
        String ex = sExternalRoot.getAbsolutePath();

        // Most specific first: first match wins.
        if (cfg.internal) {
            add(from, to, kinds, "/data/data/" + sPkg, in, K_INT);
            add(from, to, kinds, "/data/user/0/" + sPkg, in, K_INT);
            add(from, to, kinds, sDataDir, in, K_INT);
        }
        String[] roots = {sSd, "/sdcard", "/storage/emulated/0", "/storage/self/primary", "/mnt/sdcard"};
        if (cfg.external) {
            for (String r : roots) {
                add(from, to, kinds, r + "/Android/data/" + sPkg, ex, K_EXT);
                add(from, to, kinds, r + "/Android/obb/" + sPkg, ex, K_OBB);
            }
        }
        if (cfg.storage) {
            String st = sStorageRoot.getAbsolutePath();
            for (String r : roots) add(from, to, kinds, r, st, K_STO);
        }
        sFrom = from.toArray(new String[0]);
        sTo = to.toArray(new String[0]);
        sKind = new int[kinds.size()];
        for (int i = 0; i < sKind.length; i++) sKind[i] = kinds.get(i);
    }

    private static void add(List<String> from, List<String> to, List<Integer> kinds,
                            String f, String t, int kind) {
        from.add(f);
        to.add(t);
        kinds.add(kind);
    }

    /** Maps a standard path to its real location; anything else is returned unchanged. */
    static String rewrite(String p) {
        String r = applyRules(p);
        return r != null ? r : rewriteStd(p);
    }

    /** What the I/O sinks use: the path rules always, the standard-path table only in fake mode. */
    static String sinkRewrite(String p) {
        String r = applyRules(p);
        if (r != null) return r;
        return sFake ? rewriteStd(p) : p;
    }

    private static String rewriteStd(String p) {
        if (excluded(p)) return p;
        if (p == null || p.isEmpty() || p.charAt(0) != '/') return p;
        for (String k : sOwn) {                      // already one of our real folders
            int rl = k.length();
            if (p.startsWith(k) && (p.length() == rl || p.charAt(rl) == '/')) return p;
        }
        if (sFake && sSd != null && p.startsWith(sSd + "/")) {
            for (String k : sOwn)                    // parents of our folders (mkdirs, listing)
                if (k.startsWith(p + "/")) return p;
        }
        for (int i = 0; i < sFrom.length; i++) {
            String f = sFrom[i];
            if (!p.startsWith(f)) continue;
            int n = f.length();
            if (p.length() > n && p.charAt(n) != '/') continue;   // /data/data/com.example2
            String rest = p.substring(n);
            for (String k : KEEP_PRIVATE_SUBPATHS)
                if (rest.equals(k) || rest.startsWith(k + "/")) return p;
            return (sKind[i] == K_OBB ? sTo[i] + "/obb" : sTo[i]) + rest;
        }
        return p;
    }

    // ------------------------------------------------- path-to-path redirects
    /** Resolves each entry like a destination folder (relative to the storage root, {pkg}) and compiles it. */
    private static Glob[] compile(List<String> entries, boolean below) {
        List<Glob> out = new ArrayList<>();
        for (String e : entries) {
            String c = canon(RedirectConfig.resolve(sSd, sPkg, e).getPath());
            if (c != null) out.add(new Glob(c, below));
        }
        return out.toArray(new Glob[0]);
    }

    private static boolean excluded(String p) {
        if (sExclude.length == 0 || p == null || p.isEmpty() || p.charAt(0) != '/') return false;
        String c = canon(p);
        if (c == null) return false;
        for (Glob g : sExclude) if (g.match(c) != null) return true;
        return false;
    }

    /** The part of a destination template before the first {n}, as a folder; null = no usable guard. */
    private static String guardOf(String to) {
        int i = to.indexOf('{');
        if (i < 0) return to;
        int s = to.lastIndexOf('/', i);
        return s <= 0 ? null : to.substring(0, s);
    }

    /**
     * Turns the configured "from => to" lines into compiled rules. The order of the list is the
     * priority: the first rule that matches a path wins. A rule is dropped (and logged) when its
     * destination folder can't be created or its destination uses a {n} its source doesn't have,
     * so a bad rule never breaks the app.
     */
    private static void buildRules(List<String[]> entries) {
        List<Glob> from = new ArrayList<>();
        List<String> to = new ArrayList<>();
        List<String> guard = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String[] e : entries) {
            String f = canon(e[0].replace("{pkg}", sPkg));
            String t = canon(e[1].replace("{pkg}", sPkg));
            if (f == null || t == null || f.equals("/") || f.equals(t)) {
                XposedBridge.log(TAG + "ignored path redirect: " + e[0] + " => " + e[1]);
                continue;
            }
            Glob g = new Glob(f, true);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{(\\d+)\\}").matcher(t);
            boolean badRef = false;
            while (m.find()) if (Integer.parseInt(m.group(1)) < 1 || Integer.parseInt(m.group(1)) > g.groups) badRef = true;
            if (badRef) {
                XposedBridge.log(TAG + "ignored path redirect (bad {n}): " + e[0] + " => " + e[1]);
                continue;
            }
            String g0 = guardOf(t);
            File dir = t.indexOf('{') >= 0 ? (g0 == null ? null : new File(g0)) : new File(t).getParentFile();
            if (dir != null && ensure(dir) == null) {
                XposedBridge.log(TAG + "path redirect dropped (can't create " + dir + "): " + e[0]);
                continue;
            }
            if (seen.contains(f)) continue;     // same source twice: the first rule wins
            seen.add(f);
            from.add(g);
            to.add(t);
            guard.add(g0);
        }
        sRuleFrom = from.toArray(new Glob[0]);
        sRuleTo = to.toArray(new String[0]);
        sRuleGuard = guard.toArray(new String[0]);
    }

    /**
     * The redirected path for p, or null when no rule applies (or p is excluded). A rule covers
     * what its source matches and everything below it (/a -> /b sends /a/x/y to /b/x/y). A
     * path that is already at a destination is never rewritten again.
     */
    static String applyRules(String p) {
        if (sRuleFrom.length == 0 || p == null || p.isEmpty() || p.charAt(0) != '/') return null;
        String c = canon(p);
        if (c == null) return null;
        for (Glob g : sExclude) if (g.match(c) != null) return null;
        for (String d : sRuleGuard)
            if (d != null && (c.equals(d) || c.startsWith(d + "/"))) return null;
        for (int i = 0; i < sRuleFrom.length; i++) {
            String[] caps = sRuleFrom[i].match(c);
            if (caps == null) continue;
            String r = sRuleTo[i];
            for (int k = 0; k < caps.length - 1; k++) r = r.replace("{" + (k + 1) + "}", caps[k]);
            return r + caps[caps.length - 1];
        }
        return null;
    }

    // ------------------------------------------------------------ block creation
    private static final int B_MKDIR = 1, B_OPEN = 2, B_RENAME = 3, B_CREATE = 4;

    private static final String[] STORAGE_ALIASES =
            {"/sdcard", "/storage/emulated/0", "/storage/self/primary", "/mnt/sdcard"};

    /**
     * Normalises an absolute path: collapses //, . and .. and drops a trailing slash, then maps
     * the alias spellings of the storage root and of the private data dir to the real ones, so
     * /sdcard/x, /storage/emulated/0/x and /storage/self/primary/x all compare equal.
     * Returns null for a relative path.
     */
    private static String canon(String p) {
        if (p == null || p.isEmpty() || p.charAt(0) != '/') return null;
        List<String> st = new ArrayList<>();
        for (String s : p.split("/")) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) {
                if (!st.isEmpty()) st.remove(st.size() - 1);
                continue;
            }
            st.add(s);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : st) sb.append('/').append(s);
        String q = sb.length() == 0 ? "/" : sb.toString();
        if (sSd != null) {
            for (String r : STORAGE_ALIASES) {
                String m = swapRoot(q, r, sSd);
                if (m != null) { q = m; break; }
            }
        }
        if (sDataDir != null && sPkg != null) {
            String[] data = {"/data/data/" + sPkg, "/data/user/0/" + sPkg};
            for (String r : data) {
                String m = swapRoot(q, r, sDataDir);
                if (m != null) { q = m; break; }
            }
        }
        return q;
    }

    private static String swapRoot(String q, String from, String to) {
        if (q.equals(from)) return to;
        return q.startsWith(from + "/") ? to + q.substring(from.length()) : null;
    }

    private static boolean listed(String canonPath) {
        if (canonPath == null) return false;
        for (Glob b : sBlocked) if (b.match(canonPath) != null) return true;
        return false;
    }

    /**
     * True if creating this path must be refused: the whole path equals a blocked path. The path
     * is checked as the app wrote it, as the real redirected location (fake mode: standard path
     * -> real folder) and as the standard-looking path (normal mode: real folder -> standard
     * path), so an entry matches whichever spelling it was written in.
     */
    static boolean isBlocked(String path) {
        if (path == null || path.isEmpty() || sBlocked.length == 0) return false;
        String c = canon(path);
        if (c == null) return false;
        if (listed(c)) return true;
        if (listed(canon(rewrite(c)))) return true;
        if (sOwn.length == 3 && listed(canon(toVirtual(new File(c)).getPath()))) return true;
        return false;
    }

    /**
     * Java file creation goes through libcore: mkdir(), open(O_CREAT), rename() and, on some
     * versions, UnixFileSystem.create*(). A blocked creation fails with EACCES (or false).
     * Native (C/C++) code and java.nio.file are not covered.
     */
    private static void hookBlock(ClassLoader cl) {
        int n = 0;
        Class<?> linux = XposedHelpers.findClassIfExists("libcore.io.Linux", cl);
        if (linux != null) {
            for (Method m : linux.getDeclaredMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                String name = m.getName();
                int kind = 0;
                if (name.equals("mkdir") && pt.length == 2 && pt[0] == String.class) kind = B_MKDIR;
                else if (name.equals("open") && pt.length == 3 && pt[0] == String.class) kind = B_OPEN;
                else if (name.equals("rename") && pt.length == 2 && pt[0] == String.class
                        && pt[1] == String.class) kind = B_RENAME;
                if (kind != 0 && hookBlockMethod(m, kind)) n++;
            }
        }
        Class<?> ufs = XposedHelpers.findClassIfExists("java.io.UnixFileSystem", cl);
        if (ufs != null) {
            for (Method m : ufs.getDeclaredMethods())
                if (Modifier.isNative(m.getModifiers()) && m.getName().startsWith("create")
                        && m.getReturnType() == boolean.class && takesPath(m)
                        && hookBlockMethod(m, B_CREATE)) n++;
        }
        XposedBridge.log(TAG + "block-creation hooks: " + n);
    }

    private static boolean hookBlockMethod(Method m, final int kind) {
        try {
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        blockCheck(kind, param);
                    } catch (Throwable ignored) {
                    }
                }
            });
            return true;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "can't hook " + m.getName() + ": " + t);
            return false;
        }
    }

    private static void blockCheck(int kind, XC_MethodHook.MethodHookParam p) throws Throwable {
        Object[] a = p.args;
        Object a0 = a[0];
        String path = a0 instanceof String ? (String) a0 : a0 instanceof File ? ((File) a0).getPath() : null;
        switch (kind) {
            case B_MKDIR:
                if (isBlocked(path)) p.setThrowable(new ErrnoException("mkdir", OsConstants.EACCES));
                break;
            case B_OPEN: {
                int flags = (Integer) a[1];
                if ((flags & OsConstants.O_CREAT) != 0 && isBlocked(path) && !new File(path).exists())
                    p.setThrowable(new ErrnoException("open", OsConstants.EACCES));
                break;
            }
            case B_RENAME:
                if (isBlocked((String) a[1])) p.setThrowable(new ErrnoException("rename", OsConstants.EACCES));
                break;
            case B_CREATE:
                if (isBlocked(path)) p.setResult(Boolean.FALSE);
                break;
            default:
        }
    }

    // ------------------------------------------ normal mode: rewrite File(String...)
    private static void hookFileConstructors() {
        XC_MethodHook h = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args[0] instanceof String)
                        param.args[0] = rewrite((String) param.args[0]);
                } catch (Throwable ignored) {
                }
            }
        };
        try {
            XposedHelpers.findAndHookConstructor(File.class, String.class, h);
            XposedHelpers.findAndHookConstructor(File.class, String.class, String.class, h);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    // ------------------------------------------------ fake mode: redirect the I/O itself
    /**
     * The app keeps File objects with standard paths. Every place that finally touches the
     * filesystem from Java gets its path argument mapped to the real folder first:
     * libcore.io.Linux (everything behind FileInputStream/FileOutputStream/RandomAccessFile,
     * File.exists/mkdir/rename/...), java.io.UnixFileSystem + File natives (listing, delete,
     * length, ...) and SQLiteConnection (database files).
     * Native (C/C++) code and java.nio.file are NOT covered.
     */
    private static void hookSinks(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object[] a = param.args;
                    if (a == null) return;
                    for (int i = 0; i < a.length; i++) {
                        Object o = a[i];
                        if (o instanceof String) {
                            String s = (String) o;
                            String r = sinkRewrite(s);
                            if (!r.equals(s)) a[i] = r;
                        } else if (o instanceof File) {
                            String s = ((File) o).getPath();
                            String r = sinkRewrite(s);
                            if (!r.equals(s)) a[i] = new File(r);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        };
        int n = hookPathMethods("libcore.io.Linux", cl, h, LINUX_PATH_METHODS);
        n += hookPathMethods("java.io.UnixFileSystem", cl, h, null);
        n += hookPathMethods("java.io.File", cl, h, null);
        n += hookPathMethods("android.database.sqlite.SQLiteConnection", cl, h, null);
        XposedBridge.log(TAG + "fake-path sinks hooked: " + n);
    }

    /** Hooks methods that take a String/File: the named ones, or (names == null) all natives. */
    private static int hookPathMethods(String cls, ClassLoader cl, XC_MethodHook h, Set<String> names) {
        Class<?> c = XposedHelpers.findClassIfExists(cls, cl);
        if (c == null) {
            XposedBridge.log(TAG + "sink class missing: " + cls);
            return 0;
        }
        int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            boolean pick = names != null ? names.contains(m.getName())
                    : Modifier.isNative(m.getModifiers());
            if (!pick || !takesPath(m)) continue;
            try {
                XposedBridge.hookMethod(m, h);
                n++;
            } catch (Throwable t) {
                XposedBridge.log(TAG + "can't hook " + cls + "." + m.getName() + ": " + t);
            }
        }
        return n;
    }

    private static boolean takesPath(Method m) {
        for (Class<?> t : m.getParameterTypes())
            if (t == String.class || t == File.class) return true;
        return false;
    }
}
