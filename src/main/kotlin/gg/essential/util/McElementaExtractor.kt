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

import gg.essential.Essential
import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.ImmediateElementaExtractor
import gg.essential.elementa.renderer.PostProcessingRenderer
import gg.essential.elementa.renderer.SpecialRenderer
import gg.essential.event.render.RenderTickEvent
import gg.essential.universal.UGraphics
import gg.essential.universal.UResolution
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.vertex.UVertexConsumer
import java.awt.Color

//#if MC >= 1.21.11
//$$ import com.mojang.blaze3d.systems.RenderSystem
//#endif

//#if MC >= 1.21.5
//$$ import com.mojang.blaze3d.textures.AddressMode
//$$ import com.mojang.blaze3d.textures.FilterMode
//#elseif MC >= 1.21.2
//$$ import org.lwjgl.opengl.GL11
//$$ import org.lwjgl.opengl.GL13
//#endif

//#if MC >= 1.21.2
//$$ import net.minecraft.client.MinecraftClient
//#if MC >= 1.21.6
//$$ import net.minecraft.client.gl.RenderPipelines
//#else
//$$ import net.minecraft.client.render.RenderLayer
//#endif
//$$ import net.minecraft.client.texture.AbstractTexture
//$$ import net.minecraft.util.Identifier
//$$ import kotlin.math.roundToInt
//#endif

