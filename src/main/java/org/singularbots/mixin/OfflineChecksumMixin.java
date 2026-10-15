package org.singularbots.mixin;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MCEF 2.1.6 still checks its remote checksum when skip-download is set. */
@Mixin(value = MCEFDownloader.class, remap = false)
public abstract class OfflineChecksumMixin {
    @Inject(method = "downloadJavaCefChecksum", at = @At("HEAD"), cancellable = true)
    private void ccedu$skipOfflineChecksum(CallbackInfoReturnable<Boolean> callback) {
        if (MCEF.getSettings().isSkipDownload()) callback.setReturnValue(false);
    }
}
