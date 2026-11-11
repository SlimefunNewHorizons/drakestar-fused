package com.drakescraft.fused;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint de cliente para integración de telemetría y HUD.
 */
public class DrakeStarFusedClient {
    public static final Logger LOGGER = LoggerFactory.getLogger("drakestar-fused-client");

    public void onInitializeClient() {
        LOGGER.info("🎮 DrakeStar Fused Client activo. Compatible con Shaders (Iris/Sodium) y canal LAN.");
    }
}
