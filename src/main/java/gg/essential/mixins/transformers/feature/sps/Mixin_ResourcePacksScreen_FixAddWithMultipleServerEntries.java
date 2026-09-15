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
// 1.8.9 does not show server resource packs in the resource packs screen at all, 1.16+ has fixed the issue
//#if MC==11202
package gg.essential.mixins.transformers.feature.sps;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.ResourcePackListEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.List;

@Mixin(ResourcePackListEntry.class)
public abstract class Mixin_ResourcePacksScreen_FixAddWithMultipleServerEntries {
    @Shadow @Final protected GuiScreenResourcePacks resourcePacksGUI;

    @ModifyExpressionValue(
        method = "mousePressed",
        at = @At(value = "CONSTANT", args = "intValue=1"),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/ResourcePackListEntry;isServerPack()Z"))
    )
    private int findInsertionPointAfterServerPacks(int index) {
        List<ResourcePackListEntry> list = this.resourcePacksGUI.getSelectedResourcePacks();
        while (index < list.size() && list.get(index).isServerPack()) {
            index++;
        }
        return index;
    }
}
//#else
//$$ package gg.essential.mixins.transformers.feature.sps;
//$$ @org.spongepowered.asm.mixin.Mixin(gg.essential.mixins.DummyTarget.class)
//$$ public abstract class Mixin_ResourcePacksScreen_FixAddWithMultipleServerEntries {}
//#endif
