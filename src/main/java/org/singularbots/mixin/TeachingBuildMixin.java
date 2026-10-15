package org.singularbots.mixin;

import dan200.computercraft.api.turtle.*;
import dan200.computercraft.shared.turtle.core.*;
import org.singularbots.compat.cct.TeachingPermissions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={TurtlePlaceCommand.class,TurtleToolCommand.class},remap=false)
public abstract class TeachingBuildMixin {
    @Unique private boolean eduNonBlockUse;
    @Shadow(remap=false) @Final private InteractDirection direction;
    @Inject(method="execute",at=@At("HEAD"),cancellable=true)
    private void eduBuild(ITurtleAccess turtle,CallbackInfoReturnable<TurtleCommandResult> result){eduNonBlockUse=(Object)this instanceof TurtlePlaceCommand&&!(turtle.getInventory().getItem(turtle.getSelectedSlot()).getItem() instanceof net.minecraft.world.item.BlockItem);if(TeachingPermissions.junior(turtle)&&!TeachingPermissions.editable(turtle.getLevel(),turtle.getPosition().relative(direction.toWorldDir(turtle))))result.setReturnValue(TurtleCommandResult.failure("Teacher building permission denied"));}
    @Inject(method="execute",at=@At("RETURN"))
    private void undoSafety(ITurtleAccess turtle,CallbackInfoReturnable<TurtleCommandResult> result){if(eduNonBlockUse&&result.getReturnValue().isSuccess())org.singularbots.compat.cct.Restoration.irreversible("Item use or fluid placement cannot be restored safely");}
}
