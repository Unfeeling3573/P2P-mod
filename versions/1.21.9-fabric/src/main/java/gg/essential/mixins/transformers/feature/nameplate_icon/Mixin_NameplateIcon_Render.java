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

import com.llamalad7.mixinextras.sugar.Local;
import gg.essential.mixins.impl.LabelCommandExt;
import gg.essential.model.ModelInstance;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.command.LabelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import gg.essential.cosmetics.CosmeticsRenderState;
import gg.essential.cosmetics.IconCosmeticRenderer;
import gg.essential.universal.UMatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC >= 26.2
//$$ import net.minecraft.client.gui.Font;
//$$ import net.minecraft.client.renderer.feature.FeatureFrameContext;
//$$ import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
//#endif

@Mixin(LabelCommandRenderer.class)
public abstract class Mixin_NameplateIcon_Render
    //#if MC >= 26.2
    //$$ extends RenderTypeFeatureRenderer<NameTagFeatureRenderer.Submit>
    //#endif
{
    //#if MC >= 26.2
    //#if MC >= 26.3
    //$$ @Inject(method = "buildGroup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer;renderText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$GlyphRenderer;Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content$Text;)V"))
    //#else
    //$$ @Inject(method = "buildGroup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font$PreparedText;visit(Lnet/minecraft/client/gui/Font$GlyphVisitor;)V"))
    //#endif
    //$$ private void renderEssentialIndicatorSeeThrough(
    //$$     CallbackInfo ci,
    //$$     @Local(argsOnly = true) FeatureFrameContext context,
        //#if MC >= 26.3
        //$$ @Local(name = "submit") TextFeatureRenderer.Submit nameTag
        //#else
        //$$ @Local(name = "nameTag") NameTagFeatureRenderer.Submit nameTag
        //#endif
    //$$ ) {
    //$$     @SuppressWarnings("Convert2MethodRef") // breaks mixin
    //$$     VertexConsumerProvider vertexConsumerProvider = renderType -> getVertexBuilder(renderType);
    //$$     boolean seeThrough = nameTag.displayMode() == Font.DisplayMode.SEE_THROUGH;
    //$$     renderEssentialIndicator(vertexConsumerProvider, context.font(), nameTag, seeThrough);
    //$$ }
    //#else
    //#if MC >= 26.1
    //$$ private static final String DRAW_TEXT = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V";
    //#else
    private static final String DRAW_TEXT = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V";
    //#endif

    @Inject(method = "render", at = @At(value = "INVOKE", target = DRAW_TEXT, ordinal = 0))
    private void renderEssentialIndicatorSeeThrough(
        CallbackInfo ci,
        @Local(argsOnly = true) VertexConsumerProvider.Immediate immediate,
        @Local(argsOnly = true) TextRenderer textRenderer,
        @Local OrderedRenderCommandQueueImpl.LabelCommand command
    ) {
        renderEssentialIndicator(immediate, textRenderer, command, true);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = DRAW_TEXT, ordinal = 1))
    private void renderEssentialIndicatorNormal(
        CallbackInfo ci,
        @Local(argsOnly = true) VertexConsumerProvider.Immediate immediate,
        @Local(argsOnly = true) TextRenderer textRenderer,
        @Local OrderedRenderCommandQueueImpl.LabelCommand command
    ) {
        renderEssentialIndicator(immediate, textRenderer, command, false);
    }
    //#endif

    @Unique
    private void renderEssentialIndicator(
        VertexConsumerProvider vertexConsumerProvider,
        TextRenderer textRenderer,
        //#if MC >= 26.2
        //$$ NameTagFeatureRenderer.Submit command,
        //#else
        OrderedRenderCommandQueueImpl.LabelCommand command,
        //#endif
        boolean seeThrough
    ) {
        UMatrixStack matrixStack = new UMatrixStack();
        matrixStack.peek().getModel().set(command.matricesEntry());

        //#if MC >= 26.3
        //$$ TextFeatureRenderer.Content.Text content = (TextFeatureRenderer.Content.Text) command.content();
        //$$ int textWidth = textRenderer.width(content.string());
        //$$ int color = content.color();
        //$$ int backgroundColor = content.backgroundColor();
        //#else
        int textWidth = textRenderer.getWidth(command.text());
        int color = command.color();
        int backgroundColor = command.backgroundColor();
        //#endif

        CosmeticsRenderState cState = LabelCommandExt.of(command).essential$getCosmeticsRenderState();
        ModelInstance icon = cState != null ? cState.nametagIcon() : null;

        // FIXME Currently this uses a custom `white.png` texture, which results in a different RenderLayer and
        //  therefore much less batching than otherwise possible (basically pre-1.21.9 levels).
        //  We should try to use the same layer as vanilla such that texts can be drawn in a single call.
        IconCosmeticRenderer.INSTANCE.drawNameTagIconAndVersionConsistentPadding(
            matrixStack, vertexConsumerProvider, seeThrough, color, backgroundColor, icon, textWidth, command.lightCoords());
    }
}
