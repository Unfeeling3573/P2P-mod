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
package gg.essential.network.connectionmanager.sps;

import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.sparkuniverse.toolbox.util.DateTime;
import gg.essential.Essential;
import gg.essential.commands.EssentialCommandRegistry;
import gg.essential.compat.PlasmoVoiceCompat;
import gg.essential.connectionmanager.common.packet.upnp.*;
import gg.essential.data.SPSData;
import gg.essential.event.network.server.ServerLeaveEvent;
import gg.essential.event.network.server.ServerTickEvent;
import gg.essential.event.render.RenderTickEvent;
import gg.essential.event.sps.PlayerJoinSessionEvent;
import gg.essential.event.sps.PlayerLeaveSessionEvent;
import gg.essential.event.sps.SPSStartEvent;
import gg.essential.gui.elementa.state.v2.MutableState;
import gg.essential.gui.elementa.state.v2.State;
import gg.essential.gui.elementa.state.v2.StateKt;
import gg.essential.gui.friends.state.IStatusManager;
import gg.essential.gui.multiplayer.EssentialMultiplayerGui;
import gg.essential.mixins.transformers.server.integrated.LanConnectionsAccessor;
import gg.essential.network.connectionmanager.ConnectionManager;
import gg.essential.network.connectionmanager.NetworkedManager;
import gg.essential.network.connectionmanager.StateCallbackManager;
import gg.essential.network.connectionmanager.common.model.ModLoaderType;
import gg.essential.network.connectionmanager.handler.upnp.ServerUPnPSessionInviteAddPacketHandler;
import gg.essential.network.connectionmanager.handler.upnp.ServerUPnPSessionPopulatePacketHandler;
import gg.essential.network.connectionmanager.handler.upnp.ServerUPnPSessionRemovePacketHandler;
import gg.essential.network.connectionmanager.queue.PacketQueue;
import gg.essential.network.connectionmanager.queue.SequentialPacketQueue;
import gg.essential.sps.ResourcePackSharingHttpServer;
import gg.essential.sps.TPSSessionMonitor;
import gg.essential.sps.WindowTitleManager;
import gg.essential.sps.SpsAddress;
import gg.essential.universal.UMinecraft;
import gg.essential.upnp.UPnPPrivacy;
import gg.essential.upnp.model.UPnPSession;
import gg.essential.util.*;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;
import me.kbrewster.eventbus.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.server.management.UserListOps;
import net.minecraft.server.management.UserListWhitelist;
import net.minecraft.server.management.UserListWhitelistEntry;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

//#if MC >= 26.2
//$$ import net.minecraft.server.MinecraftServer;
//#endif

//#if MC>=12111
//$$ import net.minecraft.world.rule.GameRule;
//#endif

//#if MC>=12109
//$$ import net.minecraft.server.PlayerConfigEntry;
//#endif

//#if MC>11202
//$$ import net.minecraft.world.World;
//$$ import net.minecraft.world.storage.FolderName;
//$$ import net.minecraft.world.storage.IServerWorldInfo;
//#endif

//#if MC>=11200
import static gg.essential.util.HelpersKt.textTranslatable;
//#else
//$$ import net.minecraft.client.resources.I18n;
//#endif

import static gg.essential.util.ExtensionsKt.getExecutor;

/**
 * SinglePlayer Sharing Manager
 */
public class SPSManager extends StateCallbackManager<IStatusManager> implements NetworkedManager {

    @NotNull
    private final ConnectionManager connectionManager;
    @NotNull
    private final PacketQueue updateQueue;
    @NotNull
    private final Object whitelistSemaphore = new Object();

    @NotNull
    private final Map<UUID, UPnPSession> remoteSessions = Maps.newConcurrentMap();

    @Nullable
    private UPnPSession localSession;
    @Nullable
    private SPSSessionSource localSessionSource;
    private GameType currentGameMode;
    private boolean allowCheats;
    private EnumDifficulty difficulty;
    private boolean difficultyLocked;
    @Nullable
    private String serverStatusResponse;

    private final Set<UUID> oppedPlayers = new HashSet<>();
    private final Map<UUID, MutableState<Boolean>> onlinePlayerStates = new HashMap<>();

    private boolean shareResourcePack = false;

