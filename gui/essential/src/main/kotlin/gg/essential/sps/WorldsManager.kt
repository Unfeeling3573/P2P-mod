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
@file:UseSerializers(UuidAsStringSerializer::class, InstantAsMillisSerializer::class)

package gg.essential.sps

import gg.essential.connectionmanager.common.packet.response.ResponseActionPacket
import gg.essential.connectionmanager.common.packet.telemetry.ClientTelemetryPacket
import gg.essential.connectionmanager.common.packet.upnp.ClientUPnPSessionClosePacket
import gg.essential.connectionmanager.common.packet.upnp.ClientUPnPSessionCreatePacket
import gg.essential.connectionmanager.common.packet.upnp.ClientUPnPSessionInvitesAddPacket
import gg.essential.connectionmanager.common.packet.upnp.ClientUPnPSessionInvitesRemovePacket
import gg.essential.connectionmanager.common.packet.upnp.ClientUPnPSessionPingProxyUpdatePacket
import gg.essential.connectionmanager.common.packet.upnp.ServerUPnPSessionInviteAddPacket
import gg.essential.connectionmanager.common.packet.upnp.ServerUPnPSessionInviteAddResponsePacket
import gg.essential.connectionmanager.common.packet.upnp.ServerUPnPSessionInviteRemoveResponsePacket
import gg.essential.connectionmanager.common.packet.upnp.ServerUPnPSessionPopulatePacket
import gg.essential.connectionmanager.common.packet.upnp.ServerUPnPSessionRemovePacket
import gg.essential.event.network.server.ServerTickEvent
import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.add
import gg.essential.gui.elementa.state.v2.await
import gg.essential.gui.elementa.state.v2.awaitNotNull
import gg.essential.gui.elementa.state.v2.combinators.bimap
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.currentAndFutureValues
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.futureValues
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableListStateOf
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.removeAll
import gg.essential.gui.elementa.state.v2.withSetter
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.SPSNotificationId
import gg.essential.gui.notification.sendSpsInviteNotification
import gg.essential.model.util.InstantAsMillisSerializer
import gg.essential.network.CMConnection
import gg.essential.network.connectionmanager.common.model.ModLoaderType
import gg.essential.network.registerPacketHandler
import gg.essential.upnp.UPnPPrivacy
import gg.essential.upnp.model.UPnPSession
import gg.essential.util.Client
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.Sha256
import gg.essential.util.UIdentifier
import gg.essential.util.USession
import gg.essential.util.UuidAsStringSerializer
import gg.essential.util.mapValuesNotNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.isSameFileAs
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.text.removeSuffix
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.toKotlinDuration

