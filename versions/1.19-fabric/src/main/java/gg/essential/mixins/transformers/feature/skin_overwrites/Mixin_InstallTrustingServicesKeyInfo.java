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
package gg.essential.mixins.transformers.feature.skin_overwrites;

// Applies only to 1.19+

import gg.essential.handlers.GameProfileManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//#if MC >= 26.3
//$$ import net.minecraft.server.Services;
//#else
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
//#endif

//#if MC >= 26.3
//$$ import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//#elseif MC >= 1.21.9
//$$ import org.spongepowered.asm.mixin.injection.ModifyArg;
//#else
import com.llamalad7.mixinextras.injector.ModifyReceiver;
//#endif

@Mixin(MinecraftClient.class)
public class Mixin_InstallTrustingServicesKeyInfo {
    //#if MC >= 26.3
    //$$ @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/Services;create(Lcom/mojang/authlib/services/MinecraftServicesDiscoveryService;Ljava/io/File;)Lnet/minecraft/server/Services;"))
    //$$ private Services installTrustingServicesKeyInfo(Services services) throws ReflectiveOperationException {
    //#else
    //#if MC>=12109
    //$$ @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ApiServices;create(Lcom/mojang/authlib/yggdrasil/YggdrasilAuthenticationService;Ljava/io/File;)Lnet/minecraft/util/ApiServices;"))
    //#else
    @ModifyReceiver(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/yggdrasil/YggdrasilAuthenticationService;createMinecraftSessionService()Lcom/mojang/authlib/minecraft/MinecraftSessionService;"))
    //#endif
    private YggdrasilAuthenticationService installTrustingServicesKeyInfo(YggdrasilAuthenticationService services) throws ReflectiveOperationException {
    //#endif
        GameProfileManager.register(services);
        return services;
    }
}
