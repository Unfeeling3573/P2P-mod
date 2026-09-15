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

import com.google.common.collect.ImmutableMap;
import gg.essential.Essential;
import gg.essential.api.utils.GuiUtil;
import gg.essential.config.EssentialConfig;
import gg.essential.event.client.ClientTickEvent;
import gg.essential.event.gui.GuiKeyTypedEvent;
import gg.essential.mixins.transformers.client.options.GameOptionsAccessor;
import gg.essential.mixins.transformers.client.options.KeyBindingAccessor;
import gg.essential.universal.UKeyboard;
import gg.essential.universal.UMinecraft;
import gg.essential.util.GuiEssentialPlatform;
import me.kbrewster.eventbus.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;

//#if MC >= 11400
//#if FORGE
//$$ import net.minecraftforge.client.settings.KeyModifier;
//#endif
//$$ import net.minecraft.client.util.InputMappings;
//#endif

/**
 * Wrapper class so we can add functionality easily
 */
public class EssentialKeybinding implements GuiEssentialPlatform.Keybind {
    public static final List<EssentialKeybinding> ALL_BINDS = new ArrayList<>();
    public static boolean cancelKeybinds = false;
    public final KeyBinding keyBinding;
    private final String keyId;
    private final boolean alwaysTick;
    private Runnable onInitialPress;
    private Runnable onRepeatedHold;
    private Runnable onRelease;
    private boolean registeredWithMinecraft = false;
    private boolean pressed = false;
    private boolean requiresEssentialFull = false;
    private final KeyBindGuiBlocker keyBindGuiBlocker = new KeyBindGuiBlocker();

    //#if MC>=12109
    //$$ public EssentialKeybinding(String keyId, KeyBinding.Category category, int keyCode) {
    //#else
    public EssentialKeybinding(String keyId, String category, int keyCode) {
    //#endif
        this(keyId, category, keyCode, false);
    }

    //#if MC>=12109
    //$$ public EssentialKeybinding(String keyId, KeyBinding.Category category, int keyCode, boolean alwaysTick) {
    //#else
    public EssentialKeybinding(String keyId, String category, int keyCode, boolean alwaysTick) {
    //#endif
        this.keyId = keyId;
        this.keyBinding = new KeyBinding(LEGACY_IDS.getOrDefault(keyId, "keybind.name." + keyId), keyCode, category);
        this.alwaysTick = alwaysTick;
        ALL_BINDS.add(this);
    }

    private int getKeyCode() {
        //#if MC < 11400
        return keyBinding.getKeyCode();
        //#else
        //$$return ((KeyBindingAccessor) keyBinding).getBoundKey().getKeyCode();
        //#endif
    }

    public void register() {
        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        ((GameOptionsAccessor) settings).setKeyBindings(register(settings.keyBindings));

        //#if MC>=11202
        KeyBindingAccessor.getKeybinds().put(keyBinding.getKeyDescription(), keyBinding);
        //#else
        //$$ KeyBindingAccessor.getKeybinds().add(keyBinding);
        //#endif
        KeyBinding.resetKeyBindingArrayAndHash();
    }

    // For registering when mc.gameSettings might not yet be initialized
    KeyBinding[] register(KeyBinding[] allBindings) {
        if (registeredWithMinecraft) return allBindings;
        registeredWithMinecraft = true;
        return ArrayUtils.add(allBindings, keyBinding);
    }

    public EssentialKeybinding withInitialPress(Runnable runnable) {
        this.onInitialPress = runnable;
        return this;
    }

    public EssentialKeybinding requiresEssentialFull() {
        requiresEssentialFull = true;
        return this;
    }

    public EssentialKeybinding withRelease(Runnable runnable) {
        this.onRelease = runnable;
        return this;
    }

    public EssentialKeybinding withRepeatedHold(Runnable runnable) {
        this.onRepeatedHold = runnable;
        return this;
    }

    public void tickEvents() {
        if (alwaysTick) {
            tickMainMenu();
        } else if (GuiUtil.getOpenedScreen() instanceof GuiMainMenu ^ cancelKeybinds) {
            cancelKeybinds = false;
            tickMainMenu();
        } else if (UMinecraft.getWorld() != null) {
            tickWorld();
        }
    }

