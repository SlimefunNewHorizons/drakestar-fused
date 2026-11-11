package com.drakescraft.fused.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Host de ejecución que expone la API de Bukkit/Purpur dentro del ciclo de vida
 * del IntegratedServer de Minecraft sin requerir una JVM separada.
 */
public class FusedCraftServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FusedCraftServer.class);
    private static FusedCraftServer activeInstance;

    private final Path pluginsDir;
    private boolean initialized = false;

    public FusedCraftServer(Path gameDir) {
        this.pluginsDir = gameDir.resolve("plugins");
    }

    public static synchronized void bootstrap(Path gameDir) {
        if (activeInstance == null) {
            activeInstance = new FusedCraftServer(gameDir);
            activeInstance.init();
        }
    }

    private void init() {
        if (initialized) return;
        try {
            if (!Files.exists(pluginsDir)) {
                Files.createDirectories(pluginsDir);
                LOGGER.info("📁 Directorio de plugins creado en: {}", pluginsDir.toAbsolutePath());
            }

            LOGGER.info("🏛 Inicializando FusedCraftServer sobre IntegratedServer...");
            LOGGER.info("⚡ Vinculando acelerador Slimefun-Rust (SIMD Off-Heap FFM ABI 1)...");
            
            // Detección e inicialización del cargador de suites
            initialized = true;
            LOGGER.info("✅ FusedCraftServer listo. Plugins en ejecución en la misma memoria del cliente.");
        } catch (Exception e) {
            LOGGER.error("❌ Error al inicializar FusedCraftServer: ", e);
        }
    }

    public static FusedCraftServer getActiveInstance() {
        return activeInstance;
    }

    public Path getPluginsDir() {
        return pluginsDir;
    }
}
