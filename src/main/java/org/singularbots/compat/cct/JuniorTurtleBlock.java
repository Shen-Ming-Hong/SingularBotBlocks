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
            turtle.placedBy(player);
        }
    }
    /** A broken robot always becomes an ownerless robot item, even in classrooms that turn block drops off (doTileDrops false). */
    @Override public void playerDestroy(Level level,Player player,BlockPos pos,BlockState state,net.minecraft.world.level.block.entity.BlockEntity entity,net.minecraft.world.item.ItemStack tool){
        if(level instanceof net.minecraft.server.level.ServerLevel server&&!server.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)&&!JuniorMining.collecting(level))
            for(var drop:getDrops(state,server,pos,entity,player,tool))net.minecraft.world.Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,drop);
        super.playerDestroy(level,player,pos,state,entity,tool);
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
