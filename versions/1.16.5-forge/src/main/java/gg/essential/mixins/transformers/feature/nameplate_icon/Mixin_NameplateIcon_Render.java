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
package gg.essential.mixins.transformers.feature.nameplate_icon;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.matrix.MatrixStack;
import gg.essential.cosmetics.CosmeticsRenderState;
import gg.essential.cosmetics.IconCosmeticRenderer;
import gg.essential.handlers.OnlineIndicator;
import gg.essential.model.ModelInstance;
import gg.essential.universal.UMatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//#if MC>=12102
//$$ import gg.essential.mixins.impl.client.model.PlayerEntityRenderStateExt;
//$$ import net.minecraft.client.render.entity.state.EntityRenderState;
//#endif

@Mixin(EntityRenderer.class)
public class Mixin_NameplateIcon_Render<T extends Entity> {
    //#if MC >= 1.21.6
    //$$ private static final String FONT_DRAW = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V";
    //#elseif MC >= 1.19.4
    //$$ private static final String FONT_DRAW = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I";
    //#else
    private static final String FONT_DRAW = "Lnet/minecraft/client/gui/FontRenderer;func_243247_a(Lnet/minecraft/util/text/ITextComponent;FFIZLnet/minecraft/util/math/vector/Matrix4f;Lnet/minecraft/client/renderer/IRenderTypeBuffer;ZII)I";
    //#endif

    @WrapOperation(method = "renderName", at = @At(value = "INVOKE", target = FONT_DRAW))
    private
    //#if MC >= 1.21.6
    //$$ void
    //#else
    int
    //#endif
    renderEssentialIndicator(
        FontRenderer instance,
        ITextComponent text,
        float x,
        float y,
        int color,
        boolean shadow,
        Matrix4f matrix,
        IRenderTypeBuffer buffer,
        //#if MC >= 1.19.4
        //$$ TextRenderer.TextLayerType layerType,
        //#else
        boolean layerType,
        //#endif
        int backgroundColor,
        int light,
        Operation<Integer> operation,
        //#if MC>=12102
        //$$ @Local(argsOnly = true) EntityRenderState state,
        //#else
        @Local(argsOnly = true) T entity,
        //#endif
        @Local(argsOnly = true) MatrixStack vMatrixStack
    ) {
        //#if MC >= 1.19.4
        //$$ boolean alwaysOnTop = layerType == TextRenderer.TextLayerType.SEE_THROUGH;
        //#else
        boolean alwaysOnTop = layerType;
        //#endif

        ModelInstance icon = null;
        if (OnlineIndicator.currentlyDrawingPlayerEntityName()) {
            //#if MC>=12102
            //$$ if (state instanceof PlayerEntityRenderStateExt) {
            //$$     CosmeticsRenderState cState = ((PlayerEntityRenderStateExt) state).essential$getCosmetics();
            //#else
            if (entity instanceof AbstractClientPlayerEntity) {
                CosmeticsRenderState cState = new CosmeticsRenderState.Live((AbstractClientPlayerEntity) entity);
            //#endif
                icon = cState.nametagIcon();
            }
        }

        int stringWidth = Minecraft.getInstance().fontRenderer.getStringPropertyWidth(text);

        IconCosmeticRenderer.INSTANCE.drawNameTagIconAndVersionConsistentPadding(
                new UMatrixStack(vMatrixStack), buffer, alwaysOnTop, color, backgroundColor, icon, stringWidth, light);

        //#if MC < 1.21.6
        return
        //#endif
        operation.call(instance, text, x, y, color, shadow, matrix, buffer, layerType, backgroundColor, light);
    }
}
