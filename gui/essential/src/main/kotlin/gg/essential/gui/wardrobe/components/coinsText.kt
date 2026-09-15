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
package gg.essential.gui.wardrobe.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.animation.*
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.LoadingIcon
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.network.connectionmanager.coins.CoinsManager
import java.awt.Color

fun LayoutScope.coinsText(
    coins: Int?,
    modifier: Modifier = Modifier,
    loadingModifier: Modifier = modifier,
    textShadow: Color? = EssentialPalette.TEXT_SHADOW,
) = coinsText(stateOf(coins), modifier, loadingModifier, textShadow)

fun LayoutScope.coinsText(
    state: WardrobeState,
    modifier: Modifier = Modifier,
    loadingModifier: Modifier = modifier,
    textShadow: Color? = EssentialPalette.TEXT_SHADOW,
) {
    val coinsVisual = mutableStateOf(state.coins.getUntracked()) // grab the current state

    val textComponent = coinsText(coinsVisual, modifier, loadingModifier, textShadow)

    val propertyHack = object {
        var coinsProperty: Int
            get() = coinsVisual.getUntracked() ?: 0
            set(value) = coinsVisual.set(value)
    }::coinsProperty

    State { state.areCoinsVisuallyFrozen() to state.coins() }.onChange(this.stateScope) { (frozen, coins) ->
        if (!frozen) {
            if (coins == null) {
                coinsVisual.set(null)
            } else {
                with(textComponent) {
                    propertyHack.animate(Animations.OUT_EXP, 2.5f, coins)
                }
            }
        }
    }
}

fun LayoutScope.coinsText(
    coinsState: State<Int?>,
    modifier: Modifier = Modifier,
    loadingModifier: Modifier = modifier,
    textShadow: Color? = EssentialPalette.TEXT_SHADOW,
): UIComponent {
    return row(Modifier.whenTrue({ coinsState() != null }, modifier, loadingModifier)) {
        ifNotNull(coinsState) {
            spacer(width = 1f)
            text(CoinsManager.COIN_FORMAT.format(it), Modifier.shadow(textShadow))
            spacer(width = 5f)
            icon(EssentialPalette.COIN_7X)
        } `else` {
            LoadingIcon(1.0)(Modifier.shadow(EssentialPalette.TEXT_SHADOW))
        }
    }
}
