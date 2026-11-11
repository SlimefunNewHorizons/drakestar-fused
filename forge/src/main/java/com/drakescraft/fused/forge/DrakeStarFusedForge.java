package com.drakescraft.fused.forge;

import com.drakescraft.fused.bridge.FusedCraftServer;
import com.drakescraft.fused.rust.SlimefunRustNativeBridge;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@Mod("drakestar_fused")
public class DrakeStarFusedForge {
    public static final String MOD_ID = "drakestar_fused";
    public static final Logger LOGGER = LoggerFactory.getLogger(DrakeStarFusedForge.class);

    public DrakeStarFusedForge() {
        LOGGER.info("⚡ [DrakeStar Fused] Módulo Forge Clásico inicializado.");
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("🏛 [Forge] Inyectando Bukkit & StarSuites en el servidor...");
        File runDir = new File(".");
        SlimefunRustNativeBridge.init(runDir.toPath());
        FusedCraftServer.bootstrap(runDir.toPath());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("🛑 [Forge] Deteniendo plugins de Bukkit y liberando memoria Rust off-heap...");
    }
}
