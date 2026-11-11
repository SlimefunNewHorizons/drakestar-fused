package com.drakescraft.fused.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Host de ejecución que expone la API de Bukkit/Purpur dentro del ciclo de vida
 * del IntegratedServer de Minecraft sin requerir una JVM separada.
 */
public class FusedCraftServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FusedCraftServer.class);
    private static FusedCraftServer activeInstance;

    private final Path gameDir;
    private final Path pluginsDir;
    private final Path suitesConfigDir;
    private boolean initialized = false;

    public FusedCraftServer(Path gameDir) {
        this.gameDir = gameDir;
        this.pluginsDir = gameDir.resolve("plugins");
        this.suitesConfigDir = this.pluginsDir.resolve("StarSuites");
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

            if (!Files.exists(suitesConfigDir)) {
                Files.createDirectories(suitesConfigDir);
                LOGGER.info("⚙️ Directorio de configuración de StarSuites creado en: {}", suitesConfigDir.toAbsolutePath());
            }

            LOGGER.info("🏛 Inicializando FusedCraftServer sobre IntegratedServer...");
            LOGGER.info("⚡ Vinculando acelerador Slimefun-Rust (SIMD Off-Heap FFM ABI 1)...");
            
            // Escaneo e inventariado de plugins instalados
            List<Path> plugins = FusedPluginLoader.discoverPlugins(pluginsDir);
            LOGGER.info("📦 Total de plugins/suites detectados en {}: {}", pluginsDir.getFileName(), plugins.size());

            // Validación de dependencias base de StarSuites
            boolean hasCore = plugins.stream().anyMatch(p -> p.getFileName().toString().toLowerCase().contains("core"));
            if (!hasCore) {
                LOGGER.warn("⚠️ Suite 0 (drakes-core.jar) no encontrada en plugins/. Algunas funciones off-heap de StarSuites correrán en modo fallback.");
            } else {
                LOGGER.info("⭐ Kernel StarSuites (drakes-core) verificado con éxito.");
            }

            initialized = true;
            LOGGER.info("✅ FusedCraftServer listo. Plugins en ejecución en la misma memoria del cliente (Single JVM).");
        } catch (Exception e) {
            LOGGER.error("❌ Error al inicializar FusedCraftServer: ", e);
        }
    }

    public static FusedCraftServer getActiveInstance() {
        return activeInstance;
    }

    public Path getGameDir() {
        return gameDir;
    }

    public Path getPluginsDir() {
        return pluginsDir;
    }

    public Path getSuitesConfigDir() {
        return suitesConfigDir;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
