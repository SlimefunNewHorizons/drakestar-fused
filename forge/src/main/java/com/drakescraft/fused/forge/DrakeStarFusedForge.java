package com.drakescraft.fused.forge;

import com.drakescraft.fused.bridge.FusedCraftServer;
import com.drakescraft.fused.rust.SlimefunRustNativeBridge;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod(DrakeStarFusedForge.MOD_ID)
public class DrakeStarFusedForge {
    public static final String MOD_ID = "drakestar_fused";
    public static final String GAME_DIR_PROPERTY = "drakestar.fused.gameDir";
    public static final Logger LOGGER = LoggerFactory.getLogger(DrakeStarFusedForge.class);

    public DrakeStarFusedForge() {
        LOGGER.info("⚡ [DrakeStar Fused] Módulo Forge Clásico inicializado.");
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    public static Path resolveGameDir() {
        String customDir = System.getProperty(GAME_DIR_PROPERTY);
        if (customDir != null && !customDir.isBlank()) {
            return Path.of(customDir).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private void onServerStarting(ServerStartingEvent event) {
        try {
            Path gameDir = resolveGameDir();
            LOGGER.info("🏛 [Forge] Iniciando DrakeStar Fused en {}...", gameDir);
            SlimefunRustNativeBridge.init(gameDir);
            FusedCraftServer.bootstrap(gameDir);
        } catch (Throwable t) {
            LOGGER.error("DrakeStar Fused (Forge) no pudo iniciar (el juego sigue sin Fused): ", t);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        try {
            LOGGER.info("🛑 [Forge] Deteniendo FusedCraftServer y liberando puente Rust...");
            FusedCraftServer.shutdown();
            SlimefunRustNativeBridge.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Error al detener DrakeStar Fused (Forge): ", t);
        }
    }
}


