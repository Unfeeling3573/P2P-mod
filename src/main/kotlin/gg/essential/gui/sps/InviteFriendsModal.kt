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

import gg.essential.Essential
import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.event.essential.InitMainMenuEvent
import gg.essential.event.render.RenderTickEvent
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.*
import gg.essential.gui.common.modal.Modal
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.modals.select.SelectModal
import gg.essential.gui.modals.select.offlinePlayers
import gg.essential.gui.modals.select.onlinePlayers
import gg.essential.gui.modals.select.selectModal
import gg.essential.gui.notification.sendOutgoingSpsInviteNotification
import gg.essential.gui.overlay.ModalManager
import gg.essential.network.connectionmanager.sps.SPSSessionSource
import gg.essential.universal.UMinecraft.getMinecraft
import gg.essential.universal.USound
import gg.essential.util.*
import gg.essential.vigilance.utils.onLeftClick
import me.kbrewster.eventbus.Subscribe
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.world.storage.WorldSummary
import java.util.*

//#if MC>11202
//$$ import net.minecraft.world.World
//$$ import net.minecraft.world.storage.IServerWorldInfo
//#endif

//#if MC>=11900
//$$ import net.minecraft.client.gui.screen.TitleScreen
//#endif

object InviteFriendsModal {

    fun showInviteModal(
        modalManager: ModalManager,
        initialInvites: Set<UUID>? = null,
        justStarted: Boolean = false,
        worldSummary: WorldSummary? = null,
        source: SPSSessionSource,
        onComplete: () -> Unit,
    ): Modal {
        val connectionManager = Essential.getInstance().connectionManager
        val spsManager = connectionManager.spsManager
        val currentServerData = getMinecraft().currentServerData

        val invites = initialInvites ?: if (currentServerData != null) {
            connectionManager.socialManager.getInvitesOnServer(currentServerData.serverIP)
        } else {
            spsManager.invitedUsers
        }

        val onModalCancelled: Modal.(Boolean) -> Unit = { pressedBackButton ->
            if (pressedBackButton) {
                throw AssertionError("Back button was pressed but should not be available")
            }
        }

        return createSelectFriendsModal(modalManager, invites, justStarted, worldSummary, onModalCancelled, onComplete)
    }

