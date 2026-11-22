package net.fabricmc.loader.api;

import net.fabricmc.loader.api.metadata.ModMetadata;

import java.nio.file.Path;
import java.util.Optional;

public interface FabricLoader {
    FabricLoader INSTANCE = new FabricLoader() {
        @Override
        public Path getGameDir() {
            String dir = System.getProperty("drakestar.fused.gameDir", ".");
            return Path.of(dir).toAbsolutePath().normalize();
        }

        @Override
        public Optional<ModContainer> getModContainer(String id) {
            String ver = "fabricloader".equals(id) ? "0.16.14" : "1.0.0-alpha.3";
            Version version = () -> ver;
            ModMetadata metadata = () -> version;
            ModContainer container = () -> metadata;
            return Optional.of(container);
        }
    };

    static FabricLoader getInstance() {
        return INSTANCE;
    }

    Path getGameDir();

    Optional<ModContainer> getModContainer(String id);
}
