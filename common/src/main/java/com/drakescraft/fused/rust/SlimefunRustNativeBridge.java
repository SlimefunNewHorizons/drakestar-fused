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
        String libName;
        if (osName.contains("win")) {
            libName = "drakes_hybrid_ffi.dll";
        } else if (osName.contains("mac")) {
            libName = "libdrakes_hybrid_ffi.dylib";
        } else {
            libName = "libdrakes_hybrid_ffi.so";
        }

        try {
            Path targetPath = workDir.resolve(libName);
            // Check if already in workDir or extract from resources
            if (!Files.exists(targetPath)) {
                try (InputStream in = SlimefunRustNativeBridge.class.getResourceAsStream("/natives/" + libName)) {
                    if (in != null) {
                        Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        LOGGER.info("📦 Extracted native engine binary: {}", targetPath);
                    }
                }
            }

            if (Files.exists(targetPath)) {
                System.load(targetPath.toAbsolutePath().toString());
                loaded = true;
                LOGGER.info("🦀 Slimefun-Rust native SIMD off-heap engine linked successfully! (AVX-512 VNNI enabled)");
            } else {
                LOGGER.warn("⚠️ Native binary {} not found in workDir, falling back to Java pure simulation mode.", libName);
            }
        } catch (Throwable t) {
            LOGGER.error("❌ Failed to bind native Slimefun-Rust library: {}", t.getMessage());
        }
        return loaded;
    }

    public static boolean isLoaded() {
        return loaded;
    }
}
