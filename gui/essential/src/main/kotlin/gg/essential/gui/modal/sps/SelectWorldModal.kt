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
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateUsingSystemTime
import gg.essential.gui.elementa.state.v2.toListState
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.alignVertical
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.fillParent
import gg.essential.gui.layoutdsl.fillRemainingWidth
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
import gg.essential.gui.layoutdsl.widthAspect
import gg.essential.gui.layoutdsl.withHoverState
import gg.essential.gui.layoutdsl.worldIcon
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalManager
import gg.essential.sps.WorldManager
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.formatTimeDifference
import gg.essential.util.scrollGradient
import gg.essential.vigilance.utils.onLeftClick
import kotlinx.coroutines.launch
import java.awt.Color

class SelectWorldModal(
    manager: ModalManager,
    private val title: String,
    private val worlds: ListState<WorldManager>,
    private val continuation: ModalFlow.ModalContinuation<WorldManager?>,
) : EssentialModal2(manager) {

    private val mainBodyColumnSpacing = 11f
    private val mainBodySearchBarBoxHeight = 18f

    private val displayedWorlds = State {
        worlds()
            .sortedByDescending { it.localLastPlayed() }
            .filter { it.name().contains(searchInput.textState(), true) }
    }.toListState()

    private lateinit var scroller: ScrollComponent
    private val selectedWorld: MutableState<WorldManager?> = mutableStateOf(null)
    private val searchInput = UITextInput("Search")

    private val entryOuterColor = EssentialPalette.BUTTON_HIGHLIGHT
    private val entryOuterHoverColor = EssentialPalette.GRAY_OUTLINE_BUTTON_OUTLINE
    private val entryInnerColor = EssentialPalette.COMPONENT_BACKGROUND_HIGHLIGHT
    private val entryInnerHoverColor = EssentialPalette.BUTTON_HIGHLIGHT

    override fun LayoutScope.layoutTitle() {
        row(Modifier.fillWidth(), Arrangement.SpaceBetween) {
            text(title, Modifier.shadow(Color.BLACK))
            image(
                EssentialPalette.CANCEL_5X,
                Modifier.color(EssentialPalette.TEXT_DARK_DISABLED).hoverColor(EssentialPalette.TEXT_MID_DARK).shadow(Color.BLACK).hoverScope()
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
                    createWorldButton()
                    forEach(displayedWorlds) { world ->
                        entry(world)
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
        primaryButton("Next", Modifier.fillWidth(), disabled = State { selectedWorld() == null }) {
            replaceWith(continuation.resume(selectedWorld.getUntracked()))
        }
    }

    private fun LayoutScope.entry(world: WorldManager) {
        val outerColor = Modifier.withHoverState { hover ->
            Modifier.color {
                when {
                    selectedWorld() == world -> EssentialPalette.TEXT_HIGHLIGHT
                    hover() -> entryOuterHoverColor
                    else -> entryOuterColor
                }
            }
        }
        val innerColor = Modifier.withHoverState { hover ->
            Modifier.color {
                when {
                    selectedWorld() == world -> EssentialPalette.TEXT_DARK_DISABLED
                    hover() -> entryInnerHoverColor
                    else -> entryInnerColor
                }
            }
        }
        box(Modifier.height(31f).fillWidth().shadow(Color.BLACK).hoverScope().then(outerColor)) {
            box(Modifier.fillParent(padding = 1f).then(innerColor)) {
                row(Modifier.fillWidth(padding = 5f), Arrangement.spacedBy(7f)) {
                    worldIcon(coroutineScope, world, Modifier.height(19f).widthAspect(1f).shadow(EssentialPalette.BLACK_SHADOW))
                    column(Modifier.fillRemainingWidth(), Arrangement.spacedBy(5f), Alignment.Start) {
                        text(world.name, Modifier.shadow(Color.BLACK), truncateIfTooSmall = true)
                        val lastPlayed = world.localLastPlayed
                        val lastPlayedString = stateUsingSystemTime { now ->
                            lastPlayed()?.let { lastTime -> "${formatTimeDifference(lastTime, now).replaceFirstChar { it.titlecase() }} ago" } ?: "Loading.."
                        }
                        text(
                            { "${world.gameDisplayVersion()} - ${lastPlayedString()}" },
                            Modifier.color(EssentialPalette.TEXT).shadow(Color.BLACK),
                            truncateIfTooSmall = true
                        )
                    }
                }
            }
        }.onLeftClick { event ->
            USound.playButtonPress()
            event.stopPropagation()
            if (event.clickCount > 1 && selectedWorld.getUntracked() == world) {
                coroutineScope.launch {
                    replaceWith(continuation.resume(world))
                }
                return@onLeftClick
            }
            selectedWorld.set(world)
        }
    }

    private fun LayoutScope.createWorldButton() {
        box(Modifier.height(24f).fillWidth().color(entryOuterColor).hoverColor(entryOuterHoverColor).shadow(Color.BLACK).hoverScope()) {
            box(Modifier.fillParent(padding = 1f).color(entryInnerColor).hoverColor(entryInnerHoverColor)) {
                text("Create new world", Modifier.shadow(EssentialPalette.COMPONENT_BACKGROUND))
            }
        }.onLeftClick { event ->
            USound.playButtonPress()
            event.stopPropagation()
            // `CreateWorldScreen` does some preparation when it's first opened, causing the game thread to
            // freeze. This means that the modal will be shown until this process is finished.
            // To remedy this, we can just close the modal before showing the create world screen.
            close()
            platform.openCreateWorldScreen()
        }
    }

}

suspend fun ModalFlow.selectWorldModal(title: String, worlds: ListState<WorldManager>): WorldManager? {
    return awaitModal { continuation ->
        SelectWorldModal(modalManager, title, worlds, continuation)
    }
}