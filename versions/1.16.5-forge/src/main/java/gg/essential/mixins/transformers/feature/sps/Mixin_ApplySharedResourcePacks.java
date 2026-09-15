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

import com.llamalad7.mixinextras.sugar.Local;
import gg.essential.Essential;
import gg.essential.mixins.impl.client.SharedResourcePacksHolder;
import net.minecraft.client.resources.DownloadingPackFinder;
import net.minecraft.resources.FilePack;
import net.minecraft.resources.IPackNameDecorator;
import net.minecraft.resources.PackCompatibility;
import net.minecraft.resources.ResourcePackInfo;
import net.minecraft.resources.data.PackMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static gg.essential.util.HelpersKt.textTranslatable;

//#if MC>=12005
//$$ import net.minecraft.resource.ResourcePackInfo;
//$$ import net.minecraft.resource.ResourcePackPosition;
//$$ import java.util.Optional;
//#endif

//#if MC>=12002
//$$ import net.minecraft.SharedConstants;
//#endif

//#if MC>=11700
//$$ import net.minecraft.resource.ResourceType;
//#endif

@Mixin(DownloadingPackFinder.class)
public abstract class Mixin_ApplySharedResourcePacks implements SharedResourcePacksHolder {
    @Unique
    private final List<ResourcePackInfo> sharedResourcePacks = new ArrayList<>();

    @Override
    public void essential$setSharedResourcePacks(List<Path> packs) {
        sharedResourcePacks.clear();

        int i = 0;
        for (Path path : packs) {
            File file = path.toFile();
            String name = "shared/" + (i++);

            //#if MC>=12005
            //$$ ResourcePackInfo packInfo = new ResourcePackInfo(
            //$$     name,
            //$$     textTranslatable("resourcePack.server.name"),
            //$$     ResourcePackSource.WORLD,
            //$$     Optional.empty()
            //$$ );
            //#endif

            //#if MC>=12005
            //$$ ResourcePackProfile.PackFactory packFactory = new ZipResourcePack.ZipBackedFactory(path);
            //#elseif MC>=12002
            //$$ ResourcePackProfile.PackFactory packFactory = new ZipResourcePack.ZipBackedFactory(path, false);
            //#elseif MC>=11903
            //$$ ResourcePackProfile.PackFactory packFactory = (name_) -> new ZipResourcePack(name_, file, false);
            //#else
            Supplier<FilePack> packFactory = () -> new FilePack(file);
            //#endif

            //#if MC>=11903
            //$$ ResourcePackProfile.Metadata metadata = ResourcePackProfile.loadMetadata(
                //#if MC>=12005
                //$$ packInfo,
                //#else
                //$$ name,
                //#endif
            //$$     packFactory
                //#if MC>=12002
                //$$ , SharedConstants.getGameVersion().getResourceVersion(ResourceType.CLIENT_RESOURCES)
                //#endif
                //#if MC>=12109
                //$$ , ResourceType.CLIENT_RESOURCES
                //#endif
            //$$ );
            //#else
            PackMetadataSection metadata;
            try (FilePack pack = packFactory.get()) {
                metadata = pack.getMetadata(PackMetadataSection.SERIALIZER);
            } catch (IOException e) {
                Essential.logger.warn("Failed to read shared resource pack {}:", path, e);
                continue;
            }
            //#endif
            if (metadata == null) {
                Essential.logger.warn("Failed to read shared resource pack {}, see error above.", path);
                continue;
            }

            //#if MC>=12005
            //$$ sharedResourcePacks.add(new ResourcePackProfile(
            //$$     packInfo,
            //$$     packFactory,
            //$$     metadata,
            //$$     new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, true)
            //$$ ));
            //#elseif MC>=11903
            //$$ sharedResourcePacks.add(ResourcePackProfile.of(
            //$$     name,
            //$$     textTranslatable("resourcePack.server.name"),
            //$$     true,
            //$$     packFactory,
            //$$     metadata,
                //#if MC<12002
                //$$ ResourceType.CLIENT_RESOURCES,
                //#endif
            //$$     ResourcePackProfile.InsertionPosition.TOP,
            //$$     true,
            //$$     ResourcePackSource.WORLD
            //$$));
            //#else
            sharedResourcePacks.add(new ResourcePackInfo(
                name,
                true,
                packFactory::get,
                textTranslatable("resourcePack.server.name"),
                metadata.getDescription(),
                //#if MC>=11700
                //$$ ResourcePackCompatibility.from(metadata.getPackFormat(), ResourceType.CLIENT_RESOURCES),
                //#else
                PackCompatibility.getCompatibility(metadata.getPackFormat()),
                //#endif
                ResourcePackInfo.Priority.TOP,
                true,
                IPackNameDecorator.WORLD
            ));
            //#endif
        }
    }

    //#if MC>=12004
    //$$ @Inject(method = "method_55526", at = @At("RETURN"))
    //#else
    @Inject(method = "findPacks", at = @At("RETURN"))
    //#endif
    private void findSharedPacks(CallbackInfo ci, @Local(argsOnly = true) Consumer<ResourcePackInfo> consumer) {
        sharedResourcePacks.forEach(consumer);
    }
}
