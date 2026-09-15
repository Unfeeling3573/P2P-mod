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
package gg.essential.gui.layoutdsl

import gg.essential.elementa.UIComponent
import gg.essential.elementa.font.FontProvider

fun Modifier.font(fontProvider: FontProvider) = this then FontModifier { fontProvider }

class FontModifier(private val provider: () -> FontProvider) : Modifier {
    override fun applyToComponent(component: UIComponent): () -> Unit {
        val oldFontProvider = component.constraints.fontProvider
        component.constraints.fontProvider = provider()
        return {
            component.constraints.fontProvider = oldFontProvider
        }
    }
}
