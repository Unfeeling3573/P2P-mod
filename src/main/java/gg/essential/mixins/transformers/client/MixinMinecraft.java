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
package gg.essential.mixins.transformers.client;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import gg.essential.Essential;
import gg.essential.event.client.ReAuthEvent;
import gg.essential.mixins.ext.server.integrated.IntegratedServerExt;
import gg.essential.sps.McIntegratedServerManager;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Session;
import gg.essential.mixins.impl.client.MinecraftExt;
import gg.essential.mixins.impl.client.MinecraftHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Objects;

import static gg.essential.util.HelpersKt.toUSession;

//#if MC >= 26.4
//$$ import net.minecraft.client.multiplayer.p2p.P2PManager;
//#endif

//#if MC >= 26.2
//$$ import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
//#endif

//#if MC>=12109
//$$ import com.llamalad7.mixinextras.sugar.Local;
//$$ import net.minecraft.util.ApiServices;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#endif

//#if MC == 1.21.5
//$$ import net.minecraft.client.gl.Framebuffer;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#endif

//#if MC>=12002
//$$ import com.mojang.authlib.yggdrasil.ProfileResult;
//$$ import net.minecraft.util.Util;
//$$ import java.util.concurrent.CompletableFuture;
//#endif

//#if MC>=11400
//#else
import gg.essential.event.gui.MouseScrollEvent;
import org.lwjgl.input.Mouse;
//#endif

//#if MC>=11700
//$$ import net.minecraft.client.network.SocialInteractionsManager;
//#if MC>=11903
//$$ import net.minecraft.client.util.ProfileKeysImpl;
//#endif
//#if MC>=11900
//$$ import net.minecraft.client.util.ProfileKeys;
//#endif
//#if MC>=11800
//$$ import com.mojang.authlib.minecraft.UserApiService;
//#else
//$$ import com.mojang.authlib.minecraft.SocialInteractionsService;
//$$ import com.mojang.authlib.minecraft.OfflineSocialInteractions;
//#endif
//#endif

@Mixin(Minecraft.class)
public abstract class MixinMinecraft implements MinecraftExt {

    private final MinecraftHook minecraftHook = new MinecraftHook((Minecraft) (Object) this);

    @Shadow @Mutable @Final private Session session;

    //#if MC>=12109
    //$$ @Shadow @Final private ApiServices apiServices;
    //#endif

    //#if MC>=12002
    //$$ @Shadow @Mutable @Final private CompletableFuture<ProfileResult> gameProfileFuture;
    //#if MC<12109
    //$$ @Shadow @Final private MinecraftSessionService sessionService;
    //#endif
    //#else
    @Shadow public abstract PropertyMap getProfileProperties();
    //#endif

    //#if MC >= 26.4
    //$$ @Shadow @Mutable @Final public P2PManager p2pManager;
    //#endif
    //#if MC >= 26.2
    //$$ @Shadow @Mutable @Final private RemoteFriendListUpdateHandler remoteFriendListUpdateHandler;
    //#endif
    //#if MC >= 1.20.4
    //$$ @Shadow @Mutable @Final private CompletableFuture<UserApiService.UserProperties> userPropertiesFuture;
    //#endif
    //#if MC>=11700
    //$$ @Shadow @Mutable @Final private SocialInteractionsManager socialInteractionsManager;
    //#if MC>=11900
    //#if MC>=12109
    //$$ @Unique
    //$$ private YggdrasilAuthenticationService authenticationService;
    //$$ @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;createUserApiService(Lcom/mojang/authlib/yggdrasil/YggdrasilAuthenticationService;Lnet/minecraft/client/RunArgs;)Lcom/mojang/authlib/minecraft/UserApiService;"))
    //$$ private YggdrasilAuthenticationService captureAuthService(YggdrasilAuthenticationService authenticationService) {
    //$$     this.authenticationService = authenticationService;
    //$$     return authenticationService;
    //$$ }
    //#else
    //$$ @Shadow @Mutable @Final private YggdrasilAuthenticationService authenticationService;
    //#endif
    //$$
    //$$ @Shadow @Mutable @Final private ProfileKeys profileKeys;
    //$$
    //$$ @Shadow @Final public File runDirectory;
    //#else
    //$$ @Shadow public abstract MinecraftSessionService getSessionService();
    //#endif
    //#if MC>=11800
    //$$ @Shadow @Mutable @Final private UserApiService userApiService;
    //#else
    //$$ @Shadow @Mutable @Final private SocialInteractionsService socialInteractionsService;
    //#endif
    //#endif