abstract class WorldsManager(
    val cmConnection: CMConnection,
    val savesFolder: Path,
    val integratedServerManager: State<IntegratedServerManager?>,
) {
    private val refHolder = ReferenceHolderImpl()
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Client)

    private val worldManagers = mutableMapOf<Path, WeakReference<WorldManager>>()

    private val remoteSpsSessionsMutable = mutableListStateOf<UPnPSession>()
    val remoteSpsSessions: ListState<UPnPSession> = remoteSpsSessionsMutable

    val integratedServerWorld: State<WorldManager?> = memo {
        val server = integratedServerManager() ?: return@memo null
        getWorldManager(server.worldFolder)
    }

    init {
        effect(refHolder) {
            return@effect
        }
    }

    init {

        // Close hosted worlds if integrated server is no longer online or public
        var oldWorldManager: WorldManager? = integratedServerWorld.getUntracked()
        effect(refHolder) {
            val server = integratedServerManager()
            val worldManager = integratedServerWorld()
            if (worldManager != oldWorldManager) {
                oldWorldManager?.localShareSession?.set(null)
                oldWorldManager = worldManager
            }
            if (server == null || worldManager == null) return@effect
            if (!server.isServerPublic()) {
                worldManager.localShareSession.set(null)
            }
        }

        // Configure integrated server based on world settings
        effect(refHolder) {
            val server = integratedServerManager() ?: return@effect
            val worldManager = integratedServerWorld()!!
            worldManager.commitLocalGameRuleDiffFile(server.lastPlayed)
            server.setOpenToLanSource { worldManager.localWorldOpen() }
            server.setWhitelistSource { worldManager.localWorldInfo().invited + USession.active().uuid }
            val settings = worldManager.gameSettings
            server.setDefaultGameModeSource(memo { settings().gameMode.toISM() }.withSetter { update ->
                scope.launch {
                    worldManager.updateGameSettings { it.copy(gameMode = WorldGameMode.from(update(it.gameMode.toISM()))) }
                }
            })
            server.setDifficultySource(memo { settings().difficulty.toISM() }.withSetter { update ->
                scope.launch {
                    worldManager.updateGameSettings { it.copy(difficulty = WorldDifficulty.from(update(it.difficulty.toISM()))) }
                }
            })
            server.setDifficultyLockedSource(memo { settings().difficultyLocked }.withSetter { update ->
                scope.launch {
                    worldManager.updateGameSettings { it.copy(difficultyLocked = update(it.difficultyLocked)) }
                }
            })
            server.setGameRulesSource(memo { worldManager.gameRules().gamerules.mapValues { it.value.value } }.withSuspendingSetter { update ->
                worldManager.updateGameRules(update)
            })
            server.setCheatsEnabledSource { settings().cheats }
            server.setOpsSource { settings().ops }
        }

        effect(refHolder) {
            return@effect
        }

        // Sync shared worlds to infra as "UPnPSession"s
        val spsSession = memo {
            val world = integratedServerWorld() ?: return@memo null
            val shareSession = world.localShareSession() ?: return@memo null
            Pair(world, shareSession)
        }
        cmConnection.registerOnConnected {
            cmConnection.connectionScope.launch {
                syncSharedWorldInfoToInfra(spsSession)
            }
        }
        scope.launch {
            sendSharedWorldTelemetry(spsSession)
        }

        effect(refHolder) {
            return@effect
        }

        effect(refHolder) {
            return@effect
        }

        registerPackets()
    }

    private fun registerPackets() {

        cmConnection.registerPacketHandler<ServerUPnPSessionPopulatePacket> { packet ->
            remoteSpsSessionsMutable.set { orgList ->
                packet.sessions.fold(orgList) { list, session ->
                    val index = list.indexOfFirst { it.hostUUID == session.hostUUID }
                    if (index != -1) {
                        list.set(index, session)
                    } else {
                        list.add(session)
                    }
                }
            }
        }
        cmConnection.registerPacketHandler<ServerUPnPSessionRemovePacket> { packet ->
            remoteSpsSessionsMutable.removeAll { it.hostUUID in packet.hostUUIDs }
            packet.hostUUIDs.forEach {
                Notifications.removeNotificationById(SPSNotificationId(it))
            }
        }

        val cooldowns = mutableSetOf<UUID>()
        cmConnection.registerPacketHandler<ServerUPnPSessionInviteAddPacket> { packet ->
            val host = packet.hostUUID

            if (remoteSpsSessions.getUntracked().none { it.hostUUID == host }) return@registerPacketHandler
            if (platform.shouldHideNotificationForHost(host)) return@registerPacketHandler

            if (host in cooldowns) return@registerPacketHandler
            cooldowns.add(host)
            scope.launch { delay(7.seconds); cooldowns.remove(host) }

            sendSpsInviteNotification(host)
        }

        return
    }

    fun getLocalWorlds(): Flow<WorldManager> {
        return flow { emitAll(savesFolder.listDirectoryEntries().asFlow()) }
            // FIXME sort by last used so we load more relevant worlds first
            .filter { (it / "level.dat").exists() }
            .filterNot { false }
            .flowOn(Dispatchers.IO)
            .map { getWorldManager(it) }
            .flowOn(Dispatchers.Client)
    }

    fun getWorldManager(folder: Path): WorldManager {
        val normalizedFolder = folder.normalize()
        val existing = worldManagers[normalizedFolder]?.get()
        if (existing != null) {
            return existing
        }
        val worldManager = newWorldManager(normalizedFolder)
        worldManagers[normalizedFolder] = WeakReference(worldManager)
        return worldManager
    }

    suspend fun startLocalWorld(world: WorldManager): Boolean {
        check(true)
        startIntegratedServer(world)
        return true
    }

    private suspend fun syncSharedWorldInfoToInfra(spsSession: State<Pair<WorldManager, UUID>?>) {
        while (true) {
            val (world, session) = spsSession.awaitNotNull()

            val ip = SpsAddress(USession.activeNow().uuid).toString()
            val privacy = UPnPPrivacy.INVITE_ONLY
            val worldName = world.name.getUntracked()
            cmConnection.call(ClientUPnPSessionCreatePacket(ip, 0, privacy, platform.mcProtocolVersion,
                    worldName, platform.mcModLoader.infraModLoader))
                .ignoreUnexpected()
                .exponentialBackoff()
                .await<ServerUPnPSessionPopulatePacket>()

            // Update the server status before starting the invite loop so infra can serve the latest status to the invited players
            val statusJsonState = memo { integratedServerManager()?.statusResponseJson?.invoke() }
            val initialStatusJson = statusJsonState.awaitNotNull()
            cmConnection.call(ClientUPnPSessionPingProxyUpdatePacket(initialStatusJson))
                .ignoreUnexpected()
                .exponentialBackoff()
                .await<ResponseActionPacket>()

            coroutineScope {
                // Sync invites
                launch {
                    var sentInvites = emptySet<UUID>()
                    for (invites in memo { world.localWorldInfo().invited }.currentAndFutureValues()) {
                        val removed = sentInvites - invites
                        val added = invites - sentInvites
                        if (removed.isNotEmpty()) {
                            cmConnection.call(ClientUPnPSessionInvitesRemovePacket(removed))
                                .ignoreUnexpected()
                                .exponentialBackoff()
                                .await<ServerUPnPSessionInviteRemoveResponsePacket>()
                        }
                        if (added.isNotEmpty()) {
                            cmConnection.call(ClientUPnPSessionInvitesAddPacket(added))
                                .ignoreUnexpected()
                                .exponentialBackoff()
                                .await<ServerUPnPSessionInviteAddResponsePacket>()
                        }
                        sentInvites = invites
                    }
                }

                // Sync server status json
                launch {
                    for (statusJson in statusJsonState.futureValues(initialStatusJson)) {
                        if (statusJson == null) continue
                        cmConnection.call(ClientUPnPSessionPingProxyUpdatePacket(statusJson))
                            .ignoreUnexpected()
                            .exponentialBackoff()
                            .await<ResponseActionPacket>()
                    }
                }

                // Wait for world/session to be closed
                spsSession.await { it?.second != session }
                coroutineContext.cancelChildren()
            }

            cmConnection.call(ClientUPnPSessionClosePacket())
                .ignoreUnexpected()
                .exponentialBackoff()
                .await<ServerUPnPSessionRemovePacket>()
        }
    }

    private suspend fun sendSharedWorldTelemetry(spsSession: State<Pair<WorldManager, UUID>?>) {
        while (true) {
            val (world, session) = spsSession.awaitNotNull()

            val startTime = TimeSource.Monotonic.markNow()
            var maxConcurrentGuests = 0

            val tpsSessionMonitor = TPSSessionMonitor()
            val listener: (ServerTickEvent) -> Unit = { tpsSessionMonitor.tick() }
            platform.registerEventBusListener(ServerTickEvent::class.java, listener)

            // Wait for world to be closed
            coroutineScope {
                launch {
                    for (guests in memo { integratedServerManager()?.connectedGuests?.invoke()?.size ?: 0 }.currentAndFutureValues()) {
                        if (guests > maxConcurrentGuests) maxConcurrentGuests = guests
                    }
                }

                spsSession.await { it?.second != session }
                coroutineContext.cancelChildren()
            }

            platform.unregisterEventBusListener(ServerTickEvent::class.java, listener)

            val worldInfo = world.localWorldInfo.getUntracked()
            val data = mapOf(
                "sessionId" to session,
                "initiatedFrom" to "MAIN_MENU", // TODO once most sources are actually implemented: lastSessionInfo.startCause,
                "sessionDurationSeconds" to startTime.elapsedNow().inWholeSeconds,
                "maxConcurrentGuests" to maxConcurrentGuests,
                "inviteCount" to worldInfo.invited.size,
                "shareRP" to worldInfo.shareResourcePack,
                "worldNameHash" to Sha256.compute(
                    (USession.activeNow().uuid.toString() + world.localFolder.name).encodeToByteArray()
                ).hexStr,
                "worldSizeMb" to withContext(Dispatchers.IO) {
                    // FIXME use path api once stabilized (possibly in Kotlin 2.1)
                    world.localFolder.toFile()
                        .walk()
                        .onEnter { !it.toPath().isSymbolicLink() }
                        .sumOf { it.toPath().fileSize() }
                } / 1_000_000,
                "allocatedMemoryMb" to Runtime.getRuntime().maxMemory() / 1_000_000,
                "userCPU" to cpuInfo(),
                "averageTPS" to (tpsSessionMonitor.getAverageTPS()),
                "minTPS" to (tpsSessionMonitor.getMinTPS()),
                "maxTPS" to (tpsSessionMonitor.getMaxTPS()),
            )

            cmConnection.call(ClientTelemetryPacket("SPS_SESSION_4", data))
                .fireAndForget()
        }
    }

    protected abstract fun newWorldManager(localFolder: Path): WorldManager
    protected abstract suspend fun startIntegratedServer(worldManager: WorldManager): IntegratedServerManager
    protected abstract suspend fun disconnect()
    protected abstract suspend fun awaitDisconnected()

    protected abstract fun cpuInfo(): String

    companion object {
        private val LOGGER = LoggerFactory.getLogger(WorldManager::class.java)

    }
}

