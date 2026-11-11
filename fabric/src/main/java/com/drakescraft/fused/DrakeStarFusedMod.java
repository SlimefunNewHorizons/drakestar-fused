package com.drakescraft.fused;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint principal de DrakeStar Fused.
 * Inicializa el cargador de plugins y prepara los enlaces JNI / FFM para Slimefun-Rust.
 */
public class DrakeStarFusedMod {
    public static final String MOD_ID = "drakestar-fused";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static DrakeStarFusedMod instance;

    public void onInitialize() {
        instance = this;
        LOGGER.info("⚡ DrakeStar Fused inicializado. Listo para puentear Bukkit/Purpur en IntegratedServer.");
    }

    public static DrakeStarFusedMod getInstance() {
        return instance;
    }
}
