package com.drakescraft.fused;

import com.drakescraft.fused.bridge.FusedCraftServer;
import com.drakescraft.fused.rust.SlimefunRustNativeBridge;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Entrypoint de DrakeStar Fused.
 *
 * Se engancha al ciclo de vida del servidor con los eventos de Fabric API (SERVER_STARTING / SERVER_STOPPING), que son
 * iguales de Minecraft 1.20 a 26.2. No usa mixins ni nombres internos de Minecraft: por eso un solo .jar sirve para
 * todas las versiones, ofuscadas (intermediary) o no (26.x).
 */
public class DrakeStarFusedMod implements ModInitializer {
    public static final String MOD_ID = "drakestar-fused";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Path gameDir = FabricLoader.getInstance().getGameDir();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                SlimefunRustNativeBridge.init(gameDir);
                FusedCraftServer.bootstrap(gameDir);
            } catch (Throwable t) {
                // Fused nunca debe tumbar el juego: se registra y se sigue.
                LOGGER.error("DrakeStar Fused no pudo iniciar (el juego sigue sin Fused): ", t);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            try {
                FusedCraftServer.shutdown();
                SlimefunRustNativeBridge.shutdown();
            } catch (Throwable t) {
                LOGGER.warn("Error al detener DrakeStar Fused: ", t);
            }
        });

        LOGGER.info("DrakeStar Fused {} cargado (Fabric Loader {}).", FabricLoader.getInstance().getModContainer(MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"), FabricLoader.getInstance().getModContainer("fabricloader").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
    }
}