abstract class WorldManager(
    val worldsManager: WorldsManager,
    val cmConnection: CMConnection,
    val localFolder: Path,
) {
    private val localResourcePacksFolder = localFolder / LOCAL_RESOURCE_PACKS_FOLDER
    private val localWorldInfoFile = localFolder / LOCAL_WORLD_SETTINGS_FILE
    private val localGameRulesDiffFile = localFolder / LOCAL_GAME_RULES_DIFF_FILE
    private val localWorldInfoState = mutableStateOf(lazy { loadLocalWorldInfo() }) // TODO maybe load async somehow?
        .bimap({ it.value }, { lazy { it }})
    private val localGameRules = mutableStateOf(lazy { readMcWorldGameRules() })
        .bimap({ it.value }, { lazy { it }})
    private val localGameRuleDiffState = mutableStateOf(lazy { loadLocalGameRuleDiff() })
        .bimap({ it.value }, { lazy { it }})
    private val localWorldSummaryState = mutableStateOf(lazy { readMcWorldSummary() }) // TODO maybe load async somehow?
        .bimap({ it.value }, { lazy { it }})
    val localShareSession = mutableStateOf<UUID?>(null)
    val localWorldOpen = memo { localShareSession() != null }
    val localWorldInfo: State<LocalWorldInfo> = localWorldInfoState
    val localGameRuleDiff: State<GameRuleDiff?> = localGameRuleDiffState
    val localWorldSummary: State<WorldSummary> = localWorldSummaryState

    val owner: State<UUID> = memo { USession.active().uuid }

    val host: State<UUID?> = memo {
        when {
            worldsManager.integratedServerWorld() == this@WorldManager && localWorldOpen() -> USession.active().uuid
            else -> null
        }
    }

    /** List of player that are allowed to join this world (including the owner). They need not be online right now. */
    val members: State<Set<UUID>> = memo {
        when {
            else -> setOf(USession.active().uuid) + localWorldInfoState().invited
        }
    }

    /** List of players that are currently connected to this world (including host). */
    val connectedMembers: State<Set<UUID>> = memo {
        when {
            worldsManager.integratedServerWorld() == this@WorldManager && localWorldOpen() ->
                worldsManager.integratedServerManager()?.connectedPlayers()?.toSet() ?: emptySet()
            else -> emptySet()

        }
    }

    val name: State<String> = memo {
        when {
            else -> localWorldSummary().name
        }
    }

    val gameDisplayVersion: State<String> = memo {
        when {
            else -> localWorldSummary().gameDisplayVersion
        }
    }

    val gameModLoader: State<GameModLoader?> = memo {
        when {
            worldsManager.integratedServerWorld() == this@WorldManager -> platform.mcModLoader
            else -> null
        }
    }

    val gameSettings: State<GameWorldSettings> = memo {
        when {
            else -> localWorldInfo().gameSettings
        }
    }

    val gameRules: State<CWGameRules> = memo {
        when {
            else -> {
                val gameRules = localGameRules()
                val diff = localGameRuleDiffState()?.gameRuleDiff ?: return@memo gameRules
                gameRules.copy(gamerules = gameRules.gamerules.mapValues { (key, gameRule) ->
                    gameRule.copy(value = diff[key] ?: gameRule.value)
                })
            }
        }
    }

    val shareResourcePacks: State<Boolean> = memo {
        when {
            else -> localWorldInfo().shareResourcePack
        }
    }

    /**
     * List containing all enabled resource packs. The first pack has the highest priority, i.e. overwrites all others.
     *
     * Note that the contained [Deferred] values are started lazily (i.e. you must access them before they will actually
     * start loading) and may take a significant amount of time to complete (because they may have to download the
     * resource pack).
     *
     * If no resource pack is enabled, contains an empty list. The value `null` represents the loading state.
     */
    val resourcePackFiles: State<List<Triple<Sha256, ResourcePackName, Deferred<Path>>>?> = memo {
        when {
            else -> localWorldInfo().resourcePacks.map { (sha256, name) ->
                val path = CompletableDeferred(localResourcePacksFolder / (sha256.hexStr + ".zip"))
                Triple(sha256, name, path)
            }
        }
    }

    val worldIconBytes: State<Deferred<ByteArray?>?> = memo {
        when {
            else -> {
                localWorldSummary() // Update icon when the world summary changes
                worldsManager.scope.async(Dispatchers.IO, CoroutineStart.LAZY) {
                    try {
                        val path = localFolder.resolve("icon.png")
                        if (path.exists()) {
                            path.toFile().readBytes()
                        } else {
                            null
                        }
                    } catch (e: IOException) {
                        LOGGER.warn("Failed to read icon.png for world ${name.getUntracked()}", e)
                        null
                    }
                }
            }
        }
    }

    val localLastPlayed: State<Instant> = memo { localWorldSummary().lastPlayed }

    private fun loadLocalWorldInfo(): LocalWorldInfo {
        if (localWorldInfoFile.exists()) {
            val content = localWorldInfoFile.readText()
            return leanJson.decodeFromString(content)
        }
        val legacyWorldInfo = readLegacyLocalWorldInfo()
        if (legacyWorldInfo != null) {
            localWorldInfoFile.writeText(Json.encodeToString(legacyWorldInfo))
            return legacyWorldInfo
        }
        val defaultWorldInfo = LocalWorldInfo(emptySet(), true, emptyList(), readMcWorldSettings())
        localWorldInfoFile.writeText(Json.encodeToString(defaultWorldInfo))
        return defaultWorldInfo
    }

    private fun loadLocalGameRuleDiff(): GameRuleDiff? {
        if (!localGameRulesDiffFile.exists()) {
            return null
        }
        val content = localGameRulesDiffFile.readText()
        return leanJson.decodeFromString(content)
    }

    internal fun commitLocalGameRuleDiffFile(serverLastPlayed: Instant) {
        val gameRuleDiff = localGameRuleDiff.getUntracked() ?: return
        // We'll only apply the changes to the world if `lastPlayed` hasn't changed since we queued the changes.
        // If it did change, our queued changes may be very old, and we don't want to overwrite
        // anything the user may have manually changed in the world while Essential was not installed.
        if (gameRuleDiff.onChangeLastPlayed == serverLastPlayed) {
            updateGameRules { it + gameRuleDiff.gameRuleDiff }
        }
        localGameRulesDiffFile.deleteIfExists()
        localGameRuleDiffState.set(null)
    }

    protected abstract fun readLegacyLocalWorldInfo(): LocalWorldInfo?
    protected abstract fun readMcWorldSettings(): GameWorldSettings
    protected abstract fun readMcWorldGameRules(): CWGameRules
    protected abstract fun readMcWorldSummary(): WorldSummary

    fun sendInviteToMember(uuid: UUID) {
        worldsManager.scope.launch {
            if (uuid in localWorldInfo.getUntracked().invited) {
                cmConnection.call(ClientUPnPSessionInvitesRemovePacket(setOf(uuid)))
                    .ignoreUnexpected()
                    .exponentialBackoff()
                    .await<ServerUPnPSessionInviteRemoveResponsePacket>()
                cmConnection.call(ClientUPnPSessionInvitesAddPacket(setOf(uuid)))
                    .ignoreUnexpected()
                    .exponentialBackoff()
                    .await<ServerUPnPSessionInviteAddResponsePacket>()
            } else {
                LOGGER.warn("Unable to send invite to $uuid as they are not a member of the world.")
            }
        }
    }

    fun updateLocalWorldInfo(update: (LocalWorldInfo) -> LocalWorldInfo) {
        val newState = update(localWorldInfoState.getUntracked())
        newState.write(localFolder)
        localWorldInfoState.set(newState)
    }

    private fun updateLocalGameRulesDiff(update: (Map<UIdentifier, String>) -> Map<UIdentifier, String>) {
        // FIXME should be able to use `localLastPlayed` here, but that doesn't yet update properly
        val lastPlayed = readMcWorldSummary().lastPlayed
        val oldState = localGameRuleDiffState.getUntracked() ?: GameRuleDiff(mapOf(), lastPlayed)
        val newState = oldState.copy(gameRuleDiff = update(oldState.gameRuleDiff), onChangeLastPlayed = lastPlayed)
            .takeIf { it.gameRuleDiff.isNotEmpty() }
        if (newState == null) {
            localGameRulesDiffFile.deleteIfExists()
        } else {
            val tmpFile = localGameRulesDiffFile.resolveSibling(localGameRulesDiffFile.name + ".tmp")
            try {
                tmpFile.writeText(Json.encodeToString(newState))
                tmpFile.moveTo(localGameRulesDiffFile, overwrite = true)
            } finally {
                tmpFile.deleteIfExists()
            }
        }
        localGameRuleDiffState.set(newState)
    }

    suspend open fun updateLocalName(name: String) {
        localWorldSummaryState.set { it.copy(name = name)}
    }

    suspend fun updateName(name: String) {
        updateLocalName(name)
    }

    fun updateLocalGameSettings(update: (GameWorldSettings) -> GameWorldSettings) {
        updateLocalWorldInfo { it.copy(gameSettings = update(it.gameSettings)) }
    }

    suspend fun updateGameSettings(update: (GameWorldSettings) -> GameWorldSettings) {
        updateLocalGameSettings(update)
    }

    fun reloadGameRulesIfNecessary() {
        if (localGameRules.getUntracked().gamerules.isEmpty()) {
            localGameRules.set(readMcWorldGameRules())
        }
    }

    fun updateGameRules(update: (Map<UIdentifier, String>) -> Map<UIdentifier, String>) {
        if (worldsManager.integratedServerWorld.getUntracked() != this) {
            updateLocalGameRulesDiff { originalDiff ->
                val gameRules = gameRules.getUntracked().gamerules.mapValues { it.value.value }
                // Find the difference between the game rules and the updated rules and add that difference to the `originalDiff`
                originalDiff + update(gameRules).filter { (key, value) -> gameRules[key] != value }
            }
        } else {
            localGameRules.set { gameRules ->
                gameRules.copy(
                    gamerules = update(gameRules.gamerules.mapValues { it.value.value })
                        .mapValuesNotNull { gameRules.gamerules[it.key]?.copy(value = it.value) })
            }
        }
    }

    suspend fun updateShareResourcePacks(enabled: Boolean) {
        updateLocalWorldInfo { it.copy(shareResourcePack = enabled) }
    }

    suspend fun updateResourcePacks(packs: List<Pair<Path, ResourcePackName>>) {
        val newPacksList = mutableListOf<Pair<Sha256, ResourcePackName>>()
        withContext(Dispatchers.IO) {
            localResourcePacksFolder.createDirectories()

            for ((sourceFile, name) in packs) {
                if (sourceFile.parent.isSameFileAs(localResourcePacksFolder)) {
                    val sha256 = Sha256.fromOrNull(sourceFile.name.removeSuffix(".zip"))
                    if (sha256 != null) {
                        newPacksList.add(sha256 to name)
                        continue
                    }
                }
                val sha256 = sourceFile.inputStream().use { Sha256.compute(it) }
                val targetFile = localResourcePacksFolder.resolve(sha256.hexStr + ".zip")
                if (!targetFile.exists()) {
                    sourceFile.copyTo(targetFile)
                }
                newPacksList.add(sha256 to name)
            }
        }

        updateLocalWorldInfo { it.copy(resourcePacks = newPacksList) }

        withContext(Dispatchers.IO) {
            val usedPacks = newPacksList.map { it.first }.toSet()
            for (file in localResourcePacksFolder.listDirectoryEntries()) {
                if (Sha256.fromOrNull(file.name.removeSuffix(".zip")) !in usedPacks) {
                    file.deleteExisting()
                }
            }
        }
    }

    data class WorldSummary(
        val name: String,
        val gameDisplayVersion: String,
        val lastPlayed: Instant,
    )

    companion object {
        private val LOGGER = LoggerFactory.getLogger(WorldManager::class.java)

        val LOCAL_WORLD_SETTINGS_FILE = "essential-world-settings.json"
        val LOCAL_GAME_RULES_DIFF_FILE = "essential-game-rules-diff.json"
        val LOCAL_RESOURCE_PACKS_FOLDER = "essential-resource-packs"
    }
}

