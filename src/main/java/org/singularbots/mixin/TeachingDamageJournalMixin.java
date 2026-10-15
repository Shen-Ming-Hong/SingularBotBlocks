package org.singularbots.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.singularbots.compat.cct.Restoration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class TeachingDamageJournalMixin {
    @Inject(method="hurt",at=@At("RETURN"))
    private void damaged(DamageSource source,float amount,CallbackInfoReturnable<Boolean> result){if(result.getReturnValueZ())Restoration.irreversible("Combat cannot be restored safely");}
}
