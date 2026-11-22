package com.drakescraft.fused.neoforge;

import com.drakescraft.fused.bridge.FusedCraftServer;
import com.drakescraft.fused.rust.SlimefunRustNativeBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod(DrakeStarFusedNeoForge.MOD_ID)
public class DrakeStarFusedNeoForge {
    public static final String MOD_ID = "drakestar_fused";
    public static final String GAME_DIR_PROPERTY = "drakestar.fused.gameDir";
    public static final Logger LOGGER = LoggerFactory.getLogger(DrakeStarFusedNeoForge.class);

    public DrakeStarFusedNeoForge(IEventBus modEventBus) {
        LOGGER.info("⚡ [DrakeStar Fused] Módulo NeoForge inicializado.");
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
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
            LOGGER.info("🏛 [NeoForge] Iniciando DrakeStar Fused en {}...", gameDir);
            SlimefunRustNativeBridge.init(gameDir);
            FusedCraftServer.bootstrap(gameDir);
        } catch (Throwable t) {
            LOGGER.error("DrakeStar Fused (NeoForge) no pudo iniciar (el juego sigue sin Fused): ", t);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        try {
            LOGGER.info("🛑 [NeoForge] Deteniendo FusedCraftServer y liberando puente Rust...");
            FusedCraftServer.shutdown();
            SlimefunRustNativeBridge.shutdown();
        } catch (Throwable t) {
            LOGGER.warn("Error al detener DrakeStar Fused (NeoForge): ", t);
        }
    }
}


