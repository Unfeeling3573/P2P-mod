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
package gg.essential.gui.modals.select.component

import gg.essential.elementa.dsl.effect
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.shadow.ShadowEffect
import gg.essential.gui.friends.state.IStatusStates
import gg.essential.gui.friends.state.PlayerActivity
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignBoth
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedMaxSize
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.heightAspect
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.width
import gg.essential.util.CachedAvatarImage
import gg.essential.util.UuidNameLookup
import java.awt.Color
import java.util.*

fun LayoutScope.playerAvatar(uuid: UUID, modifier: Modifier = Modifier, shadowColor: Color = EssentialPalette.COMPONENT_BACKGROUND) {
    val image = CachedAvatarImage.create(uuid)
        .effect(ShadowEffect(shadowColor))

    image(modifier)
}

fun LayoutScope.playerAvatarWithOnlineIndicator(uuid: UUID, activities: IStatusStates, modifier: Modifier = Modifier) {
    box(Modifier.childBasedMaxSize().then(modifier)) {
        playerAvatar(uuid, Modifier.shadow())
        if_({ activities.getActivityState(uuid)() !is PlayerActivity.Offline }) {
            // Green online indicator
            box(Modifier.color(EssentialPalette.UPDATE_AVAILABLE_GREEN).width(2f).heightAspect(1f)
                    .alignBoth(Alignment.End(-1f)).shadow(Color.BLACK))
        }
    }
}

fun LayoutScope.playerName(uuid: UUID, modifier: Modifier = Modifier) {
    text(UuidNameLookup.nameState(uuid, "Loading..."), modifier)
}