    @Nullable
    private ResourcePackSharingHttpServer.PackInfo packInfo;
    private String resourcePackUrl = null; // Used by 1.19+ in Mixin_IntegratedServerResourcePack
    private String resourcePackChecksum = null;

    private Instant sessionStartTime = Instant.now();

    private TPSSessionMonitor tpsSessionMonitor = null;

    /**
     * A random ID for each session, used for telemetry purposes.
     */
    private UUID sessionId = null;

    /**
     * The maximum number of concurrent guests that connected during the session
     */
    private int maxConcurrentGuests = 0;

    //#if FABRIC
    //$$ private final ModLoaderType modLoader = ModLoaderType.FABRIC;
    //#elseif NEOFORGE
    //$$ private final ModLoaderType modLoader = ModLoaderType.NEOFORGE;
    //#elseif FORGE
    private final ModLoaderType modLoader = ModLoaderType.FORGE;
    //#endif

    public SPSManager(@NotNull final ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;

        this.updateQueue = new SequentialPacketQueue.Builder(connectionManager)
            .onTimeoutRetransmit()
            .create();

        connectionManager.registerPacketHandler(ServerUPnPSessionInviteAddPacket.class, new ServerUPnPSessionInviteAddPacketHandler());
        connectionManager.registerPacketHandler(ServerUPnPSessionPopulatePacket.class, new ServerUPnPSessionPopulatePacketHandler());
        connectionManager.registerPacketHandler(ServerUPnPSessionRemovePacket.class, new ServerUPnPSessionRemovePacketHandler());

        Runtime.getRuntime().addShutdownHook(new Thread(this::closeLocalSession)); // cleaning up UPnP if we can
    }

    public boolean isAllowCheats() {
        return allowCheats;
    }

    @Nullable
    public UPnPSession getRemoteSession(UUID hostUUID) {
        return this.remoteSessions.get(hostUUID);
    }

    public void addRemoteSession(@NotNull UPnPSession session) {
        this.remoteSessions.put(session.getHostUUID(), session);

        EssentialMultiplayerGui gui = EssentialMultiplayerGui.getInstance();
        if (gui != null) {
            gui.updateSpsSessions();
        }
        for (IStatusManager manager : getCallbacks()) {
            manager.refreshActivity(session.getHostUUID());
        }
    }

    public void removeRemoteSession(@NotNull UUID hostUUID) {
        this.remoteSessions.remove(hostUUID);
        for (IStatusManager manager : getCallbacks()) {
            manager.refreshActivity(hostUUID);
        }
        EssentialMultiplayerGui gui = EssentialMultiplayerGui.getInstance();
        if (gui != null) {
            gui.updateSpsSessions();
        }
    }

    @NotNull
    public Set<UUID> getInvitedUsers() {
        UPnPSession session = this.localSession;
        return session != null ? session.getInvites() : Collections.emptySet();
    }

    private void sendInvites(Set<UUID> invited) {
        if (invited.isEmpty()) {
            return;
        }

        this.updateQueue.enqueue(new ClientUPnPSessionInvitesAddPacket(invited));
    }

    private void revokeInvites(Set<UUID> removed) {
        if (removed.isEmpty()) {
            return;
        }

        this.updateQueue.enqueue(new ClientUPnPSessionInvitesRemovePacket(removed));
    }

    public synchronized void updateInvitedUsers(Set<UUID> invited) {
        if (this.localSession == null) {
            throw new IllegalStateException("Cannot update invites while no session is active.");
        }

        // Copy the set
        invited = new HashSet<>(invited);

        // Remove the client UUID, so we don't end up inviting ourselves
        invited.remove(UUIDUtil.getClientUUID());

        invited = Collections.unmodifiableSet(invited);

        revokeInvites(SetsKt.minus(this.localSession.getInvites(), invited));
        sendInvites(SetsKt.minus(invited, this.localSession.getInvites()));

        this.localSession = new UPnPSession(
            this.localSession.getHostUUID(),
            this.localSession.getIp(),
            this.localSession.getPort(),
            this.localSession.getPrivacy(),
            invited,
            this.localSession.getCreatedAt(),
            MinecraftUtils.getCurrentProtocolVersion(),
            MinecraftUtils.INSTANCE.getWorldName(),
            modLoader
        );
        Multithreading.runAsync(this::refreshWhitelist);

        persistSettings();
    }

