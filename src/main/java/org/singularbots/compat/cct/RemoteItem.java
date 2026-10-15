package org.singularbots.compat.cct;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import java.util.UUID;

public final class RemoteItem extends Item {
    public RemoteItem(Properties properties) { super(properties); }
    public static UUID binding(ItemStack stack) {
        if (!(stack.getItem() instanceof RemoteItem)) return null;
        var data=stack.get(DataComponents.CUSTOM_DATA);
        var tag=data==null ? new CompoundTag() : data.copyTag();
        return tag.hasUUID("eduTurtle") ? tag.getUUID("eduTurtle") : null;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof JuniorTurtleEntity turtle)) return InteractionResult.PASS;
        if (context.getPlayer() instanceof ServerPlayer player) {
            RemoteService.track(turtle);
            if (!RemoteService.authorized(player,turtle)) { player.displayClientMessage(RemoteService.denial(player,turtle),true); return InteractionResult.FAIL; }
            var tag=context.getItemInHand().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            tag.putUUID("eduTurtle",turtle.identity());
            context.getItemInHand().set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
            RemoteService.open(player,turtle);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            var turtle=RemoteService.resolve(server.serverLevel(),binding(stack));
            if (turtle!=null) RemoteService.open(server,turtle);
            else player.displayClientMessage(Component.translatable("message.singularbots.unavailable"),true);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
