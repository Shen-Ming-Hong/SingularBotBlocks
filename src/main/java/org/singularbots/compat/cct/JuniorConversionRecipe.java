package org.singularbots.compat.cct;

import dan200.computercraft.shared.turtle.items.TurtleItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Conversion consumes one turtle and preserves its computer disk ID and upgrade components. */
public final class JuniorConversionRecipe extends CustomRecipe {
    public JuniorConversionRecipe(CraftingBookCategory category){super(category);}
    private ItemStack original(CraftingInput input){
        ItemStack turtle=ItemStack.EMPTY;boolean dye=false;
        for(var stack:input.items()) {
            if(stack.isEmpty())continue;
            if(stack.getItem() instanceof TurtleItem && !stack.is(TeachingContent.JUNIOR_ITEM.get()) && turtle.isEmpty())turtle=stack;
            else if(stack.is(Items.GREEN_DYE)&&!dye)dye=true;
            else return ItemStack.EMPTY;
        }
        return dye?turtle:ItemStack.EMPTY;
    }
    @Override public boolean matches(CraftingInput input,Level level){return !original(input).isEmpty();}
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider lookup){
        var original=original(input);if(original.isEmpty())return ItemStack.EMPTY;
        var result=new ItemStack(TeachingContent.JUNIOR_ITEM.get());result.applyComponents(original.getComponentsPatch());return result;
    }
    @Override public boolean canCraftInDimensions(int width,int height){return width*height>=2;}
    @Override public RecipeSerializer<?> getSerializer(){return TeachingContent.CONVERSION.get();}
}