typealias FileSize = Long

private val leanJson = Json { ignoreUnknownKeys = true }

@Serializable
data class LocalWorldInfo(
    val invited: Set<UUID>,
    val shareResourcePack: Boolean, // TODO rename pack to be plural
    val resourcePacks: List<Pair<Sha256, ResourcePackName>> = emptyList(), // TODO remove default
    val gameSettings: GameWorldSettings,
) {
    fun write(dir: Path) {
        dir.createDirectories()
        val tmpFile = dir / "${WorldManager.LOCAL_WORLD_SETTINGS_FILE}.tmp"
        try {
            tmpFile.writeText(Json.encodeToString(this@LocalWorldInfo))
            tmpFile.moveTo(dir / WorldManager.LOCAL_WORLD_SETTINGS_FILE, overwrite = true)
        } finally {
            tmpFile.deleteIfExists()
        }
    }
}

@Serializable
data class GameRuleDiff(
    val gameRuleDiff: Map<UIdentifier, String>,
    // The last played time of the world when these game rules were changed
    val onChangeLastPlayed: Instant,
)

@Serializable
data class GameWorldSettings(
    val gameMode: WorldGameMode,
    val difficulty: WorldDifficulty,
    val difficultyLocked: Boolean = false, // TODO remove default,
    val cheats: Boolean,
    val ops: Set<UUID>,
)

