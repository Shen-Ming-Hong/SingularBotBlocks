package org.singularbots.compat.cct;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** A robot item put in an item frame shows, to everyone, the look of the player who put it there; taken out, it is a plain robot item again. */
@EventBusSubscriber(modid="singularbots")
public final class FramedRobots {
    private FramedRobots(){}
    /** Marks a robot item that wears the look of whoever framed it, so clients draw its own colour and overlay. */
    public static final String SHOWN="SingularBotsShown";
    public static boolean shown(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);return data!=null&&data.contains(SHOWN);}
    @SubscribeEvent public static void framed(PlayerInteractEvent.EntityInteract event){
        if(!(event.getTarget() instanceof ItemFrame frame)||!(event.getEntity() instanceof ServerPlayer player)||!frame.getItem().isEmpty()
            ||!event.getItemStack().is(TeachingContent.JUNIOR_ITEM.get()))return;
        // The frame takes its copy after this event; dress that copy on the next tick.
        player.server.tell(new TickTask(player.server.getTickCount()+1,()->{
            var item=frame.getItem();if(frame.isRemoved()||!item.is(TeachingContent.JUNIOR_ITEM.get())||shown(item))return;
            var dressed=item.copy();dress(dressed,player);frame.setItem(dressed,false);
        }));
    }
    private static void dress(ItemStack stack,ServerPlayer player){
        var look=org.singularbots.program.AppearanceStore.Look.NONE;
        try{look=StudentPrograms.appearance(player.serverLevel()).get(player.getUUID());}catch(Exception ignored){/* An unreadable file shows the plain robot. */}
        if(look.colour()!=null)stack.set(DataComponents.DYED_COLOR,new DyedItemColor(look.colour(),false));
        if(look.decoration()!=null&&!look.decoration().equals("none"))stack.set(dan200.computercraft.shared.ModRegistry.DataComponents.OVERLAY.get(),
            player.serverLevel().registryAccess().registryOrThrow(dan200.computercraft.shared.turtle.TurtleOverlay.REGISTRY)
                .getHolderOrThrow(ResourceKey.create(dan200.computercraft.shared.turtle.TurtleOverlay.REGISTRY,ResourceLocation.fromNamespaceAndPath(TeachingContent.MOD,look.decoration()))));
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putBoolean(SHOWN,true));
    }
    /** Taken out of a frame (it drops as an item entity), the robot item carries no look again. */
    public static void undress(ItemStack stack){
        if(!shown(stack))return;
        stack.remove(DataComponents.DYED_COLOR);stack.remove(dan200.computercraft.shared.ModRegistry.DataComponents.OVERLAY.get());
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.remove(SHOWN));
        var data=stack.get(DataComponents.CUSTOM_DATA);if(data!=null&&data.isEmpty())stack.remove(DataComponents.CUSTOM_DATA);
    }
    @SubscribeEvent public static void dropped(EntityJoinLevelEvent event){
        if(event.getLevel() instanceof ServerLevel&&event.getEntity() instanceof ItemEntity item&&shown(item.getItem())){var stack=item.getItem().copy();undress(stack);item.setItem(stack);}
    }
}
