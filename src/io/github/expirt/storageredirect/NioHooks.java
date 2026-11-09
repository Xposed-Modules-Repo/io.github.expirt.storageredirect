package io.github.expirt.storageredirect;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Intercepts java.nio.file operations through sun.nio.fs.UnixFileSystemProvider.
 *
 * Isolated into its own class so that Android runtimes below API 26 do not fail
 * class verification due to references to java.nio.file.Path.
 */
public final class NioHooks {

    public interface PathTranslator {
        String translate(String path, String op);
        void ensureParents(String path, boolean isDir);
        boolean isDebug();
        void log(String msg);
    }

    private static final Set<String> NIO_PATH_METHODS = new HashSet<>(Arrays.asList(
            "readAttributes", "checkAccess", "createDirectory", "implDelete",
            "copy", "move", "newByteChannel", "newFileChannel", "newDirectoryStream",
            "readSymbolicLink", "createSymbolicLink", "createLink", "exists", "isDirectory", "isRegularFile"
    ));

    private NioHooks() {}

    public static int install(ClassLoader cl, final PathTranslator translator) {
        int count = 0;
        String[] providerClasses = {
                "sun.nio.fs.UnixFileSystemProvider",
                "sun.nio.fs.LinuxFileSystemProvider"
        };

        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object[] args = param.args;
                    if (args == null) return;
                    String methodName = param.method != null ? param.method.getName() : "";

                    for (int i = 0; i < args.length; i++) {
                        Object o = args[i];
                        if (o instanceof Path) {
                            Path p = (Path) o;
                            String orig = p.toString();
                            String redirected = translator.translate(orig, methodName);

                            if (redirected != null && !redirected.equals(orig)) {
                                args[i] = p.getFileSystem().getPath(redirected);

                                boolean isDirOp = "createDirectory".equals(methodName) || "newDirectoryStream".equals(methodName);
                                translator.ensureParents(redirected, isDirOp);

                                if (translator.isDebug()) {
                                    translator.log("[NIO] op=" + methodName + " orig=\"" + orig + "\" -> dest=\"" + redirected + "\"");
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    if (translator.isDebug()) {
                        translator.log("[NIO-ERROR] in hook: " + t);
                    }
                }
            }
        };

        for (String clsName : providerClasses) {
            Class<?> cls = XposedHelpers.findClassIfExists(clsName, cl);
            if (cls == null) continue;

            for (Method m : cls.getDeclaredMethods()) {
                if (!NIO_PATH_METHODS.contains(m.getName())) continue;
                boolean takesPath = false;
                for (Class<?> pType : m.getParameterTypes()) {
                    if (Path.class.isAssignableFrom(pType)) {
                        takesPath = true;
                        break;
                    }
                }
                if (!takesPath) continue;

                try {
                    XposedBridge.hookMethod(m, hook);
                    count++;
                } catch (Throwable t) {
                    if (translator.isDebug()) {
                        translator.log("Failed to hook NIO method " + clsName + "." + m.getName() + ": " + t);
                    }
                }
            }
        }

        return count;
    }
}