    @Nullable
    public UPnPSession getLocalSession() {
        return this.localSession;
    }

    public EnumDifficulty getDifficulty() {
        return difficulty;
    }

    public boolean isDifficultyLocked() {
        return difficultyLocked;
    }

    public void startLocalSession(SPSSessionSource sessionSource) {
        sessionStartTime = Instant.now();
        sessionId = UUID.randomUUID();
        currentGameMode = GameType.ADVENTURE; // This is just a dummy value that will be updated later.
        this.localSessionSource = sessionSource;
        this.maxConcurrentGuests = 0;

        Multithreading.runAsync(ResourcePackSharingHttpServer.INSTANCE::startServer); // Load the class to start it
        updateResourcePack(packInfo); // Applies the current pack to the integrated server

        IntegratedServer server = UMinecraft.getMinecraft().getIntegratedServer();
        if (server == null) {
            return;
        }

        //#if MC>=11602
        //$$ World world = server.getWorld(World.OVERWORLD);
        //#else
        World world = server.getWorld(0);
        //#endif

        //#if MC>=11602
        //$$ IServerWorldInfo worldInfo = (IServerWorldInfo) world.getWorldInfo();
        //#else
        WorldInfo worldInfo = world.getWorldInfo();
        //#endif

        this.allowCheats = worldInfo.areCommandsAllowed();
        this.difficulty = worldInfo.getDifficulty();
        this.difficultyLocked = worldInfo.isDifficultyLocked();

        //#if MC>=12109
        //$$ server.setUseAllowlist(true);
        //#else
        server.getPlayerList().setWhiteListEnabled(true);
        //#endif

        updateOppedPlayers(new HashSet<>(), false);

        // We pass `false` for `allowCheats` to ensure that not everybody can enable commands.
        // This option by default will allow anyone to use operator commands, without being explicitly
        // added as operator.
        //#if MC>=11400
        //$$ int port = net.minecraft.util.HTTPUtil.getSuitableLanPort();
        //$$ boolean success = server.shareToLAN(
            //#if MC >= 26.2
            //$$ MinecraftServer.MultiplayerScope.LAN,
            //#endif
            //#if MC >= 26.3
            //#elseif MC >= 26.2
            //$$ null,
            //#else
            //$$ currentGameMode,
            //#endif
        //$$     false,
        //$$     port
        //$$ );
        //$$ if (!success) {
        //$$     return;
        //$$ }
        //#else
        String portStr = server.shareToLAN(currentGameMode, false);
        // Method inappropriately marked as non-null by Forge
        //noinspection ConstantConditions
        if (portStr == null) {
            return;
        }
        int port = Integer.parseInt(portStr);
        //#endif

        {
            // Simple Voice Chat documentation claims that by default it uses port 24454, but it seems they actually
            // use the integrated server port by default. That's probably a good default as well
            int voicePort = port;

            // Plasmo Voice has 2 major versions, 1.x (using the modid plasmo_voice) and 2.x (using the modid plasmovoice)
            if (ModLoaderUtil.INSTANCE.isModLoaded("plasmo_voice")) {
                // Plasmo 1.x documentation claims that it uses the server port by default, but it seems
                // that they actually use 60606 for the integrated server.
                voicePort = 60606;
            } else if (ModLoaderUtil.INSTANCE.isModLoaded("plasmovoice")) {
                // Plasmo 2.x uses a random port, so we use their API to get the port.
                Optional<Integer> plasmoPort = PlasmoVoiceCompat.getPort();
                if (plasmoPort.isPresent()) {
                    voicePort = plasmoPort.get();
                }
            }
            connectionManager.getIceManager().setVoicePort(voicePort);
        }

        String address = new SpsAddress(UUIDUtil.getClientUUID()).toString();

        this.updateLocalSession(address, 0);

        Essential.EVENT_BUS.post(new SPSStartEvent(address));
        EssentialCommandRegistry.INSTANCE.registerSPSHostCommands();

        WindowTitleManager.INSTANCE.updateTitle();

        tpsSessionMonitor = new TPSSessionMonitor();
    }

