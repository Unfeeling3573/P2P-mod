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
import gg.essential.event.network.server.ServerLeaveEvent
import gg.essential.event.render.RenderTickEvent
import gg.essential.gui.elementa.state.v2.State
import gg.essential.mixins.ext.server.dispatcher
import gg.essential.network.CMConnection
import gg.essential.network.connectionmanager.ConnectionManager
import gg.essential.universal.UMinecraft.getMinecraft
import gg.essential.universal.UScreen
import gg.essential.util.GuiUtil
import gg.essential.util.UIdentifier
import gg.essential.util.associateNotNull
import gg.essential.util.await
import gg.essential.util.getHardCodedCategoryForGameRule
import kotlinx.coroutines.withContext
import me.kbrewster.eventbus.Subscribe
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.client.resources.I18n
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.GameRules
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.notExists

//#if MC>=12111
//$$ import net.minecraft.registry.Registries
//$$ import net.minecraft.util.Identifier
//$$ import net.minecraft.world.rule.GameRule
//$$ import net.minecraft.world.rule.GameRuleType
//#endif

//#if MC>=12105
//$$ import kotlin.jvm.optionals.getOrNull
//#endif

//#if MC>=12004
//$$ import net.minecraft.nbt.NbtSizeTracker
//#endif

//#if MC>=11600
//$$ import gg.essential.mixins.transformers.server.integrated.ServerWorldAccessor
//$$ import gg.essential.mixins.transformers.server.integrated.ServerWorldInfoAccessor
//$$ import gg.essential.mixins.transformers.server.integrated.Mixin_SetWorldName
//$$ import net.minecraft.world.storage.DerivedWorldInfo
//$$ import net.minecraft.world.storage.SaveFormat
//$$ import net.minecraft.world.storage.ServerWorldInfo
//#endif

