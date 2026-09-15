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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gg.essential.sps.packets.SpsNet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

@Mixin(CustomPayloadS2CPacket.class)
public class Mixin_RegisterCustomPayload_S2C {
    //#if FABRIC
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayList([Ljava/lang/Object;)Ljava/util/ArrayList;"))
    private static ArrayList<CustomPayload.Type<PacketByteBuf, ?>> register(ArrayList<CustomPayload.Type<PacketByteBuf, ?>> list) {
        list.add(new CustomPayload.Type(SpsNet.SpsCustomPayload.ID, SpsNet.SpsCustomPayload.CODEC));
        return list;
    }
    //#endif
}