    public synchronized void updateLocalSession(@NotNull String ip, int port) {
        // Currently all sessions are locked into invite only
        UPnPPrivacy privacy = UPnPPrivacy.INVITE_ONLY;

        int protocolVersion = MinecraftUtils.getCurrentProtocolVersion();
        String worldName = MinecraftUtils.INSTANCE.getWorldName();

        UPnPSession oldSession = this.localSession;
        UPnPSession session = new UPnPSession(
            UUIDUtil.getClientUUID(),
            ip,
            port,
            privacy,
            oldSession != null ? oldSession.getInvites() : Collections.emptySet(),
            oldSession != null ? oldSession.getCreatedAt() : new DateTime(),
            protocolVersion,
            worldName,
            modLoader
        );

        if (this.localSession == null) {
            this.updateQueue.enqueue(new ClientUPnPSessionCreatePacket(ip, port, privacy, protocolVersion, worldName, modLoader));
        } else {
            this.updateQueue.enqueue(new ClientUPnPSessionUpdatePacket(ip, port, privacy));
        }

        this.localSession = session;
        Multithreading.runAsync(this::refreshWhitelist);
    }

    public synchronized void closeLocalSession() {
    }

    @Subscribe
    private void checkIfClosedByThirdParty(RenderTickEvent event) {
        if (localSession == null) return;

        IntegratedServer server = Minecraft.getMinecraft().getIntegratedServer();
        if (server == null || !server.getPublic()) {
            closeLocalSession();
        }
    }

    // Called from server main thread
    public void updateServerStatusResponse(@NotNull String updatedResponse) {
        if (this.localSession == null) {
            return;
        }

        if (updatedResponse.equals(this.serverStatusResponse)) {
            return;
        }
        this.serverStatusResponse = updatedResponse;

        ExtensionsKt.getExecutor(Minecraft.getMinecraft()).execute(() ->
            this.updateQueue.enqueue(new ClientUPnPSessionPingProxyUpdatePacket(updatedResponse))
        );
    }

    public void refreshWhitelist() {
        // There must only be one call to doRefreshWhitelist active at any time so we do not get any races between the
        // point where they retrieve the invited users and where they apply them (or rather, where they enter the server
        // task queue).
        synchronized (this.whitelistSemaphore) {
            this.doRefreshWhitelist();
        }
    }

    private void doRefreshWhitelist() {
        UPnPSession session = this.localSession;
        if (session == null) {
            return;
        }

        Set<UUID> invited;
        if (session.getPrivacy() == UPnPPrivacy.INVITE_ONLY) {
            invited = session.getInvites();
        } else /* session.getPrivacy() == UPnPPrivacy.FRIENDS */ {
            invited = new HashSet<>(this.connectionManager.getRelationshipManager().getFriends().keySet());
        }

        // Cache all user names so we do not unnecessarily block the server thread
        CollectionsKt.map(invited, UUIDUtil::getName).forEach(CompletableFuture::join);

        IntegratedServer server = UMinecraft.getMinecraft().getIntegratedServer();
        if (server == null) {
            return;
        }

        // Sync server whitelist with our list
        getExecutor(server).execute(() -> {
            UserListWhitelist whitelist = server.getPlayerList().getWhitelistedPlayers();
            for (String userName : whitelist.getKeys()) {
                //#if MC>=12109
                //$$ GameProfile profile = server.getApiServices().nameToIdCache().findByName(userName).map(it -> new GameProfile(it.id(), it.name())).orElse(null);
                //#elseif MC>=11701
                //$$ GameProfile profile = server.getUserCache().findByName(userName).orElse(null);
                //#else
                GameProfile profile = server.getPlayerProfileCache().getGameProfileForUsername(userName);
                //#endif
                if (profile != null && !invited.contains(profile.getId())) {
                    //#if MC>=12109
                    //$$ whitelist.remove(new PlayerConfigEntry(profile));
                    //#else
                    whitelist.removeEntry(profile);
                    //#endif
                }
            }
            for (UUID uuid : invited) {
                String userName = UUIDUtil.getName(uuid).join();
                //#if MC>=12109
                //$$ PlayerConfigEntry profile = new PlayerConfigEntry(uuid, userName);
                //#else
                GameProfile profile = new GameProfile(uuid, userName);
                //#endif
                //noinspection ConstantConditions forge is stupid
                if (whitelist.getEntry(profile) == null) {
                    whitelist.addEntry(new UserListWhitelistEntry(profile));
                }
            }

            // Kick anyone who is not on the whitelist
            for (EntityPlayerMP entity : ((LanConnectionsAccessor) server.getPlayerList()).getPlayerEntityList()) {
                if (!invited.contains(entity.getUniqueID()) && !UUIDUtil.getClientUUID().equals(entity.getUniqueID())) {
                    //#if MC>11200
                    entity.connection.disconnect(textTranslatable("multiplayer.disconnect.server_shutdown"));
                    //#else
                    //$$ entity.playerNetServerHandler.kickPlayerFromServer(
                    //$$     I18n.format("multiplayer.disconnect.server_shutdown")
                    //$$ );
                    //#endif
                }
            }
        });
    }

