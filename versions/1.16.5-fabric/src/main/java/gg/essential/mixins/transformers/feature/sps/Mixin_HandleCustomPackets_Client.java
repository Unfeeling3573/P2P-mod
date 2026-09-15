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

import gg.essential.sps.packets.SpsNet;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class Mixin_HandleCustomPackets_Client {
    //#if FABRIC
    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void handleEssentialSpsPackets(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        Identifier channel = packet.getChannel();
        if (channel.equals(SpsNet.CHANNEL)) {
            ci.cancel();

            PacketByteBuf buf = packet.getData();
            try {
                SpsNet.INSTANCE.handle(buf, null, (ClientPlayNetworkHandler) (Object) this);
            } finally {
                buf.release();
            }
        }
    }
    //#endif
}
