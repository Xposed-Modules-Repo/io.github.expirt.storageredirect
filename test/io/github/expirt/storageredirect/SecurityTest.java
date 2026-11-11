package io.github.expirt.storageredirect;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Automated Security Audit and Verification Test Suite for Storage Redirect v2.15.
 *
 * Covers:
 *  1. ZIP Slip with absolute paths
 *  2. ZIP Slip with "../" relative traversal
 *  3. ZIP Slip with Windows-style separators ("..\..\") and drive letters ("C:\")
 *  4. Package name validation (path traversal, control chars, null bytes)
 *  5. Malicious backup metadata targets (system dirs, root, unauthorized apps)
 *  6. Unauthorized clean-restore targets (storage root, backup file, system dirs)
 *  7. Symlink escape detection and containment (no traversing through symlinks)
 *  8. Legitimate backup creation and end-to-end restoration
 *  9. Safe handling of null bytes in path translation
 */
public class SecurityTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    private static void check(String testName, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + testName);
            totalPassed++;
        } else {
            System.err.println("  [FAIL] " + testName);
            totalFailed++;
        }
    }

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("    Storage Redirect Security Verification Suite  ");
        System.out.println("==================================================");

        File tempRoot = null;
        try {
            File tmpDir = new File(System.getProperty("java.io.tmpdir", "build/tmp"));
            if (!tmpDir.exists()) tmpDir.mkdirs();
            tempRoot = File.createTempFile("sr_security_test_", "", tmpDir);
            tempRoot.delete();
            tempRoot.mkdirs();

            testPackageNameValidation();
            testZipSlipResolution(tempRoot);
            testMaliciousBackupMetadata(tempRoot);
            testUnauthorizedCleanRestore(tempRoot);
            testSymlinkEscapeProtection(tempRoot);
            testLegitimateBackupRestore(tempRoot);
            testInternalFolderFiltering(tempRoot);
            testBackupDiscovery(tempRoot);

        } catch (Throwable t) {
            System.err.println("Unexpected fatal error in test suite: " + t);
            t.printStackTrace();
            totalFailed++;
        } finally {
            if (tempRoot != null && tempRoot.exists()) {
                cleanupTree(tempRoot);
            }
        }

        System.out.println("==================================================");
        System.out.println("SECURITY TEST RESULTS: " + totalPassed + " Passed, " + totalFailed + " Failed");
        System.out.println("==================================================");

        if (totalFailed > 0) {
            System.exit(1);
        }
    }

    private static void testPackageNameValidation() {
        System.out.println("\n--- Package Name Validation Tests ---");

        // Valid package names
        check("Valid package: com.rarlab.rar", RedirectConfig.isValidPackageName("com.rarlab.rar"));
        check("Valid package: io.github.expirt.storageredirect", RedirectConfig.isValidPackageName("io.github.expirt.storageredirect"));
        check("Valid package: a.b.c_123", RedirectConfig.isValidPackageName("a.b.c_123"));

        // Path traversal attempts in package name
        check("Reject traversal ../../etc", !RedirectConfig.isValidPackageName("../../etc"));
        check("Reject traversal com.app/evil", !RedirectConfig.isValidPackageName("com.app/evil"));
        check("Reject traversal com.app\\evil", !RedirectConfig.isValidPackageName("com.app\\evil"));
        check("Reject double dots: com..app", !RedirectConfig.isValidPackageName("com..app"));
        check("Reject leading dot: .com.app", !RedirectConfig.isValidPackageName(".com.app"));
        check("Reject trailing dot: com.app.", !RedirectConfig.isValidPackageName("com.app."));

        // Null, empty, and illegal characters
        check("Reject null package", !RedirectConfig.isValidPackageName(null));
        check("Reject empty package", !RedirectConfig.isValidPackageName(""));
        check("Reject package with null byte", !RedirectConfig.isValidPackageName("com.app\0evil"));
        check("Reject package with command chars", !RedirectConfig.isValidPackageName("com.app;rm -rf /"));
        check("Reject package with spaces", !RedirectConfig.isValidPackageName("com.app evil"));
        check("Reject single segment without dot", !RedirectConfig.isValidPackageName("standalone"));
    }

    private static void testZipSlipResolution(File tempRoot) throws IOException {
        System.out.println("\n--- ZIP Slip Path Resolution Tests ---");

        File extractBase = new File(tempRoot, "extract_base");
        extractBase.mkdirs();

        // 1. Legitimate relative paths
        File f1 = RedirectConfig.safeResolveZipEntry(extractBase, "config.properties");
        check("Resolve legitimate root entry", f1.getName().equals("config.properties") && f1.getParentFile().equals(extractBase.getCanonicalFile()));

        File f2 = RedirectConfig.safeResolveZipEntry(extractBase, "apps/com.example.properties");
        check("Resolve legitimate nested entry", f2.getPath().startsWith(extractBase.getCanonicalPath()));

        // 2. Absolute paths must be rejected
        boolean rejectedAbs1 = false;
        try {
            RedirectConfig.safeResolveZipEntry(extractBase, "/etc/passwd");
        } catch (IOException ex) {
            rejectedAbs1 = true;
        }
        check("Reject absolute path /etc/passwd", rejectedAbs1);

        boolean rejectedAbs2 = false;
        try {
            RedirectConfig.safeResolveZipEntry(extractBase, "/data/data/com.victim/files");
        } catch (IOException ex) {
            rejectedAbs2 = true;
        }
        check("Reject absolute path /data/data/...", rejectedAbs2);

        // 3. Relative path traversal (../)
        boolean rejectedTrav1 = false;
        try {
            RedirectConfig.safeResolveZipEntry(extractBase, "../escaped.txt");
        } catch (IOException ex) {
            rejectedTrav1 = true;
        }
        check("Reject relative traversal ../escaped.txt", rejectedTrav1);

        boolean rejectedTrav2 = false;
        try {
            RedirectConfig.safeResolveZipEntry(extractBase, "apps/../../escaped.txt");
        } catch (IOException ex) {
            rejectedTrav2 = true;
        }
        check("Reject nested traversal apps/../../escaped.txt", rejectedTrav2);

        // 4. Windows-style backslashes and drive letters
        boolean rejectedWin1 = false;
        try {
            RedirectConfig.safeResolveZipEntry(extractBase, "..\\..\\evil.txt");
        } catch (IOException ex) {
            rejectedWin1 = true;
        }
        check("Reject Windows-style ..\\..\\evil.txt", rejectedWin1);

        boolean rejectedWin2 = false;
        try {
            RedirectConfig.safeResolveZipEntry(extractBase, "apps\\..\\..\\evil.txt");
        } catch (IOException ex) {
            rejectedWin2 = true;
        }
        check("Reject Windows-style apps\\..\\..\\evil.txt", rejectedWin2);

        boolean rejectedDrive = false;
        try {
            RedirectConfig.safeResolveZipEntry(extractBase, "C:\\Windows\\evil.dll");
        } catch (IOException ex) {
            rejectedDrive = true;
        }
        check("Reject drive letter C:\\...", rejectedDrive);

        // 5. Null bytes
        boolean rejectedNull = false;
        try {
            RedirectConfig.safeResolveZipEntry(extractBase, "config.properties\0.txt");
        } catch (IOException ex) {
            rejectedNull = true;
        }
        check("Reject entry containing null byte", rejectedNull);
    }

    private static void testMaliciousBackupMetadata(File tempRoot) throws IOException {
        System.out.println("\n--- Malicious Backup Metadata Tests ---");

        File sdDir = new File(tempRoot, "mock_sd");
        sdDir.mkdirs();
        String sd = sdDir.getCanonicalPath();

        File fakeZip = new File(tempRoot, "test_backup.zip");
        createDummyZip(fakeZip);

        Backups.Progress noop = new Backups.Progress() {
            @Override public void onProgress(int files, long bytes) {}
            @Override public boolean cancelled() { return false; }
        };

        // 1. Target is the storage root itself
        Backups.Info infoRoot = new Backups.Info();
        infoRoot.pkg = "com.rarlab.rar";
        infoRoot.sources.put("storage", sd);
        boolean caughtRoot = false;
        try {
            Backups.restore(fakeZip, infoRoot, false, sd, noop);
        } catch (IOException ex) {
            caughtRoot = true;
        }
        check("Reject restore target equaling storage root", caughtRoot);

        // 2. Target is system directory /system
        Backups.Info infoSys = new Backups.Info();
        infoSys.pkg = "com.rarlab.rar";
        infoSys.sources.put("internal", "/system/bin");
        boolean caughtSys = false;
        try {
            Backups.restore(fakeZip, infoSys, false, sd, noop);
        } catch (SecurityException | IOException ex) {
            caughtSys = true;
        }
        check("Reject restore target in /system", caughtSys);

        // 3. Target is another application's private directory
        Backups.Info infoVictim = new Backups.Info();
        infoVictim.pkg = "com.rarlab.rar";
        infoVictim.sources.put("internal", "/data/data/com.android.settings/databases");
        boolean caughtVictim = false;
        try {
            Backups.restore(fakeZip, infoVictim, false, sd, noop);
        } catch (SecurityException | IOException ex) {
            caughtVictim = true;
        }
        check("Reject restore target in another app's private directory", caughtVictim);

        // 4. Target is root Android data container (/Android/data)
        Backups.Info infoContainer = new Backups.Info();
        infoContainer.pkg = "com.rarlab.rar";
        infoContainer.sources.put("external", sd + "/Android/data");
        boolean caughtContainer = false;
        try {
            Backups.restore(fakeZip, infoContainer, false, sd, noop);
        } catch (SecurityException | IOException ex) {
            caughtContainer = true;
        }
        check("Reject restore target equaling /Android/data root container", caughtContainer);

        // 5. Target is module configuration directory
        Backups.Info infoModCfg = new Backups.Info();
        infoModCfg.pkg = "com.rarlab.rar";
        infoModCfg.sources.put("internal", sd + "/Android/media/uninviting");
        boolean caughtModCfg = false;
        try {
            Backups.restore(fakeZip, infoModCfg, false, sd, noop);
        } catch (SecurityException | IOException ex) {
            caughtModCfg = true;
        }
        check("Reject restore target in module configuration directory", caughtModCfg);

        // 6. Target path contains traversal "../"
        Backups.Info infoTrav = new Backups.Info();
        infoTrav.pkg = "com.rarlab.rar";
        infoTrav.sources.put("internal", sd + "/CustomData/com.rarlab.rar/../../DCIM");
        boolean caughtTrav = false;
        try {
            Backups.restore(fakeZip, infoTrav, false, sd, noop);
        } catch (IOException ex) {
            caughtTrav = true;
        }
        check("Reject restore target with traversal path in metadata", caughtTrav);
    }

    private static void testUnauthorizedCleanRestore(File tempRoot) throws IOException {
        System.out.println("\n--- Unauthorized Clean-Restore Protection Tests ---");

        File sdDir = new File(tempRoot, "clean_test_sd");
        sdDir.mkdirs();
        String sd = sdDir.getCanonicalPath();

        // Create important files in storage root
        File importantFile = new File(sdDir, "important_user_photo.jpg");
        Files.write(importantFile.toPath(), "precious photo".getBytes(StandardCharsets.UTF_8));

        File fakeZip = new File(sdDir, "backup_archive.zip");
        createDummyZip(fakeZip);

        Backups.Progress noop = new Backups.Progress() {
            @Override public void onProgress(int files, long bytes) {}
            @Override public boolean cancelled() { return false; }
        };

        // Attempt clean restore targeting the storage root
        Backups.Info infoRoot = new Backups.Info();
        infoRoot.pkg = "com.rarlab.rar";
        infoRoot.sources.put("storage", sd);

        boolean cleanFailed = false;
        try {
            Backups.restore(fakeZip, infoRoot, true, sd, noop);
        } catch (IOException ex) {
            cleanFailed = true;
        }

        check("Clean restore on storage root was aborted", cleanFailed);
        check("Storage root file was preserved untouched", importantFile.exists());
        check("Backup archive itself was preserved", fakeZip.exists());
    }

    private static void testSymlinkEscapeProtection(File tempRoot) throws IOException {
        System.out.println("\n--- Symlink Escape Protection Tests ---");

        File sdDir = new File(tempRoot, "symlink_sd");
        sdDir.mkdirs();
        String sd = sdDir.getCanonicalPath();

        // Victim directory outside the app target
        File victimDir = new File(tempRoot, "victim_private_dir");
        victimDir.mkdirs();
        File victimFile = new File(victimDir, "confidential.db");
        Files.write(victimFile.toPath(), "secret data".getBytes(StandardCharsets.UTF_8));

        // Approved app directory
        File appDir = new File(sdDir, "CustomData/com.test.app/internal");
        appDir.mkdirs();

        // Symlink pointing from inside appDir to victimDir
        File symlinkDir = new File(appDir, "link_to_victim");
        boolean symlinkCreated = false;
        try {
            Files.createSymbolicLink(symlinkDir.toPath(), victimDir.toPath());
            symlinkCreated = true;
        } catch (Throwable t) {
            System.out.println("  [SKIP] Filesystem does not support symlink creation: " + t.getMessage());
        }

        if (symlinkCreated) {
            check("Symlink correctly identified as symbolic link", Backups.isSymbolicLink(symlinkDir));

            // Run clean restore on appDir
            File fakeZip = new File(sdDir, "backup.zip");
            createDummyZip(fakeZip);

            Backups.Info info = new Backups.Info();
            info.pkg = "com.test.app";
            info.sources.put("internal", appDir.getCanonicalPath());

            Backups.Progress noop = new Backups.Progress() {
                @Override public void onProgress(int files, long bytes) {}
                @Override public boolean cancelled() { return false; }
            };

            try {
                Backups.restore(fakeZip, info, true, sd, noop);
            } catch (Throwable ignored) {}

            check("Victim file outside symlink was NOT deleted", victimFile.exists());
            check("Victim content remains intact", new String(Files.readAllBytes(victimFile.toPath()), StandardCharsets.UTF_8).equals("secret data"));
        }
    }

    private static void testLegitimateBackupRestore(File tempRoot) throws IOException {
        System.out.println("\n--- Legitimate Backup & Restore Tests ---");

        File sdDir = new File(tempRoot, "legit_sd");
        sdDir.mkdirs();
        String sd = sdDir.getCanonicalPath();

        File appInternal = new File(sdDir, "CustomData/com.test.legit/internal");
        appInternal.mkdirs();

        File backupZip = new File(sdDir, "CustomData/Backups/com.test.legit/backup-1.zip");
        backupZip.getParentFile().mkdirs();

        // Create a legitimate backup ZIP
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(backupZip)));
        try {
            // backup-info.properties
            Properties p = new Properties();
            p.setProperty("format", "1");
            p.setProperty("package", "com.test.legit");
            p.setProperty("source.internal", appInternal.getCanonicalPath());
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            p.store(bo, "Storage Redirect backup");
            ZipEntry ie = new ZipEntry("backup-info.properties");
            zos.putNextEntry(ie);
            zos.write(bo.toByteArray());
            zos.closeEntry();

            // internal/test.txt
            ZipEntry fe = new ZipEntry("internal/files/test.txt");
            zos.putNextEntry(fe);
            zos.write("hello legitimate storage redirect".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        } finally {
            zos.close();
        }

        // Read info
        Backups.Info info = Backups.readInfo(backupZip);
        check("Read legitimate backup metadata", info.pkg.equals("com.test.legit") && info.sources.containsKey("internal"));

        // Restore
        Backups.Progress p = new Backups.Progress() {
            @Override public void onProgress(int files, long bytes) {}
            @Override public boolean cancelled() { return false; }
        };

        Backups.Result r = Backups.restore(backupZip, info, true, sd, p);
        check("Restore result not null", r != null);
        check("Restored expected file count", r != null && r.files == 1);

        File restoredFile = new File(appInternal, "files/test.txt");
        check("Restored file physically exists", restoredFile.exists());
        if (restoredFile.exists()) {
            String content = new String(Files.readAllBytes(restoredFile.toPath()), StandardCharsets.UTF_8);
            check("Restored content matches original", "hello legitimate storage redirect".equals(content));
        }
    }

    private static void testInternalFolderFiltering(File tempRoot) throws IOException {
        System.out.println("\n--- Internal Storage Subfolder Filtering Tests ---");
        File sdDir = new File(tempRoot, "filter_sd");
        sdDir.mkdirs();
        File internalDir = new File(sdDir, "internal");
        internalDir.mkdirs();

        new File(internalDir, "files").mkdirs();
        new File(internalDir, "databases").mkdirs();
        new File(internalDir, "cache").mkdirs();
        new File(internalDir, "shared_prefs").mkdirs();
        new File(internalDir, "no_backup").mkdirs();

        Files.write(new File(internalDir, "files/data.txt").toPath(), "data".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(internalDir, "databases/app.db").toPath(), "db".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(internalDir, "cache/temp.cache").toPath(), "cache".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(internalDir, "shared_prefs/settings.xml").toPath(), "xml".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(internalDir, "no_backup/token.dat").toPath(), "token".getBytes(StandardCharsets.UTF_8));

        File backupDir = new File(sdDir, "backup");
        backupDir.mkdirs();

        Set<String> excluded = new HashSet<String>();
        excluded.add("cache"); // Exclude cache

        List<Backups.Source> sources = new ArrayList<Backups.Source>();
        sources.add(new Backups.Source("internal", internalDir, excluded));

        Map<String, String> info = new LinkedHashMap<String, String>();
        info.put("package", "com.test.filter");
        info.put("format", "2");

        Backups.Progress noop = new Backups.Progress() {
            @Override public void onProgress(int f, long b) {}
            @Override public boolean cancelled() { return false; }
        };

        File zip = Backups.create(backupDir, "filter_test", sources, 1, info, noop);
        check("Filter backup archive created", zip != null && zip.exists());

        // Check entries inside zip
        Set<String> entryNames = new HashSet<String>();
        ZipFile zf = new ZipFile(zip);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                entryNames.add(en.nextElement().getName());
            }
        } finally {
            zf.close();
        }

        check("Backup includes files/data.txt", entryNames.contains("internal/files/data.txt"));
        check("Backup includes databases/app.db", entryNames.contains("internal/databases/app.db"));
        check("Backup includes shared_prefs/settings.xml", entryNames.contains("internal/shared_prefs/settings.xml"));
        check("Backup includes no_backup/token.dat", entryNames.contains("internal/no_backup/token.dat"));
        check("Backup excludes cache/temp.cache", !entryNames.contains("internal/cache/temp.cache") && !entryNames.contains("internal/cache/"));
    }

    private static void testBackupDiscovery(File tempRoot) throws IOException {
        System.out.println("\n--- Backup Discovery Across Storage Tests ---");
        File sdDir = new File(tempRoot, "discovery_sd");
        sdDir.mkdirs();
        String sd = sdDir.getCanonicalPath();

        File uninvitingBackup = new File(sdDir, "Android/media/uninviting/backup/com.test.disc");
        uninvitingBackup.mkdirs();
        createDummyZip(new File(uninvitingBackup, "b1.zip"));

        File customBackup = new File(sdDir, "CustomData/Backups/com.test.disc");
        customBackup.mkdirs();
        createDummyZip(new File(customBackup, "b2.zip"));

        List<Backups.Item> forPkg = Backups.listForPackage(sd, "com.test.disc", null);
        check("listForPackage finds backups in uninviting and CustomData", forPkg.size() >= 2);

        List<Backups.Item> all = Backups.listAll(sd);
        check("listAll finds all backups across storage roots", all.size() >= 2);
    }

    private static void createDummyZip(File zipFile) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            ZipEntry ze = new ZipEntry("backup-info.properties");
            zos.putNextEntry(ze);
            zos.write("package=com.dummy\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        } finally {
            zos.close();
        }
    }

    private static void cleanupTree(File f) {
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null) {
                for (File c : fs) cleanupTree(c);
            }
        }
        f.delete();
    }
}
