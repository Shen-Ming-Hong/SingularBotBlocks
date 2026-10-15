package org.singularbots.mixin;

import dan200.computercraft.api.turtle.*;
import dan200.computercraft.shared.turtle.core.*;
import org.singularbots.compat.cct.TeachingPermissions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={TurtleDropCommand.class,TurtleSuckCommand.class},remap=false)
public abstract class TeachingInventoryPermissionMixin {
    @Shadow(remap=false) @Final private InteractDirection direction;
    @Inject(method="execute",at=@At("HEAD"),cancellable=true)
    private void eduItems(ITurtleAccess turtle,CallbackInfoReturnable<TurtleCommandResult> result){if(TeachingPermissions.junior(turtle)&&!TeachingPermissions.inventoryAllowed(turtle,turtle.getPosition().relative(direction.toWorldDir(turtle))))result.setReturnValue(TurtleCommandResult.failure("Inventory interaction denied"));}
}
