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
package gg.essential.key;

import gg.essential.Essential;
import gg.essential.config.EssentialConfig;
import gg.essential.elementa.WindowScreen;
import gg.essential.elementa.components.Window;
import gg.essential.elementa.components.inspector.Inspector;
import gg.essential.elementa.utils.OptionsKt;
import gg.essential.event.client.ClientTickEvent;
import gg.essential.gui.emotes.EmoteWheel;
import gg.essential.gui.friends.SocialMenu;
import gg.essential.gui.modals.QuickAccessModal;
import gg.essential.gui.overlay.Layer;
import gg.essential.gui.overlay.OverlayManagerImpl;
import gg.essential.gui.screenshot.components.ScreenshotBrowser;
import gg.essential.gui.wardrobe.Wardrobe;
import gg.essential.handlers.ZoomHandler;
import gg.essential.network.connectionmanager.ConnectionManager;
import gg.essential.network.connectionmanager.cosmetics.AssetLoader;
import gg.essential.network.connectionmanager.cosmetics.CosmeticsManager;
import gg.essential.network.cosmetics.Cosmetic;
import gg.essential.universal.UKeyboard;
import gg.essential.universal.UMinecraft;
import gg.essential.universal.UScreen;
import gg.essential.universal.wrappers.UPlayer;
import gg.essential.util.*;
import me.kbrewster.eventbus.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static gg.essential.gui.sps.InviteOrHostModalFlowKt.launchInviteOrHostModalFlow;

//#if MC >= 26.3
//$$ import com.mojang.blaze3d.platform.InputConstants;
//#endif

//#if MC>=12109
//$$ import net.minecraft.util.Identifier;
//#endif

public class EssentialKeybindingRegistry {
    //#if MC>=12109
    //$$ private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("essential", "general"));
    //#else
    public static final String CATEGORY = "Essential";
    //#endif

    //#if MC >= 26.3
    //$$ private static final int KEY_NONE = InputConstants.UNKNOWN.getValue();
    //$$ private static final int KEY_H = InputConstants.KEY_H;
    //$$ private static final int KEY_B = InputConstants.KEY_B;
    //$$ private static final int KEY_I = InputConstants.KEY_I;
    //$$ private static final int KEY_EQUALS = InputConstants.KEY_EQUALS;
    //$$ private static final int KEY_MINUS = InputConstants.KEY_MINUS;
    //$$ private static final int KEY_BACKSLASH = InputConstants.KEY_BACKSLASH;
    //$$ private static final int KEY_Z = InputConstants.KEY_Z;
    //$$ private static final int KEY_G = InputConstants.KEY_G;
    //$$ private static final int KEY_R = InputConstants.KEY_R;
    //$$ private static final int KEY_C = InputConstants.KEY_C;
    //#else
    private static final int KEY_NONE = UKeyboard.KEY_NONE;
    private static final int KEY_H = UKeyboard.KEY_H;
    private static final int KEY_B = UKeyboard.KEY_B;
    private static final int KEY_I = UKeyboard.KEY_I;
    private static final int KEY_EQUALS = UKeyboard.KEY_EQUALS;
    private static final int KEY_MINUS = UKeyboard.KEY_MINUS;
    private static final int KEY_BACKSLASH = UKeyboard.KEY_BACKSLASH;
    private static final int KEY_Z = UKeyboard.KEY_Z;
    private static final int KEY_G = UKeyboard.KEY_G;
    private static final int KEY_R = UKeyboard.KEY_R;
    private static final int KEY_C = UKeyboard.KEY_C;
    //#endif

    private static final EssentialKeybindingRegistry INSTANCE = new EssentialKeybindingRegistry();
    private EssentialKeybinding cosmetics_visibility_toggle;
    private EssentialKeybinding emote_wheel_open;
    private EssentialKeybinding quick_access_open;
    private EssentialKeybinding zoom;
    private boolean holdingChatPeek;

