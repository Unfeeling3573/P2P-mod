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
package gg.essential.gui.elementa.essentialmarkdown.selection

import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.ImmediateElementaExtractor
import gg.essential.elementa.renderer.fillMcScaleXYWH
import gg.essential.gui.elementa.essentialmarkdown.DrawState
import gg.essential.gui.elementa.essentialmarkdown.EssentialMarkdown
import gg.essential.gui.elementa.essentialmarkdown.drawables.Drawable
import gg.essential.universal.UMatrixStack
import java.awt.Color

abstract class Cursor<T : Drawable>(val target: T) {
    protected open val xBase = target.x
    protected open val yBase = target.y
    protected val height = target.height.toDouble()
    protected val width = height / 9.0

    @Deprecated(UMatrixStack.Compat.DEPRECATED, ReplaceWith("draw(matrixStack, state)"))
    @Suppress("DEPRECATION")
    fun draw(state: DrawState) = draw(UMatrixStack(), state)

    @Deprecated("`draw`-style rendering is deprecated. Use `extract` instead.")
    fun draw(matrixStack: UMatrixStack, state: DrawState) {
        extract(ImmediateElementaExtractor(matrixStack), state)
    }

    fun extract(extractor: ElementaExtractor, state: DrawState) {
        if (!EssentialMarkdown.DEBUG)
            return
        extractor.fillMcScaleXYWH(
            xBase + state.xShift,
            yBase + state.yShift,
            width.toFloat(),
            height.toFloat(),
            Color.RED,
        )
    }

    abstract operator fun compareTo(other: Cursor<*>): Int
}
