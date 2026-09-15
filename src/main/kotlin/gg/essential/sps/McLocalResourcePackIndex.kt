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
package gg.essential.sps

import gg.essential.mixins.ext.client.resource.ResourcePackWithPath
import gg.essential.universal.UMinecraft.getMinecraft
import java.nio.file.Path

class McLocalResourcePackIndex(essentialBaseDirectory: Path): LocalResourcePackIndex(essentialBaseDirectory) {

    override fun updatePaths(updateMinecraftPackRepo: Boolean) {
        val resourcePackRepository = getMinecraft().resourcePackRepository
        if (updateMinecraftPackRepo) {
            resourcePackRepository.updateRepositoryEntriesAll()
        }
        loadedResourcePacks.set(resourcePackRepository.repositoryEntriesAll
            .mapNotNull { pack ->
                //#if FORGELIKE && MC >= 1.16
                //$$ if (pack.isHidden) return@mapNotNull null
                //#endif

                (pack.resourcePack as? ResourcePackWithPath)?.`essential$path`?.let { path ->
                    //#if MC>=11903
                    //$$ val resourcePackName = pack.displayName.string
                    //#else
                    val resourcePackName = pack.resourcePack.packName
                    //#endif
                    path.toAbsolutePath() to resourcePackName
                }
            }.toMap())
    }

}