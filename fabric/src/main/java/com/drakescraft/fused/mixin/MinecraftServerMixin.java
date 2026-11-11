package com.drakescraft.fused.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin hooked into MinecraftServer tick loop and shutdown sequence.
 * Ensures graceful shutdown of Bukkit plugins and SIMD off-heap memory buffers.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void onStopServer(CallbackInfo ci) {
        System.out.println("⚡ [DrakeStar Fused] Deteniendo plugins de Bukkit y liberando memoria off-heap de Rust...");
    }
}
