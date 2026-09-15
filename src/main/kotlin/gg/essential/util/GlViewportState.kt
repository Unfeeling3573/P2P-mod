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

import org.lwjgl.opengl.GL11
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class GlViewportState(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun activate() {
        GL11.glViewport(x, y, width, height)
    }

    companion object {
        // Note: LWJGL2 requires a buffer of 16 elements, even if the property we query only has 4
        private val tmpIntBuffer = ByteBuffer.allocateDirect(16 * Int.SIZE_BYTES).order(ByteOrder.nativeOrder()).asIntBuffer()

        fun active(): GlViewportState {
            val viewport = tmpIntBuffer
                //#if MC >= 1.16
                //$$ .also { GL11.glGetIntegerv(GL11.GL_VIEWPORT, it) }
                //#else
                .also { GL11.glGetInteger(GL11.GL_VIEWPORT, it) }
                //#endif
            return GlViewportState(
                x = viewport[0],
                y = viewport[1],
                width = viewport[2],
                height = viewport[3],
            )
        }
    }
}