    /**
     * Invoked once the game is launching
     */
    //#if MC < 11400
    @Inject(method = "init", at = @At("HEAD"))
    //#elseif FABRIC
    //$$ @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;dataFixer:Lcom/mojang/datafixers/DataFixer;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    //#else
    //$$ @Inject(method = "<init>", at = @At(value = "ESSENTIAL:CONSTANT_IN_INIT", args = "stringValue=Backend library: {}"))
    //#endif
    private void preinit(CallbackInfo ci) {
        minecraftHook.preinit();
    }

    /**
     * Invoked once the game has finished launching
     */
    //#if MC < 11400
    @Inject(method = "init", at = @At(value = "INVOKE",
            //#if MC<=10809
            //$$ target = "Lnet/minecraft/client/particle/EffectRenderer;<init>(Lnet/minecraft/world/World;Lnet/minecraft/client/renderer/texture/TextureManager;)V"
            //#else
            target = "Lnet/minecraft/client/particle/ParticleManager;<init>(Lnet/minecraft/world/World;Lnet/minecraft/client/renderer/texture/TextureManager;)V"
            //#endif
    ))
    //#else
    //$$ @Inject(method = "<init>", at = @At(
        //#if MC<11400 || FABRIC
        //$$ value = "INVOKE", shift = At.Shift.AFTER,
        //#else
        //$$ value = "ESSENTIAL:AFTER_INVOKE_IN_INIT",
        //#endif
    //$$     target = "Lnet/minecraft/client/Minecraft;updateWindowSize()V"
    //$$ ))
    //#endif
    private void init(CallbackInfo ci) {
        minecraftHook.startGame();
    }

    //#if MC < 11400
    @Inject(method = "init", at = @At("RETURN"))
    //#else
    //$$ @Inject(method = "<init>", at = @At("RETURN"))
    //#endif
    private void postInit(CallbackInfo ci) {
        minecraftHook.postInit();
    }

    /**
     * Invoked every tick (every 50milliseconds)
     */
    @Inject(method = "runTick", at = @At("RETURN"))
    private void runTick(CallbackInfo ci) {
        minecraftHook.runTick();
    }

    //#if MC>=11400
    //$$ // Note: This is targeted after the cancelTasks call, such that any tasks we schedule from the event
    //$$ //       won't immediately be cancelled (which could otherwise result in the game locking up, e.g. when
    //$$ //       scheduling a resource pack reload which will then never finish).
    //#if MC>=12111
    //$$ @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;cancelTasks()V", shift = At.Shift.AFTER))
    //#elseif MC>=12005
    //$$ @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;cancelTasks()V", shift = At.Shift.AFTER))
    //#else
    //$$ @Inject(method = "unloadWorld(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;dropTasks()V", shift = At.Shift.AFTER))
    //#endif
    //$$ private void unloadWorld(CallbackInfo ci) {
    //$$     minecraftHook.disconnect();
    //$$ }
    //#else
    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void loadWorld(WorldClient worldClient, String message, CallbackInfo ci) {
        if (worldClient == null) {
            minecraftHook.disconnect();
        }
    }
    //#endif

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void shutdown(CallbackInfo ci) {
        minecraftHook.shutdown();
    }

