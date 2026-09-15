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

import gg.essential.Essential
import gg.essential.util.Sha256
import io.netty.buffer.ByteBuf
import net.minecraft.network.INetHandler
import java.util.*

class S2CSharedResourcePacks(
    var packs: List<Sha256>,
) : CustomPacket {
    constructor() : this(emptyList())

    override fun fromBytes(buf: ByteBuf) {
        packs = List(buf.readUnsignedByte().toInt()) {
            Sha256(ByteArray(Sha256.BYTES).also { buf.readBytes(it) })
        }
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeByte(packs.size)
        for (sha256 in packs) {
            buf.writeBytes(sha256.bytes)
        }
    }

    override fun handleOnMainThread(source: UUID?, netHandler: INetHandler) {
        Essential.getInstance().sharedResourcePacksManager.serverPacks.set(packs)
    }
}