@Serializable
enum class WorldGameMode {
    Survival,
    Creative,
    Adventure,
    Spectator,
}

@Serializable
enum class WorldDifficulty {
    Peaceful,
    Easy,
    Normal,
    Hard,
}

@Serializable
enum class GameModLoader(
    val infraModLoader: ModLoaderType
) {
    Fabric(ModLoaderType.FABRIC),
    NeoForge(ModLoaderType.NEOFORGE),
    Forge(ModLoaderType.FORGE),
    ;
}

fun ModLoaderType.toMod(): GameModLoader {
    return GameModLoader.entries.first { it.infraModLoader == this }
}

private fun WorldGameMode.toISM(): IntegratedServerManager.GameMode = when (this) {
    WorldGameMode.Survival -> IntegratedServerManager.GameMode.Survival
    WorldGameMode.Creative -> IntegratedServerManager.GameMode.Creative
    WorldGameMode.Adventure -> IntegratedServerManager.GameMode.Adventure
    WorldGameMode.Spectator -> IntegratedServerManager.GameMode.Spectator
}

private fun WorldDifficulty.toISM(): IntegratedServerManager.Difficulty = when (this) {
    WorldDifficulty.Peaceful -> IntegratedServerManager.Difficulty.Peaceful
    WorldDifficulty.Easy -> IntegratedServerManager.Difficulty.Easy
    WorldDifficulty.Normal -> IntegratedServerManager.Difficulty.Normal
    WorldDifficulty.Hard -> IntegratedServerManager.Difficulty.Hard
}

