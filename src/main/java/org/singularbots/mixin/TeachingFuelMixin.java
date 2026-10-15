package org.singularbots.mixin;

import dan200.computercraft.shared.turtle.core.TurtleBrain;
import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
import org.singularbots.compat.cct.JuniorTurtleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fixed CC: Tweaked 1.120.2 compatibility hook, affecting Junior turtles only. */
@Mixin(value=TurtleBrain.class,remap=false)
public abstract class TeachingFuelMixin {
    @Shadow public abstract TurtleBlockEntity getOwner();
    @Inject(method="isFuelNeeded",at=@At("HEAD"),cancellable=true)
    private void eduFuel(CallbackInfoReturnable<Boolean> result) { if (getOwner() instanceof JuniorTurtleEntity) result.setReturnValue(false); }
    @ModifyVariable(method="executeCommand",at=@At("HEAD"),argsOnly=true)
    private dan200.computercraft.api.turtle.TurtleCommand eduCommand(dan200.computercraft.api.turtle.TurtleCommand command) {
        if(!(getOwner() instanceof JuniorTurtleEntity junior))return command;
        var runtime=junior.runtime();long generation=runtime.generation();
        return access->{
            if(generation!=runtime.generation())return rejected(command,"Command cancelled by stop or teacher lock");
            var settings=org.singularbots.compat.cct.TeachingSettings.get(access.getLevel().getServer());
            if(!settings.allowed())return rejected(command,"Teacher stopped or disabled turtles");
            if(getOwner() instanceof JuniorTurtleEntity current&&current.locked()&&!(command instanceof org.singularbots.compat.cct.RemoteService.CheckedRemoteCommand remote&&remote.teacher()))return rejected(command,"Teacher locked this turtle");
            var before=org.singularbots.compat.cct.TeachingPermissions.CURRENT.get();
            var capture=org.singularbots.compat.cct.Restoration.capture(access);
            try{org.singularbots.compat.cct.TeachingPermissions.CURRENT.set(access);return command.execute(access);}
            finally{try{if(capture!=null)capture.finish();}finally{if(before==null)org.singularbots.compat.cct.TeachingPermissions.CURRENT.remove();else org.singularbots.compat.cct.TeachingPermissions.CURRENT.set(before);}}
        };
    }
    @org.spongepowered.asm.mixin.Unique
    private static dan200.computercraft.api.turtle.TurtleCommandResult rejected(dan200.computercraft.api.turtle.TurtleCommand command,String error){
        return command instanceof org.singularbots.compat.cct.RemoteService.CheckedRemoteCommand remote
            ?remote.rejected(error):dan200.computercraft.api.turtle.TurtleCommandResult.failure(error);
    }
}
