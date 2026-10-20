package org.singularbots.compat.cct;

import dan200.computercraft.api.turtle.*;
import dan200.computercraft.api.upgrades.UpgradeData;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.*;
import net.minecraft.world.item.ItemStack;

/**
 * Built-in, non-removable diamond pickaxe. It is part of the robot, not a free tool item: robot items never carry it, so
 * every robot item (/give, the creative tab, a broken robot) stacks with every other; placing one fits it again, and
 * clients draw it on the item.
 */
public final class JuniorMining {
    private static final ThreadLocal<net.minecraft.world.level.Level> MINING=new ThreadLocal<>();
    private static final ResourceKey<ITurtleUpgrade> PICKAXE=ResourceKey.create(ITurtleUpgrade.REGISTRY,ResourceLocation.withDefaultNamespace("diamond_pickaxe"));
    private JuniorMining(){}
    public static boolean collecting(net.minecraft.world.level.Level level){return MINING.get()==level;}
    /** Keep the exception inside the actual dig; never change the world's gamerule. */
    public static boolean dig(net.minecraft.server.level.ServerPlayerGameMode player,net.minecraft.core.BlockPos pos){
        var access=TeachingPermissions.CURRENT.get();
        if(access==null||!TeachingPermissions.junior(access))return player.destroyBlock(pos);
        var before=MINING.get();
        try{MINING.set(access.getLevel());return player.destroyBlock(pos);}
        finally{if(before==null)MINING.remove();else MINING.set(before);}
    }
    private static UpgradeData<ITurtleUpgrade> upgrade(HolderLookup.Provider registries){return UpgradeData.ofDefault(registries.lookupOrThrow(ITurtleUpgrade.REGISTRY).getOrThrow(PICKAXE));}
    public static ItemStack item(){return new ItemStack(TeachingContent.JUNIOR_ITEM.get());}
    static boolean builtIn(UpgradeData<ITurtleUpgrade> upgrade){return upgrade!=null&&upgrade.holder().is(PICKAXE);}
    /** Drops the built-in pickaxe from a robot item, e.g. one saved by an older version, so it stacks with new ones. */
    static void strip(ItemStack stack){
        var left=dan200.computercraft.shared.ModRegistry.DataComponents.LEFT_TURTLE_UPGRADE.get();
        if(builtIn(stack.get(left)))stack.remove(left);
    }
    /** A copy of a robot item wearing the built-in pickaxe, for drawing it. */
    public static ItemStack armed(ItemStack stack,HolderLookup.Provider registries){
        var left=dan200.computercraft.shared.ModRegistry.DataComponents.LEFT_TURTLE_UPGRADE.get();
        if(stack.has(left))return stack;
        var armed=stack.copy();armed.set(left,upgrade(registries));return armed;
    }
    static void ensure(JuniorTurtleEntity turtle){
        var current=turtle.getAccess().getUpgradeWithData(TurtleSide.LEFT);
        if(builtIn(current))return;
        if(current!=null)dan200.computercraft.shared.turtle.TurtleUtil.storeItemOrDrop(turtle.getAccess(),current.getUpgradeItem());
        turtle.getAccess().setUpgrade(TurtleSide.LEFT,upgrade(turtle.getLevel().registryAccess()));
    }
}