// TODO support that one shared folders mod
class McWorldsManager(
    cm: ConnectionManager,
    savesFolder: Path,
    integratedServerManager: State<McIntegratedServerManager?>,
) : WorldsManager(cm, savesFolder, integratedServerManager) {

    override fun newWorldManager(localFolder: Path): WorldManager = McWorldManager(this, cmConnection, localFolder)

    override suspend fun startIntegratedServer(worldManager: WorldManager): IntegratedServerManager {
        val mc = Minecraft.getMinecraft()
        val folderName = worldManager.localFolder.name
        //#if MC>=11900
        //$$ val worldSummaries = mc.levelStorage.loadSummaries(mc.levelStorage.levelList).get()
        //#else
        val worldSummaries = mc.saveLoader.saveList
        //#endif
        val worldSummary = worldSummaries.find { it.fileName == folderName }
            ?: throw IllegalArgumentException("Failed to get world summary of `$folderName`")

        val prevScreen = UScreen.currentScreen

        //#if MC>=12004
        //$$ mc.createIntegratedServerLoader().start(worldSummary.name) { GuiUtil.openScreen { net.minecraft.client.gui.screen.TitleScreen() } }
        //#elseif MC>=11900
        //$$ mc.createIntegratedServerLoader().start(net.minecraft.client.gui.screen.TitleScreen(), worldSummary.name)
        //#elseif MC>=11602
        //$$ mc.loadWorld(worldSummary.fileName)
        //#else
        getMinecraft().launchIntegratedServer(worldSummary.fileName, worldSummary.displayName, null)
        //#endif

        return suspendCoroutine { continuation ->
            Essential.EVENT_BUS.register(object {
                @Subscribe
                private fun onTick(event: RenderTickEvent) {
                    val server = mc.integratedServer
                    if (server == null || server.isServerStopped) {
                        // IntegratedServerLoader may load various things (e.g. per-world resource packs) before
                        // it actually instantiates the actual IntegratedServerObject.
                        // There's no good way to know when that's done, or if it has failed (at least not on older
                        // versions).
                        // It should however at least change the screen to some error screen when it failed, so we can
                        // detect that.
                        if (UScreen.currentScreen === prevScreen) {
                            return
                        }
                        // However on newer versions it does also display generic progress screens, so we need to
                        // account for those as well.
                        //#if MC >= 1.20.4
                        //$$ if (UScreen.currentScreen is net.minecraft.client.gui.screen.MessageScreen) {
                        //$$     return
                        //$$ }
                        //#endif
                        // And also the other kind of progress screen, from `Minecraft.disconnectWithProgressScreen`
                        // (even when there's no disconnecting to be done)
                        //#if MC >= 1.16
                        //$$ if (UScreen.currentScreen is net.minecraft.client.gui.screen.WorkingScreen) {
                        //$$     return
                        //$$ }
                        //#endif

                        // If the screen has changed, but the server isn't running, then something's probably gone wrong
                        Essential.EVENT_BUS.unregister(this)
                        continuation.resumeWithException(RuntimeException("Integrated server failed to start."))

                        // If the server fails to start and the client is disconnected during the login phase
                        // (`NetHandlerLoginClient.onDisconnected`), `loadWorld(null)` will never be called and
                        // therefore the `integratedServer` field will never be reset properly.
                        // To prevent various code from being confused about the state of the game in these cases (it
                        // will look like the game is in Singleplayer, but it's not), we will make that call manually.
                        if (server != null) {
                            //#if MC>=12106
                            //$$ mc.disconnectWithProgressScreen()
                            //#elseif MC>=11602
                            //$$ mc.unloadWorld()
                            //#else
                            mc.loadWorld(null as WorldClient?)
                            //#endif
                        }

                        return
                    }

                    //#if MC<11600
                    @Suppress("SENSELESS_COMPARISON") // Forge applies an inappropriate NonNullByDefault
                    //#endif
                    if (getMinecraft().world != null) {
                        Essential.EVENT_BUS.unregister(this)
                        continuation.resume(Essential.getInstance().integratedServerManager.getUntracked()!!)
                        return
                    }
                }
            })
        }
    }

    init {
        Essential.EVENT_BUS.register<ServerLeaveEvent>({ _ ->
        })
    }

    override suspend fun disconnect() {
        //#if MC>=12106
        //$$ MinecraftClient.getInstance().world?.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT)
        //#else
        //#if MC<11600
        @Suppress("UNNECESSARY_SAFE_CALL") // Forge applies an inappropriate NonNullByDefault
        //#endif
        Minecraft.getMinecraft().world?.sendQuittingDisconnectingPacket()
        //#endif

        awaitDisconnected()
    }

    override suspend fun awaitDisconnected() {
        //#if MC<11600
        @Suppress("SENSELESS_COMPARISON") // Forge applies an inappropriate NonNullByDefault
        //#endif
        if (Minecraft.getMinecraft().world == null) {
            return // already disconnected
        }
        Essential.EVENT_BUS.await<ServerLeaveEvent>()
    }

    override fun cpuInfo(): String {
        //#if MC>=12105
        //$$ return try {
        //$$     val processor = oshi.SystemInfo().hardware.processor
        //$$     "${processor.logicalProcessorCount}x ${processor.processorIdentifier.name}".replace(Regex("\\s+"), " ")
        //$$ } catch (e: Throwable) {
        //$$     "<unknown>"
        //$$ }
        //#elseif MC>=11600
        //$$ return com.mojang.blaze3d.platform.PlatformDescriptors.getCpuInfo()
        //#else
        return net.minecraft.client.renderer.OpenGlHelper.getCpu()
        //#endif
    }

}

