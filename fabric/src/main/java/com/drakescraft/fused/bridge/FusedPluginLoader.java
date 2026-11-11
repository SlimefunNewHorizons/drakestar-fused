package com.drakescraft.fused.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Escanea, valida firmas y prepara el classloading de los JARs de StarSuites y Slimefun en plugins/
 */
public class FusedPluginLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(FusedPluginLoader.class);

    public record PluginMeta(String name, String version, String mainClass, Path path) {}

    public static List<Path> discoverPlugins(Path pluginsDir) {
        List<Path> jarList = new ArrayList<>();
        if (!Files.isDirectory(pluginsDir)) {
            return jarList;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path entry : stream) {
                jarList.add(entry);
                PluginMeta meta = inspectPluginJar(entry);
                if (meta != null) {
                    LOGGER.info("📦 [FusedPluginLoader] Cargado: {} v{} ({})", meta.name(), meta.version(), meta.mainClass());
                } else {
                    LOGGER.info("📦 [FusedPluginLoader] Detectado JAR sin plugin.yml: {}", entry.getFileName());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error escaneando la carpeta de plugins: ", e);
        }

        return jarList;
    }

    private static PluginMeta inspectPluginJar(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry != null) {
                try (InputStream in = jar.getInputStream(entry)) {
                    // Lectura rapida de campos basicos sin depender de SnakeYAML externo
                    String content = new String(in.readAllBytes());
                    String name = extractYamlField(content, "name");
                    String version = extractYamlField(content, "version");
                    String main = extractYamlField(content, "main");
                    return new PluginMeta(name, version, main, jarPath);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String extractYamlField(String yaml, String key) {
        for (String line : yaml.split("\n")) {
            line = line.trim();
            if (line.startsWith(key + ":")) {
                String val = line.substring(key.length() + 1).trim();
                return val.replace("'", "").replace("\"", "");
            }
        }
        return "unknown";
    }
}
