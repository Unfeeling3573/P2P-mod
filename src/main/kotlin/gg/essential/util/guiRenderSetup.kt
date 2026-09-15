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

import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.UGpuTextureView
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import org.lwjgl.opengl.GL11

//#if MC >= 1.21.6
//$$ import com.mojang.blaze3d.buffers.GpuBuffer
//$$ import org.lwjgl.system.MemoryStack
//#else
import org.lwjgl.opengl.GL30
//#endif

//#if MC >= 1.21.5
//#else
//#if MC >= 1.17
//$$ import org.lwjgl.opengl.GL30.glBindFramebuffer
//$$ import org.lwjgl.opengl.GL30.glDeleteFramebuffers
//$$ import org.lwjgl.opengl.GL30.glFramebufferTexture2D
//$$ import org.lwjgl.opengl.GL30.glGenFramebuffers
//#elseif MC >= 1.14
//$$ import com.mojang.blaze3d.platform.GlStateManager.bindFramebuffer as glBindFramebuffer
//$$ import com.mojang.blaze3d.platform.GlStateManager.deleteFramebuffers as glDeleteFramebuffers
//$$ import com.mojang.blaze3d.platform.GlStateManager.framebufferTexture2D as glFramebufferTexture2D
//$$ import com.mojang.blaze3d.platform.GlStateManager.genFramebuffers as glGenFramebuffers
//#else
import net.minecraft.client.renderer.OpenGlHelper.glBindFramebuffer
import net.minecraft.client.renderer.OpenGlHelper.glDeleteFramebuffers
import net.minecraft.client.renderer.OpenGlHelper.glFramebufferTexture2D
import net.minecraft.client.renderer.OpenGlHelper.glGenFramebuffers
//#endif
//#endif

//#if MC >= 1.21.2
//$$ import com.mojang.blaze3d.systems.ProjectionType
//#endif

//#if MC >= 1.20
//$$ import com.mojang.blaze3d.systems.VertexSorter
//#endif

//#if MC >= 1.17
//$$ import com.mojang.blaze3d.systems.RenderSystem
//#endif

/**
 * Sets up the viewport, scissor, projection matrix, and modelview matrix to match MC's gui rendering standard.
 */
fun withDefaultMcGuiRenderingSetup(textureWidth: Int, textureHeight: Int, guiScale: Double, block: () -> Unit) {
    //#if MC < 1.21.6
    val orgViewportState = GlViewportState.active()
    GlViewportState(0, 0, textureWidth, textureHeight).activate()
    val prevScissorState = ScissorState.active()
    ScissorState.DISABLED.activate()
    //#endif

    val projectionMatrix = UMatrixStack()
    // flip OpenGL axes to MC standards
    projectionMatrix.scale(1f, -1f, -1f)
    if (platform.usesReversedZ && !platform.irisReversesZ) {
        projectionMatrix.scale(1f, 1f, -1f)
    }
    // [-1, 1] to [0; 1]
    if (platform.isZZeroToOne) {
        projectionMatrix.translate(-1f, -1f, 0f)
        projectionMatrix.scale(2f, 2f, 1f)
    } else {
        projectionMatrix.translate(-1f, -1f, -1f)
        projectionMatrix.scale(2f, 2f, 2f)
    }
    // [0; 1] to [0; textureSize/maxDepth]
    projectionMatrix.scale(1f / textureWidth, 1f / textureHeight, 1f / 1000f)
    // Apply gui scale
    projectionMatrix.scale(guiScale, guiScale, 1.0)

    //#if MC >= 1.21.6
    //$$ val orgProjectionMatrix = RenderSystem.getProjectionMatrixBuffer()
    //$$ val orgProjectionType = RenderSystem.getProjectionType()
    //$$ val projectionMatrixBuffer = MemoryStack.stackPush().use { stack ->
    //$$     val byteBuf = stack.malloc(16 * Float.SIZE_BYTES)
    //$$     projectionMatrix.peek().model.get(byteBuf)
    //$$     RenderSystem.getDevice().createBuffer({ "ProjMatrix UBO" }, GpuBuffer.USAGE_UNIFORM, byteBuf)
    //$$ }
    //$$ RenderSystem.setProjectionMatrix(projectionMatrixBuffer.slice(), ProjectionType.ORTHOGRAPHIC)
    //#elseif MC >= 1.21.2
    //$$ val orgProjectionMatrix = RenderSystem.getProjectionMatrix()
    //$$ val orgProjectionType = RenderSystem.getProjectionType()
    //$$ RenderSystem.setProjectionMatrix(projectionMatrix.peek().model, ProjectionType.ORTHOGRAPHIC)
    //#elseif MC >= 1.20
    //$$ val orgProjectionMatrix = RenderSystem.getProjectionMatrix()
    //$$ val orgVertexSorter = RenderSystem.getVertexSorting()
    //$$ RenderSystem.setProjectionMatrix(projectionMatrix.peek().model, VertexSorter.BY_Z)
    //#elseif MC >= 1.17
    //$$ val orgProjectionMatrix = RenderSystem.getProjectionMatrix()
    //$$ RenderSystem.setProjectionMatrix(projectionMatrix.peek().model)
    //#else
    GL11.glMatrixMode(GL11.GL_PROJECTION)
    GL11.glPushMatrix()
    projectionMatrix.replaceGlobalState()
    GL11.glMatrixMode(GL11.GL_MODELVIEW)
    //#endif

    UMatrixStack.UNIT.runReplacingGlobalState {
        block()
    }

    //#if MC >= 1.21.6
    //$$ RenderSystem.setProjectionMatrix(orgProjectionMatrix, orgProjectionType)
    //$$ projectionMatrixBuffer.close()
    //#elseif MC >= 1.21.2
    //$$ RenderSystem.setProjectionMatrix(orgProjectionMatrix, orgProjectionType)
    //#elseif MC >= 1.20
    //$$ RenderSystem.setProjectionMatrix(orgProjectionMatrix, orgVertexSorter)
    //#elseif MC >= 1.17
    //$$ RenderSystem.setProjectionMatrix(orgProjectionMatrix)
    //#else
    GL11.glMatrixMode(GL11.GL_PROJECTION)
    GL11.glPopMatrix()
    GL11.glMatrixMode(GL11.GL_MODELVIEW)
    //#endif

    //#if MC < 1.21.6
    prevScissorState.activate()
    orgViewportState.activate()
    //#endif
}

