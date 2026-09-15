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

import gg.essential.Essential
import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.event.gui.GuiOpenedEvent
import gg.essential.gui.elementa.state.v2.collections.effectOnChange
import gg.essential.gui.elementa.state.v2.filter
import gg.essential.gui.elementa.state.v2.mapEach
import gg.essential.gui.elementa.state.v2.mapList
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.notification.Notifications
import gg.essential.gui.wardrobe.Wardrobe
import gg.essential.gui.wardrobe.WardrobeCategory
import gg.essential.network.connectionmanager.telemetry.TelemetryManager
import gg.essential.notices.NoticeType
import gg.essential.notices.model.Notice
import gg.essential.util.GuiUtil
import gg.essential.util.isMainMenu
import me.kbrewster.eventbus.Subscribe

class PersistentToastNoticeListener(
    refHolder: ReferenceHolder,
    private val noticesManager: NoticesManager,
) {

    private val notices = noticesManager.activeNotices.filter { it.type == NoticeType.DISMISSIBLE_TOAST }

    private val isMainMenu = mutableStateOf(false)

    class ToastState(val notice: Notice) {
        var close: (() -> Unit)? = null
    }

    private val actions = mapOf(
        "OPEN_EMOTES" to { GuiUtil.openScreen { Wardrobe(initialCategory = WardrobeCategory.Emotes) } },
    )

    init {
        Essential.EVENT_BUS.register(this)

        val toasts = notices
            .mapList { if (isMainMenu()) it else emptyList() }
            .mapEach { ToastState(it) }
        toasts.effectOnChange(refHolder, add = { (_, toast) ->
            pushNoticeToast(toast)
        }, remove = { (_, toast) ->
            toast.close?.invoke()
            toast.close = null
        })
    }

    private fun pushNoticeToast(toast: ToastState) {
        val notice = toast.notice
        val title = notice.metadata["title"] as? String ?: return
        val message = notice.metadata["message"] as? String ?: return
        val action = notice.metadata["action"] as? String

        val telemetryManager = Essential.getInstance().connectionManager.telemetryManager

        var closedProgrammatically = false

        Notifications.pushPersistentToast(
            title,
            message,
            action = {
                // FIXME: This is hard coded for now until we have a better way to handle this
                actions[action]?.let { it() }
                noticesManager.dismissNotice(notice.id)
                telemetryManager.clientActionPerformed(TelemetryManager.Actions.PERSISTENT_TOAST_CLICKED, notice.id)
            },
            close = {
                if (closedProgrammatically) return@pushPersistentToast
                noticesManager.dismissNotice(notice.id)
                telemetryManager.clientActionPerformed(TelemetryManager.Actions.PERSISTENT_TOAST_CLEARED, notice.id)
            },
            configure = {
                toast.close = {
                    closedProgrammatically = true
                    dismissNotificationInstantly()
                }
            },
        )
    }

    @Subscribe
    fun guiOpenedEvent(event: GuiOpenedEvent) {
        isMainMenu.set(event.screen.isMainMenu)
    }
}