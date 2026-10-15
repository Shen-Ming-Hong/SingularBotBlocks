package org.singularbots.compat.cct;

import dan200.computercraft.api.turtle.ITurtleAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Legacy vertical-column semantics, updated for negative-Y worlds. */
public final class TeachingPermissions {
    public static final ThreadLocal<ITurtleAccess> CURRENT=new ThreadLocal<>();
    public static boolean junior(ITurtleAccess access){return access instanceof dan200.computercraft.shared.turtle.core.TurtleBrain brain&&brain.getOwner() instanceof JuniorTurtleEntity;}
    public static boolean enterable(Level level,BlockPos pos){
        if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos))return false;
        return !border(level,pos,-1)&&!border(level,pos,1);
    }
    private static boolean border(Level level,BlockPos pos,int direction){
        for(int y=pos.getY();y>=level.getMinBuildHeight()&&y<level.getMaxBuildHeight();y+=direction){var block=level.getBlockState(new BlockPos(pos.getX(),y,pos.getZ())).getBlock();if(block instanceof PermissionBlock p){if(p.rule==PermissionBlock.Rule.BORDER)return true;if(p.rule==PermissionBlock.Rule.ANTI_BORDER)break;}}
        return false;
    }
    public static boolean editable(Level level,BlockPos pos){
        if(!enterable(level,pos)||level.getBlockState(pos).getBlock() instanceof PermissionBlock)return false;
        for(int y=pos.getY();y>=level.getMinBuildHeight();y--){var block=level.getBlockState(new BlockPos(pos.getX(),y,pos.getZ())).getBlock();if(block instanceof PermissionBlock p){if(p.rule==PermissionBlock.Rule.ALLOW_BUILD)return true;if(p.rule==PermissionBlock.Rule.DENY_BUILD)return false;}}
        return TeachingSettings.get(level.getServer()).canBuild();
    }
    public static boolean inventoryAllowed(ITurtleAccess access,BlockPos pos){
        if(!editable(access.getLevel(),pos))return false;
        if(access.getLevel().getBlockEntity(pos) instanceof JuniorTurtleEntity other){var owner=access.getOwningPlayer();return owner!=null&&other.getAccess().getOwningPlayer()!=null&&owner.getId().equals(other.libraryId());}
        return true;
    }
}
