package org.singularbots.mixin;

import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.turtle.core.TurtlePlayer;
import dan200.computercraft.shared.turtle.upgrades.TurtleTool;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.GameMasterBlock;
import org.singularbots.compat.cct.TeachingPermissions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skip tool-category restrictions only in the Junior command's scoped fake-player operation. */
@Mixin(value=TurtleTool.class,remap=false)
public abstract class JuniorMiningMixin {
    @Redirect(method="lambda$dig$0",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayerGameMode;destroyBlock(Lnet/minecraft/core/BlockPos;)Z",remap=true))
    private boolean collectDrops(net.minecraft.server.level.ServerPlayerGameMode player,BlockPos pos){
        return org.singularbots.compat.cct.JuniorMining.dig(player,pos);
    }
    @Inject(method="checkBlockBreakable",at=@At("HEAD"),cancellable=true)
    private void allBreakableBlocks(Level level,BlockPos pos,TurtlePlayer player,CallbackInfoReturnable<TurtleCommandResult> result){
        var access=TeachingPermissions.CURRENT.get();
        if(access==null||!TeachingPermissions.junior(access)||access.getLevel()!=level)return;
        var state=level.getBlockState(pos);
        result.setReturnValue(state.isAir()||state.getBlock() instanceof GameMasterBlock||state.getDestroySpeed(level,pos)<0
            ?TurtleCommandResult.failure("Cannot mine an unbreakable block")
            :TurtleCommandResult.success());
    }
}
