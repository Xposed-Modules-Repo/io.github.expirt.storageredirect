package io.github.expirt.storageredirect;

import android.content.Context;
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
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.AndroidAppHelper;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Redirects the hooked app's storage to the folders configured for internal data, app external
 * data and main storage (each fully customizable, "{pkg}" = package name), and applies
 * Path-to-Path redirection rules recursively with longest-prefix priority.
 *
 * Supports live configuration reloading:
 *  - Rate-limited freshness check (at most once every 800 ms per process)
 *  - Lock-free atomic swap of immutable ActiveState
 *  - Dynamic activation/deactivation without restarting the target app
 */
public class StorageRedirectHook implements IXposedHookLoadPackage {

    /** getDir() names left in the real private dir (WebView needs a real private dir). */
    private static final Set<String> KEEP_PRIVATE_DIRS = new HashSet<>(Arrays.asList("webview"));
    /** Sub-paths of the private data dir that are never redirected (dex/odex, native libs). */
    private static final String[] KEEP_PRIVATE_SUBPATHS = {"/code_cache", "/app_webview", "/lib"};
    private static final String TAG = "[StorageRedirect] ";

    /** libcore.io.Linux methods that take file paths (hooked in fake mode and for path redirects). */
    private static final Set<String> LINUX_PATH_METHODS = new HashSet<>(Arrays.asList(
            "access", "chmod", "chown", "lchown", "lstat", "stat", "mkdir", "mkfifo", "open",
            "readlink", "realpath", "remove", "rename", "statvfs", "symlink", "link",
            "utimensat", "setxattr", "getxattr", "removexattr", "listxattr"));

    private static final int K_INT = 0, K_EXT = 1, K_OBB = 2, K_STO = 3;

    // Resolver codes
    private static final int FILES = 1, CACHE = 2, NOBACKUP = 3, GETDIR = 4, DBPATH = 5,
            DBDIR = 6, PREFS = 7, EXT_FILES = 8, EXT_CACHE = 9, OBB = 10, MEDIA = 11,
            ENV_DATA = 12, ENV_FILES = 13, ENV_CACHE = 14, ENV_OBB = 15, ENV_MEDIA = 16,
            STORAGE_ROOT = 17, STORAGE_PUBLIC = 18;

    private enum Kind { DIR, DIRS, FILE }

    private static final long CHECK_INTERVAL_MS = 800L;
    private static final AtomicLong sLastCheckTime = new AtomicLong(0L);

    private static volatile Context sContext;
    private static String sPkg;
    private static String sSd;          // Real storage root, e.g. /storage/emulated/0
    private static String sDataDir;     // Real private data dir, e.g. /data/user/0/<pkg>

    /**
     * Immutable active state published via single volatile swap.
     * Prevents race conditions and guarantees consistent views across concurrent threads.
     */
    private static final class ActiveState {
        final RedirectConfig config;
        final long configMtime;
        final String configPath;
        final boolean hasOverride;
        final File internalRoot;
        final File externalRoot;
        final File storageRoot;
        final String[] own;
        final boolean fake;
        final boolean debug;
        final PathEngine pathEngine;
        final Glob[] blocked;
        final Glob[] exclude;
        final String[] scope;
        final String[] from;
        final String[] to;
        final int[] kind;
        final boolean active;

        ActiveState(RedirectConfig config, long configMtime, String configPath, boolean hasOverride,
                    File internalRoot, File externalRoot, File storageRoot, String[] own,
                    boolean fake, boolean debug, PathEngine pathEngine, Glob[] blocked, Glob[] exclude,
                    String[] scope, String[] from, String[] to, int[] kind, boolean active) {
            this.config = config;
            this.configMtime = configMtime;
            this.configPath = configPath;
            this.hasOverride = hasOverride;
            this.internalRoot = internalRoot;
            this.externalRoot = externalRoot;
            this.storageRoot = storageRoot;
            this.own = own;
            this.fake = fake;
            this.debug = debug;
            this.pathEngine = pathEngine;
            this.blocked = blocked;
            this.exclude = exclude;
            this.scope = scope;
            this.from = from;
            this.to = to;
            this.kind = kind;
            this.active = active;
        }
    }

