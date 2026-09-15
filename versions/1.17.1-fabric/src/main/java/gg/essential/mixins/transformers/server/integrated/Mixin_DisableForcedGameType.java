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
package gg.essential.mixins.transformers.server.integrated;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gg.essential.mixins.ext.server.integrated.IntegratedServerExt;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IntegratedServer.class)
public abstract class Mixin_DisableForcedGameType {
    // `getForcedGameMode` always returns a forced game mode when the server is remote.
    // We wrap the `isRemote` condition to also check if SPS is open too
    // and therefore this method will then return null.
    @ModifyExpressionValue(
            method = "getForcedGameMode",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/integrated/IntegratedServer;isRemote()Z")
    )
    private boolean essential$allowForcingGameType(boolean original) {
        return original && !((IntegratedServerExt) this).getEssential$manager().getAppliedOpenToLan();
    }
}
