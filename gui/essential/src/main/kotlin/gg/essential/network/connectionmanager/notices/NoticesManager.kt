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

import gg.essential.connectionmanager.common.packet.notices.ClientNoticeBulkDismissPacket
import gg.essential.connectionmanager.common.packet.notices.ClientNoticeRequestPacket
import gg.essential.connectionmanager.common.packet.notices.ServerNoticeBulkDismissPacket
import gg.essential.connectionmanager.common.packet.notices.ServerNoticePopulatePacket
import gg.essential.connectionmanager.common.packet.notices.ServerNoticeRemovePacket
import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.add
import gg.essential.gui.elementa.state.v2.addAll
import gg.essential.gui.elementa.state.v2.clear
import gg.essential.gui.elementa.state.v2.filter
import gg.essential.gui.elementa.state.v2.mapEach
import gg.essential.gui.elementa.state.v2.mutableListStateOf
import gg.essential.gui.elementa.state.v2.mutableSetState
import gg.essential.gui.elementa.state.v2.removeAll
import gg.essential.gui.elementa.state.v2.stateUsingSystemTime
import gg.essential.gui.elementa.state.v2.toListState
import gg.essential.handlers.ShutdownHook
import gg.essential.network.CMConnection
import gg.essential.network.connectionmanager.NetworkedManager
import gg.essential.network.registerPacketHandler
import gg.essential.notices.NoticeType
import gg.essential.notices.model.Notice
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class NoticesManager(private val connectionManager: CMConnection) : NetworkedManager, INoticesManager {
    private val mutableAllNotices = mutableListStateOf<Notice>()
    val allNotices: ListState<Notice>
        get() = mutableAllNotices

    private val dismissedNoticeIds = mutableSetState<String>()

    private enum class NoticeStatus { Expired, Active, Upcoming }
    private val noticesWithStatus =
        stateUsingSystemTime { now ->
            val dismissedIds = dismissedNoticeIds()
            allNotices().mapNotNull { notice ->
                if (notice.id in dismissedIds) return@mapNotNull null
                val activeAfter = notice.activeAfter
                val expiresAt = notice.expiresAt
                val status = when {
                    activeAfter != null && now.isBefore(activeAfter.toInstant()) -> NoticeStatus.Upcoming
                    expiresAt != null && now.isAfter(expiresAt.toInstant()) -> NoticeStatus.Expired
                    else -> NoticeStatus.Active
                }
                Pair(notice, status)
            }
        }.toListState()

    val expiredNotices: ListState<Notice> = noticesWithStatus.filter { it.second == NoticeStatus.Expired }.mapEach { it.first }
    val activeNotices: ListState<Notice> = noticesWithStatus.filter { it.second == NoticeStatus.Active }.mapEach { it.first }
    val upcomingNotices: ListState<Notice> = noticesWithStatus.filter { it.second == NoticeStatus.Upcoming }.mapEach { it.first }

    private val listeners = mutableListOf<NoticeListener>()

    init {
        connectionManager.registerPacketHandler<ServerNoticePopulatePacket> { packet: ServerNoticePopulatePacket ->
            populateNotices(packet.notices)
        }
        connectionManager.registerPacketHandler<ServerNoticeRemovePacket> { packet: ServerNoticeRemovePacket ->
            removeNotices(packet.ids)
        }

        ShutdownHook.INSTANCE.register { flushDismissNotices() }
    }

    @Deprecated("Requires manually handling upcoming and expiring notices. Use above `State`s instead.")
    fun register(listener: NoticeListener) {
        listeners.add(listener)
    }

    override fun populateNotices(notices: Collection<Notice>) {
        mutableAllNotices.set { oldList ->
            notices.fold(oldList) { list, notice ->
                val index = list.indexOfFirst { it.id == notice.id }
                if (index != -1) list.set(index, notice) else list.add(notice)
            }
        }
        for (notice in notices) {
            for (listener in listeners) {
                try {
                    listener.noticeAdded(notice)
                } catch (e: Exception) {
                    LOGGER.error("An error occurred within a notice listener for noticeAdded: {}", notice.id, e)
                }
            }
        }
    }

    override fun removeNotices(noticeIds: Set<String>?) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            for (value in allNotices.getUntracked()) {
                for (listener in listeners) {
                    try {
                        listener.noticeRemoved(value)
                    } catch (e: Exception) {
                        LOGGER.error(
                            "An error occurred within a notice listener for noticeRemoved: {}",
                            value.id,
                            e
                        )
                    }
                }
            }
            mutableAllNotices.clear()
            dismissedNoticeIds.clear()
            return
        }

        val removedNotices = mutableListOf<Notice>()
        mutableAllNotices.set { oldList ->
            noticeIds.fold(oldList) { list, noticeId ->
                val index = list.indexOfFirst { it.id == noticeId }
                if (index == -1) return@fold list
                removedNotices.add(list[index])
                list.removeAt(index)
            }
        }
        dismissedNoticeIds.removeAll(noticeIds)
        for (removed in removedNotices) {
            for (listener in listeners) {
                try {
                    listener.noticeRemoved(removed)
                } catch (e: Exception) {
                    LOGGER.error("An error occurred within a notice listener for noticeRemoved: {}", removed.id, e)
                }
            }
        }
    }

    override fun dismissNotice(noticeId: String) {
        dismissNotices(setOf(noticeId))
    }

    fun dismissNotices(noticeIds: Set<String>) {
        dismissedNoticeIds.addAll(noticeIds)
        flushDismissNotices()
    }

    /**
     * Queues the notice to be dismissed until the queue is flushed using [.flushDismissNotices]
     * @param noticeId the notice id to queue for dismissal
     */
    fun queueDismissNotice(noticeId: String) {
        dismissedNoticeIds.add(noticeId)
    }

    fun flushDismissNotices() {
        val notices = dismissedNoticeIds.getUntracked().toSet()
        if (notices.isEmpty()) {
            return
        }
        connectionManager.send(ClientNoticeBulkDismissPacket(notices)) { maybePacket ->
            if (maybePacket.isPresent) {
                val packet = maybePacket.get()
                if (packet is ServerNoticeBulkDismissPacket) {
                    val serverNoticeBulkDismissPacket = packet
                    val noticeIds = serverNoticeBulkDismissPacket.noticeIds
                    if (!noticeIds.isEmpty()) {
                        removeNotices(noticeIds)
                    }
                    for (error in serverNoticeBulkDismissPacket.errors) {
                        when (error.reason) {
                            "NOTICE_NOT_FOUND", "NOTICE_ALREADY_DISMISSED" -> {
                                removeNotices(setOf(error.noticeId))
                            }

                            else -> {
                                LOGGER.error(
                                    "Notice unable to be dismissed: NoticeId: {}, Reason: {}",
                                    error.noticeId,
                                    error.reason
                                )
                            }
                        }
                    }
                    return@send
                }
            }
            LOGGER.error("Unexpected notice response: {}", maybePacket)
        }
    }

    override fun resetState() {
        mutableAllNotices.clear()
        dismissedNoticeIds.clear()

        listeners.forEach { it.resetState() }
    }

    override fun onConnected() {
        resetState()

        connectionManager.call(
            ClientNoticeRequestPacket(
                null,
                setOf(*NoticeType.entries.toTypedArray()),
                null,
                null
            )
        ).fireAndForget()
        listeners.forEach { it.onConnect() }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(NoticesManager::class.java)
    }
}