    @Subscribe
    private void onDisconnect(ServerLeaveEvent event) {
        closeLocalSession();
    }

    @Override
    public synchronized void onConnected() {
        this.updateQueue.reset();

        UPnPSession session = this.localSession;
        if (session != null) {
            this.updateQueue.enqueue(new ClientUPnPSessionCreatePacket(
                session.getIp(),
                session.getPort(),
                session.getPrivacy(),
                session.getProtocolVersion(),
                session.getWorldName(),
                modLoader
            ));
            this.updateQueue.enqueue(new ClientUPnPSessionInvitesAddPacket(session.getInvites()));
            String serverStatusResponse = this.serverStatusResponse;
            if (serverStatusResponse != null) {
                this.updateQueue.enqueue(new ClientUPnPSessionPingProxyUpdatePacket(serverStatusResponse));
            }
        }

        resetState();
    }

    @Override
    public void resetState() {
        this.remoteSessions.clear();
    }

    private void persistSettings() {
        IntegratedServer integratedServer = UMinecraft.getMinecraft().getIntegratedServer();
        if (integratedServer != null) {
            SPSData.SPSSettings spsSettings = new SPSData.SPSSettings(
                    this.currentGameMode,
                    this.difficulty,
                    this.difficultyLocked,
                    this.allowCheats,
                    this.getInvitedUsers(),
                    this.shareResourcePack,
                    this.oppedPlayers
            );
            SPSData.INSTANCE.saveSPSSettings(spsSettings, ExtensionsKt.getWorldDirectory(integratedServer));
        }
    }

    public void updateOppedPlayers(Set<UUID> oppedPlayers) {
        updateOppedPlayers(oppedPlayers, true);
    }

    private void updateOppedPlayers(Set<UUID> oppedPlayers, boolean persistSettings) {
        final IntegratedServer integratedServer = Minecraft.getMinecraft().getIntegratedServer();
        if (integratedServer == null) {
            throw new IllegalStateException("No local session is currently active.");
        }

        this.oppedPlayers.clear();
        this.oppedPlayers.addAll(oppedPlayers);

        HashSet<UUID> immutableOppedPlayers = new HashSet<>();
        if (this.allowCheats) {
            immutableOppedPlayers.addAll(this.oppedPlayers);
            immutableOppedPlayers.add(UUIDUtil.getClientUUID());
        }

        if (persistSettings) {
            persistSettings();
        }

        getExecutor(integratedServer).execute(() -> {
            final PlayerList playerList = integratedServer.getPlayerList();
            final UserListOps opList = playerList.getOppedPlayers();

            List<GameProfile> allProfiles = Arrays.stream(opList.getKeys())
                //#if MC>=12109
                //$$ .map(username -> integratedServer.getApiServices().nameToIdCache().findByName(username).map(it -> new GameProfile(it.id(), it.name())))
                //#else
                .map(username -> integratedServer.getPlayerProfileCache().getGameProfileForUsername(username))
                //#endif
                    //#if MC>=11700
                    //$$ .filter(Optional::isPresent).map(Optional::get)
                    //#endif
                    .collect(Collectors.toList());

            // Remove all players that are no longer op
            for (GameProfile profile : allProfiles) {
                if (!immutableOppedPlayers.contains(profile.getId())) {
                    //#if MC>=12109
                    //$$ playerList.removeFromOperators(new PlayerConfigEntry(profile));
                    //#else
                    playerList.removeOp(profile);
                    //#endif
                }
            }

            // Op all new players
            for (UUID oppedPlayer : immutableOppedPlayers) {

                //#if MC>=12109
                //$$ PlayerConfigEntry gameProfile = new PlayerConfigEntry(oppedPlayer, UUIDUtil.getName(oppedPlayer).join());
                //#else
                GameProfile gameProfile = new GameProfile(oppedPlayer, UUIDUtil.getName(oppedPlayer).join());
                //#endif

                if (opList.getEntry(gameProfile) == null) {
                    playerList.addOp(gameProfile);
                }
            }
        });
    }

