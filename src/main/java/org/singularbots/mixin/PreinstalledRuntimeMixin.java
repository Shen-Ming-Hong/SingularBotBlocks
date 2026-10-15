package org.singularbots.mixin;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFPlatform;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Set paths at the initialization boundary, after MCEF's development override. */
@Mixin(value = MCEF.class, remap = false)
public abstract class PreinstalledRuntimeMixin {
    @Inject(method = "initialize", at = @At("HEAD"))
    private static void ccedu$usePreinstalledRuntime(CallbackInfoReturnable<Boolean> callback) {
        String path = Minecraft.getInstance().gameDirectory.toPath().resolve("mods/mcef-libraries").toAbsolutePath().normalize().toString();
        System.setProperty("mcef.libraries.path", path);
        System.setProperty("jcef.path", path + "/" + MCEFPlatform.getPlatform().getNormalizedName());
        MCEF.getLogger().info("Preinstalled browser runtime: {}", path);
    }
}
