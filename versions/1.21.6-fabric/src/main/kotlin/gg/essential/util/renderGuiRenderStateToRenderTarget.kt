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
package gg.essential.util

import com.mojang.blaze3d.systems.RenderSystem
import gg.essential.universal.UGraphics
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.image.GpuTexture
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.gui.render.state.GuiRenderState
import net.minecraft.client.render.fog.FogRenderer

private val COMPOSITE_PIPELINE = URenderPipeline.builderWithDefaultShader(
    "essential:gui_renderer_composite",
    UGraphics.DrawMode.QUADS,
    UGraphics.CommonVertexFormats.POSITION_TEXTURE,
).apply {
    blendState = BlendState.PREMULTIPLIED_ALPHA
}.build()

/**
 * Renders the given [GuiRenderState] to a [GpuTexture].
 */
fun renderGuiRenderStateToTexture(guiRenderState: GuiRenderState): GpuTexture {
    val mcColor = platform.mcFrameBufferColorTexture
    val resultColor = platform.newGpuTexture(mcColor.width, mcColor.height, GpuTexture.Format.RGBA8)
    renderGuiRenderStateToTexture(guiRenderState, resultColor.ucView)
    return resultColor
}

/**
 * Renders the given [GuiRenderState] to a [UGpuTextureView].
 *
 * Most of the implementation is dealing with the fact that MC's gui renderer always renders to MC's framebuffer and
 * doesn't respect [RenderSystem.outputColorTextureOverride].
 */
fun renderGuiRenderStateToTexture(guiRenderState: GuiRenderState, resultColor: UGpuTextureView) {
    val mc = MinecraftClient.getInstance()
    val mcColor = platform.mcFrameBufferColorTexture
    val mcDepth = platform.mcFrameBufferDepthTexture!!

    val width = mcColor.width
    val height = mcColor.height

    // Backup framebuffer
    val orgColor = platform.newGpuTexture(width, height, GpuTexture.Format.RGBA8)
    val orgDepth = platform.newGpuTexture(width, height, GpuTexture.Format.DEPTH32)
    orgColor.copyFrom(mcColor)
    orgDepth.copyFrom(mcDepth)
    mcColor.clearColor()
    mcDepth.clearDepth()

    // Backup projection matrix
    // GuiRenderer replaces this buffer with its own, which is invalidated in its `close`, so we need to backup
    // and restore the original buffer, otherwise OpenGL will end up trying to use an already-freed buffer.
    val orgProjectionMatrixBuffer = RenderSystem.getProjectionMatrixBuffer()
    val orgProjectionType = RenderSystem.getProjectionType()
    // same deal as above, just for FogRenderer
    val orgShaderFog = RenderSystem.getShaderFog()

    // Render gui (to framebuffer)
    val fogRenderer = FogRenderer()
    val guiRenderer = GuiRenderer(
        guiRenderState,
        //#if MC >= 26.2
        //$$ mc.gameRenderer.featureRenderDispatcher(),
        //#else
        mc.bufferBuilders.entityVertexConsumers,
        //#if MC>=12109
        //$$ mc.gameRenderer.entityRenderCommandQueue,
        //$$ mc.gameRenderer.entityRenderDispatcher,
        //#endif
        //#endif
        emptyList(),
    )
    //#if MC >= 26.2
    //$$ guiRenderer.render()
    //#else
    guiRenderer.render(fogRenderer.getFogBuffer(FogRenderer.FogType.NONE))
    //#endif
    guiRenderer.close()
    fogRenderer.close()
    GuiRendererInfo.customGuiRendererUsedThisFrame = true

    // Restore projection matrix
    // Nullability annotations are a bit off, null seems fine. And we MUST NOT keep the current buffer, even if the org
    // buffer is null, otherwise we'll have use-after-free bugs, see comment above on `orgProjectionMatrixBuffer`.
    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    RenderSystem.setProjectionMatrix(orgProjectionMatrixBuffer, orgProjectionType)
    // same deal as above, just for FogRenderer
    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    RenderSystem.setShaderFog(orgShaderFog)

    // Copy framebuffer to texture
    platform.wrapGpuTexture(GpuTexture.Format.RGBA8, resultColor)
        .copyFrom(mcColor)

    // Restore framebuffer
    mcColor.copyFrom(orgColor)
    mcDepth.copyFrom(orgDepth)
    orgColor.close()
    orgDepth.close()
}

object GuiRendererInfo {
    var customGuiRendererUsedThisFrame = false
}
