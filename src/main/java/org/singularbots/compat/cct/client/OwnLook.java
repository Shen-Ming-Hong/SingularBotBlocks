package org.singularbots.compat.cct.client;

import dan200.computercraft.shared.turtle.TurtleOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.singularbots.network.RemoteNetwork;
import org.singularbots.turtle.Appearance;
import java.util.*;

/**
 * Online players' saved robot looks. Robot items carry none, so a robot item another player holds is drawn with that
 * player's look, and every other robot item (own inventory and hands, the ground) with the local player's look; a framed one wears its framer's.
 */
public final class OwnLook {
    private OwnLook(){}
    private static final Map<UUID,RemoteNetwork.Look> LOOKS=new HashMap<>();
    /** The player whose model is being drawn, so items in their hands wear their look. */
    private static UUID holder;
    static void receive(RemoteNetwork.Look value){LOOKS.put(value.player(),value);}
    static void reset(){LOOKS.clear();holder=null;}
    static UUID holder(UUID value){var previous=holder;holder=value;return previous;}
    private static RemoteNetwork.Look current(){
        var player=Minecraft.getInstance().player;
        var look=LOOKS.get(holder!=null?holder:player==null?null:player.getUUID());
        if(look==null&&holder!=null&&player!=null)look=LOOKS.get(player.getUUID());
        return look==null?new RemoteNetwork.Look(new UUID(0,0),"",-1,"none"):look;
    }
    public static int colour(){return current().colour();}
    /** A copy of a robot item wearing the current look, for CC's item model, which reads the colour and overlay components. */
    static ItemStack dress(ItemStack stack){
        var look=current();var dressed=stack.copy();
        if(look.colour()==-1)dressed.remove(DataComponents.DYED_COLOR);else dressed.set(DataComponents.DYED_COLOR,new DyedItemColor(look.colour(),false));
        dressed.set(dan200.computercraft.shared.ModRegistry.DataComponents.OVERLAY.get(),overlay(look.decoration()));
        return dressed;
    }
    private static Holder<TurtleOverlay> overlay(String decoration){
        var level=Minecraft.getInstance().level;
        if(level==null||decoration.equals("none")||!Appearance.DECORATIONS.contains(decoration))return null;
        return level.registryAccess().registryOrThrow(TurtleOverlay.REGISTRY)
            .getHolder(ResourceKey.create(TurtleOverlay.REGISTRY,ResourceLocation.fromNamespaceAndPath("singularbots",decoration))).orElse(null);
    }
}