    /**
     * @return a set of players that were opped by the host.
     * Please check `allowCheats` to see if returned players are opped on the server currently.
     */
    public Set<UUID> getOppedPlayers() {
        return oppedPlayers;
    }

    /**
     * @param uuid UUID player to get the online state of
     * @return a state with value of whether the specified player is online or not
     */
    public State<Boolean> getOnlineState(UUID uuid) {
        if (uuid.equals(UUIDUtil.getClientUUID())) {
            return StateKt.stateOf(true);
        }
        return onlinePlayerStates.computeIfAbsent(uuid, k -> StateKt.mutableStateOf(false));
    }

    @Subscribe
    public void onPlayerJoinSession(PlayerJoinSessionEvent event) {
        onlinePlayerStates.computeIfAbsent(event.getProfile().getId(), k -> StateKt.mutableStateOf(true)).set(true);
        maxConcurrentGuests = (int) Math.max(maxConcurrentGuests, onlinePlayerStates.values().stream().filter(State::getUntracked).count());
    }

    @Subscribe
    public void onPlayerLeaveSession(PlayerLeaveSessionEvent event) {
        final MutableState<Boolean> onlineState = onlinePlayerStates.remove(event.getProfile().getId());
        if (onlineState != null) {
            onlineState.set(false);
        }
    }

    @Subscribe
    public void onServerTick(ServerTickEvent event) {
        if (tpsSessionMonitor != null) {
            tpsSessionMonitor.tick();
        }
    }

    public boolean isShareResourcePack() {
        return shareResourcePack;
    }

    public void setShareResourcePack(boolean shareResourcePack) {
        this.shareResourcePack = shareResourcePack;
        if (shareResourcePack) {
            ResourcePackSharingHttpServer.INSTANCE.onShareResourcePackEnable();
        }
        updateResourcePack(packInfo); // Update to either populate or empty the resource pack on the integrated server
        persistSettings();
    }

    public void updateResourcePack(@Nullable ResourcePackSharingHttpServer.PackInfo info) {
        this.packInfo = info;
        final IntegratedServer integratedServer = Minecraft.getMinecraft().getIntegratedServer();
        if (integratedServer != null) {
            if (packInfo == null || !shareResourcePack) {
                setServerResourcePack(null, null);
            } else {
                // We build url of the form `http://UUID.essential-sps/HASH` where `UUID` is the host's UUID and `HASH`
                // is the checksum of the resource pack to be loaded.
                // There's no paricular reason we chose that host, any magic value would do, it's replaced on the client
                // side, but this one feels most appropriate given it also matches the meaning of the url.
                // The resource pack hash is included because MC caches resource packs by url and we don't want it to
                // have re-download the whole thing every time when merely switching between two packs.
                String url = "http://" + new SpsAddress(UUIDUtil.getClientUUID()) + "/" + packInfo.getChecksum();
                setServerResourcePack(url, packInfo.getChecksum());
            }
        }
    }

    private void setServerResourcePack(String url, String checksum) {
        this.resourcePackUrl = url;
        this.resourcePackChecksum = checksum;
        // Resource pack is handled by Mixin_IntegratedServerResourcePack on 1.19+
        //#if MC<11900
        final IntegratedServer integratedServer = Minecraft.getMinecraft().getIntegratedServer();
        if (integratedServer == null) {
            return;
        }
        if (url == null || checksum == null) {
            integratedServer.setResourcePack("", "");
        } else {
            integratedServer.setResourcePack(url, checksum);
        }
        //#endif
    }

    public String getResourcePackUrl() {
        return resourcePackUrl;
    }

    public String getResourcePackChecksum() {
        return resourcePackChecksum;
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
