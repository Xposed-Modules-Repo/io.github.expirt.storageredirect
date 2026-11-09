package io.github.expirt.storageredirect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Dedicated test suite for the PathEngine and path redirection system.
 *
 * Verifies all 25+ requirements specified in the task:
 *  - Basic mapping (exact file, exact dir, direct child, deeply nested)
 *  - Negative matching (similar names, prefix collisions, unrelated, empty/invalid)
 *  - Filesystem behavior (read, write, nested dirs, list, delete, rename, copy/move)
 *  - Rule behavior (longest-prefix, multiple rules, {pkg}, overlapping, cyclic protection)
 *  - Destination behavior (exists, does not exist, missing parents, inaccessible, file vs dir)
 */
public class PathEngineTest {

    private static int sPassed = 0;
    private static int sFailed = 0;
    private static final List<String> sFailures = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("    Running PathEngine Automated Test Suite       ");
        System.out.println("==================================================");

        // 1. Basic mapping
        testExactFileMapping();
        testExactDirectoryMapping();
        testDirectChildMapping();
        testDeeplyNestedMapping();

        // 2. Negative matching
        testSimilarDirectoryNames();
        testPrefixCollisions();
        testUnrelatedPaths();
        testEmptyAndInvalidPaths();

        // 3. Rule behavior
        testLongestPrefixPriority();
        testMultipleIndependentRules();
        testPkgSubstitution();
        testOverlappingRulesDeterministic();
        testCyclicMappingProtection();
        testRelativeRulePaths();

        // 4. Destination behavior
        testDestinationExists();
        testDestinationDoesNotExist();
        testIntermediateParentsDoNotExist();
        testDestinationIsFileRatherThanDirectory();
        testDestinationInaccessible();

        // 5. Filesystem behavior
        testReadExistingRedirectedFiles();
        testWriteNewRedirectedFiles();
        testCreateNestedDirectories();
        testListRedirectedDirectoryContents();
        testDeleteRedirectedFiles();
        testRenameRedirectedFiles();
        testCopyAndMoveOperations();