    private void tickMainMenu() {
        if (getRequiresEssentialFull() && !EssentialConfig.INSTANCE.getEssentialFull()) return;
        int keyCode = getKeyCode();

        //#if MC >= 26.3
        //$$ boolean keyDown;
        //$$ if (keyCode == InputConstants.UNKNOWN.getValue()) {
        //$$     keyDown = false;
        //$$ } else {
        //$$     var keyboardState = org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyboardState();
        //$$     keyDown = keyboardState != null && keyCode < keyboardState.remaining() && keyboardState.get(keyCode) != 0;
        //$$ }
        //#else
        boolean keyDown = keyCode != UKeyboard.KEY_NONE && UKeyboard.isKeyDown(keyCode);
        //#endif

        if (!pressed && keyDown) {
            pressed = true;
            keyBindGuiBlocker.block();
            if (onInitialPress != null)
                onInitialPress.run();
        } else if (pressed && keyDown) {
            if (onRepeatedHold != null)
                onRepeatedHold.run();
        } else if (pressed) {
            pressed = false;
            if (onRelease != null)
                onRelease.run();
        }
    }

    private int heldCount = 0;

    private void tickWorld() {
        if (getRequiresEssentialFull() && !EssentialConfig.INSTANCE.getEssentialFull()) return;
        // keyBinding.isPressed() will detect presses that have occurred between ticks, including if it has since been released
        // The following blocks check keyBinding.isKeyDown() to see if the key is still being held at the moment of the call
        if (!pressed && keyBinding.isPressed()) {
            pressed = true;
            heldCount = 0;
            keyBindGuiBlocker.block();
            if (onInitialPress != null)
                onInitialPress.run();
        }

        // There may have been more keypresses between ticks, so we will drain those here.
        while (pressed
                // Avoid counting multiple presses from long holds due to MC-118107
                && heldCount <= 3
                // isPressed() internally checks and decrements a counter of queued keypresses, so we can just loop on it
                && keyBinding.isPressed()) {
            // Complete the missed keypress event, skipping onRepeatedHold as they didn't hold for even 1 tick
            if (onRelease != null) onRelease.run();

            // Set us back up for the next keypress to resolve, either next loop, or the rest of the method
            if (onInitialPress != null) onInitialPress.run();
            heldCount = 0;
        }

        // Note: This block can intentionally run in the same tick that `pressed` was set to true, incase `onRelease` needs
        // to occur immediately
        if (pressed) {
            if (keyBinding.isKeyDown()) {
                heldCount++;
                if (onRepeatedHold != null)
                    onRepeatedHold.run();
            } else {
                pressed = false;

                // MC's Keybinding doesn't properly interpret OS-level key repeats in all situations and may accumulate
                // extra "between tick clicks", as detected by the .isPressed() method, when held for a few seconds (MC-118107).
                // This results in the Keybinding thinking multiple keypresses have been queued up when in reality the key was just held down once.
                // To counter this, we will forcibly unpress the key to reset it's internal state if we detect a long hold.
                if (heldCount > 3) ((KeyBindingAccessor) keyBinding).invokeUnpressKey();
                heldCount = 0;

                if (onRelease != null)
                    onRelease.run();
            }
        }
    }

    public boolean isRegisteredWithMinecraft() {
        return registeredWithMinecraft;
    }

    public void setKeyCode(int keyCode) {
        //#if MC < 11400
        keyBinding.setKeyCode(keyCode);
        //#else
        //#if FORGE
        //$$ keyBinding.setKeyModifierAndCode(KeyModifier.NONE,
        //#else
        //$$ keyBinding.setBoundKey(
        //#endif
        //#if MC>=12109
        //$$     InputUtil.fromKeyCode(new net.minecraft.client.input.KeyInput(keyCode, -1, 0)));
        //#else
        //$$     InputMappings.getInputByCode(keyCode, -1));
        //#endif
        //#endif
    }

    @Override
    public boolean isBound() {
        //#if MC>=11400
        //$$ return !keyBinding.isInvalid();
        //#else
        return keyBinding.getKeyCode() != 0;
        //#endif
    }

    @Override
    public @Nullable String getBoundKeyName() {
        if (!isBound()) return null;
        return UKeyboard.getKeyName(keyBinding);
    }

    @Override
    public boolean isConflicting() {
        if (!this.isBound()) {
            return false;
        }

        for (KeyBinding binding : Minecraft.getMinecraft().gameSettings.keyBindings) {
            if (this.keyBinding == binding) continue;

            //#if MC>=11701
            //$$ if (this.keyBinding.equals(binding)) {
            //#elseif MC==10809
            //$$ if (this.keyBinding.getKeyCode() == binding.getKeyCode()) {
            //#else
            if (this.keyBinding.conflicts(binding)) {
            //#endif
                return true;
            }
        }

        return false;
    }