    public void refreshBinds() {
        boolean anyKeybindingsRegistered = false;
        for (EssentialKeybinding bind : EssentialKeybinding.ALL_BINDS) {
            if (bind.getRequiresEssentialFull() && !EssentialConfig.INSTANCE.getEssentialFull()) {
                bind.unregister();
                continue;
            }
            if (bind.isRegisteredWithMinecraft()) {
                // already registered, continue
                anyKeybindingsRegistered = true;
                continue;
            }

            anyKeybindingsRegistered = true;
            bind.register();
        }
        //#if MC<=11202
        if (!anyKeybindingsRegistered) {
            // A better name would be keyKeybindCategories
            KeyBinding.getKeybinds().remove(CATEGORY);
        } else {
            KeyBinding.getKeybinds().add(CATEGORY);
        }
        //#endif
    }

    // Note: This gets called incredibly early in Minecraft's initialization, be very careful what you do here.
    public KeyBinding[] registerKeyBinds(KeyBinding[] allBindings) {
        new EssentialKeybinding("ESSENTIAL_FRIENDS", CATEGORY, KEY_H).requiresEssentialFull().withInitialPress(() -> {
            if (!UKeyboard.isKeyDown(UKeyboard.KEY_F3) && UScreen.getCurrentScreen() == null) {
                GuiUtil.openScreen(SocialMenu.class, SocialMenu::new);
            }
        });

        EssentialKeybinding studio = new EssentialKeybinding("COSMETIC_STUDIO", CATEGORY, KEY_B).withInitialPress(() -> {
            if (!UKeyboard.isKeyDown(UKeyboard.KEY_F3) && UScreen.getCurrentScreen() == null) {
                GuiUtil.openScreen(Wardrobe.class, Wardrobe::new);
            }
        });

        new EssentialKeybinding("SCREENSHOT_MANAGER", CATEGORY, KEY_I).requiresEssentialFull().withInitialPress(() -> {
            if (!UKeyboard.isKeyDown(UKeyboard.KEY_F3) && UScreen.getCurrentScreen() == null) {
                GuiUtil.openScreen(ScreenshotBrowser.class, ScreenshotBrowser::new);
            }
        });


        if (System.getProperty("elementa.dev", "false").equals("true")) {
            new EssentialKeybinding("INSERT_INSPECTOR", CATEGORY, KEY_EQUALS, true).withInitialPress(() -> {
                if (UKeyboard.isShiftKeyDown()) {
                    OptionsKt.setElementaDebug(!OptionsKt.getElementaDebug());
                } else {
                    Window window = null;
                    GuiScreen openedScreen = GuiUtil.INSTANCE.openedScreen();
                    Layer layer = OverlayManagerImpl.INSTANCE.getHoveredLayer();
                    if (layer != null) {
                        window = layer.getWindow();
                    } else if (openedScreen instanceof WindowScreen) {
                        window = ((WindowScreen) openedScreen).getWindow();
                    } else {
                        return;
                    }

                    List<Inspector> inspectors = window.childrenOfType(Inspector.class);
                    if (inspectors.size() > 0) {
                        for (Inspector inspector : inspectors)
                            window.removeChild(inspector);
                    } else {
                        window.addChild(new Inspector(window));
                    }
                }
            });

            if (MinecraftUtils.INSTANCE.isDevelopment()) {
                new EssentialKeybinding("ESSENTIAL_DEBUG_KEY", CATEGORY, KEY_MINUS, true).withInitialPress(() ->
                    Essential.getInstance().debugKeyFunction());

                new EssentialKeybinding("TOGGLE_DEBUG", CATEGORY, KEY_BACKSLASH).withInitialPress(ExtensionsKt::toggleElementaDebug);
            }
        }

        int cosmeticToggleKey = KEY_NONE;
        cosmetics_visibility_toggle = new EssentialKeybinding("COSMETICS_VISIBILITY_TOGGLE", CATEGORY, cosmeticToggleKey, false).withInitialPress(() -> {
            if (OverlayManagerImpl.INSTANCE.getFocusedLayer() == null
                    && !EssentialConfig.INSTANCE.getDisableCosmetics()
            ) {
                EssentialConfig.INSTANCE.getOwnCosmeticsVisibleStateWithSource().set(pair ->
                        new kotlin.Pair<>(!pair.component1(), EssentialConfig.CosmeticsVisibilitySource.UserWithNotification)
                );
            }
        });

        EssentialKeybinding chatPeek = new EssentialKeybinding("CHAT_PEEK", CATEGORY, KEY_Z)
            .withRepeatedHold(() -> this.holdingChatPeek = true)
            .withRelease(() -> {
                this.holdingChatPeek = false;
                Minecraft.getMinecraft()
                    //#if MC >= 26.2
                    //$$ .gui
                    //#endif
                    .ingameGUI
                    .getChatGUI()
                    .resetScroll();
            });

        EssentialKeybinding invite = new EssentialKeybinding("INVITE_FRIENDS", CATEGORY, KEY_NONE)
            .withInitialPress(() -> {
                launchInviteOrHostModalFlow();
            });

        quick_access_open = new EssentialKeybinding("MENU_ACCESS", CATEGORY, KEY_G)
                .withInitialPress(QuickAccessModal::openInGame)
                .requiresEssentialFull();

        {
            emote_wheel_open = new EssentialKeybinding("EMOTE_WHEEL", CATEGORY, KEY_R).requiresEssentialFull()
                    .withRepeatedHold(() -> EmoteWheel.open()).withRelease(() -> EmoteWheel.emoteClicked = false);
            for (int i = 0; i < 8; i++) {
                int index = i;
                new EssentialKeybinding("EMOTE_SLOT_" + (i + 1), CATEGORY, KEY_NONE).requiresEssentialFull()
                        .withInitialPress(() -> {
                            ConnectionManager connectionManager = Essential.getInstance().getConnectionManager();
                            CosmeticsManager cosmeticsManager = connectionManager.getCosmeticsManager();

                            String emote = connectionManager.getEmoteWheelManager().getSelectedEmoteWheelSlots().getUntracked().get(index);
                            if (emote == null) {
                                return;
                            }

                            Cosmetic cosmetic = cosmeticsManager.getCosmetic(emote);
                            EntityPlayerSP player = UPlayer.getPlayer();
                            if (cosmetic != null && player != null) {
                                cosmeticsManager.getModelLoader().getModel(cosmetic, cosmetic.getDefaultVariantName(), AssetLoader.Priority.Blocking).whenCompleteAsync((model, throwable) -> {
                                    if (throwable == null && EmoteWheel.canEmote(player)) {
                                        EmoteWheel.Companion.equipEmote(model);
                                    }
                                }, ExtensionsKt.getExecutor(UMinecraft.getMinecraft()));
                            }
                        });
            }
        }

        if (!OptiFineUtil.isLoaded()) {
            zoom = new EssentialKeybinding("ZOOM", CATEGORY, KEY_C);
            zoom.requiresEssentialFull();
            ZoomHandler.getInstance().zoomKeybinding = zoom.keyBinding;
        }

        studio.requiresEssentialFull();
        cosmetics_visibility_toggle.requiresEssentialFull();
        chatPeek.requiresEssentialFull();
        invite.requiresEssentialFull();

        // We start with all bindings registered because we cannot at this point determine which ones we need, but we do
        // need them registered for them to load from the options.txt file.
        for (EssentialKeybinding binding : EssentialKeybinding.ALL_BINDS) {
            allBindings = binding.register(allBindings);
        }
        return allBindings;
    }

    @Subscribe
    public void tick(ClientTickEvent event) {
        for (EssentialKeybinding essentialKeybinding : EssentialKeybinding.ALL_BINDS) {
            essentialKeybinding.tickEvents();
        }
    }

    @NotNull
    public EssentialKeybinding getToggleCosmetics() {
        return cosmetics_visibility_toggle;
    }

    @NotNull
    public EssentialKeybinding getOpenEmoteWheel() {
        return emote_wheel_open;
    }

    @NotNull
    public EssentialKeybinding getOpenQuickAccess() {
        return quick_access_open;
    }

    public EssentialKeybinding getZoom() {
        return zoom;
    }

    public boolean isHoldingChatPeek() {
        return holdingChatPeek;
    }

    public static EssentialKeybindingRegistry getInstance() {
        return INSTANCE;
    }
}
