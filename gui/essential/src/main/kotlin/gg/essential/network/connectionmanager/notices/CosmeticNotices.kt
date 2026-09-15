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
package gg.essential.network.connectionmanager.notices

import com.google.common.collect.MapMaker
import gg.essential.cosmetics.CosmeticId
import gg.essential.cosmetics.isAvailable
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.filter
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.stateUsingSystemTime
import gg.essential.network.connectionmanager.cosmetics.CosmeticsData
import gg.essential.notices.NoticeType

class CosmeticNotices(
    private val noticesManager: NoticesManager,
    private val cosmeticsData: CosmeticsData,
) {
    private val METADATA_KEY = "cosmetic_id"

    private val notices = noticesManager.activeNotices.filter { it.type == NoticeType.NEW_BANNER }

    private val newCosmeticIds: State<Set<CosmeticId>> =
        memo { notices().mapTo(mutableSetOf()) { it.metadata[METADATA_KEY] as String } }

    val hasAnyNewCosmetics: State<Boolean> = stateUsingSystemTime { now ->
        newCosmeticIds().any { id ->
            cosmeticsData.cosmetic(id)()?.isAvailable(now) == true
        }
    }.memo()

    private val newStateCache: MutableMap<CosmeticId, State<Boolean>> = MapMaker().weakValues().makeMap()
    fun getNewState(cosmeticId: String): State<Boolean> {
        return newStateCache.getOrPut(cosmeticId) {
            memo { cosmeticId in newCosmeticIds() }
        }
    }

    fun clearNewState(cosmeticId: String) {
        val notice = notices.getUntracked().find { it.metadata[METADATA_KEY] == cosmeticId }
        if (notice != null) {
            noticesManager.queueDismissNotice(notice.id)
        }
    }
}