//#if MC < 26.3
class DrawFramebufferContext : AutoCloseable {
    //#if MC < 1.21.5
    private val lazyFrameBuffer = lazy { glGenFramebuffers() }
    private val frameBuffer by lazyFrameBuffer
    //#endif

    override fun close() {
        //#if MC < 1.21.5
        if (lazyFrameBuffer.isInitialized()) {
            glDeleteFramebuffers(frameBuffer)
        }
        //#endif
    }

    fun <T> withDrawFramebuffer(colorView: UGpuTextureView, depthView: UGpuTextureView, block: () -> T): T {
        //#if MC >= 1.21.6
        //$$ val orgColor = RenderSystem.outputColorTextureOverride
        //$$ val orgDepth = RenderSystem.outputDepthTextureOverride
        //$$ overrideRenderTarget(colorView, depthView)
        //$$ try {
        //$$     return block()
        //$$ } finally {
        //$$     RenderSystem.outputColorTextureOverride = orgColor
        //$$     RenderSystem.outputDepthTextureOverride = orgDepth
        //$$ }
        //#elseif MC >= 1.21.5
        //$$ val fb = net.minecraft.client.MinecraftClient.getInstance().framebuffer
        //$$ val orgColor = fb.colorAttachment
        //$$ val orgDepth = fb.depthAttachment
        //$$ overrideRenderTarget(colorView, depthView)
        //$$ try {
        //$$     return block()
        //$$ } finally {
        //$$     fbAttachmentFieldSetters.first.invoke(fb, orgColor)
        //$$     fbAttachmentFieldSetters.second.invoke(fb, orgDepth)
        //$$ }
        //#else
        val prevDrawFrameBufferBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, frameBuffer)
        overrideRenderTarget(colorView, depthView)
        try {
            return block()
        } finally {
            glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0)
            glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0)
            glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFrameBufferBinding)
        }
        //#endif
    }

    fun overrideRenderTarget(colorView: UGpuTextureView, depthView: UGpuTextureView) {
        //#if MC >= 1.21.6
        //$$ RenderSystem.outputColorTextureOverride = UGraphics.getPlatformAdapter().textureView(colorView)
        //$$ RenderSystem.outputDepthTextureOverride = UGraphics.getPlatformAdapter().textureView(depthView)
        //#elseif MC == 1.21.5
        //$$ val fb = net.minecraft.client.MinecraftClient.getInstance().framebuffer
        //$$ fbAttachmentFieldSetters.first.invoke(fb, UGraphics.getPlatformAdapter().texture(colorView.texture))
        //$$ fbAttachmentFieldSetters.second.invoke(fb, UGraphics.getPlatformAdapter().texture(depthView.texture))
        //#else
        val colorGlId = UGraphics.getPlatformAdapter().texture(colorView.texture)
        val depthGlId = UGraphics.getPlatformAdapter().texture(depthView.texture)
        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorGlId, 0)
        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthGlId, 0)
        //#endif
    }

    //#if MC == 1.21.5
    //$$ private val fbAttachmentFieldSetters by lazy {
    //$$     val fb = net.minecraft.client.MinecraftClient.getInstance().framebuffer
    //$$     val lookup = java.lang.invoke.MethodHandles.lookup()
    //$$     val cls = net.minecraft.client.gl.Framebuffer::class.java
    //$$     val gpuTextureFields = cls.declaredFields.filter {
    //$$         it.type == com.mojang.blaze3d.textures.GpuTexture::class.java
    //$$     }
    //$$     val colorField = lookup.unreflectSetter(gpuTextureFields.first {
    //$$         it.isAccessible = true
    //$$         it.get(fb) == fb.colorAttachment
    //$$     })
    //$$     val depthField = lookup.unreflectSetter(gpuTextureFields.first {
    //$$         it.isAccessible = true
    //$$         it.get(fb) == fb.depthAttachment
    //$$     })
    //$$     Pair(colorField, depthField)
    //$$ }
    //#endif
}
//#endif
