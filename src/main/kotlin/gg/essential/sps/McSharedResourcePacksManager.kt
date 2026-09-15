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
package gg.essential.sps

import gg.essential.Essential
import gg.essential.api.gui.Slot
import gg.essential.event.network.server.ServerLeaveEvent
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.content.ConfirmDenyNotificationActionComponent
import gg.essential.mixins.ext.server.coroutineScope
import gg.essential.mixins.impl.client.SharedResourcePacksHolder
import gg.essential.sps.packets.SpsNet
import gg.essential.sps.packets.S2CSharedResourcePacks
import gg.essential.sps.quic.jvm.LOCALHOST
import gg.essential.util.Sha256
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import java.io.IOException
import java.nio.file.Path

//#if MC <= 1.12.2
import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.provideDelegate
import gg.essential.elementa.utils.withAlpha
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.fillParent
import gg.essential.gui.layoutdsl.layoutAsBox
import gg.essential.gui.layoutdsl.text
import gg.essential.universal.UMatrixStack
import gg.essential.util.McElementaExtractor
import gg.essential.util.UDrawContext
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.opengl.GL11
import java.awt.Color
//#endif

class McSharedResourcePacksManager(
    worldsManager: WorldsManager,
    integratedServerManager: State<McIntegratedServerManager?>,
) : SharedResourcePacksManager(worldsManager) {
    private val refHolder = ReferenceHolderImpl()

    // Accessed from server thread
    var sharedResourcePacksPacket: S2CSharedResourcePacks? = null
        private set

    init {
        // Reset server-supplied packs when leaving server
        Essential.EVENT_BUS.register<ServerLeaveEvent>({ _ ->
            serverPacks.set(emptyList())
        })

        // Reset acceptance on disconnect from server
        Essential.EVENT_BUS.register<ServerLeaveEvent>({ _ ->
            currentPrompt.set(null)
            acceptedPacks.set(emptyList())
        })

        // Show prompt notification when desired
        var activeNotificationId: Any? = null
        effect(refHolder) {
            val prompt = currentPrompt()
            if (prompt == null) {
                activeNotificationId?.let { Notifications.removeNotificationById(it) }
                activeNotificationId = null
                return@effect
            }
            val verdict = prompt.second
            if (verdict == null && activeNotificationId == null) {
                activeNotificationId = showPromptNotification(
                    accept = { currentPrompt.set { it?.copy(second = true) } },
                    reject = { currentPrompt.set { it?.copy(second = false) } },
                )
            } else if (verdict != null) {
                activeNotificationId?.let { Notifications.removeNotificationById(it) }
                activeNotificationId = null
            }
        }

        val packsToBeShared = memo {
            val world = worldsManager.integratedServerWorld()
            if (world == null || !world.shareResourcePacks()) emptyList()
            else world.resourcePackFiles()?.map { it.first } ?: emptyList()
        }
        var updateJob: Job? = null
        effect(refHolder) {
            val packs = packsToBeShared()
            val packet = S2CSharedResourcePacks(packs)
            val server = integratedServerManager()?.server ?: return@effect
            updateJob?.cancel()
            updateJob = server.coroutineScope.launch {
                sharedResourcePacksPacket = packet
                for (player in server.playerList.players) {
                    SpsNet.send(player, packet)
                }
            }
        }
    }

    override fun getP2PUrl(sha256: Sha256): String {
        val iceManager = Essential.getInstance().connectionManager.iceManager
        val proxyHttpPort = iceManager.proxyHttpPort
            ?: throw IOException("ICE http proxy not available, cannot download resource packs")
        return "http://${LOCALHOST.hostAddress}:$proxyHttpPort/${sha256.hexStr}"
    }

    override suspend fun applySharedResourcePacks(packs: List<Path>) {
        //#if MC <= 1.12.2
        showLoadingScreen()
        //#endif
        val mc = Minecraft.getMinecraft()
        //#if MC>=11600
        //$$ val holder = mc.packFinder
        //#else
        val holder = mc.resourcePackRepository
        //#endif
        (holder as SharedResourcePacksHolder).`essential$setSharedResourcePacks`(packs)
        //#if MC>=11600
        //$$ //#if FORGE && MC<11700
        //$$ // Forge wants us to use its selective reload method, but that makes no sense when adding/removing entire
        //$$ // packs with unknown content. They also removed it again in MC 1.17.
        //$$ @Suppress("DEPRECATION")
        //$$ //#endif
        //$$ mc.reloadResources().await()
        //#else
        mc.refreshResources()
        //#endif
    }

    //#if MC <= 1.12.2
    private fun showLoadingScreen() {
        val mc = Minecraft.getMinecraft()
        val scaledResolution = ScaledResolution(mc)

        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.loadIdentity()
        GlStateManager.ortho(0.0, scaledResolution.scaledWidth_double, scaledResolution.scaledHeight_double, 0.0, 100.0, 300.0)
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.loadIdentity()
        GlStateManager.translate(0.0f, 0.0f, -200.0f)

        val window = Window(ElementaVersion.V11)
        val background by UIBlock().apply {
            layoutAsBox(Modifier.fillParent().color(Color.BLACK.withAlpha(150))) {
                text("Loading Resource Packs...")
            }
        } childOf window
        val extractor = McElementaExtractor(UDrawContext(UMatrixStack()))
        window.extract(extractor)
        extractor.close()

        mc.updateDisplay()
    }
    //#endif

    private fun showPromptNotification(accept: () -> Unit, reject: () -> Unit): Any {
        val id = Any()
        // TODO design
        Notifications.pushPersistentToast("Server resource pack(s)", "Apply server resource pack(s)?", {}, {}) {
            uniqueId = id
            val component = ConfirmDenyNotificationActionComponent(
                confirmTooltip = "Accept",
                denyTooltip = "Decline",
                confirmAction = accept,
                denyAction = reject,
                dismissNotification = dismissNotification,
            )
            timerEnabled = component.timerEnabledState
            withCustomComponent(Slot.ACTION, component)
        }
        return id
    }
}