    @Override
    public void setSession(Session session) {
        Session oldSession = this.session;

        //#if MC >= 26.2
        //$$ this.remoteFriendListUpdateHandler.close();
        //#endif
        //#if MC >= 26.4
        //$$ this.p2pManager.shutdown();
        //#endif

        this.session = session;

        //#if MC>=12002
        //#if MC>=12109
        //$$ MinecraftSessionService sessionService = this.apiServices.sessionService();
        //#else
        //$$ MinecraftSessionService sessionService = this.sessionService;
        //#endif
        //$$ this.gameProfileFuture = CompletableFuture.supplyAsync(() ->
        //$$     sessionService.fetchProfile(session.getUuidOrNull(), true), Util.getIoWorkerExecutor());
        //#else
        if (!Objects.equals(oldSession.getProfile().getId(), session.getProfile().getId())) {
            this.getProfileProperties().clear();
        }
        //#endif

        //#if MC>=11700
        //#if MC>=11900
        //$$ YggdrasilAuthenticationService authenticationService = this.authenticationService;
        //#else
        //$$ YggdrasilAuthenticationService authenticationService = ((YggdrasilMinecraftSessionService) this.getSessionService()).getAuthenticationService();
        //#endif
        //#if MC>=12004
        //$$ this.userApiService = authenticationService.createUserApiService(session.getAccessToken());
        //$$ this.userPropertiesFuture = CompletableFuture.supplyAsync(() -> {
        //$$     try {
        //$$         return this.userApiService.fetchProperties();
        //$$     } catch (AuthenticationException e) {
        //$$         Essential.logger.error("Failed to fetch user properties", e);
        //$$         return UserApiService.OFFLINE_PROPERTIES;
        //$$     }
        //$$ }, Util.getDownloadWorkerExecutor());
        //#else
        //$$ try {
        //#if MC>=11800
        //$$     this.userApiService = authenticationService.createUserApiService(session.getAccessToken());
        //#else
        //$$     this.socialInteractionsService = authenticationService.createSocialInteractionsService(session.getAccessToken());
        //#endif
        //$$ } catch (AuthenticationException e) {
        //$$     Essential.logger.error("Failed to verify authentication", e);
        //#if MC>=11800
        //$$     this.userApiService = UserApiService.OFFLINE;
        //#else
        //$$     this.socialInteractionsService = new OfflineSocialInteractions();
        //#endif
        //$$ }
        //#endif
        //#endif

        //#if MC >= 26.4
        //$$ this.p2pManager = new P2PManager((Minecraft) (Object) this, session);
        //#endif

        //#if MC >= 26.2
        //$$ var friendsService = authenticationService.createFriendsService(session.getAccessToken());
        //$$ this.remoteFriendListUpdateHandler = new RemoteFriendListUpdateHandler(friendsService, (Minecraft) (Object) this);
        //#endif

        //#if MC >= 26.2
        //$$ this.playerSocialManager = new PlayerSocialManager((Minecraft) (Object) this, this.userApiService, friendsService, this.remoteFriendListUpdateHandler);
        //#elseif MC>=11800
        //$$ this.socialInteractionsManager = new SocialInteractionsManager((MinecraftClient) (Object) this, this.userApiService);
        //#elseif MC>=11700
        //$$ this.socialInteractionsManager = new SocialInteractionsManager((MinecraftClient) (Object) this, this.socialInteractionsService);
        //#endif

        //#if MC >= 26.2
        //$$ if (this.playerSocialManager.isFriendListEnabled()) {
        //$$     this.remoteFriendListUpdateHandler.start();
        //$$ }
        //#endif

        //#if MC>=12002
        //$$ this.profileKeys = ProfileKeys.create(this.userApiService, session, this.runDirectory.toPath());
        //#elseif MC>=11903
        //$$ this.profileKeys = new ProfileKeysImpl(this.userApiService, session.getProfile().getId(), this.runDirectory.toPath());
        //#elseif MC>=11900
        //$$ this.profileKeys = new ProfileKeys(this.userApiService, session.getProfile().getId(), this.runDirectory.toPath());
        //#endif

        Essential.EVENT_BUS.post(new ReAuthEvent(toUSession(session)));
    }

    //#if MC>=11400
    //$$ // Already handled in MixinMouse
    //#else
    @Shadow public GuiScreen currentScreen;