    public boolean isKeyCode(int keyCode) {
        return isBound() && getKeyCode() == keyCode;
    }

    // Like isKeyCode but automatically converts the keyCode into a scancode for 26.3+.
    // May fail and return incorrect results in cases where there isn't a 1:1 correspondance.
    // For use where we don't yet have access to the proper scancode. Should be avoided where possible.
    @Deprecated
    public boolean isKeyCodeLegacy(int keyCode) {
        //#if MC >= 26.3
        //$$ keyCode = org.lwjgl.sdl.SDLKeyboard.SDL_GetScancodeFromKey(keyCode, null);
        //#endif
        return isKeyCode(keyCode);
    }

    /**
     * Unregisters the keybinding by removing it from the MC keybinding list
     */
    public void unregister() {
        if (!registeredWithMinecraft) return;
        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        //There should only ever be one instance
        int i = ArrayUtils.indexOf(settings.keyBindings, keyBinding);
        if (i > 0)
            ((GameOptionsAccessor) settings).setKeyBindings(ArrayUtils.removeAll(settings.keyBindings, i));

        //#if MC>=11202
        KeyBindingAccessor.getKeybinds().remove(keyBinding.getKeyDescription());
        //#else
        //$$ KeyBindingAccessor.getKeybinds().remove(keyBinding);
        //#endif
        KeyBinding.resetKeyBindingArrayAndHash();

        registeredWithMinecraft = false;
    }

    public boolean getRequiresEssentialFull() {
        return requiresEssentialFull;
    }

    /**
     * We used to translate certain keybinding ids (which was a bad idea because they will then get lost if someone
     * changes their language or if we decide to change the translation, and I18n is not even loaded at the point where
     * we need to register keybindings since 1.17) but we no longer do. To not wipe everyone's bindings, we keep the
     * legacy ids for those bindings which have seen use in production.
     */
    private static final Map<String, String> LEGACY_IDS = ImmutableMap.<String, String>builder()
        // Does not apply to 1.17+ cause I18n is not loaded when we registered the bindings, so they were never
        // translated in the first place.
        //#if MC<11700
        .put("ESSENTIAL_FRIENDS", "Open Friends Gui")
        .put("COSMETIC_STUDIO", "Open Cosmetic Studio")
        .put("ZOOM", "Zoom")
        .put("COSMETICS_VISIBILITY_TOGGLE", "Cosmetic Visibility Toggle")
        .put("CHAT_PEEK", "Chat Peek")
        .put("INVITE_FRIENDS", "Invite Friends")
        //#endif
        .build();

    /**
     * Handler to facilitate blocking certain GuiKeyTypedEvent from reaching GUIs while the keybind is held.
     * <p>
     * Note: In 1.16+ [GuiKeyTypedEvent]s are split into a keycode event, and a character event. And since there is no
     * guaranteed way to map a keycode to a character, we cannot consistently cancel ONLY the desired character event.
     * So this class will simply block ALL character typing events while this keybind's keycode remains held.
     */
    private class KeyBindGuiBlocker {
        private boolean registered = false;

        private void block() {
            if (!registered) {
                registered = true;
                Essential.EVENT_BUS.register(this);
            }
        }

        // Priority is set this high to run before the one in [OverlayManagerImpl], though this should conceptually run
        // before any other GuiKeyTypedEvent listener, not just that specific one.
        @Subscribe(priority = 100)
        public void keybindBlocker(GuiKeyTypedEvent event) {
            if (event.getKeyBindingKeyCode() == getKeyCode() || event.getTypedChar() != '\0') {
                event.setCancelled(true);
            }
        }

        @Subscribe
        public void keybindBlockerUnRegister(ClientTickEvent event) {
            // We need to use a tick event (as opposed to onRelease) and UKeyboard (as opposed to KeyBinding.isKeyDown)
            // because all KeyBindings are internally marked as released when changing screens, but we want to suppress
            // key events until the actual key is released.

            int keyCode = getKeyCode();
            //#if MC >= 26.3
            //$$ var keyboardState = org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyboardState();
            //$$ boolean keyDown = keyboardState != null && keyCode < keyboardState.remaining() && keyboardState.get(keyCode) != 0;
            //#else
            boolean keyDown = UKeyboard.isKeyDown(keyCode);
            //#endif
            if (!keyDown) {
                Essential.EVENT_BUS.unregister(this);
                registered = false;
            }
        }
    }
}
