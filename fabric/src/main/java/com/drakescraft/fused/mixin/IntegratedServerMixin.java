package com.drakescraft.fused.mixin;

import com.drakescraft.fused.bridge.FusedCraftServer;
import com.drakescraft.fused.rust.SlimefunRustNativeBridge;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.file.Path;

/**
 * Mixin hooked into Minecraft's IntegratedServer startup sequence.
 * Intercepts initServer() to bootstrap Bukkit runtime and Slimefun-Rust inside the client process.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {

    @Inject(method = "initServer", at = @At("HEAD"))
    private void onInitServerHead(CallbackInfoReturnable<Boolean> cir) {
        IntegratedServer server = (IntegratedServer) (Object) this;
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