class McElementaExtractor @JvmOverloads constructor(
    //#if MC >= 1.20
    //$$ private val
    //#endif
    uDrawContext: UDrawContext,
    override val guiScale: Float = UResolution.scaleFactor.toFloat(),
) : ElementaExtractor, AutoCloseable {
    override val version: ElementaVersion
        get() = ElementaVersion.V11

    //#if MC < 1.21.6
    private val immediate = ImmediateElementaExtractor(uDrawContext.matrixStack)
    //#endif

    // Textures submitted to the DrawContext need to be kept alive until GuiRenderer has consumed them
    private val temporaryTextures = mutableListOf<UGpuTextureView>()
    private val renderTickEventListener: (RenderTickEvent) -> Unit = eventListener@{ event ->
        if (!event.isPre) return@eventListener
        Essential.EVENT_BUS.unregister(renderTickEventListener)
        check(closed) { "McElementaExtractor was not closed properly" }

        for (textureView in temporaryTextures) {
            textureView.close()
            textureView.texture.close()
        }
        temporaryTextures.clear()
    }

    init {
        Essential.EVENT_BUS.register(renderTickEventListener)

        // MC's DrawContext only accepts Int in MC space
        // ElementaRenderer accepts real pixels (so fractional MC pixels) though
        //#if MC >= 1.21.6
        //$$ uDrawContext.mc.matrices.pushMatrix()
        //$$ uDrawContext.mc.matrices.scale(1/guiScale, 1/guiScale)
        //#elseif MC >= 1.20
        //$$ uDrawContext.mc.matrices.push()
        //$$ uDrawContext.mc.matrices.scale(1/guiScale, 1/guiScale, 1f)
        //#endif
    }

    private var closed = false
    override fun close() {
        check(!closed) { "Already closed." }
        closed = true

        //#if MC >= 1.21.6
        //$$ uDrawContext.mc.matrices.popMatrix()
        //#elseif MC >= 1.20
        //$$ uDrawContext.mc.matrices.pop()
        //#endif

        if (temporaryTextures.isEmpty()) {
            Essential.EVENT_BUS.unregister(renderTickEventListener)
        }
    }

    // Not yet implemented because restoring isn't trivial and we don't need it yet
    override fun pushScissor(x1: Int, y1: Int, x2: Int, y2: Int) = TODO("Not yet implemented")
    override fun pushScissorRaw(x1: Int, y1: Int, x2: Int, y2: Int) = TODO("Not yet implemented")
    override fun popScissor() = TODO("Not yet implemented")
    override fun isVisible(x1: Int, y1: Int, x2: Int, y2: Int) = true

    override fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Color) {
        //#if MC >= 1.20
        //$$ uDrawContext.mc.fill(x1, y1, x2, y2, color.rgb)
        //#else
        immediate.fill(x1, y1, x2, y2, color)
        //#endif
    }

    override fun blit(
        x1: Int, y1: Int, x2: Int, y2: Int,
        u1: Float, v1: Float, u2: Float, v2: Float,
        texture: UGpuTextureView, sampler: UGpuSampler,
        textureContentImmutable: Boolean, premultipliedAlpha: Boolean,
        color: Color,
    ) {
        if (sampler != UGpuSampler.NEAREST) throw UnsupportedOperationException("Sampler is currently hard-coded")
        //#if MC >= 1.21.6
        //$$ val textureManager = MinecraftClient.getInstance().textureManager
        //$$ val identifier = Identifier.of("essential", "__tmp_texture__")
        //$$ textureManager.registerTexture(identifier, object : AbstractTexture() {
        //$$     init {
        //$$         this.glTextureView = UGraphics.getPlatformAdapter().textureView(texture)
        //$$
                //#if MC >= 1.21.11
                //$$ this.sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
                //#else
                //$$ this.glTextureView!!.texture().setTextureFilter(FilterMode.NEAREST, false)
                //$$ this.glTextureView!!.texture().setAddressMode(AddressMode.CLAMP_TO_EDGE)
                //#endif
        //$$     }
        //$$     override fun close() {} // we don't want the later `destroyTexture` to close our texture
        //$$ })
        //$$ // drawTexture accepts uWidth/vHeight as integers only, so we pass a huge texture size to it to attempt to
        //$$ // accurately represent our [0; 1] u/v coordinates as fractions of that number.
        //$$ // MC does its division using floats, those have 23-bits of precision, so we'll use a size close to that
        //$$ val textureSize = 1 shl 22
        //$$ uDrawContext.mc.drawTexture(
        //$$     if (premultipliedAlpha) RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA else RenderPipelines.GUI_TEXTURED,
        //$$     identifier,
        //$$     x1, y1,
        //$$     u1 * textureSize, v1 * textureSize,
        //$$     x2 - x1, y2 - y1,
        //$$     ((u2 - u1) * textureSize).roundToInt(), ((v2 - v1) * textureSize).roundToInt(),
        //$$     textureSize, textureSize,
        //$$     color.rgb,
        //$$ )
        //$$ textureManager.destroyTexture(identifier)
        //#elseif MC >= 1.20
        //$$ // Versions prior to 1.21.2 did not accept a `color`
        //$$ // Versions prior to 1.21.6 require a custom RenderLayer (default one has broken depth and doesn't support pre-multiplied alpha)
        //$$ // So we'll continue to use our renderer for these
        //$$ uDrawContext.mc.draw() // flush buffer
        //$$ immediate.blit(x1, y1, x2, y2, u1, v1, u2, v2, texture, sampler, textureContentImmutable, premultipliedAlpha, color)
        //#else
        immediate.blit(x1, y1, x2, y2, u1, v1, u2, v2, texture, sampler, textureContentImmutable, premultipliedAlpha, color)
        //#endif
    }

    // Not yet implemented because non-trivial and we don't need it yet
    override fun custom(
        x1: Int, y1: Int, x2: Int, y2: Int,
        pipeline: URenderPipeline,
        textures: List<Pair<UGpuTextureView, UGpuSampler>>,
        vertices: Int,
        build: (UVertexConsumer, Int, Int) -> Unit,
    ) = TODO("Not yet implemented")

    // FIXME rendering needs to be delayed until on the render thread once that's a thing
    override fun <T> special(
        x1: Int, y1: Int, x2: Int, y2: Int,
        factory: SpecialRenderer.Factory<T>,
        args: T
    ) {
        val w = x2 - x1
        val h = y2 - y1
        if (w <= 0 || h <= 0) return

        //#if MC >= 1.20 && MC < 1.21.6
        //$$ uDrawContext.mc.draw()
        //#endif

        val device = UGraphics.getDevice()
        val texture = device.createTexture(
            null,
            UGpuTexture.Usage.COPY_DST + UGpuTexture.Usage.TEXTURE_BINDING + UGpuTexture.Usage.RENDER_ATTACHMENT,
            UGpuFormat.DEFAULT_RGBA,
            w,
            h,
            1,
        )
        device.clearColor(texture, 0f, 0f, 0f, 0f)
        val textureView = device.createTextureView(texture, 0, 1)
        temporaryTextures.add(textureView)

        factory.create().use { renderer ->
            renderer.render(textureView, listOf(
                SpecialRenderer.Instance(
                    0, 0, w, h,
                    0, 0, w, h,
                    args,
                )
            ))
        }

        blit(x1, y1, x2, y2, 0f, 1f, 1f, 0f, textureView, UGpuSampler.NEAREST, false, true, Color.WHITE)
    }

    // Not implemented, would have to spin up a full ElementaRenderer for this one and we don't need it yet
    override fun <T> pushPostProcessing(factory: PostProcessingRenderer.Factory<T>, args: T) = throw UnsupportedOperationException()
    override fun popPostProcessing(factory: PostProcessingRenderer.Factory<*>) = throw UnsupportedOperationException()
}
