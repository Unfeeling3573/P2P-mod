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
// 1.8.9 does not show server resource packs in the resource packs screen at all, 1.16+ shows all packs natively
//#if MC==11202
package gg.essential.mixins.transformers.feature.sps;

import com.llamalad7.mixinextras.sugar.Local;
import gg.essential.mixins.impl.client.SharedResourcePacksHolder;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryServer;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiScreenResourcePacks.class)
public abstract class Mixin_ApplySharedResourcePacks_IncludeInResourcePacksScreen {
    @Shadow private List<ResourcePackListEntry> selectedResourcePacks;

    @Inject(method = "initGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/ResourcePackRepository;getResourcePackEntry()Lnet/minecraft/client/resources/ResourcePackRepository$Entry;"))
    private void addSharedResourcePacks(CallbackInfo ci, @Local ResourcePackRepository repository) {
        for (IResourcePack pack : ((SharedResourcePacksHolder) repository).essential$getSharedResourcePacks()) {
            this.selectedResourcePacks.add(new ResourcePackListEntryServer((GuiScreenResourcePacks) (Object) this, pack));
        }
    }
}
//#else
//$$ package gg.essential.mixins.transformers.feature.sps;
//$$ @org.spongepowered.asm.mixin.Mixin(gg.essential.mixins.DummyTarget.class)
//$$ public abstract class Mixin_ApplySharedResourcePacks_IncludeInResourcePacksScreen {}
//#endif
