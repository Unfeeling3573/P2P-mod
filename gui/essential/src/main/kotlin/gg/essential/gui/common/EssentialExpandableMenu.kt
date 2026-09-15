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
package gg.essential.gui.common

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.EssentialExpandableMenu.Style
import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.combinators.not
import gg.essential.gui.elementa.state.v2.listStateOf
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.image.ImageFactory
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.FloatPosition
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.alignVertical
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.effect
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.hoverColor
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.image
import gg.essential.gui.layoutdsl.layoutAsColumn
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.whenTrue
import gg.essential.gui.util.hoverScopeV2
import gg.essential.gui.util.makeHoverScope
import gg.essential.universal.USound
import gg.essential.vigilance.utils.onLeftClick
import java.awt.Color

fun LayoutScope.expandableMenu(
    title: String,
    items: List<LayoutScope.(Style) -> Unit>,
    modifier: Modifier = Modifier,
    collapsedHeight: Float = 19f,
    outlineSize: Float = 1f,
    style: Style = Style()
) = expandableMenu(stateOf(title), listStateOf(*items.toTypedArray()), modifier, collapsedHeight, outlineSize, stateOf(style))

fun LayoutScope.expandableMenu(
    title: State<String>,
    items: ListState<LayoutScope.(Style) -> Unit>,
    modifier: Modifier = Modifier,
    collapsedHeight: Float = 19f,
    outlineSize: Float = 1f,
    style: State<Style> = stateOf(Style())
) = EssentialExpandableMenu(title, items, collapsedHeight, outlineSize, style)(modifier)

class EssentialExpandableMenu(
    private val title: State<String>,
    private val items: ListState<LayoutScope.(Style) -> Unit>,
    private val collapsedHeight: Float = 19f,
    private val outlineSize: Float = 1f,
    private val style: State<Style> = stateOf(Style())
) : UIBlock() {

    private val expandedState: MutableState<Boolean> = mutableStateOf(false)

    private val buttonColor = style.map { it.buttonColor }
    private val buttonHoverColor = style.map { it.buttonHoverColor }

    private val buttonTextColor = style.map { it.buttonTextColor }
    private val buttonTextHoverColor = style.map { it.buttonTextHoverColor }

    private val outlineColor = style.map { it.outlineColor }
    private val outlineHoverColor = style.map { it.outlineHoverColor }

    private val componentDividerColor = style.map { it.componentDividerColor }
    private val componentDividerHoverColor = style.map { it.componentDividerHoverColor }

    private val componentBackgroundColor = style.map { it.componentBackgroundColor }
    private val componentBackgroundHoverColor = style.map { it.componentBackgroundHoverColor }

    private val arrowIconState = expandedState.map { if (it) EssentialPalette.ARROW_UP_7X5 else EssentialPalette.ARROW_DOWN_7X5 }

    init {
        this.layoutAsColumn(
            Modifier.fillWidth().childBasedHeight(outlineSize).color(outlineColor).hoverColor(outlineHoverColor).shadow(),
        ) {
            val upper = box(Modifier.fillWidth(padding = outlineSize).height(collapsedHeight - (2 * outlineSize)).color(buttonColor).hoverColor(buttonHoverColor).hoverScope()) {
                text(
                    title,
                    Modifier.alignHorizontal(Alignment.Start(7f)).color(buttonTextColor).hoverColor(buttonTextHoverColor).shadow(EssentialPalette.BLACK),
                    centeringContainsShadow = false,
                )
                image(
                    arrowIconState,
                    Modifier.alignVertical(Alignment.Center(true)).alignHorizontal(Alignment.End(7f)).color(buttonTextColor).hoverColor(buttonTextHoverColor).shadow()
                )
            }.onLeftClick { event ->
                USound.playButtonPress()
                event.stopPropagation()
                expandedState.set { !it }
            }
            // Since we only want the outline and dividers to "hover" when hovering the main button, we have to pass along the hover scope
            this@EssentialExpandableMenu.makeHoverScope(upper.hoverScopeV2())

            column(
                Modifier.fillWidth(padding = outlineSize).whenTrue(!expandedState, Modifier.height(0f))
                    .color(componentDividerColor).hoverColor(componentDividerHoverColor)
                    .effect { ScissorEffect() },
                Arrangement.spacedBy(1f, FloatPosition.CENTER)
            ) {
                forEach(items) { item ->
                    bind(style) { style ->
                        box(Modifier.fillWidth().color(componentBackgroundColor).hoverColor(componentBackgroundHoverColor).hoverScope()) {
                            item(style)
                        }
                    }
                }
            }
        }
    }

    fun setExpanded(expanded: Boolean) {
        expandedState.set(expanded)
    }

    data class Style(
        val buttonColor: Color = EssentialPalette.GRAY_OUTLINE_BUTTON,
        val buttonHoverColor: Color = EssentialPalette.GRAY_OUTLINE_BUTTON_HOVER,
        val buttonTextColor: Color = EssentialPalette.TEXT_HIGHLIGHT,
        val buttonTextHoverColor: Color = buttonTextColor,
        val outlineColor: Color = EssentialPalette.GRAY_OUTLINE_BUTTON_OUTLINE,
        val outlineHoverColor: Color = EssentialPalette.GRAY_OUTLINE_BUTTON_OUTLINE_HOVER,
        val componentDividerColor: Color = buttonColor,
        val componentDividerHoverColor: Color = buttonColor,
        val componentBackgroundColor: Color = EssentialPalette.COMPONENT_BACKGROUND,
        val componentBackgroundHoverColor: Color = componentBackgroundColor,
        val collapsedIcon: ImageFactory = EssentialPalette.ARROW_DOWN_7X4,
        val expandedIcon: ImageFactory = EssentialPalette.ARROW_UP_7X4,
    )

}
