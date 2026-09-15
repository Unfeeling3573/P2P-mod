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
package gg.essential.mixins.transformers.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gg.essential.Essential;
import gg.essential.gui.overlay.OverlayManager;
import gg.essential.gui.overlay.OverlayManagerImpl;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class Mixin_DisableFriendsHotkey {

    @WrapOperation(
            method = "handleGlobalKeyPress",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;toggleFriendsScreen()Z"
            )
    )
    private boolean essential$allowFriendsHotkey(Minecraft instance, Operation<Boolean> original) {
        OverlayManager overlayManager = Essential.getInstance().getOverlayManager();
        if (overlayManager instanceof OverlayManagerImpl impl && impl.hasOpenModals()) {
            return false;
        }
        return original.call(instance);
    }

}