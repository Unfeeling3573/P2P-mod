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
package gg.essential.mixins.transformers.client.renderer.entity;

import com.llamalad7.mixinextras.sugar.Local;
import gg.essential.cosmetics.CosmeticsRenderState;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC>=11200
import net.minecraft.inventory.EntityEquipmentSlot;
//#endif

@Mixin(value = LayerArmorBase.class)
public class Mixin_DisableArmorRendering {

    //#if NEOFORGE && MC == 1.21.1
    //$$ // Neoforge, in 1.21.1 only, creates an overload function with extra parameters, deprecating the original function we target, which is then never called.
    //$$ // https://github.com/neoforged/NeoForge/commit/b92c510d64ed9506140d74538f4699277e8193bd#diff-0e2da9a67dd6c01e595ab3608d405fcea07417ad0b9f782306e966265bdbe5ebR19
    //$$ @Unique private static final String RENDER_ARMOR = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V";
    //#elseif MC >= 1.12
    @Unique private static final String RENDER_ARMOR = "renderArmorLayer";
    //#else
    //$$ @Unique private static final String RENDER_ARMOR =  "renderLayer";
    //#endif


    @Inject(method = RENDER_ARMOR, at = @At(value = "HEAD"), cancellable = true)
    //#if MC>=11200
    private void essential$disableArmorRendering(CallbackInfo info, @Local(argsOnly = true) EntityLivingBase entityLivingBaseIn, @Local(argsOnly = true) EntityEquipmentSlot slotIn) {
        int slotIndex = slotIn.getIndex();
    //#else
    //$$ private void essential$disableArmorRendering(EntityLivingBase entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale, int slotIn, CallbackInfo info) {
    //$$     int slotIndex = slotIn-1;
    //#endif
        if (!(entityLivingBaseIn instanceof AbstractClientPlayer)) return;
        CosmeticsRenderState cState = new CosmeticsRenderState.Live((AbstractClientPlayer) entityLivingBaseIn);
        if (cState.blockedArmorSlots().contains(slotIndex)) {
            info.cancel();
        }
    }
}
