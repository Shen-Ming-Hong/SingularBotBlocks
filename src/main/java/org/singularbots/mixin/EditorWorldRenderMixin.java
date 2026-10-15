package org.singularbots.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.singularbots.client.EditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The full-screen editor is opaque; drawing the world behind it only slows the browser, which is pumped once per frame. */
@Mixin(GameRenderer.class)
public abstract class EditorWorldRenderMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void ccedu$skipHiddenWorld(DeltaTracker deltaTracker, CallbackInfo callback) {
        if (Minecraft.getInstance().screen instanceof EditorScreen) callback.cancel();
    }
}
