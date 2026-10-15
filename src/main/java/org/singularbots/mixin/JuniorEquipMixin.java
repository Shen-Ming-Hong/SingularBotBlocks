package org.singularbots.mixin;

import dan200.computercraft.api.turtle.*;
import dan200.computercraft.shared.turtle.core.TurtleEquipCommand;
import org.singularbots.compat.cct.TeachingPermissions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=TurtleEquipCommand.class,remap=false)
public abstract class JuniorEquipMixin {
    @Shadow @Final private TurtleSide side;
    @Inject(method="execute",at=@At("HEAD"),cancellable=true)
    private void builtInTool(ITurtleAccess access,CallbackInfoReturnable<TurtleCommandResult> result){
        if(side==TurtleSide.LEFT&&TeachingPermissions.junior(access))result.setReturnValue(TurtleCommandResult.failure("The robot's built-in diamond pickaxe cannot be removed"));
    }
}
