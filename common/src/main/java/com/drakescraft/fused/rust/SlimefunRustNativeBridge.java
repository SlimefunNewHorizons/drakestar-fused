package com.drakescraft.fused.rust;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and binds the native Rust acceleration library (`libdrakes_hybrid_ffi.so` or `.dll`).
 * Offloads massive energy grids, quantum logistics, and SIMD calculations away from the JVM.
 */
public class SlimefunRustNativeBridge {
    public static final String JNI_DIRECT_PATH_PROPERTY = "drakestar.fused.jniDirectPath";
    private static final Logger LOGGER = LoggerFactory.getLogger(SlimefunRustNativeBridge.class);
    private static boolean loaded = false;
    private static Path loadedPath = null;
    private static String lastError = null;

    private static Path stageForCurrentLoader(Path workDir, Path sourcePath, String libName) throws Exception {
        if (Boolean.getBoolean(JNI_DIRECT_PATH_PROPERTY)) {
            return sourcePath.toAbsolutePath();
        }
        ClassLoader cl = SlimefunRustNativeBridge.class.getClassLoader();
        String rawName = (cl != null && cl.getName() != null && !cl.getName().isBlank())
            ? cl.getName()
            : "loader-" + Integer.toHexString(System.identityHashCode(cl));
        String safeLoaderName = rawName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path stageDir = workDir.resolve("client-natives").resolve(safeLoaderName);
        Files.createDirectories(stageDir);
        Path staged = stageDir.resolve(libName);
        Files.copy(sourcePath, staged, StandardCopyOption.REPLACE_EXISTING);
        return staged.toAbsolutePath();
    }

    public static synchronized boolean init(Path workDir) {
        if (loaded) return true;
        lastError = null;

        String osName = System.getProperty("os.name").toLowerCase();
        String[] candidateNames;
        if (osName.contains("win")) {
            candidateNames = new String[] { "drakes_hybrid_ffi.dll", "slimefun_ffi.dll", "libdrakes_hybrid_ffi.dll" };
        } else if (osName.contains("mac")) {
            candidateNames = new String[] { "libdrakes_hybrid_ffi.dylib", "libslimefun_ffi.dylib" };
        } else {
            candidateNames = new String[] { "libdrakes_hybrid_ffi.so", "libslimefun_ffi.so" };
        }

        Path[] lookupDirs = new Path[] {
            workDir,
            workDir.resolve("natives"),
            workDir.resolve("bin"),
            workDir.resolve("libraries")
        };

        for (String libName : candidateNames) {
            for (Path dir : lookupDirs) {
                Path targetPath = dir.resolve(libName);
                if (Files.exists(targetPath)) {
                    try {
                        Path loadTarget = stageForCurrentLoader(workDir, targetPath, libName);
                        System.load(loadTarget.toString());
                        loaded = true;
                        loadedPath = loadTarget;
                        LOGGER.info("🦀 Slimefun-Rust native SIMD off-heap engine linked: {}", loadTarget);
                        return true;
                    } catch (Throwable t) {
                        lastError = t.getMessage();
                        LOGGER.warn("No se pudo cargar {} desde {}: {}", libName, dir, t.getMessage());
                    }
                }
            }

            // Intentar extracción desde /natives/ en el JAR
            try (InputStream in = SlimefunRustNativeBridge.class.getResourceAsStream("/natives/" + libName)) {
                if (in != null) {
                    Path extracted = workDir.resolve(libName);
                    Files.copy(in, extracted, StandardCopyOption.REPLACE_EXISTING);
                    Path loadTarget = stageForCurrentLoader(workDir, extracted, libName);
                    LOGGER.info("📦 Extracted native engine binary: {}", loadTarget);
                    System.load(loadTarget.toString());
                    loaded = true;
                    loadedPath = loadTarget;
                    LOGGER.info("🦀 Slimefun-Rust native engine extracted and linked successfully: {}", loadTarget);
                    return true;
                }
            } catch (Throwable t) {
                lastError = t.getMessage();
            }
        }

        LOGGER.warn("⚠️ Native binary not found in workDir, falling back to Java pure simulation mode.");
        return false;
    }

    public static synchronized void shutdown() {
        loaded = false;
        loadedPath = null;
        LOGGER.info("🦀 Slimefun-Rust native bridge reset.");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static Path getLoadedPath() {
        return loadedPath;
    }

    public static String getLastError() {
        return lastError;
    }

}