private fun WorldGameMode.Companion.from(value: IntegratedServerManager.GameMode): WorldGameMode = when (value) {
    IntegratedServerManager.GameMode.Survival -> WorldGameMode.Survival
    IntegratedServerManager.GameMode.Creative -> WorldGameMode.Creative
    IntegratedServerManager.GameMode.Adventure -> WorldGameMode.Adventure
    IntegratedServerManager.GameMode.Spectator -> WorldGameMode.Spectator
}

private fun WorldDifficulty.Companion.from(value: IntegratedServerManager.Difficulty): WorldDifficulty = when (value) {
    IntegratedServerManager.Difficulty.Peaceful -> WorldDifficulty.Peaceful
    IntegratedServerManager.Difficulty.Easy -> WorldDifficulty.Easy
    IntegratedServerManager.Difficulty.Normal -> WorldDifficulty.Normal
    IntegratedServerManager.Difficulty.Hard -> WorldDifficulty.Hard
}

// TODO rename to GameRules (and same for GameRule) once feature-flags-processor permits
@Serializable
data class CWGameRules(val gamerules: Map<UIdentifier, CWGameRule>)

@Serializable
data class CWGameRule(
    val category: String, // e.g. "gamerule.category.player" (Note: mods could be adding custom categories!)

    val type: Type,
    val defaultValue: String,
    val value: String,

    val categoryFallback: String, // e.g. "Player"
    val nameFallback: String, // e.g. "PvP"
    val descriptionFallback: String, // e.g. "Players can fight with other players."
) {
    @Serializable
    enum class Type {
        Boolean,
        Int,
        Unknown, // for modded types we can't understand
    }
}

typealias ResourcePackName = String

// TODO clean up old entries from time to time
internal fun resourcePackCachePath(sha256: Sha256): Path =
    platform.essentialBaseDir / "resourcepacks-cache" / (sha256.hexStr + ".zip")

private operator fun Instant.minus(other: Instant): Duration =
    java.time.Duration.between(other, this).toKotlinDuration()
