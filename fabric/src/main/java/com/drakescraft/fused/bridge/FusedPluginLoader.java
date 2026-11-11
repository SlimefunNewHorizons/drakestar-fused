package com.drakescraft.fused.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Escanea y carga los JARs de StarSuites y Slimefun localizados en plugins/
 */
public class FusedPluginLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(FusedPluginLoader.class);

    public static List<Path> discoverPlugins(Path pluginsDir) {
        List<Path> jarList = new ArrayList<>();
        if (!Files.isDirectory(pluginsDir)) {
            return jarList;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path entry : stream) {
                jarList.add(entry);
                LOGGER.info("📦 Plugin detectado: {}", entry.getFileName());
            }
        } catch (IOException e) {
            LOGGER.error("Error escaneando la carpeta de plugins: ", e);
        }

        return jarList;
    }
}
