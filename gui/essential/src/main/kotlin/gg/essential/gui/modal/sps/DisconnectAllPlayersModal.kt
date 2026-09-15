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

import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.OutlineButtonStyle
import gg.essential.gui.common.modal.EssentialModal2
import gg.essential.gui.common.textStyle
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalManager

suspend fun ModalFlow.disconnectAllPlayersWarningModal() =
    awaitModal { DisconnectAllPlayersModal(modalManager, it) }

class DisconnectAllPlayersModal(
    modalManager: ModalManager,
    private val continuation: ModalFlow.ModalContinuation<Boolean>,
) : EssentialModal2(modalManager) {

    override fun LayoutScope.layoutTitle() {
        text("Disconnect all players?", Modifier.color(EssentialPalette.MODAL_WARNING).shadow(EssentialPalette.BLACK))
    }

    override fun LayoutScope.layoutBody() {
        wrappedText(
            "If you stop hosting, all connected\nplayers will be kicked.",
            centered = true,
            modifier = Modifier.color(EssentialPalette.TEXT).shadow(EssentialPalette.BLACK)
        )
    }

    override fun LayoutScope.layoutButtons() {
        row(Arrangement.spacedBy(8f)) {
            outlineButton(
                Modifier.width(90f),
                OutlineButtonStyle.RED,
                action = { replaceWith(continuation.resume(false)) }
            ) { currentStyle ->
                text("Stop Hosting", Modifier.textStyle(currentStyle))
            }
            cancelButton("Cancel")
        }

    }
}