    private static final class RewriteTable {
        final String[] from;
        final String[] to;
        final int[] kind;

        RewriteTable(String[] from, String[] to, int[] kind) {
            this.from = from;
            this.to = to;
            this.kind = kind;
        }
    }

    private static volatile ActiveState sState;

    private static final ThreadLocal<Boolean> sInHook = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (lpparam.packageName == null || "android".equals(lpparam.packageName)) return;
        sPkg = lpparam.packageName;
        try {
            sSd = Environment.getExternalStorageDirectory().getAbsolutePath();
            sDataDir = (lpparam.appInfo != null && lpparam.appInfo.dataDir != null)
                    ? lpparam.appInfo.dataDir : "/data/user/0/" + sPkg;

            ClassLoader cl = lpparam.classLoader;

            // Hook Application lifecycle to capture real application context as soon as available
            try {
                Class<?> appCls = XposedHelpers.findClassIfExists("android.app.Application", cl);
                if (appCls != null) {
                    XC_MethodHook appHook = new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Context ctx = (Context) param.thisObject;
                                if (ctx != null) {
                                    sContext = ctx;
                                    RedirectConfig.init(ctx);
                                    checkFreshness();
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    };
                    XposedHelpers.findAndHookMethod(appCls, "onCreate", appHook);
                    XposedHelpers.findAndHookMethod(appCls, "attachBaseContext", Context.class, appHook);
                }
            } catch (Throwable ignored) {
            }

            // Build and publish initial active state
            checkAndReload();

            // Install hooks ONCE per process (hooks remain active and query sState dynamically)
            Class<?> ctx = XposedHelpers.findClassIfExists("android.app.ContextImpl", cl);
            if (ctx != null) {
                hookInternal(ctx);
                hookExternal(ctx);
            } else {
                XposedBridge.log(TAG + "ContextImpl not found");
            }

            hookEnvironmentAppDirs(cl);
            hookMainStorage(cl);
            hookFileConstructors();
            hookSinks(cl);
            hookBlock(cl);

            ActiveState state = sState;
            XposedBridge.log(TAG + "active for " + sPkg
                    + (state != null && state.fake ? " [fake paths]" : "")
                    + (state != null && state.debug ? " [debug-diagnostics]" : "")
                    + (state != null && state.config.internal ? " internal=" + state.own[0] : " internal=off")
                    + (state != null && state.config.external ? " external=" + state.own[1] : " external=off")
                    + (state != null && state.config.storage ? " storage=" + state.own[2] : " storage=off")
                    + (state != null && state.blocked.length > 0 ? " blocked=" + state.blocked.length : "")
                    + (state != null && state.pathEngine != null ? " path-redirects=" + state.pathEngine.getRules().size() : ""));
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    // ------------------------------------------------------------ live config reload
    /**
     * Lock-free rate-limited freshness check invoked at the entry of hooked path methods.
     * Steady-state cost: single volatile read. At most one stat every 800 ms per process.
     */
    private static void checkFreshness() {
        if (Boolean.TRUE.equals(sInHook.get())) return;
        long now = System.currentTimeMillis();
        long last = sLastCheckTime.get();
        if (now - last < CHECK_INTERVAL_MS) {
            return;
        }
        if (!sLastCheckTime.compareAndSet(last, now)) {
            return;
        }
        sInHook.set(Boolean.TRUE);
        try {
            checkAndReload();
        } finally {
            sInHook.set(Boolean.FALSE);
        }
    }

    private static void checkAndReload() {
        boolean wasInHook = Boolean.TRUE.equals(sInHook.get());
        sInHook.set(Boolean.TRUE);
        try {
            if (sPkg == null || sSd == null) return;
            Context ctx = sContext;
            if (ctx == null) {
                try {
                    ctx = AndroidAppHelper.currentApplication();
                    if (ctx != null) {
                        sContext = ctx;
                        RedirectConfig.init(ctx);
                    }
                } catch (Throwable ignored) {
                }
            }

            File configRoot = RedirectConfig.resolveConfigRoot(ctx, sSd);
            File overrideFile = new File(new File(configRoot, "apps"), sPkg + ".properties");
            File defaultFile = new File(configRoot, "config.properties");
            if (!defaultFile.isFile()) {
                File alt = new File(configRoot, "redirect.properties");
                if (alt.isFile()) defaultFile = alt;
            }
            boolean hasOverride = overrideFile.isFile();
            File targetFile = hasOverride ? overrideFile : defaultFile;
            long mtime = targetFile.isFile() ? targetFile.lastModified() : -1L;
            String targetPath = targetFile.getAbsolutePath();

            ActiveState current = sState;
            if (current != null) {
                boolean changed = (hasOverride != current.hasOverride)
                        || !targetPath.equals(current.configPath)
                        || (mtime != current.configMtime);
                if (!changed) return;
            }

            ActiveState newState = buildActiveState(ctx, sSd, sPkg, sDataDir, targetFile, hasOverride, mtime);
            sState = newState;
            initFolders(newState);
            if (newState.debug) {
                XposedBridge.log(TAG + "Live config reloaded for " + sPkg + " (mtime=" + mtime
                        + ", override=" + hasOverride + ")");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error in checkAndReload: " + t);
        } finally {
            if (!wasInHook) {
                sInHook.set(Boolean.FALSE);
            }
        }
    }

    private static ActiveState buildActiveState(Context ctx, String sd, String pkg, String dataDir,
                                                File targetFile, boolean hasOverride, long mtime) {
        RedirectConfig cfg = RedirectConfig.read(targetFile);
        boolean anyRedirect = cfg.internal || cfg.external || cfg.storage;
        List<String> blockEntries = cfg.blockList();
        List<String[]> ruleEntries = cfg.ruleList();
        boolean active = anyRedirect || !blockEntries.isEmpty() || !ruleEntries.isEmpty();

        File internalRoot = RedirectConfig.resolve(sd, pkg, cfg.internalPath);
        File externalRoot = RedirectConfig.resolve(sd, pkg, cfg.externalPath);
        File storageRoot = RedirectConfig.resolve(sd, pkg, cfg.storagePath);
        String[] own = new String[]{internalRoot.getAbsolutePath(), externalRoot.getAbsolutePath(),
                storageRoot.getAbsolutePath()};

        Glob[] blocked = compile(sd, pkg, dataDir, blockEntries, false);
        Glob[] exclude = compile(sd, pkg, dataDir, cfg.excludeList(), true);

        PathEngine pathEngine = null;
        if (!ruleEntries.isEmpty() || cfg.debug) {
            pathEngine = new PathEngine(sd, pkg, dataDir);
            pathEngine.setDebug(cfg.debug);
            pathEngine.setLogger(new PathEngine.DiagnosticLogger() {
                @Override
                public void log(String op, String orig, String resolved, String rule, boolean success, String error) {
                    if (cfg.debug) {
                        XposedBridge.log(TAG + "[DIAG] op=" + op + " orig=\"" + orig + "\" -> dest=\""
                                + resolved + "\"" + (rule != null ? " rule=\"" + rule + "\"" : "")
                                + " success=" + success + (error != null ? " error=\"" + error + "\"" : ""));
                    }
                }
            });
            pathEngine.buildRules(ruleEntries);
        }

        List<String> scope = new ArrayList<>();
        if (cfg.internal) scope.add(own[0]);
        if (cfg.external) scope.add(own[1]);
        if (cfg.storage) scope.add(own[2]);

        RewriteTable table = buildRewriteTable(cfg, internalRoot, externalRoot, storageRoot, sd, pkg, dataDir);

        return new ActiveState(cfg, mtime, targetFile.getAbsolutePath(), hasOverride,
                internalRoot, externalRoot, storageRoot, own, cfg.fakePaths, cfg.debug,
                pathEngine, blocked, exclude, scope.toArray(new String[0]),
                table.from, table.to, table.kind, active);
    }

    private static void initFolders(ActiveState state) {
        if (state == null || !state.active) return;
        try {
            if (state.config.internal) {
                for (String n : new String[]{"files", "cache", "databases", "shared_prefs", "no_backup"}) {
                    File d = new File(state.internalRoot, n);
                    if (!isBlocked(state, d.getPath())) ensure(d);
                }
            }
            if (state.config.external) {
                File files = new File(state.externalRoot, "files");
                File cache = new File(state.externalRoot, "cache");
                if (!isBlocked(state, files.getPath())) ensure(files);
                if (!isBlocked(state, cache.getPath())) ensure(cache);
            }
            if (state.config.storage) {
                ensure(state.storageRoot);
            }
        } catch (Throwable t) {
            if (state.debug) XposedBridge.log(TAG + "Error initializing folders on reload: " + t);
        }
    }

    // ------------------------------------------------------------ hook groups
    private static void hookInternal(Class<?> ctx) {
        hook(ctx, "getFilesDir", Kind.DIR, FILES);
        hook(ctx, "getCacheDir", Kind.DIR, CACHE);
        hook(ctx, "getNoBackupFilesDir", Kind.DIR, NOBACKUP);
        hook(ctx, "getDir", Kind.DIR, GETDIR);
        hook(ctx, "getDatabasePath", Kind.FILE, DBPATH);
        hook(ctx, "getDatabasesDir", Kind.DIR, DBDIR);
        hook(ctx, "getPreferencesDir", Kind.DIR, PREFS);
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

    private static void hookEnvironmentAppDirs(ClassLoader cl) {
        Class<?> env = XposedHelpers.findClassIfExists("android.os.Environment", cl);
        if (env == null) return;
        hook(env, "buildExternalStorageAppDataDirs", Kind.DIRS, ENV_DATA);
        hook(env, "buildExternalStorageAppFilesDirs", Kind.DIRS, ENV_FILES);
        hook(env, "buildExternalStorageAppCacheDirs", Kind.DIRS, ENV_CACHE);
        hook(env, "buildExternalStorageAppObbDirs", Kind.DIRS, ENV_OBB);
        hook(env, "buildExternalStorageAppMediaDirs", Kind.DIRS, ENV_MEDIA);
    }

    private static void hookMainStorage(ClassLoader cl) {
        Class<?> env = XposedHelpers.findClassIfExists("android.os.Environment", cl);
        if (env == null) return;
        hook(env, "getExternalStorageDirectory", Kind.DIR, STORAGE_ROOT);
        hook(env, "getExternalStoragePublicDirectory", Kind.DIR, STORAGE_PUBLIC);
    }

    // -------------------------------------------------------------- resolver
    private static File resolve(int code, XC_MethodHook.MethodHookParam p) {
        checkFreshness();
        ActiveState state = sState;
        if (state == null || !state.active) return null;

        Object a0 = (p.args != null && p.args.length > 0) ? p.args[0] : null;

        switch (code) {
            case FILES:
                if (!state.config.internal) return null;
                return new File(state.internalRoot, "files");
            case CACHE:
                if (!state.config.internal) return null;
                return new File(state.internalRoot, "cache");
            case NOBACKUP:
                if (!state.config.internal) return null;
                return new File(state.internalRoot, "no_backup");
            case DBDIR:
                if (!state.config.internal) return null;
                return new File(state.internalRoot, "databases");
            case PREFS:
                if (!state.config.internal || !state.config.prefs) return null;
                return new File(state.internalRoot, "shared_prefs");
            case GETDIR: {
                if (!state.config.internal) return null;
                String n = (String) a0;
                if (n == null || KEEP_PRIVATE_DIRS.contains(n)) return null;
                return new File(state.internalRoot, "app_" + n);
            }
            case DBPATH: {
                if (!state.config.internal) return null;
                String n = (String) a0;
                if (n == null || n.isEmpty() || n.indexOf('/') >= 0) return null;
                return new File(new File(state.internalRoot, "databases"), n);
            }
            case EXT_FILES: {
                if (!state.config.external) return null;
                File b = new File(state.externalRoot, "files");
                String t = (String) a0;
                return (t == null || t.isEmpty()) ? b : new File(b, t);
            }
            case EXT_CACHE:
                if (!state.config.external) return null;
                return new File(state.externalRoot, "cache");
            case OBB:
                if (!state.config.external) return null;
                return new File(state.externalRoot, "obb");
            case MEDIA:
                if (!state.config.external) return null;
                return new File(state.externalRoot, "media");
            case ENV_DATA:
                if (state.fake || !state.config.external) return null;
                return envDir(state, a0, p, null);
            case ENV_FILES:
                if (state.fake || !state.config.external) return null;
                return envDir(state, a0, p, "files");
            case ENV_CACHE:
                if (state.fake || !state.config.external) return null;
                return envDir(state, a0, p, "cache");
            case ENV_OBB:
                if (state.fake || !state.config.external) return null;
                return envDir(state, a0, p, "obb");
            case ENV_MEDIA:
                if (state.fake || !state.config.external) return null;
                return envDir(state, a0, p, "media");
            case STORAGE_ROOT:
                if (state.fake || !state.config.storage) return null;
                return state.storageRoot;
            case STORAGE_PUBLIC: {
                if (state.fake || !state.config.storage) return null;
                String t = (String) a0;
                return (t == null || t.isEmpty()) ? state.storageRoot : new File(state.storageRoot, t);
            }
            default:
                return null;
        }
    }

    private static File envDir(ActiveState state, Object pkg, XC_MethodHook.MethodHookParam p, String sub) {
        if (p.args.length != 1 || !sPkg.equals(pkg)) return null;
        return sub == null ? state.externalRoot : new File(state.externalRoot, sub);
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
                                if (ok == null) return;
                                ActiveState state = sState;
                                boolean fake = state != null && state.fake;
                                File shown = fake ? toVirtual(state, f) : f;
                                param.setResult(kind == Kind.DIRS ? new File[]{shown} : shown);
                            } catch (Throwable t) {
                                XposedBridge.log(t);
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
        return PathEngine.ensureDirInternal(dir);
    }

    // -------------------------------------------- fake mode: real -> standard path
    static File toVirtual(ActiveState state, File real) {
        if (state == null || real == null) return real;
        String p = real.getAbsolutePath();
        String rest = under(p, state.own[0]);
        if (rest != null) return new File(sDataDir + rest);
        rest = under(p, state.own[1]);
        if (rest != null) {
            if (rest.equals("/obb") || rest.startsWith("/obb/"))
                return new File(sSd + "/Android/obb/" + sPkg + rest.substring(4));
            return new File(sSd + "/Android/data/" + sPkg + rest);
        }
        rest = under(p, state.own[2]);
        if (rest != null) return new File(sSd + rest);
        return real;
    }

    private static String under(String p, String base) {
        if (p.equals(base)) return "";
        return p.startsWith(base + "/") ? p.substring(base.length()) : null;
    }

    // ------------------------------------------- standard path -> real (rewrite table)
    private static RewriteTable buildRewriteTable(RedirectConfig cfg, File internalRoot, File externalRoot,
                                                  File storageRoot, String sd, String pkg, String dataDir) {
        boolean layer = (cfg.internal || cfg.external || cfg.storage) && (cfg.fakePaths || cfg.rawPaths || cfg.storage);
        if (!layer) {
            return new RewriteTable(new String[0], new String[0], new int[0]);
        }
        List<String> from = new ArrayList<>();
        List<String> to = new ArrayList<>();
        List<Integer> kinds = new ArrayList<>();
        String in = internalRoot.getAbsolutePath();
        String ex = externalRoot.getAbsolutePath();

        if (cfg.internal) {
            add(from, to, kinds, "/data/data/" + pkg, in, K_INT);
            add(from, to, kinds, "/data/user/0/" + pkg, in, K_INT);
            if (dataDir != null) add(from, to, kinds, dataDir, in, K_INT);
        }
        String[] roots = {sd, "/sdcard", "/storage/emulated/0", "/storage/self/primary", "/mnt/sdcard"};
        if (cfg.external) {
            for (String r : roots) {
                add(from, to, kinds, r + "/Android/data/" + pkg, ex, K_EXT);
                add(from, to, kinds, r + "/Android/obb/" + pkg, ex, K_OBB);
            }
        }
        if (cfg.storage) {
            String st = storageRoot.getAbsolutePath();
            for (String r : roots) add(from, to, kinds, r, st, K_STO);
        }
        int[] k = new int[kinds.size()];
        for (int i = 0; i < k.length; i++) k[i] = kinds.get(i);
        return new RewriteTable(from.toArray(new String[0]), to.toArray(new String[0]), k);
    }

    private static void add(List<String> from, List<String> to, List<Integer> kinds,
                            String f, String t, int kind) {
        from.add(f);
        to.add(t);
        kinds.add(kind);
    }

    /** Maps a standard path to its real location; anything else is returned unchanged. */
    static String rewrite(String p) {
        checkFreshness();
        ActiveState state = sState;
        if (state == null || !state.active) return p;
        String r = applyRules(state, p);
        return r != null ? r : rewriteStd(state, p);
    }

    /** What the I/O sinks use: the path rules always, the standard-path table only in fake mode. */
    static String sinkRewrite(String p) {
        checkFreshness();
        ActiveState state = sState;
        if (state == null || !state.active) return p;
        String r = applyRules(state, p);
        if (r != null) return r;
        return state.fake ? rewriteStd(state, p) : p;
    }

    private static String rewriteStd(ActiveState state, String p) {
        if (excluded(state, p)) return p;
        if (p == null || p.isEmpty() || p.charAt(0) != '/') return p;
        for (String k : state.own) {
            int rl = k.length();
            if (p.startsWith(k) && (p.length() == rl || p.charAt(rl) == '/')) return p;
        }
        if (state.fake && sSd != null && p.startsWith(sSd + "/")) {
            for (String k : state.own)
                if (k.startsWith(p + "/")) return p;
        }
        for (int i = 0; i < state.from.length; i++) {
            String f = state.from[i];
            if (!p.startsWith(f)) continue;
            int n = f.length();
            if (p.length() > n && p.charAt(n) != '/') continue;
            String rest = p.substring(n);
            for (String k : KEEP_PRIVATE_SUBPATHS)
                if (rest.equals(k) || rest.startsWith(k + "/")) return p;
            return (state.kind[i] == K_OBB ? state.to[i] + "/obb" : state.to[i]) + rest;
        }
        return p;
    }

    // ------------------------------------------------- path-to-path redirects
    private static Glob[] compile(String sd, String pkg, String dataDir, List<String> entries, boolean below) {
        List<Glob> out = new ArrayList<>();
        for (String e : entries) {
            String c = canonPath(RedirectConfig.resolve(sd, pkg, e).getPath(), sd, dataDir, pkg);
            if (c != null) out.add(new Glob(c, below));
        }
        return out.toArray(new Glob[0]);
    }

    private static boolean excluded(ActiveState state, String p) {
        if (state == null || state.exclude.length == 0 || p == null || p.isEmpty() || p.charAt(0) != '/') return false;
        String c = canon(state, p);
        if (c == null) return false;
        for (Glob g : state.exclude) if (g.match(c) != null) return true;
        return false;
    }

    static String applyRules(ActiveState state, String p) {
        if (state == null || state.pathEngine == null || p == null || p.isEmpty() || p.charAt(0) != '/') return null;
        if (excluded(state, p)) return null;
        return state.pathEngine.resolvePath(p);
    }

    static String canon(ActiveState state, String p) {
        if (state != null && state.pathEngine != null) {
            return state.pathEngine.canon(p);
        }
        return PathEngine.canonPath(p, sSd, sDataDir, sPkg);
    }

    private static String canonPath(String p, String sd, String dataDir, String pkg) {
        return PathEngine.canonPath(p, sd, dataDir, pkg);
    }

    // ------------------------------------------------------------ block creation
    private static final int B_MKDIR = 1, B_OPEN = 2, B_RENAME = 3, B_CREATE = 4;

    private static boolean listed(ActiveState state, String canonPath) {
        if (state == null || canonPath == null) return false;
        for (Glob b : state.blocked) if (b.match(canonPath) != null) return true;
        return false;
    }

    static boolean isBlocked(String path) {
        checkFreshness();
        ActiveState state = sState;
        if (state == null || !state.active) return false;
        return isBlocked(state, path);
    }

    static boolean isBlocked(ActiveState state, String path) {
        if (state == null || path == null || path.isEmpty() || state.blocked.length == 0) return false;
        String c = canon(state, path);
        if (c == null) return false;
        if (listed(state, c)) return true;
        if (listed(state, canon(state, rewrite(path)))) return true;
        if (state.own.length == 3 && listed(state, canon(state, toVirtual(state, new File(c)).getPath()))) return true;
        return false;
    }

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
                    if (Boolean.TRUE.equals(sInHook.get())) return;
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
                if (Boolean.TRUE.equals(sInHook.get())) return;
                sInHook.set(Boolean.TRUE);
                try {
                    Object[] a = param.args;
                    if (a == null || a.length == 0) return;
                    if (a.length == 1 && a[0] instanceof String) {
                        a[0] = rewrite((String) a[0]);
                    } else if (a.length == 2) {
                        if (a[0] instanceof String && a[1] instanceof String) {
                            String parent = (String) a[0];
                            String child = (String) a[1];
                            String combined;
                            if (parent == null || parent.isEmpty()) {
                                combined = child;
                            } else if (child == null || child.isEmpty()) {
                                combined = parent;
                            } else if (parent.endsWith("/")) {
                                combined = parent + child;
                            } else {
                                combined = parent + "/" + child;
                            }
                            String rewritten = rewrite(combined);
                            if (rewritten != null && !rewritten.equals(combined)) {
                                a[0] = rewritten;
                                a[1] = "";
                            }
                        } else if (a[0] instanceof File && a[1] instanceof String) {
                            File parent = (File) a[0];
                            String child = (String) a[1];
                            String parentPath = parent != null ? parent.getPath() : "";
                            String combined;
                            if (parentPath.isEmpty()) {
                                combined = child;
                            } else if (child == null || child.isEmpty()) {
                                combined = parentPath;
                            } else if (parentPath.endsWith("/")) {
                                combined = parentPath + child;
                            } else {
                                combined = parentPath + "/" + child;
                            }
                            String rewritten = rewrite(combined);
                            if (rewritten != null && !rewritten.equals(combined)) {
                                a[0] = new File(rewritten);
                                a[1] = "";
                            }
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    sInHook.set(Boolean.FALSE);
                }
            }
        };
        try {
            XposedHelpers.findAndHookConstructor(File.class, String.class, h);
            XposedHelpers.findAndHookConstructor(File.class, String.class, String.class, h);
            XposedHelpers.findAndHookConstructor(File.class, File.class, String.class, h);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    // ------------------------------------------------ redirect the I/O itself
    private static void hookSinks(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(sInHook.get())) return;
                sInHook.set(Boolean.TRUE);
                try {
                    Object[] a = param.args;
                    if (a == null) return;
                    String methodName = param.method != null ? param.method.getName() : "";
                    for (int i = 0; i < a.length; i++) {
                        Object o = a[i];
                        if (o instanceof String) {
                            String s = (String) o;
                            String r = sinkRewrite(s);
                            if (r != null && !r.equals(s)) {
                                a[i] = r;
                                ensureTargetParentsForSink(methodName, r, i, a);
                                logDiagnostic(methodName, s, r, "String", true, null);
                            }
                        } else if (o instanceof File) {
                            File f = (File) o;
                            String s = f.getPath();
                            String r = sinkRewrite(s);
                            if (r != null && !r.equals(s)) {
                                a[i] = new File(r);
                                ensureTargetParentsForSink(methodName, r, i, a);
                                logDiagnostic(methodName, s, r, "File", true, null);
                            }
                        }
                    }
                } catch (Throwable t) {
                    ActiveState state = sState;
                    if (state != null && state.debug) XposedBridge.log(TAG + "error in sink hook: " + t);
                } finally {
                    sInHook.set(Boolean.FALSE);
                }
            }
        };

        int n = hookPathMethods("libcore.io.Linux", cl, h, LINUX_PATH_METHODS, true);
        n += hookPathMethods("java.io.UnixFileSystem", cl, h, null, false);
        n += hookPathMethods("android.database.sqlite.SQLiteConnection", cl, h, null, true);
        XposedBridge.log(TAG + "sinks hooked: " + n);

        hookNio(cl);
    }

    private static void ensureTargetParentsForSink(String methodName, String r, int argIndex, Object[] a) {
        ActiveState state = sState;
        if (state == null || state.pathEngine == null || r == null || r.isEmpty()) return;
        PathEngine engine = state.pathEngine;
        if ("createDirectory".equals(methodName) || "mkdir".equals(methodName)) {
            engine.ensureTargetParents(r, false);
        } else if ("list".equals(methodName)) {
            PathEngine.PathRule rule = engine.findMatchingRule(r);
            if (rule != null && rule.isDirectoryRule) {
                engine.ensureTargetParents(r, true);
            }
        } else if ("createFileExclusively".equals(methodName) || "nativeOpen".equals(methodName)) {
            engine.ensureTargetParents(r, false);
        } else if ("open".equals(methodName)) {
            if (a.length > 1 && a[1] instanceof Integer) {
                int flags = (Integer) a[1];
                if ((flags & OsConstants.O_CREAT) != 0) {
                    engine.ensureTargetParents(r, false);
                }
            }
        } else if ("rename".equals(methodName) || "rename0".equals(methodName)) {
            if (argIndex == 1) {
                engine.ensureTargetParents(r, false);
            }
        }
    }

    private static void hookNio(ClassLoader cl) {
        try {
            Class.forName("java.nio.file.Path");
        } catch (Throwable notSupported) {
            return;
        }

        try {
            int nioCount = NioHooks.install(cl, new NioHooks.PathTranslator() {
                @Override
                public String translate(String path, String op) {
                    return sinkRewrite(path);
                }

                @Override
                public void ensureParents(String path, boolean isDir) {
                    ActiveState state = sState;
                    if (state != null && state.pathEngine != null) {
                        state.pathEngine.ensureTargetParents(path, isDir);
                    }
                }

                @Override
                public boolean isDebug() {
                    ActiveState state = sState;
                    return state != null && state.debug;
                }

                @Override
                public void log(String msg) {
                    XposedBridge.log(TAG + msg);
                }
            });
            XposedBridge.log(TAG + "java.nio.file hooks installed: " + nioCount);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "NIO hooking error: " + t);
        }
    }

