package org.singularbots.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.singularbots.compat.cct.Restoration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class TeachingJournalMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"))
    private void before(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> result){Restoration.beforeBlock((Level)(Object)this,pos);}
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("RETURN"))
    private void after(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> result){Restoration.afterBlock((Level)(Object)this,pos);}
}
