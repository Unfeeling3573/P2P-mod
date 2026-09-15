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
package gg.essential.sps.packets

import gg.essential.util.identifier
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.network.INetHandler
import java.util.*

//#if NEOFORGE
//$$ import gg.essential.Essential
//$$ import net.minecraft.network.Connection
//$$ import net.neoforged.bus.api.SubscribeEvent
//#if MC>=12106
//$$ import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent
//#endif
//#if MC>=12006
//$$ import net.neoforged.fml.common.EventBusSubscriber
//$$ import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
//#else
//$$ import net.neoforged.fml.common.Mod.EventBusSubscriber
//$$ import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent
//#endif
//#endif

//#if FABRIC || NEOFORGE
//$$ import net.minecraft.network.PacketByteBuf
//$$ import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket
//$$ import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket
//#if MC>=12005
//$$ import net.minecraft.network.codec.PacketCodec
//$$ import net.minecraft.network.codec.PacketCodecs
//#endif
//#if MC>=12002
//$$ import net.minecraft.network.packet.CustomPayload
//#endif
//#elseif MC>=12002
//$$ import net.minecraftforge.network.ChannelBuilder
//$$ import net.minecraftforge.network.PacketDistributor
//$$ import net.minecraftforge.network.SimpleChannel
//$$ import java.util.function.BiConsumer
//#elseif MC>=11800
//$$ import net.minecraftforge.network.NetworkRegistry
//$$ import net.minecraftforge.network.PacketDistributor
//$$ import net.minecraftforge.network.simple.SimpleChannel
//#elseif MC>=11700
//$$ import net.minecraftforge.fmllegacy.network.NetworkRegistry
//$$ import net.minecraftforge.fmllegacy.network.PacketDistributor
//$$ import net.minecraftforge.fmllegacy.network.simple.SimpleChannel
//#elseif MC>=11600
//$$ import net.minecraftforge.fml.network.NetworkRegistry
//$$ import net.minecraftforge.fml.network.PacketDistributor
//$$ import net.minecraftforge.fml.network.simple.SimpleChannel
//#else
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper
import net.minecraftforge.fml.relauncher.Side
//#endif

//#if NEOFORGE
//#if MC>=12106
//$$ @EventBusSubscriber(modid = Essential.MODID)
//#else
//$$ @EventBusSubscriber(modid = Essential.MODID, bus = EventBusSubscriber.Bus.MOD)
//#endif
//#endif
object SpsNet {
    @JvmField
    val CHANNEL = identifier("essential", "sps")

