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
package gg.essential.gui.sps

import gg.essential.gui.elementa.state.v2.MutableListState
import gg.essential.gui.elementa.state.v2.add
import gg.essential.gui.elementa.state.v2.mutableListStateOf
import gg.essential.gui.modal.sps.FirewallBlockingModal
import gg.essential.gui.modal.sps.WorldHostingModal
import gg.essential.gui.modal.sps.selectWorldModal
import gg.essential.gui.modals.ensurePrerequisites
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.error
import gg.essential.gui.notification.warning
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.network.connectionmanager.features.Feature
import gg.essential.sps.WorldManager
import gg.essential.util.FirewallUtil
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.ServerType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.collections.plus

fun launchInviteOrHostModalFlow() {
    launchModalFlow(platform.createModalManager()) {
        inviteOrHostModalFlow()
    }
}

suspend fun ModalFlow.inviteOrHostModalFlow(): Nothing {
    if (platform.isInMainMenu()) {
        selectWorldModalFlow()
        throw CancellationException()
    }
    when (ServerType.current()) {
        is ServerType.Singleplayer -> {
            while (FirewallUtil.isFirewallBlocking()) {
                awaitModal { continuation ->
                    FirewallBlockingModal(modalManager, null, tryAgainAction = { replaceWith(continuation.resumeImmediately(Unit)) })
                }
            }
            worldHostingModal()
        }
        is ServerType.Multiplayer -> {
            ensurePrerequisites(features = listOf(Feature.SOCIAL), rules = false)
            awaitModal { platform.createServerInviteModal(modalManager) }
        }
        is ServerType.SPS.Host -> worldHostingModal()
        is ServerType.SPS.Guest -> Notifications.warning("Only hosts can send invites", "")
        is ServerType.Realms, null -> Notifications.error("Can't invite to this world", "")
    }
    throw CancellationException()
}

private suspend fun ModalFlow.worldHostingModal() {
    val worldsManager = platform.worldsManager
    val currentWorld = worldsManager.integratedServerWorld.getUntracked() ?: return
    ensurePrerequisites(features = listOf(Feature.WORLD_HOSTING, Feature.SOCIAL), rules = false)
    awaitModal<Nothing> { WorldHostingModal(modalManager, worldsManager, currentWorld) }
}

private suspend fun ModalFlow.selectWorldModalFlow() {
    ensurePrerequisites(features = listOf(Feature.WORLD_HOSTING, Feature.SOCIAL), rules = false)
    val worldsManager = platform.worldsManager
    val localWorlds: MutableListState<WorldManager> = mutableListStateOf()
    modalManager.coroutineScope.launch {
        worldsManager.getLocalWorlds().collect {
            localWorlds.add(it)
        }
    }
    val worlds = localWorlds
    val selectedWorld = selectWorldModal("Select a world to host", worlds) ?: return

    awaitModal<Nothing> { WorldHostingModal(modalManager, worldsManager, selectedWorld) }
}