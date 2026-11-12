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
    private static final Logger LOGGER = LoggerFactory.getLogger(SlimefunRustNativeBridge.class);
    private static boolean loaded = false;

    public static synchronized boolean init(Path workDir) {
        if (loaded) return true;

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
                        System.load(targetPath.toAbsolutePath().toString());
                        loaded = true;
                        LOGGER.info("🦀 Slimefun-Rust native SIMD off-heap engine linked: {}", targetPath);
                        return true;
                    } catch (Throwable t) {
                        LOGGER.warn("No se pudo cargar {} desde {}: {}", libName, dir, t.getMessage());
                    }
                }
            }

            // Intentar extracción desde /natives/ en el JAR
            try (InputStream in = SlimefunRustNativeBridge.class.getResourceAsStream("/natives/" + libName)) {
                if (in != null) {
                    Path targetPath = workDir.resolve(libName);
                    Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("📦 Extracted native engine binary: {}", targetPath);
                    System.load(targetPath.toAbsolutePath().toString());
                    loaded = true;
                    LOGGER.info("🦀 Slimefun-Rust native engine extracted and linked successfully: {}", targetPath);
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        LOGGER.warn("⚠️ Native binary not found in workDir, falling back to Java pure simulation mode.");
        return false;
    }

    public static synchronized void shutdown() {
        loaded = false;
        LOGGER.info("🦀 Slimefun-Rust native bridge reset.");
    }

    public static boolean isLoaded() {
        return loaded;
    }

}