    //#if FABRIC || NEOFORGE
    //$$ private val registrations = mutableListOf<Pair<Class<out CustomPacket>, (ByteBuf, UUID?, PacketListener) -> Unit>>()
    //#elseif MC>=12002
    //$$ val INSTANCE: SimpleChannel = ChannelBuilder.named(CHANNEL)
    //$$     .networkProtocolVersion(0)
    //$$     .acceptedVersions { _, _ -> true }
    //$$     .simpleChannel()
    //#elseif MC>=11600
    //$$ val INSTANCE: SimpleChannel = NetworkRegistry.newSimpleChannel(CHANNEL, { "1" }, { true }, { true })
    //#else
    @JvmField
    val WRAPPER: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL.toString())
    //#endif

    private var nextId = 0
    private inline fun <reified T : CustomPacket> register(noinline new: () -> T, serverBound: Boolean = false) {
        //#if FABRIC || NEOFORGE
        //$$ registrations.add(Pair(T::class.java, { buf, source, netHandler -> new().apply { fromBytes(buf) }.handle(source, netHandler) }))
        //#elseif MC>=12002
        //$$ INSTANCE.messageBuilder(T::class.java, nextId++)
        //$$     .encoder { packet, buf -> packet.toBytes(buf) }
        //$$     .decoder { buf -> new().apply { fromBytes(buf) } }
        //$$     .consumerNetworkThread(BiConsumer { packet, ctx ->
        //$$         packet.handle(ctx.sender?.uuid, ctx.connection.packetListener ?: return@BiConsumer)
        //$$         ctx.packetHandled = true
        //$$     })
        //$$     .add()
        //#elseif MC>=11600
        //$$ @Suppress("INACCESSIBLE_TYPE") // forge api returns package-private class
        //$$ INSTANCE.registerMessage(
        //$$     nextId++,
        //$$     T::class.java,
        //$$     { packet, buf -> packet.toBytes(buf) },
        //$$     { buf -> new().apply { fromBytes(buf) } },
        //$$     { packet, ctx ->
                //#if MC>=11700
                //$$ val uuid = ctx.get().sender?.uuid
                //$$ val netHandler = ctx.get().networkManager.packetListener
                //#else
                //$$ val uuid = ctx.get().sender?.uniqueID
                //$$ val netHandler = ctx.get().networkManager.netHandler
                //#endif
        //$$         packet.handle(uuid, netHandler)
        //$$         ctx.get().packetHandled = true
        //$$     },
        //$$ )
        //#else
        WRAPPER.registerMessage(
            BasicMessageHandler(),
            T::class.java,
            nextId++,
            if (serverBound) Side.SERVER else Side.CLIENT,
        )
        @Suppress("UNUSED_EXPRESSION") new // Forge uses reflection to create packet instances
        //#endif
    }

    // TODO can we rename this back to `init` and the `MessageHandler` back to `Handler`
    //  (similar names exist in the api project, so feature-flag-processor revolts but I don't think it should)
    fun registerPackets() {
        // Note: Ids are assigned in order of registration, so order matters and entries must not be removed!
        register(::S2CSharedResourcePacks)
    }

    //#if NEOFORGE
    //$$ @SubscribeEvent
    //$$ @JvmStatic
    //#if MC>=12006
    //$$ fun registerWithNeoForge(event: RegisterPayloadHandlersEvent) {
    //$$     val registrar = event.registrar("1").optional()
    //$$     registrar.playBidirectional(SpsCustomPayload.ID, SpsCustomPayload.CODEC) { msg, ctx ->
    //$$         val player = try { ctx.player() } catch (e: UnsupportedOperationException) { null }
    //$$         handle(msg.buf, player?.uuid, ctx.listener())
    //$$     }
    //$$ }
    //#if MC>=12106
    //$$ @SubscribeEvent
    //$$ @JvmStatic
    //$$ fun registerWithNeoForgeClient(event: RegisterClientPayloadHandlersEvent) {
    //$$     event.register(SpsCustomPayload.ID) { msg, ctx ->
    //$$         val player = try { ctx.player() } catch (e: UnsupportedOperationException) { null }
    //$$         handle(msg.buf, player?.uuid, ctx.listener())
    //$$     }
    //$$ }
    //#endif
    //#else
    //$$ fun registerWithNeoForge(event: RegisterPayloadHandlerEvent) {
    //$$     val registrar = event.registrar(CHANNEL.namespace).optional()
    //$$     registrar.play(CHANNEL, { buf ->
    //$$         val bytes = ByteArray(buf.readableBytes())
    //$$         buf.readBytes(bytes)
    //$$         SpsCustomPayload(bytes)
    //$$     }) { msg, ctx ->
    //$$         val packetListener = ctx.channelHandlerContext.pipeline().get(Connection::class.java).packetListener!!
    //$$         handle(msg.buf, ctx.player.orElse(null)?.uuid, packetListener)
    //$$     }
    //$$ }
    //#endif
    //#endif

    fun sendToServer(message: CustomPacket) {
        //#if FABRIC || NEOFORGE
        //$$ val buf = PacketByteBuf(Unpooled.buffer())
        //$$ buf.writeVarInt(registrations.indexOfFirst { (cls, _) -> cls.isInstance(message) })
        //$$ message.toBytes(buf)
        //#if MC>=12002
        //$$ val packet = CustomPayloadC2SPacket(SpsCustomPayload(ByteArray(buf.readableBytes()).also { buf.readBytes(it) }))
        //#else
        //$$ val packet = CustomPayloadC2SPacket(CHANNEL, buf)
        //#endif
        //$$ // FIXME `sendPacket` doesn't remap automatically any more
        //#if NEOFORGE && MC>=12006
        //$$ Minecraft.getInstance().connection?.send(packet)
        //#else
        //$$ MinecraftClient.getInstance().networkHandler?.sendPacket(packet)
        //#endif
        //#elseif MC>=12002
        //$$ INSTANCE.send(message, PacketDistributor.SERVER.noArg())
        //#elseif MC>=11600
        //$$ INSTANCE.sendToServer(message)
        //#else
        WRAPPER.sendToServer(message)
        //#endif
    }

    fun send(player: EntityPlayerMP, message: CustomPacket) {
        //#if FABRIC || NEOFORGE
        //$$ val buf = PacketByteBuf(Unpooled.buffer())
        //$$ buf.writeVarInt(registrations.indexOfFirst { (cls, _) -> cls.isInstance(message) })
        //$$ message.toBytes(buf)
        //#if MC>=12002
        //$$ val packet = CustomPayloadS2CPacket(SpsCustomPayload(ByteArray(buf.readableBytes()).also { buf.readBytes(it) }))
        //#else
        //$$ val packet = CustomPayloadS2CPacket(CHANNEL, buf)
        //#endif
        //$$ // FIXME `sendPacket` doesn't remap automatically any more
        //#if NEOFORGE && MC>=12006
        //$$ player.connection.send(packet)
        //#else
        //$$ player.networkHandler.sendPacket(packet)
        //#endif
        //#elseif MC>=12002
        //$$ INSTANCE.send(message, PacketDistributor.PLAYER.with(player))
        //#elseif MC>=11600
        //$$ INSTANCE.send(PacketDistributor.PLAYER.with { player }, message)
        //#else
        WRAPPER.sendTo(message, player)
        //#endif
    }

    //#if FABRIC || NEOFORGE
    //#if MC>=12002
    //$$ class SpsCustomPayload(val bytes: ByteArray) : CustomPayload {
    //$$     val buf: PacketByteBuf get() = PacketByteBuf(Unpooled.wrappedBuffer(bytes))
    //#if MC>=12005
    //$$     override fun getId(): CustomPayload.Id<out CustomPayload> = ID
    //#else
    //$$     override fun id() = ID
    //$$     override fun write(buf: PacketByteBuf) {
    //$$         buf.writeBytes(bytes)
    //$$     }
    //#endif
    //$$     companion object {
            //#if MC>=12005
            //$$ @JvmField
            //$$ val ID: CustomPayload.Id<SpsCustomPayload> = CustomPayload.Id(CHANNEL)
            //$$ @JvmField
            //$$ val CODEC: PacketCodec<PacketByteBuf, SpsCustomPayload> = PacketCodec.tuple(PacketCodecs.BYTE_ARRAY, SpsCustomPayload::bytes, ::SpsCustomPayload)
            //#else
            //$$ val ID = CHANNEL
            //#endif
    //$$     }
    //$$ }
    //#endif
    //$$ fun handle(buf: PacketByteBuf, source: UUID?, netHandler: PacketListener) {
    //$$     val id = buf.readVarInt()
    //$$     val (_, handle) = registrations.getOrNull(id) ?: return
    //$$     handle(buf, source, netHandler)
    //$$ }
    //#endif
}

