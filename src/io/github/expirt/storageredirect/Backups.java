package io.github.expirt.storageredirect;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Plain zip backups of an app's redirected folders. Layout inside a backup:
 *   backup-info.properties
 *   internal/...   external/...   storage/...      (only the sources that were included)
 * There is deliberately no restore: backups are ordinary archives you can copy or open anywhere.
 */
final class Backups {

    static final class Source {
        final String kind;   // internal | external | storage
        final File dir;
        Source(String kind, File dir) { this.kind = kind; this.dir = dir; }
    }

    static final class Item {
        File file;
        String name;   // without .zip
        long size;
        long time;
    }

    interface Progress {
        void onProgress(int files, long bytes);
        boolean cancelled();
    }

    private Backups() { }

    // ------------------------------------------------------------------ names
    /** Makes a safe file name (without extension). Returns "" if nothing usable is left. */
    static String sanitize(String n) {
        if (n == null) return "";
        n = n.trim();
        if (n.toLowerCase(Locale.ROOT).endsWith(".zip")) n = n.substring(0, n.length() - 4);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            b.append((c < 32 || "\\/:*?\"<>|".indexOf(c) >= 0) ? '_' : c);
        }
        String s = b.toString().trim();
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1).trim();
        if (s.length() > 120) s = s.substring(0, 120).trim();
        return s;
    }

    static String expand(String template, String pkg, String app, String ver, Date when) {
        String s = template
                .replace("{pkg}", pkg)
                .replace("{app}", app == null ? pkg : app)
                .replace("{ver}", ver == null ? "" : ver)
                .replace("{date}", new SimpleDateFormat("yyyyMMdd", Locale.US).format(when))
                .replace("{time}", new SimpleDateFormat("HHmmss", Locale.US).format(when));
        return sanitize(s);
    }

    static File uniqueFile(File dir, String base) {
        File f = new File(dir, base + ".zip");
        int i = 2;
        while (f.exists()) f = new File(dir, base + " (" + (i++) + ").zip");
        return f;
    }

    // ---------------------------------------------------------------- listing
    static List<Item> list(File dir) {
        List<Item> out = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            String n = f.getName();
            if (!f.isFile() || !n.toLowerCase(Locale.ROOT).endsWith(".zip")) continue;
            Item it = new Item();
            it.file = f;
            it.name = n.substring(0, n.length() - 4);
            it.size = f.length();
            it.time = f.lastModified();
            out.add(it);
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                return a.time == b.time ? a.name.compareToIgnoreCase(b.name) : (a.time < b.time ? 1 : -1);
            }
        });
        return out;
    }

    /** Returns null on success, otherwise a short error message. */
    static String rename(Item it, String newName) {
        String base = sanitize(newName);
        if (base.length() == 0) return "Invalid name";
        if (base.equals(it.name)) return null;
        File dst = new File(it.file.getParentFile(), base + ".zip");
        if (dst.exists()) return "A backup with that name already exists";
        return it.file.renameTo(dst) ? null : "Rename failed";
    }

    static boolean delete(Item it) {
        return it.file.delete();
    }

    // ----------------------------------------------------------------- create
    /**
     * Zips the sources into dir/base.zip (a unique name is used if it exists).
     * Returns the file, or null if cancelled. Written as .part first, so a failed or cancelled
     * run never leaves a half-written .zip behind.
     */
    static File create(File dir, String base, List<Source> sources, int level,
                       Map<String, String> info, Progress p) throws IOException {
        if (!dir.exists() && !dir.mkdirs() && !dir.exists())
            throw new IOException("Can't create " + dir);
        File out = uniqueFile(dir, base);
        File part = new File(dir, out.getName() + ".part");
        String skip = dir.getAbsolutePath();
        int[] files = {0};
        long[] bytes = {0};
        boolean ok = false;
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(part)));
            zos.setLevel(level);

            Properties pr = new Properties();
            pr.putAll(info);
            for (Source s : sources) pr.setProperty("source." + s.kind, s.dir.getAbsolutePath());
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            pr.store(bo, "Storage Redirect backup");
            ZipEntry ie = new ZipEntry("backup-info.properties");
            ie.setTime(System.currentTimeMillis());
            zos.putNextEntry(ie);
            zos.write(bo.toByteArray());
            zos.closeEntry();

            for (Source s : sources) {
                if (p.cancelled()) break;
                addTree(zos, s.dir, s.kind + "/", skip, files, bytes, p);
            }
            zos.close();
            zos = null;
            if (p.cancelled()) return null;
            if (!part.renameTo(out)) throw new IOException("Can't finalize " + out.getName());
            ok = true;
            return out;
        } finally {
            if (zos != null) try { zos.close(); } catch (Throwable ignored) { }
            if (!ok) part.delete();
        }
    }

    private static void addTree(ZipOutputStream zos, File dir, String prefix, String skip,
                                int[] files, long[] bytes, Progress p) throws IOException {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        byte[] buf = new byte[64 * 1024];
        for (File f : fs) {
            if (p.cancelled()) return;
            String path = f.getAbsolutePath();
            if (path.equals(skip) || path.startsWith(skip + "/")) continue;   // never back up the backups
            if (f.isDirectory()) {
                ZipEntry de = new ZipEntry(prefix + f.getName() + "/");
                de.setTime(f.lastModified());
                zos.putNextEntry(de);
                zos.closeEntry();
                addTree(zos, f, prefix + f.getName() + "/", skip, files, bytes, p);
            } else if (f.isFile()) {
                InputStream in = null;
                try {
                    in = new BufferedInputStream(new FileInputStream(f));
                } catch (IOException unreadable) {
                    continue;                                                  // skip files we can't read
                }
                try {
                    ZipEntry fe = new ZipEntry(prefix + f.getName());
                    fe.setTime(f.lastModified());
                    zos.putNextEntry(fe);
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        zos.write(buf, 0, n);
                        bytes[0] += n;
                        if (p.cancelled()) break;
                    }
                    zos.closeEntry();
                    files[0]++;
                    p.onProgress(files[0], bytes[0]);
                } finally {
                    try { in.close(); } catch (Throwable ignored) { }
                }
            }
        }
    }

    // ---------------------------------------------------------------- restore
    /** What a backup says about where it came from (backup-info.properties). */
    static final class Info {
        int format = 1;
        String pkg, app, version, created;
        final Map<String, String> sources = new LinkedHashMap<>();   // kind -> absolute folder
    }

    static final class Result {
        int files, skipped, failed;
        long bytes;
    }

    static Info readInfo(File zip) throws IOException {
        ZipFile z = new ZipFile(zip);
        try {
            ZipEntry e = z.getEntry("backup-info.properties");
            if (e == null) throw new IOException("Not a Storage Redirect backup");
            Properties p = new Properties();
            InputStreamReader r = new InputStreamReader(z.getInputStream(e), "UTF-8");
            try { p.load(r); } finally { r.close(); }
            Info in = new Info();
            try { in.format = Integer.parseInt(p.getProperty("format", "1").trim()); } catch (Throwable ignored) { }
            in.pkg = p.getProperty("package");
            if (!RedirectConfig.isValidPackageName(in.pkg)) {
                throw new IOException("Untrusted or invalid package name in backup: " + in.pkg);
            }
            in.app = p.getProperty("app");
            in.version = p.getProperty("version");
            in.created = p.getProperty("created");
            for (String k : p.stringPropertyNames()) {
                String v = p.getProperty(k);
                if (v == null || v.trim().length() == 0) continue;
                if (k.startsWith("source.")) in.sources.put(k.substring(7), v.trim());
            }
            return in;
        } finally {
            z.close();
        }
    }

    /**
     * Extracts a backup back into the folders it was made from (info.sources).
     * clean = delete the existing contents of those folders first.
     * Returns null if cancelled (the restore is then incomplete).
     */
    static Result restore(File zip, Info info, boolean clean, String sdRoot, Progress p) throws IOException {
        if (info == null) throw new IOException("Null backup metadata");
        if (!RedirectConfig.isValidPackageName(info.pkg)) {
            throw new IOException("Untrusted or invalid package in backup: " + (info != null ? info.pkg : "null"));
        }
        if (info.sources.isEmpty()) throw new IOException("This backup has no folder information");

        File canonicalSd = new File(sdRoot).getCanonicalFile();
        String sd = canonicalSd.getPath();
        File canonicalZip = zip.getCanonicalFile();
        String zp = canonicalZip.getPath();

        Map<String, File> targets = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : info.sources.entrySet()) {
            String kind = e.getKey();
            if (kind == null || !kind.matches("^[a-zA-Z0-9_-]+$")) {
                throw new IOException("Invalid source identifier in backup: " + kind);
            }
            String pathStr = e.getValue();
            if (pathStr == null || pathStr.trim().isEmpty()) {
                throw new IOException("Empty path in backup metadata for " + kind);
            }
            if (pathStr.indexOf('\0') >= 0 || pathStr.contains("..") || pathStr.contains("\\")) {
                throw new IOException("Illegal characters or traversal in backup path: " + pathStr);
            }
            File t = new File(pathStr.trim());
            if (!t.isAbsolute()) {
                throw new IOException("Bad path in backup (must be absolute): " + pathStr);
            }
            File canonicalTarget = t.getCanonicalFile();
            String tp = canonicalTarget.getPath();

            // 1. Never target the storage root itself or any of its parents
            if (tp.equals(sd) || sd.startsWith(tp + File.separator)) {
                throw new IOException("Won't target " + tp + " (it contains or equals the storage root)");
            }
            // 2. Never target the directory containing this backup or the backup itself
            if (zp.equals(tp) || zp.startsWith(tp + File.separator)) {
                throw new IOException("Won't target " + tp + " (it contains or is this backup archive)");
            }
            // 3. Never target system directories or root
            if (tp.equals("/") || tp.startsWith("/system") || tp.startsWith("/vendor")
                    || tp.startsWith("/apex") || tp.startsWith("/etc") || tp.startsWith("/dev")
                    || tp.startsWith("/proc") || tp.startsWith("/sys") || tp.startsWith("/data/system")
                    || tp.startsWith("/data/misc") || tp.startsWith("/data/local")) {
                throw new SecurityException("Target path in unauthorized system directory: " + tp);
            }
            // 4. Never target module configuration directories
            if (tp.startsWith(sd + "/Android/media/uninviting") || tp.startsWith(sd + "/CustomData/.config")) {
                throw new SecurityException("Target path in module configuration directory: " + tp);
            }
            // 5. Must reside in approved application storage directories
            boolean inSd = tp.startsWith(sd + File.separator);
            if (inSd) {
                // Cannot be root Android data container directories
                if (tp.equals(sd + "/Android") || tp.equals(sd + "/Android/data")
                        || tp.equals(sd + "/Android/obb") || tp.equals(sd + "/Android/media")) {
                    throw new SecurityException("Target cannot be a root Android data container: " + tp);
                }
            } else {
                // If not in shared storage, it must be inside the application's private directory
                String privData = "/data/data/" + info.pkg;
                String privUser0 = "/data/user/0/" + info.pkg;
                boolean inAppPrivate = (tp.equals(privData) || tp.startsWith(privData + File.separator)
                        || tp.equals(privUser0) || tp.startsWith(privUser0 + File.separator));
                if (!inAppPrivate) {
                    throw new SecurityException("Target path outside approved storage directories: " + tp);
                }
            }

            if (isSymbolicLink(canonicalTarget)) {
                throw new SecurityException("Target directory cannot be a symbolic link: " + tp);
            }

            targets.put(kind, canonicalTarget);
        }

        if (clean) {
            // Validate all targets before performing any destructive operation
            for (File t : targets.values()) {
                if (t.exists()) {
                    if (!t.isDirectory()) {
                        throw new IOException("Target to clean is not a directory: " + t.getPath());
                    }
                    if (isSymbolicLink(t)) {
                        throw new IOException("Target to clean is a symbolic link: " + t.getPath());
                    }
                }
            }
            for (File t : targets.values()) {
                deleteChildren(t);
            }
        }

        Result r = new Result();
        ZipFile z = new ZipFile(zip);
        try {
            byte[] buf = new byte[64 * 1024];
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                if (p.cancelled()) return null;
                ZipEntry e = en.nextElement();
                String name = e.getName();
                if (name == null || name.equals("backup-info.properties")) continue;
                if (name.indexOf('\0') >= 0) { r.skipped++; continue; }
                name = name.replace('\\', '/');
                int slash = name.indexOf('/');
                File root = slash > 0 ? targets.get(name.substring(0, slash)) : null;
                if (root == null) { r.skipped++; continue; }
                String rel = name.substring(slash + 1);
                if (rel.length() == 0) { root.mkdirs(); continue; }

                // Validate rel for traversal
                if (rel.startsWith("/") || new File(rel).isAbsolute()) { r.skipped++; continue; }
                String[] segs = rel.split("/");
                boolean invalidSeg = false;
                for (String s : segs) {
                    if ("..".equals(s)) { invalidSeg = true; break; }
                }
                if (invalidSeg) { r.skipped++; continue; }

                File dest = new File(root, rel);
                File canonicalDest = dest.getCanonicalFile();
                File canonicalRoot = root.getCanonicalFile();
                String c = canonicalDest.getPath();
                String rc = canonicalRoot.getPath();
                if (!c.equals(rc) && !c.startsWith(rc + File.separator)) { r.skipped++; continue; } // ZIP slip check

                // Symlink check on intermediate parents
                File cur = canonicalDest.getParentFile();
                boolean hasSymlinkParent = false;
                while (cur != null && !cur.equals(canonicalRoot)) {
                    if (isSymbolicLink(cur)) { hasSymlinkParent = true; break; }
                    cur = cur.getParentFile();
                }
                if (hasSymlinkParent || isSymbolicLink(canonicalDest)) {
                    r.skipped++;
                    continue;
                }

                try {
                    if (e.isDirectory()) { canonicalDest.mkdirs(); continue; }
                    File par = canonicalDest.getParentFile();
                    if (par != null && !par.exists() && !par.mkdirs() && !par.exists()) {
                        throw new IOException("Cannot create directory: " + par);
                    }
                    InputStream in = z.getInputStream(e);
                    OutputStream out = null;
                    try {
                        out = new BufferedOutputStream(new FileOutputStream(canonicalDest));
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            r.bytes += n;
                        }
                    } finally {
                        if (out != null) try { out.close(); } catch (Throwable ignored) { }
                        try { in.close(); } catch (Throwable ignored) { }
                    }
                    if (e.getTime() > 0) canonicalDest.setLastModified(e.getTime());
                    r.files++;
                    p.onProgress(r.files, r.bytes);
                } catch (IOException io) {
                    r.failed++;
                }
            }
        } finally {
            z.close();
        }
        return r;
    }

    private static void deleteChildren(File dir) throws IOException {
        if (dir == null || !dir.exists()) return;
        if (isSymbolicLink(dir)) {
            throw new IOException("Refusing to delete contents of symbolic link: " + dir);
        }
        File[] fs = dir.listFiles();
        if (fs == null) return;
        String dirCanonical = dir.getCanonicalPath();
        for (File f : fs) {
            String childCanonical = f.getCanonicalPath();
            if (!childCanonical.startsWith(dirCanonical + File.separator)) {
                throw new IOException("Child escapes target directory (symlink/mount): " + f);
            }
            if (isSymbolicLink(f)) {
                // Do not traverse into symlink directory; delete link only
                if (!f.delete() && f.exists()) {
                    throw new IOException("Failed to delete symbolic link: " + f);
                }
            } else if (f.isDirectory()) {
                deleteChildren(f);
                if (!f.delete() && f.exists()) {
                    throw new IOException("Failed to delete directory: " + f);
                }
            } else {
                if (!f.delete() && f.exists()) {
                    throw new IOException("Failed to delete file: " + f);
                }
            }
        }
    }

    static boolean isSymbolicLink(File f) {
        return RedirectConfig.isSymbolicLink(f);
    }
}
