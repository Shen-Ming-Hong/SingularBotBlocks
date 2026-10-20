package org.singularbots.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.singularbots.client.EditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The full-screen editor is opaque; drawing the world behind it only slows the browser, which is pumped once per frame.
 * While running or stepping the editor covers only one side, so the world is drawn for the student to watch the robot. */
@Mixin(GameRenderer.class)
public abstract class EditorWorldRenderMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void ccedu$skipHiddenWorld(DeltaTracker deltaTracker, CallbackInfo callback) {
        if (Minecraft.getInstance().screen instanceof EditorScreen editor && editor.coversWorld()) callback.cancel();
    }
}
