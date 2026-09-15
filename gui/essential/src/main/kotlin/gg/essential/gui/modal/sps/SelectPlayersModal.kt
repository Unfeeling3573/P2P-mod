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
package gg.essential.gui.modal.sps

import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.dsl.effect
import gg.essential.elementa.dsl.pixel
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.effect.HorizontalScissorEffect
import gg.essential.gui.common.input.UITextInput
import gg.essential.gui.common.input.essentialInput
import gg.essential.gui.common.modal.EssentialModal2
import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.onChange
import gg.essential.gui.elementa.state.v2.toListState
import gg.essential.gui.friends.state.PlayerActivity
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.alignVertical
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.checkboxAlt
import gg.essential.gui.layoutdsl.childBasedMaxHeight
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.fillParent
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.hoverColor
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.image
import gg.essential.gui.layoutdsl.onLeftClick
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.scrollable
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.modals.select.component.playerAvatarWithOnlineIndicator
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalManager
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.UuidNameLookup
import gg.essential.util.scrollGradient
import gg.essential.vigilance.utils.onLeftClick
import java.awt.Color
import java.util.UUID

class SelectPlayersModal(
    manager: ModalManager,
    private val title: String,
    private val players: ListState<UUID>,
    private val continuation: ModalFlow.ModalContinuation<List<UUID>?>,
) : EssentialModal2(manager) {

    private val mainBodyColumnSpacing = 11f
    private val mainBodySearchBarBoxHeight = 18f
    private val activities = platform.createSocialStates().activity
    private val searchInput = UITextInput("Search")

    private val displayedPlayers = memo {
        // Initial value is tilde to ensure loading items are sorted to the bottom of the list.
        players().map { it to UuidNameLookup.nameState(it, "~") }
            .sortedWith(compareBy<Pair<UUID, State<String>>> {
                activities.getActivityState(it.first)() is PlayerActivity.Offline
            }.thenBy {
                it.second().lowercase()
            }).filter { it.second().contains(searchInput.textState(), true) }
    }.toListState()

    private lateinit var scroller: ScrollComponent
    private val selectedPlayers: MutableState<List<UUID>> = mutableStateOf(listOf())

    override fun LayoutScope.layoutTitle() {
        row(Modifier.fillWidth(), Arrangement.SpaceBetween) {
            wrappedText(title, Modifier.shadow(Color.BLACK))
            image(
                EssentialPalette.CANCEL_5X,
                Modifier.alignVertical(Alignment.Start).color(EssentialPalette.TEXT_DARK_DISABLED)
                    .hoverColor(EssentialPalette.TEXT_MID_DARK).shadow(Color.BLACK).hoverScope()
                    .onLeftClick {
                        replaceWith(continuation.resumeImmediately(null))
                    }
            )
        }
    }

    override fun LayoutScope.layoutBody() {
        column(Modifier.fillWidth(), Arrangement.spacedBy(mainBodyColumnSpacing)) {
            box(Modifier.fillWidth().height(mainBodySearchBarBoxHeight)) {
                essentialInput(searchInput, icon = EssentialPalette.SEARCH_7X, iconAndInputPadding = 3f, modifier = Modifier.shadow(Color.BLACK))
            }
            scroller = scrollable(
                Modifier.fillWidth().height(136f),
                vertical = true
            ) {
                column(Modifier.fillWidth().alignVertical(Alignment.Start), Arrangement.spacedBy(2f)) {
                    forEach(displayedPlayers) { friend ->
                        entry(friend.first, friend.second)
                    }
                }
            }.apply {
                removeEffect<ScissorEffect>()
                effect(HorizontalScissorEffect(bottomMargin = 1.pixel))
                scrollGradient(20.pixels)
            }
        }
    }

    override fun LayoutScope.layoutBodyScrollBar() {
        column(Modifier.alignHorizontal(Alignment.Start), Arrangement.spacedBy(mainBodyColumnSpacing)) {
            spacer(height = mainBodySearchBarBoxHeight)
            layoutBodyScrollBarImpl(scroller)
        }
    }

    override fun LayoutScope.layoutButtons() {
        primaryButton("Add Members", Modifier.fillWidth(), disabled = State { selectedPlayers().isEmpty() }) {
            replaceWith(continuation.resume(selectedPlayers.getUntracked()))
        }
    }

    private fun LayoutScope.entry(uuid: UUID, name: State<String>) {
        val isSelected = mutableStateOf(uuid in selectedPlayers.getUntracked()).apply {
            onChange(this@SelectPlayersModal) { selected ->
                selectedPlayers.set {
                    if (selected) it + uuid else it - uuid
                }
            }
        }
        box(Modifier.height(19f).fillWidth().color(EssentialPalette.BUTTON_HIGHLIGHT).hoverColor(EssentialPalette.GRAY_OUTLINE_BUTTON_OUTLINE).shadow(Color.BLACK).hoverScope()) {
            box(Modifier.fillParent(padding = 1f).color(EssentialPalette.COMPONENT_BACKGROUND_HIGHLIGHT).hoverColor(EssentialPalette.BUTTON_HIGHLIGHT)) {
                row(Modifier.fillWidth(padding = 4f).childBasedMaxHeight(), Arrangement.SpaceBetween) {
                    row(Modifier.childBasedMaxHeight(1f), Arrangement.spacedBy(5f)) {
                        playerAvatarWithOnlineIndicator(uuid, activities)
                        text(name, Modifier.shadow(Color.BLACK).alignVertical(Alignment.End))
                    }
                    checkboxAlt(isSelected, Modifier.alignVertical(Alignment.End))
                }
            }
        }.onLeftClick { event ->
            USound.playButtonPress()
            event.stopPropagation()
            isSelected.set { !it }
        }
    }

}

suspend fun ModalFlow.selectPlayersModal(title: String, players: ListState<UUID>): List<UUID>? {
    return awaitModal { continuation ->
        SelectPlayersModal(modalManager, title, players, continuation)
    }
}