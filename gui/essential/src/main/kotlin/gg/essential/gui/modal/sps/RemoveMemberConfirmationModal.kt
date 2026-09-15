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
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalManager
import gg.essential.util.UuidNameLookup
import java.util.*

suspend fun ModalFlow.removeMemberConfirmationModal(uuid: UUID) =
    awaitModal { RemoveMemberConfirmationModal(modalManager, uuid, it) }

class RemoveMemberConfirmationModal(modalManager: ModalManager, val uuid: UUID, private val continuation: ModalFlow.ModalContinuation<Unit>) : EssentialModal2(modalManager) {
    override fun LayoutScope.layoutBody() {
        wrappedText(
            "Remove {username} from this world?\nThey won’t be able to join anymore.",
            textModifier = Modifier.color(EssentialPalette.TEXT).shadow(EssentialPalette.BLACK)
        ) {
            "username" { text(UuidNameLookup.nameState(uuid, "Loading..."), Modifier.color(EssentialPalette.TEXT_HIGHLIGHT).shadow(EssentialPalette.BLACK)) }
        }
    }

    override fun LayoutScope.layoutButtons() {
        primaryAndCancelButtons(
            "Remove",
            "Cancel",
            { replaceWith(continuation.resume(Unit)) },
            primaryStyle = OutlineButtonStyle.RED,
        )
    }
}