    private static int hookPathMethods(String cls, ClassLoader cl, XC_MethodHook h, Set<String> names, boolean nativeOnlyIfNamesNull) {
        Class<?> c = XposedHelpers.findClassIfExists(cls, cl);
        if (c == null) {
            XposedBridge.log(TAG + "sink class missing: " + cls);
            return 0;
        }
        int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            boolean pick = names != null ? names.contains(m.getName())
                    : (nativeOnlyIfNamesNull ? Modifier.isNative(m.getModifiers()) : true);
            if (!pick || !takesPath(m)) continue;
            try {
                XposedBridge.hookMethod(m, h);
                n++;
            } catch (Throwable t) {
                ActiveState state = sState;
                if (state != null && state.debug) XposedBridge.log(TAG + "can't hook " + cls + "." + m.getName() + ": " + t);
            }
        }
        return n;
    }

    private static boolean takesPath(Method m) {
        for (Class<?> t : m.getParameterTypes())
            if (t == String.class || t == File.class) return true;
        return false;
    }

    private static void logDiagnostic(String op, String orig, String dest, String type, boolean success, String error) {
        ActiveState state = sState;
        if (state == null || !state.debug) return;
        PathEngine.PathRule rule = state.pathEngine != null ? state.pathEngine.findMatchingRule(orig) : null;
        XposedBridge.log(TAG + "[DIAG] op=" + op + " (" + type + ") orig=\"" + orig + "\" -> dest=\""
                + dest + "\"" + (rule != null ? " rule=\"" + rule.source + "=>" + rule.destination + "\"" : "")
                + " success=" + success + (error != null ? " error=\"" + error + "\"" : ""));
    }
}
