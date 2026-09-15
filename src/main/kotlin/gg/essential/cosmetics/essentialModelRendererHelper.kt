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
package gg.essential.cosmetics

import gg.essential.gui.common.CosmeticHoverOutlineHook
import gg.essential.model.backend.RenderBackend
import gg.essential.model.backend.minecraft.MinecraftRenderBackend

//#if MC>=11600 && MC < 26.2
//$$ import net.minecraft.client.renderer.IRenderTypeBuffer
//#endif

fun CosmeticHoverOutlineHook.renderCosmeticsForOutlines(queues: Map<CosmeticId, MinecraftRenderBackend.CommandQueue>) {
    //#if MC >= 1.16 && MC < 26.2
    //$$ net.minecraft.client.Minecraft.getInstance().renderTypeBuffers.bufferSource.finish()
    //#endif

    for ((cosmetic, queue) in queues) {
        beginOutlineRender(cosmetic)
        queue.renderImmediate()
        endOutlineRender(cosmetic)
    }
}

fun RenderBackend.VertexConsumerProvider.flush() {
    //#if MC>=11600 && MC < 26.2
    //$$ ((this as MinecraftRenderBackend.VertexConsumerProvider).provider as? IRenderTypeBuffer.Impl)?.finish()
    //#endif
}
