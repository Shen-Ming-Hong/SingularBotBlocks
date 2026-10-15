package org.singularbots.mixin;

import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.turtle.TurtleCommand;
import dan200.computercraft.shared.turtle.apis.TurtleAPI;
import dan200.computercraft.shared.turtle.core.TurtleAccessInternal;
import org.singularbots.compat.cct.Restoration;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Raw Lua pauses/steps at turtle actions; Blockly pauses at stable instruction IDs. */
@Mixin(value=TurtleAPI.class,remap=false)
public abstract class TeachingLuaPauseMixin {
    @Shadow(remap=false) @Final private TurtleAccessInternal turtle;
    @Inject(method="trackCommand",at=@At("HEAD"),cancellable=true)
    private void pause(TurtleCommand command,CallbackInfoReturnable<MethodResult> result){var t=Restoration.owner(turtle);if(t!=null&&!t.runtime().rawCommand())result.setReturnValue(waitThenExecute(command));}
    @Unique private MethodResult waitThenExecute(TurtleCommand command){return MethodResult.pullEvent("ccedu_debug",args->{var t=Restoration.owner(turtle);return t!=null&&!t.runtime().rawCommand()?waitThenExecute(command):turtle.executeCommand(command);});}
}
