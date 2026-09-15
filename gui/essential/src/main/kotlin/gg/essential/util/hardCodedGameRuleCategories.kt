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
package gg.essential.util

// Gamerules weren't always inherently grouped into categories in Minecraft,
// so for older versions we'll use this hard-coded mapping.
fun getHardCodedCategoryForGameRule(gameRule: String): String {
    return when (gameRule) {
        "disableElytraMovementCheck",
        "doImmediateRespawn",
        "doLimitedCrafting",
        "drowningDamage",
        "fallDamage",
        "fireDamage",
        "freezeDamage",
        "keepInventory",
        "naturalRegeneration",
        "playersSleepingPercentage",
        "pvp",
        "spawnRadius",
        "spectatorsGenerateChunks",
            -> "player"
        "disableRaids",
        "forgiveDeadPlayers",
        "maxEntityCramming",
        "mobGriefing",
        "universalAnger",
            -> "mobs"
        "doInsomnia",
        "doMobSpawning",
        "doPatrolSpawning",
        "doTraderSpawning",
        "doWardenSpawning",
            -> "spawning"
        "doEntityDrops",
        "doMobLoot",
        "doTileDrops",
            -> "drops"
        "doDaylightCycle",
        "doFireTick",
        "doWeatherCycle",
        "randomTickSpeed",
            -> "world_updates"
        "announceAdvancements",
        "commandBlockOutput",
        "logAdminCommands",
        "sendCommandFeedback",
        "showDeathMessages",
            -> "chat"
        "maxCommandChainLength",
        "reducedDebugInfo",
            -> "miscellaneous"
        else
            -> "other"
    }
}
