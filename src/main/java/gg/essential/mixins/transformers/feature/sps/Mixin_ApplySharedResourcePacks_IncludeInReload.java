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
// On 1.16+ packs are already included in the reload by default
//#if MC<11600
package gg.essential.mixins.transformers.feature.sps;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.sugar.Local;
import gg.essential.mixins.impl.client.SharedResourcePacksHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Minecraft.class)
public abstract class Mixin_ApplySharedResourcePacks_IncludeInReload {
    @Shadow private ResourcePackRepository mcResourcePackRepository;

    @Inject(method = "refreshResources", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;mcResourcePackRepository:Lnet/minecraft/client/resources/ResourcePackRepository;", ordinal = 0))
    private void addSharedResourcePacks(CallbackInfo ci, @Local List<IResourcePack> result) {
        result.addAll(Lists.reverse(((SharedResourcePacksHolder) this.mcResourcePackRepository).essential$getSharedResourcePacks()));
    }
}
//#else
//$$ package gg.essential.mixins.transformers.feature.sps;
//$$ @org.spongepowered.asm.mixin.Mixin(gg.essential.mixins.DummyTarget.class)
//$$ public abstract class Mixin_ApplySharedResourcePacks_IncludeInReload {}
//#endif