//#if MC>=11600
//$$ interface CustomPacket {
//$$     fun fromBytes(buf: ByteBuf)
//$$     fun toBytes(buf: ByteBuf)
//#else
interface CustomPacket : IMessage {
//#endif
    fun handleOnMainThread(source: UUID?, netHandler: INetHandler)

    fun handle(source: UUID?, netHandler: INetHandler) {
        val mc = Minecraft.getMinecraft()
        // Note: Using vanilla scheduling methods instead of our ones to keep order relative to other packets
        //#if MC>=11600
        //$$ if (!mc.isOnExecutionThread) {
        //$$     mc.execute { handleOnMainThread(source, netHandler) }
        //$$     return
        //$$ }
        //#else
        if (!mc.isCallingFromMinecraftThread) {
            mc.addScheduledTask { handleOnMainThread(source, netHandler) }
            return
        }
        //#endif
        handleOnMainThread(source, netHandler)
    }
}

//#if MC>=11600
//#else
private class BasicMessageHandler<T : CustomPacket> : IMessageHandler<T, IMessage> {
    override fun onMessage(message: T, context: MessageContext): IMessage? {
        // Note: Forge manages for this to actually be `null` if you get unlucky with timing during disconnect.
        //       Presumably caused by [NetworkDispatcher.cleanAttributes].
        val netHandler = context.netHandler ?: return null
        message.handle(if (context.side == Side.SERVER) context.serverHandler.player.uniqueID else null, netHandler)
        return null
    }
}
//#endif
