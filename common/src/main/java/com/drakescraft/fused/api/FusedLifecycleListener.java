package com.drakescraft.fused.api;

import java.nio.file.Path;

/**
 * Common lifecycle listener across all loader implementations (Fabric, NeoForge, Forge).
 */
public interface FusedLifecycleListener {
    void onServerStarting(Path worldDir, Path pluginsDir);
    void onServerStarted(Path worldDir, Path pluginsDir);
    void onServerStopping();
    void onServerStopped();
}