        System.out.println("==================================================");
        System.out.println("TEST RESULTS: " + sPassed + " Passed, " + sFailed + " Failed");
        if (sFailed > 0) {
            System.out.println("Failures:");
            for (String f : sFailures) {
                System.out.println("  - " + f);
            }
            System.exit(1);
        } else {
            System.out.println("ALL TESTS PASSED SUCCESSFULLY!");
            System.exit(0);
        }
    }

    private static void assertEquals(String testName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            sPassed++;
            System.out.println("  [PASS] " + testName);
        } else {
            sFailed++;
            String msg = testName + " - Expected: <" + expected + ">, Got: <" + actual + ">";
            sFailures.add(msg);
            System.err.println("  [FAIL] " + msg);
        }
    }

    private static void assertTrue(String testName, boolean condition) {
        if (condition) {
            sPassed++;
            System.out.println("  [PASS] " + testName);
        } else {
            sFailed++;
            String msg = testName + " - Expected condition to be true";
            sFailures.add(msg);
            System.err.println("  [FAIL] " + msg);
        }
    }

    // ------------------------------------------------------------ 1. Basic mapping
    private static void testExactFileMapping() {
        System.out.println("\n--- Basic Mapping Tests ---");
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        List<String[]> rules = Collections.singletonList(
                new String[]{"/storage/emulated/0/docs/file.txt", "/storage/emulated/0/Onedroid/docs/file.txt"}
        );
        engine.buildRules(rules);

        String res = engine.resolvePath("/storage/emulated/0/docs/file.txt");
        assertEquals("Exact file mapping (/docs/file.txt)", "/storage/emulated/0/Onedroid/docs/file.txt", res);

        // Alias spelling /sdcard
        String resAlias = engine.resolvePath("/sdcard/docs/file.txt");
        assertEquals("Exact file mapping via /sdcard alias", "/storage/emulated/0/Onedroid/docs/file.txt", resAlias);
    }

    private static void testExactDirectoryMapping() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        List<String[]> rules = Collections.singletonList(
                new String[]{"/storage/emulated/0/OldFolder", "/storage/emulated/0/Onedroid/NewFolder"}
        );
        engine.buildRules(rules);

        String res = engine.resolvePath("/storage/emulated/0/OldFolder");
        assertEquals("Exact directory mapping (/OldFolder)", "/storage/emulated/0/Onedroid/NewFolder", res);
    }

    private static void testDirectChildMapping() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        List<String[]> rules = Collections.singletonList(
                new String[]{"/storage/emulated/0/OldFolder", "/storage/emulated/0/Onedroid/NewFolder"}
        );
        engine.buildRules(rules);

        String resFile = engine.resolvePath("/storage/emulated/0/OldFolder/file.txt");
        assertEquals("Direct child file mapping (/OldFolder/file.txt)", "/storage/emulated/0/Onedroid/NewFolder/file.txt", resFile);

        String resDir = engine.resolvePath("/storage/emulated/0/OldFolder/images");
        assertEquals("Direct child directory mapping (/OldFolder/images)", "/storage/emulated/0/Onedroid/NewFolder/images", resDir);
    }

    private static void testDeeplyNestedMapping() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        List<String[]> rules = Collections.singletonList(
                new String[]{"/storage/emulated/0/OldFolder", "/storage/emulated/0/Onedroid/NewFolder"}
        );
        engine.buildRules(rules);

        String res = engine.resolvePath("/storage/emulated/0/OldFolder/a/b/c/file.dat");
        assertEquals("Deeply nested file mapping (/OldFolder/a/b/c/file.dat)",
                "/storage/emulated/0/Onedroid/NewFolder/a/b/c/file.dat", res);

        String resSub = engine.resolvePath("/storage/emulated/0/OldFolder/cache/data.db");
        assertEquals("Nested cache db mapping (/OldFolder/cache/data.db)",
                "/storage/emulated/0/Onedroid/NewFolder/cache/data.db", resSub);
    }

    // ------------------------------------------------------------ 2. Negative matching
    private static void testSimilarDirectoryNames() {
        System.out.println("\n--- Negative Matching Tests ---");
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        List<String[]> rules = Collections.singletonList(
                new String[]{"/storage/emulated/0/OldFolder", "/storage/emulated/0/Onedroid/NewFolder"}
        );
        engine.buildRules(rules);

        String resBackup = engine.resolvePath("/storage/emulated/0/OldFolderBackup");
        assertEquals("Similar name OldFolderBackup should NOT match", null, resBackup);

        String resBackupChild = engine.resolvePath("/storage/emulated/0/OldFolderBackup/file.txt");
        assertEquals("Child of OldFolderBackup should NOT match", null, resBackupChild);
    }

    private static void testPrefixCollisions() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        List<String[]> rules = Collections.singletonList(
                new String[]{"/storage/emulated/0/OldFolder", "/storage/emulated/0/Onedroid/NewFolder"}
        );
        engine.buildRules(rules);

        String res2 = engine.resolvePath("/storage/emulated/0/OldFolder2");
        assertEquals("Prefix collision OldFolder2 should NOT match", null, res2);

        String res2Child = engine.resolvePath("/storage/emulated/0/OldFolder2/sub/file.txt");
        assertEquals("Child of OldFolder2 should NOT match", null, res2Child);
    }

    private static void testUnrelatedPaths() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        List<String[]> rules = Collections.singletonList(
                new String[]{"/storage/emulated/0/OldFolder", "/storage/emulated/0/Onedroid/NewFolder"}
        );
        engine.buildRules(rules);

        String res = engine.resolvePath("/storage/emulated/0/AnotherFolder/OldFolder");
        assertEquals("Unrelated path AnotherFolder/OldFolder should NOT match", null, res);

        String resOther = engine.resolvePath("/storage/emulated/0/DCIM/Camera/photo.jpg");
        assertEquals("Unrelated DCIM path should NOT match", null, resOther);
    }

    private static void testEmptyAndInvalidPaths() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        List<String[]> rules = Collections.singletonList(
                new String[]{"/storage/emulated/0/OldFolder", "/storage/emulated/0/Onedroid/NewFolder"}
        );
        engine.buildRules(rules);

        assertEquals("Null path should return null", null, engine.resolvePath(null));
        assertEquals("Empty path should return null", null, engine.resolvePath(""));
        assertEquals("Relative path should return null", null, engine.resolvePath("relative/path/file.txt"));
        assertEquals("Root slash should return null", null, engine.resolvePath("/"));
    }

    // ------------------------------------------------------------ 3. Rule behavior
    private static void testLongestPrefixPriority() {
        System.out.println("\n--- Rule Behavior Tests ---");
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        // Rule A is entered first, Rule B is more specific
        List<String[]> rules = Arrays.asList(
                new String[]{"/storage/emulated/0/Data", "/storage/emulated/0/Onedroid/Data"},
                new String[]{"/storage/emulated/0/Data/Cache", "/storage/emulated/0/Onedroid/Cache"}
        );
        engine.buildRules(rules);

        // Path targeting Data/Cache must use Rule B
        String resCache = engine.resolvePath("/storage/emulated/0/Data/Cache/file.db");
        assertEquals("Longest-prefix: /Data/Cache/file.db matches Rule B",
                "/storage/emulated/0/Onedroid/Cache/file.db", resCache);

        // Path targeting Data/other must use Rule A
        String resOther = engine.resolvePath("/storage/emulated/0/Data/other.txt");
        assertEquals("Longest-prefix: /Data/other.txt matches Rule A",
                "/storage/emulated/0/Onedroid/Data/other.txt", resOther);
    }

    private static void testMultipleIndependentRules() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        List<String[]> rules = Arrays.asList(
                new String[]{"/storage/emulated/0/FolderA", "/storage/emulated/0/TargetA"},
                new String[]{"/storage/emulated/0/FolderB", "/storage/emulated/0/TargetB"},
                new String[]{"/storage/emulated/0/FolderC", "/storage/emulated/0/TargetC"}
        );
        engine.buildRules(rules);

        assertEquals("Rule A match", "/storage/emulated/0/TargetA/1.txt",
                engine.resolvePath("/storage/emulated/0/FolderA/1.txt"));
        assertEquals("Rule B match", "/storage/emulated/0/TargetB/2.txt",
                engine.resolvePath("/storage/emulated/0/FolderB/2.txt"));
        assertEquals("Rule C match", "/storage/emulated/0/TargetC/3.txt",
                engine.resolvePath("/storage/emulated/0/FolderC/3.txt"));
    }

    private static void testPkgSubstitution() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.example.game", "/data/user/0/com.example.game");
        List<String[]> rules = Collections.singletonList(
                new String[]{"/storage/emulated/0/Android/data/{pkg}/files", "/storage/emulated/0/SavedGames/{pkg}"}
        );
        engine.buildRules(rules);

        String res = engine.resolvePath("/storage/emulated/0/Android/data/com.example.game/files/save.dat");
        assertEquals("{pkg} substitution in source and destination",
                "/storage/emulated/0/SavedGames/com.example.game/save.dat", res);
    }

    private static void testOverlappingRulesDeterministic() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        List<String[]> rules = Arrays.asList(
                new String[]{"/storage/emulated/0/App", "/storage/emulated/0/DestApp"},
                new String[]{"/storage/emulated/0/App/Data", "/storage/emulated/0/DestData"},
                new String[]{"/storage/emulated/0/App/Data/Logs", "/storage/emulated/0/DestLogs"}
        );
        engine.buildRules(rules);

        assertEquals("3rd level overlap", "/storage/emulated/0/DestLogs/app.log",
                engine.resolvePath("/storage/emulated/0/App/Data/Logs/app.log"));
        assertEquals("2nd level overlap", "/storage/emulated/0/DestData/app.db",
                engine.resolvePath("/storage/emulated/0/App/Data/app.db"));
        assertEquals("1st level overlap", "/storage/emulated/0/DestApp/config.json",
                engine.resolvePath("/storage/emulated/0/App/config.json"));
    }

    private static void testCyclicMappingProtection() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        // Cyclic rules: A -> B and B -> A
        List<String[]> rules = Arrays.asList(
                new String[]{"/storage/emulated/0/A", "/storage/emulated/0/B"},
                new String[]{"/storage/emulated/0/B", "/storage/emulated/0/A"}
        );
        engine.buildRules(rules);

        // Must not cause infinite loop or stack overflow
        String res = engine.resolvePath("/storage/emulated/0/A/test.txt");
        assertTrue("Cyclic mapping terminated safely", res != null && !res.isEmpty());
    }

    private static void testRelativeRulePaths() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        List<String[]> rules = Arrays.asList(
                // Relative from and to (both default to /storage/emulated/0)
                new String[]{"Download/{pkg}", "CustomData/{pkg}/downloads"},
                // Absolute from, relative to
                new String[]{"/data/user/0/{pkg}/files", "CustomData/{pkg}/internal/files"}
        );
        engine.buildRules(rules);

        String resRel = engine.resolvePath("/storage/emulated/0/Download/com.test.app/report.pdf");
        assertEquals("Relative rule paths default to /storage/emulated/0",
                "/storage/emulated/0/CustomData/com.test.app/downloads/report.pdf", resRel);

        String resMixed = engine.resolvePath("/data/user/0/com.test.app/files/doc.txt");
        assertEquals("Full path source with relative destination",
                "/storage/emulated/0/CustomData/com.test.app/internal/files/doc.txt", resMixed);
    }

    // ------------------------------------------------------------ 4. Destination behavior
    private static void testDestinationExists() {
        System.out.println("\n--- Destination Behavior Tests ---");
        File tempDir = createTempDir("sr_test_dest_exists");
        File srcDir = new File(tempDir, "src");
        File destDir = new File(tempDir, "dest");
        destDir.mkdirs();

        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        engine.buildRules(Collections.singletonList(
                new String[]{srcDir.getAbsolutePath(), destDir.getAbsolutePath()}
        ));

        String res = engine.resolvePath(new File(srcDir, "file.txt").getAbsolutePath());
        assertEquals("Destination exists: maps correctly", new File(destDir, "file.txt").getAbsolutePath(), res);
        deleteRecursively(tempDir);
    }

    private static void testDestinationDoesNotExist() {
        File tempDir = createTempDir("sr_test_dest_not_exists");
        File srcDir = new File(tempDir, "src");
        File destDir = new File(tempDir, "dest");

        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        engine.buildRules(Collections.singletonList(
                new String[]{srcDir.getAbsolutePath(), destDir.getAbsolutePath()}
        ));

        // Rule must NOT be discarded even though destDir does not exist yet!
        assertEquals("Rule retained when dest does not exist", 1, engine.getRules().size());
        String res = engine.resolvePath(new File(srcDir, "file.txt").getAbsolutePath());
        assertEquals("Maps correctly to nonexistent destination", new File(destDir, "file.txt").getAbsolutePath(), res);
        deleteRecursively(tempDir);
    }

    private static void testIntermediateParentsDoNotExist() {
        File tempDir = createTempDir("sr_test_nested_parents");
        File srcDir = new File(tempDir, "src");
        File deepDest = new File(tempDir, "a/b/c/dest");

        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        engine.buildRules(Collections.singletonList(
                new String[]{srcDir.getAbsolutePath(), deepDest.getAbsolutePath()}
        ));

        String targetPath = new File(srcDir, "file.txt").getAbsolutePath();
        String redirected = engine.resolvePath(targetPath);
        engine.ensureTargetParents(redirected, false);

        assertTrue("Missing intermediate parents created", deepDest.exists() || deepDest.getParentFile().exists());
        deleteRecursively(tempDir);
    }

    private static void testDestinationIsFileRatherThanDirectory() {
        File tempDir = createTempDir("sr_test_file_dest");
        tempDir.mkdirs();
        File srcFile = new File(tempDir, "config.json");
        File destFile = new File(tempDir, "dest_config.json");

        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        engine.buildRules(Collections.singletonList(
                new String[]{srcFile.getAbsolutePath(), destFile.getAbsolutePath()}
        ));

        PathEngine.PathRule rule = engine.getRules().get(0);
        assertTrue("Destination detected as file rule, not directory", !rule.isDirectoryRule);
        assertTrue("File destination path not created as a directory", !destFile.isDirectory());
        deleteRecursively(tempDir);
    }

    private static void testDestinationInaccessible() {
        PathEngine engine = new PathEngine("/storage/emulated/0", "com.test.app", "/data/user/0/com.test.app");
        // Inaccessible path /root/forbidden/dest
        engine.buildRules(Collections.singletonList(
                new String[]{"/storage/emulated/0/accessible", "/root/forbidden/dest"}
        ));

        assertEquals("Rule retained even if destination was initially inaccessible", 1, engine.getRules().size());
        assertEquals("Resolved path computed accurately",
                "/root/forbidden/dest/file.txt",
                engine.resolvePath("/storage/emulated/0/accessible/file.txt"));
    }

    // ------------------------------------------------------------ 5. Filesystem behavior
    private static void testReadExistingRedirectedFiles() {
        System.out.println("\n--- Filesystem Behavior Tests ---");
        File tempDir = createTempDir("sr_test_fs_read");
        File srcDir = new File(tempDir, "old");
        File destDir = new File(tempDir, "new");
        destDir.mkdirs();

        try {
            // Write physical file to destination
            File physicalFile = new File(destDir, "hello.txt");
            writeFile(physicalFile, "Hello Redirected World!");

            PathEngine engine = new PathEngine(tempDir.getAbsolutePath(), "com.test.app", null);
            engine.buildRules(Collections.singletonList(
                    new String[]{srcDir.getAbsolutePath(), destDir.getAbsolutePath()}
            ));

            String originalRequest = new File(srcDir, "hello.txt").getAbsolutePath();
            String resolved = engine.resolvePath(originalRequest);
            assertEquals("Read path resolved", physicalFile.getAbsolutePath(), resolved);

            // Read through resolved path
            String content = readFile(new File(resolved));
            assertEquals("File content read from destination", "Hello Redirected World!", content);
        } catch (IOException e) {
            sFailed++;
            sFailures.add("testReadExistingRedirectedFiles exception: " + e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void testWriteNewRedirectedFiles() {
        File tempDir = createTempDir("sr_test_fs_write");
        File srcDir = new File(tempDir, "old");
        File destDir = new File(tempDir, "new");

        try {
            PathEngine engine = new PathEngine(tempDir.getAbsolutePath(), "com.test.app", null);
            engine.buildRules(Collections.singletonList(
                    new String[]{srcDir.getAbsolutePath(), destDir.getAbsolutePath()}
            ));

            String originalRequest = new File(srcDir, "new_file.txt").getAbsolutePath();
            String resolved = engine.resolvePath(originalRequest);

            // Ensure parent directory created on demand
            engine.ensureTargetParents(resolved, false);
            File destFile = new File(resolved);
            writeFile(destFile, "Newly created file");

            assertTrue("File physically created in destination directory", destFile.exists());
            assertEquals("Content verified", "Newly created file", readFile(destFile));
            assertTrue("Source directory left untouched", !srcDir.exists() || !new File(srcDir, "new_file.txt").exists());
        } catch (IOException e) {
            sFailed++;
            sFailures.add("testWriteNewRedirectedFiles exception: " + e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void testCreateNestedDirectories() {
        File tempDir = createTempDir("sr_test_fs_mkdir");
        File srcDir = new File(tempDir, "old");
        File destDir = new File(tempDir, "new");

        PathEngine engine = new PathEngine(tempDir.getAbsolutePath(), "com.test.app", null);
        engine.buildRules(Collections.singletonList(
                new String[]{srcDir.getAbsolutePath(), destDir.getAbsolutePath()}
        ));

        String originalDir = new File(srcDir, "nested/sub/folder").getAbsolutePath();
        String resolvedDir = engine.resolvePath(originalDir);

        // Ensure parent and mkdirs
        engine.ensureTargetParents(resolvedDir, true);
        File physicalNested = new File(resolvedDir);

        assertTrue("Nested directory physically created in destination", physicalNested.exists() && physicalNested.isDirectory());
        deleteRecursively(tempDir);
    }

    private static void testListRedirectedDirectoryContents() {
        File tempDir = createTempDir("sr_test_fs_list");
        File srcDir = new File(tempDir, "old");
        File destDir = new File(tempDir, "new");
        srcDir.mkdirs();
        destDir.mkdirs();

        try {
            // Source has stale_file.txt (from before rule)
            writeFile(new File(srcDir, "stale_file.txt"), "stale");
            // Destination has real contents
            writeFile(new File(destDir, "photo.jpg"), "jpg");
            writeFile(new File(destDir, "notes.txt"), "notes");
            new File(destDir, "subfolder").mkdirs();

            PathEngine engine = new PathEngine(tempDir.getAbsolutePath(), "com.test.app", null);
            engine.buildRules(Collections.singletonList(
                    new String[]{srcDir.getAbsolutePath(), destDir.getAbsolutePath()}
            ));

            String targetDir = srcDir.getAbsolutePath();
            String resolvedDir = engine.resolvePath(targetDir);
            File dirToList = new File(resolvedDir);
            String[] listing = dirToList.list();

            assertTrue("Listing is not null", listing != null);
            List<String> list = Arrays.asList(listing);
            assertTrue("Contains photo.jpg from destination", list.contains("photo.jpg"));
            assertTrue("Contains notes.txt from destination", list.contains("notes.txt"));
            assertTrue("Contains subfolder from destination", list.contains("subfolder"));
            assertTrue("Does NOT contain stale_file.txt from source", !list.contains("stale_file.txt"));
        } catch (IOException e) {
            sFailed++;
            sFailures.add("testListRedirectedDirectoryContents exception: " + e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void testDeleteRedirectedFiles() {
        File tempDir = createTempDir("sr_test_fs_delete");
        File srcDir = new File(tempDir, "old");
        File destDir = new File(tempDir, "new");
        destDir.mkdirs();

        try {
            File toDelete = new File(destDir, "delete_me.txt");
            writeFile(toDelete, "bye");

            PathEngine engine = new PathEngine(tempDir.getAbsolutePath(), "com.test.app", null);
            engine.buildRules(Collections.singletonList(
                    new String[]{srcDir.getAbsolutePath(), destDir.getAbsolutePath()}
            ));

            String resolved = engine.resolvePath(new File(srcDir, "delete_me.txt").getAbsolutePath());
            File physical = new File(resolved);
            boolean deleted = physical.delete();

            assertTrue("File deleted at destination", deleted);
            assertTrue("File does not exist anymore", !physical.exists());
        } catch (IOException e) {
            sFailed++;
            sFailures.add("testDeleteRedirectedFiles exception: " + e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void testRenameRedirectedFiles() {
        File tempDir = createTempDir("sr_test_fs_rename");
        File srcDir = new File(tempDir, "old");
        File destDir = new File(tempDir, "new");
        destDir.mkdirs();

        try {
            File srcFile = new File(destDir, "original.txt");
            writeFile(srcFile, "rename content");

            PathEngine engine = new PathEngine(tempDir.getAbsolutePath(), "com.test.app", null);
            engine.buildRules(Collections.singletonList(
                    new String[]{srcDir.getAbsolutePath(), destDir.getAbsolutePath()}
            ));

            String resolvedFrom = engine.resolvePath(new File(srcDir, "original.txt").getAbsolutePath());
            String resolvedTo = engine.resolvePath(new File(srcDir, "renamed.txt").getAbsolutePath());

            engine.ensureTargetParents(resolvedTo, false);
            boolean renamed = new File(resolvedFrom).renameTo(new File(resolvedTo));

            assertTrue("File renamed successfully", renamed);
            assertTrue("Old destination file does not exist", !new File(resolvedFrom).exists());
            assertTrue("New destination file exists", new File(resolvedTo).exists());
            assertEquals("Content preserved after rename", "rename content", readFile(new File(resolvedTo)));
        } catch (IOException e) {
            sFailed++;
            sFailures.add("testRenameRedirectedFiles exception: " + e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void testCopyAndMoveOperations() {
        File tempDir = createTempDir("sr_test_fs_copy_move");
        File srcDir = new File(tempDir, "old");
        File destDir = new File(tempDir, "new");
        destDir.mkdirs();

        try {
            File original = new File(destDir, "source.txt");
            writeFile(original, "copy/move data");

            PathEngine engine = new PathEngine(tempDir.getAbsolutePath(), "com.test.app", null);
            engine.buildRules(Collections.singletonList(
                    new String[]{srcDir.getAbsolutePath(), destDir.getAbsolutePath()}
            ));

            // Copy
            String resolvedSrc = engine.resolvePath(new File(srcDir, "source.txt").getAbsolutePath());
            String resolvedCopy = engine.resolvePath(new File(srcDir, "copy.txt").getAbsolutePath());
            engine.ensureTargetParents(resolvedCopy, false);

            Files.copy(Paths.get(resolvedSrc), Paths.get(resolvedCopy), StandardCopyOption.REPLACE_EXISTING);
            assertTrue("Copy succeeded at destination", new File(resolvedCopy).exists());
            assertEquals("Copy content verified", "copy/move data", readFile(new File(resolvedCopy)));

            // Move
            String resolvedMove = engine.resolvePath(new File(srcDir, "moved.txt").getAbsolutePath());
            engine.ensureTargetParents(resolvedMove, false);

            Files.move(Paths.get(resolvedCopy), Paths.get(resolvedMove), StandardCopyOption.REPLACE_EXISTING);
            assertTrue("Move destination exists", new File(resolvedMove).exists());
            assertTrue("Move source deleted", !new File(resolvedCopy).exists());
        } catch (IOException e) {
            sFailed++;
            sFailures.add("testCopyAndMoveOperations exception: " + e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    // ------------------------------------------------------------ Helpers
    private static File createTempDir(String name) {
        File base = new File("/data/user/0/com.agy/files/usr/tmp");
        if (!base.exists() && !base.mkdirs()) {
            base = new File(System.getProperty("java.io.tmpdir", "/tmp"));
            if (!base.exists() && !base.mkdirs()) {
                base = new File("build/tmp");
            }
        }
        File d = new File(base, name + "_" + System.currentTimeMillis());
        d.mkdirs();
        return d;
    }

    private static void writeFile(File f, String text) throws IOException {
        f.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(text.getBytes("UTF-8"));
        } finally {
            fos.close();
        }
    }

    private static String readFile(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        try {
            byte[] buf = new byte[(int) f.length()];
            int n = fis.read(buf);
            return new String(buf, 0, n > 0 ? n : 0, "UTF-8");
        } finally {
            fis.close();
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File c : files) deleteRecursively(c);
            }
        }
        f.delete();
    }
}
