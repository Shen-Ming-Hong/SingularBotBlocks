package org.singularbots.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.singularbots.compat.cct.Restoration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class TeachingEntityJournalMixin {
    @Inject(method="addFreshEntity",at=@At("RETURN"))
    private void spawned(Entity entity,CallbackInfoReturnable<Boolean> result){if(result.getReturnValueZ()&&!(entity instanceof ItemEntity))Restoration.irreversible("Entity spawning cannot be restored safely");}
}