class McWorldManager(
    worldsManager: WorldsManager,
    cmConnection: CMConnection,
    localFolder: Path,
) : WorldManager(worldsManager, cmConnection, localFolder) {

    private fun <T> readFromDat(datFile: Path, read: (NBTTagCompound) -> T): T? {
        if (datFile.notExists()) return null
        return try {
            val nbt = datFile.inputStream().use {
                //#if MC>=12004
                //$$ // Max size from LevelStorage#readLevelProperties
                //$$ NbtIo.readCompressed(it, NbtSizeTracker.of(0x6400000))
                //#else
                CompressedStreamTools.readCompressed(it)
                //#endif
            }
            read(nbt)
        } catch (exception: Exception) {
            Essential.logger.warn("An error occurred reading ${datFile.name} for ${localFolder}.", exception)
            null
        }
    }

    private fun <T> readFromLevelDat(read: (NBTTagCompound) -> T): T? {
        return readFromDat(localFolder / "level.dat", read)
    }

    //#if MC >= 26.1
    //$$ private fun <T> readFromGameRulesDat(read: (CompoundTag) -> T): T? {
    //$$     return readFromDat(localFolder / "data" / "minecraft" / "game_rules.dat", read)
    //$$ }
    //#endif

    override fun readLegacyLocalWorldInfo(): LocalWorldInfo? {
        // TODO we want null when missing, not defaults
        // SPSData.getSPSSettings()
        return null
    }

    override fun readMcWorldSettings(): GameWorldSettings {
        return readFromLevelDat { root ->
            //#if MC>=12105
            //$$ val data = root.getCompound("Data").getOrNull()
            //#if MC >= 26.1
            //$$ val difficultySettings = data?.getCompound("difficulty_settings")?.getOrNull()
            //#endif
            //$$ GameWorldSettings(
            //$$     WorldGameMode.entries[data?.getInt("GameType")?.getOrNull() ?: 0],
                //#if MC >= 26.1
                //$$ WorldDifficulty.entries.firstOrNull { it.name.equals(difficultySettings?.getString("difficulty")?.getOrNull(), true) } ?: WorldDifficulty.Peaceful,
                //$$ difficultySettings?.getBoolean("locked")?.getOrNull() ?: false,
                //#else
                //$$ WorldDifficulty.entries[data?.getInt("Difficulty")?.getOrNull() ?: 0],
                //$$ data?.getBoolean("DifficultyLocked")?.getOrNull() ?: false,
                //#endif
            //$$     data?.getBoolean("allowCommands")?.getOrNull() ?: false,
            //$$     emptySet(),
            //$$ )
            //#else
            val data = root.getCompoundTag("Data")
            GameWorldSettings(
                WorldGameMode.entries[data.getInteger("GameType")],
                WorldDifficulty.entries[data.getInteger("Difficulty")],
                data.getBoolean("DifficultyLocked"),
                data.getBoolean("allowCommands"),
                emptySet(),
            )
            //#endif
        } ?: GameWorldSettings(WorldGameMode.Adventure, WorldDifficulty.Normal, difficultyLocked = false, cheats = false, emptySet())
    }

    override fun readMcWorldGameRules(): CWGameRules {
        //#if MC >= 26.1
        //$$ return readFromGameRulesDat { gameRulesRoot ->
        //$$     val gameRules = gameRulesRoot.getCompound("data")?.getOrNull() ?: CompoundTag()
        //#else
        return readFromLevelDat { root ->
            //#if MC >= 1.21.11
            //$$ val gameRulesNbtKey = "game_rules"
            //#else
            val gameRulesNbtKey = "GameRules"
            //#endif
            //#if MC >= 1.21.5
            //$$ val gameRules = root.getCompound("Data").getOrNull()?.getCompound(gameRulesNbtKey)?.getOrNull() ?: NbtCompound()
            //#else
            val gameRules = root.getCompoundTag("Data").getCompoundTag(gameRulesNbtKey)
            //#endif
        //#endif
            //#if MC>=11600 && MC<12111
            //$$ val ruleMap = mutableMapOf<String, Pair<GameRules.RuleKey<*>, GameRules.RuleType<*>>>()
            //#if MC>=12102
            //$$ // Note: FeatureSet value doesn't actually matter, `accept` will (at least currently) visit all gamerules
            //$$ GameRules(net.minecraft.resource.featuretoggle.FeatureSet.empty()).accept(object : GameRules.Visitor {
            //#else
            //$$ GameRules.visitAll(object : GameRules.IRuleEntryVisitor {
            //#endif
            //$$     override fun <T : GameRules.RuleValue<T>?> visit(key: GameRules.RuleKey<T>, type: GameRules.RuleType<T>) {
            //$$         ruleMap[key.name] = key to type
            //$$     }
            //$$ })
            //#endif
            CWGameRules(gameRules.keySet.associateNotNull { name ->
                val id = UIdentifier.ofLegacy(name)
                //#if MC>=12111
                //$$ val rule = Registries.GAME_RULE.get(Identifier.of(name)) ?: return@associateNotNull null
                //$$ val type = when (rule.type) {
                //$$     GameRuleType.BOOL -> CWGameRule.Type.Boolean
                //$$     GameRuleType.INT -> CWGameRule.Type.Int
                //$$     else -> CWGameRule.Type.Unknown
                //$$ }
                //$$ val category = rule.category.category.toTranslationKey("gamerule.category")
                //$$ val defaultValue = rule.defaultValue.toString()
                //$$ val value = gameRules.get(name, rule.codec).orElse(null)?.toString() ?: defaultValue
                //#else
                val value = gameRules.getString(name)
                    //#if MC>=12105
                    //$$ .orElse("")
                    //#endif
                //#if MC>=11600
                //$$ val (ruleKey, ruleType) = ruleMap[name] ?: return@associateNotNull null // unsure if theres anything else to do?
                //$$ val category = ruleKey.category.localeString
                //$$ val newValue = ruleType.createValue()
                //$$ val type = when (newValue) {
                //$$     is GameRules.BooleanValue -> CWGameRule.Type.Boolean
                //$$     is GameRules.IntegerValue -> CWGameRule.Type.Int
                //$$     else -> CWGameRule.Type.Unknown
                //$$ }
                //$$ val defaultValue = newValue.stringValue()
                //#else
                val mcGameRules = GameRules()
                val category = "gamerule.category.${getHardCodedCategoryForGameRule(name)}"
                val type = when {
                    mcGameRules.areSameType(name, GameRules.ValueType.BOOLEAN_VALUE) -> CWGameRule.Type.Boolean
                    mcGameRules.areSameType(name, GameRules.ValueType.NUMERICAL_VALUE) -> CWGameRule.Type.Int
                    mcGameRules.hasRule(name) -> CWGameRule.Type.Unknown
                    else -> return@associateNotNull null
                }
                val defaultValue = mcGameRules.getString(name)
                //#endif
                //#endif
                id to CWGameRule(
                    category,
                    type,
                    defaultValue,
                    value,
                    I18n.format(category),
                    I18n.format(id.toTranslationKey("gamerule")),
                    I18n.format(id.toTranslationKey("gamerule", "description")),
                )
            })
        } ?: CWGameRules(emptyMap())
    }

    override fun readMcWorldSummary(): WorldSummary {
        return readFromLevelDat { root ->
            //#if MC>=12105
            //$$ val data = root.getCompound("Data").getOrNull()
            //$$ WorldSummary(
            //$$     name = data?.getString("LevelName")?.getOrNull() ?: "",
            //$$     gameDisplayVersion = data?.getCompound("Version")?.getOrNull()?.getString("Name")?.getOrNull() ?: "",
            //$$     lastPlayed = Instant.ofEpochMilli(data?.getLong("LastPlayed")?.getOrNull() ?: 0),
            //$$ )
            //#else
            val data = root.getCompoundTag("Data")
            WorldSummary(
                name = data.getString("LevelName"),
                gameDisplayVersion = data.getCompoundTag("Version").getString("Name"),
                lastPlayed = Instant.ofEpochMilli(data.getLong("LastPlayed")),
            )
            //#endif
        } ?: WorldSummary("<failed to load>", "<failed to load>", Instant.EPOCH)
    }

    override suspend fun updateLocalName(name: String) {
        super.updateLocalName(name)
        val integratedServer = getMinecraft().integratedServer
        if (integratedServer != null && worldsManager.integratedServerWorld.getUntracked() == this) {
            withContext(integratedServer.dispatcher) {
                // Saved on next world save
                //#if MC>=11600
                //$$ integratedServer.worlds.forEach { world ->
                //$$     when (val serverWorldInfo = (world as ServerWorldAccessor).serverWorldInfo) {
                //$$         is ServerWorldInfo -> {
                //$$             val worldSettings = (serverWorldInfo as ServerWorldInfoAccessor).worldSettings
                //$$             (worldSettings as Mixin_SetWorldName).setWorldName(name)
                //$$         }
                //$$         is DerivedWorldInfo -> {}
                //$$         else -> {
                //$$             Essential.logger.error(
                //$$                 "Unable to rename world as its info class isn't supported: expected: {}, actual: {}",
                //$$                 ServerWorldInfo::class.simpleName,
                //$$                 serverWorldInfo::class.simpleName
                //$$             )
                //$$         }
                //$$     }
                //$$ }
                //#else
                integratedServer.worldName = name
                integratedServer.worlds.forEach { it.worldInfo.worldName = name }
                //#endif
            }
        } else {
            // World not loaded
            //#if MC>=11600
            //$$ getMinecraft().saveLoader.getLevelSave(localFolder.name).use { it.updateSaveName(name) }
            //#else
            getMinecraft().saveLoader.renameWorld(localFolder.name, name)
            //#endif
        }
    }
}