    @ModifyConstant(
        //#if MC>=11200
        method = "runTickMouse",
        //#else
        //$$ method = "runTick",
        //#endif
        constant = @Constant(longValue = 200),
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;systemTime:J", opcode = Opcodes.GETFIELD),
            to = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;getEventDWheel()I")
        )
    )
    private long mouseScroll(long ret) {
        if (this.currentScreen == null && Mouse.getEventDWheel() != 0) {
            MouseScrollEvent event = new MouseScrollEvent(Mouse.getEventDWheel(), null);
            Essential.EVENT_BUS.post(event);
            if (event.isCancelled()) {
                ret = -1; // this effectively skips the if which would otherwise process the event
            }
        }
        return ret;
    }
    //#endif

    @Shadow @Nullable private IntegratedServer integratedServer;

    //#if MC>=11900
    //$$ private static final String LAUNCH_INTEGRATED_SERVER = "startIntegratedServer";
    //#elseif MC>=11802
    //$$ private static final String LAUNCH_INTEGRATED_SERVER = "startIntegratedServer(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;ZLnet/minecraft/client/MinecraftClient$WorldLoadAction;)V";
    //#elseif MC>=11600
    //$$ private static final String LAUNCH_INTEGRATED_SERVER = "startIntegratedServer(Ljava/lang/String;Lnet/minecraft/util/registry/DynamicRegistries$Impl;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/Minecraft$WorldSelectionType;)V";
    //#else
    private static final String LAUNCH_INTEGRATED_SERVER = "launchIntegratedServer";
    //#endif
    //#if MC>=12111
    //$$ private static final String STOP_INTEGRATED_SERVER = "disconnect(Lnet/minecraft/client/gui/screen/Screen;ZZ)V";
    //#elseif MC>=12005
    //$$ private static final String STOP_INTEGRATED_SERVER = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V";
    //#elseif MC>=11600
    //$$ private static final String STOP_INTEGRATED_SERVER = "unloadWorld(Lnet/minecraft/client/gui/screen/Screen;)V";
    //#else
    private static final String STOP_INTEGRATED_SERVER = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V";
    //#endif

    @Inject(method = {
        LAUNCH_INTEGRATED_SERVER,
        //$$ // Forge patches on these versions add `boolean creating` to end of arguments
        //#if FORGE && MC>=11600 && MC<11900
        //#if MC>=11802
        //$$ "doLoadLevel(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;ZLnet/minecraft/client/Minecraft$ExperimentalDialogType;Z)V",
        //#elseif MC>=11700
        //$$ "doLoadLevel(Ljava/lang/String;Lnet/minecraft/core/RegistryAccess$RegistryHolder;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/Minecraft$ExperimentalDialogType;Z)V",
        //#else
        //$$ "loadWorld(Ljava/lang/String;Lnet/minecraft/util/registry/DynamicRegistries$Impl;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/Minecraft$WorldSelectionType;Z)V",
        //#endif
        //#endif
    }, at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;integratedServer:Lnet/minecraft/server/integrated/IntegratedServer;", shift = At.Shift.AFTER))
    private void setIntegratedServerManager(CallbackInfo ci) {
        IntegratedServerExt ext = (IntegratedServerExt) this.integratedServer;
        assert ext != null;
        Essential.getInstance().getIntegratedServerManager().set(ext.getEssential$manager());
    }

    @Inject(method = STOP_INTEGRATED_SERVER, at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;integratedServer:Lnet/minecraft/server/integrated/IntegratedServer;", shift = At.Shift.AFTER))
    private void unsetIntegratedServerManager(CallbackInfo ci) {
        Essential.getInstance().getIntegratedServerManager().set((McIntegratedServerManager) null);
    }

    //#if MC == 1.21.5
    //$$ @Unique
    //$$ private Framebuffer framebufferOverride;
    //$$ @Override
    //$$ public void essential$setFramebufferOverride(Framebuffer framebuffer) {
    //$$     this.framebufferOverride = framebuffer;
    //$$ }
    //$$ @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    //$$ private void overrideFramebuffer(CallbackInfoReturnable<Framebuffer> ci) {
    //$$     Framebuffer override = framebufferOverride;
    //$$     if (override != null) {
    //$$         ci.setReturnValue(override);
    //$$     }
    //$$ }
    //#endif
}
