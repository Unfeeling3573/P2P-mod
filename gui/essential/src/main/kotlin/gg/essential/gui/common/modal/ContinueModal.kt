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
package gg.essential.gui.common.modal

import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalFlow.ModalContinuation
import gg.essential.gui.overlay.ModalManager

/**
 * A basic confirmation modal.
 * Returns, via continuation, true if the user confirms continuing, false when the user clicks "Cancel".
 */
class ContinueModal(
    modalManager: ModalManager,
    private val message: String,
    private val continuation: ModalContinuation<Boolean>
) : EssentialModal2(modalManager) {

    override fun LayoutScope.layoutTitle() {
        wrappedText(message, centered = true)
    }

    override fun LayoutScope.layoutButtons() {
        primaryAndCancelButtons("Continue", "Cancel", { continuation.resume(true) }, {
            continuation.resumeImmediately(false)
            close()
        })
    }

}

suspend fun ModalFlow.continueModal(message: String): Boolean {
    return awaitModal { continuation ->
        ContinueModal(modalManager, message, continuation)
    }
}