package com.drakescraft.fused.mixin;

import com.drakescraft.fused.bridge.FusedCraftServer;
import com.drakescraft.fused.rust.SlimefunRustNativeBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.file.Path;

/**
 * Mixin hooked into Minecraft's IntegratedServer startup sequence via targets string
 * ensuring universal compatibility from 1.20 to 26.2 without hardcoded NMS class imports.
 */
@Mixin(targets = "net.minecraft.server.integrated.IntegratedServer")
public abstract class IntegratedServerMixin {

    @Inject(method = "initServer", at = @At("HEAD"))
    private void onInitServerHead(CallbackInfoReturnable<Boolean> cir) {
        File runDir = new File(".");
        Path gamePath = runDir.toPath();

        System.out.println("==================================================================");
        System.out.println("⚡ [DrakeStar Fused] Inyectando Bukkit & StarSuites en IntegratedServer");
        System.out.println("==================================================================");

        // Bootstrap Native Rust off-heap engine
        SlimefunRustNativeBridge.init(gamePath);

        // Bootstrap Fused CraftServer & Plugin Manager
        FusedCraftServer.bootstrap(gamePath);
    }
}
