package com.drakescraft.fused;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint de cliente (solo registra que Fused esta activo en el cliente).
 */
public class DrakeStarFusedClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("drakestar-fused-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("DrakeStar Fused activo en el cliente.");
    }
}
