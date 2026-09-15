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
package gg.essential.mixins.transformers.feature.sps;

import com.llamalad7.mixinextras.sugar.Local;
import gg.essential.Essential;
import gg.essential.sps.packets.SpsNet;
import gg.essential.sps.packets.S2CSharedResourcePacks;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class Mixin_SendSharedResourcePacks {
    @Inject(
        method = "initializeConnectionToPlayer",
        at = @At("RETURN")
        //#if MC<=11202
        , remap = false
        //#endif
    )
    private void essential$sendSharedResourcePacks(CallbackInfo info, @Local(argsOnly = true) EntityPlayerMP player) {
        S2CSharedResourcePacks packet = Essential.getInstance().getSharedResourcePacksManager().getSharedResourcePacksPacket();
        if (packet == null) return;
        SpsNet.INSTANCE.send(player, packet);
    }
}
