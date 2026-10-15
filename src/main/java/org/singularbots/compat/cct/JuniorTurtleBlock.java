package org.singularbots.compat.cct;

import dan200.computercraft.shared.platform.RegistryEntry;
import dan200.computercraft.shared.turtle.blocks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class JuniorTurtleBlock extends TurtleBlock {
    public JuniorTurtleBlock(Properties properties,RegistryEntry<BlockEntityType<TurtleBlockEntity>> type) { super(properties,type); }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity entity,net.minecraft.world.item.ItemStack stack){
        super.setPlacedBy(level,pos,state,entity,stack);
        if(!level.isClientSide&&entity instanceof Player player&&level.getBlockEntity(pos) instanceof JuniorTurtleEntity turtle){
            var data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            turtle.placedBy(player,data==null||!data.contains("SingularBots"));
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof JuniorTurtleEntity turtle)
            RemoteService.open(server,turtle);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof JuniorTurtleEntity turtle && !RemoteService.authorized(server,turtle))
            {server.displayClientMessage(RemoteService.denial(server,turtle),true);return net.minecraft.world.ItemInteractionResult.FAIL;}
        if(!level.isClientSide&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof JuniorTurtleEntity turtle)RemoteService.open(server,turtle);
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
