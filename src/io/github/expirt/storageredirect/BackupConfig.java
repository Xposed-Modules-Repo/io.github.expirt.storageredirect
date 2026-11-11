package io.github.expirt.storageredirect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/** Per-app backup settings: <sdcard>/CustomData/.config/backup/<package>.properties */
final class BackupConfig {

    static final String DEF_DIR = "Android/media/uninviting/backup/{pkg}";
    static final String LEGACY_DEF_DIR = "CustomData/Backups/{pkg}";
    static final String DEF_NAME = "{pkg}-{date}-{time}";

    boolean internal = true;          // back up the redirected internal-data folder
    boolean internalFiles = true;       // internal /files (app documents and storage)
    boolean internalDatabases = true;   // internal /databases (SQLite database files)
    boolean internalSharedPrefs = true; // internal /shared_prefs (app preferences)
    boolean internalNoBackup = true;    // internal /no_backup (no_backup directory)
    boolean internalCache = false;      // internal /cache (temporary cache files)
    boolean internalOther = true;       // any other files/folders in internal data
    boolean external = false;         // back up the redirected app-external folder
    boolean storage = false;          // back up the redirected main-storage folder
    int level = 6;                    // zip deflate level: 0 none, 1 fast, 6 normal, 9 max
    String dir = DEF_DIR;             // where backups are saved (absolute, or relative to storage root)
    String name = DEF_NAME;           // file-name template: {pkg} {app} {ver} {date} {time}

    static File file(String sd, String pkg) {
        if (pkg == null || !RedirectConfig.isValidPackageName(pkg)) {
            throw new IllegalArgumentException("Invalid package name: " + pkg);
        }
        return new File(new File(RedirectConfig.dir(sd), "backup"), pkg + ".properties");
    }

    File backupDir(String sd, String pkg) {
        return RedirectConfig.resolve(sd, pkg, dir);
    }

    static BackupConfig load(String sd, String pkg) {
        BackupConfig c = new BackupConfig();
        if (pkg == null || !RedirectConfig.isValidPackageName(pkg)) return c;
        FileInputStream in = null;
        try {
            File f = file(sd, pkg);
            if (!f.isFile()) return c;
            in = new FileInputStream(f);
            Properties p = new Properties();
            p.load(in);
            c.internal = bool(p, "internal", c.internal);
            c.internalFiles = bool(p, "internal.files", c.internalFiles);
            c.internalDatabases = bool(p, "internal.databases", c.internalDatabases);
            c.internalSharedPrefs = bool(p, "internal.shared_prefs", c.internalSharedPrefs);
            c.internalNoBackup = bool(p, "internal.no_backup", c.internalNoBackup);
            c.internalCache = bool(p, "internal.cache", c.internalCache);
            c.internalOther = bool(p, "internal.other", c.internalOther);
            c.external = bool(p, "external", c.external);
            c.storage = bool(p, "storage", c.storage);
            try { c.level = Integer.parseInt(p.getProperty("level", "6").trim()); } catch (Throwable ignored) { }
            if (c.level < 0 || c.level > 9) c.level = 6;
            c.dir = str(p, "dir", c.dir);
            c.name = str(p, "name", c.name);
        } catch (Throwable ignored) {
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
        return c;
    }

    boolean save(String sd, String pkg) {
        if (pkg == null || !RedirectConfig.isValidPackageName(pkg)) return false;
        FileOutputStream out = null;
        try {
            File f = file(sd, pkg);
            File d = f.getParentFile();
            if (!d.exists() && !d.mkdirs() && !d.exists()) return false;
            Properties p = new Properties();
            p.setProperty("internal", String.valueOf(internal));
            p.setProperty("internal.files", String.valueOf(internalFiles));
            p.setProperty("internal.databases", String.valueOf(internalDatabases));
            p.setProperty("internal.shared_prefs", String.valueOf(internalSharedPrefs));
            p.setProperty("internal.no_backup", String.valueOf(internalNoBackup));
            p.setProperty("internal.cache", String.valueOf(internalCache));
            p.setProperty("internal.other", String.valueOf(internalOther));
            p.setProperty("external", String.valueOf(external));
            p.setProperty("storage", String.valueOf(storage));
            p.setProperty("level", String.valueOf(level));
            p.setProperty("dir", dir);
            p.setProperty("name", name);
            out = new FileOutputStream(f);
            p.store(out, "Storage Redirect backup settings - " + pkg);
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
