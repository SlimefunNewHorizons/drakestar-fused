package com.drakescraft.fused.neoforge;

import com.drakescraft.fused.bridge.FusedCraftServer;
import com.drakescraft.fused.rust.SlimefunRustNativeBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@Mod("drakestar_fused")
public class DrakeStarFusedNeoForge {
    public static final String MOD_ID = "drakestar_fused";
    public static final Logger LOGGER = LoggerFactory.getLogger(DrakeStarFusedNeoForge.class);

    public DrakeStarFusedNeoForge(IEventBus modEventBus) {
        LOGGER.info("⚡ [DrakeStar Fused] Módulo NeoForge inicializado.");
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("🏛 [NeoForge] Inyectando Bukkit & StarSuites en el servidor...");
        File runDir = new File(".");
        SlimefunRustNativeBridge.init(runDir.toPath());
        FusedCraftServer.bootstrap(runDir.toPath());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("🛑 [NeoForge] Deteniendo plugins de Bukkit y liberando memoria Rust off-heap...");
        FusedCraftServer.shutdown();
    }
}

