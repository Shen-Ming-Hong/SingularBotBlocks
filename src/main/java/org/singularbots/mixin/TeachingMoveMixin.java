package org.singularbots.mixin;

import dan200.computercraft.api.turtle.*;
import dan200.computercraft.shared.turtle.core.*;
import org.singularbots.compat.cct.TeachingPermissions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=TurtleMoveCommand.class,remap=false)
public abstract class TeachingMoveMixin {
    @Shadow(remap=false) @Final private MoveDirection direction;
    @Inject(method="execute",at=@At("HEAD"),cancellable=true)
    private void eduMove(ITurtleAccess turtle,CallbackInfoReturnable<TurtleCommandResult> result){if(TeachingPermissions.junior(turtle)&&!TeachingPermissions.enterable(turtle.getLevel(),turtle.getPosition().relative(direction.toWorldDir(turtle))))result.setReturnValue(TurtleCommandResult.failure("Teacher boundary blocks movement"));}
}
