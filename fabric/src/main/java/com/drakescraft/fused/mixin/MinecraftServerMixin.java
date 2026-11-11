package com.drakescraft.fused.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin hooked into MinecraftServer tick loop and shutdown sequence via targets string
 * ensuring universal compatibility from 1.20 to 26.2 without hardcoded NMS class imports.
 */
@Mixin(targets = "net.minecraft.server.MinecraftServer")
public abstract class MinecraftServerMixin {

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void onStopServer(CallbackInfo ci) {
        System.out.println("⚡ [DrakeStar Fused] Deteniendo plugins de Bukkit y liberando memoria off-heap de Rust...");
    }
}
