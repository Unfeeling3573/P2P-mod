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

import gg.essential.mixins.transformers.network.CustomPayloadC2SPacketAccessor;
import gg.essential.sps.packets.SpsNet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class Mixin_HandleCustomPackets_Server {
    //#if FABRIC
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void handleEssentialSpsPackets(CustomPayloadC2SPacket packet, CallbackInfo ci) {
        CustomPayloadC2SPacketAccessor packetAcc = (CustomPayloadC2SPacketAccessor) packet;
        Identifier channel = packetAcc.getChannel();
        if (channel.equals(SpsNet.CHANNEL)) {
            ci.cancel();

            PacketByteBuf buf = packetAcc.getData();
            SpsNet.INSTANCE.handle(buf, player.getUuid(), (ServerPlayNetworkHandler) (Object) this);
            // Note: data buf is released by [CustomPayloadC2SPacket.apply]
        }
    }
    //#endif
}
