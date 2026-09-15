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
package gg.essential.gui.common.constraints

import gg.essential.elementa.UIComponent
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UVertexConsumer
import org.intellij.lang.annotations.Language
import java.awt.Color
import kotlin.math.roundToInt

fun extractColorPicker(extractor: ElementaExtractor, component: UIComponent, hue: Float) {
    drawHsvRect(
        extractor,
        component,
        Hsva(hue, 0f, 1f, 1f),
        Hsva(hue, 1f, 1f, 1f),
        Hsva(hue, 0f, 0f, 1f),
        Hsva(hue, 1f, 0f, 1f),
    )
}

fun extractAlpha(extractor: ElementaExtractor, component: UIComponent, color: Color) {
    val hsv = color.toHsva()
    val l = hsv.copy(alpha = 0f)
    val r = hsv.copy(alpha = 1f)
    drawHsvRect(extractor, component, l, r, l, r)
}

fun extractHueLine(extractor: ElementaExtractor, component: UIComponent) {
    val t = Hsva(0f, 1f, 0.9f, 1f)
    val b = Hsva(1f, 1f, 0.9f, 1f)
    drawHsvRect(extractor, component, t, t, b, b)
}

// Note: All in range [0..1], including hue
private data class Hsva(val hue: Float, val saturation: Float, val value: Float, val alpha: Float)
private fun Color.toHsva(): Hsva =
    Color.RGBtoHSB(red, green, blue, null)
        .let { Hsva(it[0], it[1], it[2], alpha / 255f) }

private fun drawHsvRect(extractor: ElementaExtractor, component: UIComponent, tl: Hsva, tr: Hsva, bl: Hsva, br: Hsva) {
    val left = component.getLeft().toDouble()
    val top = component.getTop().toDouble()
    val right = component.getRight().toDouble()
    val bottom = component.getBottom().toDouble()

    val l = (left * extractor.guiScale).roundToInt()
    val t = (top * extractor.guiScale).roundToInt()
    val r = (right * extractor.guiScale).roundToInt()
    val b = (bottom * extractor.guiScale).roundToInt()

    extractor.custom(l, t, r, b, PIPELINE, emptyList(), 4) { builder, _, _ ->
        drawVertex(builder, l, t, tl)
        drawVertex(builder, l, b, bl)
        drawVertex(builder, r, b, br)
        drawVertex(builder, r, t, tr)
    }
}

private fun drawVertex(graphics: UVertexConsumer, x: Int, y: Int, color: Hsva) {
    graphics
        .pos(UMatrixStack.UNIT, x.toDouble(), y.toDouble(), 0.0)
        .color(
            color.hue,
            color.saturation,
            color.value,
            color.alpha,
        )
        .endVertex()
}

private val PIPELINE = run {
    @Language("GLSL")
    val vertSource = """
        #version 110
        
        void main() {
            gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            gl_FrontColor = gl_Color;
        }
    """.trimIndent()
    @Language("GLSL")
    val fragSource = """
        #version 110
        
        // From https://stackoverflow.com/a/17897228 (Licence: WTFPL)
        // All components are in the range [0…1], including hue.
        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }
        
        void main() {
            gl_FragColor = vec4(hsv2rgb(gl_Color.rgb), gl_Color.a);
        }
    """.trimIndent()
    URenderPipeline.builderWithLegacyShader(
        "essential:color_picker",
        UGraphics.DrawMode.QUADS,
        UGraphics.CommonVertexFormats.POSITION_COLOR,
        vertSource,
        fragSource,
    ).apply {
        blendState = BlendState.ALPHA
    }.build()
}
