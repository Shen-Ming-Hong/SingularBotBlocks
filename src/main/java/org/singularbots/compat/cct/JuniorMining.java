package org.singularbots.compat.cct;

import dan200.computercraft.api.turtle.*;
import dan200.computercraft.api.upgrades.UpgradeData;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.*;
import net.minecraft.world.item.ItemStack;

/** Built-in, non-removable diamond pickaxe. It is part of the robot, not a free tool item. */
public final class JuniorMining {
    private static final ResourceKey<ITurtleUpgrade> PICKAXE=ResourceKey.create(ITurtleUpgrade.REGISTRY,ResourceLocation.withDefaultNamespace("diamond_pickaxe"));
    private JuniorMining(){}
    private static UpgradeData<ITurtleUpgrade> upgrade(HolderLookup.Provider registries){return UpgradeData.ofDefault(registries.lookupOrThrow(ITurtleUpgrade.REGISTRY).getOrThrow(PICKAXE));}
    public static ItemStack item(HolderLookup.Provider registries){
        var stack=new ItemStack(TeachingContent.JUNIOR_ITEM.get());stack.set(dan200.computercraft.shared.ModRegistry.DataComponents.LEFT_TURTLE_UPGRADE.get(),upgrade(registries));return stack;
    }
    static void ensure(JuniorTurtleEntity turtle){
        var current=turtle.getAccess().getUpgradeWithData(TurtleSide.LEFT);
        if(current!=null&&current.holder().is(PICKAXE))return;
        if(current!=null)dan200.computercraft.shared.turtle.TurtleUtil.storeItemOrDrop(turtle.getAccess(),current.getUpgradeItem());
        turtle.getAccess().setUpgrade(TurtleSide.LEFT,upgrade(turtle.getLevel().registryAccess()));
    }
}