    fun createSelectFriendsModal(
        modalManager: ModalManager,
        invites: Set<UUID>,
        justStarted: Boolean,
        worldSummary: WorldSummary? = null,
        onModalCancelled: Modal.(Boolean) -> Unit = {},
        onComplete: () -> Unit = {}
    ): SelectModal<UUID> {
        val connectionManager = Essential.getInstance().connectionManager
        val reInviteEnabledStateList = mutableMapOf<UUID, MutableState<Boolean>>()

        val updateInvites: (newInvites: Set<UUID>) -> Unit = { newInvites ->
            val currentServerData = getMinecraft().currentServerData

            if (MinecraftUtils.isHostingSPS()) {
                connectionManager.spsManager.updateInvitedUsers(newInvites)
            } else if (currentServerData != null) {
                connectionManager.socialManager.setInvitedFriendsOnServer(currentServerData.serverIP, newInvites)
            }
        }

        fun getReInviteEnabledState(uuid: UUID): MutableState<Boolean> {
            return reInviteEnabledStateList.getOrPut(uuid) { mutableStateOf(true) }
        }

        fun startReInviteTimer(uIComponent: UIComponent, uuid: UUID) {
            val reInviteEnabledState = getReInviteEnabledState(uuid)

            reInviteEnabledState.set(false)

            uIComponent.delay(5000) {
                reInviteEnabledState.set(true)
            }
        }

        /**
         * Re-invites a player returns true if successful
         */
        fun reInvite(selectModal: SelectModal<UUID>, uuid: UUID): Boolean {
            val reInviteEnabledState = getReInviteEnabledState(uuid)

            if (!reInviteEnabledState.get()) {
                return false
            }

            updateInvites(selectModal.selectedIdentifiers - uuid)
            updateInvites(selectModal.selectedIdentifiers + uuid)

            sendInviteNotification(uuid)
            return true
        }

        val isServer = getMinecraft().currentServerData != null
        val title = if (isServer) "Invite friends to server" else "Invite friends to world"
        val modalSimpleName = "InviteFriends" +
                if (isServer) "ToServer" else "ToWorld"
        return selectModal(modalManager, title, modalSimpleName) {
            fun LayoutScope.customPlayerEntry(selected: MutableState<Boolean>, uuid: UUID) {
                val onlineState = connectionManager.spsManager.getOnlineState(uuid)
                val reInviteEnabled = getReInviteEnabledState(uuid)
                val reInviteVisible = memo {
                    selected() && worldSummary == null && !onlineState()
                }

                fun LayoutScope.reinviteButton(modifier: Modifier = Modifier) {
                    iconButton(
                        modifier = modifier,
                        icon = stateOf(EssentialPalette.REINVITE_5X),
                        color = { _ ->
                            if (reInviteEnabled()) EssentialPalette.TEXT else EssentialPalette.TEXT_DISABLED
                        },
                        backgroundColor = { hovered ->
                            when {
                                !reInviteEnabled() -> EssentialPalette.COMPONENT_BACKGROUND
                                hovered() -> EssentialPalette.TEXT_DISABLED
                                else -> EssentialPalette.BUTTON_HIGHLIGHT
                            }
                        },
                        tooltip = stateOf("Re-invite"),
                    ).apply {
                        rebindEnabled(reInviteEnabled.toV1(this))
                    }.onLeftClick { event ->
                        if (reInvite(findParentOfType(), uuid)) {
                            USound.playButtonPress()
                            event.stopPropagation()
                            startReInviteTimer(this, uuid)
                        }
                    }
                }

                box(Modifier.fillParent()) {
                    row(Modifier.fillParent(padding = 3f)) {
                        playerEntry(selected, uuid)

                        row(Arrangement.spacedBy(3f)) {
                            if_(reInviteVisible) {
                                reinviteButton(Modifier.width(9f))
                            }

                            defaultAddRemoveButton(selected)
                        }
                    }
                }.onLeftClick { event ->
                    if (reInviteVisible.get()) {
                        if (reInvite(findParentOfType(), uuid)) {
                            USound.playButtonPress()
                            event.stopPropagation()
                            startReInviteTimer(this, uuid)
                        }
                    } else {
                        USound.playButtonPress()
                        event.stopPropagation()
                        selected.set { !it }
                    }
                }
            }

            onlinePlayers(LayoutScope::customPlayerEntry)
            offlinePlayers(LayoutScope::customPlayerEntry)

            // Set the initially selected user to the initial invites / invited users
            setInitiallySelected(*invites.toTypedArray())

            modalSettings {
                primaryButtonText = if (worldSummary != null) "Host World" else "Done"

                if (justStarted || worldSummary != null) {
                    cancelButtonText = "Back"
                    onCancel { buttonPressed ->
                        onModalCancelled(this, buttonPressed)
                    }
                } else {
                    hideCancelButton()
                }
            }

            selectTooltip = "Invite"
            deselectTooltip = "Cancel"

            requiresSelection = false
            requiresButtonPress = false
        }.onPrimaryAction { newInvites ->
            if (worldSummary != null) {
                //#if MC>=12004
                //$$ getMinecraft().createIntegratedServerLoader().start(worldSummary.name) { GuiUtil.openScreen { TitleScreen() } }
                //#elseif MC>=11900
                //$$ getMinecraft().createIntegratedServerLoader().start(TitleScreen(), worldSummary.name)
                //#elseif MC>=11602
                //$$ getMinecraft().loadWorld(worldSummary.fileName)
                //#else
                getMinecraft().launchIntegratedServer(worldSummary.fileName, worldSummary.displayName, null)
                //#endif

                // We need an SPS session running before updating invites and invoking callback, so we do that in the SinglePlayerJoinEvent
                Essential.EVENT_BUS.register(PostSingleplayerOpenHandler(newInvites, onComplete))
            } else {
                if (MinecraftUtils.isHostingSPS()) {
                    // Invite existing invited players that weren't already re-invited
                    connectionManager.spsManager.updateInvitedUsers(newInvites)
                }
                onComplete()
            }
        }.onSelection { identifier, selected ->
            if (worldSummary != null) {
                return@onSelection
            }

            updateInvites(selectedIdentifiers)

            if (selected) {
                sendInviteNotification(identifier)
                startReInviteTimer(this, identifier)
            }
        }
    }

    fun sendInviteNotification(uuid: UUID) {
        UUIDUtil.getName(uuid).thenAcceptOnMainThread { sendOutgoingSpsInviteNotification(it) }
    }

    class PostSingleplayerOpenHandler(private val currentInvites: Set<UUID>, private val callback: () -> Unit) {
        @Subscribe
        private fun checkIfWorldHasLoaded(event: RenderTickEvent) {
            //#if MC<11600
            @Suppress("SENSELESS_COMPARISON") // Forge applies an inappropriate NonNullByDefault
            //#endif
            if (getMinecraft().world == null) return // not yet

            Essential.EVENT_BUS.unregister(this)

            val spsManager = Essential.getInstance().connectionManager.spsManager
            spsManager.startLocalSession(SPSSessionSource.MAIN_MENU)
            spsManager.updateInvitedUsers(currentInvites)

            callback()
        }

        @Subscribe
        private fun onInitMainMenuEvent(event: InitMainMenuEvent) {
            if (GuiUtil.openedScreen().isMainMenu) {
                // If a world doesn't load properly, such as incompatible version, we can end up back on the main menu
                // with the integrated server not reset, so we reset it manually
                getMinecraft().integratedServer?.let { integratedServer ->
                    if (getMinecraft().isIntegratedServerRunning && integratedServer.isServerStopped) {
                        Essential.EVENT_BUS.unregister(this)
                        //#if MC>=12106
                        //$$ getMinecraft().disconnectWithProgressScreen()
                        //#elseif MC>=11602
                        //$$ getMinecraft().unloadWorld()
                        //#else
                        getMinecraft().loadWorld(null as WorldClient?)
                        //#endif
                    }
                }
            }
        }
    }
}
