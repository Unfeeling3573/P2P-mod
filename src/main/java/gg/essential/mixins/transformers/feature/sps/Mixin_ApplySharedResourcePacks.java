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
package gg.essential.mixins.transformers.feature.sps;

import gg.essential.Essential;
import gg.essential.mixins.impl.client.SharedResourcePacksHolder;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mixin(ResourcePackRepository.class)
public abstract class Mixin_ApplySharedResourcePacks implements SharedResourcePacksHolder {
    @Unique
    private final List<IResourcePack> sharedResourcePacks = new ArrayList<>();

    @Override
    public void essential$setSharedResourcePacks(List<Path> packs) {
        sharedResourcePacks.clear();

        for (Path path : packs) {
            File file = path.toFile();
            // Note: Cannot just use the IResourcePack constructor because `updateResourcePack` closes the pack
            try {
                Constructor<ResourcePackRepository.Entry> constructor = ResourcePackRepository.Entry.class
                    .getDeclaredConstructor(ResourcePackRepository.class, File.class);
                constructor.setAccessible(true);
                ResourcePackRepository.Entry entry = constructor.newInstance(this, file);
                entry.updateResourcePack();
            } catch (IOException e) {
                Essential.logger.warn("Failed to read shared resource pack {}:", path, e);
                continue;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }

            sharedResourcePacks.add(getResourcePack(file));
        }
    }

    @Override
    public List<IResourcePack> essential$getSharedResourcePacks() {
        return sharedResourcePacks;
    }

    //#if MC>=11200
    @Shadow protected abstract IResourcePack getResourcePack(File file);
    //#else
    //$$ @Unique private IResourcePack getResourcePack(File file) {
    //$$     return new net.minecraft.client.resources.FileResourcePack(file);
    //$$ }
    //#endif
}
