/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.mixins.transformers.compatibility.vanilla;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// When overlay is present during world loading and resource packs are applied in the host panel,
// world loading can freeze because Minecraft will wait until overlay is non-null,
// but the reload process cannot complete because it never drains the main thread task queue.
// Calling `runTasks` allows the resource packs to finish loading, which lets world loading continue.
@Mixin(MinecraftClient.class)
public abstract class Mixin_FixWorldLoadingWithResourcePackEnabled {
    @Shadow
    @Nullable
    private Overlay overlay;

    @Inject(method = "startIntegratedServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;render(Z)V", shift = At.Shift.AFTER))
    public void essential$runTasksIfOverlayPresent(CallbackInfo ci) {
        if (overlay != null) {
            while (((ReentrantThreadExecutor<Runnable>) (Object) this).runTask());
        }
    }
}
