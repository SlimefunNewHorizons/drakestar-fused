package com.drakescraft.fused.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Escanea, valida, auto-desempaqueta (JiJ) o descarga suites de Slimefun en plugins/
 * Soporta aprovisionamiento automático desde Jar-in-Jar y fallback a maven.drakescraft.cl
 */
public class FusedPluginLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(FusedPluginLoader.class);

    private static final String MAVEN_SUITES_URL = "https://maven.drakescraft.cl/releases/com/drakescraft/suites/";
    private static final String PACK_SUITES_URL = "https://pack.drakescraft.cl/suites/";

    public static final String[] DEFAULT_SUITES = {
        "drakes-core.jar",
        "drakes-tech.jar",
        "drakes-bio.jar",
        "drakes-magic.jar",
        "drakes-generators.jar",
        "drakes-utility.jar",
        "drakes-combat.jar",
        "drakes-server.jar"
    };

    public record PluginMeta(String name, String version, String mainClass, Path path) {}

    public static List<Path> discoverPlugins(Path pluginsDir) {
        List<Path> jarList = new ArrayList<>();
        try {
            if (!Files.exists(pluginsDir)) {
                Files.createDirectories(pluginsDir);
            }
        } catch (IOException e) {
            LOGGER.error("No se pudo crear el directorio plugins/: ", e);
            return jarList;
        }

        // 1. Auto-aprovisionamiento JiJ o remoto si la carpeta esta vacia
        ensureSuitesProvisioned(pluginsDir);

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

    private static void ensureSuitesProvisioned(Path pluginsDir) {
        boolean hasPlugins = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            if (stream.iterator().hasNext()) {
                hasPlugins = true;
            }
        } catch (IOException ignored) {}

        if (hasPlugins) {
            return;
        }

        LOGGER.info("✨ [FusedPluginLoader] Carpeta plugins/ vacía. Iniciando aprovisionamiento de StarSuites...");

        for (String suiteName : DEFAULT_SUITES) {
            Path targetFile = pluginsDir.resolve(suiteName);
            if (Files.exists(targetFile)) {
                continue;
            }

            // A. Intentar extraer desde Jar-in-Jar (assets/drakestar-fused/bundled-suites/)
            String internalResource = "/assets/drakestar-fused/bundled-suites/" + suiteName;
            try (InputStream in = FusedPluginLoader.class.getResourceAsStream(internalResource)) {
                if (in != null) {
                    LOGGER.info("📦 [JiJ] Extrayendo suite interna: {}", suiteName);
                    Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    continue;
                }
            } catch (Exception e) {
                LOGGER.warn("No se pudo extraer JiJ para {}: {}", suiteName, e.getMessage());
            }

            // B. Si no viene en el bundle interno, descargar desde infraestructura de Star
            downloadSuiteFallback(suiteName, targetFile);
        }
    }

    private static void downloadSuiteFallback(String suiteName, Path targetFile) {
        String[] sources = {
            PACK_SUITES_URL + suiteName,
            MAVEN_SUITES_URL + suiteName.replace(".jar", "") + "/1.0.0/" + suiteName
        };

        for (String urlStr : sources) {
            try {
                LOGGER.info("🌐 [Auto-Download] Descargando {} desde {}...", suiteName, urlStr);
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "DrakeStar-Fused-Bootstrap/1.0");

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(targetFile.toFile())) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = in.read(buf)) != -1) {
                            out.write(buf, 0, r);
                        }
                    }
                    LOGGER.info("✅ [Auto-Download] {} descargado con éxito.", suiteName);
                    return;
                }
            } catch (Exception e) {
                LOGGER.debug("Fallo descarga desde {}: {}", urlStr, e.getMessage());
            }
        }
        LOGGER.warn("⚠️ No se pudo aprovisionar automáticamente la suite: {}. Se omitirá por ahora.", suiteName);
    }

    private static PluginMeta inspectPluginJar(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry != null) {
                try (InputStream in = jar.getInputStream(entry)) {
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
